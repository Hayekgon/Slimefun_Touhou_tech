package com.example.touhou.core;

import com.example.touhou.Touhou;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * 附属数值配置（全部可从 config.yml 改，不在代码里写死）。
 *
 * <p>为什么要把 Spec 里的每个数字都外置：这份 spec 是"暂定"的，
 * 数值改一次就要重编译一次太难受；而且这些数字（发电量、阈值、进程时长）
 * 恰好是测试时最需要来回调的。
 */
public final class AddonConfig {

    // ---- 反应堆数值 ----
    /** 电量存储上限（Slimefun 本体是 int，所以默认取 2^31-1）。 */
    public int energyCapacity = Integer.MAX_VALUE;
    /** 发电功率（J / Slimefun tick）。 */
    public long energyProduction = 240_000_000L;
    /** 一个原油桶的进程时长（Slimefun tick）。20 tick = 1 秒，所以 600 = 30 秒。 */
    public int processTicks = 600;
    /**
     * 产物模式的<b>工作效率倍率</b>（相对发电模式）。
     *
     * <p>5.0 = 500%：同一个进程在产物模式下推进速度是发电模式的 5 倍
     * （30 秒的燃料 6 秒烧完）。实现方式是每 tick 多推进几 tick 进度，
     * 所以<b>中途切换模式</b>也能立刻生效，不用重建进程。
     */
    public double productModeSpeedMultiplier = 5.0D;
    /**
     * 产物模式的<b>发电倍率</b>（相对发电模式）。0.1 = 只发原本的 10%。
     *
     * <p>spec：产物模式"直接使发电量变为原本的 10%"，代价换来 500% 的工作效率。
     */
    public double productModeEnergyRate = 0.1D;
    /** 发电模式的暂停阈值：储电 > 该值时暂停进程（保留进度）。 */
    public long modeThreshold = 100_000_000_00L;

    // ---- 多方块投影（照搬 LogiTech 的全息机制，见 MultiBlockProjection） ----
    /**
     * 投影总开关。
     *
     * <p>为 {@code false} 时 GUI 的投影按钮点下去只会提示"已被配置关闭"，
     * 不会生成任何实体 —— 用于"这台服务器不想让玩家刷实体"的场合。
     */
    public boolean projectionEnabled = true;

    /**
     * 单次投影的<b>构件的上限</b>（格）。
     *
     * <p>★ 为什么必须有这个上限：投影是"每格一个 {@code ItemDisplay} 实体"，
     * 而实体是有成本的。LogiTech 的机制本身没有任何上限 —— 结构多大就画多大
     * （它的超新星模拟器是 7×9×7 = 186 个零件，一开全息就是 187 个实体）。
     * 本项目保留这个能力，但加一道闸：超过上限就<b>整组拒绝</b>
     * （不是画一半），原因直接回显给玩家。
     *
     * <p>默认 256：两种内置结构（反应堆 98 格、赛钱箱 48 格）都远在闸内。
     */
    public int projectionMaxParts = 256;

    /** 清理孤儿投影时的默认扫描半径（格）。 */
    public int projectionCleanRadius = 32;

    // ---- 结构检测 ----
    /**
     * 结构检测的层图：外层是"层"（沿 y 从下往上），内层是"行"（沿 z），行内字符沿 x。
     *
     * <p>★ 用 {@code List<List<String>>} 而不是扁平列表：YAML 的序列里表达不出"层边界"
     * （{@code - ""} 会被当成一条空字符串而不是分隔符），扁平写法只能被误解析成"1 层 N 行"。
     * 实测踩过：层图被当成 1 层 9 行 → 结构检查一直回退到 ALWAYS_OK。
     *
     * <p>★ 供电说明：能源调节器<b>远程连接半径 7 格</b>，所以结构不必为它留洞。
     * 只要核心 7 格内有能源调节器（或电容）即可，否则本体电力网络不会 tick 这台发电机。
     */
    public List<List<String>> structureLayers = List.of(
            // 5×5×5 反应堆容器（按用户实建结构扫描还原）
            List.of("FFFFF", "FBBBF", "FBBBF", "FBBBF", "FFFFF"),
            List.of("FSSSF", "S___S", "S___S", "S___S", "FSSSF"),
            List.of("FSSSF", "S___S", "S_C_S", "S___S", "FSSSF"),
            List.of("FSSSF", "S___S", "S___S", "S___S", "FSSSF"),
            List.of("FFFFF", "FTTTF", "FTTTF", "FTTTF", "FFFFF"));
    /**
     * 层图字符含义表：字符 -> part id、Material 名、{@code nu}（必须空气）、或 {@code #标签}。
     *
     * <p>值是"该格可以是什么"：
     * <ul>
     *   <li>粘液方块 → 写它的 sfId；</li>
     *   <li>原版方块 → 写 Material 名（例如 {@code IRON_BLOCK}）；</li>
     *   <li>{@code nu} → <b>必须是空气</b>（{@link ReactorStructure#partIdAt} 对空气返回的正是它）；</li>
     *   <li>{@code #标签} → <b>按标签判定</b>：只要该格的方块属于这个标签就算对。
     *       标签在代码里用 {@link ItemTags} 登记（见 {@code AddSlimefunItems}）。</li>
     * </ul>
     *
     * <p>★ 为什么要标签：反应堆 IO 接口要能<b>替代保护罩</b>参与搭建。
     * 用"一对多 id 列表"也行，但标签更贴合直觉、也更好扩展
     * （以后再加别的外壳变体，只要挂上同一个标签即可）。
     * 保护罩与 IO 接口现在共用标签 {@code touhou:reactor_shell}。
     *
     * <p>★ 以前这里写死成 {@code legendFrame}/{@code legendGlass} 两个字段（只有 F/G 两种字符），
     * 结构一复杂就不够用了；现在整表从 config 的 {@code structure.legend} 直接读。
     *
     * <p>★★ legend 的<b>键不能是 {@code '.'}</b>：Bukkit 的 YAML 把 {@code '.'} 当路径分隔符，
     * 键写成 {@code "."} 时 {@code getKeys(false)} 根本读不到它 —— 于是层图里的空气字符
     * 变成"未登记字符"，结构检查直接抛异常回退成 ALWAYS_OK（实测踩过）。
     * 所以空气字符用的是下划线 {@code _}。
     */
    public java.util.Map<Character, String> structureLegend = new java.util.LinkedHashMap<>(
            java.util.Map.of(
                    'F', "TOUHOU_COMPLEX_MACHINE_REACTOR_FRAME",
                    'S', "#touhou:reactor_shell",
                    'T', "TOUHOU_COMPLEX_MACHINE_REACTOR_STABILIZER",
                    'B', "TOUHOU_COMPLEX_MACHINE_REACTOR_BASE",
                    '_', "nu"));
    /**
     * "每一类最多出现一个"的 part id 集合（空集 = 不限制）。
     *
     * <p>IO 接口拆成了输入接口与输出接口两个物品，它们可以<b>同时</b>出现在一座反应堆里
     * （一个管进、一个管出），但<b>各自</b>最多一个 —— 多了说不清 Cargo 该认哪个口。
     */
    public java.util.Set<String> structureUniqueParts = new java.util.LinkedHashSet<>(
            java.util.List.of("TOUHOU_COMPLEX_MACHINE_REACTOR_INPUT_PORT",
                    "TOUHOU_COMPLEX_MACHINE_REACTOR_OUTPUT_PORT"));
    /** 结构不完整时是否中断正在进行的燃料进程。 */
    public boolean abortProcessOnBroken = true;

    // ---- 赛钱箱（多方块核心）----
    /**
     * 赛钱箱核心的 part id —— 层图里用字符 {@code C} 表示它自己那一格。
     *
     * <p>写成常量而不是散落的字面量：{@link SaizenbakoStructure} 要用它判"这一格确实是核心"，
     * 而 {@link #saizenLegend} 里<b>不能</b>登记 {@code C}（那是保留字符）。
     */
    public static final String SAIZEN_CORE_ID = "TOUHOU_COMPLEX_MACHINE_SAIZENBAKO";
    /** 木桩的 part id —— 层图里的 {@code P}，也是"6 个预留槽喂给谁"的答案。 */
    public static final String SAIZEN_POST_ID = "TOUHOU_COMPLEX_MACHINE_SHRINE_POST";

    /**
     * 赛钱箱结构的层图 —— <b>逐格抄自 {@code docs/saizenbako-layers.yml}</b>。
     *
     * <p>尺寸 9(x) × 6(y) × 9(z)，原点 = 角 1（层号 0 = 最下层）。
     * 与反应堆那份层图唯一的写法差别：<b>核心 {@code C} 不在层图正中</b> ——
     * 它落在第 0 层第 4 行第 8 列（也就是 x 最右一列、z 居中）。
     * 所以 {@link SaizenbakoStructure} 构造 {@link LayeredReactorStructure} 时
     * 打开了"允许核心偏心"的开关（详见那个类的类注释）。
     *
     * <p>★ 这份数据是权威的：它由游戏内区域扫描报告机械转换而来，不要"顺手改一改"。
     *   要改结构请改 config.yml 的 {@code saizenbako.structure.layers}，并同步改这里。
     */
    public List<List<String>> saizenLayers = List.of(
            // 第 1 层（相对核心 y=0）：核心在这一层的最右一列
            List.of("..O.L.O..", ".........", "R.......O", ".........", "........C",
                    ".........", "R.......O", ".........", "..O.L.O.."),
            // 第 2 层（y=+1）
            List.of("..O...O..", ".........", "R.......O", ".........", ".........",
                    ".........", "R.......O", ".........", "..O...O.."),
            // 第 3 层（y=+2）：6 根木桩全在这一层
            List.of("..P...P..", "L........", "R.......P", ".........", ".........",
                    ".........", "R.......P", "L........", "..P...P.."),
            // 第 4 层（y=+3）
            List.of("R........", "R........", "R........", "R........", "R........",
                    "R........", "R........", "R........", "R........"),
            // 第 5 层（y=+4）
            List.of(".........", ".........", "R........", ".........", ".........",
                    ".........", "R........", ".........", "........."),
            // 第 6 层（y=+5）
            List.of("R........", "R........", "R........", "R........", "R........",
                    "R........", "R........", "R........", "R........"));

    /**
     * 赛钱箱层图的字符含义表（格式与 {@link #structureLegend} 完全一致）。
     *
     * <p>{@code _} = 必须是空气（{@link ReactorStructure#partIdAt} 对空气返回的正是 {@code nu}）；
     * 其余都是原版方块名或粘液 id。<b>刻意不登记 {@code C}</b>：它是保留字符，
     * 代表核心自己那一格，不需要（也不该）作为构件检测。
     */
    public java.util.Map<Character, String> saizenLegend = new java.util.LinkedHashMap<>(
            java.util.Map.of(
                    '_', "nu",
                    'R', "RED_WOOL",
                    'L', "LANTERN",
                    'O', "OAK_LOG",
                    'P', SAIZEN_POST_ID));

    /**
     * 把层图里的空气字符统一成 {@code _}。
     *
     * <p>★★ 为什么需要这一步（真实踩点）：扫描报告 {@code docs/saizenbako-layers.yml} 里
     * 空气格画的是 {@code .}（人眼友好），而它的 legend 声明的是 {@code "_": nu}
     * （下划线才是能用的那个键）—— 两份东西对不上。于是照抄层图后
     * {@link LayeredReactorStructure} 会在构造期直接抛
     * "用了未登记的字符 '.'"，<b>表现为插件启用失败</b>。
     *
     * <p>为什么不能干脆在 legend 里登记 {@code "."}：Bukkit 的 YAML 把 {@code .}
     * 当路径分隔符，键写成 {@code "."} 时 {@code getKeys(false)} 根本读不到它
     * （本工程反应堆那边已经踩过一次，见 {@link #structureLegend} 的注释）。
     *
     * <p>所以做法是：<b>层图保持与扫描报告逐字一致</b>（方便对照 diff），
     * 在读取时把 {@code .} 翻成 {@code _}。转换只在这一处发生。
     */
    public static List<List<String>> normalizeAirChar(List<List<String>> layers) {
        if (layers == null) {
            return List.of();
        }
        List<List<String>> out = new java.util.ArrayList<>(layers.size());
        for (List<String> layer : layers) {
            List<String> rows = new java.util.ArrayList<>(layer.size());
            for (String row : layer) {
                rows.add(row == null ? "" : row.replace('.', '_'));
            }
            out.add(rows);
        }
        return out;
    }

    /**
     * 赛钱箱一次运作消耗的 POWER（spec：「消耗赛钱箱 2 点 POWER」）。
     *
     * <p>⚠ 赛钱箱的容量默认只有 5 POWER，所以这个数直接决定"一次能连做几次"。
     */
    public int saizenPowerCost = 2;

    /**
     * 祭坛（赛钱箱）自己的消息前缀。
     *
     * <p>★ 必须与反应堆分开：之前两者共用 {@code messagePrefix}，
     * 于是祭坛的提示顶着「&8[&6灵乌路空反应堆&8]」出现在玩家聊天栏（真实踩点，2026-09-18）。
     */
    public String saizenPrefix = "&8[&d祭坛&8] &r";

    /**
     * 祭坛自己的消息档位，默认 {@code off} —— 只报 warning/error。
     *
     * <p>刻意比反应堆的 {@code important} 更安静：祭坛的「已激活 / 结构明细」
     * 在 GUI 里本来就看得见，再往聊天栏推一遍纯属刷屏。
     */
    public String saizenMessageLevel = "off";

    /**
     * 梦想封印 集自己的消息前缀（{@code config.yml} 的 {@code seal.message-prefix}）。
     *
     * <p>★ 同样是"不许蹭别人的前缀"：这个道具原先走默认作用域，
     * 提示顶着「&amp;8[&amp;6灵乌路空反应堆&amp;8]」出来（与祭坛踩过的坑一模一样）。
     */
    public String sealPrefix = "&8[&d梦想封印 集&8] &r";

    /**
     * 梦想封印 集自己的消息档位，默认 {@code off}。
     *
     * <p>本道具目前只发 warning（不受档位影响），这一格是给"以后想推常规反馈"留的开关。
     */
    public String sealMessageLevel = "off";

    /**
     * 杀意的百合自己的消息前缀（{@code config.yml} 的 {@code lily.message-prefix}）。
     *
     * <p>★ 与梦想封印 集同理：这两件是同一组（PARTY_ITEM）的两件符卡，
     * 但前缀各自独立 —— 否则玩家分不清是哪件道具在说话。
     */
    public String lilyPrefix = "&8[&d杀意的百合&8] &r";

    /**
     * 杀意的百合自己的消息档位，默认 {@code off}。
     *
     * <p>它的玩家可见反馈只有 warning（{@link Notify#warn} 不受档位影响）
     * 与"已发射"那条常规反馈；后者默认不推，避免每打一发就刷一行。
     */
    public String lilyMessageLevel = "off";

    /**
     * 另一个世界的回响 —— 自己的消息前缀（{@code config.yml} 的 {@code echo.message-prefix}）。
     *
     * <p>★ 与梦想封印 集 / 杀意的百合同理：一件道具/机制的提示不该顶着另一台机器的前缀
     * （赛钱箱踩过的老路，见 {@link Notify#saizen()}）。
     *
     * <p>★ 本机制的玩家可见反馈<b>只有 warning</b>（"转化成功"那一条，走
     * {@link Notify#warn} —— 它不受档位影响、永远输出），所以这里没有配套的
     * {@code messages.level}：加一个没有任何调用点的档位只会让人以为"调了没用"。
     */
    public String echoPrefix = "&8[&f另一个世界的回响&8] &r";

    /**
     * 「丰收之时」—— 自己的消息前缀（{@code config.yml} 的 {@code harvest.message-prefix}）。
     *
     * <p>★ 与 {@code echoPrefix} 同理：一台机器的提示不该蹭别的机器或反应堆的前缀
     * （赛钱箱踩过的老路，见 {@link Notify#saizen()}）。
     *
     * <p>★ 本机器只发 warning（催熟结果 / 冷却中 / 没权限），而 warning 不受档位影响、
     * 永远输出 ⇒ 这里同样<b>没有</b>配套的 {@code messages.level}。
     */
    public String harvestPrefix = "&8[&6丰收之时&8] &r";

    /**
     * 每方块冷却（毫秒，{@code harvest.cooldown-millis}）。
     *
     * <p>一次右键要扫 9×9×3 = 243 格、撒 50 个粒子、播一个音效。
     * 玩家按住右键的连点频率远高于"一次真能催熟出东西"的频率，
     * 所以必须有一道节流把它们挡在门外。默认 <b>5000 ms（5 秒）</b>：
     * 一次催熟要逐格循环打骨粉（每格最多 8 次）、还可能结瓜、撒 50 个粒子，
     * 5 秒是"玩家能明确感觉到这是一次有代价的操作"的档位，
     * 又不至于长到影响正常的来回劳作节奏。
     *
     * <p>★ 键是<b>方块坐标</b>（不是玩家）：同一台机器被谁点都一样要等，
     * 而不同田里的两台机器互不影响 —— 那正是"防连点"想要的形状。
     * 配 0 = 关闭冷却。
     */
    public int harvestCooldownMillis = 5000;

    // ---- 冰の妖精（「冰の妖精」的右键范围效果）----

    /**
     * 「冰の妖精」—— 自己的消息前缀（{@code config.yml} 的 {@code cirno.message-prefix}）。
     *
     * <p>★ 与 {@link #echoPrefix} / {@link #harvestPrefix} 同理：一台机器的提示不该蹭
     * 别的机器或反应堆的前缀（赛钱箱踩过的老路，见 {@link Notify#saizen()}）。
     *
     * <p>★ 本机器只发 warning（冷却中 / 没权限），而 warning 不受档位影响、永远输出
     * ⇒ 这里同样<b>没有</b>配套的 {@code messages.level}。
     * ★ 那句成功提示 {@code Bakabakabakabaka} <b>不走前缀</b>（用户要求整行蓝色原文），
     * 理由见 {@code Cirno#getItemHandler} 的注释。
     */
    public String cirnoPrefix = "&8[&b冰の妖精&8] &r";

    /**
     * 「冰の妖精」右键效果的<b>每方块冷却</b>（毫秒，{@code cirno.cooldown-millis}）。
     *
     * <p>★★ <b>8000 ms（8 秒）是用户口径</b>（2026-09-22 明确指定"冷却固定为 8000 ms"），
     * 不是本实现的判断 —— 与 {@code harvestCooldownMillis} 那种"我选的 5 秒"不同。
     *
     * <p>一次右键会扫 9×9×9 = 729 格、把其中的水换成冰、还要给范围内每个实体挂药水效果。
     * 8 秒既是"玩家能明确感觉到这是一次有代价的操作"的档位，也保证"冻住一整片水池"
     * 这件事不会因为连点而被反复触发。
     *
     * <p>★ 键是<b>方块坐标</b>（不是玩家）：同一台机器被谁点都一样要等，
     * 而不同水池里的两台机器互不影响。理由与 {@code Cirno} 的类注释同一套：
     * ① 冷却是"机器自己"的属性；② 按玩家记的话，轮着点两台机器就能绕过。
     * 配 0 = 关闭冷却。
     */
    public int cirnoCooldownMillis = 8000;

    /**
     * 冷却的上限（钳制用）—— 60 秒。
     *
     * <p>★ 为什么要有个上限：冷却写错单位（本意 8 秒却写成 80000）会变成"永远冷却中"，
     * 在游戏里表现为<b>右键完全没反应</b>，很难联想到是配置问题。
     * 钳住 + 控制台 warning 能把这类错误变成一个说得清的读数。
     */
    public static final int MAX_CIRNO_COOLDOWN_MILLIS = 60000;

    // ---- 雾中の妖精（「雾中の妖精」的一次性实体效果）----

    /**
     * 「雾中の妖精」—— 自己的消息前缀（{@code config.yml} 的 {@code fairy.message-prefix}）。
     *
     * <p>★ 与 {@link #echoPrefix} / {@link #harvestPrefix} / {@link #cirnoPrefix} 同理：
     * 一件道具的提示不该蹭别的机器或反应堆的前缀（赛钱箱踩过的老路，见 {@link Notify#saizen()}）。
     *
     * <p>★ 本道具只用它发 warning（"物品已消耗、召唤却失败"），而 warning 不受档位影响、
     * 永远输出 ⇒ 这里同样<b>没有</b>配套的 {@code messages.level}。
     * ★ 它<b>不再</b>服务于"占位提示"（2026-09-22 用户口径变更：右键已放下的方块
     * 什么都不输出）—— 所以现在只剩"召唤失败"一个调用点；
     * <b>但前缀仍然要用，别当成死配置删掉</b>。
     *
     * <p>★ 那句成功的绿色 {@code Bomb} <b>不走前缀</b>（用户要求整行原话），
     * 理由见 {@code FairyInMist} 里的破例注释。
     *
     * <p>★ <b>没有冷却配置项</b>：对空气右键本身就消耗 1 个物品，不会刷屏
     * （用户原话"不做冷却"）。
     */
    public String fairyPrefix = "&8[&a雾中の妖精&8] &r";

    // ---- 落叶（「落叶」的获取机制：玩家手动破坏树叶按概率掉落）----

    /**
     * 落叶机制总开关（{@code leaves.enabled}）。
     *
     * <p>关掉之后：破坏树叶<b>不再</b>掉落落叶；物品本身照旧存在
     * （已经拿到的落叶不受影响，指南页也照常显示）。
     */
    public boolean fallenLeavesEnabled = true;

    /**
     * 破坏一片树叶掉落落叶的<b>概率</b>（{@code leaves.drop-chance}，0~1）。
     *
     * <p>用户口径是 <b>20%</b> ⇒ 默认 {@code 0.2}。
     * 配 0 = 永不掉落，配 1 = 每次都掉（两者都用于调试/极端玩法）。
     */
    public double fallenLeavesDropChance = 0.2D;

    /**
     * 命中时掉落数量的<b>下限</b>（{@code leaves.min-amount}，含端点）。
     *
     * <p>用户口径是 <b>2~7 个</b> ⇒ 默认 2 / 7。
     */
    public int fallenLeavesMinAmount = 2;

    /**
     * 命中时掉落数量的<b>上限</b>（{@code leaves.max-amount}，含端点）。
     *
     * <p>★ 上下限都会被 {@code validate} 钳到
     * {@code 1..FallenLeaves.MAX_DROP_AMOUNT}，且保证 {@code max >= min} ——
     * 否则 {@code nextInt(max - min + 1)} 会因负数参数在破坏方块时抛异常。
     */
    public int fallenLeavesMaxAmount = 7;

    /**
     * 用<b>剪刀</b>破坏树叶时要不要掉落叶（{@code leaves.drop-with-shears}）。
     *
     * <p>★ 默认 {@code false} = <b>不生效</b>（用户口径）。这不只是玩法偏好：
     * <b>剪刀破坏会掉"树叶方块本身"</b>，而拿到方块就能"放置 → 破坏"无限循环 ——
     * 关掉它就等于堵掉了那条刷取路径（判据见 {@link FallenLeaves#isExcludedTool}）。
     */
    public boolean fallenLeavesDropWithShears = false;

    /**
     * 用带<b>精准采集</b>的工具破坏树叶时要不要掉落叶
     * （{@code leaves.drop-with-silk-touch}）。
     *
     * <p>★ 默认 {@code false} = <b>不生效</b>（用户口径），理由同上：
     * 精准采集同样会掉"树叶方块本身"。
     */
    public boolean fallenLeavesDropWithSilkTouch = false;

    // ---- 维度穿梭（「另一个世界的回响」的获取机制）----

    /**
     * 维度穿梭总开关（{@code config.yml} 的 {@code echo.enabled}）。
     *
     * <p>关掉之后：玩家传送<b>不会</b>再转化任何水晶；物品本身照旧存在
     * （已经拿到的回响不受影响，指南页也照常显示获取方式）。
     */
    public boolean echoEnabled = true;

    /**
     * 换算：<b>几个</b>能量水晶算一个"转化单位"（{@code echo.crystal-per-echo}）。
     *
     * <p>默认 1 —— 用户口径是 <b>1:1</b>（1 个能量水晶 → 1 个回响）。
     * 做成两个数是为了"10 个水晶换 1 个回响"这类调整不必改代码，
     * <b>不是</b>概率、也不是每次传送的上限。
     */
    public int echoCrystalPerEcho = 1;

    /**
     * 每个"转化单位"产出<b>几个</b>回响（{@code echo.echo-per-crystal}）。
     *
     * <p>默认 1。与上面的数一起决定换算：
     * {@code 回响数 = 水晶数 / crystal-per-echo * echo-per-crystal}。
     * ⚠ 当前实现是"逐槽按比例折算、向下取整"（见
     * {@code EchoOfAnotherWorld#convertInventory}），所以配成 2:1 时
     * "只有 1 个水晶"不会产出半个回响，而是原样留在背包里。
     */
    public int echoEchoPerCrystal = 1;

    /**
     * 两个换算数的<b>上限</b>（{@link #validate} 用）。
     *
     * <p>★ 为什么必须有上限：换算用 {@code int} 算，而一格最多 64 个水晶 ——
     * {@code echo-per-crystal} 大到 3 千万量级时 {@code 64 * perEcho} 会溢出成负数，
     * 那会让"产出 &lt;= 0"这条守卫成立，表现为<b>水晶被消耗、回响一个都不发</b>。
     * 64 已经足以表达任何正常倍率（64 水晶 → 4096 回响封顶）。
     */
    public static final int MAX_ECHO_RATIO = 64;

    /**
     * 玩家级转化冷却（毫秒，{@code echo.convert-cooldown-millis}）。
     *
     * <h2>★ 为什么需要它（以及它<b>不</b>解决什么）</h2>
     * 幂等本身是<b>免费</b>的：转化是就地改写背包内容，判据又只看"能量水晶"这个粘液 id
     * —— 同一个玩家在同一秒里被触发一百次，第二次起一定数出 0 个水晶、什么都不做。
     * 所以这道冷却防的<b>不是</b>无限刷，而是：
     * <ol>
     *   <li><b>两次事件落在同一次穿梭上</b>：某些传送路径（跨世界传送门插件、
     *       {@code /execute in} 链式传送）可能连发两次换世界事件；</li>
     *   <li><b>刚转化完又被弹回门里</b>：玩家站在目的地传送门方块中时可能很快被再传一次。
     *       那一瞬他手上已经是"回响"而不是水晶（所以不会凭空增殖），但如果他背包里
     *       <b>还有第二批</b>水晶，这道冷却至少让"来回穿梭"变成有节奏的行为而不是每 tick 一次。</li>
     * </ol>
     *
     * <p>默认 <b>3000 ms（3 秒）</b>：比一次传送动画/落地长、比一次有意的往返穿梭短。
     * 设 {@code 0} = 关闭冷却（只留幂等那道保证）。
     */
    public int echoConvertCooldownMillis = 3000;

    /**
     * 控制台 info 输出总开关（{@code config.yml} 的 {@code logging.console-info}）。
     *
     * <ul>
     *   <li>{@code false}（默认）—— 这些<b>全部静默</b>，控制台只留
     *       {@link Log#warn}（配置越界、可选依赖缺失）与 {@link Log#severe}（代码异常）。</li>
     * </ul>
     *
     * <p>★ 默认取 {@code false}：正常运转的流水账对排查没帮助，反而把真正重要的
     * warning/error 淹掉。要看细节时再改 {@code config.yml} 打开。
     */
    public boolean consoleInfo = false;

    /**
     * 「POWER供给单元」的<b>服务器总开关</b>（{@code supply.enabled}，默认开）。
     *
     * <p>关掉之后这台机器<b>不再给玩家无线充电</b>，但它仍然是普通的 POWER 节点
     * （照常并网、储能）—— 所以关它不会让已经摆好的机器变成"坏方块"，
     * 只是停掉"无线送电给玩家"这一个功能。
     */
    public boolean supplyEnabled = true;

    /**
     * 赛钱箱机器逻辑的运作间隔（Slimefun tick）。
     *
     * <p>机器每 tick 都要做「读 6 根木桩 → 镜像到预留槽 → 比对配方」，
     * 全速跑没必要（玩家投料是手动动作），默认 10 tick ≈ 1 秒一次。
     */
    public int saizenWorkIntervalTicks = 10;

    /**
     * 已激活时，每多少轮运作做一次<b>现场结构复检</b>（轮）。
     *
     * <p>用途只有一个：结构被拆掉之后要尽快把机器停下来并清掉木桩编号。
     * 5 轮 × 1 秒 = 约 5 秒复检一次（一次复检 = 48 格方块查询）。
     * ★ 复检只会「让机器停下来」，绝不会自动激活 —— 激活永远是玩家点出来的。
     */
    public int saizenRecheckPasses = 5;

    /**
     * 结构是否绕纵轴四向旋转不变（{@code null} = 自动从层图算）。
     *
     * <p>参照 LogiTech 的 {@code isSymmetric}：为 {@code true} 时结构检测只在 NORTH 探测一次，
     * 省掉 3/4 次方块查询。默认自动判定（把每层旋转 90° 与原图逐格比对），
     * 一般不需要手填。
     *
     * <p>★ 手工设成 {@code true} 而结构其实不对称，会让结构在其它朝向下永远校验失败。
     * 只有在你确信自动判定算错时才覆盖它。
     */
    public Boolean structureSymmetric = null;
    /**
     * 在结构构件周围找核心的扫描半径（格），<b>同时也是"反向绑定"的半径</b>。
     *
     * <p>5×5×5 结构里构件到核心最多差 2 格，默认 2 足够。
     * 结构变大时要同步调大：调小了，构件放下时找不到核心，就会按需求"停止检测"，
     * 只能靠核心那边的反向绑定兜住（同一个半径）。
     */
    public int coreSearchRadius = 2;

    /**
     * IO 接口的搬运间隔（<b>真实秒</b>）。
     *
     * <p>spec：「要求接口以每 5s 一次的效率进行工作」→ 默认 5 秒。
     *
     * <p>★ 单位是真实秒而不是 tick：搬运跑在 {@code BlockTicker} 上，那是 Slimefun tick，
     * 长度由 {@code tickRate} 决定（默认 10/秒 = 原版的一半）。写死 tick 数会让
     * "5 秒"变成 50 秒。换算见 {@link ReactorManager#ioIntervalTicks()}。
     *
     * <p>调小的代价是每轮都要遍历 4 个输入槽 + 16 个输出槽做搬运判定；
     * 接口是"低频缓冲器"，没必要更快。
     */
    public int ioIntervalSeconds = ReactorManager.DEFAULT_IO_INTERVAL_SECONDS;

    // ---- 附加粒子特效（核心周围的火焰粒子） ----
    /** 粒子特效总开关（每台机器还能在 GUI 里单独关）。 */
    public boolean particleEnabled = true;
    /** 球壳内半径（格）：粒子只在这个半径<b>之外</b>生成。 */
    public double particleInnerRadius = 3.0D;
    /** 球壳外半径（格）。 */
    public double particleOuterRadius = 5.0D;
    /** 每个间隔生成多少颗火焰粒子。 */
    public int particleAmount = 20;
    /** 生成间隔（tick）。4 tick = 0.2 秒；想要 2 秒一次就写 40。 */
    public int particleIntervalTicks = 4;
    /** 一次进程结束时立刻爆出的粒子数。 */
    public int particleCompletionBurst = 40;

    // ---- 杂项 ----
    /** 反应堆 GUI 是否禁用物品运输（货运网络）访问。 */
    public boolean disableItemTransport = true;
    /** 发电模式的暂停表述。 */
    public String messagePrefix = "&8[&6灵乌路空反应堆&8] &r";
    /**
     * 游戏内消息栏的详细程度（见 {@link Notify}）。
     *
     * <p>需求：「尽量减少游戏内消息栏的输出，除了重要事件（例如多方块结构构建成功）
     * 和 warning/error 以外都不输出」。所以默认 {@code important}：
     * <pre>
     * off       只报 warning/error
     * important warning/error + 重要事件（结构构建成功/激活）   ← 默认
     * normal    再加常规操作反馈（切换模式、开关粒子…）
     * all       再加诊断细节（归属 uid、绑定核心…）
     * </pre>
     *
     * <p>⚠ warning/error <b>不受这个档位影响</b>，永远输出 —— 需求明确要求。
     */
    public String messageLevel = "important";

    // ---- 粘液书（指南）配方页 / 物品描述 ----
    /**
     * 是否往多方块核心的<b>物品描述</b>里追加「建造所需材料」清单。
     *
     * <p>清单由 {@link StructureMaterials} 从 {@link #structureLayers} /
     * {@link #saizenLayers} 现算，<b>不</b>依赖结构实例（lore 构造时结构还没创建）。
     * 关掉它物品描述就回到"只有简介"的样子，控制台/命令的诊断输出不受影响。
     */
    public boolean guideMaterialLore = true;

    /**
     * 物品描述里最多列<b>几种</b>材料（{@code <= 0} = 不限制）。
     *
     * <p>★ 为什么要这道闸：合并计数之后，两种内置结构都只有 4 种材料
     * （反应堆 44 框架 + 36 保护罩 + 9 基座 + 9 稳定器 = 98 个构件），
     * 正常配置下列得完；但 legend 是玩家可改的，塞进几十种方块时 lore 会撑爆物品提示框。
     * 超出上限的部分<b>只统计不列出</b>：标题行永远报出"共 X 个构件 / Y 种"，
     * 所以信息一个都没丢，只是没逐行展开。
     */
    public int guideMaterialKinds = 6;

    private static AddonConfig instance;

    private AddonConfig() {
    }

    public static AddonConfig get() {
        if (instance == null) {
            instance = load();
        }
        return instance;
    }

    /** 配置热重载（/touhou reload 之类）时调用。 */
    public static void reload() {
        instance = load();
    }

    private static AddonConfig load() {
        AddonConfig c = new AddonConfig();
        FileConfiguration cfg = Touhou.getInstance().getConfig();

        // ★ 赛钱箱那一段【先读】：下面 "config.yml 缺少 reactor: 段" 时会直接 return，
        //   先读它才能保证"就算反应堆段整段丢了，赛钱箱的配置照样生效"。
        loadSaizenbako(c, cfg);

        // 梦想封印 集那一段同理（只有消息前缀/档位；四个数字在 Items.yml 里，见 loadSeal）。
        loadSeal(c, cfg);

        // 杀意的百合那一段（同样只有消息前缀/档位；道具自己的数值也在 Items.yml 里）。
        loadLily(c, cfg);

        // 另一个世界的回响 / 维度穿梭那一段（物品数值在代码里，机制参数在 config.yml）。
        loadEcho(c, cfg);

        // 丰收之时那一段（机器行为参数：每方块冷却 + 自己的消息前缀）。
        loadHarvest(c, cfg);

        // 落叶那一段（获取机制：概率 / 数量上下限 / 总开关）。
        loadLeaves(c, cfg);

        // 冰の妖精那一段（机器行为参数：每方块冷却 + 自己的消息前缀）。
        loadCirno(c, cfg);

        // 雾中の妖精那一段（只有自己的消息前缀 —— 本道具不做冷却）。
        loadFairy(c, cfg);

        c.consoleInfo = cfg.getBoolean("logging.console-info", c.consoleInfo);
        c.supplyEnabled = cfg.getBoolean("supply.enabled", c.supplyEnabled);

        // 粘液书那一段也放在 reactor: 之前读（与投影同理）：它是"全局"设置，
        // 不该因为 reactor 段整段丢了就让物品描述里的材料清单一并消失。
        ConfigurationSection guide = cfg.getConfigurationSection("guide");
        if (guide != null) {
            c.guideMaterialLore = guide.getBoolean("material-lore", c.guideMaterialLore);
            c.guideMaterialKinds = guide.getInt("material-kinds", c.guideMaterialKinds);
        }

        // 投影那一段放在 reactor: 之前读（与赛钱箱同理）：
        // 它是"全局"设置，不该因为 reactor 段整段丢了就失效。
        ConfigurationSection proj = cfg.getConfigurationSection("projection");
        if (proj != null) {
            c.projectionEnabled = proj.getBoolean("enabled", c.projectionEnabled);
            c.projectionMaxParts = proj.getInt("max-parts", c.projectionMaxParts);
            c.projectionCleanRadius = proj.getInt("clean-radius", c.projectionCleanRadius);
        }

        ConfigurationSection s = cfg.getConfigurationSection("reactor");
        if (s == null) {
            Touhou.getInstance().getLogger().warning("config.yml 缺少 reactor: 段，使用内置默认值");
            // ★ validate() 必须在这里也调一次：下面读到的 echo / 投影 / 赛钱箱
            //   这些段的数值钳制全在 validate() 里，而它是本方法唯一一次被调用的地方。
            //   原先直接 return，等于"config.yml 少了 reactor: 段 ⇒ 别的段的越界值全部不钳"。
            validate(c);
            return c;
        }

        c.energyCapacity = s.getInt("energy-capacity", c.energyCapacity);
        c.energyProduction = s.getLong("energy-production", c.energyProduction);
        c.processTicks = s.getInt("process-ticks", c.processTicks);
        c.productModeSpeedMultiplier = s.getDouble("product-mode-speed-multiplier",
                c.productModeSpeedMultiplier);
        c.productModeEnergyRate = s.getDouble("product-mode-energy-rate", c.productModeEnergyRate);
        c.modeThreshold = s.getLong("mode-threshold", c.modeThreshold);
        c.abortProcessOnBroken = s.getBoolean("abort-process-on-broken", c.abortProcessOnBroken);
        // symmetric 是"三态"：没写 = 自动算（null）；写了 true/false 才覆盖
        if (s.isSet("structure.symmetric")) {
            c.structureSymmetric = s.getBoolean("structure.symmetric");
        }
        c.coreSearchRadius = s.getInt("structure.core-search-radius", c.coreSearchRadius);
        c.ioIntervalSeconds = s.getInt("io.interval-seconds", c.ioIntervalSeconds);
        c.disableItemTransport = s.getBoolean("disable-item-transport", c.disableItemTransport);
        c.messagePrefix = s.getString("message-prefix", c.messagePrefix);
        c.messageLevel = s.getString("messages.level", c.messageLevel);

        List<?> layers = s.getList("structure.layers");
        if (layers != null && !layers.isEmpty()) {
            List<List<String>> parsed = LayerParser.parseLayers(layers);
            if (!parsed.isEmpty()) {
                c.structureLayers = parsed;
            }
        }
        // 整个 legend 段读进来：键是单个字符，值是 part id / Material 名 / nu / #标签
        ConfigurationSection legend = s.getConfigurationSection("structure.legend");
        if (legend != null) {
            java.util.Map<Character, String> parsed = new java.util.LinkedHashMap<>();
            for (String key : legend.getKeys(false)) {
                String value = legend.getString(key);
                if (key.isEmpty() || value == null || value.isBlank()) {
                    continue;
                }
                parsed.put(key.charAt(0), value.trim());
            }
            if (!parsed.isEmpty()) {
                c.structureLegend = parsed;
            }
        }
        // unique-parts 是列表（每类各自唯一）；仍兼容旧的单个 unique-part 字符串配置
        java.util.List<String> uniqueParts = s.getStringList("structure.unique-parts");
        if (!uniqueParts.isEmpty()) {
            c.structureUniqueParts = new java.util.LinkedHashSet<>(uniqueParts);
        } else {
            String legacy = s.getString("structure.unique-part", null);
            if (legacy != null && !legacy.isBlank()) {
                c.structureUniqueParts = new java.util.LinkedHashSet<>(java.util.List.of(legacy.trim()));
            }
        }

        // ---- 粒子特效 ----
        c.particleEnabled = s.getBoolean("particles.enabled", c.particleEnabled);
        c.particleInnerRadius = s.getDouble("particles.inner-radius", c.particleInnerRadius);
        c.particleOuterRadius = s.getDouble("particles.outer-radius", c.particleOuterRadius);
        c.particleAmount = s.getInt("particles.amount", c.particleAmount);
        c.particleIntervalTicks = s.getInt("particles.interval-ticks", c.particleIntervalTicks);
        c.particleCompletionBurst = s.getInt("particles.completion-burst", c.particleCompletionBurst);

        validate(c);
        return c;
    }

    /**
     * 读 {@code saizenbako:} 段（赛钱箱多方块）。
     *
     * <p>写法与 {@code reactor.structure} 完全一致（层图 + legend），解析也复用
     * {@link LayerParser} —— <b>刻意不另造一套格式</b>：这套层图格式已经在扫描工具、
     * 反应堆、命令诊断三处对齐过了，再造一份解析器就等于再造一处会漂移的地方。
     *
     * <p>缺段/缺项时一律保留内置默认值（默认值就是 {@code docs/saizenbako-layers.yml}
     * 那份权威层图），所以"老 config.yml 里没有这一段"也不会让机器失效。
     */
    private static void loadSaizenbako(AddonConfig c, FileConfiguration cfg) {
        ConfigurationSection s = cfg.getConfigurationSection("saizenbako");
        if (s == null) {
            return;                     // 没写这一段 = 全用内置默认层图
        }
        c.saizenPowerCost = s.getInt("power-cost", c.saizenPowerCost);
        c.saizenPrefix = s.getString("message-prefix", c.saizenPrefix);
        c.saizenMessageLevel = s.getString("messages.level", c.saizenMessageLevel);
        c.saizenWorkIntervalTicks = s.getInt("machine.work-interval-ticks", c.saizenWorkIntervalTicks);
        c.saizenRecheckPasses = s.getInt("machine.recheck-passes", c.saizenRecheckPasses);

        List<?> layers = s.getList("structure.layers");
        if (layers != null && !layers.isEmpty()) {
            List<List<String>> parsed = LayerParser.parseLayers(layers);
            if (!parsed.isEmpty()) {
                c.saizenLayers = parsed;
            }
        }
        ConfigurationSection legend = s.getConfigurationSection("structure.legend");
        if (legend != null) {
            java.util.Map<Character, String> parsed = new java.util.LinkedHashMap<>();
            for (String key : legend.getKeys(false)) {
                String value = legend.getString(key);
                if (key.isEmpty() || value == null || value.isBlank()) {
                    continue;
                }
                parsed.put(key.charAt(0), value.trim());
            }
            if (!parsed.isEmpty()) {
                c.saizenLegend = parsed;
            }
        }
    }

    /**
     * 读 {@code seal:} 段（梦想封印 集）。
     *
     * <p>★ 只读<b>消息</b>相关的两项：道具的四个数字（上限 40 / 消耗 1 / 充能 1 每 2 秒 /
     * 冷却 1.5 秒）、取电半径、提示节流全部是 {@code ItemSetting}，
     * Slimefun 会把它们落在 {@code plugins/Slimefun/Items.yml} 的
     * {@code TOUHOU_PARTY_ITEM_FANTASY_SEAL_CONVERGE} 那一节里 ——
     * 数值只有一个出处，绝不在这里再抄一份（见 {@code FantasySeal} 的类注释）。
     *
     * <p>缺段/缺项时一律保留内置默认值，所以老 config.yml 直接用也不会出问题。
     */
    private static void loadSeal(AddonConfig c, FileConfiguration cfg) {
        ConfigurationSection s = cfg.getConfigurationSection("seal");
        if (s == null) {
            return;
        }
        c.sealPrefix = s.getString("message-prefix", c.sealPrefix);
        c.sealMessageLevel = s.getString("messages.level", c.sealMessageLevel);
    }

    /**
     * 读 {@code lily:} 段（杀意的百合）。
     *
     * <p>★ 与 {@link #loadSeal} 完全同构，只读<b>消息</b>相关的两项：
     * 道具的 POWER 刻度与全部弹幕参数都是 {@code ItemSetting}，落在
     * {@code plugins/Slimefun/Items.yml} 的 {@code TOUHOU_PARTY_ITEM_MURDEROUS_LILY} 一节里
     * —— 数值只有一个出处，绝不在这里再抄一份。
     *
     * <p>其中的 POWER 刻度还与梦想封印 集<b>共用同一份声明</b>（{@link PartyItem}），
     * 所以"数据等沿用"这件事在代码里是一处而不是两处。
     */
    private static void loadLily(AddonConfig c, FileConfiguration cfg) {
        ConfigurationSection s = cfg.getConfigurationSection("lily");
        if (s == null) {
            return;
        }
        c.lilyPrefix = s.getString("message-prefix", c.lilyPrefix);
        c.lilyMessageLevel = s.getString("messages.level", c.lilyMessageLevel);
    }

    /**
     * 读 {@code echo:} 段（另一个世界的回响 + 维度穿梭）。
     *
     * <p>与 {@link #loadSeal} / {@link #loadLily} 同构：缺段/缺项一律保留内置默认值，
     * 所以老 {@code config.yml}（没有这一段）直接用也不会出问题。
     *
     * <p>★ 为什么这一段不像两件符卡那样"数值全在 Items.yml"：本机制<b>不是物品属性</b>
     * —— 它是"玩家传送时发生什么"，没有对应的 {@code SlimefunItem} 实例去承载
     * {@code ItemSetting}（触发点是事件，不是右键某件物品）。所以它的参数就落在
     * 本文件（{@code config.yml}），与 {@code saizenbako.power-cost}、
     * {@code reactor.process-ticks} 同一类。
     */
    private static void loadEcho(AddonConfig c, FileConfiguration cfg) {
        ConfigurationSection s = cfg.getConfigurationSection("echo");
        if (s == null) {
            return;
        }
        c.echoPrefix = s.getString("message-prefix", c.echoPrefix);
        c.echoEnabled = s.getBoolean("enabled", c.echoEnabled);
        c.echoCrystalPerEcho = s.getInt("crystal-per-echo", c.echoCrystalPerEcho);
        c.echoEchoPerCrystal = s.getInt("echo-per-crystal", c.echoEchoPerCrystal);
        c.echoConvertCooldownMillis = s.getInt("convert-cooldown-millis", c.echoConvertCooldownMillis);
    }

    /**
     * 读 {@code harvest:} 段 —— 「丰收之时」的机器参数。
     *
     * <p>与 {@code echo:} 段同一类：这些是"机器行为参数"，不是物品属性，
     * 所以落在 {@code config.yml}（本文件）而不是 {@code Items.yml}。
     */
    private static void loadHarvest(AddonConfig c, FileConfiguration cfg) {
        ConfigurationSection s = cfg.getConfigurationSection("harvest");
        if (s == null) {
            return;
        }
        c.harvestPrefix = s.getString("message-prefix", c.harvestPrefix);
        c.harvestCooldownMillis = s.getInt("cooldown-millis", c.harvestCooldownMillis);
    }

    /**
     * 读 {@code leaves:} 段 —— 「落叶」的获取机制参数。
     *
     * <p>与 {@code echo:} / {@code harvest:} 同一类：这些是"机制参数"而不是物品属性，
     * 所以落在 {@code config.yml}（本文件）而不是 {@code Items.yml}。
     */
    private static void loadLeaves(AddonConfig c, FileConfiguration cfg) {
        ConfigurationSection s = cfg.getConfigurationSection("leaves");
        if (s == null) {
            return;
        }
        c.fallenLeavesEnabled = s.getBoolean("enabled", c.fallenLeavesEnabled);
        // ★ 概率这一段允许写成百分比以外的形式吗？不允许 —— 口径固定为 0~1 的小数，
        //   并在 validate 里钳死，免得"写 20 以为是 20%"这种歧义。
        c.fallenLeavesDropChance = s.getDouble("drop-chance", c.fallenLeavesDropChance);
        c.fallenLeavesMinAmount = s.getInt("min-amount", c.fallenLeavesMinAmount);
        c.fallenLeavesMaxAmount = s.getInt("max-amount", c.fallenLeavesMaxAmount);
        c.fallenLeavesDropWithShears = s.getBoolean("drop-with-shears", c.fallenLeavesDropWithShears);
        c.fallenLeavesDropWithSilkTouch =
                s.getBoolean("drop-with-silk-touch", c.fallenLeavesDropWithSilkTouch);
    }

    /**
     * 读 {@code cirno:} 段 —— 「冰の妖精」的机器参数。
     *
     * <p>与 {@code echo:} / {@code harvest:} / {@code leaves:} 同一类：这些是"机器行为参数"，
     * 不是物品属性，所以落在 {@code config.yml}（本文件）而不是 {@code Items.yml}。
     *
     * <p>★ 读不到段时保持内置默认值（8000 ms）—— 别在这里抛异常：
     * 老 {@code config.yml} 不会自动补新段（{@code saveDefaultConfig()} 只在文件不存在时生成），
     * 用户升级插件后手里多半就是"没有 cirno: 段"的那份配置。
     */
    private static void loadCirno(AddonConfig c, FileConfiguration cfg) {
        ConfigurationSection s = cfg.getConfigurationSection("cirno");
        if (s == null) {
            return;
        }
        c.cirnoPrefix = s.getString("message-prefix", c.cirnoPrefix);
        c.cirnoCooldownMillis = s.getInt("cooldown-millis", c.cirnoCooldownMillis);
    }

    /**
     * 读 {@code fairy:} 段 —— 「雾中の妖精」的道具参数。
     *
     * <p>与 {@code echo:} / {@code harvest:} / {@code cirno:} 同一类：这些是"机制参数"，
     * 不是物品属性，所以落在 {@code config.yml}（本文件）而不是 {@code Items.yml}。
     *
     * <p>★ 本道具<b>只有前缀一项</b>：用户明确"不做冷却"（对空气右键本身就消耗物品），
     * 所以这里没有 cooldown 键 —— 加一个没有调用点的配置项只会让人以为"调了没用"。
     *
     * <p>★ 读不到段时保持内置默认值 —— 别在这里抛异常：老 {@code config.yml}
     * 不会自动补新段（{@code saveDefaultConfig()} 只在文件不存在时生成）。
     */
    private static void loadFairy(AddonConfig c, FileConfiguration cfg) {
        ConfigurationSection s = cfg.getConfigurationSection("fairy");
        if (s == null) {
            return;
        }
        c.fairyPrefix = s.getString("message-prefix", c.fairyPrefix);
    }

    /**
     * 把明显不合法的配置挡下来并改成安全值。
     *
     * <p>真实踩点：`MachineFuel` 的进程 tick 必须 > 0（`FuelOperation` 构造器里有
     * `Validate.isTrue(totalTicks > 0)`），配置里手滑写 0 会在运行时抛异常。
     */
    private static void validate(AddonConfig c) {
        if (c.energyCapacity <= 0) {
            Touhou.getInstance().getLogger().warning("reactor.energy-capacity <= 0，回退为 2^31-1");
            c.energyCapacity = Integer.MAX_VALUE;
        }
        if (c.energyProduction <= 0) {
            Touhou.getInstance().getLogger().warning("reactor.energy-production <= 0，回退为 240000000");
            c.energyProduction = 240_000_000L;
        }
        if (c.processTicks <= 0) {
            Touhou.getInstance().getLogger().warning("reactor.process-ticks <= 0，回退为 10");
            c.processTicks = 10;
        }
        // 阈值不能超过存储上限，否则"发电模式"永远触发不了、退化成产物模式
        if (c.modeThreshold >= c.energyCapacity) {
            Touhou.getInstance().getLogger().warning(
                    "reactor.mode-threshold (" + c.modeThreshold + ") >= 存储上限 ("
                            + c.energyCapacity + ")，发电模式将永不触发；已钳制为上限的 90%");
            c.modeThreshold = (long) (c.energyCapacity * 0.9);
        }
        if (c.particleOuterRadius < 0.5D) {
            Touhou.getInstance().getLogger().warning("reactor.particles.outer-radius 太小，回退为 5.0");
            c.particleOuterRadius = 5.0D;
        }
        if (c.particleInnerRadius < 0.0D || c.particleInnerRadius >= c.particleOuterRadius) {
            // 内半径 >= 外半径 = 空壳，什么都不会生成；钳到外半径的一半
            Touhou.getInstance().getLogger().warning(
                    "reactor.particles.inner-radius (" + c.particleInnerRadius
                            + ") 必须 < outer-radius (" + c.particleOuterRadius
                            + ")，已钳制为外半径的 60%");
            c.particleInnerRadius = c.particleOuterRadius * 0.6D;
        }
        if (c.particleAmount < 0) {
            c.particleAmount = 0;
        }
        if (c.particleIntervalTicks <= 0) {
            Touhou.getInstance().getLogger().warning("reactor.particles.interval-ticks <= 0，回退为 4");
            c.particleIntervalTicks = 4;
        }
        if (c.particleCompletionBurst < 0) {
            c.particleCompletionBurst = 0;
        }
        // IO 搬运间隔（真实秒）：<=0 会让接口每 tick 都搬（退化成高频遍历），必须是正数
        if (c.ioIntervalSeconds <= 0) {
            Touhou.getInstance().getLogger().warning(
                    "reactor.io.interval-seconds <= 0，回退为 "
                            + ReactorManager.DEFAULT_IO_INTERVAL_SECONDS + " 秒");
            c.ioIntervalSeconds = ReactorManager.DEFAULT_IO_INTERVAL_SECONDS;
        }
        // 找核心的扫描半径：至少 1（否则只剩核心自己那一格）
        if (c.coreSearchRadius < 1) {
            Touhou.getInstance().getLogger().warning(
                    "reactor.structure.core-search-radius < 1，回退为 2");
            c.coreSearchRadius = 2;
        }
        // 赛钱箱：一次运作的 POWER 消耗必须 >= 0（0 = 不耗电，允许但不推荐）
        if (c.saizenPowerCost < 0) {
            Touhou.getInstance().getLogger().warning("saizenbako.power-cost < 0，回退为 0");
            c.saizenPowerCost = 0;
        }
        // 运作间隔必须 >= 1（0 会让机器每 tick 都跑一整轮）
        if (c.saizenWorkIntervalTicks <= 0) {
            Touhou.getInstance().getLogger().warning(
                    "saizenbako.machine.work-interval-ticks <= 0，回退为 10");
            c.saizenWorkIntervalTicks = 10;
        }
        // 复检轮数必须 >= 1（0 会让复检变成"每轮都做"，48 格查询 * 10/秒 太贵）
        if (c.saizenRecheckPasses <= 0) {
            Touhou.getInstance().getLogger().warning(
                    "saizenbako.machine.recheck-passes <= 0，回退为 5");
            c.saizenRecheckPasses = 5;
        }
        // 投影上限至少 1（0 会让投影永远开不起来，且提示会自相矛盾）
        if (c.projectionMaxParts < 1) {
            Touhou.getInstance().getLogger().warning(
                    "projection.max-parts < 1，回退为 256");
            c.projectionMaxParts = 256;
        }
        // 清理半径至少 1（0 会让 getNearbyEntities 扫不到任何东西）
        if (c.projectionCleanRadius < 1) {
            Touhou.getInstance().getLogger().warning(
                    "projection.clean-radius < 1，回退为 32");
            c.projectionCleanRadius = 32;
        }
        // 维度穿梭的两个换算数：必须 >= 1（0 会让"几个换几个"变成除零/永不产出）
        if (c.echoCrystalPerEcho < 1) {
            Touhou.getInstance().getLogger().warning(
                    "echo.crystal-per-echo < 1，回退为 1");
            c.echoCrystalPerEcho = 1;
        }
        if (c.echoEchoPerCrystal < 1) {
            Touhou.getInstance().getLogger().warning(
                    "echo.echo-per-crystal < 1，回退为 1");
            c.echoEchoPerCrystal = 1;
        }
        // ★ 上限也必须钳：换算用 int 算，perEcho 大到某个量级会溢出成负数
        //   （那会让"产出 <= 0"判定成立，表现为"水晶被吃掉、回响不发"）。
        //   一格最多 64 个水晶，所以 64 已经足够表达任何正常倍率（64:4096 封顶）。
        if (c.echoCrystalPerEcho > MAX_ECHO_RATIO) {
            Touhou.getInstance().getLogger().warning(
                    "echo.crystal-per-echo > " + MAX_ECHO_RATIO + "，钳制为 " + MAX_ECHO_RATIO);
            c.echoCrystalPerEcho = MAX_ECHO_RATIO;
        }
        if (c.echoEchoPerCrystal > MAX_ECHO_RATIO) {
            Touhou.getInstance().getLogger().warning(
                    "echo.echo-per-crystal > " + MAX_ECHO_RATIO + "，钳制为 " + MAX_ECHO_RATIO);
            c.echoEchoPerCrystal = MAX_ECHO_RATIO;
        }
        // 冷却允许 0（=关闭冷却，只留幂等），但负数没有意义
        if (c.echoConvertCooldownMillis < 0) {
            Touhou.getInstance().getLogger().warning(
                    "echo.convert-cooldown-millis < 0，回退为 0（关闭冷却）");
            c.echoConvertCooldownMillis = 0;
        }
        // 丰收之时的每方块冷却：同样允许 0（=关闭），负数没意义
        if (c.harvestCooldownMillis < 0) {
            Touhou.getInstance().getLogger().warning(
                    "harvest.cooldown-millis < 0，回退为 0（关闭冷却）");
            c.harvestCooldownMillis = 0;
        }
        // 冰の妖精的每方块冷却：同样允许 0（=关闭）。
        //   ★ 上限钳到 MAX_CIRNO_COOLDOWN_MILLIS（60 秒）：写错的单位（例如想写 8 秒却写了
        //     "8000 秒"那种手滑）会让机器实际上变成"永远冷却中"，而那在游戏里
        //     表现成"右键完全没反应"，极难排查 —— 所以给它一个显式上界并在控制台留一行 warning。
        if (c.cirnoCooldownMillis < 0 || c.cirnoCooldownMillis > MAX_CIRNO_COOLDOWN_MILLIS) {
            Touhou.getInstance().getLogger().warning(
                    "cirno.cooldown-millis (" + c.cirnoCooldownMillis + ") 越界，钳制为 0.."
                            + MAX_CIRNO_COOLDOWN_MILLIS + " ms");
            c.cirnoCooldownMillis = Math.max(0,
                    Math.min(MAX_CIRNO_COOLDOWN_MILLIS, c.cirnoCooldownMillis));
        }
        // 落叶概率：口径固定为 0~1 的小数，越界就钳住
        //   ★ 上限必须钳到 1.0：写成 20（"以为是 20%"）时若不钳，`random.nextDouble() >= 20`
        //     永远为假 ⇒ 变成"每次必掉"，正是最容易误判的那种静默行为。
        if (c.fallenLeavesDropChance < 0.0D || c.fallenLeavesDropChance > 1.0D) {
            Touhou.getInstance().getLogger().warning(
                    "leaves.drop-chance (" + c.fallenLeavesDropChance
                            + ") 不在 0~1 之间，已钳制（注意：口径是小数，20% 要写 0.2）");
            c.fallenLeavesDropChance = Math.max(0.0D, Math.min(1.0D, c.fallenLeavesDropChance));
        }
        // 落叶数量上下限：钳到 1..MAX，并保证 max >= min
        //   ★ 若不保证 max >= min，`nextInt(max - min + 1)` 会拿到 <= 0 的参数并抛异常
        //     —— 那发生在"玩家破坏方块"这条高频路径上，会直接炸事件总线。
        if (c.fallenLeavesMinAmount < 1 || c.fallenLeavesMinAmount > FallenLeaves.MAX_DROP_AMOUNT) {
            Touhou.getInstance().getLogger().warning(
                    "leaves.min-amount (" + c.fallenLeavesMinAmount + ") 越界，钳制为 1.."
                            + FallenLeaves.MAX_DROP_AMOUNT);
            c.fallenLeavesMinAmount = Math.max(1,
                    Math.min(FallenLeaves.MAX_DROP_AMOUNT, c.fallenLeavesMinAmount));
        }
        if (c.fallenLeavesMaxAmount < 1 || c.fallenLeavesMaxAmount > FallenLeaves.MAX_DROP_AMOUNT) {
            Touhou.getInstance().getLogger().warning(
                    "leaves.max-amount (" + c.fallenLeavesMaxAmount + ") 越界，钳制为 1.."
                            + FallenLeaves.MAX_DROP_AMOUNT);
            c.fallenLeavesMaxAmount = Math.max(1,
                    Math.min(FallenLeaves.MAX_DROP_AMOUNT, c.fallenLeavesMaxAmount));
        }
        if (c.fallenLeavesMaxAmount < c.fallenLeavesMinAmount) {
            Touhou.getInstance().getLogger().warning(
                    "leaves.max-amount (" + c.fallenLeavesMaxAmount + ") < min-amount ("
                            + c.fallenLeavesMinAmount + ")，已把 max 抬到 min");
            c.fallenLeavesMaxAmount = c.fallenLeavesMinAmount;
        }
    }

    /** 赛钱箱那一段的摘要（{@code /touhou saizen ... info} 用）。 */
    public List<String> describeSaizenbako() {
        StringBuilder legend = new StringBuilder();
        for (java.util.Map.Entry<Character, String> e : saizenLegend.entrySet()) {
            if (legend.length() > 0) {
                legend.append(" / ");
            }
            legend.append(e.getKey()).append('=').append(e.getValue());
        }
        return List.of(
                "power-cost        = " + saizenPowerCost + " POWER / 次",
                "work-interval     = " + saizenWorkIntervalTicks + " tick（约 "
                        + String.format("%.1f", saizenWorkIntervalTicks / 10.0) + " 秒）",
                "recheck-passes    = 每 " + saizenRecheckPasses + " 轮复检一次结构",
                "structure-layers  = " + LayerParser.describe(saizenLayers)
                        + "（核心 C 不在正中：允许偏心核心）",
                "legend            = " + legend);
    }

    /** 供 GUI / 指令展示的摘要。 */
    public List<String> describe() {
        StringBuilder legend = new StringBuilder();
        for (java.util.Map.Entry<Character, String> e : structureLegend.entrySet()) {
            if (legend.length() > 0) {
                legend.append(" / ");
            }
            legend.append(e.getKey()).append('=').append(e.getValue());
        }
        return List.of(
                "energy-capacity   = " + energyCapacity,
                "energy-production = " + energyProduction + " J/tick",
                "process-ticks     = " + processTicks,
                "mode-threshold    = " + modeThreshold,
                "structure-detect  = 事件驱动（放置/破坏/爆炸各触发一次，同一 tick 的多次变动合并为一轮）",
                "core-search-radius= " + coreSearchRadius + " 格（找核心 / 反向绑定接口都用它）",
                "io                = 搬运 " + ioIntervalSeconds + " 秒",
                "structure-layers  = " + LayerParser.describe(structureLayers),
                "legend            = " + legend,
                "messages.level    = " + Notify.Level.parse(messageLevel).display(),
                "projection        = " + (projectionEnabled ? "开" : "关")
                        + "  单次上限 " + projectionMaxParts + " 格"
                        + "  清理半径 " + projectionCleanRadius + " 格",
                "particles         = " + (particleEnabled ? "开" : "关")
                        + " 球壳 " + particleInnerRadius + "~" + particleOuterRadius + " 格"
                        + " / " + particleAmount + " 颗每 " + particleIntervalTicks + " tick"
                        + " / 完成爆 " + particleCompletionBurst,
                "guide             = 核心物品描述里的材料清单 "
                        + (guideMaterialLore ? "开" : "关")
                        + "，最多列 " + (guideMaterialKinds <= 0 ? "全部" : guideMaterialKinds + " 种"),
                "seal              = 梦想封印 集 前缀「" + sealPrefix + "」档位 "
                        + Notify.Level.parse(sealMessageLevel).display()
                        + "（POWER 刻度在 Items.yml，不在本文件）",
                "lily              = 杀意的百合 前缀「" + lilyPrefix + "」档位 "
                        + Notify.Level.parse(lilyMessageLevel).display()
                        + "（与 seal 共用 PartyItem 的 POWER 刻度，同样不在本文件）",
                "echo              = 维度穿梭 " + (echoEnabled ? "开" : "关")
                        + "  换算 " + echoCrystalPerEcho + " 水晶 → " + echoEchoPerCrystal + " 回响"
                        + "  玩家冷却 " + echoConvertCooldownMillis + " ms"
                        + "  前缀「" + echoPrefix + "」",
                "harvest           = 丰收之时 每方块冷却 " + harvestCooldownMillis + " ms"
                        + "  前缀「" + harvestPrefix + "」",
                "leaves            = 落叶 " + (fallenLeavesEnabled ? "开" : "关")
                        + "  破坏树叶 " + String.format(java.util.Locale.ROOT, "%.1f", fallenLeavesDropChance * 100.0D)
                        + "% 掉落 " + fallenLeavesMinAmount + "~" + fallenLeavesMaxAmount + " 个",
                "cirno             = 冰の妖精 每方块冷却 " + cirnoCooldownMillis + " ms"
                        + "（用户口径 8000）  前缀「" + cirnoPrefix + "」",
                "fairy             = 雾中の妖精 前缀「" + fairyPrefix + "」"
                        + "（只用于「召唤失败」的 warning —— 右键已放下的方块不再有任何提示）");
    }
}
