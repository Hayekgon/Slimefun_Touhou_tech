package com.example.touhou.core;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.utils.HeadTexture;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.meta.ItemMeta;

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

    /** 顶级产能机器的产物：逻辑奇点。 */
    public static SlimefunItemStack LOGIC_SINGULARITY;

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

    /** Flee into Gensokyo：博丽的御币（充能式 360° 追踪弹幕发射器）。 */
    public static SlimefunItemStack HAKUREI_GOHEI;

    // ------------------------------------------------------------------ POWER 能源系统
    // 归属 1 级组 POWER（touhou_power）。
    // 材质按你的要求分别引用原生物品：能源调节器头 / 货运节点头 / 红色混凝土。

    /** POWER集成核心 —— 网络锚点 + 原生电网桥。 */
    public static SlimefunItemStack POWER_INTEGRATED_CORE;

    /** POWER中继器 —— 导体 + 半径跳接。 */
    public static SlimefunItemStack POWER_REPEATER;

    /** POWER存储单元 —— long 精度储能。 */
    public static SlimefunItemStack POWER_STORAGE_UNIT;

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
                "&7把&e桶装原油&7烧成电力与&d逻辑奇点",
                "",
                "&8多方块核心 · 需要搭建完整结构",
                "&8结构不完整时会进入 &c未激活&8 状态");

        LOGIC_SINGULARITY = new SlimefunItemStack(
                "TOUHOU_MATERIAL_LOGIC_SINGULARITY",
                Material.ENDER_EYE,
                "&d逻辑奇点",
                "",
                "&7被压缩到极致的因果残渣",
                "&8来自旧地狱反应堆的副产物");

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
        // id 规范：TOUHOU_"物品组"_"物品名英文" ⇒ TOUHOU_PARTY_ITEM_HAKUREI_GOHEI
        HAKUREI_GOHEI = new SlimefunItemStack(
                "TOUHOU_PARTY_ITEM_HAKUREI_GOHEI",
                Material.FISHING_ROD,
                "&d博丽的御币",
                "",
                "&7以灵力为引，向四周撒出&d36&7发追踪弹幕",
                "&7每发间隔 &d10°&7，绕自身一整圈展开",
                "&7其中一发正对视线方向",
                "",
                "&8充能道具 · 单次发射消耗 &c640 J&8 电力",
                "&8弹幕命中会清空目标无敌帧");

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

        // 纯发光用：钓鱼竿上的无限附魔没有实际效果
        HAKUREI_GOHEI.addUnsafeEnchantment(Enchantment.ARROW_INFINITE, 1);
        ItemMeta goheiMeta = HAKUREI_GOHEI.getItemMeta();
        if (goheiMeta != null) {
            goheiMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            HAKUREI_GOHEI.setItemMeta(goheiMeta);
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
