package com.example.touhou.core;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import java.util.ArrayList;
import java.util.List;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 模板：TOUHOU 自研「6×6 大型合成工作台」。
 *
 * <p>这是<b>骨架</b>，不是能直接注册的产品代码。拿去用时按下面四处替换：
 * <ol>
 *   <li>{@code Touhou.getInstance()} 的 NamespacedKey 前缀 → 本项目实际写法；</li>
 *   <li>{@code charge(...) / removeCharge(...)} → 接本项目的 POWER 能源网络
 *       （{@code PowerComponent#powerCharge} / {@code powerSetCharge}，见 modules/05）；</li>
 *   <li>{@code Notify.workbench()} → 新建一条属于本机器自己的 {@code Scope}
 *       （modules/04 §5，别蹭别人的前缀）；</li>
 *   <li>{@code openRecipeMenu(...)} → 本项目 {@code RecipePages} 的展示方式。</li>
 * </ol>
 *
 * <p>语义全部来自 LogiTech 的 {@code BugCrafter} / {@code AbstractWorkBench} / {@code CraftUtils}；
 * 证据与踩坑见 skill 模块 {@code modules/12-large-workbench.md}。
 *
 * <p>★ 三条绝不能改的硬约束（改了就是"永不匹配"）：
 * <pre>
 *   ① INPUT_SLOTS 的**数组顺序**就是配方 ItemStack[] 的下标顺序（GUI 槽号不连续也要按阅读顺序排）；
 *   ② ORDERED 配方的空位必须是 **null 占位**，不能被"过滤掉空气"的那套逻辑压扁；
 *   ③ 输入槽数量必须 ≥ 配方长度（少了那条配方直接判 0，不报错、不提示）。
 * </pre>
 *
 * <p>★ 另一条工程约定（本项目特有，LogiTech 那边是隐式的）：
 * <b>按钮的业务点击在 {@code newInstance(BlockMenu, Block)} 里挂</b>，
 * {@code constructMenu} 只负责图标 + 锁。原因：{@code constructMenu} 跑在
 * {@code init()}（构造期，没有 Block / Location），拿不到 {@code BlockMenu}，
 * 而合成、摆料、开配方菜单三件事都要它。
 */
public class LargeWorkbenchTemplate extends SlimefunItem {

    // ================================================================ 槽位常量

    /** 界面尺寸：6 行。 */
    public static final int INV_SIZE = 54;

    /** 6 行 × 6 列 = 36 格输入。每行只用前 6 列，第 7/8/9 列（6,7,8）留给边框与按钮。 */
    public static final int INPUT_ROWS = 6;
    public static final int INPUT_COLS = 6;

    /**
     * ★★ 输入槽数组 —— <b>阅读顺序</b>（行优先），<b>不是</b> GUI 里连续排。
     *
     * <p>它的下标 i 直接对应配方 {@code input[i]}，所以这个数组是"配方坐标系"的唯一出处。
     */
    public static final int[] INPUT_SLOTS = buildInputSlots();

    /** 产物槽：第 4/5/6 行右侧各 2 格（6 个槽，够放一条配方的多产出）。 */
    public static final int[] OUTPUT_SLOTS = {34, 35, 43, 44, 52, 53};

    /** 点我合成（第 2 行第 8 列）。 */
    public static final int CRAFT_SLOT = 16;
    /** 点一下放入上一次的配方（第 3 行第 8 列）。 */
    public static final int LAST_RECIPE_SLOT = 25;
    /** 打开配方菜单（第 1 行第 8 列）。 */
    public static final int RECIPE_MENU_SLOT = 7;

    /** 输入侧的边框/占位（每行第 7 列，位于输入区右侧）。 */
    public static final int[] BORDER_IN = {6, 8, 15, 17, 24, 26};
    /** 产物侧的连线纹理（每行第 7 列，位于产物槽左边）。 */
    public static final int[] BORDER_OUT = {33, 42, 51};

    /** 一次点击最多合成几个（本工作台自己的上限，与电量共同决定实际上限）。 */
    public static final int CRAFT_LIMIT = 7;

    /** 每次合成耗多少 POWER（0 = 不耗电，纯手动口径）。接能源网络时改这里。 */
    public static final int ENERGY_PER_CRAFT = 0;

    private static int[] buildInputSlots() {
        int[] out = new int[INPUT_ROWS * INPUT_COLS];
        int n = 0;
        for (int row = 0; row < INPUT_ROWS; row++) {
            for (int col = 0; col < INPUT_COLS; col++) {
                out[n++] = row * 9 + col;
            }
        }
        return out;
    }

    // ================================================================ 有序配方表

    /**
     * 一条"有序配方"。
     *
     * <p>★ {@code input} 的长度可以 &lt; 36，但<b>必须</b>与 {@link #INPUT_SLOTS} 的前缀对齐：
     * {@code input[i]} 说的就是 {@code INPUT_SLOTS[i]} 那一格"要什么 / 要空"。
     *
     * <p>★ 空位写 {@code null} = "这一格<b>必须</b>是空的"。写成空气 {@code ItemStack}
     * 会把这条配方变成"永不匹配"，而且不报错 —— 见 modules/12 坑 3。
     *
     * @param ticks  LogiTech 的约定：{@code -1} = 手动/有序配方的标记，不参与机器耗电计时。
     *               本项目不用机械 tick 模型时它只是个标签，<b>别再给它赋予"秒数"的含义</b>。
     * @param input  有序输入（保留 null 占位）
     * @param output 产物（可以有多个，也可以带数量）
     */
    public record OrderedRecipe(int ticks, ItemStack[] input, ItemStack[] output) {
    }

    /** 本工作台的全部配方 —— 由 {@link #TYPE} 的注册回调在线填充。 */
    private final List<OrderedRecipe> recipes = new ArrayList<>();

    /**
     * ★★★ 配方类型：把这个类型交给任意 {@link SlimefunItem}，它的配方就自动进入本工作台。
     *
     * <p>Slimefun4 本体的 {@code RecipeType#register} 会在每个物品 {@code load()} 时被调用
     * （{@code SlimefunItem.java:446}），所以<b>不需要</b>任何额外的注册代码 ——
     * 这就是"6×6 大型配方"的落地方式。
     *
     * <p>★ 刻意用<b>本体的</b> 4 参构造器（第 3/4 参就是注册/反注册回调），
     * 而不是自己写一个 {@code RecipeType} 子类去拦 {@code register}：
     * 少一层自我状态，也少一处"子类忘了调 super"的坑。
     * LogiTech 之所以要自定义子类，是因为它额外需要"后订阅者也能收到已有配方"
     * （{@code CustomRecipeType#relatedTo}）与一个全局注册表；本项目不需要。
     */
    public static final RecipeType TYPE = new RecipeType(
            new NamespacedKey(com.example.touhou.Touhou.getInstance(), "touhou_large_workbench"),
            new CustomItemStack(Material.CRAFTING_TABLE, "&6大型合成工作台"),
            (recipe, result) -> PENDING.add(new Object[]{recipe, result}),
            (recipe, result) -> PENDING.removeIf(e -> sameItem((ItemStack) e[1], result)));

    /**
     * 回调的落点。
     *
     * <p>★ 为什么需要这个中间层：{@link #TYPE} 是 {@code static final} 字段，类加载时就构造好了，
     * 那一刻还没有任何工作台实例；而回调是在"某些物品 {@code load()}"时才触发的。
     * 所以回调不能直接写 {@code this.recipes.add(...)}，得先落进静态收集器，
     * 等工作台构造时再一次性认领。
     */
    private static final List<Object[]> PENDING = new ArrayList<>();

    // ================================================================ 注册 / 构造

    public LargeWorkbenchTemplate(ItemGroup itemGroup, SlimefunItemStack item,
                                  RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        // ---- 认领此前已注册的全部配方
        for (Object[] e : PENDING) {
            accept((ItemStack[]) e[0], (ItemStack) e[1]);
        }

        // ---- 界面：同 id 的 preset 后注册覆盖先注册（modules/04 §2.1）
        new BlockMenuPreset(getId(), "&6大型合成工作台") {
            @Override
            public void init() {
                LargeWorkbenchTemplate.this.constructMenu(this);
            }

            @Override
            public void newInstance(BlockMenu menu, Block b) {
                LargeWorkbenchTemplate.this.onMenuCreated(menu, b);
            }

            @Override
            public boolean canOpen(Block b, Player p) {
                return p.hasPermission("slimefun.inventory.bypass") || canUse(p, false);
            }

            /**
             * ★ 物流只认这两张表：输入槽与产物槽。
             *
             * <p>刻意<b>不</b>把按钮 / 边框 / 配方菜单交给 Cargo —— 否则物流会往合成按钮里塞东西。
             * LogiTech 的 {@code AbstractWorkBench#getSlotsAccessedByItemTransport}
             * （{@code AbstractWorkBench.java:198-200}）也是这么分的。
             */
            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return flow == ItemTransportFlow.WITHDRAW ? OUTPUT_SLOTS.clone() : INPUT_SLOTS.clone();
            }
        };
    }

    /** 一条配方进入本工作台。 */
    private void accept(ItemStack[] recipe, ItemStack result) {
        // ★ 有序化在这里发生：原样复制，**不做**任何"去空位 / 合并同类项"的压缩
        OrderedRecipe r = new OrderedRecipe(-1, copyKeepNulls(recipe), new ItemStack[]{result.clone()});
        // 同一个产物 + 同样的有序输入只留一条（物品重载会重复回调）
        recipes.removeIf(old -> sameItem(old.output()[0], result) && sameLayout(old.input(), r.input()));
        recipes.add(r);
    }

    // ================================================================ 界面骨架

    private void constructMenu(BlockMenuPreset preset) {
        preset.setSize(INV_SIZE);

        // ★ 一律通过 GuiLock 注册：默认全锁死，只有显式声明的真实槽能放取（modules/04 §3）
        final GuiLock lock = GuiLock.wrap(preset);

        // ---- 36 个输入槽：真实槽（玩家能放能取、Cargo 能进出）
        lock.markRealSlot(INPUT_SLOTS);
        // ---- 产物槽：只出不进
        for (int slot : OUTPUT_SLOTS) {
            lock.outputSlot(slot);
        }

        // ---- 边框 / 连线纹理（锁死的装饰物）
        for (int slot : BORDER_IN) {
            lock.addItem(slot, ChestMenuUtils.getInputSlotTexture());
        }
        for (int slot : BORDER_OUT) {
            lock.addItem(slot, ChestMenuUtils.getOutputSlotTexture());
        }

        // ---- 三个按钮：这里只"装图标 + 上锁"，业务回调在 onMenuCreated 里挂
        lock.button(CRAFT_SLOT, new CustomItemStack(Material.COMMAND_BLOCK,
                "&e点我进行合成", List.of("&7一次性最多合成 " + CRAFT_LIMIT + " 个")), (p, e) -> {
        });
        lock.button(RECIPE_MENU_SLOT, new CustomItemStack(Material.BOOK, "&6点击查看配方"), (p, e) -> {
        });
        lock.button(LAST_RECIPE_SLOT, new CustomItemStack(Material.KNOWLEDGE_BOOK,
                "&6点击放置上一次配方",
                List.of("&7左键放入一份配方", "&7右键放入 64 份")), (p, e) -> {
        });

        lock.autoGuard();
    }

    /**
     * 界面（重）建好之后的钩子 —— <b>这里才有真实方块</b>。
     *
     * <p>做两件事：① 给三个按钮挂真实业务回调（覆盖 {@code constructMenu} 里的空实现）；
     * ② 刷新"上一次配方"图标。
     *
     * <p>★ 覆盖是安全的：这里用的 handler 自己 {@code e.setCancelled(true)}，
     * 所以"图标拿不走"的保证仍然成立（GuiLock 的锁靠的是取消事件，不是靠"只有它能注册"）。
     */
    private void onMenuCreated(BlockMenu menu, Block b) {
        try {
            menu.addMenuClickHandler(CRAFT_SLOT, new ChestMenu.AdvancedMenuClickHandler() {
                @Override
                public boolean onClick(Player p, int s, ItemStack cursor,
                                       me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
                    return false;
                }

                @Override
                public boolean onClick(InventoryClickEvent e, Player p, int s, ItemStack cursor,
                                       me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
                    e.setCancelled(true);
                    craft(menu, p);
                    return false;
                }
            });
            menu.addMenuClickHandler(RECIPE_MENU_SLOT, new ChestMenu.AdvancedMenuClickHandler() {
                @Override
                public boolean onClick(Player p, int s, ItemStack cursor,
                                       me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
                    return false;
                }

                @Override
                public boolean onClick(InventoryClickEvent e, Player p, int s, ItemStack cursor,
                                       me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
                    e.setCancelled(true);
                    openRecipeMenu(p, menu);
                    return false;
                }
            });
            menu.addMenuClickHandler(LAST_RECIPE_SLOT, new ChestMenu.AdvancedMenuClickHandler() {
                @Override
                public boolean onClick(Player p, int s, ItemStack cursor,
                                       me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
                    return false;
                }

                @Override
                public boolean onClick(InventoryClickEvent e, Player p, int s, ItemStack cursor,
                                       me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
                    e.setCancelled(true);
                    moveLastRecipe(p, menu, e.isRightClick());
                    return false;
                }
            });
            refreshLastRecipeIcon(menu);
        } catch (RuntimeException e) {
            // 界面重建失败绝不能连累方块本身（这是 Slimefun 的 newInstance 回调）
            com.example.touhou.Log.info("[WORKBENCH] 界面重建失败: " + e);
        }
    }

    // ================================================================ 合成

    /**
     * 点"合成"按钮的入口 —— 对应 LogiTech 的 {@code AbstractWorkBench#craft}
     * （{@code AbstractWorkBench.java:144-188}）。
     *
     * <p>顺序刻意与它一致：<b>先按电量算 limit → 找配方 → 按 limit 一次扣料出货</b>。
     * 先扣料再判电量会出现"扣了料没出货"的静默损坏。
     */
    public void craft(BlockMenu inv, Player player) {
        Location loc = inv.getLocation();
        int limit = CRAFT_LIMIT;
        if (ENERGY_PER_CRAFT > 0) {
            int charge = charge(loc);
            int byEnergy = charge / ENERGY_PER_CRAFT;
            if (byEnergy == 0) {
                Notify.warn(Notify.workbench(), player,
                        "&c电力不足（每次需要 " + ENERGY_PER_CRAFT + "，现有 " + charge + "）");
                return;
            }
            limit = Math.min(byEnergy, CRAFT_LIMIT);
        }

        OrderedRecipe hit = findRecipe(inv);
        if (hit == null) {
            Notify.warn(Notify.workbench(), player, "&c这不是一个有效的配方");
            return;
        }

        // 能真的合成几次：受 limit、输入余量、产物槽余量三者共同限制
        long times = Math.min(limit, matchOrdered(readInputs(inv), hit));
        times = Math.min(times, maxCraftsByOutput(hit, inv));
        if (times <= 0) {
            Notify.warn(Notify.workbench(), player, "&c合成失败：产物槽放不下，或材料不足");
            return;
        }

        // ---- 扣料 → 出货（扣料前已由 matchOrdered 保证数量足够）
        consumeOrdered(inv, hit, (int) times);
        for (ItemStack out : hit.output()) {
            ItemStack give = out.clone();
            give.setAmount(out.getAmount() * (int) times);
            ItemStack leftover = inv.pushItem(give, OUTPUT_SLOTS);
            if (leftover != null && leftover.getAmount() > 0) {
                // 理论上 maxCraftsByOutput 已拦住；真出现就如实报，不静默吞掉
                Notify.warn(Notify.workbench(), player,
                        "&c产物槽放不下，剩余 " + leftover.getAmount() + " 个未能输出");
            }
        }

        if (ENERGY_PER_CRAFT > 0) {
            long cost = (long) ENERGY_PER_CRAFT * times;
            removeCharge(loc, (int) Math.min(cost, Integer.MAX_VALUE));
        }
        lastRecipe = hit;
        refreshLastRecipeIcon(inv);
        Notify.info(Notify.workbench(), player, "&a合成成功 ×" + times);
    }

    /**
     * ★★ 有序匹配 —— 逐下标比对，这是本工作台唯一的匹配口径。
     *
     * <p>与 LogiTech {@code CraftUtils.matchShapedRecipe(ItemPusher[], MachineRecipe, long)}
     * （{@code CraftUtils.java:1189-1214}）逐条对齐：
     * <pre>
     *   slotItems.length &lt; want.length          → 0    （输入槽比配方短，直接不可能）
     *   输入 null 而配方该位非 null              → 0    （该有的没有）
     *   配方该位 null 而输入有东西               → **不检查**（多出来的料被忽略，见 modules/12 坑 6）
     *   能放几个 = min(所有非空位的 floor(现有量 / 需求量))
     * </pre>
     *
     * @return 能合成几次（0 = 不匹配）
     */
    public static long matchOrdered(ItemStack[] slotItems, OrderedRecipe recipe) {
        ItemStack[] want = recipe.input();
        if (slotItems.length < want.length) {
            return 0L;
        }
        long max = Long.MAX_VALUE;
        for (int i = 0; i < want.length; i++) {
            ItemStack need = want[i];
            if (need == null) {
                continue;                       // ★ 配方不要求这一格，输入是什么都行
            }
            ItemStack have = slotItems[i];
            if (have == null || have.getType().isAir()) {
                return 0L;                      // ★ 该有的没有
            }
            if (!sameItem(have, need)) {
                return 0L;
            }
            max = Math.min(max, have.getAmount() / Math.max(1, need.getAmount()));
        }
        return max == Long.MAX_VALUE ? 0L : max;
    }

    /** 按"上一次配方 → 列表顺序"找第一条匹配的配方（对应 {@code findNextShapedRecipe}）。 */
    private OrderedRecipe findRecipe(BlockMenu inv) {
        ItemStack[] cur = readInputs(inv);
        if (lastRecipe != null && matchOrdered(cur, lastRecipe) > 0) {
            return lastRecipe;
        }
        for (OrderedRecipe r : recipes) {
            if (matchOrdered(cur, r) > 0) {
                return r;
            }
        }
        return null;
    }

    // ================================================================ 一键摆料

    /**
     * 一键摆料 —— 对应 LogiTech 的 {@code AbstractWorkBench#moveRecipe}
     * （{@code AbstractWorkBench.java:227-305}）。
     *
     * <p>从<b>玩家背包</b>取料放进输入槽；{@code times} 是被摆的"份数"
     * （LogiTech：右键 64 份、左键 1 份，见 {@code BugCrafter.java:107}）。
     *
     * <p>★ 关键点与 LogiTech 一致：下标 <b>i</b> 同时是"配方下标"和"{@link #INPUT_SLOTS} 的下标"，
     * 所以 {@code INPUT_SLOTS[i]} 就是 {@code recipe.input()[i]} 该去的那一格 ——
     * 中间<b>不能</b>再插一层"找空槽"的逻辑。
     */
    public int placeRecipe(Player player, BlockMenu inv, OrderedRecipe recipe, int times) {
        ItemStack[] want = recipe.input();
        if (want.length > INPUT_SLOTS.length) {
            Notify.warn(Notify.workbench(), player, "&c这条配方比本工作台的输入槽还大，放不进去");
            return 0;
        }
        int placed = 0;
        for (int i = 0; i < want.length; i++) {
            ItemStack need = want[i];
            if (need == null) {
                continue;                       // 空位不摆东西
            }
            int target = Math.min(need.getAmount() * times, 64);
            ItemStack cur = inv.getItemInSlot(INPUT_SLOTS[i]);
            int already = cur == null || !sameItem(cur, need) ? 0 : cur.getAmount();
            int needPut = target - already;
            if (needPut <= 0) {
                continue;
            }
            int moved = takeFromPlayer(player, need, needPut);
            if (moved > 0) {
                giveToSlot(inv, INPUT_SLOTS[i], need, moved);
                placed += moved;
            }
        }
        return placed;
    }

    /** 从玩家背包里取 n 个"与样例相同"的物品，返回真正取到的数量。 */
    private static int takeFromPlayer(Player player, ItemStack sample, int n) {
        int got = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int s = 0; s < contents.length && got < n; s++) {
            ItemStack it = contents[s];
            if (it == null || it.getType().isAir() || !sameItem(it, sample)) {
                continue;
            }
            int take = Math.min(n - got, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) {
                player.getInventory().setItem(s, null);
            }
            got += take;
        }
        return got;
    }

    /** 把 n 个物品并进某个输入槽（同类就叠加）。 */
    private static void giveToSlot(BlockMenu inv, int slot, ItemStack sample, int n) {
        ItemStack cur = inv.getItemInSlot(slot);
        if (cur == null || cur.getType().isAir()) {
            ItemStack put = sample.clone();
            put.setAmount(n);
            inv.replaceExistingItem(slot, put);
            return;
        }
        ItemStack put = cur.clone();
        put.setAmount(cur.getAmount() + n);
        inv.replaceExistingItem(slot, put);
    }

    private void moveLastRecipe(Player player, BlockMenu inv, boolean max) {
        if (lastRecipe == null) {
            Notify.warn(Notify.workbench(), player, "&c还没有合成记录");
            return;
        }
        int n = placeRecipe(player, inv, lastRecipe, max ? 64 : 1);
        Notify.info(Notify.workbench(), player,
                n > 0 ? "&a已放入上一次的配方" : "&c背包里没有可用的材料");
    }

    // ================================================================ 配方展示

    /**
     * 把每条配方显示成一行"有序配方合成 → 产物"（对应
     * {@code AbstractWorkBench#provideDisplayRecipe}，{@code AbstractWorkBench.java:90-106}）。
     *
     * <p>★ 6×6 的图案塞不进粘液书的 3×3 配方页，所以展示的是<b>占位说明 + 产物</b>，
     * 不是"能照着摆的图"。LogiTech 也是这么做的：输入位被换成一条
     * "&amp;f有序配方合成 / &amp;7请在配方显示界面或者机器界面查看"。
     */
    public List<ItemStack[]> displayRecipes() {
        List<ItemStack[]> out = new ArrayList<>();
        for (OrderedRecipe r : recipes) {
            ItemStack note = new CustomItemStack(Material.PAPER, "&f有序配方合成",
                    List.of("&7请在配方菜单或机器界面查看"));
            out.add(new ItemStack[]{note, r.output()[0]});
        }
        return out;
    }

    /** 配方条数（自检 / 命令用）。 */
    public int recipeCount() {
        return recipes.size();
    }

    // ================================================================ 界面刷新

    private void refreshLastRecipeIcon(BlockMenu inv) {
        if (inv == null) {
            return;
        }
        List<String> lore = new ArrayList<>();
        lore.add("&7合成历史记录: " + (lastRecipe == null ? "&c无" : "&r" + nameOf(lastRecipe.output()[0])));
        inv.replaceExistingItem(LAST_RECIPE_SLOT, new CustomItemStack(Material.KNOWLEDGE_BOOK,
                "&6点击放置上一次配方", lore));
    }

    private void openRecipeMenu(Player p, BlockMenu inv) {
        // 本项目用 RecipePages / 自建菜单展示；这里只留挂点。
        throw new UnsupportedOperationException("接入本项目的配方菜单展示");
    }

    // ================================================================ 读写输入槽

    /**
     * 逐格读出输入槽。
     *
     * <p>★ 保留数组长度、空位填 {@code null} —— 这是有序匹配的前提。
     * 任何"把空气过滤掉再返回"的写法（LogiTech 的 {@code stackIn} 就是那种）都会让下标错位。
     */
    public static ItemStack[] readInputs(BlockMenu inv) {
        ItemStack[] out = new ItemStack[INPUT_SLOTS.length];
        for (int i = 0; i < INPUT_SLOTS.length; i++) {
            ItemStack it = inv.getItemInSlot(INPUT_SLOTS[i]);
            out[i] = it == null || it.getType().isAir() ? null : it;
        }
        return out;
    }

    /** 一条配方最多能合成几次（按产物槽余量）—— 放不下就不许扣料。 */
    private static long maxCraftsByOutput(OrderedRecipe r, BlockMenu inv) {
        long max = Long.MAX_VALUE;
        for (ItemStack out : r.output()) {
            long free = 0;
            for (int slot : OUTPUT_SLOTS) {
                ItemStack cur = inv.getItemInSlot(slot);
                if (cur == null || cur.getType().isAir()) {
                    free += out.getMaxStackSize();
                } else if (sameItem(cur, out)) {
                    free += Math.max(0, out.getMaxStackSize() - cur.getAmount());
                }
            }
            max = Math.min(max, free / Math.max(1, out.getAmount()));
        }
        return max == Long.MAX_VALUE ? 0L : max;
    }

    /** 真扣料 —— 调用前必须已用 {@link #matchOrdered} 确认数量足够。 */
    private static void consumeOrdered(BlockMenu inv, OrderedRecipe r, int times) {
        ItemStack[] want = r.input();
        for (int i = 0; i < want.length; i++) {
            if (want[i] != null) {
                inv.consumeItem(INPUT_SLOTS[i], want[i].getAmount() * times);
            }
        }
    }

    // ================================================================ 挂点 / 工具

    private OrderedRecipe lastRecipe;

    /** 接 POWER 能源网络：见 modules/05（节点类型选 CONSUMER）。 */
    protected int charge(Location loc) {
        return 0;
    }

    protected void removeCharge(Location loc, int amount) {
    }

    /**
     * 复制数组并<b>保留 null 占位</b> —— 与 LogiTech {@code MachineRecipeUtils.i()}
     * （{@code MachineRecipeUtils.java:104-117}）同义，<b>不是</b> {@code stackIn()}。
     */
    public static ItemStack[] copyKeepNulls(ItemStack[] in) {
        ItemStack[] out = new ItemStack[in.length];
        for (int i = 0; i < in.length; i++) {
            ItemStack it = in[i];
            out[i] = it == null || it.getType().isAir() ? null : it.clone();
        }
        return out;
    }

    /**
     * 两个物品"算不算同一种"。
     *
     * <p>★ 这里用 {@code isSimilar}（材质 + 附魔 + 显示名 + 持久数据），
     * 与 LogiTech 的 {@code matchItemStack(..., strictCheck=false)} 同档；
     * 只有"配方里写死了一个 NBT 特定的物品"时才需要更严的判据。
     */
    public static boolean sameItem(ItemStack a, ItemStack b) {
        if (a == null || b == null) {
            return a == b;
        }
        return a.isSimilar(b);
    }

    /** 两个有序数组的"图案"是否完全一致（含 null 占位）。 */
    private static boolean sameLayout(ItemStack[] a, ItemStack[] b) {
        if (a.length != b.length) {
            return false;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] == null || b[i] == null) {
                if (a[i] != b[i]) {
                    return false;
                }
            } else if (!sameItem(a[i], b[i])) {
                return false;
            }
        }
        return true;
    }

    private static String nameOf(ItemStack it) {
        if (it == null) {
            return "无";
        }
        if (it.hasItemMeta() && it.getItemMeta() != null && it.getItemMeta().hasDisplayName()) {
            return it.getItemMeta().getDisplayName();
        }
        return it.getType().name();
    }
}
