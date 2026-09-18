package com.example.touhou.core;

import com.example.touhou.Touhou;
import com.example.touhou.power.PowerNetwork;
import com.example.touhou.power.PowerNetworkManager;
import com.example.touhou.power.PowerStorageUnit;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow.PickupStatus;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
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
 *       （{@link PowerStorageUnit#KEY_CHARGE} = {@code touhou:power-charge}）——
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
 */
public class FantasySeal extends SlimefunItem {

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

    // ---- POWER 刻度（spec 的四个数字，全部可配） ----

    /** 道具自身的 POWER 缓冲上限。 */
    private final ItemSetting<Integer> powerCapacity = setting("power-capacity", 40);
    /** 单次发射消耗的 POWER。 */
    private final ItemSetting<Integer> powerCost = setting("power-cost", 1);
    /**
     * 每个充能节拍充入多少 POWER。
     *
     * <p>★ 2026-09-20 按用户要求从 1 改成 <b>5</b>：于是 5 POWER / 2 秒，
     * 16 秒（8 个节拍）正好从空充到上限 40 ——「每次充能需要 16s」这条 spec
     * 与「上限 40 POWER」就此咬合。
     *
     * <p>⚠ 改这里的默认值<b>不会</b>影响已经跑过的服务端：ItemSetting 的值会被
     * Slimefun 持久化到 {@code plugins/Slimefun/Items.yml}，那边的旧值优先。
     * 必须同时改 Items.yml（或删掉那个键）。
     */
    private final ItemSetting<Integer> chargePerCycle = setting("charge-per-cycle", 5);
    /** 充能节拍的间隔（秒）：每隔这么久取一次电。 */
    private final ItemSetting<Integer> chargeIntervalSeconds = setting("charge-interval-seconds", 2);
    /** 发射后的冷却（毫秒）。spec：1.5 秒。 */
    private final ItemSetting<Integer> cooldownMillis = setting("cooldown-millis", 1500);
    /** 取电的搜索半径（格，切比雪夫）：玩家周围这个范围内最近的 POWER 节点所在的网。 */
    private final ItemSetting<Integer> chargeRange = setting("charge-range", 4);
    /** "附近没有网络 / 网络没电"这类提示的最小间隔（秒）—— 节流，避免刷屏。 */
    private final ItemSetting<Integer> hintThrottleSeconds = setting("hint-throttle-seconds", 15);

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

    /** 各项默认值（与 ItemSetting 的默认值同源，配置读取兜底时也用它们）。 */
    private static final int DEFAULT_CAPACITY = 40;
    private static final int DEFAULT_COST = 1;
    private static final int DEFAULT_PER_CYCLE = 1;
    private static final int DEFAULT_INTERVAL_SECONDS = 2;
    private static final int DEFAULT_COOLDOWN_MILLIS = 1500;
    private static final int DEFAULT_RANGE = 4;
    private static final int DEFAULT_HINT_SECONDS = 15;

    /**
     * 道具 POWER 的 PDC 键。
     *
     * <p>★ 字样直接取自 {@link PowerStorageUnit#KEY_CHARGE}（{@code touhou:power-charge}），
     * 而不是再手写一遍字符串 —— "POWER 体系共用一套键"这件事必须只有一个出处。
     */
    private static final NamespacedKey CHARGE_KEY = Objects.requireNonNull(
            NamespacedKey.fromString(PowerStorageUnit.KEY_CHARGE),
            "非法的 POWER 电量键: " + PowerStorageUnit.KEY_CHARGE);

    /**
     * lore 里那一行实时电量的<b>纯文本前缀</b>。
     *
     * <p>刷新时是"找到以它开头的那一行就地替换"，所以物品模板里必须先放一行
     * （见 {@code AddItems}）—— 这样玩家在第一次充能之前也能看到这一行。
     * 比对前会 {@code stripColor}，因此颜色码不参与匹配。
     */
    private static final String LORE_LABEL = "POWER:";

    /** 本道具发射出的、仍在飞行中的弹幕 UUID（异步制导线程也读写，故用并发集合）。 */
    private static final Set<UUID> TRACKED_ARROWS = ConcurrentHashMap.newKeySet();

    /** 发射冷却到期时刻（毫秒，按玩家记）。 */
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();

    /** 上一次给该玩家发"充能失败"提示的时刻（毫秒）—— 提示节流用。 */
    private static final Map<UUID, Long> HINT_AT = new ConcurrentHashMap<>();

    /** 冷却表的清理门槛（毫秒）：早已过期的记录留着没意义。 */
    private static final long COOLDOWN_KEEP_MILLIS = 60_000L;

    /** 充能循环的任务句柄（只起一次）。 */
    private BukkitTask chargeTask;

    public FantasySeal(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler((ItemUseHandler) this::onUse);
    }

    /** 供 {@link FantasySealArrowListener} 判定"这是不是本道具发射的弹幕"。 */
    public static boolean isTrackedArrow(Arrow arrow) {
        return arrow != null && TRACKED_ARROWS.contains(arrow.getUniqueId());
    }

    private <T> ItemSetting<T> setting(String key, T defaultValue) {
        ItemSetting<T> s = new ItemSetting<>(this, key, defaultValue);
        addItemSetting(s);
        return s;
    }

    // ------------------------------------------------------------------ 道具 POWER 读写

    /**
     * 读道具里的 POWER。
     *
     * <p>全程只碰 {@link ItemStack} / PDC，<b>不碰方块数据</b> —— 所以它可以在任何线程、
     * 任何"没有方块"的上下文里安全调用（例如命令自检里那个只存在于内存里的测试物品）。
     */
    public long chargeOf(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return 0L;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return 0L;
        }
        Long v = meta.getPersistentDataContainer().get(CHARGE_KEY, PersistentDataType.LONG);
        return v == null ? 0L : Math.max(0L, v);
    }

    /** 写道具里的 POWER（自动 clamp 到 {@code [0, 上限]}），并刷新 lore 里那一行实时电量。 */
    public void setChargeOf(ItemStack item, long charge) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        long cap = configuredCapacity();
        long value = Math.max(0L, Math.min(charge, cap));
        meta.getPersistentDataContainer().set(CHARGE_KEY, PersistentDataType.LONG, value);
        renderChargeLore(meta, value, cap);
        item.setItemMeta(meta);
    }

    /** 当前 POWER 还能发射几次（{@code power-cost <= 0} 视为不消耗，返回 {@code Long.MAX_VALUE}）。 */
    public long usesOf(ItemStack item) {
        long cost = configuredCost();
        return cost <= 0 ? Long.MAX_VALUE : chargeOf(item) / cost;
    }

    /**
     * 刷新 lore 里那一行"POWER: x/y（可用 n 次）"。
     *
     * <p>就地替换<b>以 {@link #LORE_LABEL} 开头</b>的那一行；找不到就追加到末尾。
     * ★ 这一行是"充能看得见"的唯一途径（本类不再是 {@code Rechargeable}，
     * 也就没有 Slimefun 自带的那行电力显示），所以每次改电量都必须走这里。
     */
    private void renderChargeLore(ItemMeta meta, long charge, long cap) {
        List<String> lore = meta.getLore() == null
                ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        long cost = configuredCost();
        String line = ChatColor.DARK_GRAY + LORE_LABEL + " " + ChatColor.WHITE + charge
                + ChatColor.DARK_GRAY + "/" + ChatColor.WHITE + cap
                + ChatColor.GRAY + "（可用 " + (cost <= 0 ? "∞" : charge / cost) + " 次）";
        for (int i = 0; i < lore.size(); i++) {
            String plain = ChatColor.stripColor(lore.get(i));
            if (plain != null && plain.startsWith(LORE_LABEL)) {
                lore.set(i, line);
                meta.setLore(lore);
                return;
            }
        }
        lore.add(line);
        meta.setLore(lore);
    }

    // ------------------------------------------------------------------ 充能循环

    /**
     * 启动充能循环（由 {@link Touhou#onEnable()} 在物品注册之后调用一次）。
     *
     * <p>★ 为什么<b>不</b>在构造器里起任务：本类的构造器跑在 {@code onEnable} 的物品注册阶段，
     * 那一刻插件还不算"已启用"；而且那个时机与世界状态无关，起任务属于"越早越容易出怪事"。
     * 显式由主类调用，一眼就能看出"谁在什么时候把它开起来的"。
     *
     * <p>循环体只做一件事：给<b>手持</b>本道具的在线玩家从附近网络取电。
     */
    public void startCharging() {
        if (chargeTask != null) {
            return;
        }
        long period = Math.max(1, configuredIntervalSeconds()) * 20L;   // 真实 tick，20/秒
        chargeTask = new BukkitRunnable() {
            @Override
            public void run() {
                chargeAll();
            }
        }.runTaskTimer(Touhou.getInstance(), period, period);
        Log.info("[SEAL] 充能循环已启动：每 " + configuredIntervalSeconds() + " 秒为手持者取 "
                + configuredPerCycle() + " POWER（搜索半径 " + configuredRange()
                + " 格，提示节流 " + configuredHintSeconds() + " 秒）");
    }

    private void chargeAll() {
        long now = System.currentTimeMillis();
        // 顺手清理两张按玩家记的表（早已过期的记录留着没意义）
        COOLDOWN.entrySet().removeIf(e -> e.getValue() < now - COOLDOWN_KEEP_MILLIS);
        HINT_AT.entrySet().removeIf(e -> now - e.getValue()
                > Math.max(60_000L, configuredHintSeconds() * 1000L));
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                chargeHeld(p, now);
            } catch (RuntimeException e) {
                // 单个玩家的异常绝不能中断整轮循环（更不能把 BukkitTask 打死）
                Log.warn("[梦想封印 集] 充能异常 @ " + p.getName() + ": " + e);
            }
        }
    }

    /** 主手 + 副手都算"手持"（把符卡放副手是常见用法）。 */
    private void chargeHeld(Player p, long now) {
        PlayerInventory inv = p.getInventory();
        chargeOne(p, inv, EquipmentSlot.HAND, now);
        chargeOne(p, inv, EquipmentSlot.OFF_HAND, now);
    }

    /**
     * 给一格里的道具取一次电。
     *
     * <p>★ 这里是"充能从哪个网络取"的完整答案：
     * <b>以玩家眼睛为球心 → 最近的一个 POWER 节点 → 那个节点所在的整张网</b>。
     * 取不到就什么都不做（只给一次节流提示），道具电量保持不变。
     */
    private void chargeOne(Player p, PlayerInventory inv, EquipmentSlot slot, long now) {
        ItemStack item = inv.getItem(slot);
        if (!isSeal(item)) {
            return;
        }
        long cap = configuredCapacity();
        long cur = Math.min(chargeOf(item), cap);
        if (cur >= cap) {
            return;   // 满了：不取电、也不提示
        }
        int range = configuredRange();
        Location origin = p.getEyeLocation();
        PowerNetwork net = PowerNetworkManager.getNetworkNear(origin, range);
        if (net == null) {
            hint(p, now, "&c附近 " + range + " 格内没有 POWER 网络，无法充能"
                    + " &7（把 POWER 方块放到脚边即可）");
            return;
        }
        long want = Math.min(configuredPerCycle(), cap - cur);
        long got = PowerNetworkManager.extractPower(net, want);
        if (got <= 0) {
            hint(p, now, "&e最近的 POWER 网络 #" + net.networkId() + " 里没有电"
                    + " &7（需要发电机或已充能的存储单元）");
            return;
        }
        setChargeOf(item, cur + got);
        inv.setItem(slot, item);   // 写回格子（CraftItemStack 本来是镜像，这一步只是保险）
    }

    /** 节流过的 warning：同一个玩家 {@code hint-throttle-seconds} 秒内最多一条。 */
    private void hint(Player p, long now, String text) {
        long throttle = Math.max(0, configuredHintSeconds()) * 1000L;
        Long last = HINT_AT.get(p.getUniqueId());
        if (last != null && throttle > 0 && now - last < throttle) {
            return;
        }
        HINT_AT.put(p.getUniqueId(), now);
        // ★ 玩家可见反馈一律走 Notify.warn：这是"玩家主动做了一件事但没成功"。
        //   Notify.info/important 那两档在默认配置下是【静默】的（见 Notify 的四档语义）。
        Notify.warn(Notify.seal(), p, text);
    }

    /** 是不是本道具（按粘液 id 反查：PDC 里带着 id 的任何 ItemStack 都认）。 */
    private boolean isSeal(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return SlimefunItem.getByItem(item) instanceof FantasySeal;
    }

    // ------------------------------------------------------------------ 交互

    private void onUse(PlayerRightClickEvent event) {
        event.cancel();   // 钓鱼竿：阻止抛竿
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        long now = System.currentTimeMillis();

        if (!canUse(p, false)) {
            Notify.warn(Notify.seal(), p, "&c你没有权限使用该道具!");
            return;
        }

        // ---- 冷却闸：spec 要求 1.5 秒，必须真拦住连发（原来的实现没有任何冷却） ----
        long until = COOLDOWN.getOrDefault(p.getUniqueId(), 0L);
        if (now < until) {
            // 玩家按了道具但没反应：这是"操作失败"，必须告诉他为什么
            Notify.warn(Notify.seal(), p, "&c尚未冷却完毕 &7（还需 "
                    + String.format("%.1f", (until - now) / 1000.0) + " 秒）");
            return;
        }

        long cost = configuredCost();
        long charge = chargeOf(item);
        if (charge < cost) {
            Notify.warn(Notify.seal(), p, "&cPOWER 不足 &7（" + charge + "/" + cost
                    + "）—— 手持它靠近 POWER 网络会自动充能");
            return;
        }

        if (cost > 0) {
            setChargeOf(item, charge - cost);
        }
        COOLDOWN.put(p.getUniqueId(), now + Math.max(0, configuredCooldownMillis()));
        fire(p, item);
    }

    // ------------------------------------------------------------------ 发射（spec 部分，未改动）

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

    // ------------------------------------------------------------------ 配置读取（一律带兜底）

    private long configuredCapacity() {
        Integer v = powerCapacity == null ? null : powerCapacity.getValue();
        return Math.max(1, v == null ? DEFAULT_CAPACITY : v);
    }

    /** 单次消耗（≥0；0 = 不耗电，允许但会退化成"无限连发"，不推荐）。 */
    public long configuredCost() {
        Integer v = powerCost == null ? null : powerCost.getValue();
        return Math.max(0, v == null ? DEFAULT_COST : v);
    }

    private int configuredPerCycle() {
        Integer v = chargePerCycle == null ? null : chargePerCycle.getValue();
        return Math.max(1, v == null ? DEFAULT_PER_CYCLE : v);
    }

    private int configuredIntervalSeconds() {
        Integer v = chargeIntervalSeconds == null ? null : chargeIntervalSeconds.getValue();
        return Math.max(1, v == null ? DEFAULT_INTERVAL_SECONDS : v);
    }

    private int configuredCooldownMillis() {
        Integer v = cooldownMillis == null ? null : cooldownMillis.getValue();
        return v == null ? DEFAULT_COOLDOWN_MILLIS : v;
    }

    private int configuredRange() {
        Integer v = chargeRange == null ? null : chargeRange.getValue();
        return Math.max(1, v == null ? DEFAULT_RANGE : v);
    }

    private int configuredHintSeconds() {
        Integer v = hintThrottleSeconds == null ? null : hintThrottleSeconds.getValue();
        return v == null ? DEFAULT_HINT_SECONDS : v;
    }

    // ------------------------------------------------------------------ 诊断

    /** 供 /touhou 命令无头验证：把关键参数与自检结果打出来。 */
    public List<String> selfCheck() {
        List<String> out = new ArrayList<>();
        out.add("FantasySeal id=" + getId() + " itemName=" + getItemName());
        out.add("  material        = " + getItem().getType());
        out.add("  能源            = 自研 POWER（不实现 Rechargeable），键 " + CHARGE_KEY);
        out.add("  powerCapacity   = " + configuredCapacity() + " POWER（满电可用 "
                + (configuredCost() <= 0 ? "∞" : configuredCapacity() / configuredCost()) + " 次）");
        out.add("  powerCost       = " + configuredCost() + " POWER / 发");
        out.add("  charge          = " + configuredPerCycle() + " POWER / "
                + configuredIntervalSeconds() + " 秒（取电半径 " + configuredRange()
                + " 格；提示节流 " + configuredHintSeconds() + " 秒）");
        // 这一行是"充能循环到底起没起来"的唯一证据：启动日志走 Log.info，
        // 而 logging.console-info 默认是 false（那条会静默），命令回显则不受开关影响。
        out.add("  chargeLoop      = " + (chargeTask == null
                ? "未启动（只会在主类 onEnable 注册完成后启动）"
                : "运行中（每 " + Math.max(1, configuredIntervalSeconds()) * 20L + " tick 一轮，"
                        + "在线玩家 " + Bukkit.getOnlinePlayers().size() + " 人）"));
        out.add("  cooldown        = " + configuredCooldownMillis() + " ms");
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

    /** 供命令输出：一件道具的 POWER 读数（玩家手持的、命令造的测试物品都走它）。 */
    public List<String> describeItem(String label, ItemStack item) {
        long cap = configuredCapacity();
        long cost = configuredCost();
        long charge = chargeOf(item);
        List<String> out = new ArrayList<>();
        out.add(label + " = " + (item == null || item.getType().isAir()
                ? "（空）" : item.getType().toString()));
        out.add("  当前 POWER   = " + charge + " / " + cap);
        out.add("  单次消耗     = " + cost + " POWER");
        out.add("  可用次数     = " + (cost <= 0 ? "∞（配置里 power-cost = 0）" : charge / cost));
        out.add("  充能         = " + configuredPerCycle() + " POWER / "
                + configuredIntervalSeconds() + " 秒（手持时自动，搜索半径 "
                + configuredRange() + " 格）");
        out.add("  冷却         = " + configuredCooldownMillis() + " ms / 发");
        return out;
    }
}
