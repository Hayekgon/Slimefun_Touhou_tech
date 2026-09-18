package com.example.touhou.mb.engine;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 多方块「类型」契约 —— 与 LogiTech 自研引擎的同名接口**逐方法对齐**。
 *
 * <p>为什么要在最小附属里再实现一遍？
 * <ul>
 *   <li>区划扫描工具产出的 Java schema 必须能<b>立刻编译</b>，不能靠"到那边再试"；</li>
 *   <li>编译出来的类可以当场在服务端跑一遍校验（自测），验证 schema 与真实世界一致；</li>
 *   <li>这段代码只依赖 {@code java.util}，把生成的源码粘到 LogiTech 工程里同样成立。</li>
 * </ul>
 *
 * <p>对照 LogiTech {@code me.matl114.logitech.utils.UtilClass.MultiBlockClass.AbstractMultiBlockType}：
 * 方法名、参数顺序、返回类型完全一致；此处省略了 {@code genMultiBlockFrom} 与参数化钩子
 * （它们依赖 LogiTech 的 {@code AbstractMultiBlock} / {@code MultiBlockService.Direction}），
 * 改由独立的 {@link MultiBlockValidator} 承担校验。
 */
public interface AbstractMultiBlockType {

    /** 零件总数（不含 requirement）。 */
    int getSchemaSize();

    /** 第 index 个零件的相对偏移（相对核心，(0,0,0) 即核心位）。必须返回副本。 */
    Vector getSchemaPart(int index);

    /** 第 index 个零件要求的 part id。 */
    String getSchemaPartId(int index);

    /** 需求方块数量（"这里必须是什么"，但不归本机所有）。 */
    default int getRequirementSize() {
        return 0;
    }

    /** 第 index 个需求方块的相对偏移。必须返回副本。 */
    default Vector getRequirementPart(int index) {
        return null;
    }

    /** 第 index 个需求方块的 part id（{@code "nu"} = 必须是空气）。 */
    default String getRequirementPartId(int index) {
        return null;
    }

    /** 形状绕 Y 轴旋转 90° 后是否完全不变。说谎会导致其它朝向永远校验失败。 */
    default boolean isSymmetric() {
        return false;
    }

    /** 类型名（给日志/报告用）。 */
    default String getTypeName() {
        return getClass().getSimpleName();
    }

    /** 参数化类型用：需要哪些入参名。内置只有 CubeMultiBlockType 的 ["height"]。 */
    default List<String> getRequiredArguments() {
        return Collections.emptyList();
    }

    /** 给定参数下"这个结构长什么样"。默认实现直接摊平 schema。 */
    default Map<Vector, String> getMultiBlockSchemaFromArguments(Map<String, String> args) {
        Map<Vector, String> out = new java.util.LinkedHashMap<>();
        for (int i = 0; i < getSchemaSize(); i++) {
            out.put(getSchemaPart(i), getSchemaPartId(i));
        }
        for (int i = 0; i < getRequirementSize(); i++) {
            out.put(getRequirementPart(i), getRequirementPartId(i));
        }
        return out;
    }
}
