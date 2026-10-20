package com.acanx.module.aha.cli.tty;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link VtInputMode} 的开关判定测试（issue #75）。
 *
 * <p>这里只测纯逻辑：真实 {@code SetConsoleMode} 依赖 Windows 控制台，无法在单测里覆盖；
 * 失败路径由 {@code ensure()} 的兜底保证（返回 {@code false}，行为与改动前一致）。</p>
 *
 * @since 0.2.0
 */
class VtInputModeTest {

    @Test
    void nonWindowsIsAlwaysDisabled() {
        assertThat(VtInputMode.shouldEnable(false, Map.of())).isFalse();
        assertThat(VtInputMode.shouldEnable(false, Map.of(VtInputMode.ENV, "1"))).isFalse();
    }

    @Test
    void windowsDefaultsToEnabled() {
        assertThat(VtInputMode.shouldEnable(true, Map.of())).isTrue();
        assertThat(VtInputMode.shouldEnable(true, Map.of(VtInputMode.ENV, ""))).isTrue();
        assertThat(VtInputMode.shouldEnable(true, Map.of(VtInputMode.ENV, "1"))).isTrue();
        assertThat(VtInputMode.shouldEnable(true, Map.of(VtInputMode.ENV, "yes"))).isTrue();
    }

    @Test
    void windowsCanBeDisabled() {
        for (String off : new String[] {"0", "false", "FALSE", "off", "no", " no "}) {
            assertThat(VtInputMode.shouldEnable(true, Map.of(VtInputMode.ENV, off)))
                    .as("AHA_PASTE_VT=%s 应关闭", off)
                    .isFalse();
        }
    }

    @Test
    void nullEnvironmentFallsBackToEnabled() {
        assertThat(VtInputMode.shouldEnable(true, null)).isTrue();
    }
}
