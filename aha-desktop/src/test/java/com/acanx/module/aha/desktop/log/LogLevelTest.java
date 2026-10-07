package com.acanx.module.aha.desktop.log;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LogLevel} 测试：解析与门槛判定。
 *
 * @since 0.2.0
 */
class LogLevelTest {

    @Test
    void parsesCaseInsensitively() {
        assertThat(LogLevel.of("info")).isEqualTo(LogLevel.INFO);
        assertThat(LogLevel.of(" WARN ")).isEqualTo(LogLevel.WARN);
        assertThat(LogLevel.of("ERROR")).isEqualTo(LogLevel.ERROR);
    }

    @Test
    void unknownLevelFallsBackToInfo() {
        // 既不该隐藏（当 TRACE 会让它被滤掉），也不该抬升严重性（当 ERROR 会误导）
        assertThat(LogLevel.of("NOT_A_LEVEL")).isEqualTo(LogLevel.INFO);
        assertThat(LogLevel.of(null)).isEqualTo(LogLevel.INFO);
        assertThat(LogLevel.of("")).isEqualTo(LogLevel.INFO);
    }

    @Test
    void thresholdComparesSeverity() {
        assertThat(LogLevel.ERROR.atLeast(LogLevel.WARN)).isTrue();
        assertThat(LogLevel.WARN.atLeast(LogLevel.WARN)).isTrue();
        assertThat(LogLevel.INFO.atLeast(LogLevel.WARN)).isFalse();
        assertThat(LogLevel.TRACE.atLeast(null)).as("门槛为空表示全都显示").isTrue();
    }

    @Test
    void severityIsOrdered() {
        assertThat(LogLevel.TRACE.severity()).isLessThan(LogLevel.DEBUG.severity());
        assertThat(LogLevel.DEBUG.severity()).isLessThan(LogLevel.INFO.severity());
        assertThat(LogLevel.INFO.severity()).isLessThan(LogLevel.WARN.severity());
        assertThat(LogLevel.WARN.severity()).isLessThan(LogLevel.ERROR.severity());
    }
}
