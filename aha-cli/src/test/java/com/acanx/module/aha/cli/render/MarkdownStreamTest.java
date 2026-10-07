package com.acanx.module.aha.cli.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MarkdownStream} 测试：流式 Markdown 轻量渲染。
 *
 * <p>无颜色时断言结构（标记是否被丢弃 / 是否保留），有颜色时断言样式序列。</p>
 *
 * @since 0.1.0
 */
class MarkdownStreamTest {

    private static final String ESC = "\u001B[";

    private static MarkdownStream plain() {
        return new MarkdownStream(Style.of(false), true, 0);
    }

    private static MarkdownStream colored() {
        return new MarkdownStream(Style.of(true), true, 0);
    }

    @Test
    void plainTextPassesThroughUnchanged() {
        assertThat(plain().feed("普通段落，没有标记。\n")).isEqualTo("普通段落，没有标记。\n");
    }

    @Test
    void headingMarkerIsKeptOnlyWhenColourIsOff() {
        // 无色时 `#` 是唯一的层级信号，必须保留（有色时反而要去掉，见 headingIsColouredByLevel）
        assertThat(plain().feed("## 标题\n正文\n")).isEqualTo("## 标题\n正文\n");
        assertThat(plain().feed("###### 六级\n")).isEqualTo("###### 六级\n");
    }

    @Test
    void headingIsColouredByLevel() {
        // 有色时不打 `#` 标记：颜色与字重已说明级别，留着只是噪音
        assertThat(colored().feed("# 标题\n"))
                .isEqualTo(ESC + "1m" + ESC + "4m" + ESC + "96m标题" + ESC + "0m\n");
        assertThat(colored().feed("## 标题\n"))
                .isEqualTo(ESC + "1m" + ESC + "96m标题" + ESC + "0m\n");
        assertThat(colored().feed("### 标题\n"))
                .isEqualTo(ESC + "1m" + ESC + "36m标题" + ESC + "0m\n");
        assertThat(colored().feed("#### 标题\n"))
                .isEqualTo(ESC + "36m标题" + ESC + "0m\n");
        assertThat(colored().feed("##### 标题\n"))
                .isEqualTo(ESC + "2m" + ESC + "36m标题" + ESC + "0m\n");
        assertThat(colored().feed("###### 标题\n"))
                .isEqualTo(ESC + "2m" + ESC + "3m" + ESC + "36m标题" + ESC + "0m\n");
    }

    @Test
    void everyHeadingLevelLooksDifferent() {
        // 回归：此前三级只有加粗、四级往后全是同一种青色，三级之后等于没渲染
        java.util.Set<String> styles = new java.util.HashSet<>();
        for (int level = 1; level <= 6; level++) {
            styles.add(colored().feed("#".repeat(level) + " 标题\n"));
        }

        assertThat(styles).hasSize(6);
    }

    @Test
    void hashWithoutSpaceIsNotAHeading() {
        assertThat(plain().feed("#hashtag\n")).isEqualTo("#hashtag\n");
        // 超过 6 个 # 也不构成标题
        assertThat(plain().feed("####### x\n")).isEqualTo("####### x\n");
    }

    @Test
    void unorderedListUsesConfiguredBullet() {
        assertThat(plain().feed("- 第一项\n")).isEqualTo("• 第一项\n");
        assertThat(plain().feed("* 第二项\n")).isEqualTo("• 第二项\n");
        assertThat(plain().feed("+ 第三项\n")).isEqualTo("• 第三项\n");
        // 编码不支持 • 时退回原符号
        MarkdownStream ascii = new MarkdownStream(Style.of(false), false, 0);
        assertThat(ascii.feed("- 第一项\n")).isEqualTo("- 第一项\n");
    }

    @Test
    void orderedListKeepsItsNumber() {
        assertThat(plain().feed("1. 第一项\n10. 第十项\n")).isEqualTo("1. 第一项\n10. 第十项\n");
    }

    @Test
    void horizontalRuleAndQuoteAreMuted() {
        // --- 与 *** 是分隔线，> 是引用；两者都只弱化，不改变文本
        assertThat(plain().feed("---\n")).isEqualTo("---\n");
        assertThat(plain().feed("***\n")).isEqualTo("***\n");
        assertThat(plain().feed("> 引用\n")).isEqualTo("> 引用\n");

        assertThat(colored().feed("> 引用\n")).contains(ESC + "2m").contains("引用");
    }

    @Test
    void fenceContentIsEmittedVerbatim() {
        String markdown = "```java\n# 不是标题\n- 不是列表\n`code`\n```\n";
        // 围栏内部原样输出：代码里的标记不能被当成 Markdown，否则读者会被误导
        assertThat(plain().feed(markdown))
                .isEqualTo("```java\n# 不是标题\n- 不是列表\n`code`\n```\n");
    }

    @Test
    void textAfterFenceIsParsedAgain() {
        assertThat(plain().feed("```\nx\n```\n## 标题\n")).isEqualTo("```\nx\n```\n## 标题\n");
    }

    @Test
    void backticksInsideFenceDoNotCloseIt() {
        // 代码块里的一行 `code` 不该被当成结束围栏
        assertThat(plain().feed("```\n`code`\n# 仍是代码\n```\n## 标题\n"))
                .isEqualTo("```\n`code`\n# 仍是代码\n```\n## 标题\n");
    }

    @Test
    void inlineCodeDropsBackticks() {
        assertThat(plain().feed("使用 `AhaConfig` 配置\n")).isEqualTo("使用 AhaConfig 配置\n");
    }

    @Test
    void inlineCodeIsStyledWhenColored() {
        assertThat(colored().feed("使用 `AhaConfig` 配置\n"))
                .isEqualTo("使用 " + ESC + "32mAhaConfig" + ESC + "0m 配置\n");
    }

    @Test
    void unclosedInlineCodeRestoresBacktick() {
        // 未闭合时把反引号还回去，保证文本不失真
        MarkdownStream stream = plain();
        assertThat(stream.feed("使用 `AhaConfig")).isEqualTo("使用 ");
        assertThat(stream.feed("\n")).isEqualTo("`AhaConfig\n");
    }

    @Test
    void overlongInlineCodeFallsBackToPlainText() {
        // 防止一个未闭合的反引号把后续内容一直扣住
        String longCode = "x".repeat(250);
        MarkdownStream stream = plain();
        String head = stream.feed("`" + longCode);
        String tail = stream.flush();
        assertThat(head + tail).isEqualTo("`" + longCode);
    }

    @Test
    void emphasisSplitAcrossChunksIsRecognized() {
        // 流式输出按分片到达：强调定界符可能被切开
        MarkdownStream stream = plain();
        assertThat(stream.feed("**")).isEmpty();
        assertThat(stream.feed("粗")).isEmpty();
        assertThat(stream.feed("**")).isEqualTo("粗");
    }

    @Test
    void emphasisIsStyledWhenColored() {
        assertThat(colored().feed("**粗**\n")).isEqualTo(ESC + "1m粗" + ESC + "0m\n");
    }

    @Test
    void unclosedEmphasisFallsBackToLiteralAtLineEnd() {
        assertThat(plain().feed("**粗\n")).isEqualTo("**粗\n");
    }

    @Test
    void emphasisDoesNotSpanLines() {
        assertThat(plain().feed("*斜\n斜*\n")).isEqualTo("*斜\n斜*\n");
    }

    @Test
    void literalAsterisksStreamThroughUnchanged() {
        assertThat(plain().feed("2 * 3 * 4\n")).isEqualTo("2 * 3 * 4\n");
    }

    @Test
    void underscoreInsideIdentifierIsNotEmphasis() {
        assertThat(plain().feed("snake_case_name\n")).isEqualTo("snake_case_name\n");
    }

    @Test
    void emphasisCoexistsWithListMarker() {
        assertThat(plain().feed("- **粗** 项\n")).isEqualTo("• 粗 项\n");
    }

    @Test
    void asterisksInsideFenceAreNotEmphasis() {
        assertThat(plain().feed("```\n**not bold**\n*not italic*\n```\n"))
                .isEqualTo("```\n**not bold**\n*not italic*\n```\n");
    }

    @Test
    void overlongEmphasisFallsBackToPlainText() {
        // 防止一个未闭合的定界符把后续内容一直扣住
        String body = "x".repeat(250);
        MarkdownStream stream = plain();
        String head = stream.feed("*" + body);
        String tail = stream.flush();
        assertThat(head + tail).isEqualTo("*" + body);
    }

    @Test
    void markersSplitAcrossChunksAreStillRecognized() {
        // 流式输出按分片到达：块级判定必须跨分片成立
        MarkdownStream stream = plain();
        // 判定完成即吐出标记，正文分片照常
        assertThat(stream.feed("## ")).isEqualTo("## ");
        assertThat(stream.feed("Ti")).isEqualTo("Ti");
        assertThat(stream.feed("tle\n")).isEqualTo("tle\n");

        MarkdownStream list = plain();
        list.feed("-");
        assertThat(list.feed(" 项\n")).isEqualTo("• 项\n");
    }

    @Test
    void everyLineIsEvaluatedIndependently() {
        assertThat(plain().feed("# A\n- B\n普通\n")).isEqualTo("# A\n• B\n普通\n");
    }

    @Test
    void flushEmitsPendingContentWithoutNewline() {
        MarkdownStream stream = plain();
        assertThat(stream.feed("未完")).isEqualTo("未完");
        assertThat(stream.flush()).isEmpty();
        // 只有标记时，flush 也要把缓冲吐出来
        MarkdownStream hanging = plain();
        hanging.feed("#");
        assertThat(hanging.flush()).isEqualTo("#");
    }
    @Test
    void tableIsRenderedWhenTheBlockEnds() {
        MarkdownStream stream = plain();

        // 表格必须整体结算才能算列宽，因此 feed 阶段不吐内容
        String during = stream.feed("| 字段 | 说明 |\n|---|---|\n| Name | 名称 |\n");
        String after = stream.flush();

        assertThat(during).isEmpty();
        assertThat(after).contains("┌").contains("│").contains("字段").contains("名称")
                .contains("└");
    }

    @Test
    void tableEndsAtTheFirstNonTableLine() {
        MarkdownStream stream = plain();

        String out = stream.feed("| a | b |\n|---|---|\n| 1 | 2 |\n普通段落\n");

        assertThat(out).contains("┌").contains("普通段落");
        // 非表格行必须原样跟在表格后面，不能被吞掉
        assertThat(out.indexOf("普通段落")).isGreaterThan(out.indexOf("┌"));
    }

    @Test
    void pipeInsideAFenceIsNotATable() {
        MarkdownStream stream = plain();

        assertThat(stream.feed("```\n| a | b |\n```\n")).isEqualTo("```\n| a | b |\n```\n");
    }

    @Test
    void linkKeepsBothTextAndAddress() {
        assertThat(plain().feed("见 [文档](https://x/y)。\n")).isEqualTo("见 文档 <https://x/y>。\n");
    }

    @Test
    void redundantLinkAddressIsNotRepeated() {
        assertThat(plain().feed("[https://x/y](https://x/y)\n")).isEqualTo("https://x/y\n");
    }

    @Test
    void bracketWithoutParenthesisStaysLiteral() {
        // 数组下标这类写法不能被误判为链接
        assertThat(plain().feed("取值 a[0] 即可\n")).isEqualTo("取值 a[0] 即可\n");
    }

}
