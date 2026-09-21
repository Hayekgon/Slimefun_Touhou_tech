package com.example.touhou.core;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.util.List;

/**
 * 「维度穿梭」的事件入口 —— 监听 {@link PlayerChangedWorldEvent}。
 *
 * <h2>★ 为什么选它（而不是旧的 EntityPortalEvent 方案）</h2>
 * 需求是"玩家<b>真的</b>从一个维度被传送到了另一个维度才算转化"。三条候选：
 * <table border="1">
 *   <caption>候选事件对比</caption>
 *   <tr><th>事件</th><th>触发时机</th><th>结论</th></tr>
 *   <tr><td>{@code PlayerPortalEvent}</td>
 *       <td>传送<b>之前</b>，而且<b>可以被取消</b>（取消后玩家留在原地）</td>
 *       <td>不适合单独用：它只说明"打算传"，还要额外延时一 tick 回读维度才能确认真的过去了</td></tr>
 *   <tr><td>{@code EntityPortalEnterEvent}</td>
 *       <td>实体碰到传送门方块时（反复触发，站在门里会一直刷）</td>
 *       <td>不行：语义是"碰到了门"，恰恰<b>不</b>满足"必须真的被传送"</td></tr>
 *   <tr><td><b>{@link PlayerChangedWorldEvent}</b></td>
 *       <td>玩家<b>已经</b>换到新世界之后</td>
 *       <td><b>采用</b>：事件驱动、零轮询；事件成立本身就等于"他真的过去了"</td></tr>
 * </table>
 *
 * <h2>★ 语义核对（已在 Paper 1.20.4 上逐条确认）</h2>
 * <ol>
 *   <li>{@code event.getFrom()} = <b>离开前</b>所在的世界（类里就存着这一个字段，
 *       Lombok 之外没有第二个世界字段）；目的世界不单独给，要用
 *       {@code player.getWorld()} 现场读 —— 事件触发时玩家已经在目的世界里了；</li>
 *   <li>它由 {@code CraftEventFactory} 在
 *       {@code net.minecraft.server.level.EntityPlayer#changeDimension(WorldServer, TeleportCause)}
 *       里 fire（<b>已用 javap 反编译服务端 jar 核对</b>：那个方法的字节码里有
 *       {@code new PlayerChangedWorldEvent}）。也就是说任何<b>真正</b>换了维度的路径
 *       都会走到它：下界/末地传送门、{@code /execute in}、插件传送、跨世界传送门插件…</li>
 *   <li>反过来说，"站在传送门里没被传"、"传送被别的插件取消"、"同世界内传送"
 *       都<b>不会</b>触发本事件 —— 需求里那句"必须真的被传送才算"因此是免费拿到的；</li>
 *   <li>维度判定不看世界名，只看 {@link World.Environment}
 *       （见 {@link EchoOfAnotherWorld#isShuttle}）：末地、同维度、CUSTOM 世界
 *       一律不转化。</li>
 * </ol>
 *
 * <h2>本类只做"递事件"这一件事</h2>
 * 全部裁决与转化都在 {@link EchoOfAnotherWorld#shuttle} 里，而
 * {@code /touhou echo} 命令调的是同一个方法 —— 于是"控制台验证过的行为"
 * 就是"玩家传送时发生的行为"，不存在两套逻辑。
 *
 * <p>优先级用 {@code MONITOR}：这是"只观察"的监听器（不取消事件、不改传送结果、
 * 不参与其它插件的裁决），按 Bukkit 约定只读的监听器放 MONITOR，保证它在
 * 所有可能改传送结果的插件之后跑。
 */
public class EchoOfAnotherWorldListener implements Listener {

    /**
     * 玩家换世界 —— 维度穿梭的唯一入口。
     *
     * <p>★ 这里刻意<b>不</b>做 {@code event} 之外的状态判断：总开关、维度组合、
     * 玩家冷却、幂等全部收在 {@link EchoOfAnotherWorld#shuttle} 一处，
     * 免得"事件路径"与"命令路径"慢慢长歪。
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent e) {
        Player player = e.getPlayer();
        if (player == null) {
            return;
        }
        World from = e.getFrom();
        World to = player.getWorld();
        try {
            EchoOfAnotherWorld.shuttle(player, from, to, false);
        } catch (RuntimeException ex) {
            // 传送是高频路径：这里绝不能把异常抛回事件总线（会变成
            // "Could not pass event" 并可能打断其它插件的传送后处理）。
            Log.severe("[ECHO] 处理维度穿梭时异常（玩家 " + player.getName()
                    + " " + (from == null ? "?" : from.getName())
                    + " -> " + (to == null ? "?" : to.getName()) + "）", ex);
        }
    }

    /** 供诊断：这个类一共监听了哪些事件（{@code /touhou echo selfcheck} 打印）。 */
    public static List<String> describe() {
        return List.of(
                "PlayerChangedWorldEvent（玩家换世界之后触发 —— "
                        + "没真的被传送就不会触发，天然满足「必须真的被传送」）",
                "优先级 MONITOR（只观察，不取消、不参与裁决）",
                "维度判定：只看 World#getEnvironment 的组合 主世界↔地狱",
                "转化逻辑与 /touhou echo shuttle 走的是同一个入口 EchoOfAnotherWorld#shuttle");
    }
}
