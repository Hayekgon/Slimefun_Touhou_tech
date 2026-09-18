package com.example.touhou.core;

import com.example.touhou.Touhou;
import com.example.touhou.core.AddonConfig;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 默认的多方块结构实现：**按"逐层矩阵"检测**。
 *
 * <p>层图写在 config.yml 的 {@code reactor.structure.layers} 里，每层是若干个等长字符串
 * （一个字符串 = 一"行"，在世界里沿 z 轴延伸；行内每个字符沿 x 轴延伸）。
 * 层与层之间沿 <b>y 轴从下往上</b>排列，核心必须落在其中一层的正中。
 *
 * <pre>
 * layers:
 *   - - "FFF"     # 最底层
 *     - "FCF"
 *     - "FFF"
 *   - - "FFF"     # 中间层
 *     - "FFF"
 *     - "FFF"
 *   - - "FFF"     # 最上层
 *     - "FFF"
 *     - "FFF"
 * </pre>
 *
 * <p>坐标约定（与 LogiTech schema 一致）：核心是 {@code (0,0,0)}，且
 * <pre>
 *   x = 列号 − 核心所在列      z = 行号 − 核心所在行      y = 层号 − 核心所在层号
 * </pre>
 * 于是"含有 C 的那一层"就是核心所在层，{@code C} 自己落在 {@code (0,0,0)}。
 * {@link ReactorStructure#check(Location)} 只收一个核心坐标就能还原整套结构，
 * 靠的就是这套约定。
 *
 * <p>★★ <b>核心可以不在层图正中</b>（{@link #allowOffCenterCore}）：
 * 坐标换算一律以 {@code C} 自己那一格为原点，所以 C 落在层图哪一格都成立 ——
 * 反应堆的 5×5×5 层图里 C 恰好在正中，于是 {@code coreCol == width/2}、
 * {@code coreRow == depth/2}，换算结果与"按半宽偏移"的老写法<b>逐格等价</b>
 * （这是本类扩展时唯一不能错的地方，见 {@link #checkOneDirection}）。
 * 之所以还要留一个开关：老代码里"核心必须居中"是<b>写层图时的防错断言</b>，
 * 手滑把 C 写偏会让整座结构整体错位、报错信息却只说"某格应为 X 实际 Y"。
 * 默认仍是"必须居中"（反应堆行为一字不改），只有明确要求允许偏心核心的
 * 结构（赛钱箱：9×6×9 的层图里 C 在最右一列）才传 {@code true}。
 *
 * <p>★ 换结构只改 config.yml 的层图即可；要换成"完全自定义"的结构
 * （例如用扫描工具生成的那份 schema），实现 {@link ReactorStructure}
 * 再换掉 {@link ReactorManager} 里注入的实例，核心机器代码一行都不用动。
 */
public final class LayeredReactorStructure implements ReactorStructure {

    /** 核心保留字符：代表多方块核心自己那一格，不需要检测。 */
    public static final char CORE = 'C';

    private final String structureName;
    /** [层][行] -> 该行字符串。 */
    private final List<List<String>> layers;
    private final Map<Character, String> legend;
    /**
     * "整个结构里最多出现一个"的 part id 集合（空集 = 不限制）。
     *
     * <p>★ 从"单个 part"改成"一组 part"的原因：IO 接口拆成了**输入接口**与**输出接口**
     * 两个物品，它们<b>都要</b>能出现在同一座反应堆里（一个管进、一个管出），
     * 但**各自**仍然只能有一个（多了说不清 Cargo 该认哪个口）。
     * 用单个 partId 表达不了"这两个各自唯一"，所以改成集合。
     */
    private final Set<String> uniqueParts;

    /**
     * 结构是否<b>绕纵轴四向旋转不变</b>。
     *
     * <p>参照 LogiTech 的 {@code isSymmetric}：为 {@code true} 时只在 NORTH 探测一次
     * （省掉 3/4 次校验）。5×5×5 反应堆每层都是中心对称的，所以默认算出 {@code true}。
     *
     * <p>★ 这个值必须与结构实际对称性一致：说谎（实际不对称却设 true）会让结构在
     * 其它朝向下永远校验失败，而且报错信息只会说"某格应为 X 实际 Y"，极难定位。
     * 所以这里默认是**算出来的**（把层图转 90° 与原图逐格比对），
     * 也允许 config 显式覆盖。
     *
     * <p>★ 一次 90° 旋转不变即可推出四次都不变（四个朝向构成 4 阶循环群），
     * 所以只比对一次。
     */
    private final boolean symmetric;

    /** 核心在层图里的绝对位置。 */
    private final int coreLayer;
    private final int coreRow;
    private final int coreCol;

    /**
     * 是否允许核心不在层图正中（默认 {@code false} = 保持"必须居中"的防错断言）。
     *
     * <p>★ 这个开关<b>只</b>控制构造期那条断言；坐标换算本身早就以核心为原点，
     * 所以打开它不会带来任何新的坐标路径（详见类注释与
     * {@link #checkOneDirection} 的换算说明）。
     */
    private final boolean allowOffCenterCore;

    private final int width;    // x 方向长度（列数）
    private final int depth;    // z 方向长度（行数）
    private final int levels;   // y 方向长度（层数）

    public LayeredReactorStructure(String structureName, List<List<String>> layers,
                                   Map<Character, String> legend) {
        this(structureName, layers, legend, Set.of());
    }

    public LayeredReactorStructure(String structureName, List<List<String>> layers,
                                   Map<Character, String> legend, Set<String> uniqueParts) {
        this(structureName, layers, legend, uniqueParts, null);
    }

    /**
     * 完整构造器（核心必须居中 —— 反应堆走的是这一条）。
     *
     * @param forceSymmetric {@code null} = 自动算（推荐）；{@code TRUE/FALSE} = 强制指定
     */
    public LayeredReactorStructure(String structureName, List<List<String>> layers,
                                   Map<Character, String> legend, Set<String> uniqueParts,
                                   Boolean forceSymmetric) {
        this(structureName, layers, legend, uniqueParts, forceSymmetric, false);
    }

    /**
     * 完整构造器 + 偏心核心开关。
     *
     * @param allowOffCenterCore {@code true} = 允许 {@code C} 不在层图正中
     *                           （赛钱箱的 9×6×9 层图里核心在最右一列）
     */
    public LayeredReactorStructure(String structureName, List<List<String>> layers,
                                   Map<Character, String> legend, Set<String> uniqueParts,
                                   Boolean forceSymmetric, boolean allowOffCenterCore) {
        this.structureName = structureName;
        this.layers = layers;
        this.legend = new LinkedHashMap<>(legend);
        this.allowOffCenterCore = allowOffCenterCore;
        Set<String> cleaned = new LinkedHashSet<>();
        if (uniqueParts != null) {
            for (String p : uniqueParts) {
                if (p != null && !p.isBlank()) {
                    cleaned.add(p.trim());
                }
            }
        }
        this.uniqueParts = Collections.unmodifiableSet(cleaned);

        if (layers.isEmpty()) {
            throw new IllegalArgumentException("结构层图不能为空");
        }
        this.levels = layers.size();

        // 行数必须逐层一致
        int rows = layers.get(0).size();
        if (rows == 0) {
            throw new IllegalArgumentException("结构层图的第一层是空的");
        }
        for (int y = 0; y < levels; y++) {
            if (layers.get(y).size() != rows) {
                throw new IllegalArgumentException("第 " + y + " 层的行数是 " + layers.get(y).size()
                        + "，但第一层是 " + rows + "；每层行数必须一致");
            }
        }
        this.depth = rows;

        // 每行长度必须一致
        int cols = layers.get(0).get(0).length();
        if (cols == 0) {
            throw new IllegalArgumentException("结构层图的第一行是空的");
        }
        for (int y = 0; y < levels; y++) {
            for (int z = 0; z < depth; z++) {
                String row = layers.get(y).get(z);
                if (row.length() != cols) {
                    throw new IllegalArgumentException("第 " + y + " 层第 " + z + " 行长度是 "
                            + row.length() + "，但第一行是 " + cols + "；每行长度必须一致");
                }
            }
        }
        this.width = cols;

        // 定位核心：必须恰好一个 C，且左右/前后都居中（否则偏移会歪）
        int foundLayer = -1;
        int foundRow = -1;
        int foundCol = -1;
        for (int y = 0; y < levels; y++) {
            for (int z = 0; z < depth; z++) {
                int col = layers.get(y).get(z).indexOf(CORE);
                if (col < 0) {
                    continue;
                }
                if (foundLayer >= 0) {
                    throw new IllegalArgumentException("结构层图里出现了多个核心字符 '" + CORE
                            + "'（第 " + foundLayer + " 层与第 " + y + " 层）；核心只能有一个");
                }
                foundLayer = y;
                foundRow = z;
                foundCol = col;
            }
        }
        if (foundLayer < 0) {
            throw new IllegalArgumentException("结构层图里没有核心字符 '" + CORE
                    + "'；请把它放在核心所在的那一格");
        }
        this.coreLayer = foundLayer;
        this.coreRow = foundRow;
        this.coreCol = foundCol;

        if (foundCol * 2 != width - 1 && !allowOffCenterCore) {
            throw new IllegalArgumentException("核心在层图里必须左右居中：核心列=" + foundCol
                    + "，每行宽=" + width
                    + "（确有必要让核心偏心时，构造时传 allowOffCenterCore=true）");
        }
        if (foundRow * 2 != depth - 1 && !allowOffCenterCore) {
            throw new IllegalArgumentException("核心在层图里必须前后居中：核心行=" + foundRow
                    + "，每层行数=" + depth
                    + "（确有必要让核心偏心时，构造时传 allowOffCenterCore=true）");
        }

        // 所有非核心字符都要在 legend 里登记
        for (int y = 0; y < levels; y++) {
            for (int z = 0; z < depth; z++) {
                String row = layers.get(y).get(z);
                for (int x = 0; x < row.length(); x++) {
                    char ch = row.charAt(x);
                    if (ch == CORE || Character.isWhitespace(ch)) {
                        continue;
                    }
                    if (!legend.containsKey(ch)) {
                        throw new IllegalArgumentException("结构层图第 " + y + " 层第 " + z
                                + " 行第 " + x + " 列用了未登记的字符 '" + ch
                                + "'；请在 structure.legend 里登记它");
                    }
                }
            }
        }

        this.symmetric = forceSymmetric != null ? forceSymmetric : computeSymmetric();
    }

    /**
     * 算出结构是否绕纵轴 90° 旋转不变。
     *
     * <p>做法：把每一层的字符矩阵<b>绕核心</b>旋转 90° 后与原矩阵逐格比对。
     * 旋转公式（行 z、列 x）：
     * <pre>
     *   相对偏移 (dx, dz) = (x − coreCol, z − coreRow)
     *   旋转后          = (−dz, dx)
     *   落回层图坐标     = (coreCol − dz, coreRow + dx)
     * </pre>
     * 只要有一格转出去落在层图矩形之外（偏心核心必然如此），就直接判为不对称。
     *
     * <p>★ 只比 90° 一次就够：四个朝向构成 4 阶循环群，
     * 一次 90° 不变即可推出四次都不变。
     *
     * <p>★ 核心居中时本式与旧写法 {@code rotated[z][x] = origin[n-1-x][z]}
     * <b>完全等价</b>（代入 {@code coreCol = coreRow = (n-1)/2} 即得），
     * 所以反应堆的对称性判定一个字都没变。
     *
     * <p>注意 legend 里的字符可能"等价"（例如保护罩与输入接口都在同一个标签里），
     * 但这里比的是<b>层图字符</b>而不是实际方块 —— 只要层图自己旋转不变就说明
     * "四个方向要的东西完全一样"，正是 {@code isSymmetric} 需要的语义。
     */
    private boolean computeSymmetric() {
        for (int y = 0; y < levels; y++) {
            List<String> layer = layers.get(y);
            for (int z = 0; z < depth; z++) {
                for (int x = 0; x < width; x++) {
                    int rx = coreCol - (z - coreRow);
                    int rz = coreRow + (x - coreCol);
                    if (rx < 0 || rx >= width || rz < 0 || rz >= depth) {
                        return false;       // 转出去就不在图里 —— 必然四向不同
                    }
                    if (layer.get(z).charAt(x) != layer.get(rz).charAt(rx)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * 结构是否四向旋转不变（config 可显式覆盖，见 {@link #withSymmetric}）。
     *
     * <p>★ 同时是 {@link ReactorStructure#isSymmetric()} 的实现 ——
     * 投影那边读的就是它：为 {@code true} 时朝向强制按 NORTH（与 LogiTech 一致）。
     */
    @Override
    public boolean isSymmetric() {
        return symmetric;
    }

    /** 是否允许核心不在层图正中（见 {@link #allowOffCenterCore}）。 */
    public boolean isOffCenterCoreAllowed() {
        return allowOffCenterCore;
    }

    /** 覆盖对称性判定（诊断/强制用；不传则用算出来的值）。 */
    public LayeredReactorStructure withSymmetric(boolean value) {
        return new LayeredReactorStructure(structureName, layers, legend, uniqueParts, value,
                allowOffCenterCore);
    }

    /** 从 config.yml 读取默认结构。 */
    public static LayeredReactorStructure fromConfig() {
        AddonConfig cfg = AddonConfig.get();
        return new LayeredReactorStructure("灵乌路空反应堆（层图）",
                cfg.structureLayers, cfg.structureLegend, cfg.structureUniqueParts,
                cfg.structureSymmetric);
    }

    /**
     * 这个 legend 值是不是"要求这里是空气"。
     *
     * <p>{@link ReactorStructure#partIdAt} 对空气返回 {@code "nu"}，所以 legend 里写 {@code nu}
     * 就是"必须空气"。这类格子要检测、但<b>不该算进"共需 N 个构件"</b> ——
     * 否则一个 5×5×5 容器会报成"共需 124 个构件"，玩家会以为要凑 124 个方块。
     */
    private static boolean isAirRequirement(String want) {
        return "nu".equals(want) || "AIR".equals(want)
                || "CAVE_AIR".equals(want) || "VOID_AIR".equals(want);
    }

    /** legend 值是不是"标签引用"（形如 {@code #touhou:reactor_shell}）。 */
    private static boolean isTag(String want) {
        return want != null && want.length() > 1 && want.charAt(0) == '#';
    }

    /** 去掉 {@code #} 前缀，得到标签名。 */
    private static String tagName(String want) {
        return want.substring(1);
    }

    /**
     * 该格是否满足 legend 的要求。
     *
     * <p>三种判据：{@code #标签} 走 {@link ItemTags}；其余与 part id / Material 名直接比字符串。
     */
    private static boolean matches(String want, String actual) {
        if (isTag(want)) {
            return ItemTags.has(tagName(want), actual);
        }
        return want.equals(actual);
    }

    @Override
    public String name() {
        return structureName;
    }

    /**
     * 检测结构是否完整 —— <b>四个朝向逐个试</b>（参照 LogiTech 的
     * {@code tryCreateMultiBlock}）。
     *
     * <p>探测顺序 = {@link Direction#values()} = NORTH, EAST, SOUTH, WEST。
     * 第一个"完全匹配"的朝向即命中，并把朝向放进 {@link Result#direction()}，
     * 供上层存进方块数据，重启后重连直接按已知朝向校验。
     *
     * <p>★ {@link #isSymmetric()} 为 true 时只试 NORTH 一个方向（省 3/4 次方块查询）——
     * 这是 LogiTech 的同款优化。语义前提是"结构绕纵轴 90° 旋转不变"，
     * 这个值默认由 {@link #computeSymmetric()} 从层图算出来，不会说谎。
     *
     * <p>★ 四个方向都不匹配时，返回**最接近**的那个方向的诊断明细
     * （缺失/错误处最少），而不是第一个方向的 —— 玩家按原样搭好却没有朝向可言时，
     * 报错信息越贴近现实越好用。
     */
    @Override
    public Result check(Location core) {
        World world = core.getWorld();
        if (world == null) {
            return Result.of(false, "核心不在任何世界", List.of(), List.of());
        }

        boolean debug = Boolean.getBoolean("touhou.debugStructure");
        if (debug) {
            StringBuilder dump = new StringBuilder();
            for (int i = 0; i < levels; i++) {
                dump.append(" [").append(i).append(": ").append(String.join("|", layers.get(i))).append(']');
            }
            Log.info("[MBSTRUCT] check core=("
                    + core.getBlockX() + "," + core.getBlockY() + "," + core.getBlockZ()
                    + ") size=" + width + "x" + levels + "x" + depth
                    + " coreLayer=" + coreLayer + " 需要 " + partCount() + " 个构件"
                    + " symmetric=" + symmetric + " 层图=" + dump);
        }

        // 对称结构只试 NORTH；否则四个方向依次试
        Direction[] attempts = symmetric
                ? new Direction[] {Direction.NORTH}
                : Direction.values();

        Direction best = null;
        List<String> bestMissing = null;
        List<String> bestWrong = null;
        int bestScore = Integer.MAX_VALUE;

        for (Direction dir : attempts) {
            DirResult r = checkOneDirection(core, dir, debug);
            if (r.complete) {
                if (debug) {
                    Log.info("[MBSTRUCT] 命中朝向 " + dir.label()
                            + "（构件 " + r.expected + " 个）");
                }
                return Result.ok(r.expected, dir);
            }
            int score = r.missing.size() + r.wrong.size();
            if (debug) {
                Log.info("[MBSTRUCT] 朝向 " + dir.label()
                        + " 不匹配：缺 " + r.missing.size() + " 错 " + r.wrong.size());
            }
            if (score < bestScore) {
                bestScore = score;
                best = dir;
                bestMissing = r.missing;
                bestWrong = r.wrong;
            }
        }

        // 全部失败：用最接近的那个方向的明细，并提示"试过哪些方向"
        String tried = symmetric
                ? "（结构四向对称，只试了 NORTH）"
                : "（已尝试 NORTH/EAST/SOUTH/WEST 四个朝向）";
        return Result.of(false,
                "结构不完整：缺 " + bestMissing.size() + " 处、错 " + bestWrong.size()
                        + " 处" + tried + "，最接近的朝向是 " + best.label(),
                bestMissing, bestWrong, null);
    }

    /** 单个朝向的检测结果（内部用）。 */
    private static final class DirResult {
        boolean complete;
        int expected;
        List<String> missing = new ArrayList<>();
        List<String> wrong = new ArrayList<>();
    }

    /**
     * 按指定朝向检测一遍。
     *
     * <p>坐标换算：层图偏移 {@code (dx, dz)} <b>以核心那一格为原点</b>，
     * 先经 {@link Direction#rotate} 旋转，再加到核心坐标上：
     * <pre>
     *   dx = x - coreCol ;  dz = z - coreRow
     *   {wdx, wdz} = dir.rotate(dx, dz)
     *   世界坐标 = (cx + wdx, cy + (y - coreLayer), cz + wdz)
     * </pre>
     *
     * <p>★ 核心居中时 {@code coreCol == width/2}、{@code coreRow == depth/2}，
     * 上式与原来的"减半宽"写法逐格相同 —— 反应堆的检测结果不变；
     * 核心偏心时（赛钱箱）只有这个写法才给出正确的世界偏移。
     * 这也是 {@link #partLocations} 必须与这里保持一致的唯一原因：
     * 两处一旦漂移，"盖章"和"校验"就会指向不同的格子。
     */
    private DirResult checkOneDirection(Location core, Direction dir, boolean debug) {
        World world = core.getWorld();
        int cx = core.getBlockX();
        int cy = core.getBlockY();
        int cz = core.getBlockZ();

        DirResult out = new DirResult();
        int printed = 0;
        Map<String, Integer> uniqueFound = new HashMap<>();

        for (int y = 0; y < levels; y++) {
            int dy = y - coreLayer;
            for (int z = 0; z < depth; z++) {
                String row = layers.get(y).get(z);
                for (int x = 0; x < width; x++) {
                    char ch = row.charAt(x);
                    if (ch == CORE || Character.isWhitespace(ch)) {
                        continue;                       // 核心自己那格不查
                    }
                    String want = legend.get(ch);
                    if (want == null) {
                        continue;
                    }
                    boolean air = isAirRequirement(want);
                    if (!air) {
                        out.expected++;                 // 只有真正的"构件"才计数
                    }
                    int[] rot = dir.rotate(x - coreCol, z - coreRow);
                    int wx = cx + rot[0];
                    int wy = cy + dy;
                    int wz = cz + rot[1];
                    String actual = ReactorStructure.partIdAt(world, wx, wy, wz);
                    if (!uniqueParts.isEmpty() && uniqueParts.contains(actual)) {
                        uniqueFound.merge(actual, 1, Integer::sum);
                    }
                    if (matches(want, actual)) {
                        if (debug && printed < 3) {
                            Log.info("[MBSTRUCT]   OK  '" + ch + "' @ ("
                                    + wx + "," + wy + "," + wz + ") = " + actual);
                            printed++;
                        }
                        continue;
                    }
                    if (debug) {
                        Log.info("[MBSTRUCT]   BAD '" + ch + "' @ ("
                                + wx + "," + wy + "," + wz + ") want=" + want + " actual=" + actual);
                    }
                    String at = ReactorStructure.fmt(world, wx, wy, wz);
                    if (air) {
                        out.wrong.add(at + " 应保持空气，实际 " + actual);
                    } else if ("nu".equals(actual)) {
                        out.missing.add(at + " 缺 " + want);
                    } else {
                        out.wrong.add(at + " 应为 " + want + "，实际 " + actual);
                    }
                }
            }
        }

        // ★ "各自最多一个"的构件（输入接口 / 输出接口）：
        //   它们都能替代保护罩，且一座反应堆里可以【同时】有一个输入口和一个输出口
        //   （一个管进、一个管出）；但同一类型多了就说不清 Cargo 该认哪个口。
        for (Map.Entry<String, Integer> e : uniqueFound.entrySet()) {
            if (e.getValue() > 1) {
                out.wrong.add("本结构里 " + e.getKey() + " 出现了 " + e.getValue()
                        + " 个，但该类构件最多只能有 1 个");
            }
        }

        out.complete = out.missing.isEmpty() && out.wrong.isEmpty();
        return out;
    }

    /** 结构尺寸 {x, y, z}。 */
    public int[] size() {
        return new int[] {width, levels, depth};
    }

    /**
     * <b>优先按已知朝向检测</b> —— 朝向命中就直接返回，否则退回全向探测。
     *
     * <p>参照 LogiTech：重连时它 {@code Direction.getDirection(loc)} 读回落盘朝向，
     * 再 {@code genMultiBlockFrom(loc, dir, true, ...)}，不重试四个方向。
     */
    @Override
    public Result check(Location core, Direction preferred) {
        if (preferred == null) {
            return check(core);
        }
        World world = core.getWorld();
        if (world == null) {
            return Result.of(false, "核心不在任何世界", List.of(), List.of());
        }
        boolean debug = Boolean.getBoolean("touhou.debugStructure");
        DirResult r = checkOneDirection(core, preferred, debug);
        if (r.complete) {
            if (debug) {
                Log.info("[MBSTRUCT] 按已落盘朝向 "
                        + preferred.label() + " 直接命中（构件 " + r.expected + " 个）");
            }
            return Result.ok(r.expected, preferred);
        }
        if (debug) {
            Log.info("[MBSTRUCT] 已落盘朝向 " + preferred.label()
                    + " 不再匹配（缺 " + r.missing.size() + " 错 " + r.wrong.size()
                    + "），回退为四向探测");
        }
        return check(core);
    }

    /**
     * 这套结构在指定朝向下占用的所有构件格（不含核心那一格）。
     *
     * <p>坐标换算与 {@link #checkOneDirection} 完全一致 —— 这是刻意的：
     * 两处一旦漂移，"盖章"和"校验"就会指向不同的格子，出现
     * "结构完整但构件没有归属凭据"这种自相矛盾的状态。
     */
    @Override
    public List<Location> partLocations(Location core, Direction direction) {
        World world = core.getWorld();
        if (world == null) {
            return List.of();
        }
        Direction dir = direction == null ? Direction.NORTH : direction;
        int cx = core.getBlockX();
        int cy = core.getBlockY();
        int cz = core.getBlockZ();

        List<Location> out = new ArrayList<>(Math.max(1, partCount()));
        for (int y = 0; y < levels; y++) {
            int dy = y - coreLayer;
            for (int z = 0; z < depth; z++) {
                String row = layers.get(y).get(z);
                for (int x = 0; x < width; x++) {
                    char ch = row.charAt(x);
                    if (ch == CORE || Character.isWhitespace(ch)) {
                        continue;                       // 核心自己那格不算构件
                    }
                    String want = legend.get(ch);
                    if (want == null || isAirRequirement(want)) {
                        continue;                       // 未登记字符 / "必须是空气"都不是构件
                    }
                    int[] rot = dir.rotate(x - coreCol, z - coreRow);
                    out.add(new Location(world, cx + rot[0], cy + dy, cz + rot[1]));
                }
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 构件偏移查询

    /**
     * <b>整张层图的落点表</b>（相对核心、未旋转）—— 投影/全息的数据来源。
     *
     * <p>坐标换算与 {@link #checkOneDirection} / {@link #partLocations} <b>同源</b>：
     * 都是 {@code (x − coreCol, y − coreLayer, z − coreRow)}。
     * ★ 这是本类第二次（也是刻意唯一的一次）暴露层图偏移：第一处是给"盖章/列举构件"的
     * {@link #partLocations}（它会过滤空气格），这里是给"投影"用的（<b>不过滤</b>，
     * 空气格也如实列出来，由投影那一侧决定画不画）。
     *
     * <p>★ 不过滤的理由：投影将来可能想用空气格做"这里必须是空的"提示，
     * 而过滤是纯展示决策，不该固化在结构里（{@link ReactorStructure#solidCells} 负责过滤）。
     */
    @Override
    public List<ReactorStructure.Cell> cells() {
        List<ReactorStructure.Cell> out = new ArrayList<>(width * depth * levels);
        for (int y = 0; y < levels; y++) {
            int dy = y - coreLayer;
            for (int z = 0; z < depth; z++) {
                String row = layers.get(y).get(z);
                for (int x = 0; x < width; x++) {
                    char ch = row.charAt(x);
                    if (ch == CORE || Character.isWhitespace(ch)) {
                        continue;                       // 核心自己那一格不进落点表
                    }
                    String want = legend.get(ch);
                    if (want == null) {
                        continue;                       // 构造期已校验过，这里只是兜底
                    }
                    out.add(new ReactorStructure.Cell(x - coreCol, dy, z - coreRow, want));
                }
            }
        }
        return out;
    }

    /**
     * <b>某一类构件的"层图偏移"列表</b>（相对核心，未旋转）。
     *
     * <p>返回 {@code {dx, dy, dz}} 三元组，顺序 = 层图扫描顺序（y → z → x）。
     *
     * <p>为什么需要它：赛钱箱要给 6 根木桩<b>按固定顺序编号</b>，
     * 而"编号顺序"是层图里的相对位置关系（先 +X、再 +Z），
     * 与整台机器在世界里的朝向无关 —— 所以编号必须从层图算，不能从世界坐标算。
     * 用世界坐标算的话，机器转个方向木桩就会重新编号，玩家刚认好的 0~5 全乱了。
     *
     * @param partId legend 里登记的 part id（例如木桩的 sfId）；
     *               legend 值写 {@code #标签} 的格子也认（按标签判定）
     * @return 每个匹配格一个 {@code {dx, dy, dz}}；没有匹配时返回空列表
     */
    public List<int[]> offsetsOf(String partId) {
        List<int[]> out = new ArrayList<>();
        if (partId == null || partId.isBlank()) {
            return out;
        }
        for (int y = 0; y < levels; y++) {
            for (int z = 0; z < depth; z++) {
                String row = layers.get(y).get(z);
                for (int x = 0; x < width; x++) {
                    char ch = row.charAt(x);
                    if (ch == CORE || Character.isWhitespace(ch)) {
                        continue;
                    }
                    String want = legend.get(ch);
                    if (want == null) {
                        continue;
                    }
                    boolean hit = isTag(want) ? ItemTags.has(tagName(want), partId) : want.equals(partId);
                    if (hit) {
                        out.add(new int[] {x - coreCol, y - coreLayer, z - coreRow});
                    }
                }
            }
        }
        return out;
    }

    /**
     * 同 {@link #offsetsOf}，但按 <b>先 +X 方向、后 +Z 方向</b>排序
     * （即 x 升序，x 相同再按 z 升序；y 不参与排序）。
     *
     * <p>这就是赛钱箱 6 根木桩 0~5 的编号规则 —— spec 原文
     * 「先 +X 正方向、后 +Z 正方向依次读取」，与游戏内扫描报告给出的
     * {@code (2,2,0) (2,2,8) (6,2,0) (6,2,8) (8,2,2) (8,2,6)} 完全一致
     * （按 x 升序、x 相同再按 z 升序排出来正是这个序列）。
     */
    public List<int[]> offsetsByXThenZ(String partId) {
        List<int[]> out = offsetsOf(partId);
        out.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[2], b[2]));
        return out;
    }

    /**
     * <b>诊断用</b>：无视 {@link #isSymmetric()}，强行把四个方向各检测一遍并报告结果。
     *
     * <p>为什么需要它：对称结构正常情况下只探 NORTH，于是"四向适配到底有没有生效"
     * 无法从常规检测结果看出来。这个方法把四个方向逐一跑一遍，直接给出证据
     * （每个方向各自"缺几处 / 错几处"）。
     *
     * <p>预期：对称结构在四个方向下应当<b>全部报完整</b>；
     * 不对称结构只会在"正确"的那个朝向报完整。
     *
     * @return 逐行报告，形如 {@code "NORTH(0) 完整"} 或 {@code "EAST(1) 缺 3 错 0"}
     */
    public java.util.List<String> probeAllDirections(Location core) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Direction dir : Direction.values()) {
            DirResult r = checkOneDirection(core, dir, false);
            out.add(String.format("%-10s %s", dir.label(),
                    r.complete ? "完整（构件 " + r.expected + " 个）"
                            : "缺 " + r.missing.size() + " 处 / 错 " + r.wrong.size() + " 处"));
        }
        out.add("本结构判定为四向对称 = " + symmetric
                + (symmetric ? "（常规检测只探 NORTH）" : "（常规检测会试 4 个方向）"));
        return out;
    }

    /** 核心在图里的位置 {layer, row, col}，供调试输出。 */
    public int[] corePosition() {
        return new int[] {coreLayer, coreRow, coreCol};
    }

    /** 需要检测的构件总数（不含核心，也不含"必须空气"的格子）。 */
    public int partCount() {
        int n = 0;
        for (List<String> layer : layers) {
            for (String row : layer) {
                for (int x = 0; x < row.length(); x++) {
                    char ch = row.charAt(x);
                    if (ch == CORE || Character.isWhitespace(ch)) {
                        continue;
                    }
                    String want = legend.get(ch);
                    if (want != null && !isAirRequirement(want)) {
                        n++;
                    }
                }
            }
        }
        return n;
    }
}
