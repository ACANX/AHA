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
        DesktopToolApprover approver = new DesktopToolApprover((name, permission, args, allowed) -> {
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
        DesktopToolApprover approver = new DesktopToolApprover((name, permission, args, allowed) -> {
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
        DesktopToolApprover approver = new DesktopToolApprover((name, permission, args, allowed) ->
                permission == ToolPermission.WRITE
                        ? DesktopToolApprover.Decision.ALLOW_SESSION
                        : DesktopToolApprover.Decision.DENY);

        assertThat(approver.approve("file-write", ToolPermission.WRITE, ARGS)).isTrue();
        assertThat(approver.approve("shell-exec", ToolPermission.EXECUTE, ARGS)).isFalse();
    }

    @Test
    void promptSeesThePermissionsAlreadyAllowed() {
        // 弹窗要如实说明「本会话已经放宽到哪」，因此询问时必须拿到那份集合
        AtomicInteger seen = new AtomicInteger();
        DesktopToolApprover approver = new DesktopToolApprover((name, permission, args, allowed) -> {
            seen.set(allowed.size());
            return DesktopToolApprover.Decision.ALLOW_SESSION;
        });

        approver.approve("file-write", ToolPermission.WRITE, ARGS);
        assertThat(seen.get()).as("第一次询问时还没有任何已放行权限").isZero();

        approver.approve("shell-exec", ToolPermission.EXECUTE, ARGS);
        assertThat(seen.get()).as("第二次询问时应当看到 WRITE 已放行").isEqualTo(1);
    }

    @Test
    void revokingSessionApprovalsTurnsTheScrewsBackOn() {
        DesktopToolApprover approver = new DesktopToolApprover(
                (name, permission, args, allowed) -> DesktopToolApprover.Decision.ALLOW_SESSION);
        approver.approve("file-write", ToolPermission.WRITE, ARGS);
        assertThat(approver.sessionAllowed()).containsExactly(ToolPermission.WRITE);

        int revoked = approver.clearSessionAllowed();

        assertThat(revoked).isEqualTo(1);
        assertThat(approver.sessionAllowed()).isEmpty();
    }

    @Test
    void denyIsDefaultWhenNoPrompt() {
        DesktopToolApprover approver = new DesktopToolApprover(null);
        assertThat(approver.approve("file-write", ToolPermission.WRITE, ARGS)).isFalse();
    }
}
