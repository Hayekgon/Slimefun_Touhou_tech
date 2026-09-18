package com.example.touhou.core;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 赛钱箱的<b>配方数据结构</b> —— 一条配方 = "6 个编号槽位各要什么" + "产出什么"。
 *
 * <h2>形状</h2>
 * <pre>
 *   编号槽位 0..5：{@link Ingredient}（物品模板 + 数量）
 *   产物     ：{@link ItemStack}（数量写在物品自己身上）
 * </pre>
 * 所以"某个木桩里放 N 个同种物品"就是 {@code slot(编号, 物品, N)} ——
 * 数量是<b>每个槽位独立</b>的，不是全配方共用一个。例如：
 * <pre>
 *   SaizenbakoRecipe.of("示例")
 *       .slot(2, new ItemStack(Material.NETHER_STAR), 16)   // 2 号木桩里放 16 个下界之星
 *       .output(new ItemStack(Material.DIAMOND, 4))
 *       .build();
 * </pre>
 *
 * <h2>★ 比对判据：粘液物品按 id 比，不能只看材质</h2>
 * {@link Ingredient#matches(ItemStack)} 的判断顺序：
 * <ol>
 *   <li>材质必须相同（快速否决）；</li>
 *   <li>只要<b>任意一边</b>是粘液物品（{@code SlimefunItem.getByItem} 非空），
 *       就必须两边都是粘液物品、且 {@code getId()} 相等 —— 于是
 *       "POWER集成核心"不会被一块同名材质的原版方块顶替，
 *       也不会被别的粘液物品顶替；</li>
 *   <li>两边都是原版物品时，材质相同即视为同种（原版物品没有 id 可用）。</li>
 * </ol>
 * 数量判据是「<b>不少于</b>配方要求的数量」（{@code >=}），不是「恰好等于」：
 * 玩家往木桩里塞 20 个下界之星时配方照样成立，一次只消耗配方要求的 16 个
 * —— 要求"恰好"会逼玩家把多出来的先拿出来，那是没必要的折磨。
 *
 * <h2>怎么加第二条配方</h2>
 * 见 {@link SaizenbakoRecipes#register(SaizenbakoRecipe)}（注册入口只有那一个）。
 * 匹配是<b>按注册顺序取第一条命中的</b>，所以更特殊的配方要注册在前面。
 */
public final class SaizenbakoRecipe {

    /** 编号槽位数量（= 木桩数量 = 预留槽数量）。 */
    public static final int SLOTS = SaizenbakoStructure.POST_COUNT;

    // ---------------------------------------------------------------- 单个槽位的要求

    /** 某一个编号槽位的要求：物品 + 数量。 */
    public static final class Ingredient {

        private final ItemStack template;
        private final int amount;
        /** 粘液物品 id；{@code null} = 原版物品（按材质比）。 */
        private final String sfId;

        private Ingredient(ItemStack template, int amount) {
            this.template = template.clone();
            this.template.setAmount(1);
            this.amount = amount;
            SlimefunItem sf = SlimefunItem.getByItem(template);
            this.sfId = sf == null ? null : sf.getId();
        }

        /** 模板物品（数量恒为 1，只用于显示与比对）。 */
        public ItemStack template() {
            return template.clone();
        }

        /** 需要多少个。 */
        public int amount() {
            return amount;
        }

        /** 是不是粘液物品（是则按 id 比）。 */
        public boolean isSlimefun() {
            return sfId != null;
        }

        /** 这一格里的物品是否满足本要求（含数量）。 */
        public boolean matches(ItemStack have) {
            return sameItem(have, template) && have.getAmount() >= amount;
        }

        /** 展示用：{@code 3 × POWER集成核心} 或 {@code 16 × NETHER_STAR}。 */
        public String describe() {
            String label = sfId != null ? sfId : template.getType().toString();
            return amount + " × " + label;
        }
    }

    // ---------------------------------------------------------------- 配方本体

    private final String id;
    /** 编号 -> 要求（没登记的编号 = 该格不限制）。 */
    private final Map<Integer, Ingredient> inputs;
    private final ItemStack output;
    /** 备注（可空），写进诊断输出。 */
    private final String note;

    private SaizenbakoRecipe(String id, Map<Integer, Ingredient> inputs, ItemStack output, String note) {
        this.id = id;
        this.inputs = Map.copyOf(inputs);
        this.output = output.clone();
        this.note = note;
    }

    public static Builder of(String id) {
        return new Builder(id);
    }

    /** 配方名（日志/诊断）。 */
    public String id() {
        return id;
    }

    /** 产物（拷贝，外部改不动内部状态）。 */
    public ItemStack output() {
        return output.clone();
    }

    /** 第 {@code index} 号槽位的要求；该格不限制时返回 {@code null}。 */
    public Ingredient ingredientAt(int index) {
        return inputs.get(index);
    }

    /** 备注（可空）。 */
    public String note() {
        return note;
    }

    /**
     * 6 个预留槽的物品是否<b>同时</b>满足本配方。
     *
     * @param reservedSlots 长度至少 {@link #SLOTS} 的数组，下标 = 编号；
     *                      允许含 {@code null}（空槽）
     */
    public boolean matches(ItemStack[] reservedSlots) {
        if (reservedSlots == null) {
            return false;
        }
        for (int i = 0; i < SLOTS; i++) {
            Ingredient want = inputs.get(i);
            if (want == null) {
                continue;                       // 该格不限制
            }
            ItemStack have = i < reservedSlots.length ? reservedSlots[i] : null;
            if (have == null || have.getType().isAir() || !want.matches(have)) {
                return false;
            }
        }
        return true;
    }

    /** 逐行展示：编号 → 要求，最后一行是产物。 */
    public List<String> describe() {
        List<String> out = new ArrayList<>();
        out.add("配方 " + id + (note == null || note.isBlank() ? "" : "（" + note + "）"));
        for (int i = 0; i < SLOTS; i++) {
            Ingredient ing = inputs.get(i);
            out.add("  #" + i + " : " + (ing == null ? "(不限制)" : ing.describe()));
        }
        out.add("  产物 : " + output.getAmount() + " × " + output.getType()
                + (output.hasItemMeta() && output.getItemMeta() != null
                        && output.getItemMeta().hasDisplayName()
                        ? "（" + output.getItemMeta().getDisplayName() + "）" : ""));
        return out;
    }

    // ---------------------------------------------------------------- 物品比对

    /**
     * 两个物品是不是"同一种" —— <b>粘液物品按 id 比</b>。
     *
     * <p>★ 为什么不能只比 {@link Material}：本插件的物品大量复用原版材质
     * （POWER 存储单元就是红色混凝土），只比材质会让配方被随便一块同材质方块满足。
     *
     * <p>★ 为什么不做 NBT 全量比对：粘液物品的身份本来就由 PDC 里的 id 决定
     * （{@code SlimefunItemStack} 的 id 写在物品数据里），id 相同即同一种物品；
     * 而原版物品没有 id 可查，只能退回材质比对 —— 这是本工程一贯的判据
     * （与 {@code ReactorStructure.partIdAt} 用 {@code BlockStorage.checkID} 判方块同源）。
     */
    public static boolean sameItem(ItemStack have, ItemStack want) {
        if (have == null || want == null || have.getType().isAir() || want.getType().isAir()) {
            return false;
        }
        if (have.getType() != want.getType()) {
            return false;
        }
        SlimefunItem a = SlimefunItem.getByItem(have);
        SlimefunItem b = SlimefunItem.getByItem(want);
        if (a == null && b == null) {
            return true;                // 都是原版物品：材质相同即同种
        }
        return a != null && b != null && a.getId().equals(b.getId());
    }

    // ---------------------------------------------------------------- Builder

    /** 配方构造器（链式；数量缺省 1）。 */
    public static final class Builder {

        private final String id;
        private final Map<Integer, Ingredient> inputs = new LinkedHashMap<>();
        private ItemStack output;
        private String note;

        private Builder(String id) {
            this.id = id == null || id.isBlank() ? "未命名配方" : id;
        }

        /** 登记一个编号槽位的要求（数量必须 &gt;= 1）。 */
        public Builder slot(int index, ItemStack item, int amount) {
            if (index < 0 || index >= SLOTS) {
                throw new IllegalArgumentException("编号槽位必须在 0~" + (SLOTS - 1) + " 之间，收到 " + index);
            }
            if (item == null || item.getType().isAir()) {
                throw new IllegalArgumentException("编号 " + index + " 的物品不能为空");
            }
            if (amount < 1) {
                throw new IllegalArgumentException("编号 " + index + " 的数量必须 >= 1，收到 " + amount);
            }
            inputs.put(index, new Ingredient(item, amount));
            return this;
        }

        /** 登记一个编号槽位的要求（数量 1）。 */
        public Builder slot(int index, ItemStack item) {
            return slot(index, item, 1);
        }

        /** 登记要求"这一格必须是空的"（例如将来做"不许有杂质"的配方）。 */
        public Builder emptySlot(int index) {
            if (index < 0 || index >= SLOTS) {
                throw new IllegalArgumentException("编号槽位必须在 0~" + (SLOTS - 1) + " 之间，收到 " + index);
            }
            inputs.remove(index);
            return this;
        }

        public Builder output(ItemStack item) {
            if (item == null || item.getType().isAir()) {
                throw new IllegalArgumentException("产物不能为空");
            }
            this.output = item.clone();
            if (this.output.getAmount() < 1) {
                this.output.setAmount(1);
            }
            return this;
        }

        /** 产物 + 数量（数量直接写在产物物品上）。 */
        public Builder output(ItemStack item, int amount) {
            if (amount < 1) {
                throw new IllegalArgumentException("产物数量必须 >= 1，收到 " + amount);
            }
            ItemStack copy = item == null ? null : item.clone();
            if (copy != null) {
                copy.setAmount(amount);
            }
            return output(copy);
        }

        /** 备注（写进诊断输出）。 */
        public Builder note(String note) {
            this.note = note;
            return this;
        }

        public SaizenbakoRecipe build() {
            if (inputs.isEmpty()) {
                throw new IllegalStateException("配方 " + id + " 一个编号槽位都没登记");
            }
            if (output == null) {
                throw new IllegalStateException("配方 " + id + " 没有登记产物");
            }
            return new SaizenbakoRecipe(id, inputs, output, note);
        }
    }
}
