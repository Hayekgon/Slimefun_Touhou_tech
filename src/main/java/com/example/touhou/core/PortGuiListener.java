package com.example.touhou.core;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * 本附属所有机器界面的<b>全局交互防护</b>。
 *
 * <p>逐槽的点击拦截（{@code GuiLock} 注册的 handler）只能挡住"针对某一格"的点击。
 * 有三类操作<b>不是</b>（或不只是）针对单格的，逐槽拦截天然管不到，必须在这里统一堵住：
 *
 * <ol>
 *   <li><b>拖拽</b>（{@link InventoryDragEvent}）：一次事件覆盖多个槽，
 *       玩家可把物品"涂"进任意格子；</li>
 *   <li><b>双击收集</b>（{@code ClickType.DOUBLE_CLICK}）：把光标物品与**所有槽位**里的
 *       同类物品一次性合并到光标上 —— 于是占位玻璃板会被"跨槽收集"拿走。
 *       ★ 逐槽 handler 无法识别它：{@code ClickAction} 只带 {@code isRightClicked/isShiftClicked}，
 *       拿不到 {@code ClickType}（实测截图：双击后果栏多出一堆占位玻璃板）。</li>
 *   <li><b>从玩家背包 Shift 快速移动</b>（{@link ClickType#SHIFT_LEFT}/{@link ClickType#SHIFT_RIGHT}
 *       且 {@code rawSlot >= size}）：事件里的槽位指向<b>玩家背包</b>，
 *       界面这边的逐槽 handler 一次都不会被调用；原版会把物品搬进界面里
 *       "第一个放得下的空槽" —— 对赛钱箱来说那正好是 6 个只读镜像的预留槽。
 *       这类机器通过 {@link GuiShiftGuard} 声明"只许进哪些槽"，本类替它接管搬运。</li>
 * </ol>
 *
 * <p>★ 数字键交换（{@code NUMBER_KEY}）与副手交换（{@code SWAP_OFFHAND}）<b>不需要在这里处理</b>：
 * 它们点的是界面里的某一格（{@code rawSlot < size}），会走 {@code GuiLock} 注册的
 * {@code AdvancedMenuClickHandler}，那个 handler 无条件 {@code setCancelled(true)}
 * （见 {@code GuiLock.LOCK}）。所以"预留槽被数字键换进物品"这条路本来就被堵死了 ——
 * 但它是<b>靠逐槽 handler 堵的</b>，别在这里重复取消（重复取消会把真实槽也一起锁掉）。
 *
 * <h2>★★ 为什么监听优先级是 {@link EventPriority#HIGH}（不是 LOW）</h2>
 * 本类原来注册在 {@code LOW}，那是个<b>会失效的优先级</b>：Slimefun 自己有一个人菜单监听器
 * （反编译 {@code me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.MenuListener}，
 * 注册在默认的 {@code NORMAL}），它对<b>玩家背包里</b>的点击是这么收尾的：
 * <pre>
 *   else {                                  // rawSlot >= 界面大小 = 点在玩家背包
 *       MenuClickHandler h = menu.getPlayerInventoryClickHandler();
 *       if (h != null) e.setCancelled(!h.onClick(...));
 *   }
 * </pre>
 * 而 {@code BlockMenuPreset#clone} 的第一行就是 {@code menu.setPlayerInventoryClickable(true)}，
 * 于是默认 handler 恒返回 {@code true} ⇒ {@code setCancelled(false)} ——
 * <b>把我们刚取消掉的事件重新放开</b>。所以：
 * <ul>
 *   <li>点在<b>界面里</b>的拦截（双击、数字键…）不受影响：那些走逐槽 handler，
 *       而 Slimefun 的收尾是 {@code setCancelled(!handler.onClick(...))}，
 *       我们的 handler 返回 false ⇒ 最终仍是"取消"；</li>
 *   <li>点在<b>玩家背包</b>的拦截（本类的 Shift 分支，以及"背包里双击收集"）
 *       <b>必须排在 NORMAL 之后</b>才能生效 —— 这就是 {@code HIGH} 的由来。
 *       实测：留在 LOW 时 Shift 点击照样能把物品塞进赛钱箱的预留槽。</li>
 * </ul>
 *
 * <p>★ 为什么不覆写 {@code BlockMenuPreset#onDrag}：Paper 1.20.4 的
 * {@code InventoryHolder} 接口<b>只有 {@code getInventory()}</b>，没有 {@code onDrag} 默认方法，
 * 覆写不了（编译期报"方法未覆盖超类型方法"）。
 *
 * <p>★ 覆盖范围：{@link #isOurGui} 认的是"这一格方块是本附属的机器"——
 * 目前是反应堆核心（{@link UtsuhoReactorCore}）、两个物流接口
 * （{@link AbstractReactorPort} 的子类），以及祭祀系多方块的
 * {@link ShrinePost}（木桩）与 {@link Saizenbako}（赛钱箱）。
 * <b>新增机器时在这里加一条判断。</b>
 *
 * <p>⚠ 新机器忘了往这里加会怎样：拖拽与双击<b>不被取消</b> ——
 * 玩家可以把物品"涂"进锁死的占位格里、或双击把占位玻璃板收集走。
 * 逐槽 handler 挡不住这两类操作（原因见上），所以这不是"可选优化"而是<b>必需项</b>。
 */
public class PortGuiListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (isOurGui(e.getInventory())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!isOurGui(e.getInventory())) {
            return;
        }
        int raw = e.getRawSlot();
        int size = e.getInventory().getSize();

        // ① 双击 = 跨槽收集，会绕过所有逐槽拦截：直接取消并告诉玩家为什么
        //    （逐槽 handler 拿不到 ClickType，只能在这里判）
        //    ★ 点在界面里的双击，逐槽 handler 已经取消了（这里因为 ignoreCancelled 看不到）；
        //      点在【玩家背包】里的双击会跨槽把界面里的占位玻璃板收走 —— 那一种由这里拦。
        if (e.getClick() == ClickType.DOUBLE_CLICK) {
            e.setCancelled(true);
            if (e.getWhoClicked() instanceof Player p) {
                // 属于"常规操作反馈"：动作被拦住了，但玩家双击是习惯性动作，
                // 默认不推送（想看到就把 messages.level 调成 normal）
                Notify.info(p, "&7这个界面不支持双击收集（请用 Shift+点击 移动物品）");
            }
            return;
        }

        // ② 点在箱子之外（窗口边框 / 界面外空白，rawSlot < 0）：直接取消。
        //    注意 rawSlot >= size 是【玩家自己的背包】，那是正常的，绝不能拦。
        if (raw < 0) {
            e.setCancelled(true);
            return;
        }

        // ③ 从玩家背包 Shift 快速移动：逐槽 handler 管不到（见类注释 ③）
        if (raw >= size && e.isShiftClick()) {
            handleShiftFromPlayerInventory(e);
        }
    }

    /**
     * 接管"从玩家背包 Shift 点击"的搬运。
     *
     * <p>只有实现了 {@link GuiShiftGuard} 的机器会被接管（其余机器保持原版行为）；
     * 接管之后物品只会流进机器声明的槽位，流不进去就留在玩家背包里。
     *
     * <h2>★★ 为什么"取消事件 + 自己搬"是安全的（不是想当然）</h2>
     * 反编译 Paper 1.20.4 的 {@code PlayerConnection#handleContainerClick} 可以看到，
     * 真正的点击动作是在<b>事件之后</b>才执行的：
     * <pre>
     *   event.setCancelled(cancelled);
     *   pluginManager.callEvent(event);
     *   switch (event.getResult()) {
     *     case DEFAULT: case ALLOW:  menu.clicked(slot, button, clickType, player);  // ← 真正执行
     *     case DENY:                 只发 PacketPlayOutSetSlot 把"服务端的真实状态"同步给客户端
     *   }
     * </pre>
     * 也就是说：取消 = 那次点击<b>根本不执行</b>，服务端状态没有任何回滚，
     * 而 DENY 分支只是把服务端当前值推回客户端显示。
     * 结论：在事件里直接把物品搬走（改服务端容器）是安全且会被正确显示的，
     * 不会出现"服务端搬了、客户端没搬"或"复制一份"的问题。
     * <ul>
     *   <li>搬运用 {@code DirtyChestMenu#pushItem(ItemStack, int...)}：它认得粘液物品的堆叠上限，
     *       也会自动合并到已有的同类物品上；</li>
     *   <li>剩下的部分<b>直接写回被点击的那个容器</b>（{@code setCurrentItem} 在
     *       不同版本行为不一致，这里用最直白的 {@code Inventory#setItem}）。</li>
     * </ul>
     */
    private static void handleShiftFromPlayerInventory(InventoryClickEvent e) {
        Inventory top = e.getInventory();
        InventoryHolder holder = top.getHolder();
        if (!(holder instanceof BlockMenu menu)) {
            return;
        }
        Location loc = menu.getLocation();
        SlimefunItem item = loc == null ? null : BlockStorage.check(loc);
        if (!(item instanceof GuiShiftGuard guard)) {
            return;                         // 这台机器没声明 ⇒ 维持原版行为
        }
        int[] targets = guard.shiftInsertSlots();
        ItemStack clicked = e.getCurrentItem();

        // 先取消：原版的搬运逻辑一步都不许走（否则物品还是会落进预留槽）
        e.setCancelled(true);
        if (clicked == null || clicked.getType().isAir() || targets == null || targets.length == 0) {
            return;
        }
        ItemStack moving = clicked.clone();
        ItemStack leftover;
        try {
            if (menu.locked()) {
                return;                     // 界面正在被移除：什么都别动
            }
            leftover = menu.pushItem(moving, targets);
        } catch (RuntimeException ex) {
            // pushItem 在菜单被锁/状态异常时会抛：宁可这次 Shift 不生效，也不能让物品消失
            return;
        }
        int moved = moving.getAmount() - (leftover == null ? 0 : leftover.getAmount());
        if (moved <= 0) {
            if (e.getWhoClicked() instanceof Player p) {
                Notify.info(p, "&7这个界面只接受往输入输出槽投料（Shift 移动未生效）");
            }
            return;
        }
        int remain = clicked.getAmount() - moved;
        Inventory from = e.getClickedInventory();
        if (from != null) {
            if (remain <= 0) {
                from.setItem(e.getSlot(), null);
            } else {
                // ★ 必须 clone 之后再 setAmount：new ItemStack(type, n) 会丢掉粘液物品的 id
                ItemStack rest = clicked.clone();
                rest.setAmount(remain);
                from.setItem(e.getSlot(), rest);
            }
        }
        if (remain > 0 && e.getWhoClicked() instanceof Player p) {
            Notify.info(p, "&7输入输出槽放不下了，剩余 " + remain + " 个留在背包里");
        }
    }

    /** 这个 Inventory 是不是本附属某个机器的界面。 */
    private static boolean isOurGui(Inventory inv) {
        if (inv == null) {
            return false;
        }
        InventoryHolder holder = inv.getHolder();
        if (!(holder instanceof me.mrCookieSlime.Slimefun.api.inventory.BlockMenu menu)) {
            return false;
        }
        Location loc = menu.getLocation();
        if (loc == null) {
            return false;
        }
        SlimefunItem item = BlockStorage.check(loc);
        return covers(item);
    }

    /**
     * 这个物品的界面是否被本类的三类全局防护覆盖（拖拽 / 双击 / 背包 Shift）。
     *
     * <p>抽出来是为了让命令能<b>自证</b>覆盖范围（{@code /touhou saizen ... guard} 会打印它）：
     * "新机器忘了往 {@link #isOurGui} 里加一条"是这套机制唯一的失效方式，
     * 所以把判据本身变成一条可查询的事实，而不是只写在注释里。
     * ★ 与 {@link #isOurGui} 用的是同一份判据，不会漂移。
     */
    public static boolean covers(SlimefunItem item) {
        return item instanceof UtsuhoReactorCore
                || item instanceof AbstractReactorPort
                // 祭祀系多方块：神社的木桩（9 格）与赛钱箱（45 格）。
                // 两者都用 GuiLock 锁了占位格/预留格，也必须在这里被认出来，
                // 否则拖拽/双击/背包 Shift 能绕过逐槽拦截（见上方类注释）。
                || item instanceof ShrinePost
                || item instanceof Saizenbako;
    }
}
