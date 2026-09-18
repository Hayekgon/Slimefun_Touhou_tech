package com.example.touhou.power;

import com.example.touhou.core.TouhouData;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;

/**
 * POWER 网络的全局管理：组网（BFS）、缓存、失效重算、tick 去重。
 *
 * <p>组网规则（这就是"优化重构"的核心）：
 * <ol>
 *   <li><b>邻接</b>：6 面相邻的 POWER 方块连通 —— 不需要任何"调节器"；</li>
 *   <li><b>中继跳接</b>：中继器额外把切比雪夫距离 ≤ {@code jump-range} 内的 POWER 方块连进来
 *       （等价于原生调节器的 range，但只在有中继器处才生效）。</li>
 * </ol>
 */
public final class PowerNetworkManager {

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    /**
     * 起点反向扫描半径（见 {@link #build(Location)} 第 ③ 步）。
     *
     * <p>必须 <b>&ge; 任何一种 POWER 方块可配置的最大跳接半径</b>，
     * 否则「半径覆盖起点」的跳接源会被漏掉。当前最大默认值是 7（中继器 {@code jump-range}
     * 与集成核心 {@code range}），这里留 1 格余量。
     */
    private static final int REVERSE_SCAN_RADIUS = 8;

    /** 归一化位置 → 所属网络。 */
    private static final Map<Location, PowerNetwork> CACHE = new HashMap<>();

    /**
     * 已占用的电网编号。
     *
     * <p>编号<b>可回收复用</b>：网络被拆除 / 失效重算时编号回到池子里，
     * 下一次 {@link #allocateId()} 会优先补最小的空缺。
     * 这样长时间运行也不会把编号堆到很大。
     */
    private static final java.util.TreeSet<Long> USED_IDS = new java.util.TreeSet<>();

    /** 集成核心的"放置顺序"键（毫秒时间戳，跨重启也单调）。 */
    public static final String KEY_ORDER = "touhou:power-order";

    private PowerNetworkManager() {
    }

    /** 分配一个最小的空闲编号。 */
    static synchronized long allocateId() {
        long id = 1L;
        while (USED_IDS.contains(id)) {
            id++;
        }
        USED_IDS.add(id);
        return id;
    }

    /** 归还编号。 */
    static synchronized void freeId(long id) {
        if (id > 0) {
            USED_IDS.remove(id);
        }
    }

    /** 当前占用中的编号数（诊断用）。 */
    public static synchronized int usedIdCount() {
        return USED_IDS.size();
    }

    /**
     * 原生 EnergyNet.getNetworkFromLocationOrCreate(Location) 的对应物：
     * 缓存命中就直接返回，否则从该位置 BFS 现算一个并写进缓存。
     */
    public static PowerNetwork getNetworkFromLocationOrCreate(Location rawLoc) {
        return getNetwork(rawLoc);
    }

    /** 只结算、不刷悬浮字（诊断用）。节点 ticker 走 PowerNetwork.tick(Block, SlimefunBlockData)。 */
    public static void requestTick(Location rawLoc) {
        Location loc = TouhouData.norm(rawLoc);
        if (loc == null) {
            return;
        }
        PowerNetwork net = getNetwork(loc);
        if (net != null && net.claimTick(Bukkit.getCurrentTick())) {
            net.tick();
        }
    }

    /** 取该位置所属的网络；不在缓存里就现算一次。 */
    public static PowerNetwork getNetwork(Location rawLoc) {
        Location loc = TouhouData.norm(rawLoc);
        if (loc == null || PowerComponent.at(loc) == null) {
            return null;
        }
        PowerNetwork cached = CACHE.get(loc);
        if (cached != null) {
            return cached;
        }
        PowerNetwork net = build(loc);
        for (Location n : net.nodes()) {
            CACHE.put(n, net);
        }
        return net;
    }

    /** 方块增删后调用：把该位置（及 6 个邻居）所属网络从缓存里清掉，下次自动重算。 */
    public static void invalidate(Location rawLoc) {
        Location loc = TouhouData.norm(rawLoc);
        if (loc == null) {
            return;
        }
        dropNetwork(loc);
        for (BlockFace f : FACES) {
            dropNetwork(TouhouData.norm(loc.getBlock().getRelative(f).getLocation()));
        }
    }

    private static void dropNetwork(Location loc) {
        if (loc == null) {
            return;
        }
        PowerNetwork net = CACHE.get(loc);
        if (net == null) {
            return;
        }
        for (Location n : net.nodes()) {
            CACHE.remove(n);
        }
        // ★ 归还编号：这样以后新放置的集成核心能补上这个空缺（编号复用）
        freeId(net.networkId());
    }

    /**
     * 网络里的集成核心，按"放置顺序"升序（先放的在前）。
     *
     * <p>顺序取自方块数据 {@link #KEY_ORDER}（放置时写入的时间戳）——
     * 不能用网络节点的遍历顺序，那个取决于从哪个方块开始 BFS，不稳定。
     */
    public static java.util.List<Location> coresByOrder(PowerNetwork net) {
        java.util.List<Location> cores = new ArrayList<>();
        if (net == null) {
            return cores;
        }
        for (Location n : net.nodes()) {
            PowerComponent pc = PowerComponent.at(n);
            if (pc != null && pc.powerType() == PowerComponent.NodeType.INTEGRATED_CORE) {
                cores.add(n);
            }
        }
        cores.sort(java.util.Comparator.comparingLong(
                l -> TouhouData.getLong(l, KEY_ORDER, Long.MAX_VALUE)));
        return cores;
    }

    /** 供诊断输出：给该位置所属网络的所有核心写上/刷新放置顺序（放置时调用）。 */
    public static void stampCoreOrder(Location rawLoc) {
        Location loc = TouhouData.norm(rawLoc);
        if (loc != null && TouhouData.getLong(loc, KEY_ORDER, -1L) < 0) {
            TouhouData.setLong(loc, KEY_ORDER, System.currentTimeMillis());
        }
    }

    /** 从起点 BFS 出一个连通分量。 */
    private static PowerNetwork build(Location start) {
        PowerNetwork net = new PowerNetwork();
        net.assignId(allocateId());
        Set<Location> seen = new HashSet<>();
        Deque<Location> queue = new ArrayDeque<>();

        seen.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            Location cur = queue.poll();
            net.nodes().add(cur);

            List<Location> next = new ArrayList<>();
            // ① 6 面邻接
            for (BlockFace f : FACES) {
                next.add(TouhouData.norm(cur.getBlock().getRelative(f).getLocation()));
            }
            // ② 半径跳接（正向）：我是跳接源，把半径内的方块连进来
            PowerComponent pc = PowerComponent.at(cur);
            int r = pc == null ? 0 : Math.max(0, pc.powerJumpRange());
            if (r > 0) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dy = -r; dy <= r; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            next.add(TouhouData.norm(cur.clone().add(dx, dy, dz)));
                        }
                    }
                }
            }

            // ③ 半径跳接（反向）：把我拉进「半径覆盖我」的跳接源里。
            //
            // ★ 没有这一步，跳接边就只能在「BFS 恰好先访问到跳接源」时才生效：
            //   从跳接源出发能连上它半径内的方块，但从那些方块出发却连不回跳接源。
            //   后果是同一堆方块会因为 BFS 起点不同而算出完全不同的网络 ——
            //   实测一组 35 个 POWER 方块：从中继器问得 35 个节点，
            //   从核心问得 28 个，从孤立核心问得 1 个，于是多核心提示永远不触发。
            //
            //   只在起点做一次反向扫描即可：一旦跳接源被拉进来，
            //   它的正向扫描（②）就会把整个半径覆盖到，BFS 自然收敛到同一张网。
            if (cur.equals(start)) {
                for (int dx = -REVERSE_SCAN_RADIUS; dx <= REVERSE_SCAN_RADIUS; dx++) {
                    for (int dy = -REVERSE_SCAN_RADIUS; dy <= REVERSE_SCAN_RADIUS; dy++) {
                        for (int dz = -REVERSE_SCAN_RADIUS; dz <= REVERSE_SCAN_RADIUS; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) {
                                continue;
                            }
                            int dist = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
                            Location cand = TouhouData.norm(start.clone().add(dx, dy, dz));
                            if (cand == null || seen.contains(cand)) {
                                continue;
                            }
                            PowerComponent cpc = PowerComponent.at(cand);
                            if (cpc != null && cpc.powerJumpRange() >= dist) {
                                next.add(cand);
                            }
                        }
                    }
                }
            }

            for (Location cand : next) {
                if (cand == null || seen.contains(cand)) {
                    continue;
                }
                if (PowerComponent.at(cand) != null) {
                    seen.add(cand);
                    queue.add(cand);
                } else if (TouhouData.isReady(cand)) {
                    // 数据已就绪，且确认不是 POWER 方块 —— 可以安全拉黑，避免重复探测。
                    seen.add(cand);
                }
                // else：该位置的区块数据还在异步加载（checkID 暂时返回 null），
                //       这次不下结论、也不拉黑，留给下一次 BFS 重新判定。
                //       若在这里直接 seen.add，会导致「暂时读不到」的方块被永久排除在本网之外，
                //       并把残缺的拓扑冻进 CACHE —— 表现为网络节点数无故偏少。
            }
        }
        return net;
    }

    // ------------------------------------------------------------------ 诊断

    public static int cachedNodeCount() {
        return CACHE.size();
    }

    public static int cachedNetworkCount() {
        return new HashSet<>(CACHE.values()).size();
    }

    /** 供 /touhou power 无头验证。 */
    public static List<String> describe(Location rawLoc) {
        List<String> out = new ArrayList<>();
        Location loc = TouhouData.norm(rawLoc);
        if (loc == null) {
            out.add("位置无效");
            return out;
        }
        PowerComponent self = PowerComponent.at(loc);
        out.add("该位置: " + TouhouData.xyz(loc)
                + (self == null ? " 不是 POWER 方块" : " 类型=" + self.powerType()));
        if (self == null) {
            return out;
        }
        PowerNetwork net = getNetwork(loc);
        if (net == null) {
            out.add("未组成网络");
            return out;
        }
        out.add("网络 #" + net.networkId() + " 节点数=" + net.size()
                + " 缓存节点=" + cachedNodeCount() + " 缓存网络=" + cachedNetworkCount());
        int core = 0, rep = 0, sto = 0;
        for (Location n : net.nodes()) {
            PowerComponent pc = PowerComponent.at(n);
            if (pc == null) {
                continue;
            }
            switch (pc.powerType()) {
                case INTEGRATED_CORE -> core++;
                case REPEATER -> rep++;
                case STORAGE -> sto++;
            }
        }
        out.add("  集成核心=" + core + " 中继器=" + rep + " 存储单元=" + sto);
        out.add("  上次统计 电量=" + net.lastTotalCharge() + "/" + net.lastTotalCapacity());
        return out;
    }

    /** 强制全量重算（诊断/修复用）。 */
    public static int rebuildAll() {
        int n = CACHE.size();
        CACHE.clear();
        return n;
    }
}
