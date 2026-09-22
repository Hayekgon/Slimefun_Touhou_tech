package com.example.touhou.core;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;

/**
 * 「落叶」的获取内核 —— <b>玩家手动破坏树叶</b>时按概率掉落。
 *
 * <h2>为什么做成"静态内核 + 薄事件壳"</h2>
 * 掉落裁决全部收在本类（{@link #rollLeaves(java.util.Random)}），
 * 事件监听器 {@link FallenLeavesListener} 只负责"把结果追加到原版掉落里"。
 * 这样 {@code /touhou leaves} 能用<b>同一个方法</b>跑大样本统计 ——
 * 于是"命令算出来的掉率"就是"玩家实际会遇到的掉率"，
 * 不存在两套逻辑（本项目一贯的做法，见 {@code EchoOfAnotherWorld#shuttle}）。
 *
 * <h2>★ 为什么不用 {@code BlockBreakEvent#getDrops()}</h2>
 * 因为<b>它没有这个方法</b>（已用 {@code javap} 在运行期 paper-api 上核实）：
 * <pre>
 *   BlockBreakEvent extends BlockExpEvent extends BlockEvent
 *     → 只有 getBlock() / getPlayer() / setDropItems(boolean) / getExpToDrop()
 *   BlockEvent 只有 getBlock()，BlockExpEvent 只加 getExpToDrop()
 * </pre>
 * 真正持有掉落列表的是 {@code BlockDropItemEvent#getItems()}：
 * <pre>
 *   public List&lt;org.bukkit.entity.Item&gt; getItems();
 * </pre>
 * 所以"把落叶<b>追加进原版掉落</b>"这件事只能在 {@code BlockDropItemEvent} 里做 ——
 * 而需求里说的"用 BlockBreakEvent"想表达的其实是"<b>只在玩家手动破坏时</b>触发"，
 * 那一点由 {@code BlockDropItemEvent} 同样满足（它只在玩家破坏方块、且系统真的
 * 产生了掉落物时触发；爆炸 / 水流 / 活塞都不会触发）。
 */
public final class FallenLeaves {

    private FallenLeaves() {
    }

    /** 本物品在注册表里的 id（命令与日志引用同一处，避免硬编码散落）。 */
    public static final String ID = "TOUHOU_MATERIAL_FALLEN_LEAVES";

    /**
     * 诊断追踪剩余次数（{@code /touhou leaves watch [n]} 设置）。
     *
     * <h2>★ 为什么必须有这个东西（真实踩点）</h2>
     * 玩家实测"用锄头挖树叶挖了几十个都不掉"，而控制台里<b>一行痕迹都没有</b>。
     * 原因之一是 {@code logging.console-info} 默认为 {@code false}，
     * 成功行被静默了 —— 于是"没触发"与"触发了但没打印"完全无法区分。
     *
     * <p>这里给一个显式的、<b>不受任何开关压制</b>的追踪窗口：窗口打开时
     * 每一次"玩家破坏树叶"都打印一条，并且能明确回答最关键的那个问题 ——
     * <b>{@code BlockDropItemEvent} 到底有没有对这个方块触发</b>。
     * （见 {@link #markBreak} / {@link #noteDropSeen} 的用法。）
     */
    private static final java.util.concurrent.atomic.AtomicInteger WATCH =
            new java.util.concurrent.atomic.AtomicInteger();

    /** 打开诊断追踪窗口：接下来 {@code n} 次"玩家破坏树叶"会被逐次打印。 */
    public static void startWatch(int n) {
        WATCH.set(Math.max(1, n));
    }

    /** 关闭诊断追踪窗口。 */
    public static void stopWatch() {
        WATCH.set(0);
    }

    /** 当前追踪窗口剩余次数。 */
    public static int watchRemaining() {
        return WATCH.get();
    }

    /**
     * 追踪窗口是否打开着。
     *
     * <p>给那些"每次都打印会刷屏"的判定细节用（命中/未命中、被排除的原因）——
     * 它们默认静默，只有玩家主动用 {@code /touhou leaves watch} 要线索时才输出。
     */
    public static boolean isWatching() {
        return WATCH.get() > 0;
    }

    /**
     * 这次破坏"要不要掉落叶"——掉落数量的上限（防配置写炸）。
     *
     * <p>★ 为什么要钳：{@link #rollLeaves} 用 {@code nextInt(max-min+1)}，
     * 若配置把 max 写成 {@code Integer.MAX_VALUE}，{@code max - min + 1} 会溢出成负数，
     * {@code nextInt(负数)} 直接抛 {@code IllegalArgumentException} ——
     * 那会在破坏方块时炸事件总线。项目里 {@code AddonConfig} 的校验也钳一道，
     * 这里是第二道保险（内核不该假设调用方一定校验过）。
     */
    public static final int MAX_DROP_AMOUNT = 64;

    /**
     * 掷一次骰子：命中就返回"这次该掉几个"，没命中返回 {@code null}。
     *
     * <p>★ 入参是 {@link Random} 而不是 {@code ThreadLocalRandom}：
     * 无头验证要跑 1000 次并核对分布，<b>用固定种子可复现</b>；
     * 线上则传 {@link ThreadLocalRandom#current()}（该事件在主线程，但用 ThreadLocalRandom 更省心）。
     *
     * @return 掉落数量（&gt;= 1）；没命中返回 {@code null}
     */
    public static Integer rollLeaves(Random random) {
        AddonConfig cfg = AddonConfig.get();
        if (!cfg.fallenLeavesEnabled) {
            return null;
        }
        double chance = cfg.fallenLeavesDropChance;
        if (chance <= 0.0D) {
            return null;
        }
        if (chance < 1.0D && random.nextDouble() >= chance) {
            return null;
        }
        int min = Math.max(1, Math.min(cfg.fallenLeavesMinAmount, MAX_DROP_AMOUNT));
        int max = Math.max(min, Math.min(cfg.fallenLeavesMaxAmount, MAX_DROP_AMOUNT));
        return min + random.nextInt(max - min + 1);
    }

    /**
     * 判断一个方块类型是不是树叶。
     *
     * <p>★ 用 {@link Tag#LEAVES} 而不是逐个列 {@code Material}：
     * 那一个标签涵盖全部树种（橡木 / 云杉 / 白桦 / 丛林 / 金合欢 / 深色橡木 /
     * 红树 / 樱花 / 杜鹃 / 开花杜鹃…），以后新版本加树种<b>自动跟上</b>，
     * 自己列清单则一定会漏。
     */
    public static boolean isLeaves(Material type) {
        return type != null && Tag.LEAVES.isTagged(type);
    }

    /**
     * 这个工具是不是"被排除"的 —— 用它破坏树叶<b>不掉</b>落叶。
     *
     * <h2>判据（两项任一成立即排除）</h2>
     * <ol>
     *   <li><b>剪刀</b>：{@code tool.getType() == Material.SHEARS}。
     *       ★ 这里<b>没有</b>用 {@code Tag.ITEMS_SHEARS} —— 本 API 版本里
     *       <b>没有那个标签</b>（已用 {@code javap} 把 {@code org.bukkit.Tag} 的常量
     *       逐个看过：只有 {@code ITEMS_SWORDS / ITEMS_AXES / ITEMS_PICKAXES /
     *       ITEMS_SHOVELS / ITEMS_HOES / ITEMS_TOOLS …}，<b>没有 SHEARS</b>）。
     *       所以直接判材质 —— 1.20.4 里"剪刀"就是唯一一个 {@code SHEARS}。</li>
     *   <li><b>精准采集</b>：{@code tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0}。
     *       ★ 枚举名已用 {@code javap} 在运行期 paper-api 上核实：
     *       1.20.4 仍然是 {@code Enchantment.SILK_TOUCH} 这个静态字段
     *       （更高版本改成了注册表查询，不能凭记忆写）。
     *       用 {@code getEnchantmentLevel > 0} 而不是 {@code containsEnchantment}：
     *       前者对"等级为 0 的非法条目"也安全。</li>
     * </ol>
     * 两项<b>都</b>判（不是一个 else）⇒ "精准采集的剪刀"这种同时成立的情形也覆盖到了。
     *
     * <h2>★ 为什么这条判据同时也是"防刷"（重要）</h2>
     * 恰恰是<b>剪刀与精准采集会让玩家拿到"树叶方块本身"</b>
     * （徒手/普通工具破坏树叶只掉树苗与木棍），而<b>拿到方块就能"放置 → 破坏"无限循环</b>。
     * 把这两条排除之后，正常破坏不掉方块，那个自循环就断了。
     * （残余路径见报告的刷物品评估一节。）
     *
     * <p>两个排除项各自受配置开关控制（{@code leaves.drop-with-shears} /
     * {@code leaves.drop-with-silk-touch}，默认都是 {@code false} = 不生效）——
     * 万一以后想放开，不必改代码。
     */
    public static boolean isExcludedTool(ItemStack tool) {
        AddonConfig cfg = AddonConfig.get();
        if (tool == null || tool.getType().isAir()) {
            return false;       // 徒手：不算被排除
        }
        if (!cfg.fallenLeavesDropWithShears && tool.getType() == Material.SHEARS) {
            return true;
        }
        return !cfg.fallenLeavesDropWithSilkTouch
                && tool.getEnchantmentLevel(Enchantment.SILK_TOUCH) > 0;
    }

    /**
     * 造一个"落叶 × amount"的物品栈（<b>从物品模板克隆</b>，于是 PDC 里的粘液 id 是对的）。
     *
     * @return 物品栈；模板还没建好/数量非法时返回 {@code null}
     */
    public static ItemStack createDrop(int amount) {
        if (amount <= 0 || AddItems.FALLEN_LEAVES == null) {
            return null;
        }
        ItemStack stack = AddItems.FALLEN_LEAVES.clone();
        stack.setAmount(Math.min(amount, MAX_DROP_AMOUNT));
        return stack;
    }
}
