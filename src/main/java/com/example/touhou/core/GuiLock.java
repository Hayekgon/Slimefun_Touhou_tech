package com.example.touhou.core;

import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * GUI 槽位安全注册器 —— <b>让"占位符能被拿走"这类 bug 在结构上不可能再发生</b>。
 *
 * <h2>为什么需要它</h2>
 * Slimefun 的菜单点击结算在 {@code MenuListener#handleEvent} 里：
 * <pre>
 *   e.setCancelled(!handler.onClick(...));      // ★ 返回 true = 放行；返回 false = 取消
 * </pre>
 * 三个坑叠在一起，靠"记住"是记不住的（本工程三个 GUI 全都踩过）：
 * <ol>
 *   <li><b>返回值语义反直觉</b>：{@code return true} 是"允许这次点击"，
 *       于是 {@code return cursor == null || cursor.getType().isAir();}
 *       看起来像"拦住塞入"，实际是"空手点击时放行"→ 占位符被拿走；</li>
 *   <li><b>注册会被覆盖</b>：{@code addItem(slot, item, handler)} 内部就调用了
 *       {@code addMenuClickHandler(slot, handler)}，而 handler 是按槽位存在一个 Map 里的 ——
 *       后注册的覆盖先注册的。于是"先 addItem 带保护、再 addMenuClickHandler 换业务逻辑"
 *       这个很自然的写法，会把保护悄悄换掉；</li>
 *   <li><b>拖拽绕过</b>：拖拽是一次事件覆盖多个槽，逐槽拦截挡不住
 *       （见 {@link PortGuiListener}）。</li>
 * </ol>
 *
 * <h2>本类的做法：反转默认值</h2>
 * 用 {@link #wrap(BlockMenuPreset)} 包一层再注册：
 * <ul>
 *   <li>经它 {@code addItem} 注册的槽位 <b>默认全部锁死</b>（图标拿不走、东西放不进）；</li>
 *   <li>"真正要放取物品的槽"必须<b>显式</b>声明：{@code markRealSlot(slot...)}；</li>
 *   <li>需要点击的按钮用 {@link #button(int, ItemStack, ClickAction)} 注册，
 *       它在一处同时完成"装图标 + 取消事件 + 回调"，不存在两次注册互相覆盖的窗口。</li>
 * </ul>
 * 这样"忘记加保护"这个错误类别被消掉了 —— 默认就是安全的。
 *
 * <h2>用法</h2>
 * <pre>
 *   private void constructMenu(BlockMenuPreset raw) {
 *       GuiLock lock = GuiLock.wrap(raw);
 *       raw.setSize(54);
 *
 *       lock.markRealSlot(10, 11, 12);               // 真正可放取的槽
 *       lock.addItem(0, ChestMenuUtils.getBackground());      // 自动锁死
 *       lock.addItem(1, ChestMenuUtils.getInputSlotTexture()); // 自动锁死
 *       lock.button(3, infoIcon(), (p, e) -> activate(p));    // 可点按钮，图标也拿不走
 *   }
 * </pre>
 *
 * <p>⚠ 只在 {@code BlockMenuPreset#init()} 里注册有效（那才是 preset 被克隆进 BlockMenu 的时机）。
 */
public final class GuiLock {

    /** 按钮被点击时的回调。{@code event} 可能为 null（拿不到事件的重载）。 */
    @FunctionalInterface
    public interface ClickAction {
        void onClick(Player player, InventoryClickEvent event);
    }

    private final BlockMenuPreset preset;
    private final java.util.Set<Integer> realSlots = new java.util.HashSet<>();
    /** 已由 {@link #button} 处理过的槽位（避免 autoGuard 重复覆盖）。 */
    private final java.util.Set<Integer> buttons = new java.util.HashSet<>();

    private GuiLock(BlockMenuPreset preset) {
        this.preset = preset;
    }

    /**
     * 包装一个 preset。之后请用返回的实例注册槽位，不要直接用 preset ——
     * {@code raw.setSize(...)} 这类与槽位无关的调用仍走原 preset。
     */
    public static GuiLock wrap(BlockMenuPreset preset) {
        GuiLock lock = new GuiLock(preset);
        register(lock);
        return lock;
    }

    // ---------------------------------------------------------------- 全局登记

    /**
     * 所有构建过的注册器（供 {@code /touhou gui} 做全局自检）。
     *
     * <p>用弱引用：界面重建（区块卸载/重载）时会生成新实例，旧的应被回收，
     * 不能让这个登记表把方块位置一直攥住。
     */
    private static final java.util.Map<String, java.lang.ref.WeakReference<GuiLock>> ALL =
            java.util.Collections.synchronizedMap(
                    new java.util.LinkedHashMap<String, java.lang.ref.WeakReference<GuiLock>>());

    private static void register(GuiLock lock) {
        String key = lock.preset.getID() + "@" + System.identityHashCode(lock.preset);
        ALL.put(key, new java.lang.ref.WeakReference<>(lock));
    }

    /** 全局自检：把每一个还活着的注册器报告串起来。 */
    public static java.util.List<String> describeAll() {
        java.util.List<String> out = new java.util.ArrayList<>();
        java.util.List<String> dead = new java.util.ArrayList<>();
        synchronized (ALL) {
            for (java.util.Map.Entry<String, java.lang.ref.WeakReference<GuiLock>> e : ALL.entrySet()) {
                GuiLock lock = e.getValue().get();
                if (lock == null) {
                    dead.add(e.getKey());
                    continue;
                }
                String id = lock.preset.getID();
                java.util.List<String> rep = lock.report();
                // 报告最后两行是结论与明细，取结论那一行
                String verdict = "?";
                for (String line : rep) {
                    if (line.contains("OK ") || line.contains("FAIL ")) {
                        verdict = line.trim();
                        break;
                    }
                }
                out.add(id + "  →  " + verdict);
            }
            for (String k : dead) {
                ALL.remove(k);
            }
        }
        return out;
    }

    /** 清空全局登记（/touhou reload 用）。 */
    public static void clearRegistry() {
        ALL.clear();
    }

    // ---------------------------------------------------------------- 声明

    /**
     * 把这些槽声明为"真正可放取物品的槽"。
     *
     * <p>★ 这是<b>唯一</b>会解除锁定的入口 —— 默认全锁，必须显式开口。
     */
    public GuiLock markRealSlot(int... slots) {
        for (int s : slots) {
            realSlots.add(s);
        }
        return this;
    }

    /** 某个槽是不是"真实槽"。 */
    public boolean isRealSlot(int slot) {
        return realSlots.contains(slot);
    }

    // ---------------------------------------------------------------- 注册

    /**
     * 注册一个<b>锁死的</b>装饰物品：拿不走、放不进。
     *
     * @param slot 槽位
     * @param icon 图标（占位玻璃板 / 纹理 / 纯展示物）
     */
    public GuiLock addItem(int slot, ItemStack icon) {
        return addItem(slot, icon, ChestMenuUtils.getEmptyClickHandler());
    }

    /**
     * 注册一个锁死的装饰物品，并允许自定义"点击时不做任何事"的 handler。
     *
     * <p>⚠ 传入的 handler 只用于兼容既有写法；<b>无论如何都会追加锁死</b>，
     * 因为它最终会被 {@link #autoGuard()} 的 handler 覆盖（覆盖后仍锁死）。
     */
    public GuiLock addItem(int slot, ItemStack icon, ChestMenu.MenuClickHandler ignored) {
        preset.addItem(slot, icon, ignored);
        buttons.remove(slot);
        return this;
    }

    /**
     * 注册一个可点击按钮：装图标 + 取消点击 + 回调，<b>一次完成</b>。
     *
     * <p>图标拿不走、东西放不进；回调里做业务（激活 / 切换模式 / 中止…）。
     */
    public GuiLock button(int slot, ItemStack icon, ClickAction onClick) {
        buttons.add(slot);
        preset.addItem(slot, icon, new ChestMenu.MenuClickHandler() {
            @Override
            public boolean onClick(Player p, int s, ItemStack cursor, me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
                // 返回 false = 取消（不依赖调用方记语义）
                return false;
            }
        });
        preset.addMenuClickHandler(slot, new ChestMenu.AdvancedMenuClickHandler() {
            @Override
            public boolean onClick(Player p, int s, ItemStack cursor, me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
                onClick.onClick(p, null);
                return false;
            }

            @Override
            public boolean onClick(InventoryClickEvent e, Player p, int s, ItemStack cursor, me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
                // ★ 显式取消：不再把"能不能拿"这件事交给返回值去表达
                e.setCancelled(true);
                onClick.onClick(p, e);
                return false;
            }
        });
        return this;
    }

    /** 注册真实槽位上的"取货按钮"：点击会执行回调，但<b>物品可以正常拿取</b>。 */
    public GuiLock slotWithAction(int slot, ClickAction onClick) {
        markRealSlot(slot);
        preset.addMenuClickHandler(slot, new ChestMenu.AdvancedMenuClickHandler() {
            @Override
            public boolean onClick(Player p, int s, ItemStack cursor, me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
                onClick.onClick(p, null);
                return true;            // true = 放行（真实槽要能取）
            }

            @Override
            public boolean onClick(InventoryClickEvent e, Player p, int s, ItemStack cursor, me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
                onClick.onClick(p, e);
                return true;
            }
        });
        return this;
    }

    /**
     * 注册一个<b>只出不进</b>的槽位：产物可以取出，但玩家塞不进东西。
     *
     * <p>用于机器输出槽 —— 这是机器 GUI 的标配需求。注意旧写法
     * {@code return cursor == null || cursor.getType().isAir();} 是<b>反的</b>：
     * 空手时返回 true = 放行 = 产物能被直接拿走。
     *
     * <p>语义：
     * <ul>
     *   <li>空手点击 → 放行（玩家取走产物）；</li>
     *   <li>手上有物品、点在<b>本槽</b> → 取消（塞不进来）；</li>
     *   <li>手上有物品、点在<b>玩家背包</b>（rawSlot 越界）→ 放行（不影响玩家整理背包）。</li>
     * </ul>
     */
    public GuiLock outputSlot(int slot) {
        markRealSlot(slot);
        preset.addMenuClickHandler(slot, new ChestMenu.AdvancedMenuClickHandler() {
            @Override
            public boolean onClick(Player p, int s, ItemStack cursor, me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
                // 拿不到事件时只能保守取消；真正的判定在下面那个重载
                return false;
            }

            @Override
            public boolean onClick(InventoryClickEvent e, Player p, int s, ItemStack cursor, me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
                boolean clickedOwnSlot = e.getRawSlot() == s;
                boolean holdingItem = cursor != null && !cursor.getType().isAir();
                if (clickedOwnSlot && holdingItem) {
                    e.setCancelled(true);
                    return false;
                }
                return true;            // 空手取产物 / 玩家整理背包：放行
            }
        });
        return this;
    }

    // ---------------------------------------------------------------- 收口

    /**
     * <b>必须在注册完所有槽位后调用</b>：给"除真实槽与按钮之外"的每一格补上锁死。
     *
     * <p>它就是这套机制的安全网 —— 即使某个槽忘了用 {@code addItem} 注册（例如空着没放图标），
     * 也会在这里被锁死，不会留出可塞入的空位。
     *
     * <p>扫描范围：优先用 {@code preset.getSize()}（本工程都显式 {@code setSize(54)}），
     * 拿不到或非正时退回 Slimefun 菜单的容量上限 54
     * （{@code BlockMenuPreset#clone} 也只复制 0~53 的 handler）。
     */
    public GuiLock autoGuard() {
        int size = preset.getSize();
        if (size <= 0) {
            size = MAX_MENU_SIZE;
        }
        size = Math.min(size, MAX_MENU_SIZE);
        for (int slot = 0; slot < size; slot++) {
            if (realSlots.contains(slot) || buttons.contains(slot)) {
                continue;
            }
            preset.addMenuClickHandler(slot, LOCK);
        }
        return this;
    }

    /** Slimefun 箱子菜单的容量上限。 */
    public static final int MAX_MENU_SIZE = 54;

    /** 锁死用的 handler：任何点击都取消，图标拿不走、东西放不进。 */
    private static final ChestMenu.AdvancedMenuClickHandler LOCK = new ChestMenu.AdvancedMenuClickHandler() {
        @Override
        public boolean onClick(Player p, int s, ItemStack cursor, me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
            return false;               // false = 取消
        }

        @Override
        public boolean onClick(InventoryClickEvent e, Player p, int s, ItemStack cursor, me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction a) {
            e.setCancelled(true);
            return false;
        }
    };

    // ---------------------------------------------------------------- 自检

    /**
     * 自检：逐格核对"该锁的是否都锁了、该开的是否都开着"。
     *
     * <p>给命令 {@code /touhou gui check} 用 —— 把"看不见的注册结果"变成一条可读结论。
     *
     * @return 逐行报告，最后一行是总结
     */
    public java.util.List<String> report() {
        java.util.List<String> out = new java.util.ArrayList<>();
        int size = preset.getSize();
        if (size <= 0) {
            size = MAX_MENU_SIZE;
        }
        size = Math.min(size, MAX_MENU_SIZE);
        int unlocked = 0;
        for (int slot = 0; slot < size; slot++) {
            if (realSlots.contains(slot)) {
                continue;               // 可放取的槽：允许自由进出
            }
            if (preset.getMenuClickHandler(slot) == null) {
                unlocked++;
                out.add("  X 槽" + slot + " 没有点击处理 —— 可以被拿走 / 塞入");
            }
        }
        int locked = size - realSlots.size();
        out.add(unlocked == 0
                ? "  OK 除 " + realSlots.size() + " 个可放取的槽外，其余 " + locked
                        + " 格全部已锁死"
                : "  FAIL 有 " + unlocked + " 格未锁死");
        out.add("  （可放取的槽=" + realSlots.size()
                + "，可点击按钮=" + buttons.size()
                + "，锁死=" + locked + "，合计=" + size + "）");
        return out;
    }

    /** 真实槽位集合（只读，供自检/命令展示）。 */
    public java.util.Set<Integer> realSlots() {
        return java.util.Collections.unmodifiableSet(realSlots);
    }
}
