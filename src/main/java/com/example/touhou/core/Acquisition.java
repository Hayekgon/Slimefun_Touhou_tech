package com.example.touhou.core;

import com.example.touhou.Touhou;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * ★★★ <b>获取方式总表</b> —— 「每一件物品都要写明它是怎么来的」这件事的<b>唯一出处</b>。
 *
 * <h2>做法（用户口径，2026-09-22）</h2>
 * <b>不改物品自己的介绍</b>，而是改<b>指南页</b>：
 * <ol>
 *   <li>给物品挂一个"门面型" {@link RecipeType}，于是指南页<b>槽 10</b>
 *       （{@code SurvivalSlimefunGuide#displayItem} 里 {@code menu.addItem(10, recipeType.getItem(p), …)}）
 *       不再是空气，而是<b>它的来源</b>：
 *       <ul>
 *         <li>有配方 / 有特定机器 ⇒ 来源就是<b>那台机器</b>（增强型工作台、魔法工作台、古代祭坛…）；</li>
 *         <li>从某个方块掉落 ⇒ 来源就是<b>那个方块</b>（树叶、哭曜石之门…）；</li>
 *         <li>多方块结构 ⇒ 来源是<b>那个结构的核心</b>（例：赛钱箱 ← 神社的木桩）；</li>
 *         <li>没有具体机器 ⇒ 来源用中性图标（钩子/屏障…），照样写清途径。</li>
 *       </ul>
 *   </li>
 *   <li>槽 10 那个图标的 <b>lore 就是"获取方式：…"</b> —— 玩家把鼠标放上去就读到完整途径。
 *       这就是用户说的"在<u>书本的</u>物品描述中写入获取方式"：
 *       写的是<b>指南页上那份说明</b>，不是物品本体（本体物品的介绍一个字不动）。</li>
 * </ol>
 *
 * <h2>★ 门面为什么不会让物品变成"可合成"</h2>
 * {@code SlimefunItem#load()} 会调 {@code RecipeType#register(recipe, output)}，那条方法只有两条路：
 * {@code registerConsumer != null} 时调回调；否则看 {@code SlimefunItem.getById(machine)}
 * 是不是 {@code MultiBlockMachine}。门面走的是
 * {@code RecipeType(NamespacedKey, ItemStack, BiConsumer, String...)} 且
 * <b>callback 传 {@code null}</b>，同时传进去的是普通 {@link ItemStack}（不是
 * {@link SlimefunItemStack}）⇒ {@code machine = ""} ⇒ <b>两条路都不通，什么都不注册</b>。
 *
 * <h2>★ 给以后的子代理：加物品时必须做的一件事</h2>
 * 在 {@link #SOURCE_BY_ID} 里给新物品加一行（{@link Source#of}）。
 * <ul>
 *   <li>有配方 → {@code Source.recipe("在<机器名>合成", SlimefunItems.XXX)}（用机器图标当来源）；</li>
 *   <li>从方块掉落 → {@code Source.of("…", new ItemStack(Material.XXX))}；</li>
 *   <li>多方块产出 → 来源图标用<b>那个结构的核心</b>；</li>
 *   <li><b>暂时还没有获取方式</b> → {@code Source.pending()}（图标是屏障，文字写"待补"）。</li>
 * </ul>
 * <b>漏了会怎样</b>：{@code /touhou acquisition} 把它列进 {@code [MISS]}，
 * 控制台启动时也会 {@code warn}（{@link #applyFacades}）。这是刻意的 ——
 * 靠人记必漏，靠检查必不漏。
 *
 * <h2>三条硬规则</h2>
 * <ol>
 *   <li><b>文字不带颜色代码</b>：颜色统一由 {@link #PREFIX} / {@link #METHOD_COLOR} 给；</li>
 *   <li><b>前缀只有一个出处</b>：{@link #PREFIX}（命令核对时也读它，不许另抄一份）；</li>
 *   <li><b>只给本插件自己的物品加</b>：判据 {@link #isOurs}（id 前缀 {@code TOUHOU_}）。</li>
 * </ol>
 */
public final class Acquisition {

    private Acquisition() {
    }

    /**
     * 指南页"获取方式"那一行的前缀。
     *
     * <p>形状：{@code §7获取方式：} + {@link #ARROW}（{@code §8→ }）+ 方法文字。
     * ★ 颜色常量只在这里定义一次；方法文字本身<b>不带</b>颜色代码。
     */
    public static final String PREFIX = ChatColor.GRAY + "获取方式：";

    /** 前缀与方法文字之间的箭头（暗灰，纯装饰）。 */
    public static final String ARROW = ChatColor.DARK_GRAY + "→ ";

    /** 方法文字统一的颜色 —— <b>金色</b>（在槽 10 的深色背景上比白色更醒目）。 */
    public static final String METHOD_COLOR = ChatColor.GOLD.toString();

    /** 「暂时还没有获取方式」的占位文字。 */
    public static final String PENDING = "暂未开放 —— 目前只能由管理员发放（配方待补）";

    /** 「没有具体机器」时用的中性来源图标。 */
    private static final Material NEUTRAL_ICON = Material.TRIPWIRE_HOOK;

    /** 「暂未开放」的来源图标 —— 屏障最不容易被误认成某台机器。 */
    private static final Material PENDING_ICON = Material.BARRIER;

    /** 本插件物品 id 的前缀 —— {@link #isOurs} 的判据。 */
    public static final String ID_PREFIX = "TOUHOU_";

    /**
     * 本体 {@link RecipeType} 的键 → 机器显示名。
     *
     * <p>用途：给有配方的物品自动推断"在哪个台子合成"。键是 {@code RecipeType#getKey()}
     * （本体的是 {@code slimefun:<key>}）。
     */
    private static final Map<String, String> MACHINE_BY_RECIPE_TYPE_KEY = Map.ofEntries(
            Map.entry("slimefun:enhanced_crafting_table", "增强型工作台"),
            Map.entry("slimefun:magic_workbench", "魔法工作台"),
            Map.entry("slimefun:armor_forge", "盔甲锻造台"),
            Map.entry("slimefun:grind_stone", "磨石"),
            Map.entry("slimefun:smeltery", "冶炼炉"),
            Map.entry("slimefun:ore_crusher", "矿石粉碎机"),
            Map.entry("slimefun:gold_pan", "淘金盘"),
            Map.entry("slimefun:compressor", "压缩机"),
            Map.entry("slimefun:pressure_chamber", "压力室"),
            Map.entry("slimefun:heated_pressure_chamber", "加热压力室"),
            Map.entry("slimefun:ore_washer", "洗矿机"),
            Map.entry("slimefun:juicer", "榨汁机"),
            Map.entry("slimefun:ancient_altar", "古代祭坛"),
            Map.entry("slimefun:food_fabricator", "食品加工机"),
            Map.entry("slimefun:food_composter", "食品堆肥机"),
            Map.entry("slimefun:freezer", "冷冻机"),
            Map.entry("slimefun:refinery", "炼油厂"),
            Map.entry("slimefun:geo_miner", "GEO 矿机"),
            Map.entry("slimefun:nuclear_reactor", "核反应堆"));

    /** 一个"来源"：指南页槽 10 显示的图标 + 一句话说清途径。 */
    public static final class Source {

        /**
         * 图标来源。
         *
         * <p>★ <b>必须是惰性的（{@link java.util.function.Supplier}），不能直接存
         * {@code SlimefunItems.XXX}</b> —— 本类的 {@code SOURCE_BY_ID} 是静态初始化块，
         * 而 {@code SlimefunItems} 的静态初始化会调
         * {@code Slimefun.getMinecraftVersion()}（{@code Slimefun.instance()} 为 null 时抛异常，
         * 见 {@code Slimefun#validateInstance}）。解析延后到
         * {@link #applyFacades()}（那时插件已经启用、Slimefun 实例存在）就没事了。
         */
        private final java.util.function.Supplier<ItemStack> icon;

        private final String method;

        private Source(java.util.function.Supplier<ItemStack> icon, String method) {
            this.icon = icon;
            this.method = method;
        }

        /** 来源图标（每次都是新副本；没有图标时返回 {@code null}）。 */
        public ItemStack icon() {
            ItemStack out = icon == null ? null : icon.get();
            return out == null ? null : out.clone();
        }

        /** 途径文字（不带颜色代码）。 */
        public String method() {
            return method;
        }

        /**
         * 造一个来源。
         *
         * @param method 途径文字（<b>不带</b>颜色代码，例 {@code "在魔法工作台合成"}）
         * @param icon   槽 10 显示的图标（例：那个掉落方块 / 那台机器的模板）
         */
        public static Source of(String method, ItemStack icon) {
            return new Source(icon == null ? null : () -> icon, method);
        }

        /** 图标延后到第一次使用时才解析（本体 {@code SlimefunItems} 必须走这条）。 */
        public static Source deferred(String method, java.util.function.Supplier<ItemStack> icon) {
            return new Source(icon, method);
        }

        /** 用原版材质当图标。 */
        public static Source of(String method, Material icon) {
            return new Source(() -> new ItemStack(icon), method);
        }

        /**
         * 走<b>本体机器</b>合成：文字自动写"在&lt;机器名&gt;合成"，图标是那台机器。
         *
         * @param machine 本体机器的模板（{@code SlimefunItems.XXX}）
         */
        public static Source recipe(SlimefunItemStack machine) {
            String name = machine == null ? null : plainName(machine);
            if (name == null) {
                return of("合成获得（合成台待确认）", (ItemStack) null);
            }            return of("在" + name + "合成", machine);
        }

        /**
         * 走本体机器合成，但图标<b>延后解析</b> —— 供静态初始化块里安全书写。
         *
         * <p>用法：{@code Source.recipe("增强型工作台", () -> SlimefunItems.ENHANCED_CRAFTING_TABLE)}
         */
        public static Source recipe(String machineName,
                                    java.util.function.Supplier<SlimefunItemStack> machine) {
            return deferred("在" + machineName + "合成", machine::get);
        }

        /** "暂未开放"：图标屏障 + 占位文字。 */
        public static Source pending() {
            return of(PENDING, PENDING_ICON);
        }
    }

    /**
     * ★★★ <b>物品 id → 来源</b> —— 本表是全项目"这东西怎么来"的唯一出处。
     *
     * <p>写的时候问自己一句：<b>玩家第一次拿到它，是靠做什么？</b>
     * 答不上来就写 {@link Source#pending()}。
     */
    private static final Map<String, Source> SOURCE_BY_ID = new LinkedHashMap<>();

    /** 每个物品 id 挂上的门面 RecipeType（诊断与自证用）。 */
    private static final Map<String, RecipeType> FACADE_BY_ID = new LinkedHashMap<>();

    static {
        // ---- 材料 / 素材 -----------------------------------------------------
        // 炙热的灰烬：设计上是反应堆的产物，但那条机制还没做出来 ⇒ 待补
        SOURCE_BY_ID.put("TOUHOU_MATERIAL_LOGIC_SINGULARITY", Source.pending());
        SOURCE_BY_ID.put("TOUHOU_MATERIAL_ECHO_OF_ANOTHER_WORLD",
                Source.of("穿过维度之门时，把身上的 Slimefun 能量水晶化作 1 个（比例 1:1）",
                        Material.CRYING_OBSIDIAN));
        SOURCE_BY_ID.put("TOUHOU_MATERIAL_SPRING_MUD",
                Source.recipe("增强型工作台", () -> SlimefunItems.ENHANCED_CRAFTING_TABLE));
        SOURCE_BY_ID.put("TOUHOU_CHARACTER_SPRING_HERALD",
                Source.recipe("魔法工作台", () -> SlimefunItems.MAGIC_WORKBENCH));
        SOURCE_BY_ID.put("TOUHOU_MATERIAL_FALLEN_LEAVES",
                Source.of("用手或普通工具破坏树叶时 20% 掉落 2~7 个（剪刀与精准采集不掉）",
                        Material.OAK_LEAVES));
        SOURCE_BY_ID.put("TOUHOU_CHARACTER_MOMIJI_TENGU",
                Source.recipe("魔法工作台", () -> SlimefunItems.MAGIC_WORKBENCH));
        // 冰の妖精：魔法工作台合成（用户指定配方类型 MAGIC_WORKBENCH）。
        SOURCE_BY_ID.put("TOUHOU_CHARACTER_CIRNO",
                Source.recipe("魔法工作台", () -> SlimefunItems.MAGIC_WORKBENCH));
        // 雾中の妖精：魔法工作台合成（用户指定配方类型 MAGIC_WORKBENCH）。
        SOURCE_BY_ID.put("TOUHOU_CHARACTER_FAIRY_IN_MIST",
                Source.recipe("魔法工作台", () -> SlimefunItems.MAGIC_WORKBENCH));
        // POINT：增强型工作台合成（用户说的"强化工作台"= 这台机器）。
        SOURCE_BY_ID.put("TOUHOU_MATERIAL_POINT",
                Source.recipe("增强型工作台", () -> SlimefunItems.ENHANCED_CRAFTING_TABLE));
        // P引擎：增强型工作台合成（同上，用户说的"强化工作台"= 这台机器）；
        // ★ 这里只写"怎么来"，【不】写产出 8 个 —— 那是物品自己的数量口径，与获取方式无关。
        SOURCE_BY_ID.put("TOUHOU_MATERIAL_P_ENGINE",
                Source.recipe("增强型工作台", () -> SlimefunItems.ENHANCED_CRAFTING_TABLE));

        // ---- 单方块机器 ------------------------------------------------------
        SOURCE_BY_ID.put("TOUHOU_SIMPLE_MACHINE_HARVEST_TIME",
                Source.recipe("魔法工作台", () -> SlimefunItems.MAGIC_WORKBENCH));

        // ---- 多方块结构件 ----------------------------------------------------
        // 8 件构件目前是 null 配方（材料档位待定）⇒ 待补
        SOURCE_BY_ID.put("TOUHOU_COMPLEX_MACHINE_REACTOR_FRAME", Source.pending());
        SOURCE_BY_ID.put("TOUHOU_COMPLEX_MACHINE_REACTOR_SHIELD", Source.pending());
        SOURCE_BY_ID.put("TOUHOU_COMPLEX_MACHINE_REACTOR_STABILIZER", Source.pending());
        SOURCE_BY_ID.put("TOUHOU_COMPLEX_MACHINE_REACTOR_BASE", Source.pending());
        SOURCE_BY_ID.put("TOUHOU_COMPLEX_MACHINE_REACTOR_INPUT_PORT", Source.pending());
        SOURCE_BY_ID.put("TOUHOU_COMPLEX_MACHINE_REACTOR_OUTPUT_PORT", Source.pending());
        // ★ 两个核心：来源就是"它自己所在的那套多方块结构" ⇒ 图标用核心本身；
        //   而它们的门面由 TouhouRecipeTypes.REACTOR_CORE 专供
        //   （那条带着"搭建完整结构 + 材料清单"的说明），本类【不覆盖】它。
        SOURCE_BY_ID.put("TOUHOU_COMPLEX_MACHINE_UTSUHO_REACTOR_CORE",
                Source.of("在增强型工作台合成，再搭建完整的多方块结构（结构见物品描述）",
                        (ItemStack) null));     // null ⇒ 建立时回落到"本物品模板"

        // ---- 2026-09-24 起拿到【魔法工作台】真配方的 7 件 ------------------------
        // ★ 神社的木桩：原来是"配方待补"（Source.pending），现在有配方了。
        SOURCE_BY_ID.put("TOUHOU_COMPLEX_MACHINE_SHRINE_POST",
                Source.recipe("魔法工作台", () -> SlimefunItems.MAGIC_WORKBENCH));
        // ★ 赛钱箱：原来写的是"搭建完整的多方块结构后放入核心（核心件为神社的木桩）"，
        //   现在它自己能合成了 ⇒ 获取方式改成那台机器（搭结构仍然要做，但那是使用前提，
        //   不是"怎么拿到这件物品"）。★ 它仍是多方块核心，这句话的完整版在物品描述里。
        SOURCE_BY_ID.put("TOUHOU_COMPLEX_MACHINE_SAIZENBAKO",
                Source.recipe("魔法工作台", () -> SlimefunItems.MAGIC_WORKBENCH));

        // ---- 符卡 / 道具 -----------------------------------------------------
        SOURCE_BY_ID.put("TOUHOU_PARTY_ITEM_FANTASY_SEAL_CONVERGE",
                Source.recipe("增强型工作台", () -> SlimefunItems.ENHANCED_CRAFTING_TABLE));
        SOURCE_BY_ID.put("TOUHOU_PARTY_ITEM_MURDEROUS_LILY",
                Source.recipe("增强型工作台", () -> SlimefunItems.ENHANCED_CRAFTING_TABLE));

        // ---- POWER 系统 ------------------------------------------------------
        // ★★ 2026-09-24：五件从"配方待补（Source.pending）"全部改成魔法工作台合成。
        for (String id : List.of("TOUHOU_POWER_POWER_INTEGRATED_CORE",
                "TOUHOU_POWER_POWER_REPEATER", "TOUHOU_POWER_POWER_STORAGE_UNIT",
                "TOUHOU_POWER_DREAMCATCHER", "TOUHOU_POWER_POWER_SUPPLY_UNIT")) {
            SOURCE_BY_ID.put(id, Source.recipe("魔法工作台", () -> SlimefunItems.MAGIC_WORKBENCH));
        }

        // ---- INFO 组（代码内置的说明纸品） -----------------------------------
        // ★ 单一出处：本组物品只在这张表里登记一次，新增一张就加一个 id（不另起一行 put）。
        for (String id : List.of("TOUHOU_INFO_MODESHIFT", "TOUHOU_INFO_PLUGIN_MESSAGE",
                "TOUHOU_INFO_DECLARATION_1", "TOUHOU_INFO_DECLARATION_2",
                "TOUHOU_INFO_DECLARATION_3", "TOUHOU_INFO_TARTARIC_ACID",
                "TOUHOU_INFO_TEAM_SHANGHAI_ALICE", "TOUHOU_INFO_NING_MENG",
                "TOUHOU_INFO_MATL114")) {
            SOURCE_BY_ID.put(id, Source.of("代码内置的说明纸品（不通过合成获得）", Material.PAPER));
        }
    }

    // ------------------------------------------------------------------ 读取

    /** 这个 id 是不是本插件的物品（{@link #ID_PREFIX} 判据）。 */
    public static boolean isOurs(String id) {
        return id != null && id.startsWith(ID_PREFIX);
    }

    /** 显式登记的方法文字（本表里没有时返回 {@code null}）。 */
    public static String method(String id) {
        Source s = id == null ? null : SOURCE_BY_ID.get(id);
        return s == null ? null : s.method();
    }

    /**
     * 有配方的物品的获取方式由配方推断：{@code 在<机器名>合成}。
     *
     * @return 推断结果；配方类型不是本体机器（NULL / 门面 / 自定义 / 未知）时返回 {@code null}
     */
    public static String fromRecipeType(RecipeType type) {
        String name = machineName(type);
        return name == null ? null : "在" + name + "合成";
    }

    /** 配方类型指向的机器显示名（认不出返回 {@code null}）。 */
    private static String machineName(RecipeType type) {
        if (type == null || type.getKey() == null) {
            return null;
        }
        return MACHINE_BY_RECIPE_TYPE_KEY.get(type.getKey().toString());
    }

    /** 目标物品的获取方式：显式登记优先，其次按配方类型推断。 */
    public static String resolve(SlimefunItem item) {
        if (item == null) {
            return null;
        }
        String explicit = method(item.getId());
        return explicit != null ? explicit : fromRecipeType(item.getRecipeType());
    }

    /** 这一件物品的来源（显式登记优先；否则按配方类型现造一个"机器来源"）。 */
    public static Source resolveSource(SlimefunItem item) {
        if (item == null) {
            return null;
        }
        Source s = SOURCE_BY_ID.get(item.getId());
        if (s != null) {
            return s;
        }
        return machineSource(item.getRecipeType());
    }

    /** 配方类型指向的机器图标（本体机器；认不出返回 {@code null}）。 */
    private static Source machineSource(RecipeType type) {
        if (type == null || type.getKey() == null) {
            return null;
        }
        return switch (type.getKey().getKey()) {
            case "enhanced_crafting_table" ->
                    Source.recipe("增强型工作台", () -> SlimefunItems.ENHANCED_CRAFTING_TABLE);
            case "magic_workbench" ->
                    Source.recipe("魔法工作台", () -> SlimefunItems.MAGIC_WORKBENCH);
            case "ancient_altar" ->
                    Source.recipe("古代祭坛", () -> SlimefunItems.ANCIENT_ALTAR);
            default -> null;
        };
    }

    /** 指南页槽 10 那一行的完整文字：{@link #PREFIX} + {@link #ARROW} + 金色方法。 */
    public static String loreLine(String methodText) {
        return PREFIX + ARROW + METHOD_COLOR + methodText;
    }

    // ------------------------------------------------------------------ 诊断

    /** 诊断用：本表登记了多少条。 */
    public static int size() {
        return SOURCE_BY_ID.size();
    }

    /** 诊断用：本表登记的全部物品 id。 */
    public static List<String> registeredIds() {
        return new ArrayList<>(SOURCE_BY_ID.keySet());
    }

    /** 诊断用：登记为"待补"的物品 id。 */
    public static List<String> pendingIds() {
        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Source> e : SOURCE_BY_ID.entrySet()) {
            if (PENDING.equals(e.getValue().method())) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** 诊断用：物品当前真正的配方类型键。 */
    public static String recipeTypeKey(SlimefunItem item) {
        if (item == null || item.getRecipeType() == null || item.getRecipeType().getKey() == null) {
            return "(null)";
        }
        return item.getRecipeType().getKey().toString();
    }

    /** 诊断用：这个物品是不是被挂了本类的门面。 */
    public static boolean isDecorated(SlimefunItem item) {
        return item != null && FACADE_BY_ID.get(item.getId()) == item.getRecipeType();
    }

    /** 诊断用：这一件是不是用着"专用门面"（本项目自己的配方类型）。 */
    public static boolean hasDedicatedFacade(SlimefunItem item) {
        return item != null && hasProjectKey(item.getRecipeType());
    }

    /**
     * 配方类型的<b>可读描述</b> —— 所有诊断命令都该用它，别各自拼字符串。
     *
     * <p>★ 为什么需要：挂上门面之后 {@code getRecipeType().getKey()} 不再是
     * {@code slimefun:null}，而是 {@code touhou:acquire_<id>}。
     * 直接打印键会让人误以为"这物品有配方了" —— 这里补一句
     * 「获取方式门面（等价于无配方）」把结论说死，免得
     * {@code /touhou leaves proof} 那类"它变不回任何东西"的叙述自相矛盾。
     */
    public static String describeRecipeType(SlimefunItem item) {
        String key = recipeTypeKey(item);
        if (isDecorated(item)) {
            return key + "（获取方式门面，等价于无配方）";
        }
        if (hasDedicatedFacade(item)) {
            return key + "（项目专用门面，等价于无配方）";
        }
        return key;
    }

    /** 诊断用：没有挂门面的物品 id（已有专人门面的核心）。 */
    public static List<String> undecorated() {
        List<String> out = new ArrayList<>();
        for (String id : new TreeSet<>(SOURCE_BY_ID.keySet())) {
            if (!FACADE_BY_ID.containsKey(id)) {
                out.add(id);
            }
        }
        return out;
    }

    /** 供 {@code /touhou acquisition} 的说明段落。 */
    public static List<String> describe() {
        return List.of(
                "规则：所有本插件物品（id 前缀 " + ID_PREFIX + "）都要在 Acquisition.SOURCE_BY_ID 里登记【来源】",
                "落地：指南页槽 10 = 来源图标（机器 / 掉落方块 / 多方块核心 / 中性图标），"
                        + "其 lore = " + PREFIX + ARROW + "获取方式",
                "★ 物品本体的介绍【不动】—— 写的是指南页上那份说明",
                "有配方的物品：来源自动 = 那台机器（" + MACHINE_BY_RECIPE_TYPE_KEY.size() + " 种机器已可识别）",
                "暂时没有获取方式的：" + PENDING + "（图标屏障）",
                "漏登记 ⇒ 本命令列进 [MISS]，控制台启动时也会 warn");
    }

    // ------------------------------------------------------------------ 落地

    /**
     * 给<b>每一件</b>本插件物品挂"获取方式门面" —— 由
     * {@link AddSlimefunItems#setup} 在所有物品注册完之后调用。
     *
     * <p>★ 跳过两类：已经挂过本类门面的（重复启用），以及
     * {@link TouhouRecipeTypes} 里那两个专用的核心门面（它们带着
     * "搭建完整结构 + 材料清单"的说明，不能被覆盖成干巴巴一句获取方式）。
     */
    public static void applyFacades() {
        int applied = 0;
        int kept = 0;
        List<String> missing = new ArrayList<>();
        for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
            if (item == null || !isOurs(item.getId())) {
                continue;
            }
            Source source = resolveSource(item);
            if (source == null) {
                missing.add(item.getId());
                continue;
            }
            // ★ 已经有一个"更具体的"槽 10 说明就不能覆盖，三种情形：
            //   ① 有真实配方（非 NULL）—— 指南页槽 10 该显示那台机器、3×3 画真实图案，
            //      换成获取方式门面等于把整张合成表弄丢（真实踩点：反应堆核心走的是
            //      RecipeType.ENHANCED_CRAFTING_TABLE，被覆盖成门面后就只剩一句文字了）；
            //   ② 本类已经挂过的门面（重复调用）；
            //   ③ 本项目自己的门面类型（TouhouRecipeTypes 的 reactor_core /
            //      dimension_shuttle —— 它们带着"搭建完整结构 + 材料清单"之类的定制文字）。
            //   ★★ 判据必须是 **键的前缀**，不能是 `type == TouhouRecipeTypes.REACTOR_CORE`：
            //      本方法跑在 AddSlimefunItems.setup() 里，而注册物品那一刻 TouhouRecipeTypes
            //      的静态字段可能【还没被赋值】（null），拿常量比会漏判。
            //      （2026-09-24 起赛钱箱不再有这个专用门面 —— 它拿到了真配方，走情形 ①。）
            if ((item.getRecipeType() != null && item.getRecipeType() != RecipeType.NULL)
                    || isDecorated(item) || hasProjectKey(item.getRecipeType())) {
                kept++;
                continue;
            }
            ItemStack icon = source.icon();
            if (icon == null) {
                // null 图标 ⇒ 回落：核心用自己，赛钱箱用它的核心件
                icon = fallbackIcon(item);
            }
            if (icon == null || icon.getType().isAir()) {
                missing.add(item.getId());
                continue;
            }
            RecipeType facade = facade(item.getId(), icon, source.method());
            item.setRecipeType(facade);
            FACADE_BY_ID.put(item.getId(), facade);
            applied++;
        }
        Log.info("[ACQUIRE] 获取方式门面已挂 " + applied + " 件，保留专用门面 " + kept
                + " 件；本表登记 " + SOURCE_BY_ID.size() + " 条");
        if (!missing.isEmpty()) {
            // ★ 刻意 warn（不受 console-info 影响）：漏登记是"以后加物品会重犯"的错，
            //   必须在控制台留下线索。见类注释里给子代理的那一节。
            Log.warn("[ACQUIRE] ★ 以下物品没有登记来源 —— "
                    + "请到 Acquisition.SOURCE_BY_ID 补一行：" + missing);
        }
    }

    /** 这个配方类型的键是不是本插件自己的（{@code touhou:...} ⇒ 专用门面，别覆盖）。 */
    private static boolean hasProjectKey(RecipeType type) {
        return type != null && type.getKey() != null
                && Touhou.getInstance() != null
                && Touhou.getInstance().getName().equalsIgnoreCase(type.getKey().getNamespace());
    }

    /**
     * 来源图标写 {@code null} 时的回落。
     *
     * <p>反应堆核心（用它自己）就走这条路 —— 这样"多方块机器的来源 = 那个结构的核心"
     * 这条口径在代码里是显式的。
     *
     * <p>⚠ 赛钱箱那一支<b>现在已经走不到</b>了：它 2026-09-24 起有真配方
     * （魔法工作台），{@code applyFacades()} 在"有真实配方"那一关就跳过了它。
     * 这一支保留着，是为了"哪天真给它填回一个 {@code null} 图标的来源"时口径还在。
     */
    private static ItemStack fallbackIcon(SlimefunItem item) {
        if ("TOUHOU_COMPLEX_MACHINE_SAIZENBAKO".equals(item.getId())) {
            SlimefunItem post = SlimefunItem.getById("TOUHOU_COMPLEX_MACHINE_SHRINE_POST");
            if (post != null && post.getItem() != null) {
                return post.getItem();
            }
        }
        return item.getItem();
    }

    /**
     * 造一个门面 RecipeType：图标 = 来源，说明 = 获取方式。
     *
     * <p>{@code key} 用物品 id 的小写（{@code touhou:acquire_<id>}）—— 唯一、可预测、
     * 不与 {@link TouhouRecipeTypes} 里那几个撞。
     *
     * <p>★ 构造器选 {@code (NamespacedKey, ItemStack, BiConsumer, String...)} 且
     * <b>callback 传 {@code null}</b> ⇒ 不注册任何配方（见类注释）。
     * ★ 名字传 {@code null}：{@code CustomItemStack} 只在 name 非 null 时才覆盖显示名，
     * 于是<b>来源图标自己的名字被保留</b>（"魔法工作台" / "旧地狱-灵乌路空反应堆"…），
     * lore 换成获取方式那一行。
     */
    private static RecipeType facade(String itemId, ItemStack icon, String methodText) {
        NamespacedKey key = new NamespacedKey(Touhou.getInstance(),
                "acquire_" + itemId.toLowerCase(Locale.ROOT));
        return new RecipeType(key, icon, null, loreLine(methodText));
    }

    /**
     * 诊断：把所有本插件物品的"获取方式标注情况"过一遍。
     *
     * @return 每项一行，形如 {@code [OK] <id> 来源=<图标材质> 方式=<method> 门面=<key>}
     */
    public static List<String> verify() {
        List<String> out = new ArrayList<>();
        List<String> noSource = new ArrayList<>();
        for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
            if (item == null || !isOurs(item.getId())) {
                continue;
            }
            Source source = resolveSource(item);
            if (source == null) {
                noSource.add(item.getId());
                out.add("[MISS] " + item.getId() + "  ⇒ 本表没登记、也无法由配方类型推断");
                continue;
            }
            boolean facade = isDecorated(item);
            boolean dedicated = !facade && hasDedicatedFacade(item);
            out.add("[OK] " + item.getId()
                    + "  方式=" + source.method()
                    + "  来源=" + sourceLabel(facade, dedicated, source)
                    + "  门面=" + (facade ? recipeTypeKey(item)
                            : dedicated ? "专用（" + recipeTypeKey(item) + "）" : "-"));
        }
        out.add("---- 汇总：本表 " + SOURCE_BY_ID.size() + " 条；"
                + "未登记来源 " + noSource.size() + " 件 " + noSource);
        return out;
    }

    /**
     * 诊断用：槽 10 上那个图标的可读身份。
     *
     * <p>★ 为什么要专门写一个：{@code icon.getType()} 只给出<b>材质</b>，
     * 而本体"魔法工作台"与"增强型工作台"的材质<b>都是工作台</b>
     * （{@code SlimefunItems.MAGIC_WORKBENCH} 的 type 就是 {@code CRAFTING_TABLE}）
     * ⇒ 只看材质根本分不清是哪台机器（真实踩点：第一次验证时两件都显示 CRAFTING_TABLE）。
     * 这里优先打<b>来源物品自己的粘液 id</b>，那才是唯一的。
     */
    private static String sourceLabel(boolean facade, boolean dedicated, Source source) {
        ItemStack icon = source == null ? null : source.icon();
        if (icon == null) {
            return dedicated ? "(专用门面自带图标)" : "(无)";
        }
        String sticky = Slimefun.getItemDataService().getItemData(icon.getItemMeta()).orElse(null);
        String name = plainOf(icon);
        return icon.getType() + (sticky == null ? "" : "[" + sticky + "]") + " \"" + name + "\"";
    }

    /** 供 {@code /touhou acquisition <id>} 单件详情。 */
    public static List<String> detail(SlimefunItem item) {
        List<String> out = new ArrayList<>();
        Source source = resolveSource(item);
        out.add("获取方式 = " + (source == null ? "★未登记（请到 Acquisition.SOURCE_BY_ID 补一行）"
                : source.method()));
        out.add("指南页槽 10 文字 = " + (source == null ? "-" : loreLine(source.method())));
        boolean facade = isDecorated(item);
        boolean dedicated = !facade && hasDedicatedFacade(item);
        out.add("配方类型 = " + describeRecipeType(item)
                + "   门面 = " + (facade ? "获取方式门面" : dedicated ? "专用门面" : "无"));
        out.add("槽 10 来源 = " + sourceLabel(facade, dedicated, source));
        return out;
    }

    /** 取一个物品栈的纯文本显示名（没有名字时回落到材质名）。 */
    private static String plainOf(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || meta.getDisplayName() == null || meta.getDisplayName().isEmpty()) {
            return stack.getType().name();
        }
        return ChatColor.stripColor(meta.getDisplayName());
    }

    /** 取一个模板的纯文本名字（给 {@link Source#recipe} 拼"在…合成"用）。 */
    private static String plainName(ItemStack stack) {
        return plainOf(stack);
    }
}
