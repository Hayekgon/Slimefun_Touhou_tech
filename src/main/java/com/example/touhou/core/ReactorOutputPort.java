package com.example.touhou.core;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * 旧地狱-反应堆<b>输出接口</b>（蓝色染色玻璃）。
 *
 * <p>只做一件事：每 5 秒把<b>代理核心输出槽</b>的物品搬回<b>本接口的主槽区</b>（32 格），
 * 供物流（Cargo）抽走。它不往里送燃料，进料由 {@link ReactorInputPort} 负责。
 *
 * <p>界面骨架见 {@link AbstractReactorPort}：行0 是 信息/模式/激活 三个按键，
 * 行1~4 是 32 个主槽，行5 是输出槽标识占位符。
 */
public class ReactorOutputPort extends AbstractReactorPort {

    public ReactorOutputPort(ItemGroup itemGroup, SlimefunItemStack item,
                             RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    protected String inventoryTitle() {
        return "&9旧地狱-反应堆输出接口";
    }

    @Override
    protected PortRole role() {
        return PortRole.OUTPUT;
    }

    /**
     * 出料：核心输出槽 → 本接口主槽区（32 格）。
     *
     * <p>核心那侧的槽位取自 {@link UtsuhoReactorCore#OUTPUT_SLOT_ALL}，
     * 与核心自己认的输出槽是同一份定义。产物先落到主槽区，再由 Cargo 抽走。
     */
    @Override
    protected int doTransfer(BlockMenu own, BlockMenu core) {
        return moveItems(core, UtsuhoReactorCore.OUTPUT_SLOT_ALL, own, ownSlots());
    }

    @Override
    protected String roleLine() {
        return "&9输出接口&7（只负责出料）";
    }

    @Override
    protected List<String> directionLines() {
        return List.of("&8· 核心输出槽 → 本接口主槽区");
    }

    @Override
    protected ItemStack ownPlaceholder() {
        return new CustomItemStack(Material.BLUE_STAINED_GLASS_PANE, "&9输出槽",
                List.of("&7核心的产物会被送到这里", "&7供物流网络抽走"));
    }
}
