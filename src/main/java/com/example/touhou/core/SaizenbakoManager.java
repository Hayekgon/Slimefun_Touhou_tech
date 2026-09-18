package com.example.touhou.core;

import com.example.touhou.Touhou;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「赛钱箱」这台多方块机器的<b>逻辑层</b> —— 结构激活 / 木桩编号 / 镜像 / 运作。
 *
 * <h2>为什么单独成类（照反应堆那套分工）</h2>
 * {@link Saizenbako} 负责"方块与界面"（PowerComponent、GUI 布局、按键），
 * 本类负责"机器怎么跑"。反应堆那边是 {@code UtsuhoReactorCore} + {@link ReactorManager}，
 * 这里保持同一种分工，理由也一样：界面代码与逻辑代码的改动节奏完全不同，
 * 混在一起后每次调数值都要动 GUI 那个大类。
 *
 * <h2>整条链路（每轮运作 = {@code work()}）</h2>
 * <pre>
 *   ① 结构复检（节流，默认每 5 轮）：不完整 ⇒ 停机 + 清木桩编号
 *   ② 镜像：读 6 根木桩 IO 槽 → 写核心 6 个预留槽（预留槽是只读镜像，玩家碰不到）
 *   ③ 配方匹配：6 个预留槽同时命中某条配方 → 继续
 *   ④ 预检：输出槽放得下、POWER 够（否则只写"原因"到信息槽，什么都不动）
 *   ⑤ 提交：消耗木桩里的材料 → 扣 POWER → 产物进输出槽 → 立刻再镜像一次
 * </pre>
 *
 * <h2>★ 三条不能破的规矩</h2>
 * <ol>
 *   <li><b>永远不会自动激活</b>：全类里唯一的"激活"入口是 {@link #activate(Location)}
 *       （由玩家点核心 GUI 的信息格、或控制台命令调用）。{@link #tick} 只会"让机器停下来"，
 *       绝不会把它打开 —— 需求明确要求"只能手动激活，不允许自动构建"；</li>
 *   <li><b>先预检、后提交</b>：材料、电力、输出空间三项全部通过才动数据，
 *       否则会出现"材料扣了、电扣了、产物没地方放"的半成品状态；</li>
 *   <li><b>消耗的是木桩里的真实物品，不是镜像</b>：镜像只是显示，
 *       玩家唯一的投料口是木桩的 IO 槽（预留槽被锁死，谁都放不进去）。</li>
 * </ol>
 */
public final class SaizenbakoManager {

    /** 运行期缓存：核心坐标 → 计数器（{@link TouhouData#norm} 归一化后当键）。 */
    private static final Map<Location, Machine> MACHINES = new ConcurrentHashMap<>();

    /** 每台机器的运行期计数器（不落盘：重启后从 0 开始，最多多跑一次运作，无副作用）。 */
    private static final class Machine {
        /** 上次运作的服务器 tick（-1 = 还没跑过）。 */
        private long lastWorkTick = -1L;
        /** 已运作轮数（用于复检节流）。 */
        private int passes;
        /** 上次写进信息槽的结论（避免每轮重复落盘）。 */
        private String note;
        /** 上次的结论是不是"成功产出"（决定待机结论要不要覆盖它）。 */
        private boolean success;
        /** 上一次已知的朝向（避免每轮读方块数据）。 */
        private ReactorStructure.Direction direction;
    }

    /** 成功产出结论的前缀（{@link #noteIdle} 用它判断"要不要保留上一条"）。 */
    private static final String CRAFT_MARK = "&a✔ 产出";

    private SaizenbakoManager() {
    }

    /**
     * 坐标可用吗（非空 + 有世界）。
     *
     * <p>与 {@link TouhouData} 里那条判据同源：{@code StorageCacheUtils}
     * 第一步就要 {@code loc.getWorld()} 拿区块键，所以这两条是它的硬前提。
     * 本类所有对外方法都先过一遍它 —— 界面构造期传进来的 {@code null}
     * 会一路走到这里，绝不能漏到 Slimefun 的 API 里去。
     */
    private static boolean usable(Location loc) {
        return loc != null && loc.getWorld() != null;
    }

    private static Machine machine(Location core) {
        return MACHINES.computeIfAbsent(TouhouData.norm(core), k -> new Machine());
    }

    /** 丢弃某台机器的运行期记录（核心被拆、配置重载）。 */
    public static void forget(Location core) {
        Location key = TouhouData.norm(core);
        if (key != null) {
            MACHINES.remove(key);
        }
    }

    /** 配置重载时清空全部运行期缓存。 */
    public static void reload() {
        MACHINES.clear();
    }

    // ================================================================ tick 入口

    /**
     * 每 tick 的入口（由 {@link Saizenbako#onPowerTick} 调用；主线程）。
     *
     * <p>三件事按顺序：
     * <ol>
     *   <li>未激活 ⇒ 只刷新界面（绝不做任何"自动激活"）；</li>
     *   <li>按 {@code saizenbako.machine.work-interval-ticks} 节流；</li>
     *   <li>跑一轮 {@link #work}。</li>
     * </ol>
     */
    public static void tick(Block b, BlockMenu inv) {
        if (b == null) {
            return;
        }
        Location core = b.getLocation();
        if (inv == null || !TouhouData.isReady(core)) {
            return;                         // 方块数据还没加载好，下一 tick 再来
        }
        try {
            if (!isActivated(core)) {
                refreshViewers(core, inv);
                return;
            }
            Machine m = machine(core);
            long now = Bukkit.getCurrentTick();
            int interval = Math.max(1, AddonConfig.get().saizenWorkIntervalTicks);
            if (m.lastWorkTick >= 0 && now - m.lastWorkTick < interval) {
                return;
            }
            m.lastWorkTick = now;
            work(core, inv, m);
        } catch (RuntimeException e) {
            // 不能让异常把 ticker 打死（本体对 ticker 异常的处理是移除方块）
            Touhou.getInstance().getLogger().warning("[赛钱箱] tick 异常 @ "
                    + TouhouData.xyz(core) + ": " + e);
        }
    }

    /**
     * 手动推 {@code n} 轮运作 —— <b>无头验证专用</b>。
     *
     * <p>为什么需要它：真实 ticker 只在服务器里跑，控制台命令想验证"投料 → 产出"
     * 必须能自己推时间线（与反应堆的 {@code simulateTicks} 同一个理由）。
     * 节流在这里<b>故意不生效</b>：推 n 轮就是 n 轮。
     */
    public static List<String> simulate(Location core, int n) {
        List<String> out = new ArrayList<>();
        BlockMenu inv = core == null ? null : StorageCacheUtils.getMenu(core);
        if (inv == null) {
            out.add("× 拿不到核心界面（方块数据/菜单未就绪）");
            return out;
        }
        // ★ 未激活就什么都不做：需求要求「核心激活后」才读预留槽、才产出。
        //   如果调试推 tick 能绕过这道门，那"手动激活"就成了摆设，
        //   无头验证出来的行为也就不是玩家看到的行为。
        if (!isActivated(core)) {
            out.add("× 未激活：机器不运作（先 /touhou saizen <x> <y> <z> activate）");
            return out;
        }
        Machine m = machine(core);
        for (int i = 0; i < Math.max(1, n); i++) {
            work(core, inv, m);
            out.add("第 " + (i + 1) + " 轮：" + (m.note == null ? "(无结论)" : m.note));
        }
        refreshViewers(core, inv);
        return out;
    }

    // ================================================================ 一轮运作

    private static void work(Location core, BlockMenu inv, Machine m) {
        SaizenbakoStructure st = SaizenbakoStructure.get();
        AddonConfig cfg = AddonConfig.get();

        // ★ 第二道防线：未激活就什么都不做（需求：核心【激活后】才读预留槽、才产出）。
        //   tick 路径本来就拦着（见 tick）；这里再拦一次，是为了让"停机"成为
        //   结构性保证 —— 例如停机之后又被调试推 tick，也绝不会偷偷产出。
        if (!isActivated(core)) {
            m.note = "&7未激活：机器不运作";
            return;
        }

        // 界面正在被移除 / 锁定：这一轮什么都别做（pushItem/consumeItem 会抛异常）
        if (inv.locked()) {
            return;
        }

        ReactorStructure.Direction dir = directionOf(core, m);

        // ---- ① 结构复检（节流）：只用来"发现被拆"，永远不会自动激活 ----
        m.passes++;
        int recheck = Math.max(1, cfg.saizenRecheckPasses);
        if (m.passes % recheck == 0) {
            ReactorStructure.Result r = st.check(core, dir);
            if (!r.isComplete()) {
                String reason = deactivate(core, "结构已失效：" + r.summary());
                m.note = reason;            // 让本轮报告把停机原因带出来
                return;
            }
            if (r.direction() != null && r.direction() != dir) {
                dir = r.direction();
                TouhouData.setString(core, TouhouData.KEY_DIRECTION, Integer.toString(dir.ordinal()));
                m.direction = dir;
            }
            // 幂等补齐：编号/绑定丢了（例如木桩被换过、方块数据被清过）就补回来
            bindPosts(st, core, dir, true);
        }

        // ---- ② 镜像：木桩 IO 槽 → 核心预留槽 ----
        Location[] posts = st.postLocations(core, dir);
        Mirror mirror = mirror(core, inv, posts);
        if (!mirror.allReady()) {
            note(core, inv, m, "&7有木桩所在区块未加载，本轮不运作");
            return;
        }

        // ---- ③ 配方匹配（读的是刚刚镜像出来的 6 个预留槽） ----
        SaizenbakoRecipe recipe = SaizenbakoRecipes.match(mirror.items());
        if (recipe == null) {
            // ★ 这是"待机"结论：不要冲掉上一次的成功产出记录 ——
            //   玩家最想知道的是"我投的料到底出没出东西"，而不是"这一秒没在干活"。
            noteIdle(core, inv, m, "&7无匹配配方（6 个预留槽需同时满足）");
            return;
        }

        // ---- ④ 预检：输出空间 + POWER ----
        ItemStack product = recipe.output();
        if (!canFit(inv, Saizenbako.IO_SLOT, product)) {
            note(core, inv, m, "&c输出槽放不下产物（先清空 " + Saizenbako.IO_SLOT + " 号槽）");
            return;
        }
        long need = Math.max(0, cfg.saizenPowerCost);
        long have = chargeOf(core);
        if (have < need) {
            note(core, inv, m, "&cPOWER 不足：需 " + need + "，当前 " + have
                    + "（把 POWER 方块贴到赛钱箱上并网）");
            return;
        }

        // ---- ⑤ 提交：材料 → 电力 → 产物 ----
        if (!consumePosts(posts, recipe)) {
            note(core, inv, m, "&c木桩里的材料不足（可能刚被取走）");
            return;
        }
        if (need > 0) {
            setChargeOf(core, have - need);
        }
        ItemStack leftover = inv.pushItem(product, Saizenbako.IO_SLOT);
        if (leftover != null && !leftover.getType().isAir()) {
            // 预检已经算过空间，走到这里说明有别的插件/玩家在同一 tick 插了一手。
            // ★ 绝不静默吞掉：把剩下的退回木桩（从 0 号开始找放得下的），再退不掉就报错到控制台
            int returned = returnToPosts(posts, leftover);
            Touhou.getInstance().getLogger().warning("[赛钱箱] 产物只放进去一部分 @ "
                    + TouhouData.xyz(core) + "，剩余 " + leftover.getAmount() + " × "
                    + leftover.getType() + "，已退回木桩 " + returned + " 个；请检查输出槽");
        }
        TouhouData.setLong(core, TouhouData.KEY_SAIZEN_LAST_CRAFT, System.currentTimeMillis());
        note(core, inv, m, CRAFT_MARK + " " + product.getAmount() + " × "
                + (product.getItemMeta() != null && product.getItemMeta().hasDisplayName()
                        ? product.getItemMeta().getDisplayName()
                        : product.getType().toString())
                + " &7（配方 " + recipe.id() + "，耗电 " + need + "）");

        // 材料刚被消耗：立刻再镜像一次，玩家眼前不留"旧库存"
        mirror(core, inv, posts);
        refreshViewers(core, inv);
    }

    /** 输出槽放不放得下这些产物。 */
    private static boolean canFit(BlockMenu inv, int slot, ItemStack product) {
        ItemStack cur = inv.getItemInSlot(slot);
        if (cur == null || cur.getType().isAir()) {
            return true;
        }
        if (!SaizenbakoRecipe.sameItem(cur, product)) {
            return false;
        }
        int max = Math.min(cur.getMaxStackSize(), inv.toInventory().getMaxStackSize());
        return cur.getAmount() + product.getAmount() <= max;
    }

    // ================================================================ 镜像

    /**
     * 镜像的结果。
     *
     * @param items    镜像后的内容（下标 = 编号；{@code null} = 空槽），也是配方匹配的输入
     * @param allReady 6 根木桩是不是全部就绪（有未加载的区块时不敢动材料）
     */
    private record Mirror(ItemStack[] items, boolean allReady) {
    }

    /**
     * <b>镜像</b>：把 6 根木桩 IO 槽里的物品按编号写进核心的 6 个预留槽。
     *
     * <p>★ 单向（木桩 → 预留槽）：预留槽被 {@link GuiLock} 锁死，玩家只能通过木桩投料；
     * 镜像只负责"让玩家在核心界面里看得到自己投了什么"。
     *
     * <p>★ 木桩所在区块没加载时<b>不清空</b>那一格的显示，只是标记 {@code allReady=false}
     * —— 否则服务器重启后（部分区块未加载）会看到 6 个预留槽全部变空，
     * 玩家会以为材料丢了。
     *
     * @return 镜像后的内容（下标 = 编号；{@code null} = 空槽）+ 是否 6 根全部就绪
     */
    private static Mirror mirror(Location core, BlockMenu inv, Location[] posts) {
        ItemStack[] items = new ItemStack[SaizenbakoStructure.POST_COUNT];
        boolean allReady = true;
        for (int i = 0; i < posts.length && i < Saizenbako.RESERVED_SLOTS.length; i++) {
            Location post = posts[i];
            if (post == null || !TouhouData.isReady(post)) {
                allReady = false;
                items[i] = null;
                continue;
            }
            ItemStack src = readPostItem(post);
            items[i] = src == null ? null : src.clone();
            int slot = Saizenbako.RESERVED_SLOTS[i];
            ItemStack shown = inv.getItemInSlot(slot);
            if (!sameStack(shown, src)) {
                // ★ 必须 clone：塞进另一个菜单的不能是同一个 ItemStack 实例
                //   （两个菜单共享一个实例 = 改一边动两边，是复制物品的经典来源）
                inv.replaceExistingItem(slot, src == null ? null : src.clone());
            }
        }
        return new Mirror(items, allReady);
    }

    /**
     * 把"没塞进输出槽的剩余产物"退回木桩的 IO 槽。
     *
     * <p>只在极端情况（同一 tick 有别的插件占了输出槽）才会走到，
     * 但物品不能凭空消失 —— 退回原投料口是玩家最容易理解的处理方式。
     *
     * @return 成功退回的数量
     */
    private static int returnToPosts(Location[] posts, ItemStack leftover) {
        int returned = 0;
        for (Location post : posts) {
            if (post == null || leftover.getAmount() <= 0) {
                continue;
            }
            BlockMenu pm = StorageCacheUtils.getMenu(post);
            if (pm == null || pm.locked()) {
                continue;
            }
            ItemStack rest = pm.pushItem(leftover.clone(), ShrinePost.IO_SLOT);
            int moved = leftover.getAmount() - (rest == null ? 0 : rest.getAmount());
            returned += moved;
            leftover.setAmount(leftover.getAmount() - moved);
        }
        return returned;
    }

    /** 读某根木桩 IO 槽里的物品（空槽 / 未加载返回 {@code null}）。 */
    private static ItemStack readPostItem(Location post) {
        BlockMenu pm = StorageCacheUtils.getMenu(post);
        if (pm == null) {
            return null;
        }
        ItemStack it = pm.getItemInSlot(ShrinePost.IO_SLOT);
        return it == null || it.getType().isAir() ? null : it;
    }

    /** 两个物品栈"看起来一样"吗（材质 + 粘液 id + 数量）—— 决定要不要写界面。 */
    private static boolean sameStack(ItemStack a, ItemStack b) {
        boolean ea = a == null || a.getType().isAir();
        boolean eb = b == null || b.getType().isAir();
        if (ea && eb) {
            return true;
        }
        if (ea || eb) {
            return false;
        }
        return a.getAmount() == b.getAmount() && SaizenbakoRecipe.sameItem(a, b);
    }

    /**
     * 立刻做一次镜像（打开界面时用）。
     *
     * <p>界面里的 6 个预留槽是"显示"，玩家一打开就该看到最新投料，
     * 而不是等下一轮运作（最多 1 秒）才刷新 —— 所以 {@code newInstance} 会调它。
     */
    public static void mirrorNow(Location core) {
        mirrorNow(core, core == null ? null : StorageCacheUtils.getMenu(core));
    }

    /**
     * 立刻镜像到<b>指定的那个菜单</b>。
     *
     * <p>★ 为什么要有这个重载：`newInstance(BlockMenu, Block)` 触发时，
     * 新菜单还<b>没</b>登记进 {@code StorageCacheUtils}，这时再 {@code getMenu(core)}
     * 很可能拿到 {@code null}（或上一次的旧实例）。既然钩子已经把菜单递到手里了，
     * 就直接写它 —— 否则"打开界面立刻看到投料"这条会时灵时不灵。
     */
    public static void mirrorNow(Location core, BlockMenu inv) {
        if (!usable(core) || inv == null || !isActivated(core)) {
            return;
        }
        try {
            SaizenbakoStructure st = SaizenbakoStructure.get();
            mirror(core, inv, st.postLocations(core, directionOf(core, machine(core))));
        } catch (RuntimeException e) {
            Touhou.getInstance().getLogger().warning("[赛钱箱] 镜像异常 @ "
                    + TouhouData.xyz(core) + ": " + e);
        }
    }

    // ================================================================ 消耗 / 电力

    /**
     * 消耗 6 根木桩里的材料 —— <b>先整体核验、再逐个消耗</b>。
     *
     * <p>为什么要两遍：6 根木桩里只要有一根不满足，就必须<b>一根都不动</b>，
     * 否则玩家会看到"材料扣了一半、什么也没产出"。
     */
    private static boolean consumePosts(Location[] posts, SaizenbakoRecipe recipe) {
        BlockMenu[] menus = new BlockMenu[SaizenbakoStructure.POST_COUNT];
        for (int i = 0; i < posts.length && i < menus.length; i++) {
            SaizenbakoRecipe.Ingredient ing = recipe.ingredientAt(i);
            if (ing == null) {
                continue;                       // 该格不限制
            }
            BlockMenu pm = posts[i] == null ? null : StorageCacheUtils.getMenu(posts[i]);
            if (pm == null || pm.locked()) {
                return false;
            }
            ItemStack have = readPostItem(posts[i]);
            if (!ing.matches(have)) {
                return false;
            }
            menus[i] = pm;
        }
        for (int i = 0; i < menus.length; i++) {
            if (menus[i] == null) {
                continue;
            }
            // ★ 数量取自配方而不是"整栈"：木桩里放 20 个下界之星时只消耗配方要求的 16 个
            menus[i].consumeItem(ShrinePost.IO_SLOT, recipe.ingredientAt(i).amount());
        }
        return true;
    }

    /** 核心自己的 POWER 电量（走 {@link Saizenbako#powerCharge} 同一个键）。 */
    private static long chargeOf(Location core) {
        SlimefunItem item = BlockStorage.check(core);
        return item instanceof Saizenbako s ? s.powerCharge(core) : 0L;
    }

    /** 写核心的 POWER 电量（走 {@link Saizenbako#powerSetCharge}：会自己 clamp）。 */
    private static void setChargeOf(Location core, long charge) {
        SlimefunItem item = BlockStorage.check(core);
        if (item instanceof Saizenbako s) {
            s.powerSetCharge(core, charge);
        }
    }

    // ================================================================ 激活 / 停机

    /**
     * 一次"激活尝试"的结果。
     *
     * @param success   结构完整、已激活
     * @param firstTime true = 这一次把它从"未激活"变成了"已激活"
     * @param message   给玩家的反馈文本（带 {@code &} 颜色代码）
     */
    public record Activation(boolean success, boolean firstTime, String message) {
    }

    /**
     * <b>唯一的激活入口</b> —— 玩家点核心 GUI 的信息格时调用（或控制台命令）。
     *
     * <p>它自己先跑一次<b>现场</b>结构检测（4 个朝向都试），所以调用方不需要重复检测；
     * 失败时把"缺在哪"一起带回去 —— 玩家点一下就该知道原因，而不是"点了没反应"。
     *
     * <p>成功后做四件事：
     * <ol>
     *   <li>把激活状态写进方块数据（{@link TouhouData#KEY_STRUCTURE_OK} + 朝向）；</li>
     *   <li>按 +X→+Z 顺序给 6 根木桩编号，并把编号 + 核心坐标写进<b>木桩自己</b>的方块数据；</li>
     *   <li>给整座结构盖章（{@link StructureRegistry#attach}，与反应堆同一套 uid 方案）；</li>
     *   <li>立刻镜像一次，玩家点完就能在预留槽里看到木桩里的库存。</li>
     * </ol>
     */
    public static Activation activate(Location core) {
        if (core == null || core.getWorld() == null) {
            return new Activation(false, false, "&c无法定位这台赛钱箱");
        }
        SaizenbakoStructure st = SaizenbakoStructure.get();
        ReactorStructure.Result result = st.check(core);       // 现场检测，四向都试

        if (!result.isComplete()) {
            // 未通过：明确写"未激活"，并把原因带回给玩家
            TouhouData.setString(core, TouhouData.KEY_STRUCTURE_OK, "false");
            StringBuilder sb = new StringBuilder();
            sb.append("&c结构不完整，无法激活：").append(result.summary());
            int shown = 0;
            for (String miss : result.missing()) {
                if (shown++ >= 3) {
                    break;
                }
                sb.append("\n&7  缺 ").append(miss);
            }
            for (String wrong : result.wrong()) {
                if (shown++ >= 6) {
                    break;
                }
                sb.append("\n&6  错 ").append(wrong);
            }
            forget(core);
            return new Activation(false, false, sb.toString());
        }

        boolean wasActive = isActivated(core);
        ReactorStructure.Direction dir = result.direction() == null
                ? ReactorStructure.Direction.NORTH : result.direction();

        TouhouData.setString(core, TouhouData.KEY_STRUCTURE_OK, "true");
        TouhouData.setString(core, TouhouData.KEY_DIRECTION, Integer.toString(dir.ordinal()));
        Machine m = machine(core);
        m.direction = dir;
        m.lastWorkTick = -1L;           // 激活后第一轮立刻跑（不用等节流窗口）
        m.passes = 0;

        // 盖章 + 编号（编号顺序 = 层图里的 +X → +Z 顺序，与机器朝向无关）
        StructureRegistry.attach(core, st.partLocations(core, dir));
        bindPosts(st, core, dir, false);
        mirrorNow(core);

        BlockMenu inv = StorageCacheUtils.getMenu(core);
        if (inv != null) {
            refreshViewers(core, inv);
        }

        String msg = wasActive
                ? "&a祭坛已在运行中 &7(朝向 " + dir.label() + ")"
                : "&a祭坛已激活 &7(6 根木桩已编号)";
        // ★ 只在状态真的发生变化时记一笔。
        //   玩家可以反复点信息格做"重新检测"（这是幂等的），每次都记一行会把控制台刷满
        //   —— 实测一次联机测试里同一坐标连刷了几十条完全相同的 "激活 ... 朝向变更=false"。
        if (!wasActive) {
            Log.info("[赛钱箱] 激活 @ " + TouhouData.xyz(core) + " 朝向=" + dir.label());
        }
        return new Activation(true, !wasActive, msg);
    }

    /**
     * 停机（结构失效 / 核心被拆 / 人工停用）。
     *
     * <p>要清的东西有<b>三类</b>，少清一类就会留下"看起来还在运行"的残留：
     * <ol>
     *   <li>核心的激活标记（{@link TouhouData#KEY_STRUCTURE_OK} = false）；</li>
     *   <li>整座结构的 uid 盖章（{@link StructureRegistry#detach}）；</li>
     *   <li><b>6 根木桩的编号与核心绑定</b> —— 需求原话「木桩被拆 / 结构失效时清除」。</li>
     * </ol>
     */
    public static String deactivate(Location core, String reason) {
        if (!usable(core)) {
            return "无法定位核心";
        }
        SaizenbakoStructure st = SaizenbakoStructure.get();
        ReactorStructure.Direction dir = directionOf(core, machine(core));
        TouhouData.setString(core, TouhouData.KEY_STRUCTURE_OK, "false");
        Location[] posts = st.postLocations(core, dir);
        StructureRegistry.detach(core, st.partLocations(core, dir));
        for (Location post : posts) {
            clearPostBinding(post);
        }
        // 停机原因也落盘：信息槽的「最近一次」要能回答"为什么不动了"
        String text = reason == null ? "已停机" : reason;
        TouhouData.setString(core, TouhouData.KEY_SAIZEN_NOTE, Notify.plain(text));
        // ★ 多方块投影必须跟着停机一起收：停机 = 结构已经不成立了，
        //   而投影实体不是方块、不会自己消失（见 MultiBlockProjection 类注释）。
        MultiBlockProjection.forget(core);
        forget(core);
        // 镜像也要清空：结构都没了，预留槽里留着上次的投料会让人以为还能用
        BlockMenu inv = StorageCacheUtils.getMenu(core);
        if (inv != null) {
            for (int slot : Saizenbako.RESERVED_SLOTS) {
                inv.replaceExistingItem(slot, null);
            }
            refreshViewers(core, inv);
        }
        Log.info("[赛钱箱] 已停机 @ " + TouhouData.xyz(core)
                + "：" + (reason == null ? "(无原因)" : Notify.plain(reason)));
        return reason == null ? "已停机" : reason;
    }

    /** 该核心此刻是否已激活（读方块数据，单一数据源）。 */
    public static boolean isActivated(Location core) {
        return StructureState.activeAt(core);
    }

    /** 读（并缓存）结构朝向。 */
    private static ReactorStructure.Direction directionOf(Location core, Machine m) {
        if (m.direction != null) {
            return m.direction;
        }
        String raw = TouhouData.getString(core, TouhouData.KEY_DIRECTION, null);
        ReactorStructure.Direction dir = ReactorStructure.Direction.NORTH;
        if (raw != null && !raw.isBlank()) {
            try {
                dir = ReactorStructure.Direction.fromInt(Integer.parseInt(raw.trim()));
            } catch (NumberFormatException ignored) {
                // 值坏了就按 NORTH 算：反正复检会重新探测并覆盖它
            }
        }
        m.direction = dir;
        return dir;
    }

    // ================================================================ 木桩编号 / 绑定

    /**
     * 给 6 根木桩编号并绑定核心（幂等：已经写对的不重复写）。
     *
     * @param force {@code true} = 即使编号已存在也重算一遍（复检路径用，
     *              用来修"编号被别人改过 / 少了核心绑定"的情况）
     */
    private static void bindPosts(SaizenbakoStructure st, Location core,
                                  ReactorStructure.Direction dir, boolean force) {
        Location[] posts = st.postLocations(core, dir);
        String coreText = TouhouData.encodeLocation(core);
        for (int i = 0; i < posts.length; i++) {
            Location post = posts[i];
            if (post == null || !TouhouData.isReady(post)) {
                continue;                   // 未加载的跳过，下一轮再来
            }
            if (force || StructureState.postIndexOf(post) != i) {
                TouhouData.setLong(post, TouhouData.KEY_POST_INDEX, i);
            }
            String cur = TouhouData.getString(post, TouhouData.KEY_CORE_POS, null);
            if (coreText != null && !coreText.equals(cur)) {
                TouhouData.setString(post, TouhouData.KEY_CORE_POS, coreText);
            }
        }
    }

    /** 清掉一根木桩的编号与核心绑定（木桩被拆 / 结构失效）。 */
    public static void clearPostBinding(Location post) {
        if (!usable(post)) {
            return;
        }
        if (StructureState.hasPostIndex(post)) {
            TouhouData.setLong(post, TouhouData.KEY_POST_INDEX, TouhouData.POST_INDEX_NONE);
        }
        TouhouData.setString(post, TouhouData.KEY_CORE_POS, "null");
        StructureRegistry.clear(post);
    }

    /**
     * 木桩被拆时的钩子（由 {@link ShrinePost} 的破坏 handler 调用）。
     *
     * <p>这里只清自己那一格的登记；<b>整座结构停机</b>交给核心的定期复检
     * （最多 {@code recheck-passes} 轮之后发现），因为木桩自己不做结构判定
     * —— 它连核心在哪都只是"记录"，没有能力判定整套结构。
     */
    public static void onPostRemoved(Location post) {
        clearPostBinding(post);
    }

    /** 核心被拆时的钩子（由 {@link Saizenbako} 的破坏 handler 调用）。 */
    public static void onCoreRemoved(Location core) {
        if (!usable(core)) {
            return;
        }
        SaizenbakoStructure st = SaizenbakoStructure.get();
        ReactorStructure.Direction dir = directionOf(core, machine(core));
        for (Location post : st.postLocations(core, dir)) {
            clearPostBinding(post);
        }
        StructureRegistry.detach(core, st.partLocations(core, dir));
        // 核心被拆：投影实体也要一起收（它们不是方块，不会随方块消失）
        MultiBlockProjection.forget(core);
        forget(core);
    }

    // ================================================================ 界面内容

    /** 只在有人看着时刷新两个信息槽（ticker 路径用）。 */
    private static void refreshViewers(Location core, BlockMenu inv) {
        if (inv == null || !inv.hasViewer()) {
            return;
        }
        inv.replaceExistingItem(Saizenbako.INFO_SLOT, Saizenbako.buildInfoIcon(core));
        inv.replaceExistingItem(Saizenbako.CORE_POS_SLOT, Saizenbako.buildCorePosIcon(core));
        // ★ 投影开关的外观要跟着状态走：别的地方（结构失效停机 / 命令关投影）
        //   也会把开关关掉，界面留着一个"已开启"的假象最误导人。
        inv.replaceExistingItem(Saizenbako.HOLOGRAM_SLOT, Saizenbako.buildHologramIcon(core));
    }

    /** 结论变化时才落盘 + 刷新界面（避免每轮都写方块数据）。 */
    private static void note(Location core, BlockMenu inv, Machine m, String text) {
        if (text == null) {
            return;
        }
        if (!text.equals(m.note)) {
            m.note = text;
            m.success = text.startsWith(CRAFT_MARK);
            TouhouData.setString(core, TouhouData.KEY_SAIZEN_NOTE, Notify.plain(text));
        }
        refreshInfo(inv, core);
    }

    /**
     * "待机"结论（没有配方匹配 / 区块没加载）—— <b>不覆盖上一次的成功产出记录</b>。
     *
     * <p>理由：这两类结论每秒都会重新出现一次，而"成功产出"是玩家真正关心的事件。
     * 如果待机结论每次都把它冲掉，玩家在核心界面里永远只看得到"无匹配配方"，
     * 分不清"我投的料被吃了没产出"和"我没投对料"。
     */
    private static void noteIdle(Location core, BlockMenu inv, Machine m, String text) {
        if (m.success) {
            return;
        }
        note(core, inv, m, text);
    }

    /** 刷新信息槽（有人看着时才动界面）。 */
    private static void refreshInfo(BlockMenu inv, Location core) {
        if (inv != null && inv.hasViewer()) {
            inv.replaceExistingItem(Saizenbako.INFO_SLOT, Saizenbako.buildInfoIcon(core));
        }
    }

    /**
     * 信息槽里「最近一次运作」那一行（读落盘的结论）。
     *
     * <p>★ 必须挡 {@code null}：本方法会被 {@link Saizenbako#buildInfoIcon} 调用，
     * 而那个方法在<b>界面构造期</b>（{@code BlockMenuPreset.init()}）就会跑一次，
     * 那时 {@code loc} 必然是 {@code null}（真实踩过：这里漏判 ⇒ 整个插件启用失败）。
     * {@link TouhouData} 那边已经统一兜底，这里是第二道防线：
     * 构造期就该显示"还没有记录"，而不是显示某个默认值。
     */
    public static String lastNote(Location core) {
        if (core == null || core.getWorld() == null) {
            return "(构造期无数据)";
        }
        String raw = TouhouData.getString(core, TouhouData.KEY_SAIZEN_NOTE, null);
        return raw == null || raw.isBlank() ? "(还没运作过)" : raw;
    }

    // ================================================================ 诊断

    /** 命令用：这台机器此刻的完整状态。 */
    public static List<String> describe(Location core) {
        List<String> out = new ArrayList<>();
        if (!usable(core)) {
            out.add("× 核心坐标无效");
            return out;
        }
        SaizenbakoStructure st = SaizenbakoStructure.get();
        AddonConfig cfg = AddonConfig.get();
        ReactorStructure.Direction dir = directionOf(core, machine(core));
        out.add("激活=" + isActivated(core)
                + "  朝向=" + dir.label()
                + "  本机 POWER=" + chargeOf(core) + "/"
                + (BlockStorage.check(core) instanceof Saizenbako s ? s.configuredCapacity() : 0)
                + "  每次耗电=" + cfg.saizenPowerCost);
        out.add("输出槽=" + Saizenbako.IO_SLOT + "（GUI 的 IO 格）"
                + "  预留槽=" + java.util.Arrays.toString(Saizenbako.RESERVED_SLOTS)
                + "  指示槽=" + java.util.Arrays.toString(Saizenbako.INDEX_SLOTS));
        // 输出槽内容：机器"到底产出了什么"的直接证据（无头验证时尤其重要 ——
        // 真实 ticker 与手动推 tick 会抢着产出，光看"轮次结论"可能被后一轮覆盖）
        BlockMenu inv = menuOf(core);
        ItemStack produced = inv == null ? null : inv.getItemInSlot(Saizenbako.IO_SLOT);
        out.add("输出槽内容=" + (produced == null || produced.getType().isAir()
                ? "(空)" : produced.getAmount() + " × " + describeItem(produced)));
        out.add("最近一次：" + lastNote(core));
        out.add("结构：" + st.describe().get(0));
        Location[] posts = st.postLocations(core, dir);
        for (int i = 0; i < posts.length; i++) {
            ItemStack it = readPostItem(posts[i]);
            out.add("  木桩 #" + i + " @ " + TouhouData.xyz(posts[i])
                    + "  方块=" + (BlockStorage.checkID(posts[i]) == null
                            ? "(无)" : BlockStorage.checkID(posts[i]))
                    + "  编号=" + StructureState.postIndexOf(posts[i])
                    + "  投料=" + (it == null ? "(空)" : it.getAmount() + " × " + it.getType()));
        }
        return out;
    }

    /** 命令用：6 个预留槽现在的镜像内容。 */
    public static List<String> reservedContents(Location core) {
        List<String> out = new ArrayList<>();
        BlockMenu inv = menuOf(core);
        if (inv == null) {
            out.add("(拿不到界面)");
            return out;
        }
        for (int i = 0; i < Saizenbako.RESERVED_SLOTS.length; i++) {
            ItemStack it = inv.getItemInSlot(Saizenbako.RESERVED_SLOTS[i]);
            out.add("预留槽 #" + i + "（下标 " + Saizenbako.RESERVED_SLOTS[i] + "）= "
                    + (it == null || it.getType().isAir()
                            ? "(空)" : it.getAmount() + " × " + describeItem(it)));
        }
        return out;
    }

    private static String describeItem(ItemStack it) {
        if (it == null) {
            return "(null)";
        }
        SlimefunItem sf = SlimefunItem.getByItem(it);
        return (sf == null ? it.getType().toString() : sf.getId() + "/" + it.getType());
    }

    /** 命令用：按第一条配方给 6 根木桩塞材料（无头验证"投料 → 产出"整条链路）。 */
    public static List<String> seedFirstRecipe(Location core) {
        List<String> out = new ArrayList<>();
        if (!usable(core)) {
            out.add("× 核心坐标无效");
            return out;
        }
        if (SaizenbakoRecipes.count() == 0) {
            out.add("× 一条配方都没注册");
            return out;
        }
        SaizenbakoRecipe recipe = SaizenbakoRecipes.all().get(0);
        SaizenbakoStructure st = SaizenbakoStructure.get();
        Location[] posts = st.postLocations(core, directionOf(core, machine(core)));
        for (int i = 0; i < posts.length; i++) {
            SaizenbakoRecipe.Ingredient ing = recipe.ingredientAt(i);
            if (ing == null) {
                out.add("  木桩 #" + i + "：配方不限制，跳过");
                continue;
            }
            BlockMenu pm = menuOf(posts[i]);
            if (pm == null) {
                out.add("× 木桩 #" + i + " 没有界面（方块/数据未就绪）");
                continue;
            }
            ItemStack give = ing.template();
            give.setAmount(ing.amount());
            pm.replaceExistingItem(ShrinePost.IO_SLOT, give);
            out.add("  木桩 #" + i + " ← " + ing.describe());
        }
        return out;
    }

    /** 取某个方块位置上的界面（坐标无效 / 未加载时返回 {@code null}）。 */
    private static BlockMenu menuOf(Location loc) {
        return usable(loc) ? StorageCacheUtils.getMenu(loc) : null;
    }

    /** 命令用：给核心直接充电（无头验证"POWER 不足不运作"那条分支）。 */
    public static String setCharge(Location core, long charge) {
        if (!usable(core)) {
            return "× 核心坐标无效";
        }
        SlimefunItem item = BlockStorage.check(core);
        if (!(item instanceof Saizenbako s)) {
            return "× 这一格不是赛钱箱";
        }
        s.powerSetCharge(core, charge);
        return "已把本机 POWER 写为 " + s.powerCharge(core) + " / " + s.configuredCapacity();
    }
}
