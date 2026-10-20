package com.acanx.module.aha.desktop.markdown;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * 桌面端解析性能基线（issue #412 / tracking #408）。
 *
 * <p>解析器要到 S1.1 才实现，本类暂时 {@code @Disabled}；解除时把反射换成直接调用。
 * 阈值 50 ms 与 CLI 侧「500 行 &lt; 3 s」不是同一量级：CLI 那条防的是数量级退化，
 * 这条是 S1.1 的交付指标（解析一次 500 行 / 约 20 KB）。</p>
 *
 * @since 0.2.0
 */
@Disabled("S1.1 实现 MarkdownParser 后解除：改为直接调用")
class MarkdownParsePerformanceTest {

    /** S1.1 的解析耗时目标。 */
    private static final Duration BUDGET = Duration.ofMillis(50);

    @Test
    void parsesFiveHundredLinesWithinFiftyMillis() throws Exception {
        String input = sampleMarkdown(100);
        Class<?> parserClass = Class.forName("com.acanx.module.aha.desktop.markdown.MarkdownParser");
        Object parser = parserClass.getDeclaredConstructor().newInstance();
        Method parse = parserClass.getMethod("parse", String.class);

        assertTimeout(BUDGET, () -> parse.invoke(parser, input));
    }

    private static String sampleMarkdown(int groups) {
        StringBuilder text = new StringBuilder(groups * 120);
        for (int i = 0; i < groups; i++) {
            text.append("| a | b |\n")
                    .append("|---|---|\n")
                    .append("| ").append(i).append(" | 值 |\n\n")
                    .append("### 标题 ").append(i).append("\n\n")
                    .append("- 条目 **粗** `code` [链接](https://example.com/").append(i).append(")\n\n");
        }
        return text.toString();
    }
}
