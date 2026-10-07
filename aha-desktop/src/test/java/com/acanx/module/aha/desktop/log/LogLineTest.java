package com.acanx.module.aha.desktop.log;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LogLine} 测试：面板行的渲染口径。
 *
 * @since 0.2.0
 */
class LogLineTest {

    @Test
    void renderCarriesTimeLevelLoggerAndMessage() {
        LogLine line = new LogLine(LogLevel.INFO,
                "com.acanx.module.aha.desktop.chat.ChatController", "已创建会话 8F3A", null, 0);

        String text = line.render(20);

        assertThat(text).contains("INFO").contains("ChatController").contains("已创建会话 8F3A");
        assertThat(text).containsPattern("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}");
    }

    @Test
    void shortLoggerKeepsLastSegment() {
        assertThat(new LogLine(LogLevel.INFO, "a.b.c.MyClass", "m", null, 0).shortLogger())
                .isEqualTo("MyClass");
        assertThat(new LogLine(LogLevel.INFO, "MyClass", "m", null, 0).shortLogger())
                .isEqualTo("MyClass");
        assertThat(new LogLine(LogLevel.INFO, null, "m", null, 0).shortLogger()).isEqualTo("-");
    }

    @Test
    void throwableIsRenderedOnItsOwnLine() {
        LogLine line = new LogLine(LogLevel.ERROR, "X", "失败了", "java.io.IOException: 文件不存在", 0);

        assertThat(line.render()).contains("失败了").contains("java.io.IOException: 文件不存在");
        assertThat(line.render().split("\n")).hasSize(2);
    }

    @Test
    void summaryOfFormatsTypeAndMessage() {
        assertThat(LogLine.summaryOf(new IOException("文件不存在")))
                .isEqualTo("java.io.IOException: 文件不存在");
        assertThat(LogLine.summaryOf(new IOException())).isEqualTo("java.io.IOException");
        assertThat(LogLine.summaryOf(null)).isNull();
    }
}
