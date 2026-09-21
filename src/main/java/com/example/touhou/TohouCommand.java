package com.example.touhou;

import com.example.touhou.core.AddGroups;
import com.example.touhou.core.AddItems;
import com.example.touhou.core.AddSlimefunItems;
import com.example.touhou.core.AddonConfig;
import com.example.touhou.core.Log;
import com.example.touhou.core.MultiBlockProjection;
import com.example.touhou.core.ReactorManager;
import com.example.touhou.core.ReactorMode;
import com.example.touhou.core.ReactorStructure;
import com.example.touhou.core.Saizenbako;
import com.example.touhou.core.TouhouData;
import com.example.touhou.core.TouhouRecipeTypes;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code /touhou} 命令 —— 让所有机器都能在<b>控制台</b>无头验证，不必真人进游戏。
 *
 * <pre>
 * /touhou reactor &lt;x&gt; &lt;y&gt; &lt;z&gt; info        当前状态 / 结构 / 模式 / 储电 / 燃料
 * /touhou reactor &lt;x&gt; &lt;y&gt; &lt;z&gt; activate    模拟"点击 GUI 信息格激活"
 * /touhou reactor &lt;x&gt; &lt;y&gt; &lt;z&gt; mode        切换发电模式 / 产物模式
 * /touhou reactor &lt;x&gt; &lt;y&gt; &lt;z&gt; test [n]    塞 1 个原油桶并模拟 n 次发电 tick（默认 12）
 * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;动作&gt;          多方块投影：on / off / toggle / rotate / info / cells / mapping / count / clean
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
            case "dreamcatcher", "dc" -> dreamcatcher(sender, Arrays.copyOfRange(args, 1, args.length));
            case "seal", "gohei", "fantasyseal" -> seal(sender, Arrays.copyOfRange(args, 1, args.length));
            case "lily", "murderouslily" -> lily(sender, Arrays.copyOfRange(args, 1, args.length));
            case "echo", "shuttle", "dimensionshuttle" ->
                    echo(sender, Arrays.copyOfRange(args, 1, args.length));
            case "autobuild" -> autobuild(sender, Arrays.copyOfRange(args, 1, args.length));
            case "reactor" -> reactor(sender, Arrays.copyOfRange(args, 1, args.length));
            case "clickinfo" -> clickInfo(sender, Arrays.copyOfRange(args, 1, args.length));
            case "proj", "projection" -> proj(sender, Arrays.copyOfRange(args, 1, args.length));
            case "structure" -> structure(sender, Arrays.copyOfRange(args, 1, args.length));
            case "saizen" -> saizen(sender, Arrays.copyOfRange(args, 1, args.length));
            case "guide" -> guide(sender, Arrays.copyOfRange(args, 1, args.length));
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
        s.sendMessage("\u00a77/touhou proj <x> <y> <z> [on|off|toggle|rotate|info|cells|mapping|count|clean [r]]   多方块投影");
        s.sendMessage("\u00a77/touhou proj list                  列出当前持有的投影组与实体数");
        s.sendMessage("\u00a77/touhou structure <x> <y> <z> [alldirs]");
        s.sendMessage("\u00a77/touhou saizen <x> <y> <z> [info|check|activate|deactivate|slots|posts|recipe|seed|tick [n]|charge <n>|guard|alldirs]");
        s.sendMessage("\u00a77/touhou dreamcatcher <x> <y> <z>   幻梦捕捉器：四周床数 / 自身 POWER / 所在网络总量 / 产出速率");
        s.sendMessage("\u00a77/touhou seal <玩家名>                读玩家主手/副手「梦想封印 集」的 POWER、上限、可用次数");
        s.sendMessage("\u00a77/touhou seal probe <x> <y> <z>       在指定坐标做一次真实的取电实测（内存测试物品）");
        s.sendMessage("\u00a77/touhou seal selfcheck              梦想封印 集的参数自检（控制台可用）");
        s.sendMessage("\u00a77/touhou lily selfcheck               杀意的百合：参数 / 方向规则 / 追踪表自检");
        s.sendMessage("\u00a77/touhou lily dir <面>                只验证激光方向裁决（不碰世界）");
        s.sendMessage("\u00a77/touhou lily beam <x> <y> <z> <面>   按某个面探激光长度（是否被物块截断）");
        s.sendMessage("\u00a77/touhou lily fire <x> <y> <z> <dx> <dy> <dz> [玩家] [--force]   完整发射仿真");
        s.sendMessage("\u00a77/touhou lily laser <x> <y> <z> <面> [玩家]   实弹激光：弹射物范围伤害 + 排除发射者");
        s.sendMessage("\u00a77/touhou lily impact <x> <y> <z> [玩家]        只做命中点爆发（激光+喷泉+落点范围伤害+追踪箭）");
        s.sendMessage("\u00a77/touhou lily tracers <x> <y> <z> [玩家]       衍生箭实弹测试（命中范围伤害 + 2 tick 消失）");
        s.sendMessage("\u00a77/touhou lily cleanup               把两张追踪表收干净并打印条目数");
        s.sendMessage("\u00a77/touhou echo selfcheck | rule | container <x> <y> <z> | convert [玩家] | probe <玩家> [世界] | shuttle <玩家> <from> <to> [--force] | cooldown [clear]   「维度穿梭」无头验证");
        s.sendMessage("\u00a77/touhou guide [reactor|saizen|echo]   粘液书自定义配方页的内容自检（展示列表 + 可合成性核查）");
        s.sendMessage("\u00a77/touhou place <x> <y> <z> <sfId> [world] [--force]");
        s.sendMessage("\u00a77/touhou remove <x> <y> <z>            删除方块 + Slimefun 方块数据（setblock 清不掉）");
        s.sendMessage("\u00a77/touhou edit <x> <y> <z> [placed|broken]  模拟结构变动（现在唯一的常规检测触发途径）");
        s.sendMessage("\u00a77/touhou gui [x y z]                   GUI 锁槽自检（防占位符被拿走）");
        s.sendMessage("\u00a77/touhou layout | groups | messages | reload");
        s.sendMessage("\u00a77/touhou power [x y z] [world] | power rebuild   POWER 网络诊断（控制台可用坐标）");
    }

    // ------------------------------------------------------------------ guide

    /**
     * <b>粘液书自定义配方页的内容自检</b>（无头）。
     *
     * <pre>
     *   /touhou guide            两个核心都打印
     *   /touhou guide reactor    只看反应堆核心
     *   /touhou guide saizen     只看赛钱箱（祭坛）核心
     * </pre>
     *
     * <p>★ 为什么要有这条命令：配方页的最终形态是"玩家翻开指南书看到的一屏图标"，
     * 而书是 GUI —— 控制台点不了。所以这里把<b>同一份</b>
     * {@code RecipeDisplayItem#getDisplayRecipes()} 的结果原样打印出来
     * （每个 ItemStack 的显示名 + lore 首行），于是"配方页里到底有什么、
     * 是不是从注册表长出来的"就有了可 grep 的证据，而不是靠肉眼翻书。
     *
     * <p>顺带打印两件"这次改动必须证明的事"：
     * <ol>
     *   <li>核心物品 lore 里的「建造所需材料」清单（需求 B 的可验证出口）；</li>
     *   <li><b>可合成性核查</b>：配方数组有几个非空格、配方类型背后是不是多方块机器、
     *       Bukkit 配方表里有几条能产出这个核心（正常必须是 0）。</li>
     * </ol>
     *
     * <p>输出走 {@link Log#command}（不受 {@code logging.console-info} 影响），
     * 于是"跑一次服务端 + 从 stdin 敲一条命令"就能拿到全部证据。
     * 发送者是玩家时另外回显到聊天栏 —— 控制台来源不再重复推一遍（避免日志里两行一样的）。
     */
    private void guide(CommandSender sender, String[] args) {
        String which = args.length >= 1 ? args[0].toLowerCase() : "all";
        boolean reactor = which.equals("all") || which.equals("reactor");
        boolean saizen = which.equals("all") || which.equals("saizen") || which.equals("saizenbako");
        boolean echo = which.equals("all") || which.equals("echo")
                || which.equals("shuttle") || which.equals("dimensionshuttle");
        if (!reactor && !saizen && !echo) {
            sender.sendMessage(PREFIX + "\u00a7c用法: /touhou guide [reactor|saizen|echo]");
            return;
        }

        guideLine(sender, PREFIX + "\u00a7e粘液书自定义配方页内容自检");
        guideLine(sender, "\u00a78  " + TouhouRecipeTypes.describe());
        guideLine(sender, "\u00a78  SaizenbakoRecipes 已注册 "
                + com.example.touhou.core.SaizenbakoRecipes.count() + " 条配方");

        if (reactor) {
            dumpCorePage(sender, "反应堆核心", AddSlimefunItems.UTSUHO_REACTOR_CORE,
                    AddonConfig.get().structureLayers, AddonConfig.get().structureLegend);
        }
        if (saizen) {
            dumpCorePage(sender, "赛钱箱（祭坛）核心", AddSlimefunItems.SAIZENBAKO,
                    AddonConfig.get().saizenLayers, AddonConfig.get().saizenLegend);
        }
        if (echo) {
            // ★ 另一个世界的回响没有"层图材料清单"（它不是多方块），
            //   所以传空层图 —— dumpCorePage 里那段"从层图现算"会打印"共 0 项"。
            //   这一页真正要证明的是：① 指南页画出了 水晶→回响 这条获取方式；
            //   ② 它有 9 格全空的配方数组 + 门面配方类型 ⇒ 不可合成。
            dumpCorePage(sender, "另一个世界的回响（维度穿梭）",
                    AddSlimefunItems.ECHO_OF_ANOTHER_WORLD,
                    java.util.List.of(), java.util.Map.of());
        }
    }

    /**
     * 打印一个核心的配方页 + 物品 lore + 可合成性核查。
     *
     * @param layers/legend 这套结构的层图数据（用来现算「建造所需材料」清单 ——
     *                      与物品 lore 走的是<b>同一个</b> {@link StructureMaterials} 入口，
     *                      所以命令输出能证明物品描述里那几行是怎么来的）
     */
    private void dumpCorePage(CommandSender sender, String label,
                              io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem item,
                              java.util.List<java.util.List<String>> layers,
                              java.util.Map<Character, String> legend) {
        guideLine(sender, "\u00a7e== " + label + " ==");
        if (item == null) {
            guideLine(sender, "\u00a7c  未注册（AddSlimefunItems 没跑完？）");
            return;
        }
        guideLine(sender, "\u00a78  id = " + item.getId()
                + "   配方类型 = " + (item.getRecipeType() == null
                        ? "(null)" : item.getRecipeType().getKey().toString()));

        // ---- ① 展示列表（配方页底部网格）----
        if (item instanceof io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem page) {
            java.util.List<ItemStack> display = page.getDisplayRecipes();
            for (String line : com.example.touhou.core.RecipePages.dump(display)) {
                guideLine(sender, "\u00a77" + line);
            }
        } else {
            guideLine(sender, "\u00a7c  它没有实现 RecipeDisplayItem —— 指南里不会出现自定义配方页");
        }

        // ---- ② 物品 lore（含「建造所需材料」清单）----
        guideLine(sender, "\u00a7e  -- 物品描述 --");
        ItemStack icon = item.getItem();
        org.bukkit.inventory.meta.ItemMeta meta = icon == null ? null : icon.getItemMeta();
        guideLine(sender, "\u00a77    名称: " + com.example.touhou.core.RecipePages.labelOf(icon));
        if (meta != null && meta.getLore() != null) {
            for (String line : meta.getLore()) {
                guideLine(sender, "\u00a77    " + com.example.touhou.core.Notify.plain(line));
            }
        } else {
            guideLine(sender, "\u00a78    (没有 lore)");
        }
        // 现算一遍材料清单：证明物品描述里那几行就是这份数据（同一个入口算出来的）
        guideLine(sender, "\u00a7e  -- 从层图现算的建造材料 --");
        for (String line : com.example.touhou.core.StructureMaterials.describe(layers, legend)) {
            guideLine(sender, "\u00a78  " + line);
        }

        // ---- ③ 可合成性核查 ----
        guideLine(sender, "\u00a7e  -- 可合成性核查（都应该是 0 / 否）--");
        ItemStack[] recipe = item.getRecipe();
        int filled = 0;
        if (recipe != null) {
            for (ItemStack it : recipe) {
                if (it != null && !it.getType().isAir()) {
                    filled++;
                }
            }
        }
        guideLine(sender, "\u00a78    配方数组非空格数 = " + filled
                + "（数组长度 " + (recipe == null ? 0 : recipe.length) + "）");
        io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem machine =
                item.getRecipeType() == null ? null : item.getRecipeType().getMachine();
        boolean multiblock = machine instanceof io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
        guideLine(sender, "\u00a78    配方类型指向的机器 = "
                + (machine == null ? "(无)" : machine.getId())
                + "，是不是多方块机器 = " + (multiblock ? "\u00a7c是" : "\u00a7a否"));
        guideLine(sender, "\u00a78    Bukkit 配方表里能产出它的配方 = " + countRecipesFor(item)
                + " 条（原版工作台口径）");
        guideLine(sender, "\u00a78    Slimefun 多方块机器配方表里能产出它的 = " + countMachineRecipesFor(item)
                + " 条（增强工作台 / 冶炼炉 / 魔法工作台…口径）");
        guideLine(sender, "\u00a78    /sf give 能不能拿到 = "
                + (io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getById(item.getId()) == item
                        ? "\u00a7a能（物品已在 Slimefun 注册表里）" : "\u00a7c查不到"));
    }

    /** 遍历 Bukkit 的配方表，数"产物是给定粘液物品"的配方有几条。 */
    private static int countRecipesFor(
            io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem item) {
        int count = 0;
        java.util.Iterator<org.bukkit.inventory.Recipe> it = Bukkit.recipeIterator();
        while (it.hasNext()) {
            org.bukkit.inventory.Recipe recipe = it.next();
            if (recipe == null) {
                continue;
            }
            ItemStack result = recipe.getResult();
            if (result != null && item.isItem(result)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 数"Slimefun 的多方块机器（增强工作台 / 冶炼炉 / 魔法工作台…）里有没有配方产出这个物品"。
     *
     * <p>★ 为什么单查 Bukkit 的配方表不够：Slimefun 的合成<b>不走</b>原版配方系统 ——
     * 它把 addon 的配方塞进 {@code MultiBlockMachine#recipes}（判据见
     * {@code TouhouRecipeTypes} 的类注释）。所以"工作台摆不出来"必须两边都查：
     * <pre>
     *   原版工作台口径 → Bukkit.recipeIterator()      （countRecipesFor）
     *   增强工作台口径 → 各 MultiBlockMachine 的展示列表（本方法）
     * </pre>
     * 展示列表是"输入, 输出, 输入, 输出…"的扁平表，所以输出落在<b>奇数下标</b>。
     */
    private static int countMachineRecipesFor(
            io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem item) {
        int count = 0;
        for (io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem machine
                : Slimefun.getRegistry().getAllSlimefunItems()) {
            if (!(machine instanceof io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine mbm)) {
                continue;
            }
            java.util.List<ItemStack> display = mbm.getDisplayRecipes();
            if (display == null) {
                continue;
            }
            for (int i = 1; i < display.size(); i += 2) {
                ItemStack out = display.get(i);
                if (out != null && item.isItem(out)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * 打印一行指南自检输出。
     *
     * <p>控制台来源只走 {@link Log#command}（带 {@code [TOUHOU] guide} 前缀，便于 grep）；
     * 玩家来源只走聊天栏 —— 两边都发会让控制台日志出现一模一样的两份。
     */
    private static void guideLine(CommandSender sender, String line) {
        if (sender instanceof org.bukkit.entity.Player) {
            sender.sendMessage(line);
        } else {
            log("[TOUHOU] guide " + com.example.touhou.core.Notify.plain(line));
        }
    }

    // ------------------------------------------------------------------ power

    /**
     * POWER 网络诊断。
     *
     * <pre>
     *   /touhou power                看向一个 POWER 方块（8 格内）后执行（仅玩家）
     *   /touhou power &lt;x y z&gt; [world] 诊断指定方块 —— ★ 控制台也能用（无头验证靠它）
     *   /touhou power rebuild        清空网络缓存，下次 tick 自动重算（排查用）
     * </pre>
     *
     * <p>输出里会分别报<b>上次结算</b>与<b>现算</b>两个口径的网络总量，
     * 并按节点类型列出「集成核心 / 中继器 / 存储单元 / <b>发电机</b>」各几个
     * （发电机就是幻梦捕捉器那一类"只捐不取"的产能设备）。
     */
    private void power(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("rebuild")) {
            int n = com.example.touhou.power.PowerNetworkManager.rebuildAll();
            sender.sendMessage(PREFIX + "\u00a7a已清空网络缓存（原 " + n + " 个节点），下次 tick 自动重算");
            return;
        }
        // ★ 给了坐标就直接诊断 —— 控制台也能用。
        //   原先这条路径只对玩家开放（"控制台看不到方块"），可无头验证恰恰只有控制台，
        //   于是这条命令在最需要它的场景下反而敲不动。坐标解析用宽松版 resolveAny，
        //   所以"没粘液方块数据的位置"也能问出"这里不是 POWER 方块"这个结论。
        if (args.length >= 3) {
            Location loc = resolveAny(sender, args);
            if (loc == null) {
                return;
            }
            sender.sendMessage(PREFIX + "\u00a7ePOWER 网络诊断 @ " + xyz(loc));
            for (String line : com.example.touhou.power.PowerNetworkManager.describe(loc)) {
                sender.sendMessage("\u00a78  " + line);
            }
            log("[TOUHOU] power @ " + xyz(loc));
            return;
        }
        if (!(sender instanceof org.bukkit.entity.Player p)) {
            sender.sendMessage(PREFIX + "\u00a77控制台看不到方块，请用: /touhou power <x> <y> <z> 或 /touhou power rebuild");
            return;
        }
        Location loc;
        org.bukkit.block.Block b = p.getTargetBlockExact(8);
        loc = b != null ? b.getLocation() : p.getLocation();
        sender.sendMessage(PREFIX + "\u00a7ePOWER 网络诊断");
        for (String line : com.example.touhou.power.PowerNetworkManager.describe(loc)) {
            sender.sendMessage("\u00a78  " + line);
        }
    }

    // ------------------------------------------------------------------ dreamcatcher

    /**
     * 幻梦捕捉器诊断 —— <b>POWER 产能设备的无头验证入口</b>。
     *
     * <pre>
     *   /touhou dreamcatcher &lt;x&gt; &lt;y&gt; &lt;z&gt;
     * </pre>
     *
     * <p>一次输出里就有需求要的四件事：<b>四周床的数量 / 当前自身 POWER /
     * 所在网络的 POWER 总量 / 产出速率（POWER/秒）</b>；另外带上"本轮计时、本轮已产出、
     * 累计产出、节流参数"，于是"1 张床 8 秒 1 点、4 张床 2 秒 1 点、没床不产出、
     * 自身缓冲不涨"这四条都能靠<b>隔一段时间敲两次命令</b>对比读出来，不必真人进游戏。
     *
     * <p>★ 床数是现场重数的（不走 ticker 的节流缓存）—— 命令看到的一定是此刻的真实情况。
     */
    private void dreamcatcher(CommandSender sender, String[] args) {
        Location loc = resolve(sender, args);
        if (loc == null) {
            return;
        }
        SlimefunItem item = BlockStorage.check(loc);
        if (!(item instanceof com.example.touhou.power.DreamCatcher dc)) {
            sender.sendMessage(PREFIX + "\u00a7c该方块不是幻梦捕捉器（实际 "
                    + (item == null ? "\u00a7c非 Slimefun 方块" : item.getId()) + "）");
            return;
        }
        sender.sendMessage(PREFIX + "\u00a7e幻梦捕捉器状态");
        for (String line : dc.describe(loc)) {
            sender.sendMessage("\u00a78  " + line);
        }
        // 单独再打一行"纯 ASCII 数值"，方便从日志里 grep 出时间序列（中文在 GBK 日志里会乱码）
        log("[TOUHOU] dreamcatcher @ " + xyz(loc)
                + " beds=" + dc.countBeds(loc)
                + " self=" + dc.powerCharge(loc) + "/" + dc.powerCapacity(loc)
                + " produced=" + com.example.touhou.core.TouhouData.getLong(
                        loc, com.example.touhou.power.DreamCatcher.KEY_PRODUCED, 0L));
    }

    // ------------------------------------------------------------------ seal

    /**
     * 梦想封印 集诊断。
     *
     * <pre>
     *   /touhou seal &lt;玩家名&gt;              读该玩家主手/副手的 POWER、上限、可用次数
     *   /touhou seal probe &lt;x&gt; &lt;y&gt; &lt;z&gt;     用指定坐标做一次"从附近网络取电"的实测
     *   /touhou seal selfcheck              参数自检（不需要玩家，控制台可用）
     * </pre>
     *
     * <p>★ 为什么要 {@code probe}：控制台没有"手持"这件事，而"充能从哪个网络取"
     * 恰恰是这次改造里最需要被证明的一环。{@code probe} 在<b>内存里</b>造一个测试用道具，
     * 然后调<b>与游戏内完全相同</b>的那段取电逻辑（{@code FantasySeal#probeCharge}：
     * 找最近节点 → 取它所在的网 → 抽电 → 写回道具），把过程与读数全打出来。
     * 于是"取电确实发生、且取自哪张网"有了可 grep 的证据。
     */
    private void seal(CommandSender sender, String[] args) {
        com.example.touhou.core.FantasySeal seal = AddSlimefunItems.FANTASY_SEAL;
        if (seal == null) {
            sender.sendMessage(PREFIX + "\u00a7c梦想封印 集未注册（物品注册失败？看控制台）");
            return;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("probe")) {
            Location loc = resolveAny(sender, Arrays.copyOfRange(args, 1, args.length));
            if (loc == null) {
                return;
            }
            // 内存里的测试道具：克隆物品模板（PDC 里带着粘液 id，与玩家手里那件同源）
            ItemStack probe = AddItems.FANTASY_SEAL.clone();
            sender.sendMessage(PREFIX + "\u00a7e梦想封印 集 · 取电实测 @ " + xyz(loc));
            sender.sendMessage("\u00a77  取电前：");
            for (String line : seal.describeItem("    测试物品", probe)) {
                sender.sendMessage("\u00a78" + line);
            }
            var result = seal.probeCharge(probe, loc);
            sender.sendMessage("\u00a77  取自：" + "\u00a7f" + result.detail());
            sender.sendMessage("\u00a77  本次取到 \u00a7f" + result.taken() + " POWER"
                    + "\u00a77（" + result.before() + " → " + result.after()
                    + " / " + result.capacity() + "）");
            for (String line : seal.describeItem("    取电后", probe)) {
                sender.sendMessage("\u00a78" + line);
            }
            ItemMeta probeMeta = probe.getItemMeta();
            if (probeMeta != null && probeMeta.getLore() != null) {
                for (String line : probeMeta.getLore()) {
                    if (ChatColor.stripColor(line) != null
                            && ChatColor.stripColor(line).startsWith("POWER:")) {
                        sender.sendMessage("\u00a77  道具 lore 里那一行：\u00a7f" + line);
                        break;
                    }
                }
            }
            log("[TOUHOU] seal probe @ " + xyz(loc) + " taken=" + result.taken()
                    + " before=" + result.before() + " after=" + result.after()
                    + " cap=" + result.capacity());
            return;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("selfcheck")) {
            sender.sendMessage(PREFIX + "\u00a7e梦想封印 集 · 参数自检");
            for (String line : seal.selfCheck()) {
                sender.sendMessage("\u00a78  " + line);
            }
            log("[TOUHOU] seal selfcheck -> " + seal.getId());
            return;
        }

        // 指定玩家：读他主手/副手那件道具
        org.bukkit.entity.Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(PREFIX + "\u00a7c玩家不在线: " + args[0]
                    + "\u00a77（用法: /touhou seal <玩家名> | probe <x> <y> <z> | selfcheck）");
            return;
        }
        sender.sendMessage(PREFIX + "\u00a7e梦想封印 集 · " + target.getName() + " 的读数");
        boolean found = false;
        for (org.bukkit.inventory.EquipmentSlot slot : new org.bukkit.inventory.EquipmentSlot[]{
                org.bukkit.inventory.EquipmentSlot.HAND,
                org.bukkit.inventory.EquipmentSlot.OFF_HAND}) {
            ItemStack item = target.getInventory().getItem(slot);
            String label = "  " + (slot == org.bukkit.inventory.EquipmentSlot.HAND ? "主手" : "副手");
            if (item == null || item.getType().isAir()) {
                sender.sendMessage("\u00a78" + label + " = （空）");
                continue;
            }
            found = true;
            for (String line : seal.describeItem(label, item)) {
                sender.sendMessage("\u00a78  " + line);
            }
        }
        if (!found) {
            sender.sendMessage(PREFIX + "\u00a77两手里都没有道具；也可以用测试物品自检："
                    + "\u00a7f/touhou seal probe <x> <y> <z>");
        }
        log("[TOUHOU] seal player=" + target.getName()
                + " mainhand=" + seal.chargeOf(target.getInventory().getItemInMainHand())
                + "/" + seal.chargeOf(target.getInventory().getItemInOffHand()));
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
            sender.sendMessage("\u00a77  投影旋转槽 " + UtsuhoReactorCore.HOLOGRAM_ROTATE_SLOT + " 锁死="
                    + (!core.guiLock().isRealSlot(UtsuhoReactorCore.HOLOGRAM_ROTATE_SLOT)
                            ? "\u00a7a是" : "\u00a7c否")
                    + "\u00a78（H=" + UtsuhoReactorCore.HOLOGRAM_SLOT + " 正下方一格）");
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
            sender.sendMessage("\u00a77  投影旋转槽 " + Saizenbako.HOLOGRAM_ROTATE_SLOT + " 锁死="
                    + (!saizen.guiLock().isRealSlot(Saizenbako.HOLOGRAM_ROTATE_SLOT)
                            ? "\u00a7a是" : "\u00a7c否")
                    + "\u00a78（紧贴 H=" + Saizenbako.HOLOGRAM_SLOT + " 右侧）");
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
     * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; rotate   投影顺时针转 90°（等价于点 GUI 的投影旋转）
     * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; info     开关状态 / 朝向 / 缓存 / 附近实体计数
     * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; cells    只打印落点表（逐格 partId + 偏移 + 旋转后世界坐标）
     * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; mapping  逐格打印"partId → 解析出的显示物品"
     * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; count    只数附近带标记的投影实体（验证"真的生成了"）
     * /touhou proj &lt;x&gt; &lt;y&gt; &lt;z&gt; clean [r]  清孤儿投影（LogiTech 的 HOLOGRAM_REMOVER 等价物）
     * /touhou proj list                  列出当前本插件持有的全部投影组
     * </pre>
     *
     * <p>{@code count} 是"投影到底有没有真的生成实体"的<b>证据</b>：它数的是世界里带
     * {@code display-source} 标记的 Display/Interaction —— 与开关状态（方块数据）
     * 是两套独立读数，互相印证。
     *
     * <p>{@code mapping} 是"每一格画出来的是什么"的<b>证据</b>：它把结构里出现的每个
     * part id 逐个过一遍解析链（{@link MultiBlockProjection#resolveIconId}），
     * 于是"层图里的 {@code #touhou:reactor_shell} 到底解析成了哪个方块"
     * 这种事不用进游戏就看得见 —— 保护罩那一格曾经因为解析不出来而整片透明。
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
                // ★ 画的时候用的是【投影朝向】（touhou:mb-holo-dir 优先，没有才跟随结构朝向），
                //   所以这里也用它 —— 否则 cells 打印出来的世界坐标会与实际画的位置不一致。
                ReactorStructure.Direction use = MultiBlockProjection.direction(loc, st);
                List<ReactorStructure.Cell> cells = st.cells();
                List<ReactorStructure.Cell> drawable = ReactorStructure.solidCells(cells);
                sender.sendMessage("\u00a77  结构实现: " + st.getClass().getName());
                sender.sendMessage("\u00a77  结构 " + st.name()
                        + "  落点 " + cells.size() + " 格（含空气要求）"
                        + "  可画 " + drawable.size() + " 格"
                        + "  四向对称=" + st.isSymmetric()
                        + "  已落盘结构朝向=" + (dir == null ? "(无)" : dir.label()));
                sender.sendMessage("\u00a77  实际投影朝向=" + use.display()
                        + "\u00a78（" + MultiBlockProjection.KEY_HOLOGRAM_DIR + " = "
                        + TouhouData.getString(loc, MultiBlockProjection.KEY_HOLOGRAM_DIR, "(未设定)")
                        + "）");
                if (st instanceof com.example.touhou.core.LayeredReactorStructure layered) {
                    int[] sz = layered.size();
                    sender.sendMessage("\u00a77  层图尺寸 " + sz[0] + "x" + sz[1] + "x" + sz[2]
                            + "  构件计数 " + layered.partCount()
                            + "  核心在图内位置(层,行,列)=" + Arrays.toString(layered.corePosition()));
                }
                if (sub.equals("cells")) {
                    // 逐格打印：偏移 → 旋转后的世界坐标 → 该格要什么
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

        // mapping：逐格证明"这一格画出来是什么"（解析链见 MultiBlockProjection#resolveIconId）
        if (sub.equals("mapping")) {
            ReactorStructure.ProjectionHost host = hostOf(item);
            if (host == null) {
                sender.sendMessage(PREFIX + "\u00a7c这个方块不是已接入投影的多方块核心，拿不到落点表");
                return;
            }
            ReactorStructure st = host.structure();
            ReactorStructure.Direction use = MultiBlockProjection.direction(loc, st);
            sender.sendMessage(PREFIX + "\u00a7epartId → 显示物品 @ " + xyz(loc)
                    + "  （按朝向 " + use.display() + " 画）");
            java.util.LinkedHashMap<String, Integer> seen = new java.util.LinkedHashMap<>();
            for (ReactorStructure.Cell c : ReactorStructure.solidCells(st.cells())) {
                seen.merge(c.id(), 1, Integer::sum);
            }
            int ok = 0;
            int miss = 0;
            for (java.util.Map.Entry<String, Integer> e : seen.entrySet()) {
                String id = e.getKey();
                String resolved = MultiBlockProjection.resolveIconId(id);
                String name = MultiBlockProjection.iconNameOf(id);
                String where = e.getValue() + " 格";
                if (resolved == null) {
                    miss++;
                    sender.sendMessage("\u00a78  " + String.format("%-26s", id)
                            + "\u00a7c → 未解析（画出来是透明格）\u00a78  " + where);
                } else {
                    ok++;
                    sender.sendMessage("\u00a77  " + String.format("%-26s", id)
                            + "\u00a7f → " + resolved + " \u00a7b「" + name + "」\u00a78  " + where
                            + (resolved.equals(id) ? "" : "\u00a78  " + resolutionNote(id, resolved)));
                }
            }
            sender.sendMessage("\u00a77  part id 共 " + seen.size() + " 种：\u00a7a解析 " + ok
                    + " \u00a7c未解析 " + miss
                    + "\u00a78（未解析的格子仍然生成实体，只是 item 为 null ⇒ 看起来透明）");
            log("[TOUHOU] proj mapping @ " + xyz(loc) + " ids=" + seen.size()
                    + " resolved=" + ok + " unresolved=" + miss);
            return;
        }

        // rotate：走与 GUI 旋转按钮完全相同的那条链路
        if (sub.equals("rotate")) {
            ReactorStructure.ProjectionHost host = hostOf(item);
            if (host == null) {
                sender.sendMessage(PREFIX + "\u00a7c这个方块不是已接入投影的多方块核心，无法旋转投影（实际 "
                        + (item == null ? "\u00a7c非 Slimefun 方块" : item.getId()) + "）");
                return;
            }
            ReactorStructure.Direction before = MultiBlockProjection.direction(loc, host.structure());
            int structBefore = TouhouData.getInt(loc, TouhouData.KEY_DIRECTION, -1);
            ReactorStructure.Direction after;
            if (item instanceof UtsuhoReactorCore) {
                after = UtsuhoReactorCore.rotateProjection(null, loc);
            } else if (item instanceof Saizenbako) {
                after = Saizenbako.rotateProjection(null, loc);
            } else {
                sender.sendMessage(PREFIX + "\u00a7c这个方块不支持旋转投影");
                return;
            }
            int structAfter = TouhouData.getInt(loc, TouhouData.KEY_DIRECTION, -1);
            sender.sendMessage(PREFIX + "\u00a7e旋转投影 @ " + xyz(loc)
                    + "  结构四向对称=" + host.structure().isSymmetric());
            sender.sendMessage("\u00a77  投影朝向 " + before.display() + " \u00a77\u2192 "
                    + after.display()
                    + "\u00a78（方块数据 " + MultiBlockProjection.KEY_HOLOGRAM_DIR + " = "
                    + TouhouData.getString(loc, MultiBlockProjection.KEY_HOLOGRAM_DIR, "(未设定)")
                    + "）");
            // ★ 这一行就是"旋转没有动结构朝向"的证据：两个键的读数必须一个变、一个不变。
            sender.sendMessage("\u00a77  结构朝向 " + structBefore + " \u2192 " + structAfter
                    + "\u00a78（方块数据 " + TouhouData.KEY_DIRECTION + "，旋转【不应】改动它）"
                    + (structBefore == structAfter ? "\u00a7a  ✓未变" : "\u00a7c  ✗被改了！"));
            sender.sendMessage("\u00a77  投影 " + (MultiBlockProjection.isOn(loc)
                    ? "\u00a7a开着" : "\u00a77关着")
                    + " \u00a77附近实体 \u00a7f" + MultiBlockProjection.countNearby(loc, 16)
                    + "\u00a78  （开着时应当已经按新朝向重画）");
            log("[TOUHOU] proj rotate @ " + xyz(loc) + " " + before.label() + " -> " + after.label()
                    + " structDir " + structBefore + "->" + structAfter
                    + " on=" + MultiBlockProjection.isOn(loc));
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

    /**
     * {@code proj mapping} 里那句"这个 id 是怎么解析出来的"。
     *
     * <p>只对 {@code #标签} 有意义 —— 它要回答的正是"同一个标签下有三个方块，
     * 为什么偏偏画的是保护罩"：因为 {@code ItemTags.setDefaultDisplay} 显式指定了它，
     * 而不是靠登记顺序碰运气。
     */
    private static String resolutionNote(String partId, String resolved) {
        if (partId == null || !partId.startsWith("#")) {
            return "";
        }
        String tag = partId.substring(1);
        String prefer = com.example.touhou.core.ItemTags.defaultDisplay(tag);
        if (prefer != null && prefer.equals(resolved)) {
            return "（标签 " + tag + " 的默认展示件；该标签共 "
                    + com.example.touhou.core.ItemTags.members(tag).size() + " 个成员）";
        }
        return "（标签 " + tag + " 没指定默认展示件 ⇒ 取成员里第一个能解析成物品的）";
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

    // ------------------------------------------------------------------ lily（杀意的百合）

    /**
     * 杀意的百合诊断 —— 无头验证三段行为与"追踪表不泄漏"。
     *
     * <pre>
     *   /touhou lily selfcheck                        参数自检（控制台可用）
     *   /touhou lily dir &lt;面&gt;                       只验证方向裁决（不碰世界、不生成实体）
     *   /touhou lily beam &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;面&gt; [world]   按某个面探一次激光长度，并打印前 3 格样本
     *   /touhou lily fire &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;dx&gt; &lt;dy&gt; &lt;dz&gt; [玩家] [world]
     *                                                完整发射仿真（真箭 → 命中 → 阶段二 → 清理）
     *   /touhou lily fire &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;yaw&gt; &lt;pitch&gt; --angles [玩家] [world]
     *   /touhou lily fire &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;dx&gt; &lt;dy&gt; &lt;dz&gt; [玩家] [world] --force
     *                                                强制在 2 tick 后终止（验证"没命中也要清理"）
     *   /touhou lily fire &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;dx&gt; &lt;dy&gt; &lt;dz&gt; --norig
     *                                                不搭台架：观察"没命中就不爆发"那条路径
     *   /touhou lily fire ... --debug                 逐 tick 打印区块/实体读数（排查用）
     *   /touhou lily laser &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;面&gt; [玩家] [world]
     *                                                实弹激光：验证弹射物范围伤害 + "不伤害发射者"
     *   /touhou lily impact &lt;x&gt; &lt;y&gt; &lt;z&gt; [玩家] [world]
     *                                                只做命中点爆发（激光+喷泉+12 支追踪箭）
     *   /touhou lily cleanup                          把两张追踪表收干净并打印条目数
     * </pre>
     *
     * <p>★ 为什么这条命令是<b>必须</b>的：无视重力 / 距离上限 min(120, 模拟距离×16) /
     * 8 秒时限 / 命中后爆发 / 方向规则 / 弹射物范围伤害 / 追踪清理 —— 这些无头环境下全靠肉眼。
     * 这里每一条都走<b>与游戏内完全相同</b>的代码路径（{@code MurderousLily} 里的
     * spawnShot / onShotHit / burst / finishShot），不是另写一份演示。
     */
    private void lily(CommandSender sender, String[] args) {
        com.example.touhou.core.MurderousLily lily = AddSlimefunItems.MURDEROUS_LILY;
        if (lily == null) {
            sender.sendMessage(PREFIX + "\u00a7c杀意的百合未注册（物品注册失败？看控制台）");
            return;
        }
        String action = args.length == 0 ? "selfcheck" : args[0].toLowerCase();

        if (action.equals("selfcheck")) {
            sender.sendMessage(PREFIX + "\u00a7e杀意的百合 · 参数自检");
            for (String line : lily.selfCheck()) {
                sender.sendMessage("\u00a78  " + line);
            }
            sender.sendMessage("\u00a77  方向规则 = " + com.example.touhou.core.MurderousLilyListener.directionRule());
            for (String line : com.example.touhou.core.MurderousLilyListener.describe()) {
                sender.sendMessage("\u00a78    监听 " + line);
            }
            log("[TOUHOU] lily selfcheck -> " + lily.getId()
                    + " trackedShots=" + com.example.touhou.core.MurderousLily.trackedShotCount()
                    + " trackedTrackers=" + com.example.touhou.core.MurderousLily.trackedTrackerCount());
            return;
        }

        if (action.equals("cleanup")) {
            int before = com.example.touhou.core.MurderousLily.trackedShotCount()
                    + com.example.touhou.core.MurderousLily.trackedTrackerCount();
            lily.clearAllTracked();
            int after = com.example.touhou.core.MurderousLily.trackedShotCount()
                    + com.example.touhou.core.MurderousLily.trackedTrackerCount();
            sender.sendMessage(PREFIX + "\u00a7e追踪表清理：" + before + " → " + after);
            log("[TOUHOU] lily cleanup before=" + before + " after=" + after);
            return;
        }

        if (action.equals("dir")) {
            if (args.length < 2) {
                sender.sendMessage(PREFIX + "\u00a7c用法: /touhou lily dir <面名|NONE>");
                return;
            }
            org.bukkit.block.BlockFace face = parseFace(args[1]);
            org.bukkit.util.Vector dir = com.example.touhou.core.MurderousLily
                    .laserDirection(face);
            sender.sendMessage(PREFIX + "\u00a7e方向裁决 · 面=" + args[1].toUpperCase()
                    + "（" + (face == null ? "按实体处理" : face.name()) + "）");
            sender.sendMessage("\u00a77  激光/喷泉方向 = "
                    + com.example.touhou.core.MurderousLily.fmt(dir));
            sender.sendMessage("\u00a78  规则：" + com.example.touhou.core.MurderousLilyListener.directionRule());
            log("[TOUHOU] lily dir face=" + args[1].toUpperCase()
                    + " dir=" + com.example.touhou.core.MurderousLily.fmt(dir));
            return;
        }

        if (action.equals("beam")) {
            String[] rest = Arrays.copyOfRange(args, 1, args.length);
            Location loc = resolveAny(sender, rest);
            if (loc == null) {
                return;
            }
            org.bukkit.block.BlockFace face = rest.length >= 4 ? parseFace(rest[3]) : null;
            var probe = lily.probeBeam(loc, face);
            sender.sendMessage(PREFIX + "\u00a7e激光几何探测 @ " + xyz(loc)
                    + "  面=" + (probe.face() == null ? "(实体)" : probe.face().name()));
            sender.sendMessage("\u00a77  方向 = " + com.example.touhou.core.MurderousLily.fmt(probe.direction()));
            sender.sendMessage("\u00a77  实际长度 = " + String.format("%.2f", probe.length())
                    + " 格" + (probe.blockedAt() < 0 ? "，未被截断"
                            : "，被实体方块截断于第 " + probe.blockedAt() + " 格"));
            sender.sendMessage("\u00a77  命中点范围伤害 = " + probe.damage()
                    + " 点弹射物伤害（球半径 " + probe.impactRadius() + " 格）");
            sender.sendMessage("\u00a78  前 3 格样本（每 0.25 格一格：方块类型 / 能不能穿光）：");
            for (int i = 1; i <= 12; i++) {
                Location p = loc.clone().add(probe.direction().clone().multiply(i * 0.25D));
                org.bukkit.block.Block b = p.getWorld().getBlockAt(p);
                boolean pass = com.example.touhou.core.MurderousLily
                        .beamPassable(p.getWorld(), p, 0.5D);
                sender.sendMessage("\u00a78    " + String.format("%.2f", i * 0.25D)
                        + " 格 → " + b.getType() + (pass ? " 可穿" : " 挡住"));
            }
            log("[TOUHOU] lily beam @ " + xyz(loc) + " face="
                    + (probe.face() == null ? "ENTITY" : probe.face().name())
                    + " dir=" + com.example.touhou.core.MurderousLily.fmt(probe.direction())
                    + " length=" + String.format("%.2f", probe.length())
                    + " blockedAt=" + probe.blockedAt());
            return;
        }

        if (action.equals("fire")) {
            lilyFire(sender, lily, Arrays.copyOfRange(args, 1, args.length));
            return;
        }

        if (action.equals("laser")) {
            String[] rest = Arrays.copyOfRange(args, 1, args.length);
            Location loc = resolveAny(sender, rest);
            if (loc == null) {
                return;
            }
            org.bukkit.block.BlockFace face = rest.length >= 4 ? parseFace(rest[3]) : null;
            String nameHint = rest.length >= 5 ? rest[4] : null;
            Object[] pick = ensureShooter(nameHint, loc);
            org.bukkit.entity.LivingEntity who = (org.bukkit.entity.LivingEntity) pick[0];
            boolean fakeMade = Boolean.TRUE.equals(pick[1]);
            if (who == null) {
                sender.sendMessage(PREFIX + "\u00a7c需要一个在线玩家，或让本命令临时造一个发射者"
                        + "（不指定玩家名时它会自己造）");
                return;
            }
            org.bukkit.util.Vector dir = com.example.touhou.core.MurderousLily.laserDirection(face);
            // ★ 光路上再放一头牛当"对照组"：僵尸（发射者）应该被排除、牛应该真的掉血
            Set<org.bukkit.block.Block> placed = new HashSet<>();
            java.util.List<org.bukkit.entity.Entity> spawned = new ArrayList<>();
            if (placeTestBlock(loc.clone().add(dir.clone().multiply(6.0D)), Material.STONE, placed)) {
                org.bukkit.entity.Cow cow = loc.getWorld().spawn(
                        loc.clone().add(dir.clone().multiply(3.0D)), org.bukkit.entity.Cow.class,
                        c -> {
                            c.setAI(false);
                            c.setSilent(true);
                            c.setPersistent(false);
                            c.setCollidable(false);
                        });
                spawned.add(cow);
            }
            var r = lily.testLaser(who, loc, face);
            sender.sendMessage(PREFIX + "\u00a7e实弹激光 @ " + xyz(loc)
                    + "  面=" + (face == null ? "(实体)" : face.name())
                    + "  发射者=" + who.getType()
                    + (fakeMade ? "（临时造的，用完即删）" : "（真人）"));
            sender.sendMessage("\u00a77  方向 = " + com.example.touhou.core.MurderousLily.fmt(r.direction()));
            sender.sendMessage("\u00a77  长度 = " + String.format("%.2f", r.length()) + " 格"
                    + (r.blockedAt() < 0 ? "，未被截断" : "，截断于第 " + r.blockedAt() + " 格"));
            sender.sendMessage("\u00a77  AABB 内可命中实体 = " + r.inBeam()
                    + "，其中因【是发射者本人】被排除 = " + r.excluded()
                    + "，实际扣血 = " + r.damaged() + "（每个 " + r.damage()
                    + " 弹射物伤害，球半径 " + r.impactRadius() + " 格）");
            sender.sendMessage("\u00a78  预期：发射者（" + who.getType()
                    + "）必须落在 excluded 里；对照组那几头牛必须是 damaged");
            clearTestBlocks(placed);
            for (org.bukkit.entity.Entity e : spawned) {
                removeFakeShooter(e);
            }
            if (fakeMade) {
                removeFakeShooter(who);
            }
            log("[TOUHOU] lily laser @ " + xyz(loc) + " face="
                    + (face == null ? "ENTITY" : face.name())
                    + " len=" + String.format("%.2f", r.length())
                    + " inBeam=" + r.inBeam() + " excluded=" + r.excluded()
                    + " damaged=" + r.damaged() + " damage=" + r.damage());
            return;
        }

        if (action.equals("impact")) {
            String[] rest = Arrays.copyOfRange(args, 1, args.length);
            Location loc = resolveAny(sender, rest);
            if (loc == null) {
                return;
            }
            String nameHint = rest.length >= 4 ? rest[3] : null;
            Object[] pick = ensureShooter(nameHint, loc);
            org.bukkit.entity.LivingEntity who = (org.bukkit.entity.LivingEntity) pick[0];
            boolean fakeMade = Boolean.TRUE.equals(pick[1]);
            if (who == null) {
                sender.sendMessage(PREFIX + "\u00a7c需要一个在线玩家，或让本命令临时造一个发射者"
                        + "（不指定玩家名时它会自己造）");
                return;
            }
            sender.sendMessage(PREFIX + "\u00a7e阶段二爆发 @ " + xyz(loc) + "  发射者=" + who.getType()
                    + (fakeMade ? "（临时造的，用完即删）" : "（真人）"));
            var report = lily.burstFrom(who, loc, new org.bukkit.util.Vector(0, 1, 0),
                    "命令直接触发");
            sender.sendMessage("\u00a77  方向 = " + com.example.touhou.core.MurderousLily
                    .fmt(report.direction()));
            sender.sendMessage("\u00a77  激光（几何）= " + String.format("%.2f", report.beamLength())
                    + " 格长" + (report.beamBlockedAt() < 0 ? "，未被截断"
                            : "，截断于第 " + report.beamBlockedAt() + " 格"));
            sender.sendMessage("\u00a77  落点范围伤害 = " + report.impactDamage()
                    + " 点弹射物伤害，球半径 " + report.impactRadius()
                    + " 格，实际扣血 " + report.damaged() + " 个实体");
            sender.sendMessage("\u00a77  喷泉 = " + report.fountain() + " 粒子，追踪箭 = "
                    + report.trackers() + " 支");
            if (fakeMade) {
                removeFakeShooter(who);
            }
            log("[TOUHOU] lily impact @ " + xyz(loc)
                    + " len=" + String.format("%.2f", report.beamLength())
                    + " impact=" + report.impactDamage() + "@" + report.impactRadius()
                    + " trackers=" + report.trackers() + " fountain=" + report.fountain());
            return;
        }

        if (action.equals("tracers")) {
            // ★ 衍生箭命中路径的实弹验证：放一头活靶，朝它射 12 支衍生箭，
            //    然后打印"最近一次追踪箭命中"的读数 + 追踪箭表条目数。
            String[] rest = Arrays.copyOfRange(args, 1, args.length);
            Location loc = resolveAny(sender, rest);
            if (loc == null) {
                return;
            }
            String nameHint = rest.length >= 4 ? rest[3] : null;
            Object[] pick = ensureShooter(nameHint, loc.clone().add(0.0D, 2.0D, 0.0D));
            org.bukkit.entity.LivingEntity who = (org.bukkit.entity.LivingEntity) pick[0];
            boolean fakeMade = Boolean.TRUE.equals(pick[1]);
            if (who == null) {
                sender.sendMessage(PREFIX + "\u00a7c需要一个发射者（不指定玩家名时本命令会临时造一个）");
                return;
            }
            // 活靶：放在发射者正下方偏一点，让"喷泉样式"的箭有机会扫到它
            org.bukkit.entity.Cow target = loc.getWorld().spawn(
                    loc.clone().add(0.0D, 0.5D, 2.0D), org.bukkit.entity.Cow.class, c -> {
                        c.setAI(false);
                        c.setSilent(true);
                        c.setPersistent(false);
                        c.setCollidable(true);   // 要能被箭打中
                    });
            java.util.List<org.bukkit.entity.Entity> spawned = new ArrayList<>();
            spawned.add(target);

            int n = lily.fireTrackersForTest(who, loc);
            sender.sendMessage(PREFIX + "\u00a7e衍生箭实弹测试 @ " + xyz(loc)
                    + "  发射者=" + who.getType() + "  活靶=COW @ " + xyz(target.getLocation()));
            sender.sendMessage("\u00a77  已放出追踪箭 = " + n + " 支；追踪表条目 = "
                    + com.example.touhou.core.MurderousLily.trackedTrackerCount());
            sender.sendMessage("\u00a78  等 3 秒让它们飞到活靶（0.5 秒后才开始追踪）…");
            for (org.bukkit.entity.Entity e : spawned) {
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        removeFakeShooter(e);
                    }
                }.runTaskLater(Touhou.getInstance(), 100L);
            }
            if (fakeMade) {
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        removeFakeShooter(who);
                    }
                }.runTaskLater(Touhou.getInstance(), 140L);
            }
            for (int i = 1; i <= 3; i++) {
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        sender.sendMessage("\u00a78  [" + (System.currentTimeMillis() % 100000)
                                + "] 追踪箭表剩余 = "
                                + com.example.touhou.core.MurderousLily.trackedTrackerCount()
                                + "   最近命中读数 = "
                                + com.example.touhou.core.MurderousLilyListener
                                        .lastTrackerHitSummary());
                        log("[TOUHOU] lily tracers 追踪箭表剩余="
                                + com.example.touhou.core.MurderousLily.trackedTrackerCount()
                                + " 最近命中=" + com.example.touhou.core.MurderousLilyListener
                                        .lastTrackerHitSummary());
                    }
                }.runTaskLater(Touhou.getInstance(), 40L * i);
            }
            return;
        }

        sender.sendMessage(PREFIX + "\u00a77用法: /touhou lily <selfcheck|dir <面>|beam <x> <y> <z> <面>|"
                + "fire <x> <y> <z> <dx> <dy> <dz> [玩家] [--force]|"
                + "laser <x> <y> <z> <面> [玩家]|impact <x> <y> <z> [玩家]|"
                + "tracers <x> <y> <z> [玩家]|cleanup>");
    }

    /**
     * {@code /touhou lily fire} 的完整发射仿真。
     *
     * <p>参数解析顺序：坐标 3 个 → 方向 3 个（向量，或用 {@code --angles} 表示 yaw/pitch）
     * → 可选玩家名 → 可选世界名 → 可选 {@code --force}。
     */
    private void lilyFire(CommandSender sender, com.example.touhou.core.MurderousLily lily,
                          String[] args) {
        if (args.length < 6) {
            sender.sendMessage(PREFIX + "\u00a7c用法: /touhou lily fire <x> <y> <z> <dx> <dy> <dz>"
                    + " [玩家] [世界] [--force | --angles]");
            return;
        }
        boolean angles = false;
        boolean force = false;
        boolean noRig = false;
        boolean debug = false;
        boolean chase = false;
        boolean atSpawn = false;
        List<String> rest = new ArrayList<>();
        for (String a : args) {
            if (a == null) {
                continue;
            }
            if (a.equalsIgnoreCase("--angles")) {
                angles = true;
            } else if (a.equalsIgnoreCase("--force")) {
                force = true;
            } else if (a.equalsIgnoreCase("--norig")) {
                noRig = true;      // 不搭台架：用来观察"没命中"的那条路径（要求射向开阔方向）
            } else if (a.equalsIgnoreCase("--debug")) {
                debug = true;      // 逐 tick 打印箭矢读数（排查"箭为什么不动"）
            } else if (a.equalsIgnoreCase("--chase")) {
                chase = true;      // 无头跟随模式：让临时发射者跟着箭飞（否则 Paper 不 tick 它）
            } else if (a.equalsIgnoreCase("--at-spawn")) {
                atSpawn = true;    // 起点改用"世界出生点 + 偏移"，让箭落在服务端活跃区块内
            } else {
                rest.add(a);
            }
        }
        if (rest.size() < 6) {
            sender.sendMessage(PREFIX + "\u00a7c坐标与方向都要给全（共 6 个数字）");
            return;
        }
        Location loc = resolveAny(sender, rest.subList(0, 3).toArray(new String[0]));
        if (loc == null) {
            return;
        }
        if (atSpawn) {
            // ★ 为什么需要它：Paper 的实体激活范围（spigot.yml 的 entity-activation-range）
            //   让"附近没有玩家"的实体不被 tick —— 实测远处放的箭 ticksLived 恒为 0。
            //   而世界出生点附近的区块始终是活跃区块，所以把测试点搬过去，箭才会真的飞。
            //   参数含义变成"相对出生点的偏移"（x/y/z，y 用给定值直接取绝对高度更容易命中空气）。
            Location spawn = loc.getWorld().getSpawnLocation();
            loc = new Location(loc.getWorld(),
                    spawn.getBlockX() + (int) Double.parseDouble(rest.get(0)),
                    (int) Double.parseDouble(rest.get(1)),
                    spawn.getBlockZ() + (int) Double.parseDouble(rest.get(2)));
            sender.sendMessage("\u00a78  （--at-spawn：出生点 " + xyz(spawn)
                    + " + 偏移 " + rest.get(0) + "/" + rest.get(1) + "/" + rest.get(2)
                    + " ⇒ 实际起点 " + xyz(loc) + "）");
        }
        double a1;
        double a2;
        double a3;
        try {
            a1 = Double.parseDouble(rest.get(3));
            a2 = Double.parseDouble(rest.get(4));
            a3 = Double.parseDouble(rest.get(5));
        } catch (NumberFormatException e) {
            sender.sendMessage(PREFIX + "\u00a7c方向必须是数字");
            return;
        }
        org.bukkit.util.Vector dir = angles
                ? fromAngles(a1, a2) : new org.bukkit.util.Vector(a1, a2, a3);
        if (dir.lengthSquared() < 1.0E-9) {
            sender.sendMessage(PREFIX + "\u00a7c方向不能是零向量");
            return;
        }
        // 后面的可选参数：第 7 个是世界名（Bukkit.getWorld 认得出）就当世界，否则当玩家名
        World worldOverride = null;
        String whoHint = null;
        for (int i = 6; i < rest.size(); i++) {
            World w = Bukkit.getWorld(rest.get(i));
            if (w != null && worldOverride == null && whoHint == null) {
                worldOverride = w;
                loc = new Location(w, loc.getX(), loc.getY(), loc.getZ());
            } else if (whoHint == null) {
                whoHint = rest.get(i);
            }
        }
        // 发射者：优先用真人；无头测试服没人时临时造一个僵尸（用完就删）
        // ★ 造型位置刻意抬高 2 格：僵尸的碰撞箱与箭的出生点重叠时，实测那支箭会被服务端
        //   在约 1.4 秒后移除（且位置一动不动）—— 抬高之后箭就能正常飞。
        Object[] shooterPick = ensureShooter(whoHint, loc.clone().add(0.0D, 2.0D, 0.0D));
        org.bukkit.entity.LivingEntity who = (org.bukkit.entity.LivingEntity) shooterPick[0];
        boolean fakeMade = Boolean.TRUE.equals(shooterPick[1]);
        if (who == null) {
            sender.sendMessage(PREFIX + "\u00a7c需要一个在线玩家，或让本命令临时造一个发射者"
                    + "（不指定玩家名时它会自己造）");
            return;
        }
        if (fakeMade) {
            sender.sendMessage("\u00a78  （没有在线玩家：临时造了 " + who.getType()
                    + " 当发射者，用完即删）");
        }
        if (!loc.getChunk().isLoaded()) {
            sender.sendMessage(PREFIX + "\u00a7e所在区块未加载，先加载它：" + xyz(loc));
            return;
        }
        org.bukkit.util.Vector d = dir.clone().normalize();
        // 测试台架：正前方 2 格放一块石头（保证这支箭一定命中），
        //           反方向 8 格再放一块（给激光一个"可被物块阻挡"的机会）
        Set<org.bukkit.block.Block> placed = new HashSet<>();
        // ★ 命中靶放在【正前方 2 格】：箭速 1.25 格/tick ⇒ 第 2 tick 就命中。
        //   为什么这么近：无头（没有玩家）的服务端里，一支新生成的箭会在约 20 tick 后
        //   被服务端自行移除（实测 tick=268 时收到 EntityRemoveFromWorldEvent，
        //   原因见交付报告），所以"飞行演示"不能依赖长距离飞行 ——
        //   命中判定、方向裁决、阶段二爆发全都必须在头几 tick 内完成。
        //   而"飞得出多远"另行用 --norig 的自由飞行读数验证（在那 20 tick 里足够看出在动，
        //   距离上限本身也能在 selfcheck 里读到）。
        Location faceBlock = loc.clone().add(d.clone().multiply(2.0D));
        Location beamBlock = loc.clone().add(d.clone().multiply(-8.0D));
        String rigText;
        if (noRig) {
            rigText = "台架：本发【不搭台架】（--norig）—— 用来观察「没命中」那条路径与自由飞行读数";
        } else {
            placeTestBlock(faceBlock, Material.STONE, placed);
            placeTestBlock(beamBlock, Material.STONE, placed);
            rigText = "台架：正前方 2 格 " + xyz(faceBlock) + " 放了 " + Material.STONE
                    + "（保证头几 tick 内命中）；反方向 8 格 " + xyz(beamBlock)
                    + " 放了 " + Material.STONE + "（给激光一个截断点）";
        }

        sender.sendMessage(PREFIX + "\u00a7e杀意的百合 · 完整发射仿真 @ " + xyz(loc));
        final String shooterKind = who.getType().toString() + (fakeMade ? "（临时造的）" : "（真人）");
        final String boomDir = com.example.touhou.core.MurderousLily.fmt(d);
        final String rigLine = rigText;
        sender.sendMessage("\u00a77  发射者 = " + shooterKind + "   方向 = " + boomDir);
        sender.sendMessage("\u00a78  " + rigLine);
        sender.sendMessage("\u00a78  箭要真的飞出去，结果会在几十 tick 后自己打出来（命令不阻塞主线程）");

        // ★ 异步交付：仿真跨很多 tick，绝不能在主线程上等（那会卡死服务端，实测踩过）
        final org.bukkit.entity.LivingEntity shooterRef = who;
        final Set<org.bukkit.block.Block> placedRef = placed;
        final String whereText = xyz(loc);
        if (chase && fakeMade) {
            sender.sendMessage("\u00a78  无头跟随模式（--chase）：临时发射者会跟着箭飞，"
                    + "这样 Paper 才会 tick 那支箭（否则附近没玩家的箭一动不动）");
        }
        lily.simulateFire(who, loc, d, force, chase, sim -> {
            for (String line : sim.lines()) {
                sender.sendMessage("\u00a78  " + line);
            }
            clearTestBlocks(placedRef);
            new org.bukkit.scheduler.BukkitRunnable() {
                @Override
                public void run() {
                    removeFakeShooter(shooterRef);   // 临时发射者用完即删
                }
                // 5 秒后再删：追踪箭的索敌还引用着它，让它们自然过期更干净
            }.runTaskLater(Touhou.getInstance(), 120L);
            log("[TOUHOU] lily fire @ " + whereText + " dir=" + boomDir
                    + " speed=" + String.format("%.3f", sim.initialSpeed())
                    + " gravity=" + sim.gravity()
                    + " travelled=" + String.format("%.2f", sim.travelled())
                    + " ticks=" + sim.elapsedTicks()
                    + " reason=" + (sim.report() == null ? "?" : sim.report().reason().name())
                    + " absorbed=" + sim.absorbedHits()
                    + " trackedBefore=" + sim.trackedBefore()
                    + " trackersNow=" + sim.trackersNow()
                    + " trackedAfter=" + sim.trackedAfter());
        });
        if (debug) {
            // ★ 排查"箭为什么一动不动"用的：打印那一格的区块加载状态。
            //   本机实测的结论写在交付报告里 —— 无头（没有玩家）时 Paper 不会 tick 远处的实体，
            //   所以那一格的区块哪怕 loaded=true，箭也只是"存在但不被 tick"。
            final Location dbgLoc = loc.clone();
            for (int i = 1; i <= 6; i++) {
                final int n = i;
                new org.bukkit.scheduler.BukkitRunnable() {
                    @Override
                    public void run() {
                        org.bukkit.World w = dbgLoc.getWorld();
                        int cx = dbgLoc.getBlockX() >> 4;
                        int cz = dbgLoc.getBlockZ() >> 4;
                        log("[LILY-DBG] tick+" + n
                                + " chunk=(" + cx + "," + cz + ")"
                                + " isChunkLoaded=" + w.isChunkLoaded(cx, cz)
                                + " nearby=" + w.getNearbyEntities(dbgLoc, 4, 4, 4).size());
                    }
                }.runTaskLater(Touhou.getInstance(), i);
            }
        }
    }

    // ------------------------------------------------------------------ echo（维度穿梭）

    /**
     * 「维度穿梭」的无头验证入口 —— 另一个世界的回响的获取机制。
     *
     * <pre>
     *   /touhou echo selfcheck                          物品 / 机制参数自检（不需要玩家）
     *   /touhou echo rule                               判定矩阵自测（纯逻辑，不碰世界）
     *   /touhou echo container &lt;x&gt; &lt;y&gt; &lt;z&gt;            在临时箱子上跑转化内核（不需要玩家）
     *   /touhou echo convert [玩家名]                    直接转化在线玩家背包（不判维度、不走传送）
     *   /touhou echo probe &lt;玩家名&gt; [世界名]            干跑：他会转几个（不改背包）
     *   /touhou echo shuttle &lt;玩家名&gt; &lt;from&gt; &lt;to&gt; [--force]  模拟一次维度穿梭（真的转化）
     *   /touhou echo cooldown [clear]                   读 / 清玩家级冷却
     * </pre>
     *
     * <p>★ <b>不需要玩家</b>的那三条（{@code selfcheck} / {@code rule} / {@code container}）
     * 是无头测试服上唯一能跑的部分；{@code probe} / {@code shuttle} / {@code convert}
     * 都需要真实在线玩家（{@code shuttle} 作用的正是事件监听器调用的那个入口）。
     *
     * <p>★ 为什么要 {@code shuttle} 这条"指定 from/to 世界"的形态：
     * 真实触发点是 {@code PlayerChangedWorldEvent}，而<b>无头测试服没有玩家</b>
     * —— 事件本身在这个环境里跑不出来（详见 {@code EchoOfAnotherWorldListener}
     * 的类注释与最终报告）。所以命令把<b>同一段裁决与转化逻辑</b>
     * （{@link com.example.touhou.core.EchoOfAnotherWorld#shuttle}）
     * 拿出来直接调，参数是"玩家 + 离开的世界 + 到达的世界"。
     * 于是下面这些断言都能在控制台里被证明：
     * <ol>
     *   <li>末地 / 同维度 / 自定义世界<b>不</b>转化（换一组 from/to 再跑一次即可）；</li>
     *   <li>主世界↔地狱<b>会</b>转化，且换算精确（水晶数 → 回响数）；</li>
     *   <li>原水晶<b>确实被删除</b>（跑完再 probe 一次就是 0）；</li>
     *   <li><b>幂等</b>：紧接着再跑一次（{@code --force} 绕过冷却）⇒ 水晶 0、回响 0、什么都不做。</li>
     * </ol>
     *
     * <p>★ {@code --force} 只做一件事：忽略玩家级冷却。它<b>不能</b>绕过幂等
     * —— 幂等是"没水晶可转"这个事实带来的，任何开关都绕不过去（这正是要证明的性质）。
     */
    private void echo(CommandSender sender, String[] args) {
        com.example.touhou.core.EchoOfAnotherWorld item = AddSlimefunItems.ECHO_OF_ANOTHER_WORLD;
        if (item == null) {
            sender.sendMessage(PREFIX + "\u00a7c另一个世界的回响未注册（物品注册失败？看控制台）");
            return;
        }
        String sub = args.length >= 1 ? args[0].toLowerCase() : "selfcheck";

        switch (sub) {
            case "selfcheck", "check" -> {                sender.sendMessage(PREFIX + "\u00a7e另一个世界的回响 · 参数自检");
                for (String line : item.selfCheck()) {
                    sender.sendMessage("\u00a78  " + line);
                }
                sender.sendMessage(PREFIX + "\u00a7e监听器");
                for (String line : com.example.touhou.core.EchoOfAnotherWorldListener.describe()) {
                    sender.sendMessage("\u00a78  " + line);
                }
                log("[TOUHOU] echo selfcheck id=" + item.getId());
            }
            case "rule", "rules", "matrix" -> echoRule(sender);
            case "container", "chest" -> echoContainer(sender,
                    args.length >= 2 ? args[1] : null,
                    args.length >= 3 ? args[2] : null,
                    args.length >= 4 ? args[3] : null);
            case "probe", "dry", "dryrun" -> echoProbe(sender,
                    args.length >= 2 ? args[1] : null,
                    args.length >= 3 ? args[2] : null);
            case "shuttle", "go" -> echoShuttle(sender,
                    args.length >= 2 ? args[1] : null,
                    args.length >= 3 ? args[2] : null,
                    args.length >= 4 ? args[3] : null,
                    Arrays.stream(args).anyMatch(a -> a.equalsIgnoreCase("--force")));
            case "convert", "convertinv" -> echoConvert(sender,
                    args.length >= 2 ? args[1] : null);
            case "cooldown", "cd" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
                    int n = com.example.touhou.core.EchoOfAnotherWorld.clearCooldowns();
                    sender.sendMessage(PREFIX + "\u00a7a已清空玩家级冷却（原 " + n + " 条）");
                    log("[TOUHOU] echo cooldown clear -> " + n);
                } else {
                    int cooling = com.example.touhou.core.EchoOfAnotherWorld.coolingDownCount();
                    long[] totals = com.example.touhou.core.EchoOfAnotherWorld.totals();
                    com.example.touhou.core.EchoOfAnotherWorld.ShuttleReport last =
                            com.example.touhou.core.EchoOfAnotherWorld.lastReport();
                    sender.sendMessage(PREFIX + "\u00a7e维度穿梭 · 玩家级冷却与累计");
                    sender.sendMessage("\u00a78  配置冷却 = "
                            + AddonConfig.get().echoConvertCooldownMillis + " ms"
                            + (AddonConfig.get().echoConvertCooldownMillis <= 0 ? "（已关闭）" : ""));
                    sender.sendMessage("\u00a78  此刻处于冷却中的玩家 = " + cooling);
                    sender.sendMessage("\u00a78  本次启动累计 = " + totals[0] + " 次穿梭 / "
                            + totals[1] + " 个回响");
                    sender.sendMessage("\u00a78  上一次穿梭 = "
                            + (last == null ? "(本次启动还没有)" : last.summary()));
                    sender.sendMessage("\u00a77  用 /touhou echo cooldown clear 清空冷却");
                    log("[TOUHOU] echo cooldown cooling=" + cooling
                            + " shuttles=" + totals[0] + " echoes=" + totals[1]
                            + " last=" + (last == null ? "none" : last.summary()));
                }
            }
            default -> sender.sendMessage(PREFIX
                    + "\u00a7c用法: /touhou echo [selfcheck | rule | container <x> <y> <z> |"
                    + " convert [玩家] | probe <玩家> [世界] |"
                    + " shuttle <玩家> <from> <to> [--force] | cooldown [clear]]");
        }
    }

    /**
     * 判定矩阵自测 —— <b>纯逻辑</b>，一个方块都不碰。
     *
     * <p>把"哪些组合算维度穿梭"逐条跑一遍并核对预期值。预期值写死在下面的表里
     * （{@code expect} 那一列），所以这条命令是"自测"而不是"打印当前实现"：
     * 只要 {@code EchoOfAnotherWorld#isShuttle} 的语义被改坏，这里立刻报 FAIL。
     *
     * <p>世界全部从 {@code Bukkit.getWorlds()} 里<b>现找</b>（按 Environment 挑），
     * 所以多世界服务器上用的是真实存在的世界，不需要人工造。
     * 找不到对应 Environment 的世界时那一行报"跳过"而不是 FAIL —— 那是环境限制，
     * 不是实现错了。
     */
    private void echoRule(CommandSender sender) {
        sender.sendMessage(PREFIX + "\u00a7e维度穿梭 · 判定矩阵自测");
        sender.sendMessage("\u00a78  规则：" + com.example.touhou.core.EchoOfAnotherWorld.ruleSummary());
        sender.sendMessage("\u00a78  判定只读 World#getEnvironment，不看世界名");

        org.bukkit.World normal = firstWorld(org.bukkit.World.Environment.NORMAL);
        org.bukkit.World nether = firstWorld(org.bukkit.World.Environment.NETHER);
        org.bukkit.World end = firstWorld(org.bukkit.World.Environment.THE_END);

        int pass = 0;
        int fail = 0;
        int skip = 0;

        // { 期望, from, to, 说明 }
        //   说明以 "null" 结尾的那几条是【防御性断言】：不依赖任何世界是否存在，
        //   永远会跑。其余几条在某台服务器缺少对应维度时会报"跳过"。
        Object[][] cases = new Object[][]{
                {Boolean.TRUE, normal, nether, "主世界 → 地狱"},
                {Boolean.TRUE, nether, normal, "地狱 → 主世界"},
                {Boolean.FALSE, normal, normal, "同维度（主世界 → 主世界）"},
                {Boolean.FALSE, nether, nether, "同维度（地狱 → 地狱）"},
                {Boolean.FALSE, normal, end, "主世界 → 末地（末地传送门，不算）"},
                {Boolean.FALSE, end, normal, "末地 → 主世界（不算）"},
                {Boolean.FALSE, end, end, "同维度（末地 → 末地）"},
                {Boolean.FALSE, nether, end, "地狱 → 末地（不算）"},
                {Boolean.FALSE, null, nether, "from 为 null（防御）null"},
                {Boolean.FALSE, normal, null, "to 为 null（防御）null"},
                {Boolean.FALSE, null, null, "两边都为 null（防御）null"},
        };
        for (Object[] c : cases) {
            boolean expect = (Boolean) c[0];
            org.bukkit.World from = (org.bukkit.World) c[1];
            org.bukkit.World to = (org.bukkit.World) c[2];
            String note = (String) c[3];
            // 防御性断言（两边都为 null，或只有一个世界参与）永远跑；
            // 需要两个真实世界的用例在缺维度时跳过。
            boolean pureLogic = note.endsWith("null");
            if (!pureLogic && (from == null || to == null)) {
                sender.sendMessage("\u00a78  [跳过] " + note + "（本机没有该维度）");
                skip++;
                continue;
            }
            boolean got = com.example.touhou.core.EchoOfAnotherWorld.isShuttle(from, to);
            boolean ok = got == expect;
            if (ok) {
                pass++;
            } else {
                fail++;
            }
            sender.sendMessage((ok ? "\u00a7a  [PASS] " : "\u00a7c  [FAIL] ") + note
                    + "  expect=" + expect + " got=" + got
                    + "  " + envOf(from) + " -> " + envOf(to));
        }
        org.bukkit.World custom = firstWorldOtherThan(java.util.EnumSet.of(
                org.bukkit.World.Environment.NORMAL,
                org.bukkit.World.Environment.NETHER,
                org.bukkit.World.Environment.THE_END));
        if (custom != null) {
            boolean got = com.example.touhou.core.EchoOfAnotherWorld.isShuttle(normal, custom);
            boolean ok = !got;
            if (ok) {
                pass++;
            } else {
                fail++;
            }
            sender.sendMessage((ok ? "\u00a7a  [PASS] " : "\u00a7c  [FAIL] ")
                    + "主世界 → 自定义世界(" + custom.getName() + ")"
                    + "  expect=false got=" + got);
        } else {
            sender.sendMessage("\u00a78  [跳过] 主世界 → 自定义世界（本机没有 CUSTOM 维度）");
            skip++;
        }

        sender.sendMessage(PREFIX + "\u00a7e结果： \u00a7a" + pass + " PASS\u00a7e / "
                + (fail == 0 ? "\u00a7a" : "\u00a7c") + fail + " FAIL\u00a7e / \u00a78" + skip + " 跳过");
        log("[TOUHOU] echo rule pass=" + pass + " fail=" + fail + " skip=" + skip);
    }

    /**
     * 干跑：报"这位玩家的背包里有几个能量水晶、会被转成什么"，<b>不改</b>任何东西。
     *
     * <p>与 {@link #echoShuttle} 的区别只有一条：它调用的是
     * {@code countPowerCrystals} 而不是 {@code shuttle} —— 所以它<b>不</b>会联网、
     * <b>不</b>会改写背包、<b>不</b>会碰冷却表。用来在"真的动手之前"看一眼后果。
     */
    private void echoProbe(CommandSender sender, String playerName, String worldName) {
        org.bukkit.entity.Player target = pickPlayer(playerName);
        if (target == null) {
            sender.sendMessage(PREFIX + "\u00a7c没有在线玩家"
                    + (playerName == null || playerName.isBlank() ? "" : " 名为 " + playerName)
                    + "\u00a77（无头测试服平时没有玩家；不需要玩家也能跑的那部分见 "
                    + "/touhou echo rule 与 /touhou echo container）");
            log("[TOUHOU] echo probe no-player name=" + playerName);
            return;
        }
        AddonConfig cfg = AddonConfig.get();
        org.bukkit.World from = resolveWorldByName(worldName, org.bukkit.World.Environment.NETHER);
        org.bukkit.World to = target.getWorld();
        // ★ 口径是【物品总数】（一格 64 个就计 64），不是槽位数 —— 预测值必须与
        //   实际转化用同一套算法，否则"干跑说会产 3 个、真跑出来 192 个"。
        int items = com.example.touhou.core.EchoOfAnotherWorld
                .countCrystalItems(target.getInventory());
        int perCrystal = Math.max(1, cfg.echoCrystalPerEcho);
        int perEcho = Math.max(1, cfg.echoEchoPerCrystal);
        int predict = items / perCrystal * perCrystal / perCrystal * perEcho;

        sender.sendMessage(PREFIX + "\u00a7e维度穿梭 · 干跑（不改背包）");
        sender.sendMessage("\u00a77  玩家： \u00a7f" + target.getName()
                + " \u00a77（此刻在 \u00a7f" + to.getName() + "\u00a77）");
        sender.sendMessage("\u00a77  假设穿梭： \u00a7f"
                + (from == null ? "(找不到世界)" : from.getName())
                + " \u00a78[" + envOf(from) + "] \u00a77→ \u00a7f" + to.getName()
                + " \u00a78[" + envOf(to) + "]");
        sender.sendMessage("\u00a77  会成立吗： \u00a7f"
                + (com.example.touhou.core.EchoOfAnotherWorld.isShuttle(from, to) ? "是" : "否"));
        sender.sendMessage("\u00a77  背包里的能量水晶： \u00a7f" + items + " \u00a77个（"
                + com.example.touhou.core.EchoOfAnotherWorld.describeSlots(target) + "）");
        sender.sendMessage("\u00a77  预计消耗 \u00a7f" + (items / perCrystal * perCrystal)
                + " \u00a77个 ⇒ 产出 \u00a7f" + predict + " \u00a77个另一个世界的回响"
                + " \u00a78（" + perCrystal + " 水晶 → " + perEcho + " 回响；"
                + "不足一个单位的原样留着）");
        sender.sendMessage("\u00a78  真跑请用：/touhou echo shuttle " + target.getName()
                + " " + (from == null ? "<fromWorld>" : from.getName()) + " " + to.getName());
        log("[TOUHOU] echo probe player=" + target.getName() + " items=" + items
                + " predict=" + predict
                + " from=" + (from == null ? "null" : from.getName())
                + " to=" + to.getName());
    }

    /**
     * 真的走一次"维度穿梭"的裁决与转化（作用在<b>真实在线玩家</b>身上）。
     *
     * <p>调用的是与事件路径完全同一个入口
     * （{@link com.example.touhou.core.EchoOfAnotherWorld#shuttle}），
     * 只是把 {@code from} / {@code to} 两个世界换成命令参数 —— 因为
     * {@code PlayerChangedWorldEvent} 在无头环境里跑不出来（详见最终报告）。
     *
     * @param playerName 在线玩家名（必填；没有在线玩家时这条命令如实报错）
     * @param fromName   离开的世界名（可写 overworld / nether / end 简写）
     * @param toName     到达的世界名（同上）
     * @param force      给了 {@code --force} 就忽略玩家级冷却
     */
    private void echoShuttle(CommandSender sender, String playerName, String fromName,
                             String toName, boolean force) {
        org.bukkit.entity.Player target = pickPlayer(playerName);
        if (target == null) {
            sender.sendMessage(PREFIX + "\u00a7c没有在线玩家"
                    + (playerName == null || playerName.isBlank() ? "" : " 名为 " + playerName)
                    + "\u00a77这条只能作用在真实玩家身上；不需要玩家也能跑的那部分见 "
                    + "/touhou echo rule 与 /touhou echo container");
            log("[TOUHOU] echo shuttle no-player name=" + playerName);
            return;
        }
        org.bukkit.World from = resolveWorldByName(fromName, org.bukkit.World.Environment.NETHER);
        org.bukkit.World to = resolveWorldOr(toName, target.getWorld());
        if (from == null || to == null) {
            sender.sendMessage(PREFIX + "\u00a7c世界解析失败：from=" + fromName + " to=" + toName
                    + "\u00a77（可写世界名，或 overworld / nether / end 简写）");
            return;
        }

        sender.sendMessage(PREFIX + "\u00a7e维度穿梭 · 实跑");
        sender.sendMessage("\u00a77  " + target.getName() + "： \u00a7f" + from.getName()
                + " \u00a78[" + envOf(from) + "] \u00a77→ \u00a7f" + to.getName() + " \u00a78[" + envOf(to) + "]"
                + (force ? " \u00a78（--force：忽略玩家冷却）" : ""));
        sender.sendMessage("\u00a77  转化前背包里的能量水晶： \u00a7f"
                + com.example.touhou.core.EchoOfAnotherWorld.describeSlots(target));
        com.example.touhou.core.EchoOfAnotherWorld.ShuttleReport rep =
                com.example.touhou.core.EchoOfAnotherWorld.shuttle(target, from, to, force);

        for (String line : rep.lines()) {
            sender.sendMessage("\u00a78  " + color(line));
        }
        sender.sendMessage("\u00a77  转化后背包里的能量水晶： \u00a7f"
                + com.example.touhou.core.EchoOfAnotherWorld.describeSlots(target));
        sender.sendMessage("\u00a78  提示：紧接着再敲一次同样的命令（带 --force）应当报"
                + " crystals=0 echoes=0 —— 那就是幂等");
        // ★ 纯 ASCII 数值单独一行，方便从 GBK 日志里 grep 出时间序列
        log("[TOUHOU] echo shuttle " + rep.summary() + " force=" + force);
    }

    /**
     * <b>直接清点并转化在线玩家背包</b>（<b>不</b>判维度、<b>不</b>走传送）——
     * 用来在真机上精确核对"扫哪些槽、换算对不对"。
     *
     * <pre>/touhou echo convert [玩家名]</pre>
     *
     * <p>★ 与 {@link #echoShuttle} 的区别：{@code shuttle} 是"模拟一次维度穿梭"，
     * 会先过维度判定与冷却；{@code convert} 是"把判定那一层掀掉，直接跑转化内核"，
     * 于是下面这几件事可以在<b>不切换维度</b>的前提下被逐条核对：
     * <ol>
     *   <li><b>副手算不算</b>：往副手放一个水晶，跑一次，看它是否变成回响
     *       （槽位口径里副手记作 Bukkit 槽 {@value com.example.touhou.core.EchoOfAnotherWorld#OFF_HAND_SLOT}）；</li>
     *   <li><b>盔甲槽不算</b>：水晶穿不上盔甲槽，所以这条只能靠"槽位口径只报 0..35 与 40"来核对
     *       （见 selfcheck 里那行）；</li>
     *   <li><b>换算与幂等</b>：连跑两次，第二次必须 0 / 0。</li>
     * </ol>
     *
     * <p>⚠ 它<b>不</b>碰冷却表，也<b>不</b>写 {@code lastReport} ——
     * 它只是"把转化内核拿出来单独跑一遍"的调试入口，不代表一次穿梭发生过。
     */
    private void echoConvert(CommandSender sender, String playerName) {
        org.bukkit.entity.Player target = pickPlayer(playerName);
        if (target == null) {
            sender.sendMessage(PREFIX + "\u00a7c没有在线玩家"
                    + (playerName == null || playerName.isBlank() ? "" : " 名为 " + playerName)
                    + "\u00a77（这条需要真实在线玩家）");
            log("[TOUHOU] echo convert no-player name=" + playerName);
            return;
        }
        AddonConfig cfg = AddonConfig.get();
        org.bukkit.inventory.PlayerInventory inv = target.getInventory();

        sender.sendMessage(PREFIX + "\u00a7e维度穿梭 · 直接转化（不判维度、不走传送）");
        sender.sendMessage("\u00a77  玩家： \u00a7f" + target.getName()
                + " \u00a77（" + target.getWorld().getName() + "）");
        sender.sendMessage("\u00a77  命中槽位（0..35 与副手 "
                + com.example.touhou.core.EchoOfAnotherWorld.OFF_HAND_SLOT
                + "）： \u00a7f" + com.example.touhou.core.EchoOfAnotherWorld.describeSlots(target));
        sender.sendMessage("\u00a77  物品总数： \u00a7f"
                + com.example.touhou.core.EchoOfAnotherWorld.countCrystalItems(inv));

        com.example.touhou.core.EchoOfAnotherWorld.Conversion c1 =
                com.example.touhou.core.EchoOfAnotherWorld.convertInventory(target, cfg);
        sender.sendMessage("\u00a77  第一遍： \u00a7fconverted=" + c1.converted()
                + " echoes=" + c1.echoes() + " dropped=" + c1.dropped()
                + " notGiven=" + c1.notGiven() + " firstSlot=" + c1.firstSlot());
        sender.sendMessage("\u00a77  转化后命中槽位： \u00a7f"
                + com.example.touhou.core.EchoOfAnotherWorld.describeSlots(target));
        sender.sendMessage("\u00a78  副手那格（Bukkit 槽 " + com.example.touhou.core.EchoOfAnotherWorld.OFF_HAND_SLOT
                + "）现在是 = " + describeOne(inv.getItemInOffHand()));

        com.example.touhou.core.EchoOfAnotherWorld.Conversion c2 =
                com.example.touhou.core.EchoOfAnotherWorld.convertInventory(target, cfg);
        sender.sendMessage((c2.converted() == 0 && c2.echoes() == 0
                ? "\u00a7a" : "\u00a7c") + "  第二遍（幂等）：converted=" + c2.converted()
                + " echoes=" + c2.echoes()
                + (c2.converted() == 0 && c2.echoes() == 0 ? "  ⇒ 幂等成立" : "  ⇒ ★ 竟然又转了"));
        log("[TOUHOU] echo convert player=" + target.getName()
                + " slots=" + com.example.touhou.core.EchoOfAnotherWorld.describeSlots(target)
                + " first=" + c1.converted() + "/" + c1.echoes()
                + " second=" + c2.converted() + "/" + c2.echoes());
    }

    /**
     * <b>在一个真箱子上跑一遍转化内核</b> —— 本机制"不需要玩家也能验"的那一半。
     *
     * <p>★ 为什么需要它：本机制的触发点（玩家换世界）必须有玩家，而自动验证里
     * 造不出可用玩家（CraftPlayer 的构造器要 NMS 的 {@code EntityPlayer}，
     * Paper 1.20.4 的类名是混淆过的 —— 详见最终报告）。但"判据 + 换算 + 幂等"
     * 这三件<b>最容易写错</b>的事只需要一个 {@code Inventory}
     * （见 {@link com.example.touhou.core.EchoOfAnotherWorld#convertInventory(Inventory, AddonConfig)}）。
     * 所以这条命令：
     * <ol>
     *   <li>在命令来源附近临时放一个箱子，塞进
     *       <b>N 个真能量水晶</b>（{@code /sf give} 生成的那种带 PDC 的物品）
     *       + 一个"材质相同但没有粘液 id"的干扰物
     *       + 一个普通物品；</li>
     *   <li>跑一遍转化，逐槽打印前后内容；</li>
     *   <li><b>紧接着再跑一遍</b> —— 第二次必须是"水晶 0 / 回响 0"，
     *       这才叫幂等；</li>
     *   <li>把箱子拆掉、恢复成空气，不留垃圾。</li>
     * </ol>
     *
     * <p>⚠ 它<b>不</b>验证"玩家背包那条路径"（副手槽、落到脚下）—— 那部分只能
     * 用真实在线玩家跑 {@link #echoShuttle}。报告里会写明这个边界。
     */
    private void echoContainer(CommandSender sender, String xArg, String yArg, String zArg) {
        AddonConfig cfg = AddonConfig.get();
        org.bukkit.World world;
        int x;
        int y;
        int z;
        if (sender instanceof org.bukkit.entity.Player p && xArg == null) {
            world = p.getWorld();
            x = p.getLocation().getBlockX();
            y = p.getLocation().getBlockY();
            z = p.getLocation().getBlockZ();
        } else {
            if (xArg == null || yArg == null || zArg == null) {
                sender.sendMessage(PREFIX + "\u00a7c用法: /touhou echo container <x> <y> <z>"
                        + "\u00a77（只有玩家执行时可以省略坐标）");
                return;
            }
            try {
                x = Integer.parseInt(xArg);
                y = Integer.parseInt(yArg);
                z = Integer.parseInt(zArg);
            } catch (NumberFormatException e) {
                sender.sendMessage(PREFIX + "\u00a7c坐标必须是整数");
                return;
            }
            world = sender instanceof org.bukkit.entity.Player p2
                    ? p2.getWorld() : Bukkit.getWorlds().get(0);
        }

        org.bukkit.block.Block spot = findFlatSpot(world, x, y, z);
        if (spot == null) {
            sender.sendMessage(PREFIX + "\u00a7c附近找不到可以放箱子的空位（需要 2 格空气）");
            return;
        }

        // ★ findFlatSpot 已经保证 spot 与 spot+1 都是空气，所以 chest 那一格必然是空气
        //   （这里不再写"那一格本来有方块"的分支 —— 那是永远走不到的死代码，
        //    而且它只会恢复 Material、会抹掉 BlockData）。
        org.bukkit.block.Block chest = spot.getRelative(0, 1, 0);
        chest.setType(Material.CHEST, false);

        boolean cleared = false;
        try {
            if (!(chest.getState() instanceof org.bukkit.block.Chest state)) {
                sender.sendMessage(PREFIX + "\u00a7c放下的方块不是箱子（" + chest.getType() + "）");
                return;
            }
            Inventory inv = state.getBlockInventory();
            inv.clear();
            cleared = true;

            // ① 塞测试样本：水晶 ×2 堆（3 + 64）+ 同材质没粘液 id 的干扰物 + 普通物品
            inv.setItem(2, new ItemStack(SlimefunItems.POWER_CRYSTAL.clone()));
            inv.setItem(5, new ItemStack(SlimefunItems.POWER_CRYSTAL.clone()));
            inv.getItem(2).setAmount(3);
            inv.getItem(5).setAmount(64);
            inv.setItem(8, new ItemStack(SlimefunItems.POWER_CRYSTAL.getType()));
            inv.setItem(11, new ItemStack(Material.DIAMOND, 5));

            sender.sendMessage(PREFIX + "\u00a7e维度穿梭 · 容器内核实跑 @ "
                    + world.getName() + " " + chest.getX() + " " + chest.getY() + " " + chest.getZ());
            sender.sendMessage("\u00a78  样本：槽 2 = 3 个能量水晶、槽 5 = 64 个能量水晶、"
                    + "槽 8 = 同材质但没有粘液 id 的干扰物、槽 11 = 5 个钻石");
            log("[TOUHOU] echo container @ " + world.getName() + " "
                    + chest.getX() + " " + chest.getY() + " " + chest.getZ()
                    + " crystalSlotsBefore=" + com.example.touhou.core.EchoOfAnotherWorld
                            .countPowerCrystals(inv));

            // ② 跑第一遍：默认 1:1 时应当 3 + 64 = 67 个水晶 → 67 个回响
            int itemsBefore = inv.getItem(2).getAmount() + inv.getItem(5).getAmount();
            int perCrystal = Math.max(1, cfg.echoCrystalPerEcho);
            int perEcho = Math.max(1, cfg.echoEchoPerCrystal);
            int usedExpect = itemsBefore / perCrystal * perCrystal;
            int echoExpect = usedExpect / perCrystal * perEcho;

            com.example.touhou.core.EchoOfAnotherWorld.Conversion one =
                    com.example.touhou.core.EchoOfAnotherWorld.convertInventory(inv, cfg);
            int crystalSlotsAfter1 = com.example.touhou.core.EchoOfAnotherWorld
                    .countPowerCrystals(inv);
            // ★ 不看报告、直接数【箱子里真的有几个回响】：报告自说自话也能过，
            //   只有真数物品才能证明"产出确实落地了"。
            int echoStacksAfter1 = com.example.touhou.core.EchoOfAnotherWorld
                    .countEchoStacks(inv);
            int echoItemsAfter1 = com.example.touhou.core.EchoOfAnotherWorld
                    .countEchoItems(inv);

            sender.sendMessage("\u00a77  第一遍： \u00a7fconverted=" + one.converted()
                    + " echoes=" + one.echoes() + " dropped=" + one.dropped()
                    + " notGiven=" + one.notGiven() + " firstSlot=" + one.firstSlot());
            sender.sendMessage("\u00a78    槽 2 = " + describeOne(inv.getItem(2))
                    + "  |  槽 5 = " + describeOne(inv.getItem(5))
                    + "  |  槽 8 = " + describeOne(inv.getItem(8))
                    + "  |  槽 11 = " + describeOne(inv.getItem(11)));
            sender.sendMessage("\u00a77  转化后：剩余水晶槽位 = \u00a7f" + crystalSlotsAfter1
                    + " \u00a77，箱子里真的有 \u00a7f" + echoStacksAfter1 + " \u00a77堆 / \u00a7f"
                    + echoItemsAfter1 + " \u00a77个回响");

            // ③ 再跑一遍：幂等 —— 必须是 0 / 0
            com.example.touhou.core.EchoOfAnotherWorld.Conversion two =
                    com.example.touhou.core.EchoOfAnotherWorld.convertInventory(inv, cfg);
            sender.sendMessage("\u00a77  第二遍（幂等）： \u00a7fconverted=" + two.converted()
                    + " echoes=" + two.echoes());

            // ④ 判定
            // ★ 剩余水晶数【按判据现数】，不是恒为 0：默认 1:1 时余 0；
            //   配成 2:1 时"3 个水晶"里有 1 个凑不成一单位，它必须【原样留在箱子里】
            //   —— 这正是"绝不删了不发货"要保证的行为。
            //   ⚠ 不能用"槽 2 数量 + 槽 5 数量"：转化后那两格里装的是回响。
            int crystalItemsLeft = countCrystalItemsIn(inv);
            boolean okConvert = one.converted() == usedExpect
                    && one.echoes() == echoExpect
                    && one.notGiven() == 0
                    // 没参与换算的水晶必须一个不少地留在原处
                    && crystalItemsLeft == itemsBefore - usedExpect
                    // ★ 真数物品：报告说发了 N 个，箱子里就必须真的有 N 个
                    && echoItemsAfter1 == echoExpect;
            boolean okIdempotent = two.converted() == 0 && two.echoes() == 0;
            // ★ 干扰物必须【还在】且材质没变 —— 不能用 !isPowerCrystal(...) 判，
            //   因为被删掉的 null 也满足"不是水晶"。
            ItemStack distractor = inv.getItem(8);
            boolean okPreserve = distractor != null
                    && !distractor.getType().isAir()
                    && distractor.getType() == SlimefunItems.POWER_CRYSTAL.getType()
                    && !com.example.touhou.core.EchoOfAnotherWorld.isPowerCrystal(distractor)
                    && inv.getItem(11) != null
                    && inv.getItem(11).getType() == Material.DIAMOND
                    && inv.getItem(11).getAmount() == 5;
            sender.sendMessage((okConvert ? "\u00a7a  [PASS] " : "\u00a7c  [FAIL] ")
                    + "换算正确且【产出真的落地】（样本 " + itemsBefore + " 个水晶 → 消耗 "
                    + one.converted() + " / 期望 " + usedExpect
                    + "，产出 " + echoExpect + " 个回响，箱子里实数 " + echoItemsAfter1
                    + " 个；剩下 " + crystalItemsLeft + " 个水晶原样留着"
                    + "；notGiven=" + one.notGiven() + "）");
            sender.sendMessage((okIdempotent ? "\u00a7a  [PASS] " : "\u00a7c  [FAIL] ")
                    + "幂等（第二遍 converted=" + two.converted()
                    + " echoes=" + two.echoes() + "，必须都是 0）");
            sender.sendMessage((okPreserve ? "\u00a7a  [PASS] " : "\u00a7c  [FAIL] ")
                    + "只动该动的格子（槽 8 的同材质干扰物仍在、槽 11 的钻石原样未动）");
            log("[TOUHOU] echo container itemsBefore=" + itemsBefore
                    + " converted=" + one.converted() + " echoes=" + one.echoes()
                    + " notGiven=" + one.notGiven()
                    + " crystalSlotsAfter1=" + crystalSlotsAfter1
                    + " crystalItemsLeft=" + crystalItemsLeft
                    + " echoStacksAfter1=" + echoStacksAfter1
                    + " echoItemsAfter1=" + echoItemsAfter1
                    + " secondConverted=" + two.converted() + " secondEchoes=" + two.echoes()
                    + " passConvert=" + okConvert + " passIdempotent=" + okIdempotent
                    + " passPreserve=" + okPreserve);
        } catch (RuntimeException e) {
            sender.sendMessage(PREFIX + "\u00a7c容器内核实跑异常: " + e);
            Log.severe("[TOUHOU] echo container 异常", e);
        } finally {
            // ★ 清理：清箱子、拆方块、恢复原状（绝不给世界留垃圾）
            if (cleared) {
                if (chest.getState() instanceof org.bukkit.block.Chest s2) {
                    s2.getBlockInventory().clear();
                }
            }
            // ★ 只拆我们自己放的那个方块（findFlatSpot 保证它原来是空气）
            chest.setType(Material.AIR, false);
            sender.sendMessage("\u00a78  已清理：箱子内容清空、方块已恢复成空气");
        }
    }

    /** 一行描述一个槽位内容（诊断用）。 */
    private static String describeOne(ItemStack it) {
        if (it == null || it.getType().isAir()) {
            return "(空)";
        }
        String sfId = io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getByItem(it) == null
                ? "-" : io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getByItem(it).getId();
        return it.getType() + "×" + it.getAmount() + "[sf=" + sfId + "]";
    }

    /**
     * 现数容器里还有几个<b>能量水晶</b>（物品总数，一格 64 个就计 64）。
     *
     * <p>用来核对"没参与换算的水晶是不是原样留着"。刻意不复用
     * {@code countPowerCrystals}（那是槽位口径），也不看 {@code Conversion} 的读数
     * —— 判定要独立于被检查的那个对象。
     */
    private static int countCrystalItemsIn(Inventory inv) {
        if (inv == null) {
            return 0;
        }
        int n = 0;
        for (int slot = 0; slot < inv.getSize(); slot++) {
            ItemStack it = inv.getItem(slot);
            if (com.example.touhou.core.EchoOfAnotherWorld.isPowerCrystal(it)) {
                n += it.getAmount();
            }
        }
        return n;
    }

    /**
     * 在 {@code (x,y,z)} 附近找一个"可以放箱子"的空位（该格与上一格都必须是空气）。
     *
     * <p>顺序是"先自己、再向上、再四个水平方向" —— 尽量贴着命令给的坐标，
     * 且<b>只挑空气</b>（绝不覆盖玩家已有的方块）。
     */
    private static org.bukkit.block.Block findFlatSpot(org.bukkit.World w, int x, int y, int z) {
        if (w == null) {
            return null;
        }
        int[][] offsets = {
                {0, 0, 0}, {0, 1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {0, 2, 0}, {2, 0, 0}, {-2, 0, 0}, {0, 0, 2}, {0, 0, -2}};
        for (int[] o : offsets) {
            org.bukkit.block.Block b = w.getBlockAt(x + o[0], y + o[1], z + o[2]);
            if (b.getType().isAir() && b.getRelative(0, 1, 0).getType().isAir()) {
                return b;
            }
        }
        return null;
    }

    /**
     * 按世界名 / 简写 / 默认 Environment 找一个世界。
     *
     * <p>三种写法都认：
     * <ol>
     *   <li>真实世界名（{@code world_nether}）—— 直接 {@code Bukkit.getWorld}；</li>
     *   <li>简写（{@code overworld} / {@code nether} / {@code end}，以及中文的
     *       主世界/地狱/末地）—— 按 {@link org.bukkit.World.Environment} 现找第一个；</li>
     *   <li>空 —— 用调用方给的默认 Environment。</li>
     * </ol>
     * 都找不到就返回 {@code null}，由调用方报错（绝不悄悄退回"随便一个世界"，
     * 否则"末地传送门不该转化"这类断言会被一个错误的世界名悄悄测成 PASS）。
     */
    private static org.bukkit.World resolveWorldByName(String name, org.bukkit.World.Environment fallback) {
        if (name == null || name.isBlank()) {
            return firstWorld(fallback);
        }
        org.bukkit.World exact = Bukkit.getWorld(name);
        if (exact != null) {
            return exact;
        }
        String key = name.trim().toLowerCase();
        return switch (key) {
            case "overworld", "normal", "主世界" -> firstWorld(org.bukkit.World.Environment.NORMAL);
            case "nether", "hell", "地狱", "下界" -> firstWorld(org.bukkit.World.Environment.NETHER);
            case "end", "the_end", "末地" -> firstWorld(org.bukkit.World.Environment.THE_END);
            default -> null;
        };
    }

    /** 按世界名 / 简写找；空则原样返回 {@code def}（默认值是个世界对象）。 */
    private static org.bukkit.World resolveWorldOr(String name, org.bukkit.World def) {
        if (name == null || name.isBlank()) {
            return def;
        }
        org.bukkit.World exact = Bukkit.getWorld(name);
        if (exact != null) {
            return exact;
        }
        return resolveWorldByName(name, def == null
                ? org.bukkit.World.Environment.NORMAL : def.getEnvironment());
    }

    private static org.bukkit.World firstWorld(org.bukkit.World.Environment env) {
        if (env == null) {
            return null;
        }
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            if (w.getEnvironment() == env) {
                return w;
            }
        }
        return null;
    }

    private static org.bukkit.World firstWorldOtherThan(java.util.Set<org.bukkit.World.Environment> known) {
        for (org.bukkit.World w : Bukkit.getWorlds()) {
            if (!known.contains(w.getEnvironment())) {
                return w;
            }
        }
        return null;
    }

    private static String envOf(org.bukkit.World w) {
        return w == null ? "(null)" : String.valueOf(w.getEnvironment());
    }

    // ------------------------------------------------------------------ 工具

    /** 解析方块面名（认不出 / 写了 NONE 都返回 {@code null} = "按实体处理"，方向恒向上）。 */
    private static org.bukkit.block.BlockFace parseFace(String raw) {
        if (raw == null || raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("entity")) {
            return null;
        }
        for (org.bukkit.block.BlockFace f : org.bukkit.block.BlockFace.values()) {
            if (f.name().equalsIgnoreCase(raw)) {
                return f;
            }
        }
        return null;
    }

    /**
     * 由 yaw / pitch 造方向向量（与 {@code Location#getDirection()} 同一套角度约定）。
     *
     * <p>yaw：0 = +Z(南)，顺时针为正（与 Minecraft 一致）；
     * pitch：-90 = 正上，90 = 正下（与 Minecraft 一致）。
     */
    private static org.bukkit.util.Vector fromAngles(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double cosPitch = Math.cos(pitch);
        return new org.bukkit.util.Vector(-Math.sin(yaw) * cosPitch, -Math.sin(pitch),
                Math.cos(yaw) * cosPitch);
    }

    /**
     * 挑一个在线玩家：给了名字就按名字找，否则取第一个在线的。
     *
     * <p>★ 没有在线玩家时返回 {@code null} —— 要不要造"临时发射者"由调用方决定
     * （见 {@link #ensureShooter}：只有 lily 的仿真路径才需要它）。
     */
    private static org.bukkit.entity.Player pickPlayer(String nameHint) {
        if (nameHint != null && !nameHint.isBlank()) {
            org.bukkit.entity.Player p = Bukkit.getPlayerExact(nameHint);
            if (p != null) {
                return p;
            }
        }
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            return p;
        }
        return null;
    }

    /**
     * 拿一个可用的发射者；没有在线玩家时<b>临时造一个生物</b>（默认僵尸，用完即删）。
     *
     * <p>★ 为什么要造：{@code Projectile#setShooter} 收的是 {@code ProjectileSource}，
     * <b>生物也满足</b>（{@code LivingEntity extends ProjectileSource}），
     * 而无头测试服平时没有玩家在线。造一个临时生物当发射者，
     * 就能把"不伤害发射者"这条规则也验证掉 —— 而且比玩家更省事：
     * 生物默认没有 AI、不会乱跑（本命令还会显式关掉 AI 与碰撞）。
     *
     * <p>⚠ 它<b>只</b>用于命令仿真；游戏内正常路径永远用的是真实玩家。
     *
     * @return {@code [0]=LivingEntity, [1]=Boolean 是否本次新建的}
     */
    private static Object[] ensureShooter(String nameHint, Location at) {
        org.bukkit.entity.Player p = pickPlayer(nameHint);
        if (p != null) {
            return new Object[]{p, Boolean.FALSE};
        }
        if (nameHint != null && !nameHint.isBlank()) {
            return new Object[]{null, Boolean.FALSE};   // 指名要谁却没找到：不造替代品
        }
        if (at == null || at.getWorld() == null) {
            return new Object[]{null, Boolean.FALSE};
        }
        try {
            org.bukkit.entity.Zombie mob = at.getWorld().spawn(at, org.bukkit.entity.Zombie.class,
                    z -> {
                        z.setAI(false);            // 不许乱跑：它要一直站在原点当"靶子"
                        z.setSilent(true);
                        z.setPersistent(false);    // 别进存档
                        z.setCollidable(false);    // 别把箭撞飞
                        z.setRemoveWhenFarAway(true);
                    });
            return new Object[]{mob, Boolean.TRUE};
        } catch (RuntimeException e) {
            Log.warn("[TOUHOU] 造临时发射者失败（本次仿真需要发射者）: " + e);
            return new Object[]{null, Boolean.FALSE};
        }
    }

    /** 把临时发射者从世界里删掉（只在"确认它是本次造的"时调用）。 */
    private static void removeFakeShooter(org.bukkit.entity.Entity fake) {
        if (fake == null) {
            return;
        }
        try {
            fake.remove();
        } catch (RuntimeException e) {
            Log.warn("[TOUHOU] 移除临时发射者失败: " + e);
        }
    }

    /**
     * 在指定格放一块测试方块，并记进 {@code placed} 以便事后恢复。
     *
     * <p>★ 只在原来是空气的位置放（绝不覆盖玩家的建筑），且事后一定恢复成空气。
     *
     * @return {@code true} = 真的放下了（原来是空气）；{@code false} = 那一格本来就有东西
     */
    private static boolean placeTestBlock(Location loc, Material material,
                                          Set<org.bukkit.block.Block> placed) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }
        org.bukkit.block.Block b = loc.getWorld().getBlockAt(loc);
        if (!b.getType().isAir()) {
            return false;   // 已经不是空气：不动它（可能本来就是墙，那更好）
        }
        b.setType(material, false);
        placed.add(b);
        return true;
    }

    /** 把测试方块恢复成空气（命令的最后一步，避免给世界留下垃圾）。 */
    private static void clearTestBlocks(Set<org.bukkit.block.Block> placed) {
        for (org.bukkit.block.Block b : placed) {
            if (b.getType() != Material.AIR) {
                b.setType(Material.AIR, false);
            }
        }
        placed.clear();
    }

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

    /**
     * 解析 &lt;x&gt; &lt;y&gt; &lt;z&gt;（arg0..2），<b>不</b>要求该坐标上有粘液方块数据。
     *
     * <p>{@link #resolve} 是"这里必须是一个粘液方块"的版本（诊断机器用）；
     * 而"玩家站的位置 / 取电点"本来就多半是空气或普通方块，用那个版本会被
     * 一句"该坐标上没有 Slimefun 方块数据"挡回来 —— 所以这里单独开一个宽松版。
     *
     * <p>世界的取法（与 {@code /touhou place} 同款约定，另加玩家优先）：
     * <ol>
     *   <li>第 4 个参数里写了世界名 ⇒ 用它；</li>
     *   <li>没写、而命令来源是玩家 ⇒ 用<b>玩家所在世界</b>
     *       （否则多世界服务器上，玩家敲 {@code power <x y z>} 会算到主世界去）；</li>
     *   <li>其余情况（控制台）⇒ 第一个世界。</li>
     * </ol>
     */
    private Location resolveAny(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(PREFIX + "\u00a7c需要坐标: <x> <y> <z> [world]");
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
        World world = null;
        for (int i = 3; i < args.length; i++) {
            if (args[i] == null || args[i].startsWith("--")) {
                continue;
            }
            world = Bukkit.getWorld(args[i]);
            break;
        }
        if (world == null && sender instanceof org.bukkit.entity.Player p) {
            world = p.getWorld();
        }
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            sender.sendMessage(PREFIX + "\u00a7c找不到世界");
            return null;
        }
        return new Location(world, x, y, z);
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
                    "power", "dreamcatcher", "seal", "lily", "echo", "proj", "guide"), args[0]);
        }
        if (args[0].equalsIgnoreCase("echo") && args.length == 2) {
            return filter(List.of("selfcheck", "rule", "container", "convert", "probe",
                    "shuttle", "cooldown"), args[1]);
        }
        if (args[0].equalsIgnoreCase("echo") && args.length == 3
                && args[1].equalsIgnoreCase("cooldown")) {
            return filter(List.of("clear"), args[2]);
        }
        if (args[0].equalsIgnoreCase("echo") && args.length == 4
                && args[1].equalsIgnoreCase("shuttle")) {
            return filter(List.of("overworld", "nether", "end"), args[3]);
        }
        if (args[0].equalsIgnoreCase("echo") && args.length == 5
                && args[1].equalsIgnoreCase("shuttle")) {
            return filter(List.of("--force"), args[4]);
        }
        if (args[0].equalsIgnoreCase("seal") && args.length == 2) {
            return filter(List.of("selfcheck", "probe"), args[1]);
        }
        if (args[0].equalsIgnoreCase("lily") && args.length == 2) {
            return filter(List.of("selfcheck", "dir", "beam", "fire", "laser", "impact",
                    "tracers", "cleanup"), args[1]);
        }
        if (args[0].equalsIgnoreCase("lily") && args.length == 3
                && args[1].equalsIgnoreCase("dir")) {
            return filter(Arrays.stream(org.bukkit.block.BlockFace.values())
                    .map(Enum::name).collect(Collectors.toList()), args[2]);
        }
        if (args[0].equalsIgnoreCase("lily") && args.length == 5
                && (args[1].equalsIgnoreCase("beam") || args[1].equalsIgnoreCase("laser"))) {
            return filter(Arrays.stream(org.bukkit.block.BlockFace.values())
                    .map(Enum::name).collect(Collectors.toList()), args[4]);
        }
        if (args[0].equalsIgnoreCase("guide") && args.length == 2) {
            return filter(List.of("reactor", "saizen", "echo"), args[1]);
        }
        if ((args[0].equalsIgnoreCase("proj") || args[0].equalsIgnoreCase("projection"))
                && args.length == 2) {
            return filter(List.of("list"), args[1]);
        }
        if ((args[0].equalsIgnoreCase("proj") || args[0].equalsIgnoreCase("projection"))
                && args.length == 4) {
            return filter(List.of("on", "off", "toggle", "rotate", "info", "cells", "mapping",
                    "count", "clean"), args[3]);
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
                "edit", "gui", "layout", "groups", "tags", "messages", "reload", "power",
                "dreamcatcher", "seal", "lily", "proj");
    }
}
