package com.acanx.module.aha.desktop.chat;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolCard} 测试：工具卡片的口径（对齐 {@code GUIDesign.md} 第 4.2 节）。
 *
 * @since 0.2.0
 */
class ToolCardTest {

    @Test
    void headlineCarriesKindToolAndTarget() {
        String head = ToolCard.headline("读取", "file-read", "src/Main.java");
        assertThat(head).isEqualTo("读取  file-read  src/Main.java");
    }

    @Test
    void headlineOmitsBlankTarget() {
        assertThat(ToolCard.headline("调用工具", "no-such-tool", null))
                .isEqualTo("调用工具  no-such-tool");
        assertThat(ToolCard.headline("调用工具", "no-such-tool", "  "))
                .isEqualTo("调用工具  no-such-tool");
    }

    @Test
    void runningLineHasNoStateOrDuration() {
        assertThat(ToolCard.resultLine(false, true, 5000, 100)).isEqualTo("运行中…");
    }

    @Test
    void resultLineFollowsDesignOrderStateDurationScale() {
        // 设计稿顺序：状态 · 耗时 · 规模
        assertThat(ToolCard.resultLine(true, true, 150, 412))
                .isEqualTo("✓ 完成 · 0.2s · 412 行");
        assertThat(ToolCard.resultLine(true, false, 1000, 0))
                .isEqualTo("✗ 失败 · 1.0s");
    }

    @Test
    void secondsRoundsHalfUpToOneDecimal() {
        // 250ms 是 0.25s，Java 的 Formatter 按 HALF_UP 进位到 0.3（实测过，别写成 0.2）
        assertThat(ToolCard.seconds(250)).isEqualTo("0.3s");
        assertThat(ToolCard.seconds(149)).isEqualTo("0.1s");
        assertThat(ToolCard.seconds(0)).isEqualTo("0.0s");
        assertThat(ToolCard.seconds(-5)).isEqualTo("0.0s");
    }

    @Test
    void durationUnder100msIsHidden() {
        // 与 CLI 同规则：不足 100ms 不显示耗时，避免「0.0s」这种噪音
        assertThat(ToolCard.resultLine(true, true, 99, 3)).isEqualTo("✓ 完成 · 3 行");
        assertThat(ToolCard.resultLine(true, true, 100, 3)).isEqualTo("✓ 完成 · 0.1s · 3 行");
    }

    @Test
    void paramsListsKeyValueLinesInOrder() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", "a.txt");
        args.put("limit", 20);
        assertThat(ToolCard.params(args)).isEqualTo("path: a.txt\nlimit: 20");
        assertThat(ToolCard.params(null)).isEqualTo("（无参数）");
        assertThat(ToolCard.params(Map.of())).isEqualTo("（无参数）");
    }

    @Test
    void outputTitleSaysHowMuchIsHidden() {
        assertThat(ToolCard.outputTitle(0)).isEmpty();
        assertThat(ToolCard.outputTitle(12)).isEqualTo("输出（共 12 行）");
        assertThat(ToolCard.outputTitle(412)).isEqualTo("输出（前 200 行，共 412 行）");
        assertThat(ToolCard.outputTitle(200)).isEqualTo("输出（共 200 行）");
    }

    @Test
    void previewKeepsAtMost200Lines() {
        StringBuilder big = new StringBuilder();
        for (int i = 1; i <= 412; i++) {
            big.append("line-").append(i).append('\n');
        }
        String preview = ToolCard.preview(big.toString());
        assertThat(ToolCard.lines(preview)).isEqualTo(200);
        assertThat(preview).startsWith("line-1").endsWith("line-200");
        assertThat(ToolCard.preview("a\nb")).isEqualTo("a\nb");
        assertThat(ToolCard.preview(null)).isEmpty();
    }

    @Test
    void numberedPadsLineNumbersToTotalWidth() {
        assertThat(ToolCard.numbered("package a;\n\nimport b;"))
                .isEqualTo("1  package a;\n2  \n3  import b;");
        StringBuilder hundred = new StringBuilder();
        for (int i = 1; i <= 100; i++) {
            hundred.append("x\n");
        }
        // 三位数行号时宽度跟着变（100 行 → 宽度 3）
        assertThat(ToolCard.numbered(hundred.toString())).startsWith("  1  x");
        assertThat(ToolCard.numbered(null)).isEmpty();
    }

    @Test
    void linesCountsNewlines() {
        assertThat(ToolCard.lines(null)).isZero();
        assertThat(ToolCard.lines("")).isZero();
        assertThat(ToolCard.lines("one")).isEqualTo(1);
        assertThat(ToolCard.lines("one\n")).isEqualTo(2);
    }

    @Test
    void textOfTurnsResultIntoDisplayableText() {
        assertThat(ToolCard.textOf(null)).isNull();
        assertThat(ToolCard.textOf("  ")).isNull();
        assertThat(ToolCard.textOf("ok")).isEqualTo("ok");
    }

    @Test
    void caretAndTooltipTellTheAction() {
        assertThat(ToolCard.caret(false)).isEqualTo("▸");
        assertThat(ToolCard.caret(true)).isEqualTo("▾");
        assertThat(ToolCard.tooltip(false)).contains("展开");
        assertThat(ToolCard.tooltip(true)).contains("收起");
    }

    @Test
    void retryMessageHandsTheFailureBackToTheModel() {
        String message = ToolCard.retryMessage("shell-exec", Map.of("command", "rm -rf build"));
        assertThat(message).contains("shell-exec").contains("重试")
                .contains("command: rm -rf build");
    }
}
