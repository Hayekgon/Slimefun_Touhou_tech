package com.example.touhou.core;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * 「赛钱箱」那座多方块的结构契约实现。
 *
 * <h2>为什么不是"再写一个 LayeredReactorStructure"</h2>
 * 层图 → 世界坐标的换算、四向探测、诊断明细、构件计数这些事，
 * {@link LayeredReactorStructure} 已经写全了。本类<b>刻意只是它的一个薄封装</b>：
 * <ul>
 *   <li>{@link #check(Location)} 先确认"这一格确实是赛钱箱"（层图里的 {@code C}
 *       那一格是核心自己，{@link LayeredReactorStructure} 不查它），再委托给层图实现；</li>
 *   <li>额外提供<b>木桩编号</b>这套赛钱箱专有的语义：{@link #postOffsets()} /
 *       {@link #postLocations} / {@link #postIndexOf}。</li>
 * </ul>
 * 换句话说：坐标数学只有一份（在 {@link LayeredReactorStructure} 里），
 * 这里只加"这台机器特有的那点东西"。再抄一份换算代码是这类工程最容易踩的坑 ——
 * 两份一旦漂移，就会出"结构检测说完整、盖章却盖到别的格子"这种自相矛盾的状态。
 *
 * <h2>★ 核心偏心（9×6×9 层图里 C 在最右一列）</h2>
 * 层图换算以 <b>核心那一格</b> 为原点，所以 C 落在哪一格都算得对；
 * 构造时传 {@code allowOffCenterCore = true} 只是为了跳过"核心必须居中"这条
 * 防错断言（那条断言对反应堆仍然有效，行为一字未改）。
 *
 * <h2>★ 木桩编号顺序</h2>
 * 「先 +X 正方向、后 +Z 正方向」= 按<b>层图偏移</b>（相对核心的 dx、dz）排序：
 * dx 升序，dx 相同再按 dz 升序。刻意用层图偏移而不是世界坐标：
 * 层图偏移是"这台机器自己的坐标系"，机器整体转 90° 之后编号不变
 * （用世界坐标排的话，玩家把机器换个朝向，木桩编号就全乱了）。
 * 这套顺序与扫描报告给出的 {@code (2,2,0) (2,2,8) (6,2,0) (6,2,8) (8,2,2) (8,2,6)} 一致。
 */
public final class SaizenbakoStructure implements ReactorStructure {

    /** 木桩数量 = 预留槽数量 = 编号范围 {@code 0..5}。 */
    public static final int POST_COUNT = 6;

    /** 结构名（写进日志/诊断）。 */
    private static final String NAME = "赛钱箱（层图，核心偏心）";

    private static SaizenbakoStructure instance;

    /** 层图实现（坐标数学全在它里面）。 */
    private final LayeredReactorStructure layered;
    /** 6 根木桩的层图偏移，<b>已按编号顺序</b>排好（{dx, dy, dz}）。 */
    private final List<int[]> postOffsets;

    private SaizenbakoStructure(LayeredReactorStructure layered) {
        this.layered = layered;
        this.postOffsets = List.copyOf(layered.offsetsByXThenZ(AddonConfig.SAIZEN_POST_ID));
        if (postOffsets.size() != POST_COUNT) {
            // ★ 层图写错要在【构造期】就炸：宁可启动时报清楚，也不要等玩家搭好结构
            //   才发现"少一根木桩/多一根木桩"（那时报错信息只会说"某格应为 P 实际 X"）。
            throw new IllegalArgumentException("赛钱箱层图里应有 " + POST_COUNT
                    + " 根木桩（字符 P），实际找到 " + postOffsets.size() + " 根");
        }
    }

    /** 取（并缓存）默认实例 —— 层图来自 config.yml，缺段时用内置默认值。 */
    public static SaizenbakoStructure get() {
        if (instance == null) {
            AddonConfig cfg = AddonConfig.get();
            instance = new SaizenbakoStructure(new LayeredReactorStructure(
                    NAME,
                    // ★ 空气字符归一化：扫描报告里画的是 '.'，legend 里登记的是 '_'
                    //   （详见 AddonConfig.normalizeAirChar 的注释）
                    AddonConfig.normalizeAirChar(cfg.saizenLayers), cfg.saizenLegend,
                    // 木桩有 6 根，所以没有"每类最多一个"的限制（空集 = 不限制）
                    java.util.Set.of(),
                    // 对称性交给程序算：这份层图绕核心转 90° 必然出界，会算出 false，
                    // 于是检测时四个方向都会试（正是需求要的 4 向识别）
                    null,
                    // ★ 核心偏心：层图里 C 在最右一列
                    true));
        }
        return instance;
    }

    /** 配置重载（{@code /touhou reload}）时丢弃缓存，下次取用时按新层图重建。 */
    public static void reload() {
        instance = null;
    }

    /**
     * 供自检/命令用：拿一份"指定层图"的实例，不碰缓存。
     *
     * <p>存在的意义：{@code /touhou saizen ... slots} 要在不改全局状态的前提下
     * 报告当前配置算出来的编号顺序。
     */
    public static SaizenbakoStructure of(AddonConfig cfg) {
        return new SaizenbakoStructure(new LayeredReactorStructure(
                NAME, AddonConfig.normalizeAirChar(cfg.saizenLayers), cfg.saizenLegend,
                java.util.Set.of(), null, true));
    }

    // ---------------------------------------------------------------- ReactorStructure

    @Override
    public String name() {
        return NAME;
    }

    /**
     * 检测结构是否完整。
     *
     * <p>比层图检测多一步：<b>先确认这一格确实是赛钱箱</b>。
     * 层图里的 {@code C} 代表"核心自己那一格"，检测时会跳过它 ——
     * 于是"在别人的方块上点一下"也会走进来，必须先挡掉。
     */
    @Override
    public Result check(Location core) {
        Result self = checkSelfIsCore(core);
        if (self != null) {
            return self;
        }
        return layered.check(core);
    }

    @Override
    public Result check(Location core, Direction preferred) {
        Result self = checkSelfIsCore(core);
        if (self != null) {
            return self;
        }
        return layered.check(core, preferred);
    }

    @Override
    public List<Location> partLocations(Location core, Direction direction) {
        return layered.partLocations(core, direction);
    }

    /**
     * <b>落点表必须显式委托给层图实现</b>（★ 血的教训）。
     *
     * <p>本类是 {@link LayeredReactorStructure} 的<b>薄封装</b>：{@code check} /
     * {@code partLocations} / {@code size} 这些方法都是一个个转发下去的。
     * 而 {@link ReactorStructure#cells()} 是本工程后来为"多方块投影"新加的
     * <b>default 方法</b>（默认返回空表）—— 忘了委托它，本类就会拿到那份空表，
     * 表现为"落点 0 格 / 结构不支持投影"，而结构检测一切正常，极难定位。
     *
     * <p>★ 教训：给接口加 default 方法时，<b>包装类必须逐项检查要不要转发</b>
     * —— default 方法不会像抽象方法那样在编译期逼你实现它。
     *
     * <p>下面这几个方法（{@link #cells} / {@link #supportsProjection} /
     * {@link #isSymmetric} / {@link #projector}）都是同一个原因加上的。
     */
    @Override
    public List<ReactorStructure.Cell> cells() {
        return layered.cells();
    }

    /** 委托：能不能投影完全取决于层图落点表（见 {@link #cells}）。 */
    @Override
    public boolean supportsProjection() {
        return !layered.cells().isEmpty();
    }

    /** 委托：投影那边靠它决定"是否强制按 NORTH 画"。 */
    @Override
    public boolean isSymmetric() {
        return layered.isSymmetric();
    }

    /** 委托：赛钱箱自己那套投影偏好（目前与层图一致，留作扩展点）。 */
    @Override
    public Projector projector() {
        return layered.projector();
    }

    /** 核心那一格是不是赛钱箱；是则返回 {@code null}（= 继续检测），不是则返回失败结果。 */
    private static Result checkSelfIsCore(Location core) {
        if (core == null || core.getWorld() == null) {
            return Result.of(false, "核心坐标无效", List.of(), List.of());
        }
        String id = BlockStorage.checkID(core);
        if (!AddonConfig.SAIZEN_CORE_ID.equals(id)) {
            return Result.of(false,
                    "这一格不是赛钱箱（实际 " + (id == null ? "非粘液方块" : id) + "）",
                    List.of(), List.of());
        }
        return null;
    }

    // ---------------------------------------------------------------- 木桩编号

    /** 6 根木桩的层图偏移（编号顺序，{@code {dx, dy, dz}}）—— 拷贝，外部改不动。 */
    public List<int[]> postOffsets() {
        List<int[]> copy = new ArrayList<>(postOffsets.size());
        for (int[] o : postOffsets) {
            copy.add(o.clone());
        }
        return copy;
    }

    /** 第 {@code index} 根木桩的层图偏移；越界返回 {@code null}。 */
    public int[] postOffset(int index) {
        if (index < 0 || index >= postOffsets.size()) {
            return null;
        }
        return postOffsets.get(index).clone();
    }

    /**
     * 第 {@code index} 根木桩在世界里的位置（编号顺序）。
     *
     * <p>纯数学：偏移按朝向旋转后加到核心坐标上，<b>不查世界</b>。
     * 所以它可以每轮 tick 调用（镜像/读 IO 槽都要它），开销只有 6 次乘法加法。
     *
     * @param direction 命中朝向；{@code null} 视为 NORTH（未落盘朝向时的兜底）
     * @return 长度 6 的数组；核心无效时返回空数组
     */
    public Location[] postLocations(Location core, Direction direction) {
        if (core == null || core.getWorld() == null) {
            return new Location[0];
        }
        Direction dir = direction == null ? Direction.NORTH : direction;
        Location[] out = new Location[postOffsets.size()];
        for (int i = 0; i < postOffsets.size(); i++) {
            int[] o = postOffsets.get(i);
            int[] rot = dir.rotate(o[0], o[2]);
            out[i] = new Location(core.getWorld(),
                    core.getBlockX() + rot[0], core.getBlockY() + o[1], core.getBlockZ() + rot[1]);
        }
        return out;
    }

    /**
     * 反查：世界里某一格是第几号木桩（越界/不是木桩时返回 {@code -1}）。
     *
     * <p>木桩自己的 GUI 想显示"我是几号"时用它 —— 但正常情况下木桩是从
     * 方块数据里读编号的（{@link TouhouData#KEY_POST_INDEX}），
     * 这个方法只用于诊断与"数据丢了要重算"的兜底。
     */
    public int postIndexOf(Location core, Direction direction, Location post) {
        if (post == null) {
            return -1;
        }
        Location[] all = postLocations(core, direction);
        for (int i = 0; i < all.length; i++) {
            Location p = all[i];
            if (p.getBlockX() == post.getBlockX()
                    && p.getBlockY() == post.getBlockY()
                    && p.getBlockZ() == post.getBlockZ()
                    && p.getWorld() == post.getWorld()) {
                return i;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- 诊断

    /** 结构尺寸 {x, y, z}。 */
    public int[] size() {
        return layered.size();
    }

    /** 需要检测的构件总数（不含核心，也不含"必须空气"的格子）。 */
    public int partCount() {
        return layered.partCount();
    }

    /** 是否允许核心偏心（本结构恒 true）。 */
    public boolean isOffCenterCore() {
        return layered.isOffCenterCoreAllowed();
    }

    /** 底层层图实现（诊断/四向探测用）。 */
    public LayeredReactorStructure layered() {
        return layered;
    }

    /**
     * <b>委托自检</b>（启动时调一次）—— 专防"包装类漏转发接口 default 方法"。
     *
     * <p>为什么值得专门写一条断言：{@link ReactorStructure#cells()} 是<b>default 方法</b>，
     * 包装类忘了转发它<b>照样编译通过</b>，只是运行时拿到空表 ——
     * 表现为"结构检测一切正常，但投影永远开不起来"，本次实测就是这么踩的
     * （详见 {@link #cells} 的注释）。断言写在这里，下次漏了就<b>启动即炸</b>。
     */
    public void verifyDelegation() {
        int inner = layered.cells().size();
        int outer = cells().size();
        if (inner != outer) {
            throw new IllegalStateException("赛钱箱结构包装类漏转发 cells()："
                    + "层图侧 " + inner + " 格，本类 " + outer + " 格（投影会因此完全不可用）");
        }
        if (outer > 0 && !supportsProjection()) {
            throw new IllegalStateException("赛钱箱结构 supportsProjection() 与 cells() 不一致");
        }
    }

    /**
     * <b>诊断用</b>：把内部状态打出来（层图尺寸 / 落点表条数 / 构件数 / 木桩数）。
     *
     * <p>存在理由：投影这条链路上"落点表为空"会让投影静默拒绝，而原因可能藏在
     * 层图、legend 或缓存实例里 —— 一行内部状态比逐层加日志快得多。
     */
    public String debugState() {
        int[] s = layered.size();
        return "class=" + layered.getClass().getName()
                + " 尺寸=" + s[0] + "x" + s[1] + "x" + s[2]
                + " cells=" + layered.cells().size()
                + " partCount=" + layered.partCount()
                + " posts=" + postOffsets.size()
                + " symmetric=" + layered.isSymmetric()
                + " corePos=" + java.util.Arrays.toString(layered.corePosition());
    }

    /** 逐行诊断：尺寸 / 构件数 / 木桩偏移与编号 / 对称性。 */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        int[] s = size();
        out.add("实现=" + NAME + "  尺寸 " + s[0] + "x" + s[1] + "x" + s[2]
                + "  构件 " + partCount() + " 个（不含核心与空气格）");
        out.add("核心偏心=" + isOffCenterCore() + "（层图里 C 在第 "
                + (layered.corePosition()[0] + 1) + " 层第 " + (layered.corePosition()[1] + 1)
                + " 行第 " + (layered.corePosition()[2] + 1) + " 列）"
                + "  四向对称=" + layered.isSymmetric());
        for (int i = 0; i < postOffsets.size(); i++) {
            int[] o = postOffsets.get(i);
            out.add("  木桩 #" + i + "  层图偏移 (dx=" + o[0] + ", dy=" + o[1] + ", dz=" + o[2] + ")");
        }
        out.add("  编号规则：先 +X 正方向、后 +Z 正方向（dx 升序，dx 相同再按 dz 升序）");
        return out;
    }
}
