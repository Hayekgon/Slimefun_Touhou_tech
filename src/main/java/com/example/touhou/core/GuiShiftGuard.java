package com.example.touhou.core;

/**
 * 「Shift 快速移动只许进哪些槽」的契约 —— 给 {@link PortGuiListener} 用。
 *
 * <h2>为什么必须由机器自己声明</h2>
 * 逐槽点击拦截（{@link GuiLock} 注册的 handler）只能挡住"点在界面里"的点击。
 * 而 <b>Shift 点击</b>有两种形态：
 * <ol>
 *   <li>点在界面里的某一格（{@code rawSlot < size}）—— 逐槽 handler 挡得住；</li>
 *   <li>点在<b>玩家自己的背包</b>里（{@code rawSlot >= size}）—— 事件里的槽位指向玩家背包，
 *       界面这边<b>根本没有 handler 会被调用</b>；原版会把这堆物品搬进界面里
 *       "第一个放得下的空槽"。</li>
 * </ol>
 * 第 ② 种就是这个接口存在的理由：赛钱箱 GUI 里唯一的空槽是 6 个<b>只读镜像</b>的预留槽，
 * 不接管的话玩家一次 Shift 点击就能把物品塞进本该"谁都碰不到"的格子里
 * （实测路径：原版 {@code quickMoveStack} 会挑第一个空槽，而预留槽正好是空的）。
 *
 * <h2>语义</h2>
 * 实现方返回"允许流入的槽位"，监听器会把这次 Shift 移动<b>自己接管</b>：
 * 事件先取消，再只往这些槽里推；推不进去的物品留在玩家背包里（不报错、不吞物品）。
 * 返回空数组 = 这个界面禁止一切 Shift 快速移动。
 *
 * <p>★ "取消 + 自己搬"为什么安全：Paper 1.20.4 的真正点击动作在事件之后才执行
 * （反编译 {@code PlayerConnection#handleContainerClick}：DENY 分支只把服务端当前值
 * 同步回客户端，不回滚服务端状态），所以事件里改容器既不会被撤销、也不会复制物品。
 * 详见 {@link PortGuiListener#handleShiftFromPlayerInventory} 的注释。
 *
 * <p>★ 只有显式实现本接口的机器才会改变行为 —— 反应堆核心与两个物流接口
 * <b>不</b>实现它，所以它们的 Shift 行为一个字都没变。
 */
public interface GuiShiftGuard {

    /**
     * 允许 Shift 快速移动流入的槽位（下标）。
     *
     * <p>返回的数组会被监听器当作只读使用；实现方应返回<b>拷贝</b>，
     * 免得外部改到这个数组（本工程所有槽位表都是这个规矩）。
     */
    int[] shiftInsertSlots();
}
