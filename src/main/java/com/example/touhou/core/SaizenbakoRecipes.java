package com.example.touhou.core;

import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import org.bukkit.Material;
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
     * <h2>历史</h2>
     * <ul>
     *   <li>原「赛钱箱-基础祈愿」（红色染料 / 钻石 / 下界之星 / POWER集成核心 / 红色染料 /
     *       红色染料 → POWER存储单元 ×4）于 <b>2026-09-24</b> 按用户要求<b>删除</b>
     *       （POWER存储单元 改走魔法工作台）；<b>不恢复</b>。</li>
     *   <li><b>2026-09-25</b> 按用户要求新增下面这 <b>3 条</b>祈愿配方（空白符卡 / 灵梦的大蝴蝶结 /
     *       梦想封印 集），于是这台机器从"0 条配方、永远不会产出"回到可用状态。</li>
     * </ul>
     *
     * <h2>★★ 注册顺序与"不会互相抢配方"的结论</h2>
     * 匹配是"按注册顺序取第一条命中的"，所以更特殊的要放前面。本表这 3 条<b>互不冲突</b>：
     * 每一条都在<b>别的配方也有约束</b>的槽位上放了不同的东西 ——
     * 例如 #1 分别是「纸×12 / 白色染料×1 / POWER存储单元×2」，三者材质或粘液 id 两两不同，
     * 而 {@link SaizenbakoRecipe#matches} 要求<b>该槽位存在且满足</b>，所以一条配方摆出来的料
     * 不可能同时满足另一条。运行期由 {@code /touhou altar test} 的<b>交叉匹配矩阵</b>证明
     * （每条配方只命中它自己）。★ 数量判据是"不小于"（{@code >=}），多放不会改变命中对象。
     */
    public static void setup() {
        RECIPES.clear();

        // ---------------- 配方 1：空白符卡（材料 → 符卡半成品）----------------
        //   #0 POINT×4  #1 纸×12  #2 青金石×8  #3 另一个世界的回响×4
        //   #4 红色染料×16  #5 红石×24  →  空白符卡×1
        // ★★ 以下三条都【刻意不调 .note(...)】：note 是**玩家可见文案**（配方页 / 配方展示都会打出来），
        //   禁止写日期、"用户给定"这类改动来历 —— 见 SKILL.md 第 13 条硬规则。
        //   （"这三条是 2026-09-25 需求给的"这件事只留在代码注释里，玩家看不到。）
        register(SaizenbakoRecipe.of("空白符卡")
                .slot(0, AddItems.POINT, 4)
                .slot(1, new ItemStack(Material.PAPER), 12)
                .slot(2, new ItemStack(Material.LAPIS_LAZULI), 8)
                .slot(3, AddItems.ECHO_OF_ANOTHER_WORLD, 4)
                .slot(4, new ItemStack(Material.RED_DYE), 16)
                .slot(5, new ItemStack(Material.REDSTONE), 24)
                .output(AddItems.BLANK_SPELLCARD, 1)
                .build());

        // ---------------- 配方 2：灵梦的大蝴蝶结 ----------------
        //   #0 红色染料×4  #1 白色染料×1  #2 布×4  #3 布×4  #4 POINT×1  #5 回响×2
        //   →  灵梦的大蝴蝶结×1
        // ★★ 「布」= {@code SlimefunItems.CLOTH}（粘液本体的物品），**不是**原版白羊毛：
        //   运行期读数 `/touhou names sf` 打出的是
        //   `field=CLOTH id=CLOTH material=PAPER name=布`，
        //   而 {@code Material.WHITE_WOOL} 的客户端官方译名是"白色羊毛" ——
        //   两者是**不同身份**的物品，而匹配判据是"任意一边是粘液物品就必须两边都是、且 id 相同"
        //   （见 {@link SaizenbakoRecipe#sameItem}）：写成原版白羊毛的话，
        //   玩家拿粘液的「布」反而摆不出来（材质也不同：CLOTH 是 PAPER）。
        register(SaizenbakoRecipe.of("灵梦的大蝴蝶结")
                .slot(0, new ItemStack(Material.RED_DYE), 4)
                .slot(1, new ItemStack(Material.WHITE_DYE), 1)
                .slot(2, SlimefunItems.CLOTH, 4)
                .slot(3, SlimefunItems.CLOTH, 4)
                .slot(4, AddItems.POINT, 1)
                .slot(5, AddItems.ECHO_OF_ANOTHER_WORLD, 2)
                .output(AddItems.REIMU_RIBBON, 1)
                .build());

        // ---------------- 配方 3：梦想封印 集 ----------------
        //   #0 无（不限制）  #1 POWER存储单元×2  #2 空白符卡×1  #3 大蝴蝶结×4
        //   #4 红色染料×16   #5 无（不限制）      →  梦想封印 集×1
        // ★ #0 / #5 用户写的是"无"⇒ **不登记**这两格（本类约定：没登记的编号 = 该格不限制）。
        //   ⚠ 别用 emptySlot()：它同样只是"不登记"，措辞容易被误读成"必须为空"。
        // ★ 这条替代了梦想封印 集原来的增强型工作台配方（goheiRecipe，已按用户要求删除）。
        register(SaizenbakoRecipe.of("梦想封印 集")
                .slot(1, AddItems.POWER_STORAGE_UNIT, 2)
                .slot(2, AddItems.BLANK_SPELLCARD, 1)
                .slot(3, AddItems.REIMU_RIBBON, 4)
                .slot(4, new ItemStack(Material.RED_DYE), 16)
                .output(AddItems.FANTASY_SEAL, 1)
                .build());
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

    /**
     * <b>纯逻辑匹配自测</b> —— 不需要世界 / 不需要真机器 / 不需要玩家。
     *
     * <p>为什么要它：{@code /touhou saizen <x> <y> <z> recipe} 要求那里真的立着一台赛钱箱
     * （{@code BlockStorage.check} 得是 {@link Saizenbako}），无头环境里没摆结构就读不到；
     * 而匹配逻辑本身是<b>纯函数</b>（{@link #match} → {@link SaizenbakoRecipe#matches} →
     * {@code Ingredient.matches} → {@code sameItem}），完全可以脱离世界直接跑。
     *
     * <p>对每条配方做四件事（全部走<b>机器用的那同一个</b> {@link #match} 入口）：
     * <ol>
     *   <li><b>正例</b>：按这条配方自己的 6 个槽位合成一份投料（数量按配方要求），
     *       期望 {@code match(...)} 返回<b>它自己</b>；</li>
     *   <li><b>交叉矩阵</b>：同一份投料丢给其它配方，期望<b>都不命中</b>
     *       —— 这就是"三条配方不会互相抢"的证据；</li>
     *   <li><b>反例 A（差一格）</b>：把第一个受约束的槽换成别的物品，期望不命中它自己；</li>
     *   <li><b>反例 B（数量差 1）</b>：把第一个受约束的槽的数量减到要求 −1，期望不命中
     *       （数量判据是"不小于"）。</li>
     * </ol>
     *
     * @return 逐行文本（ASCII 摘要行 + 给人看的细节），供 {@code /touhou altar test} 打印
     */
    public static List<String> selfTest() {
        List<String> out = new ArrayList<>();
        out.add("赛钱箱配方匹配自测（纯逻辑，与机器用的是同一个 match()）：共 " + RECIPES.size() + " 条");
        int failures = 0;
        for (SaizenbakoRecipe r : RECIPES) {
            ItemStack[] feed = feedFor(r);
            if (feed == null) {
                out.add("  × " + r.id() + "：一个受约束的槽位都没有，无法自测");
                failures++;
                continue;
            }
            SaizenbakoRecipe hit = match(feed);
            boolean self = hit == r;
            if (!self) {
                failures++;
            }
            // 交叉矩阵：同一份投料还命中了别的配方吗？
            List<String> cross = new ArrayList<>();
            for (SaizenbakoRecipe other : RECIPES) {
                if (other != r && other.matches(feed)) {
                    cross.add(other.id());
                }
            }
            if (!cross.isEmpty()) {
                failures++;
            }
            // 反例 A：差一格（把第一个受约束的槽换成"另一种东西"）
            int first = firstConstrainedSlot(r);
            ItemStack[] wrongItem = feed.clone();
            wrongItem[first] = new ItemStack(Material.STONE);
            boolean wrongItemMissed = !r.matches(wrongItem);
            // 反例 B：数量差 1
            ItemStack[] tooFew = feed.clone();
            ItemStack less = tooFew[first].clone();
            less.setAmount(Math.max(1, less.getAmount() - 1));
            boolean tooFewExpected = less.getAmount() < r.ingredientAt(first).amount();
            tooFew[first] = less;
            boolean tooFewMissed = !r.matches(tooFew);
            if (!wrongItemMissed || !tooFewMissed) {
                failures++;
            }
            out.add("  " + (self && cross.isEmpty() && wrongItemMissed && tooFewMissed ? "✔" : "×")
                    + " " + r.id()
                    + "  正例命中自己=" + self + "（match 返回 " + (hit == null ? "null" : hit.id()) + "）"
                    + "  交叉命中=" + (cross.isEmpty() ? "无" : cross)
                    + "  反例·差一格不命中=" + wrongItemMissed
                    + "  反例·数量差1不命中=" + tooFewMissed
                    + (tooFewExpected ? "" : "（该槽数量本就是 1，减 1 后仍按 ≥ 判定，见注释）"));
        }
        out.add("自测结论：" + (failures == 0 ? "全部通过" : failures + " 项不通过"));
        return out;
    }

    /**
     * 合成一份"完全按配方要求"的投料数组（数量按配方，未登记的槽位为 {@code null}）。
     *
     * <p>★ 未登记的槽位给 {@code null}：{@link SaizenbakoRecipe#matches} 对"不限制"的槽位
     * 直接跳过，所以 {@code null} 正是"这一格随便"的等价物。
     */
    private static ItemStack[] feedFor(SaizenbakoRecipe recipe) {
        ItemStack[] feed = new ItemStack[SaizenbakoRecipe.SLOTS];
        boolean any = false;
        for (int i = 0; i < SaizenbakoRecipe.SLOTS; i++) {
            SaizenbakoRecipe.Ingredient ing = recipe.ingredientAt(i);
            if (ing == null) {
                continue;
            }
            any = true;
            ItemStack stack = ing.template();
            stack.setAmount(ing.amount());
            feed[i] = stack;
        }
        return any ? feed : null;
    }

    /** 这条配方里编号最小的受约束槽位（用于造反例）。 */
    private static int firstConstrainedSlot(SaizenbakoRecipe recipe) {
        for (int i = 0; i < SaizenbakoRecipe.SLOTS; i++) {
            if (recipe.ingredientAt(i) != null) {
                return i;
            }
        }
        return 0;
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
