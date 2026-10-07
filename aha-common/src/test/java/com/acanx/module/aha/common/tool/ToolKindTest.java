package com.acanx.module.aha.common.tool;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolKind} 测试：CLI 与桌面端共用的语义判定。
 *
 * <p>本类从 {@code aha-cli} 下移到 {@code aha-common}，因为桌面端的工具卡片依赖同一套判定
 * （{@code GUIDesign.md} 第 3.1 节要求两端语义一致）。测试随实现一起搬家，避免只搬实现。</p>
 *
 * @since 0.1.0
 */
class ToolKindTest {

    @Test
    void classifiesKnownTools() {
        assertThat(ToolKind.of("file-read")).isEqualTo(ToolKind.READ);
        assertThat(ToolKind.of("file-list")).isEqualTo(ToolKind.READ);
        assertThat(ToolKind.of("file-write")).isEqualTo(ToolKind.WRITE);
        assertThat(ToolKind.of("shell-exec")).isEqualTo(ToolKind.EXEC);
        assertThat(ToolKind.of("http-get")).isEqualTo(ToolKind.NETWORK);
    }

    @Test
    void unknownAndNullFallBackToOther() {
        assertThat(ToolKind.of("no-such-tool")).isEqualTo(ToolKind.OTHER);
        assertThat(ToolKind.of(null)).isEqualTo(ToolKind.OTHER);
    }

    @Test
    void targetPrefersSemanticKey() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("other", "ignored-first");
        args.put("path", "src/Main.java");
        assertThat(ToolKind.READ.targetOf(args)).isEqualTo("src/Main.java");
    }

    @Test
    void targetFallsBackToFirstNonBlankArgument() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("first", "  ");
        args.put("second", "value");
        assertThat(ToolKind.OTHER.targetOf(args)).isEqualTo("value");
        assertThat(ToolKind.OTHER.targetOf(Map.of())).isNull();
    }

    @Test
    void programIsFirstWordOfCommandOnlyForExec() {
        Map<String, Object> args = Map.of("command", "  git   status --short");
        assertThat(ToolKind.EXEC.programOf(args)).isEqualTo("git");
        assertThat(ToolKind.READ.programOf(args)).isNull();
    }

    @Test
    void summaryKeepsOrderAndRendersNull() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("a", 1);
        args.put("b", null);
        assertThat(ToolKind.summary(args)).containsExactly(
                Map.entry("a", "1"), Map.entry("b", "null"));
    }
}
