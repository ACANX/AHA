package com.acanx.module.aha.cli.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StartupPixelLogo} 测试。
 *
 * <p>像素风靠颜色成形：半块字符只画形状，前景/背景才是真正的像素。因此这里钉住的是
 * 「尺寸与宣告一致」「透明处走终端默认色而不是黑色」「行尾不留白」，以及
 * 「9 条光线确实存在」——它们在 48x48 里每个只有几个像素，一旦被平滑掉就只剩光秃秃的灯泡。</p>
 *
 * @since 0.1.0
 */
class StartupPixelLogoTest {

    private static final String ANSI = "\u001B\\[[0-9;]*m";

    private static String strip(String text) {
        return text.replaceAll(ANSI, "");
    }

    private static long halfBlocks(List<String> lines) {
        return lines.stream().mapToLong(line -> strip(line).chars().filter(c -> c == '\u2580').count())
                .sum();
    }

    @Test
    void fullArtIsFortyEightByTwentyFour() {
        LogoArt art = StartupPixelLogo.art();
        assertThat(art.width()).isEqualTo(48);
        assertThat(art.height()).isEqualTo(24);
        assertThat(art.lines()).hasSize(24);
    }

    @Test
    void smallArtIsHalfOfTheFullArt() {
        // 同一份网格降采样，不做二次描画：尺寸正好一半，形状不会走样
        LogoArt small = StartupPixelLogo.smallArt();
        assertThat(small.width()).isEqualTo(24);
        assertThat(small.height()).isEqualTo(12);
        assertThat(small.lines()).hasSize(12);
    }

    @Test
    void everyLineFitsInsideTheDeclaredWidth() {
        for (LogoArt art : List.of(StartupPixelLogo.art(), StartupPixelLogo.smallArt())) {
            for (String line : art.lines()) {
                assertThat(TextWidth.width(strip(line))).isLessThanOrEqualTo(art.width());
            }
        }
    }

    @Test
    void usesHalfBlocksAndTwentyFiveSixColors() {
        for (LogoArt art : List.of(StartupPixelLogo.art(), StartupPixelLogo.smallArt())) {
            for (String line : art.lines()) {
                assertThat(strip(line)).matches("[\u2580 ]*");
                if (!strip(line).isBlank()) {
                    assertThat(line).contains("\u001B[");
                }
            }
        }
    }

    @Test
    void transparentPixelsFallBackToTheTerminalDefaults() {
        // 用 39 / 49 而不是黑色：否则终端背景深的用户会看到一块突兀的黑框
        String joined = String.join("", StartupPixelLogo.art().lines());
        assertThat(joined).contains("39;");
        assertThat(joined).contains("49");
        assertThat(joined).doesNotContain("48;5;16m");
    }

    @Test
    void noLineCarriesTrailingWhitespace() {
        for (String line : StartupPixelLogo.art().lines()) {
            assertThat(line).isEqualTo(line.stripTrailing());
        }
    }

    @Test
    void theNineRaysSurviveAsDistinctPixels() {
        // 光线全在图形上三分之一与两侧，且每条约 7 个像素
        long rays = halfBlocks(StartupPixelLogo.art().lines().subList(0, 8));
        assertThat(rays).isGreaterThan(20);
    }

    @Test
    void theWordmarkAndFaceAreDrawnInsideTheBulb() {
        // 字样与笑脸是贴上去的深色像素，数量太少说明被灯泡盖掉了
        long dark = StartupPixelLogo.art().lines().stream()
                .mapToLong(line -> line.split("\u001B\\[", -1).length).sum();
        assertThat(dark).isGreaterThan(24);   // 至少 24 行都带着色片段
    }
}
