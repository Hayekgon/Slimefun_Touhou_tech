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
 */
public final class ItemTags {

    /** 标签 -> part id 集合（保持插入顺序，方便诊断输出稳定）。 */
    private static final Map<String, Set<String>> TAGS = new LinkedHashMap<>();

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
        }
        return out;
    }
}
