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
                true, TerminalCapabilities.ColorDepth.ANSI16, 80, "");
        TerminalCapabilities piped = new TerminalCapabilities(
                false, TerminalCapabilities.ColorDepth.TRUECOLOR, 80, "");
        TerminalCapabilities noColor = new TerminalCapabilities(
                true, TerminalCapabilities.ColorDepth.NONE, 80, "");

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
                false, TerminalCapabilities.ColorDepth.ANSI256, 0, "test");

        assertThat(base.withTty(true).tty()).isTrue();
        assertThat(base.withTty(true).colored()).isTrue();
        assertThat(base.withTty(true).withWidth(120).width()).isEqualTo(120);
        assertThat(base.withTty(true).withWidth(-1).width()).isZero();
    }

    @Test
    void descriptionSummarizesDetectionInputs() {
        TerminalCapabilities capabilities = new TerminalCapabilities(
                true, TerminalCapabilities.ColorDepth.ANSI16, 80, "TERM=xterm depth=ANSI16");
        assertThat(capabilities.description()).contains("TERM=xterm");
    }
}
