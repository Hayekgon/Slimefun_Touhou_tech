package com.example.touhou;

import com.example.touhou.core.AddGroups;
import com.example.touhou.core.AddItems;
import com.example.touhou.core.AddonConfig;
import com.example.touhou.core.Log;
import com.example.touhou.core.MultiBlockProjection;
import com.example.touhou.core.ReactorManager;
import com.example.touhou.core.ReactorMode;
import com.example.touhou.core.ReactorStructure;
import com.example.touhou.core.Saizenbako;
import com.example.touhou.core.TouhouData;
import com.example.touhou.core.UtsuhoReactorCore;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@code /touhou} 命令 —— 让所有机器都能在<b>控制台</b>无头验证，不必真人进游戏。
 *
 * <pre>
 * /touhou reactor &lt;x&gt; &lt;y&gt; &lt;z&gt; info        当前状态 / 结构 / 模式 / 储电 / 燃料
 * /touhou reactor &lt;x&gt; &lt;y&gt; &lt;z&gt; activate    模拟"点击 GUI 信息格激活"
 * /touhou reactor &lt;x&gt; &lt;y&gt; &lt;z&gt; mode        切换发电模式 / 产物模式
 * /touhou reactor &lt;x&gt; &lt;y&gt; &lt;z&gt; test [n]    塞 1 个原油桶并模拟 n 次发电 tick（默认 12）
 * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;动作&gt;          多方块投影：on / off / info / cells / count / clean
 * /touhou proj list                      列出当前持有的投影组与实体数
 * /touhou saizen &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;动作&gt;        赛钱箱：info / check / activate / slots / posts / tick …
 * /touhou structure &lt;x&gt; &lt;y&gt; &lt;z&gt;           只检测多方块结构（会打缺失明细）
 * /touhou place &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;sfId&gt;       无玩家放置粘液方块（写方块数据 + 建菜单）
 * /touhou remove &lt;x&gt; &lt;y&gt; &lt;z&gt;              连世界方块带 Slimefun 方块数据一起删干净
 * /touhou edit &lt;x&gt; &lt;y&gt; &lt;z&gt; [placed|broken]  模拟结构变动（现在唯一的常规检测触发途径）
 * /touhou gui [x y z]                      GUI 锁槽自检（防占位符被拿走）
 * /touhou layout                           反应堆 GUI 布局自检（与 spec 逐格比对）
 * /touhou groups                           物品组层级自检
 * /touhou reload                           重载 config.yml 与结构层图
 * </pre>
 *
 * <p>★ 为什么这些命令值得存在：本工程所有机器的<b>操作入口都在 GUI 里</b>，
 * 而 GUI 点击无法从控制台触发。所以每个入口都配了一个走<b>完全相同</b>代码路径的命令
 * （例如 {@code proj on} 调的就是投影开关按钮调的那个 {@link MultiBlockProjection#toggle}）
 * —— 这样"控制台验证过的行为"就是"玩家点出来的行为"。
 *
 * <p>★ 命令回显统一走 {@link Log#command}：<b>不受</b> {@code logging.console-info} 总开关影响
 * （命令是主动敲的，一次一条，不会刷屏，而且是排查时最需要的线索）。
 */
public class TohouCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "\u00a78[\u00a7dTH\u00a78] \u00a7r";

    /** `io scans` 的上一次读数（算"每秒扫描多少次"用）。 */
    private static final java.util.concurrent.atomic.AtomicLong SCAN_SAMPLE =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong SCAN_SAMPLE_TIME =
            new java.util.concurrent.atomic.AtomicLong();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "power" -> power(sender, Arrays.copyOfRange(args, 1, args.length));
            case "autobuild" -> autobuild(sender, Arrays.copyOfRange(args, 1, args.length));
            case "reactor" -> reactor(sender, Arrays.copyOfRange(args, 1, args.length));
            case "clickinfo" -> clickInfo(sender, Arrays.copyOfRange(args, 1, args.length));
            case "proj", "projection" -> proj(sender, Arrays.copyOfRange(args, 1, args.length));
            case "structure" -> structure(sender, Arrays.copyOfRange(args, 1, args.length));
            case "saizen" -> saizen(sender, Arrays.copyOfRange(args, 1, args.length));
            case "place" -> place(sender, Arrays.copyOfRange(args, 1, args.length));
            case "gui" -> guiCheck(sender, Arrays.copyOfRange(args, 1, args.length));
            case "clickpart", "edit" -> editBlock(sender, Arrays.copyOfRange(args, 1, args.length));
            case "remove" -> removeBlock(sender, Arrays.copyOfRange(args, 1, args.length));
            case "layout" -> {
                sender.sendMessage(PREFIX + "\u00a7e反应堆 GUI 布局自检（与 spec 逐格比对）");
                for (String line : UtsuhoReactorCore.layoutSummary()) {
                    sender.sendMessage("\u00a78" + line);
                }
            }
            case "groups" -> {
                sender.sendMessage(PREFIX + "\u00a7eTH Tech 物品组层级");
                sender.sendMessage("\u00a78" + AddGroups.describeHierarchy());
                for (String line : AddGroups.describe()) {
                    sender.sendMessage("\u00a78  " + line);
                }
                for (String line : AddGroups.describeMenus()) {
                    sender.sendMessage("\u00a77  " + line);
                }
                for (String line : AddGroups.describeItems()) {
                    sender.sendMessage("\u00a78" + line);
                }
                for (String line : AddGroups.mainMenuPreview()) {
                    sender.sendMessage("\u00a7b  " + line);
                }
            }
            case "reload" -> {
                Touhou.getInstance().reloadConfig();
                AddonConfig.reload();
                ReactorManager.reload();
                // GUI 注册器登记表也要清：结构/配置重载后界面会重建，旧引用没意义了
                com.example.touhou.core.GuiLock.clearRegistry();
                // uid → 核心 的内存表同理（方块数据里的 uid 保留，等核心重连时重新填表）
                com.example.touhou.core.StructureRegistry.clearCache();
                sender.sendMessage(PREFIX + "\u00a7a配置与结构已重载");
                for (String line : ReactorManager.config().describe()) {
                    sender.sendMessage("\u00a78  " + line);
                }
                sender.sendMessage("\u00a78  结构实现: " + ReactorManager.structure().name());
                sender.sendMessage(PREFIX + "\u00a7e消息栏档位: "
                        + com.example.touhou.core.Notify.currentLevel().display());
            }
            case "messages", "msg" -> {
                // 消息栏档位的查看（改档位请改 config.yml 的 messages.level 后 /touhou reload）
                sender.sendMessage(PREFIX + "\u00a7e游戏内消息栏档位: "
                        + com.example.touhou.core.Notify.currentLevel().display());
                for (String line : com.example.touhou.core.Notify.describePolicy()) {
                    sender.sendMessage("\u00a78  " + line);
                }
                log("[TOUHOU] messages -> " + com.example.touhou.core.Notify.currentLevel().key());
            }
            default -> help(sender);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(PREFIX + "\u00a7eTH Tech 命令");
        s.sendMessage("\u00a77/touhou reactor <x> <y> <z> [info|test [n] [charge]|activate|mode|buildmode|scan|abort|gate|start|charge <n>|inv|raw|tags|particles|io]");
        s.sendMessage("\u00a77/touhou reactor <x> <y> <z> io [layout|fill|abort|reset|seed|count|scans|guard]   IO 接口诊断");
        s.sendMessage("\u00a77/touhou autobuild <x> <y> <z> [manual|auto] [n]   构建模式时间线验证（无头推 tick）");
        s.sendMessage("\u00a77/touhou clickinfo <x> <y> <z>       模拟玩家点击 GUI 的信息格（激活入口）");
        s.sendMessage("\u00a77/touhou proj <x> <y> <z> [on|off|toggle|info|cells|count|clean [r]]   多方块投影");
        s.sendMessage("\u00a77/touhou proj list                  列出当前持有的投影组与实体数");
        s.sendMessage("\u00a77/touhou structure <x> <y> <z> [alldirs]");
        s.sendMessage("\u00a77/touhou saizen <x> <y> <z> [info|check|activate|deactivate|slots|posts|recipe|seed|tick [n]|charge <n>|guard|alldirs]");
        s.sendMessage("\u00a77/touhou place <x> <y> <z> <sfId> [world] [--force]");
        s.sendMessage("\u00a77/touhou remove <x> <y> <z>            删除方块 + Slimefun 方块数据（setblock 清不掉）");
        s.sendMessage("\u00a77/touhou edit <x> <y> <z> [placed|broken]  模拟结构变动（现在唯一的常规检测触发途径）");
        s.sendMessage("\u00a77/touhou gui [x y z]                   GUI 锁槽自检（防占位符被拿走）");
        s.sendMessage("\u00a77/touhou layout | groups | messages | reload | power [rebuild]");
    }

    // ------------------------------------------------------------------ power

    /**
     * POWER 网络诊断。
     *
     * <pre>
     *   /touhou power            看向一个 POWER 方块（8 格内）后执行
     *   /touhou power &lt;x y z&gt;    诊断指定方块
     *   /touhou power rebuild    清空网络缓存，下次 tick 自动重算（排查用）
     * </pre>
     */
    private void power(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("rebuild")) {
            int n = com.example.touhou.power.PowerNetworkManager.rebuildAll();
            sender.sendMessage(PREFIX + "\u00a7a已清空网络缓存（原 " + n + " 个节点），下次 tick 自动重算");
            return;
        }
        if (!(sender instanceof org.bukkit.entity.Player p)) {
            sender.sendMessage(PREFIX + "\u00a77控制台看不到方块，请用: /touhou power rebuild");
            return;
        }
        Location loc;
        if (args.length >= 3) {
            try {
                loc = new Location(p.getWorld(),
                        Integer.parseInt(args[0]), Integer.parseInt(args[1]), Integer.parseInt(args[2]));
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + "\u00a7c用法: /touhou power [x y z | rebuild]");
                return;
            }
        } else {
            org.bukkit.block.Block b = p.getTargetBlockExact(8);
            loc = b != null ? b.getLocation() : p.getLocation();
        }
        sender.sendMessage(PREFIX + "\u00a7ePOWER 网络诊断");
        for (String line : com.example.touhou.power.PowerNetworkManager.describe(loc)) {
            sender.sendMessage("\u00a78  " + line);
        }
    }

    // ------------------------------------------------------------------ remove

    /**
     * 无头"拆掉"一个粘液方块 —— 把世界方块设成空气，<b>并清掉它的 Slimefun 方块数据</b>。
     *
     * <pre>/touhou remove &lt;x&gt; &lt;y&gt; &lt;z&gt;</pre>
     *
     * <p>★ 为什么不能用 {@code /setblock ... air}：那只改世界方块，<b>不清 Slimefun 方块数据</b>；
     * 而结构检测走的是 {@link BlockStorage#checkID}，于是"看起来拆了，检测却仍认为构件在"。
     * 这个命令是给<b>控制台无人值守测试</b>用的等价手段。
     */
    private void removeBlock(CommandSender sender, String[] args) {
        Location loc = resolve(sender, args);
        if (loc == null) {
            return;
        }
        String before = BlockStorage.checkID(loc);
        if (before == null) {
            sender.sendMessage(PREFIX + "\u00a77该位置本来就没有 Slimefun 方块数据 @ " + xyz(loc));
        }
        BlockStorage.clearBlockInfo(loc);
        loc.getBlock().setType(Material.AIR);
        sender.sendMessage(PREFIX + "\u00a7e已拆除 @ " + xyz(loc)
                + "  原 id=" + (before == null ? "(无)" : before)
                + "  现在的 checkID=" + BlockStorage.checkID(loc));
        log("[TOUHOU] remove @ " + xyz(loc) + " was=" + before);
    }

    // ------------------------------------------------------------------ edit / clickpart

    /**
     * 无头模拟「结构变动」—— 放置 / 破坏都会触发同一条检测链路。
     *
     * <pre>
     * /touhou edit &lt;x&gt; &lt;y&gt; &lt;z&gt; [placed|broken]
     * /touhou clickpart &lt;x&gt; &lt;y&gt; &lt;z&gt;        旧名字，等价于 edit ... placed（保留兼容）
     * </pre>
     *
     * <p>★ 下标是 3 不是 4：子命令名 "edit" 已经在 {@code onCommand} 里被
     * {@code copyOfRange} 剥掉了，所以 {@code args = [x, y, z, placed|broken]}。
     * （本工程实测踩过：写成 {@code args[4]} 时"broken"这条分支永远测不到。）
     */
    private void editBlock(CommandSender sender, String[] args) {
        Location loc = resolve(sender, args);
        if (loc == null) {
            return;
        }
        ReactorManager.Edit edit = ReactorManager.Edit.PLACED;
        if (args.length >= 4 && args[3].toLowerCase().startsWith("b")) {
            edit = ReactorManager.Edit.BROKEN;
        }
        SlimefunItem item = BlockStorage.check(loc);
        sender.sendMessage(PREFIX + "\u00a7e模拟结构变动（" + edit.label() + "）@ " + xyz(loc) + "  "
                + (item == null ? "\u00a7c(非 Slimefun 方块)" : item.getId()));
        String note = ReactorManager.onStructureEdited(loc, edit, null);
        sender.sendMessage("\u00a77  " + color(note));
        log("[TOUHOU] edit(" + edit + ") @ " + xyz(loc)
                + " item=" + (item == null ? "null" : item.getId()) + " -> " + note);
        sender.sendMessage("\u00a78  （检测在下一 tick 执行；用 /touhou reactor <核心> scan 看累计次数）");
    }

    // ------------------------------------------------------------------ gui

    /**
     * 全局 GUI 锁槽自检。
     *
     * <pre>
     * /touhou gui            列出所有已构建过界面的机器 + 各自锁槽结论
     * /touhou gui &lt;x&gt; &lt;y&gt; &lt;z&gt;  只看某一台
     * </pre>
     *
     * <p>为什么要有这个命令：「占位符 / 按钮图标能被拿走」这个 bug 在本工程
     * <b>三个 GUI 里各犯过一次</b>（返回值语义读反 + 注册被覆盖）。
     * 修好之后加一条可随时执行的核对手段，比"记住别再犯"可靠。
     */
    private void guiCheck(CommandSender sender, String[] args) {
        if (args.length >= 3) {
            Location loc = resolve(sender, args);
            if (loc == null) {
                return;
            }
            SlimefunItem item = BlockStorage.check(loc);
            reportOne(sender, loc, item);
            return;
        }

        sender.sendMessage(PREFIX + "\u00a7e全局 GUI 锁槽自检");
        sender.sendMessage("\u00a77  （只列出已经被打开过界面的机器；没打开过的还没构建 preset）");
        int n = 0;
        for (String line : com.example.touhou.core.GuiLock.describeAll()) {
            sender.sendMessage("\u00a78  " + line);
            n++;
        }
        if (n == 0) {
            sender.sendMessage("\u00a78  （还没有任何机器界面被构建过 \u2014\u2014 先右键打开一次界面）");
        }
        log("[TOUHOU] gui check -> " + n + " 行");
    }

    /** 报告某一台机器的锁槽情况。 */
    private void reportOne(CommandSender sender, Location loc, SlimefunItem item) {
        sender.sendMessage(PREFIX + "\u00a7e锁槽自检 @ " + xyz(loc) + "  "
                + (item == null ? "\u00a7c(非 Slimefun 方块)" : item.getId()));
        if (item instanceof UtsuhoReactorCore core) {
            if (core.guiLock() == null) {
                sender.sendMessage("\u00a78  ? 这台核心还没构建过界面（右键打开一次即可）");
                return;
            }
            for (String line : core.guiLock().report()) {
                sender.sendMessage("\u00a78" + line);
            }
            sender.sendMessage("\u00a77  投影开关槽 " + UtsuhoReactorCore.HOLOGRAM_SLOT + " 锁死="
                    + (!core.guiLock().isRealSlot(UtsuhoReactorCore.HOLOGRAM_SLOT)
                            ? "\u00a7a是" : "\u00a7c否"));
        } else if (item instanceof com.example.touhou.core.AbstractReactorPort port) {
            for (String line : com.example.touhou.core.AbstractReactorPort.guardReport(port)) {
                sender.sendMessage("\u00a78" + line);
            }
        } else if (item instanceof com.example.touhou.core.ShrinePost post) {
            if (post.guiLock() == null) {
                sender.sendMessage("\u00a78  ? 这根木桩还没构建过界面（右键打开一次即可）");
                return;
            }
            for (String line : post.guiLock().report()) {
                sender.sendMessage("\u00a78" + line);
            }
            sender.sendMessage("\u00a77  可放取槽应恰好 1 个（IO="
                    + com.example.touhou.core.ShrinePost.IO_SLOT + "）");
            sender.sendMessage("\u00a77  全局防护（拖拽/双击/背包Shift）="
                    + (com.example.touhou.core.PortGuiListener.covers(item)
                            ? "\u00a7a已覆盖（PortGuiListener）" : "\u00a7c未覆盖")
                    + "  Shift 只许流入=" + Arrays.toString(post.shiftInsertSlots()));
        } else if (item instanceof Saizenbako saizen) {
            if (saizen.guiLock() == null) {
                sender.sendMessage("\u00a78  ? 这台赛钱箱还没构建过界面（右键打开一次即可）");
                return;
            }
            for (String line : saizen.guiLock().report()) {
                sender.sendMessage("\u00a78" + line);
            }
            // 「禁用一切交互」的逐条核对：可放取槽只有 IO 一格，
            // 6 个预留槽 + 6 个指示槽 + 投影开关槽都在锁死名单里，且预留槽不在物流表里。
            sender.sendMessage("\u00a77  可放取槽应恰好 1 个（IO=" + Saizenbako.IO_SLOT + "）");
            for (int slot : Saizenbako.RESERVED_SLOTS) {
                boolean locked = !saizen.guiLock().isRealSlot(slot);
                sender.sendMessage("\u00a77  预留槽 " + slot + " 锁死=" + (locked ? "\u00a7a是" : "\u00a7c否"));
            }
            for (int slot : Saizenbako.INDEX_SLOTS) {
                boolean locked = !saizen.guiLock().isRealSlot(slot);
                sender.sendMessage("\u00a77  指示槽 " + slot + " 锁死=" + (locked ? "\u00a7a是" : "\u00a7c否"));
            }
            sender.sendMessage("\u00a77  投影开关槽 " + Saizenbako.HOLOGRAM_SLOT + " 锁死="
                    + (!saizen.guiLock().isRealSlot(Saizenbako.HOLOGRAM_SLOT)
                            ? "\u00a7a是" : "\u00a7c否"));
            sender.sendMessage("\u00a77  物流表=" + Arrays.toString(new int[] {Saizenbako.IO_SLOT})
                    + "（预留槽不在表里 \u21d2 Cargo/漏斗碰不到）");
            sender.sendMessage("\u00a77  全局防护（拖拽/双击/背包Shift）="
                    + (com.example.touhou.core.PortGuiListener.covers(item)
                            ? "\u00a7a已覆盖（PortGuiListener）" : "\u00a7c未覆盖")
                    + "  Shift 只许流入=" + Arrays.toString(saizen.shiftInsertSlots()));
        } else {
            sender.sendMessage("\u00a7c  这个方块没有自研界面");
        }
    }

    // ------------------------------------------------------------------ proj

    /**
     * <b>多方块投影</b>的无头诊断与开关。
     *
     * <pre>
     * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; on       开启投影（等价于点 GUI 的投影开关）
     * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; off      关闭投影
     * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; toggle   切换（默认动作）
     * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; info     开关状态 / 缓存 / 附近实体计数 / 落点表
     * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; cells    只打印落点表（逐格 partId + 偏移 + 旋转后世界坐标）
     * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; count    只数附近带标记的投影实体（验证"真的生成了"）
     * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; clean [r]  清孤儿投影（LogiTech 的 HOLOGRAM_REMOVER 等价物）
     * /touhou proj list                  列出当前本插件持有的全部投影组
     * </pre>
     *
     * <p>{@code count} 是"投影到底有没有真的生成实体"的<b>证据</b>：它数的是世界里带
     * {@code display-source} 标记的 Display/Interaction —— 与开关状态（方块数据）
     * 是两套独立读数，互相印证。
     */
    private void proj(CommandSender sender, String[] args) {
        // proj list：不需要坐标
        if (args.length >= 1 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(PREFIX + "\u00a7e当前持有的多方块投影");
            sender.sendMessage("\u00a77  投影组 " + MultiBlockProjection.cachedGroupCount()
                    + " 组 / 实体 " + MultiBlockProjection.ownedEntityCount() + " 个"
                    + "  上次画出 " + MultiBlockProjection.lastCellCount()
                    + " 格 / 被拒绝 " + MultiBlockProjection.refusalCount() + " 次");
            for (String line : MultiBlockProjection.describePolicy()) {
                sender.sendMessage("\u00a78  " + line);
            }
            log("[TOUHOU] proj list groups=" + MultiBlockProjection.cachedGroupCount()
                    + " entities=" + MultiBlockProjection.ownedEntityCount());
            return;
        }

        Location loc = resolve(sender, args);
        if (loc == null) {
            return;
        }
        String sub = args.length >= 4 ? args[3].toLowerCase() : "toggle";
        SlimefunItem item = BlockStorage.check(loc);

        // 关闭不挑方块类型：任何位置都允许清（核心被拆之后还剩"半个方块"时也能收尾）
        if (sub.equals("off")) {
            MultiBlockProjection.hide(loc);
            sender.sendMessage(PREFIX + "\u00a77已关闭投影 @ " + xyz(loc)
                    + "  剩余附近实体 " + MultiBlockProjection.countNearby(loc, 16));
            log("[TOUHOU] proj off @ " + xyz(loc));
            return;
        }

        if (sub.equals("count")) {
            int n = MultiBlockProjection.countNearby(loc, 16);
            sender.sendMessage(PREFIX + "\u00a7e投影实体计数 @ " + xyz(loc));
            sender.sendMessage("\u00a77  16 格内带 display-source 标记的 Display/Interaction：\u00a7f" + n);
            sender.sendMessage("\u00a77  方块数据开关 " + MultiBlockProjection.KEY_HOLOGRAM
                    + " = " + (MultiBlockProjection.isOn(loc) ? "\u00a7aon" : "\u00a77off"));
            sender.sendMessage("\u00a78  两者应当一致：开 = 计数 > 0（父实体 1 + 每格 1），关 = 计数 0");
            log("[TOUHOU] proj count @ " + xyz(loc) + " -> " + n);
            return;
        }

        if (sub.equals("clean")) {
            int range = args.length >= 5 ? parseIntOr(args[4],
                    AddonConfig.get().projectionCleanRadius)
                    : AddonConfig.get().projectionCleanRadius;
            int removed = MultiBlockProjection.removeOrphans(loc, range);
            sender.sendMessage(PREFIX + "\u00a7e清理孤儿投影 @ " + xyz(loc)
                    + "  半径 " + range + " 格");
            sender.sendMessage("\u00a77  已删除 \u00a7f" + removed + " \u00a77个实体"
                    + "（只删带标记、又不在当前持有表里的；正在用的投影不会被误删）");
            log("[TOUHOU] proj clean @ " + xyz(loc) + " range=" + range + " removed=" + removed);
            return;
        }

        // 以下动作要认方块类型（落点表只有接入了投影的核心才拿得到）
        if (sub.equals("info") || sub.equals("cells")) {
            sender.sendMessage(PREFIX + "\u00a7e投影状态 @ " + xyz(loc) + "  ("
                    + (item == null ? "\u00a7c非 Slimefun 方块" : item.getId()) + ")");
            for (String line : MultiBlockProjection.describe(loc)) {
                sender.sendMessage("\u00a77  " + line);
            }
            sender.sendMessage("\u00a77  附近实体: \u00a7f" + MultiBlockProjection.countNearby(loc, 16));
            // 落点表：直接证明"投影画在哪、画的是什么"
            ReactorStructure.ProjectionHost host = hostOf(item);
            if (host != null) {
                ReactorStructure st = host.structure();
                ReactorStructure.Direction dir = ReactorManager.storedDirection(loc);
                List<ReactorStructure.Cell> cells = st.cells();
                List<ReactorStructure.Cell> drawable = ReactorStructure.solidCells(cells);
                sender.sendMessage("\u00a77  结构实现: " + st.getClass().getName());
                sender.sendMessage("\u00a77  结构 " + st.name()
                        + "  落点 " + cells.size() + " 格（含空气要求）"
                        + "  可画 " + drawable.size() + " 格"
                        + "  四向对称=" + st.isSymmetric()
                        + "  已落盘朝向=" + (dir == null ? "(无)" : dir.label()));
                if (st instanceof com.example.touhou.core.LayeredReactorStructure layered) {
                    int[] sz = layered.size();
                    sender.sendMessage("\u00a77  层图尺寸 " + sz[0] + "x" + sz[1] + "x" + sz[2]
                            + "  构件计数 " + layered.partCount()
                            + "  核心在图内位置(层,行,列)=" + Arrays.toString(layered.corePosition()));
                }
                if (sub.equals("cells")) {
                    // 逐格打印：偏移 → 旋转后的世界坐标 → 该格要什么
                    ReactorStructure.Direction use = st.isSymmetric() || dir == null
                            ? ReactorStructure.Direction.NORTH : dir;
                    int n = 0;
                    for (ReactorStructure.Cell c : drawable) {
                        int[] rot = use.rotate(c.dx(), c.dz());
                        sender.sendMessage("\u00a78  " + String.format("%-3s", c.id())
                                + " 偏移(" + c.dx() + "," + c.dy() + "," + c.dz() + ")"
                                + " \u2192 (" + (loc.getBlockX() + rot[0]) + ","
                                + (loc.getBlockY() + c.dy()) + ","
                                + (loc.getBlockZ() + rot[1]) + ")");
                        if (++n >= 60) {
                            sender.sendMessage("\u00a78  …（还有 " + (drawable.size() - n) + " 格）");
                            break;
                        }
                    }
                }
            } else {
                sender.sendMessage("\u00a7c  这个方块不是已接入投影的多方块核心");
            }
            log("[TOUHOU] proj " + sub + " @ " + xyz(loc) + " on=" + MultiBlockProjection.isOn(loc));
            return;
        }

        // on / toggle：走与 GUI 完全相同的那条链路
        boolean wantOn = sub.equals("on");
        boolean before = MultiBlockProjection.isOn(loc);
        boolean nowOn;
        if (before == wantOn) {
            sender.sendMessage(PREFIX + "\u00a77投影已经是 "
                    + (before ? "\u00a7a开" : "\u00a77关") + " \u00a77状态，未改动");
            nowOn = before;
        } else if (item instanceof UtsuhoReactorCore) {
            nowOn = UtsuhoReactorCore.toggleProjection(null, loc);
        } else if (item instanceof Saizenbako) {
            nowOn = Saizenbako.toggleProjection(null, loc);
        } else {
            sender.sendMessage(PREFIX + "\u00a7c这个方块不是多方块核心，无法开关投影（实际 "
                    + (item == null ? "\u00a7c非 Slimefun 方块" : item.getId()) + "）");
            sender.sendMessage("\u00a78  已知可开关的："
                    + AddItems.UTSUHO_REACTOR_CORE.getItemId() + " / " + AddonConfig.SAIZEN_CORE_ID);
            return;
        }
        sender.sendMessage(PREFIX + "\u00a7e投影现在："
                + (nowOn ? "\u00a7a开" : "\u00a77关")
                + " \u00a77附近实体 \u00a7f" + MultiBlockProjection.countNearby(loc, 16));
        log("[TOUHOU] proj " + sub + " @ " + xyz(loc) + " -> " + (nowOn ? "on" : "off")
                + " nearby=" + MultiBlockProjection.countNearby(loc, 16));
    }

    /**
     * 从方块反查它的投影宿主（{@link ReactorStructure.ProjectionHost}）。
     *
     * <p>★ 这里就是"未来新结构直接可投影"的接入点：新增一种多方块核心时，
     * 只需要在这里多认一个类型 + 它自己的 {@code projectionHost()}，
     * 投影机制本身（{@link MultiBlockProjection}）一行都不用动。
     */
    private static ReactorStructure.ProjectionHost hostOf(SlimefunItem item) {
        if (item instanceof UtsuhoReactorCore) {
            return UtsuhoReactorCore.projectionHost();
        }
        if (item instanceof Saizenbako) {
            return Saizenbako.projectionHost();
        }
        return null;
    }

    // ------------------------------------------------------------------ clickinfo

    /**
     * 模拟"玩家点击 GUI 信息格（J）"。
     *
     * <p>存在的意义：激活入口在 GUI 里，而 GUI 点击无法从控制台触发；
     * 这个方法调用的是与点击<b>完全相同</b>的那段逻辑
     * （{@link UtsuhoReactorCore#simulateInfoClick}）。
     */
    private void clickInfo(CommandSender sender, String[] args) {
        Location loc = resolve(sender, args);
        if (loc == null) {
            return;
        }
        SlimefunItem item = BlockStorage.check(loc);
        if (!(item instanceof UtsuhoReactorCore reactor)) {
            sender.sendMessage(PREFIX + "\u00a7c该方块不是灵乌路空反应堆（实际 "
                    + (item == null ? "\u00a7c非 Slimefun 方块" : item.getId()) + "）");
            return;
        }
        String before = ReactorManager.cachedState(loc).display();
        String feedback = reactor.simulateInfoClick(loc, sender::sendMessage);
        sender.sendMessage(PREFIX + "模拟点击信息格 @ " + xyz(loc));
        sender.sendMessage(PREFIX + "\u00a77状态 " + before + " \u00a77\u2192 \u00a7f"
                + ReactorManager.cachedState(loc).display());
        sender.sendMessage(PREFIX + "反馈: " + feedback);
        log("[TOUHOU] clickinfo @ " + xyz(loc) + " -> " + ReactorManager.cachedState(loc));
    }

    // ------------------------------------------------------------------ autobuild

    /**
     * 无头验证<b>构建模式</b>：把状态机真推进若干 tick，打印状态时间线。
     *
     * <pre>/touhou autobuild &lt;x&gt; &lt;y&gt; &lt;z&gt; [manual|auto] [n]</pre>
     *
     * <p>为什么不能只靠 {@code reactor ... buildmode} 验证：那个只证明"设置写进去了"，
     * 而自动构建的关键是<b>时间线</b> —— 结构完整后要等检测触发才被激活。
     *
     * <p>⚠ 会<b>先清掉这台机器的运行期记录</b>（等价于"刚放下/刚重启"）。
     */
    private void autobuild(CommandSender sender, String[] args) {
        Location loc = resolve(sender, args);
        if (loc == null) {
            return;
        }
        SlimefunItem item = BlockStorage.check(loc);
        if (!(item instanceof UtsuhoReactorCore)) {
            sender.sendMessage(PREFIX + "\u00a7c该方块不是灵乌路空反应堆（实际 "
                    + (item == null ? "\u00a7c非 Slimefun 方块" : item.getId()) + "）");
            return;
        }
        String want = args.length >= 4 ? args[3].toUpperCase() : "";
        com.example.touhou.core.BuildMode bm;
        if (want.startsWith("A") || want.startsWith("自")) {
            bm = ReactorManager.setBuildMode(loc, com.example.touhou.core.BuildMode.AUTO);
        } else if (want.startsWith("M") || want.startsWith("手")) {
            bm = ReactorManager.setBuildMode(loc, com.example.touhou.core.BuildMode.MANUAL);
        } else {
            bm = ReactorManager.getBuildMode(loc);
        }
        int n = args.length >= 5 ? parseIntOr(args[4], 20) : 20;

        // 先做一次现场检测：结构本来就不完整的话，"自动激活"永远不会发生
        ReactorStructure.Result r = ReactorManager.checkStructure(loc);

        // 回到"刚放下机器"的初始态：清运行期记录 + 写回未激活
        ReactorManager.forget(loc);
        TouhouData.setEnum(loc, TouhouData.KEY_STATE, com.example.touhou.core.ReactorState.INACTIVE);

        sender.sendMessage(PREFIX + "\u00a7e构建模式演变 @ " + xyz(loc));
        sender.sendMessage("\u00a77  构建模式 = " + bm.display()
                + "  结构完整 = " + (r.isComplete() ? "\u00a7a是" : "\u00a7c否"));
        sender.sendMessage("\u00a78  ★ 激活现在由【结构变动事件】驱动，不再由 tick 驱动："
                + "本命令用一次 /touhou edit 模拟变动");
        if (!r.isComplete()) {
            sender.sendMessage("\u00a7c  ⚠ 结构不完整，自动激活不会发生（缺 "
                    + r.missing().size() + " / 错 " + r.wrong().size() + "）");
            for (String m : r.missing()) {
                sender.sendMessage("\u00a78    缺 " + m);
            }
            for (String w : r.wrong()) {
                sender.sendMessage("\u00a78    错 " + w);
            }
        }
        // 模拟一次结构变动：这是现在唯一的常规检测触发途径
        String note = ReactorManager.onStructureEdited(loc, ReactorManager.Edit.PLACED, null);
        sender.sendMessage("\u00a77  模拟结构变动 \u2192 " + color(note));
        ReactorManager.flushPendingChecksForDiagnostics();
        for (String line : ReactorManager.simulateTicks(loc, n, null)) {
            sender.sendMessage("\u00a78  " + line);
        }
        boolean nowActivated = ReactorManager.isActivated(loc);
        log("[TOUHOU] autobuild @ " + xyz(loc) + " mode=" + bm + " n=" + n
                + " structure=" + r.isComplete()
                + " activatedAfter=" + nowActivated
                + " finalState=" + ReactorManager.cachedState(loc));

        // 判定：AUTO + 结构完整 必须自动激活；MANUAL 必须停在未激活
        if (bm == com.example.touhou.core.BuildMode.AUTO) {
            sender.sendMessage(r.isComplete() && nowActivated
                    ? "\u00a7a  \u2714 自动构建生效：结构完整后自动激活"
                    : "\u00a7c  \u2718 自动构建未生效（activated=" + nowActivated + "）");
        } else {
            sender.sendMessage(r.isComplete() && !nowActivated
                    ? "\u00a7a  \u2714 手动构建符合预期：停在未激活，等待点击信息格"
                    : "\u00a7c  \u2718 手动构建异常（activated=" + nowActivated + "）");
        }
    }

    // ------------------------------------------------------------------ reactor

    private void reactor(CommandSender sender, String[] args) {
        Location loc = resolve(sender, args);
        if (loc == null) {
            return;
        }
        String action = args.length >= 4 ? args[3].toLowerCase() : "info";
        SlimefunItem item = BlockStorage.check(loc);

        switch (action) {
            case "activate" -> {
                sender.sendMessage(PREFIX + color(ReactorManager.activate(loc).message()));
                log("[TOUHOU] activate @ " + xyz(loc) + " -> " + ReactorManager.cachedState(loc));
            }
            case "tags" -> {
                sender.sendMessage(PREFIX + "\u00a7e物品标签表（结构 legend 里写 #标签 即可引用）");
                for (String line : com.example.touhou.core.ItemTags.describe()) {
                    sender.sendMessage("\u00a77" + line);
                }
            }
            case "io" -> reactorIo(sender, loc, item, args);
            case "particles" -> {
                // 开关附加粒子特效（与 GUI 槽 P 是同一个开关，方便无头验证）
                String want = args.length >= 5 ? args[4].toLowerCase() : "toggle";
                boolean on = switch (want) {
                    case "on", "true", "开" -> {
                        ReactorManager.setParticles(loc, true);
                        yield true;
                    }
                    case "off", "false", "关" -> {
                        ReactorManager.setParticles(loc, false);
                        yield false;
                    }
                    default -> ReactorManager.toggleParticles(loc);
                };
                sender.sendMessage(PREFIX + "\u00a7e附加粒子特效: " + (on ? "\u00a7a开" : "\u00a7c关"));
                log("[TOUHOU] particles @ " + xyz(loc) + " -> " + (on ? "on" : "off"));
            }
            case "abort" -> {
                // 中止核心当前进程并立刻开启下一轮（与 IO 接口 GUI 的按钮同一条链路）
                boolean had = UtsuhoReactorCore.abortProcessAt(loc);
                sender.sendMessage(PREFIX + "\u00a7e中止进程 @ " + xyz(loc) + " \u2192 "
                        + (had ? "\u00a7a已中止并尝试开启下一轮" : "\u00a77本来就没有进程"));
                log("[TOUHOU] abort @ " + xyz(loc) + " had=" + had);
            }
            case "buildmode" -> {
                // 构建模式：manual（结构完整后要点信息格）/ auto（结构完整后自动激活）
                // args = [x, y, z, "buildmode", <模式>?]
                String want = args.length >= 5 ? args[4].toUpperCase() : "";
                com.example.touhou.core.BuildMode bm;
                if (want.startsWith("A") || want.startsWith("自")) {
                    bm = ReactorManager.setBuildMode(loc, com.example.touhou.core.BuildMode.AUTO);
                } else if (want.startsWith("M") || want.startsWith("手")) {
                    bm = ReactorManager.setBuildMode(loc, com.example.touhou.core.BuildMode.MANUAL);
                } else {
                    bm = ReactorManager.toggleBuildMode(loc);
                }
                sender.sendMessage(PREFIX + "\u00a7a构建模式已设为 " + bm.display());
                for (String line : bm.lore()) {
                    sender.sendMessage("\u00a78  " + color(line));
                }
                log("[TOUHOU] buildmode @ " + xyz(loc) + " -> " + bm);
            }
            case "scan", "throttle" -> {
                // 检测状态：结构到底查没查、查了几次，靠这个看，不用猜
                sender.sendMessage(PREFIX + "\u00a7e结构检测状态 @" + xyz(loc));
                sender.sendMessage("\u00a77  " + ReactorManager.describeScanState(loc));
                sender.sendMessage("\u00a77  归属登记: "
                        + com.example.touhou.core.StructureRegistry.describe(loc));
                sender.sendMessage("\u00a77  已落盘朝向: "
                        + (ReactorManager.knownDirection(loc) == null ? "\u00a7c(未记录)"
                                : ReactorManager.knownDirection(loc).label()));
                sender.sendMessage("\u00a77  构件格数: "
                        + ReactorManager.structure().partLocations(loc,
                                ReactorManager.knownDirection(loc)).size());
                sender.sendMessage("\u00a78  触发途径：核心/构件被【放置、破坏、爆炸】各触发一次"
                        + "（同一 tick 的多次变动合并为一轮；构件靠 uid 认核心，不扫世界）");
                sender.sendMessage("\u00a78  额外：每个核心在本次开服后第一次 tick 补一次检测（重启恢复用）");
                log("[TOUHOU] scan @ " + xyz(loc) + " -> " + ReactorManager.describeScanState(loc));
            }
            case "mode" -> {
                // 带参数 = 直接设成那个模式；不带 = 切换
                // args = [x, y, z, "mode", <模式>?] —— 模式在第 5 个参数（下标 4）
                String want = args.length >= 5 ? args[4].toUpperCase() : "";
                ReactorMode mode;
                if (want.startsWith("P") || want.startsWith("产")) {
                    mode = ReactorManager.setMode(loc, ReactorMode.PRODUCT);
                } else if (want.startsWith("G") || want.startsWith("发")) {
                    mode = ReactorManager.setMode(loc, ReactorMode.GENERATE);
                } else {
                    mode = ReactorManager.toggleMode(loc);
                }
                sender.sendMessage(PREFIX + "\u00a7a模式已切换为 " + mode.display());
                log("[TOUHOU] mode @ " + xyz(loc) + " -> " + mode);
            }
            case "test" -> runTest(sender, loc, item,
                    args.length >= 5 ? parseIntOr(args[4], 12) : 12,
                    args.length >= 6 ? parseIntOr(args[5], 0) : 0);
            case "raw" -> {
                var data = com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
                        .getBlock(loc);
                var menu = data == null ? null : data.getBlockMenu();
                if (menu == null) {
                    sender.sendMessage(PREFIX + "\u00a7c方块菜单未初始化");
                    return;
                }
                sender.sendMessage(PREFIX + "\u00a7e槽位原始内容 @ " + xyz(loc));
                for (int s = 0; s < menu.toInventory().getSize(); s++) {
                    ItemStack it = menu.getItemInSlot(s);
                    if (it != null && !it.getType().isAir()) {
                        sender.sendMessage("\u00a78  槽 " + s + " = " + it.getType()
                                + (it.hasItemMeta() && it.getItemMeta().hasDisplayName()
                                        ? " name=" + it.getItemMeta().getDisplayName() : ""));
                    }
                }
            }
            case "charge" -> {
                SlimefunItem it = BlockStorage.check(loc);
                if (!(it instanceof UtsuhoReactorCore reactor)) {
                    sender.sendMessage(PREFIX + "\u00a7c该方块不是灵乌路空反应堆");
                    return;
                }
                // args = [x, y, z, "charge", <数量>]
                int amount = args.length >= 5 ? parseIntOr(args[4], 0) : 0;
                reactor.setCharge(loc, Math.max(0, amount));
                sender.sendMessage(PREFIX + "\u00a7e储电已置为 " + ReactorManager.currentCharge(loc));
                log("[TOUHOU] charge @ " + xyz(loc) + " = " + ReactorManager.currentCharge(loc));
            }
            case "start" -> {
                SlimefunItem it = BlockStorage.check(loc);
                if (!(it instanceof UtsuhoReactorCore reactor)) {
                    sender.sendMessage(PREFIX + "\u00a7c该方块不是灵乌路空反应堆");
                    return;
                }
                var data0 = com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
                        .getBlock(loc);
                var menu0 = data0 == null ? null : data0.getBlockMenu();
                int before = countFuel(menu0);
                boolean consumed = reactor.simulateStart(loc);
                int after = countFuel(menu0);
                sender.sendMessage(PREFIX + "\u00a7e尝试开进程 @ " + xyz(loc));
                sender.sendMessage("\u00a77  结果: " + (consumed ? "\u00a7a已开进程" : "\u00a7c未开进程")
                        + " \u00a77燃料 " + before + " \u2192 " + after
                        + (after < before ? " \u00a7c(消耗了!)" : " \u00a7a(未消耗)"));
                log("[TOUHOU] start @ " + xyz(loc) + " consumed=" + consumed
                        + " fuelBefore=" + before + " fuelAfter=" + after);
            }
            case "gate" -> {
                var g = ReactorManager.startGate(loc);
                sender.sendMessage(PREFIX + "\u00a7e运行条件门 @ " + xyz(loc));
                sender.sendMessage("\u00a77  允许开进程: "
                        + (g.allowed() ? "\u00a7a是" : "\u00a7c否") + " \u00a77原因: " + g.reason());
                sender.sendMessage("\u00a78  （条件：结构完整 + 已激活 + 已接入电网 + 模式/电量）");
                // 电网诊断：把 7 格内所有 Slimefun 方块列出来，看调节器到底有没有被认出来
                sender.sendMessage("\u00a77  电网扫描（" + ReactorManager.ENERGY_REGULATOR_RANGE
                        + " 格内的 Slimefun 方块）:");
                int found = 0;
                for (int d = 1; d <= ReactorManager.ENERGY_REGULATOR_RANGE; d++) {
                    for (int dx = -d; dx <= d; dx++) {
                        for (int dy = -d; dy <= d; dy++) {
                            for (int dz = -d; dz <= d; dz++) {
                                if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != d) {
                                    continue;
                                }
                                Location around = new Location(loc.getWorld(),
                                        loc.getBlockX() + dx, loc.getBlockY() + dy, loc.getBlockZ() + dz);
                                String id = BlockStorage.checkID(around);
                                if (id != null) {
                                    SlimefunItem fi = SlimefunItem.getById(id);
                                    String kind;
                                    if (SlimefunItems.ENERGY_REGULATOR.getItemId().equals(id)) {
                                        kind = "ENERGY_REGULATOR(远程 " + ReactorManager.ENERGY_REGULATOR_RANGE + " 格)";
                                    } else if (fi instanceof io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent c) {
                                        kind = String.valueOf(c.getEnergyComponentType());
                                    } else {
                                        kind = "非电网元件";
                                    }
                                    sender.sendMessage("\u00a78    (" + (loc.getBlockX() + dx) + ","
                                            + (loc.getBlockY() + dy) + "," + (loc.getBlockZ() + dz)
                                            + ") id=" + id + " type=" + kind);
                                    found++;
                                }
                            }
                        }
                    }
                }
                if (found == 0) {
                    sender.sendMessage("\u00a78    （没有找到任何 Slimefun 方块）");
                }
                log("[TOUHOU] gate @ " + xyz(loc) + " allowed=" + g.allowed()
                        + " reason=" + g.reason() + " netBlocks=" + found);
            }
            case "inv" -> {
                var data = com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils
                        .getBlock(loc);
                var menu = data == null ? null : data.getBlockMenu();
                if (menu == null) {
                    sender.sendMessage(PREFIX + "\u00a7c方块菜单未初始化");
                    return;
                }
                sender.sendMessage(PREFIX + "\u00a7e背包内容 @ " + xyz(loc));
                for (int s = 0; s < menu.toInventory().getSize(); s++) {
                    ItemStack it = menu.getItemInSlot(s);
                    if (it != null && !it.getType().isAir()) {
                        sender.sendMessage("\u00a78  槽 " + s + " = " + ReactorManager.fuelName(it));
                    }
                }
            }
            default -> {
                sender.sendMessage(PREFIX + "\u00a7e反应堆 @" + xyz(loc));
                if (!(item instanceof UtsuhoReactorCore)) {
                    sender.sendMessage("\u00a7c  该方块不是灵乌路空反应堆（实际 "
                            + (item == null ? "\u00a7c非 Slimefun 方块" : item.getId()) + "）");
                }
                for (String line : ReactorManager.describe(loc)) {
                    sender.sendMessage("\u00a78  " + line);
                }
                // 虚分派自检：确认 getGeneratedOutput 的重写真的生效
                sender.sendMessage("\u00a77  getGeneratedOutput 分派:");
                for (String line : UtsuhoReactorCore.describeDispatches()) {
                    sender.sendMessage("\u00a78" + line);
                }
                ReactorStructure.Result r = ReactorManager.checkStructure(loc);
                sender.sendMessage("\u00a77  结构检测: " + r.summary());
                int n = 0;
                for (String m : r.missing()) {
                    if (n++ >= 8) {
                        sender.sendMessage("\u00a77    …");
                        break;
                    }
                    sender.sendMessage("\u00a78    缺 " + m);
                }
                n = 0;
                for (String w : r.wrong()) {
                    if (n++ >= 8) {
                        sender.sendMessage("\u00a77    …");
                        break;
                    }
                    sender.sendMessage("\u00a78    错 " + w);
                }
            }
        }
    }

    /**
     * {@code reactor <x y z> io ...} —— 物流接口诊断（输入接口与输出接口共用）。
     *
     * <pre>
     *   layout  打印 54 格布局自检 + 占位符来源
     *   abort   走一遍"中止核心进程并开启下一轮"
     *   reset   把搬运计数器清零，下一 tick 立刻搬一次
     *   fill    往本接口自有槽塞一桶原油（仅输入接口有意义）
     *   seed    塞 7 个红石当标记物
     *   count   数两边容器里的红石
     *   scans   打印找核心的扫描计数与速率
     *   guard   锁槽自检
     * </pre>
     */
    private void reactorIo(CommandSender sender, Location loc, SlimefunItem item, String[] args) {
        String sub = args.length >= 5 ? args[4].toLowerCase() : "";
        com.example.touhou.core.AbstractReactorPort port;
        if (item instanceof com.example.touhou.core.AbstractReactorPort p) {
            port = p;
        } else {
            sender.sendMessage(PREFIX + "\u00a7c这个方块不是反应堆物流接口（实际 "
                    + (item == null ? "\u00a7c非 Slimefun 方块" : item.getId()) + "）");
            return;
        }

        if (sub.equals("scans")) {
            // 「没找到核心时不要每 tick 重扫 125 格」这条优化的**证据**：
            // 隔几秒取两次读数，差值 / 秒 就是真实的扫描速率。
            long now = com.example.touhou.core.AbstractReactorPort.scanCount();
            long at = SCAN_SAMPLE_TIME.get();
            long prev = SCAN_SAMPLE.get();
            long elapsed = at == 0 ? 0 : System.currentTimeMillis() - at;
            sender.sendMessage(PREFIX + "\u00a7e找核心扫描计数 @ " + xyz(loc));
            sender.sendMessage("\u00a77  累计扫描次数: " + now);
            sender.sendMessage("\u00a77  已绑定核心的接口数: "
                    + com.example.touhou.core.AbstractReactorPort.boundCount());
            for (String bind : com.example.touhou.core.AbstractReactorPort.describeBindings()) {
                sender.sendMessage("\u00a78    " + bind);
            }
            sender.sendMessage("\u00a77  结构检测累计: "
                    + ReactorManager.structureCheckCount() + " 次"
                    + "（去抖批次 " + ReactorManager.debouncedBatchCount() + " 轮）");
            if (elapsed > 0) {
                long delta = now - prev;
                double perSec = delta * 1000.0 / elapsed;
                sender.sendMessage("\u00a77  与上次读数相隔 " + elapsed + " ms：扫描 "
                        + delta + " 次  ≈ " + String.format("%.1f", perSec) + " 次/秒");
                sender.sendMessage("\u00a78    期望：空闲时 ≈ 0（只有放置/破坏/爆炸/点 GUI 才会扫）");
            } else {
                sender.sendMessage("\u00a78  已记录本次读数，请隔几秒再执行一次以得到速率");
            }
            SCAN_SAMPLE.set(now);
            SCAN_SAMPLE_TIME.set(System.currentTimeMillis());
            log("[TOUHOU] io scans @ " + xyz(loc) + " total=" + now);
            return;
        }
        if (sub.equals("layout")) {
            sender.sendMessage(PREFIX + "\u00a7e物流接口 GUI 布局自检");
            for (String line : port.layoutSummary()) {
                sender.sendMessage("\u00a78" + line);
            }
            sender.sendMessage("\u00a7e六行纯网格（直接与需求原文对照）");
            for (String row : com.example.touhou.core.AbstractReactorPort.gridOf(port)) {
                sender.sendMessage("\u00a7b  " + row);
            }
            sender.sendMessage("\u00a7e占位符来源（spec：本体自带优先）");
            for (String line : com.example.touhou.core.AbstractReactorPort.textureReport()) {
                sender.sendMessage("\u00a78  " + line);
            }
            log("[TOUHOU] io layout @ " + xyz(loc));
            return;
        }
        if (sub.equals("guard")) {
            for (String line : com.example.touhou.core.AbstractReactorPort.guardReport(port)) {
                sender.sendMessage("\u00a78" + line);
            }
            log("[TOUHOU] io guard @ " + xyz(loc));
            return;
        }
        if (sub.equals("abort")) {
            // 注意：loc 是【接口】位置，中止要作用在它代理的核心上
            Location core = com.example.touhou.core.AbstractReactorPort.diagnosticCore(loc);
            if (core == null) {
                sender.sendMessage(PREFIX + "\u00a7c这个接口还没有绑定核心，无法中止");
                return;
            }
            boolean had = UtsuhoReactorCore.abortProcessAt(core);
            sender.sendMessage(PREFIX + "\u00a7e中止核心进程 @ " + xyz(core) + " \u2192 "
                    + (had ? "\u00a7a已中止并尝试开启下一轮" : "\u00a77本来就没有进程"));
            log("[TOUHOU] io abort @ port=" + xyz(loc) + " core=" + xyz(core) + " had=" + had);
            return;
        }
        if (sub.equals("reset")) {
            com.example.touhou.core.AbstractReactorPort.resetInterval(loc);
            sender.sendMessage(PREFIX + "\u00a7a搬运计数器已清零（下一 tick 立刻搬一次）");
            return;
        }
        if (sub.equals("fill")) {
            boolean ok = port.injectFuel(loc);
            sender.sendMessage(PREFIX + "\u00a77已向接口自有槽塞入原油桶: "
                    + (ok ? "\u00a7a成功" : "\u00a7c失败（该接口不是输入接口，或容器未就绪）"));
        }
        // seed port|core [slot]：塞 7 个红石当标记物（核心不认它，不会被烧掉）
        if (sub.equals("seed")) {
            String where = args.length >= 6 ? args[5] : "port";
            int slot = args.length >= 7 ? parseIntOr(args[6], -1) : -1;
            boolean ok = port.seed(loc, where, slot);
            sender.sendMessage(PREFIX + "\u00a77已向 " + where + " 容器塞入 7 个红石: "
                    + (ok ? "\u00a7a成功" : "\u00a7c失败（容器未就绪或未绑定核心）"));
        }
        // count：把两边容器里的红石数量打出来（验证搬运去向）
        if (sub.equals("count")) {
            sender.sendMessage(PREFIX + "\u00a7e红石标记计数 @ " + xyz(loc));
            sender.sendMessage("\u00a77  接口自有槽: "
                    + port.countRedstone(loc, "port", port.ownSlots()));
            sender.sendMessage("\u00a77  核心输入槽: "
                    + port.countRedstone(loc, "core", UtsuhoReactorCore.INPUT_SLOT_ALL));
            sender.sendMessage("\u00a77  核心输出槽: "
                    + port.countRedstone(loc, "core", UtsuhoReactorCore.OUTPUT_SLOT_ALL));
        }
        String[] r = port.probe(loc);
        boolean shared = com.example.touhou.core.AbstractReactorPort.sharesMenuWithCore(loc);
        sender.sendMessage(PREFIX + "\u00a7e物流接口诊断 @ " + xyz(loc)
                + "  (" + item.getId() + ")");
        sender.sendMessage("\u00a77  代理的核心: " + ("未找到".equals(r[0])
                ? "\u00a7c" + r[0] : "\u00a7a" + r[0]));
        sender.sendMessage("\u00a77  核心容器: " + r[1]);
        sender.sendMessage("\u00a77  接口自身容器: " + r[2]);
        sender.sendMessage("\u00a77  本轮搬运: " + r[3]);
        sender.sendMessage("\u00a77  接口与核心共用同一容器: "
                + (shared ? "\u00a7c是（异常，应为各自独立）" : "\u00a7a否（各自独立 \u2714）"));
        sender.sendMessage("\u00a77  搬运间隔: " + ReactorManager.ioIntervalSeconds()
                + " 秒（= " + ReactorManager.ioIntervalTicks() + " 个 Slimefun tick，按实际 tickRate 换算）");
        sender.sendMessage("\u00a77  找核心扫描: 累计 "
                + com.example.touhou.core.AbstractReactorPort.scanCount()
                + " 次，当前已绑定核心的接口 "
                + com.example.touhou.core.AbstractReactorPort.boundCount()
                + " 个（事件驱动：绑定后不再扫世界）");
        log("[TOUHOU] io @ " + xyz(loc) + " core=" + r[0] + " coreMenu=" + r[1]
                + " ownMenu=" + r[2] + " transfer=" + r[3] + " shared=" + shared
                + " scans=" + com.example.touhou.core.AbstractReactorPort.scanCount());
    }

    /**
     * 模拟发电循环。
     *
     * <p>本体 {@code EnergyNet} 每 tick 对每个发电机做的事是：
     * {@code energy = provider.getGeneratedOutput(loc, data); supply += energy;}
     * 最后 {@code setCharge(charge + remaining)}。这里照抄这条链，只是把"电往哪去"
     * 简化成"存回本机"。
     */
    private void runTest(CommandSender sender, Location loc, SlimefunItem item, int ticks, int startCharge) {
        if (!(item instanceof UtsuhoReactorCore reactor)) {
            sender.sendMessage(PREFIX + "\u00a7c该方块不是灵乌路空反应堆");
            return;
        }
        var data = com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils.getBlock(loc);
        if (data == null) {
            sender.sendMessage(PREFIX + "\u00a7c方块数据未加载");
            return;
        }
        var menu = data.getBlockMenu();
        if (menu == null) {
            sender.sendMessage(PREFIX + "\u00a7c方块菜单未初始化（机器可能没被正确放置过）");
            return;
        }

        // 1. 塞一个原油桶到第一个输入槽
        int inputSlot = reactor.getInputSlots()[0];
        menu.replaceExistingItem(inputSlot, SlimefunItems.OIL_BUCKET.clone());

        // 2. 还没有进程就按"本体第一次 tick 的做法"开一个
        if (reactor.getMachineProcessor().getOperation(loc.getBlock()) == null) {
            ItemStack fuel = menu.getItemInSlot(inputSlot);
            if (fuel == null || !ReactorManager.isFuel(fuel)) {
                sender.sendMessage(PREFIX + "\u00a7c输入槽里没有可识别为燃料的物品（期望原版原油桶）");
                return;
            }
            // ★ 先 clone 再 consume：consumeItem 在"数量剩 1"时会就地清空那个 ItemStack
            ItemStack snapshot = fuel.clone();
            menu.consumeItem(inputSlot, 1);
            reactor.getMachineProcessor().startOperation(loc.getBlock(),
                    new io.github.thebusybiscuit.slimefun4.implementation.operations.FuelOperation(
                            snapshot, null, ReactorManager.config().processTicks));
            sender.sendMessage(PREFIX + "\u00a77已开新进程：燃料=" + ReactorManager.fuelName(snapshot)
                    + " 时长=" + ReactorManager.config().processTicks + " tick");
        }

        // 3. 设置初始储电（2025.1 只有 setCharge(Location,int)；容量就是 int 上限，强转无损）
        //    传第 6 个参数可以模拟"已经满电/过阈值"的场景，用来验证发电模式的暂停门控。
        reactor.setCharge(loc, Math.max(0, startCharge));

        sender.sendMessage(PREFIX + "\u00a7e模拟 " + ticks + " 次发电 tick  模式="
                + ReactorManager.getMode(loc).display()
                + "  初始储电=" + startCharge
                + "  阈值=" + ReactorManager.config().modeThreshold);

        long total = 0;
        for (int i = 1; i <= ticks; i++) {
            long before = ReactorManager.currentCharge(loc);
            // ① 本体电力网络这一 tick 会做的事（离网时根本不会被调用，所以这里单独看结局）
            int energy = reactor.getGeneratedOutput(loc, data);
            // ② 机器自己的 BlockTicker 这一 tick 会做的事
            //    （产物模式的进度走这条，离网也跑；发电模式这里什么都不做）
            reactor.tickProductProcess(loc);
            total += energy;
            // 本体的 setCharge 是 int：超过 2^31-1 的部分会被截断，这里打印"实际存进去的值"
            long after = Math.min(Integer.MAX_VALUE, before + energy);
            if (after > 0) {
                reactor.setCharge(loc, (int) after);
            }
            var op = reactor.getMachineProcessor().getOperation(loc.getBlock());
            int progress = op == null ? -1 : op.getProgress();
            sender.sendMessage("\u00a78  tick " + i + ": +" + energy + " J  储电=" + after
                    + "  进程=" + (progress < 0 ? "无" : progress + "/" + op.getTotalTicks()));
        }

        // 4. 清点输出
        int buckets = 0;
        int singularities = 0;
        for (int slot : reactor.getOutputSlots()) {
            ItemStack out = menu.getItemInSlot(slot);
            if (out == null || out.getType().isAir()) {
                continue;
            }
            if (out.getType() == Material.BUCKET) {
                buckets += out.getAmount();
            } else if (SlimefunItem.getByItem(out) instanceof SlimefunItem sf) {
                if ("TOUHOU_MATERIAL_LOGIC_SINGULARITY".equals(sf.getId())) {
                    singularities += out.getAmount();
                }
            }
        }

        sender.sendMessage(PREFIX + "\u00a7a合计发电 \u00a7e" + total + " J"
                + "  \u00a77输出：桶 \u00a7f" + buckets + " \u00a77奇点 \u00a7f" + singularities
                + " \u00a77余料 \u00a7f" + (menu.getItemInSlot(inputSlot) == null ? 0
                        : menu.getItemInSlot(inputSlot).getAmount()));
        log("[TOUHOU] test @ " + xyz(loc) + " mode=" + ReactorManager.getMode(loc)
                + " ticks=" + ticks + " totalJ=" + total + " buckets=" + buckets
                + " singularities=" + singularities);
    }

    // ------------------------------------------------------------------ structure

    /** 数一数输入槽里一共有多少个燃料。 */
    private static int countFuel(me.mrCookieSlime.Slimefun.api.inventory.BlockMenu menu) {
        if (menu == null) {
            return -1;
        }
        int n = 0;
        for (int slot : UtsuhoReactorCore.INPUT_SLOT_ALL) {
            ItemStack it = menu.getItemInSlot(slot);
            if (ReactorManager.isFuel(it)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    private void structure(CommandSender sender, String[] args) {
        Location loc = resolve(sender, args);
        if (loc == null) {
            return;
        }
        // 末尾带 alldirs 时：无视对称性优化，把四个方向各检测一遍（验证四向适配）
        boolean allDirs = Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase("alldirs"));

        ReactorStructure.Result r = ReactorManager.checkStructure(loc);
        sender.sendMessage(PREFIX + "\u00a7e结构检测 @ " + xyz(loc) + "  实现="
                + ReactorManager.structure().name());
        sender.sendMessage("\u00a77  " + r.summary());
        ReactorStructure.Direction stored = ReactorManager.storedDirection(loc);
        sender.sendMessage("\u00a77  上次记录的朝向: "
                + (stored == null ? "\u00a78(无)" : stored.label())
                + "  " + ReactorManager.describeSymmetric());
        for (String m : r.missing()) {
            sender.sendMessage("\u00a7c    缺 " + m);
        }
        for (String w : r.wrong()) {
            sender.sendMessage("\u00a76    错 " + w);
        }
        if (r.isComplete()) {
            sender.sendMessage("\u00a7a  \u2714 结构完整");
        }

        if (allDirs) {
            sender.sendMessage("\u00a7e  四向强制探测（无视对称性优化）:");
            for (String line : ReactorManager.probeAllDirections(loc)) {
                sender.sendMessage("\u00a78    " + line);
            }
        }
    }

    // ------------------------------------------------------------------ saizen

    /**
     * 赛钱箱（多方块核心）的无头诊断与验证。
     *
     * <pre>
     * /touhou saizen &lt;x&gt; &lt;y&gt; &lt;z&gt; info        状态 / 结构 / 6 根木桩定位 / 预留槽镜像
     * /touhou saizen &lt;x&gt; &lt;y&gt; &lt;z&gt; check       只做一次现场结构检测（不改状态）
     * /touhou saizen &lt;x&gt; &lt;y&gt; &lt;z&gt; activate    等价于点 GUI 信息格（唯一的手动激活入口）
     * /touhou saizen &lt;x&gt; &lt;y&gt; &lt;z&gt; deactivate  停机并清掉木桩编号
     * /touhou saizen &lt;x&gt; &lt;y&gt; &lt;z&gt; slots       序号 ↔ 预留槽 ↔ 指示槽 映射 + GUI 逐格自检
     * /touhou saizen &lt;x&gt; &lt;y&gt; &lt;z&gt; posts       6 根木桩的实际坐标 + 各自编号
     * /touhou saizen &lt;x&gt; &lt;y&gt; &lt;z&gt; recipe      打印全部已注册配方
     * /touhou saizen &lt;x&gt; &lt;y&gt; &lt;z&gt; seed        按第一条配方给 6 根木桩塞材料（无头投料）
     * /touhou saizen &lt;x&gt; &lt;y&gt; &lt;z&gt; tick [n]    手动推 n 轮运作（默认 1）
     * /touhou saizen &lt;x&gt; &lt;y&gt; &lt;z&gt; charge &lt;n&gt;  直接写本机 POWER（验证"电力不足不运作"）
     * /touhou saizen &lt;x&gt; &lt;y&gt; &lt;z&gt; guard       预留槽/占位槽的锁槽自检
     * /touhou saizen &lt;x&gt; &lt;y&gt; &lt;z&gt; alldirs     无视朝向缓存，四个朝向各检测一遍
     * </pre>
     *
     * <p>为什么要有这一整套：这台机器的激活入口在 GUI 里，而 GUI 点击无法从控制台触发。
     * {@code activate} 调的是与点击<b>完全相同</b>的那段逻辑
     * （{@link Saizenbako#simulateActivateClick}），所以无头验证过的行为就是玩家点出来的行为。
     */
    private void saizen(CommandSender sender, String[] args) {
        Location loc = resolve(sender, args);
        if (loc == null) {
            return;
        }
        String sub = args.length >= 4 ? args[3].toLowerCase() : "info";
        SlimefunItem item = BlockStorage.check(loc);
        if (!(item instanceof Saizenbako saizen)) {
            sender.sendMessage(PREFIX + "\u00a7c该方块不是赛钱箱（实际 "
                    + (item == null ? "\u00a7c非 Slimefun 方块" : item.getId()) + "）");
            return;
        }

        switch (sub) {
            case "check", "alldirs" -> {
                var st = com.example.touhou.core.SaizenbakoStructure.get();
                ReactorStructure.Result r = st.check(loc);
                sender.sendMessage(PREFIX + "\u00a7e赛钱箱结构检测 @ " + xyz(loc) + "  实现=" + st.name());
                for (String line : st.describe()) {
                    sender.sendMessage("\u00a78  " + line);
                }
                sender.sendMessage("\u00a77  " + r.summary());
                for (String m : r.missing()) {
                    sender.sendMessage("\u00a7c    缺 " + m);
                }
                for (String w : r.wrong()) {
                    sender.sendMessage("\u00a76    错 " + w);
                }
                sender.sendMessage(r.isComplete() ? "\u00a7a  \u2714 结构完整" : "\u00a7c  \u2718 结构不完整");
                if (sub.equals("alldirs")) {
                    sender.sendMessage("\u00a7e  四向强制探测（本次结构不对称，常规检测本来就会试 4 个朝向）:");
                    for (String line : st.layered().probeAllDirections(loc)) {
                        sender.sendMessage("\u00a78    " + line);
                    }
                }
                log("[TOUHOU] saizen " + sub + " @ " + xyz(loc) + " -> " + r.summary());
            }
            case "activate" -> {
                String text = saizen.simulateActivateClick(loc, sender::sendMessage);
                sender.sendMessage(PREFIX + "\u00a77激活结果: " + color(text));
                for (String line : com.example.touhou.core.SaizenbakoManager.describe(loc)) {
                    sender.sendMessage("\u00a78  " + line);
                }
                log("[TOUHOU] saizen activate @ " + xyz(loc) + " -> "
                        + com.example.touhou.core.Notify.plain(text));
            }
            case "deactivate" -> {
                String reason = com.example.touhou.core.SaizenbakoManager.deactivate(loc, "控制台手动停机");
                sender.sendMessage(PREFIX + "\u00a7e已停机: " + color(reason));
                log("[TOUHOU] saizen deactivate @ " + xyz(loc));
            }
            case "slots" -> {
                sender.sendMessage(PREFIX + "\u00a7e赛钱箱 GUI 布局自检（与 spec 逐格比对）");
                for (String line : saizen.layoutSummary()) {
                    sender.sendMessage("\u00a78" + line);
                }
                sender.sendMessage("\u00a7e五行纯网格（X占位 R预留 i指示 I信息 IO输入输出 S核心位置 H投影开关）");
                for (String row : Saizenbako.gridOf()) {
                    sender.sendMessage("\u00a7b  " + row);
                }
                log("[TOUHOU] saizen slots @ " + xyz(loc));
            }
            case "posts" -> {
                sender.sendMessage(PREFIX + "\u00a7e6 根木桩定位与编号（先 +X、后 +Z）");
                var st = com.example.touhou.core.SaizenbakoStructure.get();
                ReactorStructure.Direction dir = ReactorManager.storedDirection(loc);
                Location[] posts = st.postLocations(loc, dir);
                for (int i = 0; i < posts.length; i++) {
                    String id = BlockStorage.checkID(posts[i]);
                    int idx = com.example.touhou.core.StructureState.postIndexOf(posts[i]);
                    sender.sendMessage("\u00a77  #" + i + " @ " + xyz(posts[i])
                            + "  方块=" + (id == null ? "\u00a7c(无)" : id)
                            + "  编号=" + (idx < 0 ? "\u00a7c未编号" : "\u00a7a#" + idx)
                            + "  预留槽=" + Saizenbako.reservedSlotOf(i));
                }
                log("[TOUHOU] saizen posts @ " + xyz(loc));
            }
            case "recipe", "recipes" -> {
                sender.sendMessage(PREFIX + "\u00a7e赛钱箱配方表");
                for (String line : com.example.touhou.core.SaizenbakoRecipes.describe()) {
                    sender.sendMessage("\u00a78  " + line);
                }
                sender.sendMessage("\u00a7e配置（saizenbako 段）");
                for (String line : AddonConfig.get().describeSaizenbako()) {
                    sender.sendMessage("\u00a78  " + line);
                }
            }
            case "seed", "fill" -> {
                sender.sendMessage(PREFIX + "\u00a7e按第一条配方给 6 根木桩投料");
                for (String line : com.example.touhou.core.SaizenbakoManager.seedFirstRecipe(loc)) {
                    sender.sendMessage("\u00a78  " + line);
                }
                log("[TOUHOU] saizen seed @ " + xyz(loc));
            }
            case "tick" -> {
                int n = args.length >= 5 ? parseIntOr(args[4], 1) : 1;
                sender.sendMessage(PREFIX + "\u00a7e手动推 " + n + " 轮机器运作");
                for (String line : com.example.touhou.core.SaizenbakoManager.simulate(loc, n)) {
                    sender.sendMessage("\u00a78  " + line);
                }
                sender.sendMessage("\u00a7e预留槽镜像现状");
                for (String line : com.example.touhou.core.SaizenbakoManager.reservedContents(loc)) {
                    sender.sendMessage("\u00a78  " + line);
                }
                log("[TOUHOU] saizen tick " + n + " @ " + xyz(loc));
            }
            case "charge" -> {
                long n = args.length >= 5 ? parseIntOr(args[4], 0) : 0;
                sender.sendMessage(PREFIX + "\u00a7e"
                        + com.example.touhou.core.SaizenbakoManager.setCharge(loc, n));
                log("[TOUHOU] saizen charge " + n + " @ " + xyz(loc));
            }
            case "guard" -> reportOne(sender, loc, item);
            default -> {
                sender.sendMessage(PREFIX + "\u00a7e赛钱箱状态 @ " + xyz(loc));
                for (String line : com.example.touhou.core.SaizenbakoManager.describe(loc)) {
                    sender.sendMessage("\u00a78  " + line);
                }
                sender.sendMessage("\u00a7e预留槽镜像");
                for (String line : com.example.touhou.core.SaizenbakoManager.reservedContents(loc)) {
                    sender.sendMessage("\u00a78  " + line);
                }
                for (String line : com.example.touhou.core.StructureState.postBindingLines(
                        com.example.touhou.core.SaizenbakoStructure.get(), loc,
                        ReactorManager.storedDirection(loc))) {
                    sender.sendMessage("\u00a78  " + line);
                }
            }
        }
    }

    // ------------------------------------------------------------------ place

    /**
     * 无玩家地放置一个粘液方块。
     *
     * <p>为什么需要它：{@code /setblock} 只改世界方块，<b>不会</b>写 Slimefun 的方块数据，
     * 于是 {@code checkID} 查不到、机器不 tick、GUI 也建不起来。玩家正常放置时是
     * {@code BlockListener.onBlockPlace} 替我们调 {@code createBlock}，这里把那步补上。
     */
    private void place(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(PREFIX + "\u00a7c用法: /touhou place <x> <y> <z> <sfId> [world] [--force]");
            return;
        }
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(args[0]);
            y = Integer.parseInt(args[1]);
            z = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + "\u00a7c坐标必须是整数");
            return;
        }
        // --force：该位置已有粘液方块数据时先清掉再放（默认会拒绝覆盖，
        // 因为 createBlock 对已存在的方块会抛异常）
        boolean force = Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase("--force"));
        String sfId = args[3].toUpperCase();
        World world = null;
        for (int i = 4; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                continue;
            }
            world = Bukkit.getWorld(args[i]);
            break;
        }
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            sender.sendMessage(PREFIX + "\u00a7c找不到世界");
            return;
        }
        SlimefunItem item = SlimefunItem.getById(sfId);
        if (item == null) {
            sender.sendMessage(PREFIX + "\u00a7c没有注册过的粘液物品 id: " + sfId);
            return;
        }

        Location loc = new Location(world, x, y, z);
        try {
            if (force) {
                // 清掉旧的方块数据（方块本身随后会被 setType 换掉）
                Slimefun.getDatabaseManager().getBlockDataController().removeBlock(loc);
            }
            loc.getBlock().setType(item.getItem().getType());
            var data = Slimefun.getDatabaseManager().getBlockDataController().createBlock(loc, sfId);
            boolean hasMenu = data != null && data.getBlockMenu() != null;
            sender.sendMessage(PREFIX + "\u00a7a已放置 \u00a7f" + sfId + " \u00a77@ " + world.getName()
                    + " " + x + "," + y + "," + z + "  方块数据=" + (data != null) + "  菜单=" + hasMenu);
        } catch (RuntimeException e) {
            // 该位置已有方块数据时会抛（重复放置），报清楚原因并提示 --force
            sender.sendMessage(PREFIX + "\u00a7c放置失败: " + e
                    + "\u00a77（该位置已有粘液方块数据，可用 --force 覆盖）");
        }
    }

    // ------------------------------------------------------------------ 工具

    /** 解析 &lt;x&gt; &lt;y&gt; &lt;z&gt;（arg0..2），校验该坐标确实有粘液方块数据。 */
    private Location resolve(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + "\u00a7c需要坐标: <x> <y> <z>");
            return null;
        }
        int x;
        int y;
        int z;
        try {
            x = Integer.parseInt(args[0]);
            y = Integer.parseInt(args[1]);
            z = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + "\u00a7c坐标必须是整数");
            return null;
        }
        for (World w : Bukkit.getWorlds()) {
            Location loc = new Location(w, x, y, z);
            if (TouhouData.isReady(loc)) {
                return loc;
            }
        }
        sender.sendMessage(PREFIX + "\u00a7c该坐标上没有 Slimefun 方块数据");
        return null;
    }

    private static String xyz(Location l) {
        return l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private static String color(String raw) {
        return ChatColor.translateAlternateColorCodes('&', raw);
    }

    private static int parseIntOr(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static void log(String line) {
        // ★ 命令回显不受 logging.console-info 开关影响：
        //   命令是主动敲的、一次一条，不会刷屏，而且是排查时最需要的线索。
        Log.command(line);
    }

    // ------------------------------------------------------------------ 补全

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(List.of("reactor", "autobuild", "clickinfo", "structure", "saizen", "place",
                    "remove", "edit", "gui", "layout", "groups", "tags", "messages", "reload",
                    "power", "proj"), args[0]);
        }
        if ((args[0].equalsIgnoreCase("proj") || args[0].equalsIgnoreCase("projection"))
                && args.length == 2) {
            return filter(List.of("list"), args[1]);
        }
        if ((args[0].equalsIgnoreCase("proj") || args[0].equalsIgnoreCase("projection"))
                && args.length == 4) {
            return filter(List.of("on", "off", "toggle", "info", "cells", "count", "clean"), args[3]);
        }
        if (args[0].equalsIgnoreCase("saizen") && args.length == 4) {
            return filter(List.of("info", "check", "activate", "deactivate", "slots", "posts",
                    "recipe", "seed", "tick", "charge", "guard", "alldirs"), args[3]);
        }
        if ((args[0].equalsIgnoreCase("edit") || args[0].equalsIgnoreCase("clickpart"))
                && args.length == 5) {
            return filter(List.of("placed", "broken"), args[4]);
        }
        if (args[0].equalsIgnoreCase("reactor") && args.length == 4) {
            return filter(List.of("info", "test", "activate", "mode", "buildmode", "scan",
                    "abort", "particles", "io", "gate", "start", "charge", "inv", "raw", "tags"), args[3]);
        }
        // 模式参数补全：buildmode 的第 5 个参数是 manual/auto
        if (args[0].equalsIgnoreCase("reactor") && args.length == 5
                && args[3].equalsIgnoreCase("buildmode")) {
            return filter(List.of("manual", "auto"), args[4]);
        }
        // io 的子动作
        if (args[0].equalsIgnoreCase("reactor") && args.length == 5
                && args[3].equalsIgnoreCase("io")) {
            return filter(List.of("layout", "guard", "fill", "abort", "reset", "seed", "count",
                    "scans"), args[4]);
        }
        if (args[0].equalsIgnoreCase("autobuild") && args.length == 4) {
            return filter(List.of("manual", "auto"), args[3]);
        }
        if (args[0].equalsIgnoreCase("power") && args.length == 2) {
            return filter(List.of("rebuild"), args[1]);
        }
        return new ArrayList<>();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String p = prefix == null ? "" : prefix.toLowerCase();
        return options.stream().filter(o -> o.toLowerCase().startsWith(p)).collect(Collectors.toList());
    }

    /** 供文档引用：本插件的命令表。 */
    public static List<String> commands() {
        return List.of("reactor", "autobuild", "clickinfo", "structure", "saizen", "place", "remove",
                "edit", "gui", "layout", "groups", "tags", "messages", "reload", "power", "proj");
    }
}
