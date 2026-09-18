package com.example.touhou.power;

import com.example.touhou.core.TouhouData;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.core.attributes.rotations.NotRotatable;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import java.text.DecimalFormat;
import java.util.List;
import javax.annotation.Nonnull;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

/**
 * POWER集成核心 —— 按需求「<b>完全参照原生粘液的能源控制器</b>」重写。
 *
 * <h2>原生 {@code EnergyRegulator} 长什么样（反编译 2026.07 实测）</h2>
 * <pre>
 * public class EnergyRegulator extends SlimefunItem implements HologramOwner, NotRotatable {
 *     public EnergyRegulator(...) { super(...); addItemHandler(onBreak()); }          // 构造器里挂断块
 *     private BlockBreakHandler onBreak() { ... removeHologram(b) ... }
 *     private BlockPlaceHandler onPlace() { ... updateHologram(e.getBlock(), "&amp;7连接中...") ... }
 *     public void preRegister() { addItemHandler(onPlace()); addItemHandler(&lt;BlockTicker&gt;); }
 *     private void tick(Block b, SlimefunBlockData data) {
 *         EnergyNet network = EnergyNet.getNetworkFromLocationOrCreate(b.getLocation());
 *         network.tick(b, data);                                                       // 本体只做这一件事
 *     }
 * }
 * </pre>
 *
 * <h2>三个关键结构点（本类逐条照搬）</h2>
 * <ol>
 *   <li><b>控制器是哑驱动器</b>：它<b>不</b>自己算电量、<b>不</b>自己拼悬浮字。{@code tick} 只有
 *       「取网络 → 丢给网络 tick」两行。电量分配、悬浮字渲染全在 {@link PowerNetwork} 里。</li>
 *   <li><b>{@code HologramOwner} 的归属在网络</b>：原生是 {@code EnergyNet implements HologramOwner}，
 *       控制器只是<b>也</b>实现它以便断块时能 {@code removeHologram}。本类保持同一分工。</li>
 *   <li><b>生命周期钩子的挂载位置与时机</b>：断块在<b>构造器</b>挂；放置处理器 + ticker 在
 *       {@code preRegister()} 挂（原生就是分开的，因为放置处理器依赖物品已注册）。</li>
 * </ol>
 *
 * <h2>放置时的表现</h2>
 * 原生放下控制器会先显示 {@code &7连接中...}，随后由网络在第一次 tick 时改写成真实数据。
 * 本类一致（用的是同一个字面量）。
 *
 * <h2>唯一的一处刻意偏离（已在代码里标注原因）</h2>
 * 原生 ticker 是 {@code isSynchronized() == false}（异步线程），因为 {@code EnergyNet} 的缓存是并发安全的。
 * 本模组的 {@link PowerNetworkManager} 缓存是普通 {@code HashMap}，且 tick 会写方块数据，
 * 因此这里保持 {@code true}（主线程）。要改成异步需先把该缓存换成并发容器。
 *
 * <h2>⚠ 与原生电网的联动仍是<b>中断</b>状态</h2>
 * 本类不实现 {@code EnergyNetComponent}，原生电网看不见它。要恢复联动就把该接口加回来，
 * 并让 {@code powerCharge/powerSetCharge} 转发到原生缓冲。
 */
public class PowerIntegratedCore extends SlimefunItem
        implements PowerComponent, HologramOwner, NotRotatable {

    /** 电量持久化 key（与存储单元同一套，互不冲突）。 */
    public static final String KEY_CHARGE = "touhou:power-charge";

    /**
     * POWER 侧容量。
     *
     * <p>★ 2026-09-18 按用户要求整体改刻度：原来的 1,000,000 改成 <b>5</b>。
     * POWER 体系现在是小整数刻度（核心 5 / 存储单元 25 / 赛钱箱 5），
     * 各机器的耗电也应以个位数计。
     *
     * <p>⚠ ItemSetting 的值会被 Slimefun 持久化到 {@code plugins/Slimefun/Items.yml}，
     * 光改这里的默认值不会生效 —— 必须同时把 Items.yml 里的旧值改掉（或删掉那个键）。
     */
    public final ItemSetting<Integer> capacity = new ItemSetting<>(this, "capacity", 5);
    /** 是否显示悬浮文字。 */
    public final ItemSetting<Boolean> hologramEnabled = new ItemSetting<>(this, "hologram", true);
    /**
     * 核心的覆盖半径（切比雪夫）。
     *
     * <p>原生 {@code EnergyRegulator} 也带一个 range（{@code EnergyNet.getRange()}，默认 7），
     * 它是「调节器能覆盖多大一片」的定义。这里对齐这个语义：核心本身就是一张网的锚点，
     * 半径内的 POWER 方块直接并入，不再要求逐格贴脸摆放。
     */
    public final ItemSetting<Integer> range = new ItemSetting<>(this, "range", 7);

    private static final DecimalFormat FMT = new DecimalFormat("#,###");

    public PowerIntegratedCore(ItemGroup itemGroup, SlimefunItemStack item,
                               RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemSetting(capacity);
        addItemSetting(hologramEnabled);
        addItemSetting(range);
        // 原生：构造器里只挂断块处理器
        addItemHandler(onBreak());
    }

    // ------------------------------------------------------------------
    // 生命周期钩子（结构与原生 EnergyRegulator 一一对应）
    // ------------------------------------------------------------------

    @Nonnull
    private SimpleBlockBreakHandler onBreak() {
        return new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(@Nonnull Block b) {
                removeHologram(b);                                     // 原生：this.removeHologram(b)
                PowerNetworkManager.invalidate(b.getLocation());       // 本模组额外：让缓存重算
            }
        };
    }

    @Nonnull
    private BlockPlaceHandler onPlace() {
        return new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(@Nonnull BlockPlaceEvent e) {
                updateHologram(e.getBlock(), "&7连接中...");           // 原生用的就是这个字面量
                PowerNetworkManager.stampCoreOrder(e.getBlockPlaced().getLocation());
                PowerNetworkManager.invalidate(e.getBlockPlaced().getLocation());
            }
        };
    }

    @Override
    public void preRegister() {
        addItemHandler(onPlace());
        addItemHandler(new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return true;   // ← 唯一偏离，原因见类注释
            }

            @Override
            public void tick(Block b, SlimefunItem item, SlimefunBlockData data) {
                PowerIntegratedCore.this.tick(b, data);
            }
        });
    }

    /** 原生 {@code EnergyRegulator#tick(Block, SlimefunBlockData)} —— 本体只做这两行。 */
    private void tick(@Nonnull Block b, SlimefunBlockData data) {
        PowerNetwork network = PowerNetworkManager.getNetworkFromLocationOrCreate(b.getLocation());
        if (network != null) {
            network.tick(b, data);
        }
    }

    // ------------------------------------------------------------------
    // PowerComponent（本模组需要：核心自己也是储能节点）
    // ------------------------------------------------------------------

    @Override
    public NodeType powerType() {
        return NodeType.INTEGRATED_CORE;
    }

    @Override
    public long powerCharge(Location loc) {
        return TouhouData.getLong(loc, KEY_CHARGE, 0L);
    }

    @Override
    public void powerSetCharge(Location loc, long charge) {
        TouhouData.setLong(loc, KEY_CHARGE, Math.max(0, Math.min(charge, powerCapacity(loc))));
    }

    @Override
    public long powerCapacity(Location loc) {
        return Math.max(0, capacity.getValue());
    }

    /** 核心也是跳接源：半径内的 POWER 方块直接并入本网络（对齐原生调节器的 range）。 */
    @Override
    public int powerJumpRange() {
        return Math.max(0, range.getValue());
    }

    // ------------------------------------------------------------------
    // 悬浮字文本（由 PowerNetwork 在 tick 时调用）
    // ------------------------------------------------------------------

    /**
     * 渲染本核心头顶的悬浮字。
     *
     * <p>原生 {@code EnergyNet} 也是这么分工的：文本由网络算，控制器不参与。
     */
    String renderText(PowerNetwork net, Location self) {
        List<Location> cores = PowerNetworkManager.coresByOrder(net);
        if (cores.size() >= 2) {
            if (self.equals(cores.get(0))) {
                // 原先的核心：指出后出现的那个核心在哪
                Location other = cores.get(1);
                return "\u00a7c检测到存在多核心，位于（\u00a7f"
                        + other.getBlockX() + "," + other.getBlockY() + "," + other.getBlockZ()
                        + "\u00a7c）";
            }
            return "\u00a7e当前核心连接入已有POWER网络，请调整";
        }
        return "\u00a7e\u26a1 " + FMT.format(Math.max(0, net.lastTotalCharge()))
                + "\u00a77/\u00a7e" + FMT.format(Math.max(0, net.lastTotalCapacity()))
                + " \u00a7fPOWER"
                + " \u00a78| \u00a77节点\u00a7f" + net.size();
    }
}
