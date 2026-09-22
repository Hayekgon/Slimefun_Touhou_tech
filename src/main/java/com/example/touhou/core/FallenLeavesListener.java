package com.example.touhou.core;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 「落叶」的事件入口。
 *
 * <h2>★★ 核心教训：掉落判定挂在 {@code BlockBreakEvent}，不是 {@code BlockDropItemEvent}</h2>
 * 第一版把判定挂在 {@code BlockDropItemEvent} 上（理由是"它带有 {@code getItems()} 掉落列表，
 * 可以把落叶<b>追加</b>进原版掉落"）。<b>实机复测直接失败</b>：
 * 玩家用普通锄头挖了几十块树叶，<b>一个都没掉</b>。
 *
 * <p>根因：{@code BlockDropItemEvent} <b>只在"系统真的为这个方块生成了至少一件掉落物"时才触发</b>。
 * 而<b>徒手 / 用锄头破坏树叶时原版掉落常常是空的</b>（树叶掉树苗是概率的，多数时候什么都不掉）
 * ⇒ 事件根本不触发 ⇒ 判定代码一次都没跑到。这也是为什么当时控制台里
 * <b>既没有成功行也没有失败行</b> —— 不是被日志开关静默了，是压根没执行。
 *
 * <p>所以现在的结构是：
 * <ul>
 *   <li>{@link #onBlockBreak} —— <b>唯一的判定与掉落入口</b>。
 *       破坏方块<b>一定</b>会触发它，与"原版掉不掉东西"无关。
 *       落叶自己生成一个 Item 实体丢到世界上（不去动原版掉落）。</li>
 *   <li>{@link #onBlockDropItems} —— <b>只做诊断打印，不产生任何掉落</b>。
 *       它的价值是回答"这一次原版掉落是不是空的"（是否打印了它），
 *       以及把原版掉落内容读出来。</li>
 * </ul>
 * 两处<b>不重复判定</b>（判定单点在 {@link #onBlockBreak}），所以不可能掉两份。
 *
 * <h2>剪刀 / 精准采集：跳过（这一条顺带堵掉了刷物品路径）</h2>
 * 用户口径：<b>用剪刀破坏</b>与<b>用带精准采集的工具破坏</b>时<b>都</b>不掉落叶
 * （两项任一成立就跳过；剪刀+精准采集同时存在自然也跳）。
 *
 * <p>★ 为什么这条很关键：<b>恰恰是剪刀与精准采集会让玩家拿到"树叶方块本身"</b>
 * （徒手/普通工具破坏树叶只掉树苗/木棍），而拿到方块就能"放置 → 破坏"循环。
 * 把这两条排除之后，正常破坏不掉方块。
 * 详见 {@link FallenLeaves#isExcludedTool} 的注释与报告。
 *
 * <p>★ 工具取自<b>主手</b>（{@code getItemInMainHand()}）：
 * 原版破坏方块用的是主手（{@code ServerPlayerGameMode#destroyBlock} 读的
 * {@code getMainHandItem()}），副手不参与破坏判定 ——
 * 所以"副手拿剪刀、主手空手破坏"在原版就是徒手破坏，照旧掉落，这是刻意的。
 */
public class FallenLeavesListener implements Listener {

    /**
     * 判定 + 生成掉落的<b>唯一</b>入口：玩家破坏方块时触发。
     *
     * <p>流程：判树叶（{@link FallenLeaves#isLeaves}）→ 判工具是否被排除
     * （剪刀 / 精准采集）→ 掷骰子 → 生成落叶 Item 实体。
     *
     * <p>★ 任何异常都不能抛回事件总线（会变成 "Could not pass event" 并可能
     * 打断其它插件的后处理）—— 这里兜住并记 {@code Log.severe}。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        try {
            if (!FallenLeaves.isLeaves(e.getBlock().getType())) {
                return;
            }
            ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
            // ★ 追踪行：证明"玩家真的在破坏树叶"。只在追踪窗口打开时打印
            //   （破坏方块是高频路径，默认不能刷屏）。走 Log.always
            //   —— 不受 console-info 影响，因为"打开了 watch"就是要看线索。
            //   加在排除判据【之前】，这样"被判据挡下"与"事件没触发"能分清。
            if (FallenLeaves.isWatching()) {
                Log.always(prefix() + "[BREAK] 方块=" + e.getBlock().getType()
                        + "  坐标=" + coord(e.getBlock())
                        + "  玩家=" + e.getPlayer().getName()
                        + "  游戏模式=" + e.getPlayer().getGameMode()
                        + "  主手=" + toolName(tool)
                        + "  被排除=" + FallenLeaves.isExcludedTool(tool));
            }
            if (FallenLeaves.isExcludedTool(tool)) {
                if (FallenLeaves.isWatching()) {
                    Log.always(prefix() + "  ⇒ 工具被排除（剪刀 / 精准采集），本次不掉落叶");
                }
                return;
            }
            Integer amount = FallenLeaves.rollLeaves(ThreadLocalRandom.current());
            if (amount == null) {
                if (FallenLeaves.isWatching()) {
                    Log.always(prefix() + "  ⇒ 概率未命中（掉率="
                            + AddonConfig.get().fallenLeavesDropChance + "），本次不掉落叶");
                }
                return;
            }
            ItemStack drop = FallenLeaves.createDrop(amount);
            if (drop == null) {
                Log.severe("[LEAVES] ★创建掉落栈失败（物品模板 " + FallenLeaves.ID + " 为空？）");
                return;
            }
            // ★ 直接生成实体丢到世界上。刻意【不去动原版掉落】：
            //   原版这一批该掉什么还掉什么（时运/精准采集的结果由原版自己算），
            //   我们只是额外多丢一份落叶 —— 不存在"两套逻辑互相覆盖"。
            //   生成在方块中心：破坏的瞬间方块会变成空气，物品落在原地正常下坠。
            e.getBlock().getWorld().dropItem(
                    e.getBlock().getLocation().add(0.5D, 0.5D, 0.5D), drop);
            if (FallenLeaves.isWatching()) {
                Log.always(prefix() + "  ⇒ ★命中，已生成落叶 x" + amount);
            } else {
                Log.info("[LEAVES] " + e.getPlayer().getName() + " 破坏 "
                        + e.getBlock().getType() + " 掉落落叶 x" + amount);
            }
        } catch (RuntimeException ex) {
            // 破坏方块是高频路径：绝不能把异常抛回事件总线
            Log.severe("[LEAVES] 处理落叶掉落时异常（方块 "
                    + (e.getBlock() == null ? "?" : e.getBlock().getType().name()) + "）", ex);
        }
    }

    /**
     * ★ <b>只做诊断打印</b>：原版为这个方块生成了掉落物时触发。
     *
     * <p>它<b>不产生任何掉落</b>（判定与掉落单点在 {@link #onBlockBreak}）。
     * 存在的意义只有一个：回答"这一次原版掉落是不是空的" ——
     * <b>打印了</b>它 = 原版这一批非空；<b>没有打印</b>它 = 原版掉落为空
     * （正是锄头挖树叶时的情形，也是第一版失效的根因）。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockDropItems(BlockDropItemEvent e) {
        try {
            if (e.isCancelled() || !FallenLeaves.isLeaves(e.getBlock().getType())) {
                return;
            }
            if (!FallenLeaves.isWatching()) {
                return;     // 诊断输出只在追踪窗口打开时产生，否则会刷屏
            }
            Log.always(prefix() + "[DROP] 原版掉落非空、BlockDropItemEvent 已触发：方块="
                    + e.getBlock().getType() + "  坐标=" + coord(e.getBlock())
                    + "  原版掉落=" + dropSummary(e));
        } catch (RuntimeException ex) {
            Log.severe("[LEAVES] 诊断追踪异常（BlockDropItemEvent）", ex);
        }
    }

    /** 追踪行前缀 —— 打开窗口时带剩余次数，方便一眼看出窗口还在不在。 */
    private static String prefix() {
        int left = FallenLeaves.watchRemaining();
        return left > 0 ? "[LEAVES-WATCH 剩" + left + "] " : "[LEAVES] ";
    }

    private static String coord(org.bukkit.block.Block b) {
        return b.getX() + "," + b.getY() + "," + b.getZ();
    }

    private static String toolName(ItemStack tool) {
        if (tool == null || tool.getType().isAir()) {
            return "空手";
        }
        return String.valueOf(tool.getType());
    }

    /** 这一批原版掉落物的摘要（物品名 x数量）。 */
    private static String dropSummary(BlockDropItemEvent e) {
        List<Item> items = e.getItems();
        if (items == null || items.isEmpty()) {
            return "(空)";
        }
        StringBuilder sb = new StringBuilder();
        for (Item it : items) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(it.getItemStack().getType()).append('x').append(it.getItemStack().getAmount());
        }
        return sb.toString();
    }

    /** 供诊断：本类监听了什么（{@code /touhou leaves selfcheck} 打印）。 */
    public static List<String> describe() {
        return List.of(
                "★ 判定与掉落入口 = BlockBreakEvent（破坏一定会触发，与原版掉不掉东西无关）",
                "   ★★ 第一版挂在 BlockDropItemEvent 上，实机失败：徒手/锄头挖树叶时"
                        + "原版掉落常常是空的 ⇒ 该事件不触发 ⇒ 判定一次都没跑到",
                "BlockDropItemEvent = 只做诊断打印（证明原版掉落是否为空），不产生任何掉落",
                "判据用 Tag.LEAVES —— 涵盖全部树种（含樱花/红树/杜鹃），新增树种自动跟上",
                "爆炸 / 水流 / 活塞不触发 BlockBreakEvent（玩家破坏）⇒ 『只在玩家手动破坏时』是免费拿到的",
                "★ 剪刀 或 精准采集（任一成立） ⇒ 跳过，不掉落叶（用户口径）",
                "   —— 这条同时堵掉了『放置-破坏』的循环：只有剪刀/精准采集才拿得到树叶方块本身",
                "工具取主手（原版破坏用主手；副手不参与破坏判定）",
                "掉落方式：自己生成 Item 实体丢到世界（不动原版掉落，无覆盖风险）",
                "裁决内核与 /touhou leaves 走的是同一个方法 FallenLeaves#rollLeaves",
                "诊断追踪：/touhou leaves watch [n|off]（走 Log.always，不受 logging.console-info 影响）");
    }
}
