package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.common.tool.ToolApprover;
import com.acanx.module.aha.common.tool.ToolPermission;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 桌面端授权裁决器：语义与 CLI 完全一致（{@code GUIDesign.md} 第 3.3 节）。
 *
 * <ul>
 *   <li>本次允许 → 只放行这一次；</li>
 *   <li>本会话内始终允许该权限 → 记入集合，后续同类不再问；</li>
 *   <li>拒绝 / 无法询问 → 不放行。</li>
 * </ul>
 *
 * <p>决策逻辑不引用 JavaFX，询问动作由 {@link Prompt} 注入——于是「本会话始终允许」这类
 * 跨次行为可以在无图形环境下测试，界面只负责弹窗与取回用户选择。</p>
 *
 * @since 0.2.0
 */
public final class DesktopToolApprover implements ToolApprover {

    /**
     * 用户的三种选择。
     */
    public enum Decision {

        /** 只允许本次。 */
        ALLOW_ONCE,

        /** 本会话内始终允许该权限。 */
        ALLOW_SESSION,

        /** 拒绝。 */
        DENY
    }

    /**
     * 询问通道（界面实现；实现应阻塞等待用户选择）。
     *
     * @since 0.2.0
     */
    public interface Prompt {

        /**
         * 询问用户。
         *
         * @param toolName   工具名
         * @param permission 所需权限
         * @param arguments  调用参数
         * @return 用户选择；无法询问时返回 {@link Decision#DENY}
         */
        Decision ask(String toolName, ToolPermission permission, Map<String, Object> arguments);
    }

    /** 本会话内已放行的权限。 */
    private final Set<ToolPermission> sessionAllowed = ConcurrentHashMap.newKeySet();

    private final Prompt prompt;

    /**
     * @param prompt 询问通道
     */
    public DesktopToolApprover(Prompt prompt) {
        this.prompt = prompt == null ? (name, permission, args) -> Decision.DENY : prompt;
    }

    @Override
    public boolean approve(String toolName, ToolPermission permission, Map<String, Object> arguments) {
        if (sessionAllowed.contains(permission)) {
            return true;
        }
        Decision decision = prompt.ask(toolName, permission, arguments);
        if (decision == Decision.ALLOW_SESSION) {
            sessionAllowed.add(permission);
        }
        return decision != Decision.DENY;
    }

    /**
     * 本会话已放行的权限（测试与界面提示用）。
     *
     * @return 权限集合
     */
    public Set<ToolPermission> sessionAllowed() {
        return Set.copyOf(sessionAllowed);
    }
}
