package com.example.touhou.core;

import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.util.Vector;

/**
 * 杀意的百合 —— 弹幕的命中、清理与无敌帧。
 *
 * <p>四件事，全部围绕<b>「追踪表不能泄漏」</b>这一条硬要求：
 * <ol>
 *   <li>{@link #onShotHit} —— 阶段一那支箭命中<b>方块或实体</b>：结算阶段一并触发阶段二；</li>
 *   <li>{@link #onChunkUnload} —— <b>所在区块被卸载</b>：把该区块里所有在途弹幕
 *       从追踪表里摘掉。这是"没有命中就消失"里最容易被漏掉的一条，
 *       也正是本任务点名要求验证的那条；</li>
 *   <li>{@link #onEntityRemove} —— 实体因为任何原因离开世界（{@code /kill}、插件清理、
 *       掉落物合并、区块卸载的 {@code UNLOAD} 因由…）时兜底摘表；</li>
 *   <li>{@link #onTrackedArrowHit} —— 箭矢命中时的无敌帧清零
 *       （与 {@link FantasySealArrowListener} 同一套判据，见
 *        {@link PartyItem#clearNoDamageTicks}）。</li>
 * </ol>
 *
 * <p>★ 为什么"区块卸载"不能只靠"实体没了就清理"：
 * 区块卸载时实体确实会被移除，但<b>移除时机与事件顺序由服务端决定</b>，
 * 且在卸载<b>之前</b>那些实体已经不在 tick 列表里了。
 * 直接监听 {@code ChunkUnloadEvent} 是不依赖实现细节的做法；
 * 另外 {@link MurderousLily} 的周期任务里还有一道 {@code world.isChunkLoaded(...)} 判定
 * —— 两道互为保险（第三道是 {@code EntityRemoveEvent}）。
 */
public class MurderousLilyListener implements Listener {

    /**
     * 阶段一那支箭命中：摘表 + 触发阶段二。
     *
     * <p>优先级用 {@code MONITOR}：这是"只观察"的监听器（不改伤害、不取消事件），
     * 按 Bukkit 约定只读的监听器放 MONITOR，保证它在所有改伤害的插件之后跑。
     *
     * <p>★ {@code ignoreCancelled = true}：命中事件被取消（某个插件拦下了这次命中）时
     * 不该爆发。而箭矢的<b>清理</b>不会因此被跳过 ——
     * 箭随后会被服务端移除，{@link #onEntityRemove} 与周期任务的判定都会兜住。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow)) {
            return;
        }
        MurderousLily lily = AddSlimefunItems.MURDEROUS_LILY;
        if (lily == null) {
            return;
        }
        // 命中点就用箭自己的位置：方块命中时它贴在命中面上，实体命中时它在实体身上
        Location point = arrow.getLocation().clone();

        // ① 阶段一那支箭：记录命中，阶段二由周期任务在同一 tick 结算
        if (MurderousLily.isTrackedShot(arrow)) {
            lily.onShotHit(arrow, e.getHitBlock(), e.getHitBlockFace(), e.getHitEntity(), point);
            return;
        }
        // ② 追踪箭：★ 用户要求 —— 命中后喷 50 个火焰粒子 +
        //    以命中点为球心（tracker-impact-radius，默认 3）格内
        //    （tracker-impact-damage，默认 24）点弹射物范围伤害，并在 2 tick 后消失。
        //    ★ 幂等由 MurderousLily.onTrackerHit 内部的"已结算"标记保证：
        //      箭多活 2 tick 期间若再来一次命中事件，那次会被静默丢掉（duplicate=true）。
        if (MurderousLily.isTrackedTracker(arrow)) {
            MurderousLily.TrackerHit hit = lily.onTrackerHit(arrow, point);
            if (hit.duplicate()) {
                lastTrackerHit = "（重复命中已被幂等保护忽略）";
            } else {
                lastTrackerHit = "粒子 " + hit.particles() + "、范围伤害 " + hit.damage()
                        + " 点 / 球半径 " + hit.radius() + " 格、实际扣血 "
                        + hit.damaged() + " 个实体";
            }
        }
    }

    /**
     * 最近一次追踪箭命中的读数（诊断用；{@code /touhou lily tracers} 打印它）。
     *
     * <p>★ 为什么要有它：追踪箭命中发生在玩家人群里，控制台看不见；
     * 把这一行留成静态读数，命令就能在不进游戏的情况下证明
     * "范围伤害真的结算了、而且只结算了一次"。
     */
    private static volatile String lastTrackerHit = "（本次启动还没有追踪箭命中过）";

    /** 最近一次追踪箭命中的读数（供命令打印）。 */
    public static String lastTrackerHitSummary() {
        return lastTrackerHit;
    }

    /**
     * 区块卸载：把这个区块里<b>所有</b>在途弹幕（阶段一的箭 + 阶段二的追踪箭）摘表。
     *
     * <p>★ 这里读 {@code arrow.getLocation()} 是安全的：{@code ChunkUnloadEvent} 触发时
     * 区块还没真正卸载（只需要区块坐标，不需要读方块，所以在异步卸载路径上也安全）。
     * {@code e.getChunk().getEntities()} 只扫这一个区块，成本与该区块的实体数成正比。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(ChunkUnloadEvent e) {
        if (e.getWorld() == null) {
            return;
        }
        for (Entity entity : e.getChunk().getEntities()) {
            if (!(entity instanceof Arrow arrow)) {
                continue;
            }
            if (!MurderousLily.isTrackedShot(arrow) && !MurderousLily.isTrackedTracker(arrow)) {
                continue;
            }
            MurderousLily.cleanupRemoved(arrow.getUniqueId());
        }
    }

    /**
     * 实体离场兜底：箭矢因为<b>任何</b>原因离开世界时把表摘干净。
     *
     * <p>{@link EntityRemoveEvent} 覆盖了 {@code UNLOAD}（区块卸载）、{@code PLUGIN}
     * （插件移除）、{@code DISCARD}（{@code /kill}）、{@code OUT_OF_WORLD} 等全部因由，
     * 所以它是"区块被删除/卸载"那条路径的第三道保险。
     *
     * <p>★ 它也会在<b>我们自己</b> {@code arrow.remove()} 时触发（因由 {@code PLUGIN}）——
     * 那时条目早已被 {@link MurderousLily#finishShot} 摘掉，这里自然是个空操作。
     */
    /**
     * 实体离场兜底：箭矢因为<b>任何</b>原因离开世界时把表摘干净。
     *
     * <p>★ 用的是 Paper 的 {@code EntityRemoveFromWorldEvent} 而<b>不是</b> Bukkit 的
     * {@code EntityRemoveEvent}：后者在 Paper 里已被标记"待移除"，
     * 注册它会在启动日志里打一条
     * {@code "...but the event is Deprecated. Server performance will be affected"} 的警告
     * （实测踩过）。前者语义相同且没有警告。
     *
     * <p>★ 它<b>会</b>被区块卸载触发（那正是我们要的），但<b>不依赖</b>它 ——
     * 区块卸载那条路径的主判据是 {@link #onChunkUnload} 与
     * {@link MurderousLily} 周期任务里的 {@code isChunkLoaded(...)}，三者互为保险。
     *
     * <p>注意：它也会在<b>我们自己</b> {@code arrow.remove()} 时触发 ——
     * 那时条目早已被 {@link MurderousLily#finishShot} 摘掉，这里自然是个空操作。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveFromWorldEvent e) {
        if (!(e.getEntity() instanceof Arrow arrow)) {
            return;
        }
        if (MurderousLily.isTrackedShot(arrow) || MurderousLily.isTrackedTracker(arrow)) {
            MurderousLily.cleanupRemoved(arrow.getUniqueId());
        }
    }

    /**
     * 箭矢命中时的无敌帧清零。
     *
     * <p>Minecraft 的受击无敌帧（noDamageTicks，默认 20）会让同 tick 内到达的后续伤害全部失效。
     * 12 支追踪箭几乎同时命中同一目标时，只有 1~2 发能真正结算 —— 账面 12×伤害实际只有 1 份。
     *
     * <p>做法与 {@link FantasySealArrowListener} 一样：在伤害结算<b>之前</b>
     * （{@code EntityDamageByEntityEvent} 在 vanilla {@code hurt()} 之前触发）
     * 把命中实体的 {@code noDamageTicks} 清零。
     *
     * <p>优先级用 {@code HIGHEST} 而非 {@code MONITOR} —— 需要"改状态"，MONITOR 约定上只读。
     *
     * <p>★ 阶段一那支箭（16）与追踪箭（12）走的都是<b>普通箭矢伤害通道</b>：
     * 护甲、无敌帧、保护插件的 {@code EntityDamageByEntityEvent} 全部照常生效。
     * 激光那条 32 的"真伤"通道是另一回事（见 {@link MurderousLily} 的类注释）。
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrackedArrowHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Arrow arrow)) {
            return;
        }
        if (!MurderousLily.isTrackedTracker(arrow) && !MurderousLily.isTrackedShot(arrow)) {
            return;
        }
        if (!(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        PartyItem.clearNoDamageTicks(target);
    }

    /** 供诊断：这个类一共监听了哪些事件。 */
    public static List<String> describe() {
        return List.of(
                "ProjectileHitEvent（阶段一命中 → 摘表 + 阶段二爆发；追踪箭命中 → 50 粒子 + 3 格 24 点范围伤害，2 tick 后消失）",
                "ChunkUnloadEvent（区块卸载 → 清理该区块内在途弹幕的追踪表）",
                "EntityRemoveFromWorldEvent（实体离场兜底 → 摘表）",
                "EntityDamageByEntityEvent（本道具箭矢命中 → 清无敌帧）");
    }

    /** 诊断用：把"方向 → 是否向上"这件事复述一遍，方便报告引用。 */
    public static String directionRule() {
        Vector up = MurderousLily.laserDirection(null);
        return "击中方块 ⇒ 取该面【外法线向外】（击中 NORTH 面 ⇒ 朝北）；击中实体 ⇒ "
                + MurderousLily.fmt(up) + " 恒向上";
    }
}
