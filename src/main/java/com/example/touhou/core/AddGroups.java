package com.example.touhou.core;

import com.example.touhou.Touhou;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.NestedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.SubItemGroup;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;

/**
 * TOUHOU 物品组层级。
 *
 * <p>命名规范（长期有效）：
 * <ul>
 *   <li>物品组 id：{@code TOUHOU_"物品组ID"}，全大写；</li>
 *   <li>物品 id：{@code TOUHOU_"物品组"_"物品名英文"}，全大写。</li>
 * </ul>
 *
 * <h2>层级</h2>
 * <pre>
 * TOUHOU_TH_TECH                (TouhouNestedGroup, 0 级容器，指南主菜单唯一入口)
 * ├── TOUHOU_MATERIAL           (SubItemGroup, 1 级)
 * ├── TOUHOU_MACHINE            (TouhouNestedGroup, 1 级容器，<b>不在主菜单出现</b>)
 * │   └── TOUHOU_COMPLEX_MACHINE(SubItemGroup, 2 级)
 * ├── TOUHOU_PARTY_ITEM         (SubItemGroup, 1 级)
 * ├── TOUHOU_INFO               (SubItemGroup, 1 级)
 * └── TOUHOU_POWER              (SubItemGroup, 1 级)
 * </pre>
 *
 * <h2>★ 为什么 MACHINE 不是普通的 NestedItemGroup（一次真实翻车）</h2>
 * 第一版把 MACHINE 建成裸的 {@code NestedItemGroup}，结果玩家在指南主菜单里看到的是
 * <b>"TH Tech" 和 "机器" 两个并列入口</b>（"物品组分散开"），而不是一个统一大类。
 *
 * <p>根因在 {@code SurvivalSlimefunGuide#getVisibleItemGroups}：
 * <pre>
 * if (group instanceof FlexItemGroup flex) { if (flex.isVisible(p, profile, mode)) 加进主菜单; }
 * else if (!group.isHidden(p))               { 加进主菜单; }
 * </pre>
 * {@code NestedItemGroup extends FlexItemGroup}，而它的
 * {@code isVisible(p, profile, mode)} 实现是 {@code mode == SURVIVAL_MODE} ——
 * <b>每个 NestedItemGroup 都会在生存指南主菜单顶层单独占一格</b>。
 * 相反 {@code SubItemGroup.isVisible(Player)} 恒为 {@code false}，
 * 所以 1 级／2 级的 SubItemGroup 不会跑出来（它们只会作为父组的子项出现）。
 *
 * <p>于是这里改用 {@link TouhouNestedGroup}：它仍然是 NestedItemGroup（能承载子组、
 * 指南点进来照样渲染），但把 {@code isVisible(3 参数)} 压成受控的开关 ——
 * 容器组设 {@code false} 就不占主菜单格子，只能从父组里点进去。
 * 三个条件同时满足了：<b>主菜单只有一个大类</b>、<b>1 级组仍挂在 0 级下</b>、
 * <b>2 级组仍挂在 1 级下</b>。
 */
public final class AddGroups {

    private AddGroups() {
    }

    /** 0 级：总大类（容器组，指南主菜单唯一入口）。 */
    public static TouhouNestedGroup TH_TECH;
    /** 1 级。 */
    public static SubItemGroup MATERIAL;
    /** 1 级：同时是容器，承载 2 级组；不在主菜单单独占格。 */
    public static TouhouNestedGroup MACHINE;
    /** 2 级：挂在 MACHINE 下。 */
    public static SubItemGroup COMPLEX_MACHINE;
    /**
     * 2 级：<b>单方块机器</b>，同样挂在 MACHINE 下。
     *
     * <p>★ 为什么必须新建这个组，而不是把「丰收之时」直接塞进 {@link #MACHINE}：
     * {@code MACHINE} 是 {@link TouhouNestedGroup}（继承 {@code FlexItemGroup}），
     * 而 {@code FlexItemGroup#add(SlimefunItem)} 的实现是直接抛
     * {@code UnsupportedOperationException("You cannot add items to a FlexItemGroup!")}
     * —— 容器组<b>只装子组、不装物品</b>。实测症状很明确（服务端启动日志）：
     * <pre>
     *   [Touhou] Item "TOUHOU_MACHINE_HARVEST_TIME" from Touhou v1.0.0 has caused an Error!
     *   Failed to properly load this Item
     *   java.lang.UnsupportedOperationException: You cannot add items to a FlexItemGroup!
     * </pre>
     * 所以照本工程既有的层级形状，在 MACHINE 下再挂一个 2 级 {@code SubItemGroup}：
     * 多方块的归 {@link #COMPLEX_MACHINE}，单方块的归本组。
     * （另一条路是塞进 COMPLEX_MACHINE，但那组的定位写明了是"多方块大型机器"，
     * 把一台单方块机器放进去会让分类失去意义。）
     *
     * <p>★ 随之而来的 id 变化：物品 id 铁律是
     * {@code TOUHOU_"物品组ID"_"英文名"}，所以挂在本组下的物品 id 是
     * {@code TOUHOU_SIMPLE_MACHINE_HARVEST_TIME}
     * （不是最初设想的 {@code TOUHOU_MACHINE_HARVEST_TIME} —— 那是"MACHINE 能直接装物品"
     * 这个前提下的推导，而那个前提不成立）。
     */
    public static SubItemGroup SIMPLE_MACHINE;
    /** 1 级。 */
    public static SubItemGroup PARTY_ITEM;
    /** 1 级：信息 / 说明类（GUI 内部件等）。 */
    public static SubItemGroup INFO;
    /** 1 级：能源 / 电力相关。 */
    public static SubItemGroup POWER;

    public static void setup(Touhou plugin) {
        // 0 级容器。tier 用 1：容器组不靠研究解锁，里面物品各自管自己
        TH_TECH = new TouhouNestedGroup(
                new NamespacedKey(plugin, "touhou_th_tech"),
                new CustomItemStack(Material.NETHER_STAR,
                        "&6TH Tech",
                        "&7东方主题科技总大类",
                        "&8所有 1 级物品组都在这里"),
                1, true);

        // 1 级容器：机器。showInMainMenu=false —— 这样它只出现在 TH_TECH 里面，
        // 不会在指南主菜单里和 TH_TECH 并列（那正是"分散开"的根源）。
        MACHINE = new TouhouNestedGroup(
                new NamespacedKey(plugin, "touhou_machine"),
                new CustomItemStack(Material.FURNACE,
                        "&b机器",
                        "&71 级物品组 / 容器",
                        "&8点开可以看到多方块大型机器"),
                1, false);

        MATERIAL = new SubItemGroup(
                new NamespacedKey(plugin, "touhou_material"),
                TH_TECH,
                new CustomItemStack(Material.IRON_INGOT,
                        "&f材料",
                        "&71 级物品组",
                        "&7合成材料与中间产物"),
                1);

        PARTY_ITEM = new SubItemGroup(
                new NamespacedKey(plugin, "touhou_party_item"),
                TH_TECH,
                new CustomItemStack(Material.FIREWORK_ROCKET,
                        "&dFlee into Gensokyo",
                        "&7活动 / 趣味物品"),
                1);

        // 1 级：信息类。GUI 内部用的功能件（比如模式切换玻璃板）放这里 ——
        // 它们不是"材料"，也不是可制造的机器，混进 MATERIAL 会让分类失去意义。
        INFO = new SubItemGroup(
                new NamespacedKey(plugin, "touhou_info"),
                TH_TECH,
                new CustomItemStack(Material.KNOWLEDGE_BOOK,
                        "&fINFO",
                        "&71 级物品组",
                        "&7说明与 GUI 功能件"),
                1);

        // 1 级：能源 / 电力。图标红色羊毛（与其它 1 级组一样走"一个人一眼能认出的方块"）
        POWER = new SubItemGroup(
                new NamespacedKey(plugin, "touhou_power"),
                TH_TECH,
                new CustomItemStack(Material.RED_WOOL,
                        "&cPower",
                        "&71 级物品组",
                        "&7能源与电力设备"),
                1);

        // 2 级：多方块大型机器，挂在 MACHINE 容器下
        COMPLEX_MACHINE = new SubItemGroup(
                new NamespacedKey(plugin, "touhou_complex_machine"),
                MACHINE,
                new CustomItemStack(Material.BEACON,
                        "&d多方块大型机器",
                        "&7需要搭建结构的多方块核心"),
                1);

        // 2 级：单方块机器（不需要搭结构、放下就能用的那种），同样挂在 MACHINE 容器下。
        // ★ 见 SIMPLE_MACHINE 字段注释：MACHINE 是容器组，物品【不能】直接挂在它下面。
        SIMPLE_MACHINE = new SubItemGroup(
                new NamespacedKey(plugin, "touhou_simple_machine"),
                MACHINE,
                new CustomItemStack(Material.HAY_BLOCK,
                        "&6单方块机器",
                        "&7放下即可使用的机器"),
                1);

        // ★ 菜单内容由这里决定（顺序就是指南里的显示顺序）。
        //   注意不要用父类的 addSubGroup：它只收 SubItemGroup，装不下 MACHINE，
        //   而且那个 private 列表我们也渲染不到。
        TH_TECH.addChild(MATERIAL);
        TH_TECH.addChild(MACHINE);
        TH_TECH.addChild(PARTY_ITEM);
        TH_TECH.addChild(INFO);
        TH_TECH.addChild(POWER);
        MACHINE.addChild(COMPLEX_MACHINE);
        MACHINE.addChild(SIMPLE_MACHINE);

        // 显式注册全部组。
        // 不注册也能用（菜单渲染读的是 mixedChildren），但显式注册让层级与注册表一致，
        // 诊断命令才看得到全部组 —— 之前 PARTY_ITEM 因为一个物品都没装、
        // 没人触发自动注册，直接从注册表里"消失"了。
        TH_TECH.register(plugin);
        MACHINE.register(plugin);
        MATERIAL.register(plugin);
        COMPLEX_MACHINE.register(plugin);
        SIMPLE_MACHINE.register(plugin);
        PARTY_ITEM.register(plugin);
        INFO.register(plugin);
        POWER.register(plugin);
    }

    /** 供诊断输出：层级关系一览。 */
    public static String describeHierarchy() {
        return "TOUHOU_TH_TECH(0级容器) -> [TOUHOU_MATERIAL, TOUHOU_MACHINE(1级容器) -> "
                + "[TOUHOU_COMPLEX_MACHINE(2级), TOUHOU_SIMPLE_MACHINE(2级)], "
                + "TOUHOU_PARTY_ITEM, TOUHOU_INFO, TOUHOU_POWER]";
    }

    /** 供命令输出：实际注册出来的组。 */
    public static List<String> describe() {
        return List.of(
                "TH_TECH         = " + keyOf(TH_TECH) + "  (容器, 0 级, 主菜单: 显示)",
                "MATERIAL        = " + keyOf(MATERIAL) + "  (SubItemGroup of TH_TECH, 1 级)",
                "MACHINE         = " + keyOf(MACHINE) + "  (容器, 1 级, 主菜单: 隐藏)",
                "COMPLEX_MACHINE = " + keyOf(COMPLEX_MACHINE) + "  (SubItemGroup of MACHINE, 2 级)",
                "SIMPLE_MACHINE  = " + keyOf(SIMPLE_MACHINE) + "  (SubItemGroup of MACHINE, 2 级)",
                "PARTY_ITEM      = " + keyOf(PARTY_ITEM) + "  (SubItemGroup of TH_TECH, 1 级)",
                "INFO            = " + keyOf(INFO) + "  (SubItemGroup of TH_TECH, 1 级)",
                "POWER           = " + keyOf(POWER) + "  (SubItemGroup of TH_TECH, 1 级, 图标 RED_WOOL)");
    }

    /**
     * 复刻指南主菜单的筛选逻辑，headless 验证"只有一个大类"。
     *
     * <p>本体 {@code SurvivalSlimefunGuide#getVisibleItemGroups} 的判据：
     * {@code FlexItemGroup} 走 {@code isVisible(p, profile, mode)}，
     * 其余走 {@code !isHidden(p)}（也就是 {@code isVisible(p)}）。
     * 这里不拿真实玩家，而是用各组的实现本身来算 ——
     * {@code SubItemGroup.isVisible(Player)} 是 final 且恒为 false，不会碰玩家对象；
     * 我们自己的容器组则直接读那个开关，所以传 null 也安全。
     */
    public static List<String> mainMenuPreview() {
        List<String> visible = new ArrayList<>();
        List<String> hidden = new ArrayList<>();
        List<String> foreign = new ArrayList<>();
        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            // 只对本附属的组下结论：别的插件/本体的组，可见性跟玩家权限与研究解锁有关，
            // 这里的预览没有真实玩家，判了也是错的。
            if (!"touhou".equals(group.getKey().getNamespace())) {
                foreign.add("  " + keyOf(group) + "  [" + group.getClass().getSimpleName() + "]");
                continue;
            }
            boolean shown;
            if (group instanceof TouhouNestedGroup nested) {
                shown = nested.isShownInMainMenu()
                        && nested.isVisible(null, null, SlimefunGuideMode.SURVIVAL_MODE);
            } else {
                // SubItemGroup.isVisible(Player) 是 final 且恒为 false → isHidden 恒为 true
                shown = !group.isHidden(null);
            }
            String line = "  " + keyOf(group) + "  [" + group.getClass().getSimpleName() + "]";
            (shown ? visible : hidden).add(line);
        }
        List<String> out = new ArrayList<>();
        out.add("指南主菜单（生存模式）里属于本附属的入口共 " + visible.size() + " 个：");
        out.addAll(visible);
        out.add("本附属被过滤掉、不会在顶层占格的组（" + hidden.size() + " 个）：");
        out.addAll(hidden);
        out.add("其它插件/本体的组 " + foreign.size() + " 个（其可见性取决于玩家，本预览不判定）：");
        out.addAll(foreign);
        return out;
    }

    /** 供命令输出：每个容器组点进去之后会看到什么（即 {@code open()} 渲染的内容）。 */
    public static List<String> describeMenus() {
        List<String> out = new ArrayList<>();
        describeMenu("TH Tech", TH_TECH, out);
        describeMenu("机器", MACHINE, out);
        return out;
    }

    private static void describeMenu(String label, TouhouNestedGroup group, List<String> out) {
        if (group == null) {
            return;
        }
        List<String> names = new ArrayList<>();
        int slot = 9;
        for (ItemGroup child : group.getMixedChildren()) {
            names.add("槽 " + slot + " = " + keyOf(child)
                    + " (" + (child instanceof TouhouNestedGroup ? "容器, 可再点进去" : "物品列表") + ")");
            slot++;
        }
        out.add(label + " → " + (names.isEmpty() ? "（空）" : String.join(" | ", names)));
    }

    /** 供命令输出：每个组里实际注册了哪些物品（验证"指南点进去能看到什么"）。 */
    public static List<String> describeItems() {
        List<String> out = new ArrayList<>();
        listItems("TH_TECH(容器)", TH_TECH, out);
        listItems("MATERIAL", MATERIAL, out);
        listItems("MACHINE(容器)", MACHINE, out);
        listItems("COMPLEX_MACHINE", COMPLEX_MACHINE, out);
        listItems("SIMPLE_MACHINE", SIMPLE_MACHINE, out);
        listItems("PARTY_ITEM", PARTY_ITEM, out);
        listItems("INFO", INFO, out);
        listItems("POWER", POWER, out);
        return out;
    }

    private static void listItems(String label, ItemGroup group, List<String> out) {
        if (group == null) {
            return;
        }
        // ★ FlexItemGroup（含 NestedItemGroup）的 getItems() 会抛
        //   UnsupportedOperationException("A FlexItemGroup has no items!")，
        //   容器组本来就不装物品，这里必须先跳过，否则诊断命令直接炸。
        if (group instanceof FlexItemGroup) {
            out.add("  " + label + " 容器组，不装物品（内容见上面的菜单）");
            return;
        }
        List<String> ids = new ArrayList<>();
        for (var item : group.getItems()) {
            ids.add(item.getId());
        }
        out.add("  " + label + " 物品数=" + ids.size()
                + (ids.isEmpty() ? "" : " -> " + String.join(", ", ids)));
    }

    private static String keyOf(ItemGroup group) {
        return group == null ? "(未注册)" : group.getKey().toString();
    }
}
