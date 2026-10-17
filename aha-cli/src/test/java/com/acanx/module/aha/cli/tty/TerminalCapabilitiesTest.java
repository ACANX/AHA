package com.acanx.module.aha.cli.tty;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TerminalCapabilities} 测试：能力探测与降级判定。
 *
 * @since 0.1.0
 */
class TerminalCapabilitiesTest {

    private static Map<String, String> env(String... pairs) {
        Map<String, String> values = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            values.put(pairs[i], pairs[i + 1]);
        }
        return values;
    }

    @Test
    void dumbTerminalSupportsNoColor() {
        assertThat(TerminalCapabilities.detectDepth(env("TERM", "dumb")))
                .isEqualTo(TerminalCapabilities.ColorDepth.NONE);
    }

    @Test
    void colorTermTruecolorWins() {
        assertThat(TerminalCapabilities.detectDepth(
                env("TERM", "xterm-256color", "COLORTERM", "truecolor")))
                .isEqualTo(TerminalCapabilities.ColorDepth.TRUECOLOR);
    }

    @Test
    void windowsTerminalIsDetectedWithoutTerm() {
        // Windows 传统环境 TERM 常为空，只能靠控制台标识判断
        assertThat(TerminalCapabilities.detectDepth(env("WT_SESSION", "0f7a")))
                .isEqualTo(TerminalCapabilities.ColorDepth.TRUECOLOR);
        assertThat(TerminalCapabilities.detectDepth(env("ConEmuANSI", "ON")))
                .isEqualTo(TerminalCapabilities.ColorDepth.TRUECOLOR);
        assertThat(TerminalCapabilities.detectDepth(env("ANSICON", "1")))
                .isEqualTo(TerminalCapabilities.ColorDepth.TRUECOLOR);
    }

    @Test
    void termIsMoreAuthoritativeThanTerminalBrand() {
        // Tabby / Xshell 等会设 TERM_PROGRAM，但色彩能力应以 TERM 为准：
        // TERM 说 256 色就是 256 色，不因为品牌而假设真彩
        assertThat(TerminalCapabilities.detectDepth(
                env("TERM", "xterm-256color", "TERM_PROGRAM", "Tabby")))
                .isEqualTo(TerminalCapabilities.ColorDepth.ANSI256);
        assertThat(TerminalCapabilities.detectDepth(
                env("TERM", "xterm-256color", "COLORTERM", "truecolor")))
                .isEqualTo(TerminalCapabilities.ColorDepth.TRUECOLOR);
    }

    @Test
    void multiplexersFallBackTo256Colors() {
        assertThat(TerminalCapabilities.detectDepth(env("TERM", "screen-256color")))
                .isEqualTo(TerminalCapabilities.ColorDepth.ANSI256);
        assertThat(TerminalCapabilities.detectDepth(env("TERM", "tmux-256color")))
                .isEqualTo(TerminalCapabilities.ColorDepth.ANSI256);
    }

    @Test
    void plainXtermIsDetectedAs16Colors() {
        assertThat(TerminalCapabilities.detectDepth(env("TERM", "xterm")))
                .isEqualTo(TerminalCapabilities.ColorDepth.ANSI16);
        // 环境变量全空时宁可保守：16 色比“猜成真彩”安全
        assertThat(TerminalCapabilities.detectDepth(env()))
                .isEqualTo(TerminalCapabilities.ColorDepth.ANSI16);
    }

    @Test
    void thirdPartyTerminalsWithoutTermFallBackToConsoleMarkers() {
        // Windows 传统控制台 TERM 为空，只能靠标识判断
        assertThat(TerminalCapabilities.detectDepth(env("TERM", "", "TERM_PROGRAM", "Tabby")))
                .isEqualTo(TerminalCapabilities.ColorDepth.TRUECOLOR);
        assertThat(TerminalCapabilities.detectDepth(env("TERM", "", "WT_SESSION", "0f7a")))
                .isEqualTo(TerminalCapabilities.ColorDepth.TRUECOLOR);
    }

    @Test
    void coloredRequiresTtyAndDepth() {
        TerminalCapabilities tty = new TerminalCapabilities(
                true, TerminalCapabilities.ColorDepth.ANSI16, 80, true, "");
        TerminalCapabilities piped = new TerminalCapabilities(
                false, TerminalCapabilities.ColorDepth.TRUECOLOR, 80, false, "");
        TerminalCapabilities noColor = new TerminalCapabilities(
                true, TerminalCapabilities.ColorDepth.NONE, 80, true, "");

        assertThat(tty.colored()).isTrue();
        // 管道输出即便终端支持颜色也要关闭，否则会吐出 ANSI 转义垃圾
        assertThat(piped.colored()).isFalse();
        assertThat(noColor.colored()).isFalse();
    }

    @Test
    void noColorDetectionDisablesDepth() {
        assertThat(TerminalCapabilities.detect(true).depth())
                .isEqualTo(TerminalCapabilities.ColorDepth.NONE);
        assertThat(TerminalCapabilities.detect(true).colored()).isFalse();
    }

    @Test
    void withersAdjustTtyAndWidth() {
        TerminalCapabilities base = new TerminalCapabilities(
                false, TerminalCapabilities.ColorDepth.ANSI256, 0, false, "test");

        assertThat(base.withTty(true).tty()).isTrue();
        assertThat(base.withTty(true).colored()).isTrue();
        assertThat(base.withTty(true).withWidth(120).width()).isEqualTo(120);
        assertThat(base.withTty(true).withWidth(-1).width()).isZero();
    }

    @Test
    void descriptionSummarizesDetectionInputs() {
        TerminalCapabilities capabilities = new TerminalCapabilities(
                true, TerminalCapabilities.ColorDepth.ANSI16, 80, true, "TERM=xterm depth=ANSI16");
        assertThat(capabilities.description()).contains("TERM=xterm");
        // 粘贴能力也要出现在摘要里：真机上「多行粘贴被逐行提交」时，
        // 看启动日志就能核对探测结果，不用去猜
        assertThat(TerminalCapabilities.detect(false).description()).contains("bracketedPaste=");
    }

    @Test
    void bracketedPasteIsAssumedUnlessWeCanTellItIsMissing() {
        // 非 Windows：xterm 家族及其衍生都支持，一律视为支持 —— 宁可少提示，不误提示
        assertThat(TerminalCapabilities.detectBracketedPaste(
                "Linux", Map.of("TERM", "xterm-256color"))).isTrue();
        assertThat(TerminalCapabilities.detectBracketedPaste("Mac OS X", Map.of())).isTrue();
        // Windows Terminal：TERM 常为空，靠 WT_SESSION 认出来
        assertThat(TerminalCapabilities.detectBracketedPaste(
                "Windows 11", Map.of("WT_SESSION", "3c4d5e6f"))).isTrue();
        // mintty / Git Bash：TERM 有值
        assertThat(TerminalCapabilities.detectBracketedPaste(
                "Windows 11", Map.of("TERM", "xterm-256color"))).isTrue();
        // 传统控制台 conhost：既无 TERM 也无 WT_SESSION ⇒ 多行粘贴会被逐行提交，
        // 这正是 issue #75 的场景，必须判成「不支持」以便给出替代入口
        assertThat(TerminalCapabilities.detectBracketedPaste("Windows 11", Map.of())).isFalse();
        // dumb 终端
        assertThat(TerminalCapabilities.detectBracketedPaste(
                "Linux", Map.of("TERM", "dumb"))).isFalse();
    }
}
