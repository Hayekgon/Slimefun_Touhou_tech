package com.example.touhou.core;

import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * 博丽的御币 —— 弹幕命中时的无敌帧处理。
 *
 * <p>Minecraft 的受击无敌帧（noDamageTicks，默认 20）会让同 tick 内到达的后续伤害全部失效。
 * 36 发弹幕几乎同时命中同一目标时，只有 1~2 发能真正结算 —— 账面 36×伤害实际只有 1 份。
 *
 * <p>做法：在伤害结算<b>之前</b>（{@code EntityDamageByEntityEvent} 在 vanilla {@code hurt()} 之前触发）
 * 把命中实体的 {@code noDamageTicks} 清零，于是每一发弹幕都能独立结算。
 *
 * <p>优先级用 {@code HIGHEST} 而非 {@code MONITOR} —— 需要"改状态"，MONITOR 约定上只读。
 * {@code ignoreCancelled = true} 保证被保护插件取消的伤害不会被我们解锁无敌帧。
 *
 * <p>只想放行一部分弹幕的话，把下面那行改成 {@code target.setNoDamageTicks(2)} 之类即可。
 */
public class GoheiArrowListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTrackedArrowHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Arrow arrow)) {
            return;
        }
        if (!HakureiGohei.isTrackedArrow(arrow)) {
            return;
        }
        if (!(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (target.getNoDamageTicks() > 0) {
            target.setNoDamageTicks(0);
        }
    }
}
