package com.example.touhou.core;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * <b>多方块投影</b> —— 照搬 LogiTech 的全息机制（{@code MultiBlockService#createHologram}
 * + {@code DisplayGroup} + {@code ItemDisplayBuilder}）。
 *
 * <h2>它画的到底是什么</h2>
 * 不是粒子、也不是假方块，而是<b>原版实体</b>：
 * <pre>
 *   核心位置 + (0.5, 0.5, 0.5)
 *     └─ Interaction 父实体（碰撞箱 0.1 × 0.1，带 PDC 标记）
 *          ├─ ItemDisplay 子实体（第 1 格，位置 = 核心 + 旋转后的层图偏移，缩放 0.5）
 *          ├─ ItemDisplay 子实体（第 2 格）
 *          └─ ……
 * </pre>
 * 每一格显示"这里该放什么"：物品取自 {@code partId → ItemStack} 映射
 * （即 LogiTech 的 {@code getIdMappingDisplayUse()}）。
 *
 * <h2>★ 与 LogiTech 逐项对应</h2>
 * <table border="1">
 *   <caption>对应表</caption>
 *   <tr><th>LogiTech</th><th>本类</th><th>说明</th></tr>
 *   <tr><td>{@code MultiBlockService.createHologram}</td><td>{@link #render}</td>
 *       <td>同一步骤：父实体 → 逐格子实体 → 入缓存</td></tr>
 *   <tr><td>{@code DisplayGroup}</td><td>{@link Projection}</td>
 *       <td>父实体 + 子实体集合 + {@code remove()}</td></tr>
 *   <tr><td>{@code ItemDisplayBuilder}</td><td>{@link #spawnCell}</td>
 *       <td>生成 ItemDisplay、写 {@code display-source}、给 {@code setItemStack}</td></tr>
 *   <tr><td>{@code DisplayBuilder#applyDisplay}</td><td>{@link #SCALE_TRANSFORMATION}</td>
 *       <td>我们只用得到 {@code Transformation} 一项，其余参数 LogiTech 也没设</td></tr>
 *   <tr><td>{@code MultiBlockService.HOLOGRAM_CACHE}</td><td>{@link #CACHE}</td>
 *       <td><b>普通 HashMap</b>，只在主线程读写（同 LogiTech）</td></tr>
 *   <tr><td>{@code removeUnrecordedHolograms}</td><td>{@link #removeOrphans}</td>
 *       <td>按 {@code display-source} 标记扫附近实体并删</td></tr>
 *   <tr><td>{@code ScheduleSave.addFinalTask}</td><td>{@link #clearAll}</td>
 *       <td>本项目由 {@code Touhou#onDisable} 调用（另有 {@code setPersistent(false)} 兜底）</td></tr>
 * </table>
 *
 * <h2>★ LogiTech 那五个坑，本类逐个的处理</h2>
 * <ol>
 *   <li><b>它不写持久化数据</b> ⇒ {@link #KEY_HOLOGRAM}（{@code touhou:mb-hologram}）
 *       由调用方（两种核心的 GUI 开关）写进方块数据；本类只负责画。</li>
 *   <li><b>它不先清理旧组</b> ⇒ {@link #render} 的<b>第一步</b>就是 {@link #hide}，
 *       否则旧的 Display 实体会变成孤儿飘在空中（LogiTech 那边就是这么飘的）。</li>
 *   <li><b>第 4 参是 {@code HashMap} 而不是 {@code Map}</b> ⇒ 本类参数签名也收
 *       {@link HashMap}，与 LogiTech 逐字一致；调用方传 {@code new HashMap<>(...)}
 *       进来即可（我们两边都不做 {@code Map.copyOf}）。</li>
 *   <li><b>不接受参数、只能画静态 schema</b> ⇒ 本项目两种结构都是固定层图
 *       （{@link ReactorStructure#cells()} 一次性给出全部格），天然没有这个问题。</li>
 *   <li><b>多级类型会一次画全部子级</b> ⇒ 本项目暂无多级组合；将来若加，
 *       {@code cells()} 返回哪一级就画哪一级 —— 投影这一侧不需要改。</li>
 * </ol>
 *
 * <h2>★ 与 LogiTech 必须不同的三处（都写在这里，免得以后被当成本类的 bug）</h2>
 * <ol>
 *   <li><b>"必须空气"的格子不生成实体。</b>LogiTech 是"映射不到就
 *       {@code setItemStack(null)}"（表现为透明，但实体照样在）。本项目 5×5×5 层图里
 *       空气格比构件还多，逐格生成透明空壳纯属浪费实体额度 ——
 *       所以我们连实体都不建（过滤见 {@link ReactorStructure#solidCells}）。
 *       构件格仍然完全照 LogiTech：映射不到就 {@code setItemStack(null)}。</li>
 *   <li><b>PDC 坐标串的格式。</b>LogiTech 用 {@code world,x,y,z}（逗号），
 *       本项目用 {@link TouhouData#encodeLocation} 的 {@code world;x;y;z}（分号）——
 *       与 {@code BlockMenu} 的坐标序列化同格式，出问题时肉眼比对日志更省事。
 *       语义是一样的：都是"这一组实体属于哪个核心"。</li>
 *   <li><b>父实体写成 {@code setPersistent(false)}。</b>LogiTech 靠关服时的
 *       {@code addFinalTask} 全清；但"关服清"要求那一刻实体还在内存里，
 *       崩服 / {@code /reload} 就会把整组实体留在存档里。加上这一条之后，
 *       重启后世界里不会再有上一轮的投影（{@link #removeOrphans} 仍保留，
 *       用于清"崩服前那一刻"留下的残骸）。</li>
 * </ol>
 *
 * <h2>★ 线程</h2>
 * {@link #CACHE} 是<b>普通 HashMap</b>（与 LogiTech 的 {@code HOLOGRAM_CACHE} 一样），
 * 能这么用是因为它只在主线程读写。<b>不要在异步线程调用本类的任何方法</b> ——
 * 生成/删除实体本身就是主线程操作。
 */
public final class MultiBlockProjection {

    /**
     * PDC 标记键 —— 与 LogiTech 的 {@code display-source} <b>同名</b>。
     *
     * <p>值 = 拥有这组投影的核心坐标串（见 {@link TouhouData#encodeLocation}）。
     * 它的用途是"孤儿识别"：世界里带这个键、但不在 {@link #CACHE} 里的实体，
     * 就是上一次留下的残骸（对应 LogiTech 的 {@code HOLOGRAM_REMOVER} 道具）。
     */
    public static final String KEY_DISPLAY_SOURCE = "display-source";

    /**
     * "这台核心的投影开着"的持久化键。
     *
     * <p>★ 这正是 LogiTech 坑 #1：它的 {@code createHologram} <b>不碰方块数据</b>，
     * 开没开全息完全靠调用方自己 {@code setCustomData(loc, getHologramKey(), 1)}。
     * 我们也一样 —— 只不过把它收口到本类的 {@link #isOn}/{@link #setOn}，
     * 免得两个核心各写一遍。
     */
    public static final String KEY_HOLOGRAM = "touhou:mb-hologram";

    /** 父实体的碰撞箱宽高（LogiTech 用 0.1）。 */
    private static final float PARENT_WIDTH = 0.1F;
    private static final float PARENT_HEIGHT = 0.1F;

    /** 子实体的缩放（LogiTech 用 0.5）。 */
    private static final float CHILD_SCALE = 0.5F;

    /**
     * 子实体的 {@code Transformation} —— 缩放 0.5，两个旋转都是"不转"。
     *
     * <p>与 LogiTech 的 {@code new TransformationBuilder().scale(0.5F, 0.5F, 0.5F).build()}
     * 逐字段一致：translation = (0,0,0)、两个 AxisAngle 都是 {@code (0,0,1,0)}。
     *
     * <p>★ 这个对象是<b>不可变</b>的（{@code Transformation} 没有 setter），
     * 所以可以全局复用一份 —— 每个子实体不必各 new 一个。
     */
    private static final Transformation SCALE_TRANSFORMATION = new Transformation(
            new Vector3f(0.0F, 0.0F, 0.0F),
            new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F),
            new Vector3f(CHILD_SCALE, CHILD_SCALE, CHILD_SCALE),
            new AxisAngle4f(0.0F, 0.0F, 1.0F, 0.0F));

    /**
     * 核心坐标 → 投影组（<b>普通 HashMap</b>，与 LogiTech 的 {@code HOLOGRAM_CACHE} 一样）。
     *
     * <p>键必须过 {@link TouhouData#norm}（世界 + 方块整数坐标）—— 本工程踩过
     * "同一个方块的两种 Location hash 相同却 equals 不等"导致 Map 里留下两条记录。
     */
    private static final HashMap<Location, Projection> CACHE = new HashMap<>();

    /**
     * 本插件生成的<b>全部</b>投影实体（父 + 子）。
     *
     * <p>用于"孤儿识别"的精确判据：带 {@link #KEY_DISPLAY_SOURCE} 标记、
     * 又<b>不</b>在这个集合里的实体 = 残骸。LogiTech 那边是拿
     * {@code HOLOGRAM_CACHE} 里各组的 {@code getDisplaySet()} 现算，
     * 这里额外存一份索引，好处是父实体也在索引里（LogiTech 的
     * {@code removeUnrecordedHolograms} 靠"没有 source 就跳过"顺手绕过了父实体）。
     */
    private static final Set<UUID> OWNED = new HashSet<>();

    /** PDC 键（懒加载：{@code NamespacedKey} 需要插件实例，构造期拿不到）。 */
    private static NamespacedKey sourceKey;

    /** 累计生成了多少个子实体（诊断用，证明"投影真的画出来了"）。 */
    private static int lastCellCount;
    /** 累计拒绝次数（超上限 / 不支持投影）。 */
    private static int refusalCount;

    private MultiBlockProjection() {
    }

    // ---------------------------------------------------------------- 面向未来的通用门面

    /**
     * 给任意结构<b>补一个默认的 projector</b>（结构自己没提供时用）。
     *
     * <p>这就是"未来新结构直接能投影"的那条路：只要结构实现了
     * {@link ReactorStructure#cells()}，哪怕完全没听过投影这回事，
     * 也能立刻开投影 —— 朝向取已落盘/建议值，图标取一套通用映射
     * （{@link #genericMapping}：粘液 id → 它的物品，原版材质 → 同名 Material）。
     *
     * <p>★ 结构若自己实现了 {@link ReactorStructure#projector()}，那份<b>优先</b> ——
     * 因为它能给出"这台机器独有"的图标（例如反应堆要区分输入/输出接口的染色玻璃）。
     *
     * @param fallbackMapping 通用映射也认不出来时用的兜底映射（可为 {@code null}）
     */
    public static ReactorStructure.Projector projectorOf(ReactorStructure structure,
                                                         ReactorStructure.Direction direction,
                                                         java.util.function.Supplier<HashMap<String, ItemStack>> fallbackMapping) {
        if (structure == null) {
            return null;
        }
        ReactorStructure.Projector own = structure.projector();
        if (own != null) {
            return own;
        }
        if (!structure.supportsProjection()) {
            return null;
        }
        HashMap<String, ItemStack> map = genericMapping(structure);
        if (fallbackMapping != null) {
            // 兜底只补"通用映射认不出来"的那些格（不覆盖已经认出来的）
            HashMap<String, ItemStack> extra = fallbackMapping.get();
            if (extra != null) {
                for (Map.Entry<String, ItemStack> e : extra.entrySet()) {
                    map.putIfAbsent(e.getKey(), e.getValue());
                }
            }
        }
        return ReactorStructure.Projector.of(direction, map);
    }

    /**
     * <b>通用图标映射</b>：把 layer 图里的 part id 逐个翻成显示物品。
     *
     * <p>两条判据（与 {@link ReactorStructure#partIdAt} 的逆运算同源）：
     * <pre>
     *   能当粘液 id 查到 SlimefunItem  → 用它的物品图标
     *   查不到且是合法的 Material 名    → 用那个原版材质
     *   两者都不是                     → 不放进映射（画出来就是透明格，与 LogiTech 一致）
     * </pre>
     *
     * <p>⚠ 需要 Slimefun 已注册完物品；本方法只在"玩家点投影按钮 / 敲命令"时调用，
     * 那时注册早就完成了。万一某个 id 还没注册，如实跳过（透明格），不抛异常。
     */
    public static HashMap<String, ItemStack> genericMapping(ReactorStructure structure) {
        HashMap<String, ItemStack> map = new HashMap<>();
        if (structure == null) {
            return map;
        }
        for (ReactorStructure.Cell cell : ReactorStructure.solidCells(structure.cells())) {
            String id = cell.id();
            if (map.containsKey(id)) {
                continue;                       // 同一 part id 反复出现，查一次就够
            }
            ItemStack icon = iconOfPartId(id);
            // ★ 映射不到时放进一个显式的 null：语义 = "这一格我们知道该有什么，
            //   但拿不到图标" —— 与"压根没登记这一格"区分开（两者画出来都是透明）。
            map.put(id, icon == null ? null : glow(icon));
        }
        return map;
    }

    /** 单个 part id → 图标；认不出来返回 {@code null}。 */
    private static ItemStack iconOfPartId(String partId) {
        if (partId == null || partId.isBlank() || ReactorStructure.isAirRequirement(partId)) {
            return null;
        }
        try {
            io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem sf =
                    io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getById(partId);
            if (sf != null && sf.getItem() != null) {
                return sf.getItem().clone();
            }
        } catch (RuntimeException ignored) {
            // Slimefun 还没就绪 / id 非法：掉到原版材质那条路
        }
        org.bukkit.Material mat = org.bukkit.Material.matchMaterial(partId);
        return mat == null || mat.isAir() ? null : new ItemStack(mat);
    }

    /**
     * <b>通用开/关</b> —— 任何"结构 + 图标映射"的组合都能用（GUI 与命令共用）。
     *
     * <p>步骤与 LogiTech 那个 8 号槽的按钮一致：
     * <pre>
     *   已开 → 关（清实体 + 把关键写回 off）
     *   未开 → 先现场校验结构（requireCompleteStructure=true 时）→ render
     *          （render 内部先清旧组）→ 写 on
     * </pre>
     *
     * <p>★ 朝向的来源（三者优先级从高到低）：
     * 结构自己 {@link ReactorStructure#projector()} 给的 → <b>本次现场检测命中的</b> →
     * 已落盘的 {@code touhou:structure-dir} → NORTH。
     * 现场那次优先于落盘值，是因为玩家可能刚把结构换了个方向搭好，落盘的还是旧朝向。
     *
     * @param host  宿主（结构 + 图标映射）；见 {@link ReactorStructure.ProjectionHost}
     * @param requireCompleteStructure 未开时是否要求"结构完整"才给开。
     *                       {@code true}（两种内置核心都用它）= 结构没搭好就不给开；
     *                       {@code false} = LogiTech 那种"随时能开来看该怎么搭"的行为。
     * @param feedback       反馈出口（可为 {@code null}；为 null 时只写一行 {@link Log#warn}）
     * @return 切换后是否处于"开"的状态
     */
    public static boolean toggle(Location core, ReactorStructure.ProjectionHost host,
                                 java.util.function.Consumer<String> feedback,
                                 boolean requireCompleteStructure) {
        Location loc = TouhouData.norm(core);
        if (loc == null) {
            return refuse(feedback, "&c核心坐标无效，无法操作投影");
        }
        if (isOn(loc)) {
            hide(loc);
            if (feedback != null) {
                feedback.accept("&7已关闭&f多方块投影");
            }
            return false;
        }
        ReactorStructure structure = host == null ? null : host.structure();
        if (structure == null || !structure.supportsProjection()) {
            return refuse(feedback, "&c当前结构不支持投影（没有落点表）");
        }

        ReactorStructure.Direction dir = structure.isSymmetric()
                ? ReactorStructure.Direction.NORTH : directionFromData(loc);
        // ★ 结构检测【总是】跑一次：它更重要的作用是"拿到现场命中的准确朝向"。
        //   是否因为"不完整"而拒绝，才由 requireCompleteStructure 决定。
        //
        //   原来这里把两件事绑在一起（requireCompleteStructure=false 时连检测都不跑），
        //   于是"不要求结构完整"就等于"朝向只能靠落盘数据猜" ——
        //   机器从没激活过时那个数据根本不存在，投影会画在错误的方向上。
        ReactorStructure.Result r = structure.check(loc, dir);
        if (requireCompleteStructure && !r.isComplete()) {
            return refuse(feedback, "&c结构不完整，无法开启投影：&7" + r.summary());
        }
        if (r.isComplete() && r.direction() != null && !structure.isSymmetric()) {
            dir = r.direction();                // 现场命中的朝向最准
        }
        // ★ 必须拷一份 effectively-final 的副本给 lambda 捕获：
        //   上面那个 if 改过 dir，编译器就不允许它再进闭包了。
        final ReactorStructure.Direction resolved = dir;

        ReactorStructure.Projector projector = projectorOf(structure, resolved,
                () -> host.displayMapping(resolved));
        if (projector == null) {
            return refuse(feedback, "&c当前结构不支持投影（拿不到 projector）");
        }
        ReactorStructure.Direction finalDir = projector.projectionDirection() == null
                ? resolved : projector.projectionDirection();
        if (!render(loc, structure, finalDir, projector.displayMapping(), feedback)) {
            return false;
        }
        setOn(loc, true);
        if (feedback != null) {
            feedback.accept("&a已开启&f多方块投影&7（" + lastCellCount() + " 格 ≈ "
                    + (lastCellCount() + 1) + " 个实体"
                    + (structure.isSymmetric() ? "，结构四向对称按 NORTH 画" : "") + "）");
        }
        return true;
    }

    /** 读已落盘的朝向（没有就 NORTH）—— 与 {@code ReactorManager.storedDirection} 同源。 */
    private static ReactorStructure.Direction directionFromData(Location core) {
        String raw = TouhouData.getString(core, TouhouData.KEY_DIRECTION, null);
        if (raw == null || raw.isBlank()) {
            return ReactorStructure.Direction.NORTH;
        }
        try {
            return ReactorStructure.Direction.fromInt(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return ReactorStructure.Direction.NORTH;
        }
    }

    // ---------------------------------------------------------------- 开关状态（持久化）

    /** 这台核心的投影开着吗（读方块数据，单一数据源）。 */
    public static boolean isOn(Location core) {
        return "on".equals(TouhouData.getString(core, KEY_HOLOGRAM, "off"));
    }

    /** 写投影开关状态（{@code loc == null} 时静默忽略，构造期安全）。 */
    public static void setOn(Location core, boolean on) {
        TouhouData.setString(core, KEY_HOLOGRAM, on ? "on" : "off");
    }

    // ---------------------------------------------------------------- 画 / 清

    /**
     * <b>显示投影</b>（LogiTech {@code createHologram} 的等价物）。
     *
     * <p>★ 顺序不能反，这是 LogiTech 坑 #2 的正面写法：
     * <pre>
     *   ① 先 hide(loc)   —— 清掉旧组，否则旧的 Display 实体全变成孤儿
     *   ② 再建父实体 + 逐格子实体
     *   ③ 最后入缓存
     * </pre>
     *
     * <p>⚠ <b>主线程</b>调用。结构声明为四向对称时朝向强制按 NORTH 处理
     * （与 LogiTech 一致：形状转不转都一样，画 NORTH 最省事也最不容易错）。
     *
     * @param core      核心方块位置（内部会归一化）
     * @param structure 结构（提供落点表与对称性）；不支持投影时拒绝
     * @param direction 当前朝向；{@code null} 视为 NORTH
     * @param itemMap   {@code partId → ItemStack} 显示映射
     *                  ★ 参数类型刻意是 {@code HashMap} 而不是 {@code Map} ——
     *                  与 LogiTech 的签名逐字一致（那边注释专门警告过
     *                  "别把 {@code Map.copyOf} 的结果直接喂进来"）。
     *                  我们这里只是把这层约束显式化：调用方自己 new 一个 HashMap。
     * @param feedback  拒绝原因的回显出口（可为 {@code null}）；成功时不回调
     * @return true = 真的画出来了
     */
    public static boolean render(Location core, ReactorStructure structure,
                                 ReactorStructure.Direction direction,
                                 HashMap<String, ItemStack> itemMap,
                                 java.util.function.Consumer<String> feedback) {
        Location loc = TouhouData.norm(core);
        if (loc == null || structure == null) {
            return refuse(feedback, "&c核心坐标无效，无法生成投影");
        }
        World world = loc.getWorld();
        if (world == null) {
            return refuse(feedback, "&c核心不在任何世界，无法生成投影");
        }
        AddonConfig cfg = AddonConfig.get();
        if (cfg != null && !cfg.projectionEnabled) {
            return refuse(feedback, "&c投影功能已被配置关闭（config.yml 的 projection.enabled）");
        }
        if (!structure.supportsProjection()) {
            return refuse(feedback, "&c当前结构不支持投影（没有落点表："
                    + structure.name() + "）");
        }

        // ★ 先算要画几格：超上限就别开始（画到一半再放弃会留下半个投影 + 一堆实体）
        List<ReactorStructure.Cell> cells = ReactorStructure.solidCells(structure.cells());
        int limit = cfg == null ? 256 : Math.max(1, cfg.projectionMaxParts);
        if (cells.size() > limit) {
            return refuse(feedback, "&c这结构有 &f" + cells.size()
                    + " &c个构件，超过投影上限 &f" + limit
                    + "&c（投影每格 = 1 个实体）。改大 config.yml 的 projection.max-parts 可放开");
        }

        // ★★ 坑 #2：必须先清旧组。LogiTech 直接 HOLOGRAM_CACHE.put 覆盖，
        //    旧的那组 Display 实体从此无人引用、永远飘在空中。
        hide(loc);

        // 对称结构朝向强制 NORTH（LogiTech：if (type.isSymmetric()) direction = NORTH;）
        ReactorStructure.Direction dir = direction == null ? ReactorStructure.Direction.NORTH : direction;
        if (structure.isSymmetric()) {
            dir = ReactorStructure.Direction.NORTH;
        }

        // 这一步与 LogiTech 的 DisplayGroup 构造器完全对应：
        //   location.getWorld().spawnEntity(location, EntityType.INTERACTION)
        // 差别只在多写了 setPersistent(false)（见类注释"必须不同的三处"）。
        Location parentAt = loc.clone().add(0.5D, 0.5D, 0.5D);
        Interaction parent;
        try {
            if (!world.isChunkLoaded(parentAt.getBlockX() >> 4, parentAt.getBlockZ() >> 4)) {
                return refuse(feedback, "&c核心所在区块未加载，无法生成投影");
            }
            parent = (Interaction) world.spawnEntity(parentAt, EntityType.INTERACTION);
        } catch (RuntimeException e) {
            Log.severe("[投影] 生成父实体失败 @ " + ReactorStructure.xyz(loc), e);
            return refuse(feedback, "&c生成投影父实体失败：" + e);
        }
        parent.setInteractionWidth(PARENT_WIDTH);
        parent.setInteractionHeight(PARENT_HEIGHT);
        // ★ 不落盘：崩服 / reload 之后世界里不会留下上一轮的投影
        parent.setPersistent(false);
        // 父实体的 display-source 也写核心坐标（LogiTech 的 DisplayGroup 构造器就是这么做的）
        PersistentDataContainer pdc = parent.getPersistentDataContainer();
        pdc.set(key(), PersistentDataType.STRING, sourceOf(loc));

        Projection group = new Projection(parent);
        OWNED.add(parent.getUniqueId());

        // 逐格画（LogiTech：for (i < type.getSchemaSize())）
        int drawn = 0;
        for (ReactorStructure.Cell cell : cells) {
            try {
                ItemDisplay child = spawnCell(world, loc, dir, cell, itemMap, group);
                if (child != null) {
                    drawn++;
                }
            } catch (RuntimeException e) {
                // 单格失败不能让整组投影烂尾：把已经建好的收干净再报错
                Log.severe("[投影] 生成第 (" + cell.dx() + "," + cell.dy() + "," + cell.dz()
                        + ") 格失败，已回滚整组 @ " + ReactorStructure.xyz(loc), e);
                group.remove();
                OWNED.remove(parent.getUniqueId());
                return refuse(feedback, "&c生成投影时出错（已回滚）：" + e);
            }
        }

        CACHE.put(loc, group);
        lastCellCount = drawn;
        return true;
    }

    /**
     * 生成一格子 {@code ItemDisplay}（LogiTech {@code ItemDisplayBuilder} 的等价物）。
     *
     * <p>四件事，一件都不多：
     * <ol>
     *   <li>位置 = <b>核心位置 + 该格相对偏移</b>（按朝向旋转 + 0.5 居中）——
     *       旋转用的是 {@link ReactorStructure.Direction#rotate(int, int)}，
     *       <b>与结构校验完全同一套</b>；</li>
     *   <li>{@code setItemStack(itemMap.get(cell.id()))} —— 映射不到就是
     *       {@code null}（表现为透明显示），与 LogiTech 逐字一致；</li>
     *   <li>缩放 0.5（{@link #SCALE_TRANSFORMATION}）；</li>
     *   <li>PDC {@code display-source} = 核心坐标串。</li>
     * </ol>
     *
     * <p>★ 刻意<b>不</b>写 {@code setItemDisplayTransform}：LogiTech 的
     * {@code ItemDisplayBuilder} 里那个字段默认是 {@code null}，只有显式调过才写 ——
     * 也就是"保持原版默认"。我们保持一致，免得画出来的姿态与 LogiTech 不一样。
     */
    private static ItemDisplay spawnCell(World world, Location core, ReactorStructure.Direction dir,
                                         ReactorStructure.Cell cell, HashMap<String, ItemStack> itemMap,
                                         Projection group) {
        int[] rot = dir.rotate(cell.dx(), cell.dz());
        Location at = new Location(world,
                core.getBlockX() + rot[0] + 0.5D,
                core.getBlockY() + cell.dy() + 0.5D,
                core.getBlockZ() + rot[1] + 0.5D);

        ItemDisplay display = (ItemDisplay) world.spawnEntity(at, EntityType.ITEM_DISPLAY);
        ItemStack icon = itemMap == null ? null : itemMap.get(cell.id());
        // ★ 映射不到就 setItemStack(null)：LogiTech 就是这么做的（表现为透明）
        display.setItemStack(icon);
        display.setTransformation(SCALE_TRANSFORMATION);
        display.setPersistent(false);
        display.getPersistentDataContainer().set(key(), PersistentDataType.STRING, sourceOf(core));

        group.children.add(display.getUniqueId());
        OWNED.add(display.getUniqueId());
        return display;
    }

    /**
     * <b>隐藏投影</b>（LogiTech {@code removeHologramSync} 的等价物）。
     *
     * <p>做两件事：从缓存取出并 {@code remove()} 整组实体；把 {@link #KEY_HOLOGRAM}
     * 写回 {@code off}。★ 第二件事是 LogiTech 也做了的（它顺手
     * {@code setCustomData(loc, "holo", 0)}），所以调用方不必再写一遍。
     */
    public static void hide(Location core) {
        Location loc = TouhouData.norm(core);
        if (loc == null) {
            return;
        }
        Projection group = CACHE.remove(loc);
        if (group != null) {
            group.remove();
        }
        setOn(loc, false);
    }

    /**
     * <b>全清</b> —— 关服 / 插件卸载时调用（LogiTech 的
     * {@code ScheduleSave.addFinalTask} 做的同一件事）。
     *
     * <p>★ 实体已经记了 {@code setPersistent(false)}，理论上关服时本来就落不了盘；
     * 这个方法保证"进程还活着的时候世界里立刻干净"，也让
     * {@link #CACHE} 不会跨生命周期留下脏数据。
     */
    public static void clearAll() {
        int n = CACHE.size();
        for (Location loc : new ArrayList<>(CACHE.keySet())) {
            Projection group = CACHE.remove(loc);
            if (group != null) {
                group.remove();
            }
        }
        OWNED.clear();
        if (n > 0) {
            Log.info("[投影] 关服清理：已移除 " + n + " 组多方块投影");
        }
    }

    /**
     * 清掉某台机器的投影与开关状态（核心被拆 / 结构失效停机时调用）。
     *
     * <p>与 {@link #hide} 的差别：这里<b>另外把方块数据的开关也写回 off</b>，
     * 而且允许 {@code loc == null}（拆方块那条路径上坐标可能已经不可用了）。
     */
    public static void forget(Location core) {
        hide(core);
    }

    // ---------------------------------------------------------------- 孤儿清理

    /**
     * <b>清理周围的孤儿投影</b>（LogiTech {@code removeUnrecordedHolograms} 的等价物，
     * 那边由 {@code HOLOGRAM_REMOVER} 道具触发，这边由 {@code /touhou proj clean} 触发）。
     *
     * <p>判据（比 LogiTech 更严一格）：
     * <pre>
     *   实体是 Display 或 Interaction
     *   且 带 display-source 标记
     *   且 不在本插件当前持有的实体索引里（OWNED）
     *   ⇒ 是残骸，删
     * </pre>
     * LogiTech 的判据是"不在 HOLOGRAM_CACHE 各组的 getDisplaySet() 里"——
     * 效果一样，但它必须靠"父实体没写 source"来绕过父实体；我们显式维护索引，
     * 父实体也在索引里，语义更直白。
     *
     * <p>⚠ 主线程调用。
     *
     * @param center 扫描中心
     * @param range  半径（格，按立方体算 —— 与 LogiTech 的 {@code getNearbyEntities} 一致）
     * @return 删除的实体数
     */
    public static int removeOrphans(Location center, int range) {
        Location loc = TouhouData.norm(center);
        if (loc == null || loc.getWorld() == null) {
            return 0;
        }
        int r = Math.max(1, range);
        List<Entity> nearby;
        try {
            nearby = new ArrayList<>(loc.getWorld().getNearbyEntities(loc, r, r, r));
        } catch (RuntimeException e) {
            Log.severe("[投影] 扫描附近实体失败 @ " + ReactorStructure.xyz(loc), e);
            return 0;
        }
        int removed = 0;
        for (Entity e : nearby) {
            if (!(e instanceof Display) && !(e instanceof Interaction)) {
                continue;
            }
            if (sourceOf(e) == null) {
                continue;                       // 不是我们画的（没有标记）
            }
            if (OWNED.contains(e.getUniqueId())) {
                continue;                       // 正在用的投影，别动
            }
            e.remove();
            removed++;
        }
        // 顺手把"活着却已经没有实体"的空组剔掉（例如实体被别的插件清了）
        List<Location> dead = new ArrayList<>();
        for (Map.Entry<Location, Projection> en : CACHE.entrySet()) {
            if (en.getValue().isDead()) {
                dead.add(en.getKey());
            }
        }
        for (Location k : dead) {
            Projection group = CACHE.remove(k);
            if (group != null) {
                group.remove();
            }
        }
        return removed;
    }

    // ---------------------------------------------------------------- 诊断

    /** 当前开着的投影组数。 */
    public static int cachedGroupCount() {
        return CACHE.size();
    }

    /** 本插件当前持有的投影实体总数（父 + 子）。 */
    public static int ownedEntityCount() {
        return OWNED.size();
    }

    /** 上一次 {@link #render} 实际画出的格数（诊断用）。 */
    public static int lastCellCount() {
        return lastCellCount;
    }

    /** 累计被拒绝的次数（超上限 / 结构不支持 / 配置关闭）。 */
    public static int refusalCount() {
        return refusalCount;
    }

    /**
     * 某台核心的投影现状（命令用）。
     *
     * @return 逐行报告
     */
    public static List<String> describe(Location core) {
        Location loc = TouhouData.norm(core);
        List<String> out = new ArrayList<>();
        if (loc == null) {
            out.add("核心坐标无效");
            return out;
        }
        boolean open = isOn(loc);
        Projection group = CACHE.get(loc);
        out.add("开关状态 : " + (open ? "&a开" : "&7关")
                + "  （方块数据 key = " + KEY_HOLOGRAM + "）");
        out.add("缓存中的组: " + (group == null ? "&7无" : "&a有（子实体 "
                + group.children.size() + " 个）"));
        if (group != null) {
            Location at = group.parent.getLocation();
            out.add("父实体    : Interaction " + group.parent.getUniqueId()
                    + " @ " + String.format("%.2f,%.2f,%.2f", at.getX(), at.getY(), at.getZ()));
            out.add("           碰撞箱 " + group.parent.getInteractionWidth()
                    + " × " + group.parent.getInteractionHeight());
        }
        out.add("全插件合计: " + CACHE.size() + " 组 / " + OWNED.size() + " 个实体");
        out.add("上次画出  : " + lastCellCount + " 格   被拒绝 " + refusalCount + " 次");
        return out;
    }

    /** 配置/结构相关的一句话说明（GUI 图标 lore 用）。 */
    public static List<String> describePolicy() {
        AddonConfig cfg = AddonConfig.get();
        int limit = cfg == null ? 256 : Math.max(1, cfg.projectionMaxParts);
        return List.of(
                "投影 = 逐格 ItemDisplay 实体（缩放 0.5），父实体是 Interaction（碰撞箱 0.1）",
                "构件格显示 partId 对应的物品；映射不到就显示为空（与 LogiTech 一致）",
                "\"必须是空气\"的格子不生成实体（本项目对 LogiTech 的唯一取舍）",
                "开关状态持久化在方块数据 " + KEY_HOLOGRAM,
                "单次投影上限 " + limit + " 格（projection.max-parts）",
                "总开关 projection.enabled = " + (cfg == null || cfg.projectionEnabled));
    }

    /** 扫描范围内本插件画的投影实体数（诊断：确认"投影真的生成了"）。 */
    public static int countNearby(Location center, int range) {
        Location loc = TouhouData.norm(center);
        if (loc == null || loc.getWorld() == null) {
            return 0;
        }
        int r = Math.max(1, range);
        int n = 0;
        for (Entity e : loc.getWorld().getNearbyEntities(loc, r, r, r)) {
            if ((e instanceof Display || e instanceof Interaction) && sourceOf(e) != null) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 内部

    private static boolean refuse(java.util.function.Consumer<String> feedback, String msg) {
        refusalCount++;
        if (feedback != null) {
            feedback.accept(msg);
        } else {
            Log.warn("[投影] " + Notify.plain(msg));
        }
        return false;
    }

    /** 核心坐标 → PDC 里的 source 串。 */
    private static String sourceOf(Location core) {
        String s = TouhouData.encodeLocation(core);
        return s == null ? "null" : s;
    }

    /** 读实体的 {@code display-source}；没有标记返回 {@code null}。 */
    private static String sourceOf(Entity e) {
        if (e == null) {
            return null;
        }
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        return pdc.get(key(), PersistentDataType.STRING);
    }

    /** 懒加载 PDC 键（{@code NamespacedKey} 需要插件实例）。 */
    private static NamespacedKey key() {
        NamespacedKey k = sourceKey;
        if (k == null) {
            k = new NamespacedKey(com.example.touhou.Touhou.getInstance(), KEY_DISPLAY_SOURCE);
            sourceKey = k;
        }
        return k;
    }

    /**
     * 一组投影 —— LogiTech {@code DisplayGroup} 的等价物。
     *
     * <p>刻意只保留"父实体 + 子实体 UUID 集合 + remove()"三件事：
     * LogiTech 那 143 行里有一半（{@code addDisplay}/{@code removeDisplay}/
     * {@code teleport}/{@code applyLists} 的 child_display_list 编码）本项目用不到 ——
     * 我们一次性画完整组、从不单格增删，也没有"整体搬走"的需求。
     */
    private static final class Projection {

        /** 父实体（Interaction，碰撞箱 0.1 × 0.1）。 */
        private final Interaction parent;
        /** 子实体 UUID（诊断/清理用，顺序无意义）。 */
        private final Set<UUID> children = new HashSet<>();

        private Projection(Interaction parent) {
            this.parent = parent;
        }

        /** 这组的实体是不是已经全没了。 */
        private boolean isDead() {
            if (parent.isValid()) {
                return false;
            }
            for (UUID id : children) {
                Entity e = org.bukkit.Bukkit.getEntity(id);
                if (e != null && e.isValid()) {
                    return false;
                }
            }
            return true;
        }

        /** 整组删除（LogiTech {@code DisplayGroup#remove}）。 */
        private void remove() {
            for (UUID id : children) {
                Entity e = org.bukkit.Bukkit.getEntity(id);
                if (e != null) {
                    e.remove();
                }
                OWNED.remove(id);
            }
            children.clear();
            if (parent.isValid()) {
                parent.remove();
            }
            OWNED.remove(parent.getUniqueId());
        }
    }

    // ---------------------------------------------------------------- 物品映射的工厂

    /**
     * 建一份 {@code partId → ItemStack} 映射 —— 便利工厂，顺带把"必须是 HashMap"
     * 这条约束收在一处（LogiTech 坑 #3）。
     *
     * @param entries 交替的 {@code partId, ItemStack} 对（{@code ItemStack} 统一
     *                {@code clone()}，免得图标被改到共享实例上）
     */
    public static HashMap<String, ItemStack> mapOf(Object... entries) {
        HashMap<String, ItemStack> map = new HashMap<>();
        for (int i = 0; i + 1 < entries.length; i += 2) {
            Object k = entries[i];
            Object v = entries[i + 1];
            if (k == null || v == null) {
                continue;                       // 物品还没注册好：如实跳过（画出来就是空的）
            }
            String id = String.valueOf(k);
            ItemStack icon = (ItemStack) v;
            map.put(id, icon.clone());
        }
        return map;
    }

    /**
     * 给投影图标加"发光"（LogiTech 的 {@code AddUtils.addGlow}）。
     *
     * <p>为什么值得加：投影是<b>远处</b>看的，戴着附魔光晕的物品在暗处也能看清轮廓
     * （LogiTech 的 {@code MBID_TO_ITEM} 就是一个个 {@code addGlow(...)} 包出来的）。
     * 1.20.4 起用 {@code ItemFlag.HIDE_ENCHANTS} 把附魔行藏掉，只留光晕。
     *
     * @param icon 原图标；{@code null} 原样返回（映射不到就是"透明格"）
     */
    public static ItemStack glow(ItemStack icon) {
        if (icon == null) {
            return null;
        }
        ItemStack out = icon.clone();
        org.bukkit.inventory.meta.ItemMeta meta = out.getItemMeta();
        if (meta != null) {
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            out.setItemMeta(meta);
        }
        return out;
    }
}
