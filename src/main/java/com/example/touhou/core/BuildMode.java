package com.example.touhou.core;

/**
 * 反应堆的"构建模式"（对应 GUI 里信息格旁边的按钮）。
 *
 * <p>两者的差别只在<b>结构完整之后要不要玩家动手</b>：
 * <ul>
 *   <li>{@link #MANUAL}：结构完整后停在"未激活"，必须玩家点一下信息格才激活
 *       （本附属最早的行为）；</li>
 *   <li>{@link #AUTO}：结构完整后<b>自动激活</b>，玩家搭完就能跑。</li>
 * </ul>
 *
 * <p>★ 检测机制照搬 LogiTech 多方块引擎（超新星那套 {@code MultiCore}）的思路：
 * <b>不是每 tick 全量校验结构</b>，而是用一个"有符号计数器"节流 ——
 * <ul>
 *   <li>未激活侧：正数向上累计，到 {@link ReactorManager#AUTO_BUILD_TICKS} 才做一次结构检测
 *       （顺便就是"自动构建"的尝试时机）；</li>
 *   <li>已激活侧：负数向下累计，到 {@link ReactorManager#RUNTIME_CHECK_TICKS} 才做一次
 *       （运行期复查，看结构有没有被拆）。</li>
 * </ul>
 * 一个 5×5×5 容器是 124 格的查询，每 tick 全量扫一遍是没必要的开销。
 *
 * <p>与 LogiTech 的唯一差别：那边的计数器存在方块数据里（重启后接着数），
 * 这里放在内存里 —— 它只影响"最多晚几个 tick 才发现"，不值得每 tick 写一次方块数据。
 */
public enum BuildMode {

    /** 手动构建：结构完整后需要玩家点击信息格激活。 */
    MANUAL("&e手动构建",
            "&7结构完整后停在&f未激活",
            "&7需要&f点击信息格&7手动激活"),

    /** 自动构建：结构完整后自动激活。 */
    AUTO("&a自动构建",
            "&7结构完整后&f自动激活",
            // ★ 这里不调用任何 ReactorManager 方法：枚举常量在类初始化期求值，
            //   那一刻去访问懒加载的配置会触发 AddonConfig.get() → Touhou.getInstance()，
            //   而配置/插件可能还没就绪。
            "&8（检测由【放置 / 破坏】事件触发，不再按 tick 轮询）");

    private final String display;
    private final String[] lore;

    BuildMode(String display, String... lore) {
        this.display = display;
        this.lore = lore;
    }

    public String display() {
        return display;
    }

    public String[] lore() {
        return lore;
    }

    public BuildMode next() {
        return this == MANUAL ? AUTO : MANUAL;
    }

    /** 是否"结构完整即自动激活"（{@link ReactorManager#updateState} 里读它做分支）。 */
    public boolean isAuto() {
        return this == AUTO;
    }
}
