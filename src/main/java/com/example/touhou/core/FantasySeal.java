package com.example.touhou.core;

import com.example.touhou.Touhou;
import com.example.touhou.power.PowerNetwork;
import com.example.touhou.power.PowerNetworkManager;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow.PickupStatus;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

/**
 * 梦想封印 集 —— 充能式 360° 追踪弹幕发射器（由 LogiTech 的 TrackingArrowLauncher 移植）。
 *
 * <h2>名字的来历</h2>
 * 「夢想封印」是博丽灵梦的代表符卡，官方英译 <b>Fantasy Seal</b>；
 * 「集」是它在《东方非想天则》里与 <b>-Scatter-</b>（散）/ <b>-Strike-</b>（撃）并列的形态之一，
 * 英文取惯用的 <b>-Converge-</b>。物品 id 因此是
 * {@code TOUHOU_PARTY_ITEM_FANTASY_SEAL_CONVERGE}。
 * ★ 该英文名<b>已由用户核对确认</b>（2026-09-20）。
 *
 * <h2>能源：自研 POWER 体系，不再是 Slimefun 电力</h2>
 * ★ 本类<b>不实现</b> {@code Rechargeable} —— 那是 Slimefun 电力（J）的接口。
 * 电量改走本插件的 POWER 体系：
 * <ul>
 *   <li>持久化在<b>物品自己的 PDC</b>，键串与 POWER 体系同一套
 *       （{@link com.example.touhou.power.PowerStorageUnit#KEY_CHARGE} = {@code touhou:power-charge}）——
 *       道具不在世界里，所以用不了方块数据（{@link TouhouData} 那一套只对方块有效）；</li>
 *   <li>充能来源是<b>玩家附近最近的 POWER 网络</b>：手持时每隔
 *       {@code charge-interval-seconds}（默认 2 秒）从那张网里"抽"走
 *       {@code charge-per-cycle}（默认 1）点 POWER。
 *       "最近" = 以玩家眼睛为球心、{@link PowerNetworkManager#findNearestNode} 扫出的
 *       第一个节点（按壳层由内向外，所以是最近的那个）所在的整张网；</li>
 *   <li>附近没有网络 / 网络里没电 ⇒ <b>不充</b>，并给一次<b>节流过</b>的提示
 *       （{@code hint-throttle-seconds}，默认 15 秒最多一条，绝不每 tick 刷屏）。</li>
 * </ul>
 *
 * <h2>行为（这些都是之前定好的 spec，本次<b>一字未改</b>）</h2>
 * <ul>
 *   <li>右键：消耗 {@code power-cost}（默认 1 POWER）发射 {@code bullet-count}（默认 36）发弹幕；</li>
 *   <li>弹幕绕纵轴等角分布，每发间隔 {@code 360/36 = 10}°，第 0 发对齐玩家视线水平方向；</li>
 *   <li>与纵轴夹角统一为 {@code vertical-angle}（90° = 水平圆盘，&lt;90 上锥，&gt;90 下锥）；</li>
 *   <li>前 {@code STRAIGHT_TICKS}（30）tick 直线外扩，之后开始追踪最近目标；</li>
 *   <li>伤害 = {@code basic-damage + power-amplifier×力量等级 + sharpness-amplifier×锋利等级}；</li>
 *   <li>与玩家距离超过 {@code max-distance}（120）立即销毁；</li>
 *   <li>命中时清目标无敌帧（配合 {@link FantasySealArrowListener}），使齐射每一发都独立结算；</li>
 *   <li>樱花粒子 1 个/tick，无火焰粒子；</li>
 *   <li><b>新增</b>：发射后冷却 {@code cooldown-millis}（默认 1.5 秒）—— 冷却期间再右键只提示、不发射。</li>
 * </ul>
 *
 * <h2>四个数字（spec 给定的刻度，全部是 ItemSetting，改一行即可）</h2>
 * <pre>
 *   power-capacity           = 40   道具自身的 POWER 缓冲上限
 *   power-cost               = 1    单次使用消耗 1 POWER
 *   charge-per-cycle         = 5    每 {@code charge-interval-seconds} 秒充 5 POWER
 *   charge-interval-seconds  = 2    于是 16 秒充 40 POWER = 一次充能正好填满
 *   cooldown-millis          = 1500 每发之后 1.5 秒冷却
 * </pre>
 * ⚠ 满电 40 POWER ÷ 1 POWER/次 = <b>40 次</b>使用（不是 80 次 —— 口径见交付报告）。
 *
 * <h2>★ 与「杀意的百合」共用的一套 POWER 数据</h2>
 * 上面这四行、加上 {@code charge-wireless} / {@code charge-range} /
 * {@code hint-throttle-seconds}，现在<b>只有一份声明</b>——在 {@link PartyItem} 里，
 * 本类与 {@link MurderousLily} 都继承它（用户要求的「数据等沿用」）。
 * 本类自己<b>没有改动任何数值、key、默认值与提示文案</b>：
 * 那次重构只是把字段与私有方法原样上移，道具 id 也没变，
 * 所以 {@code Items.yml} 里 {@code TOUHOU_PARTY_ITEM_FANTASY_SEAL_CONVERGE} 那一节的键一个都没变。
 */
public class FantasySeal extends PartyItem {

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

    // ---- POWER 刻度与取电参数：全部继承自 PartyItem（见那个类的注释）----

    // ------------------------------------------------------------------ 常量

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

    /**
     * lore 里那一行实时电量的<b>纯文本前缀</b>。
     *
     * <p>刷新时是"找到以它开头的那一行就地替换"，所以物品模板里必须先放一行
     * （见 {@code AddItems}）—— 这样玩家在第一次充能之前也能看到这一行。
     * 比对前会 {@code stripColor}，因此颜色码不参与匹配。
     *
     * <p>★ 这个字面量现在只在 {@link #loreLabel()} 一处被交给 {@link PartyItem}
     * （公共的 lore 刷新逻辑在那边），本类不再自己实现改写。
     */
    private static final String LORE_LABEL = "POWER:";

    /** 本道具发射出的、仍在飞行中的弹幕 UUID（异步制导线程也读写，故用并发集合）。 */
    private static final Set<UUID> TRACKED_ARROWS = ConcurrentHashMap.newKeySet();

    public FantasySeal(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler((ItemUseHandler) this::onUse);
    }

    // ---- POWER 刻度 / 取电参数 / 充能循环 / 发射冷却闸：全部继承自 PartyItem ----
    //   （用户要求的「数据等沿用」：那 8 个 ItemSetting 与整条取电链路在 PartyItem 里
    //    只有一份声明，本类不再各写一份常量；设置仍然落在 items.yml 的本道具 id 分节里。）

    /**
     * lore 里那一行实时电量的前缀（见 {@link PartyItem#loreLabel()}）。
     *
     * <p>★ 仍然是 {@code "POWER:"} —— 一个字符都没改，所以物品模板里那一行照样被就地改写。
     */
    @Override
    protected String loreLabel() {
        return LORE_LABEL;
    }

    /** 控制台日志里本道具的名字（充能循环启动/异常都会带上它）。 */
    @Override
    protected String itemLabel() {
        return "梦想封印 集";
    }

    /** 本道具的消息作用域（前缀/档位取自 config.yml 的 {@code seal:} 段）。 */
    @Override
    protected Notify.Scope scope() {
        return Notify.seal();
    }

    /** 供 {@link FantasySealArrowListener} 判定"这是不是本道具发射的弹幕"。 */
    public static boolean isTrackedArrow(Arrow arrow) {
        return arrow != null && TRACKED_ARROWS.contains(arrow.getUniqueId());
    }

    // ------------------------------------------------------------------ 交互

    private void onUse(PlayerRightClickEvent event) {
        event.cancel();   // 钓鱼竿：阻止抛竿
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        long now = System.currentTimeMillis();

        if (!passPermission(p)) {
            Notify.warn(scope(), p, "&c你没有权限使用该道具!");
            return;
        }

        // ---- 冷却闸 + POWER 闸：spec 要求 1.5 秒冷却，两件符卡共用 PartyItem 里那一段 ----
        if (!passGate(p, item, now)) {
            return;
        }
        fire(p, item);
    }

    // ------------------------------------------------------------------ 发射（spec 部分，未改动）

    private void fire(Player p, ItemStack item) {
        // ★ 伤害公式抽到了 PartyItem#arrowDamage：基础值 + 力量×0.8 + 锋利×0.4，
        //   算式与抽取前逐字相同，只是「杀意的百合」的箭矢也走这一段（不再各写一套）。
        float damage = arrowDamage(item, basicDamage.getValue(), powerAmplifier.getValue(),
                sharpnessAmplifier.getValue());

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
                            sync(() -> lock.forEach(FantasySeal.this::steer));
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

    // ------------------------------------------------------------------ 工具
    //
    // ★ sync / handLocation / isTargetable 已上移到 PartyItem（两件符卡共用同一套实现，
    //   逻辑一字未改）；untrack 留在这里，因为它改的是本类自己的弹幕追踪表。

    private static void untrack(Arrow arrow) {
        TRACKED_ARROWS.remove(arrow.getUniqueId());
    }

    // ------------------------------------------------------------------ 配置读取
    //
    // ★ 公共的 8 个刻度（上限 / 单次消耗 / 充能速率 / 间隔 / 冷却 / 无线开关 / 取电半径 /
    //   提示节流）连同它们的兜底读取全部在 PartyItem 里，本类不再各写一份
    //   （这正是「数据等沿用」的做法：只有一个出处，改一处两件符卡一起变）。
    //   下面只有本类独有的跟踪半径 —— 它没有兜底读取，因为 ItemSetting 一旦注册
    //   就总会有值（注册时 Slimefun 会把默认值写回 Items.yml）。

    // ------------------------------------------------------------------ 诊断

    /** 供 /touhou 命令无头验证：本道具独有的参数（公共 POWER 部分由 {@link PartyItem#selfCheck()} 补）。 */
    @Override
    protected List<String> describeSelf() {
        List<String> out = new ArrayList<>();
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
        return out;
    }

    /**
     * 一次"从附近网络取电"的实测结果（{@code /touhou seal probe} 用）。
     *
     * @param before   取电前的道具 POWER
     * @param after    取电后的道具 POWER
     * @param taken    实际取到的 POWER
     * @param capacity 道具上限
     * @param detail   取电来源 / 失败原因
     */
    public record ChargeProbe(long before, long after, long taken, long capacity, String detail) {
    }

    /**
     * 以 {@code origin} 为"玩家所在位置"，对 {@code item} 做一次<b>真实的</b>取电 ——
     * 走的就是 {@link #chargeOne} 那条路径：找最近节点 → 拿它所在的网 → 抽电 → 写回道具。
     *
     * <p>★ 存在的意义：控制台没有"手持"这件事，而"道具从哪个网络取电"必须能被验证。
     * 于是把这条路径暴露成一个只依赖坐标的入口，命令与游戏内行为共用同一段逻辑。
     */
    public ChargeProbe probeCharge(ItemStack item, Location origin) {
        long before = chargeOf(item);
        long cap = configuredCapacity();
        if (item == null || item.getType().isAir()) {
            return new ChargeProbe(0L, 0L, 0L, cap, "（没有传入道具）");
        }
        if (origin == null || origin.getWorld() == null) {
            return new ChargeProbe(before, before, 0L, cap, "坐标无效");
        }
        if (before >= cap) {
            return new ChargeProbe(before, before, 0L, cap, "道具已满（" + cap + " POWER）");
        }
        int range = configuredRange();
        Location node = PowerNetworkManager.findNearestNode(origin, range);
        if (node == null) {
            return new ChargeProbe(before, before, 0L, cap,
                    "半径 " + range + " 格内没有任何 POWER 节点");
        }
        PowerNetwork net = PowerNetworkManager.getNetwork(node);
        if (net == null) {
            return new ChargeProbe(before, before, 0L, cap, "节点 " + TouhouData.xyz(node) + " 未组成网络");
        }
        long want = Math.min(configuredPerCycle(), cap - before);
        long got = PowerNetworkManager.extractPower(net, want);
        if (got <= 0) {
            return new ChargeProbe(before, before, 0L, cap,
                    "最近节点 " + TouhouData.xyz(node) + "（网络 #" + net.networkId() + "，距离 "
                            + String.format("%.2f", node.distance(origin)) + " 格）里没有电");
        }
        setChargeOf(item, before + got);
        return new ChargeProbe(before, chargeOf(item), got, cap,
                "最近节点 " + TouhouData.xyz(node) + "（网络 #" + net.networkId() + "，节点数 "
                        + net.size() + "，距离 " + String.format("%.2f", node.distance(origin))
                        + " 格，搜索半径 " + range + " 格）");
    }
}
