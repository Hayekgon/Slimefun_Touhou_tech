package com.example.touhou.core;

import com.example.touhou.Touhou;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.Rechargeable;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow.PickupStatus;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * 博丽的御币 —— 充能式 360° 追踪弹幕发射器（由 LogiTech 的 TrackingArrowLauncher 移植）。
 *
 * <p>与原版（LogiTech）的差异：<b>只依赖 Slimefun / Paper 原生 API</b>，
 * 不再使用 LogiTech 的 {@code ChargableProps} / {@code AddUtils} / {@code Schedules} /
 * {@code BukkitUtils} / {@code WorldUtils}，全部改成自带的私有实现。
 *
 * <h2>行为</h2>
 * <ul>
 *   <li>右键：消耗 640 J 发射 {@code bullet-count}（默认 36）发弹幕；</li>
 *   <li>弹幕绕纵轴等角分布，每发间隔 {@code 360/36 = 10}°，第 0 发对齐玩家视线水平方向；</li>
 *   <li>与纵轴夹角统一为 {@code vertical-angle}（90° = 水平圆盘，&lt;90 上锥，&gt;90 下锥）；</li>
 *   <li>前 {@code STRAIGHT_TICKS}（30）tick 直线外扩，之后开始追踪最近目标；</li>
 *   <li>伤害 = {@code basic-damage + power-amplifier×力量等级 + sharpness-amplifier×锋利等级}；</li>
 *   <li>与玩家距离超过 {@code max-distance}（120）立即销毁；</li>
 *   <li>命中时清目标无敌帧（配合 {@link GoheiArrowListener}），使齐射每一发都独立结算；</li>
 *   <li>樱花粒子 1 个/tick，无火焰粒子。</li>
 * </ul>
 */
public class HakureiGohei extends SlimefunItem implements Rechargeable {

    // ------------------------------------------------------------------ 可配置项（写入 items.yml）

    /** 追踪球体半径。球心 = 玩家自身；同时是单发弹幕的锁定半径。 */
    private final ItemSetting<Integer> trackRange = setting("track-range", 120);
    /** 弹幕存活半径：与玩家距离超过该值立即销毁。 */
    private final ItemSetting<Integer> maxDistance = setting("max-distance", 120);
    /** 基础伤害。 */
    private final ItemSetting<Integer> basicDamage = setting("basic-damage", 16);
    /** 每级力量附加伤害。 */
    private final ItemSetting<Double> powerAmplifier = setting("power-amplifier", 0.8);
    /** 每级锋利附加伤害。 */
    private final ItemSetting<Double> sharpnessAmplifier = setting("sharpness-amplifier", 0.4);
    /** 单次发射弹幕数（36 ⇒ 间隔 10°）。 */
    private final ItemSetting<Integer> bulletCount = setting("bullet-count", 36);
    /** 与纵轴夹角（度）。90 = 水平圆盘。 */
    private final ItemSetting<Integer> verticalAngle = setting("vertical-angle", 90);

    // ------------------------------------------------------------------ 常量

    private static final float MAX_CHARGE = 9_000_000.0F;
    private static final float ENERGY_CONSUMPTION = 640.0F;
    private static final float ARROW_SPEED = 1.25F;
    /** 必须为 0，否则随机散布会破坏"每发精确间隔 10°"。 */
    private static final float ARROW_SPREAD = 0.0F;
    private static final int MAX_TRACETIME = 150;
    private static final int PERIOD_TRACETIME = 2;
    /** 起手直线飞行阶段（tick）。 */
    private static final int STRAIGHT_TICKS = 30;
    private static final int STRAIGHT_RUNTIME = STRAIGHT_TICKS / PERIOD_TRACETIME;
    private static final int PARTICLE_COUNT = 1;
    private static final int PARTICLE_TICK_STEP = 1;
    private static final double STEER_FACTOR = 0.25D;
    private static final double INERTIA_FACTOR = 0.8D;

    /** 本道具发射出的、仍在飞行中的弹幕 UUID（异步制导线程也读写，故用并发集合）。 */
    private static final Set<UUID> TRACKED_ARROWS = ConcurrentHashMap.newKeySet();

    public HakureiGohei(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler((ItemUseHandler) this::onUse);
    }

    /** 供 {@link GoheiArrowListener} 判定"这是不是本道具发射的弹幕"。 */
    public static boolean isTrackedArrow(Arrow arrow) {
        return arrow != null && TRACKED_ARROWS.contains(arrow.getUniqueId());
    }

    private <T> ItemSetting<T> setting(String key, T defaultValue) {
        ItemSetting<T> s = new ItemSetting<>(this, key, defaultValue);
        addItemSetting(s);
        return s;
    }

    // ------------------------------------------------------------------ Rechargeable

    @Override
    public float getMaxItemCharge(ItemStack item) {
        return MAX_CHARGE;
    }

    // ------------------------------------------------------------------ 交互

    private void onUse(PlayerRightClickEvent event) {
        event.cancel();   // 钓鱼竿：阻止抛竿
        Player p = event.getPlayer();
        ItemStack item = event.getItem();

        float charge = getItemCharge(item);
        if (charge < ENERGY_CONSUMPTION) {
            // 玩家按了道具但电量不够：这是"操作失败"，属于 warning，必须告诉他
            Notify.warn(p, ChatColor.translateAlternateColorCodes('&',
                    "&8[&d博丽的御币&8] &c电力不足! &f" + fmt(charge) + "J/640J"));
            return;
        }
        if (!canUse(p, false)) {
            Notify.warn(p, "&c你没有权限使用该道具!");
            return;
        }
        setItemCharge(item, charge - ENERGY_CONSUMPTION);
        fire(p, item);
    }

    // ------------------------------------------------------------------ 发射

    private void fire(Player p, ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        int power = meta == null ? 0 : meta.getEnchantLevel(Enchantment.ARROW_DAMAGE);
        int sharpness = meta == null ? 0 : meta.getEnchantLevel(Enchantment.DAMAGE_ALL);
        float damage = basicDamage.getValue().floatValue()
                + powerAmplifier.getValue().floatValue() * power
                + sharpnessAmplifier.getValue().floatValue() * sharpness;

        Location origin = handLocation(p);
        int count = Math.max(1, bulletCount.getValue());
        double degreeStep = 360.0D / count;

        // 与纵轴夹角：0 = 正上，90 = 水平，180 = 正下
        double polar = Math.toRadians(verticalAngle.getValue());
        double sinPolar = Math.sin(polar);
        double cosPolar = Math.cos(polar);
        // Minecraft yaw：0 = +Z(南)，水平朝向 = (-sin(yaw), 0, cos(yaw))
        // ⇒ i == 0 那一发的水平方位与玩家视线完全一致
        float baseYaw = p.getLocation().getYaw();

        HashSet<Arrow> arrows = new HashSet<>();
        for (int i = 0; i < count; i++) {
            double azimuth = Math.toRadians(baseYaw + degreeStep * i);
            Vector dir = new Vector(
                    -Math.sin(azimuth) * sinPolar,
                    cosPolar,
                    Math.cos(azimuth) * sinPolar);
            arrows.add(spawnArrow(origin, dir, p, damage));
        }

        // 索敌球体：以玩家自身为球心（360° 发射必须如此，否则背对的一半找不到目标）
        double radius = trackRange.getValue();
        HashSet<LivingEntity> targets = new HashSet<>();
        for (Entity e : origin.getWorld().getNearbyEntities(origin, radius, radius, radius)) {
            if (e == p || !isTargetable(e)) {
                continue;
            }
            if (e.getLocation().distance(origin) <= radius && e instanceof LivingEntity le) {
                targets.add(le);
            }
        }

        launchAutoTrace(p, arrows, targets);
    }

    private Arrow spawnArrow(Location origin, Vector direction, Player shooter, float damage) {
        Arrow a = origin.getWorld().spawnArrow(origin.clone(), direction, ARROW_SPEED, ARROW_SPREAD);
        a.setDamage(damage);
        a.setCritical(false);
        a.setShooter(shooter);
        a.setGravity(false);
        a.setPickupStatus(PickupStatus.DISALLOWED);
        TRACKED_ARROWS.add(a.getUniqueId());
        return a;
    }

    // ------------------------------------------------------------------ 制导循环

    private void launchAutoTrace(Player shooter, HashSet<Arrow> arrows, HashSet<LivingEntity> targets) {
        final HashSet<Arrow> running = new HashSet<>(arrows);
        BukkitRunnable task = new BukkitRunnable() {
            private int runTime;
            private boolean busy;

            @Override
            public void cancel() {
                super.cancel();
                sync(() -> arrows.forEach(a -> {
                    a.remove();
                    untrack(a);
                }));
            }

            @Override
            public void run() {
                if (busy) {
                    return;
                }
                busy = true;
                try {
                    if (runTime >= MAX_TRACETIME) {
                        cancel();
                        return;
                    }
                    runTime++;

                    // 额外规则：超出存活半径的弹幕立即销毁（起手直线阶段同样生效）
                    destroyOutOfRange(shooter, arrows, running);
                    if (arrows.isEmpty()) {
                        cancel();
                        return;
                    }

                    // 起手 STRAIGHT_TICKS tick 内不制导，先让圆环完整展开
                    if (runTime > STRAIGHT_RUNTIME) {
                        Iterator<Arrow> it = running.iterator();
                        HashMap<Arrow, LivingEntity> lock = new HashMap<>();

                        while (it.hasNext()) {
                            Arrow arrow = it.next();
                            if (arrow.isOnGround() || arrow.isDead() || !arrow.isValid() || arrow.isInBlock()) {
                                arrow.setGravity(true);
                                it.remove();
                                arrows.remove(arrow);
                                untrack(arrow);
                                continue;
                            }
                            Location al = arrow.getLocation();
                            float best = trackRange.getValue().floatValue();
                            LivingEntity chosen = null;
                            Iterator<LivingEntity> ti = targets.iterator();
                            while (ti.hasNext()) {
                                LivingEntity t = ti.next();
                                if (t.isDead() || !t.isValid() || !al.getWorld().equals(t.getWorld())) {
                                    ti.remove();
                                    continue;
                                }
                                float d = (float) al.distance(t.getLocation());
                                if (d < best) {
                                    best = d;
                                    chosen = t;
                                }
                            }
                            if (chosen != null) {
                                lock.put(arrow, chosen);
                            } else {
                                arrow.setGravity(true);
                                it.remove();
                            }
                        }

                        if (!lock.isEmpty()) {
                            sync(() -> lock.forEach(HakureiGohei.this::steer));
                        }
                    }

                    // 樱花粒子：1 个/tick；已落地/失效的不再喷
                    if (runTime % PARTICLE_TICK_STEP == 0) {
                        for (Arrow arrow : arrows) {
                            if (arrow.isValid() && !arrow.isDead() && !arrow.isOnGround()) {
                                arrow.getWorld().spawnParticle(Particle.CHERRY_LEAVES, arrow.getLocation(),
                                        PARTICLE_COUNT, 0.0, 0.0, 0.0, 1.0, null, true);
                            }
                        }
                    }
                } finally {
                    busy = false;
                }
            }
        };
        task.runTaskTimerAsynchronously(Touhou.getInstance(), 4L, PERIOD_TRACETIME);
    }

    /** 半主动比例导引：向心分量负责转弯，惯性分量保证轨迹平滑。 */
    private void steer(Arrow arrow, LivingEntity target) {
        Location al = arrow.getLocation();
        Location tl = target.getEyeLocation();
        if (al.getWorld().equals(tl.getWorld()) && al.distance(tl) > 1.0) {
            arrow.setVelocity(tl.subtract(al).toVector().normalize().multiply(STEER_FACTOR)
                    .add(arrow.getVelocity().multiply(INERTIA_FACTOR)));
        } else {
            arrow.setVelocity(arrow.getVelocity().normalize().multiply(ARROW_SPEED));
        }
    }

    /** 超出存活半径（或玩家离线/跨世界）的弹幕立即销毁。 */
    private void destroyOutOfRange(Player shooter, HashSet<Arrow> arrows, HashSet<Arrow> running) {
        if (arrows.isEmpty()) {
            return;
        }
        boolean gone = shooter == null || !shooter.isOnline() || !shooter.isValid();
        Location sl = gone ? null : shooter.getLocation();
        int limit = maxDistance.getValue();

        List<Arrow> doomed = new ArrayList<>();
        for (Arrow arrow : arrows) {
            if (!arrow.isValid() || arrow.isDead()) {
                doomed.add(arrow);
                continue;
            }
            if (gone
                    || !arrow.getWorld().equals(sl.getWorld())
                    || arrow.getLocation().distance(sl) > limit) {
                doomed.add(arrow);
            }
        }
        if (doomed.isEmpty()) {
            return;
        }
        arrows.removeAll(doomed);
        running.removeAll(doomed);
        sync(() -> doomed.forEach(a -> {
            a.remove();
            untrack(a);
        }));
    }

    private static void untrack(Arrow arrow) {
        TRACKED_ARROWS.remove(arrow.getUniqueId());
    }

    // ------------------------------------------------------------------ 工具

    /** 回到主线程执行（制导循环跑在异步线程）。 */
    private static void sync(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            Bukkit.getScheduler().runTask(Touhou.getInstance(), r);
        }
    }

    /** 手持位置：眼位向脚位回拉 30%。 */
    private static Location handLocation(Player p) {
        Location eye = p.getEyeLocation();
        Location feet = p.getLocation();
        eye.add(feet.subtract(eye).multiply(0.3).toVector());
        return eye;
    }

    private static boolean isTargetable(Entity e) {
        if (!e.isValid() || e.isDead() || !(e instanceof LivingEntity le) || le.isInvulnerable()) {
            return false;
        }
        if (e instanceof ArmorStand stand && (stand.isMarker() || stand.isSmall())) {
            return false;
        }
        return true;
    }

    private static String fmt(float v) {
        return String.valueOf(Math.round(v));
    }

    // ------------------------------------------------------------------ 诊断

    /** 供 /touhou 命令无头验证：把关键参数与自检结果打出来。 */
    public List<String> selfCheck() {
        List<String> out = new ArrayList<>();
        out.add("HakureiGohei id=" + getId() + " itemName=" + getItemName());
        out.add("  material        = " + getItem().getType());
        out.add("  maxCharge       = " + MAX_CHARGE + " J, 单发耗电 = " + ENERGY_CONSUMPTION + " J");
        out.add("  trackRange      = " + trackRange.getValue());
        out.add("  maxDistance     = " + maxDistance.getValue());
        out.add("  bulletCount     = " + bulletCount.getValue()
                + "  (间隔 " + (360.0 / Math.max(1, bulletCount.getValue())) + "°)");
        out.add("  verticalAngle   = " + verticalAngle.getValue() + "°");
        out.add("  damage          = " + basicDamage.getValue()
                + " + " + powerAmplifier.getValue() + "×力量 + "
                + sharpnessAmplifier.getValue() + "×锋利");
        out.add("  straightTicks   = " + STRAIGHT_TICKS + ", period = " + PERIOD_TRACETIME + "t");
        out.add("  trackedArrows   = " + TRACKED_ARROWS.size());
        Optional<ItemGroup> g = Optional.ofNullable(getItemGroup());
        out.add("  itemGroup       = " + g.map(x -> x.getKey().toString()).orElse("(无)"));
        return out;
    }
}
