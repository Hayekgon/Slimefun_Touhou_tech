package com.example.touhou.core;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反应堆的附加火焰粒子特效。
 *
 * <p>两种触发：
 * <ol>
 *   <li><b>持续</b>：机器处于 <b>空闲中 / 进程运行中</b> 时，每 {@code interval-ticks} 个 tick
 *       在以核心为中心、半径 {@code inner-radius}~{@code outer-radius} 格的<b>球壳</b>内
 *       随机位置生成 {@code amount} 颗火焰粒子。
 *       <b>未激活</b>时完全不生成（spec 要求）。</li>
 *   <li><b>完成爆发</b>：每次燃料进程结束的瞬间，立刻在同一个球壳内随机撒
 *       {@code completion-burst} 颗。</li>
 * </ol>
 *
 * <p>★ 为什么球壳要"逐颗"生成而不能用 {@code spawnParticle(..., count, ox, oy, oz, extra)}：
 * 那个重载的偏移量是<b>立方体</b>范围，会把粒子撒成方块形状，一眼假。
 * 想要球壳只能一颗一颗算坐标。
 *
 * <p>★ 半径取样的两个细节（都关系到"看起来是不是一个壳"）：
 * <ul>
 *   <li>要在球壳<b>体积</b>上均匀，半径必须按 ∛ 分布：
 *       {@code r = cbrt(u*(R³-r³) + r³)}，u∈[0,1)。
 *       直接对 r 均匀取样会让粒子<b>贴着内壁</b>（体积密度 ∝ 1/r²），中间看着是空的。</li>
 *   <li>方向用 {@code cosPhi} 均匀取样（而不是均匀取 φ），否则两极会堆积。</li>
 * </ul>
 *
 * <p>★ 计数器放在内存里（{@link #TICKS}）而不是方块数据里：这是纯表现层，
 * 每次 tick 写方块数据既没必要、又会让 Slimefun 的异步存盘变忙。
 *
 * <p>⚠ 只应在主线程调用（BlockTicker 就是主线程；{@code spawnParticle} 也必须在主线程）。
 */
public final class ReactorParticles {

    /** 每台机器的间隔计数器（位置 -> 已经过的 tick 数）。 */
    private static final Map<Location, Integer> TICKS = new ConcurrentHashMap<>();

    private ReactorParticles() {
    }

    /**
     * 持续粒子：由 BlockTicker 每 tick 调用一次，内部自己按间隔节流。
     *
     * @param running 机器是否处于"空闲中 / 运行中"（false = 未激活，完全不生成）
     */
    public static void tick(Location core, boolean running) {
        AddonConfig cfg = ReactorManager.config();
        if (!cfg.particleEnabled || !running) {
            TICKS.remove(core);
            return;
        }
        if (!ReactorManager.isParticlesOn(core)) {
            TICKS.remove(core);
            return;
        }
        int elapsed = TICKS.merge(core, 1, Integer::sum);
        if (elapsed < cfg.particleIntervalTicks) {
            return;
        }
        TICKS.put(core, 0);
        spawnInShell(core, cfg.particleInnerRadius, cfg.particleOuterRadius, cfg.particleAmount);
    }

    /** 进程结束时的爆发。 */
    public static void burstOnComplete(Location core) {
        AddonConfig cfg = ReactorManager.config();
        if (!cfg.particleEnabled || !ReactorManager.isParticlesOn(core)) {
            return;
        }
        spawnInShell(core, cfg.particleInnerRadius, cfg.particleOuterRadius,
                cfg.particleCompletionBurst);
    }

    /** 忘记某台机器的计数器（拆方块时调用）。 */
    public static void forget(Location core) {
        TICKS.remove(core);
    }

    /**
     * 在球壳（半径 {@code inner} ~ {@code outer}）内随机撒 {@code count} 颗火焰粒子。
     *
     * <p>{@code inner <= 0} 时退化成实心球。
     */
    private static void spawnInShell(Location core, double inner, double outer, int count) {
        if (count <= 0 || core.getWorld() == null) {
            return;
        }
        double r1 = Math.max(0.0D, Math.min(inner, outer));
        double r2 = Math.max(r1, outer);
        World world = core.getWorld();
        // 以方块中心为球心，看起来更居中
        double cx = core.getBlockX() + 0.5D;
        double cy = core.getBlockY() + 0.5D;
        double cz = core.getBlockZ() + 0.5D;
        java.util.Random random = java.util.concurrent.ThreadLocalRandom.current();

        // 体积均匀取样：r³ 在 [r1³, r2³] 上线性
        double cube1 = r1 * r1 * r1;
        double span = r2 * r2 * r2 - cube1;

        for (int i = 0; i < count; i++) {
            // 球面均匀方向
            double theta = random.nextDouble() * Math.PI * 2.0D;
            double cosPhi = random.nextDouble() * 2.0D - 1.0D;
            double sinPhi = Math.sqrt(Math.max(0.0D, 1.0D - cosPhi * cosPhi));
            // 体积均匀的半径
            double r = Math.cbrt(cube1 + random.nextDouble() * span);

            double x = cx + r * sinPhi * Math.cos(theta);
            double y = cy + r * cosPhi;
            double z = cz + r * sinPhi * Math.sin(theta);

            // count=1 + 零偏移：精确落在我们算好的点上
            world.spawnParticle(Particle.FLAME, x, y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
