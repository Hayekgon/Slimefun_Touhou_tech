package com.example.touhou.core;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import org.bukkit.Location;

/**
 * 反应堆的方块数据读写。
 *
 * <p>Slimefun 的机器状态走**方块数据**（字符串 Map），不走 PDC：
 * key {@code energy-charge} 是本体电力网络写的，我们自己的 key 加 {@code touhou:} 前缀避免撞车。
 *
 * <p>★ 两个必须守住的点（本 fork 的 API 特性）：
 * <ol>
 *   <li>{@code getDataContainer(loc)} 在区块还没加载完时可能返回 {@code null}
 *       或 {@code isDataLoaded() == false}；此时<b>不能当空值处理</b>，
 *       要发起加载请求并当作"暂时读不到"（返回默认值），下一 tick 再来。</li>
 *   <li>所有写操作走 {@code StorageCacheUtils.setData}，由 Slimefun 异步落盘。</li>
 * </ol>
 *
 * <h2>★★ 第三个必须守住的点：{@code loc == null} 一律当"读不到 / 不用写"</h2>
 * 这一条是<b>血的教训</b>（2026-09-20 真实踩过，整个插件启用失败）：
 * Slimefun 的 {@code BlockMenuPreset} 在<b>构造器里</b>就调用 {@code init()}，
 * 于是 {@code constructMenu(...)} 会在"物品构造期"跑一遍 —— 那一刻世界上还没有这个方块，
 * 界面代码手里的 {@code Location} 必然是 {@code null}。
 * 只要那条路径上有一个 {@code getXxx(null, ...)}，就会 NPE 到
 * {@code StorageCacheUtils.getBlock(null)} → {@code Location#getWorld()}
 * → {@code Error occurred while enabling Touhou}，<b>整个插件起不来</b>。
 *
 * <p>所以本类做了<b>统一的兜底</b>，而不是指望每个调用点自己判空：
 * <ul>
 *   <li>所有<b>读</b>方法：{@code loc == null}（或世界为空）⇒ 直接返回传入的默认值，
 *       不碰 {@code StorageCacheUtils}；</li>
 *   <li>所有<b>写</b>方法：{@code loc == null} ⇒ 直接 {@code return}（静默忽略）。</li>
 * </ul>
 * 这是消灭"整类启动崩溃"的唯一一处收口：调用方可以放心传 {@code null}，
 * 拿到的就是"没有数据"这个语义。
 */
public final class TouhouData {

    /** 模式持久化 key。 */
    public static final String KEY_MODE = "touhou:reactor-mode";
    /** 运行状态 key（同时是"未激活"的判据，见 {@link ReactorState}）。 */
    public static final String KEY_STATE = "touhou:reactor-state";
    /** 最近一次结构检测结果（持久化，便于重启后仍能正确显示未激活）。 */
    public static final String KEY_STRUCTURE_OK = "touhou:structure-ok";
    /** 累计发电量（统计用，可选）。 */
    public static final String KEY_TOTAL_GENERATED = "touhou:total-generated";
    /** 累计消耗燃料桶数。 */
    public static final String KEY_TOTAL_FUEL = "touhou:total-fuel";
    /** 附加粒子特效开关（每台机器独立，默认开）。 */
    public static final String KEY_PARTICLES = "touhou:particles";
    /** 构建模式：MANUAL / AUTO（每台机器独立，默认手动）。 */
    public static final String KEY_BUILD_MODE = "touhou:build-mode";
    /**
     * 结构朝向（{@code 0..3}，对应 {@code ReactorStructure.Direction} 的序号）。
     *
     * <p>参照 LogiTech 多方块引擎的 {@code mb-dir}：结构检测成功后把命中的朝向写在这里，
     * 重启 / 区块重载后重连时可以直接按已知朝向校验，不必再试四个方向。
     */
    public static final String KEY_DIRECTION = "touhou:structure-dir";

    // ---- 祭祀系多方块（赛钱箱 / 神社的木桩）----

    /**
     * 神社的木桩<b>编号</b>（{@code 0..5}）。
     *
     * <p>由赛钱箱激活时写进每根木桩自己的方块数据（需求：编号要持久化到木桩）。
     * 编号顺序 = 先 +X 正方向、后 +Z 正方向（详见 {@code SaizenbakoStructure}）。
     * 「未编号」写成 {@link #POST_INDEX_NONE}，而不是把键删掉 ——
     * 方块数据的删除在不同 fork 上行为不一致，写一个哨兵值最稳。
     */
    public static final String KEY_POST_INDEX = "touhou:post-index";
    /** 木桩未编号的哨兵值。 */
    public static final int POST_INDEX_NONE = -1;

    /**
     * 木桩所属结构核心的坐标（{@code world;x;y;z}）。
     *
     * <p>木桩自己不是 POWER 节点、也不持有结构对象，所以"我的核心在哪"必须落盘：
     * 有了它，木桩的界面才能在<b>不扫世界</b>的情况下显示核心位置与激活状态。
     * ★ 序列化格式与 {@code BlockMenu} 内部用的 {@code world;x;y;z} 一致
     * （见 {@link #encodeLocation}），出问题时用肉眼比对日志即可。
     */
    public static final String KEY_CORE_POS = "touhou:mb-core-pos";

    /**
     * 赛钱箱<b>最近一次运作的结论</b>（信息槽显示"为什么没在产出"）。
     *
     * <p>只在结论变化时写一次，所以不会每 tick 落盘。
     */
    public static final String KEY_SAIZEN_NOTE = "touhou:saizen-note";

    /** 赛钱箱上次运作成功的时间戳（毫秒，诊断用）。 */
    public static final String KEY_SAIZEN_LAST_CRAFT = "touhou:saizen-last-craft";

    private TouhouData() {
    }

    /**
     * 这个坐标能不能安全地读写方块数据。
     *
     * <p>判据只有两条：坐标不为空、世界不为空。
     * {@code StorageCacheUtils} 内部第一步就是 {@code loc.getWorld()}（拿区块键），
     * 所以这两条是它的硬前提 —— 不满足就别传进去。
     */
    private static boolean usable(Location loc) {
        return loc != null && loc.getWorld() != null;
    }

    /** 拿数据容器；未加载时发起请求并返回 null。<b>loc 为空同样返回 null。</b> */
    public static ASlimefunDataContainer container(Location loc) {
        if (!usable(loc)) {
            return null;
        }
        // ★ 这里用的是 StorageCacheUtils.getBlock(Location) 而不是 getDataContainer(Location)：
        //   编译依赖 Slimefun4-2025.1 里**没有** getDataContainer(Location)（只有运行时的
        //   Slimefun 2026.07 才有）。用两边都有的 getBlock 才不会编译不过。
        SlimefunBlockData data = StorageCacheUtils.getBlock(loc);
        if (data == null || data.isPendingRemove()) {
            return null;
        }
        if (!data.isDataLoaded()) {
            StorageCacheUtils.requestLoad(data);
            return null;
        }
        return data;
    }

    /**
     * 读字符串；读不到返回 def。
     *
     * <p>优先走容器（能区分"数据没加载"和"值就是空的"）；
     * 容器拿不到时退回 {@code StorageCacheUtils.getData}，它是一个对两边都安全的读法。
     *
     * <p>★ {@code loc == null} ⇒ 直接返回 {@code def}（见类注释的"第三个必须守住的点"）。
     */
    public static String getString(Location loc, String key, String def) {
        if (!usable(loc) || key == null) {
            return def;
        }
        ASlimefunDataContainer data = StorageCacheUtils.getBlock(loc);
        if (data != null && !data.isPendingRemove() && data.isDataLoaded()) {
            String v = data.getData(key);
            return v == null ? def : v;
        }
        String fallback = StorageCacheUtils.getData(loc, key);
        return fallback == null ? def : fallback;
    }

    /** 写字符串。<b>loc 为空时静默忽略（构造期没有方块，本来也无处可写）。</b> */
    public static void setString(Location loc, String key, String value) {
        if (!usable(loc) || key == null) {
            return;
        }
        StorageCacheUtils.setData(loc, key, value);
    }

    /** 读 long；读不到或格式错误返回 def。 */
    public static long getLong(Location loc, String key, long def) {
        String v = getString(loc, key, null);
        if (v == null) {
            return def;
        }
        try {
            return Long.parseLong(v.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** 读 int；读不到或格式错误返回 def。 */
    public static int getInt(Location loc, String key, int def) {
        long v = getLong(loc, key, def);
        return v > Integer.MAX_VALUE ? Integer.MAX_VALUE
                : (v < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) v);
    }

    public static void setLong(Location loc, String key, long value) {
        setString(loc, key, Long.toString(value));
    }

    public static void setInt(Location loc, String key, int value) {
        setString(loc, key, Integer.toString(value));
    }

    /** 在已有值上累加（用于统计）。 */
    public static void addLong(Location loc, String key, long delta) {
        setLong(loc, key, getLong(loc, key, 0L) + delta);
    }

    /** 读枚举；读不到或值非法返回 def。 */
    public static <E extends Enum<E>> E getEnum(Location loc, String key, Class<E> type, E def) {
        String v = getString(loc, key, null);
        if (v == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, v.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    public static void setEnum(Location loc, String key, Enum<?> value) {
        setString(loc, key, value.name());
    }

    /** 数据是否已就绪（用于"这一 tick 先别干活"的早退判断）。 */
    public static boolean isReady(Location loc) {
        return container(loc) != null;
    }

    /** 人类可读坐标。 */
    public static String xyz(Location loc) {
        return loc == null ? "(null)"
                : loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    // ---------------------------------------------------------------- 坐标序列化

    /**
     * 把坐标写成 {@code world;x;y;z} 存进方块数据。
     *
     * <p>★ 必须连同世界名一起存：只存 x/y/z 的话，同一个坐标在多世界服务器上会指错地方
     * （本工程已经因为 {@code Location} 当 Map 键不归一化踩过一次同类问题，见 {@link #norm}）。
     * 格式刻意与 Slimefun 的 {@code BlockMenu#serializeLocation} 一致，便于对照排查。
     *
     * @return 世界为空时返回 {@code null}（调用方应把 {@code null} 当成"没记录"）
     */
    public static String encodeLocation(Location loc) {
        Location norm = norm(loc);
        if (norm == null) {
            return null;
        }
        return norm.getWorld().getName() + ";" + norm.getBlockX() + ";"
                + norm.getBlockY() + ";" + norm.getBlockZ();
    }

    /** {@link #encodeLocation} 的逆运算；格式不对 / 世界不存在时返回 {@code null}。 */
    public static Location decodeLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split(";");
        if (parts.length != 4) {
            return null;
        }
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;                // 世界还没加载（或被删了）：当作"没记录"
        }
        try {
            return new Location(world, Integer.parseInt(parts[1].trim()),
                    Integer.parseInt(parts[2].trim()), Integer.parseInt(parts[3].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * <b>把 Location 归一化成"方块坐标键"</b> —— 用 Location 当 Map 键之前必须先过这里。
     *
     * <p>★ 为什么必须有它：Bukkit 的 {@code Location#hashCode()} 用的是<b>方块整数坐标</b>
     * （{@code (int) x} 等），而 {@code Location#equals()} 比较的是<b>原始 double 加上 yaw/pitch</b>。
     * 结果同一个方块的两种 Location（比如一个来自 {@code new Location(w,98,100,99)}，
     * 另一个来自方块数据反序列化/带朝向的那个）会 <b>hash 到同一个桶却判不相等</b> ——
     * Map 里于是同时留下两条记录。
     *
     * <p>本工程实测踩过：只放了 2 个接口，{@code CORE_OF.size()} 却是 4；而且逐条打印
     * 明细时两行坐标<b>看起来一模一样</b>（{@code toString} 的整数部分相同），
     * 靠"打印明细"根本看不出来，最后是从 hashCode / equals 不一致推出来的。
     *
     * <p>归一化之后键只由"世界 + 方块整数坐标"决定，pitch/yaw 一律清零。
     */
    public static Location norm(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return null;
        }
        return new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
