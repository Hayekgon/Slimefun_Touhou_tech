package com.example.touhou.mb.engine;

import java.util.ArrayList;
import java.util.List;

/**
 * 结构校验 —— 复刻 LogiTech {@code MultiBlockType#genMultiBlockFrom} 的三段结构。
 *
 * <pre>
 * for i in 0..schemaSize-1:
 *     partloc = core + rotate(schemaPart(i))
 *     if schemaPartId(i) != safeGetPartId(partloc)                → "部件并不是有效的部件,需要X"
 *     if !hasPrevRecord &amp;&amp; validHandler(safeGetUUID(partloc))      → "结构冲突!…属于另个机器"
 * if !hasPrevRecord:
 *     for i in 0..requirementSize-1:
 *         if requirementIds(i) != safeGetPartId(core + rotate(requirementLoc(i)))  → "不满足构建所需要的额外条件"
 * return new MultiBlock(this, dir)
 * </pre>
 *
 * <p>★ 本实现刻意修掉了内置实现的一个已确认 bug：<b>需求方块也要旋转</b>。
 * LogiTech 的 {@code genMultiBlockFrom} 里结构零件走了 {@code dir.rotate(...)}，
 * 需求方块却直接用了原始偏移；对 {@code isSymmetric=false} 且需求不在旋转轴上的结构，
 * "校验看的位置"和"建造器画的位置"会不一致。
 * 注意<b>需求方块的旋转次数要和零件完全一致</b>，否则两者会错位。
 *
 * <p>★ 另一条必须守住的门控：{@code hasPrevRecord == true} 时跳过冲突检查与需求检查，
 * 否则重建/重连自己的结构会被误判。
 */
public final class MultiBlockValidator {

    private MultiBlockValidator() {
    }

    /** 旋转次数：0/1/2/3 表示绕 Y 轴 0°/90°/180°/270°。 */
    public static final int ROT_0 = 0;
    public static final int ROT_90 = 1;
    public static final int ROT_180 = 2;
    public static final int ROT_270 = 3;

    /** 把"旋转 n 次"折叠成一次调用，避免在循环里反复乘。 */
    private static Vector rotate(Vector v, int rotations) {
        Vector out = v;
        for (int i = 0; i < (rotations & 3); i++) {
            out = out.rotateY90();
        }
        return out;
    }

    /** 校验结果。 */
    public static final class Result {

        private final AbstractMultiBlock multiBlock;
        private final List<String> errors;

        Result(AbstractMultiBlock multiBlock, List<String> errors) {
            this.multiBlock = multiBlock;
            this.errors = errors;
        }

        public boolean isSuccess() {
            return multiBlock != null;
        }

        public AbstractMultiBlock getMultiBlock() {
            return multiBlock;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String firstError() {
            return errors.isEmpty() ? null : errors.get(0);
        }

        @Override
        public String toString() {
            return isSuccess() ? "OK" : "FAIL: " + firstError();
        }
    }

    /**
     * 便捷重载：<b>绝对核心坐标</b> + 无旋转(NORTH) + 默认错误上限。
     *
     * <p>对应 LogiTech {@code genMultiBlockFrom(Location core, Direction dir, ...)}
     * 里"方向 = NORTH"的那一路。
     */
    public static Result validate(int coreX, int coreY, int coreZ,
                                  AbstractMultiBlockType type,
                                  PartIdResolver resolver,
                                  boolean hasPrevRecord) {
        return validate(coreX, coreY, coreZ, type, resolver, hasPrevRecord, ROT_0, 12);
    }

    /**
     * @param coreX,coreY,coreZ <b>核心方块的绝对坐标</b>。所有 schema 偏移都会<b>加</b>在这上面
     *                     （等价于 LogiTech 的 {@code core.clone().add(dir.rotate(offset))}）。
     *                     ⚠ 千万不要把"已经是绝对坐标的核心"再当偏移加一次 —— 实测踩过：
     *                     那会让查询点整整跑偏一个核心距离（跑到 125 格外的空气里），
     *                     表现为"每一格都报 实际 nu"。
     * @param type           要校验的结构类型
     * @param resolver       世界查询（真实服务端 or 离线假实现）
     * @param hasPrevRecord  该核心是否已登记过结构（true ⇒ 跳过冲突与需求检查）
     * @param rotations      绕 Y 轴的 90° 次数（0..3）
     * @param maxErrors      最多收集多少条错误（避免大结构刷屏）
     */
    public static Result validate(int coreX, int coreY, int coreZ,
                                  AbstractMultiBlockType type,
                                  PartIdResolver resolver,
                                  boolean hasPrevRecord,
                                  int rotations,
                                  int maxErrors) {

        List<String> errors = new ArrayList<>();

        // ---- 第 1 段：结构零件（含冲突检查）----
        for (int i = 0; i < type.getSchemaSize(); i++) {
            Vector rot = rotate(type.getSchemaPart(i), rotations);
            // 绝对坐标：只用于报错信息（人要看的是世界坐标）
            int wx = coreX + rot.getX();
            int wy = coreY + rot.getY();
            int wz = coreZ + rot.getZ();

            String expected = type.getSchemaPartId(i);
            // ★ 交给 resolver 的是"相对核心的偏移"，不是绝对坐标。
            //   PartIdResolver 的契约就是"偏移 → 查到什么"，由它自己加核心
            //   （真实实现 RuntimePartIdResolver 内部会 core + offset）。
            //   这里若传绝对坐标，就会变成 core + (core + offset)，查询点整整跑偏一个核心距离，
            //   表现为"每一格都报 实际 nu" —— 实测踩过，别再犯。
            String actual = resolver.getPartId(rot.getX(), rot.getY(), rot.getZ());
            if (!expected.equals(actual)) {
                errors.add("位于 (%d,%d,%d) 的部件不是 %s（实际 %s）".formatted(wx, wy, wz, expected, actual));
                if (errors.size() >= maxErrors) {
                    return new Result(null, errors);
                }
            }
            if (!hasPrevRecord && resolver.hasHandler(rot.getX(), rot.getY(), rot.getZ())) {
                errors.add("结构冲突!位于 (%d,%d,%d) 的部件属于另个机器!".formatted(wx, wy, wz));
                if (errors.size() >= maxErrors) {
                    return new Result(null, errors);
                }
            }
        }

        // ---- 第 2 段：需求方块（只在全新搭建时校验）★ 这里旋转了，内置实现漏了 ----
        if (!hasPrevRecord) {
            for (int i = 0; i < type.getRequirementSize(); i++) {
                Vector rot = rotate(type.getRequirementPart(i), rotations);
                int wx = coreX + rot.getX();
                int wy = coreY + rot.getY();
                int wz = coreZ + rot.getZ();

                String expected = type.getRequirementPartId(i);
                String actual = resolver.getPartId(rot.getX(), rot.getY(), rot.getZ());
                if (!expected.equals(actual)) {
                    errors.add("位于 (%d,%d,%d) 的方块不满足额外条件，需要 %s（实际 %s）"
                            .formatted(wx, wy, wz, expected, actual));
                    if (errors.size() >= maxErrors) {
                        return new Result(null, errors);
                    }
                }
            }
        }

        if (!errors.isEmpty()) {
            return new Result(null, errors);
        }
        return new Result(new MultiBlock(type), errors);
    }
}
