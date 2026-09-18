package com.example.touhou;

import com.example.touhou.core.AddGroups;
import com.example.touhou.core.Log;
import com.example.touhou.core.StructureBuildListener;
import com.example.touhou.core.AddItems;
import com.example.touhou.core.AddSlimefunItems;
import com.example.touhou.core.FantasySealArrowListener;
import com.example.touhou.core.PortGuiListener;
import com.example.touhou.core.ReactorManager;
import com.example.touhou.core.SaizenbakoRecipes;
import com.example.touhou.core.SaizenbakoStructure;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * TH Tech 主类 —— 东方主题科技附属（Touhou）。
 *
 * <p>与 MyAddon 的关系：**两个完全独立的插件**，各自有 plugin.yml / config.yml / 命令。
 * 它们都只依赖 Slimefun，不互相依赖；TOUHOU 里唯一与 MyAddon 的关系是
 * 默认结构层图复用了 MyAddon 的测试方块 id（可在 config.yml 里改掉）。
 *
 * <p>生命周期顺序不能乱（与 Slimefun 附属的通用约定一致）：
 * <pre>
 *   AddGroups（分类） → AddItems（物品模板） → AddSlimefunItems（物品本体 + 配方）
 * </pre>
 */
public class Touhou extends JavaPlugin implements SlimefunAddon {

    private static Touhou instance;

    public static Touhou getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // 存档默认配置
        saveDefaultConfig();

        // 1. 分类（TH Tech 物品组层级）
        AddGroups.setup(this);
        // 2. 物品模板（SlimefunItemStack）
        AddItems.setup();
        // 3. 物品本体 + 配方
        AddSlimefunItems.setup(this);
        Log.info(AddSlimefunItems.describe());

        // 3.5 赛钱箱配方表（必须在物品注册之后：配方里引用的是物品 id，见 SaizenbakoRecipes）
        SaizenbakoRecipes.setup();
        Log.info("[SAIZEN] " + SaizenbakoRecipes.describe().get(0));

        // 4. 监听器：梦想封印 集的弹幕无敌帧处理
        getServer().getPluginManager().registerEvents(new FantasySealArrowListener(), this);
        //    物流接口界面的拖拽拦截（按槽拦截挡不住拖拽，见 PortGuiListener 注释）
        getServer().getPluginManager().registerEvents(new PortGuiListener(), this);
        //    结构变动（放置/破坏/爆炸）→ 触发一次检测（见 StructureBuildListener 注释）
        getServer().getPluginManager().registerEvents(new StructureBuildListener(), this);

        // 无头自检 + 充能循环：关键参数打到控制台，方便不进游戏就能核对
        if (AddSlimefunItems.FANTASY_SEAL != null) {
            for (String line : AddSlimefunItems.FANTASY_SEAL.selfCheck()) {
                Log.info("[SEAL] " + line);
            }
            // ★ 为什么充能循环在这里起、而不是在 FantasySeal 的构造器里：
            //   构造器跑在"物品注册"那一刻（onEnable 中途），那时整套注册流程还没走完；
            //   放到注册之后、onEnable 的末尾启动，时序最直观，也不存在
            //   "任务已经跑起来、物品却还没注册好"的窗口（见 FantasySeal#startCharging）。
            AddSlimefunItems.FANTASY_SEAL.startCharging();
        }

        // 结构预热：层图写错会在启动时就被发现，而不是等玩家放下机器才炸
        ReactorManager.structure();
        //    赛钱箱那份层图同理：木桩数量不对（≠6）会在这里直接抛异常
        for (String line : SaizenbakoStructure.get().describe()) {
            Log.info("[SAIZEN] " + line);
        }
        //    投影落点表的"委托自检"：包装类漏转发接口 default 方法时立刻炸（见 SaizenbakoStructure#cells）
        SaizenbakoStructure.get().verifyDelegation();
        Log.info("[SAIZEN] 投影落点自检: 反应堆 "
                + ReactorManager.structure().cells().size() + " 格（可画 "
                + com.example.touhou.core.ReactorStructure.solidCells(
                        ReactorManager.structure().cells()).size() + " 格） / 赛钱箱 "
                + SaizenbakoStructure.get().cells().size() + " 格（可画 "
                + com.example.touhou.core.ReactorStructure.solidCells(
                        SaizenbakoStructure.get().cells()).size() + " 格）");

        setupCommands();

        Log.info("Touhou (TH Tech) enabled!");
    }

    private void setupCommands() {
        PluginCommand cmd = getCommand("touhou");
        if (cmd == null) {
            getLogger().warning("plugin.yml 里缺少 touhou 命令声明，调试命令不可用");
            return;
        }
        TohouCommand handler = new TohouCommand();
        cmd.setExecutor(handler);
        cmd.setTabCompleter(handler);
    }

    @Override
    public void onDisable() {
        // ★ 多方块投影是【实体】，不是方块 —— 它们不会随插件卸载自己消失。
        //   参照 LogiTech 的 ScheduleSave.addFinalTask：关服时全清。
        //   （另一道保险是生成时就 setPersistent(false)，见 MultiBlockProjection 类注释）
        try {
            com.example.touhou.core.MultiBlockProjection.clearAll();
        } catch (RuntimeException e) {
            // 关服路径上绝不能因为清理失败而抛出去
            getLogger().warning("[投影] 关服清理异常：" + e);
        }
        getServer().getScheduler().cancelTasks(this);
    }

    /** SlimefunAddon 契约：返回插件实例 */
    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    /** SlimefunAddon 契约：Issue 追踪地址，可以返回 null */
    @Override
    public String getBugTrackerURL() {
        return null;
    }
}
