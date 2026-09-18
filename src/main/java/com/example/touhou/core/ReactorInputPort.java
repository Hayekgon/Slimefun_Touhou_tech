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
 * 旧地狱-反应堆<b>输入接口</b>（红色染色玻璃）。
 *
 * <p>只做一件事：每 5 秒把<b>本接口主槽区</b>（32 格）的物品搬进<b>代理核心的输入槽</b>。
 * 它不碰核心的输出，出料由 {@link ReactorOutputPort} 负责。
 *
 * <p>界面骨架见 {@link AbstractReactorPort}：行0 是 信息/模式/激活 三个按键，
 * 行1~4 是 32 个主槽，行5 是输入槽标识占位符。
 */
public class ReactorInputPort extends AbstractReactorPort {

    public ReactorInputPort(ItemGroup itemGroup, SlimefunItemStack item,
                            RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    protected String inventoryTitle() {
        return "&c旧地狱-反应堆输入接口";
    }

    @Override
    protected PortRole role() {
        return PortRole.INPUT;
    }

    /**
     * 进料：本接口主槽区（32 格） → 核心输入槽。
     *
     * <p>核心那侧的槽位取自 {@link UtsuhoReactorCore#INPUT_SLOT_ALL}（4 格），
     * 与核心自己认的输入槽是同一份定义（不另抄一份，避免两边漂移）。
     * 32 格一次搬不完也没关系：推不进去的会留在原槽，下一轮继续搬。
     */
    @Override
    protected int doTransfer(BlockMenu own, BlockMenu core) {
        return moveItems(own, ownSlots(), core, UtsuhoReactorCore.INPUT_SLOT_ALL);
    }

    @Override
    protected String roleLine() {
        return "&c输入接口&7（只负责进料）";
    }

    @Override
    protected List<String> directionLines() {
        return List.of("&8· 本接口主槽区 → 核心输入槽");
    }

    @Override
    protected ItemStack ownPlaceholder() {
        return new CustomItemStack(Material.RED_STAINED_GLASS_PANE, "&c输入槽",
                List.of("&7把燃料放进这里", "&7会被自动送进核心"));
    }
}
