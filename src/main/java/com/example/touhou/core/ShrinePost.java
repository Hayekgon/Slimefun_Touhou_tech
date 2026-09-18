package com.example.touhou.core;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 「神社的木桩」—— 多方块<b>结构方块</b>（构件）。
 *
 * <h2>定位</h2>
 * 它不是核心，也<b>不存电、不发电</b>：只是一根木桩，负责
 * <ol>
 *   <li>作为多方块结构的一格参与搭建（结构层图待定，见下）；</li>
 *   <li>给玩家一个<b>观察窗口</b>：本格所属结构的 POWER 量与激活状态；</li>
 *   <li>充当该结构的<b>物流口</b>：{@link #IO_SLOT} 允许外界物流（Cargo）进出货。</li>
 * </ol>
 *
 * <h2>★ 与原生粘液的关系：不是 {@code AGenerator}，也不是 {@code EnergyNetComponent}</h2>
 * 本类直接继承 {@link SlimefunItem}，<b>刻意不</b>继承 {@code AGenerator}、
 * <b>刻意不</b>实现 {@code EnergyNetComponent}。原因是本机的能源走的是<b>自研 POWER 系统</b>
 * （{@code com.example.touhou.power}）：
 * <ul>
 *   <li>{@code EnergyNetComponent} 会被本体电力网络按"电容/发电机"记账，
 *       跟 POWER 网络是两套互不相干的账本，接进来只会让"这台机器到底吃哪种电"变得说不清；</li>
 *   <li>木桩自己不存电（它是构件，不是储能节点），所以它也<b>不</b>实现
 *       {@code PowerComponent} —— 节点身份留给真正参与的方块（例如赛钱箱）。</li>
 * </ul>
 * 它读 POWER 的方式是"问自己所属的网络"（{@link com.example.touhou.power.PowerNetworkManager}），
 * 只读不写，不参与均衡。
 *
 * <h2>GUI 布局（9 格 = 单行，像发射器那样）</h2>
 * <pre>
 *   槽位:   0    1    2    3    4    5    6    7    8
 *            X    X    X    I    IO   S    X    X    X
 *
 *   X  = 占位符（灰色玻璃板），不可取出、不可放入
 *   I  = 信息槽：当前 POWER 量 + 激活状态 + <b>本木桩的编号</b>（槽 3）
 *   IO = 混合型输入输出端：外界物流可进可出（槽 4），同时也是<b>本木桩的投料口</b>
 *   S  = 信息槽：多方块核心位置 + 本编号对应哪个预留槽（槽 5）
 * </pre>
 * 槽位映射由 {@link #SKELETON} 单点定义，构造 GUI 与自检都读它，
 * 不存在"两份常量各写一半"的漂移（这套做法抄自 {@link AbstractReactorPort}）。
 *
 * <h2>★ 本木桩在这台机器里的角色（本轮起是真的了）</h2>
 * 赛钱箱激活时会给结构里的 6 根木桩编号（先 +X、后 +Z，见 {@link SaizenbakoStructure}），
 * 编号写进<b>木桩自己</b>的方块数据（{@link TouhouData#KEY_POST_INDEX}），
 * 同时写下"我的核心在哪"（{@link TouhouData#KEY_CORE_POS}）。于是：
 * <ul>
 *   <li>木桩界面能显示"我是几号、往核心的哪个预留槽供料"；</li>
 *   <li>玩家唯一的投料口是 {@link #IO_SLOT}：赛钱箱每轮把 6 根木桩的 IO 槽
 *       镜像到核心的 6 个预留槽，再从木桩里扣材料（预留槽被锁死，谁都放不进去）。</li>
 * </ul>
 * 结构失效 / 木桩被拆时，编号与绑定会被清掉（见 {@link SaizenbakoManager#onPostRemoved}）。
 */
public class ShrinePost extends SlimefunItem
        implements GuiShiftGuard, io.github.thebusybiscuit.slimefun4.core.attributes.NotHopperable {

    // ---------------------------------------------------------------- GUI 布局

    /**
     * 9 格里每一格的语义。
     *
     * <p>与 {@link AbstractReactorPort.GridCell} 同一套思路：先声明语义，
     * 再由唯一的一份骨架推导"哪个槽是什么"，避免 GUI 构造与自检各写一份。
     */
    public enum GridCell {
        /** 占位符（需求里的 X）—— 灰色玻璃板，不可取出不可放入。 */
        BORDER,
        /** 信息槽：POWER 量 + 激活状态（需求里的 I）。 */
        INFO,
        /** 混合型输入输出端：外界物流可进可出（需求里的 IO）。 */
        IO,
        /** 信息槽：多方块核心位置（需求里的 S）。 */
        CORE_POS
    }

    /**
     * <b>唯一的</b>骨架 —— 逐格抄自需求，槽号 = 列号（单行）。
     *
     * <pre>
     *   0 X | 1 X | 2 X | 3 I | 4 IO | 5 S | 6 X | 7 X | 8 X
     * </pre>
     */
    private static final GridCell[] SKELETON = {
            GridCell.BORDER, GridCell.BORDER, GridCell.BORDER,
            GridCell.INFO, GridCell.IO, GridCell.CORE_POS,
            GridCell.BORDER, GridCell.BORDER, GridCell.BORDER
    };

    /** 单行菜单，和发射器一样大。 */
    private static final int INVENTORY_SIZE = 9;

    /** 信息槽（POWER 量 + 激活状态）。 */
    public static final int INFO_SLOT = 3;
    /**
     * 混合型输入输出端 —— <b>这一格是唯一允许外界物流读写的地方</b>。
     *
     * <p>它在 {@link BlockMenuPreset#getSlotsAccessedByItemTransport} 里对
     * {@code INSERT} 与 {@code WITHDRAW} <b>同时</b>开放，所以 Cargo 既能往里放、
     * 也能往外抽（需求里的"混合型输入输出端"）。
     */
    public static final int IO_SLOT = 4;
    /** 信息槽（多方块核心位置）。 */
    public static final int CORE_POS_SLOT = 5;

    /** Cargo 能对本方块进出货的槽位（只有 IO 一格）。 */
    private static final int[] TRANSPORT_SLOTS = {IO_SLOT};

    static {
        verifySkeleton();
    }

    // ---------------------------------------------------------------- 构造

    public ShrinePost(ItemGroup itemGroup, SlimefunItemStack item,
                      RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        // GUI：本体的 BlockMenuPreset 以 id 取用，后注册的覆盖先注册的，
        //      所以子类自己再 new 一个是接管的正确方式（与 UtsuhoReactorCore 同款写法）。
        new BlockMenuPreset(getId(), inventoryTitle()) {
            @Override
            public void init() {
                ShrinePost.this.constructMenu(this);
            }

            /**
             * ★ 这里才有真实方块：与坐标相关的文案都放这个钩子里刷新
             * （构造期 {@code init()} 拿到的 Location 必然是 null）。
             */
            @Override
            public void newInstance(BlockMenu menu, Block b) {
                try {
                    Location loc = b == null ? null : b.getLocation();
                    if (loc != null && menu != null) {
                        menu.replaceExistingItem(INFO_SLOT, infoIcon(loc));
                        menu.replaceExistingItem(CORE_POS_SLOT, corePosIcon(loc));
                    }
                } catch (RuntimeException e) {
                    com.example.touhou.Touhou.getInstance().getLogger()
                            .warning("[木桩] 界面初始化异常 @ "
                                    + (b == null ? "(null)" : TouhouData.xyz(b.getLocation())) + ": " + e);
                }
            }

            @Override
            public boolean canOpen(Block b, Player p) {
                return p.hasPermission("slimefun.inventory.bypass")
                        || (canUse(p, false) && Slimefun.getProtectionManager()
                                .hasPermission(p, b.getLocation(), Interaction.INTERACT_BLOCK));
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                // ★ 混合型端口：进和出都走 IO_SLOT（需求明确要求"可被外界物流输入输出"）
                return TRANSPORT_SLOTS.clone();
            }
        };

        // 低频 tick：只用来刷新信息槽里的 POWER 数字。
        // ★ 主线程（isSynchronized = true）：要读世界方块数据 + 写菜单。
        addItemHandler(new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return true;
            }

            @Override
            public void tick(Block b, SlimefunItem item,
                             com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData data) {
                ShrinePost.this.tickPost(b.getLocation());
            }
        });

        // 拆掉时要清掉本格的结构登记与编号（需求：木桩被拆时清除）。
        // 整座结构的停机由赛钱箱那边的定期复检负责 —— 木桩不做结构判定（它连核心在哪
        // 也只是"记录"，没有能力判定整套结构）。
        addItemHandler(new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(BlockBreakEvent e, ItemStack tool, List<ItemStack> drops) {
                onPostRemoved(e.getBlock().getLocation());
            }
        });
    }

    /** 界面标题。 */
    protected String inventoryTitle() {
        return "&6神社的木桩";
    }

    /**
     * Shift 快速移动只允许进 {@link #IO_SLOT}。
     *
     * <p>本界面其实没有空槽（其余 8 格都有图标），原版 Shift 也只会合并到 IO 槽；
     * 声明它是为了与其他机器保持同一套语义 —— 顺便挡住"哪天有大佬把某个格子清空"
     * 之后 Shift 直接把物品塞进去的隐患（详见 {@link GuiShiftGuard}）。
     */
    @Override
    public int[] shiftInsertSlots() {
        return TRANSPORT_SLOTS.clone();
    }

    // ---------------------------------------------------------------- 骨架 / 分类

    /** 取某一格的语义（越界返回 {@code null}）。 */
    public static GridCell cellAt(int slot) {
        if (slot < 0 || slot >= INVENTORY_SIZE) {
            return null;
        }
        return SKELETON[slot];
    }

    /** 某一格渲染成需求里的哪个字符（便于逐格与 spec 对照）。 */
    public static String classify(int slot) {
        GridCell cell = cellAt(slot);
        if (cell == null) {
            return "??";
        }
        return switch (cell) {
            case BORDER -> "X";
            case INFO -> "I";
            case IO -> "IO";
            case CORE_POS -> "S";
        };
    }

    /** 取骨架里某一类的全部槽位（按槽号升序）—— 构造 GUI 与自检共用。 */
    public static int[] slotsOf(GridCell cell) {
        List<Integer> list = new ArrayList<>();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (SKELETON[slot] == cell) {
                list.add(slot);
            }
        }
        int[] arr = new int[list.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    /** 供命令用：9 格纯网格（单空格分隔），直接与需求原文对照。 */
    public static List<String> gridOf() {
        StringBuilder sb = new StringBuilder();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (slot > 0) {
                sb.append(' ');
            }
            sb.append(classify(slot));
        }
        return List.of(sb.toString());
    }

    /**
     * 骨架自检：9 格、每格非空、各类语义都用到，且三个功能槽各恰好 1 格。
     *
     * <p>类初始化时跑一次；不合法直接抛异常，避免带着坏骨架上线。
     */
    private static void verifySkeleton() {
        if (SKELETON.length != INVENTORY_SIZE) {
            throw new IllegalStateException("木桩骨架不是 " + INVENTORY_SIZE + " 格");
        }
        Map<GridCell, Integer> count = new LinkedHashMap<>();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (SKELETON[slot] == null) {
                throw new IllegalStateException("木桩骨架槽 " + slot + " 为空");
            }
            count.merge(SKELETON[slot], 1, Integer::sum);
        }
        for (GridCell cell : GridCell.values()) {
            if (count.getOrDefault(cell, 0) <= 0) {
                throw new IllegalStateException("木桩骨架里没有用到 " + cell);
            }
        }
        if (count.getOrDefault(GridCell.INFO, 0) != 1
                || count.getOrDefault(GridCell.IO, 0) != 1
                || count.getOrDefault(GridCell.CORE_POS, 0) != 1) {
            throw new IllegalStateException("木桩骨架的 信息/输入输出/核心位置 必须各恰好 1 格");
        }
        if (count.getOrDefault(GridCell.BORDER, 0) != 6) {
            throw new IllegalStateException("木桩骨架的占位符应为 6 格，实际 "
                    + count.getOrDefault(GridCell.BORDER, 0));
        }
    }

    // ---------------------------------------------------------------- GUI 构造

    private void constructMenu(BlockMenuPreset preset) {
        preset.setSize(INVENTORY_SIZE);

        // ★ 一律通过 GuiLock 注册：默认全部锁死，只有显式声明的真实槽可交互。
        //   这样"占位玻璃板被玩家拿走"这类 bug 结构上不可能发生（详见 GuiLock 类注释）。
        final GuiLock lock = GuiLock.wrap(preset);

        // 需求：IO 是混合型输入输出端 —— 它是本界面唯一的"真实槽"
        //（玩家手动放取 + Cargo 进出货都走它）
        lock.markRealSlot(IO_SLOT);

        // X：占位符（灰色玻璃板，锁死）
        for (int slot : slotsOf(GridCell.BORDER)) {
            lock.addItem(slot, borderIcon());
        }

        // I：信息槽（POWER 量 + 激活状态）—— 可点击，点了重新读一次
        lock.button(INFO_SLOT, infoIcon(null), (p, e) -> handleInfoClick(p, e));

        // S：信息槽（多方块核心位置）—— 可点击，点了重新读一次
        lock.button(CORE_POS_SLOT, corePosIcon(null),
                (p, e) -> handleCorePosClick(p, e));

        // 安全网：其余未注册的格子全部锁死
        lock.autoGuard();
        this.guiLock = lock;
    }

    /** 供命令自检用：本方块最后构建出来的 GUI 注册器。 */
    private transient GuiLock guiLock;

    /** 当前这台木桩 GUI 的锁槽自检报告（未构建过返回 null）。 */
    public GuiLock guiLock() {
        return guiLock;
    }

    /** 取骨架里某一类的唯一槽位（不存在返回 -1）。 */
    public static int slotOf(GridCell cell) {
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            if (SKELETON[slot] == cell) {
                return slot;
            }
        }
        return -1;
    }

    /** X：占位符 —— 灰色玻璃板，不可取出、不可放入。 */
    private static ItemStack borderIcon() {
        return new CustomItemStack(Material.GRAY_STAINED_GLASS_PANE, "&7&l·");
    }

    // ---------------------------------------------------------------- tick / 刷新

    private void tickPost(Location loc) {
        // 拆掉的方块不会再 tick；这里只需要把两个信息槽刷成最新数字。
        refreshGui(loc, StorageCacheUtils.getMenu(loc));
    }

    /**
     * 木桩被拆：清掉本格的编号与核心绑定。
     *
     * <p>只清自己那一格。整座结构什么时候停、其余木桩的编号什么时候清，
     * 由赛钱箱的定期复检决定（{@link SaizenbakoManager}）。
     * <p>★ 需求原话「编号要持久化到木桩的方块数据，木桩被拆 / 结构失效时清除」——
     * 拆掉的那一根自己消失即可，另外 5 根的编号在核心复检时被清掉。
     */
    private void onPostRemoved(Location loc) {
        SaizenbakoManager.onPostRemoved(loc);
    }

    /**
     * 刷新两个信息槽，只在有人看着时写。
     *
     * <p>★ 必须 {@code hasViewer()} 才写：tick 是主线程的，但没人在看时
     * 每 tick 覆盖物品纯属浪费（还会让菜单一直处于 dirty 状态被反复落盘）。
     */
    private void refreshGui(Location loc, BlockMenu inv) {
        if (inv == null || !inv.hasViewer()) {
            return;
        }
        inv.replaceExistingItem(INFO_SLOT, infoIcon(loc));
        inv.replaceExistingItem(CORE_POS_SLOT, corePosIcon(loc));
    }

    // ---------------------------------------------------------------- 信息槽

    /**
     * I：信息槽 —— 当前 POWER 量 + 激活状态 + <b>本木桩的编号</b>。
     *
     * <p>POWER 量读的是<b>本格所属网络</b>的上一次结算结果
     * （{@code PowerNetwork.lastTotalCharge/lastTotalCapacity}），
     * 属于只读查询：不触发结算、不改任何电量。
     *
     * <p>★ 必须能在 {@code loc == null} 时安全返回：本方法会被 GUI 的构造期调用。
     */
    private ItemStack infoIcon(Location loc) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(powerLines(loc));
        lore.add("");
        lore.add(StructureState.stateLine(loc));
        lore.add(StructureState.indexLine(loc));
        lore.add("");
        lore.add("&7投料：把材料放进 &fIO 槽（下标 " + IO_SLOT + "）");
        lore.add("&7核心会把 6 根木桩的 IO 槽镜像到预留槽");
        lore.add("");
        lore.add("&e点击本格：刷新显示");
        return new CustomItemStack(Material.REDSTONE_TORCH, "&e木桩信息", lore);
    }

    /** POWER 那几行：能拿到网络就报总数，拿不到就说清楚为什么。 */
    private static List<String> powerLines(Location loc) {
        List<String> lines = new ArrayList<>();
        if (loc == null) {
            lines.add("&7POWER： &8(定位失败)");
            return lines;
        }
        var net = com.example.touhou.power.PowerNetworkManager.getNetworkFromLocationOrCreate(loc);
        if (net == null) {
            lines.add("&7POWER： &8未接入网络");
            lines.add("&8（本格本身不是 POWER 节点；");
            lines.add("&8  把 POWER 方块接到这座结构上即可）");
            return lines;
        }
        lines.add("&7POWER： &e" + fmt(net.lastTotalCharge())
                + " &7/ &e" + fmt(net.lastTotalCapacity()) + " &7POWER");
        lines.add("&8网络 #" + net.networkId()
                + " · 节点 " + net.size()
                + " · 储能点 " + countStorages(net));
        return lines;
    }

    /** 网络里有几个储能节点（集成核心 / 存储单元）。导体（中继器）不计。 */
    private static int countStorages(com.example.touhou.power.PowerNetwork net) {
        int n = 0;
        for (Location node : net.nodes()) {
            var pc = com.example.touhou.power.PowerComponent.at(node);
            if (pc != null && pc.powerType() != com.example.touhou.power.PowerComponent.NodeType.REPEATER) {
                n++;
            }
        }
        return n;
    }

    /** S：信息槽 —— 多方块核心位置 + 本编号对应哪个预留槽。 */
    private ItemStack corePosIcon(Location loc) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.addAll(StructureState.coreLine(loc, loc == null ? null : StructureState.coreOf(loc)));
        if (loc != null) {
            lore.add("&7木桩自身坐标： &f" + StructureState.xyz(loc));
        }
        int idx = StructureState.postIndexOf(loc);
        if (idx >= 0) {
            lore.add("");
            lore.add("&7木桩编号： &e#" + idx);
            lore.add("&7供料目标： &f核心预留槽 #" + idx
                    + " &8(下标 " + Saizenbako.reservedSlotOf(idx) + ")");
            lore.add("&7编号顺序： &8先 +X 方向、后 +Z 方向");
        }
        lore.add("");
        lore.add("&e点击本格：刷新显示");
        return new CustomItemStack(Material.COMPASS, "&e核心位置", lore);
    }

    // ---------------------------------------------------------------- 按键

    /** I 点击：刷新显示（编号 / 激活状态 / POWER 都从方块数据现读）。 */
    private void handleInfoClick(Player p, org.bukkit.event.inventory.InventoryClickEvent event) {
        refreshFrom(p, event);
    }

    /** S 点击：只是刷新（同上）。 */
    private void handleCorePosClick(Player p, org.bukkit.event.inventory.InventoryClickEvent event) {
        refreshFrom(p, event);
    }

    /**
     * 定位本木桩并刷新界面。
     *
     * <p>★ 定位失败必须<b>早退</b>：{@code StorageCacheUtils.getMenu(null)} 的行为未定义
     * （不同 fork 版本可能直接 NPE），而且这里本来就没有任何东西可刷。
     * 失败时按 {@link Notify} 的规矩给一条 warning —— 玩家主动点了却没反应，
     * 静默失败比刷一条消息更糟。
     */
    private void refreshFrom(Player p, org.bukkit.event.inventory.InventoryClickEvent event) {
        Location loc = locatePost(p, event);
        if (loc == null) {
            Notify.warn(Notify.saizen(), p, "&c无法定位这根木桩，请关掉界面后重新右键打开");
            return;
        }
        refreshGui(loc, StorageCacheUtils.getMenu(loc));
    }

    /**
     * 定位"当前这个菜单属于哪根木桩"。
     *
     * <p>与 {@link AbstractReactorPort#locatePort} 同一条思路，按可靠性排序：
     * <ol>
     *   <li>事件带来的 {@code Inventory} 的 holder（{@code BlockMenu} 带 Location）；</li>
     *   <li>退回"玩家看向的方块"（界面挡住视线时可能失败，但只影响提示，不影响功能）。</li>
     * </ol>
     */
    private Location locatePost(Player p, org.bukkit.event.inventory.InventoryClickEvent event) {
        if (event != null) {
            try {
                var inv = event.getInventory();
                var holder = inv == null ? null : inv.getHolder();
                if (holder instanceof BlockMenu menu) {
                    Location l = menu.getLocation();
                    if (l != null && me.mrCookieSlime.Slimefun.api.BlockStorage.check(l) == this) {
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
        return me.mrCookieSlime.Slimefun.api.BlockStorage.check(l) == this ? l : null;
    }

    // ---------------------------------------------------------------- 自检 / 工具

    /**
     * 自检：把 9 格布局渲染成一行并与<b>由骨架推导的期望</b>比对。
     *
     * <p>期望值不另写一份常量，直接来自 {@link #classify} —— 单一数据源。
     */
    public List<String> layoutSummary() {
        List<String> out = new ArrayList<>();
        Map<String, Integer> count = new LinkedHashMap<>();
        for (int slot = 0; slot < INVENTORY_SIZE; slot++) {
            count.merge(classify(slot), 1, Integer::sum);
        }
        out.add("结构方块=" + getId() + "  标题=" + inventoryTitle());
        out.add("尺寸 " + INVENTORY_SIZE
                + " | X占位=" + count.getOrDefault("X", 0)
                + " | I信息=" + count.getOrDefault("I", 0)
                + " | IO输入输出=" + count.getOrDefault("IO", 0)
                + " | S核心位置=" + count.getOrDefault("S", 0)
                + " | 合计=" + count.values().stream().mapToInt(Integer::intValue).sum());
        out.add("  槽位 " + String.join(" ", gridOf()));
        out.add("  说明 0X 1X 2X 3I 4IO 5S 6X 7X 8X（与 spec 逐格一致）");
        out.add("  物流 " + (TRANSPORT_SLOTS.length == 1 && TRANSPORT_SLOTS[0] == IO_SLOT
                ? "OK Cargo 可对槽 " + IO_SLOT + " 进出货（INSERT + WITHDRAW）"
                : "FAIL 物流槽位不是唯一的 IO 槽"));
        return out;
    }

    /** 千分位格式（与 POWER 悬浮字同一套写法）。 */
    private static String fmt(long v) {
        return String.format("%,d", Math.max(0, v));
    }
}
