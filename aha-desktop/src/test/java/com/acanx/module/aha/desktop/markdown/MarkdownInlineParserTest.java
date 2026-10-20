package com.acanx.module.aha.desktop.markdown;

import com.acanx.module.aha.desktop.markdown.MarkdownInline.Code;
import com.acanx.module.aha.desktop.markdown.MarkdownInline.Em;
import com.acanx.module.aha.desktop.markdown.MarkdownInline.Link;
import com.acanx.module.aha.desktop.markdown.MarkdownInline.Strike;
import com.acanx.module.aha.desktop.markdown.MarkdownInline.Strong;
import com.acanx.module.aha.desktop.markdown.MarkdownInline.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MarkdownInlineParser} 测试（issue #414 / tracking #408 S1.2）。
 *
 * <p>重点在**误判**：`2 * 3 * 4`、`snake_case_name`、4 个连续定界符、未闭合定界符都不能被
 * 当成强调；这些规则与 CLI {@code InlineScanner} 共享同一份样本（见
 * {@code MarkdownSemanticsSamples}）。</p>
 *
 * @since 0.2.0
 */
class MarkdownInlineParserTest {

    private final MarkdownInlineParser parser = new MarkdownInlineParser();

    @Test
    void plainTextBecomesASingleTextNode() {
        assertThat(parser.parse("普通文本")).containsExactly(new Text("普通文本"));
    }

    @Test
    void codeSpanIsNotParsedFurther() {
        assertThat(parser.parse("`**x**`")).containsExactly(new Code("**x**"));
    }

    @Test
    void boldItalicAndBoth() {
        assertThat(parser.parse("**粗**")).containsExactly(new Strong(List.of(new Text("粗"))));
        assertThat(parser.parse("*斜*")).containsExactly(new Em(List.of(new Text("斜"))));
        assertThat(parser.parse("***粗斜***"))
                .containsExactly(new Strong(List.of(new Em(List.of(new Text("粗斜"))))));
    }

    @Test
    void underscoreIsAlsoEmphasis() {
        assertThat(parser.parse("_斜_")).containsExactly(new Em(List.of(new Text("斜"))));
        assertThat(parser.parse("__粗__")).containsExactly(new Strong(List.of(new Text("粗"))));
    }

    @Test
    void strikeAndLink() {
        assertThat(parser.parse("~~删~~")).containsExactly(new Strike(List.of(new Text("删"))));
        assertThat(parser.parse("[文档](https://x/y)"))
                .containsExactly(new Link("文档", "https://x/y"));
    }

    @Test
    void multiplicationIsNotEmphasis() {
        assertThat(parser.parse("2 * 3 * 4")).containsExactly(new Text("2 * 3 * 4"));
        assertThat(parser.parse("a * b")).containsExactly(new Text("a * b"));
    }

    @Test
    void underscoreInsideIdentifierIsNotEmphasis() {
        assertThat(parser.parse("snake_case_name")).containsExactly(new Text("snake_case_name"));
        assertThat(parser.parse("a_b_c")).containsExactly(new Text("a_b_c"));
    }

    @Test
    void fourOrMoreDelimitersAreLiteral() {
        assertThat(parser.parse("****粗****")).containsExactly(new Text("****粗****"));
        assertThat(parser.parse("前缀 ****粗**** 后缀")).containsExactly(new Text("前缀 ****粗**** 后缀"));
    }

    @Test
    void unclosedDelimiterStaysLiteral() {
        assertThat(parser.parse("*斜")).containsExactly(new Text("*斜"));
        assertThat(parser.parse("**粗")).containsExactly(new Text("**粗"));
    }

    @Test
    void nestedEmphasisIsParsedRecursively() {
        assertThat(parser.parse("**粗 *斜* 体**"))
                .containsExactly(new Strong(List.of(
                        new Text("粗 "), new Em(List.of(new Text("斜"))), new Text(" 体"))));
    }

    @Test
    void emphasisMustNotStartOrEndWithSpace() {
        assertThat(parser.parse("*斜 *")).containsExactly(new Text("*斜 *"));
        assertThat(parser.parse("** 粗 **")).containsExactly(new Text("** 粗 **"));
    }

    @Test
    void textAroundInlineElementsIsMerged() {
        assertThat(parser.parse("前 **粗** 后"))
                .containsExactly(new Text("前 "), new Strong(List.of(new Text("粗"))), new Text(" 后"));
    }
}
