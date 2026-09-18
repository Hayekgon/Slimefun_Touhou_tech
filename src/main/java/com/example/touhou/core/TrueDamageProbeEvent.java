package com.example.touhou.core;

import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.Plugin;

/**
 * <b>真伤探测事件</b> —— 「这条真伤，保护插件答应吗？」
 *
 * <h2>为什么需要它</h2>
 * 本项目的"真伤"是 {@code Damageable#setHealth(...)}（{@link PartyItem} 体系与 LogiTech
 * 的方块激光都是这个做法）。它<b>绕过护甲、抗性、附魔与无敌帧</b>，
 * 但也因此<b>不触发</b> {@code EntityDamageEvent} ——
 * 领地 / WorldGuard / 战斗插件都收不到通知，<b>拦不住</b>。
 * 于是"真伤"在这种实现下等于"无视一切保护"。
 *
 * <h2>做法：自己发一个事件，并让它冒充标准的伤害事件</h2>
 * 本类<b>继承</b> {@link EntityDamageByEntityEvent}、用标准的 {@link DamageCause#MAGIC}，
 * 所以<b>任何监听该事件做 PVP / 领地判定的插件都会回答它</b>
 * —— 这就是"不直接依赖任何保护插件 API，又能让它们表态"的招数
 * （出处：{@code slimefun-laser-dev/references/03-reusable-patterns.md} §1.8，
 * 对应 LogiTech 的 {@code AttackPermissionTestEvent}）。
 *
 * <p>与 LogiTech 那份的<b>三处有意不同</b>：
 * <ol>
 *   <li>它那个 {@code getRealDamage()} <b>恒返回 0</b>（构造器参数没写进字段，是个 bug）；
 *       本类的 {@link #getRealDamage()} 真的返回这次要打的数值，
 *       于是"按预计伤害判定"的插件能拿到正确输入。</li>
 *   <li>它用 {@code ENTITY_ATTACK}（近战）；本类用 {@code MAGIC} ——
 *       激光不是"拿剑砍"，魔法因由更贴切，也能让按 cause 过滤的插件区分开。</li>
 *   <li>它把 damager 当成"攻击者"；本类的激光<b>没有实体发起者</b>，
 *       所以 damager 直接用被打的目标自己（{@code damage()} 非负的既有约定），
 *       并通过 {@link #getShooter()} 暴露真正的发射者 UUID。</li>
 * </ol>
 *
 * <h2>结算流程（见 {@link MurderousLily#damageTrue}）</h2>
 * <pre>
 *   new TrueDamageProbeEvent(plugin, null, target, MAGIC, 32, shooterId)
 *      → callEvent(...)
 *      → isCancelled() ? 不结算 : target.setHealth(hp - 32)
 * </pre>
 * 代价：探测 + 真伤是<b>两次</b>事件往返（原生 {@code damage()} 只有一次），
 * 而且真伤本身的护甲穿透是"设计如此"，不是能靠事件补回来的。
 */
public class TrueDamageProbeEvent extends EntityDamageByEntityEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    /** 这次要打出的真实伤害（真伤）。 */
    private final double realDamage;

    /** 产生这次真伤的插件（本插件）。 */
    private final Plugin source;

    /** 真正的发射者 UUID（激光没有实体发起者，所以只能用 UUID 记；没有就是 null）。 */
    private final java.util.UUID shooter;

    /**
     * @param source     发起这次真伤的插件
     * @param damager    直接造成伤害的实体（激光/粒子没有实体 ⇒ 传 null）
     * @param damagee    被打的目标
     * @param cause      伤害因由（本项目用 {@link DamageCause#MAGIC}）
     * @param realDamage 这次要打出的真伤数值（会真的返回给监听者）
     * @param shooter    发射者 UUID（用于"不伤害发射者"与插件日志；没有就 null）
     */
    @SuppressWarnings({"deprecation", "removal"})
    // ★ 这个 4 参构造器（Entity, Entity, DamageCause, double）在 Paper 的新 API 里被标记为
    //   "待移除"，取而代之的是要传 DamageSource 的重载。这里仍然用它，原因是：
    //   激光是"没有实体发起者"的魔法真伤，而 DamageSource 需要 registry 里的伤害类型
    //   （1.20.4 的 org.bukkit.damage 还很新），凭空构造反而更容易在别的服务端上炸。
    //   探测事件只是给保护插件看的"意向"，用最稳的构造器比用最新的更重要。
    public TrueDamageProbeEvent(Plugin source, Entity damager, Damageable damagee,
                                DamageCause cause, double realDamage, java.util.UUID shooter) {
        // ★ EntityDamageByEntityEvent 的 damager 不允许为 null，而激光没有实体发起者：
        //   按 Bukkit 的既有约定退化成"目标打自己"，真正的发起者由 getShooter() 暴露。
        super(damager == null ? damagee : damager, damagee, cause, realDamage);
        this.source = source;
        this.realDamage = realDamage;
        this.shooter = shooter;
    }

    /** 这次要打出的真伤数值（与 LogiTech 那份"恒为 0"的实现不同，这里是真值）。 */
    public double getRealDamage() {
        return this.realDamage;
    }

    /** 发起这次真伤的插件。 */
    public Plugin getSource() {
        return this.source;
    }

    /** 发射者 UUID（可能为 null：例如命令直接触发的诊断激光）。 */
    public java.util.UUID getShooter() {
        return this.shooter;
    }

    /**
     * 这次是不是"无实体发起者"的伤害（激光就是）。
     *
     * <p>给保护插件一个便宜的判据：{@code getDamager() == getEntity()} 只在这种情况下成立。
     */
    public boolean isEntityless() {
        return getDamager() == getEntity();
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
