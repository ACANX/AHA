package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.cli.tty.TerminalCapabilities.ColorDepth;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolBlock} / {@link ToolKind} 测试。
 *
 * <p>这两者决定「用户能否一眼看出刚才是读了文件还是跑了命令」，因此断言集中在
 * 类型归类、明细还原与区块结构上——文案与配色可以调，这几条不能。</p>
 *
 * @since 0.1.0
 */
class ToolBlockTest {

    private static final Style PLAIN = Style.of(false);

    private static final ToolEnvironment ENV =
            new ToolEnvironment("cmd.exe /c", "E:\\repo");

    /**
     * 拼接多行并去掉缩进——折行会在行间插入缩进空格，
     * 直接拼接会把一个词切成两段，断言就会误判成「内容丢了」。
     *
     * @param lines 行
     * @return 去缩进后的整段文本
     */
    private static String join(List<String> lines) {
        return String.join("", lines).replace(" ", "");
    }

    @Test
    void toolNamesAreClassifiedByImpact() {
        // 读、写、执行、网络四类的后果完全不同，必须分开着色
        assertThat(ToolKind.of("file-read")).isEqualTo(ToolKind.READ);
        assertThat(ToolKind.of("file-list")).isEqualTo(ToolKind.READ);
        assertThat(ToolKind.of("file-write")).isEqualTo(ToolKind.WRITE);
        assertThat(ToolKind.of("shell-exec")).isEqualTo(ToolKind.EXEC);
        assertThat(ToolKind.of("http-get")).isEqualTo(ToolKind.NETWORK);
        assertThat(ToolKind.of("no-such-tool")).isEqualTo(ToolKind.OTHER);
        assertThat(ToolKind.of(null)).isEqualTo(ToolKind.OTHER);
    }

    @Test
    void targetComesFromTheMostRelevantArgument() {
        assertThat(ToolKind.READ.targetOf(Map.of("path", "a.txt"))).isEqualTo("a.txt");
        assertThat(ToolKind.EXEC.targetOf(Map.of("command", "ls -la"))).isEqualTo("ls -la");
        assertThat(ToolKind.NETWORK.targetOf(Map.of("url", "https://x"))).isEqualTo("https://x");
        // 未识别的参数键也要有可读主语，否则区块只剩工具名
        assertThat(ToolKind.OTHER.targetOf(Map.of("whatever", "v"))).isEqualTo("v");
        assertThat(ToolKind.READ.targetOf(Map.of())).isNull();
    }

    @Test
    void headCarriesKindToolNameAndProgram() {
        // 色带行必须定宽铺满，只放短标识：长内容走各自的明细行
        assertThat(ToolBlock.head("file-read", Map.of("path", "Docs/PLAN.md"), PLAIN, 0))
                .isEqualTo("[读取文件] file-read");
        // 命令类多一段程序名——它是「这次操作干了什么」最省字的说法
        assertThat(ToolBlock.head("shell-exec", Map.of("command", "powershell -NoProfile -X"),
                PLAIN, 0)).isEqualTo("[执行命令] shell-exec  powershell");
    }

    @Test
    void headWithColorPaintsTheWholeLine() {
        String head = ToolBlock.head("shell-exec", Map.of("command", "ls -la"),
                Style.of(ColorDepth.ANSI256), 40);

        assertThat(head).contains("执行命令").contains("shell-exec").contains("ls");
        // 背景色带必须铺满：只上色不铺底看起来就是一段彩色文字，不成为“区块”
        assertThat(head).contains("\u001B[48;5;236m");
        assertThat(TextWidth.width(head.replaceAll("\u001B\\[[0-9;]*m", "")))
                .isEqualTo(39);
    }

    @Test
    void commandLineLooksLikeATerminalTranscript() {
        String line = ToolBlock.command("ls -la", PLAIN, ENV, 0);

        // 在哪个目录、用什么终端、跑了什么——一行说全
        assertThat(line).isEqualTo("E:\\repo> ls -la");
        // POSIX 用 $，提示符字符本身就是平台信号
        assertThat(ToolBlock.command("ls", PLAIN, new ToolEnvironment("/bin/sh -c", "/tmp/p"), 0))
                .isEqualTo("/tmp/p$ ls");
    }

    @Test
    void execHasNoSeparateDetailLines() {
        // 命令全文已由命令行承担，再来一遍只是重复
        assertThat(ToolBlock.details("shell-exec", Map.of("command", "ls"), PLAIN, 0, ENV)).isEmpty();
    }

    @Test
    void longCommandWrapsInsteadOfBeingCutOff() {
        // 回归：此前命令被截到 80 字符，超长部分完全看不到，
        // 「到底执行了什么」无从判断，失败也就无法排查
        String tail = "--last-flag";
        String command = "powershell -NoProfile -Command \"Get-ChildItem -Recurse -Path "
                + "aha-core/src/main/java -Filter *.java | Select-String -Pattern 'INSERT'\" " + tail;

        List<String> lines = List.of(ToolBlock.command(command, PLAIN, ENV, 60).split("\n"));
        String joined = join(lines);

        assertThat(lines.size()).isGreaterThan(1);
        // 尾部必须完整出现——这正是此前被丢掉的部分
        assertThat(joined).contains(tail);
        assertThat(joined).contains("Select-String");
    }

    @Test
    void massivelyLongCommandElidesOnlyTheMiddle() {
        StringBuilder command = new StringBuilder();
        for (int i = 1; i <= 200; i++) {
            command.append("segment").append(i).append(' ');
        }
        String text = command.toString();

        List<String> lines = List.of(ToolBlock.command(text, PLAIN, ENV, 40).split("\n"));
        String joined = join(lines);

        // 几百行的超长命令允许中间省略，但首尾必须保留
        assertThat(joined).contains("中间省略");
        assertThat(joined).contains("segment1");
        assertThat(joined).contains("segment200");
    }

    @Test
    void otherToolsListTheirArguments() {
        List<String> lines = ToolBlock.details("file-read", Map.of("path", "Docs/PLAN.md"),
                PLAIN, 0, ENV);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("path").contains("Docs/PLAN.md");
    }

    @Test
    void oversizedArgumentValueIsSummarised() {
        String content = "x".repeat(ToolBlock.ARG_VALUE_LIMIT + 50);

        List<String> lines = ToolBlock.details("file-write",
                Map.of("path", "a.txt", "content", content), PLAIN, 0, ENV);
        String joined = String.join("", lines);

        // 整个文件内容铺到终端没有意义，报规模即可
        assertThat(joined).doesNotContain(content);
        assertThat(joined).contains("字符，已省略");
    }

    @Test
    void headIsVividAndCommandIsBrightWhite() {
        Style style = Style.of(ColorDepth.ANSI256);

        // 首行是「谁做了什么」的主语：加粗 + 更鲜艳的类型色（执行 = 215）
        String head = ToolBlock.head("shell-exec", Map.of("command", "ls"), style, 40);
        assertThat(head).contains("\u001B[1m").contains("\u001B[38;5;215m");

        // 命令行是排查时的第一现场：高亮白，与上下的彩色、常规色都拉开
        assertThat(ToolBlock.command("ls", style, ENV, 0)).contains("\u001B[38;5;255m");
    }

    @Test
    void commandFallsBackToBrightWhiteOn16ColourTerminals() {
        assertThat(ToolBlock.command("ls", Style.of(ColorDepth.ANSI16), ENV, 0))
                .contains("\u001B[97m");
    }

    @Test
    void resultMarksSuccessAndFailure() {
        String ok = ToolBlock.result(true, "0.1s", PLAIN, 0, true);
        String fail = ToolBlock.result(false, "权限不足", PLAIN, 0, true);

        assertThat(ok).startsWith("✓ 完成").contains("0.1s");
        assertThat(fail).startsWith("✗ 失败").contains("权限不足");
    }

    @Test
    void resultDegradesMarkersWhenSymbolsUnavailable() {
        assertThat(ToolBlock.result(true, null, PLAIN, 0, false)).startsWith("[ok] 完成");
        assertThat(ToolBlock.result(false, null, PLAIN, 0, false)).startsWith("[fail] 失败");
    }

    @Test
    void bodyIsCappedAndReportsTheTotal() {
        String content = String.join("\n", "l1", "l2", "l3", "l4", "l5", "l6", "l7");

        List<String> lines = ToolBlock.body(content, PLAIN, 0, 3, true);

        assertThat(lines).hasSize(4);
        assertThat(lines.get(0)).contains("l1");
        assertThat(lines.get(3)).contains("输出共 7 行");
    }

    @Test
    void bodyWrapsInsteadOfTruncating() {
        // 回归：此前按终端宽度硬截断，后面的内容「完全看不到」
        String content = "a".repeat(50) + "-TAIL";

        List<String> lines = ToolBlock.body(content, PLAIN, 20, 10, false);

        assertThat(lines.size()).isGreaterThan(1);
        assertThat(join(lines)).contains("-TAIL");
        for (String line : lines) {
            assertThat(TextWidth.width(line)).isLessThanOrEqualTo(20);
        }
    }

    @Test
    void bodySkipsBlankOrMissingOutput() {
        assertThat(ToolBlock.body(null, PLAIN, 0, 5, true)).isEmpty();
        assertThat(ToolBlock.body("   ", PLAIN, 0, 5, true)).isEmpty();
    }

    @Test
    void detailCombinesDurationAndScale() {
        assertThat(ToolBlock.detail(true, "a\nb\nc", 1500)).isEqualTo("1.5s · 3 行");
        assertThat(ToolBlock.detail(true, "abc", 0)).isEqualTo("3 字符");
        assertThat(ToolBlock.detail(true, "", 200)).isEqualTo("0.2s · 无输出");
        // 不足 100ms 不显示耗时，免得出现「0.0s」这种噪音
        assertThat(ToolBlock.detail(true, "abc", 30)).isEqualTo("3 字符");
    }

    @Test
    void failureDetailIsSingleLineAndBounded() {
        String detail = ToolBlock.failureDetail("第一行\n第二行" + "x".repeat(200));

        assertThat(detail).doesNotContain("\n");
        assertThat(TextWidth.width(detail)).isLessThanOrEqualTo(80);
    }

    @Test
    void failureDetailCarriesTheDuration() {
        // 秒级失败多半是参数或环境问题，超时才失败往往是命令卡住——耗时本身是线索
        assertThat(ToolBlock.failureDetail("boom", 1500)).isEqualTo("1.5s · boom");
        assertThat(ToolBlock.failureDetail("boom", 30)).isEqualTo("boom");
    }

    @Test
    void failureBodyIsNotDimmed() {
        List<String> lines = ToolBlock.body("boom", Style.of(true), 0, 40, false);

        // 成功预览用弱化色，错误正文不着色，保证可读
        assertThat(lines.get(0)).isEqualTo("  boom");
    }
}
