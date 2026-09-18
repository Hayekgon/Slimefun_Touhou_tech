package com.example.touhou.core;

import com.example.touhou.Touhou;
import com.example.touhou.power.PowerIntegratedCore;
import com.example.touhou.power.PowerRepeater;
import com.example.touhou.power.PowerStorageUnit;
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
    /** 逻辑奇点（材料）。 */
    public static SlimefunItem LOGIC_SINGULARITY;
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
    /** 博丽的御币（Flee into Gensokyo，充能道具）。 */
    public static HakureiGohei HAKUREI_GOHEI;

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

    public static void setup(Touhou plugin) {
        // 材料：逻辑奇点本身就是反应堆的产物，所以给一个"没有配方"的占位，
        // 只作为物品存在（玩家只能从反应堆拿到）。
        LOGIC_SINGULARITY = register(new SlimefunItem(
                AddGroups.MATERIAL,
                AddItems.LOGIC_SINGULARITY,
                RecipeType.NULL,
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
                RecipeType.NULL, noRecipe()), plugin);

        // ★ 标签：保护罩与两个接口共用 touhou:reactor_shell。
        //   结构层图里写 `S: "#touhou:reactor_shell"`，这些方块都能满足那一格的要求 ——
        //   也就是"接口可以替代保护罩搭建"。
        ItemTags.tag("touhou:reactor_shell",
                REACTOR_SHIELD.getId(),
                REACTOR_INPUT_PORT.getId(),
                REACTOR_OUTPUT_PORT.getId());
        // ★★ 但是【投影图标】必须挑定一个：层图里 'S' 那一格占了整座反应堆的一大半，
        //    画出来的应该是用户明确要求的「旧地狱-反应堆保护罩」，而不是"成员里恰好排第一的那个"
        //    （登记顺序只是注册顺序的副产品，改一行注册就会让图标悄悄换成接口）。
        //    所以显式登记代表件 —— 见 ItemTags.setDefaultDisplay 的注释。
        ItemTags.setDefaultDisplay("touhou:reactor_shell", REACTOR_SHIELD.getId());

        // Flee into Gensokyo：博丽的御币（形状模仿钓鱼竿）
        // ★ PARTY_ITEM 之前因为一个物品都没有，注册表里根本没它；
        //   装上这个物品之后，这个 1 级组才真正出现在 TH_TECH 菜单里。
        HAKUREI_GOHEI = register(new HakureiGohei(
                AddGroups.PARTY_ITEM,
                AddItems.HAKUREI_GOHEI,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                goheiRecipe()), plugin);

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
    }

    /** 博丽的御币配方：竖着一条，模仿钓鱼竿的形状。 */
    private static ItemStack[] goheiRecipe() {
        return new ItemStack[] {
                null, AddItems.LOGIC_SINGULARITY, null,
                null, SlimefunItems.REINFORCED_PLATE, null,
                null, SlimefunItems.ELECTRIC_MOTOR, null
        };
    }

    /**
     * 反应堆核心的合成配方。
     *
     * <p>刻意用"钢锭 + 强化合金 + 下界之星"这种中后期材料，而不是测试方块 ——
     * 这台机器按 spec 的定位是顶级产能机器，配方不该便宜到随手能做。
     */
    private static ItemStack[] reactorRecipe() {
        return new ItemStack[] {
                SlimefunItems.REINFORCED_PLATE, SlimefunItems.BLISTERING_INGOT_3, SlimefunItems.REINFORCED_PLATE,
                SlimefunItems.ELECTRIC_MOTOR, SlimefunItems.STRANGE_NETHER_GOO, SlimefunItems.ELECTRIC_MOTOR,
                SlimefunItems.REINFORCED_PLATE, SlimefunItems.CARBONADO, SlimefunItems.REINFORCED_PLATE
        };
    }

    /** 9 格全空（RecipeType.NULL 用）。 */
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
                + " / " + (LOGIC_SINGULARITY == null ? "逻辑奇点=未注册" : "逻辑奇点=OK")
                + " / " + (INFO_MODESHIFT == null ? "模式玻璃板=未注册" : "模式玻璃板=OK")
                + " / INFO=" + (INFO_PLUGIN_MESSAGE == null ? "未注册"
                        : (INFO_PLUGIN_MESSAGE.getId() + "," + INFO_DECLARATION_1.getId()
                                + "," + INFO_DECLARATION_2.getId()
                                + "," + INFO_DECLARATION_3.getId()
                                + "," + INFO_TARTARIC_ACID.getId()
                                + "," + INFO_TEAM_SHANGHAI_ALICE.getId()
                                + "," + INFO_NING_MENG.getId()))
                + " / " + (HAKUREI_GOHEI == null ? "博丽的御币=未注册" : "博丽的御币=OK")
                + " / " + (REACTOR_INPUT_PORT == null ? "输入接口=未注册" : "输入接口=OK")
                + " / " + (REACTOR_OUTPUT_PORT == null ? "输出接口=未注册" : "输出接口=OK")
                + " / POWER=" + (POWER_INTEGRATED_CORE == null ? "未注册"
                        : (POWER_INTEGRATED_CORE.getId() + "," + POWER_REPEATER.getId()
                                + "," + POWER_STORAGE_UNIT.getId()))
                + " / 祭祀=" + (SHRINE_POST == null ? "未注册"
                        : (SHRINE_POST.getId() + "," + SAIZENBAKO.getId()))
                + " / 构件=" + (REACTOR_FRAME == null ? "未注册"
                        : (REACTOR_FRAME.getId() + "," + REACTOR_SHIELD.getId() + ","
                                + REACTOR_STABILIZER.getId() + "," + REACTOR_BASE.getId()));
    }
}
