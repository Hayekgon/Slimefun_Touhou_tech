package com.example.touhou.mb.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 平面结构类型 —— 行为对齐 LogiTech 的 {@code MultiBlockType}。
 *
 * <p>内部就是两张 {@code LinkedHashMap<Vector, String>}：{@link #addBlock}（归本机所有的零件）
 * 与 {@link #addRequirement}（只要求存在、不归本机所有）。{@code build()} 时调用子类
 * {@link #init()} 并把两张 map 摊平成并列数组，然后清空。
 *
 * <p>和 LogiTech 一致的两条硬约定：
 * <ol>
 *   <li>{@code (0,0,0)} 是核心位，{@code addBlock/addRequirement} 对它<b>静默忽略</b>；</li>
 *   <li>{@code build()} 之后不能再 add（map 已置 null，会 NPE）。</li>
 * </ol>
 */
public class MultiBlockType implements AbstractMultiBlockType {

    /** 零件：偏移 → part id。归本机所有，会被写 uuid/status，参与完整性校验。 */
    protected Map<Vector, String> STRUCTURE_MAP = new LinkedHashMap<>();

    /** 需求：偏移 → part id。常用 "nu" 表示必须是空气。 */
    protected Map<Vector, String> REQUIREMENT_MAP = new LinkedHashMap<>();

    protected boolean isSymmetric = false;

    private Vector[] structureLoc;
    private String[] structureIds;
    private Vector[] requirementLoc;
    private String[] requirementIds;

    /** 子类在这里声明形状。只会被 {@link #build()} 调用一次，且**不得读世界/读配置**。 */
    public void init() {
        // 默认空结构
    }

    public MultiBlockType addBlock(int x, int y, int z, String id) {
        if (x == 0 && y == 0 && z == 0) {
            return this;                      // ★ 核心位，静默忽略
        }
        this.STRUCTURE_MAP.put(new Vector(x, y, z), id);
        return this;
    }

    public MultiBlockType addRequirement(int x, int y, int z, String id) {
        if (x == 0 && y == 0 && z == 0) {
            return this;                      // ★ 同上
        }
        this.REQUIREMENT_MAP.put(new Vector(x, y, z), id);
        return this;
    }

    /** 冻结：调 init() → 摊平 → 清空。幂等的前提是只调一次。 */
    public MultiBlockType build() {
        this.init();

        List<Vector> sl = new ArrayList<>(STRUCTURE_MAP.keySet());
        List<String> si = new ArrayList<>(STRUCTURE_MAP.values());
        List<Vector> rl = new ArrayList<>(REQUIREMENT_MAP.keySet());
        List<String> ri = new ArrayList<>(REQUIREMENT_MAP.values());

        structureLoc = sl.toArray(new Vector[0]);
        structureIds = si.toArray(new String[0]);
        requirementLoc = rl.toArray(new Vector[0]);
        requirementIds = ri.toArray(new String[0]);

        STRUCTURE_MAP = null;
        REQUIREMENT_MAP = null;
        return this;
    }

    /** 直接灌入数据（读 JSON 数据文件时用），跳过 init()。 */
    public MultiBlockType loadArrays(List<Vector> parts, List<String> partIds,
                                     List<Vector> reqs, List<String> reqIds,
                                     boolean symmetric) {
        structureLoc = parts.toArray(new Vector[0]);
        structureIds = partIds.toArray(new String[0]);
        requirementLoc = reqs.toArray(new Vector[0]);
        requirementIds = reqIds.toArray(new String[0]);
        this.isSymmetric = symmetric;
        STRUCTURE_MAP = null;
        REQUIREMENT_MAP = null;
        return this;
    }

    @Override
    public int getSchemaSize() {
        return structureLoc.length;
    }

    @Override
    public Vector getSchemaPart(int index) {
        return structureLoc[index].clone();     // 不可变对象，clone() 恒等；保留调用点与 LogiTech 对齐
    }

    @Override
    public String getSchemaPartId(int index) {
        return structureIds[index];
    }

    @Override
    public int getRequirementSize() {
        return requirementLoc.length;
    }

    @Override
    public Vector getRequirementPart(int index) {
        return requirementLoc[index].clone();
    }

    @Override
    public String getRequirementPartId(int index) {
        return requirementIds[index];
    }

    @Override
    public boolean isSymmetric() {
        return isSymmetric;
    }
}
