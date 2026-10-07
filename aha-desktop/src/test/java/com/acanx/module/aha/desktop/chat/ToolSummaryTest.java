package com.acanx.module.aha.desktop.chat;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolSummary} 测试：卡片两行文字的口径（对齐 GUIDesign 第 4.2 节）。
 *
 * @since 0.2.0
 */
class ToolSummaryTest {

    @Test
    void headContainsKindNameAndTarget() {
        String head = ToolSummary.head("file-read", Map.of("path", "src/Main.java"));
        assertThat(head).contains("读取").contains("file-read").contains("src/Main.java");
    }

    @Test
    void headOmitsTargetWhenUnknown() {
        String head = ToolSummary.head("no-such-tool", Map.of());
        assertThat(head).contains("调用工具").contains("no-such-tool");
    }

    @Test
    void resultLineCarriesStateAndScale() {
        assertThat(ToolSummary.result(true, "a\nb\nc")).isEqualTo("✓ 完成 · 3 行");
        assertThat(ToolSummary.result(false, null)).isEqualTo("✗ 失败");
        assertThat(ToolSummary.result(true, "")).isEqualTo("✓ 完成");
    }

    @Test
    void linesCountsNewlines() {
        assertThat(ToolSummary.lines(null)).isZero();
        assertThat(ToolSummary.lines("")).isZero();
        assertThat(ToolSummary.lines("one")).isEqualTo(1);
        assertThat(ToolSummary.lines("one\n")).isEqualTo(2);
    }
}
