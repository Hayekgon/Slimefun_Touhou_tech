package com.example.touhou.core;

import com.example.touhou.Touhou;
import com.example.touhou.power.PowerNetwork;
import com.example.touhou.power.PowerNetworkManager;
import com.example.touhou.power.PowerStorageUnit;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * PARTY_ITEM 组「符卡类道具」的公共基类 —— <b>共享同一套 POWER 充能数据</b>。
 *
 * <h2>为什么抽这个基类（而不是各写一份）</h2>
 * 本组目前两件道具（{@code 梦想封印 集} / {@code 杀意的百合}）的能源刻度是
 * <b>逐字相同</b>的一整套：
 * <pre>
 *   power-capacity           = 40   道具自身的 POWER 缓冲上限
 *   power-cost               = 1    单次使用消耗 1 POWER
 *   charge-per-cycle         = 5    每 {@code charge-interval-seconds} 秒充 5 POWER
 *   charge-interval-seconds  = 2    于是 16 秒充 40 POWER = 一次充能正好填满
 *   cooldown-millis          = 1500 每发之后 1.5 秒冷却
 *   charge-wireless          = false 无线充电关闭（等专用的无线供电器）
 *   charge-range             = 4    取电的搜索半径（格，切比雪夫）
 *   hint-throttle-seconds    = 15   "充能失败"提示的最小间隔（秒）
 * </pre>
 * 「数据沿用」这条要求的落点就是这里：<b>声明与读取路径只有一份</b>。
 * 若在新道具里照抄一份，两个类里那 8 个 ItemSetting 的 key 会各写一遍 ——
 * 改数值时必然只改一处，然后两件符卡悄悄开始不一样。
 *
 * <h2>为什么共用一套 key 不会串味（★ 这是"能不能共 key"的关键前提）</h2>
 * <ol>
 *   <li>{@code ItemSetting} 的宿主是<b>物品实例</b>：{@code SlimefunItem#itemSettings}
 *       是<b>每实例一个</b> {@code HashSet}（{@code addItemSetting} 的"同 key 只能加一次"
 *       校验也是逐实例的）。所以两件道具各自持有自己的 8 个设置，互不覆盖。</li>
 *   <li>持久化按<b>粘液 id 分节</b>：Slimefun 把设置写进
 *       {@code plugins/Slimefun/Items.yml} 里 {@code <物品 id>:} 那一节。
 *       于是 {@code TOUHOU_PARTY_ITEM_FANTASY_SEAL_CONVERGE} 与
 *       {@code TOUHOU_PARTY_ITEM_MURDEROUS_LILY} 各有一份完整的 8 个键，
 *       玩家可以只调其中一件。</li>
 * </ol>
 * 结论：<b>可以共用 key，但每件道具仍各有一条独立的配置分节</b>。
 *
 * <h2>对「梦想封印 集」的影响</h2>
 * 本类是把 {@code FantasySeal} 原有的字段与私有方法<b>原样上移</b>，没有改任何
 * 数值、key、默认值、读取顺序与提示文案；道具自己的 id 没变，所以它那一节
 * Items.yml 的键<b>一个都没变</b>（上线后实跑核对过）。行为不变这一点在交付报告里给了证据。
 *
 * <h2>子类必须提供的</h2>
 * <ul>
 *   <li>{@link #loreLabel()} —— lore 里那一行实时电量的前缀（例如 {@code "POWER:"}）；</li>
 *   <li>{@link #describeSelf()} —— 供 {@code /touhou} 诊断打印的道具专有参数；</li>
 *   <li>以及可选的 {@link #chargingStartDetail()}（充能循环启动日志里的附加说明）。</li>
 * </ul>
 *
 * <p>★ 线程口径：本类只碰 {@link ItemStack} / PDC / 玩家背包，<b>一律在主线程调用</b>
 * （充能循环与右键处理都是主线程）。
 */
public abstract class PartyItem extends SlimefunItem {

    // ------------------------------------------------------------------ 可配置项（写入 items.yml）

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
    /**
     * 是否启用<b>无线充电</b>：手持时站在 POWER 网络附近自动充能。
     *
     * <p>★ 默认 {@code false} —— 2026-09-20 按用户要求<b>关闭</b>这项能力。
     * 「靠近电网就自动充能」不该长在一件符卡道具上，用户后续会单独做一台
     * <b>无线供电器</b>来提供它。
     *
     * <p>所以这里<b>保留实现、只关开关</b>（而不是删代码）：
     * 底层取电链路 —— {@link PowerNetworkManager#extractPower}
     * 与本类的取电入口 —— 原封不动，那台机器直接复用即可。
     * 两件符卡共用这一个开关，<b>当前都是关的</b>（"数据沿用"的一部分）。
     * 想临时恢复：把 {@code items.yml} 的 {@code charge-wireless} 改成 {@code true}。
     */
    private final ItemSetting<Boolean> chargeWireless = setting("charge-wireless", false);
    /** 取电的搜索半径（格，切比雪夫）：玩家周围这个范围内最近的 POWER 节点所在的网。 */
    private final ItemSetting<Integer> chargeRange = setting("charge-range", 4);
    /** "附近没有网络 / 网络没电"这类提示的最小间隔（秒）—— 节流，避免刷屏。 */
    private final ItemSetting<Integer> hintThrottleSeconds = setting("hint-throttle-seconds", 15);

    // ------------------------------------------------------------------ 常量

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

    /** 发射冷却到期时刻（毫秒，按玩家记）。 */
    private static final Map<UUID, Long> COOLDOWN = new ConcurrentHashMap<>();

    /** 上一次给该玩家发"充能失败"提示的时刻（毫秒）—— 提示节流用。 */
    private static final Map<UUID, Long> HINT_AT = new ConcurrentHashMap<>();

    /** 冷却表的清理门槛（毫秒）：早已过期的记录留着没意义。 */
    private static final long COOLDOWN_KEEP_MILLIS = 60_000L;

    /** 充能循环的任务句柄（每件道具只起一次）。 */
    private BukkitTask chargeTask;

    protected PartyItem(ItemGroup itemGroup, SlimefunItemStack item,
                        RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    // ------------------------------------------------------------------ 子类契约

    /**
     * lore 里那一行实时电量的<b>纯文本前缀</b>（不含颜色码）。
     *
     * <p>刷新时是"找到以它开头的那一行就地替换"，所以物品模板里必须先放一行
     * （见 {@code AddItems}）—— 这样玩家在第一次充能之前也能看到这一行。
     * 比对前会 {@code stripColor}，因此颜色码不参与匹配。
     */
    protected abstract String loreLabel();

    /** 供 {@code /touhou} 诊断打印的<b>道具专有</b>参数（公共的 POWER 部分由本类补齐）。 */
    protected abstract List<String> describeSelf();

    /** 每件道具自己的中文名，用于控制台日志（例如 {@code 梦想封印 集}）。 */
    protected abstract String itemLabel();

    /** 充能循环启动日志里的附加说明（默认没有）。 */
    protected String chargingStartDetail() {
        return "";
    }

    // ------------------------------------------------------------------ 设置注册

    /**
     * 注册一个 ItemSetting 并返回它。
     *
     * <p>★ 必须<b>先 addItemSetting 再 reload</b>（也就是本方法的顺序）：
     * Slimefun 在 {@code SlimefunItem#register} 之前不接受新增设置
     * （{@code addItemSetting} 会抛 {@code UnsupportedOperationException}），
     * 而 register 那一刻会用已登记的这一批设置去读 Items.yml 并写回默认值。
     */
    protected final <T> ItemSetting<T> setting(String key, T defaultValue) {
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
     * <p>就地替换<b>以 {@link #loreLabel()} 开头</b>的那一行；找不到就追加到末尾。
     * ★ 这一行是"充能看得见"的唯一途径（本类不是 {@code Rechargeable}，
     * 也就没有 Slimefun 自带的那行电力显示），所以每次改电量都必须走这里。
     */
    private void renderChargeLore(ItemMeta meta, long charge, long cap) {
        List<String> lore = meta.getLore() == null
                ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        String label = loreLabel();
        long cost = configuredCost();
        String line = ChatColor.DARK_GRAY + label + " " + ChatColor.WHITE + charge
                + ChatColor.DARK_GRAY + "/" + ChatColor.WHITE + cap
                + ChatColor.GRAY + "（可用 " + (cost <= 0 ? "∞" : charge / cost) + " 次）";
        for (int i = 0; i < lore.size(); i++) {
            String plain = ChatColor.stripColor(lore.get(i));
            if (plain != null && plain.startsWith(label)) {
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
        // ★ 2026-09-20 按用户要求关闭【无线充电】：
        //   本道具不再"站在 POWER 网络附近就自动充能"。底层取电链路保持可用，
        //   等用户那台专用的「无线供电器」做好后直接复用。
        if (!Boolean.TRUE.equals(chargeWireless.getValue())) {
            Log.info("[" + itemLabel() + "] 无线充电已关闭（charge-wireless=false）——"
                    + "本道具当前不会自动充能，等专用的无线供电器");
            return;
        }
        long period = Math.max(1, configuredIntervalSeconds()) * 20L;   // 真实 tick，20/秒
        chargeTask = Bukkit.getScheduler().runTaskTimer(Touhou.getInstance(), this::chargeAll,
                period, period);
        Log.info("[" + itemLabel() + "] 充能循环已启动：每 " + configuredIntervalSeconds()
                + " 秒为手持者取 " + configuredPerCycle() + " POWER（搜索半径 " + configuredRange()
                + " 格，提示节流 " + configuredHintSeconds() + " 秒）" + chargingStartDetail());
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
                Log.warn("[" + itemLabel() + "] 充能异常 @ " + p.getName() + ": " + e);
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
        if (!isThisItem(item)) {
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
    protected void hint(Player p, long now, String text) {
        long throttle = Math.max(0, configuredHintSeconds()) * 1000L;
        Long last = HINT_AT.get(p.getUniqueId());
        if (last != null && throttle > 0 && now - last < throttle) {
            return;
        }
        HINT_AT.put(p.getUniqueId(), now);
        // ★ 玩家可见反馈一律走 Notify.warn：这是"玩家主动做了一件事但没成功"。
        //   Notify.info/important 那两档在默认配置下是【静默】的（见 Notify 的四档语义）。
        Notify.warn(scope(), p, text);
    }

    /** 本道具的消息作用域（前缀 + 档位各有独立的 config 段，见 AddonConfig）。 */
    protected abstract Notify.Scope scope();

    /** 是不是本道具（按粘液 id 反查：PDC 里带着 id 的任何 ItemStack 都认）。 */
    protected final boolean isThisItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        return SlimefunItem.getByItem(item) != null
                && SlimefunItem.getByItem(item).getId().equals(getId());
    }

    // ------------------------------------------------------------------ 发射闸（权限 / 冷却 / POWER）

    /**
     * 冷却闸 + POWER 闸的<b>公共</b>实现（两件符卡的发射闸完全一致）。
     *
     * <p>顺序与本项目既有实现一致：<b>先冷却、再查电</b>。
     * 冷却没到就提示"还需 x 秒"直接返回；冷却通过但电不够则只提示、<b>不</b>进入冷却
     * （否则会出现"电不够还白扣一次冷却"的怪手感）。
     *
     * @return {@code true} = 放行（已扣电、已记冷却）；{@code false} = 已给出提示、不要发射
     */
    protected final boolean passGate(Player p, ItemStack item, long now) {
        long until = COOLDOWN.getOrDefault(p.getUniqueId(), 0L);
        if (now < until) {
            // 玩家按了道具但没反应：这是"操作失败"，必须告诉他为什么
            Notify.warn(scope(), p, "&c尚未冷却完毕 &7（还需 "
                    + String.format("%.1f", (until - now) / 1000.0) + " 秒）");
            return false;
        }
        long cost = configuredCost();
        long charge = chargeOf(item);
        if (charge < cost) {
            Notify.warn(scope(), p, "&cPOWER 不足 &7（" + charge + "/" + cost
                    + "）—— 手持它靠近 POWER 网络会自动充能");
            return false;
        }
        if (cost > 0) {
            setChargeOf(item, charge - cost);
        }
        COOLDOWN.put(p.getUniqueId(), now + Math.max(0, configuredCooldownMillis()));
        return true;
    }

    /** 权限闸（与既有道具一致：走 Slimefun 的 canUse）。 */
    protected final boolean passPermission(Player p) {
        return canUse(p, false);
    }

    // ------------------------------------------------------------------ 工具

    /** 回到主线程执行（制导循环可能跑在异步线程）。 */
    protected static void sync(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
        } else {
            Bukkit.getScheduler().runTask(Touhou.getInstance(), r);
        }
    }

    /** 手持位置：眼位向脚位回拉 30%。 */
    protected static Location handLocation(Player p) {
        Location eye = p.getEyeLocation();
        Location feet = p.getLocation();
        eye.add(feet.subtract(eye).multiply(0.3).toVector());
        return eye;
    }

    /**
     * 索敌判据：活着的、非无敌的 {@link LivingEntity}；标记/小号盔甲架不算。
     *
     * <p>★ 这个判据原本是 {@code FantasySeal#isTargetable}（私有），
     * 上移到基类后<b>逻辑一字未改</b>，好让「杀意的百合」的追踪箭用同一把尺子。
     */
    protected static boolean isTargetable(Entity e) {
        if (!e.isValid() || e.isDead() || !(e instanceof LivingEntity le) || le.isInvulnerable()) {
            return false;
        }
        if (e instanceof ArmorStand stand && (stand.isMarker() || stand.isSmall())) {
            return false;
        }
        return true;
    }

    /**
     * 弹幕命中时的无敌帧清零（配合各自的 {@code *ArrowListener}）。
     *
     * <p>Minecraft 的受击无敌帧（noDamageTicks，默认 20）会让同 tick 内到达的后续伤害全部失效。
     * 36 发齐射（梦想封印 集）与 12 支追踪箭（杀意的百合）都几乎同时命中同一目标，
     * 不处理的话账面 N×伤害实际只结算 1 份。
     *
     * <p>做法：在伤害结算<b>之前</b>（{@code EntityDamageByEntityEvent} 在 vanilla {@code hurt()} 之前触发）
     * 把命中实体的 {@code noDamageTicks} 清零，于是每一发都能独立结算。
     *
     * <p>★ 这是「渲染用的箭矢伤害路径」的一部分：两件符卡共用它，
     * 所以「杀意的百合」的 12 支追踪箭与梦想封印 集的 36 发弹幕走的是<b>同一条</b>结算路径。
     *
     * @return {@code true} = 确认是本类道具发射的弹幕（调用方据此决定是否继续处理）
     */
    protected static boolean clearNoDamageTicks(Entity target) {
        if (target instanceof LivingEntity le && le.getNoDamageTicks() > 0) {
            le.setNoDamageTicks(0);
        }
        return true;
    }

    /**
     * 弹幕的<b>基础伤害</b> = 基础值 + 力量附魔加成 + 锋利附魔加成。
     *
     * <p>★ 公式与 {@code FantasySeal} 原有实现逐字相同（基础 16 / 力量 ×0.8 / 锋利 ×0.4），
     * 只是从 {@code fire()} 里抽出来共用 —— 于是「杀意的百合」的箭矢伤害
     * 与梦想封印 集走的是<b>同一段代码</b>，不会各写一套然后悄悄漂移。
     */
    protected static float arrowDamage(ItemStack item, double base, double powerAmp, double sharpAmp) {
        ItemMeta meta = item == null ? null : item.getItemMeta();
        int power = meta == null ? 0 : meta.getEnchantLevel(Enchantment.ARROW_DAMAGE);
        int sharpness = meta == null ? 0 : meta.getEnchantLevel(Enchantment.DAMAGE_ALL);
        return (float) (base + powerAmp * power + sharpAmp * sharpness);
    }

    // ------------------------------------------------------------------ 配置读取（一律带兜底）

    public final long configuredCapacity() {
        Integer v = powerCapacity == null ? null : powerCapacity.getValue();
        return Math.max(1, v == null ? DEFAULT_CAPACITY : v);
    }

    /** 单次消耗（≥0；0 = 不耗电，允许但会退化成"无限连发"，不推荐）。 */
    public final long configuredCost() {
        Integer v = powerCost == null ? null : powerCost.getValue();
        return Math.max(0, v == null ? DEFAULT_COST : v);
    }

    protected final int configuredPerCycle() {
        Integer v = chargePerCycle == null ? null : chargePerCycle.getValue();
        return Math.max(1, v == null ? DEFAULT_PER_CYCLE : v);
    }

    protected final int configuredIntervalSeconds() {
        Integer v = chargeIntervalSeconds == null ? null : chargeIntervalSeconds.getValue();
        return Math.max(1, v == null ? DEFAULT_INTERVAL_SECONDS : v);
    }

    public final int configuredCooldownMillis() {
        Integer v = cooldownMillis == null ? null : cooldownMillis.getValue();
        return v == null ? DEFAULT_COOLDOWN_MILLIS : v;
    }

    protected final int configuredRange() {
        Integer v = chargeRange == null ? null : chargeRange.getValue();
        return Math.max(1, v == null ? DEFAULT_RANGE : v);
    }

    protected final int configuredHintSeconds() {
        Integer v = hintThrottleSeconds == null ? null : hintThrottleSeconds.getValue();
        return v == null ? DEFAULT_HINT_SECONDS : v;
    }

    // ------------------------------------------------------------------ 诊断

    /**
     * 供 {@code /touhou} 命令无头验证：把<b>公共</b>的 POWER 参数与自检结果打出来。
     *
     * <p>道具专有的那些（弹幕数、激光长度、追踪半径…）由 {@link #describeSelf()} 补。
     * 两段拼在一起 = 完整自检。
     */
    public final List<String> selfCheck() {
        List<String> out = new ArrayList<>();
        out.add("id=" + getId() + " itemName=" + getItemName());
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
        out.addAll(describeSelf());
        out.add("  itemGroup       = " + (getItemGroup() == null
                ? "(无)" : getItemGroup().getKey().toString()));
        return out;
    }

    /** 供 {@code /touhou} 命令输出：一件道具的 POWER 读数（玩家手持的、命令造的测试物品都走它）。 */
    public final List<String> describeItem(String label, ItemStack item) {
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
