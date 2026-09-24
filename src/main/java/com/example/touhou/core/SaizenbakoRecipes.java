package com.example.touhou.core;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 赛钱箱的<b>配方注册表</b> —— 加配方只改这一个文件。
 *
 * <h2>注册入口（唯一）</h2>
 * <pre>
 *   SaizenbakoRecipes.register(
 *       SaizenbakoRecipe.of("我的配方")
 *           .slot(0, new ItemStack(Material.RED_DYE), 1)
 *           .slot(2, new ItemStack(Material.NETHER_STAR), 16)   // 2 号木桩放 16 个
 *           .output(new ItemStack(Material.DIAMOND, 4))
 *           .build());
 * </pre>
 * <ul>
 *   <li>{@code slot(编号, 物品, 数量)} —— 编号 0~5 对应"6 根木桩按 +X→+Z 顺序的编号"，
 *       也就是核心的 6 个预留槽；<b>数量是每个槽位独立的</b>，
 *       "某个木桩里放 N 个同种物品"就写在这个参数上；</li>
 *   <li>{@code slot(编号, 物品)} —— 数量 1 的简写；</li>
 *   <li>{@code output(物品, 数量)} —— 产物；</li>
 *   <li>没登记的编号 = <b>该格不限制</b>（放什么都行、也不参与消耗）；</li>
 *   <li>匹配是<b>按注册顺序取第一条命中的</b> —— 更特殊的配方注册在前面。</li>
 * </ul>
 *
 * <h2>消耗与产出的落点</h2>
 * <ul>
 *   <li>消耗：<b>木桩自己的 IO 槽</b>（玩家唯一的投料口，预留槽是只读镜像）；</li>
 *   <li>产出：赛钱箱 GUI 的 IO 槽（下标 {@link Saizenbako#IO_SLOT}），
 *       外界物流可以把它抽走。</li>
 * </ul>
 */
public final class SaizenbakoRecipes {

    /** 注册表（注册顺序 = 匹配优先级）。 */
    private static final List<SaizenbakoRecipe> RECIPES = new ArrayList<>();

    private SaizenbakoRecipes() {
    }

    /**
     * 装配内置配方 —— 由 {@code Touhou#onEnable} 在物品注册之后调用。
     *
     * <p>★ 为什么要在物品注册之后：配方里引用的是 {@link AddItems} 的
     * {@code SlimefunItemStack}，它们的 id 就是匹配判据（见
     * {@link SaizenbakoRecipe#sameItem}）。物品模板没建好之前注册，配方里就会是空 id。
     *
     * <h2>★★ 2026-09-24：原有的那条配方已被用户要求删除，这里现在是【空的】</h2>
     * 删掉的是「赛钱箱-基础祈愿」（6 根木桩依次放 红色染料 / 钻石 / 下界之星 /
     * POWER集成核心 / 红色染料 / 红色染料 → 产出 POWER存储单元 ×4）——
     * 用户的原话是"POWER存储单元：<b>先去掉原有的祭坛配方</b>，再改为魔法工作台"
     * （新的魔法工作台配方见 {@code AddSlimefunItems#powerStorageUnitRecipe()}）。
     *
     * <p>★ 删掉之后这台机器<b>还能不能运作</b>（如实说明，别让它在代码里静默变成空壳）：
     * <ul>
     *   <li><b>结构 / 激活 / GUI / POWER 存储照常</b> —— 那些都不依赖配方表；</li>
     *   <li>配方表 <b>0 条</b> ⇒ {@link SaizenbakoManager} 每轮都会得到
     *       {@code match() == null}，于是在核心 GUI 上显示
     *       「<b>无匹配配方（6 个预留槽需同时满足）</b>」（不是静默：见
     *       {@code SaizenbakoManager} 的待机分支），<b>永远不会产出</b>；</li>
     *   <li>{@code /touhou saizen … seed} 会直接报「× 一条配方都没注册」；</li>
     *   <li>核心的配方页会提示「请在 SaizenbakoRecipes.setup() 里注册一条」。</li>
     * </ul>
     * 也就是说：<b>祭坛机制本身保留、但当前没有任何可用的祈愿配方</b>。
     * 以后要恢复，就在下面按 {@link #register} 的写法补一条（注册顺序 = 匹配优先级）。
     */
    public static void setup() {
        RECIPES.clear();

        // ---------------- 目前没有内置配方 ----------------
        // （原来那条「赛钱箱-基础祈愿」按用户要求于 2026-09-24 删除，见方法注释。）
    }

    /**
     * 注册一条配方（<b>加配方就调它</b>）。
     *
     * <p>允许在 {@link #setup()} 之外调用（例如将来做配置文件驱动的配方），
     * 但要注意："更特殊的配方注册在前面"这条优先级规则由调用顺序决定。
     */
    public static void register(SaizenbakoRecipe recipe) {
        if (recipe == null) {
            return;
        }
        RECIPES.add(recipe);
    }

    /**
     * 找出第一条被 6 个预留槽<b>同时</b>满足的配方。
     *
     * @param reservedSlots 长度 ≥ 6，下标 = 编号（{@code null} = 空槽）
     * @return 命中的配方；没有命中返回 {@code null}
     */
    public static SaizenbakoRecipe match(ItemStack[] reservedSlots) {
        for (SaizenbakoRecipe r : RECIPES) {
            if (r.matches(reservedSlots)) {
                return r;
            }
        }
        return null;
    }

    /** 全部已注册配方（只读）。 */
    public static List<SaizenbakoRecipe> all() {
        return Collections.unmodifiableList(RECIPES);
    }

    /** 配方条数。 */
    public static int count() {
        return RECIPES.size();
    }

    /** 诊断：逐行打印全部配方。 */
    public static List<String> describe() {
        List<String> out = new ArrayList<>();
        out.add("已注册赛钱箱配方 " + RECIPES.size() + " 条（按注册顺序匹配）");
        for (SaizenbakoRecipe r : RECIPES) {
            out.addAll(r.describe());
        }
        return out;
    }
}
