package com.example.touhou.core;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <b>结构归属登记</b> —— 照搬 LogiTech 多方块引擎的 {@code MultiBlockService} +
 * {@code MultiBlockHandler} 那套"uuid + 状态码"方案。
 *
 * <h2>照搬了什么</h2>
 * <ol>
 *   <li><b>每个方块都带 {@code uid} + {@code sta} 两个方块数据</b>（核心与所有构件各一份），
 *       对应 LogiTech 的 {@code MultiBlockService.setUUID/setStatus}（键 {@code uuid} / {@code mb-sta}）；</li>
 *   <li><b>状态码语义</b>（照抄 LogiTech 的取值）：
 *       <pre>
 *   0  = 未登记（不属于任何结构）
 *   1  = 已登记，结构成立
 *  -N  = 过渡态：构件已放下但核心还没认领它（N 是"等候计数"）
 *       </pre></li>
 *   <li><b>盖章是线性的</b>（{@code MultiBlockHandler.createHandler}）：
 *       核心拿到朝向之后，直接按 schema 遍历每一格写 uid/sta，
 *       <b>不做任何搜索</b>。撤销同理（{@code destroy}）。</li>
 *   <li><b>构件靠 uid 认核心</b>（{@code acceptPartRequest}）：
 *       读自己那格的 uid → 查内存表拿到核心位置。
 *       这比"扫 5×5×5 = 125 格找核心"便宜两个数量级。</li>
 * </ol>
 *
 * <h2>为什么这里比 LogiTech 简单</h2>
 * <ul>
 *   <li>LogiTech 的零件 tick 时要做"负计数握手"（{@code statusCode + 1}）来应付
 *       异步搭建与重启；我们的构件是<b>事件驱动</b>的（放置/破坏才动），
 *       所以过渡态只需要一个 {@code -1}，不需要排队计数。</li>
 *   <li>LogiTech 把校验跑在异步线程（它只读方块数据缓存）；我们的层图允许把任意坐标
 *       声明为"必须是空气"（{@code nu}），那就得读 Bukkit 世界 —— 只能主线程。
 *       所以这里<b>不加锁也不异步</b>：调用点全在主线程，去抖已经保证不会重入。</li>
 * </ul>
 */
public final class StructureRegistry {

    /** 方块数据键：结构 uid（对应 LogiTech 的 {@code uuid}）。 */
    public static final String KEY_UID = "touhou:mb-uid";
    /** 方块数据键：登记状态码（对应 LogiTech 的 {@code mb-sta}）。 */
    public static final String KEY_STATUS = "touhou:mb-sta";

    /** 未登记。 */
    public static final int STA_NONE = 0;
    /** 已登记且结构成立。 */
    public static final int STA_ACTIVE = 1;
    /** 过渡态：构件已放下、核心还没认领。 */
    public static final int STA_WAITING = -1;

    /** uid → 核心位置（对应 LogiTech 的 {@code MULTIBLOCK_CACHE}）。 */
    private static final Map<String, Location> CORE_OF_UID = new ConcurrentHashMap<>();

    private StructureRegistry() {
    }

    // ---------------------------------------------------------------- 读

    /** 某方块的登记状态码；没有数据时视为 {@link #STA_NONE}。 */
    public static int statusOf(Location loc) {
        String raw = TouhouData.getString(loc, KEY_STATUS, null);
        if (raw == null || raw.isBlank()) {
            return STA_NONE;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return STA_NONE;
        }
    }

    /** 某方块的归属 uid；没有则为 {@code null}。 */
    public static String uidOf(Location loc) {
        String raw = TouhouData.getString(loc, KEY_UID, null);
        return raw == null || raw.isBlank() || "null".equals(raw) ? null : raw.trim();
    }

    /**
     * <b>构件反查核心</b>：读自己那格的 uid → 查内存表。
     *
     * <p>对应 LogiTech 的 {@code acceptPartRequest}：状态码为 1 时
     * {@code handler.getCore()} 就是答案，全程不碰世界。
     *
     * @return 核心位置；未登记 / 表里没有（刚重启还没重连）时为 {@code null}
     */
    public static Location coreOfPart(Location part) {
        String uid = uidOf(part);
        if (uid == null) {
            return null;
        }
        Location core = CORE_OF_UID.get(uid);
        if (core == null) {
            return null;                // 重启后还没重连：等核心的第一次检测把表填回来
        }
        if (BlockStorage.check(core) == null) {
            CORE_OF_UID.remove(uid);    // 核心已经没了
            return null;
        }
        return core;
    }

    /** 登记表里有多少个结构（诊断用）。 */
    public static int registeredCount() {
        return CORE_OF_UID.size();
    }

    // ---------------------------------------------------------------- 写

    /**
     * <b>给整座结构盖章</b> —— 核心与每个构件都写同一个 uid + {@code sta=1}。
     *
     * <p>直接对应 LogiTech {@code MultiBlockHandler.createHandler} 里那段循环：
     * <pre>
     *   for (i = 0; i &lt; size; i++) { setUUID(core + part(i), uid); setStatus(..., 1); }
     *   setUUID(core, uid); setStatus(core, 1);
     * </pre>
     *
     * <p>⭐ 这一步是"减少检测次数"的关键：盖完章之后，
     * 构件再想知道自己的核心，只需读 1 次方块数据（{@link #coreOfPart}），
     * 不必再扫 125 格；核心想确认结构，也只需比对 uid/partId（1 遍层图）。
     *
     * @param parts 构件位置（来自 {@link ReactorStructure#partLocations}）
     * @return 本次写入的 uid
     */
    public static String attach(Location core, Iterable<Location> parts) {
        String uid = uidOf(core);
        if (uid == null) {
            uid = UUID.randomUUID().toString();
        }
        for (Location part : parts) {
            TouhouData.setString(part, KEY_UID, uid);
            TouhouData.setString(part, KEY_STATUS, Integer.toString(STA_ACTIVE));
        }
        TouhouData.setString(core, KEY_UID, uid);
        TouhouData.setString(core, KEY_STATUS, Integer.toString(STA_ACTIVE));
        CORE_OF_UID.put(uid, core);
        return uid;
    }

    /**
     * <b>撤销整座结构的登记</b> —— 对应 LogiTech {@code MultiBlockHandler.destroy}。
     *
     * <p>LogiTech 那边是：核心写 {@code uuid=null / sta=0}，再对每个零件写同样的值。
     * 这里保持一致，并且额外清掉内存表，避免"uid 表里还留着一个已经不存在的核心"。
     *
     * @param parts 构件位置；传 {@code null} 表示只清核心自己（用于核心被拆、
     *              但构件列表已无从得知的场景 —— 那种情况由构件自己发现核心不存在）
     * @return 本次撤销的 uid；核心本来就没登记时为 {@code null}
     */
    public static String detach(Location core, Iterable<Location> parts) {
        String uid = uidOf(core);
        if (parts != null) {
            for (Location part : parts) {
                clear(part);
            }
        }
        clear(core);
        if (uid != null) {
            CORE_OF_UID.remove(uid);
        }
        return uid;
    }

    /**
     * 只清一格（构件被单独拆掉时用）。
     *
     * <p>注意<b>不</b>动内存表：拆一格构件不等于整座结构注销。
     */
    public static void clear(Location loc) {
        TouhouData.setString(loc, KEY_UID, "null");
        TouhouData.setString(loc, KEY_STATUS, Integer.toString(STA_NONE));
    }

    /** 构件已放下但核心还没认领：写 {@link #STA_WAITING}，让诊断能看出"在等核心"。 */
    public static void markWaiting(Location part) {
        TouhouData.setString(part, KEY_UID, "null");
        TouhouData.setString(part, KEY_STATUS, Integer.toString(STA_WAITING));
    }

    /** 诊断用：这一格的结构归属说明。 */
    public static String describe(Location loc) {
        int sta = statusOf(loc);
        String uid = uidOf(loc);
        String label = switch (sta) {
            case STA_ACTIVE -> "已登记（结构成立）";
            case STA_NONE -> "未登记";
            case STA_WAITING -> "过渡态：已放下，等候核心认领";
            default -> "过渡态 " + sta;
        };
        return label + "  uid=" + (uid == null ? "(无)" : uid.substring(0, 8) + "…")
                + "  登记表内结构数=" + registeredCount();
    }

    /** 重载配置/结构后清空内存表（方块数据里的 uid 保留，等核心重连时重新填表）。 */
    public static void clearCache() {
        CORE_OF_UID.clear();
    }
}
