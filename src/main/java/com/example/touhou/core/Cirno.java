package com.example.touhou.core;

import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 冰の妖精（琪露诺 / Cirno）—— 放在地上，<b>右键它</b>就把以自身为中心的
 * <b>9×9×9</b> 立方体里的一切冻起来。
 *
 * <h2>做什么</h2>
 * <pre>
 *   x: 中心 ±4（长 9）
 *   y: 中心 ±4（高 9）   ← ★ 注意竖向也是 ±4，与「丰收之时」的 ±1 不同
 *   z: 中心 ±4（宽 9）
 * </pre>
 * 三件事，按顺序：
 * <ol>
 *   <li><b>水 → 冰</b>：范围内所有 {@link Material#WATER} 方块（<b>水源与水流都算</b>，
 *       它们在 Bukkit 里是同一个 {@code Material}）换成 {@link Material#ICE}；</li>
 *   <li><b>缓慢 9 / 5 秒</b>（{@link PotionEffectType#SLOW}，amplifier 9，100 tick）：
 *       给范围内所有 {@code LivingEntity}（玩家 / 生物 / 盔甲架…）；</li>
 *   <li><b>聊天栏一行蓝色</b>：{@code Bakabakabakabakabakabakabakabaka}（给触发者）。</li>
 * </ol>
 *
 * <h2>触发方式：{@link BlockUseHandler}（与「丰收之时」同一路）</h2>
 * 它是"右键<b>那个方块</b>"的钩子，事件对象自带 {@code getClickedBlock()} / {@code getPlayer()}；
 * 而且本机器<b>刻意不做 GUI</b>，所以不会踩"注册了 BlockUseHandler 就永远打不开自有界面"
 * 那条坑（见 {@code UtsuhoReactorCore} 与 {@link HarvestTime} 的注释）。
 *
 * <p>★ 判据是"右键<b>玩家自己放置出来的那台机器</b>"：{@code BlockUseHandler} 只会在
 * "右键的方块是一个已注册的粘液方块"时被回调，而本物品放下之后就是那个粘液方块，
 * 所以"只在右键自己放置出来的方块时生效"是<b>结构上成立</b>的，不需要额外的记账。
 *
 * <h2>★ 水 → 冰：为什么用 {@code setType(..., false)}（不引发物理更新）</h2>
 * 这一条是<b>权衡后的选择</b>，两条路都真实可行，理由是：
 * <ol>
 *   <li><b>{@code true}（引发更新）会把水"推"回来</b>：方块更新触发的流体重算会让
 *       立方体<b>边界外</b>的水顺着坡度流进刚刚结冰的区域，于是"冻住一片水"变成
 *       "冻住一片水，然后旁边又流进来一片水"，玩家看到的是一片永远冻不完的水。
 *       毕竟我们把它冻成 {@code ICE}（不是会融化的 {@code FROSTED_ICE}），
 *       语义就是"这块水<b>已经</b>不是水了"，不该再按流体去推一遍邻居。</li>
 *   <li><b>性能</b>：一次右键最多 729 格，其中可能有大半是水。每格都带更新
 *       = 最多几百次流体/光照重算，而这台机器是"防连点冷却之外没有任何限制"的
 *       （见 {@link #cooldownLeft}），把重算量压到 0 是划得来的。</li>
 *   <li><b>代价可接受</b>：不引发更新意味着<b>红石/流体邻居不会立刻反应</b>。
 *       对本效果而言这正是想要的（免得刚冻上的冰又被水流冲开），
 *       而冰本身是稳定方块，后续玩家/slimefun 的任何真实操作都照常触发更新。</li>
 * </ol>
 *
 * <h2>★ <b>不</b>碰哪些东西（显式判据，不用 {@code isLiquid()}）</h2>
 * 判据是<b>精确的</b> {@code block.getType() == Material.WATER}，<b>不是</b>
 * "看起来像液体"那种一网打尽，否则会误伤：
 * <table border="1">
 *   <caption>不能误伤的东西</caption>
 *   <tr><th>方块</th><th>为什么不能动</th></tr>
 *   <tr><td>{@code WATER_CAULDRON}（炼药锅）</td>
 *       <td>它是<b>容器方块</b>，冻成冰会凭空毁掉玩家的炼药锅</td></tr>
 *   <tr><td>{@code KELP} / {@code KELP_PLANT} / {@code SEAGRASS} / {@code TALL_SEAGRASS}</td>
 *       <td>它们是<b>含水方块</b>（{@code Waterlogged}）—— 水只是附着状态，
 *           连它们一起冻就等于把海带/海草拔掉</td></tr>
 *   <tr><td>岩浆 / {@code LAVA}</td>
 *       <td>"冰之妖精"能力范围内没有"把岩浆变冰"这一条；而且这是另一套方块语义</td></tr>
 * </table>
 * 代码里的判据就是一行 {@code type == Material.WATER}，上面这些方块的类型
 * <b>本来就不是</b> {@code WATER}（{@code WATER_CAULDRON} 是它自己的枚举值，
 * 海带是 {@code KELP}），所以它们天然被排除；注释写在这里是为了说明
 * "为什么不写成 {@code isLiquid()}"（本 API 版本里 {@code Material} 甚至没有
 * {@code isLiquid()} 这个方法，只有 {@code isSolid()} / {@code isBlock()}，已用 javap 核实）。
 *
 * <h2>★ 药水常量名：{@code SLOW} 而不是 {@code SLOWNESS}（用 javap 在运行期 jar 上核实）</h2>
 * <pre>
 *   javap -cp paper-api-1.20.4.jar org.bukkit.potion.PotionEffectType | Select-String SLOW
 *   public static final org.bukkit.potion.PotionEffectType SLOW;
 *   public static final org.bukkit.potion.PotionEffectType SLOW_DIGGING;
 *   public static final org.bukkit.potion.PotionEffectType SLOW_FALLING;
 *   → 本版本【没有】SLOWNESS 这个常量（那是更高版本才改的名）；
 *     三个含 "SLOW" 的常量里只有 SLOW 是"缓慢"。
 * </pre>
 * 同理核实了 {@code PotionEffect} 的构造器：
 * {@code PotionEffect(PotionEffectType, int duration, int amplifier, boolean ambient, boolean particles)}
 * 确实存在（还有 6 参/7 参的版本）。所以下面用的是 5 参那条：
 * {@code ambient=true}（信标式柔和光）、{@code particles=true}（要看得见药水粒子）。
 *
 * <h2>★ 冷却：按<b>方块</b>记，不按玩家（本实现的判断）</h2>
 * 与「丰收之时」的 {@code LAST_USE_MILLIS} 同一路数 —— 键是<b>方块坐标</b>：
 * <ul>
 *   <li>语义对：冷却属于"这台机器自己"，而不是"这次是谁点的"；</li>
 *   <li>防绕过：按玩家记的话，一个玩家轮着点两台机器就能连刷（或者两个人轮流点同一台，
 *       等于没有冷却）；按方块记则"同一台机器谁点都要等"；</li>
 *   <li>与既有做法一致：{@code HarvestTime.cooldownLeft(Block)} 就是这么写的，
 *       读代码的人不需要学第二套口径。</li>
 * </ul>
 * 冷却时长默认 <b>8000 ms（8 秒）</b>，键是 {@code config.yml} 的 {@code cirno.cooldown-millis}
 * （配 0 = 关闭冷却）。冷却期间<b>什么都不做</b>（不转冰、不给缓慢、不发那句 Bakabaka），
 * 只回一条 {@link Notify#warn} 告诉玩家还剩多久。
 */
public class Cirno extends SimpleSlimefunItem<BlockUseHandler> {

    /**
     * 本机器在注册表里的 id（供命令与日志引用，避免多处硬编码字符串）。
     *
     * <p>★ 刻意走 {@link SlimefunItem#getById} 而不是直接读
     * {@code AddSlimefunItems.CIRNO}（见 {@link #find()}）：这样命令的输出顺带证明了
     * "它真的以那个 id 注册进 Slimefun 了"，而不是只在静态字段里有个对象。
     */
    public static final String ID = "TOUHOU_MATERIAL_CIRNO";

    /** 范围半径：三轴都是 ±4 ⇒ 9×9×9 立方体（中心那一格也在范围内）。 */
    public static final int RADIUS = 4;

    /**
     * 缓慢的<b>持续 tick</b>：5 秒 = 100 tick。
     *
     * <p>★ 这是<b>原版 tick</b>（20/秒），不是 Slimefun tick —— 药水时长是原版的量，
     * 与 {@code Slimefun#getTickerTask().getTickRate()} 无关，所以这里不需要换算。
     */
    public static final int SLOW_DURATION_TICKS = 100;

    /** 缓慢的<b>等级（amplifier）</b>：9 ⇒ 游戏内显示"缓慢 X"（amplifier 是 0 基的）。 */
    public static final int SLOW_AMPLIFIER = 9;

    /** 触发者会看到的那一句话（用户给定原文，<b>逐字不改</b>）。 */
    public static final String MESSAGE = "Bakabakabakabakabakabakabakabaka";

    /**
     * 那句话的颜色 —— 用户要求<b>蓝色</b>。
     *
     * <p>★ 用 {@code §9}（原版 16 色里的"蓝色"）而不是 {@code §b}（那是青色/aqua）。
     */
    public static final String MESSAGE_COLOR = "\u00a79";

    /**
     * 每方块冷却表：方块坐标 → 上次生效的时刻（毫秒）。
     *
     * <p>★ 键用 {@link Location}（它 {@code equals} 比较世界 + 整数坐标，hashCode 一致），
     * 与 {@link HarvestTime#markUsed} 同一路数。它<b>不持久化</b>：重启后冷却归零 ——
     * 这正是想要的（这是防连点的节流，不是玩家资产）。
     */
    private static final Map<Location, Long> LAST_USE_MILLIS = new ConcurrentHashMap<>();

    public Cirno(ItemGroup itemGroup, SlimefunItemStack item,
                 RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    /**
     * 本机器唯一的交互钩子：右键方块。
     *
     * <p>流程：取方块 → 判权限 → 判冷却（不通过就只发一条提示）→ 效果内核 → 聊天栏那句话。
     * <b>任何一环不通过都立即返回，绝不"部分生效"</b> —— 冷却那一步尤其重要：
     * 用户在冷却期间必须"不转冰、不给缓慢、不发那句"，一个字节都不做。
     */
    @Override
    public BlockUseHandler getItemHandler() {
        return event -> {
            Block block = clickedBlock(event);
            if (block == null) {
                return;
            }
            Player player = event.getPlayer();

            // ---- 权限：与木桩 / 赛钱箱 / 反应堆核心 / 丰收之时同一条判据
            if (!canUseHere(player, block)) {
                Notify.warn(Notify.cirno(), player, "&c你没有权限让这片水结冰");
                return;
            }

            // ---- 冷却：按方块记（见类注释）。冷却中只发提示，什么都不做。
            long left = cooldownLeft(block);
            if (left > 0) {
                Notify.warn(Notify.cirno(), player, cooldownText(left));
                return;
            }
            markUsed(block);

            // ---- 效果内核（★ 与 /touhou cirno effect 调的<b>同一个</b>方法）
            FreezeReport report = freeze(block, player == null ? "(控制台)" : player.getName());

            // ---- 那句话：蓝色，只有触发者看得到
            if (player != null) {
                // ★★ 为什么这里破例用 player.sendMessage 而不是 Notify（本文件里唯一的破例）
                //   ① 用户要求发的是"Bakabakabakabakabakabakabakabaka"这<b>一整行</b>，
                //      而 Notify 的每条消息都会在前面拼上作用域前缀
                //      （Notify.send：prefix + text），玩家看到的就成了
                //      "[冰の妖精] Bakabaka…"，不再是用户给的那句话；
                //   ② 用户明确要求"蓝色字体"，Notify 的颜色由调用方写在 text 里、
                //      前缀却另有颜色 —— 混在一起没法保证"整行就是蓝色"；
                //   ③ Notify.info 在这个项目的默认档位（important）下是<b>静默</b>的
                //      （见 modules/04 §6.2 与 Notify 的类注释），用它等于"点了没反应"；
                //      而 Notify.warn 会带上前缀，回到第 ① 条。
                //   ⇒ 结论：这一处用 player.sendMessage(颜色 + 原文)，并在注释里说明为什么破例。
                //   冷却提示与权限提示都<b>照旧走 Notify.warn</b>（那两条是"操作没成功"，
                //   正好符合 warn 的判据，也不介意带前缀）。
                player.sendMessage(MESSAGE_COLOR + MESSAGE);
            }
            Log.info(report.logLine());
        };
    }

    /**
     * <b>效果内核</b>：把以 {@code machine} 为中心的 9×9×9 立方体冻一遍。
     *
     * <p>★ 抽成 {@code public static} 是为了让<b>无头验证能调真实入口</b>
     * （{@code /touhou cirno effect}）—— 与 {@link HarvestTime#harvest} 同一路数。
     * 它<b>不</b>做冷却判据、<b>不</b>发聊天栏那句话（那两件事需要真实玩家），
     * 所以命令里验的是"效果内核"，<b>不是</b>"玩家右键那一步" —— 报告里必须写清这条。
     *
     * <p>三件事严格按顺序：先冻水，再给药水，最后没有最后（那句话在处理器里）。
     * 先冻水再给药水是有意的：这样"被冻住"与"被减速"是同一帧发生的，
     * 玩家看到的画面是"水瞬间结冰，同时自己动不了"。
     *
     * @param machine 机器方块（它自己那一格会被<b>跳过</b>，绝不被冻成冰）
     * @param actor   触发者名（进日志用；无头验证时传个标记）
     * @return 逐项读数（见 {@link FreezeReport}）
     */
    public static FreezeReport freeze(Block machine, String actor) {
        FreezeReport report = new FreezeReport();
        if (machine == null) {
            return report;
        }
        Location center = machine.getLocation();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        report.centerX = cx;
        report.centerY = cy;
        report.centerZ = cz;
        report.actor = actor == null ? "(未知)" : actor;
        int minY = center.getWorld().getMinHeight();
        int maxY = center.getWorld().getMaxHeight();

        // ---------- ① 水 → 冰 ----------
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                int y = cy + dy;
                if (y < minY || y >= maxY) {
                    continue;
                }
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    Block block = center.getWorld().getBlockAt(cx + dx, y, cz + dz);
                    // ★ 机器自己那一格在范围内，但它绝不是水（它是头颅方块）；
                    //   这一句是显式守卫 —— 将来若有人把材质换成别的东西也不会误伤自己。
                    if (block.equals(machine)) {
                        continue;
                    }
                    if (!isFreezableWater(block)) {
                        continue;
                    }
                    // ★ setType(..., false)：不引发物理更新 —— 判据见类注释
                    //   （否则边界外的水会顺着更新流回来，"冻不完"）。
                    block.setType(Material.ICE, false);
                    report.addFrozen(block.getX(), block.getY(), block.getZ());
                }
            }
        }

        // ---------- ② 范围内的 LivingEntity：缓慢 9 / 5 秒 ----------
        // ★ 判据用"实体的方块坐标落在 ±4 内"，与上面那层方块扫描<b>同一套边界</b>
        //   （不用 Location#distance，那是球体；用户口径是立方体）。
        for (Entity entity : center.getWorld().getEntities()) {
            if (!(entity instanceof LivingEntity living)) {
                continue;               // 非 Living 的实体没有药水效果，跳过即可
            }
            if (!inRange(center, entity.getLocation())) {
                continue;
            }
            // ★ 先把同类效果清掉再挂：否则"先前的缓慢 I 还剩 30 秒"会与本次的缓慢 9 并存，
            //   玩家读到的可能不是我们刚给的那一条（本效果的口径是"整片区域统一缓慢 9 / 5 秒"）。
            living.removePotionEffect(PotionEffectType.SLOW);
            PotionEffect effect = new PotionEffect(
                    PotionEffectType.SLOW, SLOW_DURATION_TICKS, SLOW_AMPLIFIER, true, true);
            boolean applied = living.addPotionEffect(effect, true);
            report.addStunned(living, applied);
        }

        return report;
    }

    // ---------------------------------------------------------------- 判据

    /**
     * 这一格是不是"该被冻成冰的水"。
     *
     * <p>★ 判据是<b>精确的</b> {@code == Material.WATER}（水源与水流在 Bukkit 里是同一个
     * {@code Material}，所以两者自动都覆盖），<b>不是</b> {@code isLiquid()} 那种一网打尽 ——
     * 那会误伤炼药锅（{@code WATER_CAULDRON}）与水下的含水方块（海带 / 海草）以及岩浆。
     * 详细理由见类注释里那张表。
     */
    public static boolean isFreezableWater(Block block) {
        return block != null && block.getType() == Material.WATER;
    }

    /** 一个位置是否落在以 {@code center} 为心的 9×9×9 立方体内（<b>含</b>端点）。 */
    public static boolean inRange(Location center, Location at) {
        if (center == null || at == null || center.getWorld() == null || at.getWorld() == null) {
            return false;
        }
        if (!center.getWorld().equals(at.getWorld())) {
            return false;
        }
        int dx = at.getBlockX() - center.getBlockX();
        int dy = at.getBlockY() - center.getBlockY();
        int dz = at.getBlockZ() - center.getBlockZ();
        return Math.abs(dx) <= RADIUS && Math.abs(dy) <= RADIUS && Math.abs(dz) <= RADIUS;
    }

    /** 范围边界（含端点，{@code [minX,maxX,minY,maxY,minZ,maxZ]}）—— 供无头验证打印用。 */
    public static int[] bounds(Block machine) {
        Location c = machine.getLocation();
        return new int[]{
                c.getBlockX() - RADIUS, c.getBlockX() + RADIUS,
                c.getBlockY() - RADIUS, c.getBlockY() + RADIUS,
                c.getBlockZ() - RADIUS, c.getBlockZ() + RADIUS
        };
    }

    /**
     * 交互权限 —— 与 {@code ShrinePost#canOpen} / {@code Saizenbako#canOpen} /
     * {@code AbstractReactorPort#canOpen} / {@link HarvestTime#canHarvest} 完全同一条判据。
     */
    public boolean canUseHere(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        return player.hasPermission("slimefun.inventory.bypass")
                || (canUse(player, false) && Slimefun.getProtectionManager()
                        .hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK));
    }

    /**
     * 右键事件里的"被点方块"。
     *
     * <p>{@code PlayerRightClickEvent#getClickedBlock()} 返回 {@code Optional}
     * （右键空气时是空的），所以要兜住空值 —— 直接 {@code get()} 会抛异常。
     */
    private static Block clickedBlock(PlayerRightClickEvent event) {
        if (event == null) {
            return null;
        }
        try {
            return event.getClickedBlock().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- 冷却（按方块）

    /** 冷却文案（还剩多久）——<b>整个插件里这句话只有这一个出处</b>，命令核对时也读它。 */
    public static String cooldownText(long leftMillis) {
        return "&b冰の妖精还在聚集寒气，请等 &f"
                + String.format(Locale.ROOT, "%.1f", Math.max(0L, leftMillis) / 1000.0D) + " &b秒";
    }

    /**
     * 记下"这台机器刚被用过"（写冷却表）。
     *
     * <p>★ 抽成公开方法是为了让<b>无头验证</b>能走与真实右键完全相同的两段式判据：
     * 先 {@link #cooldownLeft} 问"能不能用"，通过才 {@code markUsed} 记账
     * （右键处理器里就是这两行之隔）。
     */
    public static void markUsed(Block block) {
        if (block != null) {
            LAST_USE_MILLIS.put(block.getLocation(), System.currentTimeMillis());
        }
    }

    /** 某个方块还剩多少毫秒冷却（没冷却返回 0）。 */
    public static long cooldownLeft(Block block) {
        long cooldown = AddonConfig.get().cirnoCooldownMillis;
        if (cooldown <= 0 || block == null) {
            return 0L;
        }
        Long last = LAST_USE_MILLIS.get(block.getLocation());
        if (last == null) {
            return 0L;
        }
        long left = cooldown - (System.currentTimeMillis() - last);
        return Math.max(0L, left);
    }

    /** 清空冷却表（诊断命令用，便于反复验证）。 */
    public static int clearCooldowns() {
        int n = LAST_USE_MILLIS.size();
        LAST_USE_MILLIS.clear();
        return n;
    }

    /** 当前处于冷却中的方块数。 */
    public static int coolingCount() {
        long cooldown = AddonConfig.get().cirnoCooldownMillis;
        if (cooldown <= 0) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int n = 0;
        for (Long t : LAST_USE_MILLIS.values()) {
            if (t != null && now - t < cooldown) {
                n++;
            }
        }
        return n;
    }

    // ---------------------------------------------------------------- 诊断

    /**
     * 运行期那台机器（供 {@code /touhou cirno} 用）。
     *
     * <p>★ 刻意走<b>注册表</b>（{@link SlimefunItem#getById}）而不是直接读
     * {@code AddSlimefunItems.CIRNO}：这样命令的输出顺带证明了"它真的以那个 id
     * 注册进 Slimefun 了"，而不是只在静态字段里有个对象。
     */
    public static SlimefunItem find() {
        return SlimefunItem.getById(ID);
    }

    // ---------------------------------------------------------------- 结果载体

    /** 一次"冻住"的结果 —— 既是命令输出，也是日志来源。 */
    public static final class FreezeReport {

        private int centerX;
        private int centerY;
        private int centerZ;
        private String actor = "(未知)";
        /** 被冻成冰的格子（"x,y,z"）。 */
        private final List<String> frozen = new ArrayList<>();
        /** 被挂了缓慢的 LivingEntity（"类型@x,y,z 缓慢等级/时长，applied=…"）。 */
        private final List<String> stunned = new ArrayList<>();

        void addFrozen(int x, int y, int z) {
            frozen.add(x + "," + y + "," + z);
        }

        void addStunned(LivingEntity entity, boolean applied) {
            PotionEffect eff = entity.getPotionEffect(PotionEffectType.SLOW);
            stunned.add(entity.getType() + " @" + entity.getLocation().getBlockX() + ","
                    + entity.getLocation().getBlockY() + "," + entity.getLocation().getBlockZ()
                    + " applied=" + applied
                    + (eff == null ? " 读回=(无缓慢)"
                            : " 读回=" + eff.getAmplifier() + "级/" + eff.getDuration() + "tick"));
        }

        /** 被冻成冰的方块数（水源 + 水流都算）。 */
        public int frozenCount() {
            return frozen.size();
        }

        /** 被挂上缓慢的 LivingEntity 数。 */
        public int stunnedCount() {
            return stunned.size();
        }

        public List<String> frozenDetails() {
            return new ArrayList<>(frozen);
        }

        public List<String> stunnedDetails() {
            return new ArrayList<>(stunned);
        }

        /** 一行纯 ASCII 数值日志（便于 grep）。 */
        public String logLine() {
            return "[CIRNO] actor=" + actor + " center=" + centerX + "," + centerY + "," + centerZ
                    + " frozen=" + frozenCount() + " stunned=" + stunnedCount();
        }
    }
}
