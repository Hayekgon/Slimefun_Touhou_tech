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
 *   <li><b>产能设备只捐不取</b>：{@code NodeType.GENERATOR} 的电量计入池子，但它的容量
 *       既不参与均衡、也不算进均衡基数 —— 所以"产出的电优先流向储能点"，
 *       只有储能点全装不下时才会回流到发电机自带的缓冲里（详见 {@link #settle()}）。</li>
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
        //
        // ★ 发电机（NodeType.GENERATOR）在这里被【单独挑出来】，它只捐不取：
        //   电量计入池子（= 把自己发的电交出来），但【容量不计入 totalCap】、
        //   也【不进 balance 均衡名单】—— 均衡的比例基数因此只剩"真正的储能点"，
        //   发电机不会靠自己的缓冲去分走一份电。
        //   ⚠ 没有发电机时，下面这条路径与改动前<b>逐字等价</b>（generators 为空）：
        //     totalCap / totalCharge / balance / caps 全都与原来一模一样。
        long totalCap = 0, totalCharge = 0;
        List<Location> balance = new ArrayList<>();
        List<Long> caps = new ArrayList<>();
        List<Location> generators = new ArrayList<>();
        for (Location loc : ready) {
            PowerComponent pc = PowerComponent.at(loc);
            if (pc == null || pc.powerType() == PowerComponent.NodeType.REPEATER) {
                continue;   // 导体不参与均衡
            }
            long cap = Math.max(0, pc.powerBalanceCapacity(loc));
            long chg = Math.max(0, Math.min(pc.powerCharge(loc), cap));
            if (pc.powerType() == PowerComponent.NodeType.GENERATOR) {
                generators.add(loc);
                totalCharge += chg;   // 只捐：把电放进池子
                continue;             // 不取：容量不进 totalCap、位置不进 balance
            }
            balance.add(loc);
            caps.add(cap);
            totalCap += cap;
            totalCharge += chg;
        }

        if (totalCap <= 0) {
            // 池子里一个储能点都没有 ⇒ 发电机<b>无处可捐</b>，这一轮就不捐了：
            // 产出的电留在自己的缓冲里（这正是"发电机自带 N 点缓冲"的用途）。
            // ⚠ 这条分支下不做任何写入，所以发电机不会被清零 —— 那是刻意的。
            long genCap = 0, genCharge = 0;
            for (Location loc : generators) {
                PowerComponent pc = PowerComponent.at(loc);
                if (pc == null) {
                    continue;
                }
                long cap = Math.max(0, pc.powerCapacity(loc));
                genCap += cap;
                genCharge += Math.max(0, Math.min(pc.powerCharge(loc), cap));
            }
            lastTotalCapacity = genCap;
            lastTotalCharge = genCharge;
            notes.add(generators.isEmpty()
                    ? "网络无储能节点（只有中继器）"
                    : "网络无储能节点（只有发电机）：产出的 POWER 存进发电机自身缓冲 "
                            + genCharge + "/" + genCap);
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

        // ---- 发电机：把电推给网络之后自身清零；装不下的才回流到自己的缓冲 ----
        //
        // ★ 这就是"<b>优先对外输出</b>"的落点：总量里的发电机那部分电，会顺着上面的
        //   均衡分配落到真正的储能点上；只有当储能点全满时，多出来的才在下面退回发电机。
        //   （"网络里只有发电机自己"的情形在上面的 totalCap <= 0 分支就已经早退，
        //     所以它也不会被清零。）
        long leftover = Math.max(0, totalCharge - assigned);
        long genCap = 0, genKept = 0;
        for (Location loc : generators) {
            PowerComponent pc = PowerComponent.at(loc);
            if (pc == null) {
                continue;
            }
            long cap = Math.max(0, pc.powerCapacity(loc));
            long keep = Math.min(leftover, cap);
            leftover -= keep;
            genCap += cap;
            genKept += keep;
            if (pc.powerCharge(loc) != keep) {
                pc.powerSetCharge(loc, keep);
            }
        }

        // 实测总量 = 储能点实际放置的量 + 发电机缓冲里留下的量。
        // ⚠ 这里刻意用"刚才算出来的值"而不是再读一遍方块数据：settle 每 tick 都跑，
        //   多一轮全节点读取纯属浪费；而且数学上与读回来的一致（分配时就 clamp 过了）。
        //   ★ 也正因为按实际放置量统计，{@code lastTotalCharge <= lastTotalCapacity} 恒成立
        //     （改动前 totalCharge 的每个分量都已经 clamp 过，所以两者数值完全一致）。
        lastTotalCapacity = totalCap + genCap;
        lastTotalCharge = assigned + genKept;

        notes.add("网络 #" + id + " 节点 " + nodes.size()
                + " 储能点 " + balance.size()
                + (generators.isEmpty() ? "" : " 发电机 " + generators.size())
                + " 电量 " + lastTotalCharge + "/" + lastTotalCapacity);
        return notes;
    }

    /**
     * 现算一次"这张网此刻到底有多少电、装得下多少电"——<b>含发电机自身缓冲</b>，导体不计。
     *
     * <p>用途是<b>诊断</b>（{@code /touhou power} 与幻梦捕捉器的自检）：{@link #lastTotalCharge()}
     * 是"上一次结算时"的快照，而命令随时可能被敲，中间可能正好插着一台机器刚产出的电。
     *
     * <p>⚠ {@link #settle()} <b>不调用</b>它：那会每 tick 多读一整轮方块数据
     * （settle 已经从分配结果里算出了同样的数，见那里的注释）。
     */
    public Totals measure() {
        long charge = 0, capacity = 0;
        int storages = 0, generators = 0;
        for (Location loc : nodes) {
            PowerComponent pc = PowerComponent.at(loc);
            if (pc == null || pc.powerType() == PowerComponent.NodeType.REPEATER) {
                continue;   // 导体不存电
            }
            long cap = Math.max(0, pc.powerCapacity(loc));
            charge += Math.max(0, Math.min(pc.powerCharge(loc), cap));
            capacity += cap;
            if (pc.powerType() == PowerComponent.NodeType.GENERATOR) {
                generators++;
            } else {
                storages++;
            }
        }
        return new Totals(charge, capacity, storages, generators);
    }

    /** {@link #measure()} 的读数：网络总电量 / 总容量 / 储能点数 / 发电机数。 */
    public record Totals(long charge, long capacity, int storages, int generators) {
    }
}
