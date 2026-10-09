package com.acanx.module.aha.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AppVersion} 测试。
 *
 * <p>重点钉住：构建期资源过滤确实生效（既不是占位符、也不是 dev 兜底值），
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
        // 资源未过滤会留下 ${...}；资源缺失会退回 dev 兜底值（见 TD-00016 §C）
        assertThat(AppVersion.DISPLAY).doesNotContain("${");
        assertThat(AppVersion.DISPLAY).doesNotContain("dev");
    }

    @Test
    void displayUsesBuildVersionWhenAvailable() {
        assertThat(AppVersion.version()).isNotBlank();
        assertThat(AppVersion.DISPLAY).isEqualTo("AHA " + AppVersion.buildVersion());
        // 未注入构建号时（本地构建 / 正式发版），构建版本等于基线版本
        if (!AppVersion.isPreRelease()) {
            assertThat(AppVersion.buildVersion()).isEqualTo(AppVersion.version());
        }
    }

    @Test
    void buildVersionIsUsableInBothFormalAndPreReleaseBuilds() {
        // 资源未过滤会留下 ${...}；资源缺失会退回 dev 兜底值（见 TD-00016 §C）
        assertThat(AppVersion.buildVersion()).doesNotContain("${").doesNotContain("dev");
        // 正式构建：build == version；预发行版：build 以 version 为前缀（如 0.1.1.00046）
        assertThat(AppVersion.buildVersion()).startsWith(AppVersion.version());
    }
}
