package com.example.touhou.power;

import com.example.touhou.core.Log;
import com.example.touhou.core.TouhouData;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

/**
 * 幻梦捕捉器 —— <b>POWER 体系的第一台产能设备</b>（原创机器，不是东方原作里的东西）。
 *
 * <p>★ 命名由来：「幻梦捕捉器」取的是<b>捕梦网（dreamcatcher）</b>的意象 ——
 * 把梦收进网里、再榨成 POWER，所以英文名就是 <b>Dreamcatcher</b>，
 * 物品 id 为 {@code TOUHOU_POWER_DREAMCATCHER}（{@code TOUHOU_} + 物品组 POWER + DREAMCATCHER）。
 *
 * <h2>它做什么</h2>
 * 把梦收进标靶里榨成 POWER：机器<b>四个水平面</b>（东/南/西/北，<b>不含上下</b>）
 * 每紧贴一张床，就多一份效率。
 *
 * <pre>
 *   产出间隔 = 基准间隔 ÷ 床数（床数 = 有床的【面数】，0~4）
 *   默认基准 = 8 秒 ⇒ 1 张床 8 秒 1 点、4 张床 2 秒 1 点
 *   床数 0   = 不产出（并且计时归零，重新放床要重新等满一整段）
 * </pre>
 *
 * <h2>三个实现上的关键决定</h2>
 * <ol>
 *   <li><b>节点类型是 {@link NodeType#GENERATOR}（只捐不取）</b>。
 *       {@link PowerNetwork#settle()} 的语义是"按容量比例均衡"，发电机如果也参与，
 *       就会按自己那 15 点缓冲分走一份电 —— 那是"存电"不是"产电"。见那个枚举的注释。</li>
 *   <li><b>产出优先对外输出</b>：先投递给网络里别的节点（{@link PowerNetworkManager#depositPower}），
 *       投不出去（网络满了 / 网络里只有自己）才留在自己的缓冲里。
 *       即便投递失败，下一 tick 的 settle 也会把发电机缓冲里的电<b>捐</b>给储能点 ——
 *       两道保险，所以"自身缓冲不会一直涨"。</li>
 *   <li><b>没有 GUI</b>：POWER 方块这一族（集成核心 / 中继器 / 存储单元）本来就没有界面，
 *       本机也不需要"塞东西进去"，所以不造多余的界面。状态看两个地方：
 *       信息显示在 {@code /touhou power <x> <y> <z>} 与 {@code /touhou dreamcatcher <x> <y> <z>}，
 *       以及核心头顶的悬浮字（网络总量里已经含本机的贡献）。</li>
 * </ol>
 *
 * <h2>落盘口径（全部走方块数据，见 {@link TouhouData}）</h2>
 * <ul>
 *   <li>{@link #KEY_CHARGE} —— 自身缓冲电量，<b>与 {@link PowerStorageUnit#KEY_CHARGE} 同一套</b>；</li>
 *   <li>{@link #KEY_BEDS} —— 上一次统计到的床数（<b>只在变化时写</b>，供诊断与重启后对照）；</li>
 *   <li>{@link #KEY_SINCE} / {@link #KEY_CYCLE} / {@link #KEY_LAST_PROD} —— 本轮计时三件套
 *       （起点 / 本轮已产出 / 上次产出时刻），用于"实测产出速率"；</li>
 *   <li>{@link #KEY_PRODUCED} —— 累计产出（跨重启、跨拆床，永不重置）。</li>
 * </ul>
 * ★ 与 tick 相关的临时量一律<b>只在产出或床数变化时</b>才落盘（每轮最多 1 次），
 * 不做"每 tick 写一次方块数据"那种事。
 */
public class DreamCatcher extends AbstractPowerBlock implements PowerComponent {

    /** 自身缓冲电量。★ 与 {@link PowerStorageUnit#KEY_CHARGE} 是<b>同一个键串</b>（POWER 体系共用一套）。 */
    public static final String KEY_CHARGE = PowerStorageUnit.KEY_CHARGE;
    /** 上一次统计到的床数（0~4）。 */
    public static final String KEY_BEDS = "touhou:dream-beds";
    /** 本轮计时的起点（毫秒时间戳）：床数一变就重置。 */
    public static final String KEY_SINCE = "touhou:dream-since";
    /** 本轮（当前床数配置下）已产出的 POWER。 */
    public static final String KEY_CYCLE = "touhou:dream-cycle";
    /** 上一次产出的时刻（毫秒时间戳）—— 判定"够不够一个间隔"用的就是它。 */
    public static final String KEY_LAST_PROD = "touhou:dream-last-produce";
    /** 累计产出（POWER），永不重置。 */
    public static final String KEY_PRODUCED = "touhou:dream-produced";

    /**
     * 四个<b>水平</b>面 —— 刻意不含 {@code UP}/{@code DOWN}：
     * 需求是"床要摆在机器的东南西北"，头顶和脚下铺床不算。
     * 顺序取"东→南→西→北"，诊断输出按这个顺序列四面。
     */
    private static final BlockFace[] HORIZONTAL = {
            BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH
    };

    /** 四个方向的显示名（与 {@link #HORIZONTAL} 一一对应）。 */
    private static final String[] HORIZONTAL_NAMES = {"东", "南", "西", "北"};

    /** 自身缓冲上限的默认值（spec：15 点）。 */
    public static final int DEFAULT_CAPACITY = 15;
    /** 基准产出间隔的默认值（spec：8 秒 = 1 张床时产 1 点）。 */
    public static final int DEFAULT_BASE_SECONDS = 8;
    /** 每轮产出量的默认值。 */
    public static final int DEFAULT_PER_CYCLE = 1;
    /** 扫床节流的默认值（毫秒）。 */
    public static final int DEFAULT_BED_SCAN_MILLIS = 1000;

    /**
     * 上一轮扫床结果的内存缓存。
     *
     * <p>★ 为什么不落盘：床是玩家随手拆放的东西，"上一轮扫到几张床"重启后重扫一次就有了，
     * 不值得为它写方块数据。落盘的只有 {@link #KEY_BEDS} 那一份"给人看的结论"。
     *
     * <p>★ 为什么用 {@code ConcurrentHashMap}：ticker 虽然是主线程
     * （{@link AbstractPowerBlock} 的 {@code isSynchronized() == true}），
     * 但命令/未来的异步调用方也会走到 {@link #bedCount}，加锁成本比踩一次并发低。
     */
    private static final Map<Location, BedScan> BED_CACHE = new ConcurrentHashMap<>();

    /** 一次扫床的结论。 */
    private record BedScan(int beds, long at) {
    }

    // ------------------------------------------------------------------ 可配置项（写入 items.yml）

    /** 自身 POWER 缓冲上限。spec：15 点。 */
    public final ItemSetting<Integer> capacity = new ItemSetting<>(this, "capacity", DEFAULT_CAPACITY);
    /** 基准产出间隔（秒）：<b>1 张床</b>时产 1 点 POWER 要多久。spec：8 秒。 */
    public final ItemSetting<Integer> baseIntervalSeconds =
            new ItemSetting<>(this, "base-interval-seconds", DEFAULT_BASE_SECONDS);
    /** 每一轮产出多少 POWER（≥1；默认 1）。 */
    public final ItemSetting<Integer> powerPerCycle =
            new ItemSetting<>(this, "power-per-cycle", DEFAULT_PER_CYCLE);
    /** 多久重新数一次四周的床（毫秒）。★ 这就是"别每 tick 扫 4 格"的那道节流。 */
    public final ItemSetting<Integer> bedScanIntervalMillis =
            new ItemSetting<>(this, "bed-scan-interval-millis", DEFAULT_BED_SCAN_MILLIS);

    public DreamCatcher(ItemGroup itemGroup, SlimefunItemStack item,
                        RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemSetting(capacity);
        addItemSetting(baseIntervalSeconds);
        addItemSetting(powerPerCycle);
        addItemSetting(bedScanIntervalMillis);
        // ticker / 放置 / 破坏三个 handler 都在 AbstractPowerBlock 的构造器里挂好了，
        // 本类不再重复注册（重复注册会让同一张网在一 tick 内被 tick 两次）。
        // ★ 本类也【不】建 BlockMenuPreset —— 没有界面就没有"构造期调用 init()"的风险，
        //   见类注释第 3 条。
    }

    // ------------------------------------------------------------------ PowerComponent

    /** 节点类型：产能设备（只捐不取）。 */
    @Override
    public NodeType powerType() {
        return NodeType.GENERATOR;
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

    /**
     * 配置里的额定容量（不依赖方块位置）。
     *
     * <p>抽出来是为了让 {@link #describe(Location)} / 命令也能报出"这台机器容量多少"，
     * 并且在 {@code ItemSetting} 尚未取到值（注册前）时兜底 —— 与
     * {@code Saizenbako#configuredCapacity} 同款写法。
     */
    public long configuredCapacity() {
        Integer v = capacity == null ? null : capacity.getValue();
        return Math.max(0, v == null ? DEFAULT_CAPACITY : v);
    }

    // ------------------------------------------------------------------ 机器逻辑

    /**
     * 每 tick 一次（{@link AbstractPowerBlock} 已经让网络先结算过）。
     *
     * <p>⚠ 异常一旦抛出去就会把 ticker 打死（这台机器从此再也不产出，也不参与并网），
     * 所以整个方法体兜一层 —— 与赛钱箱同样的保险。
     */
    @Override
    protected void onPowerTick(Block b) {
        try {
            tickProduction(b);
        } catch (RuntimeException e) {
            Log.warn("[幻梦捕捉器] tick 异常 @ " + TouhouData.xyz(b.getLocation()) + ": " + e);
        }
    }

    /** 机器被拆：清掉内存里的扫床缓存（不留脏记录）。 */
    @Override
    protected void onPowerRemoved(Block b) {
        Location loc = TouhouData.norm(b.getLocation());
        if (loc != null) {
            BED_CACHE.remove(loc);
        }
    }

    private void tickProduction(Block b) {
        Location loc = TouhouData.norm(b.getLocation());
        if (loc == null) {
            return;
        }
        long now = System.currentTimeMillis();
        int beds = bedCount(loc, now);             // 走节流（会重置计时，所以只在这里调）
        if (beds <= 0) {
            return;                                // 床数 0：不产出（计时已随床数变化归零）
        }
        long interval = intervalMillis(beds);
        if (interval <= 0) {
            return;
        }
        long last = TouhouData.getLong(loc, KEY_LAST_PROD, -1L);
        if (last < 0) {
            // 首次（或方块数据被清过）：先把表走起来，一个间隔之后才开始产出。
            // ⚠ 这里绝不能把默认值写成 now —— 那样每 tick 读到的都是一个"刚刚"的时间戳，
            //   差值永远是 0，机器会永远不产出（读默认值当"当前时刻"的经典坑）。
            TouhouData.setLong(loc, KEY_LAST_PROD, now);
            TouhouData.setLong(loc, KEY_SINCE, now);
            TouhouData.setLong(loc, KEY_CYCLE, 0L);
            return;
        }
        if (now - last < interval) {
            return;
        }
        produce(loc, now);
    }

    /**
     * 产出一轮 POWER。
     *
     * <p>顺序刻意是"<b>先投递、后留存</b>"：投递失败的部分才进自己的缓冲，
     * 所以只要网络里还有空位，自身缓冲就会一直是 0（这就是"优先对外输出"）。
     */
    private void produce(Location loc, long now) {
        long per = Math.max(1, configuredPerCycle());

        TouhouData.setLong(loc, KEY_LAST_PROD, now);
        TouhouData.addLong(loc, KEY_CYCLE, per);
        TouhouData.addLong(loc, KEY_PRODUCED, per);

        PowerNetwork net = PowerNetworkManager.getNetwork(loc);
        long accepted = PowerNetworkManager.depositPower(net, loc, per);
        if (accepted < per) {
            // 网络里没人接得住（满了，或者本机就是这张网里唯一的节点）：
            // 留在自己的缓冲里，等下一 tick 由 settle 再捐一次。
            long put = Math.min(powerCapacity(loc), powerCharge(loc) + (per - accepted));
            powerSetCharge(loc, put);
        }
    }

    // ------------------------------------------------------------------ 床

    /**
     * 数四周的床 —— 返回的是"<b>有床的面数</b>"（0~4），不是床的方块数。
     *
     * <p>两个刻意的口径：
     * <ul>
     *   <li>只看四个<b>水平</b>面（不含上下），与需求一致；</li>
     *   <li>一面里就算摆了两张床也只算 1 —— 需求给的是"效率 = 床数量 × 100%"、
     *       "4 面都有床时 2 秒产出 1"，上限就是 4 倍。</li>
     * </ul>
     *
     * <p>判据用 {@link Tag#BEDS}（任何床都算，含床头/床尾两半），不写死材质表。
     *
     * <p>★★ 这里<b>刻意不</b>加"邻居区块没加载就跳过"的保护（2026-09-21 实测教训）：
     * 那个保护看起来更省，实际会让机器<b>静默失效</b> ——
     * 同一台机器、同一片床，服务端对邻块报 {@code isChunkLoaded == false} 时
     * （border chunk / 刚被 forceload 起来的一瞬间都可能这样）四张床直接算成 0 张，
     * 玩家看到的现象就是"床明明摆着却不产电"，而且日志里一句线索都没有。
     * 现在一律<b>直接读方块材质</b>：区块真没加载，那一次方块查询会同步把邻块载进来
     * （最多 4 格、只在本机被 tick 时才发生），代价远小于"功能悄悄失灵"。
     */
    public int countBeds(Location loc) {
        Location n = TouhouData.norm(loc);
        if (n == null) {
            return 0;
        }
        int beds = 0;
        for (BlockFace f : HORIZONTAL) {
            if (Tag.BEDS.isTagged(n.getBlock().getRelative(f).getType())) {
                beds++;
            }
        }
        return beds;
    }

    /**
     * 带节流的扫床：距上次扫描不足 {@link #bedScanIntervalMillis} 就直接返回上次的结论。
     *
     * <p>★ 床数一变就做两件事（都只在那一次发生）：
     * <ol>
     *   <li>把新床数写进方块数据（诊断/重启后对照用）；</li>
     *   <li><b>本轮计时归零</b> —— 刚放下床不会立刻蹦出一点 POWER，
     *       拆掉床也不会留下"欠着的一轮"。</li>
     * </ol>
     *
     * <p>⚠ 本方法<b>有副作用</b>（会写方块数据、会重置计时），所以只给 ticker 用；
     * "只看一眼现在几张床"请用无副作用的 {@link #countBeds(Location)}（命令走的是那条）。
     */
    public int bedCount(Location loc, long now) {
        Location n = TouhouData.norm(loc);
        if (n == null) {
            return 0;
        }
        BedScan prev = BED_CACHE.get(n);
        long throttle = Math.max(1, configuredBedScanMillis());
        if (prev != null && now - prev.at() < throttle) {
            return prev.beds();
        }
        int beds = countBeds(n);
        BED_CACHE.put(n, new BedScan(beds, now));
        if (prev == null || prev.beds() != beds) {
            TouhouData.setLong(n, KEY_BEDS, beds);
            TouhouData.setLong(n, KEY_LAST_PROD, now);
            TouhouData.setLong(n, KEY_SINCE, now);
            TouhouData.setLong(n, KEY_CYCLE, 0L);
        }
        return beds;
    }

    /**
     * 一轮产出需要多少毫秒 —— {@code 基准间隔 ÷ 床数}。
     *
     * @return 床数为 0 时返回 -1（调用方据此不产出）
     */
    public long intervalMillis(int beds) {
        if (beds <= 0) {
            return -1L;
        }
        long base = Math.max(1, configuredBaseSeconds()) * 1000L;
        // 面数最多 4，再 clamp 一次纯属防御（例如将来有人把"面"改成别的判据）
        return Math.max(1L, base / Math.min(HORIZONTAL.length, beds));
    }

    // ------------------------------------------------------------------ 配置读取（一律带兜底）

    private int configuredBaseSeconds() {
        Integer v = baseIntervalSeconds == null ? null : baseIntervalSeconds.getValue();
        return v == null ? DEFAULT_BASE_SECONDS : v;
    }

    private int configuredPerCycle() {
        Integer v = powerPerCycle == null ? null : powerPerCycle.getValue();
        return v == null ? DEFAULT_PER_CYCLE : v;
    }

    private int configuredBedScanMillis() {
        Integer v = bedScanIntervalMillis == null ? null : bedScanIntervalMillis.getValue();
        return v == null ? DEFAULT_BED_SCAN_MILLIS : v;
    }

    // ------------------------------------------------------------------ 诊断

    /**
     * 供 {@code /touhou dreamcatcher <x> <y> <z>} 无头验证。
     *
     * <p>需求要求的四件事都在里面：<b>四周床的数量 / 当前自身 POWER / 所在网络的 POWER 总量 /
     * 产出速率（POWER/秒）</b>；另外多给三行"为什么是这个速率"，方便不进游戏就能核对：
     * 本轮计时、下一轮还差多久、累计产出与<b>实测</b>速率。
     *
     * <p>★ 床数是<b>现场重数</b>的（直接走 {@link #countBeds(Location)}，绕开 ticker 的节流缓存，
     * 也<b>不</b>碰计时）：命令是玩家主动敲的，看到的一定得是此刻地面上的真实情况，
     * 而且"看一眼"不应该产生任何副作用。
     */
    public List<String> describe(Location rawLoc) {
        List<String> out = new ArrayList<>();
        Location loc = TouhouData.norm(rawLoc);
        if (loc == null) {
            out.add("位置无效");
            return out;
        }
        long now = System.currentTimeMillis();
        long cap = powerCapacity(loc);
        long own = powerCharge(loc);
        int beds = countBeds(loc);
        long interval = intervalMillis(beds);
        long produced = TouhouData.getLong(loc, KEY_PRODUCED, 0L);
        long cycle = TouhouData.getLong(loc, KEY_CYCLE, 0L);
        long since = TouhouData.getLong(loc, KEY_SINCE, -1L);
        long last = TouhouData.getLong(loc, KEY_LAST_PROD, -1L);

        out.add("幻梦捕捉器 @ " + TouhouData.xyz(loc) + "  世界=" + loc.getWorld().getName());
        out.add("  节点类型       = " + powerType() + "（只捐不取：产出的电优先流向储能点）");
        out.add("  四周床数       = " + beds + " / " + HORIZONTAL.length + "  " + faceSummary(loc));
        out.add("  自身 POWER     = " + own + " / " + cap);
        if (beds <= 0 || interval <= 0) {
            out.add("  产出速率       = 0 POWER/秒（四周没有床 ⇒ 完全不产出）");
        } else {
            out.add("  产出速率       = " + String.format("%.3f", 1000.0 / interval) + " POWER/秒"
                    + "  = 1 POWER / " + String.format("%.2f", interval / 1000.0) + " 秒"
                    + "（基准 " + configuredBaseSeconds() + " 秒 ÷ " + beds + " 张床）");
            if (last < 0) {
                out.add("  本轮计时       = 尚未起表（下一 tick 开始计时）");
            } else {
                long elapsed = Math.max(0L, now - last);
                long remain = Math.max(0L, interval - elapsed);
                out.add("  本轮计时       = 距上次产出 " + String.format("%.2f", elapsed / 1000.0)
                        + " 秒 / 共 " + String.format("%.2f", interval / 1000.0)
                        + " 秒，下一轮还需 " + String.format("%.2f", remain / 1000.0) + " 秒");
            }
        }
        out.add("  累计产出       = " + produced + " POWER" + cycleLine(cycle, since, now));
        PowerNetwork net = PowerNetworkManager.getNetwork(loc);
        if (net == null) {
            out.add("  所在网络       = 未接入任何网络（本机是孤立方块）");
            out.add("  网络 POWER 总量 = 0 / 0（自己那 " + own + " POWER 就在上面一行）");
        } else {
            PowerNetwork.Totals t = net.measure();
            out.add("  所在网络       = #" + net.networkId() + " 节点 " + net.size()
                    + "（储能点 " + t.storages() + " / 发电机 " + t.generators() + "）");
            out.add("  网络 POWER 总量 = " + t.charge() + " / " + t.capacity()
                    + "（现算，含发电机自身缓冲）");
        }
        out.add("  节流           = 每 " + configuredBedScanMillis() + " 毫秒重数一次四周的床"
                + "（上一次机器统计 " + TouhouData.getLong(loc, KEY_BEDS, -1L) + " 张）");
        out.add("  区块           = " + chunkLine(loc));
        return out;
    }

    /** 四面明细：{@code [东=床 南=空 西=床 北=空]}。 */
    private String faceSummary(Location loc) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < HORIZONTAL.length; i++) {
            boolean bed = Tag.BEDS.isTagged(loc.getBlock().getRelative(HORIZONTAL[i]).getType());
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(HORIZONTAL_NAMES[i]).append('=').append(bed ? "床" : "空");
        }
        return sb.append(']').toString();
    }

    /** 本机所在区块的加载状态 —— <b>纯诊断</b>：床的判定从不看它（见 {@link #countBeds}）。 */
    private String chunkLine(Location loc) {
        int cx = loc.getBlockX() >> 4;
        int cz = loc.getBlockZ() >> 4;
        return "chunk(" + cx + "," + cz + ") 服务端判定 加载="
                + (loc.getWorld().isChunkLoaded(cx, cz) ? "是" : "否");
    }

    /** "（本轮 X POWER / Y 秒 ⇒ 实测 Z POWER/秒）"——没有起点时返回空串。 */
    private String cycleLine(long cycle, long since, long now) {
        if (since <= 0 || now <= since) {
            return "";
        }
        double seconds = (now - since) / 1000.0;
        if (seconds < 0.001) {
            return "";
        }
        return "（本轮 " + cycle + " POWER / " + String.format("%.2f", seconds)
                + " 秒 ⇒ 实测 " + String.format("%.3f", cycle / seconds) + " POWER/秒）";
    }
}
