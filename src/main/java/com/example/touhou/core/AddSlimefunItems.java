package com.example.touhou.core;

import com.example.touhou.Touhou;
import com.example.touhou.power.DreamCatcher;
import com.example.touhou.power.PowerIntegratedCore;
import com.example.touhou.power.PowerRepeater;
import com.example.touhou.power.PowerStorageUnit;
import com.example.touhou.power.PowerSupplyUnit;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * TOUHOU 侧物品与配方的注册。
 *
 * <p>顺序必须和全局约定一致：{@code AddGroups → AddItems → AddSlimefunItems}，
 * 这里对应 {@link AddGroups#setup} → {@link AddItems#setup} → {@link #setup}。
 */
public final class AddSlimefunItems {

    private AddSlimefunItems() {
    }

    /**
     * 「报春の妖精」每次合成的产出数量 —— <b>2</b>。
     *
     * <p>抽成常量是为了让"需求说 2 个"只有一个出处：注册处（第 5 个参数
     * {@code new SlimefunItemStack(AddItems.SPRING_HERALD, SPRING_HERALD_OUTPUT_AMOUNT)}）
     * 与验证命令 {@code /touhou springherald} 都读它，
     * 于是命令打印的"期望产出"与真正写进合成表的数量不可能对不上。
     */
    public static final int SPRING_HERALD_OUTPUT_AMOUNT = 2;

    /**
     * 「P引擎」每次合成的产出数量 —— <b>8</b>。
     *
     * <p>抽成常量是为了让"需求说 8 个"只有一个出处：注册处
     * （{@code new SlimefunItemStack(AddItems.P_ENGINE, P_ENGINE_OUTPUT_AMOUNT)}）
     * 与验证命令 {@code /touhou pengine recipe} 都读它，
     * 于是命令打印的"期望产出"与真正写进合成表的数量不可能对不上。
     *
     * <p>★★ 与 {@link #SPRING_HERALD_OUTPUT_AMOUNT}（产出 2）是同一套机制：
     * 走 {@code SlimefunItem} 第 5 个参数 {@code recipeOutput}，
     * 而<b>模板 {@link AddItems#P_ENGINE} 的数量必须保持 1</b> ——
     * 改模板会连累 {@code /sf give} 与指南页图标，并触发粘液本体那条
     * "illegal stack size … should be handled via the recipeOutput parameter" 告警
     * （原话见 {@link #SPRING_HERALD} 的字段注释）。
     */
    public static final int P_ENGINE_OUTPUT_AMOUNT = 8;

    /** 反应堆核心（多方块核心 + 发电机）。 */
    public static UtsuhoReactorCore UTSUHO_REACTOR_CORE;
    /** 炙热的灰烬（材料）。 */
    public static SlimefunItem BLAZING_ASH;
    /** 春泥（妖精之力素材）。 */
    public static SlimefunItem SPRING_MUD;
    /**
     * 报春の妖精（报春的妖精；<b>魔法工作台</b>合成，一次产出 <b>2</b> 个）。
     *
     * <p>★ "产出 2 个"用的是<b>粘液本体的机制</b>：{@code SlimefunItem} 那条吃第 5 个参数
     * {@code recipeOutput} 的构造器，传 {@code new SlimefunItemStack(模板, 2)}。
     * 本家 90+ 处多产出物品（能源连接器 8 个 / 铜线 8 个 / 硬质玻璃 16 个…）都是这个写法，
     * 而 {@code SlimefunItem#onEnable} 里那条 "illegal stack size" 告警的原话就是
     * "Crafting Results with amounts of higher should be handled via the recipeOutput parameter"。
     *
     * <p>★ 为什么<b>不</b>自造 {@code RecipeType + BiConsumer}：那条路只改得到
     * {@code MultiBlockMachine} 的配方表，改不到本物品的 {@code recipeOutput} 字段，
     * 而<b>指南页产物格</b>（{@code SurvivalSlimefunGuide} 的 {@code menu.addItem(16, output, …)}）
     * 与<b>自动合成机</b>（{@code SlimefunItemRecipe} 的 {@code item.getRecipeOutput()}）
     * 读的正是后者 ⇒ 那两处会少一半。用本家机制则天然全对。
     *
     * <p>⚠ {@link AddItems#SPRING_HERALD} 模板本身的数量必须保持 <b>1</b>：
     * 改模板会连累 {@code /sf give} 与指南页物品图标，而且会踩上面那条告警。
     */
    public static SlimefunItem SPRING_HERALD;
    /**
     * 红叶飞散の天狗 —— 魔法工作台合成的<b>头部装备</b>，戴上后给 4 项属性加成。
     *
     * <p>★ 归属 1 级组 {@link AddGroups#MATERIAL}：与报春の妖精 / 落叶同为"素材/角色道具"，
     * 但它是唯一一件<b>带属性修饰符</b>的（见 {@link #momijiTenguAttributes()}）。
     *
     * <p>★ <b>产出 1 个</b> ⇒ 4 参构造器，<b>不传</b> {@code recipeOutput}
     * （那是"一次出多个"才用的，见 {@link #SPRING_HERALD} 的反面教材）。
     */
    public static SlimefunItem MOMIJI_TENGU;
    /**
     * 丰收之时 —— 范围强制催熟的<b>单方块机器</b>（右键生效，不需要 GUI）。
     *
     * <p>★ 归属 2 级组 {@link AddGroups#SIMPLE_MACHINE}（单方块机器，挂在容器组 MACHINE 下）：
     * 它是可放置、有实际功能的机器，与"纯素材"的春泥 / 报春の妖精不同类；
     * 又因为它<b>不是</b>多方块，所以不进 {@code COMPLEX_MACHINE}。
     * （<b>不能</b>直接挂 {@code MACHINE} —— 那是 FlexItemGroup，装物品会抛异常，
     * 判据见 {@link AddGroups#SIMPLE_MACHINE}。）
     *
     * <p>★ 配方类型 {@code RecipeType.MAGIC_WORKBENCH}：<b>用户没有指定</b>，
     * 这里按上一件物品（报春の妖精）的惯例取魔法工作台 —— 理由是配方里有
     * 「另一个世界的回响」（靠维度穿梭才能拿到），定位偏后期，配魔法工作台合理。
     * <b>这是我的判断，不是用户口径。</b>
     *
     * <p>★ <b>单次产出 1 个</b> ⇒ 用 4 参构造器，<b>不传</b>第 5 参数 {@code recipeOutput}
     * （与报春の妖精刻意相反，别顺手抄成 2）。
     */
    public static HarvestTime HARVEST_TIME;
    /**
     * 落叶 —— <b>玩家手动破坏树叶</b>时按概率掉落的材料（见 {@link FallenLeaves}）。
     *
     * <p>★ 它不是合成品：配方类型 {@link RecipeType#NULL} + 9 格全空
     * （{@link #noRecipe()}）⇒ 在任何工作台/机器里都摆不出来，
     * 指南页槽 10 显示空气（NULL 的 {@code getItem(Player)} 返回空气）。
     * 用户没要求做门面类型来说明获取方式，所以本实现没加。
     *
     * <p>★ 归属 1 级组 {@link AddGroups#MATERIAL}：与春泥 / 报春の妖精同为"纯素材"。
     */
    public static SlimefunItem FALLEN_LEAVES;
    /**
     * POINT —— 增强型工作台合成的材料（配方里引用了三件角色物品）。
     *
     * <p>★ 归属 1 级组 {@link AddGroups#MATERIAL}（用户原话"放到'材料'物品组"）。
     * ⚠ 该组的<b>显示名</b>已改为「幻想之物」，但字段名/key 未动 —— 传的仍是
     * {@code AddGroups.MATERIAL}，不是 {@code AddGroups.CHARACTER}。
     *
     * <p>★ 产出 <b>1 个</b> ⇒ 4 参构造器（不传 {@code recipeOutput}）。
     */
    public static SlimefunItem POINT;
    /**
     * P引擎 —— 增强型工作台合成的材料，★★ <b>单次产出 8 个</b>。
     *
     * <p>★ 归属 1 级组 {@link AddGroups#MATERIAL}（用户原话"放到'幻想之物'物品组"）。
     * ⚠ 该组的<b>显示名</b>已改为「幻想之物」，但字段名/key 未动 —— 传的仍是
     * {@code AddGroups.MATERIAL}，不是 {@code AddGroups.CHARACTER}。
     *
     * <p>★★ <b>多产出必须用第 5 个参数 {@code recipeOutput}</b>：
     * 注册处传 {@code new SlimefunItemStack(AddItems.P_ENGINE, P_ENGINE_OUTPUT_AMOUNT)}，
     * 而<b>模板本身的数量保持 1</b>（原因见那个常量的注释与 {@link #SPRING_HERALD} 的反面说明）。
     * 判断"有没有真的生效"要读<b>两条消费路径</b>：
     * {@code getRecipeOutput().getAmount()}（指南页产物格 / 自动合成机）与
     * <b>增强型工作台配方表里那条</b>（{@code MultiBlockMachine#addRecipe} 收到的东西）——
     * 两者都必须是 8（{@code /touhou pengine recipe} 会把这三条一起打出来）。
     */
    public static SlimefunItem P_ENGINE;
    /**
     * 冰の妖精（琪露诺 / Cirno）—— <b>右键生效的范围冰冻机器</b>（不需要 GUI）。
     *
     * <p>★ 归属 1 级组 {@link AddGroups#MATERIAL}（用户指定"物品组 MATERIAL"）。
     * 它虽然是一件"放下之后有功能"的东西，但用户明确把物品组定成了 MATERIAL，
     * 所以<b>不</b>挪到 {@code SIMPLE_MACHINE} —— 物品组归属是用户口径，不是本实现的判断。
     * （顺带一提：{@code MATERIAL} 是普通 {@code SubItemGroup}，装物品没有任何限制；
     * 只有容器组 {@code MACHINE} 才不能装，见 {@link AddGroups#SIMPLE_MACHINE}。）
     *
     * <p>★ 配方类型 {@code RecipeType.MAGIC_WORKBENCH}（用户指定），
     * 9 格图案见 {@link #cirnoRecipe()}。产出 <b>1 个</b> ⇒ 用 <b>4 参构造器</b>、
     * <b>不传</b> {@code recipeOutput}（与报春の妖精刻意相反，别顺手抄成 2）。
     *
     * <p>★ 右键行为全在 {@link Cirno#getItemHandler}（{@code SimpleSlimefunItem} 的钩子），
     * 这里只负责注册与配方。
     */
    public static Cirno CIRNO;
    /**
     * 雾中の妖精 —— <b>对空气右键</b>消耗 1 个、召唤一只叫 {@code Bomb} 的苦力怕
     * （1 秒后原地消失，留下 1 个绿色星形粒子与一个带「保护 IX」的 Bomb 物品）。
     *
     * <p>★ 归属 1 级组 {@link AddGroups#CHARACTER}（「幻想之缘起」，角色物品的家）——
     * 与另外三件角色物品同组，<b>不是</b> {@code MATERIAL}。
     *
     * <p>★ 配方类型 {@code RecipeType.MAGIC_WORKBENCH}（用户指定），
     * 9 格图案见 {@link #fairyInMistRecipe()}。产出 <b>1 个</b> ⇒ 用 <b>4 参构造器</b>、
     * <b>不传</b> {@code recipeOutput}（与报春の妖精刻意相反，别顺手抄成 2）。
     *
     * <p>★ 它的三种右键行为（对空气右键触发 / 对方块右键放下 / 右键已放下的方块只提示）
     * 全在 {@link FairyInMist}（类注释里有那张表），这里只负责注册与配方。
     */
    public static FairyInMist FAIRY_IN_MIST;
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
    /**
     * matl114（授权使用的多方块结构源码）—— INFO 组的第九张说明纸。
     *
     * <p>★ 配方类型 {@link RecipeType#NULL} + {@link #noRecipe()}：与同组其余七张纸完全一致，
     * 它不是可制造物（在收尾的 {@code /touhou acquisition all} 里由 {@link Acquisition} 标注获取方式）。
     */
    public static SlimefunItem INFO_MATL114;
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

        // 春泥：普通材料，用【增强工作台】合成 —— 本项目第一个走原版合成表路径的
        // 非机器物品（两个核心分别是机器与事件获取）。八格泥土围边，中间夹蒲公英与玫瑰。
        SPRING_MUD = register(new SlimefunItem(
                AddGroups.MATERIAL,
                AddItems.SPRING_MUD,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                springMudRecipe()), plugin);

        // 报春の妖精：普通材料，用【魔法工作台】合成，一次产出 2 个。
        // ★ 配方类型就用本体 RecipeType.MAGIC_WORKBENCH —— 它的 machine 是 "MAGIC_WORKBENCH"
        //   （SlimefunItems 里的 id），属于 MultiBlockMachine 子孙，走的是
        //   RecipeType#register 的第 2 条路（mbm.addRecipe），而传进去的 result 已经是
        //   我们在第 5 参数里给的 amount=2 栈 ⇒ 合成表里存的就是 2 个。
        //   指南页槽 10 显示的也是魔法工作台图标（本体类型的图标就是那台机器），
        //   不需要再自造门面类型。
        // ★ "产出 2 个"只在最后一个参数里出现一次（单一出处），
        //   模板 AddItems.SPRING_HERALD 保持 1 个 —— 判据见本字段的注释。
        SPRING_HERALD = register(new SlimefunItem(
                AddGroups.CHARACTER,
                AddItems.SPRING_HERALD,
                RecipeType.MAGIC_WORKBENCH,
                springHeraldRecipe(),
                new SlimefunItemStack(AddItems.SPRING_HERALD, SPRING_HERALD_OUTPUT_AMOUNT)), plugin);

        // 红叶飞散の天狗：魔法工作台合成，【产出 1 个】⇒ 4 参构造器（不传 recipeOutput）。
        // ★★ 它的 4 项属性加成【不在这里挂】—— 在 AddItems.setup() 里、注册【之前】就挂好了。
        //    踩坑记录（本次实机第一版就是这么炸的）：
        //      SlimefunItem#register → onEnable 末尾会调 itemStackTemplate.lock()
        //      （判据见 SlimefunItem 的 onEnable：if (itemStackTemplate instanceof SlimefunItemStack
        //        stack && isItemStackImmutable()) stack.lock()），
        //      锁定之后再改 ItemMeta 会抛：
        //        WrongItemStackException: You probably wanted to alter a different ItemStack:
        //        TOUHOU_CHARACTER_MOMIJI_TENGU is not mutable.
        //        at SlimefunItemStack.validate(SlimefunItemStack.java:274)
        //      ⇒ 整个插件启用失败。所以属性修饰符必须在**模板刚建好、还没注册**时写进 ItemMeta。
        MOMIJI_TENGU = register(new SlimefunItem(
                AddGroups.CHARACTER,
                AddItems.MOMIJI_TENGU,
                RecipeType.MAGIC_WORKBENCH,
                momijiTenguRecipe()), plugin);

        // 丰收之时：范围强制催熟的单方块机器。
        // ★ 4 参构造器（单次产出 1 个）—— 刻意【不】传第 5 参数 recipeOutput，
        //   与报春の妖精相反（那件是 2 个）。模板数量保持 1。
        // ★ 它的右键行为全在 HarvestTime#getItemHandler（SimpleSlimefunItem 的钩子），
        //   这里只负责注册与配方。
        HARVEST_TIME = register(new HarvestTime(
                AddGroups.SIMPLE_MACHINE,
                AddItems.HARVEST_TIME,
                RecipeType.MAGIC_WORKBENCH,
                harvestTimeRecipe()), plugin);

        // 落叶：不是合成品，靠"玩家手动破坏树叶"获得（见 FallenLeaves / FallenLeavesListener）。
        // ★ 配方类型用 NULL + 9 格全空 ⇒ 任何工作台/机器里都摆不出来（与炙热的灰烬同一路数）。
        //   代价是指南页槽 10 显示空气；用户没要求做门面类型说明获取方式，所以没加。
        FALLEN_LEAVES = register(new SlimefunItem(
                AddGroups.MATERIAL,
                AddItems.FALLEN_LEAVES,
                RecipeType.NULL,
                noRecipe()), plugin);

        // POINT：增强型工作台合成（用户说的"强化工作台"就是这台），【产出 1 个】
        //   ⇒ 4 参构造器、不传 recipeOutput（那是"一次出多个"才用的）。
        // ★ 配方里引用了另外三件角色物品（雾中の妖精 / 红叶飞散の天狗 / 报春の妖精）——
        //   它们都是本项目自己的模板，配方匹配拿**粘液 id** 比，所以必须给模板本身。
        POINT = register(new SlimefunItem(
                AddGroups.MATERIAL,
                AddItems.POINT,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                pointRecipe()), plugin);

        // P引擎：增强型工作台合成（与 POINT 同一台机器），★★【单次产出 8 个】
        //   ⇒ 必须用吃第 5 个参数 recipeOutput 的 5 参构造器（判据见 P_ENGINE_OUTPUT_AMOUNT）。
        //   ⚠ 模板 AddItems.P_ENGINE 保持 1 个 —— 顺手把模板改成 8 会连累 /sf give 与指南图标，
        //     并触发粘液本体那条 "illegal stack size" 告警（SPRING_HERALD 的字段注释有原文）。
        //   ★ 配方里引用了上一件物品 POINT（本项目自己的模板）—— 配方匹配拿**粘液 id** 比，
        //     所以必须给模板本身，给"看起来一样"的别的东西是匹配不上的。
        P_ENGINE = register(new SlimefunItem(
                AddGroups.MATERIAL,
                AddItems.P_ENGINE,
                RecipeType.ENHANCED_CRAFTING_TABLE,
                pEngineRecipe(),
                new SlimefunItemStack(AddItems.P_ENGINE, P_ENGINE_OUTPUT_AMOUNT)), plugin);

        // 冰の妖精：魔法工作台合成，【产出 1 个】⇒ 4 参构造器（不传 recipeOutput）。
        // ★ 它的右键行为（9×9×9 冰冻 + 缓慢 9 + 那句 Bakabaka）全在 Cirno#getItemHandler，
        //   这里只负责注册与配方 —— 与「丰收之时」同一路数（SimpleSlimefunItem 的钩子）。
        CIRNO = register(new Cirno(
                AddGroups.CHARACTER,
                AddItems.CIRNO,
                RecipeType.MAGIC_WORKBENCH,
                cirnoRecipe()), plugin);

        // 雾中の妖精：魔法工作台合成，【产出 1 个】⇒ 4 参构造器（不传 recipeOutput）。
        // ★ 它的三种右键行为全在 FairyInMist（对空气右键触发 / 对方块右键放下 /
        //   右键已放下的方块只提示），这里只负责注册与配方。
        FAIRY_IN_MIST = register(new FairyInMist(
                AddGroups.CHARACTER,
                AddItems.FAIRY_IN_MIST,
                RecipeType.MAGIC_WORKBENCH,
                fairyInMistRecipe()), plugin);

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
        // matl114：INFO 组的第九张纸。★ 与同组其余七张同一路数（NULL + 9 格全空）。
        INFO_MATL114 = register(new SlimefunItem(
                AddGroups.INFO, AddItems.INFO_MATL114,
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

        // ------------------------------------------------------------------ 获取方式门面
        // ★ 必须在【所有物品都注册完之后】才能跑：它要遍历 Slimefun 注册表
        //   （Slimefun.getRegistry().getAllSlimefunItems()），没注册的看不见。
        //   它同时顺手做"漏登记获取方式"的核查并在控制台 warn —— 见 Acquisition 的类注释。
        Acquisition.applyFacades();
    }

    /** 梦想封印 集的配方：竖着一条，模仿钓鱼竿的形状（与改名前一致，没有动）。 */
    /**
     * 春泥的合成配方（增强工作台）：八格泥土围边，中间一排是 蒲公英 / 泥土 / 玫瑰。
     *
     * <pre>
     *   泥土    泥土    泥土
     *   蒲公英  泥土    玫瑰
     *   泥土    泥土    泥土
     * </pre>
     *
     * <p>★ 这里的「玫瑰」用 {@code Material.POPPY}：它在 1.14 之前的中文名就叫「玫瑰」，
     * 中文社区至今仍多这么称呼（1.14 之后官方中文改叫「虞美人」）。
     * 若将来想换成玫瑰丛（{@code ROSE_BUSH}）或凋灵玫瑰（{@code WITHER_ROSE}），改这一处即可。
     */
    private static ItemStack[] springMudRecipe() {
        return new ItemStack[] {
                new ItemStack(Material.DIRT), new ItemStack(Material.DIRT), new ItemStack(Material.DIRT),
                new ItemStack(Material.DANDELION), new ItemStack(Material.DIRT), new ItemStack(Material.POPPY),
                new ItemStack(Material.DIRT), new ItemStack(Material.DIRT), new ItemStack(Material.DIRT)
        };
    }

    /**
     * 报春の妖精的合成配方（<b>魔法工作台</b>）：八格春泥围边，正中间一个水桶。
     *
     * <pre>
     *   春泥   春泥   春泥
     *   春泥   水桶   春泥
     *   春泥   春泥   春泥
     * </pre>
     *
     * <p>★ 「春泥」用的是本项目自己的物品模板 {@link AddItems#SPRING_MUD}
     * （id {@code TOUHOU_MATERIAL_SPRING_MUD}）—— 配方匹配是拿<b>粘液 id</b>比的
     * （{@code SlimefunUtils.isItemSimilar}），所以这里必须给模板本身，
     * 给"看起来一样"的苔藓块是匹配不上的。
     *
     * <p>★ 「水桶」是原版 {@code Material.WATER_BUCKET}：合成后会照常消耗掉那个桶
     * （不做"返还空桶"处理 —— 本体也不做，见 {@code MagicWorkbench#craft}）。
     *
     * <p>★ 产出数量 <b>2</b> 不在这里写 —— 本方法只管 9 格图案，
     * 数量由注册处那个第 5 参数 {@code new SlimefunItemStack(模板, 2)} 统一决定
     * （判据见 {@link #SPRING_HERALD}）。
     */
    private static ItemStack[] springHeraldRecipe() {
        return new ItemStack[] {
                AddItems.SPRING_MUD, AddItems.SPRING_MUD, AddItems.SPRING_MUD,
                AddItems.SPRING_MUD, new ItemStack(Material.WATER_BUCKET), AddItems.SPRING_MUD,
                AddItems.SPRING_MUD, AddItems.SPRING_MUD, AddItems.SPRING_MUD
        };
    }

    /**
     * 红叶飞散の天狗的合成配方（<b>魔法工作台</b>）。
     *
     * <pre>
     *   红色染料     落叶            魔法结晶III
     *   落叶         另一个世界的回响 落叶
     *   魔法结晶III  落叶            红色染料
     * </pre>
     *
     * <p>★ 「落叶」与「另一个世界的回响」都是本项目自己的模板
     * （{@link AddItems#FALLEN_LEAVES} / {@link AddItems#ECHO_OF_ANOTHER_WORLD}）——
     * 配方匹配拿<b>粘液 id</b> 比，所以必须给模板本身。
     *
     * <p>★ 「魔法结晶III」用的是<b>本体常量</b> {@code SlimefunItems.MAGIC_LUMP_3}
     * （字段名已用 {@code javap} 在运行期 jar 上核实：本体里是
     * {@code MAGIC_LUMP_1 / MAGIC_LUMP_2 / MAGIC_LUMP_3} 三档，
     * 没有任何叫 {@code MAGIC_CRYSTAL} 的字段 —— 中文名"魔法结晶"对应的是 {@code MAGIC_LUMP}）。
     * 用常量而不是硬写 id 字符串，才不会在改名时静默失配。
     *
     * <p>★ 产出 <b>1</b> 个：本方法只管 9 格图案；注册处用的是 4 参构造器
     * （没有 {@code recipeOutput}），所以产出就是模板自己的数量 1。
     */
    private static ItemStack[] momijiTenguRecipe() {
        return new ItemStack[] {
                new ItemStack(Material.RED_DYE), AddItems.FALLEN_LEAVES, SlimefunItems.MAGIC_LUMP_3,
                AddItems.FALLEN_LEAVES, AddItems.ECHO_OF_ANOTHER_WORLD, AddItems.FALLEN_LEAVES,
                SlimefunItems.MAGIC_LUMP_3, AddItems.FALLEN_LEAVES, new ItemStack(Material.RED_DYE)
        };
    }

    // ---------------------------------------------------------------- 属性加成
    //
    // ★★ 「红叶飞散の天狗」的 4 项属性加成【不在这里挂】，而在
    //    {@code AddItems.applyMomijiAttributes()}（AddItems.setup() 的末尾、注册之前）。
    //    踩坑记录（本次实机第一版就是这么炸的）：
    //      SlimefunItem#register → onEnable 末尾会调 itemStackTemplate.lock()
    //      （判据见 SlimefunItem 的 onEnable：if (itemStackTemplate instanceof SlimefunItemStack
    //        stack && isItemStackImmutable()) stack.lock()），
    //      锁定之后再改 ItemMeta 会抛：
    //        WrongItemStackException: You probably wanted to alter a different ItemStack:
    //        TOUHOU_CHARACTER_MOMIJI_TENGU is not mutable.
    //        at SlimefunItemStack.validate(SlimefunItemStack.java:274)
    //        at SlimefunItemStack.setItemMeta(SlimefunItemStack.java:254)
    //      ⇒ 整个插件启用失败（"Error occurred while enabling Touhou"）。
    //    所以"给物品模板加属性修饰符"这件事必须发生在**注册之前**。
    //    诊断读数见 {@code /touhou momiji attr}。

    /**
     * 丰收之时的合成配方（<b>魔法工作台</b>）。
     *
     * <pre>
     *   骨块     小麦            骨块
     *   小麦种子  另一个世界的回响  小麦种子
     *   骨块     小麦            骨块
     * </pre>
     *
     * <p>★ 正中间是<b>本项目自己的物品</b>「另一个世界的回响」
     * （{@link AddItems#ECHO_OF_ANOTHER_WORLD}，id {@code TOUHOU_MATERIAL_ECHO_OF_ANOTHER_WORLD}）——
     * 配方匹配拿粘液 id 比，所以必须给模板本身。
     * 其余八格是原版材料：骨块 / 小麦 / 小麦种子。
     *
     * <p>★ 单次产出 <b>1</b> 个：本方法只给 9 格图案，注册处用的是 4 参构造器
     * （没有 {@code recipeOutput}），所以产出就是模板自己的数量 1。
     */
    private static ItemStack[] harvestTimeRecipe() {
        return new ItemStack[] {
                new ItemStack(Material.BONE_BLOCK), new ItemStack(Material.WHEAT), new ItemStack(Material.BONE_BLOCK),
                new ItemStack(Material.WHEAT_SEEDS), AddItems.ECHO_OF_ANOTHER_WORLD, new ItemStack(Material.WHEAT_SEEDS),
                new ItemStack(Material.BONE_BLOCK), new ItemStack(Material.WHEAT), new ItemStack(Material.BONE_BLOCK)
        };
    }

    /**
     * POINT 的合成配方（<b>增强型工作台</b>）。
     *
     * <pre>
     *   青金石           雾中の妖精       青金石
     *   红叶飞散の天狗    魔法结晶III      报春の妖精
     *   青金石           冰の妖精         青金石
     * </pre>
     *
     * <p>★ 用户说的"强化工作台"与项目里的"增强型工作台"<b>是同一台机器</b>：
     * 本体的 {@code RecipeType.ENHANCED_CRAFTING_TABLE}，
     * 在 {@link Acquisition#MACHINE_BY_RECIPE_TYPE_KEY} 里映射成显示名"增强型工作台"。
     * 本体<b>没有</b>叫"强化工作台"的配方类型，所以用这个。
     *
     * <p>★ 三件角色物品都给<b>模板本身</b>（{@code AddItems.XXX}）—— 配方匹配拿粘液 id 比，
     * 给"看起来一样"的别的物品是匹配不上的。
     * 「雾中の妖精」是 CHARACTER 组的，但配方里引用它不影响 POINT 自己的物品组。
     *
     * <p>★ 「青金石」= {@code Material.LAPIS_LAZULI}：1.20.4 里就叫这个
     * （老版本的染料类物品在 1.13 被拆成了独立 Material，不再有笼统的 {@code INK_SACK}）。
     * 「魔法结晶III」= 本体常量 {@code SlimefunItems.MAGIC_LUMP_3}（与红叶飞散の天狗那件同款口径）。
     *
     * <p>★ 产出 <b>1</b> 个：本方法只管 9 格图案；注册处用的是 4 参构造器（无 {@code recipeOutput}）。
     */
    private static ItemStack[] pointRecipe() {
        return new ItemStack[] {
                new ItemStack(Material.LAPIS_LAZULI), AddItems.FAIRY_IN_MIST, new ItemStack(Material.LAPIS_LAZULI),
                AddItems.MOMIJI_TENGU, SlimefunItems.MAGIC_LUMP_3, AddItems.SPRING_HERALD,
                new ItemStack(Material.LAPIS_LAZULI), AddItems.CIRNO, new ItemStack(Material.LAPIS_LAZULI)
        };
    }

    /**
     * P引擎的合成配方（<b>增强型工作台</b>）。
     *
     * <pre>
     *   铜线     铜线     铜线
     *   碳       钢板     锌锭
     *   碳       POINT    锌锭
     * </pre>
     *
     * <p>★ 用户说的"强化工作台"与项目里的"增强型工作台"<b>是同一台机器</b>：
     * 本体的 {@code RecipeType.ENHANCED_CRAFTING_TABLE}，在
     * {@link Acquisition#MACHINE_BY_RECIPE_TYPE_KEY} 里映射成显示名"增强型工作台"。
     * 本体<b>没有</b>叫"强化工作台"的配方类型，所以用这个（与 POINT 那次同一判断）。
     *
     * <p>★ 四个本体材料都用 <b>{@code SlimefunItems} 常量</b>而不是硬写 id 字符串：
     * 铜线 {@code COPPER_WIRE} / 碳 {@code CARBON} / 钢板 {@code STEEL_PLATE} /
     * 锌锭 {@code ZINC_INGOT}。这四个字段名已在<b>运行期</b>
     * {@code Slimefun-2026.07-release.jar} 上用 {@code javap} 逐个核实存在
     * （同一个 jar 里另有 {@code COMPRESSED_CARBON} 等近似项，
     * 需求点名的是 {@code CARBON}，所以<b>不</b>替换）。
     * 用常量而不是字符串，才不会在改名时静默失配。
     *
     * <p>★ 「POINT」是上一件物品 {@link AddItems#POINT} 的<b>模板本身</b>：
     * 配方匹配拿粘液 id 比，给"看起来一样"的别的东西是匹配不上的。
     *
     * <p>★ 配方撞车检查：本图案与项目里另外几条增强型工作台配方
     * （春泥的泥土围边 / 反应堆核心的强化板 / 两张符卡的竖条）都不相同，
     * 不会出现"后注册的覆盖前一个"（判据见 {@code modules\03} §6）。
     *
     * <p>★★ 产出 <b>8</b> 个<b>不在这里写</b> —— 本方法只管 9 格图案，
     * 数量由注册处那个第 5 参数统一决定（见 {@link #P_ENGINE_OUTPUT_AMOUNT}）。
     */
    private static ItemStack[] pEngineRecipe() {
        return new ItemStack[] {
                SlimefunItems.COPPER_WIRE, SlimefunItems.COPPER_WIRE, SlimefunItems.COPPER_WIRE,
                SlimefunItems.CARBON, SlimefunItems.STEEL_PLATE, SlimefunItems.ZINC_INGOT,
                SlimefunItems.CARBON, AddItems.POINT, SlimefunItems.ZINC_INGOT
        };
    }

    /**
     * 冰の妖精的合成配方（<b>魔法工作台</b>，用户指定 3×3 图案）。
     *
     * <pre>
     *   水桶            春泥              冰
     *   春泥            报春の妖精        春泥
     *   浮冰            春泥              蓝冰
     * </pre>
     *
     * <p>逐格对应（数组下标 0..8 是"左上 → 右下"的阅读顺序）：
     * <ol>
     *   <li>{@code [0]} 水桶 = 原版 {@code Material.WATER_BUCKET}（合成后照常消耗掉那个桶，
     *       不做"返还空桶"处理 —— 与报春の妖精的配方一致，本体也不做）；</li>
     *   <li>{@code [1] [3] [5] [7]} 春泥 = 本项目自己的物品
     *       {@link AddItems#SPRING_MUD}（id {@code TOUHOU_MATERIAL_SPRING_MUD}）——
     *       配方匹配是拿<b>粘液 id</b> 比的（{@code SlimefunUtils.isItemSimilar}），
     *       所以必须给模板本身，给"看起来一样"的苔藓块是匹配不上的；</li>
     *   <li>{@code [2]} 冰 / {@code [6]} 浮冰 / {@code [8]} 蓝冰 =
     *       原版 {@code Material.ICE} / {@code PACKED_ICE} / {@code BLUE_ICE}；</li>
     *   <li>{@code [4]} 报春の妖精 = {@link AddItems#SPRING_HERALD}
     *       （用户点名"刚改过名的那件"）。★ 它的旧字段 {@code LILY_WHITE} / 旧 id
     *       {@code TOUHOU_MATERIAL_LILY_WHITE} 已经<b>不存在</b>了，
     *       所以这里只能引用改名后的 {@code SPRING_HERALD}。</li>
     * </ol>
     *
     * <p>★ 单次产出 <b>1</b> 个：本方法只给 9 格图案，注册处用的是 4 参构造器
     * （没有 {@code recipeOutput}），所以产出就是模板自己的数量 1。
     */
    private static ItemStack[] cirnoRecipe() {
        return new ItemStack[] {
                new ItemStack(Material.WATER_BUCKET), AddItems.SPRING_MUD, new ItemStack(Material.ICE),
                AddItems.SPRING_MUD, AddItems.SPRING_HERALD, AddItems.SPRING_MUD,
                new ItemStack(Material.PACKED_ICE), AddItems.SPRING_MUD, new ItemStack(Material.BLUE_ICE)
        };
    }

    /**
     * 雾中の妖精的合成配方（<b>魔法工作台</b>，用户指定 3×3 图案）。
     *
     * <pre>
     *   落叶           TNT           落叶
     *   春泥           春泥           春泥
     *   空白符文       春泥           空白符文
     * </pre>
     *
     * <p>逐格对应（数组下标 0..8 是"左上 → 右下"的阅读顺序）：
     * <ol>
     *   <li>{@code [0] [2]} 落叶 = 本项目自己的物品 {@link AddItems#FALLEN_LEAVES}
     *       （id {@code TOUHOU_MATERIAL_FALLEN_LEAVES}）—— 配方匹配是拿<b>粘液 id</b>
     *       比的（{@code SlimefunUtils.isItemSimilar}），所以必须给模板本身；</li>
     *   <li>{@code [1]} TNT = 原版 {@code Material.TNT}；</li>
     *   <li>{@code [3] [4] [5] [7]} 春泥 = {@link AddItems#SPRING_MUD}
     *       （id {@code TOUHOU_MATERIAL_SPRING_MUD}）—— 同上，必须给模板本身；</li>
     *   <li>{@code [6] [8]} 空白符文 = <b>本体常量</b> {@code SlimefunItems.BLANK_RUNE}。
     *       ★ 字段名已用 {@code javap} 在运行期 {@code Slimefun4-2025.1.jar} 上核实
     *       （{@code public static final SlimefunItemStack BLANK_RUNE;}）——
     *       与 {@code momijiTenguRecipe} 里用 {@code SlimefunItems.MAGIC_LUMP_3} 同一路数：
     *       <b>用常量而不是硬写 id 字符串</b>，才不会在改名时静默失配。</li>
     * </ol>
     *
     * <p>★ 单次产出 <b>1</b> 个：本方法只给 9 格图案，注册处用的是 4 参构造器
     * （没有 {@code recipeOutput}），所以产出就是模板自己的数量 1。
     */
    private static ItemStack[] fairyInMistRecipe() {
        return new ItemStack[] {
                AddItems.FALLEN_LEAVES, new ItemStack(Material.TNT), AddItems.FALLEN_LEAVES,
                AddItems.SPRING_MUD, AddItems.SPRING_MUD, AddItems.SPRING_MUD,
                SlimefunItems.BLANK_RUNE, AddItems.SPRING_MUD, SlimefunItems.BLANK_RUNE
        };
    }

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
                + " / " + (SPRING_HERALD == null ? "报春の妖精=未注册"
                        : "报春の妖精=OK(" + SPRING_HERALD.getId()
                                + ",产出" + SPRING_HERALD_OUTPUT_AMOUNT + ")")
                + " / " + (MOMIJI_TENGU == null ? "红叶飞散の天狗=未注册"
                        : "红叶飞散の天狗=OK(" + MOMIJI_TENGU.getId() + ",产出1,属性4项)")
                + " / " + (HARVEST_TIME == null ? "丰收之时=未注册"
                        : "丰收之时=OK(" + HARVEST_TIME.getId() + ",产出1)")
                + " / " + (FALLEN_LEAVES == null ? "落叶=未注册"
                        : "落叶=OK(" + FALLEN_LEAVES.getId() + ",破坏树叶掉落)")
                + " / " + (CIRNO == null ? "冰の妖精=未注册"
                        : "冰の妖精=OK(" + CIRNO.getId() + ",产出1,右键9x9x9冰冻)")
                + " / " + (FAIRY_IN_MIST == null ? "雾中の妖精=未注册"
                        : "雾中の妖精=OK(" + FAIRY_IN_MIST.getId() + ",产出1,对空气右键召唤)")
                + " / " + (POINT == null ? "POINT=未注册"
                        : "POINT=OK(" + POINT.getId() + ",产出1)")
                + " / " + (P_ENGINE == null ? "P引擎=未注册"
                        : "P引擎=OK(" + P_ENGINE.getId() + ",产出" + P_ENGINE_OUTPUT_AMOUNT
                                + ",模板" + AddItems.P_ENGINE.getAmount() + ")")
                + " / " + (ECHO_OF_ANOTHER_WORLD == null ? "另一个世界的回响=未注册"
                        : "另一个世界的回响=OK(" + ECHO_OF_ANOTHER_WORLD.getId() + ")")
                + " / " + (INFO_MODESHIFT == null ? "模式玻璃板=未注册" : "模式玻璃板=OK")
                + " / INFO=" + (INFO_PLUGIN_MESSAGE == null ? "未注册"
                        : (INFO_PLUGIN_MESSAGE.getId() + "," + INFO_DECLARATION_1.getId()
                                + "," + INFO_DECLARATION_2.getId()
                                + "," + INFO_DECLARATION_3.getId()
                                + "," + INFO_TARTARIC_ACID.getId()
                                + "," + INFO_TEAM_SHANGHAI_ALICE.getId()
                                + "," + INFO_NING_MENG.getId()
                                + "," + INFO_MATL114.getId()))
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
