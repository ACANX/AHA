package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.cli.tty.Clipboard;
import com.acanx.module.aha.common.exception.AhaException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PasteSessionCommand} 测试：把剪贴板内容作为一条消息交回输入循环，
 * 以及读不到剪贴板 / 剪贴板为空时的行为与文案。
 *
 * @since 0.1.0
 */
class PasteSessionCommandTest {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private final PendingInput pending = new PendingInput();

    private SessionContext context() {
        return new SessionContext("s-1", null, null,
                new PrintStream(buffer, true, StandardCharsets.UTF_8), "内置默认身份", 0);
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private PasteSessionCommand command(String text, String source) {
        return new PasteSessionCommand(pending, () -> new Clipboard.Content(text, source));
    }

    @Test
    void sendsTheClipboardAsOneMessage() {
        PasteSessionCommand command = command("line1\nline2\nline3", "powershell …");

        SessionCommand.Outcome outcome = command.execute(context(), List.of());

        assertThat(outcome).isEqualTo(SessionCommand.Outcome.CONTINUE);
        // 换行原样保留 ⇒ 多行内容是一条消息，而不是三条（这就是它存在的理由）
        assertThat(pending.take()).isEqualTo("line1\nline2\nline3");
        assertThat(output())
                .contains("3 行")
                .contains("17 字符")
                .contains("powershell")
                .contains("| line1")
                .contains("已作为本条消息提交");
    }

    @Test
    void appendsTheNoteAsTheLastParagraph() {
        PasteSessionCommand command = command("some log", "fake");

        command.execute(context(), List.of("这段", "报错怎么修"));

        assertThat(pending.take()).isEqualTo("some log\n\n这段 报错怎么修");
    }

    @Test
    void doesNotSendAnythingWhenTheClipboardHasNoText() {
        PasteSessionCommand command = command("   \n  ", "fake");

        command.execute(context(), List.of());

        assertThat(pending.take()).as("没有内容可发").isNull();
        assertThat(output()).contains("剪贴板里没有文本内容");
    }

    @Test
    void reportsReadFailureWithoutSendingAnything() {
        PasteSessionCommand command = new PasteSessionCommand(pending, () -> {
            throw new AhaException(Clipboard.CODE_CLIPBOARD_UNAVAILABLE,
                    "读不到剪贴板（已尝试 3 种方式：wl-paste（Cannot run program））。");
        });

        command.execute(context(), List.of());

        assertThat(pending.take()).isNull();
        // 原因要原样带出来（含「已尝试哪些方式」），否则用户无从下手
        assertThat(output())
                .contains("[error] 读不到剪贴板")
                .contains("wl-paste");
    }

    @Test
    void previewIsTruncatedForLongContent() {
        String longText = String.join("\n",
                IntStream.rangeClosed(1, 20).mapToObj(i -> "第 " + i + " 行").toList());
        PasteSessionCommand command = command(longText, "fake");

        command.execute(context(), List.of());

        assertThat(pending.take()).as("发出去的是全文，不是预览").hasLineCount(20);
        assertThat(output())
                .contains("前 8 行")
                .contains("还有 12 行");
    }

    @Test
    void isDiscoverableThroughHelpOnceRegistered() {
        SessionCommandRegistry registry = new SessionCommandRegistry();
        registry.register(command("x", "fake"));

        assertThat(registry.find("paste")).isPresent();
        registry.dispatch(context(), "/help");

        assertThat(output()).contains("/paste");
    }
}
