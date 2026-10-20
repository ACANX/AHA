package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 中文字体回退判定（issue #382 / #395 真机）。
 *
 * <p>只测纯函数 {@link FontFallback#resolve(String, java.util.Collection, String)}：
 * 不依赖真机字体，也不需要 JavaFX 工具包 —— 判定与「取字体列表 / 加载内置字体」是分开的，
 * 这正是为了让候选链顺序、内置兜底、非 Linux 分支能被稳定覆盖。</p>
 *
 * @since 0.1.5
 */
class FontFallbackTest {

    @Test
    void linuxPicksFirstCandidateRegardlessOfInstalledOrder() {
        FontFallback.Resolution resolution = FontFallback.resolve("Linux",
                List.of("DejaVu Sans", "WenQuanYi Micro Hei", "Noto Sans CJK SC"), null);

        assertThat(resolution.family()).isEqualTo("Noto Sans CJK SC");
        assertThat(resolution.matches()).containsExactly("Noto Sans CJK SC", "WenQuanYi Micro Hei");
        assertThat(resolution.needsHint()).isFalse();
    }

    @Test
    void candidateMatchIsCaseInsensitiveAndKeepsInstalledName() {
        FontFallback.Resolution resolution =
                FontFallback.resolve("Linux", List.of("noto sans cjk sc"), null);

        assertThat(resolution.family()).isEqualTo("noto sans cjk sc");
    }

    @Test
    void linuxWithoutAnyCjkFontAsksForHint() {
        FontFallback.Resolution resolution = FontFallback.resolve("Linux",
                List.of("DejaVu Sans", "DejaVu Sans Mono"), null);

        assertThat(resolution.family()).isNull();
        assertThat(resolution.needsHint()).isTrue();
        assertThat(resolution.describe()).contains("方块");
        assertThat(FontFallback.missingFontHint()).contains(FontFallback.LINUX_INSTALL_COMMAND);
    }

    @Test
    void linuxFallsBackToBundledFontWhenSystemHasNone() {
        FontFallback.Resolution resolution = FontFallback.resolve("Linux",
                List.of("DejaVu Sans", "DejaVu Sans Mono"), "Droid Sans Fallback");

        assertThat(resolution.family()).isEqualTo("Droid Sans Fallback");
        assertThat(resolution.needsHint()).isFalse();
        assertThat(resolution.describe()).contains("内置兜底");
    }

    @Test
    void systemFontWinsOverBundledFont() {
        FontFallback.Resolution resolution = FontFallback.resolve("Linux",
                List.of("Noto Sans CJK SC"), "Droid Sans Fallback");

        assertThat(resolution.family()).isEqualTo("Noto Sans CJK SC");
        assertThat(resolution.describe()).contains("（系统）");
    }

    @Test
    void nonLinuxIgnoresBundledFont() {
        FontFallback.Resolution resolution = FontFallback.resolve("Windows 11",
                List.of("Segoe UI"), "Droid Sans Fallback");

        assertThat(resolution.family()).isNull();
        assertThat(resolution.needsHint()).isFalse();
    }

    @Test
    void nonLinuxKeepsDefaultFontEvenWhenCjkFontIsPresent() {
        FontFallback.Resolution resolution = FontFallback.resolve("Windows 11",
                List.of("Segoe UI", "Microsoft YaHei UI"), null);

        assertThat(resolution.family()).isNull();
        assertThat(resolution.needsHint()).isFalse();
        assertThat(resolution.matches()).containsExactly("Microsoft YaHei UI");
    }

    @Test
    void heuristicFallbackCatchesFamiliesOutsideTheCandidateList() {
        FontFallback.Resolution resolution = FontFallback.resolve("Linux",
                List.of("DejaVu Sans", "Noto Serif CJK TC"), null);

        assertThat(resolution.family()).isEqualTo("Noto Serif CJK TC");
    }

    @Test
    void monospaceCssUsesSelectedFamilyAsSingleName() {
        // JavaFX 的 -fx-font-family 只接受单个族名：选中 CJK 族时必须整串替换，
        // 不能拼逗号列表（列表会被当成一个不存在的族名）
        assertThat(FontFallback.monospaceCss("Noto Sans CJK SC")).isEqualTo("'Noto Sans CJK SC'");
        assertThat(FontFallback.monospaceCss("Droid Sans Fallback"))
                .isEqualTo("'Droid Sans Fallback'");
    }

    @Test
    void monospaceCssWithoutCjkKeepsLegacyChain() {
        assertThat(FontFallback.monospaceCss(null)).isEqualTo(FontFallback.MONOSPACE_CSS);
        assertThat(FontFallback.monospaceCss("  ")).isEqualTo(FontFallback.MONOSPACE_CSS);
    }

    @Test
    void nullAndBlankInputsDoNotThrow() {
        assertThat(FontFallback.resolve(null, null, null).needsHint()).isFalse();
        assertThat(FontFallback.resolve("Linux", List.of("", "   "), null).needsHint()).isTrue();
    }

    @Test
    void bundledFontResourceIsShipped() {
        // 资源缺失时 Linux 上仍会方块；守卫直接断言 classpath 里有这份字体
        assertThat(FontFallback.class.getResource(FontFallback.BUNDLED_RESOURCE)).isNotNull();
    }
}
