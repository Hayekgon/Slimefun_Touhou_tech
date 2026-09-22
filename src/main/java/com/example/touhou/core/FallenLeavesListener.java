package com.example.touhou.core;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Tag;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 「落叶」的事件入口 —— 监听 {@link BlockDropItemEvent}。
 *
 * <h2>★ 为什么是这个事件（而不是需求里写的 {@code BlockBreakEvent}）</h2>
 * 需求要"玩家手动破坏树叶时掉落"+"<b>追加到原版掉落里</b>"，而掉落列表
 * 根本不在 {@code BlockBreakEvent} 上（已用 {@code javap} 在运行期 paper-api 核实）：
 * <pre>
 *   BlockBreakEvent extends BlockExpEvent extends BlockEvent
 *     BlockEvent      → getBlock()
 *     BlockExpEvent   → + getExpToDrop() / setExpToDrop()
 *     BlockBreakEvent → + getPlayer() / setDropItems(boolean) / isDropItems()
 *     ★ 全程【没有】getDrops()
 *   BlockDropItemEvent → getBlock(), getPlayer(), getBlockState(), List&lt;Item&gt; getItems()
 * </pre>
 * 所以"追加到原版掉落"只能在 {@code BlockDropItemEvent} 里做 —— 而它<b>同样</b>
 * 满足"只在玩家手动破坏时触发"：它由玩家破坏方块后系统生成掉落物时触发，
 * <b>爆炸 / 水流 / 活塞都不触发</b>。
 *
 * <h2>★ 剪刀 / 精准采集：直接跳过（这一条顺带堵掉了刷物品路径）</h2>
 * 用户口径：<b>用剪刀破坏</b>与<b>用带精准采集的工具破坏</b>时<b>都</b>不掉落叶
 * （两项任一成立就跳过；剪刀+精准采集同时存在自然也跳）。
 *
 * <p>★ 为什么这条很关键：<b>恰恰是剪刀与精准采集会让玩家拿到"树叶方块本身"</b>
 * （徒手/普通工具破坏树叶只掉树苗/木棍），而拿到方块就能"放置 → 破坏"无限循环。
 * 把这两条排除之后，正常破坏不掉方块，那个循环就断了。
 * 详见 {@code FallenLeaves#isExcludedTool} 的注释与报告。
 *
 * <p>★ 工具取自<b>主手</b>（{@code getItemInMainHand()}）：
 * 原版破坏方块用的是主手（{@code ServerPlayerGameMode#destroyBlock} 读的
 * {@code player.getMainHandItem()}），副手不参与破坏判定 ——
 * 所以"副手拿剪刀、主手空手破坏"在原版就是徒手破坏，照旧掉落，这是刻意的。
 *
 * <h2>掉落怎么给</h2>
 * 走 {@code BlockDropItemEvent}：那批掉落物是原版已经算好的结果
 * （时运 / 精准采集等逻辑都已跑完），我们<b>不重算、不覆盖</b>，
 * 只是"再生成一个 Item 实体并登记进这次的掉落列表"。
 * ★ 不能凭空 {@code new} 一个 {@code Item} 塞进 {@code getItems()} ——
 * 列表里放的是世界里<b>已经存在</b>的实体，没有公开构造器；
 * 所以实际做法是 {@code World#dropItem(...)} 生成后登记进列表，
 * 这样后续监听器/取消逻辑也能看到它。
 */
public class FallenLeavesListener implements Listener {

    /**
     * 玩家破坏方块、系统生成掉落物时触发。
     *
     * <p>流程：判树叶（{@link FallenLeaves#isLeaves}）→ 判工具是否被排除
     * （剪刀 / 精准采集）→ 掷骰子 → 生成并登记掉落。
     *
     * <p>★ 任何异常都不能抛回事件总线（会变成 "Could not pass event" 并可能
     * 打断其它插件的后处理）—— 这里兜住并记 {@code Log.severe}。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDropItems(BlockDropItemEvent e) {
        try {
            if (e.isCancelled()) {
                return;
            }
            if (!FallenLeaves.isLeaves(e.getBlock().getType())) {
                return;
            }
            // ★ 剪刀 / 精准采集：跳过（这一条顺带堵掉"放置-破坏"刷落叶的路径）
            ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
            if (FallenLeaves.isExcludedTool(tool)) {
                return;
            }
            Integer amount = FallenLeaves.rollLeaves(ThreadLocalRandom.current());
            if (amount == null) {
                return;
            }
            ItemStack drop = FallenLeaves.createDrop(amount);
            if (drop == null) {
                return;
            }
            List<Item> items = e.getItems();
            if (items == null) {
                return;
            }
            Item added = e.getBlock().getWorld().dropItem(
                    e.getBlock().getLocation().add(0.5D, 0.5D, 0.5D), drop);
            items.add(added);
            Log.info("[LEAVES] " + e.getPlayer().getName() + " 破坏 "
                    + e.getBlock().getType() + " 掉落落叶 x" + amount);
        } catch (RuntimeException ex) {
            // 破坏方块是高频路径：绝不能把异常抛回事件总线
            Log.severe("[LEAVES] 处理落叶掉落时异常（方块 "
                    + (e.getBlock() == null ? "?" : e.getBlock().getType().name()) + "）", ex);
        }
    }

    /** 供诊断：本类监听了什么（{@code /touhou leaves selfcheck} 打印）。 */
    public static List<String> describe() {
        return List.of(
                "BlockDropItemEvent（玩家破坏方块、系统生成掉落物时触发）",
                "判据用 Tag.LEAVES —— 涵盖全部树种（含樱花/红树/杜鹃），新增树种自动跟上",
                "爆炸 / 水流 / 活塞不触发本事件 ⇒ 需求要的『只在玩家手动破坏时』是免费拿到的",
                "★ 剪刀 或 精准采集（任一成立） ⇒ 跳过，不掉落叶（用户口径）",
                "   —— 这条同时堵掉了『放置-破坏』刷落叶的路径：只有剪刀/精准采集才拿得到树叶方块本身",
                "工具取主手（原版破坏用主手；副手不参与破坏判定）",
                "优先级 MONITOR（只追加，不取消、不参与裁决）",
                "掉落方式：生成 Item 实体并登记进 event.getItems()（= 原版那批掉落物，不覆盖其时运/精准采集结果）",
                "裁决内核与 /touhou leaves 走的是同一个方法 FallenLeaves#rollLeaves");
    }
}
