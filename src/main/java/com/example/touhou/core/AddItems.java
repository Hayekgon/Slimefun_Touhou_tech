package com.example.touhou.core;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.utils.HeadTexture;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * TOUHOU 物品模板。
 *
 * <p>id 规范：{@code TOUHOU_"物品组"_"物品名英文"}，全大写。
 * 这里的"物品组"用物品实际所属的组 ID（MATERIAL / MACHINE / COMPLEX_MACHINE），
 * 而不是父组；例如多方块大型机器里的物品用 {@code COMPLEX_MACHINE}。
 */
public final class AddItems {

    private AddItems() {
    }

    /**
     * 「莉莉白」的头颅 Value —— 用户给定的那串 base64（原样照抄，不做任何加工）。
     *
     * <p>★ 调试命令 {@code /touhou lilywhite} 也读这个常量做比对，
     * 于是"用户给的那串"与"物品实际带着的那串"是同一份字符串、只有一个出处。
     */
    public static final String LILY_WHITE_TEXTURE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUv"
            + "YTAyZTc3YTIyNmFhOGJhZjlkMjkwMTYxMzliMWI2OTJhOTRlYTg4NzkwMWNhMzUzMDI3MzNjNzMxZjgxNDVlYSJ9fX0=";

    /**
     * 渐变配色表。
     *
     * <p>★ 起止色随配色走，而不是把"粉白"那组写死在 {@link #gradientName} 里 ——
     * 第二件带渐变的物品（「丰收之时」的橙→黄）直接加一行即可，
     * 也让"哪件物品用哪套渐变"在代码里一眼可查。
     */
    private enum Gradient {
        /** 莉莉白：粉 → 白。 */
        PINK_WHITE(0xFFB3D9, 0xFFFFFF),
        /** 丰收之时：橙 → 黄（用户要求"橙色→黄色 左到右渐变"）。 */
        ORANGE_YELLOW(0xFF8C00, 0xFFE24A),
        /**
         * 落叶的<b>名称</b>：金 → 棕（用户要求"金色至棕色"）。
         *
         * <p>★ 色号是本实现的判断（用户只给了颜色名）：金取 {@code #FFD700}
         * （就是"gold"那个标准金），棕取 {@code #8B4513}（saddlebrown，标准的鞍棕色）。
         */
        GOLD_BROWN(0xFFD700, 0x8B4513),
        /**
         * 落叶的<b>描述</b>：金 → 橙（用户要求"金色至橙色"）。
         *
         * <p>★★ 注意这与 {@link #GOLD_BROWN} <b>不是同一套</b> ——
         * 用户原文对名称和描述各给了一套（名称"金色至棕色"、描述"金色至橙色"）。
         * 本实现<b>照做</b>，没有擅自统一；报告里也提了一句"看起来像不统一"。
         */
        GOLD_ORANGE(0xFFD700, 0xFF8C00);

        private final int start;
        private final int end;

        Gradient(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    /** 多方块核心：旧地狱-灵乌路空反应堆。 */
    public static SlimefunItemStack UTSUHO_REACTOR_CORE;

    /** 顶级产能机器的产物：炙热的灰烬。 */
    public static SlimefunItemStack BLAZING_ASH;

    /**
     * 另一个世界的回响 —— <b>玩家穿过维度之门</b>时由身上的能量水晶转化而来。
     *
     * <p>id 按本项目铁律 {@code TOUHOU_"物品组"_"英文名"}：它归属 1 级组
     * {@link AddGroups#MATERIAL}，英文名取 <b>Echo of Another World</b>
     * ⇒ {@code TOUHOU_MATERIAL_ECHO_OF_ANOTHER_WORLD}。
     *
     * <p>★ 为什么物品组选 {@code MATERIAL}：它没有任何功能（不能右键、不耗电、
     * 不参与结构、不接 POWER），只是转化机制产出的一种材料，与同样"只能靠事件获得"
     * 的 {@link #BLAZING_ASH} 并列。用户没有指定物品组，这是本实现的判断。
     */
    public static SlimefunItemStack ECHO_OF_ANOTHER_WORLD;

    /**
     * 模式切换玻璃板（GUI 的 K 槽位内部件）。
     *
     * <p>它必须是一个真实的 {@link SlimefunItemStack}（而不是随便一个 ItemStack），
     * 否则 GUI 里显示的物品没有粘液 id、也别想被任何工具识别。
     *
     * <p>id 规范：{@code TOUHOU_"物品组"_"物品名英文"}。
     * 它归属 1 级组 {@code INFO}，所以 id 是 {@code TOUHOU_INFO_MODESHIFT}
     * （旧 id {@code TOUHOU_PHD_MODESHIFT} 里的 "PHD" 不是任何物品组，已废弃）。
     */
    public static SlimefunItemStack INFO_MODESHIFT;

    /** 春泥（妖精之力素材）。 */
    public static SlimefunItemStack SPRING_MUD;

    /**
     * 莉莉白 —— 报春的妖精（东方 Project 的「リリーホワイト」）。
     *
     * <p>id 按本项目铁律 {@code TOUHOU_"物品组"_"英文名"}：它归属 1 级组
     * {@link AddGroups#MATERIAL}，英文名取 <b>Lily White</b>
     * ⇒ {@code TOUHOU_MATERIAL_LILY_WHITE}。
     *
     * <p>★ 为什么物品组选 {@code MATERIAL}：用户没有指定物品组。它没有任何功能
     * （不能右键、不耗电、不参与结构、不接 POWER），只是魔法工作台合成出来的
     * <b>角色道具/材料</b>，与同样是"纯素材"的 {@link #SPRING_MUD} 并列。
     * 这也是本实现的判断（见最终报告）。
     *
     * <p>★ 材质是<b>头颅 Value</b>（用户给定的一串 base64），不是 Material ——
     * {@link SlimefunItemStack#SlimefunItemStack(String, String, String, String...)}
     * 那条构造器会把它交给 {@code getSkull}（本机运算，不发网络请求）。
     * 选这条构造器的判据见 {@link #gradientName}。
     *
     * <p>★ <b>本模板的数量必须保持 1</b>：合成时"产出 2 个"由配方侧的第 5 参数
     * {@code recipeOutput} 决定（见 {@link AddSlimefunItems#LILY_WHITE}），
     * 不是靠改这里。改了这里会连累 {@code /sf give} 与指南页，
     * 而且会踩 Slimefun 那条 "illegal stack size" 告警
     * （{@code SlimefunItem#onEnable}，原文就写着"多产出请走 recipeOutput 参数"）。
     */
    public static SlimefunItemStack LILY_WHITE;

    /**
     * 丰收之时 —— 秋姐妹（秋穰子 / 秋静叶）的赠与，信奉丰收之人的宝物。
     *
     * <p>id 按本项目铁律 {@code TOUHOU_"物品组"_"英文名"}：它归属 2 级组
     * {@link AddGroups#SIMPLE_MACHINE}，英文名取 <b>Harvest Time</b>
     * ⇒ {@code TOUHOU_SIMPLE_MACHINE_HARVEST_TIME}。
     *
     * <p>★ 物品组为什么是 {@code SIMPLE_MACHINE}（而不是最初设想的 {@code MACHINE}）：
     * 它是<b>可放置的单方块机器</b>（放下后右键催熟周围作物），归"机器"这一类没错；
     * 但 {@code MACHINE} 是<b>容器组</b>（{@code TouhouNestedGroup} / {@code FlexItemGroup}），
     * 往里面加物品会直接抛 {@code UnsupportedOperationException: You cannot add items to
     * a FlexItemGroup!}（实测撞到过，见 {@link AddGroups#SIMPLE_MACHINE} 的注释）。
     * 所以为它新建了 2 级组 {@code SIMPLE_MACHINE}（单方块机器），与多方块的
     * {@code COMPLEX_MACHINE} 并列挂在 {@code MACHINE} 下 —— id 也随之变成
     * {@code TOUHOU_SIMPLE_MACHINE_HARVEST_TIME}。
     *
     * <p>★ 材质：干草块（{@code Material.HAY_BLOCK}）+ 附魔光效
     * （光效靠 {@code setup()} 末尾那段 {@code addUnsafeEnchantment} + {@code HIDE_ENCHANTS}，
     * 与梦想封印 集 / 杀意的百合 / 另一个世界的回响同一套做法）。
     */
    public static SlimefunItemStack HARVEST_TIME;

    /**
     * 落叶 —— 秋风的问候（东方 Project 里"秋天的问候"这一意象的实体化）。
     *
     * <p>id 按本项目铁律 {@code TOUHOU_"物品组"_"英文名"}：它归属 1 级组
     * {@link AddGroups#MATERIAL}，英文名取 <b>Fallen Leaves</b>
     * ⇒ {@code TOUHOU_MATERIAL_FALLEN_LEAVES}（与同为"纯素材"的春泥并列）。
     *
     * <p>★ 获取方式：<b>玩家手动破坏树叶</b>时按概率掉落（见
     * {@link FallenLeaves}）—— 它不是合成品，所以配方类型是
     * {@link RecipeType#NULL} + 9 格全空（{@code AddSlimefunItems#noRecipe()}）。
     * ⚠ 这让指南页<b>槽 10 显示空气</b>（{@code RecipeType#getItem} 对 NULL 返回空气）。
     * 用户没要求做"维度穿梭"那种门面类型来说明获取方式，所以本实现<b>没有擅自加</b>；
     * 想加的话照 {@code TouhouRecipeTypes} 里那三个门面类型的写法补一个即可。
     *
     * <p>★★ 两套渐变<b>故意不同</b>：<b>名称</b>用金→棕（{@link Gradient#GOLD_BROWN}），
     * <b>描述三行</b>用金→橙（{@link Gradient#GOLD_ORANGE}）。这是用户原文
     * （名称"金色至棕色"、描述"金色至橙色"）—— 看起来像不统一，但按原文照做。
     */
    public static SlimefunItemStack FALLEN_LEAVES;

    // ------------------------------------------------------------------ INFO 组：信息类纸张
    // 全部使用 PAPER 材质，纯信息展示，无配方、无功能。
    // id 规范：TOUHOU_INFO_"物品名英文"，全大写。

    /** 插件消息（版本号动态取自 plugin.yml，不写死）。 */
    public static SlimefunItemStack INFO_PLUGIN_MESSAGE;

    /**
     * 声明 1/3：同人性质 + 素材来源与授权。
     *
     * <p>整份声明太长（约 45 行 lore）不利于阅读，按原文章节顺序拆成三张纸，
     * 每张末尾留前后篇指引。三张合起来等于《插件声明-完整版》。
     */
    public static SlimefunItemStack INFO_DECLARATION_1;

    /** 声明 2/3：非商业声明 + 相同方式共享 + 免责声明。 */
    public static SlimefunItemStack INFO_DECLARATION_2;

    /** 声明 3/3：其他权利归属 + 致谢。 */
    public static SlimefunItemStack INFO_DECLARATION_3;

    /** 酒石酸菌 —— 多方块结构授权者。 */
    public static SlimefunItemStack INFO_TARTARIC_ACID;

    /** 上海爱丽丝幻乐团 —— 东方 Project 相关 IP 实际持有者。 */
    public static SlimefunItemStack INFO_TEAM_SHANGHAI_ALICE;

    /** Ning_Meng__ —— 项目提出者、代码开发者。 */
    public static SlimefunItemStack INFO_NING_MENG;

    /**
     * Flee into Gensokyo：<b>梦想封印 集</b>（充能式 360° 追踪弹幕发射器）。
     *
     * <p>★ 2026-09-20 改名 + 改 id：原来是「博丽的御币」/ {@code TOUHOU_PARTY_ITEM_HAKUREI_GOHEI}，
     * 现在统一到符卡名（英文依据见 {@link FantasySeal} 的类注释，已由用户核对确认）。
     * ⚠ <b>改 id 的代价</b>：存档里那些老 id 的物品会变成"未知物品"（Slimefun 靠 PDC 里的 id 反查本体），
     * 这是本次改动的既定代价，不做兼容映射。
     */
    public static SlimefunItemStack FANTASY_SEAL;

    /**
     * 杀意的百合：<b>杀意的百合</b>（一支箭 → 命中点爆发：激光 + 喷泉 + 12 支追踪箭）。
     *
     * <p>★ 英文名取 <b>Murderous Lily</b>（东方 Project 里纯狐的符卡「殺意の百合」），
     * id 按本项目铁律 {@code TOUHOU_"物品组PARTY_ITEM"_"英文名"} ⇒
     * {@code TOUHOU_PARTY_ITEM_MURDEROUS_LILY}。
     *
     * <p>★ 与梦想封印 集的关系：<b>物品组、材质、附魔光效、整套 POWER 数据全部沿用</b>
     * （POWER 数据的唯一出处是 {@link PartyItem} 基类，见那个类的注释）。
     * 差别的只有描述与"打出去之后发生什么"。
     */
    public static SlimefunItemStack MURDEROUS_LILY;

    // ------------------------------------------------------------------ POWER 能源系统
    // 归属 1 级组 POWER（touhou_power）。
    // 材质按你的要求分别引用原生物品：能源调节器头 / 货运节点头 / 红色混凝土。

    /** POWER集成核心 —— 网络锚点 + 原生电网桥。 */
    public static SlimefunItemStack POWER_INTEGRATED_CORE;

    /** POWER中继器 —— 导体 + 半径跳接。 */
    public static SlimefunItemStack POWER_REPEATER;

    /** POWER存储单元 —— long 精度储能。 */
    public static SlimefunItemStack POWER_STORAGE_UNIT;

    /**
     * 幻梦捕捉器 —— POWER 体系的第一台<b>产能</b>设备。
     *
     * <p>★ 命名由来：「幻梦捕捉器」取的是<b>捕梦网（dreamcatcher）</b>的意象，
     * 所以英文名就是 <b>Dreamcatcher</b>，id 为
     * {@code TOUHOU_"物品组POWER"_DREAMCATCHER}（全大写，遵守本项目 id 铁律）。
     */
    public static SlimefunItemStack POWER_DREAMCATCHER;

    /**
     * POWER供给单元 —— POWER 体系的<b>无线供电器</b>。
     *
     * <p>★ 它是"梦想封印 集 原来那套无线充电"的接棒者：道具自带的无线充电已按用户要求
     * <b>关闭</b>（{@code PartyItem} 的 {@code charge-wireless} 保持 {@code false}），
     * 用户当初就说"后续我会单独制作无线供电器"—— 这就是那台机器。
     *
     * <p>id 为 {@code TOUHOU_"物品组POWER"_"英文名"} ⇒ {@code TOUHOU_POWER_POWER_SUPPLY_UNIT}
     * （双 POWER 是本项目 id 铁律的字面结果，与 {@code TOUHOU_POWER_POWER_INTEGRATED_CORE}
     * 等三件同一形态，刻意<b>不</b>自作主张简化）。
     */
    public static SlimefunItemStack POWER_SUPPLY_UNIT;

    // ------------------------------------------------------------------ 多方块构件
    // 归属 2 级组 COMPLEX_MACHINE，所以 id 前缀统一是 TOUHOU_COMPLEX_MACHINE_。
    // 这四个是搭建"旧地狱-灵乌路空反应堆"结构的构件（结构层图由用户指定）。

    /** 旧地狱-反应堆框架（主体骨架）。 */
    public static SlimefunItemStack REACTOR_FRAME;

    /** 旧地狱-反应堆保护罩（隔离辐射的外壳）。 */
    public static SlimefunItemStack REACTOR_SHIELD;

    /** 旧地狱-反应堆稳定器（约束核融合的部件）。 */
    public static SlimefunItemStack REACTOR_STABILIZER;

    /** 旧地狱-反应堆基座（承重底座）。 */
    public static SlimefunItemStack REACTOR_BASE;

    /**
     * 旧地狱-反应堆<b>输入接口</b>（红色染色玻璃）。
     *
     * <p>可以<b>替代保护罩</b>参与搭建（挂标签 {@code touhou:reactor_shell}）。
     * 只负责<b>进料</b>：每 5 秒把本接口输入槽的物品搬进代理核心的输入槽。
     * 它把核心的 GUI 映射出来、并代替核心跟物流打交道，但<b>自己不存电</b>。
     */
    public static SlimefunItemStack REACTOR_INPUT_PORT;

    /**
     * 旧地狱-反应堆<b>输出接口</b>（蓝色染色玻璃）。
     *
     * <p>同样可以替代保护罩。只负责<b>出料</b>：每 5 秒把代理核心输出槽的物品
     * 搬回本接口的输出槽，供 Cargo 抽走。
     */
    public static SlimefunItemStack REACTOR_OUTPUT_PORT;

    // ------------------------------------------------------------------ 祭祀/信仰系多方块
    // 同样归属 2 级组 COMPLEX_MACHINE，所以 id 前缀也是 TOUHOU_COMPLEX_MACHINE_。
    // ★ 结构层图来自 docs\saizenbako-layers.yml（9×6×9，6 根木桩 + 核心自己；
    //   核心 'C' 在最右一列，所以结构实现允许"核心偏心"）。
    //   判定在 SaizenbakoStructure，机器逻辑在 SaizenbakoManager。
    //   ★ 激活只能手动：玩家在赛钱箱 GUI 里点信息格才做一次现场结构检测。

    /**
     * 神社的木桩 —— 多方块<b>结构方块</b>（构件）。
     *
     * <p>不耗电、不存电、不发电：给玩家一个观察窗口（POWER 量 + 激活状态 + 自己的编号），
     * 并提供一格混合型输入输出端给外界物流进出货 —— 那一格也是这台机器的<b>投料口</b>。
     */
    public static SlimefunItemStack SHRINE_POST;

    /**
     * 赛钱箱 —— 多方块<b>核心</b>。
     *
     * <p>它是 POWER 网络的一个<b>存储节点</b>（不是原生电力网络的节点）。
     * 结构完整并激活后：读 6 个预留槽（= 6 根木桩 IO 槽的镜像）比对配方，
     * 命中则消耗木桩里的材料 + 2 POWER，产物放进 IO 槽。
     */
    public static SlimefunItemStack SAIZENBAKO;

    public static void setup() {
        UTSUHO_REACTOR_CORE = new SlimefunItemStack(
                "TOUHOU_COMPLEX_MACHINE_UTSUHO_REACTOR_CORE",
                Material.NETHER_QUARTZ_ORE,
                "&6旧地狱-灵乌路空反应堆",
                "",
                "&7以地狱鸦之躯驾驭核融合的&c制御棒&7主人",
                "&7把&e桶装原油&7烧成电力与&6炙热的灰烬",
                "",
                "&8多方块核心 · 需要搭建完整结构",
                "&8结构不完整时会进入 &c未激活&8 状态");

        // ★ 2026-09-21：由「逻辑奇点」改名为「炙热的灰烬」——显示名 / 材质 / 描述全换，
        //   但【物品 id 保持不变】（TOUHOU_MATERIAL_LOGIC_SINGULARITY）。
        //   理由：它是反应堆的产物，玩家存档里可能已经攒了一堆；改 id 会让那些物品
        //   变成"未知物品"。若日后确实要改 id，记得连 TohouCommand 里那处
        //   硬编码的 id 判断一起改。
        // 描述按语义断成三行（原文一字未改，只是折行——单行 60+ 字符在 tooltip 里会很难读）。
        BLAZING_ASH = new SlimefunItemStack(
                "TOUHOU_MATERIAL_LOGIC_SINGULARITY",
                Material.GUNPOWDER,
                "&6炙热的灰烬",
                "",
                "&7来自旧地狱的碎片，沾有些许灰烬。已经发光发热了许久，",
                "&7但过去了那么久，握在手中仍然炽热无比，",
                "&7或许是灵魂永不消逝的残念构成的奇妙物质");

        // 另一个世界的回响：与炙热的灰烬同为"只能靠机器/事件获得"的材料，归属 MATERIAL。
        // 材质：ECHO_SHARD（原版「回响碎片」）—— 与"回响"这个名字天然对应。
        // 描述：★ 用户给定的原文，【逐字】照抄、不拆行、不改写。
        //   （原文里"手中的电子被解构为不存在之物"的"电子"疑似笔误 ——
        //     但用户明确要求按原文照抄，所以这里一个字都没动，只在报告里提了一句。）
        // 光效：靠 setup() 末尾那段 addUnsafeEnchantment + HIDE_ENCHANTS 做出来
        //   （与梦想封印 集 / 杀意的百合同一套做法：挂一个无用的假附魔，再把附魔行藏掉，
        //    于是只剩图标外圈那层光晕。本物品没有"要看附魔行"的要求，所以照惯例隐藏）。
        ECHO_OF_ANOTHER_WORLD = new SlimefunItemStack(
                "TOUHOU_MATERIAL_ECHO_OF_ANOTHER_WORLD",
                Material.ECHO_SHARD,
                "&f另一个世界的回响",
                "",
                "&7通过传送门时无疑窥见了幻想世界的一角。手中的电子被解构为不存在之物。");

                SPRING_MUD = new SlimefunItemStack(
                "TOUHOU_MATERIAL_SPRING_MUD",
                Material.MOSS_BLOCK,
                "&a春泥",
                "",
                "&a化作春泥更护花......",
                "&a散发出微弱的妖精之力");

        // 莉莉白：报春的妖精。
        // ★ 材质：用户给定的【头颅 Value】（base64）。链路（已核实，见 SlimefunItemStack）：
        //   SlimefunItemStack(id, texture, name, lore...)
        //     → getTexture(id, texture)：texture.startsWith("ey") ⇒ 原样返回（这串正好以 "ey" 开头）
        //     → getSkull(id, texture)：PlayerSkin.fromBase64(...) → PlayerHead.getItemStack(...)
        //   所以这里【直接】把那串 Value 当第二个参数传进去即可，不用自己拼 SkullMeta。
        //   运行期用 LILY_WHITE.getSkullTexture() 读回来核对（/touhou lilywhite）。
        //
        // ★ 名字「莉莉白」与描述第一行用【粉白左到右渐变】：那串已经翻好的
        //   §x§R§R§G§G§B§B 序列【必须】直接塞进构造器，绝不能写成 "&x&f&f&b&3&d&9..." ——
        //   构造器内部只调 ChatColor.translateAlternateColorCodes('&', name)，
        //   而它【不认】&x 这种十六进制序列（'x' 不是合法颜色字符，会被原样留下）。
        //   同理：不带渐变的普通行才用 "&7" 写法（构造器会翻成 §7）。
        //   判据与实测证据见本类的 gradientName 与最终报告。
        //
        // ★ 描述按用户原文：[第一行渐变] / (endl)换行 / [第二行灰色]。中间的 "" 就是换行。
        LILY_WHITE = new SlimefunItemStack(
                "TOUHOU_MATERIAL_LILY_WHITE",
                LILY_WHITE_TEXTURE,
                gradientName("莉莉白"),
                "",
                gradientName("报春的妖精，莉莉白，"),
                "&7请城管不要无辜殴打无害的莉莉白，她很可爱=v=");

        // 丰收之时：秋姐妹的赠与（橙黄渐变的机器）。
        // ★ 名字与描述【都是】橙→黄左到右渐变（用户要求"字体同上"）。
        // ★ 材质：干草块 —— 与"丰收"意象直接对应。
        // ★ 描述按用户原文，【一行】照抄、不折行、不改写。
        HARVEST_TIME = new SlimefunItemStack(
                "TOUHOU_SIMPLE_MACHINE_HARVEST_TIME",
                Material.HAY_BLOCK,
                gradientNameOrangeYellow("丰收之时"),
                "",
                gradientNameOrangeYellow("秋姐妹的赠与信奉丰收之人的宝物"));

        // 落叶：秋风的问候。
        // ★ 材质：海带（KELP）—— 细长下垂的形状与"落叶/枯叶"最接近的现成原版材质。
        // ★★ 两套渐变故意不同：名称金→棕，描述三行金→橙（用户原文如此，见字段注释）。
        // ★ 描述三行【逐字照抄】，第二行自带双引号（用户原文就有），一个字都没改。
        FALLEN_LEAVES = new SlimefunItemStack(
                "TOUHOU_MATERIAL_FALLEN_LEAVES",
                Material.KELP,
                gradientName("落叶", Gradient.GOLD_BROWN),
                "",
                gradientName("萧瑟的秋风，带来了这份来自秋天的问候，", Gradient.GOLD_ORANGE),
                gradientName("\"秋天来临之际，冬天也不远了呢\"", Gradient.GOLD_ORANGE),
                gradientName("似曾相识的获取方式呢", Gradient.GOLD_ORANGE));

INFO_MODESHIFT = new SlimefunItemStack(
                "TOUHOU_INFO_MODESHIFT",
                Material.PURPLE_STAINED_GLASS_PANE,
                "&d模式切换",
                "",
                "&7点击在两种运行模式之间切换");

        // ------------------------------------------------------------------ INFO 组：信息类纸张
        INFO_PLUGIN_MESSAGE = new SlimefunItemStack(
                "TOUHOU_INFO_PLUGIN_MESSAGE",
                Material.PAPER,
                "&f插件消息",
                "",
                "&7version: &f" + pluginVersion(),
                "",
                "&8东方 Project 二次创作（同人）作品",
                "&8详见 &f声明-1 / 声明-2 / 声明-3");

        INFO_DECLARATION_1 = new SlimefunItemStack(
                "TOUHOU_INFO_DECLARATION_1",
                Material.PAPER,
                "&f声明-1",
                "",
                "&8同人性质 · 素材来源与授权",
                "",
                "&7本插件是基于「东方 Project」的二次创作（同人）作品。",
                "",
                "&f一、同人性质",
                "&7本插件为个人创作的东方 Project 二次创作作品，出于爱好制作，",
                "&7免费发布，不以营利为目的。",
                "&7与上海爱丽丝幻乐团（上海アリス幻樂団）及东方 Project 官方",
                "&7无任何隶属、赞助、认可或授权关系。",
                "&7「东方 Project」及其角色、名称、设定的著作权",
                "&7归上海爱丽丝幻乐团（ZUN）所有。",
                "",
                "&f二、素材来源与授权",
                "&7「多方块结构-祭坛」改编自 &b酒石酸菌&7 的《车万女仆模组》，",
                "&7已获得原作者许可使用（含直接使用、借鉴与修改）。",
                "&8原作者：酒石酸菌",
                "&8来源作品：《车万女仆模组》",
                "&8原作链接：https://github.com/TouhouLittleMaid/",
                "&8授权条件：CC BY-NC-SA 协议",
                "&8修改说明：为适应本插件的多方块逻辑作了调整与修改",
                "&8该部分协议：CC BY-NC-SA 4.0（署名—非商业性使用—相同方式共享）",
                "&7除上述部分外的原创代码与内容由本人独立创作，遵循 GPL 协议。",
                "",
                "&8续见 &f声明-2");

        INFO_DECLARATION_2 = new SlimefunItemStack(
                "TOUHOU_INFO_DECLARATION_2",
                Material.PAPER,
                "&f声明-2",
                "",
                "&8非商业 · 相同方式共享 · 免责",
                "",
                "&f三、非商业声明",
                "&7本插件完全免费提供，不包含任何收费内容、内购项目或付费解锁功能。",
                "&7任何人不得将本插件（包括其改编版本）用于商业目的，",
                "&7包括但不限于收费分发、广告变现、商业服务器定制等。",
                "",
                "&f四、相同方式共享",
                "&7源自上述授权素材的改编部分，如需再次发布或再改编，",
                "&7必须继续以 CC BY-NC-SA 4.0（或更高版本）协议发布，",
                "&7并保留本声明中的署名信息与来源链接。",
                "",
                "&f五、免责声明",
                "&7本插件按「现状」提供，作者不对使用本插件所导致的",
                "&7任何直接或间接损失承担责任。请在使用前自行评估风险。",
                "&7若本声明所述内容存在疏漏或侵犯了您的权利，",
                "&7请通过 &bhayekgon@163.com&7 与本人联系，将立即核实并处理。",
                "",
                "&8前承 &f声明-1 &8· 续见 &f声明-3");

        INFO_DECLARATION_3 = new SlimefunItemStack(
                "TOUHOU_INFO_DECLARATION_3",
                Material.PAPER,
                "&f声明-3",
                "",
                "&8其他权利归属 · 致谢",
                "",
                "&f六、其他权利归属",
                "&7Minecraft 及其相关内容的著作权归 Mojang Studios / Microsoft 所有。",
                "&7本插件为第三方非官方扩展，与 Mojang Studios 及 Microsoft",
                "&7无任何隶属或合作关系。",
                "&7本声明不授予任何商标权。未经许可，不得使用「东方 Project」",
                "&7「Minecraft」等名称或标识暗示本插件获得官方认可或授权。",
                "",
                "&f致谢",
                "&7感谢 &b酒石酸菌&7 授权使用「多方块结构-祭坛」。",
                "&7感谢上海爱丽丝幻乐团创造东方 Project。",
                "&7感谢 Minecraft 社区。",
                "",
                "&8前承 &f声明-2");

        INFO_TARTARIC_ACID = new SlimefunItemStack(
                "TOUHOU_INFO_TARTARIC_ACID",
                Material.PAPER,
                "&b酒石酸菌",
                "",
                "&7多方块结构授权者",
                "&8来自其模组项目《车万女仆》中的多方块结构-祭坛",
                "",
                "&7授权条件：CC BY-NC-SA",
                "&8https://github.com/TouhouLittleMaid/");

        INFO_TEAM_SHANGHAI_ALICE = new SlimefunItemStack(
                "TOUHOU_INFO_TEAM_SHANGHAI_ALICE",
                Material.PAPER,
                "&d上海爱丽丝幻乐团",
                "",
                "&7上海爱丽丝幻乐团（上海アリス幻樂団）",
                "&7及东方 Project 相关 IP 实际持有者");

        INFO_NING_MENG = new SlimefunItemStack(
                "TOUHOU_INFO_NING_MENG",
                Material.PAPER,
                "&aNing_Meng__",
                "",
                "&7项目提出者，代码开发者");

        // 1 级物品组 PARTY_ITEM（Flee into Gensokyo）
        // id 规范：TOUHOU_"物品组"_"物品名英文" ⇒ TOUHOU_PARTY_ITEM_FANTASY_SEAL_CONVERGE
        //   「夢想封印」= Fantasy Seal；「集」= -Converge-（与 -Scatter- / -Strike- 并列），
        //   去连字符后即 FANTASY_SEAL_CONVERGE。★ 英文名已由用户核对确认（2026-09-20）。
        //   ⚠ 物品组仍是 PARTY_ITEM，没跟着改组。
        // 描述：由用户指定的原文（一句话，不拆行）。
        //   末尾那几行灰字是"参数放最后"的约定（spec 允许），
        //   其中 "POWER:" 那一行还有功能：FantasySeal 会就地改写它来显示实时电量
        //   （见 FantasySeal#renderChargeLore —— 本道具不再是 Rechargeable，
        //    没有 Slimefun 自带的那行电力显示，所以这一行必须留着）。
        // 材质：旗帜图案 · 花朵盾徽（FLOWER_BANNER_PATTERN），带附魔光效。
        //   ★ 换材质历史：FISHING_ROD（仿钓鱼竿的握持手感）→ PAPER（纸）→ 现在这个。
        //   ★ 注意「附魔光效」不是材质自带的，而是靠 setup() 末尾那段
        //     addUnsafeEnchantment + HIDE_ENCHANTS 做出来的 —— 换材质不会丢光效，
        //     但**别把那段发光块删了**，删了就只是一张普通图案。
        FANTASY_SEAL = new SlimefunItemStack(
                "TOUHOU_PARTY_ITEM_FANTASY_SEAL_CONVERGE",
                Material.FLOWER_BANNER_PATTERN,
                "&d梦想封印 集",
                "",
                "&7灵梦的符卡梦想封印之一，据说没有几个人能完整的抗下一发",
                "",
                "&8POWER: &7手持时自动充能",
                "&8充能 &f5 POWER / 2 秒&8（身边 &f4 格&8内要有 POWER 网络）",
                "&8单次发射消耗 &f1 POWER&8，上限 &f40 POWER",
                "&8每发之后冷却 &f1.5 秒&8，弹幕追踪 &f120 格");

        // 1 级物品组 PARTY_ITEM（Flee into Gensokyo）的第二件符卡。
        // id 规范：TOUHOU_"物品组"_"物品名英文" ⇒ TOUHOU_PARTY_ITEM_MURDEROUS_LILY
        //   「杀意的百合」= 纯狐（Junko）的符卡「殺意の百合」，英文取 Murderous Lily。
        // 描述：★ 用户给定的原文，【逐字】照抄、不拆行、不改写。
        //   末尾几行灰字是"参数放最后"的约定（spec 允许）；其中 "POWER:" 那一行有功能：
        //   MurderousLily 会就地改写它来显示实时电量（见 PartyItem#renderChargeLore）。
        // 材质：与梦想封印 集【完全一样】（FLOWER_BANNER_PATTERN + 附魔光效），
        //   光效同样靠 setup() 末尾那段 addUnsafeEnchantment + HIDE_ENCHANTS 做出来。
        MURDEROUS_LILY = new SlimefunItemStack(
                "TOUHOU_PARTY_ITEM_MURDEROUS_LILY",
                Material.FLOWER_BANNER_PATTERN,
                "&d杀意的百合",
                "",
                "&7她纯粹愤怒具象化的弹幕，或者说，她就是愤怒本身。",
                "&7嫦娥啊，你看到了吗。",
                "",
                "&8POWER: &7手持时自动充能（当前与梦想封印 集同款口径）",
                "&8充能 &f5 POWER / 2 秒&8（身边 &f4 格&8内要有 POWER 网络）",
                "&8单次发射消耗 &f1 POWER&8，上限 &f40 POWER",
                "&8每发之后冷却 &f1.5 秒&8，箭矢至多 &f120 格 / 12 秒");

        // 2 级物品组 COMPLEX_MACHINE（多方块大型机器）的构件。
        // 结构层图由用户指定，这里只提供"积木"。
        REACTOR_FRAME = new SlimefunItemStack(
                "TOUHOU_COMPLEX_MACHINE_REACTOR_FRAME",
                Material.ANCIENT_DEBRIS,
                "&6旧地狱-反应堆框架",
                "",
                "&7反应堆的主体骨架",
                "&8多方块构件");

        REACTOR_SHIELD = new SlimefunItemStack(
                "TOUHOU_COMPLEX_MACHINE_REACTOR_SHIELD",
                Material.TINTED_GLASS,
                "&b旧地狱-反应堆保护罩",
                "",
                "&7隔绝核融合辐射的外壳",
                "&8多方块构件");

        REACTOR_STABILIZER = new SlimefunItemStack(
                "TOUHOU_COMPLEX_MACHINE_REACTOR_STABILIZER",
                Material.GLOWSTONE,
                "&e旧地狱-反应堆稳定器",
                "",
                "&7约束核融合反应的部件",
                "&8多方块构件");

        REACTOR_BASE = new SlimefunItemStack(
                "TOUHOU_COMPLEX_MACHINE_REACTOR_BASE",
                Material.RED_NETHER_BRICKS,
                "&c旧地狱-反应堆基座",
                "",
                "&7承载整个反应堆的底座",
                "&8多方块构件");

        REACTOR_INPUT_PORT = new SlimefunItemStack(
                "TOUHOU_COMPLEX_MACHINE_REACTOR_INPUT_PORT",
                Material.RED_STAINED_GLASS,
                "&c旧地狱-反应堆输入接口",
                "",
                "&7可以替代&f保护罩&7用于搭建反应堆",
                "&7一台反应堆&f最多一个输入接口",
                "",
                "&7每 &f5 秒&7把本接口&c输入槽&7的物品",
                "&7搬进&6核心的输入槽",
                "&8只负责进料，不搬运产物",
                "",
                "&8直接右键：映射核心 GUI",
                "&8Shift+右键：打开接口自己的界面");

        REACTOR_OUTPUT_PORT = new SlimefunItemStack(
                "TOUHOU_COMPLEX_MACHINE_REACTOR_OUTPUT_PORT",
                Material.BLUE_STAINED_GLASS,
                "&9旧地狱-反应堆输出接口",
                "",
                "&7可以替代&f保护罩&7用于搭建反应堆",
                "&7一台反应堆&f最多一个输出接口",
                "",
                "&7每 &f5 秒&7把&6核心输出槽&7的物品",
                "&7搬回本接口的&9输出槽&7，供物流抽走",
                "&8只负责出料，不输送燃料",
                "",
                "&8直接右键：映射核心 GUI",
                "&8Shift+右键：打开接口自己的界面");

        // ------------------------------------------------------------------ 祭祀/信仰系多方块
        // 结构层图已定（docs\saizenbako-layers.yml）：9×6×9，6 根木桩 + 核心自己。
        SHRINE_POST = new SlimefunItemStack(
                "TOUHOU_COMPLEX_MACHINE_SHRINE_POST",
                Material.DARK_OAK_LOG,
                "&6神社的木桩",
                "",
                "&7立在神社境内的木桩",
                "&7是搭建&6博丽神社&7结构的基础构件",
                "",
                "&7界面：&f9 格&7（信息 / 输入输出 / 核心位置）",
                "&8第 5 格是混合型输入输出端，也是本桩的投料口",
                "",
                "&8多方块构件 · 不耗电、不存电",
                "&8激活后核心会给本桩编号（先 +X 后 +Z）");

        SAIZENBAKO = new SlimefunItemStack(
                "TOUHOU_COMPLEX_MACHINE_SAIZENBAKO",
                Material.LOOM,
                "&6赛钱箱",
                "",
                "&7塞进五元硬币，许下一个愿望",
                "&7香火钱会化作&ePOWER&7储存在这里",
                "",
                "&7容量：&f5 POWER",
                "&8把 POWER 方块贴到它身上即可并网",
                "",
                "&8多方块核心 · 使用自研 POWER 能源",
                "&8点信息格激活：6 个预留槽同时匹配配方才产出");

        // 附魔光效：给物品挂一个"没有任何实际效果"的附魔，再用 HIDE_ENCHANTS 把附魔行藏掉，
        //   于是只剩图标外圈那层光晕。
        //   ★ 与材质无关：任何材质都能这样发光，光效完全来自这里。
        //   ★ 两件符卡共用同一段做法（材质都是 FLOWER_BANNER_PATTERN）——
        //     杀意的百合的光效必须与梦想封印 集一模一样。
        FANTASY_SEAL.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1);
        ItemMeta sealMeta = FANTASY_SEAL.getItemMeta();
        if (sealMeta != null) {
            sealMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            FANTASY_SEAL.setItemMeta(sealMeta);

        // 炙热的灰烬：附魔光效 + 火焰附加 15 级。
        //   ★ 这里刻意【不】加 HIDE_ENCHANTS —— 用户要的是"火焰附加15级"这条能看见，
        //     藏掉附魔行就只剩光晕、看不到那条了。
        //   ★ "附魔光效"本身就由附魔产生（挂任何附魔物品都会发光），所以一条
        //     FIRE_ASPECT(15) 同时满足「附魔光效」与「火焰附加15级」两个要求，
        //     不需要再额外挂一个无用的假附魔。
        //   ★ 15 级远超原版上限（火焰附加自然上限是 2），必须用 addUnsafeEnchantment。
        //   ★ 火药不是武器，这个附魔不会有任何实际战斗效果，纯粹是显示与光效。
        BLAZING_ASH.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 15);
        }

        MURDEROUS_LILY.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1);
        ItemMeta lilyMeta = MURDEROUS_LILY.getItemMeta();
        if (lilyMeta != null) {
            lilyMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            MURDEROUS_LILY.setItemMeta(lilyMeta);
        }

        // 另一个世界的回响：附魔光效（同 FANTASY_SEAL 那种"挂无用附魔 + HIDE_ENCHANTS"）。
        //   ★ 与炙热的灰烬【不同】：那边故意不隐藏，因为用户要看"火焰附加15级"这条；
        //     本物品没有这个要求，所以按光效惯例把附魔行藏掉，只留光晕。
        ECHO_OF_ANOTHER_WORLD.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1);
        ItemMeta echoMeta = ECHO_OF_ANOTHER_WORLD.getItemMeta();
        if (echoMeta != null) {
            echoMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            ECHO_OF_ANOTHER_WORLD.setItemMeta(echoMeta);
        }

        // 丰收之时：附魔光效（与上面几件同一套"挂无用附魔 + HIDE_ENCHANTS"的做法）。
        //   ★ 它没有"要看附魔行"的要求，所以照光效惯例把附魔行藏掉，只留光晕。
        HARVEST_TIME.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1);
        ItemMeta harvestMeta = HARVEST_TIME.getItemMeta();
        if (harvestMeta != null) {
            harvestMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            HARVEST_TIME.setItemMeta(harvestMeta);
        }

        // 落叶：附魔光效（同一套"挂无用附魔 + HIDE_ENCHANTS"）。
        //   ★ 它没有"要看附魔行"的要求，所以照光效惯例把附魔行藏掉，只留光晕。
        FALLEN_LEAVES.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1);
        ItemMeta leavesMeta = FALLEN_LEAVES.getItemMeta();
        if (leavesMeta != null) {
            leavesMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            FALLEN_LEAVES.setItemMeta(leavesMeta);
        }

        // ------------------------------------------------------------------ POWER（1 级组）
        // 头贴图直接引用原生物品的贴图常量（HeadTexture 是公开枚举）。
        POWER_INTEGRATED_CORE = new SlimefunItemStack(
                "TOUHOU_POWER_POWER_INTEGRATED_CORE",
                HeadTexture.ENERGY_REGULATOR,
                "&cPOWER集成核心",
                "",
                "&7POWER 网络的锚点与储能点",
                "&7上方显示网络统计的悬浮文字",
                "",
                "&8不需要它也能组网（邻接即连通）",
                "&8与原生电网的联动当前已中断");

        POWER_REPEATER = new SlimefunItemStack(
                "TOUHOU_POWER_POWER_REPEATER",
                HeadTexture.CARGO_CONNECTOR_NODE,
                "&cPOWER中继器",
                "",
                "&7把相邻的 POWER 方块串起来",
                "&7并额外连接半径内的 POWER 方块",
                "&8半径是立方体范围（含斜向），不必沿直线摆",
                "",
                "&8本身不存电");

        POWER_STORAGE_UNIT = new SlimefunItemStack(
                "TOUHOU_POWER_POWER_STORAGE_UNIT",
                Material.RED_CONCRETE,
                "&cPOWER存储单元",
                "",
                "&7POWER 网络的储能单元",
                "&7电量按容量比例在网络内自动均衡",
                "",
                "&8long 精度，不会像原生电容那样溢出");

        // 幻梦捕捉器：POWER 体系的第一台产能设备（原创机器）。
        // ★ 英文名 Dreamcatcher = 捕梦网；名称与 id 都按本项目规范：
        //   TOUHOU_"物品组POWER"_DREAMCATCHER，全大写。
        POWER_DREAMCATCHER = new SlimefunItemStack(
                "TOUHOU_POWER_DREAMCATCHER",
                Material.TARGET,
                "&c幻梦捕捉器",
                "",
                "&7把梦收进标靶，再榨成&cPOWER&7。",
                "&7它只认四个水平方向上的&f床&7 ——",
                "&7谁睡得越沉，谁就产得越快。",
                "",
                "&7每面各紧贴一张床 ⇒ 效率 &f100%&7，最多四面叠满",
                "&7一张床：&f8 秒&7 产 &f1 POWER",
                "&7四张床：&f2 秒&7 产 &f1 POWER",
                "&7四周没有床：&f不产出",
                "",
                "&8自身缓冲 &f15 POWER&8，产出的电优先并入网络",
                "&8（先送给储能点，实在没人接才留在自己这里）",
                "&8POWER 体系的第一台产能设备");

        // POWER供给单元：把网络里的电无线送给附近玩家手持的符卡。
        // ★ 材质是海晶灯（按需求）；它是 POWER 一族里第一台"对外供电"的机器，
        //   所以描述里必须写清"给谁充、多大范围、多久一次"——它没有 GUI。
        POWER_SUPPLY_UNIT = new SlimefunItemStack(
                "TOUHOU_POWER_POWER_SUPPLY_UNIT",
                Material.SEA_LANTERN,
                "&cPOWER供给单元",
                "",
                "&7把 POWER 网络里的电无线送给",
                "&7附近玩家手持的符卡。",
                "",
                "&7半径 &f4 格&7（立方体范围，含斜向与上下）",
                "&7每 &f2 秒&7 为范围内每位玩家充 &f5 POWER",
                "&7主手与副手都算，各自独立充能",
                "",
                "&8自身缓冲 &f5 POWER&8，缓冲不够时从网络现取",
                "&8机器与网络都没电时静默待机，不刷提示",
                "&8没有界面：状态看 &f/touhou supply <x> <y> <z>");

        // ------------------------------------------------------------------ 标签登记
        // ★ 为什么标签登记在这个"物品模板"层、而不是像最初那样放在 AddSlimefunItems 里：
        //   紧跟着的「建造所需材料」要把层图里的 #touhou:reactor_shell 翻成可读名字
        //   （见 StructureMaterials.nameOf 的第 1 条判据），而那段 lore 就是在本方法末尾拼的。
        //   原先登记在 AddSlimefunItems.setup()，那一步排在 AddItems.setup()【之后】——
        //   等它登记完物品描述早就定型了，实测结果就是物品描述里赫然写着
        //   「#touhou:reactor_shell* ×36」，而 /touhou guide 现算出来的是
        //   「旧地狱-反应堆保护罩 ×36」，同一件事两个说法。
        //   标签本就是"哪些物品属于同一族"的模板层知识，放在这里也更顺 ——
        //   反正成员写的就是模板自己的 id，不需要先 register 成 SlimefunItem。
        //
        //   ★ 结构层图里写 `S: "#touhou:reactor_shell"`，这些方块都能满足那一格的要求 ——
        //     也就是"接口可以替代保护罩搭建"。
        ItemTags.tag("touhou:reactor_shell",
                REACTOR_SHIELD.getItemId(),
                REACTOR_INPUT_PORT.getItemId(),
                REACTOR_OUTPUT_PORT.getItemId());
        //   ★★ 但【投影图标】必须挑定一个：层图里 'S' 那一格占了整座反应堆的一大半，
        //     画出来的应该是用户明确要求的「旧地狱-反应堆保护罩」，而不是"成员里恰好排第一的那个"
        //     （登记顺序只是注册顺序的副产品，改一行注册就会让图标悄悄换成接口）。
        //     所以显式登记代表件 —— 见 ItemTags.setDefaultDisplay 的注释。
        //     它同时也是上面材料清单里那一格显示成什么名字的依据。
        ItemTags.setDefaultDisplay("touhou:reactor_shell", REACTOR_SHIELD.getItemId());

        // ------------------------------------------------------------------ 两个多方块核心：建造材料 lore
        // ★ 为什么必须放在 setup() 的【最后】：
        //   材料名要按 part id 反查物品显示名，而 legend 里能出现的构件模板
        //   （框架/保护罩/基座/稳定器/木桩…）必须都已经 new 出来才查得到
        //   —— 这一点由 StructureMaterials 的模板表兜住（见那个类的注释）。
        //
        // ★★ 数据源是 AddonConfig 的【层图数据】，绝不是 ReactorManager.structure() /
        //   SaizenbakoStructure.get()：本方法跑在 onEnable 的第二步，那两个结构实例
        //   要等 onEnable 后半段才被创建，这里取只会拿到 null（会直接毁掉插件启用）。
        //   而 AddonConfig 在 saveDefaultConfig() 之后就已经把层图与 legend 填好了。
        applyStructureLore(UTSUHO_REACTOR_CORE,
                AddonConfig.get().structureLayers, AddonConfig.get().structureLegend);
        applyStructureLore(SAIZENBAKO,
                AddonConfig.get().saizenLayers, AddonConfig.get().saizenLegend);
    }

    /**
     * 往核心物品的 lore 末尾追加「建造所需材料」清单。
     *
     * <p>清单内容由 {@link StructureMaterials} 从层图现算（合并计数 + 数量降序 +
     * {@code #标签}/Material/粘液 id 三路翻名字），这里只负责"接在原有 lore 后面"。
     *
     * <p>★ 颜色代码要在这里翻：{@code ItemMeta#setLore} 不认 {@code &}，
     * 直接塞进去玩家看到的是字面的 {@code &7- &f…}。
     * （物品名与基础 lore 由 {@link SlimefunItemStack} 的构造器负责翻译，所以那边写 {@code &} 没事。）
     *
     * <p>★ {@link ItemMeta#setLore} 必须<b>整表替换</b>，所以先 get 再 append 再 set；
     * 清单为空（层图/legend 缺失）时原样返回，绝不清空已有 lore。
     */
    private static void applyStructureLore(SlimefunItemStack core,
                                           List<List<String>> layers,
                                           Map<Character, String> legend) {
        if (core == null || !AddonConfig.get().guideMaterialLore) {
            return;
        }
        List<String> extra = StructureMaterials.lore(
                layers, legend, AddonConfig.get().guideMaterialKinds, null);
        if (extra.isEmpty()) {
            return;
        }
        ItemMeta meta = core.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = meta.getLore() == null
                ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        for (String line : extra) {
            lore.add(ReactorManager.color(line));
        }
        meta.setLore(lore);
        core.setItemMeta(meta);
    }

    /**
     * 把一段文字逐字符染成<b>粉 → 白</b>的左到右渐变（莉莉白用的那套配色）。
     *
     * <p>配色与算法分离：本方法只是 {@link #gradientName(String, Gradient)} 的一个
     * 便捷入口，真正的插值在那边。
     *
     * @param text 要染的文字（纯文本，不要带 {@code §}/{@code &} 颜色代码）
     * @return 每字符都带 {@code §x§R§R§G§G§B§B} 前缀的字符串
     */
    public static String gradientName(String text) {
        return gradientName(text, Gradient.PINK_WHITE);
    }

    /**
     * 把一段文字逐字符染成<b>橙 → 黄</b>的左到右渐变（「丰收之时」用的配色）。
     *
     * @param text 要染的文字（纯文本，不要带 {@code §}/{@code &} 颜色代码）
     * @return 每字符都带 {@code §x§R§R§G§G§B§B} 前缀的字符串
     */
    public static String gradientNameOrangeYellow(String text) {
        return gradientName(text, Gradient.ORANGE_YELLOW);
    }

    /**
     * 把一段文字逐字符染成渐变，返回<b>已翻好的</b>
     * {@code §x§R§R§G§G§B§B} 序列串。
     *
     * <h2>为什么不能用 &amp; 写法</h2>
     * Slimefun 的颜色工具 {@code ChatColors#color} 内部只调用了
     * {@code ChatColor.translateAlternateColorCodes('&', s)}，而它<b>不认</b>
     * {@code &x&f&f&b&3&d&9} 这种十六进制序列 —— {@code 'x'} 不是合法颜色字符，
     * 会被原样留下，玩家看到的就是字面的 {@code &x&f&f...}。
     * 这里改用 {@code net.md_5.bungee.api.ChatColor#of(String)}（Slimefun 自带 bungee-chat）
     * 生成 {@code §x§R§R§G§G§B§B}，再把整串直接交给
     * {@link SlimefunItemStack#SlimefunItemStack(String, String, String, String...)}。
     *
     * <p>★ 那条构造器对名字/描述仍会执行一次
     * {@code translateAlternateColorCodes('&', …)}，但它只翻 {@code '&'} 开头的两位序列，
     * <b>不会动已有的 {@code §}</b> —— 所以上面那串带 {@code §x} 的渐变能原样穿过去。
     * 这一条是实测过的（证据见 {@code /touhou lilywhite} 与 {@code /touhou harvest} 打印的 JSON）。
     *
     * <h2>插值口径</h2>
     * 第 {@code i} 个字符的比例是 {@code t = i / (len - 1)}（首字符=起点色，末字符=终点色），
     * 在每个通道上做线性插值。长度只有 1 时直接取起点色，不做除零。
     *
     * <p>★ 空格也照常染色：多几个无用字符对客户端无害，但"每个可见字符都能均匀分到
     * 自己的色号"这件事变得没有例外，验证命令的输出也更好读。
     *
     * @param text    要染的文字（纯文本，不要带 {@code §}/{@code &} 颜色代码）
     * @param palette 用哪套起止色
     * @return 每字符都带 {@code §x§R§R§G§G§B§B} 前缀的字符串
     */
    public static String gradientName(String text, Gradient palette) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int length = text.length();
        StringBuilder out = new StringBuilder(length * 14);
        for (int i = 0; i < length; i++) {
            double t = length == 1 ? 0.0 : (double) i / (double) (length - 1);
            int rgb = lerpRgb(palette.start, palette.end, t);
            // ★ ChatColor.of("#rrggbb") 造出来的 toString() 就是 §x§R§R§G§G§B§B 七个字符，
            //   直接拼进去即可（不经过 Slimefun 的 & 翻译，也就不会被它弄坏）。
            out.append(net.md_5.bungee.api.ChatColor.of(String.format("#%06X", rgb)))
                    .append(text.charAt(i));
        }
        return out.toString();
    }

    /** 两个 RGB 整数之间按比例 t 做线性插值（t=0 取 from，t=1 取 to）。 */
    private static int lerpRgb(int from, int to, double t) {
        int fr = (from >> 16) & 0xFF;
        int fg = (from >> 8) & 0xFF;
        int fb = from & 0xFF;
        int tr = (to >> 16) & 0xFF;
        int tg = (to >> 8) & 0xFF;
        int tb = to & 0xFF;
        int r = (int) Math.round(fr + (tr - fr) * t);
        int g = (int) Math.round(fg + (tg - fg) * t);
        int b = (int) Math.round(fb + (tb - fb) * t);
        return (r << 16) | (g << 8) | b;
    }

    /**
     * 插件版本号，动态取自 {@code plugin.yml}。
     *
     * <p>刻意不写死：版本号只在 {@code plugin.yml} 维护一处，
     * 「插件消息」里的 version 就不会出现"代码写着 1.0.0、实际发的是 1.1.0"的漂移。
     */
    @SuppressWarnings("deprecation")
    private static String pluginVersion() {
        org.bukkit.plugin.Plugin self = org.bukkit.Bukkit.getPluginManager().getPlugin("Touhou");
        if (self != null) {
            String v = self.getDescription().getVersion();
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return "1.0.0";
    }
}
