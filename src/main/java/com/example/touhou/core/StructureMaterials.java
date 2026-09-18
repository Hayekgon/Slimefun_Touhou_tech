package com.example.touhou.core;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多方块结构的<b>建造材料清单</b> —— 从一个「层图 + legend」直接算出"要摆哪些方块、各多少个"。
 *
 * <h2>★ 数据来源：{@link AddonConfig} 的层图，<b>不是</b>结构实例</h2>
 * 这一点是硬约束，理由是<b>时机</b>：物品的 lore 在 {@link AddItems#setup()} 里构造，
 * 而 {@code Touhou#onEnable} 的顺序是
 * <pre>
 *   AddGroups.setup → AddItems.setup → AddSlimefunItems.setup → … → ReactorManager.structure()
 * </pre>
 * 也就是说，构造 lore 的那一刻 {@link ReactorManager#structure()} /
 * {@link SaizenbakoStructure#get()} <b>还没被创建</b>（它们要等 {@code onEnable} 后半段）。
 * 那时候去取结构实例，轻则拿到 {@code null}、重则整个插件启用失败。
 * 而 {@link AddonConfig#structureLayers} / {@link AddonConfig#saizenLayers}
 * 在 {@link AddonConfig#load()} 里就已经填好了（{@code saveDefaultConfig()} 之后立刻可用），
 * 所以这里<b>只认层图数据</b>，一辈子不碰结构实例。
 *
 * <h2>统计规则</h2>
 * <ul>
 *   <li>沿"层 → 行 → 字符"逐格走一遍，<b>只数需要放置的方块</b>；</li>
 *   <li>{@code nu}（以及 {@code AIR} / {@code CAVE_AIR} / {@code VOID_AIR}）是
 *       "这一格必须是空的"，<b>不算材料</b>（判据与 {@link ReactorStructure#isAirRequirement} 同源）；</li>
 *   <li>核心自己那一格（保留字符 {@link #CORE_CHAR}）也不算材料 —— 它是核心本身；</li>
 *   <li><b>同一个 part id 合并计数</b>（{@code 44 × 框架} 而不是 44 行"框架"）。</li>
 * </ul>
 * 于是反应堆那份 5×5×5 层图会算成「44 框架 + 36 保护罩标签 + 9 基座 + 9 稳定器 = 98 个构件」，
 * 与 {@code /touhou proj} 报的"可画 98 格"完全对得上（两条路径读的是同一份层图）。
 *
 * <h2>{@code #标签} / Material 名 / 粘液 id —— 三路都要能翻成可读名字</h2>
 * legend 的值有三种写法（见 {@link AddonConfig#structureLegend}），
 * {@link #nameOf(String)} 按顺序处理：
 * <ol>
 *   <li>{@code #标签} → {@link ItemTags} 的<b>默认展示件</b>（没指定就取成员里第一个能解析的），
 *       再拿那个 id 去走下面两条路 —— 反应堆的 {@code #touhou:reactor_shell}
 *       于是显示成「旧地狱-反应堆保护罩」（玩家明确指定的代表件），
 *       而不是"成员里恰好排第一的那个"（登记顺序只是注册顺序的副产品）；</li>
 *   <li>粘液 id → 已注册的 {@link SlimefunItem} 的显示名；物品还没注册时退回
 *       {@link AddItems} 的模板表（见 {@link #templates()}）；</li>
 *   <li>Material 名 → 该原版物品的本地化名（拿不到就退回枚举名）。</li>
 * </ol>
 * 一个都认不出来时<b>如实回显原始 part id</b>（不抛异常、也不猜）——
 * 层图写错了应该能在物品描述里一眼看出来，而不是让插件崩掉。
 */
public final class StructureMaterials {

    /**
     * 层图里"核心自己那一格"的保留字符。
     *
     * <p>与 {@link LayeredReactorStructure} 同一口径：{@code C} 不作为构件登记，
     * legend 里<b>没有</b>它（它代表的就是核心物品本身），所以统计时要跳过。
     */
    public static final char CORE_CHAR = 'C';

    private StructureMaterials() {
    }

    // ---------------------------------------------------------------- 一项材料

    /** 一项建造材料：part id + 可读名字 + 需要多少个。 */
    public static final class Entry implements Comparable<Entry> {

        private final String partId;
        private final String name;
        private final int amount;
        private final boolean tagRef;

        private Entry(String partId, String name, int amount, boolean tagRef) {
            this.partId = partId;
            this.name = name;
            this.amount = amount;
            this.tagRef = tagRef;
        }

        /** 层图 legend 里的原始值（粘液 id / Material 名 / {@code #标签}）。 */
        public String partId() {
            return partId;
        }

        /** 可读名字（解析不出来时退回 {@link #partId()}）。 */
        public String name() {
            return name;
        }

        /** 需要多少个。 */
        public int amount() {
            return amount;
        }

        /** 是不是 {@code #标签}（同标签的任意构件都能满足这一项）。 */
        public boolean isTagRef() {
            return tagRef;
        }

        /** 展示用：{@code 44 × 旧地狱-反应堆框架}。 */
        public String describe() {
            return amount + " × " + name + (tagRef ? "（标签 " + partId + "）" : "");
        }

        /**
         * 数量<b>降序</b>，同数量按名字升序。
         *
         * <p>★ 为什么要定序：材料表要写进物品 lore，而 lore 一旦生成就不再变。
         * 若顺序随 {@link java.util.HashMap} 的遍历顺序漂移，同一份层图每次启动
         * 都会得到不一样的描述 —— 那种"看起来变了其实没变"的差异最难排查。
         */
        @Override
        public int compareTo(Entry o) {
            if (o == null) {
                return -1;
            }
            if (amount != o.amount) {
                return Integer.compare(o.amount, amount);
            }
            return name.compareTo(o.name);
        }
    }

    // ---------------------------------------------------------------- 统计

    /**
     * 走一遍层图，统计"每种材料各要多少个"。
     *
     * <p>纯函数：只读入参，不碰世界、不碰 Slimefun 注册表、不碰任何结构实例 ——
     * 所以它能在 {@code AddItems.setup()} 那种"什么都还没准备好"的时刻安全调用。
     *
     * <p>★ 层图里的空气字符会先过一遍 {@link AddonConfig#normalizeAirChar}：
     * 赛钱箱那份层图（抄自扫描报告）把空气画成 {@code .}，而 legend 里登记的是 {@code _}
     * —— 不归一化就会把 {@code .} 当成"未登记字符"，整张表的材料数全部算漏。
     *
     * @param layers 层图（可以是 {@link AddonConfig} 里那份原始数据，含 {@code .}）
     * @param legend 字符 → part id 的表；{@code null} 表示没有可用数据
     * @return 材料清单（已按数量降序排好）；没有可统计的内容时返回空表
     */
    public static List<Entry> count(List<List<String>> layers, Map<Character, String> legend) {
        List<Entry> out = new ArrayList<>();
        if (layers == null || layers.isEmpty() || legend == null || legend.isEmpty()) {
            return out;
        }
        // 同 part id 合并计数；LinkedHashMap 只为让诊断输出稳定，最终还会再排序
        Map<String, Integer> counter = new LinkedHashMap<>();
        for (List<String> layer : AddonConfig.normalizeAirChar(layers)) {
            for (String row : layer) {
                if (row == null) {
                    continue;
                }
                for (int x = 0; x < row.length(); x++) {
                    char ch = row.charAt(x);
                    // 核心自己那一格：它是核心物品，不是"要摆的材料"
                    if (ch == CORE_CHAR) {
                        continue;
                    }
                    String partId = legend.get(ch);
                    if (partId == null || partId.isBlank()) {
                        continue;               // 未登记字符：结构检测那一侧会报出来，这里不猜
                    }
                    if (ReactorStructure.isAirRequirement(partId)) {
                        continue;               // "这一格必须是空气"，不是材料
                    }
                    counter.merge(partId, 1, Integer::sum);
                }
            }
        }
        for (Map.Entry<String, Integer> e : counter.entrySet()) {
            out.add(new Entry(e.getKey(), nameOf(e.getKey()), e.getValue(), isTagRef(e.getKey())));
        }
        out.sort(null);                          // Entry 自带 compareTo（数量降序）
        return out;
    }

    /** 材料总数（把所有种类加起来）—— 就是"这套结构一共要摆多少格"。 */
    public static int totalOf(List<Entry> entries) {
        int sum = 0;
        if (entries != null) {
            for (Entry e : entries) {
                sum += e.amount();
            }
        }
        return sum;
    }

    // ---------------------------------------------------------------- 物品 lore

    /**
     * 生成"建造所需材料"的 <b>lore 行</b>（含前导空行，直接接到物品原有 lore 后面）。
     *
     * <p>格式（{@code /touhou guide} 会原样打出来，便于无头核对）：
     * <pre>
     *   &amp;8建造所需材料 · 共 98 个构件 / 4 种
     *   &amp;7- &amp;f旧地狱-反应堆框架 &amp;7×44
     *   &amp;7- &amp;f旧地狱-反应堆保护罩&amp;7* &amp;7×36
     *   …
     *   &amp;8* 该项可用同一标签的任意构件替代
     * </pre>
     *
     * <p>★ 关于"材料太多撑爆 lore"：反应堆有 98 个构件，但<b>合并计数后只有 4 种</b>
     * （框架/保护罩/基座/稳定器），所以正常配置下列得完。真正可能撑爆的是
     * "有人往层图 legend 里塞进几十种方块"，所以这里仍然有一道闸：
     * 最多列 {@code maxKinds} 行，超出的部分只报"另有 N 种"，
     * 并<b>永远</b>保留第一行的"共 X 个构件 / Y 种"—— 那行才是完整信息。
     *
     * @param maxKinds 最多列几种（&lt;= 0 视为不限制）
     * @param header   标题行（{@code null} 用默认的"建造所需材料"）
     */
    public static List<String> lore(List<List<String>> layers, Map<Character, String> legend,
                                    int maxKinds, String header) {
        List<Entry> entries = count(layers, legend);
        List<String> out = new ArrayList<>();
        if (entries.isEmpty()) {
            return out;
        }
        out.add("");
        out.add(header == null || header.isBlank() ? "&8建造所需材料" : header);
        out.add("&8共 " + totalOf(entries) + " 个构件 / " + entries.size() + " 种");
        int shown = maxKinds <= 0 ? entries.size() : Math.min(maxKinds, entries.size());
        for (int i = 0; i < shown; i++) {
            Entry e = entries.get(i);
            out.add("&7- &f" + e.name() + (e.isTagRef() ? "&7*" : "") + " &7×" + e.amount());
        }
        if (shown < entries.size()) {
            out.add("&8… 另有 " + (entries.size() - shown) + " 种（共 " + entries.size() + " 种）");
        }
        if (hasTagRef(entries)) {
            out.add("&8* 该项可用同一标签的任意构件替代");
        }
        return out;
    }

    /** 清单里有没有 {@code #标签} 项（决定要不要打那行脚注）。 */
    public static boolean hasTagRef(List<Entry> entries) {
        if (entries != null) {
            for (Entry e : entries) {
                if (e.isTagRef()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 诊断：材料清单逐行文本（{@code /touhou guide} 用）。 */
    public static List<String> describe(List<List<String>> layers, Map<Character, String> legend) {
        List<Entry> entries = count(layers, legend);
        List<String> out = new ArrayList<>();
        if (entries.isEmpty()) {
            out.add("  (没有可统计的材料 —— 层图或 legend 是空的)");
            return out;
        }
        out.add("  共 " + totalOf(entries) + " 个构件 / " + entries.size() + " 种");
        for (Entry e : entries) {
            out.add("    " + e.describe());
        }
        return out;
    }

    // ---------------------------------------------------------------- 名字解析

    /** 这个 part id 是不是"标签引用"（形如 {@code #touhou:reactor_shell}）。 */
    public static boolean isTagRef(String partId) {
        return partId != null && partId.length() > 1 && partId.charAt(0) == '#';
    }

    /**
     * part id → 可读名字。<b>认不出来就如实回显原始 id</b>（不返回 {@code null}、不抛异常）。
     *
     * <p>三路解析的详细判据见类注释。这里额外做一件"人话"处理：
     * {@code nu} 这类"必须空气"的格子显示成「(空气)」——
     * 正常情况下它们已经在 {@link #count} 里被滤掉了，留着只是为了诊断输出不出现莫名其妙的 {@code nu}。
     */
    public static String nameOf(String partId) {
        if (partId == null || partId.isBlank()) {
            return "(空)";
        }
        if (ReactorStructure.isAirRequirement(partId)) {
            return "(空气)";
        }
        if (!isTagRef(partId)) {
            String direct = nameOfId(partId);
            return direct == null ? partId : direct;
        }
        // ---- #标签：走 ItemTags 的代表件（与 MultiBlockProjection.resolveIconId 同一套判据）
        String tag = partId.substring(1).trim();
        if (ItemTags.isKnown(tag)) {
            String preferred = ItemTags.defaultDisplay(tag);
            if (preferred != null) {
                String name = nameOfId(preferred);
                if (name != null) {
                    return name;
                }
            }
            for (String member : ItemTags.members(tag)) {
                String name = nameOfId(member);
                if (name != null) {
                    return name;
                }
            }
        }
        return partId;                          // 标签没登记 / 成员全解析不出来：回显原样
    }

    /** 具体 id（粘液 id 或 Material 名）→ 可读名字；认不出来返回 {@code null}。 */
    private static String nameOfId(String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        // 1) 已注册的粘液物品（运行期最准的一条路：显示名以物品本体为准）
        try {
            SlimefunItem sf = SlimefunItem.getById(id);
            if (sf != null && sf.getItem() != null) {
                String name = displayNameOf(sf.getItem());
                if (name != null) {
                    return name;
                }
            }
        } catch (RuntimeException ignored) {
            // Slimefun 还没就绪 / id 非法：掉到模板表那条路
        }
        // 2) AddItems 的模板表 —— ★ 物品注册【之前】唯一能拿到显示名的路
        //    （lore 是在 AddItems.setup() 里拼的，那时 SlimefunItem 一个都还没注册）
        SlimefunItemStack template = templates().get(id);
        if (template != null) {
            String name = displayNameOf(template);
            if (name != null) {
                return name;
            }
        }
        // 3) 原版 Material 名
        Material mat = Material.matchMaterial(id);
        if (mat != null && !mat.isAir()) {
            return materialName(mat);
        }
        return null;
    }

    /** 物品显示名（去颜色代码）；没有自定义名字返回 {@code null}。 */
    private static String displayNameOf(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        String plain = Notify.plain(meta.getDisplayName());
        return plain == null || plain.isBlank() ? null : plain;
    }

    /**
     * 原版材质 → 可读名字。
     *
     * <p>优先取客户端语言文件里的名字（{@code RED_WOOL → Red Wool}），
     * 拿不到就退回枚举名 —— <b>两者都比"什么都不显示"强</b>，
     * 而枚举名至少能和 {@code config.yml} 的 legend 逐字对上，排查时反而更好用。
     *
     * <p>公开出来是因为 {@link RecipePages} 的展示名兜底要用同一条口径 ——
     * "材质怎么变成字"这件事只该有一个答案。
     */
    public static String materialName(Material mat) {
        if (mat == null) {
            return "(未知材质)";
        }
        try {
            String i18n = new ItemStack(mat).getI18NDisplayName();
            if (i18n != null && !i18n.isBlank()) {
                return i18n;
            }
        } catch (RuntimeException ignored) {
            // 部分服务端/材质没有本地化名：掉到枚举名
        }
        return mat.toString();
    }

    // ---------------------------------------------------------------- 模板表

    /**
     * 粘液 id → {@link AddItems} 里的物品模板（懒加载，只建一次）。
     *
     * <p>★ 为什么需要它：lore 是在 {@link AddItems#setup()} 里拼的，那一刻
     * <b>所有 {@code SlimefunItem} 都还没注册</b>（注册发生在紧接着的
     * {@code AddSlimefunItems.setup()}），所以 {@code SlimefunItem.getById} 查不到
     * 本插件自己的物品。而模板（{@link SlimefunItemStack}）那时已经都在
     * {@link AddItems} 的静态字段上了 —— 拿它取显示名即可。
     *
     * <p>★ 为什么用反射而不是手写一张清单：手写清单意味着"以后每加一个构件物品，
     * 都要记得回来补一行"，漏了不会报错、只会静默退化成显示粘液 id。
     * 反射读的是 {@link AddItems} 自己声明的静态 {@link SlimefunItemStack} 字段，
     * <b>新加模板自动就在表里</b>，没有第二处要维护。代价只是一次性的字段遍历
     * （25 个字段，且在启动期只跑一次），完全划算。
     *
     * <p>只在"模板表还是空的"时候构建一次 —— 调用点一定在 {@code AddItems.setup()} 之后，
     * 那时字段全都赋好值了。构建失败（理论上不会）时如实返回空表，
     * 后备的 Material / 原始 id 两条路仍然能给出可读结果。
     */
    private static Map<String, SlimefunItemStack> templates() {
        if (!TEMPLATES.isEmpty()) {
            return TEMPLATES;
        }
        Field[] fields;
        try {
            fields = AddItems.class.getDeclaredFields();
        } catch (RuntimeException | LinkageError e) {
            Log.warn("[结构材料] 无法枚举 AddItems 的模板字段，物品描述里的材料名会退回粘液 id：" + e);
            return TEMPLATES;
        }
        for (Field f : fields) {
            if (!Modifier.isStatic(f.getModifiers())
                    || !SlimefunItemStack.class.isAssignableFrom(f.getType())) {
                continue;
            }
            try {
                Object value = f.get(null);
                if (value instanceof SlimefunItemStack stack && stack.getItemId() != null) {
                    TEMPLATES.put(stack.getItemId(), stack);
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // 单个字段读不到不影响其它字段：跳过，让后备路径兜住
            }
        }
        return TEMPLATES;
    }

    /** 模板表缓存（见 {@link #templates()}）。 */
    private static final Map<String, SlimefunItemStack> TEMPLATES = new LinkedHashMap<>();
}
