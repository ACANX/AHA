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
    void imageIsParsedWithoutLoading() {
        assertThat(parser.parse("![图](https://x/y.png)"))
                .containsExactly(new MarkdownInline.Image("图", "https://x/y.png"));
        // 不是图片语法时按字面量，不做猜测
        assertThat(parser.parse("!不是图片")).containsExactly(new Text("!不是图片"));
        assertThat(parser.parse("![a]()")).containsExactly(new Text("![a]()"));
    }

    @Test
    void whitelistedHtmlIsParsed() {
        assertThat(parser.parse("上<br>下"))
                .containsExactly(new Text("上"), new MarkdownInline.Break(), new Text("下"));
        assertThat(parser.parse("<br/>")).containsExactly(new MarkdownInline.Break());
        assertThat(parser.parse("x<sub>2</sub>"))
                .containsExactly(new Text("x"), new MarkdownInline.Html("sub", List.of(new Text("2"))));
        assertThat(parser.parse("x<sup>2</sup>"))
                .containsExactly(new Text("x"), new MarkdownInline.Html("sup", List.of(new Text("2"))));
        assertThat(parser.parse("x<SUB>2</SUB>"))
                .containsExactly(new Text("x"), new MarkdownInline.Html("sub", List.of(new Text("2"))));
    }

    @Test
    void htmlOutsideWhitelistStaysLiteral() {
        // 不吞内容：script 标签与内部代码都留在文本里
        assertThat(parser.parse("<script>alert(1)</script>"))
                .containsExactly(new Text("<script>alert(1)</script>"));
        assertThat(parser.parse("<span style=\"color:red\">红</span>"))
                .containsExactly(new Text("<span style=\"color:red\">红</span>"));
        // 只有开标签没有闭合标签：同样是字面量
        assertThat(parser.parse("<sub>半截")).containsExactly(new Text("<sub>半截"));
    }

    @Test
    void bareUrlBecomesALink() {
        assertThat(parser.parse("见 https://example.com/a 结束"))
                .containsExactly(new Text("见 "),
                        new Link("https://example.com/a", "https://example.com/a"),
                        new Text(" 结束"));
        assertThat(parser.parse("http://x")).containsExactly(new Link("http://x", "http://x"));
    }

    @Test
    void bareUrlStopsAtChinesePunctuationAndTrailingPunctuation() {
        assertThat(parser.parse("访问 https://example.com。"))
                .containsExactly(new Text("访问 "), new Link("https://example.com", "https://example.com"),
                        new Text("。"));
        assertThat(parser.parse("见 https://example.com/a."))
                .containsExactly(new Text("见 "), new Link("https://example.com/a", "https://example.com/a"),
                        new Text("."));
    }

    @Test
    void urlInsideCodeSpanIsNotALink() {
        assertThat(parser.parse("`https://example.com`"))
                .containsExactly(new Code("https://example.com"));
    }

    @Test
    void textAroundInlineElementsIsMerged() {
        assertThat(parser.parse("前 **粗** 后"))
                .containsExactly(new Text("前 "), new Strong(List.of(new Text("粗"))), new Text(" 后"));
    }
}
