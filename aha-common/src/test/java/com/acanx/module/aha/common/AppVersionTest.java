package com.acanx.module.aha.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AppVersion} 测试。
 *
 * <p>重点钉住：构建期资源过滤确实生效（既不是占位符、也不是 -dev 兜底值），
 * 且版本号只有一个来源。</p>
 *
 * @since 0.1.0
 */
class AppVersionTest {

    @Test
    void displayHasProductNamePrefix() {
        assertThat(AppVersion.DISPLAY).startsWith("AHA ");
    }

    @Test
    void versionIsFilteredByMaven() {
        // 资源未过滤会留下 ${...}；资源缺失会退回 -dev 兜底值
        assertThat(AppVersion.DISPLAY).doesNotContain("${");
        assertThat(AppVersion.DISPLAY).doesNotContain("-dev");
    }

    @Test
    void versionIsBareNumberWithoutPrefix() {
        assertThat(AppVersion.version()).isNotBlank();
        assertThat(AppVersion.DISPLAY).isEqualTo("AHA " + AppVersion.version());
    }
}
