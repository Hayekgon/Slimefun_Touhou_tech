package com.example.touhou.core;

import com.example.touhou.Touhou;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.NamespacedKey;

/**
 * 两个多方块核心的<b>自定义配方类型</b>。
 *
 * <h2>它解决什么问题</h2>
 * 指南的"配方页"槽 10 显示的图标是 {@code RecipeType#getItem(Player)} 的结果
 * （见 {@code SurvivalSlimefunGuide#displayItem} 里的 {@code menu.addItem(10, recipeType.getItem(p), …)}）。
 * 用 {@link RecipeType#NULL} 时那个方法返回的是<b>空气</b>（{@code LocalizationService#getRecipeTypeItem}
 * 里 {@code toItem() == null} 的分支），于是页面上"这台机器是什么"完全看不出来。
 * 换成这里定义的配方类型之后，槽 10 会显示核心自己的图标与名字。
 *
 * <h2>★★ 为什么它<b>不会</b>让核心变成可合成物品</h2>
 * 合成表是这样落地的：{@code SlimefunItem#load()} 调
 * {@code RecipeType#register(recipe, output)}，而那个方法只有两条路：
 * <pre>
 *   1. registerConsumer != null  → 调它（原版 ANCIENT_ALTAR / MOB_DROP 走这条）；
 *   2. 否则 SlimefunItem.getById(this.machine) instanceof MultiBlockMachine → 把配方加进那台多方块机器。
 * </pre>
 * 本类构造出来的配方类型：{@code registerConsumer} 为 {@code null}（
 * {@code RecipeType(NamespacedKey, SlimefunItemStack, String...)} 这条构造器不传回调），
 * 而 {@code machine} = 核心的物品 id，那个 {@code SlimefunItem} 是
 * {@link UtsuhoReactorCore} / {@link Saizenbako}（{@code AGenerator} / {@code PowerComponent} 的子孙），
 * <b>不是</b> {@code MultiBlockMachine}。两条路都不通 ⇒ <b>什么都不注册</b>。
 * 再加上两个核心传入的配方数组本来就是 {@code null} 填满的 9 格（{@code AddSlimefunItems#noRecipe}），
 * 于是工作台/增强工作台/任何机器里都摆不出这两个核心。
 *
 * <p>运行期由 {@code /touhou guide} 里的"可合成性核查"负责证明这一点：
 * 它遍历 {@code Bukkit.recipeIterator()} 数"产物是这两个核心的配方有几条"，正常必须是 0。
 *
 * <h2>为什么要有 setup() 而不是 static final 字段</h2>
 * {@code RecipeType(NamespacedKey, SlimefunItemStack, …)} 要吃 {@link SlimefunItemStack}
 * 当图标，而那些模板由 {@link AddItems#setup()} 创建。所以本类的初始化必须排在
 * {@code AddGroups → AddItems → 本类 → AddSlimefunItems} 这条链里
 * （与项目"静态初始化顺序不能乱"的约定一致，见 {@code Touhou#onEnable}）。
 */
public final class TouhouRecipeTypes {

    private TouhouRecipeTypes() {
    }

    /**
     * 反应堆核心的配方类型。
     *
     * <p>它承载的是 {@code UtsuhoReactorCore#getDisplayRecipes()} 那一页
     * （原油桶 → 逻辑奇点 + 发电参数）。
     */
    public static RecipeType REACTOR_CORE;

    /**
     * 赛钱箱（祭坛）核心的配方类型。
     *
     * <p>它承载的是 {@code Saizenbako#getDisplayRecipes()} 那一页
     * （{@link SaizenbakoRecipes} 里注册的每一条祈愿配方）。
     */
    public static RecipeType SAIZENBAKO;

    /**
     * 建好两个配方类型 —— 必须在 {@link AddItems#setup()} 之后、
     * {@link AddSlimefunItems#setup} 之前调用。
     */
    public static void setup() {
        REACTOR_CORE = core("reactor_core", AddItems.UTSUHO_REACTOR_CORE,
                "",
                "&a&o搭建完整的多方块结构后放入核心",
                "&8材料清单见物品描述");
        SAIZENBAKO = core("saizenbako", AddItems.SAIZENBAKO,
                "",
                "&a&o搭建完整的多方块结构后放入核心",
                "&8材料清单见物品描述");
    }

    /**
     * 造一个"只当门面、不落合成表"的配方类型。
     *
     * <p>{@code key} 只用插件名当命名空间（{@code touhou:xxx}）—— Slimefun 会拿它去
     * {@code recipes.yml} 里找 {@code touhou.xxx.name / .lore} 做本地化；找不到就
     * <b>原样保留物品自己的名字与 lore</b>，所以不配语言文件也不会显示成空白。
     */
    private static RecipeType core(String key, SlimefunItemStack icon, String... lore) {
        return new RecipeType(new NamespacedKey(Touhou.getInstance(), key), icon, lore);
    }

    /** 诊断：本模块的状态（{@code /touhou guide} 用）。 */
    public static String describe() {
        return "自定义配方类型：反应堆核心=" + keyOf(REACTOR_CORE) + " / 赛钱箱=" + keyOf(SAIZENBAKO);
    }

    private static String keyOf(RecipeType type) {
        return type == null ? "未创建" : type.getKey().toString();
    }
}
