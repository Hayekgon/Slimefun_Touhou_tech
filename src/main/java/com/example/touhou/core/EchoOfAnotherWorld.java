package com.example.touhou.core;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * <b>另一个世界的回响</b>（{@code TOUHOU_MATERIAL_ECHO_OF_ANOTHER_WORLD}）——
 * 一件没有界面、没有配方、也没有 ticker 的普通材料。
 *
 * <p>它之所以是一个类而不是一行 {@code new SlimefunItem(...)}，只为了下面两件事：
 * <ol>
 *   <li><b>指南页</b>：实现 {@link RecipeDisplayItem}，把「维度穿梭」这条获取方式
 *       画进粘液书（能量水晶 → 另一个世界的回响，1:1）。画法与
 *       {@link Saizenbako#getDisplayRecipes()}、反应堆核心那两页完全一致
 *       （{@link RecipePages#block} + 输入/输出两列）；</li>
 *   <li><b>获取机制的唯一实现处</b>：{@link #shuttle} 就是"玩家真的换了一个维度
 *       ⇒ 把背包里的能量水晶换成回响"的全部逻辑。事件监听器
 *       {@link EchoOfAnotherWorldListener} 只负责把事件递进来，命令
 *       {@code /touhou echo} 递的是同一个入口 —— 于是"控制台验证过的行为"
 *       就是"玩家传送时发生的行为"。</li>
 * </ol>
 *
 * <h2>为什么不叫「配方」而叫「穿梭」</h2>
 * 它<b>不是合成出来的</b>：物品的配方数组是 {@code AddSlimefunItems#noRecipe()}
 * 的 9 格全空，配方类型 {@link TouhouRecipeTypes#DIMENSION_SHUTTLE} 是
 * "只当门面、不落合成表"的类型（判据见那个类的类注释）。
 * 所以它不会出现在工作台、增强型工作台或任何多方块机器里。
 *
 * <h2>★ 侦测口径：只认"真的换了世界"</h2>
 * 用的是 {@link EchoOfAnotherWorldListener 玩家换世界事件}
 * （{@code PlayerChangedWorldEvent}），也就是说：<b>光是站在传送门方块里不算，
 * 必须真的被传送过去</b>。判定只看两个世界的 {@link World.Environment}：
 * <pre>
 *   (NORMAL, NETHER) 或 (NETHER, NORMAL)  ⇒ 穿梭成立
 *   其余（末地、同维度、自定义世界…）      ⇒ 一律不转化
 * </pre>
 * 末地传送门因此天然被排除：{@code THE_END} 不在允许的组合里。
 * 详细的候选事件对比（为什么不选 {@code PlayerPortalEvent} /
 * {@code EntityPortalEnterEvent}）写在监听器那个类的类注释里。
 *
 * <h2>★ 扫描口径（用户明确要求，别自行扩大）</h2>
 * <pre>
 *   ✅ 主背包 + 快捷栏（Bukkit 槽 0..{@value #STORAGE_SLOTS}-1，逐格读）
 *   ✅ 副手（Bukkit 槽 {@value #OFF_HAND_SLOT}）
 *   ❌ 盔甲槽 / 末影箱 / 潜影盒与任何背包类容器的【内部】（不递归展开）
 * </pre>
 *
 * <h2>★ 幂等</h2>
 * 转化是"就地改写背包内容"：全程只看 {@code POWER_CRYSTAL} 这个粘液 id，
 * 改完那一格就是回响、不再是水晶 ⇒ 同一次穿梭<b>无论被触发几次，
 * 第二次一定数出 0 个水晶、什么都不做</b>。
 * 这不是靠"记住处理过了"，而是判据本身的性质（幂等是判据的推论而不是额外的表）。
 * 唯一的额外状态是 {@link #LAST_SHUTTLE_MILLIS} 那道<b>玩家级冷却</b>
 * （见 {@link AddonConfig#echoConvertCooldownMillis}），它防的是"来回穿梭刷转化"
 * 与"同一次穿梭连发两次事件"，与幂等是两件事。
 */
public class EchoOfAnotherWorld extends SlimefunItem implements RecipeDisplayItem {

    /**
     * 上一批转化的现场报告 —— 只留一份，给无头命令回读。
     *
     * <p>与 {@code MurderousLily} 的 {@code BurstReport} 同样的用途：
     * 转化发生在"玩家传送"这个控制台看不见的时刻，把结果留成静态读数，
     * 命令就能在不进游戏的情况下证明"确实转了、转了几个、落在哪个槽"。
     * 全部写入都发生在主线程，所以用 {@code volatile} 足够。
     */
    public record ShuttleReport(Kind kind, String player, String fromWorld, String toWorld,
                                String fromEnv, String toEnv, int crystals, int echoes,
                                int dropped, int firstSlot, long at) {

        /** 一次穿梭的结局。 */
        public enum Kind {
            /** 两个世界之间确实发生了"主世界 ↔ 地狱"的穿梭，且转化了物品。 */
            CONVERTED("已转化"),
            /** 穿梭成立，但背包里一个能量水晶都没有（什么都不做）。 */
            NO_CRYSTAL("穿梭成立但没有能量水晶"),
            /** 玩家级冷却中，本次跳过（防来回穿梭刷转化）。 */
            COOLDOWN("冷却中，已跳过"),
            /** 不是"主世界 ↔ 地狱"的穿梭（末地 / 同维度 / 自定义世界…）。 */
            NOT_ELIGIBLE("不属于主世界↔地狱，不转化"),
            /** 总开关关掉了。 */
            DISABLED("功能已被配置关闭");

            private final String label;

            Kind(String label) {
                this.label = label;
            }

            public String label() {
                return label;
            }
        }

        /** 供命令/日志的一行摘要（纯 ASCII 数值在前，中文在后，便于 GBK 日志 grep）。 */
        public String summary() {
            return "kind=" + kind.name()
                    + " " + fromWorld + "(" + fromEnv + ")->" + toWorld + "(" + toEnv + ")"
                    + " crystals=" + crystals + " echoes=" + echoes
                    + " dropped=" + dropped + " firstSlot=" + firstSlot;
        }

        /** 供玩家聊天栏的多行明细。 */
        public List<String> lines() {
            List<String> out = new ArrayList<>();
            out.add("&e维度穿梭 &7" + fromWorld + " &8[" + fromEnv + "] &7→ &f"
                    + toWorld + " &8[" + toEnv + "]");
            out.add("&7判定： &f" + kind.label());
            out.add("&7转化： &f" + crystals + " &7× 能量水晶 → &f" + echoes
                    + " &7× 另一个世界的回响"
                    + (firstSlot >= 0 ? "&7（落在背包第 &f" + firstSlot + " &7格）" : ""));
            if (dropped > 0) {
                out.add("&7背包放不下 &f" + dropped + " &7个，已掉在脚下");
            }
            return out;
        }
    }

    /** 上一批转化的报告（{@code null} = 本次启动还没有发生过转化）。 */
    private static volatile ShuttleReport lastReport;

    /**
     * 玩家级冷却表：玩家 UUID → 上次转化的时刻（毫秒）。
     *
     * <p>与 {@code MurderousLily.TRACKER_HIT_DONE} 同一形态
     * （{@code ConcurrentHashMap} 的键集），但这里存的是时刻而不是"处理过了"这个事实
     * —— 因为我们要判的是"距上次转化多久"，不是"这辈子转过没有"。
     * 传送事件只在主线程触发，用并发容器只是为了"读的时候不必加锁"。
     */
    private static final Map<UUID, Long> LAST_SHUTTLE_MILLIS = new ConcurrentHashMap<>();

    /** 累计转化次数 / 累计转出的回响数（诊断用，跨玩家累加）。 */
    private static final AtomicLong TOTAL_SHUTTLES = new AtomicLong();
    private static final AtomicLong TOTAL_ECHOES = new AtomicLong();

    /**
     * 副手在 Bukkit {@link PlayerInventory} 里的槽位号。
     *
     * <p>Bukkit 的编号是固定的：{@code 0..35} 主背包 + 快捷栏、
     * {@code 36..39} 盔甲、<b>{@code 40} 副手</b>。
     * 写成常量而不是散落的字面量 —— 命令诊断、写回、报告三处引用的必须是同一个数。
     */
    public static final int OFF_HAND_SLOT = 40;

    /**
     * 主背包 + 快捷栏的槽位数（Bukkit 官方编号 {@code 0..35}）。
     *
     * <p>★ 为什么写死 36 而不是用 {@code PlayerInventory#getSize()}：后者是
     * <b>41</b>（36 主背包 + 4 盔甲 + 1 副手）。用户口径明确要求"<b>不含盔甲槽</b>"，
     * 所以这里只走 0..35，盔甲那 4 格永远不碰。
     */
    public static final int STORAGE_SLOTS = 36;

    public EchoOfAnotherWorld(io.github.thebusybiscuit.slimefun4.api.items.ItemGroup group,
                              SlimefunItemStack item, RecipeType type, ItemStack[] recipe) {
        super(group, item, type, recipe);
    }

    // ---------------------------------------------------------------- 粘液书配方页

    /**
     * 指南页底部网格：<b>能量水晶 → 另一个世界的回响</b>（1:1），再补一条机制说明。
     *
     * <p>★ 这一页是本物品"获取方式"的玩家可见出口：
     * <ul>
     *   <li>上面那 9 格网格是 {@code SlimefunItem#getRecipe()} —— 全空，
     *       因为本物品不是合成出来的；</li>
     *   <li>槽 10 显示的是配方类型 {@link TouhouRecipeTypes#DIMENSION_SHUTTLE}
     *       的图标与名字「维度穿梭」；</li>
     *   <li>底部这一页（本方法）画的就是"什么东西、变成什么、要几个"。</li>
     * </ul>
     *
     * <p>★ 与另外两页的区别：那两页的内容是<b>现场从注册表长出来</b>的
     * （祭坛遍历 {@code SaizenbakoRecipes}、反应堆遍历已注册的 {@code MachineFuel}）；
     * 本页的机制只有一条、而且是<b>写死在代码里</b>的（{@link #shuttle}），
     * 所以这里直接构造图标 —— 不存在"注册表变了书没变"的问题。
     * <b>但数量读的是配置</b>（{@link AddonConfig#echoCrystalPerEcho} /
     * {@link AddonConfig#echoEchoPerCrystal}），所以改 config.yml 之后书里的数字会跟着变
     * （指南每次开页都现调本方法）。
     */
    @Override
    public List<ItemStack> getDisplayRecipes() {
        AddonConfig cfg = AddonConfig.get();

        List<ItemStack> inputs = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();

        // 输入：能量水晶（用 Slimefun 本体的模板，角标按"几个换几个"写）
        inputs.add(RecipePages.input(SlimefunItems.POWER_CRYSTAL, 0,
                Math.max(1, cfg.echoCrystalPerEcho),
                List.of("&8拿在身上，穿过维度之门")));

        // 输出：另一个世界的回响
        outputs.add(RecipePages.output(AddItems.ECHO_OF_ANOTHER_WORLD, 0,
                Math.max(1, cfg.echoEchoPerCrystal),
                null,
                List.of("&8掉在背包里水晶原来的位置")));

        List<ItemStack> display = new ArrayList<>(RecipePages.block(inputs, outputs));

        // 机制说明（放输出列、左列留空 —— 与祭坛/反应堆那两页的收尾写法一致）
        List<String> rule = new ArrayList<>();
        rule.add("");
        rule.add("&a维度穿梭");
        rule.add("&7触发： &f玩家被传送 &7且维度发生 &f主世界 ↔ 地狱 &7的变化");
        rule.add("&7范围： &f主背包 + 快捷栏 + 副手 &7（不含盔甲/末影箱/潜影盒内部）");
        rule.add("&7换算： &f" + Math.max(1, cfg.echoCrystalPerEcho) + " &7× 能量水晶 → &f"
                + Math.max(1, cfg.echoEchoPerCrystal) + " &7× 另一个世界的回响");
        rule.add("&7冷却： &f" + (cfg.echoConvertCooldownMillis / 1000.0D) + " &7秒 / 玩家");
        rule.add("&8站在传送门方块里但没有真的被传送 ⇒ 不转化");
        rule.add("&8末地传送门与同维度移动 ⇒ 不转化");
        display.addAll(RecipePages.block(List.of(),
                List.of(RecipePages.note(Material.ECHO_SHARD, "&a维度穿梭 · 规则", rule))));

        return display;
    }

    // ---------------------------------------------------------------- 获取机制

    /**
     * 一次"维度穿梭"的裁决与转化 —— <b>本机制的唯一实现处</b>。
     *
     * <p>调用者只有两个：真实事件（{@link EchoOfAnotherWorldListener}）与
     * 无头命令（{@code /touhou echo shuttle ...}）。两边走的是<i>同一段</i>代码，
     * 所以命令输出能证明玩家传送时到底发生了什么。
     *
     * @param player 被传送的玩家
     * @param from   传送前所在的世界（{@code PlayerChangedWorldEvent#getFrom()}）
     * @param to     传送后所在的世界（{@code player.getWorld()}）
     * @param bypassCooldown {@code true} = 忽略玩家级冷却（只给无头验证用）
     * @return 本次的现场报告（{@code kind} 说明结局）
     */
    public static ShuttleReport shuttle(Player player, World from, World to, boolean bypassCooldown) {
        AddonConfig cfg = AddonConfig.get();
        String playerName = player == null ? "(null)" : player.getName();
        String fromName = from == null ? "(null)" : from.getName();
        String toName = to == null ? "(null)" : to.getName();
        String fromEnv = from == null ? "(null)" : String.valueOf(from.getEnvironment());
        String toEnv = to == null ? "(null)" : String.valueOf(to.getEnvironment());

        // ① 总开关
        if (!cfg.echoEnabled) {
            return remember(new ShuttleReport(ShuttleReport.Kind.DISABLED, playerName,
                    fromName, toName, fromEnv, toEnv, 0, 0, 0, -1, System.currentTimeMillis()));
        }
        // ② 维度组合：只认 主世界 ↔ 地狱
        if (!isShuttle(from, to) || player == null) {
            return remember(new ShuttleReport(ShuttleReport.Kind.NOT_ELIGIBLE, playerName,
                    fromName, toName, fromEnv, toEnv, 0, 0, 0, -1, System.currentTimeMillis()));
        }
        // ③ 玩家级冷却（防"在传送门里被弹回 → 再传一次"这种来回穿梭刷转化）
        long now = System.currentTimeMillis();
        if (!bypassCooldown && cfg.echoConvertCooldownMillis > 0) {
            Long last = LAST_SHUTTLE_MILLIS.get(player.getUniqueId());
            if (last != null && now - last < cfg.echoConvertCooldownMillis) {
                return remember(new ShuttleReport(ShuttleReport.Kind.COOLDOWN, playerName,
                        fromName, toName, fromEnv, toEnv,
                        countCrystalItems(player.getInventory()), 0, 0, -1, now));
            }
        }
        // ④ 转化
        Conversion done = convertInventory(player, cfg);
        if (done.converted() > 0) {
            LAST_SHUTTLE_MILLIS.put(player.getUniqueId(), now);
            TOTAL_SHUTTLES.incrementAndGet();
            TOTAL_ECHOES.addAndGet(done.echoes());
            Log.info("[ECHO] shuttle player=" + playerName
                    + " " + fromName + "(" + fromEnv + ")->" + toName + "(" + toEnv + ")"
                    + " crystals=" + done.converted() + " echoes=" + done.echoes()
                    + " dropped=" + done.dropped());
            // ★ 玩家可见反馈必须走 Notify.warn（不受档位影响、永远输出）。
            //   本机制的档位是 off（与祭坛/符卡同档），用 Notify.info 会被静默，
            //   玩家就会看到"东西莫名其妙变了"却没有任何提示。
            Notify.warn(Notify.echo(), player, "&f能量水晶 &7在穿过维度之门时被解构了 —— "
                    + "得到 &f" + done.echoes() + " &7个 &f另一个世界的回响");
            return remember(new ShuttleReport(ShuttleReport.Kind.CONVERTED, playerName,
                    fromName, toName, fromEnv, toEnv, done.converted(), done.echoes(),
                    done.dropped(), done.firstSlot(), now));
        }
        // ⑤ 穿梭成立但没东西可转（包括"同一批水晶已经转过了"这条幂等路径）
        return remember(new ShuttleReport(ShuttleReport.Kind.NO_CRYSTAL, playerName,
                fromName, toName, fromEnv, toEnv, 0, 0, 0, -1, now));
    }

    /**
     * 判定两个世界之间是不是"维度穿梭"。
     *
     * <p>判据是 {@link World.Environment} 的组合，<b>不是世界名字</b>：
     * 多世界服务器上"名字里带 nether 的普通世界"不会误判，
     * 而"自建的地狱维度（CUSTOM）"也不会被当成地狱。
     *
     * <p>末地天然被排除：{@code (NORMAL, THE_END)} / {@code (THE_END, NORMAL)} /
     * {@code (THE_END, THE_END)} 都不在允许列表里。
     */
    public static boolean isShuttle(World from, World to) {
        if (from == null || to == null) {
            return false;
        }
        World.Environment f = from.getEnvironment();
        World.Environment t = to.getEnvironment();
        return (f == World.Environment.NORMAL && t == World.Environment.NETHER)
                || (f == World.Environment.NETHER && t == World.Environment.NORMAL);
    }

    /**
     * 数<b>玩家背包</b>里有几个能量水晶（<b>不</b>改动任何东西）。
     *
     * <p>给"干跑 / 诊断 / 冷却期的报告"用 —— 与 {@link #convertInventory}
     * 走的是同一套判据（{@link #scanSlots(PlayerInventory)}），
     * 所以"能转的几个槽位"与"数出来的几个槽位"永远一致。
     *
     * <p>⚠ 它数的是<b>槽位数</b>（几个格子装着水晶），不是物品总数 ——
     * 一格装 64 个也只算 1。要"会被转掉多少个"请用 {@link #countCrystalItems}。
     * （{@code ShuttleReport.crystals} 用的是物品总数口径，即后者。）
     */
    public static int countPowerCrystals(PlayerInventory inv) {
        if (inv == null) {
            return 0;
        }
        return scanSlots(inv).size();
    }

    /**
     * 数<b>物品总数</b>：主背包 + 快捷栏 + 副手里的能量水晶一共几个
     * （一格 64 个就计 64）。
     *
     * <p>这才是"会被转掉多少"的正确口径 —— {@link Conversion#converted()} 也是物品数。
     * 上面那个 {@link #countPowerCrystals} 是槽位口径，两个别混用。
     */
    public static int countCrystalItems(PlayerInventory inv) {
        if (inv == null) {
            return 0;
        }
        int n = 0;
        for (int slot : scanSlots(inv)) {
            ItemStack it = inv.getItem(slot);
            if (it != null && it.getAmount() > 0) {
                n += it.getAmount();
            }
        }
        return n;
    }

    /**
     * 数<b>任意容器</b>里有几个能量水晶（只数不改）。
     *
     * <p>★ 为什么这一组方法收的是 {@link Inventory} 而不是 {@code PlayerInventory}：
     * 无头测试服上没有玩家，但"判据对不对、数得准不准、转完还剩几个"这三件事
     * <b>不需要玩家也能证明</b> —— 把水晶放进一个真实箱子（{@code Chest} 的
     * {@code Inventory}）里，让它走同一段实现即可。命令
     * {@code /touhou echo container} 就是这么验的。
     */
    public static int countPowerCrystals(Inventory inv) {
        if (inv == null) {
            return 0;
        }
        // ★ 玩家背包走 PlayerInventory 那条（多算副手），普通容器只数自己的格子。
        //   两个重载的判据是同一个 isPowerCrystal，所以"数出来几个"与"能转几个"一致。
        if (inv instanceof PlayerInventory pi) {
            return scanSlots(pi).size();
        }
        return scanSlots(inv).size();
    }

    /**
     * 转化内核：先扫（只看不删），再逐格改写。
     *
     * <h2>★★ 为什么必须"先扫完、再改写"（真实踩到的大坑）</h2>
     * 最初的写法是：先 {@code scanSlots(..., true)} —— 那个重载<b>顺手把命中的槽位清空</b>了 ——
     * 再对每个命中槽位 {@code inv.getItem(slot)} 读回数量来算产出。
     * 于是读回来的必然是 {@code null}（那一格刚被自己清掉），循环里那个"双保险"判定
     * 把每一格都 {@code continue} 掉：<b>水晶被删了，回响一个都没发</b>
     * （实测：3 个能量水晶凭空消失，箱子与玩家背包<b>都一样</b>）。
     *
     * <p>★ 这是一处<b>自己造成的</b>顺序错误，<b>不是</b> {@code CraftInventoryPlayer}
     * 的什么怪癖 —— 当时的箱子内核测试也是 {@code converted=0}，两边表现完全一致就是证据。
     * （本类早期版本的注释曾把它归咎于"玩家背包 {@code setItem} 后同 tick {@code getItem}
     * 返回 null"，那是误判，已改正。）
     *
     * <p>所以修法是：扫描阶段<b>只看不删</b>，数量一律在"还没动过任何格子"的时候读；
     * 然后对每个命中槽位做<b>一次</b> {@code setItem}，把"删水晶 + 发回响"合并成同一个动作
     * —— 中途不再有"这一格是空的"的中间态。
     *
     * <p>★ 这条路径的<b>唯一硬保证是"绝不删了不发货"</b>，实现方式是三条判据一起成立才动那一格：
     * <ol>
     *   <li>先算出"这一格里有多少个水晶<b>真正参与换算</b>"：
     *       {@code used = n / perCrystal * perCrystal}（只有凑得成整单位的那些）；
     *       <b>不足一个单位的部分原样留在那一格</b>；</li>
     *   <li>{@code used == 0} 或算出来产出 {@code <= 0} ⇒ <b>这一格完全不动</b>
     *       （配置成 2:1 时"只有 1 个水晶"就属于这种，原样留着 ——
     *       这里曾经写成"产出 0 就把槽位设成 null"，那是<b>真的会把水晶删掉</b>的 bug）；</li>
     *   <li>产出先尽量写回原槽；写不回原槽的部分按"背包空槽 → 掉在脚下"的顺序发出去，
     *       一个都不会蒸发（容器内核那条没有"脚下"，塞不下就记进
     *       {@link Conversion#notGiven()}，由命令判 FAIL）。</li>
     * </ol>
     *
     * @param storageSlots 命中槽位（Bukkit 官方编号）
     * @param offHand      副手是否命中；命中时槽号是 {@link #OFF_HAND_SLOT}
     * @param owner        玩家（用它的背包当"溢出接收方"、用它的位置掉东西）；
     *                     {@code null} = 容器内核那条（溢出只记账、不落世界）
     */
    private static Conversion convertSlots(Inventory inv, List<Integer> storageSlots,
                                           boolean offHand, AddonConfig cfg, Player owner) {
        int perCrystal = Math.max(1, cfg == null ? 1 : cfg.echoCrystalPerEcho);
        int perEcho = Math.max(1, cfg == null ? 1 : cfg.echoEchoPerCrystal);

        // 命中槽位（副手那一格拼在最后）
        List<Integer> all = new ArrayList<>(storageSlots);
        if (offHand) {
            all.add(OFF_HAND_SLOT);
        }

        int crystals = 0;       // 真正被消耗掉的水晶数
        int delivered = 0;      // 真正进了背包/容器的回响数
        int dropped = 0;        // 背包放不下、掉在脚下的回响数
        int notGiven = 0;       // 容器内核那条：塞不下又没处可掉的回响数（必须为 0）
        int firstSlot = -1;
        List<ItemStack> spill = new ArrayList<>();

        for (int slot : all) {
            ItemStack current = inv.getItem(slot);
            // 双保险：真读不到（或已经不是水晶）就跳过，绝不写回一个空
            if (!isPowerCrystal(current)) {
                continue;
            }
            int n = current.getAmount();
            int used = n / perCrystal * perCrystal;             // 只有整单位参与换算
            int produced = (int) Math.min(Integer.MAX_VALUE,
                    (long) used / perCrystal * perEcho);
            if (used <= 0 || produced <= 0) {
                // 凑不出一个单位 ⇒ 这一格【原样不动】（曾经的 bug 就在这里删东西）
                continue;
            }
            if (firstSlot < 0) {
                firstSlot = slot;
            }
            crystals += used;
            int remain = n - used;                              // 不足一单位的部分

            // ① 原位处理：留下 remainder；没有 remainder 时这一格让给回响
            int inPlace = 0;
            if (remain > 0) {
                ItemStack keep = current.clone();
                keep.setAmount(remain);
                inv.setItem(slot, keep);
            } else {
                inPlace = Math.min(produced, maxStackOf(AddItems.ECHO_OF_ANOTHER_WORLD));
                inv.setItem(slot, inPlace > 0 ? echoStack(inPlace) : null);
            }
            delivered += inPlace;

            // ② 写不回原槽的产出：攒起来，出了循环统一按"背包 → 脚下"发
            int rest = produced - inPlace;
            while (rest > 0) {
                int chunk = Math.min(rest, maxStackOf(AddItems.ECHO_OF_ANOTHER_WORLD));
                spill.add(echoStack(chunk));
                rest -= chunk;
            }
        }

        // ③ 溢出分配：优先塞背包空槽；塞不下才掉在脚下（容器内核那条没有"脚下"）
        //
        // ★ 这里必须【按实际入库量】记账，不能把 spill 整个算作 delivered ——
        //   {@code addItem} 返回的是"没塞进去的那部分"，所以真正入库的是
        //   {@code stack.getAmount() - 退回量}。踩过：只把"进原槽的"计入 delivered，
        //   于是溢出那部分明明进了箱子、报告却说少发了（箱子实数 66、报告写 64）。
        for (ItemStack stack : spill) {
            int offered = stack == null ? 0 : stack.getAmount();
            if (offered <= 0) {
                continue;
            }
            int rejected = 0;
            Map<Integer, ItemStack> left = inv.addItem(stack);
            for (ItemStack l : left.values()) {
                rejected += l == null ? 0 : l.getAmount();
            }
            delivered += Math.max(0, offered - rejected);
            if (rejected <= 0) {
                continue;
            }
            // 退回的那部分：玩家那条掉在脚下；容器内核那条只记账（命令据此判 FAIL）
            ItemStack back = echoStack(rejected);
            if (owner != null) {
                dropped += rejected;
                owner.getWorld().dropItemNaturally(owner.getLocation(), back);
            } else {
                notGiven += rejected;
            }
        }

        int echoes = delivered + dropped;
        if (crystals == 0) {
            return new Conversion(0, 0, 0, -1, notGiven);
        }
        return new Conversion(crystals, echoes, dropped, firstSlot, notGiven);
    }

    /** 一个物品模板的单堆上限（回响是 64，但别写死）。 */
    private static int maxStackOf(ItemStack template) {
        return template == null ? 64 : Math.max(1, template.getMaxStackSize());
    }

    /**
     * 把<b>玩家背包</b>里的能量水晶就地换成回响。
     *
     * <p>写回策略<b>刻意用"原位替换"</b>而不是"全清再发"：
     * <ol>
     *   <li>水晶和回响是一族、数量守恒（默认 N 个水晶 → N 个回响），原位替换
     *       不会打乱背包布局，也不会把玩家的物品挪到别的槽位；</li>
     *   <li>不需要"先清空再回填"那种两步操作，也就不存在"中途失败丢东西"的窗口。</li>
     * </ol>
     * 回响比"原位替换掉的那些槽位"多出来的部分（只有配置成 1:N 才会 > 0）
     * 优先塞进背包空槽，实在塞不下才掉在玩家脚下 —— 绝不静默蒸发。
     *
     * @return 现场读数（消耗了几个水晶 / 得到几个回响 / 掉在地上几个 / 第一个转化的槽位）
     */
    public static Conversion convertInventory(Player player, AddonConfig cfg) {
        if (player == null) {
            return new Conversion(0, 0, 0, -1, 0);
        }
        PlayerInventory inv = player.getInventory();
        List<Integer> slots = scanSlots(inv);
        boolean offHand = false;
        // 副手那一格在 0..35 之外，单独摘出来（槽号 40）
        for (int i = slots.size() - 1; i >= 0; i--) {
            if (slots.get(i) == OFF_HAND_SLOT && isPowerCrystal(inv.getItemInOffHand())) {
                slots.remove(i);
                offHand = true;
            }
        }
        return convertSlots(inv, slots, offHand, cfg, player);
    }

    /**
     * 把<b>任意容器</b>里的能量水晶就地换成回响（判据与玩家那条完全同源）。
     *
     * <p>比玩家那条少两件事，都是为了"只动该动的格子"：
     * <ul>
     *   <li>不扫副手（那是 {@link PlayerInventory} 才有的概念）；</li>
     *   <li>多出来的回响<b>不</b>掉在世界里 —— 塞不进容器就记进
     *       {@link Conversion#notGiven()}，否则一个箱子就能往地上吐东西。</li>
     * </ul>
     * 所以命令 {@code /touhou echo container} 能用它证明"判据 + 换算 + 幂等"
     * 三件事，而不会给世界留垃圾。
     */
    public static Conversion convertInventory(Inventory inv, AddonConfig cfg) {
        if (inv == null) {
            return new Conversion(0, 0, 0, -1, 0);
        }
        return convertSlots(inv, scanSlots(inv), false, cfg, null);
    }

    /**
     * 一次转换的读数。
     *
     * @param converted 被消耗掉的水晶数（只有凑成整单位的那些）
     * @param echoes    实际发出去的回响数（= 进背包/容器的 + 掉在脚下的）
     * @param dropped   背包放不下、掉在脚下的回响数
     * @param firstSlot 第一个转化的槽位（{@code -1} = 什么都没转）
     * @param notGiven  <b>发了但没落地</b>的回响数（容器内核塞不进容器时才有值；
     *                  正常必须为 0 —— 这是"绝不蒸发"那条保证的机器可核对出口）
     */
    public record Conversion(int converted, int echoes, int dropped, int firstSlot, int notGiven) {
    }

    // ---------------------------------------------------------------- 内部

    /**
     * 扫<b>任意容器</b>，返回该处理的槽位号（槽位号就是该 {@link Inventory} 自己的编号）。
     *
     * <p>★ 用 {@link Inventory#getItem(int) getItem(slot)} 逐格读，而<b>不是</b>
     * {@code getStorageContents()} 那个整表：整表读到的每一格可能是"读的那一刻的副本"，
     * 而 {@code getItem} 在"读 → 判 → 写"这个顺序下语义最直白
     * （踩坑经过见 {@link #convertSlots}）。
     * 逐格读对箱子是 27 次、对玩家是 36 次调用，都是本地数组访问，成本可忽略。
     */
    private static List<Integer> scanSlots(Inventory inv) {
        List<Integer> hit = new ArrayList<>();
        if (inv == null) {
            return hit;
        }
        // 箱子 27 格、玩家背包 36 格、大箱子 54 格：getSize() 就是权威尺寸
        int size = inv.getSize();
        for (int slot = 0; slot < size; slot++) {
            if (isPowerCrystal(inv.getItem(slot))) {
                hit.add(slot);
            }
        }
        return hit;
    }

    /**
     * 扫<b>玩家背包</b>，返回该处理的槽位号（Bukkit 官方编号，可直接拿去
     * {@code setItem}）。
     *
     * <p>★ 扫描口径（用户明确要求，别自行扩大）：
     * <pre>
     *   ✅ 主背包 + 快捷栏   → 槽 0..{@value #STORAGE_SLOTS}-1
     *   ✅ 副手              → 槽 {@value #OFF_HAND_SLOT}
     *   ❌ 盔甲槽            → 水晶本来也穿不上，不扫
     *   ❌ 末影箱            → 另一个 Inventory，语义上也不算"携带"
     *   ❌ 潜影盒/背包类容器 → 它们只是背包里的一个物品；其内部内容【不递归展开】，
     *                          遇到就当普通物品跳过（不是水晶，自然不转）
     *   ❌ 世界里掉着的物品实体、漏斗、其它容器
     * </pre>
     *
     * <p>实现上只走 {@code 0..35} + 副手，而<b>不</b>是遍历
     * {@code PlayerInventory#getSize()}（那是 41，会把 4 个盔甲槽也扫进去）。
     */
    private static List<Integer> scanSlots(PlayerInventory inv) {
        List<Integer> hit = new ArrayList<>();
        if (inv == null) {
            return hit;
        }
        for (int slot = 0; slot < STORAGE_SLOTS; slot++) {
            if (isPowerCrystal(inv.getItem(slot))) {
                hit.add(slot);
            }
        }
        // 副手（Bukkit 槽位号 40）：单独判，因为控制器把它放在另一个方法上
        if (isPowerCrystal(inv.getItemInOffHand())) {
            hit.add(OFF_HAND_SLOT);
        }
        return hit;
    }

    /**
     * 是不是"能量水晶"。
     *
     * <p>★ 判据是<b>粘液 id</b>（{@code POWER_CRYSTAL}）而不是 {@code Material}：
     * 只比材质的话，任何碰巧同材质的原版物品都会被算进去。
     * 用 {@link SlimefunItem#getByItem(ItemStack)} 反查 PDC 里的 id，
     * 与 {@code /touhou guide} 的可合成性核查同一套口径。
     *
     * <p>先比 {@code Material} 再比 id 只是<b>为了省一次 PDC 读</b>
     * （绝大多数槽位是空气或普通物品），判定结论完全一样 ——
     * 材质不对的绝对不是水晶。
     */
    public static boolean isPowerCrystal(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return false;
        }
        if (SlimefunItems.POWER_CRYSTAL != null
                && item.getType() != SlimefunItems.POWER_CRYSTAL.getType()) {
            return false;
        }
        SlimefunItem sf = SlimefunItem.getByItem(item);
        return sf != null && "POWER_CRYSTAL".equals(sf.getId());
    }

    /** 一个新的回响物品（每次都要 clone：模板是共享的，Slimefun 会对它 lock）。 */
    public static ItemStack echoStack(int amount) {
        ItemStack out = AddItems.ECHO_OF_ANOTHER_WORLD.clone();
        out.setAmount(Math.max(1, Math.min(amount, out.getMaxStackSize())));
        return out;
    }

    /**
     * 数容器里<b>有几个</b>「另一个世界的回响」（按物品<b>总数</b>算）。
     *
     * <p>★ 这是"产出到底有没有真的落地"的<b>独立读数</b>：它不看任何报告对象，
     * 直接数容器内容。命令 {@code /touhou echo container} 用它来判 PASS/FAIL ——
     * 只比对报告里的数字是证明不了东西的（报告自说自话也能"通过"）。
     */
    public static int countEchoItems(Inventory inv) {
        if (inv == null) {
            return 0;
        }
        int n = 0;
        int size = inv.getSize();
        for (int slot = 0; slot < size; slot++) {
            ItemStack it = inv.getItem(slot);
            if (isEcho(it) && it.getAmount() > 0) {
                n += it.getAmount();
            }
        }
        return n;
    }

    /** 数容器里回响占了<b>几堆</b>（用来证明"超过一堆时会分堆"，而不是被截断）。 */
    public static int countEchoStacks(Inventory inv) {
        if (inv == null) {
            return 0;
        }
        int n = 0;
        int size = inv.getSize();
        for (int slot = 0; slot < size; slot++) {
            if (isEcho(inv.getItem(slot))) {
                n++;
            }
        }
        return n;
    }

    /** 是不是「另一个世界的回响」（按粘液 id 判，与水晶那条同一套口径）。 */
    public static boolean isEcho(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
            return false;
        }
        SlimefunItem sf = SlimefunItem.getByItem(item);
        return sf != null && getIdSafe().equals(sf.getId());
    }

    /** 回响的物品 id（拿不到模板时返回空串，避免 NPE）。 */
    private static String getIdSafe() {
        return AddItems.ECHO_OF_ANOTHER_WORLD == null
                ? "" : AddItems.ECHO_OF_ANOTHER_WORLD.getItemId();
    }

    private static ShuttleReport remember(ShuttleReport report) {
        lastReport = report;
        return report;
    }

    /** 上一批转化的读数（{@code null} = 本次启动还没发生过）。 */
    public static ShuttleReport lastReport() {
        return lastReport;
    }

    /** 累计读数：{@code [0]=穿梭次数（有转化的）, [1]=累计转出的回响数}。 */
    public static long[] totals() {
        return new long[]{TOTAL_SHUTTLES.get(), TOTAL_ECHOES.get()};
    }

    /** 清空玩家级冷却（{@code /touhou echo cooldown clear} 用）。 */
    public static int clearCooldowns() {
        int n = LAST_SHUTTLE_MILLIS.size();
        LAST_SHUTTLE_MILLIS.clear();
        return n;
    }

    /** 当前处于冷却中的玩家数（诊断用）。 */
    public static int coolingDownCount() {
        AddonConfig cfg = AddonConfig.get();
        if (cfg.echoConvertCooldownMillis <= 0) {
            return 0;
        }
        long now = System.currentTimeMillis();
        int n = 0;
        for (Long at : LAST_SHUTTLE_MILLIS.values()) {
            if (at != null && now - at < cfg.echoConvertCooldownMillis) {
                n++;
            }
        }
        return n;
    }

    /**
     * 参数自检 —— 把"这次改动里所有能被机器核对的断言"逐条打出来。
     *
     * <p>与 {@code PartyItem#selfCheck()} 同一个用途：不进游戏、只看控制台，
     * 就能确认物品本体（材质 / 附魔光效 / 配方类型 / 配方数组）与机制参数都对。
     */
    public List<String> selfCheck() {
        AddonConfig cfg = AddonConfig.get();
        ItemStack icon = getItem();
        List<String> out = new ArrayList<>();
        out.add("id = " + getId() + "  材质 = " + icon.getType()
                + (icon.getType() == Material.ECHO_SHARD ? "（回响碎片：对）" : "（★ 不是 ECHO_SHARD）"));
        out.add("附魔光效 = " + (icon.getEnchantments().isEmpty()
                ? "★ 没有附魔 ⇒ 没有光晕"
                : icon.getEnchantments().size() + " 条附魔（挂无用附魔产生光晕）")
                + "  HIDE_ENCHANTS = "
                + (icon.getItemMeta() != null
                        && icon.getItemMeta().hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
                        ? "已设置（附魔行被藏掉，只留光晕）" : "★ 未设置"));
        out.add("物品组 = " + (getItemGroup() == null ? "(null)" : getItemGroup().getKey().toString())
                + "  配方类型 = " + (getRecipeType() == null
                        ? "(null)" : getRecipeType().getKey().toString()));
        ItemStack[] recipe = getRecipe();
        int filled = 0;
        if (recipe != null) {
            for (ItemStack it : recipe) {
                if (it != null && !it.getType().isAir()) {
                    filled++;
                }
            }
        }
        out.add("配方数组非空格数 = " + filled + "（数组长度 " + (recipe == null ? 0 : recipe.length)
                + "）⇒ " + (filled == 0 ? "不是合成品（对）" : "★ 竟然有材料，会变成可合成物品"));
        out.add("判定口径 = 玩家被传送且 (from,to) 的 Environment 属于 "
                + "{NORMAL→NETHER, NETHER→NORMAL}；末地/同维度/自定义世界一律不转化");
        out.add("换算 = " + cfg.echoCrystalPerEcho + " 水晶 → " + cfg.echoEchoPerCrystal
                + " 回响（config.yml: echo.crystal-per-echo / echo.echo-per-crystal）");
        out.add("总开关 echo.enabled = " + cfg.echoEnabled
                + "  玩家冷却 = " + cfg.echoConvertCooldownMillis + " ms");
        out.add("指南页条目数 = " + getDisplayRecipes().size()
                + "（应为 4：1 输入 + 1 输出 + 1 空位 + 1 机制说明）");
        out.add("背包扫描口径 = 槽 0.." + (STORAGE_SLOTS - 1)
                + "（主背包 + 快捷栏，逐格 getItem 读） + 副手（Bukkit 槽 " + OFF_HAND_SLOT + "）；"
                + "不含盔甲槽 / 末影箱 / 潜影盒与背包类容器内部（不递归展开）");
        ShuttleReport rep = lastReport;
        out.add("上次穿梭 = " + (rep == null ? "(本次启动还没有)" : rep.summary()));
        out.add("累计 = " + TOTAL_SHUTTLES.get() + " 次穿梭 / " + TOTAL_ECHOES.get() + " 个回响");
        return out;
    }

    /** 诊断：本机制支持的维度组合（命令与报告引用同一处文案）。 */
    public static String ruleSummary() {
        return "条件是【玩家真的被传送】且维度组合为主世界↔地狱；"
                + "侦测走 PlayerChangedWorldEvent（没传过去就不会触发）";
    }

    /** 便于诊断：把玩家背包里的槽位分布写成一行（{@code slot:amount}）。 */
    public static String describeSlots(Player player) {
        if (player == null) {
            return "(没有玩家)";
        }
        return describeSlots(player.getInventory());
    }

    /**
     * 便于诊断：把容器的槽位分布写成一行（{@code slot:amount}）。
     *
     * <p>玩家背包会走 {@link #describeSlots(PlayerInventory)} 那条重载
     * （也就是会带上副手），普通容器走这条（只数它自己的格子）。
     */
    public static String describeSlots(Inventory inv) {
        if (inv == null) {
            return "(空)";
        }
        return formatSlots(inv, scanSlots(inv), "该容器里没有能量水晶");
    }

    /** 玩家背包版：额外把副手也算进去。 */
    public static String describeSlots(PlayerInventory inv) {
        if (inv == null) {
            return "(空)";
        }
        return formatSlots(inv, scanSlots(inv), "背包里没有能量水晶");
    }

    private static String formatSlots(Inventory inv, List<Integer> slots, String emptyText) {
        if (slots.isEmpty()) {
            return "(" + emptyText + ")";
        }
        StringBuilder sb = new StringBuilder();
        for (int slot : slots) {
            ItemStack it = inv.getItem(slot);
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(slot).append(':').append(it == null ? 0 : it.getAmount());
        }
        return sb.toString();
    }
}
