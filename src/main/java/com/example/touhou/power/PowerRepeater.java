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
 *   <li>额外把 {@code jump-range}（默认 7）内的 POWER 方块直接连进来 ——
 *       这是<b>可选</b>的远程连接手段，而不是组网的前提。</li>
 * </ol>
 *
 * <p>★★ <b>「半径」的度量与原生不同，范围差约 80 倍，别以为填一样的数字就等价</b>：
 * 本模组用<b>切比雪夫距离（立方体）</b>，{@code r = 7} 能覆盖 <b>3374</b> 格（含全部斜向）；
 * 而原生 Slimefun 的 range 是<b>沿 6 个轴向直走</b>的十字形，同样 7 只覆盖 <b>42</b> 格。
 * 完整对照表与"为什么刻意选宽的"写在
 * {@link PowerComponent#powerJumpRange()} 的 javadoc 里。
 *
 * <p>性能提醒：正因为是立方体，跳接一次是 15³ = <b>3374 次</b>方块查询（建网时发生，
 * 不是每 tick）。中继器多的时候开销可观；大网络请优先用贴墙铺设，
 * 只在真正需要跨距离的地方放中继器。
 * 若哪天想省这笔开销，把度量换成原生那套轴向十字即可（1/80 的查询量），
 * 代价是斜向摆的中继器连不上、对玩家很反直觉。
 */
public class PowerRepeater extends AbstractPowerBlock implements PowerComponent {

    /**
     * 跳接半径（切比雪夫，立方体范围；<b>不是</b>原生的轴向十字，见类注释）。
     */
    public final ItemSetting<Integer> jumpRange = new ItemSetting<>(this, "jump-range", 7);

    public PowerRepeater(ItemGroup itemGroup, SlimefunItemStack item,
                         RecipeType recipeType, ItemStack[] recipe) {
        this(itemGroup, item, recipeType, recipe, null);
    }

    /**
     * ★ 5 参重载：魔法工作台合成时<b>单次产出 8 个</b>（{@code recipeOutput}）。
     *
     * <p>模板数量保持 1，多产出只走这个参数 —— 判据见
     * {@code AddSlimefunItems#POWER_REPEATER_OUTPUT_AMOUNT}。
     */
    public PowerRepeater(ItemGroup itemGroup, SlimefunItemStack item,
                         RecipeType recipeType, ItemStack[] recipe, ItemStack recipeOutput) {
        super(itemGroup, item, recipeType, recipe, recipeOutput);
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
