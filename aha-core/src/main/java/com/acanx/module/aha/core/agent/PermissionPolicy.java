package com.acanx.module.aha.core.agent;

import com.acanx.module.aha.common.tool.ToolPermission;

import java.util.EnumSet;
import java.util.Set;

/**
 * 工具权限策略：决定某项工具权限是否被自动批准。
 *
 * <p>设计约定：{@link ToolPermission#READ} 与 {@link ToolPermission#NETWORK} 默认自动放行；
 * {@link ToolPermission#WRITE}、{@link ToolPermission#EXECUTE}、{@link ToolPermission#ADMIN}
 * 默认需要显式授权（交互确认或配置 {@code Tools.AutoApprove}）。</p>
 *
 * <p>未被自动批准的调用会抛出
 * {@link com.acanx.module.aha.common.exception.ToolExecutionException}，
 * 错误码为 {@code PERMISSION_DENIED}。</p>
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface PermissionPolicy {

    /**
     * 是否允许自动执行。
     *
     * @param toolName   工具名
     * @param permission 所需权限
     * @return 是否允许
     */
    boolean isAutoApproved(String toolName, ToolPermission permission);

    /**
     * 全部允许（谨慎使用）。
     *
     * @return 策略
     */
    static PermissionPolicy allowAll() {
        return (toolName, permission) -> true;
    }

    /**
     * 全部拒绝。
     *
     * @return 策略
     */
    static PermissionPolicy denyAll() {
        return (toolName, permission) -> false;
    }

    /**
     * 仅自动批准指定权限。
     *
     * @param permissions 权限集合
     * @return 策略
     */
    static PermissionPolicy autoApprove(Set<ToolPermission> permissions) {
        Set<ToolPermission> copy = permissions == null || permissions.isEmpty()
                ? EnumSet.noneOf(ToolPermission.class)
                : EnumSet.copyOf(permissions);
        return (toolName, permission) -> copy.contains(permission);
    }

    /**
     * 默认策略：自动批准 {@link ToolPermission#READ} 与 {@link ToolPermission#NETWORK}。
     *
     * @return 策略
     */
    static PermissionPolicy defaultPolicy() {
        return autoApprove(EnumSet.of(ToolPermission.READ, ToolPermission.NETWORK));
    }

    /**
     * 在默认策略基础上追加自动批准的权限。
     *
     * @param extra 额外权限
     * @return 策略
     */
    static PermissionPolicy defaultPlus(Set<ToolPermission> extra) {
        Set<ToolPermission> all = EnumSet.of(ToolPermission.READ, ToolPermission.NETWORK);
        if (extra != null) {
            all.addAll(extra);
        }
        return autoApprove(all);
    }
}
