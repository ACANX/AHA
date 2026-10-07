package com.acanx.module.aha.common.tool;

import java.util.Map;

/**
 * 工具授权裁决器。
 *
 * <p>当 {@link ToolPermission} 未被自动放行时，由实现决定是否允许本次调用：
 * 交互式 CLI 可提示用户确认，无人值守场景可一律拒绝。</p>
 *
 * <p>引入该契约的原因是：仅抛 {@code PERMISSION_DENIED} 会让用户面对
 * “未获自动授权”而无从下手——既没有确认入口，也看不到授权方式。</p>
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface ToolApprover {

    /**
     * 裁决一次需要授权的工具调用。
     *
     * <p>实现应阻塞等待用户输入（交互场景），并在无法取得授权时返回 {@code false}。</p>
     *
     * @param toolName   工具名
     * @param permission 所需权限
     * @param arguments  调用参数，供用户判断风险
     * @return 是否允许执行
     */
    boolean approve(String toolName, ToolPermission permission, Map<String, Object> arguments);

    /**
     * 一律拒绝（无人值守场景的默认值）。
     *
     * @return 始终返回 {@code false} 的裁决器
     */
    static ToolApprover denyAll() {
        return (toolName, permission, arguments) -> false;
    }
}
