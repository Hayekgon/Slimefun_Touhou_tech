package com.example.touhou.power;

import com.example.touhou.core.TouhouData;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

/**
 * POWER存储单元 —— 长期储能，<b>long 精度</b>。
 *
 * <p>这是相对原生电容的一个硬改进：原生 {@code EnergyNetComponent#getCharge} 是 {@code int}，
 * 容量上限 ~2.1e9 且中间运算容易溢出；这里全程 long，并将电量落在方块数据里
 * （key {@code touhou:power-charge}，由 Slimefun 异步落盘）。
 */
public class PowerStorageUnit extends AbstractPowerBlock implements PowerComponent {

    /** 电量持久化 key。 */
    public static final String KEY_CHARGE = "touhou:power-charge";

    /**
     * 容量。★ 2026-09-18 按用户要求整体改刻度：原来的 5,000,000 改成 <b>25</b>。
     *
     * <p>⚠ ItemSetting 的值会被 Slimefun 持久化到 {@code plugins/Slimefun/Items.yml}，
     * 光改这里的默认值不会生效 —— 必须同时把 Items.yml 里的旧值改掉（或删掉那个键）。
     */
    public final ItemSetting<Integer> capacity = new ItemSetting<>(this, "capacity", 25);

    public PowerStorageUnit(ItemGroup itemGroup, SlimefunItemStack item,
                            RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemSetting(capacity);
    }

    @Override
    public NodeType powerType() {
        return NodeType.STORAGE;
    }

    @Override
    public long powerCharge(Location loc) {
        return TouhouData.getLong(loc, KEY_CHARGE, 0L);
    }

    @Override
    public void powerSetCharge(Location loc, long charge) {
        long cap = powerCapacity(loc);
        TouhouData.setLong(loc, KEY_CHARGE, Math.max(0, Math.min(charge, cap)));
    }

    @Override
    public long powerCapacity(Location loc) {
        return Math.max(0, capacity.getValue());
    }
}
