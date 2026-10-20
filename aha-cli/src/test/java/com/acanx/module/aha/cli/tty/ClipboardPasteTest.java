package com.acanx.module.aha.cli.tty;

import com.acanx.module.aha.common.exception.AhaException;
import org.jline.keymap.KeyMap;
import org.jline.reader.Binding;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Widget;
import org.jline.terminal.impl.LineDisciplineTerminal;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ClipboardPaste} 测试：键位真的绑上了、按下去真的把内容写进输入行、
 * 大块内容折叠成占位符、读不到剪贴板时绝不吞掉用户的按键。
 *
 * <p>用**真实 JLine 行读取器**（管道流 + 固定尺寸）驱动，而不是只调方法：
 * 键位解析、缓冲区写入这些都是 JLine 的行为，只有真跑一遍才能证明「按键 → 插入」这段接通了。</p>
 *
 * @since 0.2.0
 */
class ClipboardPasteTest {

    private final ByteArrayOutputStream sink = new ByteArrayOutputStream();

    private PrintStream out() {
        return new PrintStream(sink, true, StandardCharsets.UTF_8);
    }

    private String printed() {
        return sink.toString(StandardCharsets.UTF_8);
    }

    /**
     * 真实 LineReader（`LineDisciplineTerminal`：字节由测试同步喂入，不会因输入流 EOF 提前关闭）。
     *
     * <p>「按用户按键」的用例只用到键位与缓冲区，不需要终端应答；需要走完整 {@code readLine}
     * 的用例（括号粘贴）才把字节喂进去。</p>
     */
    private LineReader reader() throws Exception {
        LineDisciplineTerminal terminal = new LineDisciplineTerminal(
                "aha-test", "xterm", new ByteArrayOutputStream(), StandardCharsets.UTF_8);
        return LineReaderBuilder.builder()
                .terminal(terminal)
                .option(LineReader.Option.BRACKETED_PASTE, true)
                .build();
    }

    /** 取「按下某键」的绑定并执行 —— 等同真实按键走一遍 JLine 的键位解析。 */
    private static Widget widgetOf(LineReader reader, String key) {
        return (Widget) reader.getKeyMaps().get(LineReader.MAIN).getBound(key);
    }

    private static boolean press(LineReader reader, String key) {
        return widgetOf(reader, key).apply();
    }

    private static Supplier<Clipboard.Content> clipboard(String text) {
        return () -> new Clipboard.Content(text, "测试剪贴板");
    }

    private static String lines(int count) {
        return String.join("\n",
                IntStream.rangeClosed(1, count).mapToObj(i -> "第 " + i + " 行").toList());
    }

    @Test
    void bindsAltVAndCtrlVToOurWidget() throws Exception {
        LineReader reader = reader();

        ClipboardPaste.bind(reader, clipboard("x"), out());

        KeyMap<Binding> main = reader.getKeyMaps().get(LineReader.MAIN);
        assertThat(main.getBound("\033v")).as("Alt+V").isInstanceOf(Widget.class);
        assertThat(main.getBound("\u0016")).as("Ctrl+V").isInstanceOf(Widget.class);
        assertThat(reader.getWidgets()).containsKey(ClipboardPaste.WIDGET);
    }

    @Test
    void pressingTheBoundKeyInsertsTheClipboardIntoTheInputLine() throws Exception {
        LineReader reader = reader();
        Supplier<Clipboard.Content> clipboard = clipboard(lines(3));
        ClipboardPaste.bind(reader, clipboard, out());

        // 按下绑定键（等同真实按键：拿到绑定并执行）
        boolean handled = press(reader, "\033v");

        assertThat(handled).isTrue();
        // 整体写入：三条内容一次进缓冲区，换行是字面换行（不会被逐行提交）
        assertThat(reader.getBuffer().toString()).isEqualTo(lines(3));
    }

    @Test
    void bigClipboardContentIsFoldedIntoAMarkerAndAnnounced() throws Exception {
        LineReader reader = reader();
        String big = lines(42);
        ClipboardPaste.bind(reader, clipboard(big), out());

        press(reader, "\033v");

        assertThat(reader.getBuffer().toString()).isEqualTo("[paste #1 +42 lines]");
        // 折叠必须说一声，否则用户以为内容丢了
        assertThat(printed()).contains("[paste]").contains("+42 lines").contains("测试剪贴板");
    }

    @Test
    void clipboardFailureKeepsTheKeyAvailable() throws Exception {
        LineReader reader = reader();
        ClipboardPaste.bind(reader, () -> {
            throw new AhaException(Clipboard.CODE_CLIPBOARD_UNAVAILABLE,
                    "读不到剪贴板（已尝试 2 种方式：wl-paste、xclip）");
        }, out());

        boolean handled = press(reader, "\033v");

        assertThat(handled).as("失败时不算已消费，用户还能继续编辑").isFalse();
        assertThat(reader.getBuffer().toString()).isEmpty();
        assertThat(printed()).contains("读不到剪贴板").contains("wl-paste");
    }

    @Test
    void blankClipboardPrintsAHintInsteadOfInsertingNothing() throws Exception {
        LineReader reader = reader();
        ClipboardPaste.bind(reader, clipboard("   \n  "), out());

        press(reader, "\033v");

        assertThat(reader.getBuffer().toString()).isEmpty();
        assertThat(printed()).contains("没有文本内容");
    }

    /**
     * 对照用例：终端**支持**括号粘贴时，粘贴本来就是整体（JLine 收到标记后把内容一次写进缓冲区，
     * 换行换成字面换行）—— 这正是 conhost 上拿不到的那条路，也是 {@link ClipboardPaste} 存在的理由。
     */
    @Test
    void bracketedPasteFromTheTerminalArrivesAsOneInput() throws Exception {
        LineDisciplineTerminal terminal = new LineDisciplineTerminal(
                "aha-test", "xterm", new ByteArrayOutputStream(), StandardCharsets.UTF_8);
        LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .option(LineReader.Option.BRACKETED_PASTE, true)
                .build();

        terminal.processInputBytes("\033[200~第一行\n第二行\n第三行\033[201~\n"
                .getBytes(StandardCharsets.UTF_8));

        assertThat(reader.readLine("> ")).isEqualTo("第一行\n第二行\n第三行");
    }
}
