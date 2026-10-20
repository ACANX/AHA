package com.acanx.module.aha.desktop.markdown;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeout;

/**
 * 桌面端解析性能（issue #412 / #413，tracking #408）。
 *
 * <p>目标来自 #408 阶段 0：500 行 / 约 20 KB 的段，解析应在 50 ms 量级。
 * 这里阈值留 4 倍余量（200 ms）避免 CI 抖动误报，同时把真实耗时打印到日志，
 * 后续阶段拿它做对比。</p>
 *
 * @since 0.2.0
 */
class MarkdownParsePerformanceTest {

    /** CI 阈值；目标值是 50 ms，见类注释。 */
    private static final Duration BUDGET = Duration.ofMillis(200);

    @Test
    void parsesFiveHundredLinesWithinBudget() {
        String input = sampleMarkdown(100);
        MarkdownParser parser = new MarkdownParser();

        List<MarkdownBlock> blocks = assertTimeout(BUDGET, () -> {
            long start = System.nanoTime();
            List<MarkdownBlock> parsed = parser.parse(input);
            long millis = (System.nanoTime() - start) / 1_000_000;
            System.out.println("[markdown-parse-perf] 500 行 → " + millis + " ms，" + parsed.size() + " 块");
            return parsed;
        });

        assertThat(blocks).isNotEmpty();
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
