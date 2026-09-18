package com.example.touhou.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 本附属的<b>方块/物品标签</b>表。
 *
 * <p>用途：多方块结构检测支持"按标签判定" —— 层图里写 {@code #标签}，
 * 只要那一格的方块属于这个标签就算对，不必把每个等价方块都列进 legend。
 *
 * <p>典型场景：<b>反应堆 IO 接口可以替代保护罩</b>搭建，于是两者挂同一个标签
 * {@code touhou:reactor_shell}，层图里 'S' 写 {@code #touhou:reactor_shell} 即可。
 * 以后再加别的外壳变体，只要挂上同一个标签，层图和配置都不用动。
 *
 * <p>标签里存的是 <b>part id 字符串</b>，与 {@link ReactorStructure#partIdAt} 的口径完全一致：
 * <ul>
 *   <li>粘液方块 → 它的 sfId；</li>
 *   <li>原版方块 → {@code Material.toString()}；</li>
 *   <li>空气 → {@code "nu"}。</li>
 * </ul>
 * 所以标签里也可以直接写原版材质名（例如 {@code TINTED_GLASS}），判定照样成立。
 *
 * <p>标签名建议用 {@code 命名空间:名字} 的形式（本附属统一用 {@code touhou:} 前缀），
 * 与 Minecraft 原版标签的写法一致；层图里引用时前面加 {@code #}。
 *
 * <p>★ 标签还兼着<b>多方块投影</b>的图标来源：层图里写 {@code #标签} 的格子
 * 检测时"任一成员都算对"，画投影时则画 {@link #defaultDisplay} 指定的那一个
 * （没指定就取成员里第一个能解析成物品的）—— 见那个方法的注释。
 */
public final class ItemTags {

    /** 标签 -> part id 集合（保持插入顺序，方便诊断输出稳定）。 */
    private static final Map<String, Set<String>> TAGS = new LinkedHashMap<>();

    /**
     * 标签 -> <b>默认展示件</b>的 part id。
     *
     * <p>★ 为什么需要它（多方块投影的"标签格画什么图标"）：
     * 层图里写 {@code #标签} 的格子，检测时"标签里任意一个成员都算对"，
     * 但<b>画投影</b>时必须挑<b>一个</b>具体物品当图标 —— 而"挑哪个"是<b>业务决策</b>，
     * 不是数据结构层面能推导出来的事（例如 {@code touhou:reactor_shell} 覆盖
     * 保护罩 / 输入接口 / 输出接口，用户明确要求这一格画<b>保护罩</b>）。
     *
     * <p>为什么不直接取"成员里第一个"：{@link #TAGS} 虽然是 {@link LinkedHashSet}
     * （登记顺序稳定），但那个顺序只是"谁先被 {@link #tag} 调用"的副产品 ——
     * 哪天有人把 {@code AddSlimefunItems} 里两行注册换个位置、或加一个新成员，
     * 图标就会<b>悄悄</b>变成另一个方块，而结构检测一切正常，极难发现。
     * 所以把"代表件"显式写下来（{@link #setDefaultDisplay}），
     * 只有没显式指定时才退回"成员里第一个能解析成物品的"。
     */
    private static final Map<String, String> DISPLAY_DEFAULTS = new LinkedHashMap<>();

    private ItemTags() {
    }

    /**
     * 登记一个标签，把一个或多个 part id 挂上去。
     *
     * <p>重复登记同一个标签是<b>累加</b>而不是覆盖 —— 这样"分两处给同一个标签加成员"是安全的。
     */
    public static void tag(String tag, String... partIds) {
        if (tag == null || tag.isBlank()) {
            return;
        }
        Set<String> parts = TAGS.computeIfAbsent(tag, k -> new LinkedHashSet<>());
        for (String id : partIds) {
            if (id != null && !id.isBlank()) {
                parts.add(id.trim());
            }
        }
    }

    /**
     * 指定某个标签的<b>默认展示件</b>（投影画这一格时用哪个物品当图标）。
     *
     * <p>惯例上就在 {@link #tag} 的下面紧接着登记（见 {@code AddSlimefunItems}），
     * 让"这个标签有哪几个成员、代表件是哪个"两件事挨着看得到。
     *
     * @param partId 必须是该标签的成员之一（不是成员也照样记下来，
     *               但 {@code /touhou tags} 会如实报出来，便于发现登记笔误）
     */
    public static void setDefaultDisplay(String tag, String partId) {
        if (tag == null || tag.isBlank() || partId == null || partId.isBlank()) {
            return;
        }
        DISPLAY_DEFAULTS.put(tag, partId.trim());
    }

    /** 标签的默认展示件；没指定过返回 {@code null}（投影那边会退回"第一个能解析的成员"）。 */
    public static String defaultDisplay(String tag) {
        return tag == null ? null : DISPLAY_DEFAULTS.get(tag);
    }

    /** 该 part id 是否属于这个标签。 */
    public static boolean has(String tag, String partId) {
        if (tag == null || partId == null) {
            return false;
        }
        Set<String> parts = TAGS.get(tag);
        return parts != null && parts.contains(partId);
    }

    /** 标签名是否登记过（没登记过的标签要报出来，否则结构会静默判错）。 */
    public static boolean isKnown(String tag) {
        return tag != null && TAGS.containsKey(tag);
    }

    /** 某个标签的成员（只读）。 */
    public static Set<String> members(String tag) {
        Set<String> parts = TAGS.get(tag);
        return parts == null ? Set.of() : Collections.unmodifiableSet(parts);
    }

    /** 全部标签（只读，诊断用）。 */
    public static Map<String, Set<String>> all() {
        return Collections.unmodifiableMap(TAGS);
    }

    /** 供命令输出：标签一览。 */
    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        if (TAGS.isEmpty()) {
            out.add("  (没有任何标签)");
            return out;
        }
        for (Map.Entry<String, Set<String>> e : TAGS.entrySet()) {
            out.add("  #" + e.getKey() + " -> " + String.join(", ", e.getValue()));
            // ★ 默认展示件也报出来：投影画 `#标签` 那一格用的就是它，
            //   而"成员里的哪一个"是投影图标那种"看不清就没法排查"的问题的唯一线索。
            String pick = DISPLAY_DEFAULTS.get(e.getKey());
            if (pick == null) {
                out.add("      默认展示件: (未指定 —— 投影取成员里第一个能解析成物品的)");
            } else {
                out.add("      默认展示件: " + pick
                        + (e.getValue().contains(pick) ? "" : "  ⚠ 它不在本标签的成员里"));
            }
        }
        return out;
    }
}
