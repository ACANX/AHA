package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Palette} 测试：语义配色与 {@code GUIDesign.md} 第 3.1 节一致。
 *
 * @since 0.2.0
 */
class PaletteTest {

    @Test
    void semanticColorsMatchDesignTable() {
        assertThat(Palette.READ).isEqualTo("#87AFD7");
        assertThat(Palette.WRITE).isEqualTo("#D7AF5F");
        assertThat(Palette.EXEC).isEqualTo("#FF875F");
        assertThat(Palette.NETWORK).isEqualTo("#5FD7D7");
        assertThat(Palette.OTHER).isEqualTo("#BCBCBC");
        assertThat(Palette.SUCCESS).isEqualTo("#7FD37F");
        assertThat(Palette.FAILURE).isEqualTo("#FF5F5F");
        assertThat(Palette.BLOCK_BACKGROUND).isEqualTo("#303030");
    }

    @Test
    void colorsAreSixDigitHex() {
        assertThat(Palette.READ).matches("#[0-9A-F]{6}");
        assertThat(Palette.FAILURE).matches("#[0-9A-F]{6}");
    }
}
