package com.example.touhou.core;

import com.example.touhou.power.AbstractPowerBlock;
import com.example.touhou.power.PowerComponent;
import com.example.touhou.power.PowerNetwork;
import com.example.touhou.power.PowerNetworkManager;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「赛钱箱」—— 多方块<b>核心</b>（POWER 节点）。
 *
 * <h2>能源：走自研 POWER，<b>不碰原生电力</b></h2>
 * 本类<b>不</b>继承 {@code AGenerator}、<b>不</b>实现 {@code EnergyNetComponent}，
 * 而是照 {@link com.example.touhou.power.PowerStorageUnit} 的方式实现
 * {@link PowerComponent}：
 * <ul>
 *   <li>继承 {@link AbstractPowerBlock} —— 它负责挂 ticker（主线程）并在放置/破坏时
 *       让 POWER 网络失效重算，本类不必自己写这两件事；</li>
 *   <li>电量读写走 {@link TouhouData}（方块数据 key {@link #KEY_CHARGE}），
 *       Slimefun 负责异步落盘 —— <b>不</b>用 PDC，也不自己开文件；</li>
 *   <li>容量是 {@link ItemSetting}（默认 {@value #DEFAULT_CAPACITY}），可在
 *       {@code items.yml} 里改，与存储单元同一套做法。</li>
 * </ul>
 *
 * <h2>★ 关于容量默认值 5</h2>
 * spec 原文只写了「容量为5」，没有说是 <b>5 POWER</b>、5 格半径还是别的什么。
 * 在没有澄清之前，本类<b>按字面</b>把它实现成"额定容量 = 5 POWER"，
 * 并做成 {@link ItemSetting} 以便随时改（见 {@link #DEFAULT_CAPACITY} 与
 * {@link #capacity} 的注释）。这个数直接影响"网络均衡时这台机器能分到多少电"，
 * 所以要改就改这里，不要散落到别处。
 * <p>⚠ 一次运作要花 {@code saizenbako.power-cost}（默认 2）POWER，
 * 容量 5 意味着"最多攒两次半" —— 这是数值上的现实约束，不是 bug。
 *
 * <h2>GUI 布局（45 格 = 5 行 × 9 列）</h2>
 * <pre>
 *   第1行:  X  X  X  X  1  i  X  X  X      槽  0~ 8， 预留#1 在槽  4，指示槽在槽  5
 *   第2行:  X  X  0  i  X  X  2  i  X      槽  9~17， 预留#0/#2 在槽 11/15，指示槽 12/16
 *   第3行:  X  X  X  I  IO S  X  X  X      槽 18~26， I 在槽 21，IO 在槽 22，S 在槽 23
 *   第4行:  X  X  5  i  X  X  3  i  X      槽 27~35， 预留#5/#3 在槽 29/33，指示槽 30/34
 *   第5行:  X  X  X  X  4  i  H  X  X      槽 36~44， 预留#4 在槽 40，指示槽 41，投影开关 42
 *
 *   X  = 占位符（灰色玻璃板，显示文本「少女祈祷中」），不可取出不可放入
 *   I  = 信息槽：POWER 量 + 激活状态 + 最近一次运作结论，<b>点击 = 现场检测并激活</b>
 *   IO = 混合型输入输出端：外界物流可进可出，<b>也是唯一的输出槽</b>
 *   S  = 信息槽：多方块核心位置 + 编号↔槽位映射，点击 = 打印结构明细
 *   0~5 = <b>预留槽</b>：真正为空（不放任何图标），玩家放不进去、也拿不出来；
 *         内容是"编号相同的木桩 IO 槽"的只读镜像
 *   i  = <b>序号指示槽</b>：紧邻预留槽右侧（下标 +1），显示该预留槽的序号 0~5，锁死不可交互
 * </pre>
 * 槽位映射由 {@link #SKELETON} 单点定义，构造 GUI 与自检都读它（抄自 {@link AbstractReactorPort}）。
 *
 * <h2>★ 这台机器怎么跑</h2>
 * 逻辑全在 {@link SaizenbakoManager}（激活 / 木桩编号 / 镜像 / 运作），
 * 本类负责"方块与界面"：{@link PowerComponent} 实现、GUI 布局、按键、图标文案。
 * 与反应堆那边 {@code UtsuhoReactorCore} + {@link ReactorManager} 是同一种分工。
 *
 * <h2>★ 构造期没有 Location（血的教训）</h2>
 * {@code BlockMenuPreset.init()} 是在 <b>preset 的构造器里</b>被调用的，
 * 那一刻还没有任何方块，{@code Location} 必然是 {@code null}。
 * 所以：{@link #constructMenu} 里<b>只放与位置无关的内容</b>（图标一律传 {@code null} 位置），
 * 所有与真实方块相关的内容放在 {@link BlockMenuPreset#newInstance(BlockMenu, Block)}
 * 与 ticker 里刷新 —— 那里才有真实的 {@code Block}。
 *
 * <h2>★ 为什么还实现了 {@code NotHopperable}</h2>
 * 核心的世界方块是 {@code LOOM}（织布机）—— 它在原版里<b>是有方块实体的容器</b>。
 * 漏斗对着它时，插进去的是"原版织布机那 3 格"，而不是本类的 45 格菜单：
 * 玩家看不到也拿不回，等于物品凭空消失。实现 {@code NotHopperable} 之后，
 * Slimefun 的 {@code HopperListener} 会直接取消这类插入
 * （判据：{@code InventoryMoveItemEvent} 的目标是本方块且它实现了该接口）。
 * 加上"6 个预留槽根本不在物流槽位表里"，漏斗/物流两条路都碰不到预留槽。
 */
public class Saizenbako extends AbstractPowerBlock
        implements PowerComponent, GuiShiftGuard, io.github.thebusybiscuit.slimefun4.core.attributes.NotHopperable {

    // ---------------------------------------------------------------- POWER

    /**
     * 电量持久化 key。
     *
     * <p>与 {@link com.example.touhou.power.PowerStorageUnit} <b>同名同义</b>
     * （{@code touhou:power-charge}）：方块数据是按位置存的，同名不会互相覆盖；
     * 用同一个 key 的好处是"看方块数据就知道所有 POWER 方块的电量都在这一个键下"。
     */
    public static final String KEY_CHARGE = "touhou:power-charge";

    /**
     * 容量默认值 —— <b>照 spec 的字面「5」实现</b>。
     *
     * <p>⚠ 这个数<b>小得反常</b>（存储单元是 500 万、集成核心是 100 万），
     * 所以有理由怀疑 spec 的「容量为5」指的是别的东西（例如 5 格半径、
     * 5 个槽位、5 级充能…）。在用户澄清之前按字面实现，并做成配置项：
     * 改 {@code items.yml} 里 {@code TOUHOU_COMPLEX_MACHINE_SAIZENBAKO.capacity} 即可，不必改代码。
     */
    public static final int DEFAULT_CAPACITY = 5;

    /** POWER 额定容量（{@code items.yml} 可改，默认 {@value #DEFAULT_CAPACITY}）。 */
    public final ItemSetting<Integer> capacity = new ItemSetting<>(this, "capacity", DEFAULT_CAPACITY);

    // ---------------------------------------------------------------- GUI 布局

    /** 45 格里每一格的语义。 */
    public enum GridCell {
        /** 占位符（需求里的 X）—— 灰色玻璃板，显示「少女祈祷中」。 */
        BORDER,
        /**
         * 预留接口（需求里的 1~6，本轮起编号 0~5）—— <b>真正的空格子</b>。
         *
         * <p>不加任何图标（保持视觉上的"空"），但它<b>不是</b>可交互槽：
         * {@link GuiLock#autoGuard()} 会把它锁死，玩家塞不进也拿不走。
         * 内容是"编号相同的木桩 IO 槽"的只读镜像（见 {@link SaizenbakoManager}）。
         */
        RESERVED,
        /**
         * 序号指示槽 —— 紧贴预留槽<b>右侧</b>那一格（下标 = 预留槽 + 1）。
         *
         * <p>显示该预留槽的序号 0~5，让玩家知道"这一格该由几号木桩供料"。
         * 锁死不可交互（与占位符同一套 {@link GuiLock} 机制）。
         */
        INDEX,
        /** 信息槽：POWER 量 + 激活状态 + 运作结论，<b>点击 = 现场检测并激活</b>（需求里的 I）。 */
        INFO,
        /** 混合型输入输出端：外界物流可进可出（需求里的 IO），<b>同时是产物输出槽</b>。 */
        IO,
        /** 信息槽：多方块核心位置 + 编号↔槽位映射（需求里的 S）。 */
        CORE_POS,
        /**
         * 多方块<b>投影开关</b>（槽 42 = 第 5 行列 7，骨架里的一个占位格）。
         *
         * <p>★ 位置选择：第 5 行（下标 36~44）是
         * {@code X X X X 4 i ? X X} —— 槽 40 是 4 号预留槽、槽 41 是它的序号指示槽，
         * 所以空格只剩 36/37/38/39/42/43/44。取<b>槽 42</b>（指示槽右边那一格）：
         * 它紧贴"预留#4 + 指示槽"这一对，玩家在右下角一眼就能看到，
         * 且不与任何信息槽 / 预留槽 / 指示槽 / IO 槽重叠。
         *
         * <p>⚠ 不能取槽 41 —— 那一格是 {@code INDEX_SLOTS[4]}（4 号预留槽的指示槽），
         * 抢了它会让 {@link #verifySkeleton()} 在类初始化时直接抛异常，
         * <b>表现为整个插件启用失败</b>（本次实测踩过）。
         *
         * <p>需求里 45 格布局的"禁用一切交互"约束不受影响：
         * 这一格仍然是<b>锁死的按钮</b>（{@link GuiLock#button}），
         * 图标拿不走、东西放不进，玩家只能点它切开关。
         */
        HOLOGRAM
    }
    /**
     * <b>唯一的</b>骨架 —— 逐格抄自需求，槽号 = 行*9 + 列。
     *
     * <pre>
     *   行0:  X  X  X  X  1  i  X  X  X
     *   行1:  X  X  0  i  X  X  2  i  X
     *   行2:  X  X  X  I  IO S  X  X  X
     *   行3:  X  X  5  i  X  X  3  i  X
     *   行4:  X  X  X  X  4  i  H  X  X
     * </pre>
     *
     * <p>{@code RESERVED} 是"预留接口"，数字是<b>序号</b>：
     * 0→槽11、1→槽4、2→槽15、3→槽33、4→槽40、5→槽29
     * （从左上开始顺时针，与 spec 的 1~6 标注一致，只是序号改成从 0 起）。
     * 每个序号后面紧跟一个 {@code INDEX}（下标 = 预留槽 + 1）。
     *
     * <p>★ {@code 行4 列7}（槽 42）原本是 {@code X} 占位符，现在是
     * {@link GridCell#HOLOGRAM}（多方块投影开关）—— 这是本骨架相对需求原文
     * <b>唯一</b>的一处偏移，见 {@link #HOLOGRAM_SLOT}。
     */
    private static final GridCell[][] SKELETON = {
            {GridCell.BORDER, GridCell.BORDER, GridCell.BORDER, GridCell.BORDER, GridCell.RESERVED,
                    GridCell.INDEX, GridCell.BORDER, GridCell.BORDER, GridCell.BORDER},
            {GridCell.BORDER, GridCell.BORDER, GridCell.RESERVED, GridCell.INDEX, GridCell.BORDER,
                    GridCell.BORDER, GridCell.RESERVED, GridCell.INDEX, GridCell.BORDER},
            {GridCell.BORDER, GridCell.BORDER, GridCell.BORDER, GridCell.INFO, GridCell.IO,
                    GridCell.CORE_POS, GridCell.BORDER, GridCell.BORDER, GridCell.BORDER},
            {GridCell.BORDER, GridCell.BORDER, GridCell.RESERVED, GridCell.INDEX, GridCell.BORDER,
                    GridCell.BORDER, GridCell.RESERVED, GridCell.INDEX, GridCell.BORDER},
            {GridCell.BORDER, GridCell.BORDER, GridCell.BORDER, GridCell.BORDER, GridCell.RESERVED,
                    GridCell.INDEX, GridCell.HOLOGRAM, GridCell.BORDER, GridCell.BORDER}
    };

    private static final int ROWS = 5;
    private static final int COLS = 9;
    /** 5 × 9 = 45 格。 */
    private static final int INVENTORY_SIZE = ROWS * COLS;

    /** 信息槽（POWER 量 + 激活状态 + 运作结论，点击激活）= 第 3 行第 4 列。 */
    public static final int INFO_SLOT = 21;
    /**
     * 混合型输入输出端 = 第 3 行第 5 列（外界物流可进可出）。
     *
     * <p>★ 它<b>同时</b>是这台机器的产物输出槽（需求第 5 条：
     * 「输出槽取赛钱箱 GUI 的 IO 槽（下标 22）」）。产物进出都走这一格，
     * 外界物流（Cargo）也就能直接把它抽走。
     */
    public static final int IO_SLOT = 22;
    /** 信息槽（多方块核心位置）= 第 3 行第 6 列。 */
    public static final int CORE_POS_SLOT = 23;

    /**
     * <b>多方块投影开关</b> = 第 5 行第 7 列（槽 42）。
     *
     * <p>见 {@link GridCell#HOLOGRAM}：它是骨架里唯一被改动过的一格
     * （原来是 {@code X} 占位符）。开关状态持久化在方块数据的
     * {@link MultiBlockProjection#KEY_HOLOGRAM}，与反应堆共用同一个键 ——
     * 两台机器不在同一个方块上，键名相同不会互相覆盖，
     * 反而让"所有多方块核心的投影开关都在这一个键下"这件事一眼可见。
     */
    public static final int HOLOGRAM_SLOT = 42;

    /**
     * 预留槽的槽位（<b>下标 = 序号</b>）。
     *
     * <p>顺序即编号：{@code RESERVED_SLOTS[i]} 就是"第 i 号预留槽"，
     * 它由"第 i 号木桩"供料（编号顺序见 {@link SaizenbakoStructure}）。
     * 单独写一份是为了给构造 GUI、自检、镜像三处提供<b>单一数据源</b>
     * （{@link #verifySkeleton()} 会核对它与骨架一致）。
     */
    public static final int[] RESERVED_SLOTS = {11, 4, 15, 33, 40, 29};

    /**
     * 序号指示槽的槽位（下标 = 序号）—— 逐个放在对应预留槽的<b>右边那一格</b>。
     *
     * <p>★ 刻意写成"由 {@link #RESERVED_SLOTS} 加 1 推导"，而不是再抄一份常量：
     * 抄一份就会出现"某个槽被挪了位置、指示槽没跟着挪"的漂移，
     * 而那种错误在界面上一眼看不出来（指示槽只是块玻璃板）。
     */
    public static final int[] INDEX_SLOTS = deriveIndexSlots();

    /** Cargo 能对本方块进出货的槽位（只有 IO 一格）。 */
    private static final int[] TRANSPORT_SLOTS = {IO_SLOT};

    static {
        verifySkeleton();
    }

    // ---------------------------------------------------------------- 构造

    public Saizenbako(ItemGroup itemGroup, SlimefunItemStack item,
                      RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemSetting(capacity);

        // GUI：本体的 preset 以 id 取用，后注册的覆盖先注册的，
        //      所以子类自己再 new 一个是接管的正确方式（与 UtsuhoReactorCore 同款写法）。
        new BlockMenuPreset(getId(), inventoryTitle()) {
            @Override
            public void init() {
                Saizenbako.this.constructMenu(this);
            }

            /**
             * ★ 这里才有真实方块 —— 所有与坐标相关的内容都放在这个钩子里。
             *
             * <p>{@code BlockMenuPreset#clone} 在界面（重建）时调用它，
             * 时机在"方块数据已加载、图标已铺好"之后，而且是主线程
             * （{@code BlockMenuPreset.newInstance(BlockMenu, Location)} 内部走
             * {@code Slimefun.runSync}），所以可以放心读写方块数据。
             */
            @Override
            public void newInstance(BlockMenu menu, Block b) {
                Saizenbako.this.onMenuCreated(menu, b);
            }

            @Override
            public boolean canOpen(Block b, Player p) {
                return p.hasPermission("slimefun.inventory.bypass")
                        || (canUse(p, false) && Slimefun.getProtectionManager()
                                .hasPermission(p, b.getLocation(), Interaction.INTERACT_BLOCK));
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                // ★ 混合型端口：进和出都走 IO_SLOT（需求明确要求"可被外界物流输入输出"）。
                //   ★ 6 个预留槽【不在】这张表里 —— 这是"物流也碰不到预留槽"的保证：
                //     Cargo 只会拿到 IO 一格，镜像槽对物流根本不存在。
                return TRANSPORT_SLOTS.clone();
            }
        };

        // ticker / 放置 / 破坏三个 handler 都在 AbstractPowerBlock 的构造器里挂好了，
        // 本类不再重复注册 —— 重复注册会让网络在同一 tick 被 tick 两次。
    }

    /** 界面标题。 */
    protected String inventoryTitle() {
        return "&6赛钱箱";
    }

    // ---------------------------------------------------------------- PowerComponent

    /**
     * 节点类型：<b>储能</b>。
     *
     * <p>照 {@link com.example.touhou.power.PowerStorageUnit} 取 {@code STORAGE} ——
     * 赛钱箱是"攒钱（攒电）"的箱子，语义上就是储能点，参与网络的按容量比例均衡。
     * ⚠ 它<b>不是</b> {@code INTEGRATED_CORE}：那需要带来"锚点 + 半径跳接 +
     * 与原生电网桥接"三件事，本方块一件都不做（spec 也没要求）。
     */
    @Override
    public NodeType powerType() {
        return NodeType.STORAGE;
    }

    @Override
    public long powerCharge(Location loc) {
        return TouhouData.getLong(loc, KEY_CHARGE, 0L);
    }

    /**
     * 写入电量 —— <b>由 POWER 网络在均衡时调用</b>（{@link PowerNetwork#settle}），
     * 以及本机器的运作扣电（{@link SaizenbakoManager}）。
     *
     * <p>与 {@link com.example.touhou.power.PowerStorageUnit} 一样自行 clamp 到
     * {@code [0, capacity]}：网络那边虽然也会 clamp，但"实现方自己保证不变量"
     * 更稳（例如将来有人从命令直接写电量也不会越界）。
     */
    @Override
    public void powerSetCharge(Location loc, long charge) {
        long cap = powerCapacity(loc);
        TouhouData.setLong(loc, KEY_CHARGE, Math.max(0, Math.min(charge, cap)));
    }

    @Override
    public long powerCapacity(Location loc) {
        return configuredCapacity();
    }

    /**
     * 配置里的额定容量（不依赖方块位置）。
     *
     * <p>抽出来是为了让自检/命令也能在不持有 Location 的情况下报出"这台机器容量多少"。
     * {@code ItemSetting} 在注册前可能是 null（或 value 为 null），一律兜底回默认值。
     */
    public long configuredCapacity() {
        Integer v = capacity == null ? null : capacity.getValue();
        return Math.max(0, v == null ? DEFAULT_CAPACITY : v);
    }

    // ---------------------------------------------------------------- GuiShiftGuard

    /**
     * Shift 快速移动只允许进 {@link #IO_SLOT}。
     *
     * <p>★ 为什么必须自己接管：从<b>玩家背包</b>里 Shift 点击时，
     * 事件里的 rawSlot 指向玩家背包，逐槽 handler 完全拦不到；
     * 原版会把这堆物品塞进界面里<b>第一个空槽</b> —— 而本界面唯一的空槽
     * 就是 6 个预留槽，于是物品会直接落进"只读镜像"格子里。
     * 详见 {@link PortGuiListener} 的 Shift 分支。
     */
    @Override
    public int[] shiftInsertSlots() {
        return TRANSPORT_SLOTS.clone();
    }

    // ---------------------------------------------------------------- POWER tick 钩子

    /**
     * 每 tick 一次（{@link AbstractPowerBlock} 已经让网络先结算过）。
     *
     * <p>机器逻辑全在 {@link SaizenbakoManager#tick}（未激活时它只刷新界面）。
     * ⚠ 异常一旦抛出去就会把 ticker 打死（这台机器从此不再参与网络均衡），
     * 所以整个方法体兜一层 —— 界面刷新失败绝不能连累供电功能。
     */
    @Override
    protected void onPowerTick(Block b) {
        try {
            SaizenbakoManager.tick(b, StorageCacheUtils.getMenu(b.getLocation()));
        } catch (RuntimeException e) {
            com.example.touhou.Touhou.getInstance().getLogger()
                    .warning("[赛钱箱] tick 异常 @ " + TouhouData.xyz(b.getLocation()) + ": " + e);
        }
    }

    /**
     * 核心被拆：清木桩编号与结构盖章。
     *
     * <p>木桩自己不做结构判定，所以"核心没了"必须由这里主动通知它们
     * （否则木桩界面上会一直挂着"已绑定核心"的旧编号）。
     */
    @Override
    protected void onPowerRemoved(Block b) {
        try {
            SaizenbakoManager.onCoreRemoved(b.getLocation());
        } catch (RuntimeException e) {
            com.example.touhou.Touhou.getInstance().getLogger()
                    .warning("[赛钱箱] 拆除清理异常 @ " + TouhouData.xyz(b.getLocation()) + ": " + e);
        }
    }

    // ---------------------------------------------------------------- 骨架 / 分类

    /** 取某一格的语义（越界返回 {@code null}）。 */
    public static GridCell cellAt(int slot) {
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return null;
        }
        return SKELETON[slot / COLS][slot % COLS];
    }

    /** 某一格渲染成需求里的哪个字符（便于逐格与 spec 对照）。 */
    public static String classify(int slot) {
        GridCell cell = cellAt(slot);
        if (cell == null) {
            return "??";
        }
        return switch (cell) {
            case BORDER -> "X";
            case RESERVED -> "R";       // 预留接口（编号 0~5）
            case INDEX -> "i";          // 序号指示槽
            case INFO -> "I";
            case IO -> "IO";
            case CORE_POS -> "S";
            case HOLOGRAM -> "H";       // 多方块投影开关
        };
    }

    /** 取骨架里某一类的全部槽位（按槽号升序）—— 构造 GUI 与自检共用。 */
    public static int[] slotsOf(GridCell cell) {
        List<Integer> list = new ArrayList<>();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (cellAt(slot) == cell) {
                list.add(slot);
            }
        }
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /** 取骨架里某一类的唯一槽位（不存在返回 -1）。 */
    public static int slotOf(GridCell cell) {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (cellAt(slot) == cell) {
                return slot;
            }
        }
        return -1;
    }

    /** 由预留槽推导序号指示槽（下标 +1）。 */
    private static int[] deriveIndexSlots() {
        int[] arr = new int[RESERVED_SLOTS.length];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = RESERVED_SLOTS[i] + 1;
        }
        return arr;
    }

    /** 序号 {@code index} 的预留槽下标（越界返回 -1）。 */
    public static int reservedSlotOf(int index) {
        return index < 0 || index >= RESERVED_SLOTS.length ? -1 : RESERVED_SLOTS[index];
    }

    /** 序号 {@code index} 的指示槽下标（越界返回 -1）。 */
    public static int indexSlotOf(int index) {
        return index < 0 || index >= INDEX_SLOTS.length ? -1 : INDEX_SLOTS[index];
    }

    /** 供命令用：5 行纯网格（每行 9 格、单空格分隔），直接与需求原文对照。 */
    public static List<String> gridOf() {
        List<String> rows = new ArrayList<>(ROWS);
        for (int row = 0; row < ROWS; row++) {
            StringBuilder sb = new StringBuilder();
            for (int col = 0; col < COLS; col++) {
                if (col > 0) {
                    sb.append(' ');
                }
                sb.append(classify(row * COLS + col));
            }
            rows.add(sb.toString());
        }
        return rows;
    }

    /**
     * 骨架自检：5×9、每格非空、各类语义都用到，且
     * 「信息 / 输入输出 / 核心位置」各恰好 1 格、预留槽恰好 6 格、
     * 序号指示槽恰好 6 格且<b>逐个紧贴</b>对应预留槽的右侧。
     *
     * <p>类初始化时跑一次；不合法直接抛异常，避免带着坏骨架上线。
     */
    private static void verifySkeleton() {
        if (SKELETON.length != ROWS) {
            throw new IllegalStateException("赛钱箱骨架不是 " + ROWS + " 行");
        }
        Map<GridCell, Integer> count = new LinkedHashMap<>();
        for (int r = 0; r < ROWS; r++) {
            if (SKELETON[r].length != COLS) {
                throw new IllegalStateException("赛钱箱骨架第 " + r + " 行不是 " + COLS + " 列");
            }
            for (int c = 0; c < COLS; c++) {
                if (SKELETON[r][c] == null) {
                    throw new IllegalStateException("赛钱箱骨架 (" + r + "," + c + ") 为空");
                }
                count.merge(SKELETON[r][c], 1, Integer::sum);
            }
        }
        for (GridCell cell : GridCell.values()) {
            if (count.getOrDefault(cell, 0) <= 0) {
                throw new IllegalStateException("赛钱箱骨架里没有用到 " + cell);
            }
        }
        if (count.getOrDefault(GridCell.INFO, 0) != 1
                || count.getOrDefault(GridCell.IO, 0) != 1
                || count.getOrDefault(GridCell.CORE_POS, 0) != 1) {
            throw new IllegalStateException("赛钱箱骨架的 信息/输入输出/核心位置 必须各恰好 1 格");
        }
        if (count.getOrDefault(GridCell.BORDER, 0) != 29) {
            // ★ 29 而不是需求原文的 30：槽 42 被挪去当投影开关了（GridCell.HOLOGRAM）
            throw new IllegalStateException("赛钱箱骨架的占位符应为 29 格，实际 "
                    + count.getOrDefault(GridCell.BORDER, 0));
        }
        if (count.getOrDefault(GridCell.HOLOGRAM, 0) != 1) {
            throw new IllegalStateException("赛钱箱骨架的投影开关应恰好 1 格，实际 "
                    + count.getOrDefault(GridCell.HOLOGRAM, 0));
        }
        if (count.getOrDefault(GridCell.RESERVED, 0) != RESERVED_SLOTS.length) {
            throw new IllegalStateException("赛钱箱骨架的预留槽应为 " + RESERVED_SLOTS.length
                    + " 格，实际 " + count.getOrDefault(GridCell.RESERVED, 0));
        }
        if (count.getOrDefault(GridCell.INDEX, 0) != INDEX_SLOTS.length) {
            throw new IllegalStateException("赛钱箱骨架的序号指示槽应为 " + INDEX_SLOTS.length
                    + " 格，实际 " + count.getOrDefault(GridCell.INDEX, 0));
        }
        // 预留槽位表必须与骨架逐格一致（这张表会被"镜像写到哪一格"用到）
        int[] fromSkeleton = slotsOf(GridCell.RESERVED);
        int[] declared = RESERVED_SLOTS.clone();
        java.util.Arrays.sort(declared);
        if (!java.util.Arrays.equals(fromSkeleton, declared)) {
            throw new IllegalStateException("预留槽位表与骨架不一致：骨架="
                    + java.util.Arrays.toString(fromSkeleton)
                    + " 表=" + java.util.Arrays.toString(declared));
        }
        // ★ 每个指示槽必须正好在对应预留槽的右边那一格（下标 +1），且骨架里那一格确实是 INDEX
        for (int i = 0; i < RESERVED_SLOTS.length; i++) {
            int want = RESERVED_SLOTS[i] + 1;
            if (INDEX_SLOTS[i] != want) {
                throw new IllegalStateException("序号 " + i + " 的指示槽下标应为 "
                        + want + "，实际 " + INDEX_SLOTS[i]);
            }
            if (cellAt(want) != GridCell.INDEX) {
                throw new IllegalStateException("序号 " + i + " 的指示槽（下标 " + want
                        + "）在骨架里不是 INDEX，而是 " + cellAt(want));
            }
            // "右边那一格"在同一行内：下标 +1 之后不能跨行（9 的倍数就是下一行了）
            if (want / COLS != RESERVED_SLOTS[i] / COLS) {
                throw new IllegalStateException("序号 " + i + " 的指示槽跨行了：预留槽 "
                        + RESERVED_SLOTS[i] + " → 指示槽 " + want);
            }
        }
        // 三个功能槽的常量必须与骨架一致
        if (slotOf(GridCell.INFO) != INFO_SLOT
                || slotOf(GridCell.IO) != IO_SLOT
                || slotOf(GridCell.CORE_POS) != CORE_POS_SLOT
                || slotOf(GridCell.HOLOGRAM) != HOLOGRAM_SLOT) {
            throw new IllegalStateException("四个功能槽常量与骨架不一致：I=" + slotOf(GridCell.INFO)
                    + " IO=" + slotOf(GridCell.IO) + " S=" + slotOf(GridCell.CORE_POS)
                    + " H=" + slotOf(GridCell.HOLOGRAM));
        }
    }

    // ---------------------------------------------------------------- GUI 构造

    /**
     * 构造界面骨架 —— <b>只在 {@code init()}（构造期）里跑，那里没有 Location</b>。
     *
     * <p>所以这里只放"与位置无关"的东西：占位符、序号指示槽、两个按钮的初始图标
     * （图标一律按 {@code loc == null} 生成）。真实内容由
     * {@link #onMenuCreated(BlockMenu, Block)} 与 ticker 刷新。
     */
    private void constructMenu(BlockMenuPreset preset) {
        preset.setSize(INVENTORY_SIZE);

        // ★ 一律通过 GuiLock 注册：默认全部锁死，只有显式声明的真实槽可交互。
        final GuiLock lock = GuiLock.wrap(preset);

        // 需求：IO 是混合型输入输出端 —— 它是本界面唯一的"真实槽"
        //（玩家手动放取 + Cargo 进出货 + 机器产物输出都走它）
        lock.markRealSlot(IO_SLOT);

        // X：占位符（灰色玻璃板，显示「少女祈祷中」）
        for (int slot : slotsOf(GridCell.BORDER)) {
            lock.addItem(slot, borderIcon());
        }

        // i：序号指示槽 —— 显示相邻预留槽的序号 0~5，锁死
        for (int i = 0; i < INDEX_SLOTS.length; i++) {
            lock.addItem(INDEX_SLOTS[i], indexIcon(i));
        }

        // 预留槽 0~5：★ 刻意【不放任何图标】—— 需求要求"保持真正为空"。
        //   所以这里连 addItem 都不调（调 addItem(slot, null) 会把 ItemStack 传成 null，
        //   那属于未定义用法：菜单克隆 / 序列化路径都可能对它 NPE）。
        //   锁由构造器末尾的 autoGuard() 负责 —— GuiLock 的安全网会给"既不是真实槽、
        //   也不是按钮"的每一格补上锁死 handler，所以空格子在功能上依然塞不进东西。
        //   ✓ 想核对这一点：/touhou gui <x> <y> <z> 的报告里，可放取槽应恰好只有 1 个（IO）。

        // I：信息槽（POWER 量 + 激活状态 + 运作结论）—— ★ 点击 = 现场检测并激活
        lock.button(INFO_SLOT, buildInfoIcon(null), (p, e) -> handleInfoClick(p, e));

        // S：信息槽（多方块核心位置 + 编号↔槽位映射）—— 点击 = 打印结构明细
        lock.button(CORE_POS_SLOT, buildCorePosIcon(null), (p, e) -> handleCorePosClick(p, e));

        // H：多方块投影开关（槽 42 = 第 5 行第 7 列，原本是 X 占位符）
        lock.button(HOLOGRAM_SLOT, buildHologramIcon(null), (p, e) -> handleHologramClick(p, e));

        // 安全网：除 IO 真实槽与四个按钮外，其余 41 格（29 占位 + 6 预留 + 6 指示）全部锁死
        lock.autoGuard();
        this.guiLock = lock;
    }

    /** 供命令自检用：本方块最后构建出来的 GUI 注册器。 */
    private transient GuiLock guiLock;

    /** 当前这台赛钱箱 GUI 的锁槽自检报告（未构建过返回 null）。 */
    public GuiLock guiLock() {
        return guiLock;
    }

    /** X：占位符 —— 灰色玻璃板，显示「少女祈祷中」，不可取出、不可放入。 */
    private static ItemStack borderIcon() {
        return new CustomItemStack(Material.GRAY_STAINED_GLASS_PANE, "&7少女祈祷中");
    }

    /**
     * i：序号指示槽 —— 显示"左边那一格预留槽"的序号。
     *
     * <p>用白色玻璃板而不是纸张：它属于界面边框的一部分，
     * 看起来就不像"能拿走的东西"（虽然它同样被锁死）。
     */
    private static ItemStack indexIcon(int index) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7这一格显示左边预留槽的序号");
        lore.add("&7该预留槽由 &f木桩 #" + index + " &7供料");
        lore.add("&8（木桩编号顺序：先 +X 方向、后 +Z 方向）");
        lore.add("");
        lore.add("&8本格锁死：不可放入、不可取出");
        return new CustomItemStack(Material.WHITE_STAINED_GLASS_PANE, "&e序号 &f" + index, lore);
    }

    // ---------------------------------------------------------------- 界面动态内容

    /**
     * 界面（重）建好之后的钩子 —— <b>这里才有真实方块</b>。
     *
     * <p>做两件事：
     * <ol>
     *   <li>按真实位置刷新两个信息槽的图标文案；</li>
     *   <li>已激活时立刻做一次镜像 —— 玩家一打开界面就该看到最新投料，
     *       而不是等下一轮运作（最多 1 秒）才刷新。</li>
     * </ol>
     * ⚠ 整个方法兜异常：界面重建失败绝不能连累方块本身（这是 Slimefun 的
     * {@code newInstance} 回调，异常会被它记成物品错误）。
     */
    private void onMenuCreated(BlockMenu menu, Block b) {
        try {
            Location loc = b == null ? null : b.getLocation();
            if (loc == null || menu == null) {
                return;
            }
            menu.replaceExistingItem(INFO_SLOT, buildInfoIcon(loc));
            menu.replaceExistingItem(CORE_POS_SLOT, buildCorePosIcon(loc));
            SaizenbakoManager.mirrorNow(loc);
        } catch (RuntimeException e) {
            com.example.touhou.Touhou.getInstance().getLogger()
                    .warning("[赛钱箱] 界面初始化异常 @ "
                            + (b == null ? "(null)" : TouhouData.xyz(b.getLocation())) + ": " + e);
        }
    }

    // ---------------------------------------------------------------- 信息槽图标

    /**
     * I：信息槽 —— POWER 量 + 激活状态 + 最近一次运作结论。
     *
     * <p>电量读两处，因为这两个数回答的是不同的问题：
     * <ul>
     *   <li><b>本机</b>（{@link #powerCharge}）：这台赛钱箱此刻存了多少
     *       —— 机器扣的就是这个数，所以它才是"还能做几次"的答案；</li>
     *   <li><b>网络</b>（{@link PowerNetwork#lastTotalCharge()}）：整张网此刻一共多少
     *       —— POWER 是按容量比例均衡的，本机容量只有 5，所以网络总量才是有意义的背景信息。</li>
     * </ul>
     * ★ 必须能在 {@code loc == null} 时安全返回：本方法会被 GUI 的<b>构造期</b>调用。
     */
    public static ItemStack buildInfoIcon(Location loc) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (loc == null) {
            lore.add("&7本机 POWER： &8(定位失败)");
        } else {
            Charge charge = chargeInfo(loc);
            lore.add("&7本机 POWER： &e" + fmt(charge.charge())
                    + " &7/ &e" + fmt(charge.capacity())
                    + " &8（每次运作 " + Math.max(0, AddonConfig.get().saizenPowerCost) + "）");
            lore.add("&8（按容量比例参与网络均衡）");
            PowerNetwork net = PowerNetworkManager.getNetworkFromLocationOrCreate(loc);
            if (net == null) {
                lore.add("&7网络 POWER： &8未组网");
                lore.add("&8（把 POWER 方块贴到本方块上即可并网）");
            } else {
                lore.add("&7网络 POWER： &e" + fmt(net.lastTotalCharge())
                        + " &7/ &e" + fmt(net.lastTotalCapacity()));
                lore.add("&8网络 #" + net.networkId() + " · 节点 " + net.size());
            }
        }
        lore.add("");
        lore.add(StructureState.stateLine(loc));
        lore.add("&7多方块结构： &f" + structureLine(loc));
        lore.add("&7最近一次： &f" + SaizenbakoManager.lastNote(loc));
        lore.add("");
        lore.add("&e点击本格：现场检测结构并激活");
        lore.add("&8未通过时会告诉你缺在哪一格");
        return new CustomItemStack(Material.REDSTONE_TORCH, "&e赛钱箱状态", lore);
    }

    /** S：信息槽 —— 多方块核心位置 + 编号↔槽位映射。 */
    public static ItemStack buildCorePosIcon(Location loc) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        Location core = loc == null ? null : StructureState.coreOf(loc);
        lore.addAll(StructureState.coreLine(loc, core));
        if (loc != null) {
            lore.add("&7本方块坐标： &f" + StructureState.xyz(loc));
        }
        lore.add("");
        lore.add("&76 个预留槽 ← 木桩编号");
        for (int i = 0; i < RESERVED_SLOTS.length; i++) {
            lore.add("&7  #" + i + " 预留槽 &f" + RESERVED_SLOTS[i]
                    + " &8(指示槽 " + INDEX_SLOTS[i] + ")");
        }
        lore.add("");
        lore.add("&e点击本格：打印结构明细与木桩定位");
        return new CustomItemStack(Material.COMPASS, "&e核心位置", lore);
    }

    /** 结构那一行的文本（只读持久化的状态，不做现场检测 —— 界面刷新不能扫世界）。 */
    private static String structureLine(Location loc) {
        if (loc == null) {
            return "(定位失败)";
        }
        String dir = TouhouData.getString(loc, TouhouData.KEY_DIRECTION, null);
        String dirText = "未记录";
        if (dir != null && !dir.isBlank()) {
            try {
                dirText = ReactorStructure.Direction.fromInt(Integer.parseInt(dir.trim())).label();
            } catch (NumberFormatException ignored) {
                dirText = "已损坏(" + dir + ")";
            }
        }
        return (StructureState.activeAt(loc) ? "已激活" : "未激活（待点击激活）")
                + "  朝向 " + dirText;
    }

    /** 本机电量与容量（{@link #buildInfoIcon} 用的小载体）。 */
    private record Charge(long charge, long capacity) {
    }

    /**
     * 读本机电量与容量。
     *
     * <p>优先问方块本体（走 {@link PowerComponent} 的读法，保证与网络看到的是同一个数）；
     * 方块本体不是赛钱箱（正在被替换/移除）时退回直接读方块数据。
     */
    private static Charge chargeInfo(Location loc) {
        var item = me.mrCookieSlime.Slimefun.api.BlockStorage.check(loc);
        if (item instanceof Saizenbako s) {
            return new Charge(s.powerCharge(loc), s.powerCapacity(loc));
        }
        return new Charge(TouhouData.getLong(loc, KEY_CHARGE, 0L), DEFAULT_CAPACITY);
    }

    /** 刷新信息槽（点击后的同步刷新；界面不存在时什么都不做）。 */
    private static void refreshGui(Location loc, BlockMenu inv) {
        if (inv == null || loc == null) {
            return;
        }
        try {
            inv.replaceExistingItem(INFO_SLOT, buildInfoIcon(loc));
            inv.replaceExistingItem(CORE_POS_SLOT, buildCorePosIcon(loc));
            inv.replaceExistingItem(HOLOGRAM_SLOT, buildHologramIcon(loc));
        } catch (RuntimeException e) {
            // 不能因为一次界面刷新异常把 POWER 的 ticker 打死
            com.example.touhou.Touhou.getInstance().getLogger()
                    .warning("[赛钱箱] GUI 刷新异常 @ " + TouhouData.xyz(loc) + ": " + e);
        }
    }

    // ---------------------------------------------------------------- 按键

    /**
     * I 点击：<b>这台机器唯一的激活入口</b>。
     *
     * <p>点一下 = 一次<b>现场</b>结构检测（四个朝向都试一遍）：
     * <ul>
     *   <li>通过 ⇒ 激活 + 给 6 根木桩编号 + 立刻镜像投料；</li>
     *   <li>不通过 ⇒ 什么都不改，并把"缺在哪 / 错在哪"直接告诉玩家。</li>
     * </ul>
     * 已激活后再点 = 重新检测一遍（幂等：结构还在就保持激活状态）。
     */
    private void handleInfoClick(Player p, InventoryClickEvent event) {
        Location loc = locateSelf(p, event);
        if (loc == null) {
            Notify.warn(Notify.saizen(), p, "&c无法定位这台祭坛，请关掉界面后重新右键打开");
            return;
        }
        SaizenbakoManager.Activation act = SaizenbakoManager.activate(loc);
        if (act.success()) {
            // ★ 用 info 而不是 important：祭坛的档位默认是 off，
            //   激活结果在 GUI 信息格里本来就看得见，不必再刷聊天栏。
            Notify.info(Notify.saizen(), p, act.message());
        } else {
            Notify.warn(Notify.saizen(), p, act.message());
        }
        refreshGui(loc, StorageCacheUtils.getMenu(loc));
    }

    /** S 点击：打印结构明细（不改任何状态）。 */
    private void handleCorePosClick(Player p, InventoryClickEvent event) {
        Location loc = locateSelf(p, event);
        if (loc == null) {
            Notify.warn(Notify.saizen(), p, "&c无法定位这台祭坛，请关掉界面后重新右键打开");
            return;
        }
        for (String line : SaizenbakoManager.describe(loc)) {
            Notify.info(Notify.saizen(), p, "&7" + line);
        }
        refreshGui(loc, StorageCacheUtils.getMenu(loc));
    }

    /**
     * H 点击：切换这台祭坛的<b>多方块投影</b>并刷新。
     *
     * <p>与反应堆那边是同一条链路（{@link #toggleProjection} →
     * {@link MultiBlockProjection#toggle}），所以"先清旧组再画 / 结构不完整不给开 /
     * 四向对称强制按 NORTH"这些规则两台机器完全一致 —— 一份机制、两处入口。
     */
    private void handleHologramClick(Player p, InventoryClickEvent event) {
        Location loc = locateSelf(p, event);
        if (loc == null) {
            Notify.warn(Notify.saizen(), p, "&c无法定位这台祭坛，请关掉界面后重新右键打开");
            return;
        }
        toggleProjection(p, loc);
        refreshGui(loc, StorageCacheUtils.getMenu(loc));
    }

    /**
     * <b>赛钱箱的多方块投影宿主</b> —— "用哪套结构"与"显示什么图标"的绑定。
     *
     * <p>赛钱箱这座结构<b>不对称</b>（层图里 C 在最右一列，转 90° 必然出界），
     * 所以朝向不会被强制成 NORTH，四个朝向各画各的 —— 与结构校验的行为一致。
     */
    public static ReactorStructure.ProjectionHost projectionHost() {
        return ReactorStructure.ProjectionHost.of(SaizenbakoStructure.get(),
                dir -> displayMapping());
    }

    /**
     * <b>投影开关</b>（GUI 与控制台命令共用同一条链路）。
     *
     * @param p   触发者；{@code null} = 控制台（反馈改走 {@code Log.command}）
     * @param loc 核心位置
     * @return 切换后是否处于"开"的状态
     */
    public static boolean toggleProjection(Player p, Location loc) {
        if (loc == null) {
            return false;
        }
        boolean on = MultiBlockProjection.toggle(loc, projectionHost(),
                line -> notifyProjection(p, line), false);
        if (on) {
            Log.info("[赛钱箱] 开启投影 @ " + TouhouData.xyz(loc)
                    + " 构件 " + MultiBlockProjection.lastCellCount() + " 格");
        }
        return on;
    }

    /**
     * 投影反馈。
     *
     * <p>★ 用 {@link Notify#warn} 而不是 {@code info}：投影开关是对玩家点击的
     * <b>直接反馈</b>，而祭坛的消息档位默认是 {@code off}（{@code info} 要求 {@code NORMAL} 档），
     * 用 info 会让"点了没反应"变成真的没反应 —— 失败原因一个字都不显示。
     * 实测踩过：结构不完整时 {@code refuse(...)} 的原因被完全静默，
     * 玩家只看到"图标没变、也没有投影"，无法判断是被拒绝了还是坏了。
     *
     * <p>另外这类消息<b>不会刷屏</b>：一次点击最多一条。
     */
    private static void notifyProjection(Player p, String text) {
        if (p != null) {
            Notify.warn(Notify.saizen(), p, text);
        } else {
            Log.command("[投影] " + Notify.plain(text));
        }
    }

    /**
     * {@code partId → 显示物品} 映射（LogiTech 的 {@code getIdMappingDisplayUse()}）。
     *
     * <p>★ 赛钱箱这座结构里有一半的格子是<b>原版方块</b>（红羊毛 / 灯笼 / 橡木原木），
     * 它们没有"物品 id"可用 —— 所以这一份映射只登记粘液构件（木桩），
     * 其余交给 {@link MultiBlockProjection#projectorOf} 的<b>通用映射</b>按
     * Material 名自动解析。这正是"结构自己只知道自己那点特殊东西"的分工。
     *
     * <p>★ 每次<b>新建一份 HashMap</b>：LogiTech 坑 #3（第 4 参是 {@code HashMap}
     * 而不是 {@code Map}）在签名层面就消掉了。
     */
    public static java.util.HashMap<String, ItemStack> displayMapping() {
        java.util.HashMap<String, ItemStack> map = new java.util.HashMap<>();
        // ★ AddItems.SHRINE_POST 在物品注册完成前是 null；这里如实跳过 ——
        //   于是那一格画出来是透明（与 LogiTech 的"映射不到就 setItemStack(null)"同一种表现），
        //   而不是把 null 塞进 HashMap 之后再在别处 NPE。
        if (AddItems.SHRINE_POST != null) {
            map.put(AddonConfig.SAIZEN_POST_ID,
                    MultiBlockProjection.glow(AddItems.SHRINE_POST));
        }
        return map;
    }

    /** H：投影开关图标（{@code SaizenbakoManager} 刷新界面时也要用它，所以是 public）。 */
    public static ItemStack buildHologramIcon(Location loc) {
        boolean on = loc != null && MultiBlockProjection.isOn(loc);
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(loc == null ? "&7投影状态： &8(定位失败)" : (on ? "&a● 已开启" : "&c○ 已关闭"));
        lore.add("&7把整座祭坛以 &f幻影方块&7 的形式投影出来");
        lore.add("&8（6 根木桩 / 红羊毛 / 灯笼 / 原木逐格显示）");
        lore.add("&7一眼看出哪一格还没搭好");
        lore.add("");
        for (String line : MultiBlockProjection.describePolicy()) {
            lore.add("&8" + line);
        }
        lore.add("");
        lore.add("&e点击切换开关");
        return named(new ItemStack(on ? Material.ITEM_FRAME : Material.GLASS_PANE),
                on ? "&b多方块投影" : "&7多方块投影", lore);
    }

    /**
     * 控制台也能走一遍"点信息格"这条链路 —— 无头验证用。
     *
     * <p>与 {@link #handleInfoClick} 调用的是<b>同一个</b> {@link SaizenbakoManager#activate}，
     * 所以控制台验证过的行为就是玩家点出来的行为。
     *
     * @param feedback 反馈文本的出口（游戏里是玩家聊天栏，控制台是日志）
     * @return 未经上色的反馈文本（带 {@code &} 代码），便于调用方判定结果
     */
    public String simulateActivateClick(Location loc, java.util.function.Consumer<String> feedback) {
        SaizenbakoManager.Activation act = SaizenbakoManager.activate(loc);
        if (feedback != null) {
            feedback.accept(ReactorManager.color(AddonConfig.get().saizenPrefix + act.message()));
        }
        // ★ 坐标无效时不能把 null 递进 StorageCacheUtils（它第一步就要 loc.getWorld()）
        refreshGui(loc, loc == null ? null : StorageCacheUtils.getMenu(loc));
        return act.message();
    }

    /**
     * 定位"当前这个菜单属于哪台赛钱箱"。
     *
     * <p>与 {@link AbstractReactorPort#locatePort} 同一条思路：
     * 先信事件带来的 holder，再退回"玩家看向的方块"。
     */
    private Location locateSelf(Player p, InventoryClickEvent event) {
        if (event != null) {
            try {
                var inv = event.getInventory();
                var holder = inv == null ? null : inv.getHolder();
                if (holder instanceof BlockMenu menu) {
                    Location l = menu.getLocation();
                    if (l != null && BlockStorage.check(l) == this) {
                        return l;
                    }
                }
            } catch (RuntimeException ignored) {
                // 掉到射线方案
            }
        }
        Block target = p.getTargetBlockExact(6);
        if (target == null) {
            return null;
        }
        Location l = target.getLocation();
        return BlockStorage.check(l) == this ? l : null;
    }

    // ---------------------------------------------------------------- 自检 / 工具

    /**
     * 自检：把 45 格布局渲染成 5×9 的图，逐格与<b>由骨架推导的期望</b>比对。
     *
     * <p>期望值不另写一份常量，直接来自 {@link #classify} —— 单一数据源。
     */
    public List<String> layoutSummary() {
        List<String> out = new ArrayList<>();
        Map<String, Integer> count = new LinkedHashMap<>();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            count.merge(classify(slot), 1, Integer::sum);
        }
        out.add("多方块核心=" + getId() + "  标题=" + inventoryTitle()
                + "  容量=" + configuredCapacity() + " POWER（默认 " + DEFAULT_CAPACITY + "）");
        out.add("尺寸 " + INVENTORY_SIZE
                + " | X占位=" + count.getOrDefault("X", 0)
                + " | R预留=" + count.getOrDefault("R", 0)
                + " | i指示=" + count.getOrDefault("i", 0)
                + " | I信息=" + count.getOrDefault("I", 0)
                + " | IO输入输出=" + count.getOrDefault("IO", 0)
                + " | S核心位置=" + count.getOrDefault("S", 0)
                + " | 合计=" + count.values().stream().mapToInt(Integer::intValue).sum());
        for (int row = 0; row < ROWS; row++) {
            StringBuilder sb = new StringBuilder();
            for (int col = 0; col < COLS; col++) {
                sb.append(String.format("%-4s", classify(row * COLS + col))).append('|');
            }
            out.add("  行" + row + " " + sb);
        }
        out.add("  常量 I=" + INFO_SLOT + " IO=" + IO_SLOT + " S=" + CORE_POS_SLOT);
        for (String line : slotMapping()) {
            out.add("  " + line);
        }
        out.add("  物流 " + (TRANSPORT_SLOTS.length == 1 && TRANSPORT_SLOTS[0] == IO_SLOT
                ? "OK Cargo 可对槽 " + IO_SLOT + " 进出货（INSERT + WITHDRAW）；6 个预留槽都不在表里"
                : "FAIL 物流槽位不是唯一的 IO 槽"));
        return out;
    }

    /**
     * 序号 ↔ 预留槽 ↔ 指示槽 的映射表（命令与自检共用，<b>单一数据源</b>）。
     */
    public static List<String> slotMapping() {
        List<String> out = new ArrayList<>();
        StringBuilder head = new StringBuilder("序号 → 预留槽 → 指示槽 ： ");
        for (int i = 0; i < RESERVED_SLOTS.length; i++) {
            if (i > 0) {
                head.append(" / ");
            }
            head.append(i).append("→").append(RESERVED_SLOTS[i]).append("→").append(INDEX_SLOTS[i]);
        }
        out.add(head.toString());
        out.add("  预留槽顺序（从左上开始顺时针）=" + java.util.Arrays.toString(RESERVED_SLOTS)
                + "  指示槽=" + java.util.Arrays.toString(INDEX_SLOTS) + "（= 预留槽 + 1）");
        return out;
    }

    /** 千分位格式（与 POWER 悬浮字同一套写法）。 */
    private static String fmt(long v) {
        return String.format("%,d", Math.max(0, v));
    }

    /**
     * 给物品设置显示名与 lore（与 {@code UtsuhoReactorCore#named} 同一套写法）。
     *
     * <p>刻意手写而不用 {@code CustomItemStack} 的便捷构造器：那些构造器只收
     * {@code Material}，会把物品原有的 meta 弄丢。直接改 {@code ItemMeta}
     * 能保留原物品的一切。
     */
    private static ItemStack named(ItemStack item, String name, List<String> lore) {
        ItemStack out = item.clone();
        org.bukkit.inventory.meta.ItemMeta meta = out.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ReactorManager.color(name));
            if (!lore.isEmpty()) {
                List<String> colored = new ArrayList<>(lore.size());
                for (String line : lore) {
                    colored.add(ReactorManager.color(line));
                }
                meta.setLore(colored);
            }
            out.setItemMeta(meta);
        }
        return out;
    }
}
