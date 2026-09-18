package com.example.touhou.power;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import java.util.List;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 所有 POWER 方块的基类：挂 ticker + 在增删时让网络失效重算。
 *
 * <p>ticker 用 {@code isSynchronized() == true}（主线程）。原生 {@code EnergyNet} 也在主线程跑，
 * 两者共用同一个"电池"（集成核心的原生缓冲）时必须同线程，否则会出现竞态。
 */
public abstract class AbstractPowerBlock extends SlimefunItem {

    protected AbstractPowerBlock(ItemGroup itemGroup, SlimefunItemStack item,
                                 RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        addItemHandler(new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return true;
            }

            @ParametersAreNonnullByDefault
            @Override
            public void tick(Block b, SlimefunItem item, com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData data) {
                PowerNetwork net = PowerNetworkManager.getNetworkFromLocationOrCreate(b.getLocation());
                if (net != null) {
                    net.tick(b, data);
                }
                onPowerTick(b);
            }
        });

        addItemHandler(new BlockPlaceHandler(false) {
            @ParametersAreNonnullByDefault
            @Override
            public void onPlayerPlace(BlockPlaceEvent e) {
                PowerNetworkManager.invalidate(e.getBlockPlaced().getLocation());
                onPowerPlaced(e.getBlockPlaced());
            }
        });

        addItemHandler(new BlockBreakHandler(false, false) {
            @ParametersAreNonnullByDefault
            @Override
            public void onPlayerBreak(BlockBreakEvent e, ItemStack tool, List<ItemStack> drops) {
                PowerNetworkManager.invalidate(e.getBlock().getLocation());
                onPowerRemoved(e.getBlock());
            }
        });
    }

    /** 每 tick 钩子（网络已经跑过）。 */
    protected void onPowerTick(Block b) {
    }

    /** 放置钩子。 */
    protected void onPowerPlaced(Block b) {
    }

    /** 破坏钩子（清理全息等外部资源）。 */
    protected void onPowerRemoved(Block b) {
    }
}
