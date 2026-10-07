package com.acanx.module.aha.cli.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InlineScanner} 测试：行内代码与强调的歧义判定。
 *
 * @since 0.1.0
 */
class InlineScannerTest {

    private static final String ESC = "\u001B[";
    private static final Style PLAIN = Style.of(false);
    private static final Style COLOR = Style.of(true);

    private static String render(String text) {
        return InlineScanner.render(text, PLAIN);
    }

    private static String renderColored(String text) {
        return InlineScanner.render(text, COLOR);
    }

    @Test
    void plainTextIsUnchanged() {
        assertThat(render("普通文本，没有标记")).isEqualTo("普通文本，没有标记");
        assertThat(render("")).isEmpty();
    }

    @Test
    void inlineCodeDropsBackticks() {
        assertThat(render("使用 `AhaConfig` 配置")).isEqualTo("使用 AhaConfig 配置");
        assertThat(renderColored("`x`")).isEqualTo(ESC + "32mx" + ESC + "0m");
    }

    @Test
    void unclosedBacktickStaysLiteral() {
        assertThat(render("使用 `AhaConfig")).isEqualTo("使用 `AhaConfig");
    }

    @Test
    void boldAndItalicAndBoth() {
        assertThat(render("**粗**")).isEqualTo("粗");
        assertThat(render("*斜*")).isEqualTo("斜");
        assertThat(render("***粗斜***")).isEqualTo("粗斜");

        assertThat(renderColored("**粗**")).isEqualTo(ESC + "1m粗" + ESC + "0m");
        assertThat(renderColored("*斜*")).isEqualTo(ESC + "3m斜" + ESC + "0m");
        assertThat(renderColored("***粗斜***")).isEqualTo(ESC + "1m" + ESC + "3m粗斜" + ESC + "0m");
    }

    @Test
    void underscoreIsAlsoEmphasis() {
        assertThat(render("_斜_")).isEqualTo("斜");
        assertThat(render("__粗__")).isEqualTo("粗");
    }

    @Test
    void multiplicationIsNotMistakenForEmphasis() {
        // 开定界符后跟空白 → 不是强调。这是最容易误判的情形
        assertThat(render("2 * 3 * 4")).isEqualTo("2 * 3 * 4");
        assertThat(render("a * b")).isEqualTo("a * b");
        assertThat(render("** 粗 **")).isEqualTo("** 粗 **");
    }

    @Test
    void underscoreInsideIdentifierIsNotEmphasis() {
        // a_b_c 是标识符，不是强调
        assertThat(render("a_b_c")).isEqualTo("a_b_c");
        assertThat(render("snake_case_name")).isEqualTo("snake_case_name");
        // 闭定界符右侧紧跟字母数字时也不成立
        assertThat(render("_a_b")).isEqualTo("_a_b");
    }

    @Test
    void underscoreWorksWhenSurroundedBySpaces() {
        assertThat(render("前缀 _斜_ 后缀")).isEqualTo("前缀 斜 后缀");
    }

    @Test
    void emphasisMustNotStartOrEndWithSpace() {
        // 闭定界符前是空白 → 不成对，整段保持字面量
        assertThat(render("*斜 *")).isEqualTo("*斜 *");
    }

    @Test
    void fourOrMoreDelimitersAreLiteral() {
        assertThat(render("****粗****")).isEqualTo("****粗****");
    }

    @Test
    void nestedEmphasisIsRenderedRecursively() {
        // 粗体内部的斜体也会被渲染，而不是留下字面的 *
        assertThat(renderColored("**粗 *斜* 体**"))
                .isEqualTo(ESC + "1m粗 " + ESC + "3m斜" + ESC + "0m 体" + ESC + "0m");
    }

    @Test
    void codeSpanInsideEmphasisIsNotReinterpreted() {
        assertThat(renderColored("**`code`**"))
                .isEqualTo(ESC + "1m" + ESC + "32mcode" + ESC + "0m" + ESC + "0m");
    }

    @Test
    void incompleteDelimiterReportsNeedForMoreInput() {
        // 单个 * 无法判定：可能是 *斜* 的开头，也可能是字面量
        InlineScanner.Context context = new InlineScanner.Context(PLAIN, 200, false, 0);
        assertThat(InlineScanner.parse("*", 0, 0, context)).isNull();
        assertThat(InlineScanner.parse("**", 0, 0, context)).isNull();
        assertThat(InlineScanner.parse("*a", 0, 0, context)).isNull();
        // 完整文本场景下同一输入按字面量处理（render 内部把 null 当字面量）
        assertThat(InlineScanner.render("*a", PLAIN)).isEqualTo("*a");
        assertThat(InlineScanner.render("未闭合 `code", PLAIN)).isEqualTo("未闭合 `code");
    }

    @Test
    void nextDelimiterFindsAllThreeKinds() {
        assertThat(InlineScanner.nextDelimiter("ab`c", 0)).isEqualTo(2);
        assertThat(InlineScanner.nextDelimiter("ab*c", 0)).isEqualTo(2);
        assertThat(InlineScanner.nextDelimiter("ab_c", 0)).isEqualTo(2);
        assertThat(InlineScanner.nextDelimiter("abc", 0)).isEqualTo(-1);
    }
}
