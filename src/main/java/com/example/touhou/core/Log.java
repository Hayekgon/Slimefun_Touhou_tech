package com.example.touhou.core;

import com.example.touhou.Touhou;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * 日志门面 —— 统一收口本插件往 Paper 控制台的所有输出。
 *
 * <h2>为什么要这个类</h2>
 * 之前 25 处 {@code Touhou.getInstance().getLogger().info(...)} 散在各处，直接调用
 * {@code java.util.logging}。于是「把控制台的 info 关掉」这件事必须在 25 个地方各改一遍，
 * 而且新写的代码很容易漏掉。收口到这里之后，开关只有一处。
 *
 * <h2>总开关</h2>
 * {@code config.yml} 的 {@code logging.console-info}：
 * <ul>
 *   <li>{@code true}（默认）—— 正常输出 info；</li>
 *   <li>{@code false} —— <b>info 全部静默</b>，只保留 {@link #warn} 与 {@link #severe}。</li>
 * </ul>
 *
 * <h2>分级口径（与 {@link Notify} 的玩家消息档位是两套，别混淆）</h2>
 * <ul>
 *   <li>{@link #info} —— <b>正常运转的叙述</b>：注册结果、结构载入、机器启停。
 *       关掉不影响任何功能，纯粹是控制台可读性问题。</li>
 *   <li>{@link #warn} —— <b>配置有问题 / 降级运行</b>：数值越界被钳制、可选依赖缺失。
 *       这类<b>永远输出</b>：出问题时控制台必须留下线索。</li>
 *   <li>{@link #severe} —— <b>代码层面的异常</b>（带堆栈）。永远输出。</li>
 * </ul>
 * 也就是说：开关只关「叙述」，不关「线索」。
 */
public final class Log {

    private Log() {
    }

    /**
     * 控制台 info 是否开启。
     *
     * <p>{@link AddonConfig} 还没加载完时（极早期）默认放行，避免丢掉启动期的关键行。
     */
    public static boolean consoleInfo() {
        AddonConfig c = AddonConfig.get();
        return c == null || c.consoleInfo;
    }

    /** 正常运转的叙述。受总开关控制。 */
    public static void info(String msg) {
        if (consoleInfo()) {
            Touhou.getInstance().getLogger().info(msg);
        }
    }

    /**
     * 命令回显 —— <b>不受 info 总开关影响</b>。
     *
     * <p>理由有两条：
     * <ol>
     *   <li>命令是玩家/管理员<b>主动敲的</b>，一次输入对应一次输出，不可能刷屏；</li>
     *   <li>它恰恰是排查问题时最需要的线索（{@code /touhou ...} 的诊断输出）。
     *       关掉 info 是为了压掉"机器自己产生的流水账"，不是为了让敲了命令看不到结果。</li>
     * </ol>
     * 与 {@link Notify} 的分级口径一致：那边也写明"命令回显不受 messages.level 影响"。
     */
    public static void command(String msg) {
        Touhou.getInstance().getLogger().info(msg);
    }

    /**
     * 惰性版本：只有真的要输出时才拼字符串。
     *
     * <p>用于那些"拼接本身有成本"的行（例如遍历层图生成摘要）——
     * 关掉 info 时连拼接都省掉。
     */
    public static void info(Supplier<String> msg) {
        if (consoleInfo()) {
            Touhou.getInstance().getLogger().info(msg.get());
        }
    }

    /** 配置有问题 / 降级运行 —— <b>永远输出</b>。 */
    public static void warn(String msg) {
        Touhou.getInstance().getLogger().warning(msg);
    }

    /** 代码层面的异常 —— <b>永远输出</b>（带堆栈）。 */
    public static void severe(String msg, Throwable t) {
        Touhou.getInstance().getLogger().log(Level.SEVERE, msg, t);
    }

    /** 代码层面的错误（无堆栈）—— <b>永远输出</b>。 */
    public static void severe(String msg) {
        Touhou.getInstance().getLogger().severe(msg);
    }
}
