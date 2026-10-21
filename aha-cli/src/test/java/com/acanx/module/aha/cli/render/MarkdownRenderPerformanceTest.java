package com.acanx.module.aha.cli.render;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * CLI 渲染性能基线（issue #412 / tracking #408）。
 *
 * <p>对象是现存实现 {@link MarkdownStream}（桌面端解析器要到 S1.1 才有，见
 * {@code MarkdownParsePerformanceTest}）。这里给出一条**真实可跑**的基线：
 * 超时阈值取得很宽，只用来挡住「数量级退化」（例如误把 O(n) 写成 O(n²)），
 * 真实耗时打印在日志里，供后续阶段对比。</p>
 *
 * @since 0.2.0
 */
class MarkdownRenderPerformanceTest {

    /** 阈值只防数量级退化：500 行在开发机上通常 &lt; 50 ms。 */
    private static final Duration BUDGET = Duration.ofSeconds(3);

    @Test
    void rendersFiveHundredLinesWithinBudget() {
        String input = sampleMarkdown(100);
        int lines = (int) input.chars().filter(ch -> ch == '\n').count();
        assertThat(lines).isGreaterThanOrEqualTo(500);

        MarkdownStream stream = new MarkdownStream(Style.of(false), true, 100);
        String out = assertTimeoutPreemptively(BUDGET, () -> {
            long start = System.nanoTime();
            String rendered = stream.feed(input) + stream.flush();
            long millis = (System.nanoTime() - start) / 1_000_000;
            System.out.println("[markdown-perf] " + lines + " 行 → " + millis + " ms，输出 "
                    + rendered.length() + " 字符");
            return rendered;
        });

        assertThat(out).contains("┌").contains("标题 99").contains("条目");
    }

    /**
     * 每组 5 行：表格（3 行）+ 标题 + 列表项。
     *
     * @param groups 组数
     * @return Markdown 文本
     */
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
