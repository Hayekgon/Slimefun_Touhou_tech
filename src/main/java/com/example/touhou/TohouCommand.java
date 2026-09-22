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
import org.bukkit.block.Block;
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
import java.util.Locale;
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
            case "lilywhite", "lw", "lily_white" ->
                    lilyWhite(sender, Arrays.copyOfRange(args, 1, args.length));
            case "harvest", "harvesttime" ->
                    harvest(sender, Arrays.copyOfRange(args, 1, args.length));
            case "leaves", "fallenleaves", "fallen_leaves" ->
                    leaves(sender, Arrays.copyOfRange(args, 1, args.length));
            case "acquisition", "acquire", "obtain", "huoqu" ->
                    acquisition(sender, Arrays.copyOfRange(args, 1, args.length));
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
        s.sendMessage("\u00a77/touhou lilywhite [selfcheck|name|recipe]   莉莉白：头贴图 / 粉白渐变（JSON 证据）/ 配方产出 2 个");
        s.sendMessage("\u00a77/touhou harvest [selfcheck | test <x> <y> <z> | probe <x> <y> <z> | clear <x> <y> <z> | cell <x> <y> <z> [面] | rng <x> <y> <z> | wake <x> <y> <z> [crops|empty] | cooldown [clear]]   丰收之时：范围催熟 / 骨粉行为 / 提示语验证");
        s.sendMessage("\u00a77/touhou acquisition [all|rule|<物品id>]   获取方式标注核查（所有物品统一，含 null 配方）");
        s.sendMessage("\u00a77/touhou leaves [selfcheck | tools | drop [n] | watch [n|off] | field <x> <y> <z> [n] | check <x> <y> <z> | clear <x> <y> <z>]   落叶：掉率/数量分布/工具判据/非树叶对照；watch=实机追踪（走 Log.always）");
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
     *   增强工作台口径 → 各 MultiBlockMachine 的配方表（本方法）
     * </pre>
     *
     * <p>★★ 必须扫 {@code getRecipes()}，<b>不能</b>扫 {@code getDisplayRecipes()}：
     * 本方法的上一版扫的是展示表，而运行期的 {@code MagicWorkbench} 走的是
     * "machineRecipes 为空"的那条构造器（{@code AbstractCraftingTable} 传的就是空数组），
     * 展示表里一条都没有；真正的配方表是 {@code addRecipe} 一条条攒出来的
     * （见 {@code RecipeType#register} → {@code MultiBlockMachine#addRecipe}）。
     * 实测症状：莉莉白的配方明明登记成功
     * （{@code recipes[148]=输入, recipes[149]=产出}），这个计数却报 0
     * —— 那是计数口径错，不是配方没落上。
     *
     * <p>配方表是"输入, 输出, 输入, 输出…"的扁平表，所以产物落在<b>奇数下标</b>；
     * 每个元素是一个长度 1 的数组（{@code addRecipe} 的写法）。
     */
    private static int countMachineRecipesFor(
            io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem item) {
        int count = 0;
        for (io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem machine
                : Slimefun.getRegistry().getAllSlimefunItems()) {
            if (!(machine instanceof io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine mbm)) {
                continue;
            }
            List<ItemStack[]> recipes = mbm.getRecipes();
            if (recipes == null) {
                continue;
            }
            for (int i = 1; i < recipes.size(); i += 2) {
                ItemStack[] holder = recipes.get(i);
                if (holder == null || holder.length == 0) {
                    continue;
                }
                ItemStack out = holder[0];
                if (out != null && item.isItem(out)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * <b>自动合成机路径的真·端到端读数</b>。
     *
     * <p>《自动合成机》家族的配方对象是 {@code SlimefunItemRecipe}，它构造时调
     * {@code super(getInputs(item), item.getRecipeOutput())} —— 也就是说
     * <b>它吐出来的产物就是 {@code getRecipeOutput()}</b>。
     * 这里用本家的公开工厂 {@code AbstractRecipe.of(SlimefunItem, RecipeType)}
     * 把那份配方对象造出来，
     * 再读它的 {@code getResult()}，于是"自动合成机会吐几个"是<b>读出来的</b>，不是推断的。
     *
     * <p>★ 为什么要反射：{@code SlimefunItemRecipe} 是包私有类，
     * 但它的父类 {@code AbstractRecipe} 与 {@code of(SlimefunItem, RecipeType)} 都是 public，
     * 所以只需 {@code getResult()} 那一步反射。拿不到时如实报，不抛（这是诊断命令）。
     *
     * <p>⚠ 运行期（Slimefun 2026.07）的工厂是 <b>两参数</b>
     * {@code of(SlimefunItem, RecipeType)}；反编译的 2025.1 源码里是单参数
     * {@code of(SlimefunItem)}。以运行期为准 —— 这正是"以运行期 jar 为准"的又一个实例
     * （第一次实测就是按单参数反射，直接吃了个 NoSuchMethodException）。
     */
    private static String autocrafterResult(
            io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem item) {
        try {
            Class<?> factory = Class.forName(
                    "io.github.thebusybiscuit.slimefun4.implementation.items.autocrafters.AbstractRecipe");
            java.lang.reflect.Method of = factory.getMethod("of",
                    io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.class,
                    io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType.class);
            Object recipe = of.invoke(null, item, item.getRecipeType());
            if (recipe == null) {
                return "AbstractRecipe.of() 返回 null（该物品不被自动合成机支持）";
            }
            java.lang.reflect.Method getResult = factory.getMethod("getResult");
            ItemStack result = (ItemStack) getResult.invoke(recipe);
            if (result == null) {
                return "配方对象 = " + recipe.getClass().getName() + "，getResult() = null";
            }
            return "配方对象 = " + recipe.getClass().getSimpleName()
                    + "   产物 = " + result.getType() + " x" + result.getAmount()
                    + "  粘液id=" + idOf(result)
                    + "  ⇒ " + (result.getAmount() == AddSlimefunItems.LILY_WHITE_OUTPUT_AMOUNT
                            ? "\u00a7a会吐 " + result.getAmount() + " 个" : "\u00a7c数量不符");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return "(自动合成机路径读取失败: " + e + ")";
        }
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

    // ------------------------------------------------------------------ lilywhite（莉莉白）

    /**
     * 「莉莉白」的无头验证入口。
     *
     * <pre>
     *   /touhou lilywhite            全部打印（默认）
     *   /touhou lilywhite selfcheck  只看物品本身：id / 材质 / 头贴图 / 名·描述
     *   /touhou lilywhite name       只看显示名与描述第一行的【渐变】证据
     *   /touhou lilywhite recipe     只看配方：类型 key / 产出数量 / 9 格内容 / 可合成性核查
     * </pre>
     *
     * <p>★ 这个命令要证明三件事，每一件都有<b>两个独立读数</b>互相印证：
     * <ol>
     *   <li><b>头贴图</b>：物品材质是 {@code PLAYER_HEAD}，
     *       且 {@code getSkullTexture()} 与用户给的那串 Value 逐字符相等；</li>
     *   <li><b>粉白渐变</b>：把显示名序列化成 <b>JSON 组件</b>打出来
     *       （{@code ItemMeta#displayName()} 是 adventure 的 {@code Component}），
     *       里面必须是<b>逐字符不同的</b> {@code #rrggbb} 颜色；
     *       同时打出"每个字符 + 它前面的颜色码"的逐字符清单，
     *       证明 {@code §x} 序列是<b>真的穿上去了</b>、而不是被当字面量留在字符串里；</li>
     *   <li><b>产出 2 个</b>：分别读<b>两条消费路径</b>各自看到的数量 ——
     *       ① {@code SlimefunItem#getRecipeOutput()#getAmount()}（指南页产物格 / 自动合成机读它）
     *       ② {@code MultiBlockMachine#getRecipes()} 里那条记录的 output（合成表实际执行的那个）
     *       并且再读一次<b>模板本身</b>的数量，证明它仍然是 1（没污染模板、
     *       也就没踩 {@code SlimefunItem#onEnable} 的 "illegal stack size" 告警）。</li>
     * </ol>
     *
     * <p>输出走 {@link Log#command}（不受 {@code logging.console-info} 影响），
     * 于是"跑一次服务端 + 从 stdin 敲一条命令"就能拿到全部证据。
     */
    private void lilyWhite(CommandSender sender, String[] args) {
        SlimefunItem item = AddSlimefunItems.LILY_WHITE;
        if (item == null) {
            sender.sendMessage(PREFIX + "\u00a7c莉莉白未注册（物品注册失败？看控制台）");
            return;
        }
        String sub = args.length >= 1 ? args[0].toLowerCase() : "all";
        boolean all = sub.equals("all") || sub.equals("check");
        boolean selfcheck = all || sub.equals("selfcheck");
        boolean name = all || sub.equals("name") || sub.equals("gradient");
        boolean recipe = all || sub.equals("recipe") || sub.equals("craft");
        if (!selfcheck && !name && !recipe) {
            sender.sendMessage(PREFIX
                    + "\u00a7c用法: /touhou lilywhite [selfcheck|name|recipe]");
            return;
        }

        if (selfcheck) {
            guideLine(sender, PREFIX + "\u00a7e莉莉白 · 物品自检");
            guideLine(sender, "\u00a78  id = " + item.getId());
            ItemStack icon = item.getItem();
            guideLine(sender, "\u00a78  材质 = " + (icon == null ? "(null)" : String.valueOf(icon.getType()))
                    + "（应为 PLAYER_HEAD）");
            // 头贴图：从物品模板读回来的那串，与用户给定值逐字符比对
            String expect = AddItems.LILY_WHITE_TEXTURE;
            String actual = null;
            if (AddItems.LILY_WHITE != null) {
                actual = AddItems.LILY_WHITE.getSkullTexture().orElse(null);
            }
            guideLine(sender, "\u00a78  getSkullTexture() = "
                    + (actual == null ? "\u00a7c(null —— 这个材质不是头颅，或贴图没写进去）" : actual));
            guideLine(sender, "\u00a78  用户给定的 Value  = " + expect);
            guideLine(sender, "\u00a78  两者相等 = "
                    + (expect.equals(actual) ? "\u00a7a是" : "\u00a7c否"));
            guideLine(sender, "\u00a78  物品组 = " + (item.getItemGroup() == null
                    ? "(null)" : item.getItemGroup().getKey().toString()));
            guideLine(sender, "\u00a78  在 Slimefun 注册表里（/sf give 能不能拿到） = "
                    + (SlimefunItem.getById(item.getId()) == item
                            ? "\u00a7a能" : "\u00a7c查不到"));
            // 模板数量必须还是 1：这是"没污染模板"的直接读数
            guideLine(sender, "\u00a78  模板 getAmount() = "
                    + (AddItems.LILY_WHITE == null ? "(null)" : AddItems.LILY_WHITE.getAmount())
                    + "（应为 1 —— 改了它会连累 /sf give 与指南页图标）");
            log("[TOUHOU] lilywhite selfcheck id=" + item.getId()
                    + " material=" + (icon == null ? "null" : icon.getType())
                    + " skullMatch=" + expect.equals(actual)
                    + " templateAmount="
                    + (AddItems.LILY_WHITE == null ? -1 : AddItems.LILY_WHITE.getAmount()));
        }

        if (name) {
            ItemMeta meta = item.getItem() == null ? null : item.getItem().getItemMeta();
            guideLine(sender, PREFIX + "\u00a7e莉莉白 · 显示名 / 描述（渐变证据）");
            if (meta == null) {
                guideLine(sender, "\u00a7c  拿不到 ItemMeta");
            } else {
                printDisplayNameEvidence(sender, "显示名", meta);
                // 描述第一行也应当是渐变；第二行是普通灰色
                List<String> lore = meta.getLore();
                if (lore == null || lore.isEmpty()) {
                    guideLine(sender, "\u00a78  (没有 lore)");
                } else {
                    for (int i = 0; i < lore.size(); i++) {
                        String line = lore.get(i);
                        String raw = line == null ? "" : line.replace("\u00a7", "\\u00a7");
                        guideLine(sender, "\u00a77  lore[" + i + "] 原样 = " + raw);
                        // 逐字符颜色清单只对第一行（渐变行）打，第二行是普通灰、不用刷屏
                        if (i == 1) {
                            for (String cl : colorPerChar(line)) {
                                guideLine(sender, "\u00a78    " + cl);
                            }
                        }
                    }
                }
            }
            // ★ 这里刻意【不】再打一遍 displayNameJson：
            //   上面 guideLine 已经把完整 JSON 打出来了，日志里同一份长串出现两次
            //   只会让 grep 更难读。这一行只留"有没有名字 + 几段渐变色"这种短读数，
            //   要完整 JSON 就往上翻那一行。
            log("[TOUHOU] lilywhite name hasDisplayName="
                    + (meta != null && meta.hasDisplayName())
                    + " gradientHexCount="
                    + (meta == null ? 0 : hexSequenceCount(meta.getDisplayName())));
        }

        if (recipe) {
            guideLine(sender, PREFIX + "\u00a7e莉莉白 · 配方（魔法工作台，产出 2 个）");
            guideLine(sender, "\u00a78  配方类型 = " + (item.getRecipeType() == null
                    ? "(null)" : item.getRecipeType().getKey().toString())
                    + "   指向的机器 = " + (item.getRecipeType() == null
                            || item.getRecipeType().getMachine() == null
                                    ? "(无)" : item.getRecipeType().getMachine().getId()));
            guideLine(sender, "\u00a78  期望产出数量（需求）= "
                    + AddSlimefunItems.LILY_WHITE_OUTPUT_AMOUNT);

            // ---- ① SlimefunItem#getRecipeOutput()：指南页产物格 / 自动合成机读的就是它
            ItemStack declared = item.getRecipeOutput();
            int declaredAmount = declared == null ? -1 : declared.getAmount();
            guideLine(sender, "\u00a78  [路径①] SlimefunItem.getRecipeOutput().getAmount() = "
                    + declaredAmount + "  期望 "
                    + AddSlimefunItems.LILY_WHITE_OUTPUT_AMOUNT + " ⇒ "
                    + (declaredAmount == AddSlimefunItems.LILY_WHITE_OUTPUT_AMOUNT
                            ? "\u00a7a符合" : "\u00a7c不符")
                    + "\u00a78（指南页产物格 / 自动合成机读这一条）");

            // ---- ② 魔法工作台配方表里那条记录：合成时实际执行的那个
            io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine workbench =
                    findMagicWorkbench();
            ItemStack tableOutput = workbench == null
                    ? null : findRecipeOutput(workbench, item.getRecipe());
            int tableAmount = tableOutput == null ? -1 : tableOutput.getAmount();
            guideLine(sender, "\u00a78  [路径②] 魔法工作台配方表里那条 output.getAmount() = "
                    + tableAmount + "  期望 "
                    + AddSlimefunItems.LILY_WHITE_OUTPUT_AMOUNT + " ⇒ "
                    + (tableAmount == AddSlimefunItems.LILY_WHITE_OUTPUT_AMOUNT
                            ? "\u00a7a符合" : "\u00a7c不符")
                    + "\u00a78（合成表实际执行这一条）");
            guideLine(sender, "\u00a78  魔法工作台配方总数（含本体自带）= "
                    + (workbench == null ? "(找不到魔法工作台)" : workbench.getRecipes().size() / 2 + " 条"));

            // ---- ③ 模板数量：必须还是 1
            int templateAmount = AddItems.LILY_WHITE == null ? -1 : AddItems.LILY_WHITE.getAmount();
            guideLine(sender, "\u00a78  [模板] AddItems.LILY_WHITE.getAmount() = "
                    + templateAmount + "  期望 1 ⇒ "
                    + (templateAmount == 1 ? "\u00a7a符合（没污染模板）" : "\u00a7c不符"));

            // ---- 配方 9 格
            guideLine(sender, "\u00a7e  -- 配方 9 格 --");
            ItemStack[] grid = item.getRecipe();
            for (int i = 0; i < (grid == null ? 0 : grid.length); i++) {
                ItemStack cell = grid[i];
                guideLine(sender, "\u00a78    [" + i + "] = "
                        + (cell == null ? "(空)"
                                : cell.getType() + " x" + cell.getAmount()
                                        + "  粘液id=" + idOf(cell)
                                        + "  名=" + com.example.touhou.core.RecipePages.labelOf(cell)));
            }

            // ---- 可合成性核查（两面口径）
            guideLine(sender, "\u00a7e  -- 可合成性核查（这里【应该】都 > 0）--");
            guideLine(sender, "\u00a78    Bukkit 配方表里能产出它的 = " + countRecipesFor(item)
                    + " 条（原版工作台口径，本物品走粘液多方块、应为 0）");
            guideLine(sender, "\u00a78    Slimefun 多方块机器配方表里能产出它的 = " + countMachineRecipesFor(item)
                    + " 条（增强工作台 / 魔法工作台口径，应为 1）");
            log("[TOUHOU] lilywhite recipe type="
                    + (item.getRecipeType() == null ? "null" : item.getRecipeType().getKey())
                    + " declaredOutput=" + declaredAmount
                    + " machineRecipeTable=" + tableAmount
                    + " templateAmount=" + templateAmount
                    + " machineRecipes=" + countMachineRecipesFor(item));

            // ---- 诊断：把魔法工作台配方表里所有"输出是莉莉白"的条目原样打出来。
            //      ★ 必须扫 getRecipes()（真表），不能扫 getDisplayRecipes()：
            //      运行期的 MagicWorkbench 走 4 参数构造器（machineRecipes 为空），
            //      本体的展示表里一条都没有；真正的配方表是 addRecipe 一条条攒出来的。
            if (workbench != null) {
                List<ItemStack> display = workbench.getDisplayRecipes();
                List<ItemStack[]> raw = workbench.getRecipes();
                guideLine(sender, "\u00a78  [诊断] 魔法工作台 displayRecipes 大小 = " + display.size()
                        + " / recipes 大小 = " + raw.size());
                int foundRaw = 0;
                for (int i = 0; i < raw.size(); i++) {
                    ItemStack[] holder = raw.get(i);
                    if (holder != null && holder.length > 0 && holder[0] != null
                            && item.isItem(holder[0])) {
                        guideLine(sender, "\u00a78    recipes[" + i + "] = " + holder[0].getType()
                                + " x" + holder[0].getAmount() + "  粘液id=" + idOf(holder[0])
                                + "  \u21d0 是莉莉白（" + (i % 2 == 1 ? "奇数下标＝产物位" : "偶数下标＝输入位") + "）");
                        foundRaw++;
                    }
                }
                guideLine(sender, "\u00a78    recipes 里莉莉白条目数 = " + foundRaw);
                // 再直说一句：isItem 判据本身有没有问题（排除"数不出来"是判据的锅）
                guideLine(sender, "\u00a78    isItem(模板自己) = "
                        + item.isItem(AddItems.LILY_WHITE)
                        + "   isItem(getRecipeOutput()) = "
                        + (declared == null ? "(null)" : String.valueOf(item.isItem(declared))));
            }

            // ---- ④ 自动合成机路径的【真·端到端】证据
            //      《自动合成机》（AutoCrafter 家族）用的就是 SlimefunItemRecipe，
            //      而它的产物来自 AbstractRecipe#getResult() —— 正是 getRecipeOutput()。
            //      所以把那份配方对象造出来读它的产物，等于把"自动合成机实际会吐几个"
            //      直接读出来了（不是推断）。AbstractRecipe 是 public，of(SlimefunItem)
            //      也是 public；SlimefunItemRecipe 包私有，用反射拿 getResult()。
            guideLine(sender, "\u00a7e  -- 自动合成机路径（本家 AutoCrafter 读的就是这一条）--");
            guideLine(sender, "\u00a78    " + autocrafterResult(item));
        }
    }

    /**
     * 打印显示名的<b>渐变证据</b>：JSON 序列化 + 逐字符颜色清单。
     *
     * <p>★ 为什么用 JSON 组件：{@code ItemMeta#displayName()} 返回的是 Paper 的
     * adventure {@code Component}，{@code toString()} 就是 JSON。
     * 于是"渐变有没有真穿上"不再靠肉眼看 tooltip —— JSON 里每个字符都会带自己的
     * {@code color} 字段，逐字不同就是渐变、整行同色就是没渐变。
     *
     * <p>★ 逐字符清单是第二重证据：它直接看<b>底层字符串</b>，
     * 能区分"渐变生效"与"§x 被当字面量留在名字里"（后者会看到字面的 'x' 字符带上色）。
     */
    private void printDisplayNameEvidence(CommandSender sender, String label, ItemMeta meta) {
        if (!meta.hasDisplayName()) {
            guideLine(sender, "\u00a7c  " + label + "：没有 displayName");
            return;
        }
        guideLine(sender, "\u00a7e  -- " + label + " --");
        guideLine(sender, "\u00a77    JSON 序列化 = " + displayNameJson(meta));
        guideLine(sender, "\u00a77    原样字符串（§ 显示为 \\u00a7）= "
                + (meta.getDisplayName() == null
                        ? "(null)" : meta.getDisplayName().replace("\u00a7", "\\u00a7")));
        for (String line : colorPerChar(meta.getDisplayName())) {
            guideLine(sender, "\u00a78    " + line);
        }
    }

    /** 取显示名的 adventure 组件 JSON（Paper 的 {@code ItemMeta#displayName()}）。 */
    private static String displayNameJson(ItemMeta meta) {
        if (meta == null || !meta.hasDisplayName()) {
            return "(无)";
        }
        try {
            net.kyori.adventure.text.Component component = meta.displayName();
            if (component == null) {
                return "(adventure 组件为 null)";
            }
            return component.toString();
        } catch (RuntimeException | LinkageError e) {
            // 旧 API 或序列化器缺失时如实报，不抛（这是条诊断命令）
            return "(adventure 序列化失败: " + e + ")；legacy 文本 = "
                    + (meta.getDisplayName() == null ? "(null)" : meta.getDisplayName());
        }
    }

    /**
     * 把一段带 {@code §} 颜色码的文本拆成"每个字符 + 它生效的颜色"清单。
     *
     * <p>颜色码本身不显示（它们不是可见字符）；本方法把它们翻译成
     * {@code #RRGGBB} 这样的<b>纯 ASCII 可读记号</b>，附在紧随其后的那个字符前面。
     * 于是"是不是逐字符换色"一眼可见 —— 而且输出里不含 {@code §} 这种难伺候的字符，
     * 无论日志走 UTF-8 还是 GBK 都不会看不清。
     *
     * <p>★ 十六进制序列必须当<b>一个整体</b>消费：{@code §x} 之后紧跟着 6 组
     * {@code §R§R§G§G§B§B}（共 12 个字符）。早先这里只跳过了 {@code §x} 两个字符，
     * 后面那 6 组就被当成了"独立的普通颜色码"，于是同一个字符被报了 7 遍、
     * 字符数也数多了（实测症状：3 个字的显示名报成 10 个字符）。
     * 现在显式识别并整段消费。
     */
    private static List<String> colorPerChar(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        // 本字符之前累积的颜色记号（纯 ASCII：#RRGGBB；普通颜色码则原样记成 &a 这类）
        StringBuilder pending = new StringBuilder();
        int visible = 0;
        int hexSeen = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\u00a7' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if ((code == 'x' || code == 'X') && i + 13 < text.length()) {
                    // §x 后面固定跟 6 组 §RGB：把每组里的十六进制位拼成 #RRGGBB
                    StringBuilder digits = new StringBuilder(6);
                    for (int k = 0; k < 6; k++) {
                        int at = i + 2 + k * 2;
                        digits.append(text.charAt(at + 1));
                    }
                    pending.append('#').append(digits);
                    hexSeen++;
                    i += 14;
                    continue;
                }
                pending.append('&').append(code);
                i += 2;
                continue;
            }
            visible++;
            out.add("'" + c + "' 颜色=" + (pending.length() == 0
                    ? "(无，继承上一个)" : pending.toString()));
            pending.setLength(0);
            i++;
        }
        out.add(0, "可见字符数=" + visible + "  十六进制颜色序列个数=" + hexSeen);
        return out;
    }

    /** 数一段文本里有几个 {@code §x§R§R§G§G§B§B} 十六进制颜色序列（供一行短日志用）。 */
    private static int hexSequenceCount(String text) {
        if (text == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i + 1 < text.length(); i++) {
            if (text.charAt(i) == '\u00a7'
                    && (text.charAt(i + 1) == 'x' || text.charAt(i + 1) == 'X')) {
                count++;
            }
        }
        return count;
    }

    /** 取物品的粘液 id（不是粘液物品就返回 "(无)"）。 */
    private static String idOf(ItemStack stack) {
        try {
            SlimefunItem sf = SlimefunItem.getByItem(stack);
            return sf == null ? "(无)" : sf.getId();
        } catch (RuntimeException e) {
            return "(读取失败)";
        }
    }

    /** 找运行期的魔法工作台本体（不是 Touhou 的物品，是 Slimefun 本体那台多方块机器）。 */
    private static io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine findMagicWorkbench() {
        SlimefunItem machine = SlimefunItem.getById("MAGIC_WORKBENCH");
        return machine instanceof io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine mbm
                ? mbm : null;
    }

    /**
     * 在魔法工作台的配方表里找"输入数组 == 给定 9 格图案"的那条，返回它的产物。
     *
     * <p>★ 配方表是 {@code [输入, 输出, 输入, 输出, …]} 的扁平表
     * （见 {@code MultiBlockMachine#addRecipe} 与 {@code RecipeType#getRecipeOutputList}），
     * 所以按 {@code indexOf} 找到输入之后，<b>下一个</b>元素就是产物。
     * 这里用 {@code ItemStack#equals} 比数组内容 —— 配方表里存的正是我们传进去的那个数组。
     */
    private static ItemStack findRecipeOutput(
            io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine machine,
            ItemStack[] recipe) {
        if (machine == null || recipe == null) {
            return null;
        }
        List<ItemStack[]> recipes = machine.getRecipes();
        int index = recipes.indexOf(recipe);
        if (index < 0 || index + 1 >= recipes.size()) {
            return null;
        }
        ItemStack[] holders = recipes.get(index + 1);
        return holders != null && holders.length > 0 ? holders[0] : null;
    }

    // ------------------------------------------------------------------ harvest（丰收之时）

    /**
     * 「丰收之时」的无头验证入口。
     *
     * <pre>
     *   /touhou harvest                        物品自检（id / 材质 / 光效 / 名与描述的渐变 / 配方产出 1 个）
     *   /touhou harvest test &lt;x&gt; &lt;y&gt; &lt;z&gt;         造一小块测试田 → 跑催熟 → 打印前后对照 → 清掉测试田
     *   /touhou harvest probe &lt;x&gt; &lt;y&gt; &lt;z&gt;        同上，但<b>保留</b>测试田（人工进游戏看效果用）
     *   /touhou harvest clear &lt;x&gt; &lt;y&gt; &lt;z&gt;        只清测试田
     *   /touhou harvest cell &lt;x&gt; &lt;y&gt; &lt;z&gt; [面]    只对<b>某一格</b>试一次骨粉，并打印结果与当前 age
     *   /touhou harvest cooldown [clear]       读 / 清每方块冷却表
     * </pre>
     *
     * <p>★ 为什么这段值得存在：本机器的行为<b>全在方块世界里</b>（哪些格被催熟了、瓜有没有结出来），
     * 而这些恰恰是控制台"看不见"的东西。{@code test} 把一块标准测试田摆出来、
     * 调<b>与右键完全相同</b>的 {@link HarvestTime#harvest} 内核、再逐格打印 age 前后值，
     * 于是"到底催熟了哪几格、瓜结在哪"就成了可 grep 的文本证据。
     *
     * <p>★ 测试田会<b>用完即清</b>（{@code test} 分支），世界不会被改乱；
     * 想人工进游戏目视检查时用 {@code probe}（保留测试田）。
     */
    private void harvest(CommandSender sender, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase() : "selfcheck";

        if (sub.equals("selfcheck") || sub.equals("all") || sub.equals("check")) {
            harvestSelfCheck(sender);
            if (sub.equals("selfcheck") || sub.equals("check")) {
                return;
            }
        }
        if (sub.equals("cooldown") || sub.equals("cd")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("clear")) {
                int n = com.example.touhou.core.HarvestTime.clearCooldowns();
                guideLine(sender, PREFIX + "\u00a7a已清空每方块冷却（原 " + n + " 条）");
                log("[TOUHOU] harvest cooldown clear -> " + n);
            } else {
                guideLine(sender, PREFIX + "\u00a7e丰收之时 · 冷却表");
                guideLine(sender, "\u00a78  配置冷却 = " + AddonConfig.get().harvestCooldownMillis + " ms"
                        + (AddonConfig.get().harvestCooldownMillis <= 0 ? "（已关闭）" : ""));
                guideLine(sender, "\u00a78  此刻处于冷却中的方块数 = "
                        + com.example.touhou.core.HarvestTime.coolingCount());
            }
            return;
        }
        if (sub.equals("cell") || sub.equals("one")) {
            harvestCell(sender, args);
            return;
        }
        if (sub.equals("rng") || sub.equals("probe-bone") || sub.equals("bonemeal")) {
            harvestRng(sender, args);
            return;
        }
        if (sub.equals("wake") || sub.equals("message") || sub.equals("wakemsg")) {
            harvestWake(sender, args);
            return;
        }
        if (sub.equals("clear") || sub.equals("clean")) {
            Location loc = resolveAny(sender, Arrays.copyOfRange(args, 1, args.length));
            if (loc == null) {
                return;
            }
            int n = clearTestFarm(loc.getBlock(), true);
            guideLine(sender, PREFIX + "\u00a7a已清掉测试田 " + n + " 格 @ " + xyz(loc));
            return;
        }
        if (sub.equals("test") || sub.equals("probe") || sub.equals("run")) {
            boolean keep = sub.equals("probe");
            Location loc = resolveAny(sender, Arrays.copyOfRange(args, 1, args.length));
            if (loc == null) {
                return;
            }
            harvestTest(sender, loc, keep);
            return;
        }
        sender.sendMessage(PREFIX + "\u00a7c用法: /touhou harvest [selfcheck |"
                + " test <x> <y> <z> | probe <x> <y> <z> | clear <x> <y> <z> |"
                + " cell <x> <y> <z> [面] | cooldown [clear]]");
    }

    /** 物品自检：id / 材质 / 光效 / 渐变（JSON + 逐字符）/ 配方与产出。 */
    private void harvestSelfCheck(CommandSender sender) {
        SlimefunItem item = com.example.touhou.core.HarvestTime.find();
        guideLine(sender, PREFIX + "\u00a7e丰收之时 · 物品自检");
        if (item == null) {
            guideLine(sender, "\u00a7c  未注册（Slimefun 注册表里查不到 "
                    + com.example.touhou.core.HarvestTime.ID + "）");
            return;
        }
        guideLine(sender, "\u00a78  id = " + item.getId()
                + "   类 = " + item.getClass().getSimpleName());
        ItemStack icon = item.getItem();
        guideLine(sender, "\u00a78  材质 = " + (icon == null ? "(null)" : String.valueOf(icon.getType()))
                + "（应为 HAY_BLOCK）");
        guideLine(sender, "\u00a78  物品组 = " + (item.getItemGroup() == null
                ? "(null)" : item.getItemGroup().getKey().toString()));
        // 附魔光效：挂了一个 HIDE_ENCHANTS 的假附魔
        ItemMeta meta = icon == null ? null : icon.getItemMeta();
        if (meta != null) {
            guideLine(sender, "\u00a78  附魔光效 = 附魔数 " + meta.getEnchants().size()
                    + "，HIDE_ENCHANTS=" + meta.hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS));
            printDisplayNameEvidence(sender, "显示名（橙黄渐变）", meta);
            List<String> lore = meta.getLore();
            if (lore != null) {
                for (int i = 0; i < lore.size(); i++) {
                    String line = lore.get(i);
                    guideLine(sender, "\u00a77  lore[" + i + "] 原样 = "
                            + (line == null ? "" : line.replace("\u00a7", "\\u00a7")));
                    if (i == 1) {
                        for (String cl : colorPerChar(line)) {
                            guideLine(sender, "\u00a78    " + cl);
                        }
                    }
                }
            }
        }
        // 配方：类型 / 9 格 / 产出必须恰好 1
        guideLine(sender, "\u00a7e  -- 配方 --");
        guideLine(sender, "\u00a78  配方类型 = " + (item.getRecipeType() == null
                ? "(null)" : item.getRecipeType().getKey().toString())
                + "   指向的机器 = " + (item.getRecipeType() == null
                        || item.getRecipeType().getMachine() == null
                                ? "(无)" : item.getRecipeType().getMachine().getId()));
        ItemStack declared = item.getRecipeOutput();
        int amount = declared == null ? -1 : declared.getAmount();
        guideLine(sender, "\u00a78  getRecipeOutput().getAmount() = " + amount + "  期望 1 ⇒ "
                + (amount == 1 ? "\u00a7a符合（4 参构造器，没有 recipeOutput）" : "\u00a7c不符")
                + "\u00a78（指南页产物格 / 自动合成机读这一条）");
        guideLine(sender, "\u00a78  模板 getAmount() = "
                + (AddItems.HARVEST_TIME == null ? "(null)" : AddItems.HARVEST_TIME.getAmount())
                + "（应为 1）");
        ItemStack[] grid = item.getRecipe();
        for (int i = 0; i < (grid == null ? 0 : grid.length); i++) {
            ItemStack cell = grid[i];
            guideLine(sender, "\u00a78    [" + i + "] = " + (cell == null ? "(空)"
                    : cell.getType() + " x" + cell.getAmount()
                            + "  粘液id=" + idOf(cell)
                            + "  名=" + com.example.touhou.core.RecipePages.labelOf(cell)));
        }
        guideLine(sender, "\u00a78  Slimefun 多方块机器配方表里能产出它的 = "
                + countMachineRecipesFor(item) + " 条（应为 1）");
        guideLine(sender, "\u00a78  范围 = 水平 ±" + com.example.touhou.core.HarvestTime.HORIZONTAL_RADIUS
                + "（长 9 / 宽 9） 垂直 ±" + com.example.touhou.core.HarvestTime.VERTICAL_RADIUS
                + "（高 3，y-1..y+1）");
        log("[TOUHOU] harvest selfcheck id=" + item.getId()
                + " material=" + (icon == null ? "null" : icon.getType())
                + " declaredOutput=" + amount
                + " templateAmount="
                + (AddItems.HARVEST_TIME == null ? -1 : AddItems.HARVEST_TIME.getAmount())
                + " gradientHexCount="
                + (meta == null ? 0 : hexSequenceCount(meta.getDisplayName())));
    }

    /**
     * <b>核心验证</b>：在 {@code center} 造一块标准测试田 → 跑一次催熟 → 逐格打印前后对照。
     *
     * <p>测试田布局（全部落在被扫描的 9×9×3 范围内）：
     * <pre>
     *   y-1   : 整层耕地（供水由机器那一格的正下方那格耕地承载，够骨粉判定用）
     *   y     : 机器自己（HAY_BLOCK + 粘液方块数据）
     *   y+1   : 各类作物 / 树苗 / 竹子 / 西瓜茎 / 南瓜茎（+ 各茎旁边留一格空气给结果）
     *   y+2   : 只为竹子准备（竹子会被催高，长出来的高度也在范围内的 y+1）
     * </pre>
     *
     * <p>每格都会打印「催熟前 → 催熟后」的方块类型与 age，
     * 于是"哪些真的跳了 age""瓜结在哪"都能逐格核对，而不是只看一个总数。
     */
    private void harvestTest(CommandSender sender, Location center, boolean keep) {
        SlimefunItem item = com.example.touhou.core.HarvestTime.find();
        if (item == null) {
            sender.sendMessage(PREFIX + "\u00a7c丰收之时未注册");
            return;
        }
        Block machine = center.getBlock();

        guideLine(sender, PREFIX + "\u00a7e丰收之时 · 测试田催熟验证 @ " + xyz(center)
                + (keep ? "\u00a77（probe：测试田保留）" : "\u00a77（test：结束后清除）"));

        // ---- ① 先清场再铺田（幂等：重复跑不会叠出怪东西）
        clearTestFarm(machine, false);
        List<String> planted = buildTestFarm(machine);
        guideLine(sender, "\u00a7e  -- 测试田（" + planted.size() + " 个可催熟目标）--");
        for (String line : planted) {
            guideLine(sender, "\u00a78    " + line);
        }

        // ---- ② 范围自检：边界必须正好是 x±4 / y±1 / z±4
        int[] b = com.example.touhou.core.HarvestTime.bounds(machine);
        guideLine(sender, "\u00a7e  -- 范围自检 --");
        guideLine(sender, "\u00a78  边界 x: " + b[0] + " .. " + b[1]
                + "   y: " + b[2] + " .. " + b[3]
                + "   z: " + b[4] + " .. " + b[5]);
        guideLine(sender, "\u00a78  期待  x: " + (machine.getX() - 4) + " .. " + (machine.getX() + 4)
                + "   y: " + (machine.getY() - 1) + " .. " + (machine.getY() + 1)
                + "   z: " + (machine.getZ() - 4) + " .. " + (machine.getZ() + 4));
        guideLine(sender, "\u00a78  格数 = 9*9*3 = 243；机器自己那格在范围内但会被跳过 —— 现在它是 "
                + machine.getType() + "（HAY_BLOCK，非 Ageable ⇒ 本来也不会被催）");
        // 边界内外各取一格，证明 inRange 判得对
        guideLine(sender, "\u00a78  inRange(北边界 z=" + b[4] + ") = "
                + com.example.touhou.core.HarvestTime.inRange(machine, machine.getX(), machine.getY(), b[4])
                + "   inRange(越界 z=" + (b[4] - 1) + ") = "
                + com.example.touhou.core.HarvestTime.inRange(machine, machine.getX(), machine.getY(), b[4] - 1)
                + "   inRange(越界 y=" + (b[3] + 1) + ") = "
                + com.example.touhou.core.HarvestTime.inRange(machine, machine.getX(), b[3] + 1, machine.getZ()));

        // ---- ③ 记录催熟前的状态
        //   ★ 快照与逐格对照必须是【同一批 Block 对象】：
        //     早先这里另建了一个 targets 列表，结果打印出来的"目标 N"顺序
        //     与"测试田（N 个可催熟目标）"完全对不上（一个按 dx/dy/dz、
        //     一个按 dx/dz/dy 扫描），读起来像是数据错乱。现在只用一份。
        List<Block> targets = new ArrayList<>();
        List<String> before = new ArrayList<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    Block blk = center.getWorld().getBlockAt(
                            machine.getX() + dx, machine.getY() + dy, machine.getZ() + dz);
                    if (blk.equals(machine)) {
                        continue;
                    }
                    if (com.example.touhou.core.HarvestTime.needsBoneMeal(blk)) {
                        targets.add(blk);
                        before.add(com.example.touhou.core.HarvestTime.describe(blk));
                    }
                }
            }
        }
        int beforeFruits = countFruits(machine);
        // 范围外对照的催熟前读数（放在机器上方三格的田里，见 buildTestFarm）
        Block outside = center.getWorld().getBlockAt(machine.getX(), machine.getY() + 2, machine.getZ() + 3);
        String outsideBefore = com.example.touhou.core.HarvestTime.describe(outside);

        // ---- ④ 跑催熟内核（与右键处理器调的<b>同一个</b>方法）
        guideLine(sender, "\u00a7e  -- 催熟（" + targets.size() + " 个目标都是 Ageable 且未满级）--");
        com.example.touhou.core.HarvestTime.HarvestReport report =
                com.example.touhou.core.HarvestTime.harvest(machine, "console-verify");

        // ---- ⑤ 逐格前后对照
        for (int i = 0; i < targets.size(); i++) {
            guideLine(sender, "\u00a78    目标 " + (i + 1) + "：" + before.get(i)
                    + "  \u2192  " + com.example.touhou.core.HarvestTime.describe(targets.get(i)));
        }
        int afterFruits = countFruits(machine);
        guideLine(sender, "\u00a7e  -- 结果 --");
        guideLine(sender, "\u00a78  实际被催熟的格数 = " + report.boneMealedCount()
                + "（内核自报；目标 " + targets.size() + " 个）");
        guideLine(sender, "\u00a78  西瓜/南瓜果实：催熟前 " + beforeFruits + " 个 → 催熟后 "
                + afterFruits + " 个（新增 " + (afterFruits - beforeFruits) + "）");
        guideLine(sender, "\u00a78  内核自报结出的果实 = " + report.fruitCount() + " 个");
        guideLine(sender, "\u00a78  详细读数 = " + report.detail()
                + "；按这次结果该播的提示 = \u00a7f"
                + com.example.touhou.core.Notify.plain(report.summary()));
        for (String f : report.fruitDetails()) {
            guideLine(sender, "\u00a7a    ★ 结果位置 " + f);
        }
        if (report.fruitCount() == 0) {
            guideLine(sender, "\u00a7c    （没有结出果实 —— 检查测试田的茎是否成熟、旁边是否留了空气格）");
        }
        // 进了扫描却没涨 age 的格：如实打出来（不是所有 Ageable 都吃骨粉）
        if (!report.movedDetails().isEmpty()) {
            guideLine(sender, "\u00a7e  -- 作为目标记下、事后换了类型的格（预期变动，不是失败）--");
            for (String line : report.movedDetails()) {
                guideLine(sender, "\u00a78    " + line);
            }
        }
        if (!report.unchangedDetails().isEmpty()) {
            guideLine(sender, "\u00a7e  -- 进了扫描但 age 未变的格（如实报告）--");
            for (String line : report.unchangedDetails()) {
                guideLine(sender, "\u00a78    " + line);
            }
        }

        // ---- ⑤a "为什么要循环"的实测证据：把每个目标再叫一次【单次】骨粉，
        //   看有多少次"叫了却没反应" —— 这正是 forceRipen 存在的理由。
        guideLine(sender, "\u00a7e  -- 单次骨粉的可靠性实测（说明「为什么要循环到满级」）--");
        int singleCalls = 0;
        int singleNoEffect = 0;
        List<String> noEffectSamples = new ArrayList<>();
        for (Block blk : targets) {
            org.bukkit.block.data.BlockData d = blk.getBlockData();
            if (!(d instanceof org.bukkit.block.data.Ageable ageable)) {
                continue;
            }
            if (ageable.getAge() >= ageable.getMaximumAge()) {
                continue;       // 已经被催满了，没法再测
            }
            singleCalls++;
            if (!com.example.touhou.core.HarvestTime.applyBoneMeal(blk)) {
                singleNoEffect++;
                if (noEffectSamples.size() < 3) {
                    noEffectSamples.add(blk.getType() + " @ "
                            + blk.getX() + "," + blk.getY() + "," + blk.getZ());
                }
            }
        }
        guideLine(sender, "\u00a78  单次调用 = " + singleCalls + " 次，其中【叫了 age 却没动】= "
                + singleNoEffect + " 次"
                + (singleCalls == 0 ? "（全部已被催满，本次无量）"
                                : "  比率 " + (singleNoEffect * 100 / Math.max(1, singleCalls)) + "%"));
        for (String s : noEffectSamples) {
            guideLine(sender, "\u00a78    " + s + "（单次无反应）");
        }
        guideLine(sender, "\u00a78  ⇒ 结论：单次 applyBoneMeal 是【逐次判随机数】的，"
                + "一次右键必须循环到满级才算「强制催熟」（见 HarvestTime#forceRipen）");

        // ---- ⑤b 范围外对照：y+2 那株小麦必须一个 age 都没涨
        guideLine(sender, "\u00a7e  -- 范围外对照（证明扫描真的只在 9×9×3 里动手）--");
        boolean outsideIntact = outside.getBlockData() instanceof org.bukkit.block.data.Ageable a
                && a.getAge() == 0;
        guideLine(sender, "\u00a78  " + outsideBefore + "  \u2192  "
                + com.example.touhou.core.HarvestTime.describe(outside) + "  ⇒ "
                + (outsideIntact ? "\u00a7aage 仍为 0（没被碰，正确）" : "\u00a7cage 变了（不该发生）"));

        // ---- ⑥ 冷却：连调两次，第二次必须被拦
        guideLine(sender, "\u00a7e  -- 冷却验证（复刻右键处理器那两行：先问 cooldownLeft、通过才 markUsed）--");
        long cfgCooldown = AddonConfig.get().harvestCooldownMillis;
        com.example.touhou.core.HarvestTime.clearCooldowns();
        long first = com.example.touhou.core.HarvestTime.cooldownLeft(machine);
        guideLine(sender, "\u00a78  第 1 次 cooldownLeft = " + first + " ms ⇒ "
                + (first == 0 ? "\u00a7a放行" : "\u00a7c被拦（不该发生：刚清过冷却表）"));
        com.example.touhou.core.HarvestTime.markUsed(machine);
        long second = com.example.touhou.core.HarvestTime.cooldownLeft(machine);
        guideLine(sender, "\u00a78  第 2 次 cooldownLeft = " + second + " ms ⇒ "
                + (cfgCooldown <= 0 ? "\u00a77放行（冷却被配置为 0 = 关闭）"
                        : (second > 0 ? "\u00a7a被拦（正确：冷却 " + cfgCooldown + " ms 生效中）"
                                : "\u00a7c放行（不该发生）")));
        guideLine(sender, "\u00a78  冷却中方块数 = "
                + com.example.touhou.core.HarvestTime.coolingCount()
                + "（配置 " + cfgCooldown + " ms）");
        com.example.touhou.core.HarvestTime.clearCooldowns();

        // ---- ⑦ 权限路径
        guideLine(sender, "\u00a7e  -- 权限 --");
        guideLine(sender, "\u00a78  canHarvest(null, 方块) = "
                + ((com.example.touhou.core.HarvestTime) item).canHarvest(null, machine)
                + "（null 玩家必须为 false；真实玩家走 bypass 权限 或 canUse+领地交互权）");
        guideLine(sender, "\u00a78  判据与木桩/赛钱箱/反应堆核心的 canOpen 同源："
                + "slimefun.inventory.bypass || (canUse && Interaction.INTERACT_BLOCK)");

        log("[TOUHOU] harvest test @ " + xyz(center)
                + " targets=" + targets.size()
                + " boneMealed=" + report.boneMealedCount()
                + " fruitsBefore=" + beforeFruits + " fruitsAfter=" + afterFruits
                + " cooldownFirst=" + first + " cooldownSecond=" + second);

        // ---- ⑧ 收尾
        if (!keep) {
            int cleaned = clearTestFarm(machine, true);
            guideLine(sender, "\u00a78  已清掉测试田 " + cleaned + " 格（世界复原）");
        } else {
            guideLine(sender, "\u00a78  测试田已保留（probe）。清掉它：/touhou harvest clear "
                    + xyz(center));
        }
    }

    /**
     * 骨粉行为实验台（{@code /touhou harvest rng <x> <y> <z>}）。
     *
     * <p>在指定坐标放一块耕地，对每种测试方块做 N 次「重置 age → 调一次骨粉 → 看 age 有没有动」，
     * 统计<b>单次响应率</b>。它回答的是两个不能靠读代码猜的问题：
     * <ol>
     *   <li>{@code Block#applyBoneMeal} 到底是不是"逐次判随机数"？</li>
     *   <li>竹子在这台服务端上到底会不会响应？</li>
     * </ol>
     * 实验台用完即清（只动它自己那一格 + 上方两格）。
     */
    private void harvestRng(CommandSender sender, String[] args) {
        Location loc = resolveAny(sender, Arrays.copyOfRange(args, 1, args.length));
        if (loc == null) {
            return;
        }
        Block base = loc.getBlock();
        World world = base.getWorld();
        int x = base.getX();
        int y = base.getY();
        int z = base.getZ();

        guideLine(sender, PREFIX + "\u00a7e骨粉行为实验台 @ " + xyz(loc)
                + "（每种方块 8 次「重置 age → 单次骨粉」）");
        guideLine(sender, "\u00a78  地面：y-1 = 耕地，方块：y = 被测物，y+1/y+2 = 空气（给竹子留高度）");

        Material[] subjects = {
                Material.WHEAT, Material.CARROTS, Material.BEETROOTS,
                Material.BAMBOO, Material.OAK_SAPLING, Material.MELON_STEM, Material.PUMPKIN_STEM
        };
        List<String> report = new ArrayList<>();
        for (Material subject : subjects) {
            // 地面
            world.getBlockAt(x, y - 1, z).setType(Material.FARMLAND, false);
            // 清上方，给竹子/树苗留空间
            world.getBlockAt(x, y + 1, z).setType(Material.AIR, false);
            world.getBlockAt(x, y + 2, z).setType(Material.AIR, false);
            Block block = world.getBlockAt(x, y, z);
            block.setType(subject, false);
            // 满级口径 + 重置口径：Ageable 看 age，树苗看 stage（树苗【不是】Ageable）
            org.bukkit.block.data.BlockData d0 = block.getBlockData();
            String maxText;
            if (d0 instanceof org.bukkit.block.data.Ageable a0) {
                maxText = "age 满级 " + a0.getMaximumAge();
            } else if (d0 instanceof org.bukkit.block.data.type.Sapling s0) {
                maxText = "stage 满级 " + s0.getMaximumStage();
            } else {
                report.add(subject + "：既不是 Ageable 也不是 Sapling，跳过");
                continue;
            }
            int responded = 0;
            int trials = 8;
            List<String> trace = new ArrayList<>();
            for (int i = 0; i < trials; i++) {
                // 重置（清上方，避免竹子长高后"上方不是空气"干扰）
                block.setType(subject, false);
                world.getBlockAt(x, y + 1, z).setType(Material.AIR, false);
                world.getBlockAt(x, y + 2, z).setType(Material.AIR, false);
                org.bukkit.block.data.BlockData d = block.getBlockData();
                if (d instanceof org.bukkit.block.data.Ageable a) {
                    a.setAge(0);
                    block.setBlockData(a, false);
                } else if (d instanceof org.bukkit.block.data.type.Sapling s) {
                    s.setStage(0);
                    block.setBlockData(s, false);
                }
                boolean changed = com.example.touhou.core.HarvestTime.applyBoneMeal(block);
                if (changed) {
                    responded++;
                }
                org.bukkit.block.data.BlockData d2 = block.getBlockData();
                int now = d2 instanceof org.bukkit.block.data.Ageable a2 ? a2.getAge()
                        : (d2 instanceof org.bukkit.block.data.type.Sapling s2 ? s2.getStage() : -1);
                trace.add((changed ? "+" : "-") + now);
            }
            report.add(subject + "  " + maxText
                    + "  单次响应 " + responded + "/" + trials
                    + "  逐次读数=" + String.join(" ", trace));
        }
        for (String line : report) {
            guideLine(sender, "\u00a78  " + line);
        }
        // 收尾：清掉实验台
        world.getBlockAt(x, y - 1, z).setType(Material.AIR, false);
        world.getBlockAt(x, y, z).setType(Material.AIR, false);
        world.getBlockAt(x, y + 1, z).setType(Material.AIR, false);
        world.getBlockAt(x, y + 2, z).setType(Material.AIR, false);
        guideLine(sender, "\u00a78  实验台已清理");
        log("[TOUHOU] harvest rng @ " + xyz(loc) + " -> " + report.size() + " 种方块已测");
    }

    /**
     * <b>提示语实测台</b>（{@code /touhou harvest wake <x> <y> <z> [crops|empty]}）。
     *
     * <h2>它验证的是"玩家会看到哪一句话"</h2>
     * 玩家可见文案的唯一出处是 {@link com.example.touhou.core.HarvestTime#announce}
     * （内部调 {@code HarvestReport#summary()}），而它只在有真实玩家右键时才被调用过。
     * 无头测试服没有玩家 ⇒ 在加这条命令之前，<b>"提示说没找到、实际却催熟了"这类
     * 文案 bug 是验不到的</b>（{@code harvest test} 走的是催熟内核，碰不到这句话）。
     *
     * <p>这条命令把 {@code publicRun}（冷却 → 催熟 → <b>拼提示</b>）跑一遍并打印：
     * 走到哪一步（stage）、内核读数、<b>那句话本身</b>，最后按模式断言该走哪一支：
     * <ul>
     *   <li>{@code crops}（默认）铺有作物的田 ⇒ 期望「秋姐妹已给予丰收的庇佑」；</li>
     *   <li>{@code empty} 只铺耕地、什么都不种 ⇒ 期望「没有可以催熟的东西」。</li>
     * </ul>
     * 两种模式复用 {@code test} 的同一套造田/清田代码，只差"种不种"这一个变量。
     */
    private void harvestWake(CommandSender sender, String[] args) {
        Location loc = resolveAny(sender, Arrays.copyOfRange(args, 1, args.length));
        if (loc == null) {
            return;
        }
        String mode = args.length >= 5 ? args[4].toLowerCase(Locale.ROOT) : "crops";
        if (!mode.equals("crops") && !mode.equals("empty")) {
            sender.sendMessage(PREFIX + "\u00a7c用法: /touhou harvest wake <x> <y> <z> [crops|empty]");
            return;
        }
        boolean withCrops = mode.equals("crops");
        Block machine = loc.getBlock();

        guideLine(sender, PREFIX + "\u00a7e丰收之时 · 提示语实测台 @ " + xyz(loc)
                + "  模式=" + mode + "（" + (withCrops ? "有作物" : "只有耕地、无任何可催熟目标") + "）");

        // 造田：复用 test 的同一套代码，只差"种不种"
        clearTestFarm(machine, false);
        for (String line : buildTestFarm(machine, withCrops)) {
            guideLine(sender, "\u00a78    " + line);
        }

        // 先清冷却与上一轮播报，保证这一轮读到的就是本轮结果
        com.example.touhou.core.HarvestTime.clearCooldowns();
        com.example.touhou.core.HarvestTime.resetLastSummary();

        // 走公共路径（冷却 → 催熟 → 拼提示）
        com.example.touhou.core.HarvestTime.PublicRun run =
                com.example.touhou.core.HarvestTime.publicRun(machine, "console-wake");
        String summary = run.summary;
        String plain = summary == null ? null : com.example.touhou.core.Notify.plain(summary);

        guideLine(sender, "\u00a7e  -- 公共路径读数 --");
        guideLine(sender, "\u00a78  " + run.describeRun());
        guideLine(sender, "\u00a78  详细读数（只进日志）= " + (run.report == null ? "-" : run.report.detail()));
        guideLine(sender, "\u00a78  玩家会看到的那句话 = \u00a7f"
                + (plain == null ? "(null —— 没走到拼提示那一步)" : plain));

        // 断言：该走哪一支
        String expect = withCrops ? "BLESSING" : "NOTHING";
        String branch = plain == null ? "NONE"
                : (plain.contains("庇佑") ? "BLESSING"
                        : (plain.contains("没有可以催熟") ? "NOTHING" : "OTHER"));
        guideLine(sender, "\u00a78  期望分支 = " + expect + "   实际分支 = " + branch + " ⇒ "
                + (branch.equals(expect) ? "\u00a7a符合" : "\u00a7c不符合"));
        log("[TOUHOU] harvest wake @ " + xyz(loc) + " mode=" + mode
                + " " + run.describeRun() + " expect=" + expect + " actual=" + branch);

        int cleaned = clearTestFarm(machine, true);
        guideLine(sender, "\u00a78  已清掉测试田 " + cleaned + " 格（世界复原）");
    }

    /** 只对某一格试骨粉（用于手工/无头核对单个方块的响应）。 */
    private void harvestCell(CommandSender sender, String[] args) {
        Location loc = resolveAny(sender, Arrays.copyOfRange(args, 1, args.length));
        if (loc == null) {
            return;
        }
        Block block = loc.getBlock();
        org.bukkit.block.BlockFace face = org.bukkit.block.BlockFace.UP;
        if (args.length >= 5) {
            try {
                face = org.bukkit.block.BlockFace.valueOf(args[4].toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                sender.sendMessage(PREFIX + "\u00a7c没有这个面: " + args[4]);
                return;
            }
        }
        String beforeDesc = com.example.touhou.core.HarvestTime.describe(block);
        boolean needed = com.example.touhou.core.HarvestTime.needsBoneMeal(block);
        boolean changed = com.example.touhou.core.HarvestTime.applyBoneMeal(block, face);
        guideLine(sender, PREFIX + "\u00a7e单格骨粉实测 @ " + xyz(loc) + "  面=" + face);
        guideLine(sender, "\u00a78  needsBoneMeal = " + needed + "（false = 不是 Ageable 或已满级）");
        guideLine(sender, "\u00a78  " + beforeDesc + "  \u2192  "
                + com.example.touhou.core.HarvestTime.describe(block));
        guideLine(sender, "\u00a78  age 是否真的跳了 = " + changed);
        log("[TOUHOU] harvest cell @ " + xyz(loc) + " face=" + face
                + " needed=" + needed + " changed=" + changed);
    }

    /**
     * 摆一块标准测试田，返回"种了什么"的可读清单。
     *
     * <p>布局（{@code m} = 机器方块）：
     * <ul>
     *   <li>{@code y-1}：整层耕地 —— 作物与茎都要有耕地才肯被骨粉推
     *       （★ 第一次实测踩过：把作物放在 {@code y+1}、下面垫的是空气，
     *        {@code applyBoneMeal} 会<b>拒绝</b>施加，表现成"目标数 3、催熟 2"这种假失败）；</li>
     *   <li>{@code y}：机器自己 + 全部作物（与机器<b>同一层</b>，于是每一格的正下方
     *       都是 {@code y-1} 的耕地，位置合法）；</li>
     *   <li>{@code y+1}：故意种一株小麦 —— 它在范围<b>外</b>（高只有 {@code y-1..y+1}，
     *       但水平没超），用来证明扫描确实只碰范围内的格子；</li>
     *   <li>两根茎的北 / 东 / 南 / 西四向都留空气，给"结果"留位置。</li>
     * </ul>
     *
     * <p>★ 机器那一格是用 {@link Slimefun#getDatabaseManager()} 的
     * {@code createBlock} 真写进去的 —— 与 {@code /touhou place} 同一条路，
     * 所以命令验证的确实是一台"真正的粘液方块"。
     */
    private List<String> buildTestFarm(Block machine) {
        return buildTestFarm(machine, true);
    }

    /**
     * 同 {@link #buildTestFarm(Block)}，但可以造一块<b>没有可催熟目标</b>的田。
     *
     * <p>★ {@code withCrops = false} 是专门为"验证「没有找到可催熟」那句话"准备的：
     * 只铺耕地、不种任何作物 ⇒ 扫描应当一个目标都找不到，
     * 提示必须走到"这片地里没有可以催熟的东西"那一支。
     * 没有这个开关，"无目标"这一支在无头环境里根本造不出来。
     */
    private List<String> buildTestFarm(Block machine, boolean withCrops) {
        List<String> planted = new ArrayList<>();
        World world = machine.getWorld();
        int mx = machine.getX();
        int my = machine.getY();
        int mz = machine.getZ();

        // 耕地层（y-1）：整层铺耕地 —— 作物 / 茎 / 果实下方都得有它
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                world.getBlockAt(mx + dx, my - 1, mz + dz).setType(Material.FARMLAND, false);
            }
        }
        // 机器自己那一格：真·粘液方块（与 /touhou place 同一条路）
        machine.setType(Material.HAY_BLOCK, false);
        try {
            Slimefun.getDatabaseManager().getBlockDataController()
                    .createBlock(machine.getLocation(), com.example.touhou.core.HarvestTime.ID);
        } catch (RuntimeException e) {
            // 已经存在方块数据时会抛 —— 那不是失败（重复跑测试田的正常情况）
            log("[TOUHOU] harvest test: 机器方块数据已存在，跳过 createBlock (" + e.getMessage() + ")");
        }

        // 目标层 = 机器那一层（y），保证正下方是 y-1 的耕地
        int ty = my;
        Object[][] plan = withCrops ? new Object[][]{
                // {dx, dz, 材质, age（-1 = 用该方块数据的默认值）}
                {0, -3, Material.WHEAT, 0},
                {1, -3, Material.CARROTS, 0},
                {2, -3, Material.POTATOES, 0},
                {3, -3, Material.BEETROOTS, 0},
                {-1, -3, Material.NETHER_WART, 0},
                {-2, -3, Material.OAK_SAPLING, 0},
                {-3, -3, Material.BAMBOO, 0},
                {0, 3, Material.MELON_STEM, 0},
                {2, 3, Material.PUMPKIN_STEM, 0},
                // 已成熟的小麦：needsBoneMeal 必须是 false ⇒ 不计入"被催熟"的格数
                {-2, 3, Material.WHEAT, 7},
        } : new Object[0][];
        for (Object[] row : plan) {
            int dx = (Integer) row[0];
            int dz = (Integer) row[1];
            Material mat = (Material) row[2];
            int age = (Integer) row[3];
            Block block = world.getBlockAt(mx + dx, ty, mz + dz);
            block.setType(mat, true);
            if (age >= 0 && block.getBlockData() instanceof org.bukkit.block.data.Ageable ageable) {
                ageable.setAge(Math.min(age, ageable.getMaximumAge()));
                block.setBlockData(ageable, true);
            }
            // 茎的四向留空气，给"结果"腾位置（果实也长在 y 这一层）
            if (mat == Material.MELON_STEM || mat == Material.PUMPKIN_STEM) {
                for (org.bukkit.block.BlockFace f : new org.bukkit.block.BlockFace[]{
                        org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.EAST,
                        org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST}) {
                    block.getRelative(f).setType(Material.AIR, false);
                }
            }
            planted.add(mat + " @ " + (mx + dx) + "," + ty + "," + (mz + dz)
                    + "  初始 age=" + (block.getBlockData() instanceof org.bukkit.block.data.Ageable a
                            ? a.getAge() + "/" + a.getMaximumAge() : "-"));
        }

        // 范围【外】的对照株：放在 y+2（垂直越界），必须一个 age 都不涨。
        //   ★ 为什么放 y+2 而不是 y+1：扫描范围是 y-1..y+1，y+1 也在【清理区】里
        //     （clearTestFarm 清 y-1..y+2），早先放 y+1 的对照株在重建测试田时
        //     就被自己的清场逻辑铲掉了 —— 实测症状是"范围外对照"被当成目标、
        //     打印成 AIR，看着像是扫描越界。y+2 同样在扫描范围外（更强），
        //     且不在被铲的那一层，能活到比对时刻。
        //   ★ withCrops=false 时不种它：那一模式要的是"一个可催熟目标都没有"。
        if (withCrops) {
            world.getBlockAt(mx, my + 1, mz + 3).setType(Material.FARMLAND, false);
            Block outside = world.getBlockAt(mx, my + 2, mz + 3);
            outside.setType(Material.WHEAT, true);
            if (outside.getBlockData() instanceof org.bukkit.block.data.Ageable a) {
                a.setAge(0);
                outside.setBlockData(a, true);
            }
            planted.add("【范围外对照】" + Material.WHEAT + " @ " + mx + "," + (my + 2) + "," + (mz + 3)
                    + "  初始 age=" + (outside.getBlockData() instanceof org.bukkit.block.data.Ageable a
                            ? a.getAge() + "/" + a.getMaximumAge() : "-")
                    + "  ← 在 y+2，高于扫描上界 y+" + (1) + "，不该被碰");
        }
        return planted;
    }

    /**
     * 清掉测试田：把 9×9 的 {@code y-1 .. y+2} 清成空气，并清掉机器那格的粘液方块数据。
     *
     * @param removeMachineData 是否连方块数据一起清（{@code false} 用于"重建前先清场"）
     * @return 被清掉的非空气格数
     */
    private int clearTestFarm(Block machine, boolean removeMachineData) {
        World world = machine.getWorld();
        int mx = machine.getX();
        int my = machine.getY();
        int mz = machine.getZ();
        int cleared = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    Block b = world.getBlockAt(mx + dx, my + dy, mz + dz);
                    if (b.getType() != Material.AIR) {
                        b.setType(Material.AIR, false);
                        cleared++;
                    }
                }
            }
        }
        if (removeMachineData) {
            try {
                Slimefun.getDatabaseManager().getBlockDataController()
                        .removeBlock(machine.getLocation());
            } catch (RuntimeException e) {
                log("[TOUHOU] harvest clear: 清方块数据失败 " + e);
            }
            com.example.touhou.core.HarvestTime.clearCooldowns();
        }
        return cleared;
    }

    /** 数范围内已经存在的西瓜/南瓜果实方块（不数茎）。 */
    private static int countFruits(Block machine) {
        World world = machine.getWorld();
        int n = 0;
        for (int dx = -4; dx <= 4; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Material t = world.getBlockAt(machine.getX() + dx,
                            machine.getY() + dy, machine.getZ() + dz).getType();
                    if (t == Material.MELON || t == Material.PUMPKIN) {
                        n++;
                    }
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ acquisition（获取方式）

    /**
     * ★ 统一的「获取方式」核查 —— {@code /touhou acquisition}。
     *
     * <pre>
     *   /touhou acquisition            全部物品的获取方式标注情况（唯一出处 = Acquisition 总表）
     *   /touhou acquisition &lt;物品id&gt;    只看一件
     *   /touhou acquisition rule       规则本身（给以后加物品时照抄）
     * </pre>
     *
     * <p>判读：{@code [OK]} = 标注齐（lore 有那一行、需要门面的也挂了门面）；
     * {@code [LORE?]} = 本表有方法但物品 lore 里没找到那一行（模板表漏登记）；
     * {@code [MISS]} = 本表没登记、也无法从配方类型推断 ⇒ <b>必须补一行</b>。
     */
    private void acquisition(CommandSender sender, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "all";
        if (sub.equals("rule") || sub.equals("rules") || sub.equals("describe")) {
            sender.sendMessage(PREFIX + "\u00a7e获取方式标注规则");
            for (String line : com.example.touhou.core.Acquisition.describe()) {
                sender.sendMessage("\u00a78  " + line);
            }
            log("[TOUHOU] acquisition rule");
            return;
        }
        if (sub.equals("all") || sub.equals("check") || sub.equals("list")) {
            List<String> report = com.example.touhou.core.Acquisition.verify();
            sender.sendMessage(PREFIX + "\u00a7e获取方式标注核查（" + report.size() + " 行）");
            for (String line : report) {
                sender.sendMessage("\u00a78  " + line);
            }
            int miss = 0;
            int loreMiss = 0;
            for (String line : report) {
                if (line.startsWith("[MISS]")) {
                    miss++;
                } else if (line.startsWith("[LORE?]")) {
                    loreMiss++;
                }
            }
            sender.sendMessage(PREFIX + (miss == 0 && loreMiss == 0
                    ? "\u00a7a全部已标注（无 [MISS] / [LORE?]）"
                    : "\u00a7c有 " + miss + " 件未标注获取方式、"
                            + loreMiss + " 件 lore 缺那一行"));
            for (String line : com.example.touhou.core.Acquisition.undecorated()) {
                sender.sendMessage("\u00a78  · 未挂门面（有配方 / 已有专人门面 / 待补）：" + line);
            }
            log("[TOUHOU] acquisition miss=" + miss + " loreMiss=" + loreMiss
                    + " table=" + com.example.touhou.core.Acquisition.size());
            return;
        }
        if (!sub.equals("one") && !sub.startsWith("touhou_")) {
            sender.sendMessage(PREFIX + "\u00a77用法: /touhou acquisition [all|rule|<物品id>]");
            return;
        }
        String id = (sub.equals("one") && args.length >= 2 ? args[1] : args[0])
                .toUpperCase(Locale.ROOT);
        io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem item =
                io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getById(id);
        if (item == null) {
            sender.sendMessage(PREFIX + "\u00a7c注册表里没有这个 id：" + id);
            log("[TOUHOU] acquisition id=" + id + " notFound");
            return;
        }
        String method = com.example.touhou.core.Acquisition.resolve(item);
        sender.sendMessage(PREFIX + "\u00a7e" + id);
        sender.sendMessage("\u00a78  获取方式 = " + (method == null
                ? "\u00a7c★未标注（请到 Acquisition.METHOD_BY_ID 补一行）" : method));
        sender.sendMessage("\u00a78  lore 里有那一行 = "
                + com.example.touhou.core.Acquisition.hasLore(item)
                + "   配方类型 = " + com.example.touhou.core.Acquisition.recipeTypeKey(item)
                + "   门面 = " + com.example.touhou.core.Acquisition.isDecorated(item));
        log("[TOUHOU] acquisition id=" + id + " method=" + method
                + " lore=" + com.example.touhou.core.Acquisition.hasLore(item));
    }

    // ------------------------------------------------------------------ leaves（落叶）

    /**
     * 「落叶」的无头验证入口。
     *
     * <pre>
     *   /touhou leaves                          物品 + 渐变 + 监听器 + 工具判据自检
     *   /touhou leaves field &lt;x&gt; &lt;y&gt; &lt;z&gt; [n]       铺一片各类型树叶（用 Tag.LEAVES 现查），默认 1000 格
     *   /touhou leaves drop [n]                 大样本掉率与数量分布（默认 1000 次，固定种子可复现）
     *   /touhou leaves tools                    剪刀 / 精准采集 / 普通工具 的判据逐条核对
     *   /touhou leaves check &lt;x&gt; &lt;y&gt; &lt;z&gt;         非树叶对照：周边方块是不是都没被当成树叶
     *   /touhou leaves clear &lt;x&gt; &lt;y&gt; &lt;z&gt;         清掉测试树叶
     * </pre>
     *
     * <p>★ <b>无头服造不出真玩家</b>（没有在线玩家，{@code BlockDropItemEvent}
     * 又必须由真实破坏触发）⇒ 这里<b>不模拟事件</b>，而是直接调
     * <b>事件监听器调用的那同一个方法</b> {@link com.example.touhou.core.FallenLeaves#rollLeaves}。
     * 于是"命令算出的掉率"就是"玩家会遇到的掉率"；事件绑定那一条由
     * {@code activeListeners} 证明（插件注册了哪些监听器可查）。
     */
    private void leaves(CommandSender sender, String[] args) {
        String sub = args.length >= 1 ? args[0].toLowerCase(Locale.ROOT) : "selfcheck";

        if (sub.equals("selfcheck") || sub.equals("all")) {
            leavesSelfCheck(sender);
        }
        if (sub.equals("tools") || sub.equals("tool")) {
            leavesTools(sender);
        }
        if (sub.equals("drop") || sub.equals("rate")) {
            int trials = args.length >= 2 ? parseIntOr(args[1], 1000) : 1000;
            leavesDrop(sender, trials);
        }
        if (sub.equals("field") || sub.equals("place")) {
            Location loc = resolveAny(sender, Arrays.copyOfRange(args, 1, args.length));
            if (loc == null) {
                return;
            }
            leavesField(sender, loc, args);
        }
        if (sub.equals("check")) {
            Location loc = resolveAny(sender, Arrays.copyOfRange(args, 1, args.length));
            if (loc == null) {
                return;
            }
            leavesCheck(sender, loc);
        }
        if (sub.equals("proof")) {
            Location loc = resolveAny(sender, Arrays.copyOfRange(args, 1, args.length));
            if (loc == null) {
                return;
            }
            leavesProof(sender, loc);
        }
        if (sub.equals("watch") || sub.equals("trace") || sub.equals("diag")) {
            leavesWatch(sender, args);
        }
        if (sub.equals("clear") || sub.equals("clean")) {
            Location loc = resolveAny(sender, Arrays.copyOfRange(args, 1, args.length));
            if (loc == null) {
                return;
            }
            int n = leavesClear(loc.getBlock(), false);
            guideLine(sender, PREFIX + "\u00a7a已清掉测试树叶 " + n + " 格 @ " + xyz(loc));
        }
        if (sub.equals("selfcheck") || sub.equals("all") || sub.equals("tools")
                || sub.equals("tool") || sub.equals("drop") || sub.equals("rate")
                || sub.equals("watch") || sub.equals("trace") || sub.equals("diag")
                || sub.equals("field") || sub.equals("place") || sub.equals("check")
                || sub.equals("proof") || sub.equals("clear") || sub.equals("clean")) {
            return;
        }
        sender.sendMessage(PREFIX + "\u00a7c用法: /touhou leaves [selfcheck | tools |"
                + " drop [n] | watch [n|off] | field <x> <y> <z> [n] | check <x> <y> <z> |"
                + " proof <x> <y> <z> | clear <x> <y> <z>]");
    }

    /**
     * ★ 诊断追踪：{@code /touhou leaves watch [n|off]}。
     *
     * <h2>为什么需要它（真实踩点）</h2>
     * 玩家实测"用锄头挖树叶挖了几十个都不掉"，而控制台里<b>一行痕迹都没有</b>：
     * {@code logging.console-info} 默认为 {@code false}，<b>成功行也被静默了</b>，
     * 于是"机制没触发"与"触发成功但看不见"完全无法区分 —— 排查绕了一大圈。
     *
     * <p>本命令打开一个窗口：接下来 n 次"破坏树叶"会被逐条追踪打印，
     * 并且走 {@code Log.always}（<b>不受 info 总开关影响</b>）。窗口内能直接读出：
     * <ul>
     *   <li>{@code [BREAK]} 行 —— 判定与掉落的入口，破坏<b>一定</b>触发它；
     *       行尾就是这次到底掉没掉（命中 xN / 未命中 / 被排除）；</li>
     *   <li>{@code [DROP]} 行 —— 原版为这个方块生成了掉落物时才有。
     *       <b>有它</b> = 原版掉落非空；<b>没有它</b> = 原版掉落为空。</li>
     * </ul>
     * ★ 历史教训：第一版把判定挂在 {@code BlockDropItemEvent} 上，
     * 而锄头挖树叶时原版掉落常常为空、那个事件不触发，于是"挖了几十个一个都不掉"。
     * 这两行日志就是用来一眼分清"事件没触发"与"判据挡下了"的。
     *
     * <p>顺带把"玩家当前主手工具是否被判据排除"打出来 —— 剪刀 / 精准采集
     * 会被排除，这一条用眼睛看比猜快。
     */
    private void leavesWatch(CommandSender sender, String[] args) {
        String a = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (a.equals("off") || a.equals("stop") || a.equals("0")) {
            com.example.touhou.core.FallenLeaves.stopWatch();
            guideLine(sender, PREFIX + "\u00a7a落叶诊断追踪已关闭");
            log("[TOUHOU] leaves watch off");
            return;
        }
        int n = args.length >= 2 ? parseIntOr(args[1], 40) : 40;
        n = Math.max(1, Math.min(n, 10000));
        com.example.touhou.core.FallenLeaves.startWatch(n);
        guideLine(sender, PREFIX + "\u00a7e落叶诊断追踪已开启：接下来 " + n
                + " 次破坏树叶会逐条打印到控制台");
        guideLine(sender, "\u00a77  ★ 这些行走 Log.always，【不受】logging.console-info 影响");
        guideLine(sender, "\u00a77  判读：[BREAK] 行 = 判定入口（行尾直接写着掉没掉 / 掉几个）；"
                + "[DROP] 行 = 这一次原版掉落非空");
        guideLine(sender, "\u00a77  只有 [BREAK] 没有 [DROP] 是正常的（锄头挖树叶原版常不掉东西）"
                + " —— 判定挂在 [BREAK] 上，不受影响");
        // 当前主手工具现读 —— 剪刀/精准采集会被排除，直接给结论
        if (sender instanceof org.bukkit.entity.Player p) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            boolean excluded = com.example.touhou.core.FallenLeaves.isExcludedTool(hand);
            guideLine(sender, "\u00a78  你当前主手 = "
                    + (hand == null || hand.getType().isAir() ? "空手" : hand.getType())
                    + "  被排除 = " + excluded
                    + (excluded ? "\u00a7c  ⇒ 这个工具破坏树叶【不掉】落叶" : "\u00a7a  ⇒ 这个工具可以掉落叶"));
            log("[TOUHOU] leaves watch on=" + n + " hand=" + hand.getType() + " excluded=" + excluded);
        } else {
            log("[TOUHOU] leaves watch on=" + n);
        }
    }

    /** 物品 / 渐变 / 配方 / 监听器自检。 */
    private void leavesSelfCheck(CommandSender sender) {
        SlimefunItem item = SlimefunItem.getById(com.example.touhou.core.FallenLeaves.ID);
        guideLine(sender, PREFIX + "\u00a7e落叶 · 物品自检");
        if (item == null) {
            guideLine(sender, "\u00a7c  未注册（Slimefun 注册表里查不到 "
                    + com.example.touhou.core.FallenLeaves.ID + "）");
            return;
        }
        ItemStack icon = item.getItem();
        guideLine(sender, "\u00a78  id = " + item.getId());
        guideLine(sender, "\u00a78  材质 = " + (icon == null ? "(null)" : String.valueOf(icon.getType()))
                + "（应为 KELP）");
        guideLine(sender, "\u00a78  物品组 = " + (item.getItemGroup() == null
                ? "(null)" : item.getItemGroup().getKey().toString()));
        guideLine(sender, "\u00a78  配方类型 = "
                + com.example.touhou.core.Acquisition.describeRecipeType(item)
                + "\u00a78（门面 ⇒ 不是合成品，槽 10 显示物品图标 + 获取方式；无门面且 NULL ⇒ 槽 10 空气）");
        ItemStack[] grid = item.getRecipe();
        int filled = 0;
        if (grid != null) {
            for (ItemStack cell : grid) {
                if (cell != null && !cell.getType().isAir()) {
                    filled++;
                }
            }
        }
        guideLine(sender, "\u00a78  配方非空格数 = " + filled + "（应为 0）");
        guideLine(sender, "\u00a78  Bukkit/Slimefun 配方表里能产出它的 = "
                + countRecipesFor(item) + " / " + countMachineRecipesFor(item) + "（都应为 0）");
        guideLine(sender, "\u00a78  ★ 获取方式标注 = "
                + (com.example.touhou.core.Acquisition.hasLore(item) ? "\u00a7a有" : "\u00a7c缺")
                + "\u00a78（" + com.example.touhou.core.Acquisition.method(item.getId()) + "）");

        ItemMeta meta = icon == null ? null : icon.getItemMeta();
        if (meta != null) {
            guideLine(sender, "\u00a78  附魔光效 = 附魔数 " + meta.getEnchants().size()
                    + "，HIDE_ENCHANTS=" + meta.hasItemFlag(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS));
            printDisplayNameEvidence(sender, "显示名（金→棕）", meta);
            List<String> lore = meta.getLore();
            if (lore == null) {
                guideLine(sender, "\u00a7c  (没有 lore)");
            } else {
                for (int i = 0; i < lore.size(); i++) {
                    String line = lore.get(i);
                    guideLine(sender, "\u00a77  lore[" + i + "] 原样 = "
                            + (line == null ? "" : line.replace("\u00a7", "\\u00a7")));
                    // 三行描述都应是金→橙；第 0 行是空行（分隔名与描述），跳过
                    if (i >= 1) {
                        for (String cl : colorPerChar(line)) {
                            guideLine(sender, "\u00a78    " + cl);
                        }
                    }
                }
            }
        }

        // ---- 配置读数
        AddonConfig cfg = AddonConfig.get();
        guideLine(sender, "\u00a7e  -- 配置 --");
        guideLine(sender, "\u00a78  总开关 = " + cfg.fallenLeavesEnabled
                + "   概率 = " + cfg.fallenLeavesDropChance
                + "（" + String.format(Locale.ROOT, "%.1f", cfg.fallenLeavesDropChance * 100.0D) + "%）"
                + "   数量 = " + cfg.fallenLeavesMinAmount + "~" + cfg.fallenLeavesMaxAmount);
        guideLine(sender, "\u00a78  剪刀是否掉落 = " + cfg.fallenLeavesDropWithShears
                + "   精准采集是否掉落 = " + cfg.fallenLeavesDropWithSilkTouch
                + "（默认都 false = 不掉）");

        // ---- 监听器：证明事件真的注册上了（查 BlockDropItemEvent 的已注册监听器表）
        guideLine(sender, "\u00a7e  -- 监听器 --");
        boolean registered = false;
        for (org.bukkit.plugin.RegisteredListener rl
                : org.bukkit.event.block.BlockDropItemEvent.getHandlerList().getRegisteredListeners()) {
            if (rl.getPlugin() instanceof com.example.touhou.Touhou
                    && rl.getListener() instanceof com.example.touhou.core.FallenLeavesListener) {
                registered = true;
                guideLine(sender, "\u00a78  BlockDropItemEvent ← 已注册 FallenLeavesListener"
                        + "  优先级=" + rl.getPriority());
            }
        }
        if (!registered) {
            guideLine(sender, "\u00a7c  BlockDropItemEvent 上没找到 FallenLeavesListener（监听器没注册？）");
        }
        // ★ 破坏事件上的诊断处理器（只打印、不掉落）—— 同样要证明它真的挂上了
        boolean watchRegistered = false;
        for (org.bukkit.plugin.RegisteredListener rl
                : org.bukkit.event.block.BlockBreakEvent.getHandlerList().getRegisteredListeners()) {
            if (rl.getPlugin() instanceof com.example.touhou.Touhou
                    && rl.getListener() instanceof com.example.touhou.core.FallenLeavesListener) {
                watchRegistered = true;
                guideLine(sender, "\u00a78  BlockBreakEvent ← 已注册 FallenLeavesListener（诊断用，只打印）"
                        + "  优先级=" + rl.getPriority());
            }
        }
        if (!watchRegistered) {
            guideLine(sender, "\u00a7c  BlockBreakEvent 上没找到诊断处理器");
        }
        guideLine(sender, "\u00a78  诊断追踪窗口剩余 = "
                + com.example.touhou.core.FallenLeaves.watchRemaining()
                + "（>0 表示 watch 打开着；用 /touhou leaves watch [n|off] 控制）");
        for (String line : com.example.touhou.core.FallenLeavesListener.describe()) {
            guideLine(sender, "\u00a78    " + line);
        }
        log("[TOUHOU] leaves selfcheck id=" + item.getId()
                + " material=" + (icon == null ? "null" : icon.getType())
                + " recipeFilled=" + filled + " listener=" + registered
                + " breakListener=" + watchRegistered
                + " watch=" + com.example.touhou.core.FallenLeaves.watchRemaining()
                + " chance=" + cfg.fallenLeavesDropChance
                + " amount=" + cfg.fallenLeavesMinAmount + "-" + cfg.fallenLeavesMaxAmount);
    }

    /** 工具判据逐条核对：徒手/普通工具 → 不排除；剪刀、精准采集 → 排除。 */
    private void leavesTools(CommandSender sender) {
        guideLine(sender, PREFIX + "\u00a7e落叶 · 工具判据核对（true = 被排除 = 不掉落叶）");
        ItemStack hand = new ItemStack(Material.AIR);
        guideLine(sender, "\u00a78  徒手(空手)            = "
                + com.example.touhou.core.FallenLeaves.isExcludedTool(hand) + "（应为 false）");
        ItemStack ironAxe = new ItemStack(Material.IRON_AXE);
        guideLine(sender, "\u00a78  普通铁斧              = "
                + com.example.touhou.core.FallenLeaves.isExcludedTool(ironAxe) + "（应为 false）");
        ItemStack shears = new ItemStack(Material.SHEARS);
        guideLine(sender, "\u00a78  剪刀                  = "
                + com.example.touhou.core.FallenLeaves.isExcludedTool(shears) + "（应为 true）");
        ItemStack silkAxe = new ItemStack(Material.IRON_AXE);
        silkAxe.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH, 1);
        guideLine(sender, "\u00a78  精准采集铁斧          = "
                + com.example.touhou.core.FallenLeaves.isExcludedTool(silkAxe) + "（应为 true）");
        ItemStack silkShears = new ItemStack(Material.SHEARS);
        silkShears.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH, 1);
        guideLine(sender, "\u00a78  精准采集剪刀（两者同时）= "
                + com.example.touhou.core.FallenLeaves.isExcludedTool(silkShears) + "（应为 true）");
        ItemStack fortuneAxe = new ItemStack(Material.IRON_AXE);
        fortuneAxe.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.LOOT_BONUS_BLOCKS, 3);
        guideLine(sender, "\u00a78  时运III铁斧           = "
                + com.example.touhou.core.FallenLeaves.isExcludedTool(fortuneAxe) + "（应为 false）");
        log("[TOUHOU] leaves tools hand=" + com.example.touhou.core.FallenLeaves.isExcludedTool(hand)
                + " shears=" + com.example.touhou.core.FallenLeaves.isExcludedTool(shears)
                + " silk=" + com.example.touhou.core.FallenLeaves.isExcludedTool(silkAxe)
                + " silkShears=" + com.example.touhou.core.FallenLeaves.isExcludedTool(silkShears)
                + " fortune=" + com.example.touhou.core.FallenLeaves.isExcludedTool(fortuneAxe));
    }

    /**
     * 大样本掉率与数量分布。
     *
     * <p>★ 用<b>固定种子</b>的 {@link java.util.Random}：同一份代码跑两次结果一致，
     * 便于"改动有没有影响分布"的对比；也避免了"这次恰好偏高"的误判。
     */
    private void leavesDrop(CommandSender sender, int trials) {
        int n = Math.max(1, Math.min(trials, 100000));
        guideLine(sender, PREFIX + "\u00a7e落叶 · 大样本掉率（" + n + " 次，固定种子 20260921）");
        java.util.Random random = new java.util.Random(20260921L);
        AddonConfig cfg = AddonConfig.get();
        int hit = 0;
        int totalItems = 0;
        java.util.Map<Integer, Integer> hist = new java.util.TreeMap<>();
        for (int i = 0; i < n; i++) {
            Integer amount = com.example.touhou.core.FallenLeaves.rollLeaves(random);
            if (amount != null) {
                hit++;
                totalItems += amount;
                hist.merge(amount, 1, Integer::sum);
            }
        }
        double rate = hit * 100.0D / n;
        guideLine(sender, "\u00a78  配置概率 = " + cfg.fallenLeavesDropChance
                + "   配置数量 = " + cfg.fallenLeavesMinAmount + "~" + cfg.fallenLeavesMaxAmount);
        guideLine(sender, "\u00a78  命中 = " + hit + " / " + n + " ⇒ 实测掉率 = "
                + String.format(Locale.ROOT, "%.2f", rate) + "%"
                + "（期望 " + String.format(Locale.ROOT, "%.2f", cfg.fallenLeavesDropChance * 100.0D) + "%）");
        guideLine(sender, "\u00a78  命中时的平均掉落量 = "
                + (hit == 0 ? "-" : String.format(Locale.ROOT, "%.3f", (double) totalItems / hit))
                + "（期望 " + String.format(Locale.ROOT, "%.3f",
                        (cfg.fallenLeavesMinAmount + cfg.fallenLeavesMaxAmount) / 2.0D) + "）");
        guideLine(sender, "\u00a7e  -- 数量分布（只统计命中次数）--");
        boolean bothEnds = hist.containsKey(cfg.fallenLeavesMinAmount)
                && hist.containsKey(cfg.fallenLeavesMaxAmount);
        for (java.util.Map.Entry<Integer, Integer> e : hist.entrySet()) {
            guideLine(sender, "\u00a78    x" + e.getKey() + " ⇒ " + e.getValue() + " 次");
        }
        guideLine(sender, "\u00a78  两端都出现过（" + cfg.fallenLeavesMinAmount + " 与 "
                + cfg.fallenLeavesMaxAmount + "）= " + bothEnds
                + "   出现过的数量种类 = " + hist.size() + " 种"
                + "（期望 " + (cfg.fallenLeavesMaxAmount - cfg.fallenLeavesMinAmount + 1) + " 种）");
        log("[TOUHOU] leaves drop trials=" + n + " hits=" + hit
                + " rate=" + String.format(Locale.ROOT, "%.2f", rate)
                + " variants=" + hist.size() + " bothEnds=" + bothEnds);
    }

    /**
     * 铺一片各类型树叶（用 {@link Tag#LEAVES} <b>现查</b>材质，不硬编码树种）。
     *
     * <p>★ 为什么现查：让"到底覆盖了哪些树种"这件事由标签说了算 ——
     * 命令打印的清单就是代码实际认的清单，将来版本加树种也能立刻看出来。
     */
    private void leavesField(CommandSender sender, Location center, String[] args) {
        Block base = center.getBlock();
        World world = base.getWorld();
        int x = base.getX();
        int y = base.getY();
        int z = base.getZ();
        int n = args.length >= 5 ? parseIntOr(args[4], 1000) : 1000;

        // 现查所有树叶材质
        List<Material> leafTypes = new ArrayList<>();
        for (Material m : Material.values()) {
            if (!m.isAir() && m.isBlock() && com.example.touhou.core.FallenLeaves.isLeaves(m)) {
                leafTypes.add(m);
            }
        }
        if (leafTypes.isEmpty()) {
            sender.sendMessage(PREFIX + "\u00a7cTag.LEAVES 里一个方块都没有（标签没加载？）");
            return;
        }
        guideLine(sender, PREFIX + "\u00a7e落叶 · 测试树叶 @ " + xyz(center)
                + "  共 " + leafTypes.size() + " 种树叶素材");
        guideLine(sender, "\u00a78  Tag.LEAVES 现查结果：" + leafTypes);

        int placed = 0;
        int radius = (int) Math.ceil(Math.sqrt(n));
        for (int i = 0; i < n; i++) {
            int dx = i % radius;
            int dz = i / radius;
            Block b = world.getBlockAt(x + dx, y, z + dz);
            b.setType(leafTypes.get(i % leafTypes.size()), false);
            placed++;
        }
        guideLine(sender, "\u00a78  已铺 " + placed + " 格（" + radius + "×" + radius
                + " 的平铺，循环使用上面的素材）");
        guideLine(sender, "\u00a78  清掉它：/touhou leaves clear " + xyz(center));
        log("[TOUHOU] leaves field @ " + xyz(center) + " placed=" + placed
                + " types=" + leafTypes.size());
    }

    /**
     * <b>非树叶对照</b>：把一片非树叶方块逐个过一遍 {@code isLeaves}，
     * 证明它们<b>不会</b>被当成树叶（即"非树叶不触发"这条判据）。
     *
     * <p>★ 诚实边界：无头服<b>造不出真玩家</b>，{@code BlockDropItemEvent}
     * 又必须由真实破坏触发，所以"事件真的没被触发"这件事<b>无法直接模拟</b>。
     * 这里验的是<b>判据本身</b>（{@code isLeaves} 对非树叶一律 false）——
     * 而"事件只在玩家手动破坏时触发"是 Bukkit 事件的固有语义，不是本插件的逻辑。
     */
    private void leavesCheck(CommandSender sender, Location center) {
        World world = center.getWorld();
        int x = center.getBlockX();
        int y = center.getBlockY();
        int z = center.getBlockZ();
        Material[] nonLeaves = {
                Material.STONE, Material.DIRT, Material.OAK_LOG, Material.OAK_PLANKS,
                Material.FARMLAND, Material.HAY_BLOCK, Material.WATER, Material.AIR
        };
        guideLine(sender, PREFIX + "\u00a7e落叶 · 非树叶对照（isLeaves 必须全为 false）");
        int wrong = 0;
        for (Material m : nonLeaves) {
            boolean isLeaf = com.example.touhou.core.FallenLeaves.isLeaves(m);
            if (isLeaf) {
                wrong++;
            }
            guideLine(sender, "\u00a78  " + m + " ⇒ isLeaves(" + isLeaf + ")"
                    + (isLeaf ? "  \u00a7c←不该为 true" : ""));
        }
        // 顺手确认：这片区域里现有的方块都不是树叶（说明对照区是干净的）
        int leafFound = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Material t = world.getBlockAt(x + dx, y, z + dz).getType();
                if (com.example.touhou.core.FallenLeaves.isLeaves(t)) {
                    leafFound++;
                }
            }
        }
        guideLine(sender, "\u00a78  区块内 3×3 现存的树叶格 = " + leafFound + "（越界误判数 = " + wrong + "）");
        guideLine(sender, "\u00a78  ⇒ 判据 " + (wrong == 0 ? "\u00a7a正确" : "\u00a7c有误"));
        log("[TOUHOU] leaves check @ " + xyz(center) + " wrong=" + wrong
                + " leavesNearby=" + leafFound);
    }

    /**
     * <b>「为什么不存在刷物品循环」的实测证明</b>（{@code /touhou leaves proof <x> <y> <z>}）。
     *
     * <h2>要证明的三条前提</h2>
     * <pre>
     *   前提① 徒手/普通工具破坏树叶 ⇒ 掉落里【没有】树叶方块本身（只有树苗/木棍/苹果）
     *         ⇒ 「放置树叶 → 破坏」是【消耗一个方块换一次判定】，不是循环
     *   前提② 只有 剪刀 / 精准采集 能拿回树叶方块本身，而这两条【已被排除】
     *         ⇒ 「能回收方块的那条路」不掉落叶
     *   前提③ 落叶是 RecipeType.NULL ⇒ 没有任何配方、也变不回树叶方块
     *   ⇒ 结论：树叶方块只能被消耗、不能被回收 ⇒ 无循环
     * </pre>
     *
     * <p>★ 怎么"实测"而不只是断言：无头服造不出真玩家，所以<b>不模拟事件</b>，
     * 而是用<b>与掉落实质等价的路径</b>去读世界真实的掉落实体：
     * <pre>
     *   world.getBlockAt(...).setType(树叶)      // 放一块真的树叶
     *   用【普通工具】替换方块内容 → 收集世界新出现的 Item 实体 → 读它们的 type
     *   用【精准采集工具】再放一块、再替换 → 同样收集并读 type
     * </pre>
     * 「替换方块」会走原版那套"方块消失 → 生成掉落"的逻辑，
     * 所以掉落实体是<b>原版真实算出来的</b>，不是我们算的。
     * 同时把 {@code FallenLeaves#isExcludedTool} 的判据并排打出来 ——
     * 两条合起来正好证明"能回收方块的那条路不掉落叶"。
     *
     * <p>★ 测试用的树叶只动本命令指定的那几格，收尾全部清掉。
     */
    private void leavesProof(CommandSender sender, Location center) {
        World world = center.getWorld();
        int x = center.getBlockX();
        int y = center.getBlockY();
        int z = center.getBlockZ();
        Material leaf = Material.OAK_LEAVES;

        guideLine(sender, PREFIX + "\u00a7e落叶 · 「无刷物品循环」实测证明 @ " + xyz(center));
        guideLine(sender, "\u00a78  测试用树叶 = " + leaf + "（用 setType 放真方块、再换掉，读世界真实掉落实体）");

        // ---- 提前准备两块地：y 与 y-2（互不干扰）
        Block spotNormal = world.getBlockAt(x, y, z);
        Block spotSilk = world.getBlockAt(x, y - 2, z);
        world.getBlockAt(x, y - 1, z).setType(Material.AIR, false);
        world.getBlockAt(x, y - 3, z).setType(Material.AIR, false);

        // ---- 前提①：普通工具破坏 → 掉落里没有树叶方块
        //   工具用"同一把镐、有/无精准采集"，把差异唯一地归因到那个附魔上
        ItemStack plainTool = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack silkTool = new ItemStack(Material.DIAMOND_PICKAXE);
        silkTool.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.SILK_TOUCH, 1);

        List<String> normalDrops = breakAndCollect(world, spotNormal, leaf, plainTool);
        boolean normalHasLeafBlock = containsMaterial(normalDrops, leaf);
        guideLine(sender, "\u00a7e  前提① 普通工具（无精准采集的钻石镐）破坏 " + leaf);
        guideLine(sender, "\u00a78    实际掉落 = " + normalDrops);
        guideLine(sender, "\u00a78    含树叶方块本身(" + leaf + ") = " + normalHasLeafBlock
                + "  ⇒ " + (normalHasLeafBlock ? "\u00a7c【前提被推翻！有刷取漏洞】"
                        : "\u00a7a符合（拿不回方块）"));

        // ---- 前提①b：空手样本 —— 因为树叶掉树苗/木棍是**概率**的，
        //   单块破坏很可能什么都不掉（实测第一次就是 []），所以只打一块说明不了问题。
        //   这里连打 200 块，统计"到底掉出了什么、有没有掉出树叶方块"。
        guideLine(sender, "\u00a7e  前提①b 空手连破 200 块树叶的掉落汇总（概率掉落需要样本）");
        java.util.Map<String, Integer> tally = new java.util.TreeMap<>();
        int leafBlockHits = 0;
        int emptyHandBlocks = 200;
        ItemStack emptyHand = new ItemStack(Material.AIR);
        for (int i = 0; i < emptyHandBlocks; i++) {
            Block b = world.getBlockAt(x, y, z);
            for (String d : breakAndCollect(world, b, leaf, emptyHand)) {
                tally.merge(d, 1, Integer::sum);
                if (d.startsWith(leaf.name() + " x")) {
                    leafBlockHits++;
                }
            }
        }
        guideLine(sender, "\u00a78    掉落汇总 = " + (tally.isEmpty() ? "（一块都没掉）" : tally));
        guideLine(sender, "\u00a78    其中【树叶方块本身】出现次数 = " + leafBlockHits
                + " ⇒ " + (leafBlockHits == 0
                        ? "\u00a7a前提① 成立：空手拿不回树叶方块"
                        : "\u00a7c【前提被推翻！】空手竟然掉了树叶方块，有刷取漏洞"));
        clearDroppedItems(world, x, y, z);

        // ---- 前提②：精准采集破坏 → 有树叶方块本身、但【排除判据成立】⇒ 不掉落叶
        List<String> silkDrops = breakAndCollect(world, spotSilk, leaf, silkTool);
        boolean silkHasLeafBlock = containsMaterial(silkDrops, leaf);
        boolean silkExcluded = com.example.touhou.core.FallenLeaves.isExcludedTool(silkTool);
        boolean shearsExcluded =
                com.example.touhou.core.FallenLeaves.isExcludedTool(new ItemStack(Material.SHEARS));
        guideLine(sender, "\u00a7e  前提② 精准采集钻石镐破坏 " + leaf);
        guideLine(sender, "\u00a78    实际掉落 = " + silkDrops);
        guideLine(sender, "\u00a78    含树叶方块本身 = " + silkHasLeafBlock
                + "  ⇒ " + (silkHasLeafBlock ? "\u00a7a符合（精准采集确实拿得回方块）"
                        : "\u00a77本次没验证到（掉落表可能没按附魔重算）"));
        guideLine(sender, "\u00a78    isExcludedTool(精准采集镐) = " + silkExcluded + "（必须 true）");
        guideLine(sender, "\u00a78    isExcludedTool(剪刀) = " + shearsExcluded + "（必须 true）");
        guideLine(sender, "\u00a78    ⇒ 能回收树叶方块的两条路（剪刀 / 精准采集）都被排除判据挡住了");

        // ---- 前提③：落叶没有任何配方
        SlimefunItem fallen = SlimefunItem.getById(com.example.touhou.core.FallenLeaves.ID);
        guideLine(sender, "\u00a7e  前提③ 落叶的配方口径");
        if (fallen == null) {
            guideLine(sender, "\u00a7c    落叶未注册！");
        } else {
            int filled = 0;
            ItemStack[] grid = fallen.getRecipe();
            if (grid != null) {
                for (ItemStack c : grid) {
                    if (c != null && !c.getType().isAir()) {
                        filled++;
                    }
                }
            }
            guideLine(sender, "\u00a78    配方类型 = "
                    + com.example.touhou.core.Acquisition.describeRecipeType(fallen)
                    + "   非空格数 = " + filled);
            guideLine(sender, "\u00a78    Bukkit 配方表里以落叶为【产物】的配方 = " + countRecipesFor(fallen));
            guideLine(sender, "\u00a78    Slimefun 多方块配方表里以落叶为产物的 = "
                    + countMachineRecipesFor(fallen));
            guideLine(sender, "\u00a78    ⇒ 落叶变不回任何东西（尤其变不回树叶方块）");
        }

        // ---- 收尾：清掉测试用方块与实体
        clearDroppedItems(world, x, y, z);
        spotNormal.setType(Material.AIR, false);
        spotSilk.setType(Material.AIR, false);
        guideLine(sender, "\u00a78  已清掉测试方块与掉落物");
        log("[TOUHOU] leaves proof @ " + xyz(center)
                + " normalDrops=" + normalDrops + " normalHasLeafBlock=" + normalHasLeafBlock
                + " silkDrops=" + silkDrops + " silkExcluded=" + silkExcluded);
    }

    /**
     * 在 {@code spot} 放一块 {@code material}，用 {@code breakNaturally(tool)}
     * <b>走原版真正的破坏路径</b>，再收集世界新出现的 {@code Item} 实体并返回可读清单。
     *
     * <p>★ 第一版这里用的是 {@code setType(AIR)} —— <b>那是错的</b>：
     * 它只会把方块抹掉、<b>一个掉落都不产生</b>（实测拿到的是空列表 []），
     * 于是"掉落里没有树叶方块"就成了空话（空列表当然不含任何东西）。
     * 改用 {@code Block#breakNaturally(ItemStack)} —— 它执行的就是原版那套
     * "结算掉落 → 生成 Item 实体"的逻辑，所以掉落实体是<b>原版真的算出来的</b>。
     *
     * <p>★ 工具用同一把镐（有/无精准采集各一次）：除非附魔本身不同，其余 NBT 完全一致，
     * 于是两次的差异只能归因于那个附魔 —— 这正是"归因唯一"的做法。
     */
    private static List<String> breakAndCollect(World world, Block spot, Material material,
                                                ItemStack tool) {
        spot.setType(material, false);
        // 先记录换掉之前世界里的 Item 实体（我们只关心这次新出现的）
        java.util.Set<java.util.UUID> before = new java.util.HashSet<>();
        for (org.bukkit.entity.Entity en : world.getNearbyEntities(
                spot.getLocation().add(0.5D, 0.5D, 0.5D), 6.0D, 6.0D, 6.0D)) {
            if (en instanceof org.bukkit.entity.Item) {
                before.add(en.getUniqueId());
            }
        }
        spot.breakNaturally(tool);
        List<String> out = new ArrayList<>();
        for (org.bukkit.entity.Entity en : world.getNearbyEntities(
                spot.getLocation().add(0.5D, 0.5D, 0.5D), 6.0D, 6.0D, 6.0D)) {
            if (en instanceof org.bukkit.entity.Item it && !before.contains(it.getUniqueId())) {
                ItemStack stack = it.getItemStack();
                out.add(stack.getType() + " x" + stack.getAmount());
            }
        }
        return out;
    }

    /** 掉落清单里有没有某个材质。 */
    private static boolean containsMaterial(List<String> drops, Material material) {
        if (material == null || drops == null) {
            return false;
        }
        String needle = material.name() + " x";
        for (String d : drops) {
            if (d.startsWith(needle)) {
                return true;
            }
        }
        return false;
    }

    /** 清掉某点附近 6 格内的掉落物实体（证明实验的收尾）。 */
    private static int clearDroppedItems(World world, int x, int y, int z) {
        int n = 0;
        for (org.bukkit.entity.Entity en : world.getNearbyEntities(
                new Location(world, x + 0.5D, y + 0.5D, z + 0.5D), 8.0D, 8.0D, 8.0D)) {
            if (en instanceof org.bukkit.entity.Item) {
                en.remove();
                n++;
            }
        }
        return n;
    }

    /** 清掉测试树叶（只清 Tag.LEAVES 口径的方块，不动别的东西）。 */
    private int leavesClear(Block base, boolean unused) {
        World world = base.getWorld();
        int x = base.getX();
        int y = base.getY();
        int z = base.getZ();
        int cleared = 0;
        int radius = 64;    // 1000 格是按 32×32 铺的，这里给足
        for (int dx = 0; dx < radius; dx++) {
            for (int dz = 0; dz < radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    Block b = world.getBlockAt(x + dx, y + dy, z + dz);
                    if (com.example.touhou.core.FallenLeaves.isLeaves(b.getType())) {
                        b.setType(Material.AIR, false);
                        cleared++;
                    }
                }
            }
        }
        return cleared;
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
                    "power", "dreamcatcher", "seal", "lily", "lilywhite", "harvest", "leaves",
                    "acquisition", "echo", "proj", "guide"), args[0]);
        }
        if (args[0].equalsIgnoreCase("acquisition") && args.length == 2) {
            return filter(List.of("all", "rule"), args[1]);
        }
        if (args[0].equalsIgnoreCase("leaves") && args.length == 2) {
            return filter(List.of("selfcheck", "tools", "drop", "watch", "field", "check", "proof",
                    "clear"), args[1]);
        }
        if (args[0].equalsIgnoreCase("lilywhite") && args.length == 2) {
            return filter(List.of("selfcheck", "name", "recipe"), args[1]);
        }
        if (args[0].equalsIgnoreCase("harvest") && args.length == 2) {
            return filter(List.of("selfcheck", "test", "probe", "clear", "cell", "rng", "wake",
                    "cooldown"), args[1]);
        }
        if (args[0].equalsIgnoreCase("harvest") && args.length == 6
                && args[1].equalsIgnoreCase("wake")) {
            return filter(List.of("crops", "empty"), args[5]);
        }
        if (args[0].equalsIgnoreCase("harvest") && args.length == 3
                && args[1].equalsIgnoreCase("cooldown")) {
            return filter(List.of("clear"), args[2]);
        }
        // harvest cell <x> <y> <z> [面] ⇒ args.length == 5 时补第 5 个参数（面）
        if (args[0].equalsIgnoreCase("harvest") && args.length == 5
                && args[1].equalsIgnoreCase("cell")) {
            return filter(Arrays.stream(org.bukkit.block.BlockFace.values())
                    .map(Enum::name).collect(Collectors.toList()), args[4]);
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
                "dreamcatcher", "seal", "lily", "lilywhite", "harvest", "leaves", "proj");
    }
}
