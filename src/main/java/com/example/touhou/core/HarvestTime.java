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
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 丰收之时 —— 秋姐妹的赠与，信奉丰收之人的宝物。
 *
 * <h2>做什么</h2>
 * 放在地上，<b>右键它</b>就对以自身为中心的 <b>9×9×3</b> 范围内的作物做一次强制催熟：
 * <pre>
 *   x: -4 .. +4   （长 9）
 *   z: -4 .. +4   （宽 9）
 *   y: -1 .. +1   （高 3，含机器自己那一格）
 * </pre>
 * 同时播 {@code entity.experience_orb.pickup} 音效、在范围内撒 50 个<b>绿色星状</b>粒子
 * （{@link Particle#VILLAGER_HAPPY}，判据见 {@link #spawnParticles}）。
 *
 * <h2>触发方式：为什么是 {@link BlockUseHandler} 而不是 {@code ItemUseHandler}</h2>
 * <ul>
 *   <li>{@code ItemUseHandler} 是"右键空气/方块时手上的<b>物品</b>"，判据是玩家手里拿什么 ——
 *       本机器是"右键<b>那个方块</b>"，拿什么右键都该生效，语义不对；</li>
 *   <li>{@code BlockUseHandler} 由本体 {@code SlimefunItemInteractListener#rightClickBlock}
 *       在"右键的方块是粘液方块"时回调，正好就是我们要的钩子，
 *       而且事件对象自带 {@code getClickedBlock()} / {@code getPlayer()}。</li>
 * </ul>
 *
 * <p>★ 它与本项目其它机器不冲突的一条前提：那些机器（反应堆 / 木桩 / 赛钱箱 / 接口）
 * 都靠 GUI 交互，而它们都<b>没有</b>注册 {@code BlockUseHandler} ——
 * 原因见 {@code UtsuhoReactorCore} 里那段注释（注册了它，本体的
 * {@code callItemHandler} 就恒返回 true，GUI 永远打不开）。
 * 本机器<b>刻意不做 GUI</b>（需求没要求），所以用这个钩子既正确又没有那个副作用。
 *
 * <h2>为什么不需要 {@code BlockMenuPreset}</h2>
 * 放置钩子、方块数据、右键回调<b>都不需要</b>菜单 —— 菜单只是"打开一个界面"用的。
 * 本机器没有库存、没有按钮，所以一行 preset 都不写（写了反而会多出一个空界面）。
 *
 * <h2>西瓜 / 南瓜是特例</h2>
 * 对它们的<b>茎</b>用骨粉只会把茎催到成熟、<b>不会结果</b>（原版行为）。所以除了
 * {@code applyBoneMeal}，还要手动补一步"结瓜"，见 {@link #tryGrowFruit}。
 */
public class HarvestTime extends SimpleSlimefunItem<BlockUseHandler> {

    /** 水平半径：长/宽各 9 ⇒ ±4。 */
    public static final int HORIZONTAL_RADIUS = 4;
    /** 垂直半径：高 3（y-1 .. y+1）⇒ ±1。 */
    public static final int VERTICAL_RADIUS = 1;

    /** 一次催熟撒多少个粒子（需求给定 50）。 */
    public static final int PARTICLE_COUNT = 50;

    /**
     * 对<b>同一个方块</b>最多施加几次骨粉（{@link #forceRipenStem} 的防呆上限）。
     *
     * <p>茎的 age 上限是 7，而一次骨粉只推几个点，所以 3 次足够到顶；
     * 取 8 是留足余量，同时保证"万一某版本骨粉行为异常"也不会变成死循环
     * （主线程上的死循环 = 整个服务器卡死）。
     */
    public static final int MAX_BONE_MEAL_PER_BLOCK = 8;

    /** 粒子撒布的水平半径（用机器的水平半径，保证"在催熟范围内"）。 */
    private static final double PARTICLE_SPREAD_H = HORIZONTAL_RADIUS;
    /** 粒子撒布的垂直半径（用机器的垂直半径）。 */
    private static final double PARTICLE_SPREAD_V = VERTICAL_RADIUS;

    /** 需要结瓜的茎 → 对应果实方块。 */
    private static final Map<Material, Material> STEM_FRUIT = Map.of(
            Material.MELON_STEM, Material.MELON,
            Material.PUMPKIN_STEM, Material.PUMPKIN);

    /**
     * <b>非 {@code Ageable} 但仍然是"可催熟植物"</b>的方块 —— 目前就是树苗。
     *
     * <h2>★ 为什么需要这张表（javap 实测出来的）</h2>
     * 这个 API 版本里 {@code org.bukkit.block.data.type.Sapling}
     * <b>不继承</b> {@link Ageable}，它只有 {@code stage / maximumStage}：
     * <pre>
     *   public interface Sapling extends org.bukkit.block.data.BlockData {
     *       int getStage(); void setStage(int); int getMaximumStage();
     *   }
     * </pre>
     * 而竹子 {@code Bamboo extends Ageable, Sapling} 两边都沾。
     * 所以只判 {@code instanceof Ageable} 会把<b>树苗整类漏掉</b>
     * （实测症状：{@code OAK_SAPLING} 在测试田里压根没进目标列表），
     * 这正是需求点名要覆盖的"树苗"。
     */
    private static final List<Material> SAPLINGS = List.of(
            Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING,
            Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
            Material.CHERRY_SAPLING, Material.MANGROVE_PROPAGULE, Material.AZALEA,
            Material.FLOWERING_AZALEA, Material.BAMBOO);

    /** 果实下方允许的方块（与原版"耕地上结果"一致；多加泥土/草方块以宽容一些自定义农田）。 */
    private static final List<Material> FRUIT_SUPPORTS = List.of(
            Material.FARMLAND, Material.DIRT, Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.ROOTED_DIRT);

    /** 水平四向（结果位置只在这四个方向找，与原版一致）。 */
    private static final BlockFace[] HORIZONTAL_FACES = {
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    /**
     * 每方块冷却表：方块坐标 → 上次生效的时刻（毫秒）。
     *
     * <p>★ 键用 {@link Location}（它 {@code equals} 比较世界+整数坐标，且 hashCode 一致），
     * 与项目里 {@code EchoOfAnotherWorld.LAST_SHUTTLE_MILLIS} 那种"内存表"同一路数。
     * 它<b>不持久化</b>：重启后冷却归零 —— 这正是想要的（这是防连点的节流，不是玩家资产）。
     */
    private static final Map<Location, Long> LAST_USE_MILLIS = new ConcurrentHashMap<>();

    public HarvestTime(ItemGroup itemGroup, SlimefunItemStack item,
                       RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    /**
     * 本机器唯一的交互钩子：右键方块。
     *
     * <p>流程：取方块 → 判权限 → 判冷却 → 催熟 → 音效粒子 → 提示。
     * 任何一环不通过都<b>立即返回</b>，绝不"部分生效"。
     */
    @Override
    public BlockUseHandler getItemHandler() {
        return event -> {
            Block block = clickedBlock(event);
            if (block == null) {
                return;
            }
            Player player = event.getPlayer();

            // ---- 权限：与木桩 / 赛钱箱 / 反应堆核心的 canOpen 同一条判据
            if (!canHarvest(player, block)) {
                Notify.warn(Notify.harvest(), player, "&c你没有权限催熟这片土地");
                return;
            }

            // ---- 冷却：防连点（9×9×3 扫描 + 50 粒子 + 音效不该被一次右键刷成一串）
            long left = cooldownLeft(block);
            if (left > 0) {
                Notify.warn(Notify.harvest(), player,
                        "&e丰收之时还在酝酿，请等 &f" + String.format(Locale.ROOT, "%.1f", left / 1000.0D) + " &e秒");
                return;
            }
            markUsed(block);

            // ---- 真正的催熟
            HarvestReport report = harvest(block, player == null ? "(控制台)" : player.getName());

            // ---- 反馈：音效（永远）+ 粒子（永远）+ 战斗提示
            block.getWorld().playSound(block.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F);
            spawnParticles(block);
            if (player != null) {
                // ★ 玩家可见的那一句话只由 announce() 生成一次，
                //   无头验证读的是同一个方法的返回值 ⇒ "命令验的"就是"玩家看到的"
                Notify.warn(Notify.harvest(), player, announce(report));
            }
        };
    }

    /**
     * <b>玩家可见文案的唯一出处</b>：把一次催熟的结果变成那句话。
     *
     * <p>★ 抽成独立方法是为了让它<b>可被无头验证直接调用</b>。
     * 在此之前，这句话是在右键 lambda 里内联拼的，只有真实玩家能触发 ——
     * 于是"提示说没找到、实际却催熟了"这类 bug <b>在无头环境里根本验不到</b>
     * （{@code /touhou harvest test} 走的是 {@link #harvest} 内核，碰不到这句话）。
     * 现在处理器与命令共用这一个方法，命令验的就是玩家看到的。
     *
     * <p>同一个理由也适用于 {@code LAST_SUMMARY} 的留存：它是"最近一次真实播报过什么"的证据。
     */
    public static String announce(HarvestReport report) {
        String text = report == null ? null : report.summary();
        LAST_SUMMARY = text;
        return text;
    }

    /**
     * 上一次"玩家会看到的那句话"（逐字副本，配色码尚未翻译）。
     *
     * <p>给无头验证读：它能证明<b>真实右键路径</b>到底播报了哪一支。
     */
    private static volatile String LAST_SUMMARY = null;

    /** 上一次播报过的文案（{@code null} = 还没有人右键过）。 */
    public static String lastSummary() {
        return LAST_SUMMARY;
    }

    /** 清掉"上一次播报"的记录（无头验证每轮开始前调，免得读到上一轮的）。 */
    public static void resetLastSummary() {
        LAST_SUMMARY = null;
    }

    /**
     * 走一遍<b>不依赖玩家</b>的公共路径：冷却 → 催熟 → 拼提示。
     *
     * <p>★ 与右键的唯一差别只有"谁触发"：
     * 权限那一步需要真实 {@code Player}（无头测试服没有），所以这里不跑它 ——
     * 它由 {@code /touhou harvest test} 里单独验（{@code canHarvest(null, block) == false}）。
     * 除此之外，冷却判据、催熟内核、<b>以及拼提示</b>都和右键走同一份代码。
     *
     * <p>★ 这就是"提示语"能被无头验证的原因：{@link #announce} 是唯一出处。
     *
     * @return 那句话；被冷却拦住时返回 {@code null}（并把 stage 标成 COOLDOWN）
     */
    public static PublicRun publicRun(Block block, String actor) {
        PublicRun out = new PublicRun();
        out.block = block;
        if (block == null) {
            out.stage = "NO_BLOCK";
            return out;
        }
        long left = cooldownLeft(block);
        if (left > 0) {
            out.stage = "COOLDOWN";
            out.cooldownLeft = left;
            return out;
        }
        markUsed(block);
        out.report = harvest(block, actor == null ? "(控制台)" : actor);
        out.summary = announce(out.report);
        out.stage = "RAN";
        return out;
    }

    /** {@link #publicRun} 的中间读数。 */
    public static final class PublicRun {
        public String stage = "(未执行)";
        public long cooldownLeft;
        public Block block;
        public HarvestReport report;
        public String summary;

        /** 一句话摘要（命令用）。 */
        public String describeRun() {
            StringBuilder sb = new StringBuilder("stage=").append(stage);
            if (report != null) {
                sb.append(" boneMealed=").append(report.boneMealedCount())
                        .append(" fruits=").append(report.fruitCount());
            }
            if (cooldownLeft > 0) {
                sb.append(" cooldownLeft=").append(cooldownLeft);
            }
            return sb.toString();
        }
    }

    /**
     * 右键事件里的"被点方块"。
     *
     * <p>{@code PlayerRightClickEvent#getClickedBlock()} 返回 {@code Optional}（本体在
     * "右键空气"时是空的），所以这里要兜住空值 —— 直接 {@code get()} 会在右键空气时抛异常。
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

    /**
     * 交互权限 —— 与 {@code ShrinePost#canOpen} / {@code Saizenbako#canOpen} /
     * {@code AbstractReactorPort#canOpen} 完全同一条判据（bypass 权限 或 canUse + 领地交互权），
     * 这样"没权限的玩家不能催熟别人的地"。
     */
    public boolean canHarvest(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        return player.hasPermission("slimefun.inventory.bypass")
                || (canUse(player, false) && Slimefun.getProtectionManager()
                        .hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK));
    }

    // ---------------------------------------------------------------- 催熟内核

    /**
     * 对以 {@code machine} 为中心的 9×9×3 范围做一次强制催熟。
     *
     * @param machine 机器方块（会被<b>跳过</b>，绝不把自己当作物）
     * @param actor   玩家名（写日志用；无头验证时传个标记）
     */
    public static HarvestReport harvest(Block machine, String actor) {
        HarvestReport report = new HarvestReport();
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
        // 本轮"碰过"的目标，扫描结束后统一归类（见下面的收尾分类）
        List<Block> touched = new ArrayList<>();
        // 真的被推动过的目标：收尾分类时要跳过它们
        //   （否则"已经催满"的格会被误判成"换了类型" —— 判据 needsBoneMeal 对满级格返回 false）
        java.util.Set<Block> ripened = new java.util.HashSet<>();

        for (int dx = -HORIZONTAL_RADIUS; dx <= HORIZONTAL_RADIUS; dx++) {
            for (int dz = -HORIZONTAL_RADIUS; dz <= HORIZONTAL_RADIUS; dz++) {
                for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
                    int y = cy + dy;
                    if (y < center.getWorld().getMinHeight() || y >= center.getWorld().getMaxHeight()) {
                        continue;
                    }
                    Block block = center.getWorld().getBlockAt(cx + dx, y, cz + dz);
                    // ★ 机器自己那一格在范围内，但绝不能被当成作物（它是 HAY_BLOCK，本就不会被催，
                    //   这一句是显式守卫，避免将来换材质后误伤自己）
                    if (block.equals(machine)) {
                        continue;
                    }
                    // 先判"是不是可催熟的东西"再动手：别对空气调 applyBoneMeal
                    if (!needsBoneMeal(block)) {
                        continue;
                    }
                    // ★ 所有目标都"反复施加到满级"，不只是茎：
                    //   applyBoneMeal 是【逐次判概率】的（同一次扫描里小麦只对 NORTH/EAST 有反应、
                    //   甜菜根只对 EAST/SOUTH/DOWN 有反应 —— 那是随机数，不是"面"的合法性），
                    //   所以只叫一次完全可能一个 age 都不涨。用户要的是"强制催熟"，
                    //   那就必须循环到满级（见 forceRipen）。
                    int gained = forceRipen(block);
                    if (gained > 0) {
                        report.addBoneMealed(describe(block));
                        ripened.add(block);
                    }
                    touched.add(block);
                    // ★ 茎：骨粉只到"成熟"，不会结果 —— 手动补一步（见类注释）
                    Material fruit = STEM_FRUIT.get(block.getType());
                    if (fruit != null) {
                        int[] at = tryGrowFruit(block, fruit);
                        if (at != null) {
                            report.addFruit(fruit, at[0], at[1], at[2]);
                        }
                    }
                }
            }
        }

        // 收尾分类：把"碰过但没算催熟"的目标分成两类，如实报出来
        //   ① 换了类型（典型：茎结果后变成 ATTACHED_*_STEM）—— 预期变动，不是失败
        //   ② 依然是可催熟植物却没推动 —— 那是真失败，必须报
        for (Block block : touched) {
            if (ripened.contains(block)) {
                continue;       // 已经算进"催熟"了
            }
            String desc = describe(block);
            if (!needsBoneMeal(block)) {
                report.addMoved(desc);
            } else {
                report.addUnchanged(desc + "（反复施加后仍未推动）");
            }
        }

        // 日志（受 logging.console-info 控制）：目标多时只打汇总，避免刷屏
        Log.info(report.logLine());
        if (report.boneMealed.size() > 0 && report.boneMealed.size() <= 16) {
            for (String d : report.boneMealed) {
                Log.info("[HARVEST]   催熟 " + d);
            }
        }
        for (String f : report.fruits) {
            Log.info("[HARVEST]   结果 " + f);
        }
        return report;
    }

    /**
     * 这个方块"值不值得叫骨粉"。
     *
     * <p>两条判据：
     * <ol>
     *   <li>{@code BlockData} 实现了 {@link Ageable} 且 {@code age < maximumAge}
     *       —— 覆盖作物（小麦 / 胡萝卜 / 马铃薯 / 甜菜根 / 下界疣…）、竹子、西瓜南瓜的茎；</li>
     *   <li>或者它是 {@link #SAPLINGS} 里列出的树苗类
     *       （★ 那些方块数据<b>不是</b> {@code Ageable}，见那张表的注释 ——
     *        只判 Ageable 会把树苗整类漏掉，实测撞到过）。</li>
     * </ol>
     * 已经成熟的不再叫骨粉（省掉无效调用与原版提示噪音），
     * 空气 / 石头 / 机器自己这些自然被排除。
     */
    public static boolean needsBoneMeal(Block block) {
        if (block == null) {
            return false;
        }
        // 已经"挂果"的茎（ATTACHED_MELON_STEM / ATTACHED_PUMPKIN_STEM）不再是可催熟目标：
        // 它的 age 虽然可能没满，但它此刻正托着一个果实，再叫骨粉没有意义。
        // ★ 用名字后缀判，避免为了两个常量再列一张表（attached 的茎必然是 *_STEM 且带 ATTACHED_ 前缀）
        String name = block.getType().name();
        if (name.startsWith("ATTACHED_") && name.endsWith("_STEM")) {
            return false;
        }
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable) {
            return ageable.getAge() < ageable.getMaximumAge();
        }
        // 树苗类：不是 Ageable，但只要还是那个方块就值得试（骨粉要么让它长成树、要么推进 stage）
        return SAPLINGS.contains(block.getType());
    }

    /**
     * 叫一次骨粉（{@code applyBoneMeal}），并如实报告"到底有没有变化"。
     *
     * <p>★ {@code applyBoneMeal} 返回的 boolean 是"<b>是否成功施加</b>"，
     * <b>不是</b>"age 有没有涨" —— 对已经成熟的作物它也会返回 true 却什么都不做
     * （原版对成熟作物的处理就是不消耗骨粉、但仍算"施加过"）。
     * 所以这里<b>自己比对 age 前后值</b>，只把真的跳了 age 的算进"催熟格数"，
     * 否则统计会虚高（无头验证就是靠这个数说话的）。
     */
    public static boolean applyBoneMeal(Block block) {
        return applyBoneMeal(block, BlockFace.UP);
    }

    /**
     * 同 {@link #applyBoneMeal(Block)}，但可指定骨粉的施加面。
     *
     * <p>★ 为什么要把面做成参数：无头验证会逐个面都试一遍（见 {@code /touhou harvest}），
     * 用来<b>实测</b>"选哪个面到底有没有区别、竹子在哪一面真的会被催高"，
     * 而不是只在注释里断言。默认入口仍然是 {@link BlockFace#UP}
     * —— 那也是原版玩家对脚下作物使用骨粉时客户端上报的面。
     */
    public static boolean applyBoneMeal(Block block, BlockFace face) {
        if (block == null) {
            return false;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable before)) {
            // 不是 Ageable：本来就不该走到这里（needsBoneMeal 会先拦掉）
            return false;
        }
        int beforeAge = before.getAge();

        block.applyBoneMeal(face == null ? BlockFace.UP : face);

        BlockData after = block.getBlockData();
        int nowAge = after instanceof Ageable a ? a.getAge() : -1;
        return nowAge >= 0 && nowAge != beforeAge;
    }

    /**
     * 把一个可催熟方块<b>反复施加骨粉直到满级</b>（或到达 {@link #MAX_BONE_MEAL_PER_BLOCK} 次上限）。
     *
     * <h2>★ 为什么必须循环，而不是叫一次就走（实测数据）</h2>
     * {@code Block#applyBoneMeal} 不是"叫一次必定推满"，它是<b>逐次判随机数</b>的。
     * {@code /touhou harvest rng} 在同一台服务端上各测 8 次的结果：
     * <pre>
     *   WHEAT        单次响应 8/8   age 推到 3/4/4/5/2/2/2/3（满级 7）
     *   CARROTS      单次响应 8/8   age 推到 2/3/3/5/2/3/3/3
     *   BEETROOTS    单次响应 6/8   age 推到 1/1/1/1/1/1/0/0   ← 有 2 次"叫了没反应"
     *   MELON_STEM   单次响应 8/8   age 推到 5/4/2/2/5/2/5/4
     *   PUMPKIN_STEM 单次响应 8/8   age 推到 3/5/3/3/3/3/4/3
     * </pre>
     * 两次实测都印证：<b>单次调用完全可能一个点都不推</b>（甜菜根 6/8）。
     * 所以"一次右键强制催熟"必须自己循环到满级，否则表现就是"有时催不动"。
     *
     * <p>★ {@code MAX_BONE_MEAL_PER_BLOCK} 是防呆上限：万一某个方块无论怎么叫都不涨，
     * 循环也不会变成死循环（主线程死循环 = 整个服务器卡死）。
     *
     * <p>★ 对树苗（不是 {@code Ageable}）用"方块有没有变"来判定 ——
     * 骨粉要么把它催成树（方块类型变了）、要么推进 stage；两种都算成功。
     *
     * @return 是否<b>真的</b>推动了它（用于统计"被催熟的格数"）
     */
    public static int forceRipen(Block block) {
        // ---- ① 树苗类：先试骨粉（原版正道），不行再走显式兜底
        if (SAPLINGS.contains(block.getType())) {
            return forceRipenSapling(block);
        }

        // ---- ② Ageable：循环到满级
        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable ageable)) {
            return 0;
        }
        int startAge = ageable.getAge();
        int max = ageable.getMaximumAge();
        for (int i = 0; i < MAX_BONE_MEAL_PER_BLOCK && ageable.getAge() < max; i++) {
            if (!applyBoneMeal(block, BlockFace.UP)) {
                continue;   // 这一次没推上去（随机数没过），接着试 —— 但受上限保护
            }
            BlockData now = block.getBlockData();
            if (!(now instanceof Ageable a)) {
                break;
            }
            ageable = a;
        }
        BlockData end = block.getBlockData();
        int endAge = end instanceof Ageable a ? a.getAge() : startAge;
        boolean changed = endAge != startAge;

        // ---- ③ 兜底：竹子在这台服务端上【完全不响应】applyBoneMeal（实测 0/8，见类注释）
        if (block.getType() == Material.BAMBOO) {
            changed |= forceBamboo(block);
        }
        return changed ? 1 : 0;
    }

    /**
     * 树苗的强制催熟：<b>先试骨粉（原版正道），再显式兜底</b>。
     *
     * <h2>★ 为什么需要兜底（实测数据）</h2>
     * {@code /touhou harvest rng} 里 {@code OAK_SAPLING} 单次骨粉响应 <b>0/8</b>
     * —— 在这台服务端（{@code Slimefun-2026.07} 那个 fork 的 Paper 1.20.4）上，
     * 对树苗调 {@code Block#applyBoneMeal} 一次都没推动 stage。
     * 需求明确要求"树苗也在催熟范围内"，所以骨粉推不动时补一条显式路径：
     * <ol>
     *   <li>先把 stage 顶满（等价于"成熟度拉满"）；</li>
     *   <li>再尝试<b>真的长成一棵树</b>（{@code World#generateTree}，用该树苗对应的树种）。
     *       长树要占空间、可能被地形挡住 —— 那就如实失败，只留下 stage 已满的结果。</li>
     * </ol>
     * 两条都不做的话，树苗这一整类就等于没被催熟。
     */
    private static int forceRipenSapling(Block block) {
        Material type = block.getType();
        String before = block.getBlockData().getAsString();
        boolean changed = false;

        // ① 骨粉（原版正道）：要么长成树、要么推 stage
        for (int i = 0; i < MAX_BONE_MEAL_PER_BLOCK; i++) {
            block.applyBoneMeal(BlockFace.UP);
            if (!SAPLINGS.contains(block.getType())) {
                return 1;                       // 已经长成树
            }
            if (!block.getBlockData().getAsString().equals(before)) {
                changed = true;
                break;
            }
        }

        // ② 兜底：把 stage 顶满
        BlockData data = block.getBlockData();
        if (data instanceof org.bukkit.block.data.type.Sapling sapling
                && sapling.getStage() < sapling.getMaximumStage()) {
            sapling.setStage(sapling.getMaximumStage());
            block.setBlockData(sapling, true);
            changed = true;
        }

        // ③ 兜底：真的长成一棵树（失败就算了 —— 空间不够是常态）
        org.bukkit.TreeType tree = treeTypeOf(type);
        if (tree != null) {
            boolean grew = block.getWorld().generateTree(block.getLocation(), tree);
            if (grew) {
                return 1;
            }
        }
        return changed ? 1 : 0;
    }

    /**
     * 竹子在这台服务端上是"骨粉推不动"的特例，用显式路径补上：
     * <b>先把 age 顶到满级（等价于"这丛竹子成熟了"），再尽量往上长一节新的竹竿</b>。
     *
     * <p>★ 上面那一节只有在<b>正上方是空气</b>时才长（与原版"竹子往上长"一致）；
     * 下面是骨粉/泥土/沙子等原版能长竹子的地面，这里不额外限制 —— 因为
     * "能不能长在这里"已经由玩家把竹子种下去这件事本身回答了。
     *
     * @return 是否真的改了东西
     */
    private static boolean forceBamboo(Block block) {
        boolean changed = false;
        BlockData data = block.getBlockData();
        if (data instanceof Ageable ageable && ageable.getAge() < ageable.getMaximumAge()) {
            ageable.setAge(ageable.getMaximumAge());
            block.setBlockData(ageable, true);
            changed = true;
        }
        Block above = block.getRelative(BlockFace.UP);
        if (above.getType() == Material.AIR) {
            above.setType(Material.BAMBOO, true);
            BlockData up = above.getBlockData();
            if (up instanceof org.bukkit.block.data.type.Bamboo bamboo
                    && bamboo.getAge() < bamboo.getMaximumAge()) {
                bamboo.setAge(bamboo.getMaximumAge());
                above.setBlockData(bamboo, true);
            }
            changed = true;
        }
        return changed;
    }

    /** 树苗材质 → 树种（{@code generateTree} 用）；没有对应树种就返回 null。 */
    private static org.bukkit.TreeType treeTypeOf(Material sapling) {
        return switch (sapling) {
            case OAK_SAPLING -> org.bukkit.TreeType.TREE;
            case SPRUCE_SAPLING -> org.bukkit.TreeType.REDWOOD;
            case BIRCH_SAPLING -> org.bukkit.TreeType.BIRCH;
            case JUNGLE_SAPLING -> org.bukkit.TreeType.JUNGLE;
            case ACACIA_SAPLING -> org.bukkit.TreeType.ACACIA;
            case DARK_OAK_SAPLING -> org.bukkit.TreeType.DARK_OAK;
            case CHERRY_SAPLING -> org.bukkit.TreeType.CHERRY;
            case MANGROVE_PROPAGULE -> org.bukkit.TreeType.MANGROVE;
            case AZALEA, FLOWERING_AZALEA -> org.bukkit.TreeType.AZALEA;
            default -> null;
        };
    }

    /**
     * 让西瓜 / 南瓜的茎<b>真的结出果实</b> —— 补上原版骨粉不做的那一步。
     *
     * <h2>判定条件（这是需求点名要说明的部分）</h2>
     * <ol>
     *   <li><b>茎必须已经成熟</b>（{@code age >= maximumAge}）。没成熟的茎给果实不符合原版语义，
     *       而且与"强制催熟"这件事自相矛盾。{@link #forceRipenStem} 会先把 age 推满，
     *       所以正常都会过。</li>
     *   <li><b>附近不能已经有果实</b>：检查茎的四个水平相邻格是不是已经是
     *       {@code MELON} / {@code PUMPKIN}。有就<b>跳过</b>（原版一根茎同时只维持一个果实）。</li>
     *   <li><b>落点必须是空气</b>（{@code Material.AIR}）—— 绝不覆盖任何已有方块。</li>
     *   <li><b>落点下方必须是能长瓜的地</b>：耕地 / 泥土 / 草方块 / 砂土 / 缠根泥土
     *       （{@link #FRUIT_SUPPORTS}）。这是原版"瓜长在耕地上"的宽容版，
     *       把泥土类也算进来是为了不把玩家自建的农田判死。</li>
     *   <li>四向里按 <b>北 → 东 → 南 → 西</b> 找<b>第一个</b>合法位置；四个方向都不合法就
     *       <b>放弃</b>（不强行覆盖、不报错）。</li>
     * </ol>
     *
     * <p>为什么用"先到先得"而不是随机方向：同一台机器、同一片田，两次右键应该给出一致的结果，
     * 否则无头验证没法断言坐标。原版本身也是确定性择位。
     *
     * @return 放下的果实坐标 {@code [x,y,z]}；没放成返回 {@code null}
     */
    public static int[] tryGrowFruit(Block stem, Material fruit) {
        BlockData data = stem.getBlockData();
        if (!(data instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
            return null;
        }
        for (BlockFace face : HORIZONTAL_FACES) {
            Block side = stem.getRelative(face);
            if (side.getType() == fruit) {
                return null;                 // 已经有果实了
            }
        }
        for (BlockFace face : HORIZONTAL_FACES) {
            Block side = stem.getRelative(face);
            if (side.getType() != Material.AIR) {
                continue;                    // 只放空气格，不覆盖任何东西
            }
            if (!FRUIT_SUPPORTS.contains(side.getRelative(BlockFace.DOWN).getType())) {
                continue;                    // 下方得是能长瓜的地
            }
            // ★ 只放"一个方块"：果实方块没有朝向，也不带 age，直接设类型即可
            //   （用 setType(..., true) 让周围方块更新，茎的贴花会自己接上）
            side.setType(fruit, true);
            return new int[]{side.getX(), side.getY(), side.getZ()};
        }
        return null;
    }

    // ---------------------------------------------------------------- 音效 / 粒子

    /**
     * 在催熟范围内撒 50 个<b>绿色星状</b>粒子。
     *
     * <h2>为什么是 {@link Particle#VILLAGER_HAPPY}</h2>
     * 用户要"绿色星状"。原版里的对应物就是<b>村民交易成功时头顶那种绿色小星星</b>。
     * 这个粒子在 Paper 1.20.4 的枚举名是 <b>{@code VILLAGER_HAPPY}</b>
     * —— ★ 注意与"凭记忆写"的差别：1.20.4 里<b>没有</b> {@code HAPPY_VILLAGER}
     * 这个常量（那是 1.20.5+ 才改的命名），已用 {@code javap} 在<b>运行期</b>
     * {@code paper-api-1.20.4-R0.1-SNAPSHOT.jar} 上核实过。
     * 第二候选 {@code COMPOSTER} 是"堆肥桶里冒出的绿色小点"，也偏绿，但形状是<b>点</b>、
     * 不是星，且原版语义绑在堆肥桶上；所以选 {@code VILLAGER_HAPPY}。
     *
     * <p>位置在机器上方 ±水平半径、±垂直半径的立方体内随机取 —— 保证"在催熟范围内"。
     */
    public static void spawnParticles(Block machine) {
        Location center = machine.getLocation().add(0.5D, 0.5D, 0.5D);
        machine.getWorld().spawnParticle(
                Particle.VILLAGER_HAPPY,
                center,
                PARTICLE_COUNT,
                PARTICLE_SPREAD_H, PARTICLE_SPREAD_V, PARTICLE_SPREAD_H,
                0.0D);
    }

    // ---------------------------------------------------------------- 诊断 / 工具

    /** 本机器在注册表里的 id（供命令与日志引用，避免多处硬编码字符串）。 */
    public static final String ID = "TOUHOU_SIMPLE_MACHINE_HARVEST_TIME";

    /**
     * 运行期那台机器（供 {@code /touhou harvest} 用）。
     *
     * <p>★ 刻意走<b>注册表</b>（{@code SlimefunItem.getById}）而不是直接读
     * {@code AddSlimefunItems.HARVEST_TIME}：这样命令的输出顺带证明了
     * "它真的以那个 id 注册进 Slimefun 了"，而不是只在静态字段里有个对象。
     */
    public static SlimefunItem find() {
        return SlimefunItem.getById(ID);
    }

    /** 范围边界（含端点）—— 供无头验证打印"边界格坐标"用。 */
    public static int[] bounds(Block machine) {
        Location c = machine.getLocation();
        return new int[]{
                c.getBlockX() - HORIZONTAL_RADIUS, c.getBlockX() + HORIZONTAL_RADIUS,
                c.getBlockY() - VERTICAL_RADIUS, c.getBlockY() + VERTICAL_RADIUS,
                c.getBlockZ() - HORIZONTAL_RADIUS, c.getBlockZ() + HORIZONTAL_RADIUS
        };
    }

    /** 一个坐标是否落在催熟范围内（含边界，含机器格）。 */
    public static boolean inRange(Block machine, int x, int y, int z) {
        int[] b = bounds(machine);
        return x >= b[0] && x <= b[1] && y >= b[2] && y <= b[3] && z >= b[4] && z <= b[5];
    }

    /** 方块的可读描述（类型 + age/stage），供报告与命令输出用。 */
    public static String describe(Block block) {
        BlockData data = block.getBlockData();
        String age;
        if (data instanceof Ageable a) {
            age = "age=" + a.getAge() + "/" + a.getMaximumAge();
        } else if (data instanceof org.bukkit.block.data.type.Sapling s) {
            // ★ 树苗不是 Ageable，它只有 stage（见 SAPLINGS 的注释）
            age = "stage=" + s.getStage() + "/" + s.getMaximumStage();
        } else {
            age = "-";
        }
        return block.getType() + " " + age + " @ "
                + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    /** 清空冷却表（{@code /touhou harvest cooldown clear} 用，便于反复验证）。 */
    public static int clearCooldowns() {
        int n = LAST_USE_MILLIS.size();
        LAST_USE_MILLIS.clear();
        return n;
    }

    /**
     * 记下"这台机器刚被用过"（写冷却表）。
     *
     * <p>★ 抽成公开方法是为了让<b>无头验证</b>能走与真实右键完全相同的两段式判据：
     * 先 {@link #cooldownLeft} 问"能不能用"，通过才 {@code markUsed} 记账。
     * 右键处理器里就是这两行之隔，所以命令里复刻它不会产生"命令测的是另一套逻辑"的偏差。
     */
    public static void markUsed(Block block) {
        if (block != null) {
            LAST_USE_MILLIS.put(block.getLocation(), System.currentTimeMillis());
        }
    }

    /** 当前处于冷却中的方块数。 */
    public static int coolingCount() {
        long cooldown = AddonConfig.get().harvestCooldownMillis;
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

    /** 某个方块还剩多少毫秒冷却（没冷却返回 0）。 */
    public static long cooldownLeft(Block block) {
        long cooldown = AddonConfig.get().harvestCooldownMillis;
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

    // ---------------------------------------------------------------- 结果载体

    /** 一次催熟的结果 —— 既是命令输出，也是日志来源。 */
    public static final class HarvestReport {
        private int centerX;
        private int centerY;
        private int centerZ;
        private String actor = "(未知)";
        private final List<String> boneMealed = new ArrayList<>();
        private final List<String> fruits = new ArrayList<>();
        /** 进了扫描、却<b>一个 age 都没涨</b>的目标（附诊断原因）——如实报，不粉饰。 */
        private final List<String> unchanged = new ArrayList<>();
        /**
         * 作为目标记下、但事后方块<b>换了类型</b>的格
         * （典型：茎结果后自己变成 {@code ATTACHED_*_STEM}）。
         *
         * <p>★ 这一类既不是"催熟了"也不是"没催动"，必须单独报 ——
         * 早先把它混进 {@code unchanged} 里，输出就自相矛盾了：
         * "内核自报催熟 8 格"与"目标 4 变成 ATTACHED_MELON_STEM（age 未变）"同时出现。
         */
        private final List<String> moved = new ArrayList<>();

        void addBoneMealed(String desc) {
            boneMealed.add(desc);
        }

        void addUnchanged(String desc) {
            unchanged.add(desc);
        }

        void addMoved(String desc) {
            moved.add(desc);
        }

        void addFruit(Material fruit, int x, int y, int z) {
            fruits.add(fruit + " @ " + x + "," + y + "," + z);
        }

        /** 真的跳了 age 的格数。 */
        public int boneMealedCount() {
            return boneMealed.size();
        }

        /** 结出的果实数。 */
        public int fruitCount() {
            return fruits.size();
        }

        public List<String> boneMealedDetails() {
            return new ArrayList<>(boneMealed);
        }

        public List<String> fruitDetails() {
            return new ArrayList<>(fruits);
        }

        /** 进了扫描但 age 没变的格（诊断用）。 */
        public List<String> unchangedDetails() {
            return new ArrayList<>(unchanged);
        }

        /** 作为目标记下、事后换了类型的格（例如茎结果变成 ATTACHED_*_STEM）。 */
        public List<String> movedDetails() {
            return new ArrayList<>(moved);
        }

        /** 这次催熟"有没有发生"的判据 —— 催熟的格数 + 结出的果数。 */
        public int effectiveCount() {
            return boneMealedCount() + fruitCount();
        }

        /**
         * 玩家可见的一句话 —— <b>整个插件里这句话只有这一个出处</b>
         * （右键路径与无头验证都走 {@link HarvestTime#announce}，而它调的就是本方法）。
         *
         * <p>★ 只有两支：{@link #effectiveCount()} 为 0 走"没有可以催熟"，
         * 否则走"秋姐妹已给予丰收的庇佑"。
         * 两支共用<b>同一个</b> {@link #effectiveCount()} 判据 ——
         * 早先这里分别调用 {@code boneMealedCount() == 0 && fruitCount() == 0} 判分支、
         * 又分别调用两个计数去拼串，一旦将来只改其中一处口径，
         * 就会出现"分支按 A 判、文案按 B 拼"的错位。现在只有一个判据、一个出口。
         */
        public String summary() {
            return effectiveCount() == 0 ? MSG_NOTHING : MSG_BLESSING;
        }

        /**
         * 详细读数（"催熟 N 格 / 结果 M 个"）——<b>不进玩家提示</b>，只进日志。
         *
         * <p>★ 为什么把它从提示里拆出来：用户要的提示是固定文案（{@link #MSG_BLESSING}），
         * 把计数塞进去就不再是那句原话了。计数并没丢，改由 {@link #logLine()} 与控制台承担。
         */
        public String detail() {
            if (effectiveCount() == 0) {
                return "无可催熟目标";
            }
            StringBuilder sb = new StringBuilder("催熟 ").append(boneMealedCount()).append(" 格");
            if (fruitCount() > 0) {
                sb.append("，结果 ").append(fruitCount()).append(" 个");
            }
            return sb.toString();
        }

        /** 一行纯 ASCII 数值日志（便于 grep）。 */
        public String logLine() {
            return "[HARVEST] actor=" + actor + " center=" + centerX + "," + centerY + "," + centerZ
                    + " boneMealed=" + boneMealedCount() + " fruits=" + fruitCount()
                    + " effective=" + effectiveCount();
        }
    }

    /** 什么都不用催时的那句话（无头验证按这句做断言）。 */
    public static final String MSG_NOTHING = "&e丰收之时环顾四周 —— 这片地里没有可以催熟的东西";

    /** 催熟成功时的那句话（用户口径的原文，逐字不改）。 */
    public static final String MSG_BLESSING = "&6秋姐妹已给予丰收的庇佑";
}
