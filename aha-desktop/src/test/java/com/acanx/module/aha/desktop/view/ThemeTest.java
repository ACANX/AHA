package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Theme} 测试：循环切换、系统偏好解析与文本解析。
 *
 * @since 0.2.0
 */
class ThemeTest {

    @Test
    void nextCyclesThroughAllThree() {
        assertThat(Theme.DARK.next()).isEqualTo(Theme.LIGHT);
        assertThat(Theme.LIGHT.next()).isEqualTo(Theme.SYSTEM);
        assertThat(Theme.SYSTEM.next()).isEqualTo(Theme.DARK);
    }

    @Test
    void systemResolvesToDarkOrLight() {
        assertThat(Theme.SYSTEM.resolve(true)).isEqualTo(Theme.DARK);
        assertThat(Theme.SYSTEM.resolve(false)).isEqualTo(Theme.LIGHT);
        // 显式选择不受系统偏好影响
        assertThat(Theme.LIGHT.resolve(true)).isEqualTo(Theme.LIGHT);
        assertThat(Theme.DARK.resolve(false)).isEqualTo(Theme.DARK);
    }

    @Test
    void ofParsesCaseInsensitivelyAndFallsBackToSystem() {
        assertThat(Theme.of("dark")).isEqualTo(Theme.DARK);
        assertThat(Theme.of(" LIGHT ")).isEqualTo(Theme.LIGHT);
        assertThat(Theme.of("system")).isEqualTo(Theme.SYSTEM);
        assertThat(Theme.of("不认识")).isEqualTo(Theme.SYSTEM);
        assertThat(Theme.of(null)).isEqualTo(Theme.SYSTEM);
    }

    @Test
    void labelsAreChinese() {
        assertThat(Theme.DARK.label()).isEqualTo("暗色");
        assertThat(Theme.LIGHT.label()).isEqualTo("亮色");
        assertThat(Theme.SYSTEM.label()).isEqualTo("跟随系统");
    }
}
