package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.common.model.SessionConfig;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AutoCompactSetting} 测试。
 *
 * @since 0.1.0
 */
class AutoCompactSettingTest {

    @Test
    void parsesPlainAndSuffixedValues() {
        assertThat(AutoCompactSetting.parse("500")).isEqualTo(500);
        assertThat(AutoCompactSetting.parse("285k")).isEqualTo(285_000);
        assertThat(AutoCompactSetting.parse("285K")).isEqualTo(285_000);
        assertThat(AutoCompactSetting.parse("1M")).isEqualTo(1_000_000);
        assertThat(AutoCompactSetting.parse(" 285k ")).isEqualTo(285_000);
    }

    @Test
    void treatsOffAndZeroAsDisabled() {
        assertThat(AutoCompactSetting.parse("off")).isZero();
        assertThat(AutoCompactSetting.parse("NONE")).isZero();
        assertThat(AutoCompactSetting.parse("0")).isZero();
    }

    @Test
    void rejectsUnrecognizedValues() {
        assertThatThrownBy(() -> AutoCompactSetting.parse("abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无法识别");
        assertThatThrownBy(() -> AutoCompactSetting.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AutoCompactSetting.parse("1.5m"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AutoCompactSetting.parse("-5"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("负数");
    }

    @Test
    void readsThresholdTolerantly() {
        // 会话配置经 JSON 往返后可能是字符串也可能是数值节点，两种都要认
        assertThat(AutoCompactSetting.thresholdOf(null)).isZero();
        assertThat(AutoCompactSetting.thresholdOf(new SessionConfig("m", null, Map.of()))).isZero();
        assertThat(AutoCompactSetting.thresholdOf(
                new SessionConfig("m", null, Map.of(AutoCompactSetting.KEY, "285000"))))
                .isEqualTo(285_000);
        assertThat(AutoCompactSetting.thresholdOf(
                new SessionConfig("m", null, Map.of(AutoCompactSetting.KEY, 285000))))
                .isEqualTo(285_000);
        // 坏值按未设置处理：不该由一个坏值把启动流程打断
        assertThat(AutoCompactSetting.thresholdOf(
                new SessionConfig("m", null, Map.of(AutoCompactSetting.KEY, "oops")))).isZero();
        assertThat(AutoCompactSetting.thresholdOf(
                new SessionConfig("m", null, Map.of(AutoCompactSetting.KEY, "")))).isZero();
    }

    @Test
    void writesThresholdAndPreservesSessionFields() {
        SessionConfig base = new SessionConfig("m", "sys", Map.of("k", "v"));

        SessionConfig updated = AutoCompactSetting.withThreshold(base, 285_000);

        assertThat(updated.model()).isEqualTo("m");
        assertThat(updated.systemPrompt()).isEqualTo("sys");
        assertThat(updated.extras()).containsEntry("k", "v")
                .containsEntry(AutoCompactSetting.KEY, "285000");
    }

    @Test
    void disablesByRemovingTheKeyInsteadOfWritingZero() {
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("k", "v");
        extras.put(AutoCompactSetting.KEY, "285000");
        SessionConfig enabled = new SessionConfig("m", null, extras);

        SessionConfig disabled = AutoCompactSetting.withThreshold(enabled, 0);

        assertThat(disabled.extras()).containsEntry("k", "v").doesNotContainKey(AutoCompactSetting.KEY);
    }

    @Test
    void describesThreshold() {
        assertThat(AutoCompactSetting.describe(285_000)).isEqualTo("285000 tokens");
        assertThat(AutoCompactSetting.describe(0)).isEqualTo("已关闭");
    }
}
