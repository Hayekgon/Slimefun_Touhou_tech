package com.example.touhou.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构层图解析。
 *
 * <p>单独成类是为了打断循环依赖：{@link AddonConfig} 要解析层图，
 * 而 {@link LayeredReactorStructure} 又要从 {@link AddonConfig} 读层图 ——
 * 把解析放进结构类里就成环了（编译期报 "cannot find symbol"）。
 *
 * <p>写法与语义见 {@link LayeredReactorStructure} 的类注释：
 * 外层 = 层（沿 y 从下往上），内层 = 行（沿 z），行内字符 = 列（沿 x）。
 */
public final class LayerParser {

    private LayerParser() {
    }

    /**
     * 解析 config.yml 的 {@code reactor.structure.layers}。
     *
     * <p>支持两种写法：
     * <ol>
     *   <li><b>嵌套列表（推荐）</b>：每个层一个子列表。
     *       <pre>
     * layers:
     *   - - "FFF"
     *     - "FCF"
     *     - "FFF"
     *   - - "FFF"
     *     - "FFF"
     *     - "FFF"
     *       </pre>
     *       这是唯一能<b>明确表达层边界</b>的写法。</li>
     *   <li>紧凑写法：每层一个逗号分隔字符串（{@code ["FFF,FCF,FFF", "FFF,FFF,FFF"]}）。</li>
     * </ol>
     *
     * <p>★ 为什么不能用"扁平行列表 + 空串分隔层"：YAML 的序列里<b>写不出空项</b>，
     * {@code - ""} 是一条空字符串而不是分隔符，解析器只能看到 N 个平铺字符串，
     * 于是被误判成"1 层 N 行"。实测就是这么踩的（结构检查一直回退到 ALWAYS_OK）。
     */
    public static List<List<String>> parseLayers(List<?> raw) {
        List<List<String>> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (Object item : raw) {
            if (item == null) {
                continue;
            }
            if (item instanceof List<?> rows) {
                List<String> layer = new ArrayList<>();
                for (Object row : rows) {
                    if (row != null) {
                        layer.add(String.valueOf(row).trim());
                    }
                }
                if (!layer.isEmpty()) {
                    out.add(layer);
                }
                continue;
            }
            String trimmed = String.valueOf(item).trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            List<String> layer = new ArrayList<>();
            for (String part : trimmed.split(",")) {
                layer.add(part.trim());
            }
            out.add(layer);
        }
        return out;
    }

    /** 把扁平行列表按"每 layerRows 行一层"切开（仅用于兼容手写的扁平配置）。 */
    public static List<List<String>> parseFlat(List<String> raw, int layerRows) {
        List<List<String>> out = new ArrayList<>();
        if (raw == null || layerRows <= 0) {
            return out;
        }
        for (int i = 0; i < raw.size(); i += layerRows) {
            List<String> layer = new ArrayList<>();
            for (int j = i; j < Math.min(raw.size(), i + layerRows); j++) {
                String s = raw.get(j) == null ? "" : raw.get(j).trim();
                if (!s.isEmpty()) {
                    layer.add(s);
                }
            }
            if (!layer.isEmpty()) {
                out.add(layer);
            }
        }
        return out;
    }

    /** 便于日志：把层图渲染成一行摘要，例如 {@code [3层: 3x3,3x3,3x3]}。 */
    public static String describe(List<List<String>> layers) {
        if (layers == null || layers.isEmpty()) {
            return "(空)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(layers.size()).append(" 层: ");
        for (int i = 0; i < layers.size(); i++) {
            List<String> layer = layers.get(i);
            int width = layer.isEmpty() ? 0 : layer.get(0).length();
            sb.append(layer.size()).append('x').append(width);
            if (i + 1 < layers.size()) {
                sb.append(',');
            }
        }
        return sb.toString();
    }
}
