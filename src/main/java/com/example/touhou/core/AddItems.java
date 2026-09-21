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

    /** 多方块核心：旧地狱-灵乌路空反应堆。 */
    public static SlimefunItemStack UTSUHO_REACTOR_CORE;

    /** 顶级产能机器的产物：炙热的灰烬。 */
    public static SlimefunItemStack BLAZING_ASH;

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
