package com.acanx.module.aha.cli.tty;

import com.acanx.module.aha.common.exception.AhaException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Clipboard} 测试：候选命令的选择、结尾换行的处理、失败时的可行动消息。
 *
 * <p>「真的把系统剪贴板读出来」依赖具体桌面环境，不作为断言对象；
 * 执行链（启动 → 读 stdout → 判退出码 → 解码）用一条确定性的命令覆盖。</p>
 *
 * @since 0.1.0
 */
class ClipboardTest {

    @Test
    void windowsPrefersPowerShellAndForcesUtf8() {
        List<List<String>> candidates = Clipboard.candidates("Windows 11", Map.of(), null);

        assertThat(candidates).extracting(command -> command.get(0))
                .containsExactly("powershell", "pwsh");
        // -Raw 与 UTF-8 都不能少：前者保住换行，后者避免中文按控制台代码页编码后读成乱码
        assertThat(candidates.get(0).get(4))
                .contains("Get-Clipboard -Raw")
                .contains("UTF8");
    }

    @Test
    void macUsesPbpaste() {
        List<List<String>> candidates = Clipboard.candidates("Mac OS X", Map.of(), null);

        assertThat(candidates).containsExactly(List.of("pbpaste"));
    }

    @Test
    void linuxTriesWaylandThenX11() {
        List<List<String>> candidates = Clipboard.candidates("Linux", Map.of(), null);

        assertThat(candidates).extracting(command -> command.get(0))
                .containsExactly("wl-paste", "xclip", "xsel");
        // wl-paste 不加 --no-newline 会多补一个换行，粘贴内容每次都多一行
        assertThat(candidates.get(0)).containsExactly("wl-paste", "--no-newline");
    }

    @Test
    void overridePropertyReplacesPlatformDefaults() {
        List<List<String>> candidates = Clipboard.candidates("Windows 11", Map.of(), "  my-clip  get  ");

        assertThat(candidates).containsExactly(List.of("my-clip", "get"));
    }

    @Test
    void stripsAtMostOneTrailingNewline() {
        // 工具补的那个换行要去掉，但用户内容里本来的空行不能动
        assertThat(Clipboard.stripOneTrailingNewline("a\nb")).isEqualTo("a\nb");
        assertThat(Clipboard.stripOneTrailingNewline("a\r\n")).isEqualTo("a");
        assertThat(Clipboard.stripOneTrailingNewline("a\n\n")).isEqualTo("a\n");
        assertThat(Clipboard.stripOneTrailingNewline("")).isEmpty();
    }

    @Test
    void readRunsTheCommandAndDecodesItsOutput() {
        // 用 JVM 自己当「剪贴板命令」：验证执行链真的通（启动、读 stdout、判退出码）。
        // -version 走 stderr（已丢弃）⇒ 内容是空的，正好断言「读到了、但内容为空」
        Path java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")
                        ? "java.exe" : "java");
        Assumptions.assumeTrue(Files.isExecutable(java), "本机没有可执行的 java，跳过");

        String previous = System.getProperty(Clipboard.CLIPBOARD_PROPERTY);
        System.setProperty(Clipboard.CLIPBOARD_PROPERTY, java.toString() + " -version");
        try {
            Clipboard.Content content = Clipboard.read();
            assertThat(content.text()).isEmpty();
            assertThat(content.source()).contains("java");
        } finally {
            if (previous == null) {
                System.clearProperty(Clipboard.CLIPBOARD_PROPERTY);
            } else {
                System.setProperty(Clipboard.CLIPBOARD_PROPERTY, previous);
            }
        }
    }

    @Test
    void readReportsEveryCandidateInTheFailureMessage() {
        // 都会失败：命令不存在。错误消息必须把尝试过的手段与原因摆出来，
        // 否则用户看到一句「读不到剪贴板」无从下手（这一条正是 issue #75 要求的「可行动」）
        String previous = System.getProperty(Clipboard.CLIPBOARD_PROPERTY);
        System.setProperty(Clipboard.CLIPBOARD_PROPERTY, "aha-clipboard-must-not-exist-probe");
        try {
            assertThatThrownBy(Clipboard::read)
                    .isInstanceOf(AhaException.class)
                    .hasMessageContaining("读不到剪贴板")
                    .hasMessageContaining("aha-clipboard-must-not-exist-probe");
        } finally {
            if (previous == null) {
                System.clearProperty(Clipboard.CLIPBOARD_PROPERTY);
            } else {
                System.setProperty(Clipboard.CLIPBOARD_PROPERTY, previous);
            }
        }
    }
}
