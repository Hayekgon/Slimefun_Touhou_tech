package com.example.touhou.power;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

/**
 * POWER中继器 —— 导体，并提供"半径跳接"。
 *
 * <p>它本身不存电（容量 0），作用有两个：
 * <ol>
 *   <li>像原生"连接器"一样把相邻的 POWER 方块串起来；</li>
 *   <li>额外把 {@code jump-range}（默认 7，与原生调节器一致）内的 POWER 方块直接连进来 ——
 *       这是<b>可选</b>的远程连接手段，而不是组网的前提。</li>
 * </ol>
 *
 * <p>性能提醒：跳接是 15³ 的立方扫描，中继器多的时候开销可观；大网络请优先用贴墙铺设，
 * 只在真正需要跨距离的地方放中继器。
 */
public class PowerRepeater extends AbstractPowerBlock implements PowerComponent {

    public final ItemSetting<Integer> jumpRange = new ItemSetting<>(this, "jump-range", 7);

    public PowerRepeater(ItemGroup itemGroup, SlimefunItemStack item,
                         RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemSetting(jumpRange);
    }

    @Override
    public NodeType powerType() {
        return NodeType.REPEATER;
    }

    @Override
    public long powerCharge(Location loc) {
        return 0L;
    }

    @Override
    public void powerSetCharge(Location loc, long charge) {
        // 导体不存电
    }

    @Override
    public long powerCapacity(Location loc) {
        return 0L;
    }

    @Override
    public int powerJumpRange() {
        return Math.max(0, jumpRange.getValue());
    }
}
