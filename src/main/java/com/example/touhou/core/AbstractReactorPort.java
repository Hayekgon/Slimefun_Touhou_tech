package com.example.touhou.core;

import com.example.touhou.Touhou;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.operations.FuelOperation;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反应堆物流接口的<b>公共基类</b>。
 *
 * <p>接口原本是**一个**物品（同时管进和出）。现在拆成两个：
 * <ul>
 *   <li>{@link ReactorInputPort} —— 红色染色玻璃，<b>只负责进料</b>：
 *       本接口输入槽 → 核心输入槽；</li>
 *   <li>{@link ReactorOutputPort} —— 蓝色染色玻璃，<b>只负责出料</b>：
 *       核心输出槽 → 本接口输出槽。</li>
 * </ul>
 * 两者都能<b>替代保护罩</b>参与搭建（共用标签 {@code touhou:reactor_shell}），
 * 并且可以<b>同时</b>出现在同一座反应堆里（一个管进、一个管出）；
 * 但<b>各自</b>最多一个（config {@code structure.unique-parts}）。
 *
 * <h2>公共行为（都在本类）</h2>
 * <ol>
 *   <li><b>右键映射核心 GUI</b>：直接右键 → 打开代理核心的 {@code BlockMenu}
 *       （与 LogiTech 超链接同款：过 {@code canUse} 与 {@code canOpen} 两道权限门槛）；</li>
 *   <li><b>Shift+右键</b> → 打开接口<b>自己的</b>界面（看本接口库存、按中止按钮）；</li>
 *   <li><b>低频搬运</b>：每 {@code reactor.io.interval-seconds}（默认 5 秒）一次，
 *       方向由子类给定；只在该核心<b>已激活</b>时搬运；</li>
 *   <li><b>中止按钮 K</b>：强制结束核心当前进程并立刻开启下一轮进程识别；</li>
 *   <li><b>自动识别代理核心</b>：按"附近有没有反应堆核心方块"绑定，带缓存与失效重扫；</li>
 *   <li><b>不存电</b>：普通 {@code SlimefunItem}，不是 {@code EnergyNetComponent}。</li>
 * </ol>
 *
 * <h2>★ 容器归属：接口有【自己的】容器（不是核心的容器）</h2>
 * <p>曾经试过用反射把核心的 {@code BlockMenu} 对象<b>塞进</b>接口那一格
 * （{@code SlimefunBlockData#setBlockMenu}），让 Cargo 零延迟直接读写核心库存。
 * <b>已放弃</b>，原因两条：
 * <ol>
 *   <li>{@code BlockDataController.saveAllBlockInventories()} 是<b>按方块位置</b>遍历的，
 *       库存快照 {@code invSnapshots} 也<b>按位置</b>为键。两格指向同一个菜单对象时，
 *       接口那一格会命中同一条 {@code menu.isDirty()} 判定并被一起保存 ——
 *       等于让同一份库存挂上两条落盘路径，有复制库存的风险；</li>
 *   <li>spec 的语义本来就是"接口把输入槽的物品<b>转入</b>核心的输入槽"——
 *       那是两个容器之间的搬运。共用一个容器的话这句话没有意义。</li>
 * </ol>
 * 现在接口持有自己的 {@link BlockMenu}，Cargo 对着接口的输入/输出槽进出货
 * （见 {@link BlockMenuPreset#getSlotsAccessedByItemTransport}），
 * 再由搬运循环与核心对搬。代价是物品最多在接口里停留一个搬运周期。
 *
 * <p>⚠ 主线程调用（BlockTicker / 右键事件都在主线程）。
 */
public abstract class AbstractReactorPort extends SlimefunItem {

    // ---------------------------------------------------------------- GUI 布局
    //
    // 6×9 全槽位布局（goal_3.txt）。两个接口骨架完全相同，区别只在主槽区的角色：
    //
    //   | X | X | X | I | X | S | X | C | X |     行0：按键行
    //   | X | O | O | O | O | O | O | O | O |     行1┐
    //   | X | O | O | O | O | O | O | O | O |     行2│ 主槽区
    //   | X | O | O | O | O | O | O | O | O |     行3│ 32 个
    //   | X | O | O | O | O | O | O | O | O |     行4┘
    //   | I | I | I | I | I | I | I | I | I |     行5：槽位标识占位符
    //
    //   X = 占位玻璃板（不可交互）
    //   I = 第 0 行是【核心信息/状态/坐标】（点它 = 结构检测 + 手动激活）；
    //       第 5 行是【输入/输出槽标识占位符】（用原生粘液自带的输入/输出槽纹理）
    //   S = 切换核心的工作模式（发电 / 生产）
    //   C = 手动激活按钮（激活反应堆）
    //   O = 主槽区。输入口 = 输入槽（把这里的物品转入核心输入槽）；
    //                        输出口 = 输出槽（把核心输出槽的物品转入这里）
    //
    //   ★ 两个接口的骨架**完全一致**（连行5 的标识占位符都在同一批槽位上），
    //     只有主槽区的"槽类纹理"按角色不同（输入口用输入槽纹理、输出口用输出槽纹理）。
    //     这样不会再出现"两套骨架互相打架"的问题。

    /**
     * 骨架里每一格在新需求（goal_3）里的语义。
     */
    public enum GridCell {
        /** 占位玻璃板（需求里的 X）。 */
        BORDER,
        /** 核心信息 / 状态 / 坐标（需求第 0 行里的 I），点它 = 结构检测 + 手动激活。 */
        INFO,
        /** 切换核心工作模式（需求里的 S）。 */
        MODE,
        /** 手动激活按钮（需求里的 C）。 */
        ACTIVATE,
        /** 主槽区（需求里的 O）—— 输入口当作输入槽、输出口当作输出槽。 */
        SLOT,
        /** 槽位标识占位符（需求第 5 行的 I）—— 装饰，不可交互。 */
        MARKER
    }

    /**
     * 接口角色：决定主槽区是"输入槽"还是"输出槽"，以及搬运方向。
     *
     * <p>★ 与 {@link GridCell} 分开：骨架已经统一（两个接口完全一样），
     * 角色只影响"主槽区叫什么、往哪个方向搬"。上一版把两者混在一个枚举里，
     * 结果骨架一改就有半数值对不上。
     */
    public enum PortRole {
        /** 输入接口：主槽区的物品转入核心输入槽。 */
        INPUT,
        /** 输出接口：核心输出槽的物品转入主槽区。 */
        OUTPUT
    }

    /**
     * <b>唯一的</b>骨架 —— 逐格抄自 goal_3.txt，槽号 = 行*9 + 列。
     *
     * <p>权威分解（54 格）：
     * <pre>
     *   X  n=10  0,1,2,4,6,8, 9,18,27,36
     *   I  n=1   3        （核心信息 / 激活）
     *   S  n=1   5
     *   C  n=1   7
     *   O  n=32  10,11,12,13,14,15,16,17, 19..26, 28..35, 37..44
     *   I  n=9   45..53   （槽位标识占位符）
     * </pre>
     */
    private static final GridCell[][] SKELETON = {
            {GridCell.BORDER, GridCell.BORDER, GridCell.BORDER, GridCell.INFO, GridCell.BORDER,
                    GridCell.MODE, GridCell.BORDER, GridCell.ACTIVATE, GridCell.BORDER},
            {GridCell.BORDER, GridCell.SLOT, GridCell.SLOT, GridCell.SLOT, GridCell.SLOT,
                    GridCell.SLOT, GridCell.SLOT, GridCell.SLOT, GridCell.SLOT},
            {GridCell.BORDER, GridCell.SLOT, GridCell.SLOT, GridCell.SLOT, GridCell.SLOT,
                    GridCell.SLOT, GridCell.SLOT, GridCell.SLOT, GridCell.SLOT},
            {GridCell.BORDER, GridCell.SLOT, GridCell.SLOT, GridCell.SLOT, GridCell.SLOT,
                    GridCell.SLOT, GridCell.SLOT, GridCell.SLOT, GridCell.SLOT},
            {GridCell.BORDER, GridCell.SLOT, GridCell.SLOT, GridCell.SLOT, GridCell.SLOT,
                    GridCell.SLOT, GridCell.SLOT, GridCell.SLOT, GridCell.SLOT},
            {GridCell.MARKER, GridCell.MARKER, GridCell.MARKER, GridCell.MARKER, GridCell.MARKER,
                    GridCell.MARKER, GridCell.MARKER, GridCell.MARKER, GridCell.MARKER}
    };

    protected static final int INVENTORY_SIZE = 54;

    // ---------------------------------------------------------------- 运行期状态

    /** 接口 -> 它代理的核心位置（缓存，避免每 tick 扫 125 格）。 */
    private static final Map<Location, Location> CORE_OF = new ConcurrentHashMap<>();
    /** 接口 -> 搬运节流计数器（还剩几 tick 搬一次）。 */
    private static final Map<Location, Integer> NEXT_TRANSFER = new ConcurrentHashMap<>();
    /**
     * 已经为此接口报过"附近没有核心"了（**只报一次**）。
     *
     * <p>★ 为什么不做成"每 30 秒一条"：本机存档里就有 9 个玩家留下的孤立接口，
     * 30 秒一句 = **每分钟 18 行 WARN**，一直刷到关服，把真正的错误淹掉。
     * 接口先于核心放置是完全正常的中间状态，玩家又看不到控制台，
     * 所以正确的做法是"提醒一次 + 需要时用命令查"，而不是周期性刷屏。
     * 找到核心后会移除（将来再丢能立刻再提醒一次）。
     */
    private static final java.util.Set<Location> MISS_WARNED =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /**
     * "真的扫了世界"的累计次数（每次 = 125 次 {@code checkID}）。
     *
     * <p>★ 只用于<b>证明</b>检测次数真的降下来了：架构改成"放置/破坏事件驱动"之后，
     * 这个数应该只随玩家的建造动作增长，<b>不再随 tick 增长</b>。
     * 没有这个计数器，这类优化只能靠"感觉快了"来判断。
     */
    private static final java.util.concurrent.atomic.AtomicLong SCAN_COUNT =
            new java.util.concurrent.atomic.AtomicLong();

    /** 累计"扫世界找核心"的次数（诊断用）。 */
    public static long scanCount() {
        return SCAN_COUNT.get();
    }

    /** 当前已绑定核心的接口数（诊断用）。 */
    public static int boundCount() {
        return CORE_OF.size();
    }

    /**
     * 逐条列出"接口 → 核心"的绑定（诊断用）。
     *
     * <p>★ 为什么要列出明细而不是只报个数：{@code boundCount()} 是个"看着像 2 却是 4"的数字时，
     * 光看总数无法判断是"重复插入同一个接口"还是"世界上真有 4 个接口"。
     * 把坐标打出来，一眼就能分辨（本次实测就靠它确认了重复插入的问题）。
     */
    public static java.util.List<String> describeBindings() {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Map.Entry<Location, Location> e : CORE_OF.entrySet()) {
            out.add(xyzText(e.getKey()) + " → " + xyzText(e.getValue()));
        }
        out.sort(String::compareTo);
        return out;
    }

    private static String xyzText(Location loc) {
        if (loc == null) {
            return "(null)";
        }
        // ★ 必须带上世界名：只打 x,y,z 的话，"不同世界的两个接口"会打印成一模一样的两行，
        //   于是"数量对不上"看起来像重复插入 —— 实测就是这么被误导过一次。
        return (loc.getWorld() == null ? "?" : loc.getWorld().getName())
                + " " + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    /**
     * 本类所有 Map 键的统一入口 —— 见 {@link TouhouData#norm}。
     *
     * <p>★ 规矩：{@link #CORE_OF} / {@link #NEXT_TRANSFER} / {@link #MISS_WARNED} 的
     * <b>每一次 get/put/remove 都必须过这里</b>。漏一处就会出现"同一个接口两条记录"
     * （Location 的 hashCode 用整数坐标、equals 用 double+yaw，两者口径不一致）。
     */
    private static Location norm(Location loc) {
        return TouhouData.norm(loc);
    }

    protected AbstractReactorPort(ItemGroup itemGroup, SlimefunItemStack item,
                                  RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        new BlockMenuPreset(getId(), inventoryTitle()) {
            @Override
            public void init() {
                AbstractReactorPort.this.constructMenu(this);
            }

            @Override
            public boolean canOpen(Block b, Player p) {
                return p.hasPermission("slimefun.inventory.bypass")
                        || (canUse(p, false) && Slimefun.getProtectionManager()
                                .hasPermission(p, b.getLocation(), Interaction.INTERACT_BLOCK));
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                // Cargo 只对本接口"负责的那一侧"进出货
                return transportSlots(flow);
            }
        };

        addItemHandler(new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return true;        // 读世界方块 + 读写容器，必须主线程
            }

            @Override
            public void tick(Block b,
                             SlimefunItem item,
                             com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData data) {
                AbstractReactorPort.this.tickPort(b.getLocation());
            }
        });

        addItemHandler(new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(BlockBreakEvent e, ItemStack tool, List<ItemStack> drops) {
                forget(e.getBlock().getLocation());
            }
        });
    }

    // ================================================================ 子类契约

    /** 界面标题。 */
    protected abstract String inventoryTitle();

    /** 本接口在骨架里"自己负责"的那一格对应什么（决定哪一片是真正的槽）。 */
    protected abstract PortRole role();

    /** 本接口负责的那一片的槽位（输入接口 = 输入槽；输出接口 = 输出槽）。 */
    public int[] ownSlots() {
        return ownSlotsOf(role());
    }

    /**
     * 把物品从 {@code from} 搬到 {@code to}（本接口只做一个方向）。
     *
     * @return 实际搬走的件数
     */
    protected abstract int doTransfer(BlockMenu own, BlockMenu core);

    /** 信息格里那行"我负责什么"的说明。 */
    protected abstract String roleLine();

    /** 信息格里"搬运方向"的两行说明。 */
    protected abstract List<String> directionLines();

    /** 本接口给自己那一片槽用的图标（进/出的视觉区分）。 */
    protected abstract ItemStack ownPlaceholder();

    // ================================================================ 骨架 / 分类

    /**
     * 取某一格的语义（越界返回 {@code null}）。
     *
     * <p>骨架只有一套（两个接口完全共用），所以这里不需要角色参数。
     */
    public static GridCell cellAt(int slot) {
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return null;
        }
        return SKELETON[slot / 9][slot % 9];
    }

    /**
     * 某一格在本接口里渲染成什么字符 —— 直接输出<b>需求里的字符</b>，便于逐格对照。
     *
     * <p>两个接口的骨架相同，唯一差别是主槽区在输入口叫 {@code I}、在输出口叫 {@code O}。
     * 第 5 行的标识占位符同理（输入口用输入槽纹理、输出口用输出槽纹理），
     * 字符上统一记作 {@code i}（小写，表示"标识占位符"）以便与第 0 行的 {@code I} 区分。
     */
    public static String classify(int slot, PortRole owned) {
        GridCell cell = cellAt(slot);
        if (cell == null) {
            return "??";
        }
        boolean isInput = owned == PortRole.INPUT;
        return switch (cell) {
            case BORDER -> "X";
            case INFO -> "I";                       // 核心信息 / 状态 / 坐标（+ 手动激活）
            case MODE -> "S";                       // 切换工作模式
            case ACTIVATE -> "C";                   // 手动激活
            case SLOT -> isInput ? "O" : "O";       // 主槽区（两种接口都用 O 表示"可放物品"）
            case MARKER -> isInput ? "i" : "o";     // 槽位标识占位符
        };
    }

    /** 本接口的实例版（等价于 {@code classify(slot, role())}）。 */
    protected String classify(int slot) {
        return classify(slot, role());
    }

    /**
     * 骨架自检：6×9、每格非空、各类语义都用到，各按键恰好 1 格，主槽区 32 格。
     *
     * <p>类初始化时调用一次；不合法直接抛异常，避免带着坏骨架运行。
     */
    private static void verifySkeleton() {
        if (SKELETON.length != 6) {
            throw new IllegalStateException("骨架不是 6 行");
        }
        Map<GridCell, Integer> count = new LinkedHashMap<>();
        for (int r = 0; r < 6; r++) {
            if (SKELETON[r].length != 9) {
                throw new IllegalStateException("骨架第 " + r + " 行不是 9 列");
            }
            for (int c = 0; c < 9; c++) {
                if (SKELETON[r][c] == null) {
                    throw new IllegalStateException("骨架 (" + r + "," + c + ") 为空");
                }
                count.merge(SKELETON[r][c], 1, Integer::sum);
            }
        }
        for (GridCell cell : GridCell.values()) {
            if (count.getOrDefault(cell, 0) <= 0) {
                throw new IllegalStateException("骨架里没有用到 " + cell);
            }
        }
        if (count.getOrDefault(GridCell.INFO, 0) != 1
                || count.getOrDefault(GridCell.MODE, 0) != 1
                || count.getOrDefault(GridCell.ACTIVATE, 0) != 1) {
            throw new IllegalStateException("骨架的 信息/模式/激活 必须各恰好 1 格");
        }
        if (count.getOrDefault(GridCell.SLOT, 0) != 32) {
            throw new IllegalStateException("骨架主槽区应为 32 格，实际 "
                    + count.getOrDefault(GridCell.SLOT, 0));
        }
        if (count.getOrDefault(GridCell.MARKER, 0) != 9) {
            throw new IllegalStateException("骨架第 5 行标识占位符应为 9 格，实际 "
                    + count.getOrDefault(GridCell.MARKER, 0));
        }
        // 两种角色下都必须能把 54 格全部归类
        for (PortRole owned : PortRole.values()) {
            for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
                if ("??".equals(classify(slot, owned))) {
                    throw new IllegalStateException(owned + " 角色下槽 " + slot + " 无法归类");
                }
            }
        }
    }

    static {
        verifySkeleton();
    }

    /** 供命令用：6 行纯网格（每行 9 格、单空格分隔），直接与需求原文对照。 */
    public static List<String> gridOf(AbstractReactorPort port) {
        return gridFor(port.role());
    }

    /**
     * <b>静态</b>网格生成：给定角色直接算出 6×9 网格。
     *
     * <p>不需要实例、不需要服务端 —— 布局自检可以纯粹用 Java 跑。
     */
    public static List<String> gridFor(PortRole owned) {
        List<String> rows = new ArrayList<>(6);
        for (int row = 0; row < 6; row++) {
            StringBuilder sb = new StringBuilder();
            for (int col = 0; col < 9; col++) {
                if (col > 0) {
                    sb.append(' ');
                }
                sb.append(classify(row * 9 + col, owned));
            }
            rows.add(sb.toString());
        }
        return rows;
    }

    // ================================================================ GUI 构造

    private void constructMenu(BlockMenuPreset preset) {
        preset.setSize(INVENTORY_SIZE);

        // ★ 一律通过 GuiLock 注册：**默认全部锁死**，只有显式声明的真实槽可交互。
        //   这套机制就是为了让"占位符能被拿走"这类 bug 结构上不可能发生，详见 GuiLock 类注释。
        final GuiLock lock = GuiLock.wrap(preset);

        // 占位符优先级（spec 要求）：一律用【粘液本体自带】的纹理，不自造。
        //   X        → getBackground()
        //   行5 标识  → 输入口用 getInputSlotTexture() / 输出口用 getOutputSlotTexture()
        ItemStack markerTexture = this instanceof ReactorInputPort
                ? ChestMenuUtils.getInputSlotTexture()
                : ChestMenuUtils.getOutputSlotTexture();

        // 主槽区：唯一可自由放取的地方，先声明为真实槽
        lock.markRealSlot(ownSlots());

        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            switch (cellAt(slot)) {
                case BORDER -> lock.addItem(slot, ChestMenuUtils.getBackground());
                case MARKER -> lock.addItem(slot, markerTexture);
                default -> {
                    // 主槽区留空；三个按键稍后单独装
                }
            }
        }

        // ---- 行0 的三个按键：信息(槽3) / 模式(槽5) / 激活(槽7) ----
        lock.button(slotOf(GridCell.INFO), infoPlaceholder(),
                (p, e) -> handleInfoClick(p, e));
        lock.button(slotOf(GridCell.MODE), modeIcon(ReactorMode.GENERATE),
                (p, e) -> handleModeClick(p, slotOf(GridCell.MODE), e));
        lock.button(slotOf(GridCell.ACTIVATE), activateIcon(),
                (p, e) -> handleActivateClick(p, slotOf(GridCell.ACTIVATE), e));

        // 安全网：把剩下没注册的格子（含空着的）全部锁死
        lock.autoGuard();
        this.guiLock = lock;
    }

    /** 供命令自检用：本接口最后构建出来的 GUI 注册器。 */
    private transient GuiLock guiLock;

    /** 当前这台接口 GUI 的锁槽自检报告（未构建过返回 null）。 */
    public GuiLock guiLock() {
        return guiLock;
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

    /**
     * 自检：逐格核对"该锁的是否都锁了"。
     *
     * <p>直接委托 {@link GuiLock#report()} —— 锁槽逻辑与自检逻辑共用同一份"真实槽"记录，
     * 不会出现"锁在一处、检在另一处"的漂移。
     *
     * @param port 取它的 GUI 注册器；为 null 时返回一句提示
     * @return 逐行报告；最后一行是总结
     */
    public static List<String> guardReport(AbstractReactorPort port) {
        GuiLock lock = port == null ? null : port.guiLock();
        if (lock == null) {
            return List.of("  ? 这台接口还没有构建过界面（放下方块后右键打开一次即可）");
        }
        return lock.report();
    }

    /** 取骨架里某一类的全部槽位（按槽号升序）。 */
    public static int[] slotsOfCell(GridCell cell) {
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

    /**
     * 取某个角色下、渲染结果为 {@code kind} 的全部槽位（按槽号升序）。
     *
     * <p>★ 与 {@link #classify} 共用同一套规则 —— 这样"构造 GUI 时用的槽位集合"
     * 与"布局自检打印出来的分类"永远一致，不会各说各话。
     */
    public static int[] slotsOf(PortRole owned, String kind) {
        List<Integer> list = new ArrayList<>();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (kind.equals(classify(slot, owned))) {
                list.add(slot);
            }
        }
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /** 本角色真正拥有的槽位（= 主槽区 32 个），静态版。 */
    public static int[] ownSlotsOf(PortRole owned) {
        return slotsOfCell(GridCell.SLOT);
    }

    /** Cargo 能对本接口进出货的槽位（只认本接口负责的那一侧）。 */
    protected int[] transportSlots(ItemTransportFlow flow) {
        if (this instanceof ReactorInputPort) {
            // 输入接口：只允许 Cargo 往里放
            return flow == ItemTransportFlow.INSERT ? ownSlots() : new int[0];
        }
        // 输出接口：只允许 Cargo 往外抽（枚举值是 WITHDRAW，不是 EXTRACT）
        return flow == ItemTransportFlow.WITHDRAW ? ownSlots() : new int[0];
    }

    // ================================================================ 按键

    /**
     * 信息按键（槽 3）：显示核心绑定 / 状态 / 坐标；<b>点它 = 结构检测 + 手动激活</b>。
     *
     * <p>★ 这里取代了原来的"右键映射核心 GUI"。映射功能已按需求移除 ——
     *   两个界面（自有 + 映射）抢右键是这次反馈的 bug 根源，
     *   现在右键只有一个语义：<b>打开本接口自己的界面</b>。
     */
    private void handleInfoClick(Player p, InventoryClickEvent event) {
        Location port = locatePort(p, event);
        if (port == null) {
            Notify.warn(p, "&c无法定位这个接口，请关掉界面后重新右键打开");
            return;
        }
        Location core = rescanCore(port);       // GUI 点击：玩家在场，值得现场重扫一次
        if (core == null) {
            Notify.warn(p, "&c这个接口没有找到反应堆核心（附近没有核心方块？）");
            return;
        }
        ReactorManager.Activation act = ReactorManager.activate(core);
        // ★ 玩家主动点了激活按钮：成功 = 重要事件，失败 = warning。
        //   失败必须说 —— 否则玩家只会看到"点了没反应"，那比刷屏更糟。
        if (act.success()) {
            Notify.important(p, act.message());
        } else {
            Notify.warn(p, act.message());
        }
        if (!act.success()) {
            // 结构不完整时把"缺在哪"补一句，省得玩家点完一头雾水
            ReactorManager.lastResult(core);
        }
        refreshGui(port, StorageCacheUtils.getMenu(port));
    }

    /** 模式按键（槽 5）：切换核心的发电 / 生产模式。 */
    private void handleModeClick(Player p, int slot, InventoryClickEvent event) {
        Location port = locatePort(p, event);
        if (port == null) {
            return;
        }
        Location core = rescanCore(port);       // GUI 点击：玩家在场，值得现场重扫一次
        if (core == null) {
            Notify.warn(p, "&c没有找到代理的核心，无法切换模式");
            return;
        }
        ReactorMode mode = ReactorManager.toggleMode(core);
        // 界面上的模式文字自己会变，再推一条聊天消息属于纯噪音（见 Notify 分档说明）
        Notify.info(p, "&7核心模式已切换到 " + mode.display());
        refreshGui(port, StorageCacheUtils.getMenu(port));
    }

    /**
     * 手动激活按键（槽 7）：结构检测 + 激活反应堆。
     *
     * <p>与信息按键走的是同一条链路（{@link ReactorManager#activate}），
     * 区别只是这个按键"专职于此"，并在成功时播放激活音效。
     */
    private void handleActivateClick(Player p, int slot, InventoryClickEvent event) {
        Location port = locatePort(p, event);
        if (port == null) {
            Notify.warn(p, "&c无法定位这个接口，请关掉界面后重新右键打开");
            return;
        }
        Location core = rescanCore(port);       // GUI 点击：玩家在场，值得现场重扫一次
        if (core == null) {
            Notify.warn(p, "&c没有找到代理的核心，无法激活");
            return;
        }
        ReactorManager.Activation act = ReactorManager.activate(core);
        if (act.success()) {
            Notify.important(p, act.message());
        } else {
            Notify.warn(p, act.message());
        }
        if (act.success()) {
            p.playSound(core, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0F, 1.0F);
        }
        refreshGui(port, StorageCacheUtils.getMenu(port));
    }

    /** 定位"当前这个菜单属于哪个接口"。 */
    private Location locatePort(Player p, InventoryClickEvent event) {
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

    // ================================================================ tick / 搬运

    private void tickPort(Location port) {
        Location core = coreOf(port);

        int interval = Math.max(1, ReactorManager.ioIntervalTicks());
        int left = NEXT_TRANSFER.getOrDefault(norm(port), 0) - 1;
        if (left > 0) {
            NEXT_TRANSFER.put(norm(port), left);
        } else {
            NEXT_TRANSFER.put(norm(port), interval);
            if (core != null) {
                transfer(port, core);
            }
        }

        // GUI 刷新放在节流之外：信息格里的数字要连续变化才不显得卡住
        refreshGui(port, StorageCacheUtils.getMenu(port));
    }

    /**
     * 一轮搬运（方向由子类决定）。
     *
     * <p><b>只在核心已激活时搬运</b>（spec）。未激活说明结构不完整或还没搭完，
     * 这时候动东西只会让玩家以为机器坏了。
     *
     * @return 本轮搬走的件数
     */
    private int transfer(Location port, Location core) {
        if (!ReactorManager.isActivated(core)) {
            return 0;               // 未激活：不搬（spec）
        }
        BlockMenu own = StorageCacheUtils.getMenu(port);
        BlockMenu coreMenu = StorageCacheUtils.getMenu(core);
        if (own == null || coreMenu == null) {
            return 0;
        }
        return doTransfer(own, coreMenu);
    }

    /**
     * 把 src 若干槽里的物品推进 dest 的若干槽。
     *
     * <p>★ 每个源槽的处理是"先问能放下多少，再精确扣多少"：
     * <ol>
     *   <li>{@code pushItem(stack, slots...)} 返回<b>放不下的剩余</b>（null = 全放下）；</li>
     *   <li>据此算出真正搬走的件数，再用 {@code replaceExistingItem/consumeItem} 扣除。</li>
     * </ol>
     * <b>绝不能"先扣再推"</b>：推不进去的那部分会凭空消失。
     */
    protected static int moveItems(BlockMenu src, int[] srcSlots, BlockMenu dest, int[] destSlots) {
        int total = 0;
        for (int slot : srcSlots) {
            ItemStack in = src.getItemInSlot(slot);
            if (in == null || in.getType().isAir()) {
                continue;
            }
            ItemStack leftover = dest.pushItem(in.clone(), destSlots);
            int remain = leftover == null ? 0 : leftover.getAmount();
            int moved = in.getAmount() - remain;
            if (moved <= 0) {
                continue;
            }
            if (moved >= in.getAmount()) {
                src.replaceExistingItem(slot, null);
            } else {
                src.consumeItem(slot, moved);
            }
            total += moved;
        }
        return total;
    }

    // ================================================================ GUI 刷新

    private static void refreshGui(Location port, BlockMenu inv) {
        if (inv == null || !inv.hasViewer()) {
            return;
        }
        Location core = CORE_OF.get(norm(port));
        SlimefunItem item = BlockStorage.check(port);
        if (!(item instanceof AbstractReactorPort p)) {
            return;
        }
        inv.replaceExistingItem(slotOf(GridCell.INFO), p.infoIcon(core));
        inv.replaceExistingItem(slotOf(GridCell.MODE),
                modeIcon(core == null ? null : ReactorManager.getMode(core)));
        inv.replaceExistingItem(slotOf(GridCell.ACTIVATE), activateIcon(core));
    }

    /** 槽 3：代理核心信息 / 状态 / 坐标（点它 = 结构检测 + 手动激活）。 */
    private ItemStack infoIcon(Location core) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&7本接口： " + roleLine());
        lore.add("");
        if (core == null) {
            lore.add("&c✘ 未找到代理的核心");
            lore.add("&7把本接口放进反应堆结构里");
            lore.add("&7（替代一格保护罩）即可自动识别");
        } else {
            lore.add("&7代理核心： &a✔ 已绑定");
            lore.add("&7坐标： &f" + core.getBlockX() + ", "
                    + core.getBlockY() + ", " + core.getBlockZ());
            lore.add("&7世界： &f" + (core.getWorld() == null ? "?" : core.getWorld().getName()));
            boolean activated = ReactorManager.isActivated(core);
            lore.add("&7激活状态： " + (activated ? "&a已激活" : "&c未激活"));
            lore.add("&7运行状态： " + ReactorManager.cachedState(core).display());
            lore.add("");
            FuelOperation op = currentOperation(core);
            if (op == null) {
                lore.add("&7当前进程： &8无");
            } else {
                int total = Math.max(1, op.getTotalTicks());
                int done = Math.min(op.getProgress(), total);
                lore.add("&7当前进程： &f" + done + " / " + total + " tick");
                lore.add("&7剩余时间： &f" + ReactorManager.formatTicks(total - done));
                lore.add("&7进度： " + progressBar(done, total));
            }
            lore.add("");
            if (activated) {
                lore.add("&7每 &f" + ReactorManager.ioIntervalSeconds() + " &7秒搬运一次");
                lore.add("&8（= 每 " + ReactorManager.ioIntervalTicks()
                        + " 个 Slimefun tick，随服务端 tickRate 自动换算）");
                lore.addAll(directionLines());
            } else {
                lore.add("&c核心未激活，暂停搬运");
                lore.add("&8（结构搭好并激活后自动开始）");
            }
        }
        lore.add("");
        lore.add("&e点击本格：结构检测 + 手动激活");
        lore.add("&8直接右键接口方块即可打开本界面");
        return new CustomItemStack(Material.REDSTONE_TORCH, "&e代理核心信息", lore);
    }

    private ItemStack infoPlaceholder() {
        return new CustomItemStack(Material.REDSTONE_TORCH, "&e代理核心信息",
                List.of("", "&7本接口： " + roleLine(), "", "&7等待识别核心…"));
    }

    /** 槽 5：切换核心工作模式（发电 / 生产）。 */
    private static ItemStack modeIcon(ReactorMode mode) {
        // ★ CustomItemStack 没有 (ItemStack, String, List) 重载，只能用 varargs 版本
        return new CustomItemStack(AddItems.INFO_MODESHIFT.clone(),
                "&e切换核心工作模式",
                "",
                "&7当前： " + (mode == null ? "&8未绑定核心" : mode.display()),
                "",
                "&7发电模式：全力发电，到阈值暂停",
                "&7生产模式：&f工作效率 ×5&7，发电降到 10%",
                "",
                "&e点击切换");
    }

    /** 槽 7：手动激活按钮（未绑定核心时的简化版）。 */
    private static ItemStack activateIcon() {
        return activateIcon(null);
    }

    /** 槽 7：手动激活按钮。 */
    private static ItemStack activateIcon(Location core) {
        boolean activated = core != null && ReactorManager.isActivated(core);
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (core == null) {
            lore.add("&c✘ 未找到代理的核心");
            lore.add("&7把本接口放进反应堆结构里");
            lore.add("&7（替代一格保护罩）即可自动识别");
        } else {
            lore.add("&7当前状态： " + ReactorManager.cachedState(core).display());
            lore.add(activated
                    ? "&7核心已在运行，重复点击只会重新检测结构"
                    : "&e结构完整后点这里激活反应堆");
        }
        lore.add("");
        lore.add("&e点击激活");
        return new CustomItemStack(Material.LEVER, "&a&l手动激活", lore);
    }

    /** 给物品设置显示名与 lore（保留原物品的一切，含粘液 id）。 */
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

    private static boolean coreHasOperation(Location core) {
        FuelOperation op = currentOperation(core);
        return op != null && !op.isFinished();
    }

    private static FuelOperation currentOperation(Location core) {
        var item = BlockStorage.check(core);
        if (!(item instanceof UtsuhoReactorCore reactor)) {
            return null;
        }
        return reactor.getMachineProcessor().getOperation(core.getBlock());
    }

    private static String progressBar(int done, int total) {
        int width = 20;
        int filled = (int) Math.round(width * (done / (double) total));
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < width; i++) {
            if (i == filled) {
                sb.append("&7");
            }
            sb.append('|');
        }
        return sb.toString();
    }

    // ================================================================ 核心定位（事件驱动绑定）

    /**
     * 取这个接口代理的核心位置 —— <b>只读绑定，不扫世界</b>。
     *
     * <p>★ 这是"双向配合"里接口的那一半：需求要求「结构性方块仅检测核心；
     * 如果检测失败则停止检测；如果检测到核心，则结构方块停止检测」。
     * 所以接口<b>只在被放置/破坏的那一刻</b>找一次核心（见
     * {@link ReactorManager#onStructureEdited}），之后：
     * <ul>
     *   <li>找到了 → 记在 {@link #CORE_OF}，每 tick 只花<b>一次</b> {@code checkID}
     *       确认它还活着（命中缓存的开销，与 125 格扫描不是一个量级）；</li>
     *   <li>没找到 → 什么都不做，<b>不重试、不轮询</b>。将来核心被放下时，
     *       由核心那一边反向绑定（{@link #bindNearby}）。</li>
     * </ul>
     *
     * <p>★ 缓存里的核心消失（被拆/被炸）后也<b>不</b>自动重扫：同样等事件。
     * 核心被拆时 {@link ReactorManager#onStructureEdited} 会调 {@link #unbindFrom} 清掉绑定。
     *
     * @return 已绑定的核心位置；没有绑定（或绑定的核心已不在）时为 {@code null}
     */
    protected static Location coreOf(Location port) {
        return boundCore(port);
    }

    /** 已绑定的核心（不扫世界）。 */
    protected static Location boundCore(Location port) {
        // ① 内存表（同一局游戏里最快的一条路）
        Location cached = CORE_OF.get(norm(port));
        if (cached != null) {
            if (isCore(cached)) {
                return cached;
            }
            CORE_OF.remove(norm(port));           // 绑定的核心已经没了
        }
        // ② 方块数据里的 uid（照搬 LogiTech 的 acceptPartRequest）——
        //    这一条是**重启之后**的关键：内存表空了，但构件身上还盖着 uid 章，
        //    核心的第一次检测会把 uid→核心 填回内存表，构件读一次数据就能认回核心，
        //    完全不需要再扫 125 格。
        Location byUid = StructureRegistry.coreOfPart(port);
        if (byUid != null) {
            CORE_OF.put(norm(port), byUid);
            return byUid;
        }
        return null;
    }

    /**
     * <b>强制现场重扫</b>并把结果绑定下来 —— 只给"玩家主动"的场合用。
     *
     * <p>调用点：GUI 三个按键（信息 / 模式 / 激活）、命令诊断。
     * 这些都是"玩家正在看着这台机器"，多扫一次 125 格完全值得 ——
     * 玩家刚把核心放下、点一下就该认出来，不能因为"要等事件"而回一句"没找到核心"。
     *
     * <p>⚠ <b>绝不能</b>在 tick 路径上调用它，否则又变回每 tick 扫 125 格。
     */
    protected static Location rescanCore(Location port) {
        Location found = scanForCore(port);
        if (found == null) {
            CORE_OF.remove(norm(port));
            warnMissing(port);
            return null;
        }
        bind(port, found);
        return found;
    }

    /** 把接口绑定到某个核心（不扫世界）。 */
    static void bind(Location port, Location core) {
        if (port == null || core == null) {
            return;
        }
        CORE_OF.put(norm(port), core);
        MISS_WARNED.remove(norm(port));       // 找到了就重置，将来再丢能立刻再警告
        // 如果构件身上已经有同一个 uid 的章，就别重复写方块数据（LogiTech 也是幂等的）
        String coreUid = StructureRegistry.uidOf(core);
        if (coreUid != null && coreUid.equals(StructureRegistry.uidOf(port))) {
            return;
        }
        if (coreUid != null) {
            // 核心已经登记过结构 → 把这一格并入同一个 uid（接口被补放进已成立的结构里）
            TouhouData.setString(port, StructureRegistry.KEY_UID, coreUid);
            TouhouData.setString(port, StructureRegistry.KEY_STATUS,
                    Integer.toString(StructureRegistry.STA_ACTIVE));
        }
    }

    /**
     * <b>反向绑定</b>：核心做完结构检测后，把周围 5×5×5 内的接口都绑到自己身上。
     *
     * <p>「双向配合」的另一半 —— 它覆盖了"<b>先放接口、后放核心</b>"这个顺序：
     * 接口放下时找不到核心就<b>停手</b>了（按需求不重试），如果核心这边不主动认领，
     * 那些接口就永远是孤儿。
     *
     * <p>只在<b>事件、玩家点击、命令</b>里调用（每次最多 125 次 {@code checkID}），
     * <b>不在 tick 路径上</b>。
     *
     * @return 本次绑定/确认的接口数量
     */
    static int bindNearby(Location core) {
        if (core == null || core.getWorld() == null) {
            return 0;
        }
        int r = ReactorManager.coreSearchRadius();
        int n = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    Location at = new Location(core.getWorld(),
                            core.getBlockX() + dx, core.getBlockY() + dy, core.getBlockZ() + dz);
                    if (isPort(at)) {
                        bind(at, core);
                        n++;
                    }
                }
            }
        }
        return n;
    }

    /**
     * 核心被拆时调用：把绑在它身上的接口全部解绑。
     *
     * <p>解绑之后接口回到"没有核心"的状态，并且<b>不会自己重扫</b> ——
     * 等玩家重新放下核心，由新的核心反向绑定。这正是需求要的"停止检测"。
     *
     * @return 被解绑的接口数量
     */
    static int unbindFrom(Location core) {
        if (core == null) {
            return 0;
        }
        // ★ 比较也要归一化：绑定表里存的核心是 norm 过的，这里的 core 可能带小数/朝向
        Location key = norm(core);
        int n = 0;
        for (Map.Entry<Location, Location> e : CORE_OF.entrySet()) {
            if (key != null && key.equals(norm(e.getValue()))) {
                CORE_OF.remove(e.getKey());
                n++;
            }
        }
        return n;
    }

    /** 这一格是不是本附属的物流接口。 */
    private static boolean isPort(Location loc) {
        var item = BlockStorage.check(loc);
        return item instanceof AbstractReactorPort;
    }

    private static boolean isCore(Location loc) {
        String id = BlockStorage.checkID(loc);
        return AddItems.UTSUHO_REACTOR_CORE != null
                && AddItems.UTSUHO_REACTOR_CORE.getItemId().equals(id);
    }

    /**
     * 在接口周围找核心（5×5×5 = 125 次 {@code checkID}）。
     *
     * <p>结构是 5×5×5、核心在正中，接口到核心最多差 2 格 —— 扫 5×5×5 足够。
     * 现在只可能从"接口刚被放下/破坏"、"玩家点了 GUI"、"命令诊断"这三处进来。
     */
    private static Location scanForCore(Location port) {
        if (port.getWorld() == null) {
            return null;
        }
        SCAN_COUNT.incrementAndGet();
        int r = ReactorManager.coreSearchRadius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    Location at = new Location(port.getWorld(),
                            port.getBlockX() + dx, port.getBlockY() + dy, port.getBlockZ() + dz);
                    if (isCore(at)) {
                        return at;
                    }
                }
            }
        }
        return null;
    }

    private static void warnMissing(Location port) {
        if (!MISS_WARNED.add(norm(port))) {
            return;                     // 这个接口已经提醒过了
        }
        Touhou.getInstance().getLogger().warning("反应堆接口 @ ("
                + port.getBlockX() + "," + port.getBlockY() + "," + port.getBlockZ()
                + ") 附近 5×5×5 内没有找到反应堆核心"
                + "（接口可以先于核心放置：按需求此时它【停止检测】不重试，"
                + "核心被放下时会反向认领它）"
                + " —— 本条只报一次；要复查请用 /touhou reactor <x> <y> <z> io");
    }

    /** 清掉某个接口的缓存（拆方块时调用）。 */
    public static void forget(Location port) {
        CORE_OF.remove(norm(port));
        NEXT_TRANSFER.remove(norm(port));
        MISS_WARNED.remove(norm(port));
    }

    // ================================================================ 诊断

    /**
     * 诊断用：立刻跑一遍"找核心 + 取两边容器 + 强制搬运一轮"。
     *
     * @return {找到的核心坐标, 核心容器是否拿到, 本接口自身容器是否就绪, 本轮搬运说明}
     */
    public String[] probe(Location port) {
        Location core = scanForCore(port);
        if (core != null) {
            CORE_OF.put(norm(port), core);
        }
        String coreText = core == null ? "未找到"
                : (core.getBlockX() + "," + core.getBlockY() + "," + core.getBlockZ());
        BlockMenu coreMenu = core == null ? null : StorageCacheUtils.getMenu(core);
        BlockMenu own = StorageCacheUtils.getMenu(port);

        String moved;
        if (core == null) {
            moved = "未执行（没找到核心）";
        } else if (coreMenu == null || own == null) {
            moved = "未执行（容器未就绪）";
        } else if (own == coreMenu) {
            moved = "异常：接口与核心指向同一个容器对象（本应各自独立）";
        } else if (!ReactorManager.isActivated(core)) {
            moved = "未执行（核心未激活，spec 要求此时不搬运）";
        } else {
            moved = "已搬运 " + doTransfer(own, coreMenu) + " 件（" + roleLine() + "）";
        }
        return new String[] {coreText,
                coreMenu != null ? "已拿到" : "拿不到",
                own != null ? "已就绪（Cargo 可对此格进出货）" : "未就绪",
                moved};
    }

    /** 诊断用：往本接口的第一个自有槽塞一个原油桶。 */
    public boolean injectFuel(Location port) {
        if (!(this instanceof ReactorInputPort)) {
            return false;
        }
        BlockMenu own = StorageCacheUtils.getMenu(port);
        if (own == null) {
            return false;
        }
        own.replaceExistingItem(ownSlots()[0],
                io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems.OIL_BUCKET.clone());
        return true;
    }

    /** 诊断用：把搬运计数器清零，让下一 tick 立刻搬一次。 */
    public static void resetInterval(Location port) {
        NEXT_TRANSFER.put(norm(port), 0);
    }

    /**
     * 诊断用：往"本接口"或"代理核心"的指定槽塞 7 个红石当标记物。
     *
     * <p>为什么用红石：验证"接口输入 → 核心输入"时如果塞原油桶，核心会
     * <b>立刻把它烧掉开进程</b>，观察不到搬运本身；验证"核心输出 → 接口输出"时
     * 核心又没有产物。红石是核心不认的普通物品，能稳定停在槽里。
     *
     * @param where {@code "port"} = 塞进本接口容器；其它值 = 塞进它代理的核心容器
     * @param slot  槽位号；{@code -1} = 该容器第一个自有槽 / 核心第一个输入槽
     */
    public boolean seed(Location port, String where, int slot) {
        boolean toPort = "port".equalsIgnoreCase(where);
        Location target = toPort ? port : CORE_OF.get(norm(port));
        if (target == null) {
            return false;
        }
        BlockMenu menu = StorageCacheUtils.getMenu(target);
        if (menu == null) {
            return false;
        }
        int index = slot >= 0 ? slot
                : (toPort ? ownSlots()[0] : UtsuhoReactorCore.INPUT_SLOT_ALL[0]);
        menu.replaceExistingItem(index, new ItemStack(Material.REDSTONE, 7));
        return true;
    }

    /** 诊断用：数某个容器若干槽里的红石总数（配合 {@link #seed} 验证搬运）。 */
    public int countRedstone(Location port, String where, int[] slots) {
        boolean toPort = "port".equalsIgnoreCase(where);
        Location target = toPort ? port : CORE_OF.get(norm(port));
        if (target == null) {
            return -1;
        }
        BlockMenu menu = StorageCacheUtils.getMenu(target);
        if (menu == null) {
            return -1;
        }
        int n = 0;
        for (int s : slots) {
            ItemStack it = menu.getItemInSlot(s);
            if (it != null && it.getType() == Material.REDSTONE) {
                n += it.getAmount();
            }
        }
        return n;
    }

    /** 诊断用：本接口当前代理的核心（没绑定返回 null）。 */
    public static Location diagnosticCore(Location port) {
        return CORE_OF.get(norm(port));
    }

    /** 诊断用：本接口与核心是不是同一个容器对象（应当为 false）。 */
    public static boolean sharesMenuWithCore(Location port) {
        Location core = CORE_OF.get(norm(port));
        if (core == null) {
            return false;
        }
        BlockMenu own = StorageCacheUtils.getMenu(port);
        BlockMenu coreMenu = StorageCacheUtils.getMenu(core);
        return own != null && own == coreMenu;
    }

    /**
     * 自检：把 54 格布局渲染成 6×9 的图，逐格与<b>由骨架推导的期望</b>比对。
     *
     * <p>期望值不另写一份常量，而是直接来自 {@link #classify} ——
     * 曾经因为"手写两份常量、改一份漏一份"而反复出错，现在改成单一数据源。
     */
    public List<String> layoutSummary() {
        List<String> out = new ArrayList<>();
        Map<String, Integer> count = new LinkedHashMap<>();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            count.merge(classify(slot), 1, Integer::sum);
        }
        out.add("接口=" + getId() + "  标题=" + inventoryTitle());
        out.add("尺寸 " + INVENTORY_SIZE
                + " | X占位=" + count.getOrDefault("X", 0)
                + " | I信息=" + count.getOrDefault("I", 0)
                + " | S模式=" + count.getOrDefault("S", 0)
                + " | C激活=" + count.getOrDefault("C", 0)
                + " | O主槽=" + count.getOrDefault("O", 0)
                + " | 标识占位=" + (count.getOrDefault("i", 0) + count.getOrDefault("o", 0))
                + " | 合计=" + count.values().stream().mapToInt(Integer::intValue).sum());

        String bad = count.getOrDefault("??", 0) > 0 ? "有未分类槽" : null;
        for (int row = 0; row < 6; row++) {
            StringBuilder sb = new StringBuilder();
            for (int col = 0; col < 9; col++) {
                sb.append(String.format("%-4s", classify(row * 9 + col))).append('|');
            }
            out.add("  行" + row + " " + sb);
        }
        out.add(bad == null
                ? "  OK 54 格全部可分类、无遗漏"
                : "  FAIL " + bad);
        return out;
    }

    /**
     * 供命令展示：本接口的占位符到底用了什么。
     *
     * <p>spec 要求"所有占位类型玻璃板均以粘液本体自带为最高优先级"。
     * 本类除"我方槽的图标"外全用本体自带，这个方法把实际取到的物品打出来作为证据。
     */
    public static List<String> textureReport() {
        return List.of(
                "x  (占位玻璃板)  ← ChestMenuUtils.getBackground()        "
                        + describe(ChestMenuUtils.getBackground()),
                "Ix (输入槽提示)  ← ChestMenuUtils.getInputSlotTexture()  "
                        + describe(ChestMenuUtils.getInputSlotTexture()),
                "Ox (输出槽提示)  ← ChestMenuUtils.getOutputSlotTexture() "
                        + describe(ChestMenuUtils.getOutputSlotTexture()));
    }

    private static String describe(ItemStack item) {
        String name = item.hasItemMeta() && item.getItemMeta().hasDisplayName()
                ? ChatColor.stripColor(item.getItemMeta().getDisplayName())
                : "(无显示名)";
        return "  → " + item.getType() + " 「" + name + "」";
    }
}
