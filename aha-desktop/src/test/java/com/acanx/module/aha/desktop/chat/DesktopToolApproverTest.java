package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.common.tool.ToolPermission;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DesktopToolApprover} 测试：与 CLI 一致的三种选择语义。
 *
 * @since 0.2.0
 */
class DesktopToolApproverTest {

    private static final Map<String, Object> ARGS = Map.of("path", "a.txt");

    @Test
    void allowOnceOnlyAllowsThatCall() {
        AtomicInteger asks = new AtomicInteger();
        DesktopToolApprover approver = new DesktopToolApprover((name, permission, args) -> {
            asks.incrementAndGet();
            return DesktopToolApprover.Decision.ALLOW_ONCE;
        });

        assertThat(approver.approve("file-write", ToolPermission.WRITE, ARGS)).isTrue();
        assertThat(approver.approve("file-write", ToolPermission.WRITE, ARGS)).isTrue();
        // 每次都问
        assertThat(asks.get()).isEqualTo(2);
        assertThat(approver.sessionAllowed()).isEmpty();
    }

    @Test
    void allowSessionRemembersPermission() {
        AtomicInteger asks = new AtomicInteger();
        DesktopToolApprover approver = new DesktopToolApprover((name, permission, args) -> {
            asks.incrementAndGet();
            return DesktopToolApprover.Decision.ALLOW_SESSION;
        });

        assertThat(approver.approve("file-write", ToolPermission.WRITE, ARGS)).isTrue();
        assertThat(approver.approve("file-write", ToolPermission.WRITE, ARGS)).isTrue();
        // 第二次不再询问
        assertThat(asks.get()).isEqualTo(1);
        assertThat(approver.sessionAllowed()).containsExactly(ToolPermission.WRITE);
    }

    @Test
    void sessionMemoryIsPerPermission() {
        DesktopToolApprover approver = new DesktopToolApprover((name, permission, args) ->
                permission == ToolPermission.WRITE
                        ? DesktopToolApprover.Decision.ALLOW_SESSION
                        : DesktopToolApprover.Decision.DENY);

        assertThat(approver.approve("file-write", ToolPermission.WRITE, ARGS)).isTrue();
        assertThat(approver.approve("shell-exec", ToolPermission.EXECUTE, ARGS)).isFalse();
    }

    @Test
    void denyIsDefaultWhenNoPrompt() {
        DesktopToolApprover approver = new DesktopToolApprover(null);
        assertThat(approver.approve("file-write", ToolPermission.WRITE, ARGS)).isFalse();
    }
}
