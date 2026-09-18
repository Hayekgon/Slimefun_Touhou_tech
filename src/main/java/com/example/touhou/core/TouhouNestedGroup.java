package com.example.touhou.core;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.GuideHistory;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.guide.SurvivalSlimefunGuide;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

/**
 * 能同时容纳 {@code SubItemGroup} <b>和</b> 另一个容器组的"混合容器组"。
 *
 * <h2>为什么需要自己写这个类</h2>
 * 本体的 {@link NestedItemGroup} 有两个硬限制，导致 "TH_TECH → MACHINE → COMPLEX_MACHINE"
 * 这种三级层级<b>用原生类做不到</b>：
 * <ol>
 *   <li>{@code NestedItemGroup.addSubGroup(SubItemGroup)} 只收 {@code SubItemGroup}，
 *       而 {@code SubItemGroup} 的父类型写死是 {@code NestedItemGroup} ——
 *       中间的 MACHINE 要同时当"别人的子组"和"别人的父组"，没有任何一个原生类能同时满足；</li>
 *   <li>更糟的是 {@code NestedItemGroup} 是 {@code FlexItemGroup}，
 *       而指南主菜单（{@code SurvivalSlimefunGuide#getVisibleItemGroups}）对
 *       {@code FlexItemGroup} 的判定是 {@code isVisible(player, profile, mode)}
 *       —— 本体实现是 {@code mode == SURVIVAL_MODE}，恒为真。
 *       <b>于是每一个 NestedItemGroup 都会在指南主菜单里单独占一格</b>：
 *       玩家看到的是 "TH Tech" 和 "机器" 两个并列入口（也就是"物品组分散开"的真正原因），
 *       而不是一个统一的大类。</li>
 * </ol>
 *
 * <h2>本类的做法</h2>
 * 继承 {@code NestedItemGroup}（这样指南点进来会走 {@code FlexItemGroup#open}），
 * 但<b>重写 {@code open()} 自己渲染菜单</b>，让子项可以是任意 {@code ItemGroup}：
 * <pre>
 * TOUHOU_TH_TECH        (TouhouNestedGroup, showInMainMenu=true, 0 级)
 * ├── TOUHOU_MATERIAL       (SubItemGroup, 1 级)
 * ├── TOUHOU_MACHINE        (TouhouNestedGroup, showInMainMenu=false, 1 级容器)
 * │   └── TOUHOU_COMPLEX_MACHINE (SubItemGroup, 2 级)
 * └── TOUHOU_PARTY_ITEM     (SubItemGroup, 1 级)
 * </pre>
 * 只有 {@code showInMainMenu=true} 的那个会出现在指南主菜单里，
 * 其余容器组由父组的菜单点进去（{@code SlimefunGuide.openItemGroup} 会把
 * {@code FlexItemGroup} 路由到它自己的 {@code open()}，所以 MACHINE 依然是用
 * 原生逻辑渲染它的 {@code COMPLEX_MACHINE}）。
 *
 * <h2>渲染细节</h2>
 * {@code open()} 是照着本体 {@code NestedItemGroup#openGuide} 的字节码写的：
 * 同样的标题 key（{@code guide.title.main}）、同样的 {@code createHeader}、
 * 同样的返回按钮（slot 1）、同样的打开音效、子项从 slot 9 起、超过 44 就不再放。
 * 之所以不用 {@code super.open()}，是因为 {@code subGroups} 字段是 private、
 * 没有 getter，父类渲染不出我们自己的混合子项列表。
 */
public class TouhouNestedGroup extends NestedItemGroup {

    /** 子项（可以是 {@code SubItemGroup}，也可以是另一个 {@code TouhouNestedGroup}）。 */
    private final List<ItemGroup> mixedChildren = new ArrayList<>();

    /** 是否出现在指南主菜单顶层。容器组（比如"机器"）设为 false，只从父组里点进去。 */
    private final boolean showInMainMenu;

    public TouhouNestedGroup(@Nonnull NamespacedKey key, @Nonnull ItemStack item, int tier,
                             boolean showInMainMenu) {
        super(key, item, tier);
        this.showInMainMenu = showInMainMenu;
    }

    /**
     * 加一个子项。<b>不要</b>用父类的 {@code addSubGroup} —— 那条路只收 {@code SubItemGroup}，
     * 而且父类的 private 子组列表我们根本渲染不到。
     */
    @ParametersAreNonnullByDefault
    public void addChild(ItemGroup child) {
        mixedChildren.add(child);
    }

    /** 子项列表（只读，诊断用）。 */
    @Nonnull
    public List<ItemGroup> getMixedChildren() {
        return java.util.Collections.unmodifiableList(mixedChildren);
    }

    /** 这个容器组会不会在指南主菜单顶层占一格。 */
    public boolean isShownInMainMenu() {
        return showInMainMenu;
    }

    /**
     * ★ 决定这个组会不会在指南主菜单里单独占一格。
     *
     * <p>本体 {@code NestedItemGroup} 返回 {@code mode == SURVIVAL_MODE}，所以每个
     * NestedItemGroup 都会在生存指南主菜单顶层出现 —— 这正是"物品组分散开"的原因。
     * 容器组把它压成 {@code false}，就只剩下 0 级总类一个入口。
     */
    @Override
    @ParametersAreNonnullByDefault
    public boolean isVisible(Player player, PlayerProfile profile, SlimefunGuideMode mode) {
        return showInMainMenu && super.isVisible(player, profile, mode);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void open(Player player, PlayerProfile profile, SlimefunGuideMode mode) {
        GuideHistory history = profile.getGuideHistory();

        // 与本体一致：只在生存模式记历史，指南的"返回"才找得到上一页
        if (mode == SlimefunGuideMode.SURVIVAL_MODE) {
            history.add(this, 1);
        }

        ChestMenu menu = new ChestMenu(Slimefun.getLocalization().getMessage(player, "guide.title.main"));
        SurvivalSlimefunGuide guide = (SurvivalSlimefunGuide) Slimefun.getRegistry().getSlimefunGuide(mode);

        menu.setEmptySlotsClickable(false);
        menu.addMenuOpeningHandler(p -> SoundEffect.GUIDE_BUTTON_CLICK_SOUND.playFor(p));
        guide.createHeader(player, profile, menu);

        // slot 1：返回上一页（本体 NestedItemGroup 的同款位置与同款文案）
        menu.addItem(1, new CustomItemStack(ChestMenuUtils.getBackButton(player, "",
                ChatColor.GRAY + Slimefun.getLocalization().getMessage(player, "guide.back.guide"))));
        menu.addMenuClickHandler(1, (p, slot, item, action) -> {
            if (history.size() > 1 && !action.isRightClicked()) {
                history.goBack(guide);
            } else {
                SlimefunGuide.openMainMenu(profile, mode, history.getMainMenuPage());
            }
            return false;
        });

        // 子项从 slot 9 开始铺，最多铺到 44（与本体一致）
        int slot = 9;
        for (ItemGroup child : mixedChildren) {
            if (slot >= 45) {
                break;
            }
            menu.addItem(slot, child.getItem(player), (p, s, item, action) -> {
                SlimefunGuide.openItemGroup(profile, child, mode, 1);
                return false;
            });
            slot++;
        }

        menu.open(player);
    }
}
