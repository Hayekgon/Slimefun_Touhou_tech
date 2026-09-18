package com.example.touhou.core;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * <b>粘液书自定义配方页</b>的条目构造 —— 照搬 LogiTech 的
 * {@code me.matl114.logitech.core.Interface.RecipeDisplay}（玩家截图里那种
 * 「材料 N / 输入数量: X / 产物 N / 进程耗时」）。
 *
 * <h2>它到底拼的是什么</h2>
 * Slimefun 指南在打开一个实现了
 * {@code io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem}
 * 的物品页时，会在<b>底部</b>渲染 {@code getDisplayRecipes()} 返回的那个 {@code List<ItemStack>}：
 * 每 <b>两个</b>相邻元素算"一条"——偶数下标进<b>左列</b>（输入），奇数下标进<b>右列</b>（输出），
 * 一页 9 行（18 个元素）。注意它<b>不是原版合成表</b>：上面那 9 格仍是
 * {@code SlimefunItem#getRecipe()}，本插件那两个核心的配方数组是全空的，所以上面就是空的。
 *
 * <p>它真正"自定义"的地方在于：<b>lore 是我们自己写的</b>。所以这里的做法与 LogiTech 完全一致 ——
 * 不发明新格式，只往每个 Icon 的 lore 里追加几行中文标签。
 *
 * <h2>与 LogiTech 的两处有意的差异</h2>
 * <ol>
 *   <li><b>输入数量总是写出来</b>。LogiTech 只在 {@code amount > 64} 时才写
 *       {@code 输入数量}（其余靠物品角标显示个数）；本附属的祭坛配方数量是"每格独立"的，
 *       玩家最需要一眼看全 6 个槽位各要多少，所以<b>一律写明</b>，
 *       同时把物品角标钳到 64 以内（原版一个物品堆最多显示 64）；</li>
 *   <li><b>条目对齐规则单独收在一处</b>（{@link #block}）：LogiTech 是在
 *       {@code _getDisplayRecipes} 里边拼边对齐的，这里拆成一个纯函数，
 *       于是"一条配方有几个输入、几个输出"与"渲染出来长什么样"互不干扰。</li>
 * </ol>
 *
 * <h2>★ 这个类不做任何缓存</h2>
 * 它只被核心的 {@code getDisplayRecipes()} 现场调用，而指南每次翻页/重开都会重新调一次
 * {@code getDisplayRecipes()}。所以"往配方注册表里加一条 → 书里多一条"是自然成立的，
 * 不需要任何失效通知（详见 {@code Saizenbako#getDisplayRecipes} 的注释）。
 */
public final class RecipePages {

    /** 指南每页能放的展示槽数（9 行 × 输入/输出两列）。 */
    public static final int PER_PAGE = 18;

    private RecipePages() {
    }

    // ---------------------------------------------------------------- 单个条目

    /**
     * 输入条目。
     *
     * @param icon   要显示的物品（会被 clone，原物不受影响）
     * @param index  这条配方里第几个输入（0 开始）—— 写成 lore 里的 {@code 材料 N}（N = index + 1）
     * @param amount 需要多少个
     * @param extra  额外说明行（可空；例如祭坛的"木桩编号"）
     */
    public static ItemStack input(ItemStack icon, int index, int amount, List<String> extra) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&a材料 " + (index + 1));
        if (extra != null) {
            lore.addAll(extra);
        }
        lore.add("&c输入数量: " + amount);
        // 角标最多显示 64 个（原版限制），超出的靠上面那行文字表达
        return named(icon, Math.max(1, Math.min(amount, 64)), null, lore);
    }

    /**
     * 输出条目。
     *
     * @param icon   要显示的物品（会被 clone）
     * @param index  这条配方里第几个输出（0 开始）—— 写成 lore 里的 {@code 产物 N}
     * @param amount 产出多少个
     * @param ticks  进程耗时（Slimefun tick）；{@code null} 表示不写这一行
     * @param extra  额外说明行（可空）
     */
    public static ItemStack output(ItemStack icon, int index, int amount, Integer ticks,
                                   List<String> extra) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        lore.add("&a产物 " + (index + 1));
        if (extra != null) {
            lore.addAll(extra);
        }
        if (amount > 64 || amount <= 0) {
            lore.add("&c输出数量: " + amount);
        }
        if (ticks != null) {
            lore.add("&e进程耗时: " + secondsText(ticks) + "s (" + ticks + "tick)");
        }
        return named(icon, Math.max(1, Math.min(amount, 64)), null, lore);
    }

    /**
     * tick 数 → 秒数文本（整数就不带小数点：{@code 300} 而不是 {@code 300.0}）。
     *
     * <p>★ 换算刻意走本工程自己的 {@link ReactorManager#ticksToSeconds(long)}
     * （它按服务端的 Slimefun {@code tickRate} 算：1 Slimefun tick = tickRate/20 秒），
     * <b>不</b>照抄 LogiTech 的硬编码 {@code time / 2}。
     * 理由：LogiTech 那个 {@code /2} 隐含"ticks 是原版 Minecraft tick（20/秒）"，
     * 而 Slimefun 机器的 tick 是 {@code MachineProcessor} 的 <b>Slimefun tick</b>
     * （本机 {@code custom-ticker-delay: 10} ⇒ 2 tick/秒，即每次 ticker 调用算一 tick）。
     * 本机 tickRate=10 时两个公式恰好同为 {@code time/2}，看不出区别；
     * 但 tickRate 一改就分叉（tickRate=2 时是 60 秒 vs 300 秒），
     * 而核心 GUI 信息格里的"本次进程总时长"用的正是 {@code ticksToSeconds} ——
     * 抄那个常数迟早会让书和 GUI 各说各话。
     */
    private static String secondsText(int ticks) {
        double seconds = ReactorManager.ticksToSeconds(ticks);
        if (Math.abs(seconds - Math.rint(seconds)) < 1.0e-6D) {
            return String.valueOf((long) Math.rint(seconds));
        }
        return String.valueOf(Math.round(seconds * 10.0D) / 10.0D);
    }

    /**
     * 纯说明条目（不是"产物"，只是一种带名字与 lore 的图标）。
     *
     * <p>用来把那些"没有输入、只有参数"的东西放进配方页 ——
     * 例如反应堆的发电功率、祭坛一次运作的 POWER 消耗。放在输出列（奇数下标）时，
     * 左列自然留空（{@link #block} 会对齐成 {@code null}），视觉上与一条配方并排但不误导。
     */
    public static ItemStack note(Material material, String name, List<String> lore) {
        return named(new ItemStack(material), 1, name, lore);
    }

    /** 纯说明条目的简写：直接拿一个已有物品当图标（保留它的粘液 id）。 */
    public static ItemStack note(ItemStack icon, String name, List<String> lore) {
        return named(icon, 1, name, lore);
    }

    /**
     * 「该格不限制」的占位条目。
     *
     * <p>★ 为什么要占位而不是跳过：祭坛的配方是"6 个编号槽位"的结构，
     * 玩家看配方页时最需要的是"第 N 号木桩要放什么"。
     * 若把没登记的槽位直接跳过，剩下几行的「材料 N」编号就会<b>错位</b>
     * （看起来像"材料 1、材料 3、材料 5"，而实际要求是 1、3、5 号木桩）。
     * 所以不登记的槽位也照常占一行，明确写"该格不限制"。
     */
    public static ItemStack freeSlot(int index, List<String> extra) {
        List<String> lore = new ArrayList<>();
        lore.add("");
        // "该格不限制"直接写在标签行里：诊断输出的"首行"就是这个标签，
        // 分开写会让 /touhou guide 打印出来的那行看起来像"这一格要什么材料"
        lore.add("&7材料 " + (index + 1) + " &8（该格不限制）");
        if (extra != null) {
            lore.addAll(extra);
        }
        lore.add("&8放什么都行，也不参与消耗");
        return named(new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE), 1, null, lore);
    }

    // ---------------------------------------------------------------- 一条配方

    /**
     * 把"一条配方的输入们 / 输出们"排成指南要的条目序列。
     *
     * <p>对齐规则（与 LogiTech 的 {@code _getDisplayRecipes} 完全一致）：
     * <b>输入靠上、输出靠下</b>，整块高度 = {@code max(输入数, 输出数)}，短的那边用
     * {@code null} 补空位。于是"6 个材料 + 1 个产物"的祭坛配方渲染成：
     * <pre>
     *   材料1 | (空)
     *   材料2 | (空)
     *   …
     *   材料6 | 产物1
     * </pre>
     * 而不是把产物孤零零地放在第一行。
     *
     * <p>返回的表<b>长度恒为偶数</b>（指南按"两两一组"渲染，奇数长度会让最后一条配不上对）。
     */
    public static List<ItemStack> block(List<ItemStack> inputs, List<ItemStack> outputs) {
        List<ItemStack> in = inputs == null ? List.of() : inputs;
        List<ItemStack> out = outputs == null ? List.of() : outputs;
        List<ItemStack> result = new ArrayList<>();
        int len = Math.max(in.size(), out.size());
        if (len == 0) {
            return result;
        }
        int firstOutput = len - out.size();     // 输出靠下：从这里开始填
        for (int i = 0; i < len; i++) {
            result.add(i < in.size() ? in.get(i) : null);
            result.add(i >= firstOutput ? out.get(i - firstOutput) : null);
        }
        return result;
    }

    // ---------------------------------------------------------------- 诊断

    /**
     * 把展示列表渲染成"每一页每个 ItemStack 的显示名 + lore"。
     *
     * <p>给 {@code /touhou guide} 用 —— 那条命令的意义是：<b>不进游戏也能证明</b>
     * "配方页里到底是什么、是不是从注册表长出来的"。
     *
     * <p>每一行长这样：
     * <pre>
     *   [10] 输入列 行6  Red Dye | 材料 6 | 木桩 #5（预留槽 4） | 输入数量: 1
     *   [11] 输出列 行6  POWER存储单元 | 产物 1 | 产出到核心 GUI 的 IO 槽 | 消耗: 2 POWER
     * </pre>
     * 左侧的"第几行、哪一列"与指南里的实际落点一一对应
     * （偶数下标 = 输入列，奇数 = 输出列；每页 9 行）。
     *
     * <p>★ 只省略<b>空行</b>：LogiTech 的风格是在标签前留一行空行做间距，
     * 那一行不携带任何信息，打印出来只会让报告变难读；
     * 其余每一行（材料 N / 输入数量 / 产物 N / 进程耗时…）都原样列出 ——
     * 需求要的"lore 首行"在最前面，后面的几行是<b>顺带给出</b>的，
     * 因为"输入数量: 512"这种关键信息本来就不在第一行。
     */
    public static List<String> dump(List<ItemStack> display) {
        List<String> out = new ArrayList<>();
        if (display == null) {
            out.add("  (null —— 核心根本没有实现展示列表)");
            return out;
        }
        out.add("  展示列表共 " + display.size() + " 个槽（"
                + (display.size() + PER_PAGE - 1) / PER_PAGE + " 页，每页 " + PER_PAGE + " 槽）");
        for (int i = 0; i < display.size(); i++) {
            if (i % PER_PAGE == 0) {
                out.add("  ---- 第 " + (i / PER_PAGE + 1) + " 页 ----");
            }
            String side = i % 2 == 0 ? "输入列" : "输出列";
            out.add("    [" + i + "] " + side + " 行" + (i % PER_PAGE / 2 + 1)
                    + "  " + describe(display.get(i)));
        }
        return out;
    }

    /** 单个条目的诊断文本：{@code 显示名 | lore每一非空行…}（空槽写成 {@code (空槽)}）。 */
    public static String describe(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "(空槽)";
        }
        StringBuilder sb = new StringBuilder(labelOf(item));
        for (String line : loreLinesOf(item)) {
            sb.append(" | ").append(line);
        }
        return sb.length() == 0 ? labelOf(item) + " | (无 lore)" : sb.toString();
    }

    /** 显示名（去颜色代码）；没有自定义名字时退回<b>材质的可读名</b>。
     *
     * <p>★ 兜底刻意走 {@link StructureMaterials#materialName}：
     * 原版物品（红色染料、钻石…）没有自定义显示名，直接写枚举名会得到
     * {@code RED_DYE} 这种"机器话"；这里取的是语言文件里的名字（{@code Red Dye}），
     * 与玩家在物品提示框里看到的是同一套口径。
     */
    public static String labelOf(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "(空)";
        }
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String plain = Notify.plain(meta.getDisplayName());
            if (plain != null && !plain.isBlank()) {
                return plain;
            }
        }
        return StructureMaterials.materialName(item.getType());
    }

    /** 全部非空 lore 行（去颜色代码）。 */
    public static List<String> loreLinesOf(ItemStack item) {
        List<String> out = new ArrayList<>();
        if (item == null || item.getType().isAir()) {
            return out;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getLore() == null) {
            return out;
        }
        for (String line : meta.getLore()) {
            String plain = Notify.plain(line);
            if (plain != null && !plain.isBlank()) {
                out.add(plain);
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 物品加工

    /**
     * 给物品设置显示名（可选）与 lore —— 与 {@code UtsuhoReactorCore#named} 同一套写法。
     *
     * <p>★ 为什么必须 {@code clone()} 再改：入参往往是
     * {@code SlimefunItemStack}/{@code SlimefunItems.XXX} 这类<b>共享模板</b>
     * （Slimefun 会对它们调 {@code lock()}，改了直接抛 {@code WrongItemStackException}），
     * 而且就地改会把"所有玩家的指南页"一起改掉。
     *
     * <p>★ 颜色代码必须自己翻：{@code ItemMeta#setLore} 不认识 {@code &}，
     * 直接塞进去玩家看到的就是字面的 {@code &a材料 1}。
     */
    private static ItemStack named(ItemStack icon, int amount, String name, List<String> lore) {
        ItemStack out = (icon == null ? new ItemStack(Material.PAPER) : icon.clone());
        out.setAmount(Math.max(1, Math.min(amount, 64)));
        ItemMeta meta = out.getItemMeta();
        if (meta != null) {
            if (name != null) {
                meta.setDisplayName(ReactorManager.color(name));
            }
            if (lore != null && !lore.isEmpty()) {
                List<String> colored = new ArrayList<>(lore.size());
                for (String line : lore) {
                    colored.add(ReactorManager.color(line));
                }
                meta.setLore(colored);
            }
            out.setItemMeta(meta);
        }
        return out;
    }
}
