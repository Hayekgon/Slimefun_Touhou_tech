package com.example.touhou.core;

/**
 * 反应堆运行状态。
 *
 * <p>三态语义（严格按 spec）：
 * <ul>
 *   <li>{@link #WORKING} 运行中：正在消耗燃料的进程中；</li>
 *   <li>{@link #IDLE} 空闲中：机器暂无进程且多方块结构完整；</li>
 *   <li>{@link #INACTIVE} 未激活：多方块结构不完整。</li>
 * </ul>
 *
 * <p>★ 最关键的一条规则：<b>结构从"不完整"变为"完整"时，不会自动进入空闲，
 * 需要玩家手动右键点击多方块核心才能从未激活转为空闲中。</b>
 * 这就是为什么要把"结构是否完整"和"是否已激活"分成两个概念 ——
 * 状态本身用 {@link #INACTIVE} 表达，而"已激活"活在 {@link ReactorManager} 的内存标记里。
 */
public enum ReactorState {

    /** 未激活：结构不完整，或者结构刚修好但玩家还没右键激活。 */
    INACTIVE("&c未激活", "&7多方块结构不完整"),

    /** 空闲中：结构完整且已激活，但没有燃料在烧。 */
    IDLE("&a空闲中", "&7结构完整，等待燃料"),

    /** 运行中：正在消耗燃料。 */
    WORKING("&e运行中", "&7正在消耗燃料");

    private final String display;
    private final String note;

    ReactorState(String display, String note) {
        this.display = display;
        this.note = note;
    }

    /** GUI 里显示的名字（带颜色代码）。 */
    public String display() {
        return display;
    }

    public String note() {
        return note;
    }
}
