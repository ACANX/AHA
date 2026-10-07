package com.acanx.module.aha.cli.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TextWidth} 测试：显示宽度计算。
 *
 * @since 0.1.0
 */
class TextWidthTest {

    @Test
    void asciiCharactersAreOneColumn() {
        assertThat(TextWidth.width("abc")).isEqualTo(3);
        assertThat(TextWidth.width("a b")).isEqualTo(3);
        assertThat(TextWidth.width("")).isZero();
        assertThat(TextWidth.width(null)).isZero();
    }

    @Test
    void cjkCharactersAreTwoColumns() {
        // 这正是不能用 String.length() 折行的原因：4 个汉字 = 4 个 char = 8 列
        assertThat(TextWidth.width("你好世界")).isEqualTo(8);
        assertThat("你好世界".length()).isEqualTo(4);
    }

    @Test
    void fullWidthPunctuationIsWide() {
        assertThat(TextWidth.charWidth('，')).isEqualTo(2);
        assertThat(TextWidth.charWidth('。')).isEqualTo(2);
        assertThat(TextWidth.charWidth('！')).isEqualTo(2);
    }

    @Test
    void controlAndCombiningCharactersTakeNoSpace() {
        assertThat(TextWidth.charWidth('\n')).isZero();
        assertThat(TextWidth.charWidth('\t')).isZero();
        assertThat(TextWidth.charWidth(0x0301)).isZero();
        assertThat(TextWidth.charWidth(0x200B)).isZero();
        assertThat(TextWidth.charWidth(0xFE0F)).isZero();
    }

    @Test
    void emojiAreWideIncludingSurrogatePairs() {
        assertThat(TextWidth.charWidth(0x1F600)).isEqualTo(2);
        // 代理对占两个 char，但只算一个码点
        assertThat(TextWidth.width("\uD83D\uDE00")).isEqualTo(2);
    }

    @Test
    void mixedContentSumsBothWidths() {
        assertThat(TextWidth.width("a你b好")).isEqualTo(1 + 2 + 1 + 2);
    }
    @Test
    void wrapBreaksByDisplayWidth() {
        // 折行必须按显示宽度：中文占 2 列，按字符数算必然错行
        java.util.List<String> lines = TextWidth.wrap("中文中文中文", 7);

        // 每行最多 7 列，即 3 个汉字（6 列）——第 4 个会超到 8 列，必须换行
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo("中文中");
        assertThat(lines.get(1)).isEqualTo("文中文");
        for (String line : lines) {
            assertThat(TextWidth.width(line)).isLessThanOrEqualTo(7);
        }
    }

    @Test
    void wrapKeepsEveryCharacter() {
        String text = "powershell -NoProfile -Command \"Get-ChildItem -Recurse\"";

        java.util.List<String> lines = TextWidth.wrap(text, 10);

        assertThat(String.join("", lines)).isEqualTo(text);
    }

    @Test
    void wrapWithoutLimitReturnsSingleLine() {
        assertThat(TextWidth.wrap("abc", 0)).containsExactly("abc");
    }

    @Test
    void wrapHandlesEmptyInput() {
        assertThat(TextWidth.wrap(null, 10)).isEmpty();
        assertThat(TextWidth.wrap("", 10)).isEmpty();
    }

    @Test
    void wrapKeepsExistingNewlines() {
        assertThat(TextWidth.wrap("a\nb", 10)).containsExactly("a", "b");
    }

}
