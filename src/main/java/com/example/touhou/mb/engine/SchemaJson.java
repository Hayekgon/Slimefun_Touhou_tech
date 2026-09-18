package com.example.touhou.mb.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据文件格式（v1）的写入与读取。
 *
 * <p>刻意手写 JSON，不引 gson/snakeyaml：产物是"给人看 + 给工具读"的中间格式，
 * 需要行序稳定、缩进可控、零依赖（生成的 schema 会被拖到别的工程里用）。
 *
 * <pre>
 * {
 *   "format": "myaddon-mb-schema",
 *   "version": 1,
 *   "name": "MY_TOWER",
 *   "type": "MultiBlockType",
 *   "symmetric": false,
 *   "world": "world",
 *   "core": [ -2, 5, -3 ],          // 绝对坐标；schema 偏移 = 绝对坐标 - core
 *   "size": [ 5, 5, 5 ],
 *   "parts": [ { "offset": [1,0,0], "id": "MYADDON_TEST_FRAME_1" } ],
 *   "requirements": [ { "offset": [0,1,0], "id": "nu" } ]
 * }
 * </pre>
 */
public final class SchemaJson {

    public static final String FORMAT = "myaddon-mb-schema";
    public static final int VERSION = 1;

    private SchemaJson() {
    }

    // ================================================================ 写出

    public static String write(SchemaData data) {
        StringBuilder sb = new StringBuilder(4096);
        sb.append("{\n");
        kv(sb, "format", str(data.format));
        kv(sb, "version", String.valueOf(data.version));
        kv(sb, "name", str(data.name));
        kv(sb, "type", str(data.type));
        kv(sb, "symmetric", String.valueOf(data.symmetric));
        kv(sb, "world", str(data.world));
        kv(sb, "core", arr(data.core));
        kv(sb, "size", arr(data.size));
        if (data.note != null && !data.note.isEmpty()) {
            kv(sb, "note", str(data.note));
        }
        kv(sb, "generatedAt", str(data.generatedAt));
        kv(sb, "generator", str(data.generator));
        entries(sb, "parts", data.parts);
        sb.append(",\n");
        entries(sb, "requirements", data.requirements);
        sb.append("\n}\n");
        return sb.toString();
    }

    private static void entries(StringBuilder sb, String key, List<Entry> list) {
        sb.append("  \"").append(key).append("\": [");
        if (list.isEmpty()) {
            sb.append(']');
            return;
        }
        sb.append('\n');
        for (int i = 0; i < list.size(); i++) {
            Entry e = list.get(i);
            sb.append("    { \"offset\": ").append(arr(e.offset))
              .append(", \"id\": ").append(str(e.id)).append(" }");
            sb.append(i + 1 < list.size() ? ",\n" : "\n");
        }
        sb.append("  ]");
    }

    private static void kv(StringBuilder sb, String key, String rawValue) {
        sb.append("  ").append(str(key)).append(": ").append(rawValue).append(",\n");
    }

    private static String arr(int[] a) {
        StringBuilder sb = new StringBuilder("[ ");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(a[i]);
        }
        return sb.append(" ]").toString();
    }

    private static String str(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    // ================================================================ 读取

    /** 数据文件的内存形态（读写共用）。 */
    public static final class SchemaData {
        public String format = FORMAT;
        public int version = VERSION;
        public String name = "MY_SCHEMA";
        public String type = "MultiBlockType";
        public boolean symmetric;
        public String world = "world";
        public int[] core = new int[3];
        public int[] size = new int[3];
        public String note = "";
        public String generatedAt = "";
        public String generator = "MyAddon MultiBlockCreator";
        public final List<Entry> parts = new ArrayList<>();
        public final List<Entry> requirements = new ArrayList<>();
    }

    /** 一条 { offset, id }。 */
    public static final class Entry {
        public int[] offset;
        public String id;

        public Entry() {
        }

        public Entry(int x, int y, int z, String id) {
            this.offset = new int[] {x, y, z};
            this.id = id;
        }
    }

    public static void writeFile(Path file, SchemaData data) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, write(data).getBytes(StandardCharsets.UTF_8));
    }

    public static SchemaData readFile(Path file) throws IOException {
        return parse(new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
    }

    /** 极简 JSON 解析：只支持本格式用到的子集。 */
    public static SchemaData parse(String json) {
        Parser p = new Parser(json);
        Object root = p.parseValue();
        if (!(root instanceof Map)) {
            throw new IllegalArgumentException("JSON 根节点必须是对象");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> obj = (Map<String, Object>) root;

        SchemaData d = new SchemaData();
        d.format = string(obj.getOrDefault("format", FORMAT));
        d.version = (int) number(obj.getOrDefault("version", (double) VERSION));
        d.name = string(obj.getOrDefault("name", "MY_SCHEMA"));
        d.type = string(obj.getOrDefault("type", "MultiBlockType"));
        d.symmetric = Boolean.TRUE.equals(obj.get("symmetric"));
        d.world = string(obj.getOrDefault("world", "world"));
        d.core = intArray(obj.get("core"), 3);
        d.size = intArray(obj.get("size"), 3);
        d.note = string(obj.getOrDefault("note", ""));
        readEntries(obj.get("parts"), d.parts);
        readEntries(obj.get("requirements"), d.requirements);
        return d;
    }

    /** 把数据文件转成可校验的 {@link AbstractMultiBlockType}。 */
    public static MultiBlockType toType(SchemaData d) {
        List<Vector> parts = new ArrayList<>();
        List<String> partIds = new ArrayList<>();
        for (Entry e : d.parts) {
            parts.add(new Vector(e.offset[0], e.offset[1], e.offset[2]));
            partIds.add(e.id);
        }
        List<Vector> reqs = new ArrayList<>();
        List<String> reqIds = new ArrayList<>();
        for (Entry e : d.requirements) {
            reqs.add(new Vector(e.offset[0], e.offset[1], e.offset[2]));
            reqIds.add(e.id);
        }
        return new MultiBlockType()
                .loadArrays(parts, partIds, reqs, reqIds, d.symmetric);
    }

    private static void readEntries(Object o, List<Entry> out) {
        if (!(o instanceof List)) {
            return;
        }
        for (Object item : (List<?>) o) {
            if (!(item instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) item;
            Entry e = new Entry();
            e.offset = intArray(m.get("offset"), 3);
            e.id = string(m.getOrDefault("id", ""));
            out.add(e);
        }
    }

    private static String string(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static int[] intArray(Object o, int len) {
        int[] out = new int[len];
        if (o instanceof List) {
            List<?> l = (List<?>) o;
            for (int i = 0; i < len && i < l.size(); i++) {
                out[i] = (int) number(l.get(i));
            }
        }
        return out;
    }

    private static double number(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        return 0;
    }

    /** 只用得上的那个子集。 */
    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        Object parseValue() {
            skipWs();
            char c = peek();
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return parseNumber();
            }
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++;                                    // '{'
            skipWs();
            if (peek() == '}') {
                i++;
                return m;
            }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                i++;                                // ':'
                m.put(key, parseValue());
                skipWs();
                char c = s.charAt(i++);
                if (c == '}') {
                    return m;
                }
            }
        }

        private List<Object> parseArray() {
            List<Object> l = new ArrayList<>();
            i++;                                    // '['
            skipWs();
            if (peek() == ']') {
                i++;
                return l;
            }
            while (true) {
                l.add(parseValue());
                skipWs();
                char c = s.charAt(i++);
                if (c == ']') {
                    return l;
                }
            }
        }

        private String parseString() {
            i++;                                    // '"'
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        private Double parseNumber() {
            int start = i;
            while (i < s.length() && "-+.eE0123456789".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            return Double.parseDouble(s.substring(start, i));
        }

        private void expect(String word) {
            if (!s.startsWith(word, i)) {
                throw new IllegalArgumentException("非法 JSON：位置 " + i + " 期望 " + word);
            }
            i += word.length();
        }

        private char peek() {
            return s.charAt(i);
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
    }
}
