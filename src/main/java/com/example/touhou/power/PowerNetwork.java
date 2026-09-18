package com.example.touhou.power;

import com.example.touhou.core.TouhouData;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;

/**
 * 一个连通分量 = 一个 POWER 网络。
 *
 * <h2>与原生 {@code EnergyNet} 的差别（本类实现的"优化重构"）</h2>
 * <ol>
 *   <li><b>不需要调节器</b>：原生 {@code Network.regulator} 是唯一锚点，没有它整个网络不存在。
 *       这里任何 POWER 方块都是节点，靠<b>邻接</b>组网。</li>
 *   <li><b>不再是硬半径</b>：邻接是真实的方块连接关系；只有<b>中继器</b>才提供半径跳接
 *       （保留原生"远程连接"的能力，但不再是组网的前提）。</li>
 *   <li><b>long 精度</b>：全程 long，不溢出。</li>
 *   <li><b>显式 tick 顺序</b>：见 {@link #tick()} —— 先并网结算，再按容量比例均衡。
 *       原生是"谁先被 tick 谁拿电"，结果不可推理。</li>
 *   <li><b>均衡而不是抢占</b>：总电量按容量比例重分配，不会出现"靠近发电机的电容先满、远处的空着"。</li>
 * </ol>
 */
public class PowerNetwork implements HologramOwner {

    /** 编号由 {@link PowerNetworkManager#allocateId()} 分配，可回收复用。 */
    private long id = -1L;
    /** 全部节点（已 {@link TouhouData#norm} 归一化）。 */
    private final Set<Location> nodes = new LinkedHashSet<>();

    private int tickedAt = -1;
    private long lastTotalCharge = -1;
    private long lastTotalCapacity = -1;

    /** 内部编号（long，可回收复用）。 */
    public long networkId() {
        return id;
    }

    /**
     * {@link HologramOwner} 继承自 {@code ItemAttribute} 的抽象契约，返回类型必须是 {@code String}。
     * 原生 {@code EnergyNet} 也因此有一个 {@code public String getId()}。
     */
    @Override
    public String getId() {
        return "POWER_" + id;
    }

    void assignId(long newId) {
        this.id = newId;
    }

    public Set<Location> nodes() {
        return nodes;
    }

    public int size() {
        return nodes.size();
    }

    /** 本 tick 是否已经跑过（同 tick 内多个节点的 ticker 都会来请求）。 */
    boolean claimTick(int currentTick) {
        if (tickedAt == currentTick) {
            return false;
        }
        tickedAt = currentTick;
        return true;
    }

    public long lastTotalCharge() {
        return lastTotalCharge;
    }

    public long lastTotalCapacity() {
        return lastTotalCapacity;
    }

    /**
     * 原生 {@code EnergyNet.tick(Block, SlimefunBlockData)} 的对应物。
     *
     * <p>原生把「驱动网络」和「刷新悬浮字」合在同一个方法里：<b>谁来 tick，就顺手刷新谁头顶的字</b>。
     * 而且 {@code HologramOwner} 是实现在 <b>网络</b> 上（{@code EnergyNet implements HologramOwner}），
     * 不是实现在控制器上——控制器只是个哑触发器。这里照搬这个结构。
     */
    public List<String> tick(Block b, SlimefunBlockData data) {
        List<String> notes = claimTick(Bukkit.getCurrentTick()) ? settle() : Collections.emptyList();
        updateHologram(b, data);
        return notes;
    }

    /** 只结算、不碰悬浮字（诊断 / 没有方块上下文的调用方）。 */
    public List<String> tick() {
        return settle();
    }

    /**
     * 原生 {@code EnergyNet#updateHologram(SlimefunBlockData, double, double)} 的对应物。
     *
     * <p>原生最后调的是三参 {@code updateHologram(Block, String, Supplier<Boolean>)}，
     * 那个 {@code Supplier} 传的是 {@code blockData::isPendingRemove}——即「方块已经在移除队列里
     * 就放弃这次刷新」。这里用同一个保险。
     */
    private void updateHologram(Block b, SlimefunBlockData data) {
        if (b == null || data == null) {
            return;
        }
        Location loc = TouhouData.norm(b.getLocation());
        if (!(loc != null && PowerComponent.at(loc) instanceof PowerIntegratedCore core)) {
            return;   // 只有集成核心头顶挂悬浮字，中继器 / 存储单元没有
        }
        if (!Boolean.TRUE.equals(core.hologramEnabled.getValue())) {
            return;
        }
        updateHologram(b, core.renderText(this, loc), data::isPendingRemove);
    }

    private List<String> settle() {
        List<String> notes = new ArrayList<>();

        // ---- 收集就绪节点 ----
        List<Location> ready = new ArrayList<>();
        int pending = 0;
        for (Location loc : nodes) {
            if (!TouhouData.isReady(loc)) {
                pending++;
                continue;
            }
            if (PowerComponent.at(loc) == null) {
                continue;   // 方块已被替换，交给失效重算处理
            }
            ready.add(loc);
        }
        if (pending > 0) {
            notes.add("等待区块数据加载: " + pending + " 个节点，本 tick 跳过");
            return notes;
        }

        // ---- 汇总容量与电量 ----
        long totalCap = 0, totalCharge = 0;
        List<Location> balance = new ArrayList<>();
        List<Long> caps = new ArrayList<>();
        for (Location loc : ready) {
            PowerComponent pc = PowerComponent.at(loc);
            if (pc == null || pc.powerType() == PowerComponent.NodeType.REPEATER) {
                continue;   // 导体不参与均衡
            }
            long cap = Math.max(0, pc.powerBalanceCapacity(loc));
            long chg = Math.max(0, Math.min(pc.powerCharge(loc), cap));
            balance.add(loc);
            caps.add(cap);
            totalCap += cap;
            totalCharge += chg;
        }

        lastTotalCapacity = totalCap;
        lastTotalCharge = totalCharge;
        if (totalCap <= 0) {
            notes.add("网络无储能节点（只有中继器）");
            return notes;
        }

        // ---- ★ 核心优化：按容量比例均衡，而不是"谁先 tick 谁拿" ----
        long assigned = 0;
        for (int i = 0; i < balance.size(); i++) {
            Location loc = balance.get(i);
            long cap = caps.get(i);
            long want;
            if (i == balance.size() - 1) {
                want = totalCharge - assigned;      // 最后一个吸收取整余数
            } else {
                want = totalCap == 0 ? 0 : totalCharge * cap / totalCap;
            }
            want = Math.max(0, Math.min(want, cap));
            assigned += want;

            PowerComponent pc = PowerComponent.at(loc);
            if (pc != null && pc.powerCharge(loc) != want) {
                pc.powerSetCharge(loc, want);
            }
        }

        notes.add("网络 #" + id + " 节点 " + nodes.size()
                + " 储能点 " + balance.size()
                + " 电量 " + totalCharge + "/" + totalCap);
        return notes;
    }
}
