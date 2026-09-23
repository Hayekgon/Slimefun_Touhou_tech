package com.example.touhou.core;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * <b>面向玩家的消息的唯一出口</b> —— 全局消息闸门。
 *
 * <h2>为什么要有它</h2>
 * 需求：「尽量减少游戏内消息栏的输出，除了<b>重要事件</b>（例如多方块结构构建成功）
 * 和 <b>warning/error</b> 以外都不输出」。
 *
 * <p>麻烦在于"哪些算重要"是逐个调用点判断的，散落到各处就会失控
 * （本项目之前就是每个 GUI 按键都回一句"已切换到 X"，玩家点几下消息栏就刷屏了）。
 * 所以这里做一个**分档闸门**：调用点只负责声明"这条消息属于哪一档"，
 * "到底发不发"由闸门统一决定，并且能用一个配置项全局调。
 *
 * <h2>四档语义（默认只放前两档）</h2>
 * <table border="1">
 *   <caption>消息分档</caption>
 *   <tr><th>方法</th><th>内容</th><th>默认</th><th>例子</th></tr>
 *   <tr><td>{@link #warn}</td><td>warning / error</td>
 *       <td><b>永远输出</b>（不受档位影响）</td>
 *       <td>结构不完整无法激活、找不到核心、没权限</td></tr>
 *   <tr><td>{@link #important}</td><td>重要事件</td><td><b>输出</b></td>
 *       <td>多方块结构构建成功、反应堆已激活</td></tr>
 *   <tr><td>{@link #info}</td><td>常规操作反馈</td><td>不输出</td>
 *       <td>已切换到发电模式、粒子特效已关闭</td></tr>
 *   <tr><td>{@link #detail}</td><td>诊断细节</td><td>不输出</td>
 *       <td>属于哪座结构、绑定了哪个核心</td></tr>
 * </table>
 *
 * <p>控制项：{@code messages.level} = {@code off | important | normal | all}。
 *
 * <h2>两条硬规则</h2>
 * <ol>
 *   <li><b>面向玩家的输出一律走本类</b>，不要直接 {@code player.sendMessage}。
 *       直接调用就绕过了闸门，"减少消息"这件事下次就会失守。</li>
 *   <li>{@link #warn} <b>不</b>受档位影响 —— 需求明确要求 warning/error 必须输出。
 *       想把警告也关掉，请改代码而不是配置（那说明这条根本不该是 warn）。</li>
 * </ol>
 *
 * <p>⚠ 命令回显（{@code /touhou ...} 的输出）<b>不走这里</b>：
 * 那是玩家主动敲命令要的结果，属于"按需输出"而不是"推送"。
 */
public final class Notify {

    /** 消息档位。 */
    public enum Level {
        /** 只放 warning/error（连"结构构建成功"都不报）。 */
        OFF("off", 0),
        /** 默认：warning/error + 重要事件。 */
        IMPORTANT("important", 1),
        /** 再加上常规操作反馈。 */
        NORMAL("normal", 2),
        /** 再加上诊断细节。 */
        ALL("all", 3);

        private final String key;
        private final int rank;

        Level(String key, int rank) {
            this.key = key;
            this.rank = rank;
        }

        public String key() {
            return key;
        }

        /** 从配置字符串解析（认不出的回落到 IMPORTANT）。 */
        public static Level parse(String raw) {
            if (raw == null) {
                return IMPORTANT;
            }
            String v = raw.trim().toLowerCase();
            for (Level l : values()) {
                if (l.key.equals(v) || l.name().equalsIgnoreCase(v)) {
                    return l;
                }
            }
            return IMPORTANT;
        }

        /** 供配置面板展示的一句话。 */
        public String display() {
            return switch (this) {
                case OFF -> "off（只报 warning/error）";
                case IMPORTANT -> "important（warning/error + 重要事件）";
                case NORMAL -> "normal（再加常规操作反馈）";
                case ALL -> "all（再加诊断细节）";
            };
        }
    }

    private Notify() {
    }

    // ---------------------------------------------------------------- 分档输出

    /**
     * warning / error —— <b>永远输出</b>，不受 {@code messages.level} 影响。
     *
     * <p>判据：玩家<b>主动做了一件事但没成功</b>（点击失败、结构不完整、没权限、
     * 电力不足、找不到核心）。这类必须说，否则玩家只会看到"点了没反应"。
     */
    public static void warn(CommandSender to, String text) {
        send(REACTOR, to, text);
    }

    /**
     * 重要事件 —— 默认输出。
     *
     * <p>判据：<b>状态发生了玩家会真正关心的一次性变化</b>。
     * 目前只有一类：多方块结构构建成功（自动激活 / 提示去点激活）。
     */
    public static void important(CommandSender to, String text) {
        if (level().rank >= Level.IMPORTANT.rank) {
            send(REACTOR, to, text);
        }
    }

    /**
     * 常规操作反馈 —— 默认<b>不</b>输出。
     *
     * <p>判据：玩家自己做了一个动作，结果就在他眼前的界面上（按钮图标变了、
     * 模式文字变了）。这类"你也知道、界面也显示"的消息是消息栏刷屏的主要来源。
     */
    public static void info(CommandSender to, String text) {
        if (level().rank >= Level.NORMAL.rank) {
            send(REACTOR, to, text);
        }
    }

    /** 诊断细节（属于哪座结构、绑定了哪个核心…）—— 默认不输出。 */
    public static void detail(CommandSender to, String text) {
        if (level().rank >= Level.ALL.rank) {
            send(REACTOR, to, text);
        }
    }

    // ---------------------------------------------------------------- 内部

    // ---------------------------------------------------------------- 作用域
    //
    // ★ 为什么需要它（真实踩点，2026-09-18）：
    //   赛钱箱最初直接复用了反应堆的 messagePrefix，于是祭坛的提示顶着
    //   「&8[&6灵乌路空反应堆&8]」的前缀出现在玩家聊天栏里。
    //   现在每台机器有自己的"作用域"= 前缀 + 档位，互不串味。
    //   Scope 里字段为 null 表示"回落到反应堆那套默认值"。

    /** 一台机器（或一类方块）的消息作用域。 */
    public record Scope(String prefix, String level) {
    }

    private static final Scope REACTOR = new Scope(null, null);

    /** 反应堆作用域（前缀/档位取 config.yml 的 reactor 段）。 */
    public static Scope reactor() {
        return REACTOR;
    }

    /** 祭坛（赛钱箱）作用域 —— 前缀与档位独立，默认比反应堆更安静。 */
    public static Scope saizen() {
        AddonConfig cfg = AddonConfig.get();
        return new Scope(cfg.saizenPrefix, cfg.saizenMessageLevel);
    }

    /**
     * 梦想封印 集作用域 —— 前缀与档位独立。
     *
     * <p>★ 为什么道具也要有自己的作用域：它原先调用的是反应堆那个默认作用域，
     * 于是提示会顶着「&amp;8[&amp;6灵乌路空反应堆&amp;8]」的前缀出现 ——
     * 正是赛钱箱踩过的那条老路（见上面 {@link #saizen()} 的注释）。
     * 一个道具的消息前缀不该来自一台多方块机器。
     *
     * <p>档位默认 {@code off}：本道具目前只发 warning（{@link #warn} 不受档位影响），
     * 留着这一格只是为了"将来想推常规反馈时改配置即可"。
     */
    public static Scope seal() {
        AddonConfig cfg = AddonConfig.get();
        return new Scope(cfg.sealPrefix, cfg.sealMessageLevel);
    }

    /**
     * 杀意的百合作用域 —— 前缀与档位独立（{@code config.yml} 的 {@code lily:} 段）。
     *
     * <p>★ 与 {@link #seal()} 同理：一个道具的消息前缀不该来自另一件道具，
     * 也不该蹭反应堆的（那是赛钱箱踩过的老路）。
     */
    public static Scope lily() {
        AddonConfig cfg = AddonConfig.get();
        return new Scope(cfg.lilyPrefix, cfg.lilyMessageLevel);
    }

    /**
     * 「另一个世界的回响 / 维度穿梭」作用域 —— 前缀独立（{@code config.yml} 的 {@code echo:} 段）。
     *
     * <p>★ 与 {@link #seal()} / {@link #lily()} 同理：一条"你的水晶被解构成了回响"的提示
     * 不该顶着反应堆或某件符卡的前缀出现。
     *
     * <p>档位那一格<b>刻意留空</b>（{@code null} = 回落到反应堆的 {@code messages.level}）：
     * 本机制目前只发 {@link #warn}，而 warn 不受档位影响、永远输出，
     * 所以配一个没有任何调用点的档位只会让人以为"调了没用"。
     * 将来真要推常规反馈时，再照 seal / lily 的写法补上即可。
     */
    public static Scope echo() {
        AddonConfig cfg = AddonConfig.get();
        return new Scope(cfg.echoPrefix, null);
    }

    /**
     * 「丰收之时」作用域 —— 前缀独立（{@code config.yml} 的 {@code harvest:} 段）。
     *
     * <p>★ 与 {@link #seal()} / {@link #lily()} / {@link #echo()} 同理：一台机器的提示
     * 不该顶着别的机器或反应堆的前缀出现（赛钱箱踩过的老路）。
     *
     * <p>档位那一格<b>刻意留空</b>（{@code null} = 回落到反应堆的 {@code messages.level}）：
     * 本机器只发 {@link #warn}（催熟结果 / 冷却中 / 没权限），而 warn 永远输出、不受档位影响，
     * 所以配一个没有任何调用点的档位只会让人以为"调了没用"。
     */
    public static Scope harvest() {
        AddonConfig cfg = AddonConfig.get();
        return new Scope(cfg.harvestPrefix, null);
    }

    /**
     * 「冰の妖精」作用域 —— 前缀独立（{@code config.yml} 的 {@code cirno:} 段）。
     *
     * <p>★ 与 {@link #seal()} / {@link #lily()} / {@link #echo()} / {@link #harvest()} 同理：
     * 一台机器的提示不该顶着别的机器或反应堆的前缀出现（赛钱箱踩过的老路）。
     *
     * <p>档位那一格<b>刻意留空</b>（{@code null} = 回落到反应堆的 {@code messages.level}）：
     * 本机器只发 {@link #warn}（冷却中 / 没权限 —— 都是"玩家主动做了但没成功"），
     * 而 warn 永远输出、不受档位影响，所以配一个没有调用点的档位只会让人以为"调了没用"。
     *
     * <p>★ 唯一<b>不</b>走这里的是那句成功提示 {@code Bakabakabakabaka}：
     * 用户要求它是一整行蓝色文字、<b>不带任何前缀</b>，而本类的每条消息都会拼前缀
     * ⇒ 那一处由 {@code Cirno} 直接用 {@code player.sendMessage} 发，
     * 破例理由写在 {@code Cirno#getItemHandler} 的注释里。
     */
    public static Scope cirno() {
        AddonConfig cfg = AddonConfig.get();
        return new Scope(cfg.cirnoPrefix, null);
    }

    /**
     * 「雾中の妖精」作用域 —— 前缀独立（{@code config.yml} 的 {@code fairy:} 段）。
     *
     * <p>★ 与 {@link #seal()} / {@link #lily()} / {@link #echo()} / {@link #harvest()} /
     * {@link #cirno()} 同理：一件道具的提示不该顶着别的机器的前缀出现（赛钱箱踩过的老路）。
     *
     * <p>档位那一格<b>刻意留空</b>（{@code null} = 回落到反应堆的 {@code messages.level}）：
     * 本道具只用它发 {@link #warn}（"放下后只是个占位" / 没权限 —— 都是"玩家主动做了但没成功"），
     * 而 warn 永远输出、不受档位影响，所以配一个没有调用点的档位只会让人以为"调了没用"。
     *
     * <p>★ 那句成功的绿色 {@code Bomb} <b>不</b>走这里：用户要求它是一整行原话、
     * 不带任何前缀，而本类的每条消息都会拼前缀 ⇒ 那一处由 {@code FairyInMist} 直接用
     * {@code player.sendMessage} 发，破例理由写在那边的注释里。
     */
    public static Scope fairy() {
        AddonConfig cfg = AddonConfig.get();
        return new Scope(cfg.fairyPrefix, null);
    }

    /**
     * 指定作用域的 warning / error —— <b>永远输出</b>。
     *
     * <p>判据：玩家主动做了一件事但没成功。这类必须说，否则玩家只看到"点了没反应"。
     */
    public static void warn(Scope sc, CommandSender to, String text) {
        send(sc, to, text);
    }

    /** 指定作用域的重要事件（受该作用域的档位限制）。 */
    public static void important(Scope sc, CommandSender to, String text) {
        if (level(sc).rank >= Level.IMPORTANT.rank) {
            send(sc, to, text);
        }
    }

    /** 指定作用域的常规反馈（默认不推，界面自己会变）。 */
    public static void info(Scope sc, CommandSender to, String text) {
        if (level(sc).rank >= Level.NORMAL.rank) {
            send(sc, to, text);
        }
    }

    private static void send(Scope sc, CommandSender to, String text) {
        if (to == null || text == null || text.isEmpty()) {
            return;
        }
        // 玩家下线后就别发了（异步/延后触发的消息可能晚到）
        if (to instanceof Player p && !p.isOnline()) {
            return;
        }
        AddonConfig cfg = AddonConfig.get();
        String prefix = sc == null || sc.prefix() == null ? cfg.messagePrefix : sc.prefix();
        to.sendMessage(ReactorManager.color((prefix == null ? "" : prefix) + text));
    }

    private static Level level(Scope sc) {
        AddonConfig cfg = AddonConfig.get();
        String raw = sc == null || sc.level() == null ? cfg.messageLevel : sc.level();
        return Level.parse(raw);
    }

    private static Level level() {
        return level(REACTOR);
    }

    /** 当前档位（诊断/配置展示用）。 */
    public static Level currentLevel() {
        return level();
    }

    /** 一句话说明"这个档位下玩家会收到什么"（命令用）。 */
    public static java.util.List<String> describePolicy() {
        return java.util.List.of(
                "档位: " + level().display(),
                "warning/error  ：永远输出（不受档位影响）—— 点击失败、结构不完整无法激活、没权限…",
                "重要事件       ：多方块结构构建成功 / 反应堆已激活",
                "常规操作反馈   ：切换模式、开关粒子、双击被拦下…（默认不推）",
                "诊断细节       ：归属 uid、绑定了哪个核心…（默认不推）",
                "改档位：config.yml 的 messages.level，然后 /touhou reload",
                "命令回显不受影响 —— 那是玩家主动敲的");
    }

    /** 去掉颜色代码的纯文本（控制台日志用，避免日志里出现 § 乱码）。 */
    public static String plain(String text) {
        return text == null ? "" : ChatColor.stripColor(ReactorManager.color(text));
    }
}
