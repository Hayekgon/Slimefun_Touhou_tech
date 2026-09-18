package com.example.touhou.core;

import com.example.touhou.Touhou;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 反应堆的运行状态机 —— 本机器**唯一**的"大脑"。
 *
 * <p>核心物品只负责 GUI 与电力接口，所有"能不能干活 / 现在是几态 / 发多少电 / 进程推进"
 * 都集中在这里，方便单独推理与测试。
 *
 * <h2>三态与激活规则（严格按 spec）</h2>
 * <pre>
 *  结构完整？
 *   否 → INACTIVE(未激活)，并清掉"已激活"标记
 *   是 → 已激活？
 *          否 → INACTIVE(未激活)   ← ★ 结构刚由不完整变完整时停在这里，必须玩家右键核心
 *          是 → 有燃料进程？ WORKING(运行中) : IDLE(空闲中)
 * </pre>
 *
 * <h2>为什么"已激活"是内存标记而不是持久化字段</h2>
 * spec 要求"结构从<b>不完整</b>到<b>完整</b>"才需要右键激活；如果把它持久化，
 * 那么服务器重启后一台本来正常运行的机器会突然变成未激活（因为重启等于"第一次看到完整结构"），
 * 体验会莫名其妙。所以：
 * <ul>
 *   <li>进程中的机器重启后自动恢复空闲（默认视为已激活）；</li>
 *   <li>只有<b>真的看到结构坏过</b>（{@code lastValid=false} → {@code true}）才要求右键。</li>
 * </ul>
 *
 * <h2>发电与暂停</h2>
 * 见 {@link ReactorMode}：发电模式在储电超过阈值时返回 0 且<b>不推进度</b>（暂停），
 * 产物模式则一路满功率；溢出的电由本体 {@code EnergyNet.storeRemainingEnergy} 丢弃，
 * 我们不需要自己写"销毁"逻辑。
 */
public final class ReactorManager {

    // ================================================================ 结构检测（事件驱动）

    /**
     * 完整结构检测要读多少格。
     *
     * <p>5×5×5 = 125 格，其中 98 格是构件、其余必须是空气 —— 一次检测就是
     * 一轮 125 次 {@code checkID}。这个数字是理解"为什么要事件驱动"的关键：
     * 旧的 tick 节流在"未激活 5 tick / 已激活 10 tick"下，一台机器
     * 每秒要扫 2~4 遍；而结构其实只在玩家动手时才可能变。
     */
    public static final int STRUCTURE_VOLUME = 125;

    /**
     * IO 接口搬运间隔的<b>出厂默认值</b>：5 <b>秒</b>（spec：「要求接口以每 5s 一次的效率工作」）。
     *
     * <p>★ 单位刻意用<b>真实秒</b>而不是 tick：搬运由 {@code BlockTicker} 驱动，
     * 而那是 <b>Slimefun tick</b>，其长度取决于 {@code Slimefun#getTickerTask().getTickRate()}
     * （默认 10 tick/秒，是原版的一半）。写死"100 tick"在本服会变成 <b>50 秒</b> ——
     * 第一版就是这么错的，信息格里显示"约 50.0 秒"才被发现。
     * 所以内部按实际 tickRate 换算成 tick 数，保证"5 秒"真的是 5 秒。
     */
    public static final int DEFAULT_IO_INTERVAL_SECONDS = 5;

    /**
     * IO 接口的搬运间隔（<b>真实秒</b>）—— 取配置值（{@code reactor.io.interval-seconds}）。
     */
    public static int ioIntervalSeconds() {
        ensureLoaded();
        return config.ioIntervalSeconds;
    }

    /**
     * IO 接口的搬运间隔换算成 <b>BlockTicker 次数</b>。
     *
     * <p>换算依据：{@code tickRate} = 一个 Slimefun tick 等于多少个原版 tick，
     * 所以 {@code 每秒的 BlockTicker 次数 = 20 / tickRate}（与本类
     * {@link #ticksToSeconds} 用的是同一份口径，避免两处算法漂移）。
     */
    public static int ioIntervalTicks() {
        int perSecond = Math.max(1, (int) Math.round(20.0 / Slimefun.getTickerTask().getTickRate()));
        return Math.max(1, ioIntervalSeconds() * perSecond);
    }

    /** 每个核心位置的运行期标记。 */
    private static final class Runtime {
        /** 上一次结构检测是否完整。 */
        boolean lastValid;
        /** 是否已激活（内存）。 */
        boolean activated = true;
        /** 缓存的最后一次检测结果，供 GUI 展示缺失明细。 */
        ReactorStructure.Result lastResult;
        /** 上次向控制台打印"结构不合格"的时间，做日志限流。 */
        long lastWarn;
        /**
         * 这台机器<b>在本 JVM 生命周期里有没有做过检测</b>。
         *
         * <p>★ 它承担"服务端重启后恢复"这件事：结构结论现在完全由事件维护，
         * 而重启不会产生任何放置/破坏事件 —— 没有这个标记的话，重启后所有反应堆
         * 都会永远停在"未激活"。所以每个核心的<b>第一次 tick</b> 补做一次现场检测，
         * 之后就再也不在 tick 路径上读世界了。
         *
         * <p>代价：一个核心一辈子只多这一次 124 格扫描（原来每 5~10 Slimefun tick 一次）。
         */
        boolean everChecked;
        /** 上一次命中的结构朝向（用于"只在变化时落盘"）。 */
        ReactorStructure.Direction lastDirection;
    }

    private static final Map<Location, Runtime> RUNTIME = new ConcurrentHashMap<>();

    private static AddonConfig config;
    private static ReactorStructure structure;

    private ReactorManager() {
    }

    /** 配置与结构实现的懒加载（避免静态初始化顺序问题）。 */
    private static void ensureLoaded() {
        if (config == null) {
            config = AddonConfig.get();
        }
        if (structure == null) {
            structure = buildStructure();
        }
    }

    private static ReactorStructure buildStructure() {
        String mode = Touhou.getInstance().getConfig().getString("reactor.structure.mode", "LAYERED");
        if ("DISABLED".equalsIgnoreCase(mode)) {
            Log.info("反应堆结构检测已禁用（structure.mode=DISABLED），使用 ALWAYS_OK");
            return ReactorStructure.ALWAYS_OK;
        }
        try {
            LayeredReactorStructure s = LayeredReactorStructure.fromConfig();
            Log.info("反应堆结构已载入：" + s.name()
                    + " 尺寸 " + java.util.Arrays.toString(s.size())
                    + " 需 " + s.partCount() + " 个构件"
                    + " 核心位置 " + java.util.Arrays.toString(s.corePosition())
                    + " 四向对称=" + s.isSymmetric()
                    + (s.isSymmetric() ? "（只探 NORTH）" : "（探测 4 个方向）")
                    + " 层图 " + LayerParser.describe(config.structureLayers));
            return s;
        } catch (RuntimeException e) {
            Touhou.getInstance().getLogger().severe("反应堆结构层图非法，已回退为 ALWAYS_OK：" + e.getMessage());
            return ReactorStructure.ALWAYS_OK;
        }
    }

    /** 热重载配置与结构（命令用）。 */
    public static void reload() {
        config = null;
        structure = null;
        RUNTIME.clear();
        StructureRegistry.clearCache();     // 结构/配置换了，uid→核心 的内存表跟着作废
        ensureLoaded();
    }

    public static AddonConfig config() {
        ensureLoaded();
        return config;
    }

    public static ReactorStructure structure() {
        ensureLoaded();
        return structure;
    }

    /** 便捷：直接检测某个位置（命令/调试用，主线程调用）。 */
    public static ReactorStructure.Result checkStructure(Location loc) {
        ensureLoaded();
        return structure.check(loc);
    }

    private static Runtime runtime(Location loc) {
        return RUNTIME.computeIfAbsent(TouhouData.norm(loc), k -> new Runtime());
    }

    // ================================================================ 状态机

    /**
     * 把一次现场检测的结论吃进运行期状态（<b>唯一</b>写 {@code lastValid} 的地方）。
     *
     * @param autoActivate 结构完整且构建模式为"自动"时是否直接激活
     * @return 结构是否完整
     */
    private static boolean applyResult(Location loc, Runtime rt, ReactorStructure.Result result,
                                       boolean autoActivate) {
        rt.lastResult = result;
        boolean valid = result.isComplete();
        // ★ 结构从"不完整"变成"完整"的那一刻，是"需要重新激活"的时刻
        if (valid && !rt.lastValid) {
            rt.activated = false;
        }
        if (!valid) {
            rt.activated = false;
        }
        rt.lastValid = valid;
        rt.everChecked = true;
        STRUCTURE_CHECKS.incrementAndGet();
        TouhouData.setString(loc, TouhouData.KEY_STRUCTURE_OK, Boolean.toString(valid));
        if (!valid) {
            // ★ 结构失效 = 投影必须立刻收掉。
            //   理由与"核心被拆"同一条：投影实体不是方块，不会随结构消失而消失，
            //   留着就是一组飘在空中的幻影方块。LogiTech 也是在
            //   onMultiBlockBreak / onMultiBlockEnable 这两个时机清全息的。
            MultiBlockProjection.forget(loc);
            refreshPersistedState(loc);
            return false;
        }
        rememberDirection(loc, rt, result);
        if (autoActivate && getBuildMode(loc) == BuildMode.AUTO) {
            rt.activated = true;
        }
        refreshPersistedState(loc);
        return true;
    }

    /**
     * 记住本次命中的结构朝向（写进方块数据，供重启后重连复用）。
     *
     * <p>参照 LogiTech 的 {@code mb-dir}：那边 {@code createNewHandler} 探测成功后
     * 立刻 {@code setDirection(loc, dir)}，后续 {@code genMultiBlockFrom} 直接读回，
     * 不再重新试四个方向。
     *
     * <p>这里只在**朝向变化**时落盘（避免每轮检测都写一次方块数据）。
     */
    private static void rememberDirection(Location loc, Runtime rt, ReactorStructure.Result result) {
        ReactorStructure.Direction dir = result.direction();
        if (dir == null || dir == rt.lastDirection) {
            return;
        }
        rt.lastDirection = dir;
        TouhouData.setString(loc, TouhouData.KEY_DIRECTION, Integer.toString(dir.ordinal()));
    }

    /**
     * 读回持久化的结构朝向。
     *
     * <p>用于诊断与"未来做朝向优先探测"的扩展点：目前 {@link LayeredReactorStructure}
     * 每次都自己按 4 方向（对称时 1 方向）探测，不依赖这个值；
     * 但把它读出来能让 {@code /touhou structure} 显示"上次是按哪个朝向认定的"。
     *
     * @return 没记录过时返回 {@code null}
     */
    public static ReactorStructure.Direction storedDirection(Location loc) {
        String raw = TouhouData.getString(loc, TouhouData.KEY_DIRECTION, null);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ReactorStructure.Direction.fromInt(Integer.parseInt(raw.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 节流推进后是否"结构完整但还没激活"—— 即此刻该走构建模式的分支。
     *
     * <p>只有真的做了一次检测才返回 true：否则节流窗口内每一 tick 都会重复走一遍
     * 自动激活流程（虽然幂等，但会白白重复落盘方块数据）。
     */
    // ================================================================ 结构变动触发检测（双向配合）

    /** 结构变动事件的两种形态。 */
    public enum Edit {
        /** 方块被放下。 */
        PLACED("放置"),
        /** 方块被拆掉（挖掘 / 爆炸 / 其它插件）。 */
        BROKEN("破坏");

        private final String label;

        Edit(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * <b>结构变动事件入口</b> —— 现在的<b>唯一常规检测触发途径</b>。
     *
     * <p>需求：「扫描改成双向配合，仅当核心与其他结构方块被放置/破坏时进行一次检测；
     * 结构性方块被放置时，对周围核心<b>静默</b>检测一次（结构性方块仅检测核心）；
     * 如果检测失败则停止检测；如果检测到核心，则结构方块停止检测，
     * 并<b>带动核心</b>检测一次周围结构完整性（检测整个多方块结构）。」
     *
     * <p>所以是两条方向相反、各司其职的链路：
     * <pre>
     * 构件 → 核心：构件【只找核心】（5×5×5 内一次 checkID 扫描，静默、不产生任何提示）
     *              找到 → 构件从此不再检测，把活交给核心
     *              没找到 → 构件【停止检测】（不重试、不轮询、不留计时器）
     * 核心 → 构件：核心做一次【整套多方块检测】（124 格），并反向把附近接口绑定到自己身上
     * </pre>
     *
     * <p>两条链路合起来覆盖了所有建造顺序：无论玩家先放核心还是先放构件，
     * <b>后放的那一个</b>都会把整座结构验一遍 —— 这就是"双向配合"的意义，
     * 也是"构件只要找到核心就可以彻底停手"的前提。
     *
     * <table border="1">
     *   <caption>行为表</caption>
     *   <tr><th>事件</th><th>目标</th><th>行为</th></tr>
     *   <tr><td>PLACED</td><td>核心</td><td>整座结构检测 + 反向绑定附近接口</td></tr>
     *   <tr><td>PLACED</td><td>其它构件</td><td>静默找核心 → 没找到就停手；找到则检测整座结构</td></tr>
     *   <tr><td>BROKEN</td><td>核心</td><td>解绑附近接口 + 清运行期状态（<b>不</b>做无意义的检测）</td></tr>
     *   <tr><td>BROKEN</td><td>其它构件</td><td>静默找核心 → 找到则检测整座结构（必然报不完整）</td></tr>
     * </table>
     *
     * <p>★ <b>去抖（合并）</b>：连续放置 45 块构件只会在下一 tick 合成<b>一次</b>完整检测，
     * 而不是 45 次。见 {@link #scheduleCheck}。去抖同时保证了"最后一块放完后结构一定被验过"——
     * 单纯按时间丢事件的节流做不到这一点（快速建造时最后几次事件会被丢掉，
     * 结果结构永远停在未验证状态）。
     *
     * @param edited 被放置/破坏的方块位置
     * @param edit   是放置还是破坏
     * @param actor  触发这次变动的玩家（没有则为 {@code null}，例如爆炸）
     * @return 诊断说明（给命令用）；事件的播报由去抖任务负责
     */
    public static String onStructureEdited(Location edited, Edit edit, org.bukkit.entity.Player actor) {
        ensureLoaded();
        if (edited == null || edited.getWorld() == null) {
            return "位置无效";
        }
        String id = BlockStorage.checkID(edited);
        if (id == null) {
            return "这一格不是 Slimefun 方块（checkID=null）";
        }
        boolean isCore = AddItems.UTSUHO_REACTOR_CORE != null
                && AddItems.UTSUHO_REACTOR_CORE.getItemId().equals(id);

        // ---- 核心本身被拆：撤销登记 + 解绑接口 + 清状态
        //      （核心都没了，做结构检测没有意义；对应 LogiTech 的 destroy 路径）
        if (isCore && edit == Edit.BROKEN) {
            java.util.List<Location> parts = structure.partLocations(edited, knownDirection(edited));
            String uid = StructureRegistry.detach(edited, parts);
            int unbound = AbstractReactorPort.unbindFrom(edited);
            forget(edited);
            return "核心被破坏 → 已撤销登记（uid=" + (uid == null ? "无" : uid.substring(0, 8) + "…")
                    + "，构件 " + parts.size() + " 格）+ 解绑接口 " + unbound + " 个";
        }

        if (isCore) {
            scheduleCheck(edited, actor);
            return "核心" + edit.label() + " → 已排入一次整套结构检测（下一 tick 执行，多次变动会合并）";
        }

        // ---- 其它构件：先静默找核心（构件只负责这一步）
        //      ★ 照搬 LogiTech 的顺序：先读自己身上的 uid（1 次方块数据读取），
        //        读不到才去扫世界（125 格）。结构成立之后永远不会走到扫描那一步。
        Location byUid = StructureRegistry.coreOfPart(edited);
        Location core = byUid;
        if (core != null) {
            bindIfPort(edited, core);
            scheduleCheck(core, actor);
            return "靠已登记的 uid 认出核心 " + xyzOf(core)
                    + " → 本构件停止检测，交由核心做一次整套结构检测（未扫世界）";
        }
        core = findCoreNear(edited);
        if (core == null) {
            // 构件先于核心放下：写一个"等候核心认领"的过渡态（LogiTech 的 sta=-N 语义），
            // 然后彻底停手 —— 等核心被放下时由核心反向认领。
            StructureRegistry.markWaiting(edited);
            return "附近 " + coreSearchRadius() + " 格内没有核心 → 已标记为等候核心"
                    + "（sta=" + StructureRegistry.STA_WAITING + "），停止检测、不重试";
        }
        // 找到核心 → 构件停手，把检测交给核心
        bindIfPort(edited, core);
        scheduleCheck(core, actor);
        return "扫世界找到核心 " + xyzOf(core)
                + " → 本构件停止检测，交由核心做一次整套结构检测";
    }

    /**
     * 只有<b>物流接口</b>才需要记进"接口 → 核心"表。
     *
     * <p>★ 这个判断不能省：{@code bind} 会往 {@code AbstractReactorPort.CORE_OF} 里插一条，
     * 而"被变动的构件"绝大多数是框架/保护罩。不加判断的话，
     * 每放一块框架都会往接口表里塞一条假记录，{@code boundCount()} 会一路涨到 98，
     * 排查"接口到底认没认到核心"时被这个数字带偏（本次实测踩过：只放了 2 个接口却报 5 个）。
     */
    private static void bindIfPort(Location edited, Location core) {
        if (BlockStorage.check(edited) instanceof AbstractReactorPort) {
            AbstractReactorPort.bind(edited, core);
        }
    }

    /** 人类可读坐标。 */
    private static String xyzOf(Location loc) {
        return loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    /**
     * 这台机器"已知的朝向"——优先内存里那次命中的，其次读落盘的 {@code mb-dir}。
     *
     * <p>为什么两个都要：内存是本次检测刚命中的（最准），落盘是<b>重启后唯一</b>的
     * 来源（跟 LogiTech 的 {@code Direction.getDirection(loc)} 一样）。
     * 只有准备"撤销登记 / 列举构件"时才需要它 —— 朝向错了就会去清一批错的格子。
     */
    public static ReactorStructure.Direction knownDirection(Location core) {
        Runtime rt = RUNTIME.get(TouhouData.norm(core));
        if (rt != null && rt.lastDirection != null) {
            return rt.lastDirection;
        }
        return storedDirection(core);
    }

    /**
     * 排入一次去抖检测：同一 tick 内对同一个核心的多次变动只算一次。
     *
     * <p>为什么要去抖而不是"按毫秒冷却直接丢弃"：快速建造（或 WorldEdit）时事件会密集到来，
     * 丢弃式节流可能把<b>最后一块</b>的事件丢掉，于是结构永远没被验证过。
     * 去抖是"攒起来、稍后跑一次"，既少扫又不会漏。
     */
    private static void scheduleCheck(Location core, org.bukkit.entity.Player actor) {
        PENDING_CHECKS.add(TouhouData.norm(core));
        if (actor != null) {
            LAST_EDITOR.put(TouhouData.norm(core), actor);
        }
        if (FLUSH_SCHEDULED) {
            return;
        }
        Touhou plugin = Touhou.getInstance();
        if (plugin == null || !plugin.isEnabled()) {
            // 关服途中还会收到事件；没有调度器可用时退化成同步执行（总比什么都不做好）
            flushPendingChecks();
            return;
        }
        FLUSH_SCHEDULED = true;
        org.bukkit.Bukkit.getScheduler().runTask(plugin, ReactorManager::flushPendingChecks);
    }

    /** 去抖窗口结束：批量执行本批结构检测。 */
    private static void flushPendingChecks() {
        FLUSH_SCHEDULED = false;
        if (PENDING_CHECKS.isEmpty()) {
            return;
        }
        java.util.Set<Location> batch = new java.util.HashSet<>(PENDING_CHECKS);
        PENDING_CHECKS.clear();
        DEBOUNCED_BATCHES.incrementAndGet();
        for (Location core : batch) {
            org.bukkit.entity.Player actor = LAST_EDITOR.remove(TouhouData.norm(core));
            Feedback feedback = detectNow(core);
            if (feedback == null) {
                continue;
            }
            // ★ 重要事件与 warning 额外写一行控制台：
            //   ① 管理员需要一份"结构什么时候成了 / 坏了"的记录；
            //   ② 爆炸这类没有玩家的场景，消息栏里根本没人可通知。
            //   常规反馈（"还不完整"）不写日志 —— 那正是这次要减少的量。
            if (feedback.severity() == Feedback.Severity.IMPORTANT
                    || feedback.severity() == Feedback.Severity.WARN) {
                Log.info("[MBREACTOR] "
                        + Notify.plain(feedback.text()) + " @ " + xyzOf(core));
            }
            if (actor == null) {
                continue;               // 没人可通知（控制台触发 / 爆炸）
            }
            // ★ 这条消息"属于哪一档"在 detectNow 里决定（Feedback.severity），
            //   "到底发不发"由 Notify 按 messages.level 裁决 —— 两件事分开。
            switch (feedback.severity()) {
                case WARN -> Notify.warn(actor, feedback.text());
                case IMPORTANT -> Notify.important(actor, feedback.text());
                case NORMAL -> Notify.info(actor, feedback.text());
                case DETAIL -> Notify.detail(actor, feedback.text());
            }
        }
    }

    /**
     * 一条给玩家的结构检测反馈。
     *
     * @param text     消息正文（{@code &} 颜色码）
     * @param severity <b>这条消息的性质</b> —— 与"消息栏档位"（{@link Notify.Level}）
     *                 是两件事：这里是"它是什么"，那边是"玩家愿意收到到哪一档"。
     */
    private record Feedback(String text, Severity severity) {

        /** 消息性质。 */
        enum Severity {
            /** 操作失败/异常，必须让玩家知道。 */
            WARN,
            /** 重要事件（结构构建成功）。 */
            IMPORTANT,
            /** 常规操作反馈。 */
            NORMAL,
            /** 诊断细节。 */
            DETAIL
        }
    }

    /**
     * 命令专用：立刻把待去抖的检测跑掉，好让"检测结果"在同一条命令的输出里就能看到。
     *
     * <p>正常游戏流程<b>不该</b>调它 —— 去抖的意义就是"攒一攒再跑"。
     * 控制台连续发命令时如果不强制刷一次，读到的永远是上一 tick 的旧状态。
     */
    public static void flushPendingChecksForDiagnostics() {
        flushPendingChecks();
    }

    /** 等待去抖执行的检测（核心位置）。 */
    private static final java.util.Set<Location> PENDING_CHECKS =
            java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** 最近一次触发变动的玩家（去抖后给他反馈）。 */
    private static final Map<Location, org.bukkit.entity.Player> LAST_EDITOR = new ConcurrentHashMap<>();
    /** 是否有去抖任务已在排队（全局只需要一个）。 */
    private static volatile boolean FLUSH_SCHEDULED;
    /** 完整结构检测的累计次数（证据用：证明检测次数真的降下来了）。 */
    private static final java.util.concurrent.atomic.AtomicLong STRUCTURE_CHECKS =
            new java.util.concurrent.atomic.AtomicLong();
    /** 去抖批次数的累计（对比"事件数"就能看出合并省了多少次检测）。 */
    private static final java.util.concurrent.atomic.AtomicLong DEBOUNCED_BATCHES =
            new java.util.concurrent.atomic.AtomicLong();

    /** 累计的完整结构检测次数。 */
    public static long structureCheckCount() {
        return STRUCTURE_CHECKS.get();
    }

    /** 累计的去抖批次数（≈ 实际执行的检测轮数）。 */
    public static long debouncedBatchCount() {
        return DEBOUNCED_BATCHES.get();
    }

    /**
     * 立刻做一次现场检测（<b>不</b>去抖），按构建模式决定是否激活。
     *
     * <p>★ 返回值带<b>分档</b>：这条消息到底会不会出现在消息栏，由 {@link Notify} 按
     * {@code messages.level} 统一裁决。这里只负责"它属于哪一档"：
     * <ul>
     *   <li>结构构建成功 / 已自动激活 → {@link Feedback.Severity#IMPORTANT}（默认推）</li>
     *   <li>"还不完整：缺 N 处" → {@link Feedback.Severity#NORMAL}（默认不推 ——
     *       否则玩家每放一块构件都会收到一行，搭一座 98 格的结构能刷上百行）</li>
     * </ul>
     *
     * @return 反馈；<b>{@code null} = 无事可说</b>（结构依旧完整且已激活，再报一次只是噪音）
     */
    private static Feedback detectNow(Location core) {
        Runtime rt = runtime(core);
        boolean wasValid = rt.lastValid;
        boolean wasActivated = rt.activated;
        // ★ 优先按已落盘朝向校验（LogiTech 的 mb-dir 重连思路）：
        //   朝向未知或不再匹配时才回退到四向探测。
        ReactorStructure.Result result = structure.check(core, knownDirection(core));
        boolean valid = applyResult(core, rt, result, true);

        if (valid) {
            // ★ 盖章（LogiTech createHandler 的循环）：按 schema 线性写 uid + sta=1，
            //   不做任何搜索。盖完之后构件靠 uid 认核心（读 1 次数据），
            //   核心靠 uid 确认归属（1 遍层图），都不需要再扫 125 格。
            StructureRegistry.attach(core, structure.partLocations(core, result.direction()));
        } else {
            // 结构不成立 → 撤销登记（LogiTech destroy 的思路：核心与各构件一起清）
            StructureRegistry.detach(core, structure.partLocations(core, knownDirection(core)));
        }

        // ★ 反向绑定：核心这边检测完，顺手把附近的接口绑到自己身上。
        //   这样"先放接口、后放核心"时接口不需要自己重新找（它已经在放下时停手了）。
        AbstractReactorPort.bindNearby(core);

        if (!valid) {
            StringBuilder sb = new StringBuilder();
            sb.append("&c结构还不完整：").append(result.summary());
            appendDiagnosis(sb, result, 2);
            return new Feedback(sb.toString(), Feedback.Severity.NORMAL);
        }

        if (getBuildMode(core) == BuildMode.AUTO) {
            if (wasValid && wasActivated) {
                return null;            // 本来就好好的，别刷屏
            }
            if (Boolean.getBoolean("touhou.debugReactor")) {
                Log.info("[MBREACTOR-EDIT] 结构变动触发检测并自动激活 @ "
                        + core.getBlockX() + "," + core.getBlockY() + "," + core.getBlockZ());
            }
            TouhouData.setEnum(core, TouhouData.KEY_STATE, ReactorState.IDLE);
            // ★ 这是需求里点名的"重要事件"：多方块结构构建成功
            return new Feedback("&a结构完整（朝向 " + result.direction().label()
                    + "），反应堆已自动激活！", Feedback.Severity.IMPORTANT);
        }
        // 手动构建：只更新落盘数据，等玩家点激活按钮
        return wasValid && wasActivated
                ? null
                : new Feedback("&e结构完整，但当前是 &f手动构建&e —— 请点击信息格激活",
                        Feedback.Severity.IMPORTANT);
    }

    /**
     * 诊断用：解释"为什么这次结构变动没有触发检测"。
     *
     * <p>没有它的话，控制台只会看到"什么都没发生"，很容易把
     * "本来就不该触发"误判成"功能没生效"。
     */
    public static java.util.List<String> explainNoEditDetect(Location edited) {
        ensureLoaded();
        java.util.List<String> out = new java.util.ArrayList<>();
        String id = BlockStorage.checkID(edited);
        if (id == null) {
            out.add("这一格现在不是 Slimefun 方块（checkID=null）");
            out.add("★ 若你刚把它拆掉：构件【只负责找核心】，被拆的那一格自己不会触发检测；"
                    + "再由核心那边检测整座结构 —— 请改用仍在的构件或直接对核心触发");
            return out;
        }
        Location core = findCoreNear(edited);
        if (core == null) {
            out.add("在 " + coreSearchRadius() + " 格内没找到反应堆核心"
                    + "（结构没搭到附近，或 core-search-radius 太小）");
            out.add("★ 按需求：找不到核心就【停止检测】，不会重试 —— 等核心放下时由核心那边检测");
            return out;
        }
        out.add("找到核心 " + core.getBlockX() + "," + core.getBlockY() + "," + core.getBlockZ()
                + "，应当已排入一次检测");
        Runtime rt = RUNTIME.get(TouhouData.norm(core));
        out.add("核心运行期状态: 已检测过=" + (rt != null && rt.everChecked)
                + " 上次结论=" + (rt != null && rt.lastValid)
                + " 已激活=" + (rt != null && rt.activated));
        out.add("（原因不明 —— 这行不该出现，请把上面几行发出来）");
        return out;
    }

    /**
     * 在构件附近找核心。
     *
     * <p>与接口那边的 {@code coreOf} 同一套思路：结构是 5×5×5、核心在正中，
     * 构件到核心最多差 2 格，所以扫 5×5×5 足够。
     */
    static Location findCoreNear(Location part) {
        World world = part.getWorld();
        if (world == null) {
            return null;
        }
        int r = coreSearchRadius();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    Location at = new Location(world,
                            part.getBlockX() + dx, part.getBlockY() + dy, part.getBlockZ() + dz);
                    String id = BlockStorage.checkID(at);
                    if (id != null && AddItems.UTSUHO_REACTOR_CORE != null
                            && AddItems.UTSUHO_REACTOR_CORE.getItemId().equals(id)) {
                        return at;
                    }
                }
            }
        }
        return null;
    }

    /** 在构件周围找核心的扫描半径（格）。 */
    public static int coreSearchRadius() {
        ensureLoaded();
        return Math.max(1, config.coreSearchRadius);
    }

    /**
     * 调试：当前的检测状态（命令 {@code /touhou reactor <x y z> scan} 用）。
     *
     * <p>检测是"看不见的行为"—— 出问题时最难判断的就是"到底查没查结构"。
     * 现在直接把<b>累计次数</b>与上次结论暴露出来，就不用再靠猜。
     */
    public static String describeScanState(Location loc) {
        Runtime rt = RUNTIME.get(TouhouData.norm(loc));
        if (rt == null) {
            return "（这台机器还没有运行期记录）";
        }
        return "本机已检测过=" + rt.everChecked
                + "  上次结论=" + rt.lastValid
                + "  已激活=" + rt.activated
                + "  待去抖的检测=" + PENDING_CHECKS.size()
                + "  |  全局累计：结构检测 " + STRUCTURE_CHECKS.get()
                + " 次 / 去抖批次 " + DEBOUNCED_BATCHES.get() + " 轮"
                + "  / 找核心 " + AbstractReactorPort.scanCount() + " 次";
    }

    /**
     * 计算当前状态。
     *
     * <p>★ <b>本方法不再读世界</b>（这是本次架构改动的核心）：
     * 结构结论完全由"放置 / 破坏"事件维护（{@link #onStructureEdited}），
     * tick 路径只消费 {@code rt.lastValid}。
     *
     * <p>唯一的例外是<b>每个核心在本 JVM 生命周期里的第一次 tick</b>：
     * 重启不会产生放置事件，不补这一次检测的话所有反应堆都会永远停在未激活。
     * 补完之后 {@code everChecked} 置位，tick 路径再也不碰世界方块。
     *
     * <p>构建模式（{@link BuildMode}）在 {@link #applyResult} 里落地：
     * <ul>
     *   <li>{@link BuildMode#MANUAL}：结构完整但未激活时<b>停在未激活</b>，
     *       等玩家点信息格（{@link #activate}）；</li>
     *   <li>{@link BuildMode#AUTO}：结构完整就直接激活 —— "自动构建"。</li>
     * </ul>
     *
     * <p>⚠ 主线程调用。
     */
    public static ReactorState updateState(Location loc, boolean hasOperation) {
        ensureLoaded();
        Runtime rt = runtime(loc);

        // 服务端启动后的第一次 tick：等价于"核心刚出现在世界里"，补一次现场检测
        if (!rt.everChecked) {
            applyResult(loc, rt, structure.check(loc), true);
            AbstractReactorPort.bindNearby(loc);
        }

        ReactorState state;
        if (!rt.lastValid || !rt.activated) {
            state = ReactorState.INACTIVE;
        } else {
            state = hasOperation ? ReactorState.WORKING : ReactorState.IDLE;
        }

        TouhouData.setEnum(loc, TouhouData.KEY_STATE, state);
        TouhouData.setString(loc, TouhouData.KEY_STRUCTURE_OK, Boolean.toString(rt.lastValid));
        return state;
    }

    /** 读上一次算出的状态（不读世界；数据未就绪时返回 INACTIVE）。 */
    public static ReactorState cachedState(Location loc) {
        return TouhouData.getEnum(loc, TouhouData.KEY_STATE, ReactorState.class, ReactorState.INACTIVE);
    }

    /**
     * 一次"激活尝试"的结果。
     *
     * @param success   结构完整、激活成功
     * @param firstTime true = 这一次把机器从"未激活"变成了"已激活"
     *                  （false = 之前就已激活，这次只是重新检测了一遍结构）
     * @param message   给玩家的反馈文本（带 {@code &} 颜色代码）
     */
    public record Activation(boolean success, boolean firstTime, String message) {
    }

    /**
     * 激活尝试 —— GUI 里点击【信息槽 J】时调用（{@code UtsuhoReactorCore#handleInfoClick}）。
     *
     * <p>它自己会先跑一次结构检测，所以调用方不需要重复检测。
     * 失败时把"缺在哪"一起带回去，让玩家不用再点第二次才知道原因。
     *
     * <p>★ 返回 {@link Activation} 而不是纯字符串：调用方需要知道"成没成功"
     * 才能决定要不要放激活音效 —— 靠解析提示文本猜成功与否太脆。
     */
    public static Activation activate(Location loc) {
        ensureLoaded();
        Runtime rt = runtime(loc);
        ReactorStructure.Result result = structure.check(loc);

        if (!result.isComplete()) {
            applyResult(loc, rt, result, false);
            StringBuilder sb = new StringBuilder();
            sb.append("&c结构不完整，无法激活：").append(result.summary());
            appendDiagnosis(sb, result, 3);
            return new Activation(false, false, sb.toString());
        }

        boolean wasActivated = rt.activated;
        applyResult(loc, rt, result, false);
        rt.activated = true;
        // ★ 立刻重算一次状态并落盘：点击是一次性事件，不能等"下一步 tick"才把
        //   未激活 → 空闲中 反映到方块数据上（否则玩家点完看到的还是未激活）。
        refreshPersistedState(loc);
        // 顺带反向绑定接口：玩家点击激活说明他在关注这台机器，把附近接口一次认全
        AbstractReactorPort.bindNearby(loc);

        String msg = wasActivated
                ? "&a结构完整，反应堆已在运行中"
                : "&a结构完整，反应堆已激活！&7（未激活 → 空闲中）";
        return new Activation(true, !wasActivated, msg);
    }

    /**
     * 按已知的"结构完整性 + 已激活标记 + 是否有进程"重算状态并写回方块数据。
     *
     * <p>与 {@link #updateState} 的区别：**不读世界**，只用内存里那份检测结果。
     * 这样点击激活时既不会多做一次世界扫描，又能立刻让 GUI 显示正确状态。
     */
    private static void refreshPersistedState(Location loc) {
        Runtime rt = runtime(loc);
        ReactorState state;
        if (!rt.lastValid || !rt.activated) {
            state = ReactorState.INACTIVE;
        } else {
            state = hasOperation(loc) ? ReactorState.WORKING : ReactorState.IDLE;
        }
        TouhouData.setEnum(loc, TouhouData.KEY_STATE, state);
    }

    /** 该位置当前是否有未完成的燃料进程。 */
    private static boolean hasOperation(Location loc) {
        var item = me.mrCookieSlime.Slimefun.api.BlockStorage.check(loc);
        if (!(item instanceof UtsuhoReactorCore reactor)) {
            return false;
        }
        var op = reactor.getMachineProcessor().getOperation(loc.getBlock());
        return op != null && !op.isFinished();
    }

    /** 是否已激活（内存标记）。 */
    public static boolean isActivated(Location loc) {
        Runtime rt = RUNTIME.get(TouhouData.norm(loc));
        return rt != null && rt.activated;
    }

    /** 最后一次结构检测结果。 */
    public static ReactorStructure.Result lastResult(Location loc) {
        Runtime rt = RUNTIME.get(TouhouData.norm(loc));
        return rt == null ? null : rt.lastResult;
    }

    /** 结构在当前时刻是否完整（会读世界，主线程用）。 */
    public static boolean isStructureComplete(Location loc) {
        ensureLoaded();
        return structure.check(loc).isComplete();
    }

    /**
     * <b>节流后的</b>结构结论 —— 不读世界，直接给 {@code rt.lastValid}。
     *
     * <p>★ 这是状态机内部用的那份结论。为什么必须有它：{@link #updateState} 已经
     * 在同一个 tick 里决定了"这一 tick 结构算不算完整"，后续逻辑（是否中断进程、
     * 是否推进产物）必须用<b>同一份</b>结论 —— 否则一次 tick 里会出现两个答案
     * （tick 头查到结构完好、tick 尾再扫时方块已被拆），行为不可复现。
     *
     * <p>需要"此刻真实结构"的场景（命令、开进程门控、切构建模式）请用
     * {@link #isStructureComplete}。
     */
    public static boolean isStructureValid(Location loc) {
        Runtime rt = RUNTIME.get(TouhouData.norm(loc));
        return rt != null && rt.lastValid;
    }

    /**
     * 结构不完整时，是否该中断正在进行的进程。
     *
     * <p>刻意做成"中断"而不是"暂停"：如果只是暂停，玩家可以拆掉结构让机器停摆，
     * 修好后进度还在 —— 变成了"结构只是个开关"。中断 + 燃料已消耗 =
     * 拆结构有实际代价，符合"多方块核心"的语义。
     *
     * <p>★ 判据用节流后的缓存结论（{@code rt.lastValid}），<b>不再现场复检</b>：
     * {@link #updateState} 在同一个 tick 里已经决定了这一 tick 的结构结论，
     * 这里再查一次等于把刚省下的 124 格扫描又加回来，而且两次结果可能不一致
     * （一次在 tick 头、一次在 tick 尾）。
     */
    public static boolean shouldAbortProcess(Location loc) {
        ensureLoaded();
        Runtime rt = runtime(loc);
        if (!config.abortProcessOnBroken) {
            return false;
        }
        return !rt.lastValid;
    }

    /**
     * 记录一次"结构坏了"的告警（控制台限流，默认 30 秒最多一次）。
     *
     * <p>★ 会<b>重新检测一次</b>而不是打印缓存结果：上一版直接打 {@code rt.lastResult}，
     * 而它可能是上一次检测（结构还完整时）留下的 —— 结果日志出现
     * "结构不完整 @ ... —— 结构完整（26 个构件）"这种自相矛盾的话
     * （用户实测日志里就是这么写的），把排查方向带偏。
     */
    public static void warnBrokenOnce(Location loc) {
        ensureLoaded();
        Runtime rt = runtime(loc);
        long now = System.currentTimeMillis();
        if (now - rt.lastWarn < 30_000L) {
            return;
        }
        rt.lastWarn = now;
        // 用现场检测的结果，保证"结论"和"证据"一致
        ReactorStructure.Result r = structure.check(loc);
        rt.lastResult = r;
        rt.lastValid = r.isComplete();
        StringBuilder sb = new StringBuilder("[Reactor] 结构不完整 @ " + loc.getBlockX() + ","
                + loc.getBlockY() + "," + loc.getBlockZ() + " —— ");
        sb.append(r.summary());
        appendDiagnosis(sb, r, 3);
        Touhou.getInstance().getLogger().warning(sb.toString());
    }

    private static void appendDiagnosis(StringBuilder sb, ReactorStructure.Result r, int limit) {
        int n = 0;
        for (String m : r.missing()) {
            if (n++ >= limit) {
                sb.append(" …");
                return;
            }
            sb.append(" [缺 ").append(m).append(']');
        }
        n = 0;
        for (String w : r.wrong()) {
            if (n++ >= limit) {
                sb.append(" …");
                return;
            }
            sb.append(" [错 ").append(w).append(']');
        }
    }

    // ================================================================ 运行条件门（唯一把关点）

    /**
     * "现在能不能开始/继续烧燃料"的判定结果。
     *
     * @param allowed 三个条件是否全部满足
     * @param reason  不满足时的原因（写日志 / 显示用）
     */
    public record ReactorGate(boolean allowed, String reason) {

        static ReactorGate ok() {
            return new ReactorGate(true, "所有条件满足");
        }

        static ReactorGate block(String reason) {
            return new ReactorGate(false, reason);
        }
    }

    /**
     * 开进程前的统一门控 —— spec 要求的三条：
     * <b>模式选择 + 电量容量 + 结构完整</b>（顺序按"最容易先失败、检查最便宜"排）。
     *
     * <p>★ 为什么必须有这个方法：原来只在 {@link #generate} 里做模式/电量门控，
     * 而那个方法只有方块接入电力网络时才会被调用 —— 于是"没接电"的机器会一直开进程、
     * 白烧燃料（用户实测：结构不完整时输入槽仍在减少）。
     * 现在把三个条件前置到"开进程"这一步，机器在所有条件满足前不消耗任何东西。
     *
     * <p>★ 条件 1 现在读的是<b>缓存结论</b>（{@link #isStructureValid}）而不是现场扫描：
     * 结构结论由放置/破坏事件维护，缓存是可信的；开进程每 600 tick 才发生一次，
     * 但那是"每轮一次全量扫描"，与"仅变动时检测"的口径不一致。
     * 爆炸等非玩家途径的变动也已经由 {@code StructureBuildListener} 覆盖。
     *
     * <p>⚠ 主线程调用（条件 3 会读世界方块判断电网连接）。
     */
    public static ReactorGate startGate(Location loc) {
        ensureLoaded();

        // 条件 1：结构完整（用事件维护的缓存结论，不再现场扫描）
        if (!isStructureValid(loc)) {
            return ReactorGate.block("多方块结构不完整");
        }
        // 条件 2：已激活（结构由坏变好之后需要玩家点信息格激活）
        if (!isActivated(loc)) {
            return ReactorGate.block("尚未激活（点击信息格激活）");
        }

        // 条件 3：电力网络 —— ★ 只有"发电模式"需要它。
        //   发电模式的电必须有去处，而本体的 EnergyNet 只会 tick 挂进网络的发电机，
        //   所以离网时它"燃料白烧、进度不动"。
        //   产物模式是【离网自跑】：参照本体 Reactor 的 PRODUCTION 模式，
        //   进程无视电网与电量条件照常推进（多出来的电没人要就直接丢）。
        if (getMode(loc) == ReactorMode.GENERATE && !isConnectedToEnergyNet(loc)) {
            return ReactorGate.block("发电模式：未接入电力网络（" + ENERGY_REGULATOR_RANGE
                    + " 格内需要能源调节器或电容）");
        }

        // 条件 4：电量 —— 只有"发电模式"会在储电到阈值时停下
        if (getMode(loc) == ReactorMode.GENERATE) {
            long charge = currentCharge(loc);
            if (charge >= config.modeThreshold) {
                return ReactorGate.block("发电模式：储电已达阈值 " + config.modeThreshold
                        + "（当前 " + charge + "）");
            }
        }
        // 产物模式：不受电量限制、也不要求接电网（满电/离网都照烧，溢出的电由本体丢弃）
        return ReactorGate.ok();
    }

    /**
     * 能源调节器的<b>远程连接距离</b>（格）。
     *
     * <p>用户实测确认：能源调节器不需要贴着发电机，<b>7 格以内</b>都能连上。
     * 所以判定不能只看 6 个相邻面，要看 7 格半径。
     */
    public static final int ENERGY_REGULATOR_RANGE = 7;

    /** 网络判定结果的缓存有效期（毫秒）。避免每 tick 扫 15³ 个方块。 */
    private static final long NET_CHECK_TTL_MS = 2000L;

    /** 每个位置的"是否接入电网"缓存。 */
    private static final Map<Location, Long> NET_CACHE_TIME = new ConcurrentHashMap<>();
    private static final Map<Location, Boolean> NET_CACHE_VALUE = new ConcurrentHashMap<>();

    /**
     * 反应堆有没有接入电力网络 —— 判据是<b>7 格半径内</b>存在能源调节器 / 电容 / 其它发电机。
     *
     * <p>★ 这条检查是用户实测反馈逼出来的：燃料在烧、进度却不动、也不发电。
     * 根因在本体的电力网络机制 —— 发电机必须被某个 {@code ENERGY_REGULATOR}
     * （能源调节器）覆盖到，才会进 {@code EnergyNet.tickAllGenerators} 的遍历列表，
     * 否则 {@code getGeneratedOutput} 永不被调用 → 燃料白烧。
     *
     * <p>三个曾经的错误认知，都在这里修正了：
     * <ol>
     *   <li>以为要"紧贴相邻"→ 错，调节器可以<b>远程连接，半径 7 格</b>；</li>
     *   <li>于是先写成 3×3×3"附近"→ 太窄，会把合法的 7 格连接判成未接入；</li>
     *   <li>又写成只看 6 个面 → 同样太窄。</li>
     * </ol>
     * 现在统一按 7 格立方体扫描（切比雪夫距离 ≤ 7，同时排除中心自身）。
     *
     * <p>性能：15×15×15 = 3375 次查询/tick 显然不能每 tick 做，
     * 所以结果按位置缓存 {@link #NET_CHECK_TTL_MS} 毫秒（网络布局很少变，
     * 2 秒的滞后对玩家体验没有影响）。
     *
     * <p>⚠ 主线程调用（读世界方块）。
     */
    public static boolean isConnectedToEnergyNet(Location loc) {
        long now = System.currentTimeMillis();
        Long stamp = NET_CACHE_TIME.get(loc);
        if (stamp != null && now - stamp < NET_CHECK_TTL_MS) {
            return Boolean.TRUE.equals(NET_CACHE_VALUE.get(loc));
        }
        boolean result = scanForEnergyNet(loc);
        NET_CACHE_TIME.put(loc, now);
        NET_CACHE_VALUE.put(loc, result);
        return result;
    }

    /** 真正的扫描：核心周围 7 格立方体内找网络挂接点。 */
    private static boolean scanForEnergyNet(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return false;
        }
        boolean debug = Boolean.getBoolean("touhou.debugReactor");
        int r = ENERGY_REGULATOR_RANGE;
        int cx = loc.getBlockX();
        int cy = loc.getBlockY();
        int cz = loc.getBlockZ();
        int scanned = 0;
        // 从内到外扫，命中即返回 —— 大多数情况调节器就在旁边几格
        for (int d = 1; d <= r; d++) {
            for (int dx = -d; dx <= d; dx++) {
                for (int dy = -d; dy <= d; dy++) {
                    for (int dz = -d; dz <= d; dz++) {
                        // 只看"这一层壳"，避免重复扫内部
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != d) {
                            continue;
                        }
                        Location around = new Location(world, cx + dx, cy + dy, cz + dz);
                        String id = BlockStorage.checkID(around);
                        scanned++;
                        if (id != null) {
                            boolean hit = isNetComponent(around);
                            if (hit) {
                                if (debug) {
                                    Log.info("[MBREACTOR-NET] 命中 d=" + d
                                            + " (" + (cx + dx) + "," + (cy + dy) + "," + (cz + dz)
                                            + ") id=" + id);
                                }
                                return true;
                            }
                        }
                    }
                }
            }
        }
        if (debug) {
            Log.info("[MBREACTOR-NET] 扫了 " + scanned
                    + " 格，未找到网络挂接点 @ " + cx + "," + cy + "," + cz);
        }
        return false;
    }

    /** 清理网络缓存（结构被拆/方块被破坏时调用，让判定立刻刷新）。 */
    public static void invalidateNetCache(Location loc) {
        NET_CACHE_TIME.remove(loc);
        NET_CACHE_VALUE.remove(loc);
    }

    /**
     * 该位置是不是电力网络的挂接点。
     *
     * <p>判据按可靠性从高到低：
     * <ol>
     *   <li>id 就是 {@code ENERGY_REGULATOR}（能源调节器）—— 实测这个方块
     *       <b>不是</b> {@code EnergyNetComponent}（{@code getEnergyComponentType()} 拿不到），
     *       所以必须先按 id 认它，不能只靠接口判断；</li>
     *   <li>或者它是 {@code CAPACITOR}（电容）/ {@code GENERATOR}（别的发电机）。</li>
     * </ol>
     */
    private static boolean isNetComponent(Location around) {
        String id = BlockStorage.checkID(around);
        if (id == null) {
            return false;
        }
        if (SlimefunItems.ENERGY_REGULATOR.getItemId().equals(id)) {
            return true;
        }
        SlimefunItem sf = SlimefunItem.getById(id);
        if (sf instanceof EnergyNetComponent component) {
            EnergyNetComponentType type = component.getEnergyComponentType();
            return type == EnergyNetComponentType.CAPACITOR
                    || type == EnergyNetComponentType.GENERATOR;
        }
        return false;
    }

    // ================================================================ 发电

    /**
     * 本 tick 应该产生多少电，并决定要不要推进燃料进度。
     *
     * <p>返回值的三种含义：
     * <ul>
     *   <li>{@code 0}：不发电。可能是结构没激活、没有进程、或者发电模式下储电已到阈值
     *       （<b>此时不推进度 = 暂停</b>）；</li>
     *   <li>{@code > 0}：发电量（J），并且已经推进了 1 tick 进度。</li>
     * </ul>
     *
     * <p>⚠ 主线程调用（本体 EnergyNet 的 tick 就在主线程）。
     */
    public static long generate(Location loc, FuelProgress progress, boolean structureComplete) {
        ensureLoaded();
        if (!structureComplete || !isActivated(loc)) {
            return 0L;
        }
        // 未接入电力网络时本体其实根本不会调到这个方法（EnergyNet 里没有这台发电机），
        // 这一句只是保险。
        if (!isConnectedToEnergyNet(loc)) {
            return 0L;
        }
        if (!progress.hasOperation()) {
            return 0L;
        }

        ReactorMode mode = getMode(loc);
        long production = config.energyProduction;

        if (mode == ReactorMode.PRODUCT) {
            // ★ 产物模式：进程由机器的 BlockTicker 自己推进（{@link #advanceProductProcess}），
            //   这样"离网"（7 格内没有能源调节器 → 本体不会 tick 这台发电机）时进程照样走。
            //   所以这里【只出电、绝不推进度】—— 否则接了电网的机器会被推两倍速度。
            //   ★ 发电量按 spec 打折：产物模式只发原本的 10%（换来 500% 工作效率）。
            //   本体 EnergyNet.storeRemainingEnergy 会把灌满后剩下的电直接丢弃，
            //   正是 spec 要的"溢出销毁"。
            long productEnergy = effectiveProduction(loc);
            TouhouData.addLong(loc, TouhouData.KEY_TOTAL_GENERATED, productEnergy);
            return productEnergy;
        }

        // 发电模式：到阈值就暂停（不推进度），进度留在 operation 里
        if (currentCharge(loc) >= config.modeThreshold) {
            return 0L;
        }
        progress.advance(1);
        TouhouData.addLong(loc, TouhouData.KEY_TOTAL_GENERATED, production);
        return production;
    }

    /**
     * 当前模式下这台机器<b>每 tick 实际发多少电</b>。
     *
     * <p>发电模式 = {@link AddonConfig#energyProduction}；
     * 产物模式 = 它 × {@link AddonConfig#productModeEnergyRate}（默认 10%）。
     */
    public static long effectiveProduction(Location loc) {
        long base = config.energyProduction;
        if (getMode(loc) == ReactorMode.PRODUCT) {
            return Math.max(0L, Math.round(base * config.productModeEnergyRate));
        }
        return base;
    }

    /**
     * 当前模式下这台机器的<b>工作效率</b>（每 tick 推进几个 tick 的进度）。
     *
     * <p>发电模式 = 1；产物模式 = {@link AddonConfig#productModeSpeedMultiplier}（默认 5 = 500%）。
     */
    public static int effectiveWorkSpeed(Location loc) {
        if (getMode(loc) != ReactorMode.PRODUCT) {
            return 1;
        }
        return Math.max(1, (int) Math.round(config.productModeSpeedMultiplier));
    }

    /**
     * <b>产物模式的自推进</b> —— 由机器的 {@code BlockTicker} 每 tick 调一次。
     *
     * <p>★ 这条路径是"产物模式无视条件进行进程"的落地点。
     * 之前产物模式的进程<b>也</b>只由 {@code getGeneratedOutput} 推进，而那个方法
     * 只有方块挂进本体电力网络时才会被调用 —— 于是"7 格内没有能源调节器"的机器
     * 切到产物模式后依然一动不动（用户实测反馈）。
     *
     * <p>本体 {@code Reactor} 的 {@code ReactorMode.PRODUCTION} 就是这种语义：
     * {@code generateEnergy} 里只有"装不下 && 发电模式"才 {@code return 0}，
     * 产物模式一律 {@code operation.addProgress(1)} —— <b>进程不依赖电的去处</b>。
     * 这里把同一语义搬到 BlockTicker 上，于是离网也能跑完进程、照常出产物。
     *
     * <p>代价（有意为之）：离网运行时电没人接收，这部分电就是白发的 ——
     * 与 spec 的"产物模式允许浪费电量"一致。
     *
     * @return 是否真的推进了 1 tick
     */
    public static boolean advanceProductProcess(Location loc, FuelProgress progress,
                                                boolean structureComplete) {
        ensureLoaded();
        if (!structureComplete || !isActivated(loc)) {
            return false;
        }
        if (getMode(loc) != ReactorMode.PRODUCT) {
            return false;
        }
        if (!progress.hasOperation()) {
            return false;
        }
        // ★ 产物模式的"工作效率"：一次推进 speed 个 tick（默认 5 = 500%）。
        //   推进量按 tick 算而不是重建进程，所以中途切模式立刻生效。
        progress.advance(effectiveWorkSpeed(loc));
        return true;
    }

    /** 当前储电（读本体写入的 energy-charge）。 */
    public static long currentCharge(Location loc) {
        return TouhouData.getLong(loc, "energy-charge", 0L);
    }

    // ================================================================ 模式

    public static ReactorMode getMode(Location loc) {
        return TouhouData.getEnum(loc, TouhouData.KEY_MODE, ReactorMode.class, ReactorMode.GENERATE);
    }

    /** 切换模式并返回新模式。 */
    public static ReactorMode toggleMode(Location loc) {
        ReactorMode next = getMode(loc).next();
        TouhouData.setEnum(loc, TouhouData.KEY_MODE, next);
        return next;
    }

    /** 直接设成指定模式并返回它（诊断 / 以后做 GUI 选择用）。 */
    public static ReactorMode setMode(Location loc, ReactorMode mode) {
        TouhouData.setEnum(loc, TouhouData.KEY_MODE, mode);
        return mode;
    }

    // ================================================================ 构建模式

    /**
     * 这台机器的<b>构建模式</b>（默认 {@link BuildMode#MANUAL}，兼容老机器）。
     *
     * <p>老存档的方块数据里没有这个 key —— 默认读成 MANUAL，
     * 正是加这个功能之前的行为（结构完整后要点信息格才激活）。
     */
    public static BuildMode getBuildMode(Location loc) {
        return TouhouData.getEnum(loc, TouhouData.KEY_BUILD_MODE, BuildMode.class, BuildMode.MANUAL);
    }

    /** 切换构建模式并返回新模式。 */
    public static BuildMode toggleBuildMode(Location loc) {
        BuildMode next = getBuildMode(loc).next();
        TouhouData.setEnum(loc, TouhouData.KEY_BUILD_MODE, next);
        return next;
    }

    /** 直接设置构建模式并返回它。 */
    public static BuildMode setBuildMode(Location loc, BuildMode mode) {
        TouhouData.setEnum(loc, TouhouData.KEY_BUILD_MODE, mode);
        return mode;
    }

    // ================================================================ 粒子特效开关

    /** 这台机器的附加粒子特效是否开着（默认开；方块数据里没写过就是开）。 */
    public static boolean isParticlesOn(Location loc) {
        return !"off".equals(TouhouData.getString(loc, TouhouData.KEY_PARTICLES, "on"));
    }

    /** 切换这台机器的粒子特效开关，返回切换后的状态。 */
    public static boolean toggleParticles(Location loc) {
        boolean next = !isParticlesOn(loc);
        TouhouData.setString(loc, TouhouData.KEY_PARTICLES, next ? "on" : "off");
        return next;
    }

    /** 直接设置粒子特效开关，返回设置后的状态。 */
    public static boolean setParticles(Location loc, boolean on) {
        TouhouData.setString(loc, TouhouData.KEY_PARTICLES, on ? "on" : "off");
        return on;
    }

    // ================================================================ 燃料与进度

    /** 一次燃料进程的抽象（把 FuelOperation 包起来，免得状态机直接依赖它）。 */
    public interface FuelProgress {

        boolean hasOperation();

        /** 已燃 tick 数。 */
        int progress();

        /** 总 tick 数。 */
        int totalTicks();

        /**
         * 推进进度。
         *
         * @param ticks 推进几个 tick —— 发电模式推 1；产物模式推
         *              {@link AddonConfig#productModeSpeedMultiplier}（工作效率倍率）。
         *              一次推进多格（而不是重建进程）的好处是<b>中途切换模式立刻生效</b>。
         */
        void advance(int ticks);

        /** 剩余 tick 数。 */
        default int remaining() {
            return Math.max(0, totalTicks() - progress());
        }
    }

    /** "没有进程"的空实现（{@link FuelProgress} 的 null 对象版本）。 */
    public static final FuelProgress NO_PROGRESS = new FuelProgress() {
        @Override
        public boolean hasOperation() {
            return false;
        }

        @Override
        public int progress() {
            return 0;
        }

        @Override
        public int totalTicks() {
            return 0;
        }

        @Override
        public void advance(int ticks) {
            // 没有进程，什么也不做
        }
    };

    /**
     * 判断某个物品是不是本机器认的燃料。
     *
     * <p>用 {@link SlimefunUtils#isItemSimilar(ItemStack, ItemStack, boolean)}
     * 与 {@link SlimefunItems#OIL_BUCKET} 比对 —— 这是本体自己的比法
     * （{@code AGenerator#isBucket} 就是这么判油桶/燃料桶/岩浆桶的），
     * 比"看 Material 是不是 LAVA_BUCKET"可靠得多（粘液物品的材质可能被模型改掉）。
     */
    public static boolean isFuel(ItemStack item) {
        if (item == null) {
            return false;
        }
        return SlimefunUtils.isItemSimilar(item, SlimefunItems.OIL_BUCKET, true);
    }

    /** 某个槽位里的燃料还能跑多少 tick。 */
    public static int fuelTicksIn(ItemStack item) {
        return item == null ? 0 : item.getAmount() * config().processTicks;
    }

    /** 燃料显示名（报告/日志用）。 */
    public static String fuelName(ItemStack item) {
        if (item == null) {
            return "(空)";
        }
        SlimefunItem sf = SlimefunItem.getByItem(item);
        return (sf == null ? item.getType().toString() : sf.getId()) + " x" + item.getAmount();
    }

    // ================================================================ GUI 文案

    /** Slimefun tick → 秒（tickRate 可能不是 1）。 */
    public static double ticksToSeconds(long ticks) {
        double tps = 20.0 / Slimefun.getTickerTask().getTickRate();
        return ticks / tps;
    }

    /** "1234 tick (约 61.7 秒)"。 */
    public static String formatTicks(long ticks) {
        return ticks + " tick（约 " + String.format("%.1f", ticksToSeconds(ticks)) + " 秒）";
    }

    public static String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    /** 供命令/调试输出的当前状态摘要。 */
    public static java.util.List<String> describe(Location loc) {
        ensureLoaded();
        java.util.List<String> out = new java.util.ArrayList<>();
        ReactorStructure.Result r = structure.check(loc);
        Runtime rt = RUNTIME.get(TouhouData.norm(loc));
        out.add("结构实现   : " + structure.name());
        out.add("结构完整   : " + r.isComplete() + "  (" + r.summary() + ")");
        out.add("已激活     : " + (rt != null && rt.activated));
        out.add("状态       : " + cachedState(loc).display());
        ReactorStructure.Direction dir = storedDirection(loc);
        out.add("结构朝向   : " + (dir == null ? "(未记录)"
                : dir.label() + "  " + describeSymmetric()));
        out.add("模式       : " + getMode(loc).display());
        out.add("构建模式   : " + getBuildMode(loc).display());
        out.add("检测状态   : " + describeScanState(loc));
        out.add("附加粒子   : " + (isParticlesOn(loc) ? "开" : "关"));
        out.add("储电       : " + currentCharge(loc) + " / " + config.energyCapacity);
        out.addAll(config.describe());
        return out;
    }

    /** 清理某个位置的运行期状态（方块被破坏时调用，避免 Map 泄漏）。 */
    public static void forget(Location loc) {
        RUNTIME.remove(TouhouData.norm(loc));
        PENDING_CHECKS.remove(TouhouData.norm(loc));
        LAST_EDITOR.remove(TouhouData.norm(loc));
        // ★ 投影也在这里收掉：核心都没了，幻影结构不能留在世界上
        //   （见 MultiBlockProjection 的类注释 —— 这正是 LogiTech 那个
        //   "旧组变孤儿"的坑，我们把它收在"忘记这台机器"的同一个入口里）
        MultiBlockProjection.forget(loc);
    }

    /** 结构对称性的一句话说明（诊断用）。 */
    public static String describeSymmetric() {
        ensureLoaded();
        if (structure instanceof LayeredReactorStructure layered) {
            return layered.isSymmetric()
                    ? "（四向对称，只探 NORTH）"
                    : "（不对称，探测 4 个方向）";
        }
        return "";
    }

    /**
     * 诊断用：无视对称性优化，把四个方向各检测一遍。
     *
     * <p>给 {@code /touhou structure <x> <y> <z> alldirs} 用 ——
     * 对称结构平时只探 NORTH，"四向适配到底生效没有"看不出来，这个能直接给证据。
     */
    public static java.util.List<String> probeAllDirections(Location loc) {
        ensureLoaded();
        if (structure instanceof LayeredReactorStructure layered) {
            return layered.probeAllDirections(loc);
        }
        return java.util.List.of("（当前结构实现不是 LayeredReactorStructure，不支持四向探测）");
    }

    // ================================================================ 无头测试

    /**
     * <b>只给命令用</b>：把状态机连着推进 n 次（不读世界之外的东西、不碰 GUI）。
     *
     * <p>存在理由：自动构建（{@link BuildMode#AUTO}）依赖的是"tick 若干次之后
     * 计数器到期 → 检测 → 自动激活"这条<b>时间线</b>，而它无法靠单次调用验证 ——
     * 控制台里没有真实 ticker 会替我们推进。这个方法走的是与
     * {@code UtsuhoReactorCore.tickOnce} 完全相同的 {@link #updateState}，
     * 所以能无头复现"搭好结构 → 等 N tick → 自动跑起来"。
     *
     * <p>返回每一次状态变化，让调用方打印出完整时间线（而不是只看最终值）。
     *
     * @param loc  核心位置
     * @param n    推进几次
     * @param tick 每次推进要附带执行的"机器那一 tick 该做的事"（可为 null）
     * @return 形如 {@code "t1 IDLE"} 的逐步记录；只有状态发生变化才记一行
     */
    public static java.util.List<String> simulateTicks(Location loc, int n,
                                                       Runnable tick) {
        java.util.List<String> out = new java.util.ArrayList<>();
        ReactorState prev = null;
        for (int i = 1; i <= n; i++) {
            ReactorState state = updateState(loc, false);
            if (tick != null) {
                tick.run();
            }
            if (state != prev) {
                out.add("t" + i + " → " + state.display()
                        + "  （已检测=" + isStructureValid(loc) + "）");
                prev = state;
            }
        }
        out.add("共推进 " + n + " tick，最终状态 " + cachedState(loc).display()
                + "，已激活=" + isActivated(loc));
        return out;
    }
}
