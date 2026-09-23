package com.example.touhou.core;

import com.example.touhou.Touhou;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * 雾中の妖精 —— <b>对空气右键</b>时消耗 1 个，召唤一只叫 {@code Bomb} 的苦力怕；
 * 1 秒后它原地消失，留下一个绿色星形粒子与一个「保护的 Bomb」。
 *
 * <h2>触发方式（用户已明确口径）</h2>
 * <table border="1">
 *   <caption>三种右键分别发生什么</caption>
 *   <tr><th>操作</th><th>发生什么</th></tr>
 *   <tr><td><b>对空气右键</b>（{@link Action#RIGHT_CLICK_AIR}）</td>
 *       <td>消耗手里 1 个本物品 + 触发整套效果</td></tr>
 *   <tr><td><b>对方块右键</b>（{@link Action#RIGHT_CLICK_BLOCK}）</td>
 *       <td>放下这个方块（{@code SlimefunItem} 默认就能放），<b>不触发</b>效果</td></tr>
 *   <tr><td><b>右键已经放下的那个方块</b></td>
 *       <td>什么都不触发（它只是个占位），只回一条提示</td></tr>
 * </table>
 *
 * <h2>★★ 为什么必须显式判 {@code RIGHT_CLICK_AIR}（本类最容易写错的一处）</h2>
 * {@code PlayerRightClickEvent} <b>同时</b>承载"右键空气"与"右键方块"两种情形，
 * 而本体 {@code SlimefunItemInteractListener#onRightClick} 的派发顺序是两个<b>独立</b>的
 * 判断（反编译确认）：
 * <pre>
 *   if (event.useItem()  != DENY) rightClickItem(...);   // ⇒ ItemUseHandler
 *   if (mainHand &amp;&amp; event.useBlock() != DENY) rightClickBlock(...);  // ⇒ BlockUseHandler / 开 GUI
 * </pre>
 * <b>ItemUseHandler 会为 RIGHT_CLICK_AIR 与 RIGHT_CLICK_BLOCK 都被调用</b> ——
 * 只写 {@code getClickedBlock().isEmpty()} 也够用，但那是"借"了另一个判据的语义。
 * 这里直接读 {@code event.getInteractEvent().getAction()}，
 * 于是"只有对空气右键才触发"是一个**说得出口**的判据，而不是巧合。
 *
 * <h2>★★ 扣物品与生效的原子性（用户点名要求）</h2>
 * 顺序是<b>"先扣、后生效，一旦开扣就不中途返回"</b>：
 * <ol>
 *   <li>所有"可能失败的前置判断"（动作是不是右键空气、是不是主手、手里到底有没有这个东西）
 *       全部做完之后，才进入"扣物品"这一步；</li>
 *   <li>扣完立刻推进到生成苦力怕 —— 中间<b>没有任何 return</b>。
 *       最坏的情况是"物品扣了、苦力怕没生成"（{@code spawn} 返回 null 或抛异常），
 *       这比"物品没扣、效果却发生了"（凭空刷效果 = 可无限刷）要好得多，
 *       而且 {@link SpawnReport} 会如实把 {@code creeperSpawned=false} 报出来、
 *       控制台也会留一行 warning；</li>
 *   <li>★ 事件被取消<b>不会</b>出现"扣了却没生效"：本处理器只在
 *       {@code useItem() != DENY} 时才被本体调用（见上面那段字节码），
 *       所以"被取消"这条路径根本进不来。</li>
 * </ol>
 * ★ <b>不做冷却</b>（用户口径）：对空气右键本身就消耗 1 个物品，不会刷屏。
 *
 * <h2>★ 主手闸：为什么还要判 {@link EquipmentSlot#MAIN_HAND}</h2>
 * 本体对每一个 {@code ItemUseHandler} 都会调一次，而 {@code PlayerInteractEvent}
 * 会为主手与副手各发一次 ⇒ 双手都拿着本物品时<b>会触发两次</b>（扣 2 个、生成 2 只）。
 * 判主手就能把它钉成"一次右键 = 一次效果"。
 *
 * <h2>★ 粒子常量：本版本<b>没有</b> {@code GREEN_STAR}（javap 核实）</h2>
 * 需求原本想要"绿色星形粒子"。{@code javap} 在运行期
 * {@code paper-api-1.20.4.jar} 上核实：{@code org.bukkit.Particle} <b>没有</b>
 * {@code GREEN_STAR} 这个常量。项目里"绿色星形"的既有对应物是
 * {@link Particle#VILLAGER_HAPPY}——村民交易成功时头顶那种<b>绿色小星星</b>
 * （「丰收之时」用的就是它，见 {@code HarvestTime#spawnParticles}）。
 * ⇒ 本实现用 {@code VILLAGER_HAPPY}，<b>单粒子、单次</b>（用户原话"1 个"）。
 * 备选 {@code COMPOSTER} 是"堆肥桶里冒出的绿色小点"，形状是<b>点</b>不是星，故不采用。
 */
public class FairyInMist extends SlimefunItem {

    /** 本物品在注册表里的 id（供命令与日志引用，避免多处硬编码字符串）。 */
    public static final String ID = "TOUHOU_CHARACTER_FAIRY_IN_MIST";

    /** 召唤物的自定义名（用户给定原文，<b>逐字不改</b>）。 */
    public static final String BOMB_NAME = "Bomb";

    /**
     * 召唤物名字的颜色 —— <b>绿色</b>（用户没说颜色，本实现自己定）。
     *
     * <p>选 {@code §a}（原版"绿色"）而不是 {@code §2}（深绿）：与聊天栏那一行同色，
     * 也与"亮绿色字体"的整体调性一致 —— 用同一个颜色常量同时喂给
     * {@link #NAME_COLOR} 与 {@link #CHAT_COLOR}，保证两处不会各写一个绿。
     */
    public static final String NAME_COLOR = "\u00a7a";

    /**
     * 聊天栏那一行的颜色 —— <b>绿色</b>（与召唤物名字同色，见 {@link #NAME_COLOR}）。
     */
    public static final String CHAT_COLOR = "\u00a7a";

    /** 掉落物（物品形式的 TNT）的显示名。 */
    public static final String BOMB_ITEM_NAME = BOMB_NAME;

    /**
     * 掉落物要挂的<b>原版附魔</b>与等级：<b>保护 IX</b>。
     *
     * <p>★ 常量名已用 {@code javap} 在运行期 {@code paper-api-1.20.4.jar} 上核实：
     * <pre>
     *   public static final org.bukkit.enchantments.Enchantment PROTECTION_ENVIRONMENTAL;
     * </pre>
     * 这就是中文里的「保护」（1.20.4 还没改名叫 {@code PROTECTION}）。
     *
     * <p>★ 等级 <b>9</b> 远超原版上限（保护自然上限 4）⇒ 必须走
     * {@link ItemStack#addUnsafeEnchantment(Enchantment, int)}
     * （同样已 javap 核实：{@code public void addUnsafeEnchantment(...)} 在
     * {@code org.bukkit.inventory.ItemStack} 上，{@code ItemMeta} 上没有这个方法，
     * 那边只有 {@code addEnchant(Enchantment, int, boolean)}）。
     */
    public static final Enchantment BOMB_ENCHANTMENT = Enchantment.PROTECTION_ENVIRONMENTAL;

    /** 附魔等级 —— 用户口径：<b>9</b>（「保护 IX」）。 */
    public static final int BOMB_ENCHANTMENT_LEVEL = 9;

    /**
     * 召唤物存在多久（tick）—— <b>20 tick = 1 秒</b>。
     *
     * <p>★ 这是<b>原版 tick</b>（20/秒），与 {@code Slimefun#getTickerTask().getTickRate()}
     * 无关 —— {@code BukkitScheduler#runTaskLater} 收的就是原版 tick。
     */
    public static final long LIFETIME_TICKS = 20L;

    /**
     * 要不要让苦力怕的<b>自定义名可见</b>。
     *
     * <p>★ 这是本实现的判断（用户只说"自定义名 Bomb"）。选 <b>true</b>：
     * 这个东西的全部意义就是"玩家看得见一只叫 Bomb 的苦力怕朝自己冲过来"，
     * 名字看不见等于没做。同时它的寿命只有 1 秒，不会长期占着屏幕。
     */
    public static final boolean BOMB_NAME_VISIBLE = true;

    /**
     * 苦力怕的爆炸半径 —— <b>0</b>。
     *
     * <p>★ <b>绝不能让这个召唤物真的炸</b>（用户原话"别让它真炸"）。
     * 除了 {@link #BOMB_INVULNERABLE} 与 {@link #BOMB_AI}，这里把爆炸半径直接压到 0，
     * 于是"即使它因为任何原因被点燃/自爆，爆炸也是 0 强度"——这是第三道保险。
     * 而且在 {@link #LIFETIME_TICKS} 到点时我们是 {@code remove()} 它，
     * 它根本没有机会走到引信结束。
     */
    public static final int BOMB_EXPLOSION_RADIUS = 0;

    /** 召唤物是否无敌（{@code setInvulnerable(true)}）—— 用户口径要求。 */
    public static final boolean BOMB_INVULNERABLE = true;

    /**
     * 召唤物是否关闭 AI（{@code setAI(false)}）。
     *
     * <h2>★★ 实测结论：必须保持 <b>true</b>（本项目为这条付过一次排查）</h2>
     * 第一版把它设成 {@code false}（"不追玩家、不捣乱"），实机（无头）结果是：
     * <pre>
     *   probe@1  valid=true  age=0  inWorld=true
     *   probe@3  valid=false age=1  inWorld=false   ← 第 1~3 tick 之间就没了
     * </pre>
     * 症状是"苦力怕生成后瞬间消失、原地什么都没留下"，而且
     * {@code persistent=true / invulnerable=true} 都拦不住。
     *
     * <p><b>根因</b>：这只苦力怕身上挂了<b>自定义名</b> ⇒ 它不再算"自然生成"，
     * 于是原版基于 {@code isPersistenceRequired()} 的清除逻辑会把它算成"该清掉的"，
     * 而"没有 AI"又让它没有任何存活目标 ⇒ 第 1 个 tick 就被原版直接丢弃。
     * （把 {@code persistent} 设成 true 也救不了：那个 flag 管的是
     *  {@code canDespawn()} 那一路，而这里走的是"无 AI 的非自然实体"那条路。）
     *
     * <p><b>解法</b>：AI 保持 <b>true</b>，改用其它手段保证它"不捣乱"：
     * {@link #BOMB_INVULNERABLE}（打不死）、{@link #BOMB_SILENT}（不叫）、
     * {@link #BOMB_EXPLOSION_RADIUS}＝0（炸不了）、{@link #BOMB_PERSISTENT}（不自然清除），
     * 再加 {@link #BOMB_COLLIDABLE}＝false（可以穿过去，不会把人挤下平台）。
     * 寿命只有 1 秒，它也没有时间"追着玩家跑"。
     */
    public static final boolean BOMB_AI = true;

    /**
     * 召唤物是否可碰撞（{@code setCollidable(false)}）。
     *
     * <p>★ 本实现的判断：AI 必须开着（见 {@link #BOMB_AI} 的实测结论），
     * 但"一只会朝你走过来的苦力怕"仍可能把玩家挤下平台/推进岩浆。
     * 关掉碰撞之后它可以被穿过，同时保留 AI 带来的"不会被原版立刻清除"这一性质。
     */
    public static final boolean BOMB_COLLIDABLE = false;

    /**
     * 召唤物是否静音（{@code setSilent(true)}）。
     *
     * <p>★ 本实现的判断：原版苦力怕的嘶嘶声是"它要炸了"的听觉信号，
     * 而我们这只<b>不会炸</b> —— 让它发出嘶嘶声等于骗玩家去躲，属于制造混乱。
     */
    public static final boolean BOMB_SILENT = true;

    /**
     * 召唤物是否持久（{@code setPersistent(true)}）。
     *
     * <p>★ 本实现的判断：只为那 1 秒的寿命买一份保险 ——
     * 若玩家在它消失前跑远、把它所在的区块卸载/离开实体跟踪范围，
     * 非持久实体会被"自然清除"，那样 20 tick 后的 {@code runTaskLater} 里
     * {@code creeper.isValid()} 就为 false，原地什么都不会留下。
     * 设成持久后它一定会活到被我们 {@code remove()} 的那一刻。
     */
    public static final boolean BOMB_PERSISTENT = true;

    /**
     * 掉落的物品是不是"物品形式的 TNT"。
     *
     * <p>★ 用户原话：这个 TNT <b>后续可能改</b>。所以"生成什么"整件事收在
     * {@link #createBombItem()} 里，名字/附魔/等级都抽成了常量 —— <b>一处改，处处生效</b>。
     */
    public static final Material BOMB_ITEM_MATERIAL = Material.TNT;

    public FairyInMist(ItemGroup itemGroup, SlimefunItemStack item,
                       RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        // ★ 注意：本类直接继承 SlimefunItem、用 addItemHandler 逐个注册，
        //   而【不】用 SimpleSlimefunItem —— 后者只支持"一个" handler
        //   （它的 preRegister 固定调 getItemHandler() 一次），
        //   而本物品需要【两个】：
        //     ① ItemUseHandler   → 对空气右键触发效果
        //     ② BlockUseHandler  → 右键已放下的方块时给一条"这只是占位"的提示
        //   两者缺一不可，所以必须走 addItemHandler 这条路。
        addItemHandler((ItemUseHandler) this::onUse);

        // ★★ 为什么敢注册 BlockUseHandler（本项目对它有历史阴影）：
        //   本体 SlimefunItemInteractListener#rightClickBlock 的判据是
        //   "callItemHandler(BlockUseHandler.class, …) 返回 true 就不开自有界面"，
        //   而那个返回值是"这个物品有没有注册该 handler"（恒为 true）
        //   ⇒ 注册了它就永远打不开自有界面（见 modules/04 §1.1 与 UtsuhoReactorCore 的注释）。
        //   本物品【刻意不做 GUI】（需求没要求），所以这条副作用对我们不存在 ——
        //   与「丰收之时」用同一路数。
        addItemHandler((BlockUseHandler) event -> {
            Block block = clickedBlock(event);
            Player player = event.getPlayer();
            if (player == null) {
                return;
            }
            // 权限判据与项目其它机器同源（bypass 或 canUse + 领地交互权）——
            // 没权限就静默返回：这只是个占位方块，没必要对没权限的人喊话。
            if (!canUseHere(player, block)) {
                return;
            }
            Notify.warn(Notify.fairy(), player, PLACEHOLDER_HINT);
        });
    }

    /**
     * 右键已放下的方块时给玩家的提示文案 —— <b>整个插件里这句话只有这一个出处</b>
     * （命令的自检输出也读它）。
     *
     * <p>★ 需求没给文案，本实现自己定。选这句的理由：
     * <ol>
     *   <li>先把"<b>没有失效</b>"说出来（"已经用掉了"），否则玩家会以为是 bug
     *       —— 它确实是"一次性的：对空气右键就消耗掉"；</li>
     *   <li>再说清"放下的这个只是个占位"，并点名它<b>不会</b>触发效果；</li>
     *   <li>最后给一句"该怎么做"，把玩家引回正确用法（对空气右键）。</li>
     * </ol>
     * 用 {@link Notify#warn} 而不是 {@code info}：玩家"主动做了一件事但没成功"
     * 正是 warn 的判据，而且 {@code Notify.info} 在默认档位下是<b>静默</b>的
     * （见 modules/04 §6.2），用它等于"点了没反应"。
     */
    public static final String PLACEHOLDER_HINT =
            "&7这只是「雾中の妖精」的&f占位方块&7 —— 右键它&c不会触发效果&7。"
                    + "把它拿在手里&f对空气右键&7才会消耗并召唤 Bomb。";

    // ------------------------------------------------------------------ ① 对空气右键

    /**
     * 本物品的 {@link ItemUseHandler}：<b>只有对空气右键</b>才走效果。
     *
     * <p>顺序（原子性判据见类注释）：判动作 → 判主手 → 判手里有东西 →
     * <b>扣 1 个</b> → 生成苦力怕（中间不再 return）。
     */
    private void onUse(PlayerRightClickEvent event) {
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }

        // ---- ① 动作闸：只有"对空气右键"才触发（判据见类注释：ItemUseHandler 不区分空气/方块）
        Action action = actionOf(event);
        if (action != Action.RIGHT_CLICK_AIR) {
            return;     // 对方块右键 = 放下（或点到了别的东西），不触发
        }

        // ---- ② 主手闸：双手都拿着时本体只会各调一次，不拦就会扣 2 个、生成 2 只
        //   ★★ 常量名是 {@code HAND} 而【不是】{@code MAIN_HAND}（javap 核实）：
        //   ```
        //   javap -cp paper-api-1.20.4.jar org.bukkit.inventory.EquipmentSlot
        //     public static final ... EquipmentSlot HAND;       ← 主手
        //     public static final ... EquipmentSlot OFF_HAND;
        //   ```
        //   `MAIN_HAND` 是 1.20.5+ 才改的名 —— 与本项目 §I 里
        //   「PotionEffectType.SLOW 而不是 SLOWNESS」是同一族坑：
        //   **凭记忆写枚举常量名会直接编译不过（这个还算友好）**。
        //   旁证：本体 `SlimefunItemInteractListener` 里判副手用的就是
        //   `EquipmentSlot.OFF_HAND`（反编译可见），主手那一侧即 `HAND`。
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        // ---- ③ 手里确实有这个东西（本体已经用 isItemSimilar 筛过，这里是双保险）
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType().isAir() || held.getAmount() <= 0) {
            return;
        }

        // ================= 以下"开扣之后不再中途返回" =================
        // ---- ④ 扣 1 个（★ 与生效原子化：先扣、后生效）
        consumeOne(player, held);

        // ---- ⑤ 生成苦力怕 + 安排 20 tick 后的收尾
        SpawnReport report = summon(player);
        if (!report.creeperSpawned) {
            // 走到了这里说明物品已经扣了、苦力怕却没生成 —— 如实报出来：
            // 控制台留一行 warning（管理员需要知道有玩家白丢了一个物品），
            // 同时让玩家看到一句提示（否则他只看到"东西没了、什么都没发生"）。
            Log.warn("[FAIRY] ★ 召唤失败但物品已消耗：player=" + player.getName()
                    + " reason=" + report.failure);
            Notify.warn(Notify.fairy(), player,
                    "&c召唤失败了（物品已消耗，请联系管理员）");
            return;
        }

        // ---- ⑥ 聊天栏那一行：绿色，只有触发者看得到
        //   ★★ 为什么这里破例用 player.sendMessage 而不是 Notify（与 Cirno 那次同一理由）：
        //     ① 用户给的是"一整行"原话（Bomb），而 Notify 的每条消息都会在前面拼上
        //        作用域前缀（Notify.send: prefix + text），玩家看到的就成了
        //        "[雾中の妖精] Bomb"，不再是那行原话；
        //     ② 用户明确要求"绿色字体"，而 Notify 的前缀另有颜色，
        //        混在一起没法保证"整行就是绿色"；
        //     ③ Notify.info 在项目默认档位（important）下是【静默】的，用它等于没反应；
        //        而 Notify.warn 会带上前缀，回到第 ① 条。
        //   ⇒ 结论：这一处用 player.sendMessage(颜色 + 原名)。
        //   占位提示与权限提示照旧走 Notify.warn（那两条是"操作没成功"，不介意带前缀）。
        player.sendMessage(CHAT_COLOR + BOMB_NAME);
        Log.info(report.logLine());
    }

    /**
     * 从主手扣掉 1 个。
     *
     * <p>★ 用 {@code setAmount(amount - 1)} 而不是 {@code ItemStack} 替换：
     * 这样物品的粘液 id（PDC）与其它 meta 都不会丢 —— 玩家手里的第 2 个、
     * 第 3 个仍然是同一个物品，而不是变成"看起来一样但没有粘液 id"的普通头颅
     * （那正是 modules/04 §4.3 里 {@code new ItemStack(type, n)} 的坑）。
     *
     * <p>★ 归零时显式写 {@code null}（清空那一格），而不是留一个 {@code amount=0} 的栈 ——
     * 后者在不同版本里可能被渲染成"幽灵物品"。
     */
    public static void consumeOne(Player player, ItemStack held) {
        if (player == null || held == null) {
            return;
        }
        int left = held.getAmount() - 1;
        if (left <= 0) {
            player.getInventory().setItemInMainHand(null);
        } else {
            held.setAmount(left);
            player.getInventory().setItemInMainHand(held);
        }
    }

    // ------------------------------------------------------------------ ② 效果内核

    /**
     * <b>效果内核</b>：在玩家身前 1 格生成那只苦力怕，并安排 1 秒后的收尾。
     *
     * <p>★ 抽成 {@code public} 是为了让<b>无头验证能调真实入口</b>
     * （{@code /touhou fairy effect}）—— 与 {@code Cirno.freeze} / {@code HarvestTime.harvest}
     * 同一路数。命令验的是"效果内核"，<b>不是</b>"玩家对空气右键那一步"
     * （扣物品、动作闸、主手闸、聊天栏那句话都不在其中）—— 报告里必须写清这条。
     *
     * @param player 触发者（<b>只用来取朝向与世界</b>，可以为 null ⇒ 返回未生成的报告）
     * @return 生成读数（见 {@link SpawnReport}）
     */
    public SpawnReport summon(Player player) {
        SpawnReport report = new SpawnReport();
        if (player == null) {
            report.failure = "player==null";
            return report;
        }
        report.actor = player.getName();
        return summonAt(spawnLocation(player), report);
    }

    /**
     * 与 {@link #summon(Player)} <b>同一个内核</b>，但只吃一个坐标 —— 不需要玩家。
     *
     * <p>★ 为什么要这一层：无头测试服<b>没有真玩家</b>，而 {@code Player} 对象无法伪造
     * （魔改/假玩家属于 modules/07 §9.6 明令禁止的"造假玩家"）。
     * 所以把"需要玩家"的部分压缩到只剩"从玩家身上算出坐标"，内核改成吃 {@code Location} ——
     * 于是 {@code /touhou fairy effect <x> <y> <z>} 验的是<b>真内核</b>，
     * 而 {@code summon(Player)} 只是"算坐标 + 调内核"。
     * 两条路生成的实体、挂的标记、安排的 20 tick 收尾<b>逐行相同</b>。
     *
     * @param at     召唤点（可以为 null ⇒ 返回未生成的报告）
     * @param report 复用的报告载体（可以为 null ⇒ 自己造一个，仅用于返回值）
     */
    public SpawnReport summonAt(Location at, SpawnReport report) {
        SpawnReport out = report == null ? new SpawnReport() : report;
        if (at == null) {
            out.failure = "location==null";
            return out;
        }
        out.location = at;
        World world = at.getWorld();
        if (world == null) {
            out.failure = "world==null";
            return out;
        }
        try {
            Entity entity = world.spawnEntity(at, org.bukkit.entity.EntityType.CREEPER);
            if (!(entity instanceof Creeper creeper)) {
                out.failure = "spawn 返回的不是 Creeper（实际 "
                        + (entity == null ? "null" : entity.getType().name()) + "）";
                return out;
            }
            out.creeper = creeper;
            out.creeperSpawned = true;

            // ---- 外观与"绝不捣乱"的四道保险（判据见各常量注释）
            creeper.setCustomName(NAME_COLOR + BOMB_NAME);
            creeper.setCustomNameVisible(BOMB_NAME_VISIBLE);
            creeper.setInvulnerable(BOMB_INVULNERABLE);   // 无敌
            creeper.setAI(BOMB_AI);                       // ★ 必须 true（见 BOMB_AI 的实测结论）
            creeper.setCollidable(BOMB_COLLIDABLE);       // 可以穿过去，不会把人挤下平台
            creeper.setSilent(BOMB_SILENT);               // 不嘶嘶叫（它不会炸，叫了是骗人）
            creeper.setPersistent(BOMB_PERSISTENT);       // 1 秒内不被自然清除
            creeper.setExplosionRadius(BOMB_EXPLOSION_RADIUS);   // 第三道保险：爆炸半径 0
            creeper.setPowered(false);

            // ---- 20 tick 后收尾（原版 tick 口径）
            final Creeper target = creeper;
            // ★★ 收尾锚定的是【召唤时记下的 Location】，不是"20 tick 后再去问实体要坐标"。
            //   为什么（实测撞到的两条）：
            //   ① 无头服/无人在线的服务端上，实体可能在 20 tick 之前就被服务器清掉了
            //      （实测：连一只完全没被改过的原版苦力怕都会在 1 tick 后被移除）。
            //      这时 `creeper.getLocation()` 要么抛异常、要么给出"死亡那一瞬"的坐标；
            //      而我们要的是"它当初站在哪"—— 那个值在召唤那一刻就已经确定了。
            //   ② 真实游玩里同理：玩家在 1 秒内下线/切维度/走远，实体也可能提前离场。
            //   所以先把坐标存下来，20 tick 后只管"在那一刻的原地留下东西"。
            final Location anchor = at.clone();
            if (debugProbes()) {
                probeEarly(target);
            }
            Touhou.getInstance().getServer().getScheduler().runTaskLater(Touhou.getInstance(),
                    () -> finishLoggedAt(anchor, target), LIFETIME_TICKS);
            return out;
        } catch (RuntimeException e) {
            out.failure = String.valueOf(e);
            return out;
        }
    }

    /**
     * 无头诊断：在第 1 / 3 / 5 tick 各读一次"实体还在不在 + 几个关键 flag"。
     *
     * <p>★ 为什么需要它（本次真实排查记录）：无头实测发现召唤出来的苦力怕
     * 会在<b>第 1~5 tick 之间</b>变成 {@code isValid=false / isDead=true}，
     * 而这直接决定了根因 —— 是原版把"无 AI 且周围无玩家"的实体自然清掉了，
     * 还是别的插件/别的代码把它删了。没有这条时间线就只能靠猜。
     */
    public static void probeEarly(Creeper target) {
        for (long probeTick : EARLY_PROBE_TICKS) {
            Touhou.getInstance().getServer().getScheduler().runTaskLater(Touhou.getInstance(),
                    () -> Log.always("[FAIRY] probe@" + probeTick
                            + " valid=" + target.isValid()
                            + " dead=" + target.isDead()
                            + " persistent=" + target.isPersistent()
                            + " invulnerable=" + target.isInvulnerable()
                            + " ai=" + target.hasAI()
                            + " silent=" + target.isSilent()
                            + " age=" + target.getTicksLived()
                            + " inWorld=" + target.getWorld().getEntities().contains(target)),
                    probeTick);
        }
    }

    /**
     * 逐 tick 快照的诊断开关 —— <b>系统属性</b> {@code -Dtouhou.debugFairy=true}。
     *
     * <p>★ 为什么用系统属性而不是 config.yml 项：与项目里
     * {@code -Dtouhou.debugReactor} / {@code -Dtouhou.debugStructure} 同一路数 ——
     * 正式包上零噪音、零配置面，排查时加一个 JVM 参数就能开。
     * 无头验证脚本（{@code /touhou fairy effect … --timer}）会显式打开它。
     */
    public static boolean debugProbes() {
        try {
            return Boolean.parseBoolean(System.getProperty("touhou.debugFairy", "false"));
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** 诊断用：在 {@code at} 生成一只"完全没有被我们改过"的原版苦力怕，并同样快照它。 */
    public static Creeper spawnVanillaProbe(Location at) {
        Creeper plain = (Creeper) at.getWorld()
                .spawnEntity(at, org.bukkit.entity.EntityType.CREEPER);
        Log.always("[FAIRY] vanilla-probe spawned valid=" + plain.isValid()
                + " persistent=" + plain.isPersistent()
                + " ai=" + plain.hasAI());
        probeEarly(plain);
        return plain;
    }

    /**
     * {@link #summonAt} 里那几个"早期快照"的 tick 点。
     *
     * <p>★ 只用于无头诊断（{@code Log.always}，不受 {@code console-info} 影响）：
     * 它把"实体在第几 tick 消失"变成一行可读的读数。
     */
    private static final long[] EARLY_PROBE_TICKS = {1L, 3L, 5L, 19L};

    /**
     * 调度器真正调用的那一层：{@link #finish} 外面包一圈<b>绝不抛出去</b>的日志。
     *
     * <p>★ 为什么要这一层：{@link #finish} 是在 {@code runTaskLater} 里跑的，
     * 一旦它抛异常，异常会被 Bukkit 的调度器吞进控制台堆栈，而我们自己完全看不到
     * —— 表现为"1 秒后苦力怕没了、原地却什么都没留下"，且没有任何线索。
     * 实测就是这样：第一版 {@code --timer} 报 {@code dropFound=false}，
     * 加上这一层之后 {@code finish} 的返回值/异常才会出现在日志里。
     */
    public static void finishLogged(Creeper creeper) {
        finishLoggedAt(creeper == null ? null : creeper.getLocation(), creeper);
    }

    /**
     * 收尾的日志包装（带"实体还在不在"的诊断行）—— 锚定 {@code anchor} 坐标。
     *
     * @param anchor  召唤时记下的坐标（收尾时"原地"就指这里）
     * @param creeper 那只苦力怕（可能已经失效；只用来报告状态与尽力 remove）
     */
    public static void finishLoggedAt(Location anchor, Creeper creeper) {
        try {
            // ★ 先打一行"收尾被调用的那一刻，实体还在不在" ——
            //   这一行把"收尾没跑"与"收尾跑了但实体已经没了"两种完全不同的原因分开
            //   （两者的症状都是"原地什么都没留下"）。实测发现无头服上实体会被提前清掉，
            //   正是靠这一行才定位到的。
            Log.always("[FAIRY] finish called: anchor=" + (anchor == null ? "(null)"
                    : anchor.getBlockX() + "," + anchor.getBlockY() + "," + anchor.getBlockZ())
                    + " creeperNull=" + (creeper == null)
                    + " creeperValid=" + (creeper != null && creeper.isValid()));
            InPlaceReport report = finishAt(anchor, creeper);
            // ★ 必须用 Log.always / Log.warn 而不是 Log.info：
            //   config.yml 的 logging.console-info 默认 false ⇒ Log.info 全部静默，
            //   于是"1 秒后的收尾到底做了什么"在控制台里一个字都看不到。
            //   诊断追踪行与命令回显一样，属于"我就是要看线索"，必须绕过总开关。
            Log.always(report.logLine());
        } catch (RuntimeException e) {
            Log.warn("[FAIRY] ★ 1 秒后的收尾抛异常（原地可能什么都没留下）：" + e);
        }
    }

    /**
     * 1 秒后的收尾：<b>让苦力怕原地消失</b>（{@code remove()}，不是死亡），
     * 并在原地撒粒子 + 掉出 {@code Bomb}。
     *
     * <p>★ 抽成 {@code public} 有<b>两个</b>理由：
     * <ol>
     *   <li>无头验证可以<b>直接调它</b>验"原地留下什么"（不必真的等 1 秒）；</li>
     *   <li>它是 {@code runTaskLater} 里跑的<b>同一个</b>方法 ——
     *       于是"命令验过的"与"1 秒后真正跑的"是同一份代码。</li>
     * </ol>
     *
     * <p>★ 用 {@code remove()} 而不是 {@code setHealth(0)} / {@code damage()}：
     * 用户要的是"直接消失"。{@code remove()} 不产生死亡动画、不掉落经验、
     * 也不触发 {@code EntityDeathEvent}（其它插件不会以为"有人杀了一只苦力怕"）。
     *
     * <p>★ {@code creeper.isValid()} 是必须的：虽然我们把实体设成了
     * {@link #BOMB_PERSISTENT}，但玩家下线/切维度/世界卸载这些情况仍可能让它提前离场，
     * 那时候 {@code getLocation()} 会抛异常 —— 所以"它还在不在"要显式判。
     */
    public static InPlaceReport finish(Creeper creeper) {
        return finishAt(creeper == null ? null : creeper.getLocation(), creeper);
    }

    /**
     * 收尾内核（锚定坐标版）：<b>让苦力怕原地消失</b>（{@code remove()}，不是死亡），
     * 并在 {@code anchor} 撒粒子 + 掉出 {@code Bomb}。
     *
     * <p>★★ 为什么坐标是<b>参数</b>而不是"从实体上现取"：见 {@code summonAt} 里那段注释
     * （无头服会把实体提前清掉；真实游玩里玩家也可能在 1 秒内离线/切维度）。
     * 锚定坐标让"1 秒后原地留下什么"这件事<b>不依赖那个实体还活着</b>。
     *
     * <p>★ 用 {@code remove()} 而不是 {@code setHealth(0)} / {@code damage()}：
     * 用户要的是"直接消失"。{@code remove()} 不产生死亡动画、不掉落经验、
     * 也不触发 {@code EntityDeathEvent}（其它插件不会以为"有人杀了一只苦力怕"）。
     * 实体已经不存在时跳过它，不算失败。
     */
    public static InPlaceReport finishAt(Location anchor, Creeper creeper) {
        InPlaceReport report = new InPlaceReport();
        if (anchor == null) {
            report.failure = "location==null";
            return report;
        }
        report.location = anchor.clone();
        // ---- 让它消失（已经不在世界就跳过）
        if (creeper != null && creeper.isValid()) {
            creeper.remove();
            report.creeperRemoved = true;
        } else {
            report.creeperAlreadyGone = true;
        }
        inPlace(report.location, report);
        return report;
    }

    /**
     * 直接验证用：<b>不等 1 秒</b>立刻走一遍 {@link #finish}。
     *
     * <p>★ 存在的理由：只有它才能让无头验证在同一 tick 内把
     * "召唤 → 收尾 → 原地留下什么"整条链读完（{@code runTaskLater} 要等服务器 tick）。
     * 它调的仍然是<b>同一个</b> {@link #finish}，所以"命令验的"与"1 秒后真正跑的"
     * 是同一份代码 —— 唯一的差别是"谁在什么时候调它"。
     *
     * @return 收尾读数；{@code creeper} 为 null / 已失效时如实返回 failure
     */
    public static InPlaceReport finishNow(Creeper creeper) {
        return finish(creeper);
    }

    /**
     * "原地留下什么" —— 抽出来是为了让无头验证能<b>不依赖实体</b>直接验这一半。
     *
     * <p>两步：① 1 个绿色星形粒子；② 掉出 {@code Bomb} 物品。
     *
     * <p>★ 粒子用 {@link Particle#VILLAGER_HAPPY}（本版本没有 {@code GREEN_STAR}，
     * 判据见类注释），<b>数量 1</b>（用户原话"1 个"）。
     * 三个 offset 传 0 是为了让它<b>精确落在原地</b>，不随机散开 ——
     * "在它原地"这四个字就是这么落实的。
     */
    public static void inPlace(Location at, InPlaceReport report) {
        if (at == null) {
            if (report != null) {
                report.failure = "location==null";
            }
            return;
        }
        World world = at.getWorld();
        if (world == null) {
            if (report != null) {
                report.failure = "world==null";
            }
            return;
        }
        // ① 绿色星形粒子（单次、精确原地）
        world.spawnParticle(Particle.VILLAGER_HAPPY, at, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        if (report != null) {
            report.particleSpawned = true;
        }
        // ② 掉出 Bomb
        try {
            ItemStack drop = createBombItem();
            Item item = world.dropItem(at, drop);
            if (report != null) {
                report.droppedItem = item;
                report.dropSpawned = item != null;
            }
        } catch (RuntimeException e) {
            if (report != null) {
                report.failure = "掉出物品失败: " + e;
            }
        }
    }

    /**
     * <b>"生成什么"的唯一出处</b> —— 用户说这个 TNT 后续可能改，所以整件事收在这里。
     *
     * <p>规格：{@link Material#TNT}，名字 {@code Bomb}（绿色），
     * 带原版「保护 IX」（{@link #BOMB_ENCHANTMENT} / {@link #BOMB_ENCHANTMENT_LEVEL}），
     * <b>不加</b> {@code HIDE_ENCHANTS} —— 用户要看到那行附魔。
     *
     * <p>★★ 必须用 {@link ItemStack#addUnsafeEnchantment(Enchantment, int)}：
     * 保护的原版上限是 4 级，9 级走普通的 {@code addEnchantment} 会被裁到 4
     * （甚至会抛 {@code IllegalArgumentException}）。已 javap 核实它就在
     * {@code org.bukkit.inventory.ItemStack} 上（{@code ItemMeta} 上没有这个方法）。
     *
     * <p>★ 名字要自己翻颜色码：{@code ItemMeta#setDisplayName} <b>不认</b>
     * {@code &}（见 modules/02 §1.1）—— 这里直接写 {@code §a}，不经过构造器。
     */
    public static ItemStack createBombItem() {
        ItemStack stack = new ItemStack(BOMB_ITEM_MATERIAL);
        // ★ 先附魔再改 meta：addUnsafeEnchantment 会自己建 meta，
        //   若顺序反过来（先 setItemMeta 再 addUnsafeEnchantment），
        //   在部分实现上两次 meta 写入会互相覆盖。
        stack.addUnsafeEnchantment(BOMB_ENCHANTMENT, BOMB_ENCHANTMENT_LEVEL);

        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(NAME_COLOR + BOMB_ITEM_NAME);
            // ★ 刻意【不】加 HIDE_ENCHANTS —— 用户要看到「保护 IX」那一行。
            stack.setItemMeta(meta);
        }
        return stack;
    }

    /**
     * 召唤位置：<b>玩家身前 1 格、与玩家同 y</b>（取脚下位置）。
     *
     * <p>★★ 为什么不用 {@code player.getLocation().add(player.getDirection())}：
     * {@code getDirection()} 是<b>带 y 分量的单位向量</b> ——
     * 玩家抬头时召唤物会被放到天上、低头时会被塞进脚下，
     * 而且"1 格"会变成"朝视线方向 1 格"（斜着看时水平位移小于 1 格）。
     * 需求要的是"身前 1 格、同 y"，所以这里把朝向<b>压到整数方向</b>：
     * <pre>
     *   Minecraft yaw 口径：0 = +Z（南），水平朝向 = (-sin(yaw), 0, +cos(yaw))
     *   （与 FantasySeal#fire 里那段注释同一套公式）
     * </pre>
     * 再 {@code round} 到整数格 ⇒ 永远是与玩家同 y 的相邻一格。
     */
    public static Location spawnLocation(Player player) {
        Location base = player.getLocation();
        double yaw = Math.toRadians(base.getYaw());
        int dx = (int) Math.round(-Math.sin(yaw));
        int dz = (int) Math.round(Math.cos(yaw));
        // 朝向恰好落在 45° 上时两个分量都可能是 0？不会：sin/cos 不会同时为 0。
        // 但 45° 附近 round 之后可能是 (±1, ±1)（斜前方）—— 那仍是"身前 1 格"的合理读法。
        Location at = base.clone();
        at.setYaw(0.0F);
        at.setPitch(0.0F);
        at.add(dx, 0, dz);
        return at;
    }

    /** 取出事件里的动作（拿不到就返回 {@code null}）。 */
    private static Action actionOf(PlayerRightClickEvent event) {
        if (event == null) {
            return null;
        }
        try {
            return event.getInteractEvent() == null
                    ? null : event.getInteractEvent().getAction();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 右键事件里的"被点方块"（右键空气时为空）。 */
    private static Block clickedBlock(PlayerRightClickEvent event) {
        if (event == null) {
            return null;
        }
        try {
            return event.getClickedBlock().orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 交互权限 —— 与 {@code ShrinePost#canOpen} / {@code Saizenbako#canOpen} /
     * {@code AbstractReactorPort#canOpen} / {@code HarvestTime#canHarvest} /
     * {@code Cirno#canUseHere} 完全同一条判据。
     */
    public boolean canUseHere(Player player, Block block) {
        if (player == null || block == null) {
            return false;
        }
        return player.hasPermission("slimefun.inventory.bypass")
                || (canUse(player, false) && Slimefun.getProtectionManager()
                        .hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK));
    }

    // ---------------------------------------------------------------- 诊断

    /**
     * 运行期那个物品（供 {@code /touhou fairy} 用）。
     *
     * <p>★ 刻意走<b>注册表</b>（{@link SlimefunItem#getById}）而不是直接读
     * {@code AddSlimefunItems.FAIRY_IN_MIST}：这样命令的输出顺带证明了
     * "它真的以那个 id 注册进 Slimefun 了"。
     */
    public static SlimefunItem find() {
        return SlimefunItem.getById(ID);
    }

    // ---------------------------------------------------------------- 结果载体

    /** 一次"召唤"的读数。 */
    public static final class SpawnReport {
        /** 召唤点。 */
        public Location location;
        /** 触发者名（进日志用）。 */
        public String actor = "(未知)";
        /** 生成出来的苦力怕（没生成时为 null）。 */
        public Creeper creeper;
        /** 苦力怕到底生成了没有 —— 原子性报告的关键字段。 */
        public boolean creeperSpawned;
        /** 失败原因（成功时为 null）。 */
        public String failure;

        /** 一行纯 ASCII 数值日志（便于 grep）。 */
        public String logLine() {
            return "[FAIRY] actor=" + actor
                    + " spawned=" + creeperSpawned
                    + " at=" + (location == null ? "(null)"
                            : location.getBlockX() + "," + location.getBlockY() + ","
                                    + location.getBlockZ())
                    + (failure == null ? "" : " failure=" + failure);
        }
    }

    /** 一次"原地收尾"的读数。 */
    public static final class InPlaceReport {
        public Location location;
        public boolean creeperRemoved;
        /** 收尾那一刻苦力怕已经不在世界（无头服/玩家离线时会这样）—— 不是失败。 */
        public boolean creeperAlreadyGone;
        public boolean particleSpawned;
        public boolean dropSpawned;
        /** 掉出来的那个实体（没掉出来时为 null）。 */
        public Item droppedItem;
        public String failure;

        public String logLine() {
            return "[FAIRY] inPlace at=" + (location == null ? "(null)"
                    : location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ())
                    + " removed=" + creeperRemoved
                    + " alreadyGone=" + creeperAlreadyGone
                    + " particle=" + particleSpawned
                    + " drop=" + dropSpawned
                    + (failure == null ? "" : " failure=" + failure);
        }
    }
}
