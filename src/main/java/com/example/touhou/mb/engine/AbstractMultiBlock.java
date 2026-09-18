package com.example.touhou.mb.engine;

/**
 * 运行时实例 —— 对齐 LogiTech 的 {@code AbstractMultiBlock}。
 *
 * <p>它是"按朝向展开后的实际坐标序列"。当"schema 里几个零件" ≠ "世界里几个方块"
 * （立方拉伸就是典型）时，必须自己实现本接口，否则 handler 只会写前 N 个位置。
 * 平面结构直接用 {@link MultiBlock}。
 */
public interface AbstractMultiBlock {

    AbstractMultiBlockType getType();

    /** 朝向。独立最小引擎里只有 NORTH，朝向由业务自己决定。 */
    default String getDirection() {
        return "NORTH";
    }

    default int getStructureSize() {
        return getType().getSchemaSize();
    }

    default Vector getStructurePart(int index) {
        return getType().getSchemaPart(index);
    }

    default String getStructurePartId(int index) {
        return getType().getSchemaPartId(index);
    }
}
