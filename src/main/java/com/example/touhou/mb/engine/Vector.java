package com.example.touhou.mb.engine;

import java.util.Objects;

/**
 * 不可变整数偏移向量（等价于 LogiTech 用作 schema key 的 BlockVector）。
 *
 * <p>★ 关键约定：一切"取偏移"的方法都必须返回<b>副本</b>。
 * LogiTech 的 {@code Direction.rotate(Vector)} 是<b>原地修改</b>入参的，
 * 如果 schema 把自己的内部 Vector 直接交出去，旋转一次就把 schema 本身改坏了。
 * 本类干脆做成不可变（{@link #rotateY90()} 返回新对象），从类型层面消灭这个坑。
 */
public final class Vector {

    private final int x;
    private final int y;
    private final int z;

    public Vector(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    /** 相对核心的平移，返回新对象。 */
    public Vector add(int dx, int dy, int dz) {
        return new Vector(x + dx, y + dy, z + dz);
    }

    public Vector subtract(Vector other) {
        return new Vector(x - other.x, y - other.y, z - other.z);
    }

    /**
     * 绕 Y 轴旋转 90°：(x, y, z) → (z, y, -x)。
     *
     * <p>这是一个确定的右手/左手都无所谓的 90° 旋转；四次调用必然回到原点。
     * 由于四个朝向构成一个由它生成的 4 阶循环群，"对这一次旋转不变" ⟺ "对四个朝向都不变"，
     * 所以用它判对称性是充分的（详见 {@code SymmetryAnalyzer}）。
     */
    public Vector rotateY90() {
        return new Vector(z, y, -x);
    }

    /** 到底是不是 (0,0,0) —— schema 里核心位永远不写。 */
    public boolean isOrigin() {
        return x == 0 && y == 0 && z == 0;
    }

    /** 副本（本类不可变，返回自身即可，保留此方法是为了和 LogiTech 的 clone() 调用点对齐）。 */
    public Vector clone() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Vector)) {
            return false;
        }
        Vector v = (Vector) o;
        return x == v.x && y == v.y && z == v.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y, z);
    }

    @Override
    public String toString() {
        return "(" + x + "," + y + "," + z + ")";
    }
}
