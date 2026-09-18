package com.example.touhou.core;

/**
 * 反应堆运行模式（对应 GUI 的 K 槽位，点击切换）。
 *
 * <p>两种模式的差别有两条：<b>要不要接电</b>、<b>储电高于阈值时要不要暂停</b>。
 *
 * <table>
 *   <tr><th>模式</th><th>接入电力网络</th><th>储电 &ge; 阈值</th><th>储电满（= 上限）</th></tr>
 *   <tr><td>{@link #GENERATE}</td><td><b>必须</b></td><td><b>暂停</b>进程（进度保留）</td><td>同样暂停</td></tr>
 *   <tr><td>{@link #PRODUCT}</td><td>不需要</td><td>继续满功率发电</td><td>继续发电，多余部分由本体电力网络丢弃</td></tr>
 * </table>
 *
 * <p>这正是本体核反应堆 {@code Reactor} 的行为：
 * <ul>
 *   <li>{@code ReactorMode.GENERATOR}（发电模式）：{@code Reactor#generateEnergy} 里
 *       {@code if (capacity - charge < production && mode == GENERATOR) return 0;}
 *       —— <b>不推进度就是暂停，进度本身留在 operation 里</b>；</li>
 *   <li>{@code ReactorMode.PRODUCTION}（产物模式）：同样的条件直接跳过，
 *       一律 {@code operation.addProgress(1)} —— <b>进程跟"电有没有去处"无关</b>。</li>
 * </ul>
 *
 * <p>★ 产物模式还必须做到第二件事：<b>不接电网也能跑</b>。
 * 本体 {@code EnergyNet} 只会 tick 挂进网络的发电机（7 格内要有能源调节器/电容），
 * 所以产物模式的进程<b>不能</b>只靠 {@code getGeneratedOutput} 推进 ——
 * 那条路离网时一次都不会被调用。本附属的做法是让机器的 {@code BlockTicker}
 * 自己推进产物模式的进程（见 {@code ReactorManager#advanceProductProcess}），
 * 此时 {@code getGeneratedOutput} 只负责出电、不再推进度（否则接网时会双倍）。
 *
 * <p>关于"溢出销毁"：本体的 {@code EnergyNet.storeRemainingEnergy} 会把
 * 所有组件灌满后剩下的能量直接丢掉，所以我们**什么都不用写**，
 * 只要允许 getGeneratedOutput 在满电时照常返回能量即可。
 * 离网运行时电干脆没人接收，这部分同样算"允许浪费"。
 */
public enum ReactorMode {

    /** 发电模式：必须接电网；到阈值就暂停，保住电量不溢出。 */
    GENERATE("&b发电模式",
            "&7储电高于阈值时&f暂停进程&7（保留进度）",
            "&7需要接入电力网络（能源调节器/电容）",
            "&8—— 岩浆发电机 / 核反应堆 GENERATOR 同款"),

    /** 产物模式：离网自跑，无视电量阈值，溢出的电丢弃。 */
    PRODUCT("&d产物模式",
            "&7解除暂停，持续满功率发电",
            "&a无需接入电力网络&7，进程照常推进",
            "&8—— 电量溢出的部分直接销毁");

    private final String display;
    private final String[] lore;

    ReactorMode(String display, String... lore) {
        this.display = display;
        this.lore = lore;
    }

    public String display() {
        return display;
    }

    public String[] lore() {
        return lore;
    }

    public ReactorMode next() {
        return this == GENERATE ? PRODUCT : GENERATE;
    }
}
