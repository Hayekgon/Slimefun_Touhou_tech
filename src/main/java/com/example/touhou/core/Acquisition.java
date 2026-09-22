package com.example.touhou.core;

import com.example.touhou.Touhou;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * ★★★ <b>获取方式总表</b> —— 「每一件物品都要写明它是怎么来的」这件事的<b>唯一出处</b>。
 *
 * <h2>为什么要有这个类（用户口径，2026-09-22）</h2>
 * 原来的问题：没有配方的物品（配方类型 {@link RecipeType#NULL}）在指南页里
 * <b>槽 10 是一片空气</b>（{@code RecipeType.NULL.getItem(Player)} 返回空气），
 * 玩家看不出这件东西<b>怎么获得</b>。有配方的物品虽然槽 10 有机器图标，
 * 但"这台机器是什么"也要玩家自己认。
 *
 * <p>用户要求：<b>所有物品，哪怕是 null 配方，也标注获取方式</b>；而且这是一条
 * <b>长期约定</b> —— 以后新加的物品 / 机器，一旦有了获取方式，同样照此处理。
 *
 * <h2>落地方式（两处，都从本表读，不允许各写一份）</h2>
 * <ol>
 *   <li><b>物品 lore 末行</b>：{@code §7获取方式：<颜色><方法>} —— 由
 *       {@link AddItems#applyAcquisitionLore()} 在 {@code AddItems.setup()} 末尾追加。
 *       这样<b>指南页</b>与<b>拿在手里</b>看到的是同一句话。</li>
 *   <li><b>指南页槽 10</b>：没有配方的物品挂一个"门面型" {@link RecipeType}
 *       （判定见 {@link #facadeFor}），槽 10 会显示这件物品自己的图标 +
 *       本表的方法文字。有配方的物品槽 10 已经显示机器图标，不再替换。</li>
 * </ol>
 *
 * <h2>★ 给以后的子代理：加物品时必须做的一件事</h2>
 * 在 {@link #METHOD_BY_ID} 里给新物品加一行。
 * <ul>
 *   <li>有配方 → 写 {@code "在<机器名>合成"}（机器名照 {@link #MACHINE_BY_RECIPE_TYPE_KEY} 表）；</li>
 *   <li>没有配方但有获取机制 → 写清楚机制（参考「落叶」「另一个世界的回响」那两行）；</li>
 *   <li><b>暂时还没有获取方式</b> → 写 {@link #PENDING}，并且<b>把物品也登记进
 *       {@link #PENDING_LORE_ONLY}</b>（不挂门面，因为门面会显示"待补"这种无意义的图标文字）。</li>
 * </ul>
 * <b>漏了会怎样</b>：{@code /touhou acquisition} 会把它列进"未标注获取方式"，
 * 控制台启动时也会报警（{@link #applyFacades}）。这是刻意的 —— 靠人记必漏，靠检查必不漏。
 *
 * <h2>三条硬规则</h2>
 * <ol>
 *   <li><b>方法文字不带颜色代码</b>：颜色由 {@link #METHOD_COLOR} 统一给。
 *       这样"所有物品的获取方式一个颜色"是改一个常量的事。</li>
 *   <li><b>前缀只有一个出处</b>：{@link #PREFIX}。命令核对 lore 时也用它，不许另抄一份字符串。</li>
 *   <li><b>只给本插件自己的物品加</b>：判据 {@link #isOurs}（id 前缀 {@code TOUHOU_}）。
 *       不碰 Slimefun 本体与别的附属。</li>
 * </ol>
 */
public final class Acquisition {

    private Acquisition() {
    }

    /** 获取方式行的前缀 —— <b>只有一个出处</b>（命令核对 lore 时也读它）。 */
    public static final String PREFIX = ChatColor.GRAY + "获取方式：" + ChatColor.WHITE;

    /**
     * 方法文字统一的颜色 —— <b>白色</b>。
     *
     * <p>刻意不用"取物品名第一个字符的颜色"那种花活：物品名有逐字符渐变
     * （{@code §x§R§R§G§G§B§B} 序列），抠第一个字符的颜色既脆又难读，
     * 而且"获取方式这一行颜色随物品变"反而让它在视觉上不像一类信息。
     */
    public static final String METHOD_COLOR = ChatColor.WHITE.toString();

    /** 「暂时还没有获取方式」的占位文字（以后有了配方/机制就替换掉它）。 */
    public static final String PENDING = "（待补 —— 该物品目前只能由管理员发放）";

    /** 本插件物品 id 的前缀 —— {@link #isOurs} 的判据。 */
    public static final String ID_PREFIX = "TOUHOU_";

    /**
     * 本体 {@link RecipeType} 的键 → 机器显示名。
     *
     * <p>用途：给有配方的物品自动推断"在哪个台子合成"。键是
     * {@code RecipeType#getKey()}（本体的是 {@code slimefun:<key>}）。
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

    /**
     * ★★★ <b>物品 id → 获取方式</b> —— 本表是全项目"这东西怎么来"的唯一出处。
     *
     * <p>写的时候问自己一句：<b>玩家第一次拿到它，是靠做什么？</b>
     * 答不上来就写 {@link #PENDING}。
     */
    private static final Map<String, String> METHOD_BY_ID = new LinkedHashMap<>();

    /**
     * 只有"待补"占位、<b>不挂门面</b>的物品 id。
     *
     * <p>为什么单独一张表：门面会把方法文字显示在指南页槽 10 的图标上，
     * 而"待补 —— 只能由管理员发放"这句话做成图标只是噪音。
     * 它们该有的是<b>真正的配方</b>（用户尚未给材料档位），拿到配方时
     * 把 {@link #METHOD_BY_ID} 里那一行换成"在…合成"、并从本表删掉即可。
     */
    private static final Map<String, String> PENDING_LORE_ONLY = new LinkedHashMap<>();

    /** 每个物品 id 挂上的门面 RecipeType（诊断与自证用）。 */
    private static final Map<String, RecipeType> FACADE_BY_ID = new LinkedHashMap<>();

    static {
        // ---- 材料 / 素材 -----------------------------------------------------
        // 炙热的灰烬（原名逻辑奇点）：设计上是反应堆的产物，但那条机制还没做出来
        // ⇒ 目前只能由管理员发放，**"待补"且不挂门面**（挂上去只会显示一句"待补"当图标，是噪音）。
        putPending("TOUHOU_MATERIAL_LOGIC_SINGULARITY");
        put("TOUHOU_MATERIAL_ECHO_OF_ANOTHER_WORLD",
                "穿过维度之门时，把身上的 Slimefun 能量水晶化作 1 个（主世界 ↔ 地狱，比例 1:1）");
        put("TOUHOU_MATERIAL_SPRING_MUD", "在增强型工作台合成（8 个泥土 + 蒲公英 + 虞美人）");
        put("TOUHOU_MATERIAL_LILY_WHITE", "在魔法工作台合成（8 个春泥 + 水桶，一次产出 2 个）");
        put("TOUHOU_MATERIAL_FALLEN_LEAVES",
                "用手或普通工具破坏树叶时 20% 掉落 2~7 个（剪刀与精准采集不掉）");

        // ---- 单方块机器 ------------------------------------------------------
        put("TOUHOU_SIMPLE_MACHINE_HARVEST_TIME", "在魔法工作台合成（骨块 + 小麦 + 小麦种子 + 另一个世界的回响）");

        // ---- 多方块结构件 ----------------------------------------------------
        // ⚠ 下面 8 件目前是 null 配方（用户尚未给材料档位），只能由管理员发放。
        putPending("TOUHOU_COMPLEX_MACHINE_REACTOR_FRAME");
        putPending("TOUHOU_COMPLEX_MACHINE_REACTOR_SHIELD");
        putPending("TOUHOU_COMPLEX_MACHINE_REACTOR_STABILIZER");
        putPending("TOUHOU_COMPLEX_MACHINE_REACTOR_BASE");
        putPending("TOUHOU_COMPLEX_MACHINE_REACTOR_INPUT_PORT");
        putPending("TOUHOU_COMPLEX_MACHINE_REACTOR_OUTPUT_PORT");
        putPending("TOUHOU_COMPLEX_MACHINE_SHRINE_POST");
        // 核心按"多方块的核心件"惯例给配方（增强型工作台）；对应的门面由
        // TouhouRecipeTypes.REACTOR_CORE / SAIZENBAKO 提供，本类不重复挂。
        put("TOUHOU_COMPLEX_MACHINE_UTSUHO_REACTOR_CORE", "在增强型工作台合成");
        put("TOUHOU_COMPLEX_MACHINE_SAIZENBAKO", "在增强型工作台合成（结构见物品描述）");

        // ---- 符卡 / 道具 -----------------------------------------------------
        put("TOUHOU_PARTY_ITEM_FANTASY_SEAL_CONVERGE", "在增强型工作台合成");
        put("TOUHOU_PARTY_ITEM_MURDEROUS_LILY", "在增强型工作台合成");

        // ---- POWER 系统 ------------------------------------------------------
        putPending("TOUHOU_POWER_POWER_INTEGRATED_CORE");
        putPending("TOUHOU_POWER_POWER_REPEATER");
        putPending("TOUHOU_POWER_POWER_STORAGE_UNIT");
        putPending("TOUHOU_POWER_DREAMCATCHER");
        putPending("TOUHOU_POWER_POWER_SUPPLY_UNIT");

        // ---- INFO 组（代码内置的说明纸品） -----------------------------------
        // "获取方式"这一栏对它们是"不需要获取"，照实写，免得玩家在指南里找不到入手途径。
        put("TOUHOU_INFO_MODESHIFT", "代码内置的 GUI 功能件（不通过合成获得）");
        put("TOUHOU_INFO_PLUGIN_MESSAGE", "代码内置的说明纸品（不通过合成获得）");
        put("TOUHOU_INFO_DECLARATION_1", "代码内置的说明纸品（不通过合成获得）");
        put("TOUHOU_INFO_DECLARATION_2", "代码内置的说明纸品（不通过合成获得）");
        put("TOUHOU_INFO_DECLARATION_3", "代码内置的说明纸品（不通过合成获得）");
        put("TOUHOU_INFO_TARTARIC_ACID", "代码内置的说明纸品（不通过合成获得）");
        put("TOUHOU_INFO_TEAM_SHANGHAI_ALICE", "代码内置的说明纸品（不通过合成获得）");
        put("TOUHOU_INFO_NING_MENG", "代码内置的说明纸品（不通过合成获得）");
    }

    private static void put(String id, String method) {
        METHOD_BY_ID.put(id, method);
    }

    /** 登记一个"暂时还没有获取方式"的物品：lore 写占位，但不挂门面。 */
    private static void putPending(String id) {
        METHOD_BY_ID.put(id, PENDING);
        PENDING_LORE_ONLY.put(id, PENDING);
    }

    // ------------------------------------------------------------------ 读取

    /** 这个 id 是不是本插件的物品（{@link #ID_PREFIX} 判据）。 */
    public static boolean isOurs(String id) {
        return id != null && id.startsWith(ID_PREFIX);
    }

    /**
     * 取某个物品的获取方式文字（<b>不带</b> {@link #PREFIX}、<b>不带</b>颜色代码）。
     *
     * @return 方法文字；本表里没登记时返回 {@code null}（调用方据此报警）
     */
    public static String method(String id) {
        return id == null ? null : METHOD_BY_ID.get(id);
    }

    /**
     * 有配方的物品的获取方式由配方推断：{@code 在<机器名>合成}。
     *
     * @return 推断结果；配方类型不是本体机器（NULL / 门面 / 自定义 / 未知）时返回 {@code null}
     */
    public static String fromRecipeType(RecipeType type) {
        if (type == null) {
            return null;
        }
        NamespacedKey key = type.getKey();
        if (key == null) {
            return null;
        }
        String machine = MACHINE_BY_RECIPE_TYPE_KEY.get(key.toString());
        return machine == null ? null : "在" + machine + "合成";
    }

    /** 目标物品的获取方式：显式登记优先，其次按配方类型推断。 */
    public static String resolve(SlimefunItem item) {
        if (item == null) {
            return null;
        }
        String explicit = METHOD_BY_ID.get(item.getId());
        if (explicit != null) {
            return explicit;
        }
        return fromRecipeType(item.getRecipeType());
    }

    /** 成品 lore 行（{@link #PREFIX} + 颜色 + 方法）—— lore 注入与命令核对共用。 */
    public static String loreLine(String method) {
        return PREFIX + METHOD_COLOR + method;
    }

    /** 诊断用：本表登记了多少条。 */
    public static int size() {
        return METHOD_BY_ID.size();
    }

    /** 诊断用：本表登记的全部物品 id（只读副本）。 */
    public static List<String> registeredIds() {
        return new ArrayList<>(METHOD_BY_ID.keySet());
    }

    /** 诊断用：登记了"待补"占位的物品 id。 */
    public static List<String> pendingIds() {
        return new ArrayList<>(PENDING_LORE_ONLY.keySet());
    }

    /** 这件物品是不是"待补、刻意不挂门面"。 */
    public static boolean isPending(String id) {
        return id != null && PENDING_LORE_ONLY.containsKey(id);
    }

    /** 诊断用：物品当前真正的配方类型键（门面换上之后读到的就是门面的键）。 */
    public static String recipeTypeKey(SlimefunItem item) {
        if (item == null || item.getRecipeType() == null || item.getRecipeType().getKey() == null) {
            return "(null)";
        }
        return item.getRecipeType().getKey().toString();
    }

    /**
     * 配方类型的<b>可读描述</b> —— 所有诊断命令都该用它，别各自拼字符串。
     *
     * <p>★ 为什么需要：挂上门面之后 {@code getRecipeType().getKey()} 不再是
     * {@code slimefun:null}，而是 {@code touhou:acquire_<id>}。
     * 直接打印键会让人误以为"这物品有配方了" —— 这里补一句
     * 「获取方式门面（等价于无配方）」把结论说死，
     * 免得 {@code /touhou leaves proof} 那类"证明它变不回任何东西"的叙述自相矛盾。
     */
    public static String describeRecipeType(SlimefunItem item) {
        String key = recipeTypeKey(item);
        if (isDecorated(item)) {
            return key + "（获取方式门面，等价于无配方）";
        }
        return key;
    }

    /** 诊断用：这个物品是不是被挂了本类的门面。 */
    public static boolean isDecorated(SlimefunItem item) {
        return item != null && FACADE_BY_ID.get(item.getId()) == item.getRecipeType();
    }

    /** 诊断用：没挂门面的那些物品 id（有配方、已有专人门面、或"待补"占位）。 */
    public static List<String> undecorated() {
        List<String> out = new ArrayList<>();
        for (String id : new TreeSet<>(METHOD_BY_ID.keySet())) {
            if (!FACADE_BY_ID.containsKey(id)) {
                out.add(id);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 落地

    /**
     * 追加获取方式 lore —— 由 {@link AddItems#applyAcquisitionLore()} 调用，
     * 时机在 {@code AddItems.setup()} 的<b>最末尾</b>。
     *
     * @param template 物品模板（{@link AddItems} 里那些 {@code public static} 字段）
     * @param id       该模板的粘液 id
     */
    static void appendLore(SlimefunItemStack template, String id) {
        if (template == null || id == null) {
            return;
        }
        String method = METHOD_BY_ID.get(id);
        if (method == null) {
            return;
        }
        ItemMeta meta = template.getItemMeta();
        if (meta == null) {
            return;
        }
        List<String> lore = meta.getLore() == null
                ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        String line = loreLine(method);
        for (String existing : lore) {
            if (existing != null && existing.startsWith(PREFIX)) {
                return;     // 已经有一行了，绝不重复追加
            }
        }
        lore.add(line);
        meta.setLore(lore);
        template.setItemMeta(meta);
    }

    /**
     * 给<b>没有配方</b>的物品挂门面型 {@link RecipeType} —— 由
     * {@link AddSlimefunItems#setup} 在所有物品注册完之后调用。
     *
     * <h2>门面为什么不会把物品变成"可合成"</h2>
     * {@code SlimefunItem#load()} 会调 {@code RecipeType#register(recipe, output)}，
     * 而那个方法只有两条路：{@code registerConsumer != null} 时调回调；
     * 否则找 {@code SlimefunItem.getById(machine)} 是不是 {@code MultiBlockMachine}。
     * 本类的门面<b>不传回调</b>，而 {@code machine} = 物品自己的 id
     * （{@code RecipeType} 那条吃 {@link SlimefunItemStack} 的构造器就是这么设的）——
     * 物品自己不是 {@code MultiBlockMachine} ⇒ <b>两条路都不通</b>，什么都不注册。
     * 再加上配方数组本来就是 9 格全空，任何台子都摆不出来。
     *
     * <p>★ 换上之后 {@code getRecipeType()} 读到的就是门面（键 {@code touhou:acquire_<id>}），
     * 指南页槽 10 因此显示"物品自己的图标 + 获取方式"。
     */
    public static void applyFacades() {
        int applied = 0;
        List<String> missing = new ArrayList<>();
        for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
            if (item == null || !isOurs(item.getId())) {
                continue;
            }
            if (METHOD_BY_ID.get(item.getId()) == null
                    && fromRecipeType(item.getRecipeType()) == null) {
                missing.add(item.getId());
                continue;
            }
            if (PENDING_LORE_ONLY.containsKey(item.getId())) {
                continue;               // "待补"占位不挂门面（显示出来只是噪音）
            }
            if (item.getRecipeType() != null && item.getRecipeType() != RecipeType.NULL) {
                continue;               // 有配方（或已有专人门面）的：槽 10 已经是机器图标，不替换
            }
            String method = resolve(item);
            if (method == null || item.getItem() == null) {
                continue;
            }
            RecipeType facade = facade(item.getId(), item.getItem(), method);
            item.setRecipeType(facade);
            FACADE_BY_ID.put(item.getId(), facade);
            applied++;
        }
        Log.info("[ACQUIRE] 获取方式门面已挂 " + applied + " 件；"
                + "本表登记 " + METHOD_BY_ID.size() + " 条");
        if (!missing.isEmpty()) {
            // ★ 刻意 warn（不受 console-info 影响）：漏登记是"以后加物品会重犯"的错，
            //   必须在控制台留下线索。见类注释里给子代理的那一节。
            Log.warn("[ACQUIRE] ★ 以下物品既没有显式获取方式、也无法从配方类型推断 —— "
                    + "请到 Acquisition.METHOD_BY_ID 补一行：" + missing);
        }
    }

    /**
     * 造一个门面 RecipeType：图标 = 物品自己，说明 = 获取方式。
     *
     * <p>{@code key} 用物品 id 的小写（{@code touhou:acquire_touhou_material_...}）——
     * 唯一、可预测、不会与 {@link TouhouRecipeTypes} 里那几个撞。
     *
     * <p>★ 构造器选的是 {@code (NamespacedKey, ItemStack, BiConsumer, String...)}
     * 且 <b>callback 传 {@code null}</b> —— 这一条是"门面不会注册任何配方"的关键
     * （见 {@link #applyFacades()} 的说明）。
     * ★ 运行时 jar（Slimefun-2026.07）与编译依赖（Slimefun4-2025.1）上这条构造器
     * <b>都存在且签名一致</b>（已用 {@code javap} 逐条核对）。
     */
    private static RecipeType facade(String itemId, ItemStack icon, String method) {
        NamespacedKey key = new NamespacedKey(Touhou.getInstance(),
                "acquire_" + itemId.toLowerCase(java.util.Locale.ROOT));
        return new RecipeType(key, icon, null, PREFIX + method);
    }

    /**
     * 诊断：把所有本插件物品的"获取方式标注情况"过一遍。
     *
     * @return 每项一行，形如 {@code [OK] <id> 方式=<method> 门面=<key>}
     */
    public static List<String> verify() {
        List<String> out = new ArrayList<>();
        List<String> noMethod = new ArrayList<>();
        for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
            if (item == null || !isOurs(item.getId())) {
                continue;
            }
            String method = resolve(item);
            if (method == null) {
                noMethod.add(item.getId());
                out.add("[MISS] " + item.getId() + "  ⇒ 本表没登记、也无法由配方类型推断");
                continue;
            }
            boolean loreOk = hasLore(item);
            boolean facade = isDecorated(item);
            boolean pending = isPending(item.getId());
            out.add("[" + (loreOk ? "OK" : "LORE?") + "] " + item.getId()
                    + "  方式=" + method
                    + "  lore=" + (loreOk ? "有" : "★缺")
                    + "  门面=" + (facade ? recipeTypeKey(item) : "-")
                    + (pending && facade ? "  ★★异常：待补项不该有门面" : ""));
        }
        out.add("---- 汇总：本表 " + METHOD_BY_ID.size() + " 条；"
                + "未标注获取方式 " + noMethod.size() + " 件 " + noMethod);
        return out;
    }

    /** 这件物品的模板 lore 里有没有获取方式行。 */
    public static boolean hasLore(SlimefunItem item) {
        if (item == null || item.getItem() == null) {
            return false;
        }
        ItemMeta meta = item.getItem().getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return false;
        }
        for (String line : meta.getLore()) {
            if (line != null && line.startsWith(PREFIX)) {
                return true;
            }
        }
        return false;
    }

    /** 供 {@code /touhou acquisition} 的说明段落。 */
    public static List<String> describe() {
        return List.of(
                "规则：所有本插件物品（id 前缀 " + ID_PREFIX + "）都要在 Acquisition.METHOD_BY_ID 里登记获取方式",
                "落地①：物品 lore 末行 " + PREFIX + "<方法>（指南页与拿在手里是同一句话）",
                "落地②：没有配方的物品挂门面 RecipeType，指南页槽 10 显示物品图标 + 获取方式",
                "有配方的物品：槽 10 保持机器图标；方法文字由配方类型推断（" + MACHINE_BY_RECIPE_TYPE_KEY.size() + " 种机器）",
                "暂时没有获取方式的：" + PENDING + "，并登记进 PENDING_LORE_ONLY（不挂门面）",
                "漏登记 ⇒ 本命令列进 [MISS]，控制台启动时也会 warn");
    }
}
