package com.example.touhou.core;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * 多方块结构检测的**可替换接口**。
 *
 * <p>Spec 里结构"具体暂定"，所以这里刻意做成两层：
 * <ul>
 *   <li>{@link ReactorStructure} —— 契约。核心机器只依赖它，不关心结构长什么样；</li>
 *   <li>{@link LayeredReactorStructure} —— 默认实现，读 config.yml 的层图；</li>
 *   <li>{@link #ALWAYS_OK} —— 调试用：永远完整（想先单独测发电与 GUI 时切到它）。</li>
 * </ul>
 * 换结构只需要换一个实现（或在 config.yml 里改层图），**不用碰核心机器一行代码**。
 *
 * <p>检测判据与扫描工具完全一致：查该坐标的 Slimefun 方块数据（{@code BlockStorage.checkID}），
 * 而不是看材质 —— 同材质下只有数据能区分原版方块与粘液方块
 * （见 {@code slimefun-multiblock-dev/references/08-region-scan-verified.md}）。
 */
public interface ReactorStructure {

    /**
     * 结构朝向 —— 绕纵轴 90° 的四个方向。
     *
     * <p>参照 LogiTech 多方块引擎的 {@code Direction}：schema（层图）里的偏移是
     * <b>相对量</b>，检测时先按朝向旋转、再加到核心坐标上。
     *
     * <p>关于序号：LogiTech 的 {@code fromInt} 是 0=NORTH 1=EAST 2=SOUTH 3=WEST，
     * 但它的 {@code values()} 顺序是 NORTH, WEST, SOUTH, EAST —— 两者的 1/3 是错位的。
     * 本工程把「序号」与「探测顺序」统一成同一个顺序，避免那种错位：
     * <pre>
     *   0 = NORTH   1 = EAST   2 = SOUTH   3 = WEST
     * </pre>
     * 于是 {@code values()[i].ordinal() == i}，存进方块数据的数字与枚举序号永远一致。
     */
    enum Direction {
        /** -Z。 */
        NORTH,
        /** +X。 */
        EAST,
        /** +Z。 */
        SOUTH,
        /** -X。 */
        WEST;

        /**
         * 把层图里的偏移分量 {@code (dx, dz)} 旋转到本朝向对应的世界偏移。
         *
         * <p>层图的语义：{@code dx} 沿 +X、{@code dz} 沿 +Z（行号越大 z 越大）。
         * NORTH 视为"不旋转"的基准态，其余方向在水平面内顺时针各转 90°：
         * <pre>
         *   NORTH (0°):   ( dx,  dz)
         *   EAST  (90°):  (-dz,  dx)
         *   SOUTH (180°): (-dx, -dz)
         *   WEST  (270°): ( dz, -dx)
         * </pre>
         * 返回值放在数组 {worldDx, worldDz} 里。
         *
         * <p>★ 刻意做成"返回新数组"而不是原地改传入对象 ——
         * LogiTech 的 {@code Direction#rotate(Vector)} <b>会原地修改参数</b>，
         * 导致它的 schema 必须每次 {@code clone()}，是个已知的坑（见 multiblock skill
         * `03-authoring-a-new-type.md`）。这里从签名上就避免掉。
         */
        public int[] rotate(int dx, int dz) {
            return switch (this) {
                case NORTH -> new int[] {dx, dz};
                case EAST -> new int[] {-dz, dx};
                case SOUTH -> new int[] {-dx, -dz};
                case WEST -> new int[] {dz, -dx};
            };
        }

        /** 序号 → 朝向（越界回落到 NORTH）。 */
        public static Direction fromInt(int i) {
            Direction[] all = values();
            return i >= 0 && i < all.length ? all[i] : NORTH;
        }

        /** 简短标签（日志/诊断用）。 */
        public String label() {
            return name() + "(" + ordinal() + ")";
        }
    }

    /** 结构名（写进报告/日志）。 */
    String name();

    /**
     * 检测以 {@code core} 为原点的结构是否完整。
     *
     * <p>⚠ 会读世界方块，必须在<b>主线程</b>调用（本体的 {@code EnergyNet} tick 与
     * {@code BlockTicker} 都是主线程，所以正常路径天然安全）。
     *
     * @return 检测结果（含缺失/多余的诊断信息，以及命中的朝向）
     */
    Result check(Location core);

    /**
     * <b>优先按已知朝向检测</b>（参照 LogiTech 的 {@code mb-dir} 重连路径）。
     *
     * <p>LogiTech 在 {@code acceptCoreRequest} 的重连分支里直接用
     * {@code Direction.getDirection(loc)} 读回落盘的朝向，再
     * {@code genMultiBlockFrom(loc, dir, hasPrevRecord=true)} —— <b>不重新试四个方向</b>。
     * 本方法的语义与它一致：先按 {@code preferred} 校验，不匹配再退回 {@link #check(Location)}
     * 的全向探测。
     *
     * <p>★ 收益：结构一旦成立，朝向就落盘了。此后每次校验都只跑 1 遍层图
     * （而不是"最坏 4 遍 + 每遍 125 格"）。不完整的结构会回退到全向探测，
     * 所以"玩家转向重建"这种场景照样能认出来。
     *
     * @param preferred 上次命中的朝向；{@code null} 等价于 {@link #check(Location)}
     */
    default Result check(Location core, Direction preferred) {
        return check(core);
    }

    /**
     * <b>这套结构在当前朝向下都占哪些格</b>（不含核心自己那一格）。
     *
     * <p>这是照搬 LogiTech {@code MultiBlockHandler.createHandler} 的关键一步：
     * 它 {@code for (i < size)} 遍历 schema 的每一格，
     * {@code core.clone().add(type.getStructurePart(i))} 算出零件坐标，
     * 然后给每格写 uuid + status —— <b>全程线性，不做任何搜索</b>。
     *
     * <p>对应的 {@code destroy()} 也用它来逐格清理。我们用它做同样两件事：
     * 结构成立时给所有构件盖章（归属凭据），结构拆除时逐格撤销。
     *
     * @return 构件位置列表（顺序稳定，便于对照报告）
     */
    default List<Location> partLocations(Location core, Direction direction) {
        return List.of();
    }

    // ---------------------------------------------------------------- 投影（照搬 LogiTech 全息）

    /**
     * schema 里的<b>一格</b>（相对核心、<b>未旋转</b>的层图偏移 + 那一格要什么）。
     *
     * <p>这是给「多方块投影 / 全息预览」用的最小数据面 —— 与 LogiTech
     * {@code AbstractMultiBlockType#getSchemaPart(i) / getSchemaPartId(i)} 的
     * 二元组一一对应，只是把「按序号取第 i 格」换成了「一次拿到整张表」：
     * 那边 {@code createHologram} 是 {@code for (i < getSchemaSize())} 逐格查，
     * 我们这边结构本来就有一份层图，直接列出来更省事，语义完全一致。
     *
     * <p>★ 朝向<b>不</b>在这里旋转：参照 LogiTech 的做法，旋转发生在画的那一步
     * （{@code direction.rotate(type.getSchemaPart(i))}），用的必须是
     * <b>与结构校验同一个</b> {@link Direction#rotate(int, int)} ——
     * 两处一旦各写一套旋转，投影就会画到与校验不同的位置上去。
     *
     * @param dx 层图 x 偏移（相对核心那一格，未旋转）
     * @param dy 层图 y 偏移（层号 − 核心所在层号）
     * @param dz 层图 z 偏移（相对核心那一格，未旋转）
     * @param id 这一格要求的 part id；空气要求格是 {@code "nu"}
     */
    record Cell(int dx, int dy, int dz, String id) {
    }

    /**
     * <b>整张 schema 的落点表</b>（不含核心自己那一格，未旋转）。
     *
     * <p>顺序 = 层图扫描顺序（y → z → x），稳定可复现，便于与诊断报告逐行对照。
     *
     * <p>★ 为什么做成 {@code default} 返回空表：接口的既有实现
     * （{@link ALWAYS_OK}、以及将来可能出现的"自定义结构"）不必被迫实现它 ——
     * 拿不到落点表的实现就是"不支持投影"，{@link MultiBlockProjection} 会如实拒绝，
     * 而不是画出个空壳。**这是纯增量扩展，结构校验的既有语义一字未改。**
     */
    default List<Cell> cells() {
        return List.of();
    }

    /**
     * <b>这套结构能不能投影</b>。
     *
     * <p>判据只有一条"有没有落点表"，刻意不去猜具体类型 ——
     * 将来任何新结构只要实现了 {@link #cells()}，投影就能直接用，
     * 不需要在投影那一侧加任何判断（详见 {@link MultiBlockProjection} 的类注释）。
     */
    default boolean supportsProjection() {
        return !cells().isEmpty();
    }

    /**
     * 结构是否<b>绕纵轴四向旋转不变</b>。
     *
     * <p>与 LogiTech 的 {@code isSymmetric} 同义，默认 {@code false}（= 老实按朝向画）。
     * 投影那边会照搬 LogiTech 的策略：为 {@code true} 时朝向<b>强制按 NORTH 处理</b>
     * （形状转不转都一样，画 NORTH 最省事也最不容易错）。
     */
    default boolean isSymmetric() {
        return false;
    }

    /**
     * 人类可读坐标（与 {@link #fmt} 同一套写法，但只收一个 Location）。
     *
     * <p>给诊断命令用：{@link #fmt} 要四个参数（world + 三个 int），
     * 命令里往往只有一个 Location。
     */
    static String xyz(Location loc) {
        if (loc == null) {
            return "(null)";
        }
        return (loc.getWorld() == null ? "?" : loc.getWorld().getName())
                + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    /**
     * <b>只取"真构件"</b>的落点（过滤掉 {@code nu} 这类"必须空气"的格子）。
     *
     * <p>★ 为什么必须有这一步：一张 5×5×5 的层图里 air 格往往比构件还多
     * （反应堆 125 格里有 27 格是空气要求），逐格生成 {@code ItemDisplay}
     * 既浪费实体额度，玩家看到的也只是一堆透明空壳。
     * LogiTech 那边是"映射不到就 {@code setItemStack(null)}"，我们更进一步：
     * <b>空气格连实体都不生成</b>（详见 {@link MultiBlockProjection} 的类注释）。
     */
    static List<Cell> solidCells(List<Cell> cells) {
        if (cells == null || cells.isEmpty()) {
            return List.of();
        }
        List<Cell> out = new ArrayList<>(cells.size());
        for (Cell c : cells) {
            if (c == null || c.id() == null || isAirRequirement(c.id())) {
                continue;
            }
            out.add(c);
        }
        return out;
    }

    /** 这个 part id 是不是"要求这里是空气"（与层图实现的判据同源）。 */
    static boolean isAirRequirement(String want) {
        return "nu".equals(want) || "AIR".equals(want)
                || "CAVE_AIR".equals(want) || "VOID_AIR".equals(want);
    }

    /**
     * <b>这套结构自己怎么投影</b>（可选）。
     *
     * <p>默认返回 {@code null} = "结构自己不管投影这件事"，此时投影那一侧会用
     * {@link MultiBlockProjection#projectorOf} 补一个<b>通用</b>的
     * （朝向取已落盘值，图标按 part id 自动解析成粘液物品 / 原版材质）。
     *
     * <p>★ 于是扩展是纯增量的：<b>什么都不用做</b>的结构照样能被投影，
     * 只是图标是"自动猜"的；想让图标更讲究（例如发光、区分变体）才实现它。
     */
    default Projector projector() {
        return null;
    }

    /**
     * <b>"这台机器怎么投影"</b> —— 把结构本身与"该显示什么图标"配成一对。
     *
     * <p>为什么要多这一层（而不是让结构自己带图标映射）：同一套层图可能被多个核心复用，
     * 而图标是<b>物品</b>层面的事（反应堆的图标在 {@code AddItems} 里，
     * 结构类不该反过来依赖物品注册表）。所以拆成两半：
     * <pre>
     *   结构  →  {@link #cells()} + {@link #check}（"长什么样 / 怎么验"）
     *   宿主  →  {@link #structure()} + {@link #displayMapping}（"用哪套结构 / 显示什么"）
     * </pre>
     * 新加一种多方块要开投影时，把这两半接上即可（最小步骤见
     * {@link MultiBlockProjection} 的类注释）。
     */
    interface ProjectionHost {

        /** 这台机器用的结构（每次现取：配置热重载后可能换了一份）。 */
        ReactorStructure structure();

        /**
         * {@code partId → 显示物品} 映射（LogiTech 的 {@code getIdMappingDisplayUse()}）。
         *
         * <p>★ 返回类型刻意是 {@link java.util.HashMap}：LogiTech 那个坑
         * （{@code createHologram} 第 4 参是 {@code HashMap} 而不是 {@code Map}，
         * 而 {@code Map.copyOf} 的结果喂不进去）在签名上就消掉了。
         *
         * @param direction 这次投影将要使用的朝向（对称结构时已被强制成 NORTH）；
         *                  用不到的宿主直接忽略它
         */
        java.util.HashMap<String, org.bukkit.inventory.ItemStack> displayMapping(Direction direction);

        /** 组一个宿主。 */
        static ProjectionHost of(ReactorStructure structure,
                                 java.util.function.Function<Direction, java.util.HashMap<String,
                                         org.bukkit.inventory.ItemStack>> mapping) {
            return new ProjectionHost() {
                @Override
                public ReactorStructure structure() {
                    return structure;
                }

                @Override
                public java.util.HashMap<String, org.bukkit.inventory.ItemStack> displayMapping(
                        Direction direction) {
                    return mapping.apply(direction);
                }
            };
        }
    }

    /**
     * 投影所需的两个能力（朝向 + 图标映射）。
     *
     * <p>刻意做成<b>极小的接口 + 一个静态工厂</b>：将来新增任何一种多方块结构，
     * 只要实现 {@link ReactorStructure#cells()} 与 {@link #projector()}
     * 就能开投影，{@link MultiBlockProjection} 一行都不用动。
     */
    interface Projector {

        /**
         * 这次投影要用的朝向。
         *
         * @return 朝向；{@code null} = 交给投影那一侧按 NORTH 兜底
         */
        Direction projectionDirection();

        /**
         * {@code partId → 显示物品} 映射（LogiTech 的 {@code getIdMappingDisplayUse()}）。
         *
         * <p>★ 返回类型刻意是 {@link java.util.HashMap}：LogiTech 那个坑
         * （第 4 参是 {@code HashMap} 而不是 {@code Map}）在签名上就消掉了 ——
         * 调用方永远不必想"要不要 {@code new HashMap<>(map)}"。
         */
        java.util.HashMap<String, org.bukkit.inventory.ItemStack> displayMapping();

        /** 便利工厂：一次给出两个能力。 */
        static Projector of(Direction direction, java.util.HashMap<String,
                org.bukkit.inventory.ItemStack> mapping) {
            final Direction dir = direction;
            final java.util.HashMap<String, org.bukkit.inventory.ItemStack> map = mapping;
            return new Projector() {
                @Override
                public Direction projectionDirection() {
                    return dir;
                }

                @Override
                public java.util.HashMap<String, org.bukkit.inventory.ItemStack> displayMapping() {
                    return map;
                }
            };
        }
    }

    /** 检测结果。 */
    final class Result {

        private static final Result OK = new Result(true, "结构完整", List.of(), List.of(), null);

        private final boolean complete;
        private final String summary;
        private final List<String> missing;
        private final List<String> wrong;
        private final Direction direction;

        public Result(boolean complete, String summary, List<String> missing, List<String> wrong) {
            this(complete, summary, missing, wrong, null);
        }

        public Result(boolean complete, String summary, List<String> missing, List<String> wrong,
                      Direction direction) {
            this.complete = complete;
            this.summary = summary;
            this.missing = missing;
            this.wrong = wrong;
            this.direction = direction;
        }

        public static Result ok(int partCount) {
            return new Result(true, "结构完整（" + partCount + " 个构件）", List.of(), List.of(), null);
        }

        /** 带朝向的成功结果。 */
        public static Result ok(int partCount, Direction direction) {
            return new Result(true,
                    "结构完整（" + partCount + " 个构件，朝向 " + direction.label() + "）",
                    List.of(), List.of(), direction);
        }

        public static Result of(boolean complete, String summary, List<String> missing, List<String> wrong) {
            return new Result(complete, summary, missing, wrong, null);
        }

        public static Result of(boolean complete, String summary, List<String> missing,
                                List<String> wrong, Direction direction) {
            return new Result(complete, summary, missing, wrong, direction);
        }

        public boolean isComplete() {
            return complete;
        }

        public String summary() {
            return summary;
        }

        /** 该放东西但没放（或 id 不对）的位置。 */
        public List<String> missing() {
            return missing;
        }

        /** 该是某个东西但实际是另一个的位置。 */
        public List<String> wrong() {
            return wrong;
        }

        /**
         * 命中的朝向（结构完整时有值；不完整时为 {@code null}）。
         *
         * <p>用于存进方块数据，让重启后的重连直接按已知朝向校验，不必再试四个方向
         * （与 LogiTech 的 {@code mb-dir} 同一思路）。
         */
        public Direction direction() {
            return direction;
        }
    }

    /** 永远完整：调试发电/GUI 时用，免得每次都要搭结构。 */
    ReactorStructure ALWAYS_OK = new ReactorStructure() {
        @Override
        public String name() {
            return "ALWAYS_OK(调试用)";
        }

        @Override
        public Result check(Location core) {
            return Result.ok(0);
        }
    };

    // ---------------------------------------------------------------- 工具

    /**
     * 取某坐标的"part id"：粘液方块 → 它的 sfId；原版方块 → {@code Material.toString()}；空气 → {@code "nu"}。
     *
     * <p>与扫描工具里的 {@code RuntimePartIdResolver} 同一条判据。
     */
    static String partIdAt(World world, int x, int y, int z) {
        Location loc = new Location(world, x, y, z);
        String sfId = BlockStorage.checkID(loc);
        if (sfId != null) {
            return sfId;
        }
        Block block = loc.getBlock();
        return block.getType().isAir() ? "nu" : block.getType().toString();
    }

    /** 人类可读的坐标。 */
    static String fmt(World world, int x, int y, int z) {
        return "(" + x + "," + y + "," + z + ")";
    }

    /** 公开的"某坐标是什么"查询 —— 调试命令用（与 {@link #partIdAt} 同一判据）。 */
    static String describePartAt(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "无效坐标";
        }
        return fmt(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ())
                + " = " + partIdAt(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
    }
}
