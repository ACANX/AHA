package com.acanx.module.aha.cli;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AppVersion} 测试。
 *
 * <p>版本号此前硬编码在两处，改 {@code pom.xml} 不生效，发行包会报旧版本。
 * 这里守住「确实由构建注入」这件事——否则它会再次静默退化成占位符。</p>
 *
 * @since 0.1.0
 */
class AppVersionTest {

    @Test
    void versionIsInjectedByBuild() {
        assertThat(AppVersion.DISPLAY).startsWith("AHA ");
        // 资源未过滤时会留下 ${project.version} 占位符，或退回 -dev 兜底
        assertThat(AppVersion.DISPLAY).doesNotContain("${");
        assertThat(AppVersion.DISPLAY).doesNotContain("-dev");
    }

    @Test
    void versionProviderMatchesDisplay() {
        assertThat(new AppVersion.VersionProvider().getVersion())
                .containsExactly(AppVersion.DISPLAY);
    }
}
