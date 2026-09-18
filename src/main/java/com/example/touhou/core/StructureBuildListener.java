package com.example.touhou.core;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * <b>结构变动 → 触发检测</b>：反应堆现在唯一的常规检测来源。
 *
 * <p>需求：「扫描改成双向配合，仅当核心与其他结构方块被放置/破坏时进行一次检测」。
 * 于是本监听器把"世界变动"翻译成 {@link ReactorManager#onStructureEdited} 调用，
 * 剩下的判断（谁找谁、找不找得到、找到之后怎么办）全在那边，监听器本身只管
 * "这一下变动算不算结构变动"。
 *
 * <h2>为什么用全局监听器，而不是给每个构件注册 BlockPlaceHandler</h2>
 * <ul>
 *   <li>构件有 4 种（框架/保护罩/稳定器/基座）+ 2 种接口，每个都要注册一遍，
 *       以后加构件还得记得加；</li>
 *   <li>构件的注册处（{@code AddSlimefunItems}）保持干净；</li>
 *   <li><b>破坏</b>这条路本来就没有对应的 item handler，只能靠事件；</li>
 *   <li>用 {@code BlockStorage.checkID} 反查即可，与结构检测本身用的是同一套判据。</li>
 * </ul>
 *
 * <h2>为什么优先级是 MONITOR</h2>
 * Slimefun 自己在 {@code HIGHEST} 处理放置/破坏（见 {@code BlockListener}）。
 * 我们要在它<b>之后</b>跑，才能保证：
 * <ul>
 *   <li>放置时方块数据已经被 {@code createBlock} 写好（否则 {@code checkID} 是 null）；</li>
 *   <li>破坏时能尊重别的插件（领地保护等）的取消 —— {@code MONITOR} 是最后一个优先级，
 *       此时取消已经全部发生，配 {@code ignoreCancelled = true} 就不会误判。</li>
 * </ul>
 * 好消息是 Slimefun 破坏方块时<b>只是标记</b> {@code pendingRemove}，
 * 并没有当场清掉方块数据，所以 MONITOR 阶段 {@code checkID} 仍然读得到 id
 * （这一点是看反编译源码确认的，不是猜的）。
 *
 * <p>⚠ 本监听器<b>不取消任何事件</b>：玩家该怎么玩还怎么玩，我们只是"顺便看一眼"。
 */
public class StructureBuildListener implements Listener {

    /**
     * 玩家放下方块。
     *
     * <p>不需要再判"是不是我们的物品"：方块数据已经写好，直接问 {@code checkID} 即可 ——
     * 这样连"玩家用发射器/其它插件放置"也一并覆盖了。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        handle(e.getBlock(), ReactorManager.Edit.PLACED, e.getPlayer());
    }

    /**
     * 玩家挖掉方块。
     *
     * <p>按需求，破坏与放置走<b>同一套双向逻辑</b>：
     * 拆掉的如果是构件，就"静默找核心 → 找到则带动核心做一次整套检测"；
     * 拆掉的如果是核心，则解绑附近接口（不需要做无意义的检测）。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        handle(e.getBlock(), ReactorManager.Edit.BROKEN, e.getPlayer());
    }

    /**
     * 爆炸炸掉方块。
     *
     * <p>★ 为什么必须单独处理：<b>爆炸不触发 {@code BlockBreakEvent}</b>。
     * 只监听放置/破坏的话，"苦力怕把保护罩炸飞"这种最常见的事故不会被发现，
     * 反应堆会带着一个洞继续跑 —— 那才是真的 bug。
     *
     * <p>爆炸是<b>一次事件带一串方块</b>，正好体现去抖的价值：
     * 炸掉 20 块构件只会合成一轮检测（见 {@link ReactorManager#onStructureEdited}）。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        for (Block b : e.blockList()) {
            handle(b, ReactorManager.Edit.BROKEN, null);
        }
    }

    /** 实体（TNT / 苦力怕 / 凋灵）造成的爆炸，同上。 */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        for (Block b : e.blockList()) {
            handle(b, ReactorManager.Edit.BROKEN, null);
        }
    }

    /**
     * 实体把方块变成另一种方块（末影人搬方块、凋灵吃方块、村民等）。
     *
     * <p>结构构件被搬走同样属于"结构变动"，不处理就会留下一个检测不到的洞。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        handle(e.getBlock(), ReactorManager.Edit.BROKEN, null);
    }

    private static void handle(Block block, ReactorManager.Edit edit, Player actor) {
        if (block == null) {
            return;
        }
        Location loc = block.getLocation();
        // 先做一次便宜的过滤：绝大多数方块根本不是粘液方块
        SlimefunItem item = BlockStorage.check(loc);
        if (item == null || !isStructureRelated(item)) {
            return;
        }
        ReactorManager.onStructureEdited(loc, edit, actor);
    }

    /**
     * 这个粘液方块算不算"反应堆结构的组成"。
     *
     * <p>= 核心 / 框架 / 保护罩 / 稳定器 / 基座 / 输入接口 / 输出接口。
     * 用 id 前缀判断而不是逐个 {@code equals}：以后加新构件时不用改这里。
     */
    private static boolean isStructureRelated(SlimefunItem item) {
        if (item instanceof UtsuhoReactorCore || item instanceof AbstractReactorPort) {
            return true;
        }
        String id = item.getId();
        return id != null && id.startsWith("TOUHOU_COMPLEX_MACHINE_REACTOR_");
    }
}
