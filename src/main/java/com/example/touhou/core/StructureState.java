package com.example.touhou.core;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * 「多方块激活状态 / 核心位置 / 木桩编号」的<b>只读查询出口</b>。
 *
 * <h2>本类替换掉了什么</h2>
 * 上一轮这里叫 {@code PlaceholderStructure}：那时赛钱箱与木桩的结构层图还没给出，
 * 于是它<b>刻意不做任何结构判定</b>（{@link #activeAt} 恒 false、{@link #coreOf} 恒 null），
 * 只给两个 GUI 提供"未定义结构"的文案。本轮层图到位
 * （{@code docs/saizenbako-layers.yml} → {@link AddonConfig#saizenLayers}），
 * 判定与绑定关系都真实存在了，所以本类改成读<b>真实落盘的那几个键</b>：
 * <table border="1">
 *   <caption>数据来源</caption>
 *   <tr><th>问题</th><th>答案从哪来</th></tr>
 *   <tr><td>这一格（或它所属的结构）激活了吗</td>
 *       <td>核心方块数据里的 {@link TouhouData#KEY_STRUCTURE_OK}（{@link #activeAt}）</td></tr>
 *   <tr><td>核心在哪</td>
 *       <td>木桩方块数据里的 {@link TouhouData#KEY_CORE_POS}（激活时写入）；
 *           拿不到时退回 {@link StructureRegistry#coreOfPart}（uid → 核心的内存表）</td></tr>
 *   <tr><td>我是几号木桩</td>
 *       <td>木桩方块数据里的 {@link TouhouData#KEY_POST_INDEX}（{@link #postIndexOf}）</td></tr>
 * </table>
 *
 * <h2>★ 这里的每一个方法都必须能在 {@code loc == null} 时安全返回</h2>
 * {@code BlockMenuPreset.init()} 是在 <b>preset 的构造器里</b>被调用的 ——
 * 那一刻还没有任何方块，{@code Location} 必然是 {@code null}。
 * 上一轮就是因为在这里放行了 null，让 {@code TouhouData.getString(null, ...)}
 * NPE 到 Slimefun 的 {@code LocationUtils.getChunkKey}，
 * 表现为<b>整个插件启用失败</b>（{@code Error occurred while enabling Touhou}）。
 * 所以：<b>构造期的界面内容一律走 {@code null} 分支</b>，真实内容交给
 * {@code BlockMenuPreset#newInstance(BlockMenu, Block)} 或 ticker 去刷新。
 */
public final class StructureState {

    /**
     * 方块数据键：结构激活标记（沿用反应堆那套 {@code touhou:structure-ok}）。
     *
     * <p>唯一写入点是 {@link Saizenbako} 的激活/失效路径（手动点击激活、复检发现结构被拆）。
     * 读它的地方有：本类的 {@link #activeAt}、两个 GUI 的信息槽、命令诊断。
     */
    public static final String KEY_STRUCTURE_OK = TouhouData.KEY_STRUCTURE_OK;

    private StructureState() {
    }

    // ---------------------------------------------------------------- 核心

    /** 这一格是不是赛钱箱核心（读方块 id，不扫世界）。 */
    public static boolean isCore(Location loc) {
        return loc != null && AddonConfig.SAIZEN_CORE_ID.equals(BlockStorage.checkID(loc));
    }

    /**
     * 这一格所属结构的核心位置。
     *
     * <p>三种来源，按可靠性排序：
     * <ol>
     *   <li>自己就是核心 ⇒ 自己；</li>
     *   <li>木桩方块数据里的 {@link TouhouData#KEY_CORE_POS}（激活时写入，重启后依然有效）；</li>
     *   <li>{@link StructureRegistry#coreOfPart}（uid → 核心，重启后要等核心复检才会填回来）。</li>
     * </ol>
     * ★ 绝不"扫附近的赛钱箱"：木桩与赛钱箱属于不同的结构，靠距离猜会把
     * 「别人的赛钱箱」认成自己的核心。
     *
     * @return 核心位置；未绑定 / 核心已不存在时返回 {@code null}
     */
    public static Location coreOf(Location self) {
        if (self == null || self.getWorld() == null) {
            return null;
        }
        if (isCore(self)) {
            return self;
        }
        Location recorded = TouhouData.decodeLocation(
                TouhouData.getString(self, TouhouData.KEY_CORE_POS, null));
        if (recorded != null) {
            return isCore(recorded) ? recorded : null;    // 核心被拆了 ⇒ 绑定作废
        }
        Location byUid = StructureRegistry.coreOfPart(self);
        return isCore(byUid) ? byUid : null;
    }

    // ---------------------------------------------------------------- 激活状态

    /**
     * 这一格的多方块结构此刻是否<b>已激活</b>。
     *
     * <p>判据是"核心方块数据里的 {@link #KEY_STRUCTURE_OK} 为 true"：
     * <ul>
     *   <li>核心自己：直接读自己的键；</li>
     *   <li>木桩：先看自己有没有这个键（没有），再读它绑定的核心那一格。</li>
     * </ul>
     * 也就是<b>单一数据源在核心身上</b> —— 木桩不复制这份状态，
     * 于是不会出现"核心已经停了、木桩还显示已激活"这种自相矛盾。
     */
    public static boolean activeAt(Location loc) {
        // ★ 必须挡 null：本方法会被 GUI 的【构造期】调用（那时 loc 必然是 null）。
        //   放行会给 TouhouData.getString 传 null，进而 NPE，表现为整个插件启用失败。
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        Boolean own = readFlag(loc);
        if (own != null) {
            return own;
        }
        Location core = coreOf(loc);
        if (core == null) {
            return false;
        }
        Boolean coreFlag = readFlag(core);
        return coreFlag != null && coreFlag;
    }

    /** 读某格自己的 {@link #KEY_STRUCTURE_OK}；没写过时返回 {@code null}（= 不知道）。 */
    private static Boolean readFlag(Location loc) {
        String raw = TouhouData.getString(loc, KEY_STRUCTURE_OK, null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return "true".equalsIgnoreCase(raw.trim());
    }

    // ---------------------------------------------------------------- 木桩编号

    /** 这根木桩的编号；未编号 / 不是木桩时返回 {@link TouhouData#POST_INDEX_NONE}。 */
    public static int postIndexOf(Location post) {
        if (post == null) {
            return TouhouData.POST_INDEX_NONE;
        }
        long v = TouhouData.getLong(post, TouhouData.KEY_POST_INDEX, TouhouData.POST_INDEX_NONE);
        return v < 0 || v >= SaizenbakoStructure.POST_COUNT
                ? TouhouData.POST_INDEX_NONE : (int) v;
    }

    /** 木桩编号是否合法（0~5）。 */
    public static boolean hasPostIndex(Location post) {
        return postIndexOf(post) >= 0;
    }

    // ---------------------------------------------------------------- GUI 文案

    /** 信息格里「激活状态」那一行（含颜色码）。 */
    public static String stateLine(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "&7激活状态： &8(定位失败)";
        }
        if (activeAt(loc)) {
            return "&7激活状态： &a已激活";
        }
        Location core = coreOf(loc);
        if (core == null) {
            return "&7激活状态： &7未激活 &8(未绑定核心)";
        }
        return "&7激活状态： &7未激活 &8(核心 " + xyz(core) + "，点它的信息格可激活)";
    }

    /**
     * 信息格里「核心位置」那几行（含颜色码）。
     *
     * @param self 自己的位置；{@code null} = 定位失败
     * @param core 结构核心位置；{@link #coreOf} 的返回值
     */
    public static List<String> coreLine(Location self, Location core) {
        List<String> lines = new ArrayList<>();
        if (core != null) {
            lines.add("&7多方块核心： &a✔ 已定位");
            lines.add("&7核心坐标： &f" + xyz(core)
                    + (self != null && sameBlock(self, core) ? " &8(本方块自身)" : ""));
        } else if (self == null) {
            lines.add("&7多方块核心： &8(定位失败)");
        } else {
            lines.add("&7多方块核心： &c✘ 未找到核心");
            lines.add("&8（结构还没被激活过：请到赛钱箱上点信息格）");
        }
        return lines;
    }

    /** 信息格里「我的编号」那一行（木桩界面用）。 */
    public static String indexLine(Location post) {
        int idx = postIndexOf(post);
        if (idx < 0) {
            return "&7木桩编号： &8未编号 &7（结构未激活）";
        }
        return "&7木桩编号： &e#" + idx + " &8→ 对应核心预留槽 #" + idx;
    }

    /**
     * 核心界面：「6 个预留槽分别由哪根木桩供料」那几行。
     *
     * <p>纯查方块数据，不扫世界 —— 所以就算界面在别的区块被打开也不会卡。
     */
    public static List<String> postBindingLines(SaizenbakoStructure structure, Location core,
                                                ReactorStructure.Direction dir) {
        List<String> lines = new ArrayList<>();
        if (structure == null || core == null || core.getWorld() == null) {
            return lines;
        }
        Location[] posts = structure.postLocations(core, dir);
        for (int i = 0; i < posts.length; i++) {
            Location p = posts[i];
            int idx = postIndexOf(p);
            String tag = idx == i ? "&a✔" : (idx < 0 ? "&c✘ 未编号" : "&e⚠ 编号 " + idx);
            lines.add("&7预留槽 #" + i + " ← 木桩 #" + i + " @ &f" + xyz(p) + "  " + tag);
        }
        return lines;
    }

    // ---------------------------------------------------------------- 诊断

    /** 诊断用：该位置的结构状态一句话。 */
    public static String describe(Location loc) {
        if (loc == null) {
            return "结构状态: (null)";
        }
        Location core = coreOf(loc);
        int idx = postIndexOf(loc);
        return "结构状态: 激活=" + activeAt(loc)
                + "  核心=" + (core == null ? "未找到" : xyz(core))
                + "  编号=" + (idx < 0 ? "未编号" : "#" + idx)
                + "  该格方块=" + describeBlock(loc)
                + "  登记=" + StructureRegistry.describe(loc);
    }

    // ---------------------------------------------------------------- 工具

    /** 人类可读坐标（对齐 {@link TouhouData#xyz}，不另造一套格式）。 */
    public static String xyz(Location loc) {
        return loc == null ? "(null)" : TouhouData.xyz(loc);
    }

    /** 两个坐标是不是同一个方块（跨世界比较也安全）。 */
    private static boolean sameBlock(Location a, Location b) {
        return a != null && b != null && a.getWorld() == b.getWorld()
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    /** 诊断用：这一格实际是什么方块。 */
    private static String describeBlock(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "(null)";
        }
        String id = BlockStorage.checkID(loc);
        return id == null ? "(不是粘液方块)" : id;
    }
}
