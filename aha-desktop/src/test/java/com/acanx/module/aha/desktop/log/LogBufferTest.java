package com.acanx.module.aha.desktop.log;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LogBuffer} 测试：容量上限与「读取时过滤」。
 *
 * @since 0.2.0
 */
class LogBufferTest {

    private static LogLine line(LogLevel level, String message) {
        return new LogLine(level, "com.acanx.module.aha.Test", message, null, 0);
    }

    @Test
    void keepsNewestLinesWhenFull() {
        LogBuffer buffer = new LogBuffer(3);
        buffer.add(line(LogLevel.INFO, "1"));
        buffer.add(line(LogLevel.INFO, "2"));
        buffer.add(line(LogLevel.INFO, "3"));
        buffer.add(line(LogLevel.INFO, "4"));

        assertThat(buffer.size()).isEqualTo(3);
        assertThat(buffer.snapshot(LogLevel.TRACE)).extracting(LogLine::message)
                .containsExactly("2", "3", "4");
        assertThat(buffer.totalReceived()).as("累计值只增不减，界面靠它判断有没有新行").isEqualTo(4);
    }

    @Test
    void filterIsAppliedOnReadSoHistorySurvives() {
        LogBuffer buffer = new LogBuffer();
        buffer.add(line(LogLevel.DEBUG, "调试"));
        buffer.add(line(LogLevel.ERROR, "错误"));

        assertThat(buffer.snapshot(LogLevel.ERROR)).extracting(LogLine::message)
                .containsExactly("错误");
        // 门槛调回去，之前的 DEBUG 还在——不需要「复现一次才看得到」
        assertThat(buffer.snapshot(LogLevel.TRACE)).hasSize(2);
    }

    @Test
    void renderTakesOnlyTheNewestLines() {
        LogBuffer buffer = new LogBuffer();
        for (int i = 1; i <= 10; i++) {
            buffer.add(line(LogLevel.INFO, "第" + i + "行"));
        }

        String text = buffer.render(LogLevel.INFO, 3);

        assertThat(text.split("\n")).hasSize(3);
        assertThat(text).contains("第10行").doesNotContain("第7行");
    }

    @Test
    void renderAlignsLoggerColumn() {
        LogBuffer buffer = new LogBuffer();
        buffer.add(new LogLine(LogLevel.INFO, "a.Short", "m1", null, 0));
        buffer.add(new LogLine(LogLevel.WARN, "a.MuchLongerName", "m2", null, 0));

        String[] rows = buffer.render(LogLevel.TRACE, 0).split("\n");

        assertThat(rows).hasSize(2);
        assertThat(rows[0].indexOf("m1")).as("消息列应对齐").isEqualTo(rows[1].indexOf("m2"));
    }

    @Test
    void clearEmptiesLinesButKeepsCounter() {
        LogBuffer buffer = new LogBuffer();
        buffer.add(line(LogLevel.INFO, "x"));

        buffer.clear();

        assertThat(buffer.size()).isZero();
        assertThat(buffer.totalReceived()).isEqualTo(1);
    }

    @Test
    void nullLineAndNonPositiveCapacityAreTolerated() {
        LogBuffer buffer = new LogBuffer(0);
        buffer.add(null);
        buffer.add(line(LogLevel.INFO, "x"));

        assertThat(buffer.capacity()).isEqualTo(LogBuffer.DEFAULT_CAPACITY);
        assertThat(buffer.size()).isEqualTo(1);
    }
}
