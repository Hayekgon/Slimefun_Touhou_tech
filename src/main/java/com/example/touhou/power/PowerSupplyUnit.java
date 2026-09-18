package com.example.touhou.power;

import com.example.touhou.core.AddonConfig;
import com.example.touhou.core.Log;
import com.example.touhou.core.PartyItem;
import com.example.touhou.core.TouhouData;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * POWER供给单元 —— <b>把 POWER 网络里的电无线送给附近玩家手持的符卡</b>。
 *
 * <p>物品组 {@code AddGroups.POWER}（1 级组 POWER / {@code touhou_power}），材质海晶灯，
 * 物品 id 按本项目铁律 {@code TOUHOU_"物品组POWER"_"英文名"} ⇒
 * {@code TOUHOU_POWER_POWER_SUPPLY_UNIT}（与 {@code TOUHOU_POWER_POWER_INTEGRATED_CORE} /
 * {@code ..._REPEATER} / {@code ..._STORAGE_UNIT} 同一命名形态）。
 *
 * <h2>它为什么存在（需求背景）</h2>
 * 「梦想封印 集」原本自带<b>无线充电</b>（手持时自动从附近的 POWER 网络取电）。
 * 2026-09-20 按用户要求把那项能力<b>关掉</b>了（{@code PartyItem#charge-wireless}
 * 保持 {@code false}），用户明确说"后续我会单独制作无线供电器" —— <b>本机就是那台供电器</b>。
 * 于是"无线充电"这件事从"道具自带"变成了"机器主动供给"：
 * <ul>
 *   <li>{@code FantasySeal} / {@code MurderousLily} 的 {@code charge-wireless} <b>仍然</b>是
 *       {@code false}（那是"道具自带无线充电"，已废弃），本类<b>一行都没碰</b>它们；</li>
 *   <li>取电链路反过来走：机器扫描附近的玩家，把电写进他们手上那件道具。</li>
 * </ul>
 *
 * <h2>充电目标：复用 {@link PartyItem} 基类（不为每种道具各写一份判断）</h2>
 * 判据只有一条 —— {@code SlimefunItem.getByItem(item) instanceof PartyItem}。
 * 于是「梦想封印 集」（{@link com.example.touhou.core.FantasySeal}）与
 * 「杀意的百合」（{@link com.example.touhou.core.MurderousLily}）<b>自动都被覆盖</b>，
 * 而且以后再加符卡道具也一样自动生效：本类里<b>没有任何一处</b>提到具体道具的类名或 id。
 * 充能本身走基类的那套 API（{@code chargeOf} / {@code setChargeOf} / {@code configuredCapacity}），
 * 所以 POWER 的读写口径（PDC 键、lore 里那一行实时电量、上限 clamp）与道具自己充能时<b>完全一致</b>。
 *
 * <h2>节点类型：{@link NodeType#STORAGE}（判断与理由）</h2>
 * <ol>
 *   <li>它的电流向与存储单元<b>逐字同构</b>：被网络按容量比例分配 → 被"外部负载"（玩家）取走。
 *       它<b>不是</b>发电机（不产电，必须参与均衡，否则网络里的电没处来），
 *       <b>不是</b>中继器（要存电，不是纯导体），<b>不是</b>集成核心（不当锚点、没有跳接半径）。</li>
 *   <li>不新增 {@code NodeType} ⇒ {@link PowerNetwork#settle()} /
 *       {@link PowerNetwork#measure()} 的分支<b>一字不动</b> ⇒ 旧存档里其它节点的行为逐字不变
 *       （沿用 {@code NodeType.GENERATOR} 那次改动立下的口径：能不动网络层就不动）。
 *       新增一个类型至少要改 settle 的均衡名单、measure 的统计与命令的计数三处 ——
 *       收益只是诊断里多一栏，不值这个风险。</li>
 *   <li>代价（写在这里免得后来人误判）：{@code /touhou power} 会把它统计进「存储单元」那一栏。
 *       那一栏的口径本来就是"参与容量均衡、又不是发电机的储能点"
 *       （见 {@code PowerNetwork#measure()} 的判据），所以<b>这不是误报</b>，是同一件事。</li>
 * </ol>
 * ★ 如果将来真要做"只取不捐"的纯负载节点（例如一台不会存电的耗电机器），
 *   再照 {@code GENERATOR} 的先例新增类型，而不是把本机改成那种语义。
 *
 * <h2>"电从哪来"的完整链路（这是本机最需要说清楚的一件事）</h2>
 * <pre>
 *   ① 机器自身缓冲（{@link #KEY_CHARGE}，上限 {@code capacity}，默认 5 POWER）
 *        ↑ 网络每 tick 的 settle() 按容量比例把它补上（它是 STORAGE 节点）
 *   ② 一次充电要 {@code charge-per-cycle}（默认 5）点：
 *        先扣自身缓冲；不够的部分<b>才</b>从所在网络现取
 *        （{@link PowerNetworkManager#extractPower}：从网络里有电的节点上依次扣，
 *          下一 tick 的 settle 会把剩下的电重新摊平）
 *   ③ 写进玩家手上那件 {@link PartyItem} 的 POWER（{@code setChargeOf}，自动 clamp + 刷 lore）
 * </pre>
 * 两处都没电（自身 0 + 网络 0）时<b>静默不充</b>：<b>本类没有任何一句 {@code Notify}</b>，
 * 所以"缓冲耗尽"不会给玩家刷任何提示 —— 需求明确要求（"静默不充，不要每 tick 刷提示"）。
 * 玩家想知道充没充上，看符卡 lore 里那一行实时电量即可（{@code setChargeOf} 会刷新它）。
 *
 * <h2>充电半径 / 充电速率（spec 没写，这两项是本类定下的判断）</h2>
 * <ul>
 *   <li><b>半径 {@code charge-range} 默认 4 格</b>：沿用 {@code FantasySeal} 原来那套无线充电的 4 格
 *       （{@code PartyItem} 的 {@code charge-range} 默认值也是 4）。理由：玩家对"无线充电"的手感预期
 *       就来自那套行为，先保持一致；做成配置项便于以后调。
 *       ⚠ 度量是<b>切比雪夫（立方体）</b>，不是球也不是沿轴直线 —— 见 {@link #inChargeRange}。</li>
 *   <li><b>速率 {@code charge-per-cycle} = 5 POWER / {@code charge-interval-seconds} = 2 秒</b>：
 *       逐字沿用 {@code FantasySeal} 的节拍。40 POWER 的符卡从空充满 = 8 拍 = <b>16 秒</b>，
 *       正是本项目既有的那条 spec（"每次充能需要 16s"）。</li>
 * </ul>
 *
 * <h2>没有 GUI（POWER 一族本来就没有界面，见 {@link DreamCatcher} 的先例）</h2>
 * 状态看两条既有诊断命令：{@code /touhou power <x> <y> <z>}（网络口径）与
 * {@code /touhou supply <x> <y> <z>}（本机口径，见 {@link #describe}）。
 *
 * <h2>落盘口径</h2>
 * <ul>
 *   <li>{@link #KEY_CHARGE} —— 自身缓冲电量，与 {@link PowerStorageUnit#KEY_CHARGE} <b>同一个键串</b>；</li>
 *   <li>{@link #KEY_GIVEN} / {@link #KEY_CYCLES} / {@link #KEY_LAST_AT} —— 累计送出、累计充能轮数、
 *       上次送出的时刻。<b>只在真的送出电时才写</b>（每轮最多 1 次），
 *       与幻梦捕捉器的"只在产出时落盘"同一口径；</li>
 *   <li>节拍与 tick/扫描计数<b>只在内存</b>（{@link #STATE}）：它们是纯运行时量，
 *       落盘就等于"每 2 秒写一次方块数据"，而对重启后的行为毫无影响。</li>
 * </ul>
 */
public class PowerSupplyUnit extends AbstractPowerBlock implements PowerComponent {

    /** 自身缓冲电量。★ 与 {@link PowerStorageUnit#KEY_CHARGE} 是<b>同一个键串</b>（POWER 体系共用一套）。 */
    public static final String KEY_CHARGE = PowerStorageUnit.KEY_CHARGE;
    /** 累计送给玩家的 POWER（跨重启，永不重置）。 */
    public static final String KEY_GIVEN = "touhou:supply-given";
    /** 累计充能轮数（真的有电送出去才算一轮）。 */
    public static final String KEY_CYCLES = "touhou:supply-cycles";
    /** 上一次真的送出电的时刻（毫秒时间戳）—— 供诊断与重启后对照。 */
    public static final String KEY_LAST_AT = "touhou:supply-last-charge";

    /** 自身缓冲上限的默认值（spec：5 POWER）。 */
    public static final int DEFAULT_CAPACITY = 5;
    /** 每个充能节拍、每位玩家充多少 POWER（沿用梦想封印 集的节拍）。 */
    public static final int DEFAULT_PER_CYCLE = 5;
    /** 充能节拍间隔的默认值（秒，沿用梦想封印 集的节拍）。 */
    public static final int DEFAULT_INTERVAL_SECONDS = 2;
    /** 无线充电半径的默认值（格，切比雪夫；沿用梦想封印 集原来那套无线充电的 4 格）。 */
    public static final int DEFAULT_RANGE = 4;

    /**
     * 双手的遍历顺序：<b>主手 → 副手</b>。
     *
     * <p>把符卡放副手是常见用法（{@code PartyItem#chargeHeld} 当初也是两手都算），
     * 所以这里两手都充；顺序固定是为了让"电量不够时先充哪一格"可复现。
     */
    private static final EquipmentSlot[] HANDS = {EquipmentSlot.HAND, EquipmentSlot.OFF_HAND};

    /**
     * 内存运行态（节拍 + 计数）—— <b>刻意不落盘</b>。
     *
     * <p>为什么用 {@code ConcurrentHashMap}：ticker 是主线程
     * （{@link AbstractPowerBlock} 的 {@code isSynchronized() == true}），
     * 但命令（{@code /touhou supply}）也会读它，与 {@link DreamCatcher} 的扫床缓存同一考虑。
     */
    private static final Map<Location, State> STATE = new ConcurrentHashMap<>();

    /** 一台机器的运行时读数。字段可变：tick 是热路径，不想每 tick new 一个对象。 */
    private static final class State {
        /** 累计 tick 次数（证明 ticker 真的在跑）。 */
        long ticks;
        /** 累计"扫玩家"次数（证明不是每 tick 都扫）。 */
        long scans;
        /** 上一次 tick 的时刻（毫秒）。 */
        long lastTickAt;
        /** 上一次"打拍子"的时刻（毫秒）—— 节拍闸门就是它。 */
        long beatAt;
        /** 上一轮真的送出了多少 POWER（诊断）。 */
        long lastGiven;
    }

    // ------------------------------------------------------------------ 可配置项（写入 items.yml）

    /** 自身 POWER 缓冲上限。spec：5 点。 */
    public final ItemSetting<Integer> capacity = new ItemSetting<>(this, "capacity", DEFAULT_CAPACITY);
    /** 每个节拍为<b>每位玩家</b>充多少 POWER（默认 5，沿用梦想封印 集的节拍）。 */
    public final ItemSetting<Integer> chargePerCycle =
            new ItemSetting<>(this, "charge-per-cycle", DEFAULT_PER_CYCLE);
    /** 充能节拍间隔（秒，默认 2）。 */
    public final ItemSetting<Integer> chargeIntervalSeconds =
            new ItemSetting<>(this, "charge-interval-seconds", DEFAULT_INTERVAL_SECONDS);
    /** 无线充电半径（格，<b>切比雪夫</b>；默认 4）。 */
    public final ItemSetting<Integer> chargeRange =
            new ItemSetting<>(this, "charge-range", DEFAULT_RANGE);

    public PowerSupplyUnit(ItemGroup itemGroup, SlimefunItemStack item,
                           RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemSetting(capacity);
        addItemSetting(chargePerCycle);
        addItemSetting(chargeIntervalSeconds);
        addItemSetting(chargeRange);
        // ticker / 放置 / 破坏三个 handler 都在 AbstractPowerBlock 的构造器里挂好了，
        // 本类不再重复注册（重复注册会让同一张网在一 tick 内被 tick 两次）。
        // ★ 本类也【不】建 BlockMenuPreset —— 没有界面就没有"构造期调用 init()、
        //   那一刻 Location 为 null"的风险（见 TouhouData 类注释里那条血的教训）。
    }

    // ------------------------------------------------------------------ PowerComponent

    /** 节点类型：储能点（被网络按容量比例补电，再把电送给玩家）。理由见类注释。 */
    @Override
    public NodeType powerType() {
        return NodeType.STORAGE;
    }

    @Override
    public long powerCharge(Location loc) {
        return TouhouData.getLong(loc, KEY_CHARGE, 0L);
    }

    @Override
    public void powerSetCharge(Location loc, long charge) {
        TouhouData.setLong(loc, KEY_CHARGE, Math.max(0, Math.min(charge, powerCapacity(loc))));
    }

    @Override
    public long powerCapacity(Location loc) {
        return configuredCapacity();
    }

    /** 配置里的额定容量（不依赖方块位置，命令与诊断也能读）。 */
    public long configuredCapacity() {
        Integer v = capacity == null ? null : capacity.getValue();
        return Math.max(0, v == null ? DEFAULT_CAPACITY : v);
    }

    // ------------------------------------------------------------------ 机器逻辑

    /**
     * 每 tick 一次（{@link AbstractPowerBlock} 已经让网络先结算过）。
     *
     * <p>⚠ 异常一旦抛出去就会把 ticker 打死（这台机器从此再也不充电，也不参与并网），
     * 所以整个方法体兜一层 —— 与幻梦捕捉器同样的保险。
     */
    @Override
    protected void onPowerTick(Block b) {
        Location loc = TouhouData.norm(b.getLocation());
        if (loc == null) {
            return;
        }
        State st = state(loc);
        st.ticks++;
        st.lastTickAt = System.currentTimeMillis();
        try {
            tickCharge(loc, st);
        } catch (RuntimeException e) {
            Log.warn("[POWER供给单元] tick 异常 @ " + TouhouData.xyz(loc) + ": " + e);
        }
    }

    /** 机器被拆：清掉内存里的运行态（不留脏记录）。 */
    @Override
    protected void onPowerRemoved(Block b) {
        Location loc = TouhouData.norm(b.getLocation());
        if (loc != null) {
            STATE.remove(loc);
        }
    }

    /** 放置时打一行 info（受 {@code logging.console-info} 控制；不刷屏，一次放置一行）。 */
    @Override
    protected void onPowerPlaced(Block b) {
        Log.info("[POWER供给单元] 已放置 @ " + TouhouData.xyz(b.getLocation())
                + "（无线充电半径 " + configuredRange() + " 格，每 "
                + configuredIntervalSeconds() + " 秒为范围内每位玩家充 "
                + configuredPerCycle() + " POWER；自身缓冲 " + configuredCapacity() + "）");
    }

    /**
     * 节拍 + 扫描 + 充电。
     *
     * <p>★ 顺序刻意是"<b>先看节拍、再扫玩家</b>"：节拍不到就直接返回，
     * 于是<b>每 tick 只花一次方块数据读取</b>，而不是每 tick 都做一次世界实体查询。
     * 这与 {@link DreamCatcher} 的"每 N 毫秒才真扫一次"是同一个思路，
     * 区别只是幻梦捕捉器的节流缓存在内存里、而这里连"这次的拍子"都还没到点。
     */
    private void tickCharge(Location loc, State st) {
        if (!AddonConfig.get().supplyEnabled) {
            return;   // 服务器总开关关掉了：本机不充电（仍是 POWER 节点，照常并网/储能）
        }
        long now = System.currentTimeMillis();
        if (now - st.beatAt < intervalMillis()) {
            return;
        }
        st.beatAt = now;        // 打拍子：无论这次扫到几个人，下一拍都要重新等满一个间隔
        st.scans++;
        List<Player> targets = nearbyPlayers(loc);
        if (targets.isEmpty()) {
            return;             // 半径内没人：这一拍什么都不做（不写方块数据、不刷任何提示）
        }
        long given = 0L;
        for (Player p : targets) {
            try {
                for (HandCharge hc : chargePlayer(loc, p)) {
                    given += hc.taken();
                }
            } catch (RuntimeException e) {
                // 单个玩家的异常绝不能中断整轮循环（更不能把 ticker 打死）
                Log.warn("[POWER供给单元] 充能异常 @ " + p.getName() + ": " + e);
            }
        }
        st.lastGiven = given;
        if (given > 0) {
            // 只有真的送出电才落盘（每拍最多一次），与幻梦捕捉器的"产出才写"同一口径
            TouhouData.addLong(loc, KEY_GIVEN, given);
            TouhouData.addLong(loc, KEY_CYCLES, 1L);
            TouhouData.setLong(loc, KEY_LAST_AT, now);
        }
    }

    /**
     * 半径内的玩家（切比雪夫），按<b>离机器由近到远</b>排序。
     *
     * <p>★ 为什么要排序：电不够时"先充谁"必须有个确定的说法，而
     * {@code getNearbyEntities} 的返回顺序<b>没有保证</b>（区块/实体表顺序）。
     * 取"最近的先充"既符合直觉，也让同一场景每次跑出来的结果一致（无头验证要靠它）。
     *
     * <p>★ 这里<b>刻意不</b>加"邻居区块没加载就跳过"的保护 —— 与 {@link DreamCatcher#countBeds}
     * 那条实测教训同源：那种保护看起来更省，实际会让机器<b>静默失效</b>
     * （块边界上的机器永远充不上电，而日志里一句线索都没有）。
     * 实体查询本来就会按需加载邻近区块，代价可以接受。
     */
    public List<Player> nearbyPlayers(Location machine) {
        Location loc = TouhouData.norm(machine);
        if (loc == null) {
            return List.of();
        }
        int r = configuredRange();
        List<Player> out = new ArrayList<>();
        for (Entity e : loc.getWorld().getNearbyEntities(loc, r, r, r)) {
            if (e instanceof Player p && inChargeRange(loc, p.getLocation())) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingDouble((Player p) -> distanceSquared(loc, p.getLocation())));
        return out;
    }

    /**
     * 目标在不在充电半径内 —— <b>半径口径的唯一出处</b>。
     *
     * <p>★ 度量是<b>切比雪夫距离</b>（{@code max(|dx|,|dy|,|dz|) <= range}），也就是一个
     * <b>立方体</b>：斜向、正上、正下都算。这与 POWER 网络的"半径跳接"用的是同一把尺子
     * （见 {@link PowerComponent#powerJumpRange()} 的对照表：
     * 半径 7 时立方体是 3374 格，而原生 Slimefun 的轴向十字只有 42 格）。
     *
     * <p>这里刻意<b>不</b>用欧氏距离：4 格欧氏距离下"站在斜上方 3,3,0"（欧氏 4.24）会被拒绝，
     * 而玩家的直觉是"我就在机器旁边"。要改成球形口径就只改这一个方法，别处不必动。
     */
    public boolean inChargeRange(Location machine, Location target) {
        Location m = TouhouData.norm(machine);
        Location t = TouhouData.norm(target);
        if (m == null || t == null || !m.getWorld().equals(t.getWorld())) {
            return false;
        }
        int dx = Math.abs(m.getBlockX() - t.getBlockX());
        int dy = Math.abs(m.getBlockY() - t.getBlockY());
        int dz = Math.abs(m.getBlockZ() - t.getBlockZ());
        return Math.max(dx, Math.max(dy, dz)) <= configuredRange();
    }

    /**
     * 给一个玩家的<b>主手 + 副手</b>各充一次，返回每一格的明细。
     *
     * <p>ticker 与 {@code /touhou supply ... charge <玩家名>} <b>共用这一段</b>：
     * 命令走的就是机器自己那一轮逻辑，不是另写一份演示。
     */
    public List<HandCharge> chargePlayer(Location machine, Player p) {
        List<HandCharge> out = new ArrayList<>();
        Location loc = TouhouData.norm(machine);
        if (loc == null || p == null) {
            return out;
        }
        PlayerInventory inv = p.getInventory();
        for (EquipmentSlot slot : HANDS) {
            ItemStack item = inv.getItem(slot);
            ChargeOutcome oc = chargeItem(loc, item);
            if (oc == null) {
                continue;   // 这一格不是符卡：不管
            }
            if (oc.charged()) {
                inv.setItem(slot, item);   // 写回格子（CraftItemStack 本来是镜像，这一步只是保险）
            }
            out.add(new HandCharge(slot, oc));
        }
        return out;
    }

    /**
     * <b>充电本身</b>：给一件物品充一次电 —— 全机唯一一处写 POWER 的地方。
     *
     * <p>① 不是 {@link PartyItem}（或已满）⇒ 返回 {@code null} / "已满"，什么都不做；
     * ② 先扣自身缓冲（{@link #KEY_CHARGE}）；
     * ③ 不够的部分才用 {@link PowerNetworkManager#extractPower} 从所在网络现取；
     * ④ 两处都没电 ⇒ 返回"0 点、静默"，不写道具、<b>不发任何消息</b>。
     *
     * <p>★ 为什么"先缓冲、后网络"而不是每次都直接从网络抽：
     * 缓冲就是这台机器"手边的那点零钱"，它存在的前提就是先花它；
     * 而直接从网络抽电会绕开"本机也有储能点"这个定位（也就没人看得出它到底存了多少）。
     * 两者最终都由同一张网的电买单，区别只在可观察性与"优先用自己"的语义。
     *
     * @return 该物品不是充能目标时返回 {@code null}（调用方据此跳过这一格）
     */
    public ChargeOutcome chargeItem(Location machine, ItemStack item) {
        Location loc = TouhouData.norm(machine);
        if (loc == null || item == null || item.getType().isAir()) {
            return null;
        }
        if (!(SlimefunItem.getByItem(item) instanceof PartyItem party)) {
            return null;   // 不是符卡类道具：本机一概不管（多态判据只有这一处）
        }
        long cap = party.configuredCapacity();
        long cur = Math.min(Math.max(0L, party.chargeOf(item)), cap);
        if (cur >= cap) {
            return new ChargeOutcome(cur, cur, 0L, 0L, cap, "道具已满（" + cap + " POWER）");
        }
        long want = Math.min(configuredPerCycle(), cap - cur);

        // ① 自身缓冲优先
        long own = Math.max(0L, powerCharge(loc));
        long fromSelf = Math.min(own, want);
        if (fromSelf > 0) {
            powerSetCharge(loc, own - fromSelf);
        }

        // ② 不够的部分从所在网络现取（下一 tick 的 settle 会把剩下的电重新摊平）
        long fromNet = 0L;
        long deficit = want - fromSelf;
        if (deficit > 0) {
            PowerNetwork net = PowerNetworkManager.getNetwork(loc);
            fromNet = PowerNetworkManager.extractPower(net, deficit);
        }

        long got = fromSelf + fromNet;
        if (got <= 0) {
            // ★ 静默不充：这里绝不能加 Notify（需求明确要求"缓冲为 0 时静默不充、
            //   不要每 tick 刷提示"）。玩家想知道充没充上，看符卡 lore 那一行电量即可。
            return new ChargeOutcome(cur, cur, 0L, 0L, cap, "机器缓冲与所在网络都没有电：静默不充");
        }
        party.setChargeOf(item, cur + got);   // 自动 clamp + 刷新 lore 里那一行实时电量
        return new ChargeOutcome(cur, cur + got, fromSelf, fromNet, cap,
                "本次充入 " + got + " POWER（自身缓冲 " + fromSelf + " + 网络 " + fromNet + "）");
    }

    // ------------------------------------------------------------------ 配置读取（一律带兜底）

    private int configuredPerCycle() {
        Integer v = chargePerCycle == null ? null : chargePerCycle.getValue();
        return Math.max(1, v == null ? DEFAULT_PER_CYCLE : v);
    }

    private int configuredIntervalSeconds() {
        Integer v = chargeIntervalSeconds == null ? null : chargeIntervalSeconds.getValue();
        return Math.max(1, v == null ? DEFAULT_INTERVAL_SECONDS : v);
    }

    /** 充电半径（格）：至少 1 —— 0 会让本机永远扫不到人（那种情况应该用 {@code supply.enabled} 关掉）。 */
    public int configuredRange() {
        Integer v = chargeRange == null ? null : chargeRange.getValue();
        return Math.max(1, v == null ? DEFAULT_RANGE : v);
    }

    /** 一个充能节拍多少毫秒（真实时间，不受 Slimefun tickRate 影响）。 */
    public long intervalMillis() {
        return configuredIntervalSeconds() * 1000L;
    }

    // ------------------------------------------------------------------ 结果对象

    /**
     * 一次充电的结果（游戏内 tick 与 {@code /touhou supply ... charge} 共用）。
     *
     * @param before      充电前的道具 POWER
     * @param after       充电后的道具 POWER
     * @param fromSelf    其中来自机器自身缓冲的部分
     * @param fromNetwork 其中来自所在网络的部分
     * @param capacity    道具自身的 POWER 上限
     * @param note        一句话说明（诊断打印用）
     */
    public record ChargeOutcome(long before, long after, long fromSelf, long fromNetwork,
                                long capacity, String note) {

        /** 本次实际充入的点数。 */
        public long taken() {
            return after - before;
        }

        /** 有没有真的充进去。 */
        public boolean charged() {
            return after > before;
        }
    }

    /** 某一格（主手 / 副手）的充电结果。 */
    public record HandCharge(EquipmentSlot slot, ChargeOutcome outcome) {

        public long taken() {
            return outcome.taken();
        }

        /** 中文槽位名。 */
        public String slotLabel() {
            return slot == EquipmentSlot.HAND ? "主手" : "副手";
        }
    }

    // ------------------------------------------------------------------ 诊断

    /**
     * 供 {@code /touhou supply <x> <y> <z>} 无头验证。
     *
     * <p>需求要求的四件事都在里面：<b>半径内的玩家 / 机器自身缓冲 / 所在网络总量 /
     * 充能速率与半径</b>；另外给出"节拍 + tick 与扫描计数"，于是
     * 「不是每 tick 扫玩家」「缓冲耗尽时静默」这两条也能靠读数核对，不必真人进游戏。
     */
    public List<String> describe(Location rawLoc) {
        List<String> out = new ArrayList<>();
        Location loc = TouhouData.norm(rawLoc);
        if (loc == null) {
            out.add("位置无效");
            return out;
        }
        long now = System.currentTimeMillis();
        State st = state(loc);
        long own = powerCharge(loc);
        long cap = powerCapacity(loc);
        long given = TouhouData.getLong(loc, KEY_GIVEN, 0L);
        long cycles = TouhouData.getLong(loc, KEY_CYCLES, 0L);
        long lastAt = TouhouData.getLong(loc, KEY_LAST_AT, -1L);

        out.add("POWER供给单元 @ " + TouhouData.xyz(loc) + "  世界=" + loc.getWorld().getName());
        out.add("  节点类型       = " + powerType()
                + "（储能点：被网络按容量比例补电，再把电送给玩家）");
        out.add("  自身 POWER     = " + own + " / " + cap);
        out.add("  无线充电       = " + (AddonConfig.get().supplyEnabled ? "开" : "关（supply.enabled=false）")
                + "  半径 " + configuredRange() + " 格（切比雪夫/立方体）"
                + "  每 " + configuredIntervalSeconds() + " 秒为每位玩家充 "
                + configuredPerCycle() + " POWER");
        if (st.lastTickAt <= 0) {
            out.add("  节拍           = 尚未起表（本机还没被 tick 过）");
        } else {
            long elapsed = Math.max(0L, now - st.beatAt);
            long remain = Math.max(0L, intervalMillis() - elapsed);
            out.add("  节拍           = 距上一拍 " + String.format("%.2f", elapsed / 1000.0)
                    + " 秒 / 共 " + String.format("%.2f", intervalMillis() / 1000.0)
                    + " 秒，下一拍还需 " + String.format("%.2f", remain / 1000.0) + " 秒"
                    + "（内存节拍，不落盘）");
        }
        out.add("  运行态         = 已 tick " + st.ticks + " 次，扫玩家 " + st.scans
                + " 次（每 " + String.format("%.1f", intervalMillis() / 1000.0)
                + " 秒才扫一次，不是每 tick；上一轮送出 " + st.lastGiven + " POWER）");
        out.add("  累计送出       = " + given + " POWER / " + cycles + " 轮"
                + (lastAt < 0 ? "（还没送出过）"
                        : "（上次 " + String.format("%.1f", Math.max(0L, now - lastAt) / 1000.0) + " 秒前）"));
        PowerNetwork net = PowerNetworkManager.getNetwork(loc);
        if (net == null) {
            out.add("  所在网络       = 未接入任何网络（本机是孤立方块）");
            out.add("  网络 POWER 总量 = 0 / 0（自己那 " + own + " POWER 就在上面一行）");
        } else {
            PowerNetwork.Totals t = net.measure();
            out.add("  所在网络       = #" + net.networkId() + " 节点 " + net.size()
                    + "（储能点 " + t.storages() + " / 发电机 " + t.generators() + "）");
            out.add("  网络 POWER 总量 = " + t.charge() + " / " + t.capacity() + "（现算，含发电机缓冲）");
        }
        List<Player> targets = nearbyPlayers(loc);
        out.add("  半径内的玩家   = " + targets.size()
                + (targets.isEmpty() ? "（无头环境没有真人玩家时恒为 0）" : " " + playerNames(targets)));
        out.add("  区块           = " + chunkLine(loc));
        return out;
    }

    /**
     * 供 {@code /touhou supply <x> <y> <z> range <tx> <ty> <tz>} 验证<b>距离判定</b>。
     *
     * <p>无头环境里没有真人玩家可以来回走，但"几格之内算范围内"是<b>纯几何</b> ——
     * 这里走的就是 {@link #inChargeRange} 那把尺子（半径口径的唯一出处），
     * 于是"4 格内 vs 4 格外"的差异可以被逐格核对。
     */
    public List<String> describeRange(Location rawLoc, Location rawTarget) {
        List<String> out = new ArrayList<>();
        Location loc = TouhouData.norm(rawLoc);
        Location t = TouhouData.norm(rawTarget);
        if (loc == null || t == null) {
            out.add("位置无效");
            return out;
        }
        int dx = t.getBlockX() - loc.getBlockX();
        int dy = t.getBlockY() - loc.getBlockY();
        int dz = t.getBlockZ() - loc.getBlockZ();
        int cheb = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        int range = configuredRange();
        boolean sameWorld = loc.getWorld().equals(t.getWorld());
        boolean in = inChargeRange(loc, t);
        out.add("距离判定：机器 " + TouhouData.xyz(loc) + " → 目标 " + TouhouData.xyz(t)
                + "（世界 " + t.getWorld().getName()
                + (sameWorld ? "" : "，与本机不同世界") + "）");
        out.add("  偏移           = dx=" + dx + " dy=" + dy + " dz=" + dz);
        out.add("  切比雪夫距离   = " + cheb + " 格（半径口径就是它：max(|dx|,|dy|,|dz|)）");
        out.add("  曼哈顿 / 欧氏  = " + (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) + " 格 / "
                + String.format("%.2f", Math.sqrt((double) dx * dx + (double) dy * dy + (double) dz * dz))
                + " 格（仅供参考，不参与判定）");
        out.add("  判定           = " + (in
                ? "在半径 " + range + " 格内 ⇒ 若该坐标有玩家，手持符卡会被充能"
                : "超出半径 " + range + " 格 ⇒ 该坐标的玩家不会被充能（哪怕只多 1 格）"));
        out.add("  半径规模       = 立方体 " + (range * 2 + 1) + "×" + (range * 2 + 1) + "×"
                + (range * 2 + 1) + "，共 " + cubeSize(range) + " 格（含斜向与上下）");
        return out;
    }

    /** 立方体半径内的格数（去掉中心那一格）。 */
    private static long cubeSize(int r) {
        long side = r * 2L + 1L;
        return side * side * side - 1L;
    }

    /** 两个位置的欧氏距离平方（跨世界返回 {@code Double.MAX_VALUE} —— 当作"永不最近"）。 */
    private static double distanceSquared(Location a, Location b) {
        if (a == null || b == null || a.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            return Double.MAX_VALUE;
        }
        return a.distanceSquared(b);
    }

    private static String playerNames(List<Player> players) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < players.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(players.get(i).getName());
        }
        return sb.append(']').toString();
    }

    /** 本机所在区块的加载状态 —— 纯诊断（充电判定从不看它，见 {@link #nearbyPlayers}）。 */
    private String chunkLine(Location loc) {
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        return "chunk(" + cx + "," + cz + ") 服务端判定 加载="
                + (loc.getWorld().isChunkLoaded(cx, cz) ? "是" : "否");
    }

    /** 取（必要时新建）一台机器的运行态。 */
    private static State state(Location loc) {
        return STATE.computeIfAbsent(loc, k -> new State());
    }
}
