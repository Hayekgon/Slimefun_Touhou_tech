package com.example.touhou.core;

import com.example.touhou.Touhou;
import com.example.touhou.core.AddonConfig;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.operations.FuelOperation;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AGenerator;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineFuel;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 「旧地狱-灵乌路空反应堆」—— 多方块核心 + 配方机器。
 *
 * <p>基类选择：继承本体的 {@link AGenerator}，理由不是"省事"，而是它的
 * {@code getGeneratedOutput} 恰好就是 spec 要的语义：
 *
 * <pre>
 *   if (capacity - charge >= production) { operation.addProgress(1); return production; }
 *   else                                 { return 0; }        ← 不推进度 = 暂停，进度保留
 * </pre>
 *
 * 也就是"发电模式的暂停机制"本身就是本体岩浆发电机的原生行为（spec 明确要求
 * "完全相同"），我们只需要在它之上加：结构门控、阈值门控、产物模式、GUI。
 *
 * <p>生命周期钩子（都在本类）：
 * <ul>
 *   <li>{@link #tick} —— 主线程；负责状态机、结构检测、GUI 刷新、燃料进程的启动与收尾；</li>
 *   <li>{@link #getGeneratedOutput(Location, ASlimefunDataContainer)} —— 本体电力网络每 tick 调用，
 *       负责"发多少电 + 推不推进度"，是暂停机制的唯一实现点；</li>
 *   <li>右键核心 —— 把"未激活"转成"空闲中"（spec 的手动激活要求）。</li>
 * </ul>
 */
public class UtsuhoReactorCore extends AGenerator
        implements io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem {

    // ---------------------------------------------------------------- GUI 布局
    //
    // 布局按用户实测截图定稿，并且<b>刻意给本体原生进度条留出槽位 22</b>：
    //   列:    0    1    2    3    4    5    6    7    8
    //   行0:   x    x    x    J    Ox   K    x    x    x
    //   行1:   Ix   I    x    P    Ox   O    O    O    O
    //   行2:   Ix   I    H    B    Ox   O    O    O    O
    //   行3:   Ix   I    R    Ox   Ox   O    O    O    O
    //   行4:   Ix   I    x    Ox   Ox   O    O    O    O
    //   行5:   x    x    x    x    x    x    x    x    x
    //
    //   `·` = 槽 12，留给<b>我们自己的</b>特效开关（原生的进度条在槽 22）
    //   J = 第 0 行列 3（点它 = 结构检测 + 激活）
    //   K = 第 0 行列 5（点它 = 发电模式 / 产物模式 切换）
    //   B = 第 2 行列 3（点它 = 手动构建 / 自动构建 切换，见 BuildMode）
    //   H = 第 2 行列 2（点它 = 多方块投影开关，见 MultiBlockProjection）
    //   R = 第 3 行列 2（点它 = 投影顺时针转 90°，见 MultiBlockProjection#rotate）
    //   O = 第 1~4 行 × 第 6~9 列的 4×4 输出区
    //
    //   ★ 为什么输出区从第 6 列（而不是第 5 列）开始：
    //     本体 {@code AGenerator#getGeneratedOutput} 把进度条<b>硬编码</b>写在槽 22
    //     （= 行 2 列 4 = 我们坐标系里的第 3 行列 5）。如果输出区覆盖 22，
    //     每 tick 的进度条写入就会覆盖/吃掉落在 22 的输出物。
    //     把输出区右移一列，槽 22 自然成为"原生进度条专用格"，两者不再打架。
    //
    //   17 玻璃板(x) + 4 输入提示(Ix) + 4 输入(I) + 1 信息(J) + 1 模式(K)
    //   + 1 特效开关(P) + 1 构建模式(B) + 1 投影开关(H) + 1 投影旋转(R)
    //   + 16 输出(O) + 7 输出提示(Ox) = 54   （槽 22 是 Ox 里的那一格，不重复计数）
    //   自检：/touhou layout（逐格比对 + 合计必须 54）

    /** x：普通占位玻璃板。 */
    private static final int[] BORDER = {
            0, 1, 2,
            6, 7, 8,
            11, 38,
            45, 46, 47, 48, 49, 50, 51, 52, 53
    };
    /** Ix：输入槽提示占位符（4 个，第 1~4 行列 0）。 */
    private static final int[] INPUT_BORDER = {9, 18, 27, 36};
    /** I：真正的输入槽（4 个，第 1~4 行列 1）。 */
    private static final int[] INPUT_SLOTS = {10, 19, 28, 37};
    /** J：信息显示 + 激活按钮（第 0 行列 3）。 */
    private static final int INFO_SLOT = 3;
    /** K：模式显示与切换（第 0 行列 5）。 */
    private static final int MODE_SLOT = 5;
    /**
     * B：构建模式显示与切换（槽 21 = 第 2 行列 3）。
     *
     * <p>★ 位置选择：{@link #MODE_SLOT} 正下方一格 —— 与
     * {@link #INFO_SLOT} / {@link #PARTICLE_SLOT} 那一对（信息在上、开关在下）
     * 保持同一套视觉关系：J/K 是"两个主按钮"，它们下方各自挂一个开关。
     */
    private static final int BUILD_MODE_SLOT = 21;
    /**
     * Ox：输出槽提示占位符（第 0 行列 4 + 第 1/3/4 行列 4 + 第 3/4 行列 3 = 1 + 3 + 2 = 6 处）。
     *
     * <p>★ 相对最初的 8 处，这里去掉了两个被改作它用的槽：
     * <ul>
     *   <li>槽 21 → {@link #BUILD_MODE_SLOT}（构建模式开关）；</li>
     *   <li>槽 12 → {@link #PARTICLE_SLOT}（特效开关）。</li>
     * </ul>
     * 槽 22 仍在表里，但它是<b>原生进度槽</b>的落点（{@link #VANILLA_PROGRESS_SLOT}）——
     * 空闲时画成输出占位符，所以在两处都出现是<b>有意</b>的，不是重复计数。
     */
    private static final int[] OUTPUT_PLACEHOLDER = {
            4,
            13, 22, 30, 31, 39, 40
    };
    /** O：真正的输出槽 —— 第 1~4 行 × 第 6~9 列的 4×4 区域，共 16 个（避开槽 22）。 */
    private static final int[] OUTPUT = {
            14, 15, 16, 17,
            23, 24, 25, 26,
            32, 33, 34, 35,
            41, 42, 43, 44
    };

    /**
     * 附加粒子特效开关槽（槽 12 = 第 1 行列 3）。
     *
     * <p>★ 关于位置：spec 说"放在 INFO 正上方的第一个格子"，但 {@link #INFO_SLOT} 是槽 3，
     * 已经在背包<b>第一行</b>了 —— 它正上方是标题栏，背包里没有那一格。
     * 所以放在同一列紧邻的下一格（槽 12），也就是 INFO 正下方第一格。
     * 要换位置只改这个常量（并同步 {@code classify}/{@code layoutSummary} 的期望图）。
     *
     * <p>这个格子原来只是"输出区留白"（输出占位符外观），挪来当开关不会挤掉任何功能。
     */
    private static final int PARTICLE_SLOT = 12;

    /**
     * 本体 {@code AGenerator} 硬编码写进度条的槽位（22）。
     *
     * <p>它把 {@code processor.updateProgressBar(inv, 22, operation)} 写死成 22，
     * 子类无法覆写（{@code processor} 字段是 private）。我们的应对办法是
     * <b>把输出区避开这个格子</b>，让 22 成为原生进度条的专用格 ——
     * 这样既保留了原生进度显示，又不会被它吃掉产物。
     */
    private static final int VANILLA_PROGRESS_SLOT = 22;

    /**
     * H：<b>多方块投影开关</b>（槽 20 = 第 2 行列 2）。
     *
     * <p>★ 位置选择：这一格原本是 {@link BORDER} 里的普通占位玻璃板，
     * 而且它<b>不在</b>任何既有用途里 —— 不是信息槽 J（3）、不是模式槽 K（5）、
     * 不是构建模式 B（21）、不是特效开关 P（12）、不是原生进度槽（22）、
     * 也不属于输入/输出区。所以拿它当第三个开关不会挤掉任何功能，
     * 与 J/K/B/P 形成"一列一个开关"的整齐关系（第 2 列从上往下：
     * 输入提示 → 投影 → 构建模式 → …）。
     *
     * <p>它<b>曾经</b>被 {@code classify}/{@code layoutSummary} 当作空白区算进输出占位符，
     * 所以那两个地方也要同步（{@code /touhou layout} 会立刻发现漏改）。
     */
    public static final int HOLOGRAM_SLOT = 20;

    /**
     * <b>投影旋转按钮</b>（槽 29 = {@link #HOLOGRAM_SLOT} 正下方一格）。
     *
     * <p>★ 位置选择：它原本是 {@link #BORDER} 里的普通占位玻璃板，
     * 而且不落在任何既有用途上 —— 把"开关"和"开关的下一个动作"上下摆成一对，
     * 与 {@link #INFO_SLOT}(J) / {@link #PARTICLE_SLOT}(P)、
     * {@link #MODE_SLOT}(K) / {@link #BUILD_MODE_SLOT}(B) 那两对是同一套视觉关系。
     *
     * <p>⚠ 改这个常量必须同步改三处，否则 {@code /touhou layout} 会报不一致：
     * {@link #BORDER}（不能再把它当占位符）、{@link #classify}、{@link #layoutSummary} 的期望图。
     *
     * <p>★ 反应堆这座结构是<b>四向对称</b>的，所以这个按钮点下去会被明确拒绝
     * （"对称结构无需旋转"），不会假装转成功 —— 理由见
     * {@link MultiBlockProjection#rotate}。
     */
    public static final int HOLOGRAM_ROTATE_SLOT = 29;

    private static final int INVENTORY_SIZE = 54;

    /** 输入槽的公开副本（命令/自检用，避免把内部数组暴露出去被改）。 */
    public static final int[] INPUT_SLOT_ALL = INPUT_SLOTS.clone();
    /** 输出槽的公开副本（IO 接口代搬时要按同一套槽位对搬）。 */
    public static final int[] OUTPUT_SLOT_ALL = OUTPUT.clone();

    // ---------------------------------------------------------------- 构造

    public UtsuhoReactorCore(ItemGroup itemGroup, SlimefunItemStack item,
                             RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        // 接管 GUI：注册我们自己的 preset（同 id，覆盖父类构造器里建的那个）
        new BlockMenuPreset(getId(), getInventoryTitle()) {
            @Override
            public void init() {
                UtsuhoReactorCore.this.constructMenu(this);
            }

            @Override
            public boolean canOpen(Block b, Player p) {
                return p.hasPermission("slimefun.inventory.bypass")
                        || (canUse(p, false) && Slimefun.getProtectionManager()
                                .hasPermission(p, b.getLocation(), Interaction.INTERACT_BLOCK));
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                if (ReactorManager.config().disableItemTransport) {
                    return new int[0];      // 反应堆是多方块核心，产物不给货运网络自动抽走
                }
                return flow == ItemTransportFlow.INSERT ? getInputSlots() : getOutputSlots();
            }
        };
        getMachineProcessor().setProgressBar(getProgressBar());

        // 主线程 tick：状态机 + 结构检测 + 进程启停 + GUI 刷新
        addItemHandler(new me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return true;        // 状态机要读世界方块，必须主线程
            }

            @Override
            public void tick(Block b, io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem item,
                             SlimefunBlockData data) {
                UtsuhoReactorCore.this.tickReactor(b);
            }
        });

        addItemHandler(new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(BlockBreakEvent e, ItemStack tool, List<ItemStack> drops) {
                ReactorManager.forget(e.getBlock().getLocation());
                ReactorParticles.forget(e.getBlock().getLocation());
                // ★ 核心被拆：投影实体必须先清掉 —— 它们不是方块、不会随方块消失，
                //   留着就是一组飘在空中的孤儿（LogiTech 正是踩了这个坑才做了
                //   HOLOGRAM_REMOVER 道具与 removeUnrecordedHolograms）。
                MultiBlockProjection.forget(e.getBlock().getLocation());
            }
        });

        // ★★ 这里【绝对不能】再注册 BlockUseHandler —— 否则右键打不开 GUI。
        //   本体 SlimefunItemInteractListener#rightClickBlock 的逻辑是：
        //       boolean interactable = sfItem.callItemHandler(BlockUseHandler.class, h -> h.onRightClick(event));
        //       if (!interactable) { openInventory(...); }     // ← 只有 false 才开界面
        //   而 callItemHandler 的返回值是"这个物品有没有注册该 handler"，
        //   不是 handler 的业务结果 —— 一旦注册了就恒为 true，菜单永远不会被打开。
        //   （第一版就是在这里加了个"右键时提示去点信息格"的 handler，结果界面直接打不开。）
        //   激活指引写在信息格（J）的 lore 里，不需要额外的右键提示。
    }

    // ---------------------------------------------------------------- AGenerator 契约

    @Override
    public String getInventoryTitle() {
        return "旧地狱-灵乌路空反应堆";
    }

    /**
     * 进度条物品 —— <b>用本体原生的</b>（烈焰粉）。
     *
     * <p>本体 {@code AGenerator#getGeneratedOutput} 会把它画在硬编码的槽
     * {@link #VANILLA_PROGRESS_SLOT}（22）上，带本体自带的进度 lore 与百分比。
     * 我们不再自研进度显示，所以这里恢复成原生物品；
     * 输出区已经刻意避开 22，两者不会再抢格子。
     */
    @Override
    public ItemStack getProgressBar() {
        return new ItemStack(Material.BLAZE_POWDER);
    }

    @Override
    public int getEnergyProduction() {
        return (int) Math.min(Integer.MAX_VALUE, ReactorManager.config().energyProduction);
    }

    @Override
    public int getCapacity() {
        // 本体是 int 电容；spec 的 2^31-1 正好是 int 上限，所以直接用 getCapacity 即可
        return ReactorManager.config().energyCapacity;
    }

    @Override
    protected void registerDefaultFuelTypes() {
        // 注意：本方法在 super() 构造期间就会被调用，此时子类实例字段还没初始化，
        // 所以这里只能引用静态常量与懒加载的配置。
        registerFuel(new MachineFuel(
                ReactorManager.config().processTicks,
                SlimefunItems.OIL_BUCKET,
                AddItems.BLAZING_ASH.clone()));
    }

    @Override
    public int[] getInputSlots() {
        return INPUT_SLOTS.clone();
    }

    @Override
    public int[] getOutputSlots() {
        return OUTPUT.clone();
    }

    // ---------------------------------------------------------------- 粘液书配方页
    //
    // ★★ 这一页的内容【全部现场生成】，一个数字都没有写死在展示代码里：
    //    · 燃料配方  → 遍历本体 AbstractEnergyProvider 里已注册的 MachineFuel
    //                  （我们只 registerFuel 了一条，但展示这边不假设有几条）；
    //    · 进程耗时  → MachineFuel#getTicks()（= AddonConfig#processTicks）；
    //    · 产物      → MachineFuel#getOutput()；
    //    · 发电参数  → AddonConfig 的 energyProduction / energyCapacity /
    //                  modeThreshold / productModeSpeedMultiplier / productModeEnergyRate。
    //    所以以后改 config.yml（甚至加第二条 MachineFuel）→ 重新打开指南就能看到新数值，
    //    展示代码一行都不用动。
    //
    // ★ 为什么不做缓存：指南每次翻页/重开都会重新调 getDisplayRecipes()，
    //   缓存只会带来"改了配置书里不变"的假象。这一页只有几个 ItemStack，现算成本可以忽略。

    /**
     * 指南页底部网格的内容（LogiTech 那种「材料 N / 输入数量 / 产物 N / 进程耗时」的图标）。
     *
     * <p>布局：每一条燃料配方占若干行（左列输入、右列输出），
     * 最后再补一行"运转参数"（左边留空、右边是参数说明）。
     *
     * <p>★ 这里<b>不用</b> {@link ReactorManager#effectiveProduction(Location)} 那类
     * "要看具体方块"的方法：指南页是<b>物品</b>的页面，没有 Location，
     * 拿 config 里的额定数值才是对的（也才不会在打开书的时候去读世界方块）。
     */
    @Override
    public List<ItemStack> getDisplayRecipes() {
        List<ItemStack> display = new ArrayList<>();

        // ---- ① 燃料配方：从已注册的 MachineFuel 表来 ----
        for (MachineFuel fuel : sortedFuels()) {
            List<ItemStack> inputs = new ArrayList<>();
            List<ItemStack> outputs = new ArrayList<>();
            ItemStack in = fuel.getInput();
            inputs.add(RecipePages.input(in, 0,
                    in == null ? 1 : Math.max(1, in.getAmount()),
                    List.of("&8燃料", "&8投入核心的输入槽")));
            ItemStack out = fuel.getOutput();
            if (out != null && !out.getType().isAir()) {
                outputs.add(RecipePages.output(out, 0, Math.max(1, out.getAmount()),
                        fuelTicksOf(fuel), List.of("&8进程结束后产出")));
            }
            display.addAll(RecipePages.block(inputs, outputs));

            // 空桶：进程结束时本体 AGenerator 会把"桶装燃料"换成空桶吐出来
            // （判据就是本类自己的 isBucketItem，与真正跑机器时用的是同一个方法）。
            // ★ 用 note 而不是 output：它不是"产物 2"，说成产物会误导
            //   （玩家会以为烧一桶能额外得到什么东西）。所以左边留空、右边单独一行。
            if (isBucketItem(in)) {
                display.addAll(RecipePages.block(List.of(),
                        List.of(RecipePages.note(new ItemStack(Material.BUCKET),
                                "&e装燃料的桶会退回",
                                List.of("", "&7进程结束时空桶回到核心的输出槽")))));
            }
        }

        // ---- ② 运转参数：没有输入，只有一行说明（放在输出列，左列自然留空） ----
        AddonConfig cfg = ReactorManager.config();
        long productModeOutput = (long) (cfg.energyProduction * cfg.productModeEnergyRate);
        List<String> params = new ArrayList<>();
        params.add("");
        params.add("&a运转参数");
        params.add("&7发电功率： &e" + String.format("%,d", cfg.energyProduction) + " &7J/tick");
        params.add("&7储电上限： &e" + String.format("%,d", cfg.energyCapacity) + " &7J");
        params.add("&7发电模式暂停阈值： &e" + String.format("%,d", cfg.modeThreshold) + " &7J");
        params.add("&8达到阈值即暂停进程（进度保留），直到电量被用掉");
        params.add("&7产物模式： &f效率 " + trimDouble(cfg.productModeSpeedMultiplier * 100)
                + "% &7/ &f电量 " + trimDouble(cfg.productModeEnergyRate * 100) + "%"
                + " &8(" + String.format("%,d", productModeOutput) + " J/tick)");
        params.add("&8需要结构完整 + 已激活才会运转");
        display.addAll(RecipePages.block(List.of(),
                List.of(RecipePages.note(Material.REDSTONE, "&e反应堆运转参数", params))));

        return display;
    }

    /** 指南里那一段的标题（默认是英文/本地化的"机器配方"，这里给中文）。 */
    @Override
    public String getRecipeSectionLabel(org.bukkit.entity.Player p) {
        return "&7⇩ &f运转配方 &7⇩";
    }

    /**
     * 已注册的燃料配方，<b>排序后</b>返回。
     *
     * <p>★ 为什么要排序：本体的 {@code AbstractEnergyProvider#fuelTypes} 是 {@link java.util.HashSet}
     * （已用 {@code javap} 确认），遍历顺序取决于对象的 identity hash ——
     * 同一份代码两次启动可能给出不同的顺序。指南页的顺序跟着它漂移会非常难排查
     * （"我什么都没改，怎么书里两条配方的上下位置变了"），所以这里定死一个顺序：
     * 先按进程耗时、再按输入物品的显示名。
     */
    private List<MachineFuel> sortedFuels() {
        List<MachineFuel> fuels = new ArrayList<>(getFuelTypes());
        fuels.sort((a, b) -> {
            if (a == null || b == null) {
                return a == b ? 0 : (a == null ? 1 : -1);
            }
            if (a.getTicks() != b.getTicks()) {
                return Integer.compare(a.getTicks(), b.getTicks());
            }
            return RecipePages.labelOf(a.getInput()).compareTo(RecipePages.labelOf(b.getInput()));
        });
        return fuels;
    }

    /** 把倍率写成不带多余小数的文本（5.0 → "500"，12.5 → "12.5"）。 */
    private static String trimDouble(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0e-6D) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.valueOf(Math.round(value * 10.0D) / 10.0D);
    }

    /**
     * 这条燃料<b>真正</b>要跑多少个 Slimefun tick。
     *
     * <p>★★ 为什么是 {@code getTicks() / 2} 而不是 {@code getTicks()}：
     * 本体的 {@code MachineFuel} 构造器会把传进来的 tick <b>乘 2</b> 再存起来
     * （用 {@code javap -c} 看字节码是 {@code iconst_2; imul; putfield ticks}）。
     * 我们注册时传的是 {@code AddonConfig#processTicks}（默认 600），
     * 所以 {@code getTicks()} 读回来是 1200 —— 若照抄它，书里会写"1200 tick"，
     * 而机器真正开的进程只有 600 tick（见 {@link #tryStartProcess} 里
     * {@code new FuelOperation(..., ReactorManager.config().processTicks)}），
     * 也就是与核心 GUI 信息格显示的"本次进程总时长"对不上。
     * 除以 2 正好还原"注册时写下的那个数"，对我们这条燃料就是 {@code processTicks}。
     */
    private static int fuelTicksOf(MachineFuel fuel) {
        if (fuel == null) {
            return ReactorManager.config().processTicks;
        }
        int raw = fuel.getTicks();
        return raw > 1 ? raw / 2 : ReactorManager.config().processTicks;
    }

    // ---------------------------------------------------------------- GUI

    /**
     * 接管 GUI 外观。
     *
     * <p>★ 为什么不是 {@code @Override public void constructMenu(BlockMenuPreset)}：
     * 本体的 {@code AGenerator} 里 {@code constructMenu} 是 <b>private</b> 的 ——
     * 它在自己的构造器里 {@code new BlockMenuPreset(...) { public void init() { constructMenu(this); } }}，
     * 那个 {@code constructMenu} 是<b>私有方法引用</b>，子类既看不到也覆盖不了。
     *
     * <p>2025.1 与 2026.07 两个版本都是这个结构。所以正确的接管方式只有一条：
     * 由子类自己再 {@code new} 一个 {@link BlockMenuPreset}（id 必须仍是 {@code getItem().getItemId()}），
     * 在它的 {@code init()} 里调用本类自己的构造逻辑。即使后来重写了菜单，
     * 父类那个 preset 也只是闲置，不会冲突（Slimefun 以 id 取 preset，后者覆盖前者）。
     */
    private void constructMenu(BlockMenuPreset preset) {
        preset.setSize(INVENTORY_SIZE);

        // ★ 统一走 GuiLock：默认全部锁死，只有显式声明的真实槽可交互。
        //   这样"占位符 / 按钮图标能被拿走"这类 bug 结构上不会发生（详见 GuiLock 类注释）。
        final GuiLock lock = GuiLock.wrap(preset);

        // 输入槽 = 真正可放取的槽
        lock.markRealSlot(INPUT_SLOTS);

        for (int slot : BORDER) {
            lock.addItem(slot, ChestMenuUtils.getBackground());
        }
        for (int slot : INPUT_BORDER) {
            lock.addItem(slot, ChestMenuUtils.getInputSlotTexture());
        }
        for (int slot : OUTPUT_PLACEHOLDER) {
            lock.addItem(slot, ChestMenuUtils.getOutputSlotTexture());
        }

        // J：信息显示 + 【激活按钮】——
        //   玩家点这一格时做一次结构检测并尝试激活（未激活 → 空闲中）
        lock.button(INFO_SLOT, infoPlaceholder(), (p, e) -> handleInfoClick(p, e));

        // K：模式切换
        lock.button(MODE_SLOT, modeIcon(ReactorMode.GENERATE),
                (p, e) -> handleModeClick(p, MODE_SLOT, e));

        // B：构建模式显示与切换（槽 21 = K 模式槽正下方）
        lock.button(BUILD_MODE_SLOT, buildModeIcon(BuildMode.MANUAL),
                (p, e) -> handleBuildModeClick(p, BUILD_MODE_SLOT, e));

        // 附加粒子特效开关（槽 12 = INFO 正下方第一格）
        lock.button(PARTICLE_SLOT, particlesIcon(true),
                (p, e) -> handleParticlesClick(p, PARTICLE_SLOT, e));

        // H：多方块投影开关（槽 20 = 第 2 行列 2，原本是普通占位玻璃板）
        lock.button(HOLOGRAM_SLOT, projectionIcon(false),
                (p, e) -> handleHologramClick(p, HOLOGRAM_SLOT, e));

        // Hr：投影旋转（槽 29 = H 正下方一格，原本也是普通占位玻璃板）
        //   ★ 初始图标按 loc == null 生成（构造期没有方块），真实朝向由 refreshGui 刷新。
        lock.button(HOLOGRAM_ROTATE_SLOT, projectionRotateIcon(null),
                (p, e) -> handleHologramRotateClick(p, HOLOGRAM_ROTATE_SLOT, e));

        // 输出槽：只出不进（产物能取走，但塞不进东西）
        for (int slot : OUTPUT) {
            lock.outputSlot(slot);
        }

        // 安全网：其余未注册的格子全部锁死
        lock.autoGuard();
        this.guiLock = lock;
    }

    /** 供命令自检用：本核心最后构建出来的 GUI 注册器。 */
    private transient GuiLock guiLock;

    /**
     * 投影图标上一次写进去的状态（{@code null} = 还没写过）。
     *
     * <p>★ 为什么需要它：{@link #refreshGui} 是<b>每 tick</b> 调的，
     * 而投影状态几乎从不变。不加这道比对的话，每次刷新都要重建一次图标
     * （要 clone 物品、拼 10 行 lore、写 ItemMeta），纯属浪费。
     * 与 {@code ReactorParticles}/{@code SaizenbakoManager} 那边
     * "只在结论变化时落盘"是同一个思路。
     */
    private transient Boolean lastHologramIconState;

    /**
     * 旋转按钮图标上一次写进去的状态（{@code null} = 还没写过）。
     *
     * <p>与 {@link #lastHologramIconState} 同一个理由（{@link #refreshGui} 是每 tick 调的），
     * 但这一格的图标同时取决于<b>朝向</b>与<b>开关</b>两件事，所以键是两者的组合串，
     * 而不是一个布尔。
     */
    private transient String lastRotateIconKey;

    /** 本核心 GUI 的锁槽自检报告。 */
    public GuiLock guiLock() {
        return guiLock;
    }

    /** 模式槽点击：切换模式并刷新。 */
    private void handleModeClick(Player p, int slot, InventoryClickEvent event) {
        if (slot != MODE_SLOT) {
            return;
        }
        Location loc = locateMachine(p, event);
        if (loc == null) {
            return;
        }
        ReactorMode mode = ReactorManager.toggleMode(loc);
        // 槽位图标自己会变，聊天栏不必再回显一次
        Notify.info(p, "&7已切换到 " + mode.display());
        refreshGui(loc, StorageCacheUtils.getMenu(loc));
    }

    /** 特效开关槽点击：切换这台机器的粒子特效并刷新。 */
    private void handleParticlesClick(Player p, int slot, InventoryClickEvent event) {
        if (slot != PARTICLE_SLOT) {
            return;
        }
        Location loc = locateMachine(p, event);
        if (loc == null) {
            return;
        }
        boolean on = ReactorManager.toggleParticles(loc);
        // 粒子的有无玩家一眼就能看见，聊天栏回显属于噪音
        Notify.info(p, (on ? "&a已开启" : "&c已关闭") + "&7附加粒子特效");
        refreshGui(loc, StorageCacheUtils.getMenu(loc));
    }

    /**
     * 投影开关槽点击：切换这台反应堆的多方块投影并刷新。
     *
     * <p>真正的开关逻辑在 {@link #toggleProjection} —— 抽出来是为了让控制台命令
     * （{@code /touhou proj ...}）走<b>同一条</b>链路，无头验证过的行为就是
     * 玩家点出来的行为（与 {@code simulateInfoClick} 同一个理由）。
     */
    private void handleHologramClick(Player p, int slot, InventoryClickEvent event) {
        if (slot != HOLOGRAM_SLOT) {
            return;
        }
        Location loc = locateMachine(p, event);
        if (loc == null) {
            Notify.warn(p, "&c无法定位反应堆，请关掉界面后对着反应堆右键重新打开");
            return;
        }
        toggleProjection(p, loc);
        refreshGui(loc, StorageCacheUtils.getMenu(loc));
    }

    /**
     * 投影<b>旋转</b>槽点击：把投影朝向顺时针转 90° 并刷新。
     *
     * <p>与 {@link #handleHologramClick} 是同一套写法（定位 → 调静态入口 → 刷新），
     * 所以 {@code /touhou proj <x y z> rotate} 验证过的行为就是玩家点出来的行为。
     *
     * <p>⚠ 反应堆结构四向对称，所以这里正常情况下会得到一条
     * "对称结构无需旋转"的拒绝提示（{@link Notify#warn}，永远输出）——
     * 这不是 bug，是需求要求"说清楚"而不是"假装转成功"。
     */
    private void handleHologramRotateClick(Player p, int slot, InventoryClickEvent event) {
        if (slot != HOLOGRAM_ROTATE_SLOT) {
            return;
        }
        Location loc = locateMachine(p, event);
        if (loc == null) {
            Notify.warn(p, "&c无法定位反应堆，请关掉界面后对着反应堆右键重新打开");
            return;
        }
        rotateProjection(p, loc);
        refreshGui(loc, StorageCacheUtils.getMenu(loc));
    }

    /**
     * <b>反应堆的多方块投影宿主</b> —— 把"用哪套结构"与"显示什么图标"配成一对。
     *
     * <p>这里就是本工程"面向未来"的那条接缝：投影机制本身（{@link MultiBlockProjection}）
     * 完全不知道反应堆的存在，它只认这个二元组。将来新加一种多方块结构，
     * <b>照这四行再写一个宿主</b>就能有投影，投影代码一行都不用动。
     */
    public static ReactorStructure.ProjectionHost projectionHost() {
        return ReactorStructure.ProjectionHost.of(ReactorManager.structure(),
                dir -> displayMapping());
    }

    /**
     * <b>投影开关</b>（GUI 与控制台命令共用同一条链路）。
     *
     * <p>真正的实现在 {@link MultiBlockProjection#toggle} —— 那是一份
     * <b>与结构无关</b>的通用实现（"先清旧组再画"、结构不完整就拒绝、
     * 朝向取现场命中的那个、对称结构强制 NORTH）。本方法只负责把
     * 反应堆自己的三样东西接上去：<b>哪个结构、哪份图标映射、朝向从哪来</b>。
     *
     * <p>抽出来（而不是写在点击回调里）是为了让 {@code /touhou proj ...}
     * 走同一条链路 —— 无头验证过的行为就是玩家点出来的行为
     * （与 {@link #simulateInfoClick} 同一个理由）。
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
            Log.info("[MBREACTOR] 开启投影 @ " + TouhouData.xyz(loc)
                    + " 构件 " + MultiBlockProjection.lastCellCount() + " 格");
        }
        return on;
    }

    /**
     * <b>投影旋转</b>（GUI 的旋转按钮与控制台命令共用同一条链路）。
     *
     * <p>真正的实现在 {@link MultiBlockProjection#rotate} —— 与投影开关一样，
     * 那是一份<b>与结构无关</b>的通用实现（对称结构拒绝、转过之后立刻按新朝向重画、
     * 只写 {@code touhou:mb-holo-dir} 不碰结构朝向键）。
     *
     * <p>★ 反应堆是<b>四向对称</b>结构，所以本方法在反应堆上总是返回一条拒绝提示
     * —— 这不是"没实现"，而是"如实告诉玩家这个结构转了也白转"（见
     * {@link MultiBlockProjection#rotate} 里的三条理由）。
     *
     * @param p   触发者；{@code null} = 控制台（反馈改走 {@code Log.command}）
     * @param loc 核心位置
     * @return 本次生效的投影朝向
     */
    public static ReactorStructure.Direction rotateProjection(Player p, Location loc) {
        if (loc == null) {
            return ReactorStructure.Direction.NORTH;
        }
        ReactorStructure.Direction now = MultiBlockProjection.rotate(loc, projectionHost(),
                line -> notifyProjection(p, line));
        Log.info("[MBREACTOR] 投影朝向 @ " + TouhouData.xyz(loc) + " -> " + now.label()
                + "（投影 " + (MultiBlockProjection.isOn(loc) ? "开" : "关") + "）");
        return now;
    }

    /**
     * 投影反馈。
     *
     * <p>★ 用 {@link Notify#warn} 而不是 {@code info}：投影开关是对玩家点击的
     * <b>直接反馈</b>，而反应堆的消息档位默认是 {@code important}（{@code info} 要求 {@code NORMAL} 档），
     * 用 info 会让"点了没反应"变成真的没反应 —— 失败原因一个字都不显示。
     * 这类消息一次点击最多一条，不会刷屏。
     */
    private static void notifyProjection(Player p, String text) {
        if (p != null) {
            Notify.warn(p, text);
        } else {
            Log.command("[投影] " + Notify.plain(text));
        }
    }

    /**
     * {@code partId → 显示物品} 映射 —— 即 LogiTech 的 {@code getIdMappingDisplayUse()}。
     *
     * <p>★ 每次<b>新建一份 HashMap</b>（不缓存）：LogiTech 坑 #3 说的就是这件事 ——
     * 它的 {@code createHologram} 第 4 参是 {@code HashMap}，而
     * {@code SolarReactorCore#getIdMappingDisplayUse()} 返回的是
     * {@code Map.copyOf(...)}（一个不可变 Map），喂进去要自己
     * {@code new HashMap<>(map)}。我们这里直接把"能被改的 HashMap"作为
     * {@link MultiBlockProjection#render} 的签名，调用方永远不必想这件事。
     *
     * <p>物品统一走 {@link MultiBlockProjection#glow}（LogiTech 的 {@code addGlow}）：
     * 投影是远处看的，带光晕的图标在暗处也能看清轮廓。
     */
    public static java.util.HashMap<String, ItemStack> displayMapping() {
        return MultiBlockProjection.mapOf(
                AddItems.REACTOR_FRAME.getItemId(), MultiBlockProjection.glow(AddItems.REACTOR_FRAME),
                AddItems.REACTOR_SHIELD.getItemId(), MultiBlockProjection.glow(AddItems.REACTOR_SHIELD),
                AddItems.REACTOR_STABILIZER.getItemId(),
                MultiBlockProjection.glow(AddItems.REACTOR_STABILIZER),
                AddItems.REACTOR_BASE.getItemId(), MultiBlockProjection.glow(AddItems.REACTOR_BASE),
                AddItems.REACTOR_INPUT_PORT.getItemId(),
                MultiBlockProjection.glow(AddItems.REACTOR_INPUT_PORT),
                AddItems.REACTOR_OUTPUT_PORT.getItemId(),
                MultiBlockProjection.glow(AddItems.REACTOR_OUTPUT_PORT));
    }

    /** H：投影开关图标。 */
    private ItemStack projectionIcon(boolean on) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(on ? "&a● 已开启" : "&c○ 已关闭");
        lore.add("&7把整座多方块结构以 &f幻影方块&7 的形式投影出来");
        lore.add("&8（逐格 ItemDisplay 实体，缩放 0.5，可穿墙看见）");
        lore.add("&7每格显示 &f这里该放什么&7，一眼看出缺哪块");
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
     * Hr：投影旋转图标 —— 文案在 {@link MultiBlockProjection#rotationIcon} 里
     * （两份核心共用一套，免得两处的"当前朝向/开关状态/点击做什么"三件事各写各的、慢慢漂移）。
     *
     * <p>★ 这里只负责把"本机的结构"传进去：旋转能不能转、当前朝向是什么，
     * 都由那份通用实现按<b>结构自己</b>的对称性与方块数据算出来。
     *
     * @param loc 核心位置；{@code null} = 构造期（图标会自动降级成"定位失败"，不抛异常）
     */
    private ItemStack projectionRotateIcon(Location loc) {
        return MultiBlockProjection.rotationIcon(loc, ReactorManager.structure());
    }

    /**
     * 构建模式槽点击：在手动 / 自动之间切换并刷新。
     *
     * <p>切到 {@link BuildMode#AUTO} 时立即尝试一次激活 —— 否则玩家要等最多
     * {@link ReactorManager#AUTO_BUILD_TICKS} 个 tick 才会看到"未激活 → 空闲中"，
     * 会以为没生效而反复点。
     */
    private void handleBuildModeClick(Player p, int slot, InventoryClickEvent event) {
        if (slot != BUILD_MODE_SLOT) {
            return;
        }
        Location loc = locateMachine(p, event);
        if (loc == null) {
            return;
        }
        BuildMode mode = ReactorManager.toggleBuildMode(loc);
        String extra = "";
        if (mode.isAuto()) {
            // 已经完整就直接激活；不完整就没必要跑一遍（activate 会做一次全量检测，代价不小）
            if (ReactorManager.isStructureComplete(loc)) {
                ReactorManager.Activation act = ReactorManager.activate(loc);
                extra = act.success() ? "&7，结构完整已自动激活" : "&7，但 " + act.message();
            } else {
                extra = "&7，等结构搭好后会自动激活";
            }
        }
        // 构建模式是"设置"，图标自己会变；只有"顺手把结构激活了"才值得推一条
        // （`extra` 里带"已自动激活"的那种属于重要事件）
        if (extra.contains("已自动激活")) {
            Notify.important(p, "&7构建模式：" + mode.display() + extra);
        } else {
            Notify.info(p, "&7构建模式：" + mode.display() + extra);
        }
        refreshGui(loc, StorageCacheUtils.getMenu(loc));
    }

    /** B：构建模式图标。 */
    private ItemStack buildModeIcon(BuildMode mode) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        for (String line : mode.lore()) {
            lore.add(line);
        }
        lore.add("");
        lore.add("&7结构检测：");
        lore.add("&8· 由【放置 / 破坏 / 爆炸】事件触发");
        lore.add("&8· 同一 tick 的多次变动合并为一轮，不再按 tick 轮询");
        lore.add("");
        lore.add("&e点击切换构建模式");
        return named(new ItemStack(mode.isAuto() ? Material.LIME_STAINED_GLASS_PANE
                        : Material.YELLOW_STAINED_GLASS_PANE),
                mode.display(), lore);
    }

    /**
     * 信息槽（J）点击 = <b>手动激活</b>。
     *
     * <p>这就是 spec 要求的那个入口：结构由"不完整"变成"完整"之后，反应堆不会自己进入空闲，
     * 必须玩家点一下信息槽，才会做一次结构检测、把它从未激活转成空闲中。
     *
     * <p>注意与<b>每次进程前的自动结构检测</b>的分工：
     * <ul>
     *   <li>本方法只负责"激活"这一步（人触发）；</li>
     *   <li>{@code tickReactor} 里每 tick 仍然照旧检测结构完整性，结构一旦坏掉就转未激活并中断进程。</li>
     * </ul>
     *
     * <p>{@link ReactorManager#activate(Location)} 自己就会先跑一次结构检测，
     * 所以这里不需要（也不应该）重复检测。
     */
    private void handleInfoClick(Player p, InventoryClickEvent event) {
        Location loc = locateMachine(p, event);
        if (loc == null) {
            // 拿不到位置只可能是玩家没看着机器且菜单来源也不可解析；给提示而不是静默失败
            Notify.warn(p, "&c无法定位反应堆，请关掉界面后对着反应堆右键重新打开");
            return;
        }
        ReactorManager.Activation act = ReactorManager.activate(loc);
        if (act.success()) {
            Notify.important(p, act.message());
        } else {
            Notify.warn(p, act.message());
        }
        if (act.success()) {
            // ★ spec：手动点激活按钮且激活成功后，播放原版 entity.zombie_villager.cure。
            //   只放给点击者（Player#playSound(Location,...) 是单人音效、位置在机器上），
            //   不打扰周围玩家；想变成"附近都能听见"就换成 world.playSound(loc, ...)。
            p.playSound(loc, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0F, 1.0F);
        }
        refreshGui(loc, StorageCacheUtils.getMenu(loc));
    }

    /**
     * 定位"当前这个菜单属于哪个反应堆"。
     *
     * <p>★ 为什么不能只用射线：GUI 一旦打开，玩家准星可能已经不在方块上（界面挡住了视线），
     * 靠 {@code getTargetBlockExact} 去猜方块是不可靠的 —— 而且随着界面种类变多，
     * 点错格子的后果不是"没反应"而是"操作了另一台机器"。
     *
     * <p>所以按可靠性从高到低试三条路：
     * <ol>
     *   <li>{@link InventoryClickEvent} 带来的 {@code Inventory} 的 <b>holder</b>：
     *       Slimefun 的 {@code BlockMenu} 实现了 {@code InventoryHolder} 且带
     *       {@code getLocation()} / {@code getBlock()}。用反射取，拿不到就换下一条
     *       （不同 fork 版本 holder 的实现方式不完全一样，反射不会因为接口差异而炸）；</li>
     *   <li>然后才退回 {@link #menuLocation(Player)} 的射线方案；</li>
     *   <li>都失败返回 {@code null}，由调用方给出提示。</li>
     * </ol>
     */
    private Location locateMachine(Player p, InventoryClickEvent event) {
        if (event != null) {
            try {
                Inventory inv = event.getInventory();
                InventoryHolder holder = inv == null ? null : inv.getHolder();
                if (holder != null) {
                    Location fromHolder = locationOf(holder);
                    if (fromHolder != null && StorageCacheUtils.getBlock(fromHolder) != null) {
                        return fromHolder;
                    }
                }
            } catch (RuntimeException ignored) {
                // 掉到射线方案
            }
        }
        return menuLocation(p);
    }

    /** 从 InventoryHolder 里抠出方块位置（兼容几种可能的暴露方式）。 */
    private static Location locationOf(Object holder) {
        Object v = invokeAny(holder, "getLocation");
        if (v instanceof Location l) {
            return l;
        }
        v = invokeAny(holder, "getBlock");
        if (v instanceof Block b) {
            return b.getLocation();
        }
        v = invokeAny(holder, "getBlockMenu");
        if (v instanceof BlockMenu m) {
            return m.getLocation();
        }
        return null;
    }

    /** 尝试无参方法；不存在或调用失败返回 null。 */
    private static Object invokeAny(Object target, String method) {
        try {
            java.lang.reflect.Method m = target.getClass().getMethod(method);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * 点击信息槽的核心逻辑（与 {@link #handleInfoClick} 共用）。
     *
     * <p>抽出来是为了让控制台也能走一遍同一条链路：
     * {@code /touhou clickinfo <x> <y> <z>} 会调本方法，从而在无头环境下验证
     * "点一下信息格 → 结构检测 → 激活"。
     *
     * @param feedback 反馈文本的出口（游戏里是玩家聊天栏，控制台是日志）
     * @return 未经上色的反馈文本（带 {@code &} 代码），便于调用方判定结果
     */
    public String simulateInfoClick(Location loc, java.util.function.Consumer<String> feedback) {
        ReactorManager.Activation act = ReactorManager.activate(loc);
        feedback.accept(ReactorManager.color(AddonConfig.get().messagePrefix + act.message()));
        refreshGui(loc, StorageCacheUtils.getMenu(loc));
        // 激活音效只在"真人点击 GUI"那条路里放（见 handleInfoClick）——
        // 控制台没有玩家可听，所以这里刻意不放。
        return act.message();
    }

    /**
     * 从玩家当前打开的菜单反查方块位置。
     *
     * <p>{@code ChestMenu} 只给了 {@code Inventory}，拿不到 Location；这里退一步：
     * 用"玩家看向的方块"来定位。信息槽 / 模式槽在 GUI 里各只有一个，点它时玩家必然站在机器前，
     * 且菜单只可能由"右键机器"打开，所以这个近似是安全的。
     * 更可靠的路径见 {@link #locateMachine(Player, InventoryClickEvent)}。
     */
    private Location menuLocation(Player p) {
        Block target = p.getTargetBlockExact(6);
        if (target == null || StorageCacheUtils.getBlock(target.getLocation()) == null) {
            return null;
        }
        return target.getLocation();
    }

    // ---------------------------------------------------------------- tick / 进程

    /** 主线程 tick：状态机 → 结构检测 → 进程启停 → GUI。 */
    private void tickReactor(Block b) {
        Location loc = b.getLocation();
        BlockMenu inv = StorageCacheUtils.getMenu(loc);
        if (inv == null || !TouhouData.isReady(loc)) {
            return;                     // 数据还没加载好，下一 tick 再来
        }

        try {
            tickOnce(b, loc, inv);
        } catch (RuntimeException e) {
            // 不能让异常把 ticker 打死（本体对 pod ticker 的异常处理是移除方块）
            Touhou.getInstance().getLogger().warning("[Reactor] tick 异常 @ "
                    + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + ": " + e);
        }
    }

    private void tickOnce(Block b, Location loc, BlockMenu inv) {
        FuelOperation op = getMachineProcessor().getOperation(b);
        boolean hasOperation = op != null && !op.isFinished();

        // ★ 结构结论只在状态机里产生一次（节流由 ReactorManager.tickGate 负责）：
        //   上一版这里先 isStructureComplete(loc) 扫一遍、updateState 里再扫一遍，
        //   同一 tick 两次全量扫描，而且两次结果可能不一致（tick 头 / tick 尾）。
        //   现在改成"先算状态，再按状态决定中止/推进" —— 一次扫描，一份结论。
        ReactorState state = ReactorManager.updateState(loc, hasOperation);
        boolean activated = state != ReactorState.INACTIVE;
        boolean structureComplete = ReactorManager.isStructureValid(loc);

        if (Boolean.getBoolean("touhou.debugReactor")) {
            Log.info("[MBREACTOR-TICK] @"
                    + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                    + " struct=" + structureComplete
                    + " state=" + state
                    + " activated=" + ReactorManager.isActivated(loc)
                    + " op=" + (op == null ? "null" : op.getProgress() + "/" + op.getTotalTicks())
                    + " fuel=" + ReactorManager.fuelName(inv.getItemInSlot(INPUT_SLOTS[0])));
        }

        // 结构坏了 → 中断进程（燃料已消耗，代价真实）
        if (!structureComplete && hasOperation && ReactorManager.shouldAbortProcess(loc)) {
            getMachineProcessor().endOperation(b);
            hasOperation = false;
            ReactorManager.warnBrokenOnce(loc);
        }

        // ★ 附加粒子特效：只有"空闲中 / 运行中"才生成，未激活时完全不生成（spec 要求）
        ReactorParticles.tick(loc, activated);

        if (hasOperation) {
            // ★ 产物模式：由这里自己推进度，**不依赖电力网络**。
            //   参照本体 Reactor 的 ReactorMode.PRODUCTION：
            //   generateEnergy 里只有"电装不下 && 发电模式"才 return 0（不推进度 = 暂停），
            //   产物模式一律 addProgress(1) —— 进程跟"电有没有去处"无关。
            //   注意 getGeneratedOutput 在产物模式下【不再】推进度，所以不会双倍。
            //   （发电模式的进度由本体 EnergyNet 那条路推进，这里什么都不做。）
            tickProductProcess(loc, structureComplete);
        } else {
            if (state == ReactorState.IDLE) {
                tryStartProcess(inv, b);
            }
            refreshGui(loc, inv);
        }
    }

    /**
     * 中止指定位置的核心进程并立刻重试开进程（内部实现）。
     *
     * <p>语义要与"结构被拆导致的中断"区分开：
     * <ul>
     *   <li>中止<b>不退还</b>燃料（已烧掉的就是烧掉了，与 {@code abortProcessOnBroken} 的代价一致）；</li>
     *   <li>中止后<b>立刻</b>尝试开新进程，不等下一 tick —— 玩家按下去就该看到结果；</li>
     *   <li>能不能开新进程仍由 {@link #tryStartProcess} 把关（结构 / 激活 / 电网 / 电量 / 燃料 / 输出空间），
     *       所以中止不是"绕过条件"，只是"提前重试"。</li>
     * </ul>
     *
     * <p>⚠ 主线程调用（会读写世界与方块数据）。
     *
     * @return 本次是否真的中止了某个进程（false = 本来就没有在跑的进程）
     */
    private boolean abortAt(Location loc) {
        if (loc == null) {
            return false;
        }
        Block b = loc.getBlock();
        boolean had = getMachineProcessor().getOperation(b) != null;
        if (had) {
            // 直接结束，不走 completeOperation —— 中止不产出任何东西（燃料已消耗）
            getMachineProcessor().endOperation(b);
        }
        // 立刻重试开进程（条件仍由 tryStartProcess 把关）
        BlockMenu inv = StorageCacheUtils.getMenu(loc);
        if (inv != null && ReactorManager.cachedState(loc) != ReactorState.INACTIVE) {
            tryStartProcess(inv, b);
        }
        refreshGui(loc, StorageCacheUtils.getMenu(loc));
        return had;
    }

    /**
     * 供 IO 接口调用：中止<b>指定核心位置</b>的进程并开启下一轮。
     *
     * <p>做成静态入口是因为调用方是另一个方块（IO 接口），它只有核心的 Location，
     * 没有核心的物品实例 —— 而 {@code BlockStorage.check(loc)} 就能拿到实例。
     *
     * @return true = 真的中止了一个在跑的进程
     */
    public static boolean abortProcessAt(Location core) {
        if (core == null) {
            return false;
        }
        var item = me.mrCookieSlime.Slimefun.api.BlockStorage.check(core);
        if (!(item instanceof UtsuhoReactorCore reactor)) {
            return false;
        }
        return reactor.abortAt(core);
    }

    /**
     * 开一个新进程（消耗 1 个燃料）。
     *
     * <p>★★ 这里是"机器只在所有条件满足时才运行"的**唯一把关点**。
     * 之前的版本只在 {@code getGeneratedOutput} 里做了模式/电量门控，
     * 而那个方法只在方块接入电力网络时才会被调用 ——
     * 于是"没接电"的机器会无限开进程、白烧燃料（用户实测：结构不完整时仍在消耗输入槽）。
     * 现在把<b>模式 + 电量 + 结构</b>三个条件全部前置到这里，与 spec 的
     * "机器应该只在满足模式选择、电量容量条件和结构完整所有条件下才能运行"一致。
     */
    private boolean tryStartProcess(BlockMenu inv, Block b) {
        Location loc = b.getLocation();

        // ---- 条件 1：多方块结构完整 ----
        // 每次开进程前都重新检测（不是复用 tick 开头那次的结果：两次之间世界可能变了）
        ReactorManager.ReactorGate gate = ReactorManager.startGate(loc);
        if (!gate.allowed()) {
            if (Boolean.getBoolean("touhou.debugReactor")) {
                Log.info("[MBREACTOR-GATE] 拒绝开进程 @ "
                        + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                        + " 原因=" + gate.reason());
            }
            return false;
        }

        int slot = fuelSlotOf(inv);
        if (slot < 0) {
            return false;               // 没有燃料，保持空闲
        }
        if (!hasOutputRoom(inv)) {
            return false;               // 输出满了，先别烧
        }
        ItemStack fuel = inv.getItemInSlot(slot);
        // ★ 顺序很重要：先 clone 再 consume。
        //   `BlockMenu#consumeItem(slot, 1)` 在"只剩 1 个"时会把那个 ItemStack 就地清空
        //   （setAmount(0)），于是同一个对象立刻变成 AIR —— 如果先 consume 再拿引用，
        //   存进 FuelOperation 的 ingredient 就变成 AIR/空，
        //   收尾时 isBucket(ingredient) 判定为 false，空桶永远不会吐出来（实测踩过）。
        ItemStack fuelSnapshot = fuel.clone();
        inv.consumeItem(slot, 1);

        // ★ ingredient 必须传"燃料本身"：本体 AGenerator 用 isBucket(ingredient) 判断
        //   要不要吐一个空桶；我们把这份判断照抄到 completeOperation 里
        //   （因为父类那段逻辑在我们的重写下不会执行）。
        getMachineProcessor().startOperation(b,
                new FuelOperation(fuelSnapshot, null, ReactorManager.config().processTicks));
        TouhouData.addLong(loc, TouhouData.KEY_TOTAL_FUEL, 1L);

        if (Boolean.getBoolean("touhou.debugReactor")) {
            Log.info("[MBREACTOR-GATE] 已开进程 @ "
                    + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                    + " 消耗=" + ReactorManager.fuelName(fuelSnapshot)
                    + " 模式=" + ReactorManager.getMode(loc)
                    + " 储电=" + ReactorManager.currentCharge(loc));
        }
        return true;
    }

    /**
     * <b>只给命令/测试用</b>：无燃料则先塞 1 个原油桶，然后走一次真实的开进程流程。
     *
     * <p>用来在控制台验证"条件不满足时不消耗燃料"——返回值表示<b>这次调用有没有真的吃掉 1 个燃料</b>。
     *
     * <p>★ 它<b>会篡改输入槽</b>（塞桶）。所以生产路径（比如 IO 接口的中止按钮）
     * 绝不能用它，只能用 {@link #tryStartProcess} —— 否则"中止并开启下一轮"
     * 会凭空变出燃料来烧。这两条路径必须分开。
     */
    public boolean simulateStart(Location loc) {
        Block b = loc.getBlock();
        BlockMenu inv = StorageCacheUtils.getMenu(loc);
        if (inv == null) {
            return false;
        }
        if (fuelSlotOf(inv) < 0) {
            inv.replaceExistingItem(getInputSlots()[0], SlimefunItems.OIL_BUCKET.clone());
        }
        // 已有在跑的进程就不重复开
        if (getMachineProcessor().getOperation(b) != null) {
            return false;
        }
        return tryStartProcess(inv, b);
    }

    /** 进程结束后补上本机器特有的产物（空桶由本体 AGenerator 负责吐）。 */
    private void pushExtraOutputs(BlockMenu inv) {
        inv.pushItem(AddItems.BLAZING_ASH.clone(), getOutputSlots());
    }

    private int fuelSlotOf(BlockMenu inv) {
        for (int slot : INPUT_SLOTS) {
            if (ReactorManager.isFuel(inv.getItemInSlot(slot))) {
                return slot;
            }
        }
        return -1;
    }

    /** 输出侧是否还有空位（避免烧了燃料却吐不出来）。 */
    private boolean hasOutputRoom(BlockMenu inv) {
        int free = 0;
        for (int slot : OUTPUT) {
            ItemStack item = inv.getItemInSlot(slot);
            if (item == null || item.getType().isAir()) {
                free++;
            }
        }
        return free >= 2;               // 一次产出桶 + 奇点，两个槽
    }

    // ---------------------------------------------------------------- 发电（暂停机制）

    /**
     * 发电入口 —— 暂停机制唯一实现点。
     *
     * <p>本方法由本体电力网络每 tick 调用（主线程）。它要做四件事：
     * <ol>
     *   <li>没有进程 → 复位进度槽，返回 0（开进程由主线程 tick 负责，这里不重复做）；</li>
     *   <li>进程未结束 → 问 {@link ReactorManager#generate} 该发多少电、
     *       要不要推进度（发电模式到阈值就返回 0 且不推进 = 暂停）；</li>
     *   <li>推进<b>之后</b>画一次进度条 —— 这样满进度那一帧（100%）玩家真的看得到，
     *       而不是停在 total−1 的百分比上；</li>
     *   <li>进程已完成 → 吐空桶 + 炙热的灰烬，然后结束进程。</li>
     * </ol>
     *
     * <h2>★★ 参数类型必须是 {@code ASlimefunDataContainer}（血泪教训）</h2>
     * 第一版写的是 {@code SlimefunBlockData}，因为编译依赖 Slimefun4-2025.1 里
     * {@code AGenerator} 的签名就是它，还能正常加 {@code @Override}。
     * 但运行时 2026.07 把签名换成了父类型 {@code ASlimefunDataContainer}：
     *
     * <pre>
     * 2025.1  AGenerator#getGeneratedOutput(Location, SlimefunBlockData)
     * 2026.07 AGenerator#getGeneratedOutput(Location, ASlimefunDataContainer)
     * </pre>
     *
     * 而 {@code EnergyNet} 的调用链是（全是 2026.07 的 default 方法）：
     * <pre>
     * getGeneratedOutputLong(Location, ASlimefunDataContainer)
     *   → getGeneratedOutputLong(Location, SlimefunBlockData)
     *     → getGeneratedOutput(Location, <b>ASlimefunDataContainer</b>)   ← 静态类型是父类型
     * </pre>
     * 于是虚分派只会找到 {@code AGenerator} 自己的实现 ——
     * <b>我们那个 {@code SlimefunBlockData} 版本成了永远不会被调用的死代码</b>
     * （重载不是重写！）。
     *
     * <p>表现就是：模式/阈值完全不生效、进度条由本体画、到 total−1 那一帧就不再动，
     * 而机器"看起来还能用"（状态机、结构检测、开进程都在我们的 BlockTicker 里）。
     *
     * <p>修法：把真正的实现挂在 {@code ASlimefunDataContainer} 这个签名上
     * （它在 2025.1 的 jar 里也存在，所以两边都能编译），
     * 另外保留一个 {@code SlimefunBlockData} 版本转调过来，方便内部与命令直接调用。
     * 注意 {@code ASlimefunDataContainer} 版本<b>不能</b>写 {@code @Override} ——
     * 2025.1 里没有任何父类型声明过这个签名，写了直接编译不过。
     */
    @Override
    public int getGeneratedOutput(Location l, SlimefunBlockData data) {
        return getGeneratedOutput(l, (ASlimefunDataContainer) data);
    }

    /** 运行时真正会被本体电力网络调用的重载（见上面 ★★ 说明）。 */
    public int getGeneratedOutput(Location l, ASlimefunDataContainer data) {
        BlockMenu inv = StorageCacheUtils.getMenu(l);
        FuelOperation op = getMachineProcessor().getOperation(l);

        if (op == null) {
            // 空闲：把进度槽恢复成布局占位符，别留下"卡在 xx%"的残留进度条
            resetProgressSlot(inv);
            refreshGui(l, inv);
            return 0;
        }
        if (op.isFinished()) {
            // 上一 tick 已经跑完（例如由 BlockTicker 推进到满），这里补收尾
            refreshGui(l, inv);
            completeOperation(l, op);
            return 0;
        }

        boolean structureComplete = ReactorManager.isStructureComplete(l);
        long amount = ReactorManager.generate(l, progressOf(op), structureComplete);

        // ★ 推进之后再刷新：进度条能画到 100%，J/K 两个槽位的数据也是最新的
        refreshGui(l, inv);

        if (op.isFinished()) {
            completeOperation(l, op);       // 吐桶 + 奇点 + endOperation
        }
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, amount));
    }

    /**
     * <b>一个 tick 里机器自己该做的事</b>：推进产物模式的进程 + 跑完就收尾出产物。
     *
     * <p>为什么单独抽出来：这一份逻辑有两个调用者 ——
     * 机器的 {@code BlockTicker}（真实运行）和 {@code /touhou reactor test} 诊断命令
     * （无头复现"机器跑 N tick 会发生什么"）。抽出来两边共用，
     * 诊断命令才不会和真实行为漂移。
     *
     * <p>发电模式在这里什么都不做：它的进度由本体 EnergyNet 调
     * {@code getGeneratedOutput} 推进（到阈值就暂停）。
     *
     * @return 本 tick 是否推进了进程（或完成了进程）
     */
    public boolean tickProductProcess(Location loc, boolean structureComplete) {
        Block b = loc.getBlock();
        FuelOperation op = getMachineProcessor().getOperation(b);
        boolean advanced = ReactorManager.advanceProductProcess(loc, progressOf(op), structureComplete);
        if (advanced) {
            // 推进之后再刷新 GUI：进度条这一帧就是新的进度（可能正好 100%）
            refreshGui(loc, StorageCacheUtils.getMenu(loc));
        }
        FuelOperation finished = getMachineProcessor().getOperation(b);
        if (finished != null && finished.isFinished()) {
            completeOperation(loc, finished);
            return true;
        }
        return advanced;
    }

    /** 便利重载：自己重新检测结构。 */
    public boolean tickProductProcess(Location loc) {
        return tickProductProcess(loc, ReactorManager.isStructureComplete(loc));
    }

    /**
     * 诊断：本类的 {@code getGeneratedOutput} 各重载，<b>实际由哪个类实现</b>。
     *
     * <p>存在理由见 {@link #getGeneratedOutput(Location, ASlimefunDataContainer)} 的 ★★ 说明：
     * 编译依赖（2025.1）与运行时（2026.07）的方法签名不一样时，
     * 重写很容易变成"同名的另一个重载"→ 我们的代码成为永不执行的死代码，
     * 而机器表面上还能用，极难发现。这里直接把虚分派的结果打出来：
     * 只要 {@code ASlimefunDataContainer} 那一行显示的不是 {@code UtsuhoReactorCore}，
     * 就说明重写没生效。
     */
    public static List<String> describeDispatches() {
        List<String> out = new ArrayList<>();
        for (java.lang.reflect.Method m : UtsuhoReactorCore.class.getMethods()) {
            if (!m.getName().startsWith("getGeneratedOutput")) {
                continue;
            }
            Class<?>[] params = m.getParameterTypes();
            String arg = params.length > 1 ? params[1].getSimpleName() : "?";
            boolean ours = "UtsuhoReactorCore".equals(m.getDeclaringClass().getSimpleName());
            // 只有 int 版 + ASlimefunDataContainer 那个签名是"必须由我们实现"的
            // （EnergyNet 实际就是调它）。getGeneratedOutputLong / Config 版本
            // 是本体 default，会自己转调下来，显示"本体的"完全正常。
            boolean critical = "ASlimefunDataContainer".equals(arg)
                    && m.getName().equals("getGeneratedOutput");
            String mark;
            if (critical) {
                mark = ours ? "  ✔ 我们的（电力网络走这条，正常）"
                            : "  ✘ 本体的（★ 重写没生效！模式/阈值会全部失效）";
            } else {
                mark = ours ? "  ✔ 我们的" : "  · 本体 default（会转调到上面的 int 版）";
            }
            out.add("  " + m.getName() + "(Location, " + arg + ") ← 实现: "
                    + m.getDeclaringClass().getSimpleName() + mark);
        }
        return out;
    }

    /**
     * 把一个 {@link FuelOperation} 包成状态机认的 {@link ReactorManager.FuelProgress}。
     *
     * <p>两条推进路径（本体 EnergyNet 的 {@code getGeneratedOutput} 与机器的
     * {@code BlockTicker}）共用这一份包装，免得两处各写一遍匿名类、
     * 以后改口径时漏掉一处。{@code op} 为 null 时返回一个"没有进程"的空实现。
     */
    private static ReactorManager.FuelProgress progressOf(FuelOperation op) {
        if (op == null) {
            return ReactorManager.NO_PROGRESS;
        }
        return new ReactorManager.FuelProgress() {
            @Override
            public boolean hasOperation() {
                return !op.isFinished();
            }

            @Override
            public int progress() {
                return op.getProgress();
            }

            @Override
            public int totalTicks() {
                return op.getTotalTicks();
            }

            @Override
            public void advance(int ticks) {
                op.addProgress(Math.max(1, ticks));
            }
        };
    }

    /**
     * 收尾：吐空桶 + 炙热的灰烬，然后结束进程。
     *
     * <p>★ 这里有一个必须自己做的理由：本体 {@code AGenerator#getGeneratedOutput} 的
     * "进程已完成"分支里会 {@code pushItem(new ItemStack(Material.BUCKET))} 并
     * {@code endOperation(l)}，但那是它<b>私有逻辑</b>的一部分 ——
     * 我们重写了自己的 {@code getGeneratedOutput}，父类那段就<b>永远不会执行</b>。
     * 第一版就是想"交给父类吐桶"，结果：桶没吐出来，operation 也一直不结束，
     * 于是每一 tick 都再走一次 finish 分支 → <b>凭空刷出多个奇点</b>。
     *
     * <p>用"记住已收尾的那个 operation 实例"来保证幂等：
     * 同一个 operation 只会被收尾一次，结束后 {@code getOperation} 返回 null 自然不再进入。
     */
    private void completeOperation(Location loc, FuelOperation op) {
        // 幂等保护：主线程 tick 与电力网络 tick 都可能看到"已完成"，
        // 谁先到谁收尾，后到的必须直接跳过 —— 否则空桶与奇点会被推两次。
        // 用本地类名 + 身份哈希作为一次性的 key 写回方块数据，收尾后立即删除。
        String guardKey = "touhou:finishing";
        String token = System.identityHashCode(op) + "@" + op.getTotalTicks();
        String existing = TouhouData.getString(loc, guardKey, null);
        if (token.equals(existing)) {
            return;
        }
        TouhouData.setString(loc, guardKey, token);

        BlockMenu inv = StorageCacheUtils.getMenu(loc);
        if (inv != null) {
            boolean bucket = isBucketItem(op.getIngredient());
            if (bucket) {
                inv.pushItem(new ItemStack(Material.BUCKET), getOutputSlots());
            }
            inv.pushItem(AddItems.BLAZING_ASH.clone(), getOutputSlots());
            if (Boolean.getBoolean("touhou.debugReactor")) {
                Log.info("[MBREACTOR-FINISH] @"
                        + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ()
                        + " ingredient=" + (op.getIngredient() == null
                                ? "null"
                                : String.valueOf(op.getIngredient().getType()))
                        + " isBucket=" + bucket);
            }
        }
        // endOperation 是幂等的：进度 Map 里没有这个 key 时什么也不做
        getMachineProcessor().endOperation(loc.getBlock());
        TouhouData.setString(loc, guardKey, "");

        // ★ spec：每次进程结束<b>立即</b>在核心周围球体内爆一簇火焰粒子
        ReactorParticles.burstOnComplete(loc);
        // 计数器归零，下一轮从 0 开始算间隔
        ReactorParticles.forget(loc);
    }

    /** 判定"这份燃料是不是桶类"，与本体 {@code AGenerator#isBucket} 同判据。 */
    private static boolean isBucketItem(ItemStack item) {
        if (item == null) {
            return false;
        }
        return item.getType() == Material.LAVA_BUCKET
                || SlimefunUtils.isItemSimilar(item, SlimefunItems.FUEL_BUCKET, true)
                || SlimefunUtils.isItemSimilar(item, SlimefunItems.OIL_BUCKET, true);
    }

    @Override
    public boolean willExplode(Location l, SlimefunBlockData data) {
        return false;
    }

    // ---------------------------------------------------------------- GUI 刷新

    /**
     * 刷新自研槽位（J 信息 / K 模式），只在有人看着时写。
     *
     * <p>★ 进度显示不再由我们负责：本体 {@code AGenerator} 会把原生进度条
     * （烈焰粉 + 原生进度 lore）写在槽 {@link #VANILLA_PROGRESS_SLOT}。
     * 输出区已经避开那个格子，所以两者互不干扰。
     */
    private void refreshGui(Location loc, BlockMenu inv) {
        if (inv == null || !inv.hasViewer()) {
            return;
        }
        ReactorState state = ReactorManager.cachedState(loc);
        ReactorMode mode = ReactorManager.getMode(loc);

        FuelOperation op = getMachineProcessor().getOperation(loc.getBlock());
        inv.replaceExistingItem(INFO_SLOT, infoIcon(loc, state, mode, op));
        inv.replaceExistingItem(MODE_SLOT, modeIcon(mode));
        inv.replaceExistingItem(BUILD_MODE_SLOT, buildModeIcon(ReactorManager.getBuildMode(loc)));
        inv.replaceExistingItem(PARTICLE_SLOT, particlesIcon(ReactorManager.isParticlesOn(loc)));

        // ★ 投影开关：只在状态变化时重建图标（这个槽每 tick 都刷，但状态几乎不变）
        boolean holoOn = MultiBlockProjection.isOn(loc);
        if (lastHologramIconState == null || lastHologramIconState != holoOn) {
            lastHologramIconState = holoOn;
            inv.replaceExistingItem(HOLOGRAM_SLOT, projectionIcon(holoOn));
        }

        // ★ 旋转按钮的图标取决于"朝向 + 开关"两件事（两者都几乎不变），
        //   所以同样只在变化时重建 —— 用组合串而不是布尔，免得漏掉"只转了朝向"这一种变化。
        String rotateKey = ReactorManager.structure().isSymmetric()
                ? "symmetric/" + holoOn
                : MultiBlockProjection.storedDirection(loc) + "/" + holoOn;
        if (!rotateKey.equals(lastRotateIconKey)) {
            lastRotateIconKey = rotateKey;
            inv.replaceExistingItem(HOLOGRAM_ROTATE_SLOT, projectionRotateIcon(loc));
        }

        // ★ 进度条也归我们管。本体那段"画进度条 / 收尾把槽清成黑色玻璃板"写在
        //   它自己的 getGeneratedOutput 里 —— 我们重写了那个方法（而且要重写对签名，
        //   见上面的 ★★），所以槽 22 必须自己维护，否则会留下"卡在 xx%"的残留。
        if (op != null) {
            getMachineProcessor().updateProgressBar(inv, VANILLA_PROGRESS_SLOT, op);
        } else {
            resetProgressSlot(inv);
        }
    }

    /** 空闲时把原生进度槽恢复成布局里的输出占位符。 */
    private static void resetProgressSlot(BlockMenu inv) {
        if (inv != null) {
            inv.replaceExistingItem(VANILLA_PROGRESS_SLOT, ChestMenuUtils.getOutputSlotTexture());
        }
    }

    /** 槽 12：附加粒子特效开关的图标。 */
    private ItemStack particlesIcon(boolean on) {
        AddonConfig cfg = ReactorManager.config();
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add(on ? "&a● 已开启" : "&c○ 已关闭");
        lore.add("&7运行中会以核心为中心、半径 &f" + cfg.particleInnerRadius
                + "~" + cfg.particleOuterRadius + " &7格的球壳内");
        lore.add("&7随机生成火焰粒子（每 &f" + cfg.particleIntervalTicks + " &7tick &f"
                + cfg.particleAmount + " &7颗）");
        lore.add("&7每次进程结束还会爆发 &f" + cfg.particleCompletionBurst + " &7颗");
        lore.add("");
        lore.add("&e点击切换开关");
        return named(new ItemStack(on ? Material.BLAZE_POWDER : Material.GUNPOWDER),
                on ? "&6附加粒子特效" : "&7附加粒子特效", lore);
    }

    /** J：信息显示。 */
    private ItemStack infoIcon(Location loc, ReactorState state, ReactorMode mode, FuelOperation op) {
        long charge = ReactorManager.currentCharge(loc);
        long capacity = ReactorManager.config().energyCapacity;
        long production = ReactorManager.effectiveProduction(loc);
        int workSpeed = ReactorManager.effectiveWorkSpeed(loc);

        int processTicks = ReactorManager.config().processTicks;
        long currentRemaining = (op == null || op.isFinished())
                ? 0
                : Math.max(0, op.getTotalTicks() - op.getProgress());

        BlockMenu inv = StorageCacheUtils.getMenu(loc);
        List<String> lore = new ArrayList<>();

        // ---- 状态放在最上面；未激活时把这里做成"可以点"的醒目提示 ----
        lore.add("&7运行状态： " + state.display());
        BuildMode buildMode = ReactorManager.getBuildMode(loc);
        if (state == ReactorState.INACTIVE) {
            // ★ 提示必须跟着构建模式走：AUTO 模式下"点击激活"是多余的指令，
            //   玩家照着做也没错，但会误以为机器需要手动干预（体验上就是"没生效"）。
            if (buildMode.isAuto()) {
                lore.add("&e&l➤ 自动构建已开启，结构搭好后会自动激活");
                lore.add("&8（保底仍可点击本格立即激活）");
            } else {
                lore.add("&e&l➤ 点击本格进行结构检测并激活");
            }
        } else {
            lore.add("&8（点击本格可重新检测结构）");
        }
        lore.add("&7当前模式： " + mode.display());
        lore.add("&7构建模式： " + buildMode.display());
        lore.add("");
        lore.add("&7本次燃料剩余燃烧时间： &f" + ReactorManager.formatTicks(currentRemaining));
        lore.add("&7当前进程总时长： &f" + ReactorManager.formatTicks(op == null ? 0 : op.getTotalTicks()));
        lore.add("");
        lore.add("&7发电功率： &e" + String.format("%,d", production) + " &7J/tick"
                + (workSpeed > 1 ? "  &8(产物模式：" + (workSpeed * 100) + "% 效率 / 电量 "
                        + Math.round(ReactorManager.config().productModeEnergyRate * 100) + "%)" : ""));
        lore.add("&7电量存储： &e" + String.format("%,d", charge) + " &7/ &e"
                + String.format("%,d", capacity) + " &7J");
        // 发电模式要显示阈值；产物模式说明不受电量限制
        lore.add(mode == ReactorMode.GENERATE
                ? "&7发电模式阈值： &f" + String.format("%,d", ReactorManager.config().modeThreshold) + " &7J"
                : "&8产物模式不受电量限制");

        // ★ 未接入电力网络时：发电模式会"燃料白烧、进度不动"，必须明确告诉玩家；
        //   产物模式则本来就设计成离网自跑，只需要说明"不发电"。
        if (!ReactorManager.isConnectedToEnergyNet(loc)) {
            lore.add("");
            if (mode == ReactorMode.GENERATE) {
                lore.add("&c&l⚠ 未接入电力网络");
                lore.add("&c" + ReactorManager.ENERGY_REGULATOR_RANGE
                        + " 格内需要有 &f能源调节器&c 或 &f电容");
                lore.add("&8（否则本体电力网络不会 tick 这台发电机，");
                lore.add("&8  表现为：燃料在烧但既不发电力、进度也不动）");
                lore.add("&8切到 &d产物模式&8 可以离网自跑（但不发电）");
            } else {
                lore.add("&e&l⚠ 离网运行（产物模式）");
                lore.add("&7" + ReactorManager.ENERGY_REGULATOR_RANGE
                        + " 格内没有能源调节器/电容：");
                lore.add("&8· 进程照常推进、产物照常产出");
                lore.add("&8· 发出来的电没人接收，直接浪费");
            }
        }

        if (inv != null) {
            int fuelItems = 0;
            for (int slot : INPUT_SLOTS) {
                ItemStack item = inv.getItemInSlot(slot);
                if (ReactorManager.isFuel(item)) {
                    fuelItems += item.getAmount();
                }
            }
            lore.add("&7输入槽燃料可运行： &f"
                    + ReactorManager.formatTicks((long) fuelItems * processTicks)
                    + " &8(" + fuelItems + " 桶)");
            lore.add("&7现存燃料可运行： &f" + ReactorManager.formatTicks(currentRemaining));
            lore.add("&7合计可运行： &f"
                    + ReactorManager.formatTicks((long) fuelItems * processTicks + currentRemaining));
        }
        lore.add("");
        lore.add(state == ReactorState.INACTIVE
                ? "&c多方块结构不完整，或结构已修好但尚未激活"
                : "&8结构完整");
        if (state == ReactorState.INACTIVE) {
            ReactorStructure.Result r = ReactorManager.lastResult(loc);
            if (r != null) {
                lore.add("&8" + r.summary());
                int n = 0;
                for (String m : r.missing()) {
                    if (n++ >= 3) {
                        break;
                    }
                    lore.add("&8  缺 " + m);
                }
                for (String w : r.wrong()) {
                    if (n++ >= 3) {
                        break;
                    }
                    lore.add("&8  错 " + w);
                }
            }
            lore.add("&e修好结构后点击本格激活");
        }
        return new CustomItemStack(Material.REDSTONE_TORCH, "&e反应堆状态", lore);
    }

    private ItemStack infoPlaceholder() {
        return new CustomItemStack(Material.REDSTONE_TORCH, "&e反应堆状态",
                List.of("&7等待数据…",
                        "",
                        "&e&l➤ 点击本格进行结构检测并激活"));
    }

    /** K：模式图标（用 INFO 组的 TOUHOU_INFO_MODESHIFT 玻璃板）。 */
    private ItemStack modeIcon(ReactorMode mode) {
        return named(AddItems.INFO_MODESHIFT.clone(), mode.display(),
                List.of(mode.lore()[0], mode.lore()[1],
                        "",
                        "&7发电模式阈值： &f"
                                + String.format("%,d", ReactorManager.config().modeThreshold) + " &7J",
                        "",
                        "&e点击切换模式"));
    }

    /**
     * 自研进度显示已被<b>移除</b>。
     *
     * <p>用户明确要求"直接使用原生的 info，放弃自己写的 info"：
     * 进度与燃烧信息统一由本体 {@code AGenerator} 的原生进度条负责
     * （烈焰粉 + 原生进度条 lore，写在槽 {@link #VANILLA_PROGRESS_SLOT}）。
     *
     * <p>曾经验证过的自研版本（中文进度条 █░ + 剩余时间 + 燃料名）在这里删掉了，
     * 不再保留两套并行的实现 —— 两套会互相覆盖、也让"到底哪套在生效"难判断。
     * 相关槽位（{@link #PROGRESS_SLOT}）现在只用输出占位符外观留白。
     */

    /**
     * 给物品设置显示名与 lore。
     *
     * <p>刻意手写而不用 {@code CustomItemStack} 的便捷构造器：那些构造器只收
     * {@code Material}，会把模式玻璃板的<b>粘液 id</b> 弄丢
     * （spec 要求它必须保持 {@code TOUHOU_PHD_MODESHIFT}）。
     * 直接改 {@code ItemMeta} 能保留原物品的一切。
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

    // ---------------------------------------------------------------- 杂项

    /**
     * 校验用：把 54 格布局渲染成 6×9 的图，并逐格与<b>实测定稿的布局</b>比对。
     *
     * <p>期望图（按用户截图定稿：J 在第 1 行列 3、K 在第 1 行列 5、
     * 输出是第 1~4 行 × 第 6~9 列的 4×4 区域）：
     * <pre>
     *   x  | x  | x  | J  | Ox | K  | x  | x  | x
     *   Ix | I  | x  | P  | Ox | O  | O  | O  | O
     *   Ix | I  | H  | B  | Ox | O  | O  | O  | O
     *   Ix | I  | R  | Ox | Ox | O  | O  | O  | O
     *   Ix | I  | x  | Ox | Ox | O  | O  | O  | O
     *   x  | x  | x  | x  | x  | x  | x  | x  | x
     * </pre>
     *
     * <p>槽位 12（第 2 行列 4）是 {@link #PARTICLE_SLOT}：附加粒子特效开关（spec 说"放在 INFO 上方"，
     * 但 INFO 已在第一行、上方是标题栏，所以放在它正下方第一格）。
     * 槽位 21（第 3 行列 4）是 {@link #BUILD_MODE_SLOT}：构建模式开关，
     * 与 P 一起构成"J/K 两个主按钮各带一个下方开关"。
     * 槽位 20（第 3 行列 3）是 {@link #HOLOGRAM_SLOT}：多方块投影开关；
     * 槽位 29（第 4 行列 3）是 {@link #HOLOGRAM_ROTATE_SLOT}：投影旋转 ——
     * 与 H 上下成对，和第 4 列那两组同款关系。
     *
     * <p>★ 上文行号按代码里的 0 基行号写（第 3 行 = 下标 2 那一行）。
     * 曾经这份期望图把 H 画在第 2 行第 3 列（槽 11），而常量其实是槽 20 ——
     * 于是 {@code /touhou layout} 一直报着 2 处不一致却没人发现
     * （自检的价值就在于这种"改了一处忘了另一处"，所以这里逐格对齐、并且合计必须等于 54）。
     *
     * <p>⚠ 槽 22 在期望图里写 {@code Ox}（空闲时的外观），运行时它是
     * {@link #VANILLA_PROGRESS_SLOT} 原生进度条的位置。
     */
    public static List<String> layoutSummary() {
        String[][] spec = {
                {"x", "x", "x", "J", "Ox", "K", "x", "x", "x"},
                {"Ix", "I", "x", "P", "Ox", "O", "O", "O", "O"},
                {"Ix", "I", "H", "B", "Ox", "O", "O", "O", "O"},
                {"Ix", "I", "R", "Ox", "Ox", "O", "O", "O", "O"},
                {"Ix", "I", "x", "Ox", "Ox", "O", "O", "O", "O"},
                {"x", "x", "x", "x", "x", "x", "x", "x", "x"}
        };

        List<String> out = new ArrayList<>();
        // ★ 合计必须是 54：六个按钮（J/K/B/P/H/R）+ 各类槽位。
        //   这里的每一项都与上面那些槽位数组一一对应，谁多一格少一格，
        //   下面的 total != 54 判定会立刻报出来（不再靠"注释里写着 54"）。
        int buttons = 6;
        int total = BORDER.length + INPUT_BORDER.length + INPUT_SLOTS.length
                + buttons + OUTPUT.length + OUTPUT_PLACEHOLDER.length;
        out.add("尺寸 " + INVENTORY_SIZE
                + " | 玻璃板x=" + BORDER.length
                + " | 输入提示Ix=" + INPUT_BORDER.length
                + " | 输入I=" + INPUT_SLOTS.length
                + " | 信息J=" + INFO_SLOT
                + " | 模式K=" + MODE_SLOT
                + " | 构建模式B=" + BUILD_MODE_SLOT
                + " | 特效开关P=" + PARTICLE_SLOT
                + " | 投影开关H=" + HOLOGRAM_SLOT
                + " | 投影旋转R=" + HOLOGRAM_ROTATE_SLOT
                + " | 输出O=" + OUTPUT.length
                + " | 输出提示Ox=" + OUTPUT_PLACEHOLDER.length
                + " | 合计=" + total);

        int mismatch = 0;
        for (int row = 0; row < 6; row++) {
            StringBuilder actualRow = new StringBuilder();
            StringBuilder expectRow = new StringBuilder();
            for (int col = 0; col < 9; col++) {
                int slot = row * 9 + col;
                String actual = classify(slot);
                String expect = spec[row][col];
                actualRow.append(String.format("%-4s", actual)).append('|');
                expectRow.append(String.format("%-4s", expect)).append('|');
                if (!actual.equals(expect)) {
                    mismatch++;
                    out.add("  X 槽" + slot + "(行" + row + "列" + col + ") 期望 " + expect
                            + " 实际 " + actual);
                }
            }
            out.add("  实际 " + actualRow);
            out.add("  期望 " + expectRow);
        }
        out.add(mismatch == 0 ? "  OK 54 格布局与 spec 完全一致（含把空白区并入输出槽的决定）"
                : "  FAIL 有 " + mismatch + " 格与 spec 不一致");
        // ★ 逐格比对之外再核一次"总数"：它能抓到"某一格既是 A 又是 B"这类
        //   逐格比对看不出来的错（每个槽位只该属于一类）。
        out.add(total == INVENTORY_SIZE ? "  OK 各类槽位合计 = " + INVENTORY_SIZE + "（不重不漏）"
                : "  FAIL 各类槽位合计 = " + total + "，应为 " + INVENTORY_SIZE);
        return out;
    }

    /** 某个槽位在 spec 里属于哪一类。 */
    private static String classify(int slot) {
        if (slot == INFO_SLOT) {
            return "J";
        }
        if (slot == MODE_SLOT) {
            return "K";
        }
        if (slot == PARTICLE_SLOT) {
            return "P";
        }
        if (slot == BUILD_MODE_SLOT) {
            return "B";
        }
        if (slot == HOLOGRAM_SLOT) {
            return "H";
        }
        if (slot == HOLOGRAM_ROTATE_SLOT) {
            return "R";
        }
        for (int s : INPUT_SLOTS) {
            if (s == slot) {
                return "I";
            }
        }
        for (int s : INPUT_BORDER) {
            if (s == slot) {
                return "Ix";
            }
        }
        for (int s : OUTPUT) {
            if (s == slot) {
                return "O";
            }
        }
        for (int s : OUTPUT_PLACEHOLDER) {
            if (s == slot) {
                return "Ox";
            }
        }
        for (int s : BORDER) {
            if (s == slot) {
                return "x";
            }
        }
        return "??";
    }
}
