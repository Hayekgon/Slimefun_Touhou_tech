package com.example.touhou.core;

import com.example.touhou.Touhou;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.AbstractArrow.PickupStatus;
import org.bukkit.entity.Arrow;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * 杀意的百合 —— 「一支箭 → 命中点爆发（激光 + 喷泉 + 12 支追踪箭）」的符卡道具。
 *
 * <h2>名字的来历</h2>
 * 「杀意的百合」是东方 Project 里 <b>纯狐</b>（純狐 / Junko，东方绀珠传最终 BOSS）的符卡
 * 「殺意の百合」的直译；她的弹幕正是"纯粹愤怒的具象化"。
 * 英文名因此取 <b>Murderous Lily</b>，物品 id 为
 * {@code TOUHOU_PARTY_ITEM_MURDEROUS_LILY}
 * （{@code TOUHOU_"物品组PARTY_ITEM"_"英文名"}，全大写 —— 本项目 id 铁律）。
 *
 * <h2>能源：与梦想封印 集<b>同一套</b> POWER 数据（用户要求的「数据等沿用」）</h2>
 * ★ 上限 40 / 单发消耗 1 / 充能 5 每 2 秒 / 冷却 1.5 秒 / 取电半径 4 格 /
 * 提示节流 15 秒 / <b>无线充电仍是关的</b>（{@code charge-wireless=false}）——
 * 这一整套<b>没有在本类里重写</b>：声明与读取路径都在 {@link PartyItem} 基类里，
 * 本类与 {@link FantasySeal} 都继承它。设置仍然各自落在
 * {@code plugins/Slimefun/Items.yml} 的本道具 id 分节里，两件道具可以分别调参。
 * （为什么共用 key 不会互相覆盖：见 {@link PartyItem} 的类注释。）
 *
 * <h2>三段行为</h2>
 * <pre>
 *   阶段一（右键）：沿准星方向射出一支箭
 *       · 无视重力（setGravity(false)）、伤害 16（普通箭矢伤害，同 FantasySeal 那条路径）
 *       · 每 tick 伴随 5 个 FLAME 粒子
 *       · 销毁规则（★ 2026-09-21 用户口径）：
 *           ① 与发射者的距离 >= min(max-distance, 模拟距离 × 16) ⇒ 立即销毁
 *           ② 飞行时间 >= max-seconds（默认 8 秒）              ⇒ 立即销毁
 *           ③ 命中方块或实体                                    ⇒ 进入阶段二
 *         （<b>不做区块加载检测</b>：早期版本那条 {@code isChunkLoaded} 判据已被用户要求删除）
 *   阶段二（命中瞬间，以命中点为原点同时发生三件事）：
 *       1) 激光：FLAME 粒子构成，长 12 格、直径 1 格、【可被实体方块截断】；真伤 32
 *       2) 喷泉：命中点周围随机撒 300 个 FLAME 粒子，呈喷泉样式（纯视觉）
 *       3) 12 支追踪箭：喷泉样式向周围射出，每支每 tick 2 个 FLAME 粒子、
 *          追踪半径 12 格、最多存在 5 秒；伤害 12（普通箭矢伤害）
 * </pre>
 *
 * <h2>★★ 方向裁决（用户原文有歧义，这里按"外法线向外"实现）</h2>
 * 用户原文：「对于激光与喷泉方向，总是垂直于命中的方块表面（例如，击中一个方块的北面，
 * 则向南面释放激光与喷泉），如果击中的是实体，则始终向上。」
 *
 * <p>括号里的例子有两种读法，本类按<b>外法线向外</b>实现 —— 也就是
 * <b>击中北面 ⇒ 激光/喷泉朝北（背向方块、朝射手来时的方向）</b>。理由：
 * <ol>
 *   <li>若照字面"朝南"（射进方块内部），激光第 0 格就被<b>刚命中的那个方块自己</b>挡住
 *       ⇒ 长度恒为 0，与「长 12 格 + 可被物块阻挡」这条需求直接矛盾；</li>
 *   <li>「垂直于命中表面」在几何上指的就是<b>该面的法线</b>，而方块的外法线朝外
 *       （北面的外法线是北，不是南）；</li>
 *   <li>「喷泉」这个意象本就是"从被打中的那个面喷出来"。</li>
 * </ol>
 * 实现位置：{@link #laserDirection}（唯一的裁决点，一行取反即可翻案）。
 *
 * <p>实体没有"面"⇒ 按用户原文<b>始终向上</b>（{@code (0,1,0)}）。
 *
 * <h2>★ 三条伤害通道（全部是原版伤害，<b>没有真伤</b>）</h2>
 * <table border="1">
 *   <caption>同一件道具里的三条伤害通道</caption>
 *   <tr><th>来源</th><th>数值</th><th>通道</th><th>护甲/无敌帧</th><th>保护插件</th></tr>
 *   <tr><td>阶段一那支箭</td><td>16</td>
 *       <td><b>普通箭矢伤害</b>：{@code arrow.setDamage(...)} + {@code setShooter(...)}，
 *           与 {@link FantasySeal} <b>完全同一条</b>路径</td>
 *       <td>生效</td><td><b>能拦</b>（原版 {@code EntityDamageByEntityEvent}）</td></tr>
 *   <tr><td>阶段二 12 支追踪箭</td><td>12</td><td>同上（普通箭矢伤害）</td>
 *       <td>生效</td><td><b>能拦</b></td></tr>
 *   <tr><td>命中点<b>范围伤害</b></td><td>24</td>
 *       <td><b>弹射物伤害</b>：{@code Damageable#damage(24, DamageSource.ARROW)}，
 *           以命中点为球心、半径 3 格（见 {@link #damageSphere}）</td>
 *       <td>生效</td><td><b>能拦</b></td></tr>
 * </table>
 * ★ 2026-09-21 用户把激光的伤害改成"以落点为球心的 24 点弹射物伤害"，
 * 于是本道具<b>不再有任何真伤</b>：原先那套"探测事件 + {@code setHealth}"的
 * 绕过护甲实现（{@code TrueDamageProbeEvent}）已<b>整体删除</b>。
 * 三条通道全部走原版伤害，也就不存在"保护插件收不到通知"的代价。
 * 激光本身只剩几何（长度/截断）与粒子，不再自己结算伤害。
 *
 * <h2>★ 不伤害发射者（可配置，默认开）</h2>
 * {@code exclude-shooter}（默认 {@code true}）：命中点范围伤害与追踪箭的索敌
 * <b>两处都排除发射者</b>。★ 刻意<b>不</b>抄 LogiTech 方块激光那段
 * （它的 AABB 结算里没有 {@code p != shooter}，站自己激光里会被烧，见
 * {@code slimefun-laser-dev/03-reusable-patterns.md} §3.2）。
 * 队伍/队友概念 spec 没提，<b>不做</b>任何推测性实现 —— 只排除发射者本人。
 *
 * <h2>追踪表不泄漏（隐性 bug 的高发区）</h2>
 * 阶段一那支箭有<b>三条</b>终止路径，每一条都必须把
 * {@link #TRACKED_SHOTS} 里的条目摘掉：
 * <ol>
 *   <li>命中方块或实体 → {@link MurderousLilyListener} 记录命中、{@link #tickShot} 当 tick 结算；</li>
 *   <li>飞满距离上限（{@code min(max-distance, 模拟距离 × 16)}）→ 周期任务里判；</li>
 *   <li>飞行时间到（{@code max-seconds}，默认 8 秒）→ 周期任务里判
 *       （毫秒与 tick 两个口径互为保险）。</li>
 * </ol>
 * 另外 {@link MurderousLilyListener} 与 {@link #cleanupRemoved} 兜住"实体因为任何其它原因
 * 离开世界"（{@code /kill}、区块卸载导致服务端回收、插件清理）。
 * 三条路径全部汇到<b>同一个</b> {@link #finishShot}，保证只结算一次、且只摘一次表。
 */
public class MurderousLily extends PartyItem {

    // ------------------------------------------------------------------ 常量（spec 给定的刻度）

    /**
     * 阶段一那支箭的<b>基准</b>飞行速度（格/tick）。
     *
     * <p>★ 2026-09-21 用户要求「初始箭矢飞行速度提高 150%」⇒ 基准 1.25 × 2.5 = <b>3.125</b>。
     * 实现方式不是改这个基准值，而是把它乘上 {@code arrow-speed-multiplier}（默认 2.5）：
     * 基准值与 {@link FantasySeal} 保持一致，倍率可配，要调只看一个地方。
     */
    private static final float ARROW_SPEED = 1.25F;
    /** 散布必须为 0：这是一支"照准星直飞"的箭，不要随机偏。 */
    private static final float ARROW_SPREAD = 0.0F;

    /** 激光的推进步长（格）：0.25 与 {@code slimefun-laser-dev} 里那把枪同口径。 */
    private static final double STEP = 0.25D;
    /**
     * 激光可见粒子的取样间隔（格）。
     *
     * <p>★ 2026-09-21 激光直径从 1 格改成 3 格，所以环也画粗了：
     * 环上的取样点由 {@link #beamRingPoints} 按直径算（直径 3 ⇒ 3 圈 × 6 点 = 18 点/环），
     * 而环与环之间保持 {@code max(0.5, 直径/2)} 的间隔 —— 管子越粗，环越密才不像"虚线"。
     */
    private static final double BEAM_VISUAL_STEP_MIN = 0.5D;
    /** 激光每一圈上的粒子数（构成管壁的环）。 */
    private static final int BEAM_RING_POINTS = 6;
    /** 追踪箭的制导周期（tick）。 */
    private static final int TRACK_PERIOD = 2;
    /** 比例导引的两个系数（与 {@link FantasySeal} 同源：向心 0.25、惯性 0.8）。 */
    private static final double STEER_FACTOR = 0.25D;
    private static final double INERTIA_FACTOR = 0.8D;
    /**
     * 追踪箭的<b>基准</b>初速（格/tick）。
     *
     * <p>★ 2026-09-21 用户要求「衍生箭矢飞行速度提高 40%」⇒ 1.0 × 1.4 = <b>1.4</b>。
     * 同样是"基准 × 倍率（{@code tracker-speed-multiplier}，默认 1.4）"的写法。
     */
    private static final float TRACKER_SPEED = 1.0F;
    /** 追踪箭命中时喷出的喷泉粒子数（用户口径：50）。 */
    private static final int TRACKER_HIT_FOUNTAIN = 50;

    /**
     * 本次发射的<b>距离上限</b>（格）：{@code min(max-distance, 模拟距离 × 16)}。
     *
     * <p>★ 这是用户 2026-09-21 定的销毁规则之一。
     * "模拟距离 × 16" = 服务端实际会把实体发给玩家的半径（模拟距离的单位是区块，1 区块 16 格），
     * 所以取 min 之后：常规配置（模拟距离 ≥ 8）下就是 {@code max-distance}（默认 120）；
     * 服务器把模拟距离调得很小时，箭会在更近处就被销毁 —— 反正那么远的箭也没人看得见。
     *
     * @return 距离上限（格）；{@code <= 0} = 现在算不出来（发射者已不在），调用方应跳过这一条
     */
    double travelLimitFor(Shot shot, World world) {
        if (shot == null) {
            return 0.0D;
        }
        double configured = configuredMaxDistance();
        if (world == null) {
            return configured;
        }
        LivingEntity shooter = shot.shooter;
        if (shooter == null || !shooter.isValid()) {
            return 0.0D;   // 没有参照点：交给时间规则兜底
        }
        Location sl = shooter.getLocation();
        if (sl.getWorld() == null || !sl.getWorld().equals(world)) {
            return configured;   // 跨世界：按配置上限算（相当于"立刻销毁"）
        }
        int simChunks = Math.max(1, world.getSimulationDistance());
        return Math.min(configured, simChunks * 16.0D);
    }

    /** lore 里那一行实时电量的前缀（见 {@link PartyItem#loreLabel()}）。 */
    private static final String LORE_LABEL = "POWER:";

    // ------------------------------------------------------------------ 可配置项（写入 items.yml）

    // ---- 阶段一（发射与飞行） ----

    /** 阶段一箭矢的伤害（<b>普通箭矢伤害</b>）。 */
    private final ItemSetting<Integer> basicDamage = setting("basic-damage", 16);
    /**
     * 阶段一箭矢的<b>速度倍率</b>（相对 {@link #ARROW_SPEED} = 1.25）。
     *
     * <p>★ 2026-09-21 用户要求「初始箭矢飞行速度提高 150%」⇒ 2.5 倍 ⇒ 实速 3.125 格/tick。
     */
    private final ItemSetting<Double> arrowSpeedMultiplier = setting("arrow-speed-multiplier", 2.5D);
    /** 每级力量附魔附加的箭矢伤害（沿用梦想封印 集的加成口径）。默认 0 = 严格 16。 */
    private final ItemSetting<Double> powerAmplifier = setting("power-amplifier", 0.0);
    /** 每级锋利附魔附加的箭矢伤害（沿用梦想封印 集的加成口径）。默认 0 = 严格 16。 */
    private final ItemSetting<Double> sharpnessAmplifier = setting("sharpness-amplifier", 0.0);
    /** 最大飞行距离（格）。★ 真正生效的上限是 {@code min(它, 模拟距离 × 16)}，见 {@link #travelLimitFor}。 */
    private final ItemSetting<Integer> maxDistance = setting("max-distance", 120);
    /** 最大飞行时间（秒）。★ 2026-09-21 用户口径：<b>8 秒</b>（原 15 秒）。 */
    private final ItemSetting<Integer> maxSeconds = setting("max-seconds", 8);
    /** 每 tick 伴随箭矢生成的火焰粒子数。 */
    private final ItemSetting<Integer> trailParticles = setting("trail-particles", 5);

    // ---- 阶段二（命中点爆发） ----

    /** 激光长度（格）。★ 2026-09-21 用户口径：<b>20</b> 格（原 12）。 */
    private final ItemSetting<Integer> laserLength = setting("laser-length", 20);
    /**
     * 激光直径（格）。★ 2026-09-21 用户口径：<b>3</b> 格（原 1）。
     *
     * <p>它同时决定"被实体方块截断"的判定半径（直径/2 = 1.5 格）。
     */
    private final ItemSetting<Double> laserDiameter = setting("laser-diameter", 3.0);
    /**
     * 命中后的<b>范围伤害半径</b>（格）：以命中点为球心，这个半径内的可命中实体全部吃伤害。
     *
     * <p>★ 2026-09-21 用户口径：3 格。
     */
    private final ItemSetting<Double> impactRadius = setting("impact-radius", 3.0D);
    /**
     * 范围伤害数值。★ 2026-09-21 用户口径：<b>24</b> 点，伤害类型是<b>弹射物伤害</b>。
     *
     * <p>⚠ 这一项取代了原先的 {@code laser-damage}（32 真伤）：
     * 现在走的是原版伤害通道（护甲/无敌帧/保护插件全部照常生效），<b>不再是真伤</b>。
     */
    private final ItemSetting<Integer> impactDamage = setting("impact-damage", 24);
    /** 喷泉粒子总数（纯视觉）。★ 2026-09-21 用户口径：<b>400</b>（原 300）。 */
    private final ItemSetting<Integer> fountainParticles = setting("fountain-particles", 400);
    /** 追踪箭数量。 */
    private final ItemSetting<Integer> trackerCount = setting("tracker-count", 12);
    /** 追踪箭的伤害（<b>普通箭矢伤害</b>）。 */
    private final ItemSetting<Integer> trackerDamage = setting("tracker-damage", 12);
    /**
     * 追踪箭的<b>速度倍率</b>（相对 {@link #TRACKER_SPEED} = 1.0）。
     *
     * <p>★ 2026-09-21 用户要求「衍生箭矢飞行速度提高 40%」⇒ 1.4 倍 ⇒ 实速 1.4 格/tick。
     */
    private final ItemSetting<Double> trackerSpeedMultiplier = setting("tracker-speed-multiplier", 1.4D);
    /** 追踪箭的锁定半径（格）。★ 2026-09-21 用户口径：<b>20</b> 格（原 12）。 */
    private final ItemSetting<Integer> trackerRange = setting("tracker-range", 20);
    /** 追踪箭的最大存在时间（秒）。 */
    private final ItemSetting<Integer> trackerSeconds = setting("tracker-seconds", 5);
    /**
     * 追踪箭射出后<b>多久开始追踪</b>（秒）。
     *
     * <p>★ 2026-09-21 用户口径：<b>0.5 秒</b>（= 10 tick）后进入追踪（原先固定 8 tick）。
     */
    private final ItemSetting<Double> trackerGuideDelaySeconds =
            setting("tracker-guide-delay-seconds", 0.5D);
    /** 每支追踪箭每 tick 生成的火焰粒子数。 */
    private final ItemSetting<Integer> trackerParticles = setting("tracker-particles", 2);

    // ---- 安全 ----

    /**
     * 是否<b>排除发射者</b>（默认 {@code true}）。
     *
     * <p>★ 用户明确要求的硬性约束：阶段一的箭、阶段二的 12 支追踪箭、
     * 以及命中点的范围伤害都不得伤害发射者本人。
     * 关掉它 = 回到 LogiTech 方块激光那种"站自己光束里会被烧"的行为，<b>不建议</b>。
     */
    private final ItemSetting<Boolean> excludeShooter = setting("exclude-shooter", true);

    // ---- POWER 刻度 / 取电参数 / 充能循环 / 发射冷却闸：全部继承自 PartyItem ----
    //   （用户要求的「数据等沿用」：8 个 ItemSetting 与整条取电链路只有 PartyItem 一份声明。
    //    注意当前 charge-wireless=false —— 与梦想封印 集一样，它不会自动充能。）

    // ------------------------------------------------------------------ 状态表（追踪表）

    /**
     * 阶段一那支箭的追踪表：<b>箭矢 UUID → 这次发射的上下文</b>。
     *
     * <p>★ 制导/清理可能发生在异步线程，所以用并发表。
     * ★ 这张表必须<b>零泄漏</b>：四条终止路径全部经 {@link #finishShot} 摘除（见类注释）。
     */
    private static final Map<UUID, Shot> TRACKED_SHOTS = new ConcurrentHashMap<>();

    /** 阶段二追踪箭的追踪表：箭矢 UUID → 绝对过期时刻（毫秒）。 */
    private static final Map<UUID, Long> TRACKED_ARROWS = new ConcurrentHashMap<>();

    /** 追踪箭的制导循环句柄（每批一条）。 */
    private static final Map<UUID, BukkitTask> TRACKER_TASKS = new ConcurrentHashMap<>();

    /**
     * 已结算过的箭矢 UUID（防止"命中事件 + 周期任务"把阶段二放两遍）。
     *
     * <p>★ 不能让 {@link #TRACKED_SHOTS} 兼任这个角色：{@link #finishShot} 会<b>摘掉</b>条目，
     * 于是"摘掉"与"没登记过"再也分不开，重复命中就会被当成第一次。
     */
    private static final Set<UUID> FINISHED = ConcurrentHashMap.newKeySet();

    /** {@link #FINISHED} 的清理门槛（毫秒）与上次清理时刻。 */
    private static final long FINISHED_KEEP_MILLIS = 60_000L;
    private static volatile long finishedCleanedAt;

    /** 每次爆发的现场报告（键 = 阶段一箭矢的 UUID）；供无头仿真回读，避免重复结算。 */
    private static final Map<UUID, BurstReport> BURSTS = new ConcurrentHashMap<>();

    /**
     * 一次发射的完整上下文（阶段一那支箭的"身世"）。
     *
     * <p>★ 这个对象是<b>可变</b>的（不是 record）：{@code hit} / {@code absorbed} 由
     * 监听器与任务在飞行过程中写入，仿真结束时才读取。全部写入都发生在主线程，
     * 读取也只在主线程的任务里，所以不需要额外的同步。
     */
    private static final class Shot {
        /** 发射者 UUID（"不伤害发射者"这条规则要用它）。 */
        final UUID shooterId;
        /** 发射者对象（追踪箭索敌要用；被移除后可能失效）。 */
        final LivingEntity shooter;
        /** 出膛点（算飞行距离）。 */
        final Location origin;
        /** 出膛方向（单位向量）。 */
        final Vector direction;
        /** 生成时刻（毫秒；12 秒上限用）。 */
        final long startedAt;
        /** 到这一 tick 必须结束（tick 口径的兜底，与毫秒口径互为保险）。 */
        final long deadlineTick;
        /** 生命周期轮询任务（结束时必须 cancel；由 {@link #withTask} 回填）。 */
        BukkitTask task;
        /** 这一发箭矢的伤害（普通箭矢伤害）。 */
        final float arrowDamage;
        /**
         * 这一发箭矢对象本身 —— 作 {@code DamageSource} 的"直接实体"用。
         *
         * <p>命令构造的临时上下文里为 {@code null}（那时没有真箭），
         * 伤害会退化成"直接实体 = 发射者"。
         */
        Arrow arrow;

        /** 已观测到的飞行距离上限（任务每 tick 刷新，供诊断）。 */
        volatile double maxTravelled;
        /** 命中信息（监听器写；写完当 tick 内由任务结算阶段二）。 */
        volatile Block hitBlock;
        volatile BlockFace hitFace;
        volatile Entity hitEntity;
        volatile Location hitPoint;
        volatile boolean hit;
        /** 阶段二是否已经结算过（防止重复爆发）。 */
        volatile boolean burstDone;
        /** 该发射者"吸收了"几次本该命中的伤害（>0 就说明"排除发射者"没生效）。 */
        volatile int absorbed;
        /**
         * 无头仿真专用：让发射者跟着箭走，使箭一直处在"活跃范围"里。
         *
         * <p>★ 为什么需要（本任务实测的核心限制）：Paper 的实体激活范围会让
         * <b>附近没有玩家</b>的实体不被 tick —— 实测一支新生成的箭
         * {@code getTicksLived()} 恒为 0、位置一动不动，约 1 秒后被服务端回收。
         * 所以控制台（没有玩家在线）想验证"箭真的会飞 / 距离与时间上限"，
         * 唯一的办法就是让一个临时发射者跟着它飞。
         * ★ 游戏内正常发射永远是 {@code false}（玩家自己就在旁边）。
         */
        volatile boolean followArrow;
        /**
         * 无头仿真专用：跟随到<b>这个距离</b>就停下（格）。
         *
         * <p>用途：距离上限那条规则需要"发射者不动、箭飞远"才会触发，
         * 而 {@link #followArrow} 恰好让两者同速。所以跟到这个距离之后就不再跟，
         * 让箭自己拉开距离 —— 于是"距离规则"也能被无头验证。
         * {@code 0} = 一直跟。
         */
        volatile double followUntil = 0.0D;

        Shot(UUID shooterId, LivingEntity shooter, Location origin, Vector direction,
             long startedAt, long deadlineTick, BukkitTask task, float arrowDamage) {
            this.shooterId = shooterId;
            this.shooter = shooter;
            this.origin = origin;
            this.direction = direction;
            this.startedAt = startedAt;
            this.deadlineTick = deadlineTick;
            this.task = task;
            this.arrowDamage = arrowDamage;
        }

        /**
         * 补上任务句柄。
         *
         * <p>★ 为什么不在构造器里传：任务体里要调 {@code tickShot}，而 {@code tickShot}
         * 要用这个上下文 —— 先建上下文、再建任务、最后回填句柄，是唯一不绕圈的顺序。
         */
        void withTask(BukkitTask t) {
            this.task = t;
        }
    }

    /**
     * 无头仿真的回调（命令用）。
     *
     * <p>★ 为什么是回调而不是"返回值 + 主线程等"：仿真必须跨很多 tick（箭要真的飞出去），
     * 而命令跑在主线程 —— 在那儿 {@code Thread.sleep} 或 {@code CountDownLatch.await}
     * 会把整个服务端卡死（本任务实测踩过：服务端 15 秒无响应被 Watchdog 打线程转储）。
     * 所以改成"任务自然结束时回调"。
     */
    public interface SimCallback {
        void done(FireSim result);
    }

    /**
     * 一次阶段二爆发的现场读数。
     *
     * @param reason        阶段一的终止原因
     * @param hitDetail     命中详情（方块坐标+面 / 实体名）
     * @param origin        爆发原点（= 命中点）
     * @param direction     裁决出来的方向
     * @param beamLength    激光实际长度（被截断后）
     * @param beamBlockedAt 截断发生在第几格（-1 = 没被截断）
     * @param impactDamage  命中点范围伤害的数值（弹射物伤害）
     * @param trackers      放出的追踪箭数量
     * @param fountain      喷泉粒子数
     * @param damaged       被范围伤害实际扣血的实体数（★ 2026-09-21 新增）
     */
    public record BurstReport(FinishReason reason, String hitDetail, Location origin,
                              Vector direction, double beamLength, int beamBlockedAt,
                              double impactDamage, int trackers, int fountain, int damaged) {
    }

    // ------------------------------------------------------------------ 构造与契约实现

    public MurderousLily(ItemGroup itemGroup, SlimefunItemStack item,
                         RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler((ItemUseHandler) this::onUse);
    }

    @Override
    protected String loreLabel() {
        return LORE_LABEL;
    }

    @Override
    protected String itemLabel() {
        return "杀意的百合";
    }

    /** 本道具的消息作用域（前缀/档位取自 config.yml 的 {@code lily:} 段）。 */
    @Override
    protected Notify.Scope scope() {
        return Notify.lily();
    }

    /** 供 {@link MurderousLilyListener} 判定"这是不是本道具阶段一的那支箭"。 */
    public static boolean isTrackedShot(Arrow arrow) {
        return arrow != null && TRACKED_SHOTS.containsKey(arrow.getUniqueId());
    }

    /** 供 {@link MurderousLilyListener} 判定"这是不是本道具阶段二的追踪箭"。 */
    public static boolean isTrackedTracker(Arrow arrow) {
        return arrow != null && TRACKED_ARROWS.containsKey(arrow.getUniqueId());
    }

    /** 当前追踪表条目数（诊断用；正常在发射结束后必须回到 0）。 */
    public static int trackedShotCount() {
        return TRACKED_SHOTS.size();
    }

    /** 当前追踪箭表条目数（诊断用）。 */
    public static int trackedTrackerCount() {
        return TRACKED_ARROWS.size();
    }

    // ------------------------------------------------------------------ 交互

    private void onUse(PlayerRightClickEvent event) {
        event.cancel();   // 钓鱼竿：阻止抛竿
        Player p = event.getPlayer();
        ItemStack item = event.getItem();
        long now = System.currentTimeMillis();

        if (!passPermission(p)) {
            Notify.warn(scope(), p, "&c你没有权限使用该道具!");
            return;
        }

        // ---- 冷却闸 + POWER 闸：与梦想封印 集共用 PartyItem 里那一段（1.5 秒 / 1 POWER） ----
        if (!passGate(p, item, now)) {
            return;
        }
        fire(p, item);
    }

    // ------------------------------------------------------------------ 阶段一：一支箭

    /** 右键发射：一支无视重力的箭，带着追踪表条目上路。 */
    private void fire(Player p, ItemStack item) {
        float damage = arrowDamage(item, basicDamage.getValue(), powerAmplifier.getValue(),
                sharpnessAmplifier.getValue());
        Location origin = handLocation(p);
        // ★ 方向与起点在同一处取（LogiTech 踩过"起点用眼位、方向用脚位朝向"的坑，
        //   见 slimefun-laser-dev/03 §3.6）：这里两者都来自同一次 getEyeLocation/getLocation。
        Vector direction = p.getLocation().getDirection().normalize();
        spawnShot(p, origin, direction, damage);
    }

    /**
     * 真正生成那支箭并挂上生命周期（游戏内发射与无头仿真走的是<b>同一个</b>方法）。
     *
     * <p>★ 参数类型是 {@link LivingEntity} 而不是 {@code Player}：
     * 游戏内永远传玩家，而无头仿真允许传一个临时生物当发射者
     * （{@code Projectile#setShooter} 收的是 {@code ProjectileSource}，
     * 生物同样满足 —— 这样"排除发射者"这条规则在没有玩家在线时也验证得了）。
     *
     * @return 生成的箭矢；{@code null} = 世界为空/生成失败
     */
    private Arrow spawnShot(LivingEntity shooter, Location origin, Vector direction, float damage) {
        World world = origin == null ? null : origin.getWorld();
        if (world == null || shooter == null) {
            return null;
        }
        Arrow arrow = world.spawnArrow(origin.clone(), direction.clone(),
                configuredArrowSpeed(), ARROW_SPREAD);
        arrow.setDamage(damage);                 // ← 普通箭矢伤害（阶段一的 16）
        arrow.setCritical(false);
        arrow.setShooter(shooter);               // ← "排除发射者"由原版箭矢自己完成
        arrow.setGravity(false);                 // ← 无视重力
        arrow.setPickupStatus(PickupStatus.DISALLOWED);

        long now = System.currentTimeMillis();
        long deadlineTick = Touhou.getInstance().getServer().getCurrentTick()
                + Math.max(1, configuredMaxSeconds()) * 20L;
        // ★ 任务先建、再放进 Shot：周期任务第一跑要能拿到完整的上下文
        Shot ctx = new Shot(shooter.getUniqueId(), shooter, origin.clone(), direction.clone(),
                now, deadlineTick, null, damage);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                tickShot(arrow);
            }
        }.runTaskTimer(Touhou.getInstance(), 1L, 1L);
        // 任务句柄建好之后才补进上下文（finishShot 需要它来 cancel）
        ctx.withTask(task);
        ctx.arrow = arrow;
        TRACKED_SHOTS.put(arrow.getUniqueId(), ctx);
        return arrow;
    }

    /**
     * 阶段一箭矢的<b>每 tick 生命周期</b>：喷尾迹、判存活、判距离、判时间、结算命中。
     *
     * <p>★ 全部收尾路径（存活 → 距离 → 时间 → 命中）都汇到 {@link #finishShot}。
     *
     * <p>★★ <b>2026-09-21 用户的销毁规则（当前口径）</b>：
     * <pre>
     *   ① 与发射者的距离 ≥ min(120, 模拟距离 × 16)  ⇒ 立即销毁（按距离规则做一次补写，不过冲）
     *   ② 飞行时间 &gt;= 8 秒                         ⇒ 立即销毁
     *   ③ 命中方块 / 实体                            ⇒ 进入阶段二
     * </pre>
     * <b>不再做任何区块加载状态检测</b>（早期版本里有一条 {@code world.isChunkLoaded(...)}
     * 判据，已被用户要求删除）：它在 Paper 上既会瞬时误报（把刚射出的箭当场判死），
     * 又会因为"箭所在区块没人看"而误杀长距离飞行的箭。
     * 现在版本只管"距离"与"时间"两件事，箭所在区块没加载时它自然也不会被 tick，
     * 到点自然结束 —— 不会留下追踪表条目（时间上限与距离上限都是箭自己的属性）。
     *
     * <p>★ 为什么把"命中结算"放在任务里而不是监听器里：监听器只负责<b>记录</b>命中信息，
     * 真正的阶段二爆发（12 支箭 + 300 粒子 + 激光）留到本 tick 的任务里做 ——
     * 这样就不会在别人的 {@code ProjectileHitEvent} 分发过程中塞进一大堆实体生成。
     */
    private void tickShot(Arrow arrow) {
        Shot shot = TRACKED_SHOTS.get(arrow.getUniqueId());
        if (shot == null) {
            return;   // 已被结算过（任务正在被 cancel 的路上）
        }
        long tick = Touhou.getInstance().getServer().getCurrentTick();
        long now = System.currentTimeMillis();
        // 仿真计时（游戏内发射时没有这一条）
        double[] simState = SIM_STATE.get(arrow.getUniqueId());
        if (simState != null) {
            simState[2] += 1.0D;
        }

        // ① 命中：监听器已经把命中信息写进上下文了 —— 立刻结算阶段二并收尾
        if (shot.hit && !shot.burstDone) {            shot.burstDone = true;
            FinishReason reason = shot.hitEntity != null
                    ? FinishReason.HIT_ENTITY : FinishReason.HIT_BLOCK;
            Location origin = shot.hitPoint == null ? arrow.getLocation() : shot.hitPoint.clone();
            String detail = reason.text() + " @ " + TouhouData.xyz(origin)
                    + "，命中面=" + (shot.hitFace == null ? "(实体：没有面)" : shot.hitFace.name())
                    + (shot.hitEntity == null ? "" : "，实体=" + shot.hitEntity.getType().name());
            finishShot(arrow, reason, shot.hitBlock, shot.hitFace);
            BurstReport report = burst(shot, origin, laserDirection(shot.hitFace), detail);
            BURSTS.put(arrow.getUniqueId(), report);
            // ★ 交付时可能正身处 ProjectileHitEvent 的分发里：把"改世界/回显"推迟一个 tick，
            //   免得在别人的事件流程中生成实体或改方块。
            final Shot doneShot = shot;
            final BurstReport doneReport = report;
            Bukkit.getScheduler().runTask(Touhou.getInstance(),
                    () -> notifySim(this, arrow.getUniqueId(), doneShot, doneReport));
            return;
        }

        Location loc = arrow.getLocation();
        World world = loc.getWorld();

        // ★ 无头仿真：让临时发射者跟着箭（否则 Paper 不 tick 附近没玩家的实体，
        //   箭会一动不动）。游戏内永远是 false。
        if (shot.followArrow && shot.shooter != null && shot.shooter.isValid()
                && world != null && tick % 2L == 0L) {
            boolean keepFollowing = shot.followUntil <= 0.0D
                    || shot.maxTravelled < shot.followUntil;
            Location at = shot.shooter.getLocation();
            if (keepFollowing && at.distanceSquared(loc) > 16.0D) {
                shot.shooter.teleport(loc.clone().add(0.0D, 0.6D, 0.0D));
            }
        }

        // ② 实体因为任何别的原因没了（/kill、插件清理、服务端淘汰…）
        if (!arrow.isValid() || arrow.isDead()) {
            endShot(arrow, shot, FinishReason.REMOVED);
            return;
        }

        // ③ 销毁规则一：与发射者的距离 >= min(max-distance, 模拟距离 × 16)
        //    ★ 用户 2026-09-21 的规则。上限里的"模拟距离 × 16"是服务端实际会把实体
        //      发给玩家的范围 —— 超出它的箭对任何人都不可见，继续飞纯属浪费。
        //      取 min 之后，"120 格"仍然是常规配置下的那个上限。
        //    ⚠ 发射者已经不在（被移除/离线）时这一条<b>失效</b>，只剩时间规则兜底 ——
        //      这是刻意的：没有参照点就没法算距离，而"不伤害发射者"那条规则
        //      在发射者消失后本来也无从谈起。
        double travelLimit = travelLimitFor(shot, world);
        if (travelLimit > 0.0D) {
            double travelled = loc.distance(shot.origin);
            if (travelled > shot.maxTravelled) {
                shot.maxTravelled = travelled;
            }
            // "立即销毁"按字面执行，但仍按距离规则做一次补写、不过冲：
            // 箭速 1.25 格/tick，若这一 tick 会越过上限，就把位置压回上限处再销毁，
            // 于是"实际飞了多远"与"规则允许多远"永远是同一个数（可被断言）。
            if (travelled >= travelLimit) {
                Vector back = loc.toVector().subtract(shot.origin.toVector());
                if (back.lengthSquared() > 1.0E-9D) {
                    Vector overshoot = back.clone().normalize()
                            .multiply(travelLimit - (travelled - back.length()));
                    arrow.teleport(shot.origin.clone().add(overshoot));
                }
                shot.maxTravelled = travelLimit;
                endShot(arrow, shot, FinishReason.DISTANCE);
                return;
            }
        }

        // ④ 销毁规则二：飞行时间 >= max-seconds（默认 8 秒）。
        //    毫秒与 tick 两个口径互为保险（谁先到用谁），避免服务端卡顿时时间口径漂移。
        if (now - shot.startedAt >= Math.max(1, configuredMaxSeconds()) * 1000L
                || tick >= shot.deadlineTick) {
            endShot(arrow, shot, FinishReason.TIME);
            return;
        }

        // 每 tick 伴随箭矢的尾迹（默认 5 个）
        int count = configuredTrailParticles();
        if (count > 0) {
            world.spawnParticle(Particle.FLAME, loc, count, 0.06, 0.06, 0.06, 0.01, null, true);
        }
    }

    /**
     * "没有命中"的那几种收尾（区块没了 / 被移除 / 超时 / 超距 / 强制）：
     * 收尾 + 留一条读数 + 通知可能的仿真回调。
     */
    private void endShot(Arrow arrow, Shot shot, FinishReason reason) {
        Location loc = arrow.isValid() ? arrow.getLocation() : shot.origin;
        finishShot(arrow, reason, null, null);
        BurstReport report = new BurstReport(reason, "-", loc, new Vector(0, 1, 0),
                0.0D, -1, 0.0D, 0, 0, 0);
        BURSTS.put(arrow.getUniqueId(), report);
        notifySim(this, arrow.getUniqueId(), shot, report);
    }

    /** 阶段一箭矢的终止原因（报告与诊断都按它归类）。 */
    public enum FinishReason {
        /** 命中方块。 */
        HIT_BLOCK("命中方块"),
        /** 命中实体。 */
        HIT_ENTITY("命中实体"),
        /** 与发射者的距离达到上限（{@code min(max-distance, 模拟距离×16)}）。 */
        DISTANCE("飞满距离上限"),
        /** 飞行时间到（{@code max-seconds}，默认 8 秒）。 */
        TIME("飞行时间到"),
        /** 实体被移除（{@code /kill}、插件清理、区块卸载导致服务端回收…）。 */
        REMOVED("实体被服务端/插件移除"),
        /** 命令/仿真强制终止。 */
        FORCED("强制终止");

        private final String text;

        FinishReason(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    /**
     * 阶段一箭矢的<b>唯一收尾出口</b>：摘追踪表、取消周期任务、从世界上移除箭矢。
     *
     * <p>★ 为什么必须"唯一"：这条路径可能来自主线程（命中事件、周期任务），
     * 也可能来自命令。写两遍就一定有一条忘记摘表 —— 那就是隐性泄漏。
     *
     * @return 被摘掉的上下文；{@code null} = 这次调用是重复的（已被结算过）
     */
    Shot finishShot(Arrow arrow, FinishReason reason, Block hitBlock, BlockFace hitFace) {
        UUID id = arrow.getUniqueId();
        Shot shot = TRACKED_SHOTS.remove(id);     // ← 先摘表：即便后面抛异常也不会留条目
        if (shot == null) {
            return null;
        }
        if (shot.task != null) {
            shot.task.cancel();
        }
        FINISHED.add(id);
        long now = System.currentTimeMillis();
        if (now - finishedCleanedAt > FINISHED_KEEP_MILLIS) {
            finishedCleanedAt = now;
            FINISHED.clear();
        }
        if (arrow.isValid()) {
            arrow.remove();
        }
        return shot;
    }

    /**
     * 命中入口（由 {@link MurderousLilyListener} 在本道具的箭命中时调用）。
     *
     * <p>★ 这里<b>只记录</b>命中信息，真正的阶段二爆发由 {@link #tickShot} 在同一个 tick
     * 稍后执行 —— 免得在别人的 {@code ProjectileHitEvent} 分发过程中生成一大堆实体。
     *
     * <p>★ 命中信息里同时把"发射者是不是挡在弹道上"记下来：
     * {@code absorbed} 是"本该打中发射者的箭矢/激光次数"，正常必须是 0。
     */
    void onShotHit(Arrow arrow, Block hitBlock, BlockFace hitFace, Entity hitEntity,
                   Location hitPoint) {
        UUID id = arrow.getUniqueId();
        Shot shot = TRACKED_SHOTS.get(id);
        if (shot == null || shot.hit) {
            return;   // 不认识 / 已记录过：只记一次
        }
        shot.hitBlock = hitBlock;
        shot.hitFace = hitFace;
        shot.hitEntity = hitEntity;
        shot.hitPoint = hitPoint == null ? arrow.getLocation() : hitPoint.clone();
        shot.hit = true;
        // 命中实体且那正是发射者本人 ⇒ 说明箭矢打到了自己（配置允许时会这样）
        if (hitEntity != null && hitEntity.getUniqueId().equals(shot.shooterId)) {
            shot.absorbed++;
        }
    }

    /**
     * 记一次"本该命中发射者"的伤害（由激光结算调用）。
     *
     * <p>它是"不伤害发射者"这条规则的反向探针：正常永远是 0；
     * 一旦 {@code exclude-shooter=false} 或代码写漏了排除，它就会 > 0。
     */
    void noteShooterHit(Shot shot) {
        if (shot != null) {
            shot.absorbed++;
        }
    }

    // ------------------------------------------------------------------ 阶段二：方向裁决

    /**
     * ★★ <b>方向裁决的唯一实现点</b>：激光与喷泉的方向。
     *
     * <p>规则（详见类注释）：命中方块 ⇒ 取该面的<b>外法线</b>（朝外，即"从被打中的那个面喷出来"）；
     * 命中实体（没有面）⇒ <b>始终向上</b>。
     *
     * <p>⚠ 想翻成字面版（"击中北面朝南"）只需把最后那句 {@code return normal;}
     * 改成 {@code return normal.multiply(-1);} —— 一行的事。
     */
    public static Vector laserDirection(BlockFace face) {
        if (face == null) {
            return new Vector(0, 1, 0);           // 实体：始终向上
        }
        Vector normal = new Vector(face.getModX(), face.getModY(), face.getModZ());
        if (normal.lengthSquared() < 1.0E-6) {
            return new Vector(0, 1, 0);           // SELF 之类的退化面：按"没有面"处理
        }
        return normal.normalize();                // ← 外法线向外（"朝南"版就把这里取反）
    }

    public static String fmt(Vector v) {
        return String.format("(%.2f, %.2f, %.2f)", v.getX(), v.getY(), v.getZ());
    }

    // ------------------------------------------------------------------ 阶段二：三件事

    /**
     * 阶段二爆发：<b>激光 + 喷泉 + 12 支追踪箭</b>，全部以命中点为原点、沿 {@code dir} 展开。
     *
     * <p>★ 公开重载：给 {@code /touhou lily impact} 那种"只做一次爆发"的诊断用。
     * 参数就是"发射者 + 原点 + 方向"，不需要知道阶段一的内部上下文。
     */
    public BurstReport burstFrom(LivingEntity shooter, Location origin, Vector dir, String hitDetail) {
        return burst(new Shot(shooter == null ? null : shooter.getUniqueId(), shooter, origin,
                dir, System.currentTimeMillis(), 0L, null, 0.0F), origin, dir, hitDetail);
    }

    /**
     * 阶段二爆发：<b>激光 + 喷泉 + 12 支追踪箭 + 命中点范围伤害</b>，
     * 全部以命中点为原点、沿 {@code dir} 展开。
     *
     * @return 现场读数（供命令/报告断言）
     */
    BurstReport burst(Shot shot, Location origin, Vector dir, String hitDetail) {
        if (origin == null || origin.getWorld() == null) {
            return new BurstReport(FinishReason.FORCED, "坐标无效", origin, dir, 0, -1, 0, 0, 0, 0);
        }
        // ① 激光（先把长度算出来，报告要用）
        double[] beam = fireLaser(shot, origin, dir);
        // ② 喷泉
        int fountain = fireFountain(origin, dir);
        // ③ 追踪箭
        int trackers = fireTrackers(shot == null ? null : shot.shooter, origin, dir);
        // ④ 命中点范围伤害（弹射物伤害，半径 3 格）
        //    ★ 放在追踪箭<b>之后</b>：追踪箭是在命中点生成的，它们也在这个球里 ——
        //      按用户口径「范围内的目标都吃这次伤害」，同一 tick 刚放出来的衍生箭当然也算。
        //      （实测：顺序反过来时这一条打出的是"实际扣血 0 个"，因为球里当时确实还没人。）
        int damaged = damageSphere(shot, origin);
        return new BurstReport(FinishReason.HIT_BLOCK, hitDetail, origin.clone(), dir.clone(),
                beam[0], (int) beam[1], configuredImpactDamage(), trackers, fountain, damaged);
    }

    // ---- ① 激光 ----

    /**
     * 激光：从命中点沿 {@code dir} 长 <b>20</b> 格、直径 <b>3</b> 格，<b>被实体方块截断</b>。
     *
     * <p>★ 2026-09-21 用户口径：长度 12 → 20，直径 1 → 3。
     * ⚠ 激光<b>不再自己结算伤害</b>：伤害改由 {@link #damageSphere} 以命中点为球心统一结算
     * （用户口径：「以落点为中心、3 格为半径的范围内造成 24 点弹射物伤害」）。
     * 这里只负责几何（长度/截断）与粒子。
     *
     * <p>口径对齐 {@code slimefun-laser-dev/references/01-hitscan-anatomy.md}：
     * <b>步进 + 逐步谓词</b>（不是 {@code BlockIterator}）。
     *
     * @return {@code [实际长度(格), 被截断于第几格(没截断 = -1)]}
     */
    double[] fireLaser(Shot ctx, Location origin, Vector dir) {
        World world = origin.getWorld();
        int want = configuredLaserLength();
        double radius = Math.max(0.1D, configuredLaserDiameter()) / 2.0D;

        // 逐步推进：每一步都判"这根直径 3 格的管子有没有被实体方块挡住"
        int steps = 0;
        int maxSteps = (int) Math.round(want / STEP);
        for (int i = 1; i <= maxSteps; i++) {
            Location probe = origin.clone().add(dir.clone().multiply(i * STEP));
            if (!beamPassable(world, probe, radius)) {
                break;
            }
            steps = i;
        }
        double length = Math.min(want, steps * STEP);
        int blockedAt = length < want - 1.0E-6 ? steps + 1 : -1;

        // 粒子：中轴一条线 + 若干同心环（环圈数随直径变粗而增加，构成实心管子）
        if (length > 0.0D) {
            Vector u = ortho(dir, 0);
            Vector v = ortho(dir, 1);
            int rings = beamRingCount();
            double step = beamVisualStep();
            for (double d = step; d <= length + 1.0E-6; d += step) {
                Location p = origin.clone().add(dir.clone().multiply(d));
                world.spawnParticle(Particle.FLAME, p, 0, 0.0, 0.0, 0.0, 1.0, null, true);
                for (int ring = 1; ring <= rings; ring++) {
                    double r = radius * ring / rings;
                    for (int k = 0; k < BEAM_RING_POINTS; k++) {
                        double a = 2.0D * Math.PI * k / BEAM_RING_POINTS;
                        Location at = p.clone()
                                .add(u.clone().multiply(Math.cos(a) * r))
                                .add(v.clone().multiply(Math.sin(a) * r));
                        world.spawnParticle(Particle.FLAME, at, 0, 0.0, 0.0, 0.0, 1.0, null, true);
                    }
                }
            }
        }
        return new double[]{length, blockedAt};
    }

    /**
     * 命中点的<b>范围伤害</b>：以命中点为球心、{@code impact-radius}（默认 3）格为半径，
     * 对范围内每个可命中实体造成 {@code impact-damage}（默认 24）点 <b>弹射物伤害</b>。
     *
     * <p>★ 2026-09-21 用户口径。与旧实现（沿激光 AABB 结算 32 真伤）的区别：
     * <ul>
     *   <li>范围从"激光那条管子"改成"以落点为球心的球"；</li>
     *   <li>伤害类型从<b>真伤</b>改成<b>弹射物伤害</b> —— 也就是走<b>原版伤害通道</b>
     *       （{@code Damageable#damage(amount, DamageSource)}，DamageType = {@code ARROW}），
     *       于是护甲、抗性、附魔、无敌帧、保护插件的 {@code EntityDamageByEntityEvent}
     *       <b>全部照常生效</b>。因此 {@code TrueDamageProbeEvent} 那套探测事件不再需要，
     *       已一并移除（本道具现在<b>没有任何真伤</b>）。</li>
     * </ul>
     *
     * <p>★ 不伤害发射者（可配置，默认开）：球内的发射者本人被跳过并计入反向探针。
     *
     * @return 实际被扣血的实体数（诊断用）
     */
    int damageSphere(Shot ctx, Location origin) {
        World world = origin == null ? null : origin.getWorld();
        if (world == null) {
            return 0;
        }
        double damage = configuredImpactDamage();
        double radius = Math.max(0.1D, configuredImpactRadius());
        if (damage <= 0.0D) {
            return 0;
        }
        UUID shooterId = ctx == null ? null : ctx.shooterId;
        LivingEntity causing = ctx == null ? null : ctx.shooter;
        int damaged = 0;
        Set<UUID> seen = new HashSet<>();
        for (Entity e : world.getNearbyEntities(origin, radius, radius, radius)) {
            if (!isTargetable(e) || !seen.add(e.getUniqueId())) {
                continue;
            }
            if (e.getLocation().distanceSquared(origin) > radius * radius) {
                continue;   // getNearbyEntities 给的是立方体，这里按球收紧
            }
            if (shooterProtected() && isShooter(e, shooterId)) {
                noteShooterHit(ctx);   // 反向探针：正常永远不该有人走到下一步
                continue;
            }
            if (damageProjectile(e, damage, causing, origin, ctx == null ? null : ctx.arrow)) {
                damaged++;
            }
        }
        return damaged;
    }

    /** 同 {@link #damageProjectile(Entity, double, LivingEntity, Location, Entity)}，
     *  但没有"直接实体"（例如命令直接触发的诊断激光）。 */
    boolean damageProjectile(Entity target, double amount, LivingEntity causing, Location hitAt) {
        return damageProjectile(target, amount, causing, hitAt, null);
    }

    /**
     * <b>弹射物伤害</b>：走原版伤害通道（护甲/无敌帧/保护插件全部生效）。
     *
     * <p>★ 与 {@code damageTrue}（{@code setHealth} 真伤）是两条完全不同的路：
     * 那个绕过一切、且不发事件；这个发的是标准 {@code EntityDamageByEntityEvent}
     * （{@code DamageCause.PROJECTILE}，DamageType = {@code ARROW}），
     * 所以领地/保护插件天然拦得住，<b>没有</b>"保护插件收不到通知"的代价。
     *
     * <p>⚠ 踩点（2026-09-21 实测修复）：{@code DamageSource} 要求
     * <b>设了 causing 就必须同时设 direct</b>，否则构造时直接抛
     * {@code IllegalArgumentException: Direct entity must be set if causing entity is set}。
     * 所以这里一定把"直接实体"也填上：有真箭就用那支箭，没有就退化成发射者自己。
     *
     * @param causing     伤害的"来源实体"（发射者；可能为 null）
     * @param direct      伤害的"直接实体"（那支箭；没有就 null）
     * @return 目标是否真的掉了血
     */
    boolean damageProjectile(Entity target, double amount, LivingEntity causing, Location hitAt,
                             Entity direct) {
        if (!(target instanceof Damageable d) || !target.isValid()) {
            return false;
        }
        double before = d.getHealth();
        try {
            DamageSource.Builder sb = DamageSource.builder(DamageType.ARROW)
                    .withDamageLocation(hitAt);
            Entity directEntity = direct != null && direct.isValid() ? direct : causing;
            if (causing != null && causing.isValid()) {
                sb = sb.withCausingEntity(causing);
            }
            if (directEntity != null && directEntity.isValid()) {
                sb = sb.withDirectEntity(directEntity);
            }
            d.damage(amount, sb.build());
        } catch (RuntimeException ex) {
            // 事件广播/伤害构造出错时**放行**到原版默认路径，别让一件道具因为
            // 某个插件的事件处理器抛异常就彻底失效（与 LogiTech 的 testAttackPermission 同口径）。
            Log.warn("[杀意的百合] 弹射物伤害异常（回退到无来源伤害）: " + ex);
            d.damage(amount);
        }
        return d.getHealth() < before;
    }

    /**
     * 这一格能不能透光：<b>管壁上不能有实体方块</b>。
     *
     * <p>判据与 {@code slimefun-laser-dev} 的 {@code isLightPassableBlock} 同源：
     * 空气 / 透明 / 水 / 气泡柱 / 岩浆 都算"能穿"，其余算实体方块。
     * 管壁取样用 6 个轴向偏移（半径 0.5）—— 够用，且只花 7 次方块查询/步。
     */
    public static boolean beamPassable(World world, Location center, double radius) {
        if (!passable(world.getBlockAt(center))) {
            return false;
        }
        double[][] offsets = {
                {radius, 0, 0}, {-radius, 0, 0}, {0, radius, 0},
                {0, -radius, 0}, {0, 0, radius}, {0, 0, -radius}};
        for (double[] o : offsets) {
            if (!passable(world.getBlockAt(center.clone().add(o[0], o[1], o[2])))) {
                return false;
            }
        }
        return true;
    }

    /** "能穿光"的材质表：与 LogiTech 的 {@code isLightPassableBlock} 同口径。 */
    private static boolean passable(Block b) {
        Material m = b.getType();
        return m.isAir() || m.isTransparent()
                || m == Material.WATER || m == Material.BUBBLE_COLUMN || m == Material.LAVA;
    }

    /** 由两端点 + 半径构造 AABB（构造器自己会取 min/max，方向不影响）。 */
    public static BoundingBox boxOf(Location a, Location b, double r) {
        return new BoundingBox(
                Math.min(a.getX(), b.getX()) - r, Math.min(a.getY(), b.getY()) - r,
                Math.min(a.getZ(), b.getZ()) - r,
                Math.max(a.getX(), b.getX()) + r, Math.max(a.getY(), b.getY()) + r,
                Math.max(a.getZ(), b.getZ()) + r);
    }

    /** 与 {@code dir} 正交的两个单位向量之一（画激光管壁的环用）。 */
    private static Vector ortho(Vector dir, int which) {
        Vector ref = Math.abs(dir.getY()) > 0.9D ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector u = dir.clone().crossProduct(ref).normalize();
        return which == 0 ? u : dir.clone().crossProduct(u).normalize();
    }

    /** 这个实体是不是这次发射的发射者。 */
    private static boolean isShooter(Entity e, UUID shooterId) {
        return shooterId != null && e.getUniqueId().equals(shooterId);
    }

    /** 是否排除发射者（{@code exclude-shooter=true} ⇒ 排除）。 */
    private boolean shooterProtected() {
        return Boolean.TRUE.equals(excludeShooter.getValue());
    }

    // ---- ② 喷泉（纯视觉） ----

    /**
     * 喷泉：在命中点周围随机撒 {@code fountain-particles}（默认 <b>400</b>）个火焰粒子，返回实际数量。
     *
     * <p>形状：以 {@code dir} 为主轴的一蓬火（offset 跟着主轴方向外扩），
     * 于是看起来是"从被打中的那个面喷出来"。
     * ★ 用<b>一次</b> {@code spawnParticle(count=N)} 发包，而不是 400 次单点发包。
     */
    int fireFountain(Location origin, Vector dir) {
        int count = configuredFountainParticles();
        if (count <= 0) {
            return 0;
        }
        World world = origin.getWorld();
        double spread = 1.2D;
        Location at = origin.clone().add(dir.clone().multiply(0.3D));
        world.spawnParticle(Particle.FLAME, at, count,
                spread + Math.abs(dir.getX()) * 1.4D,
                spread + Math.abs(dir.getY()) * 1.4D,
                spread + Math.abs(dir.getZ()) * 1.4D,
                0.12D, null, true);
        return count;
    }

    // ---- ③ 12 支追踪箭 ----

    /**
     * 追踪箭：以喷泉样式向周围放出 {@code tracker-count}（默认 12）支箭，返回实际支数。
     *
     * <p>每支：每 tick 2 个火焰粒子、追踪半径 <b>20</b> 格、最多存在 5 秒、
     * 伤害 12（普通箭矢伤害）、初速 <b>1.4</b> 格/tick（基准 1.0 × 1.4）、
     * 射出 <b>0.5 秒</b>后才进入追踪。
     * <b>不伤害发射者</b>：索敌阶段就排除发射者（见 {@link #steerTrackers}），
     * 加上箭身上带着 shooter（原版再兜一层）。
     */
    int fireTrackers(LivingEntity shooter, Location origin, Vector dir) {
        if (shooter == null || origin.getWorld() == null) {
            return 0;
        }
        int count = configuredTrackerCount();
        World world = origin.getWorld();
        float damage = configuredTrackerDamage();
        float speed = configuredTrackerSpeed();

        // "喷泉样式"：以 dir 为主轴，一层层张开仰角并绕主轴均布方位 —— 12 支 = 3 层 × 4 方位
        Vector axis = dir.lengthSquared() < 1.0E-6 ? new Vector(0, 1, 0) : dir.clone().normalize();
        Vector u = ortho(axis, 0);
        Vector v = ortho(axis, 1);
        int elevations = Math.max(1, Math.min(count, 3));
        int perElevation = Math.max(1, (int) Math.ceil(count / (double) elevations));

        HashSet<Arrow> arrows = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int ring = i / perElevation;
            int slot = i % perElevation;
            double azimuth = 2.0D * Math.PI * slot / perElevation;
            double polar = Math.toRadians(75.0D) * (ring + 1) / (double) elevations;
            Vector out = u.clone().multiply(Math.cos(azimuth))
                    .add(v.clone().multiply(Math.sin(azimuth)));
            Vector velocity = axis.clone().multiply(Math.cos(polar))
                    .add(out.multiply(Math.sin(polar))).normalize().multiply(speed);

            Arrow arrow = world.spawnArrow(origin.clone(), velocity, speed, 0.0F);
            arrow.setDamage(damage);              // ← 普通箭矢伤害（阶段二的 12）
            arrow.setCritical(false);
            arrow.setShooter(shooter);            // ← 排除发射者的原版保险
            arrow.setGravity(false);              // 追踪弹幕不吃重力，喷泉样式才不会半空垮掉
            arrow.setPickupStatus(PickupStatus.DISALLOWED);
            TRACKED_ARROWS.put(arrow.getUniqueId(),
                    System.currentTimeMillis() + Math.max(1, configuredTrackerSeconds()) * 1000L);
            arrows.add(arrow);
        }
        startTrackerLoop(shooter, origin, arrows);
        return arrows.size();
    }

    /**
     * 追踪箭的制导循环（每批一条，2 tick 一跳、<b>异步</b>）。
     *
     * <p>与 {@link FantasySeal} 的制导循环同构：起手 {@code tracker-guide-delay-seconds}
     *（默认 0.5 秒 = 10 tick）内直线外扩，之后才锁定最近目标；
     * 锁定半径 = {@code tracker-range}（默认 20 格）。
     */
    private void startTrackerLoop(LivingEntity shooter, Location origin, HashSet<Arrow> arrows) {
        if (arrows.isEmpty()) {
            return;
        }
        final UUID key = UUID.randomUUID();     // 每批一个键：多批同时在途也互不 cancel
        final HashSet<Arrow> live = new HashSet<>(arrows);
        BukkitRunnable task = new BukkitRunnable() {
            private int runTime;
            private boolean busy;

            @Override
            public void cancel() {
                super.cancel();
                TRACKER_TASKS.remove(key);
                // ★ 循环结束时收掉剩下的箭并摘表（追踪箭表的清理路径之一）
                sync(() -> live.forEach(a -> {
                    TRACKED_ARROWS.remove(a.getUniqueId());
                    if (a.isValid()) {
                        a.remove();
                    }
                }));
            }

            @Override
            public void run() {
                if (busy) {
                    return;
                }
                busy = true;
                try {
                    long now = System.currentTimeMillis();
                    runTime += TRACK_PERIOD;
                    if (runTime > Math.max(1, configuredTrackerSeconds()) * 20 + TRACK_PERIOD) {
                        cancel();
                        return;
                    }
                    // ① 过期 / 失效 / 落地 / 出界 / 区块没了 —— 全部摘表
                    Location sl = shooter.isValid() ? shooter.getLocation() : null;
                    Iterator<Arrow> it = live.iterator();
                    List<Arrow> doomed = new ArrayList<>();
                    while (it.hasNext()) {
                        Arrow a = it.next();
                        Long expireAt = TRACKED_ARROWS.get(a.getUniqueId());
                        if (expireAt == null) {
                            it.remove();
                            continue;
                        }
                        Location al = a.getLocation();
                        World aw = al.getWorld();
                        boolean gone = aw == null
                                || !aw.isChunkLoaded(al.getBlockX() >> 4, al.getBlockZ() >> 4)
                                || !a.isValid() || a.isDead() || a.isOnGround();
                        boolean far = sl == null || !al.getWorld().equals(sl.getWorld())
                                || al.distance(sl) > configuredTrackerRange() * 3.0D;
                        if (gone || far || now >= expireAt) {
                            doomed.add(a);
                        }
                    }
                    if (!doomed.isEmpty()) {
                        live.removeAll(doomed);
                        sync(() -> doomed.forEach(a -> {
                            TRACKED_ARROWS.remove(a.getUniqueId());
                            if (a.isValid()) {
                                a.remove();
                            }
                        }));
                    }
                    if (live.isEmpty()) {
                        cancel();
                        return;
                    }

                    // ② 每支追踪箭每 tick 的火焰粒子
                    int pc = configuredTrackerParticles();
                    if (pc > 0) {
                        for (Arrow a : live) {
                            if (a.isValid() && !a.isDead()) {
                                a.getWorld().spawnParticle(Particle.FLAME, a.getLocation(),
                                        pc, 0.03, 0.03, 0.03, 0.01, null, true);
                            }
                        }
                    }

                    // ③ 制导：起手让喷泉张开（默认 0.5 秒），之后锁定最近目标
                    if (runTime > configuredTrackerGuideTicks()) {
                        steerTrackers(shooter, live);
                    }
                } catch (RuntimeException e) {
                    // 异步循环里绝不能把异常抛出去（会打死 BukkitTask）
                    Log.warn("[杀意的百合] 追踪循环异常: " + e);
                } finally {
                    busy = false;
                }
            }
        };
        TRACKER_TASKS.put(key, task.runTaskTimerAsynchronously(Touhou.getInstance(),
                TRACK_PERIOD, TRACK_PERIOD));
    }

    /**
     * 追踪箭的索敌与转向（半主动比例导引，与 {@link FantasySeal#steer} 同源）。
     *
     * <p>★ <b>不伤害发射者</b>的落点就在这里：{@code exclude-shooter=true} 时
     * 锁定阶段直接<b>跳过发射者本人</b>（把他从候选里剔除），于是箭不会拐回来追自己；
     * 即便手动把自己塞进弹道，原版箭矢的 shooter 归属也会再兜一层。
     * 队友/队伍概念 spec 没提，这里<b>不做</b>任何推测性实现。
     */
    private void steerTrackers(LivingEntity shooter, HashSet<Arrow> live) {
        boolean protect = shooterProtected();
        double radius = configuredTrackerRange();
        sync(() -> {
            for (Arrow a : live) {
                if (!a.isValid() || a.isDead()) {
                    continue;
                }
                Location al = a.getLocation();
                World aw = al.getWorld();
                if (aw == null) {
                    continue;
                }
                LivingEntity best = null;
                double bestDist = radius;
                for (Entity e : aw.getNearbyEntities(al, radius, radius, radius)) {
                    if (protect && isShooter(e, shooter.getUniqueId())) {
                        continue;   // ★ 不伤害发射者：连锁都不锁
                    }
                    if (!isTargetable(e)) {
                        continue;
                    }
                    double d = e.getLocation().distance(al);
                    if (d < bestDist) {
                        bestDist = d;
                        best = (LivingEntity) e;
                    }
                }
                if (best == null) {
                    continue;   // 半径内没目标：保持原方向继续飞
                }
                Location tl = best.getEyeLocation();
                if (!al.getWorld().equals(tl.getWorld()) || al.distance(tl) <= 1.0D) {
                    continue;
                }
                a.setVelocity(tl.subtract(al).toVector().normalize().multiply(STEER_FACTOR)
                        .add(a.getVelocity().multiply(INERTIA_FACTOR)));
            }
        });
    }

    /**
     * <b>追踪箭命中时的小喷泉</b>：以命中点（方块或实体）为中心喷 50 个火焰粒子。
     *
     * <p>★ 2026-09-21 用户口径：「衍生箭矢命中后也会以命中方块或实体为中心，
     * 以喷泉状释放 50 个火焰粒子」。
     * 由 {@link MurderousLilyListener} 在本道具的追踪箭命中时调用（主线程）。
     *
     * <p>同时把该箭从追踪箭表里摘掉 —— 命中的箭马上就没了，
     * 留着条目就是泄漏（这条路径与"过期/失效"那条互为补充）。
     *
     * @param arrow    命中的那支追踪箭
     * @param hitPoint 命中点（null 时退化为箭自己的位置）
     * @return 实际喷出的粒子数
     */
    int onTrackerHit(Arrow arrow, Location hitPoint) {
        if (arrow == null) {
            return 0;
        }
        TRACKED_ARROWS.remove(arrow.getUniqueId());
        Location at = hitPoint == null ? arrow.getLocation() : hitPoint.clone();
        World world = at.getWorld();
        if (world == null) {
            return 0;
        }
        int count = TRACKER_HIT_FOUNTAIN;
        // 一次发包、球形散开：这就是"喷泉状"的最小实现（与命中点那口大喷泉同一套写法）
        world.spawnParticle(Particle.FLAME, at, count, 0.8D, 0.8D, 0.8D, 0.12D, null, true);
        return count;
    }

    /**
     * 兜底清理：实体已经不在世界上、但表里还有条目时摘掉它。
     *
     * <p>由 {@link MurderousLilyListener} 在区块卸载 / 实体离场时调用 ——
     * 这是"区块被卸载/删除"那条路径的<b>第二道</b>触发方式（第一道在 {@link #tickShot} 里）。
     */
    static void cleanupRemoved(UUID arrowId) {
        Shot shot = TRACKED_SHOTS.remove(arrowId);
        if (shot != null && shot.task != null) {
            shot.task.cancel();
        }
        TRACKED_ARROWS.remove(arrowId);
        // ★ 可能是"箭被服务端/插件移除"（/kill、区块卸载导致回收…）：
        //   这时也要把仿真结果交付出去，否则命令会一直等不到输出。
        //   ⚠ 但此刻很可能正身处 Paper 的实体管理/区块卸载流程里（例如
        //     {@code EntityRemoveFromWorldEvent}），**绝不能在这里改世界**：
        //     实测那样会抛 "Cannot update ticket level while unloading chunks or
        //     updating entity manager"。所以交付推迟到下一个 tick。
        if (shot != null) {
            final Shot done = shot;
            Bukkit.getScheduler().runTask(Touhou.getInstance(), () -> notifySim(
                    AddSlimefunItems.MURDEROUS_LILY, arrowId, done,
                    new BurstReport(FinishReason.REMOVED, "-", done.origin,
                            new Vector(0, 1, 0), 0.0D, -1, 0.0D, 0, 0, 0)));
        }
    }

    /** 某个箭矢是否已结算过（诊断用）。 */
    static boolean alreadyFinished(UUID arrowId) {
        return FINISHED.contains(arrowId);
    }

    // ------------------------------------------------------------------ 配置读取（一律带兜底）

    private int configuredMaxDistance() {
        Integer v = maxDistance == null ? null : maxDistance.getValue();
        return Math.max(1, v == null ? 120 : v);
    }

    private int configuredMaxSeconds() {
        Integer v = maxSeconds == null ? null : maxSeconds.getValue();
        return Math.max(1, v == null ? 15 : v);
    }

    private int configuredTrailParticles() {
        Integer v = trailParticles == null ? null : trailParticles.getValue();
        return v == null ? 5 : Math.max(0, v);
    }

    private int configuredLaserLength() {
        Integer v = laserLength == null ? null : laserLength.getValue();
        return Math.max(1, v == null ? 20 : v);
    }

    private double configuredLaserDiameter() {
        Double v = laserDiameter == null ? null : laserDiameter.getValue();
        return v == null ? 3.0D : Math.max(0.1D, v);
    }

    private double configuredImpactRadius() {
        Double v = impactRadius == null ? null : impactRadius.getValue();
        return v == null ? 3.0D : Math.max(0.1D, v);
    }

    private int configuredImpactDamage() {
        Integer v = impactDamage == null ? null : impactDamage.getValue();
        return v == null ? 24 : Math.max(0, v);
    }

    private int configuredFountainParticles() {
        Integer v = fountainParticles == null ? null : fountainParticles.getValue();
        return v == null ? 400 : Math.max(0, v);
    }

    private int configuredTrackerCount() {
        Integer v = trackerCount == null ? null : trackerCount.getValue();
        return v == null ? 12 : Math.max(1, v);
    }

    private float configuredTrackerDamage() {
        Integer v = trackerDamage == null ? null : trackerDamage.getValue();
        return v == null ? 12.0F : Math.max(0, v);
    }

    private int configuredTrackerRange() {
        Integer v = trackerRange == null ? null : trackerRange.getValue();
        return v == null ? 20 : Math.max(1, v);
    }

    private int configuredTrackerSeconds() {
        Integer v = trackerSeconds == null ? null : trackerSeconds.getValue();
        return v == null ? 5 : Math.max(1, v);
    }

    /** 追踪箭实速 = 基准 {@link #TRACKER_SPEED} × 倍率（默认 1.4 ⇒ 1.4 格/tick）。 */
    private float configuredTrackerSpeed() {
        Double v = trackerSpeedMultiplier == null ? null : trackerSpeedMultiplier.getValue();
        return (float) (TRACKER_SPEED * (v == null ? 1.4D : Math.max(0.05D, v)));
    }

    /** 阶段一箭矢实速 = 基准 {@link #ARROW_SPEED} × 倍率（默认 2.5 ⇒ 3.125 格/tick）。 */
    private float configuredArrowSpeed() {
        Double v = arrowSpeedMultiplier == null ? null : arrowSpeedMultiplier.getValue();
        return (float) (ARROW_SPEED * (v == null ? 2.5D : Math.max(0.05D, v)));
    }

    /** 追踪箭射出后多久开始追踪（tick）：{@code tracker-guide-delay-seconds} × 20，至少 1。 */
    private int configuredTrackerGuideTicks() {
        Double v = trackerGuideDelaySeconds == null ? null : trackerGuideDelaySeconds.getValue();
        double seconds = v == null ? 0.5D : Math.max(0.0D, v);
        return Math.max(1, (int) Math.round(seconds * 20.0D));
    }

    /**
     * 激光管壁的<b>同心环数</b>：随直径变粗而增加（直径 1 ⇒ 1 圈；直径 3 ⇒ 2 圈）。
     *
     * <p>★ 为什么需要它：直径从 1 格改成 3 格之后，只画最外圈看起来像"空心的圈"，
     * 必须填内环才像一根实心火管。
     */
    private int beamRingCount() {
        return Math.max(1, (int) Math.round(configuredLaserDiameter() / 2.0D));
    }

    /**
     * 激光可见粒子的<b>轴向取样间隔</b>（格）：{@code max(0.5, 直径/2)}。
     *
     * <p>跟着直径走：管子越粗，环与环之间越要密，否则远处看是"一节一节的虚线"。
     */
    private double beamVisualStep() {
        return Math.max(BEAM_VISUAL_STEP_MIN, configuredLaserDiameter() / 2.0D);
    }

    private int configuredTrackerParticles() {
        Integer v = trackerParticles == null ? null : trackerParticles.getValue();
        return v == null ? 2 : Math.max(0, v);
    }

    // ------------------------------------------------------------------ 诊断

    @Override
    protected List<String> describeSelf() {
        List<String> out = new ArrayList<>();
        out.add("  basicDamage     = " + basicDamage.getValue()
                + "（阶段一箭矢，普通箭矢伤害；+ " + powerAmplifier.getValue()
                + "×力量 + " + sharpnessAmplifier.getValue() + "×锋利）");
        out.add("  arrowSpeed      = " + String.format("%.3f", configuredArrowSpeed())
                + " 格/tick（基准 " + ARROW_SPEED + " × 倍率 " + arrowSpeedMultiplier.getValue()
                + "，即用户要求的「提高 150%」）");
        out.add("  maxDistance     = " + configuredMaxDistance() + " 格"
                + "（真正生效 = min(它, 模拟距离×16)）"
                + "   maxSeconds = " + configuredMaxSeconds() + " 秒");
        out.add("  销毁规则        = 距离 ≥ min(max-distance, 模拟距离×16) 或 飞行 ≥ "
                + configuredMaxSeconds() + " 秒；命中则进入阶段二（不做区块加载检测）");
        out.add("  trailParticles  = " + configuredTrailParticles() + " FLAME / tick（伴随阶段一箭矢）");
        out.add("  laser           = 长 " + configuredLaserLength() + " 格，直径 "
                + configuredLaserDiameter() + " 格（可被实体方块截断；只管几何与粒子，不结算伤害）");
        out.add("  范围伤害        = 以命中点为球心 " + configuredImpactRadius() + " 格内每个目标 "
                + configuredImpactDamage() + " 点【弹射物伤害】（护甲/无敌帧/保护插件全部生效）");
        out.add("  fountain        = " + configuredFountainParticles() + " FLAME（命中点一次撒完）");
        out.add("  trackers        = " + configuredTrackerCount() + " 支，伤害 "
                + configuredTrackerDamage() + "（普通箭矢伤害），初速 "
                + String.format("%.2f", configuredTrackerSpeed()) + " 格/tick（提高 40%），"
                + configuredTrackerGuideTicks() + " tick（" + trackerGuideDelaySeconds.getValue()
                + " 秒）后开始追踪，半径 " + configuredTrackerRange() + " 格，存活 "
                + configuredTrackerSeconds() + " 秒，粒子 " + configuredTrackerParticles()
                + " FLAME / tick / 支");
        out.add("  追踪箭命中      = 以命中点为中心喷 " + TRACKER_HIT_FOUNTAIN + " FLAME");
        out.add("  粒子估算        = 阶段一 " + configuredTrailParticles() + "/tick；阶段二瞬时 "
                + (configuredFountainParticles() + laserParticleEstimate())
                + " 个（一次性），之后 " + configuredTrackerCount() + " 支 × "
                + configuredTrackerParticles() + " = "
                + (configuredTrackerCount() * configuredTrackerParticles())
                + "/tick，持续 ≤ " + configuredTrackerSeconds() + " 秒");
        out.add("  excludeShooter  = " + shooterProtected()
                + "（true = 范围伤害与追踪索敌都排除发射者）");
        out.add("  trackedShots    = " + TRACKED_SHOTS.size() + "   阶段一追踪表（正常应为 0）");
        out.add("  trackedTrackers = " + TRACKED_ARROWS.size() + "   追踪箭表（正常应为 0）");
        return out;
    }

    /** 激光一次爆发的粒子点数（供估算打印；取样口径与 {@link #fireLaser} 严格同源）。 */
    private int laserParticleEstimate() {
        double want = configuredLaserLength();
        double step = beamVisualStep();
        int rings = 0;
        for (double d = step; d <= want + 1.0E-6; d += step) {
            rings++;
        }
        return rings * (1 + beamRingCount() * BEAM_RING_POINTS);
    }

    // ------------------------------------------------------------------ 无头仿真（/touhou lily）

    /**
     * 一次无头发射仿真的结果。
     *
     * @param arrowSpawned  箭矢是否生成
     * @param arrowId       那支箭的 UUID（命令用来确认追踪表确实清掉了它）
     * @param initialSpeed  初速标量（应等于 configuredArrowSpeed()，即基准 × 倍率）
     * @param gravity       箭矢的 {@code hasGravity()} 读数（必须是 false）
     * @param travelled     实际飞行距离（格）
     * @param elapsedTicks  实际存活 tick
     * @param report        阶段二现场读数（没命中时 reason 是终止原因、其余为 0）
     * @param trackedBefore 阶段一结束瞬间阶段一追踪表条目数（应为 0）
     * @param trackersNow   清理前追踪箭表条目数
     * @param trackedAfter  强制清理之后两张表的条目数之和（必须为 0）
     * @param lines         给命令直接打印的详细行
     */
    public record FireSim(boolean arrowSpawned, UUID arrowId, double initialSpeed, boolean gravity,
                          double travelled, long elapsedTicks, BurstReport report,
                          int absorbedHits, int trackedBefore, int trackersNow, int trackedAfter,
                          List<String> lines) {
    }

    /** 仿真回调表（键 = 阶段一箭矢 UUID）。 */
    private static final Map<UUID, SimCallback> SIM_CALLBACKS = new ConcurrentHashMap<>();

    /** 仿真状态（键 = 阶段一箭矢 UUID）：起手读数 + 已跑 tick 数。 */
    private static final Map<UUID, double[]> SIM_STATE = new ConcurrentHashMap<>();

    /**
     * <b>无头入口</b>：在指定坐标与方向<b>模拟一次完整发射</b>，跑完后回调结果。
     *
     * <p>为什么必须有它：无头环境下"无视重力 / 120 格 / 12 秒 / 命中后爆发 / 方向规则 /
     * 真伤 / 追踪清理"全都只能靠肉眼，而这些都是最容易写错的地方。
     *
     * <p>它走的是<b>与游戏内完全相同</b>的代码路径：
     * {@link #spawnShot} → 每 tick 的 {@code tickShot} → {@link #onShotHit}（监听器记录命中）
     * → {@link #burst}。不是另写一份演示。
     *
     * <p>★★ <b>为什么是回调而不是返回值</b>：仿真必须跨很多 tick（箭要真的飞出去、追踪箭要活 5 秒），
     * 而命令跑在<b>主线程</b> —— 在那儿 {@code Thread.sleep} / {@code CountDownLatch.await}
     * 会把整个服务端卡死（本任务实测踩过：主线程被阻塞 15 秒，服务端 Watchdog 直接打线程转储）。
     * 所以这里"发射完就返回"，结果通过 {@code callback} 在任务结束的那一刻交付。
     *
     * @param forceExpire 为 {@code true} 时只跑几个 tick 就强制终止
     *                    （用来验证"没命中也要清理追踪表"这条路径）
     */
    public void simulateFire(LivingEntity shooter, Location origin, Vector direction,
                             boolean forceExpire, SimCallback callback) {
        simulateFire(shooter, origin, direction, forceExpire, false, callback);
    }

    /**
     * 同上，但可以打开<b>无头跟随模式</b>。
     *
     * @param chase 为 {@code true} 时让发射者跟着箭飞（见 {@link Shot#followArrow}）——
     *              这是控制台<em>唯一</em>能观察到"箭真的在飞"的办法
     */
    public void simulateFire(LivingEntity shooter, Location origin, Vector direction,
                             boolean forceExpire, boolean chase, SimCallback callback) {
        if (origin == null || origin.getWorld() == null) {
            callback.done(emptySim(false, null, 0, false, "坐标无效"));
            return;
        }
        if (shooter == null) {
            callback.done(emptySim(false, null, 0, false,
                    "✗ 仿真需要一个发射者（命令会临时造一个生物来当）"));
            return;
        }
        Vector dir = direction.clone().normalize();
        Arrow arrow = spawnShot(shooter, origin, dir, basicDamage.getValue().floatValue());
        if (arrow == null) {
            callback.done(emptySim(false, null, 0, false, "箭矢生成失败（世界为空？）"));
            return;
        }
        UUID id = arrow.getUniqueId();
        double initialSpeed = arrow.getVelocity().length();
        boolean gravity = arrow.hasGravity();
        if (chase) {
            Shot ctx = TRACKED_SHOTS.get(id);
            if (ctx != null) {
                ctx.followArrow = true;
                ctx.followUntil = Math.max(0.0D, configuredMaxDistance() - 40.0D);
            }
        }
        SIM_CALLBACKS.put(id, callback);
        SIM_STATE.put(id, new double[]{initialSpeed, gravity ? 1.0D : 0.0D, 0.0D, 0.0D});

        List<String> lines = new ArrayList<>();
        lines.add("阶段一 · 箭矢已生成 uuid=" + id);
        lines.add("  初速方向 = " + fmt(arrow.getVelocity())
                + "   速度 = " + String.format("%.3f", initialSpeed)
                + "（期望 " + configuredArrowSpeed() + "，散布 " + ARROW_SPREAD + "）");
        lines.add("  setGravity(false) 生效 = " + (!gravity)
                + "（读回 hasGravity()=" + gravity + "）");
        lines.add("  箭矢伤害 = " + arrow.getDamage() + "（普通箭矢伤害通道）");
        lines.add("  发射者 = " + shooter.getType() + "（不伤害发射者 = " + shooterProtected() + "）");
        lines.add("  追踪表条目（发射后）= " + trackedShotCount());
        for (String line : lines) {
            Log.command("[LILY-SIM] " + line);
        }

        // 兜底：万一阶段一那条任务因为任何原因没能回调（例如实体被别的插件抢走），
        // 这里在"最大存活时间 + 2 秒"之后强制收尾并交付结果。
        long capTicks = Math.max(1, configuredMaxSeconds()) * 20L + 40L;
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!SIM_CALLBACKS.containsKey(id)) {
                    return;   // 已经交付过了
                }
                Shot shot = TRACKED_SHOTS.get(id);
                if (shot != null) {
                    endShot(arrow, shot, FinishReason.FORCED);
                } else {
                    // 阶段一已经结束但回调没交付（异常路径）：直接交付一份空读数
                    SimCallback cb = SIM_CALLBACKS.remove(id);
                    if (cb != null) {
                        cb.done(emptySim(true, id, initialSpeed, gravity, "阶段一已结束但没有读数"));
                    }
                }
            }
        }.runTaskLater(Touhou.getInstance(), forceExpire ? 6L : capTicks);
    }

    /** 空读数（起手就失败，或异常兜底时用）。 */
    private FireSim emptySim(boolean spawned, UUID id, double speed, boolean gravity, String note) {
        List<String> lines = new ArrayList<>();
        lines.add(note);
        return new FireSim(spawned, id, speed, gravity, 0, 0, null, 0, 0, 0, 0, lines);
    }

    /**
     * 仿真任务结束时交付结果（由 {@link #tickShot} / {@link #endShot} 调用）。
     *
     * <p>在这里才算"清理验证"：把两张表的状态读出来、断言归零、并把全过程拼成可 grep 的文本。
     *
     * <p>★ 参数里带 {@code self}（道具实例）是因为本方法要在 {@code static}
     * 的清理入口（{@link #cleanupRemoved}）里也能调用，而那些配置读取是实例方法。
     */
    private static void notifySim(MurderousLily self, UUID id, Shot shot, BurstReport report) {
        SimCallback cb = SIM_CALLBACKS.remove(id);
        if (cb == null) {
            return;   // 不是仿真（游戏内正常发射）
        }
        double[] state = SIM_STATE.remove(id);
        double initialSpeed = state == null ? 0.0D : state[0];
        boolean gravity = state != null && state[1] > 0.5D;
        long elapsed = state == null ? 0L : (long) state[2];

        List<String> lines = new ArrayList<>();
        double limit = self.travelLimitFor(shot, shot.origin.getWorld());
        lines.add("阶段一结束 · 原因=" + report.reason().text()
                + "   已飞 " + String.format("%.2f", shot.maxTravelled) + " 格 / " + elapsed + " tick"
                + "（距离上限 min(" + self.configuredMaxDistance() + ", 模拟距离×16) = "
                + String.format("%.2f", limit) + " 格，时间上限 "
                + self.configuredMaxSeconds() + " 秒）");
        int trackedBefore = trackedShotCount();
        lines.add("  追踪表条目（阶段一结束后）= " + trackedBefore + "（必须为 0）");
        if (report.reason() == FinishReason.HIT_BLOCK || report.reason() == FinishReason.HIT_ENTITY) {
            lines.add("  命中详情 = " + report.hitDetail());
            lines.add("  推导出的激光方向 = " + fmt(report.direction())
                    + "（规则：方块取外法线向外；实体恒向上）");
            lines.add("阶段二 · 激光实际长度 = " + String.format("%.2f", report.beamLength())
                    + " 格（上限 " + self.configuredLaserLength() + " 格）"
                    + (report.beamBlockedAt() < 0 ? "，未被截断"
                            : "，被实体方块截断于第 " + report.beamBlockedAt() + " 格"));
            lines.add("  命中点范围伤害 = " + report.impactDamage()
                    + " 点弹射物伤害（球半径 " + self.configuredImpactRadius() + " 格），"
                    + "实际扣血 " + report.damaged() + " 个实体"
                    + "（护甲/无敌帧/保护插件全部生效 —— 这条<b>不是</b>真伤）");
            lines.add("  喷泉粒子 = " + report.fountain() + "（一次性）");
            lines.add("  追踪箭 = " + report.trackers() + " 支，伤害 "
                    + self.configuredTrackerDamage() + "（普通箭矢伤害），半径 "
                    + self.configuredTrackerRange() + " 格，存活 ≤ "
                    + self.configuredTrackerSeconds() + " 秒");
        } else {
            lines.add("  结束原因 = " + report.reason().text()
                    + "  ⇒ 阶段二【不触发】（这正是「没有命中就不爆发」那条需求）");
        }

        // 清理验证：登记追踪箭的那几张表在阶段一结束后必须已经没有本发箭的条目
        int trackersNow = trackedTrackerCount();
        int trackedAfter = trackedShotCount() + trackedTrackerCount();
        lines.add("清理 · 阶段一追踪表 " + trackedBefore + " → " + trackedShotCount()
                + "   追踪箭表（本发新放的在飞）" + trackersNow);
        lines.add("清理 · 结果 = " + (trackedBefore == 0
                ? "✅ 阶段一追踪表已归零，无泄漏" : "❌ 阶段一追踪表仍有 " + trackedBefore + " 条残留！"));
        lines.add("清理 · 那支箭 uuid=" + id + " 仍在追踪表里 = "
                + TRACKED_SHOTS.containsKey(id) + "（必须为 false），已结算过 = "
                + FINISHED.contains(id));
        lines.add("防自伤 · 发射者被本道具命中的次数 = " + shot.absorbed
                + "（必须为 0 —— 激光 AABB 与追踪索敌都排除了发射者）");

        cb.done(new FireSim(true, id, initialSpeed, gravity, shot.maxTravelled, elapsed, report,
                shot.absorbed, trackedBefore, trackersNow, trackedAfter, lines));
    }

    /**
     * 只做"方向裁决 + 激光几何"的纯计算（<b>不</b>生成任何实体、<b>不</b>改动世界）。
     *
     * <p>用途：验证<b>方向规则</b>与<b>"被实体方块截断在第几格"</b>这两件事，
     * 不必真的打一箭。它与 {@link #fireLaser} 共用 {@link #laserDirection} 与
     * {@link #beamPassable}，所以量出来的长度就是实弹那条路径的长度。
     *
     * @param face 命中的方块面（{@code null} = 命中实体 ⇒ 方向恒向上）
     */
    public BeamProbe probeBeam(Location origin, BlockFace face) {
        Vector dir = laserDirection(face);
        World world = origin.getWorld();
        int want = configuredLaserLength();
        double radius = Math.max(0.1D, configuredLaserDiameter()) / 2.0D;
        int steps = 0;
        int maxSteps = (int) Math.round(want / STEP);
        for (int i = 1; i <= maxSteps; i++) {
            Location probe = origin.clone().add(dir.clone().multiply(i * STEP));
            if (!beamPassable(world, probe, radius)) {
                break;
            }
            steps = i;
        }
        double length = Math.min(want, steps * STEP);
        boolean truncated = length < want - 1.0E-6;
        return new BeamProbe(face, dir, length, truncated ? steps + 1 : -1,
                configuredImpactDamage(), configuredImpactRadius());
    }

    /**
     * 一次"激光几何探测"的结果（{@code /touhou lily beam} 用）。
     *
     * @param face         传入的命中面（{@code null} = 按实体处理）
     * @param direction    裁决出来的方向
     * @param length       实际长度（被实体方块截断后）
     * @param blockedAt    截断发生在第几格（{@code -1} = 没被截断）
     * @param damage       命中点范围伤害的数值（弹射物伤害）
     * @param impactRadius 范围伤害的球半径（格）
     */
    public record BeamProbe(BlockFace face, Vector direction, double length, int blockedAt,
                            double damage, double impactRadius) {
    }

    /**
     * 实弹测试：在指定坐标按指定面<b>真的</b>放一次激光与范围伤害
     * （含粒子、弹射物伤害、球体 AABB 结算）。
     *
     * <p>与 {@link #probeBeam} 的差别是它<b>会真的打</b>：用来验证"不伤害发射者"这条规则
     * ——把发射者自己放进球的中心，看 {@code excluded} 与 {@code damaged} 两个读数。
     *
     * <p>★ 2026-09-21：判据从"激光那条管子"改成"以命中点为球心、{@code impact-radius} 格"
     * （与 {@link #damageSphere} 同源），因为伤害现在按球结算。
     *
     * @return 现场读数（长度 / 截断格 / 球内实体数 / 被排除的发射者数 / 实际扣血的实体数）
     */
    public LaserTest testLaser(LivingEntity shooter, Location origin, BlockFace face) {
        Vector dir = laserDirection(face);
        World world = origin.getWorld();
        int want = configuredLaserLength();
        double radius = Math.max(0.1D, configuredLaserDiameter()) / 2.0D;
        int steps = 0;
        for (int i = 1; i <= (int) Math.round(want / STEP); i++) {
            if (!beamPassable(world, origin.clone().add(dir.clone().multiply(i * STEP)), radius)) {
                break;
            }
            steps = i;
        }
        double length = Math.min(want, steps * STEP);

        // 真正的伤害与"排除发射者"都由这一段产生（与游戏内同源）
        Shot probeShot = new Shot(shooter == null ? null : shooter.getUniqueId(), shooter,
                origin.clone(), dir.clone(), System.currentTimeMillis(), 0L, null, 0.0F);
        int sphereRadius = (int) Math.ceil(configuredImpactRadius());
        int inBeam = world.getNearbyEntities(origin, sphereRadius, sphereRadius, sphereRadius)
                .stream().filter(MurderousLily::isTargetable).collect(java.util.stream.Collectors
                        .toMap(Entity::getUniqueId, e -> e, (a, b) -> a)).size();
        int damaged = damageSphere(probeShot, origin);
        int excluded = Math.max(0, inBeam - damaged
                - countNonShooterInSphere(world, origin, probeShot));
        return new LaserTest(length, length < want - 1.0E-6 ? steps + 1 : -1, dir,
                inBeam, excluded, damaged, configuredImpactDamage(),
                configuredImpactRadius());
    }

    /** 球内"不是发射者"的可命中实体数（诊断用；用来把 excluded 反推出来）。 */
    private int countNonShooterInSphere(World world, Location origin, Shot ctx) {
        int r = (int) Math.ceil(configuredImpactRadius());
        Set<UUID> seen = new HashSet<>();
        int n = 0;
        for (Entity e : world.getNearbyEntities(origin, r, r, r)) {
            if (!isTargetable(e) || !seen.add(e.getUniqueId())) {
                continue;
            }
            if (e.getLocation().distanceSquared(origin) > configuredImpactRadius()
                    * configuredImpactRadius()) {
                continue;
            }
            if (isShooter(e, ctx == null ? null : ctx.shooterId)) {
                continue;
            }
            n++;
        }
        return n;
    }

    /**
     * 一次"实弹激光"的现场读数（{@code /touhou lily laser} 用）。
     *
     * @param length    实际长度（格）
     * @param blockedAt 截断格号（-1 = 没截断）
     * @param direction 方向
     * @param inBeam    光束 AABB 里的可命中实体数
     * @param excluded  其中因为是"发射者本人"而被排除的数量
     * @param damaged   实际被真伤扣血的实体数
     * @param damage    每个目标的真伤数值
     */
    public record LaserTest(double length, int blockedAt, Vector direction, int inBeam,
                            int excluded, int damaged, double damage, double impactRadius) {
    }

    /**
     * 把两张追踪表彻底收干净（诊断/命令用），返回清掉的条目数。
     *
     * <p>★ 存在的意义是"证明没有泄漏"：命令在仿真结束后调它，
     * 再读 {@link #trackedShotCount()} + {@link #trackedTrackerCount()}，必须是 0。
     *
     * <p>⚠ 这里<b>不</b>阻塞等待任何东西：它只做"现在就清空"。
     * （反例：早先的实现里有个 {@code sleepTicks(CountDownLatch.await)} 的辅助方法，
     * 在主线程上等 tick —— 那会把服务端整个卡住，已删除。）
     */
    public int clearAllTracked() {
        int n = TRACKED_SHOTS.size() + TRACKED_ARROWS.size();
        for (Shot s : TRACKED_SHOTS.values()) {
            if (s.task != null) {
                s.task.cancel();
            }
        }
        TRACKED_SHOTS.clear();
        TRACKED_ARROWS.clear();
        for (BukkitTask t : TRACKER_TASKS.values()) {
            t.cancel();
        }
        TRACKER_TASKS.clear();
        SIM_CALLBACKS.clear();
        SIM_STATE.clear();
        return n;
    }
}
