package com.example.touhou.core;

import com.example.touhou.Touhou;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.NamespacedKey;

/**
 * 多方块核心与特殊机制的<b>自定义配方类型</b>（"只当门面、不落合成表"）。
 *
 * <h2>它解决什么问题</h2>
 * 指南的"配方页"槽 10 显示的图标是 {@code RecipeType#getItem(Player)} 的结果
 * （见 {@code SurvivalSlimefunGuide#displayItem} 里的 {@code menu.addItem(10, recipeType.getItem(p), …)}）。
 * 用 {@link RecipeType#NULL} 时那个方法返回的是<b>空气</b>（{@code LocalizationService#getRecipeTypeItem}
 * 里 {@code toItem() == null} 的分支），于是页面上"这台机器是什么"完全看不出来。
 * 换成这里定义的配方类型之后，槽 10 会显示核心自己的图标与名字。
 *
 * <h2>★★ 为什么它<b>不会</b>让物品变成可合成物品</h2>
 * 合成表是这样落地的：{@code SlimefunItem#load()} 调
 * {@code RecipeType#register(recipe, output)}，而那个方法只有两条路：
 * <pre>
 *   1. registerConsumer != null  → 调它（原版 ANCIENT_ALTAR / MOB_DROP 走这条）；
 *   2. 否则 SlimefunItem.getById(this.machine) instanceof MultiBlockMachine → 把配方加进那台多方块机器。
 * </pre>
 * 本类构造出来的配方类型：{@code registerConsumer} 为 {@code null}（
 * {@code RecipeType(NamespacedKey, SlimefunItemStack, String...)} 这条构造器不传回调），
 * 而 {@code machine} = 物品自己的 id，那个 {@code SlimefunItem} 是
 * {@link UtsuhoReactorCore}（{@code AGenerator} 的子孙）/ {@link EchoOfAnotherWorld}
 * （普通物品），<b>不是</b> {@code MultiBlockMachine}。两条路都不通 ⇒ <b>什么都不注册</b>。
 * 再加上传进来的配方数组本来就是 {@code null} 填满的 9 格（{@code AddSlimefunItems#noRecipe}），
 * 于是任何工作台/机器里都摆不出它们。
 *
 * <p>运行期由 {@code /touhou guide} 里的"可合成性核查"负责证明这一点：
 * 它遍历 {@code Bukkit.recipeIterator()} 数"产物是这些物品的配方有几条"，正常必须是 0。
 *
 * <p>★★ <b>2026-09-24 变更</b>：「赛钱箱」拿到了用户给的<b>真实</b>魔法工作台配方，
 * 所以它<b>不再</b>使用本类的门面（{@code SAIZENBAKO} 那个类型已随之下线）——
 * 门面类型与"真配方"是互斥的：有真配方就该让指南页槽 10 显示那台真实的机器。
 * <p>★★ <b>2026-09-25 新增</b>：{@link #SAIZEN_ALTAR}（赛钱箱<b>祈愿</b>的门面）——
 * 祈愿产出的物品（空白符卡 / 灵梦的大蝴蝶结 / 梦想封印 集）自己没有 9 格配方，
 * 真配方在 {@link SaizenbakoRecipes} 那张 6 槽表里，所以只能用门面表达"来源 = 祭坛"。
 * 现在本类共三个：反应堆核心、维度穿梭、赛钱箱祈愿。
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
     * （原油桶 → 炙热的灰烬 + 发电参数）。
     *
     * <p>⚠ 注意：反应堆核心的<b>配方类型</b>其实是
     * {@code RecipeType.ENHANCED_CRAFTING_TABLE}（它有真的增强型工作台配方）；
     * 本类型只出现在"自定义配方页"的展示里，与槽 10 无关。
     */
    public static RecipeType REACTOR_CORE;

    /**
     * 「维度穿梭」的配方类型 —— 本项目的第三个门面类型。
     *
     * <p>它承载的是 {@link EchoOfAnotherWorld#getDisplayRecipes()} 那一页
     * （能量水晶 → 另一个世界的回响，1:1）。
     *
     * <p>★ 为什么这一件也走"门面类型"而不是 {@link RecipeType#NULL}：
     * 与两个核心同理 —— 槽 10 用 {@code NULL} 时显示的是空气，玩家看不出
     * "这件东西到底是怎么来的"。换成它之后槽 10 会显示
     * <b>「维度穿梭」的图标与名字</b>，正好把获取方式写在物品页上。
     *
     * <p>★ 它同样<b>不会</b>让回响变成可合成物品：本类型由 {@link #core} 造出
     * （{@code registerConsumer == null}），而 {@code machine} 指向的那个
     * {@code SlimefunItem} 是 {@link EchoOfAnotherWorld}（普通 {@code SlimefunItem}，
     * 不是 {@code MultiBlockMachine}），两条注册路径都不通；再加上物品传入的配方数组是
     * {@code AddSlimefunItems#noRecipe()} 的 9 格全空 —— 工作台/增强型工作台/任何机器里
     * 都摆不出它。运行期由 {@code /touhou guide echo} 的"可合成性核查"证明。
     *
     * <p>key 取 {@code touhou:dimension_shuttle}（「维度穿梭」的直译）。
     */
    public static RecipeType DIMENSION_SHUTTLE;

    /**
     * 「赛钱箱（祭坛）祈愿」的配方类型 —— 本项目的第四个门面类型（2026-09-25 新增）。
     *
     * <p>它承载的是 {@link SaizenbakoRecipes} 里注册的每一条祈愿配方
     * （6 根木桩各放什么 → 产出什么）。把它挂在"由祈愿产出的物品"上当 {@code RecipeType}，
     * 指南页<b>槽 10</b> 就会显示赛钱箱图标 + "在赛钱箱（祭坛）里祈愿产出"那两行说明，
     * 而不是一片空气。
     *
     * <p>★★ 为什么<b>不能</b>用本体的 {@code RecipeType.ANCIENT_ALTAR}：
     * 那是本体<b>另一台机器</b>（古代祭坛：9 格 + {@code registerConsumer} 会把配方写进
     * {@code AncientAltar.getRecipes()}）。我们的赛钱箱祈愿是自研机制（6 个槽、按木桩编号、
     * 走 {@link SaizenbakoRecipe} 匹配），配方根本不在物品的 9 格数组里 ——
     * 用 {@code ANCIENT_ALTAR} 等于真的把配方塞进古代祭坛，玩家会在古代祭坛里合成出它们。
     *
     * <p>★ 与核心那个老门面（{@code reactor_core} / {@code dimension_shuttle}）同一形态：
     * {@link #core} 造出来的类型 {@code registerConsumer == null}，而 {@code machine} 指向的
     * 是本插件自己的物品（不是 {@code MultiBlockMachine}）⇒ 两条注册路径都不通
     * ⇒ <b>什么都不注册</b>；再加上这些物品传入的配方数组是
     * {@code AddSlimefunItems#noRecipe()} 的 9 格全空，所以任何台子里都摆不出来
     * （真配方在 {@link SaizenbakoRecipes} 那张表里）。
     *
     * <p>key 取 {@code touhou:saizen_altar}。
     */
    public static RecipeType SAIZEN_ALTAR;

    /**
     * 建好三个门面配方类型 —— 必须在 {@link AddItems#setup()} 之后、
     * {@link AddSlimefunItems#setup} 之前调用。
     */
    public static void setup() {
        REACTOR_CORE = core("reactor_core", AddItems.UTSUHO_REACTOR_CORE,
                "",
                "&a&o搭建完整的多方块结构后放入核心",
                "&8材料清单见物品描述");
        // 维度穿梭：图标就用产出物「另一个世界的回响」本身，
        // 于是指南槽 10 一眼就是"这件东西 + 它的获取方式名"。
        DIMENSION_SHUTTLE = core("dimension_shuttle", AddItems.ECHO_OF_ANOTHER_WORLD,
                "",
                "&a&o穿过维度之门（主世界 ↔ 地狱）",
                "&8身上的能量水晶会化作 1 个另一个世界的回响");
        // 赛钱箱祈愿：图标用核心「赛钱箱」自己 —— 槽 10 一眼就是"在哪台机器上做"。
        // ★ 那 6 个槽要放什么、放多少，看指南页底部的自定义配方页
        //   （Saizenbako#getDisplayRecipes()，数据源就是 SaizenbakoRecipes）。
        SAIZEN_ALTAR = core("saizen_altar", AddItems.SAIZENBAKO,
                "",
                "&a&o在赛钱箱（祭坛）里祈愿产出",
                "&86 根木桩各放什么见指南页底部的配方页");
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
        return "自定义配方类型：反应堆核心=" + keyOf(REACTOR_CORE)
                + " / 维度穿梭=" + keyOf(DIMENSION_SHUTTLE)
                + " / 赛钱箱祈愿=" + keyOf(SAIZEN_ALTAR)
                + "（赛钱箱本身是真实魔法工作台配方，没有门面）";
    }

    /**
     * 这个配方类型是不是本类专供的门面（反应堆核心 / 维度穿梭 / 赛钱箱祈愿）。
     *
     * <p>★ 用途：{@link Acquisition#applyFacades()} 要靠它判断"这件物品已经有
     * 一个更具体的槽 10 说明" —— 那几个门面带着"搭建完整结构 + 材料清单"之类的
     * 定制文字，<b>不能被通用的获取方式门面覆盖掉</b>。
     *
     * <p>⚠ 2026-09-24：赛钱箱<b>本体</b>那一支已移除（它拿到了真配方，
     * <b>本来就不会</b>被通用门面覆盖 —— {@code applyFacades} 对"有真配方的物品"直接跳过）；
     * 2026-09-25 起本类多了一个 {@link #SAIZEN_ALTAR}，挂在"由祈愿产出的物品"上。
     */
    public static boolean isDedicatedFacade(RecipeType type) {
        return type != null
                && (type == REACTOR_CORE || type == DIMENSION_SHUTTLE || type == SAIZEN_ALTAR);
    }

    private static String keyOf(RecipeType type) {
        return type == null ? "未创建" : type.getKey().toString();
    }
}
