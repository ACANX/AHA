package com.acanx.module.aha.cli;

import com.acanx.module.aha.common.tool.ToolPermission;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.UserInterruptException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ConsoleToolApprover} 测试。
 *
 * @since 0.1.0
 */
class ConsoleToolApproverTest {

    private static LineReader readerReturning(String... answers) {
        LineReader reader = mock(LineReader.class);
        if (answers.length == 0) {
            when(reader.readLine(anyString())).thenReturn(null);
        } else if (answers.length == 1) {
            when(reader.readLine(anyString())).thenReturn(answers[0]);
        } else {
            when(reader.readLine(anyString())).thenReturn(answers[0], java.util.Arrays.copyOfRange(answers, 1, answers.length));
        }
        return reader;
    }

    @Test
    void acceptsOnYes() {
        ConsoleToolApprover approver = new ConsoleToolApprover(readerReturning("y"));

        assertThat(approver.approve("file-write", ToolPermission.WRITE, Map.of("path", "a"))).isTrue();
    }

    @Test
    void acceptsOnFullYesAndIgnoresCase() {
        assertThat(new ConsoleToolApprover(readerReturning("YES"))
                .approve("w", ToolPermission.WRITE, Map.of())).isTrue();
        assertThat(new ConsoleToolApprover(readerReturning(" Y "))
                .approve("w", ToolPermission.WRITE, Map.of())).isTrue();
    }

    @Test
    void rejectsOnNo() {
        assertThat(new ConsoleToolApprover(readerReturning("n"))
                .approve("w", ToolPermission.WRITE, Map.of())).isFalse();
    }

    @Test
    void rejectsOnEmptyOrUnknownInput() {
        // 回车默认拒绝：不能因为用户误触而放行高风险操作
        assertThat(new ConsoleToolApprover(readerReturning(""))
                .approve("w", ToolPermission.WRITE, Map.of())).isFalse();
        assertThat(new ConsoleToolApprover(readerReturning("maybe"))
                .approve("w", ToolPermission.WRITE, Map.of())).isFalse();
    }

    @Test
    void rejectsWhenInputUnavailable() {
        // Ctrl+C / Ctrl+D / 无输入
        assertThat(new ConsoleToolApprover(readerReturning())
                .approve("w", ToolPermission.WRITE, Map.of())).isFalse();
    }

    @Test
    void rejectsOnUserInterruptWithoutPropagating() {
        LineReader reader = mock(LineReader.class);
        when(reader.readLine(anyString())).thenThrow(new UserInterruptException("^C"));

        // 中断单次授权不应炸掉整轮对话
        assertThat(new ConsoleToolApprover(reader)
                .approve("w", ToolPermission.WRITE, Map.of())).isFalse();
    }

    @Test
    void rejectsOnEndOfFileWithoutPropagating() {
        LineReader reader = mock(LineReader.class);
        when(reader.readLine(anyString())).thenThrow(new EndOfFileException());

        assertThat(new ConsoleToolApprover(reader)
                .approve("w", ToolPermission.WRITE, Map.of())).isFalse();
    }

    @Test
    void alwaysOptionStopsFurtherPromptsForSamePermission() {
        LineReader reader = readerReturning("a");
        ConsoleToolApprover approver = new ConsoleToolApprover(reader);

        assertThat(approver.approve("w1", ToolPermission.WRITE, Map.of())).isTrue();
        assertThat(approver.approve("w2", ToolPermission.WRITE, Map.of())).isTrue();
        assertThat(approver.approve("w3", ToolPermission.WRITE, Map.of())).isTrue();

        // 只应询问一次
        verify(reader, times(1)).readLine(anyString());
    }

    @Test
    void alwaysOptionIsScopedToPermission() {
        LineReader reader = readerReturning("a", "n");
        ConsoleToolApprover approver = new ConsoleToolApprover(reader);

        assertThat(approver.approve("w", ToolPermission.WRITE, Map.of())).isTrue();
        // EXECUTE 未被放行，仍需确认
        assertThat(approver.approve("e", ToolPermission.EXECUTE, Map.of())).isFalse();

        verify(reader, times(2)).readLine(anyString());
    }

    @Test
    void argumentsAreSummarisedWithoutBlowingUpOutput() {
        // 超长参数不应原样刷屏
        String huge = "x".repeat(5000);
        ConsoleToolApprover approver = new ConsoleToolApprover(readerReturning("n"));

        assertThat(approver.approve("w", ToolPermission.WRITE, Map.of("content", huge))).isFalse();
    }
}
