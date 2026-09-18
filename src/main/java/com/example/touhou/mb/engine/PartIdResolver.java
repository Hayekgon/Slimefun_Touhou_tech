package com.example.touhou.mb.engine;

/**
 * 校验时的"世界查询"抽象。
 *
 * <p>引擎不直接依赖 Bukkit/Slimefun —— 它是纯逻辑，这样才能：
 * <ul>
 *   <li>把生成的 schema 类拖到 LogiTech 工程里照用（那边由 {@code MultiBlockService.safeGetPartId(Location)} 实现同一职责）；</li>
 *   <li>在离线环境（CLI / 单元自测）里用一个假实现把同一个 schema 校验一遍。</li>
 * </ul>
 *
 * <p>★ <b>坐标契约（很容易搞错，实测踩过）</b>：
 * 三个参数是<b>相对核心的偏移</b>，不是绝对坐标。
 * 实现方负责自己加上核心：{@code loc = core + (x, y, z)}。
 *
 * <p>对照 LogiTech：{@code MultiBlockService.safeGetPartId(Location)} 的语义 =
 * "这个坐标上的方块，其 part id 是什么"（查不到 Slimefun 数据就退回 Material 名 / 材质别名），
 * 只是那边直接收 {@code Location}（已经加好了核心），这里收偏移、由实现方加。
 *
 * <p>反面教材：如果 {@link MultiBlockValidator} 把 {@code core + offset} 传进来，
 * 而实现方又加一次核心，查询点就会整整跑偏一个核心距离（跑到几十上百格外的空气里），
 * 表现为"每一格都报 实际 nu"，而 schema 本身完全正确 —— 非常难查。
 */
public interface PartIdResolver {

    /**
     * 该<b>偏移</b>处的 part id：粘液方块 → 其 Slimefun id；原版方块 → {@code Material.toString()}；
     * 空气 → {@code "nu"}。
     *
     * @param x 相对核心的 X 偏移
     * @param y 相对核心的 Y 偏移
     * @param z 相对核心的 Z 偏移
     */
    String getPartId(int x, int y, int z);

    /** 该偏移处是否已被另一个多方块实例登记（结构冲突判定）。同样收<b>偏移</b>。 */
    default boolean hasHandler(int x, int y, int z) {
        return false;
    }
}
