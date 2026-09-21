package com.example.touhou.core;

import com.example.touhou.Touhou;
import com.example.touhou.power.DreamCatcher;
import com.example.touhou.power.PowerIntegratedCore;
import com.example.touhou.power.PowerRepeater;
import com.example.touhou.power.PowerStorageUnit;
import com.example.touhou.power.PowerSupplyUnit;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import org.bukkit.inventory.ItemStack;

/**
 * TOUHOU 侧物品与配方的注册。
 *
 * <p>顺序必须和全局约定一致：{@code AddGroups → AddItems → AddSlimefunItems}，
 * 这里对应 {@link AddGroups#setup} → {@link AddItems#setup} → {@link #setup}。
 */
public final class AddSlimefunItems {

    private AddSlimefunItems() {
    }

    /** 反应堆核心（多方块核心 + 发电机）。 */
    public static UtsuhoReactorCore UTSUHO_REACTOR_CORE;
    /** 炙热的灰烬（材料）。 */
    public static SlimefunItem BLAZING_ASH;
    /**
     * 另一个世界的回响（材料；玩家穿过维度之门时由能量水晶转化而来）。
     *
     * <p>它<b>不是</b>合成品：配方数组是 {@link #noRecipe()}，配方类型是
     * {@link TouhouRecipeTypes#DIMENSION_SHUTTLE}（"只当门面、不落合成表"的类型，
     * 判据见那个类的类注释）。获取机制见 {@link EchoOfAnotherWorld#shuttle}。
     */
    public static EchoOfAnotherWorld ECHO_OF_ANOTHER_WORLD;
    /** 模式切换玻璃板（GUI 功能件，归属 1 级组 INFO，无配方）。 */
    public static SlimefunItem INFO_MODESHIFT;

    // INFO 组：信息类纸张（全部 PAPER 材质、无配方，仅作展示）
    /** 插件消息（含动态版本号）。 */
    public static SlimefunItem INFO_PLUGIN_MESSAGE;
    /** 声明 1/3（同人性质 · 素材来源与授权）。 */
    public static SlimefunItem INFO_DECLARATION_1;
    /** 声明 2/3（非商业 · 相同方式共享 · 免责）。 */
    public static SlimefunItem INFO_DECLARATION_2;
    /** 声明 3/3（其他权利归属 · 致谢）。 */
    public static SlimefunItem INFO_DECLARATION_3;
    /** 酒石酸菌（多方块结构授权者）。 */
    public static SlimefunItem INFO_TARTARIC_ACID;
    /** 上海爱丽丝幻乐团。 */
    public static SlimefunItem INFO_TEAM_SHANGHAI_ALICE;
    /** Ning_Meng__（项目提出者、代码开发者）。 */
    public static SlimefunItem INFO_NING_MENG;
    /** 梦想封印 集（Flee into Gensokyo，POWER 充能道具）。 */
    public static FantasySeal FANTASY_SEAL;
    /**
     * 杀意的百合（Flee into Gensokyo 的第二件符卡）。
     *
     * <p>它继承 {@link PartyItem}，所以 POWER 刻度与取电链路与梦想封印 集<b>共用一份声明</b>；
     * 设置各落在 {@code Items.yml} 自己的 id 分节里。
     */
    public static MurderousLily MURDEROUS_LILY;

    // 多方块构件（归属 2 级组 COMPLEX_MACHINE）
    public static SlimefunItem REACTOR_FRAME;
    public static SlimefunItem REACTOR_SHIELD;
    public static SlimefunItem REACTOR_STABILIZER;
    public static SlimefunItem REACTOR_BASE;
    /** 旧地狱-反应堆<b>输入</b>接口（红色染色玻璃；只负责进料）。 */
    public static ReactorInputPort REACTOR_INPUT_PORT;
    /** 旧地狱-反应堆<b>输出</b>接口（蓝色染色玻璃；只负责出料）。 */
    public static ReactorOutputPort REACTOR_OUTPUT_PORT;

    // 祭祀/信仰系多方块（归属 2 级组 COMPLEX_MACHINE）
    /** 神社的木桩（多方块<b>结构方块</b>；不耗电不存电）。 */
    public static ShrinePost SHRINE_POST;
    /** 赛钱箱（多方块<b>核心</b>；自研 POWER 网络的存储节点）。 */
    public static Saizenbako SAIZENBAKO;

    // POWER 能源系统（归属 1 级组 POWER）
    public static PowerIntegratedCore POWER_INTEGRATED_CORE;
    public static PowerRepeater POWER_REPEATER;
    public static PowerStorageUnit POWER_STORAGE_UNIT;
    /** 幻梦捕捉器（POWER 体系的第一台产能设备）。 */
    public static DreamCatcher POWER_DREAMCATCHER;
    /**
     * POWER供给单元（POWER 体系的<b>无线供电器</b>）。
     *
     * <p>★ 梦想封印 集 / 杀意的百合的 {@code charge-wireless} 保持 {@code false}
     * （那是"道具自带无线充电"，已废弃）；无线充电能力落在这台机器上 ——
     * 它扫描附近的玩家，把电写进他们手上的 {@code PartyItem}。
     */
    public static PowerSupplyUnit POWER_SUPPLY_UNIT;

    public static void setup(Touhou plugin) {
        // ★ 自定义配方类型要先建好：它是两个多方块核心的"指南配方页"载体，物品构造器就要吃它
        //   （见 TouhouRecipeTypes 的类注释 —— 它【不会】让核心变成可合成物品）。
        //   它只依赖 AddItems 的模板，所以放在 AddItems.setup() 之后的任意位置都行；
        //   放最前面是为了"顺着往下读注册流程时先看到它"。
        TouhouRecipeTypes.setup();

        // 材料：炙热的灰烬本身就是反应堆的产物，所以给一个"没有配方"的占位，
        // 只作为物品存在（玩家只能从反应堆拿到）。
        BLAZING_ASH = register(new SlimefunItem(
                AddGroups.MATERIAL,
                AddItems.BLAZING_ASH,
                RecipeType.NULL,
                noRecipe()), plugin);

        // 另一个世界的回响：同样"不是合成出来的"，但配方类型不用 NULL 而用
        // TouhouRecipeTypes.DIMENSION_SHUTTLE —— 这样指南页槽 10 会显示
        // 「维度穿梭」而不是空气，玩家一眼能看出获取方式（判据见那个类的类注释）。
        // 配方数组仍是 9 格全空（noRecipe），所以它不会出现在任何合成表 / 机器里。
        // 真正的获取机制在 EchoOfAnotherWorld#shuttle，事件入口见
        // EchoOfAnotherWorldListener（Touhou#onEnable 里注册）。
        ECHO_OF_ANOTHER_WORLD = register(new EchoOfAnotherWorld(
                AddGroups.MATERIAL,
                AddItems.ECHO_OF_ANOTHER_WORLD,
                TouhouRecipeTypes.DIMENSION_SHUTTLE,
                noRecipe()), plugin);

        // GUI 模式玻璃板：必须真实注册，否则它的粘液 id 不存在。
        // 归属 INFO（1 级）—— 它是 GUI 内部功能件，不是可制造的材料。
        INFO_MODESHIFT = register(new SlimefunItem(
                AddGroups.INFO,
                AddItems.INFO_MODESHIFT,
                RecipeType.NULL,
                noRecipe()), plugin);

        // INFO 组：信息类纸张。同样用 RecipeType.NULL —— 它们不是可制造物，
        // 只是把版本号、声明与致谢放进游戏里，玩家在 INFO 组里就能读到。
        INFO_PLUGIN_MESSAGE = register(new SlimefunItem(
                AddGroups.INFO, AddItems.INFO_PLUGIN_MESSAGE,
                RecipeType.NULL, noRecipe()), plugin);
        INFO_DECLARATION_1 = register(new SlimefunItem(
                AddGroups.INFO, AddItems.INFO_DECLARATION_1,
                RecipeType.NULL, noRecipe()), plugin);
        INFO_DECLARATION_2 = register(new SlimefunItem(
                AddGroups.INFO, AddItems.INFO_DECLARATION_2,
                RecipeType.NULL, noRecipe()), plugin);
        INFO_DECLARATION_3 = register(new SlimefunItem(
                AddGroups.INFO, AddItems.INFO_DECLARATION_3,
                RecipeType.NULL, noRecipe()), plugin);
        INFO_TARTARIC_ACID = register(new SlimefunItem(
                AddGroups.INFO, AddItems.INFO_TARTARIC_ACID,
                RecipeType.NULL, noRecipe()), plugin);
        INFO_TEAM_SHANGHAI_ALICE = register(new SlimefunItem(
                AddGroups.INFO, AddItems.INFO_TEAM_SHANGHAI_ALICE,
                RecipeType.NULL, noRecipe()), plugin);
        INFO_NING_MENG = register(new SlimefunItem(
                AddGroups.INFO, AddItems.INFO_NING_MENG,
                RecipeType.NULL, noRecipe()), plugin);

        // 多方块大型机器：反应堆核心
        // ★ 保留原有的【增强工作台】配方（强化板 / 鼓胀锭III / 马达 / 下界粘液球 / 碳素）——
        //   这条配方是刻意设计过的中后期配方，按 spec 的定位这台机器是顶级产能机器，
        //   不该便宜到随手能做。（曾经被误改成"不可合成"，已还原。）
        //
        //   ⚠ 它与"粘液书底部的自定义配方页"互不冲突：那一页来自
        //     {@code RecipeDisplayItem}（见 {@link RecipePages}），是实现在【物品】上的，
        //     与配方类型无关；配方类型只决定指南页 <b>槽 10</b> 显示哪台机器、上面 3×3 画什么。
        UTSUHO_REACTOR_CORE = register(new UtsuhoReactorCore(
                AddGroups.COMPLEX_MACHINE,
                AddItems.UTSUHO_REACTOR_CORE,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                reactorRecipe()), plugin);

        // 多方块构件：结构层图由用户指定，这里只把"积木"注册进 2 级组 COMPLEX_MACHINE。
        // ⚠ 配方暂缺（RecipeType.NULL = 不可合成），先用 /sf give 或创造模式拿取；
        //   用户给了配方就往下面的数组里填。
        REACTOR_FRAME = register(new SlimefunItem(
                AddGroups.COMPLEX_MACHINE, AddItems.REACTOR_FRAME,
                RecipeType.NULL, noRecipe()), plugin);
        REACTOR_SHIELD = register(new SlimefunItem(
                AddGroups.COMPLEX_MACHINE, AddItems.REACTOR_SHIELD,
                RecipeType.NULL, noRecipe()), plugin);
        REACTOR_STABILIZER = register(new SlimefunItem(
                AddGroups.COMPLEX_MACHINE, AddItems.REACTOR_STABILIZER,
                RecipeType.NULL, noRecipe()), plugin);
        REACTOR_BASE = register(new SlimefunItem(
                AddGroups.COMPLEX_MACHINE, AddItems.REACTOR_BASE,
                RecipeType.NULL, noRecipe()), plugin);

        // 物流接口：不是普通 SlimefunItem —— 它要映射核心 GUI + 代替核心跟 Cargo 打交道
        // （详见 AbstractReactorPort 类注释）。配方同样暂缺。
        // ★ 拆成两个物品：一个只管进料（红），一个只管出料（蓝）。
        //   两者都能替代保护罩、且可【同时】出现在一座反应堆里，
        //   所以唯一性约束是"每类各自最多一个"（config structure.unique-parts）。
        REACTOR_INPUT_PORT = register(new ReactorInputPort(
                AddGroups.COMPLEX_MACHINE, AddItems.REACTOR_INPUT_PORT,
                RecipeType.NULL, noRecipe()), plugin);
        REACTOR_OUTPUT_PORT = register(new ReactorOutputPort(
                AddGroups.COMPLEX_MACHINE, AddItems.REACTOR_OUTPUT_PORT,
                RecipeType.NULL, noRecipe()), plugin);

        // 祭祀/信仰系多方块：神社的木桩（构件）+ 赛钱箱（核心）。
        // ⚠ 配方暂缺（RecipeType.NULL = 不可合成），先用 /sf give 拿取。
        // ★ 结构层图来自 docs\saizenbako-layers.yml（内置于 AddonConfig.saizenLayers，
        //   可从 config.yml 的 saizenbako.structure 覆盖）：9×6×9，6 根木桩 + 核心自己。
        //   结构判定实现在 SaizenbakoStructure（层图数学复用 LayeredReactorStructure），
        //   机器逻辑在 SaizenbakoManager（激活 / 木桩编号 / 镜像 / 配方运作）。
        //   ★ 两边都【不】继承 AGenerator、也【不】实现 EnergyNetComponent ——
        //     它们走的是自研 POWER 能源系统（赛钱箱是 PowerComponent 的 STORAGE 节点）。
        SHRINE_POST = register(new ShrinePost(
                AddGroups.COMPLEX_MACHINE, AddItems.SHRINE_POST,
                RecipeType.NULL, noRecipe()), plugin);

        SAIZENBAKO = register(new Saizenbako(
                AddGroups.COMPLEX_MACHINE, AddItems.SAIZENBAKO,
                TouhouRecipeTypes.SAIZENBAKO, noRecipe()), plugin);

        // ★ 标签 touhou:reactor_shell（保护罩 + 两个接口）的登记【不在这里】——
        //   它挪到了 AddItems.setup() 的末尾。原因：核心物品描述里的「建造所需材料」
        //   要把层图里的 #touhou:reactor_shell 翻成可读名字，而那段 lore 在
        //   AddItems.setup() 里就拼完了；本方法排在它之后，在这里登记已经来不及
        //   （实测症状：物品描述写着 #touhou:reactor_shell，而 /touhou guide 现算出
        //     「旧地狱-反应堆保护罩」，同一件事两个说法）。
        //   请看 AddItems 末尾「标签登记」那一段，别在这里再登记一次。

        // Flee into Gensokyo：梦想封印 集（形状模仿钓鱼竿）
        // ★ PARTY_ITEM 之前因为一个物品都没有，注册表里根本没它；
        //   装上这个物品之后，这个 1 级组才真正出现在 TH_TECH 菜单里。
        //   ★ 它现在吃的是自研 POWER（不再实现 Rechargeable），充能循环由
        //     Touhou#onEnable 在注册完成之后显式启动（见 FantasySeal#startCharging）。
        FANTASY_SEAL = register(new FantasySeal(
                AddGroups.PARTY_ITEM,
                AddItems.FANTASY_SEAL,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                goheiRecipe()), plugin);

        // 杀意的百合：PARTY_ITEM 组的第二件符卡（用户 spec 里的「杀意的百合」）。
        // ★ 它【沿用】梦想封印 集的整套 POWER 数据 —— 那 8 个 ItemSetting 的声明与读取
        //   都在 PartyItem 基类里，本类不各写一份（见 PartyItem 的类注释）。
        //   物品组 / 材质 / 附魔光效同样沿用（见 AddItems）。
        // ★ 配方目前与梦想封印 集同一形状（竖着一条），因为 spec 没有给配方；
        //   要改配方只动下面这个方法即可（与 goheiRecipe 并列）。
        MURDEROUS_LILY = register(new MurderousLily(
                AddGroups.PARTY_ITEM,
                AddItems.MURDEROUS_LILY,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                lilyRecipe()), plugin);

        // ------------------------------------------------------------------ POWER 能源系统
        // ⚠ 配方暂缺（RecipeType.NULL），先用 /sf give 拿取；给了配方再填。
        POWER_INTEGRATED_CORE = register(new PowerIntegratedCore(
                AddGroups.POWER, AddItems.POWER_INTEGRATED_CORE,
                RecipeType.NULL, noRecipe()), plugin);

        POWER_REPEATER = register(new PowerRepeater(
                AddGroups.POWER, AddItems.POWER_REPEATER,
                RecipeType.NULL, noRecipe()), plugin);

        POWER_STORAGE_UNIT = register(new PowerStorageUnit(
                AddGroups.POWER, AddItems.POWER_STORAGE_UNIT,
                RecipeType.NULL, noRecipe()), plugin);

        //    幻梦捕捉器：POWER 体系的【第一台产能设备】（节点类型 GENERATOR，只捐不取）。
        //    配方同样暂缺 —— 它跟另外三件 POWER 设备保持一致的拿取方式。
        POWER_DREAMCATCHER = register(new DreamCatcher(
                AddGroups.POWER, AddItems.POWER_DREAMCATCHER,
                RecipeType.NULL, noRecipe()), plugin);

        //    POWER供给单元：POWER 体系的无线供电器（节点类型 STORAGE）。
        //    配方同样暂缺 —— 与另外四件 POWER 设备保持一致的拿取方式（/sf give）。
        //    ★ 它给玩家手上那件 PartyItem 充电（梦想封印 集 / 杀意的百合都算），
        //      判据是基类多态，不是逐个 id —— 见 PowerSupplyUnit 的类注释。
        POWER_SUPPLY_UNIT = register(new PowerSupplyUnit(
                AddGroups.POWER, AddItems.POWER_SUPPLY_UNIT,
                RecipeType.NULL, noRecipe()), plugin);
    }

    /** 梦想封印 集的配方：竖着一条，模仿钓鱼竿的形状（与改名前一致，没有动）。 */
    private static ItemStack[] goheiRecipe() {
        return new ItemStack[] {
                null, AddItems.BLAZING_ASH, null,
                null, SlimefunItems.REINFORCED_PLATE, null,
                null, SlimefunItems.ELECTRIC_MOTOR, null
        };
    }

    /**
     * 杀意的百合的配方：与梦想封印 集<b>同一形状</b>（竖着一条）。
     *
     * <p>★ spec 没有给这件道具的配方，所以按"同组同档次的符卡"处理：
     * 形状照抄，只把顶端的材料换成反应堆另一种产物口径也无从谈起 —— 先保持一致。
     * 想改配方只需要改这一个方法（注册处不动）。
     * ⚠ 与梦想封印 集<b>用同一个 {@link ItemStack}[] 形状</b>会不会撞配方？
     * 不会：Slimefun 的增强工作台按"9 格图案 + 配方类型"匹配，
     * 两件物品的图案一模一样 ⇒ 后注册的那个会覆盖前一个。
     * 所以这里把顶端材料改成 {@code CARBONADO}，让两张配方可区分（真实可合成）。
     */
    private static ItemStack[] lilyRecipe() {
        return new ItemStack[] {
                null, SlimefunItems.CARBONADO, null,
                null, SlimefunItems.REINFORCED_PLATE, null,
                null, SlimefunItems.ELECTRIC_MOTOR, null
        };
    }

    /**
     * 反应堆核心的合成配方（<b>当前未被使用</b> —— 保留下来只是为了"想恢复合成时一行就能切回去"）。
     *
     * <p>★ 当前状态：<b>正在使用</b>（{@code UTSUHO_REACTOR_CORE} 以
     * {@code RecipeType.ENHANCED_CRAFTING_TABLE} + 本方法注册）。
     *
     * <p>它一度被改成"不可合成"（自定义配方类型 + 全空数组），那是<b>一次误改</b>：
     * 原因是需求转述时把"祭坛不可合成"错套到了反应堆身上 —— 反应堆<b>原本就有</b>
     * 这条刻意设计的中后期配方。已还原。
     *
     * <p>⚠ 想改成不可合成时请先确认：粘液书底部的自定义配方页<b>不会</b>因此丢失 ——
     * 那一页来自 {@code RecipeDisplayItem}（见 {@link RecipePages}），与配方类型无关；
     * 配方类型只决定指南页<b>槽 10</b> 显示哪台机器的图标、以及上面 3×3 网格画什么。
     */
    private static ItemStack[] reactorRecipe() {
        return new ItemStack[] {
                SlimefunItems.REINFORCED_PLATE, SlimefunItems.BLISTERING_INGOT_3, SlimefunItems.REINFORCED_PLATE,
                SlimefunItems.ELECTRIC_MOTOR, SlimefunItems.STRANGE_NETHER_GOO, SlimefunItems.ELECTRIC_MOTOR,
                SlimefunItems.REINFORCED_PLATE, SlimefunItems.CARBONADO, SlimefunItems.REINFORCED_PLATE
        };
    }

    /**
     * 9 格全空 —— "这个物品没有合成表"。
     *
     * <p>两个多方块核心（反应堆 / 赛钱箱）现在也走它，配合
     * {@link TouhouRecipeTypes} 的自定义配方类型：核心既不会出现在原版工作台，
     * 也不会出现在增强工作台或任何多方块机器里（判据见那个类的类注释）。
     */
    private static ItemStack[] noRecipe() {
        return new ItemStack[] {
                null, null, null,
                null, null, null,
                null, null, null
        };
    }

    /** SlimefunItem#register 返回 void，链式写法需要自己包一层。 */
    private static <T extends SlimefunItem> T register(T item, Touhou addon) {
        item.register(addon);
        return item;
    }

    /** 供诊断输出：本模块注册了什么。 */
    public static String describe() {
        return "TOUHOU 注册："
                + (UTSUHO_REACTOR_CORE == null ? "反应堆核心=未注册" : "反应堆核心=OK")
                + " / " + (BLAZING_ASH == null ? "炙热的灰烬=未注册" : "炙热的灰烬=OK")
                + " / " + (ECHO_OF_ANOTHER_WORLD == null ? "另一个世界的回响=未注册"
                        : "另一个世界的回响=OK(" + ECHO_OF_ANOTHER_WORLD.getId() + ")")
                + " / " + (INFO_MODESHIFT == null ? "模式玻璃板=未注册" : "模式玻璃板=OK")
                + " / INFO=" + (INFO_PLUGIN_MESSAGE == null ? "未注册"
                        : (INFO_PLUGIN_MESSAGE.getId() + "," + INFO_DECLARATION_1.getId()
                                + "," + INFO_DECLARATION_2.getId()
                                + "," + INFO_DECLARATION_3.getId()
                                + "," + INFO_TARTARIC_ACID.getId()
                                + "," + INFO_TEAM_SHANGHAI_ALICE.getId()
                                + "," + INFO_NING_MENG.getId()))
                + " / " + (FANTASY_SEAL == null ? "梦想封印 集=未注册" : "梦想封印 集=OK")
                + " / " + (MURDEROUS_LILY == null ? "杀意的百合=未注册" : "杀意的百合=OK")
                + " / " + (REACTOR_INPUT_PORT == null ? "输入接口=未注册" : "输入接口=OK")
                + " / " + (REACTOR_OUTPUT_PORT == null ? "输出接口=未注册" : "输出接口=OK")
                + " / POWER=" + (POWER_INTEGRATED_CORE == null ? "未注册"
                        : (POWER_INTEGRATED_CORE.getId() + "," + POWER_REPEATER.getId()
                                + "," + POWER_STORAGE_UNIT.getId()
                                + "," + (POWER_DREAMCATCHER == null
                                        ? "幻梦捕捉器=未注册" : POWER_DREAMCATCHER.getId())))
                + " / 祭祀=" + (SHRINE_POST == null ? "未注册"
                        : (SHRINE_POST.getId() + "," + SAIZENBAKO.getId()))
                + " / 构件=" + (REACTOR_FRAME == null ? "未注册"
                        : (REACTOR_FRAME.getId() + "," + REACTOR_SHIELD.getId() + ","
                                + REACTOR_STABILIZER.getId() + "," + REACTOR_BASE.getId()));
    }
}
