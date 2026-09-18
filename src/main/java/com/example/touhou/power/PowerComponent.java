package com.example.touhou.power;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;

/**
 * POWER 网络节点契约。
 *
 * <p>与原生 {@code EnergyNetComponent} 的区别：
 * <ul>
 *   <li>电量用 <b>long</b>（原生 {@code getCharge} 是 int，大容量会溢出）；</li>
 *   <li>节点类型由附属自己定义，不需要改本体；</li>
 *   <li><b>不要求</b>有"调节器"才能组网 —— 任意两个相邻的 POWER 方块自动连通。</li>
 * </ul>
 */
public interface PowerComponent {

    /** 节点种类。 */
    enum NodeType {
        /** 集成核心：网络锚点，同时把 POWER 网络与原生电网桥接。 */
        INTEGRATED_CORE,
        /** 中继器：导体，并额外提供"半径跳接"（等价于原生调节器的 range）。 */
        REPEATER,
        /** 存储单元：长期储能。 */
        STORAGE,
        /**
         * 产能设备（发电机）：<b>只捐不取</b>。
         *
         * <p>★ 这是本模组第一类"产电"节点（2026-09-20 新增，首台设备是幻梦捕捉器）。
         * 之所以要单列一种类型，是因为 {@link PowerNetwork#settle} 的原有语义是
         * <b>按容量比例均衡</b>：所有非导体节点一起分池子里的电。发电机如果也参与均衡，
         * 它就会按自己的缓冲容量分走一份 —— 那是"存电"而不是"产电"，
         * 玩家看到的会是"机器自己越攒越多、电网却没电"。
         *
         * <p>约定（{@code settle} 逐条实现）：
         * <ol>
         *   <li>电量<b>计入</b>网络总量（等于"捐出去"）；</li>
         *   <li>容量<b>不计入</b>均衡基数、也<b>不进</b>均衡名单（不按比例分电）；</li>
         *   <li>结算后自身清零；只有当储能点全都装不下（含"网络里只有发电机自己"）时，
         *       多出来的那部分才回流到它自己的缓冲里 —— 一滴都不会凭空消失。</li>
         * </ol>
         *
         * <p>⚠ 旧存档里的节点类型只有前三种，新增本类型<b>不改变</b>它们的任何行为：
         * 没有发电机时 {@code settle} 的计算路径与改动前逐字等价。
         */
        GENERATOR
    }

    NodeType powerType();

    /** 当前电量（long）。 */
    long powerCharge(Location loc);

    /** 写入电量；实现方自行 clamp 到 [0, powerCapacity(loc)]。 */
    void powerSetCharge(Location loc, long charge);

    /** 额定容量（long）。导体为 0。 */
    long powerCapacity(Location loc);

    /** 参与"按容量比例均衡"的权重容量；默认等于 {@link #powerCapacity}。 */
    default long powerBalanceCapacity(Location loc) {
        return powerCapacity(loc);
    }

    /**
     * 半径跳接：本节点能把多远范围内的 POWER 方块直接连进同一张网。
     *
     * <p>返回 &gt; 0 的节点是「跳接源」：中继器（{@code jump-range}，默认 7）。
     * 存储单元等纯导体返回 0。集成核心的 {@code range} 默认也是 0
     * （它一度是 7，导致未连接的核心被误判同网，已改，见 {@code PowerIntegratedCore#range}）。
     *
     * <h2>★★ 这里的「半径」= <b>切比雪夫距离</b>（立方体），比原生 Slimefun 宽得多</h2>
     * 实测反编译 {@code io.github.thebusybiscuit.slimefun4.api.network.Network}
     * （Slimefun-2026.07），原生的 range 语义是：
     * <pre>
     *   discoverNeighbors(l) 沿 <b>6 个轴向各直走 range 格</b>
     *   （discoverNeighbors(l, ±1,0,0 / 0,±1,0 / 0,0,±1)，逐格 addLocationToNetwork）
     * </pre>
     * 也就是一个<b>十字形</b>，而且只有 REGULATOR / CONNECTOR 会扩散、且链式传递。
     * 三种常见度量在 {@code r = 7} 时能覆盖的格子数：
     * <table border="1">
     *   <caption>r=7 的覆盖规模</caption>
     *   <tr><th>度量</th><th>形状</th><th>格数</th></tr>
     *   <tr><td>原生 Slimefun</td><td>6 条轴向射线（十字）</td><td><b>42</b></td></tr>
     *   <tr><td>曼哈顿 |dx|+|dy|+|dz| ≤ r</td><td>正八面体</td><td>~575</td></tr>
     *   <tr><td><b>本模组：切比雪夫 max(|dx|,|dy|,|dz|) ≤ r</b></td><td>立方体</td><td><b>3374</b></td></tr>
     *   <tr><td>欧几里得 distance ≤ r</td><td>球</td><td>~1436</td></tr>
     * </table>
     * <b>本模组用的是最大的那个 —— 比原生宽约 80 倍。</b>这是<b>刻意</b>的选择：
     * 「半径 7 格」对人直觉上就该是"周围一圈都算"，而不是"只能沿直线拉"；
     * 斜向摆的中继器连不上会非常反直觉。代价是建网时每个跳接源要做 15³ 次方块查询
     * （3374 次，见 {@code PowerRepeater} 的性能提醒）。
     *
     * <p>⚠ 写这句话是为了防止后来人（包括我自己）看到「7、与原生一致」就以为行为等价 ——
     * 语义一致、<b>覆盖范围差 80 倍</b>。
     *
     * <p>跳接是<b>双向</b>的：A 在 B 的半径内 ⇒ A、B 同网，无论 BFS 从哪边开始
     * （实现见 {@code PowerNetworkManager#build} 的 ② 正向与 ③ 反向扫描）。
     */
    default int powerJumpRange() {
        return 0;
    }

    /** 取某位置的 POWER 节点；不是则返回 null。 */
    static PowerComponent at(Location loc) {
        if (loc == null) {
            return null;
        }
        String id = BlockStorage.checkID(loc);
        if (id == null) {
            return null;
        }
        SlimefunItem item = SlimefunItem.getById(id);
        return item instanceof PowerComponent pc ? pc : null;
    }
}
