package com.example.touhou.mb.engine;

/**
 * 平面结构的运行时实例：结构与 schema 一一对应，纯平移即可。
 *
 * <p>（LogiTech 版还带 {@code Direction.rotate}；本独立引擎里朝向由调用方在
 * {@link #getStructurePart(int)} 之上自行处理，保证零依赖。）
 */
public class MultiBlock implements AbstractMultiBlock {

    private final AbstractMultiBlockType type;

    public MultiBlock(AbstractMultiBlockType type) {
        this.type = type;
    }

    @Override
    public AbstractMultiBlockType getType() {
        return type;
    }
}
