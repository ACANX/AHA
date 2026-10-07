package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.common.event.ContentEvent;
import com.acanx.module.aha.common.event.DoneEvent;
import com.acanx.module.aha.common.event.ErrorEvent;
import com.acanx.module.aha.common.event.ToolCallEvent;
import com.acanx.module.aha.common.event.ToolResultEvent;
import com.acanx.module.aha.common.event.UsageEvent;
import com.acanx.module.aha.cli.tty.StatusSurface;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StreamRenderer} 测试：内联渲染、降级与截断。
 *
 * @since 0.1.0
 */
class StreamRendererTest {

    private static final String ESC = "\u001B[";

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private StreamRenderer renderer(boolean color, int width) {
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        // 状态行不进输出流（它走底部常驻区），因此这里用空载体避免干扰正文断言
        return new StreamRenderer(out, Style.of(color), width,
                new StatusLine(StatusSurface.NONE, false, true, width, null, Style.of(false)), true, ToolEnvironment.none());
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void contentDeltasAreRenderedInOrder() {
        StreamRenderer renderer = renderer(false, 0);
        renderer.onEvent(new ContentEvent("s", "Hello"));
        renderer.onEvent(new ContentEvent("s", " world"));
        renderer.endTurn();

        assertThat(output()).isEqualTo("Hello world" + System.lineSeparator());
    }

    @Test
    void statusLineGoesToTheSurfaceNotTheOutput() {
        // 状态行走终端底部常驻区，绝不能混进正文输出流：
        // 否则它会与正文错行，还会进管道与日志
        RecordingStatusSurface surface = new RecordingStatusSurface();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        StreamRenderer renderer = new StreamRenderer(out, Style.of(false), 0,
                new StatusLine(surface, true, true, 60, "deepseek-chat", Style.of(false)), true, ToolEnvironment.none());

        renderer.beginTurn();
        // 状态行只回答「在干什么」：耗时归工具区块末行，不在这里
        assertThat(surface.last()).isEqualTo("⠋ 等待响应…");
        assertThat(output()).doesNotContain("等待响应…");

        // 写正文前状态行回到空闲文案（而非撤掉底部区域——那一行还兼着输入行下边框）
        renderer.onEvent(new ContentEvent("s", "答案"));
        assertThat(surface.last()).isEqualTo("deepseek-chat");
        assertThat(output()).contains("答案");
    }

    @Test
    void toolExecutionShowsItsOwnStatus() {
        RecordingStatusSurface surface = new RecordingStatusSurface();
        PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);
        StreamRenderer renderer = new StreamRenderer(out, Style.of(false), 0,
                new StatusLine(surface, true, true, 60, "m", Style.of(false)), true, ToolEnvironment.none());

        renderer.onEvent(new ToolCallEvent("s", "shell-exec", Map.of("command", "ls")));

        // 首行只有类别与工具名，参数走明细行（长内容才不会被截断）
        assertThat(output()).contains("[执行命令] shell-exec");
        assertThat(surface.last()).contains("执行工具 shell-exec…");
    }

    @Test
    void markdownHeadingIsRenderedByLevel() {
        StreamRenderer renderer = renderer(true, 0);
        renderer.onEvent(new ContentEvent("s", "## 标题"));
        renderer.endTurn();

        // 有色时不打 `#` 标记，按级别配色
        assertThat(output()).contains(ESC + "1m" + ESC + "96m标题" + ESC + "0m")
                .doesNotContain("##");
    }

    @Test
    void toolCallStartsOnAFreshLine() {
        StreamRenderer renderer = renderer(false, 0);
        renderer.onEvent(new ContentEvent("s", "正在读取"));
        renderer.onEvent(new ToolCallEvent("s", "file-read", Map.of("path", "a.txt")));
        renderer.onEvent(new ToolResultEvent("s", "file-read", "ok"));

        String text = output();
        // 区块前留空行，首行是「类型 + 工具名」，参数在紧随其后的明细行
        assertThat(text).contains("正在读取" + System.lineSeparator()
                + System.lineSeparator() + "[读取文件] file-read");
        assertThat(text).contains("path").contains("a.txt");
        assertThat(text).contains("✓ 完成").contains("ok");
    }

    @Test
    void consecutiveToolCallsAreSeparatedByABlankLine() {
        // 多轮工具调用若不隔开就会糊成一片，人得逐行读才能分清是几次操作
        StreamRenderer renderer = renderer(false, 0);
        renderer.onEvent(new ToolCallEvent("s", "file-read", Map.of("path", "a.txt")));
        renderer.onEvent(new ToolResultEvent("s", "file-read", "ok"));
        renderer.onEvent(new ToolCallEvent("s", "file-read", Map.of("path", "b.txt")));
        renderer.onEvent(new ToolResultEvent("s", "file-read", "ok2"));

        String text = output();
        int firstResult = text.indexOf("✓ 完成");
        int secondHead = text.lastIndexOf("[读取文件] file-read");

        assertThat(firstResult).isNotNegative();
        assertThat(secondHead).isGreaterThan(firstResult);
        assertThat(text.substring(firstResult, secondHead))
                .contains(System.lineSeparator() + System.lineSeparator());
    }

    @Test
    void colorsOnlyWhenEnabled() {
        StreamRenderer colored = renderer(true, 0);
        colored.onEvent(new ErrorEvent("s", "LLM_HTTP_401", "unauthorized"));
        assertThat(output()).contains(ESC + "31m").contains(ESC + "0m");

        buffer.reset();
        StreamRenderer plain = renderer(false, 0);
        plain.onEvent(new ErrorEvent("s", "LLM_HTTP_401", "unauthorized"));
        // 错误正文另起一行完整给出，不再挤在标题行里
        assertThat(output()).doesNotContain(ESC)
                .contains("[error] LLM_HTTP_401").contains("unauthorized");
    }

    @Test
    void longToolOutputIsPreviewedInsteadOfDumped() {
        // 工具输出可能极长（读文件、跑命令），全量贴进对话只会淹没上下文：
        // 只给前几行，并明确告知还有多少行
        StreamRenderer renderer = renderer(false, 0);
        renderer.onEvent(new ToolCallEvent("s", "shell-exec", Map.of("command", "x".repeat(900))));
        renderer.onEvent(new ToolResultEvent("s", "shell-exec",
                String.join("\n", "l1", "l2", "l3", "l4", "l5", "l6")));

        String text = output();
        // 结果区有明确标题，正文按行给出，超出上限时报总数
        assertThat(text).contains("结果").contains("l1").contains("l5");
        assertThat(text).doesNotContain("l6");
        assertThat(text).contains("输出共 6 行");
    }

    @Test
    void usageIsPrintedOnlyWhenMeaningful() {
        StreamRenderer renderer = renderer(false, 0);
        renderer.onEvent(new UsageEvent("s", 0, 0));
        renderer.onEvent(new DoneEvent("s", "stop"));
        assertThat(output()).isEmpty();

        renderer.onEvent(new UsageEvent("s", 12, 34));
        assertThat(output()).contains("[usage] 输入 12 / 输出 34 tokens");
    }

    @Test
    void endTurnBreaksLineOnlyWhenMidLine() {
        StreamRenderer renderer = renderer(false, 0);
        renderer.onEvent(new ContentEvent("s", "abc"));
        renderer.endTurn();
        renderer.endTurn();

        assertThat(output()).isEqualTo("abc" + System.lineSeparator());
    }

    @Test
    void contentIsWrappedAtTerminalWidth() {
        StreamRenderer renderer = renderer(false, 4);
        renderer.onEvent(new ContentEvent("s", "ab"));
        renderer.onEvent(new ContentEvent("s", "cdef"));
        renderer.endTurn();

        assertThat(output()).isEqualTo("abcd" + System.lineSeparator() + "ef" + System.lineSeparator());
    }

    @Test
    void wideContentWrapsByDisplayWidth() {
        StreamRenderer renderer = renderer(false, 6);
        renderer.onEvent(new ContentEvent("s", "一二三四"));
        renderer.endTurn();

        // 6 列只能放 3 个汉字
        assertThat(output()).isEqualTo("一二三" + System.lineSeparator() + "四" + System.lineSeparator());
    }
    @Test
    void failedToolPrintsTheFullErrorNotJustTheSummary() {
        StreamRenderer renderer = renderer(false, 0);
        String error = "退出码 255: 'slash' is not recognized as an internal or external command"
                + System.lineSeparator()
                + "命令: powershell -NoProfile -Command \"Get-ChildItem\"";
        renderer.onEvent(new ToolCallEvent("s", "shell-exec", Map.of("command", "powershell ...")));
        renderer.onEvent(new ToolResultEvent("s", "shell-exec", error, false));

        String text = output();

        assertThat(text).contains("✗ 失败");
        // 完整错误必须落在正文，而不是只留 80 字符摘要
        assertThat(text).contains("命令: powershell -NoProfile -Command \"Get-ChildItem\"");
    }

}
