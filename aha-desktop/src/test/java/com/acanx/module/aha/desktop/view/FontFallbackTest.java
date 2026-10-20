package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 中文字体回退判定（issue #382）。
 *
 * <p>只测纯函数 {@link FontFallback#resolve(String, java.util.Collection)}：
 * 不依赖真机字体，也不需要 JavaFX 工具包 —— 判定与「取字体列表」是分开的，
 * 这正是为了让候选链顺序、缺失降级、非 Linux 分支能被稳定覆盖。</p>
 *
 * @since 0.1.5
 */
class FontFallbackTest {

    @Test
    void linuxPicksFirstCandidateRegardlessOfInstalledOrder() {
        FontFallback.Resolution resolution = FontFallback.resolve("Linux",
                List.of("DejaVu Sans", "WenQuanYi Micro Hei", "Noto Sans CJK SC"));

        assertThat(resolution.family()).isEqualTo("Noto Sans CJK SC");
        assertThat(resolution.matches()).containsExactly("Noto Sans CJK SC", "WenQuanYi Micro Hei");
        assertThat(resolution.needsHint()).isFalse();
    }

    @Test
    void candidateMatchIsCaseInsensitiveAndKeepsInstalledName() {
        FontFallback.Resolution resolution = FontFallback.resolve("Linux", List.of("noto sans cjk sc"));

        assertThat(resolution.family()).isEqualTo("noto sans cjk sc");
    }

    @Test
    void linuxWithoutAnyCjkFontAsksForHint() {
        FontFallback.Resolution resolution = FontFallback.resolve("Linux",
                List.of("DejaVu Sans", "DejaVu Sans Mono"));

        assertThat(resolution.family()).isNull();
        assertThat(resolution.needsHint()).isTrue();
        assertThat(resolution.describe()).contains("方块");
        assertThat(FontFallback.missingFontHint()).contains(FontFallback.LINUX_INSTALL_COMMAND);
    }

    @Test
    void nonLinuxKeepsDefaultFontEvenWhenCjkFontIsPresent() {
        FontFallback.Resolution resolution = FontFallback.resolve("Windows 11",
                List.of("Segoe UI", "Microsoft YaHei UI"));

        assertThat(resolution.family()).isNull();
        assertThat(resolution.needsHint()).isFalse();
        assertThat(resolution.matches()).containsExactly("Microsoft YaHei UI");
    }

    @Test
    void heuristicFallbackCatchesFamiliesOutsideTheCandidateList() {
        FontFallback.Resolution resolution = FontFallback.resolve("Linux",
                List.of("DejaVu Sans", "Noto Serif CJK TC"));

        assertThat(resolution.family()).isEqualTo("Noto Serif CJK TC");
    }

    @Test
    void monospaceChainPutsCjkBeforeGenericFamily() {
        assertThat(FontFallback.MONOSPACE_CSS).contains("'Noto Sans CJK SC'");
        // 通用族 monospace 总是存在；CJK 候选若排在它后面就永远不会被选中
        assertThat(FontFallback.MONOSPACE_CSS.indexOf("'Noto Sans CJK SC'"))
                .isLessThan(FontFallback.MONOSPACE_CSS.indexOf("monospace"));
    }

    @Test
    void nullAndBlankInputsDoNotThrow() {
        assertThat(FontFallback.resolve(null, null).needsHint()).isFalse();
        assertThat(FontFallback.resolve("Linux", List.of("", "   ")).needsHint()).isTrue();
    }
}
