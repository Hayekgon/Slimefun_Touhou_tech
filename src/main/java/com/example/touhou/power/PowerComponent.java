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
        STORAGE
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
     * 半径跳接（切比雪夫距离）：本节点能把多远范围内的 POWER 方块直接连进同一张网。
     *
     * <p>返回 &gt; 0 的节点是「跳接源」：中继器（{@code jump-range}，默认 7）与
     * 集成核心（{@code range}，默认 7，对齐原生 {@code EnergyRegulator} 的 range）。
     * 存储单元等纯导体返回 0。
     *
     * <p>跳接是<b>双向</b>的：A 在 B 的半径内 ⇒ A、B 同网，无论 BFS 从哪边开始。
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
