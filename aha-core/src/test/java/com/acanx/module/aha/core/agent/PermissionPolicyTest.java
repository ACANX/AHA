package com.acanx.module.aha.core.agent;

import com.acanx.module.aha.common.tool.ToolPermission;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PermissionPolicy} 测试。
 *
 * @since 0.1.0
 */
class PermissionPolicyTest {

    @Test
    void allowAllPermitsEverything() {
        PermissionPolicy policy = PermissionPolicy.allowAll();

        assertThat(policy.isAutoApproved("t", ToolPermission.READ)).isTrue();
        assertThat(policy.isAutoApproved("t", ToolPermission.WRITE)).isTrue();
        assertThat(policy.isAutoApproved("t", ToolPermission.EXECUTE)).isTrue();
        assertThat(policy.isAutoApproved("t", ToolPermission.ADMIN)).isTrue();
    }

    @Test
    void denyAllRejectsEverything() {
        PermissionPolicy policy = PermissionPolicy.denyAll();

        assertThat(policy.isAutoApproved("t", ToolPermission.READ)).isFalse();
        assertThat(policy.isAutoApproved("t", ToolPermission.NETWORK)).isFalse();
    }

    @Test
    void defaultPolicyAllowsReadAndNetworkOnly() {
        PermissionPolicy policy = PermissionPolicy.defaultPolicy();

        assertThat(policy.isAutoApproved("t", ToolPermission.READ)).isTrue();
        assertThat(policy.isAutoApproved("t", ToolPermission.NETWORK)).isTrue();
        assertThat(policy.isAutoApproved("t", ToolPermission.WRITE)).isFalse();
        assertThat(policy.isAutoApproved("t", ToolPermission.EXECUTE)).isFalse();
        assertThat(policy.isAutoApproved("t", ToolPermission.ADMIN)).isFalse();
    }

    @Test
    void autoApproveMatchesOnlyConfiguredPermissions() {
        PermissionPolicy policy = PermissionPolicy.autoApprove(EnumSet.of(ToolPermission.WRITE));

        assertThat(policy.isAutoApproved("t", ToolPermission.WRITE)).isTrue();
        assertThat(policy.isAutoApproved("t", ToolPermission.READ)).isFalse();
        assertThat(policy.isAutoApproved("t", ToolPermission.EXECUTE)).isFalse();
    }

    @Test
    void autoApproveHandlesEmptyAndNull() {
        assertThat(PermissionPolicy.autoApprove(EnumSet.noneOf(ToolPermission.class))
                .isAutoApproved("t", ToolPermission.READ)).isFalse();
        assertThat(PermissionPolicy.autoApprove(null)
                .isAutoApproved("t", ToolPermission.READ)).isFalse();
    }

    @Test
    void defaultPlusExtendsDefaultSet() {
        PermissionPolicy policy = PermissionPolicy.defaultPlus(EnumSet.of(ToolPermission.EXECUTE));

        assertThat(policy.isAutoApproved("t", ToolPermission.READ)).isTrue();
        assertThat(policy.isAutoApproved("t", ToolPermission.NETWORK)).isTrue();
        assertThat(policy.isAutoApproved("t", ToolPermission.EXECUTE)).isTrue();
        assertThat(policy.isAutoApproved("t", ToolPermission.WRITE)).isFalse();
    }
}
