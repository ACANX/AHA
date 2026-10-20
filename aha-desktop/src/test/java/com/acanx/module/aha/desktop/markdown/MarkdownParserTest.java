package com.acanx.module.aha.desktop.markdown;

import com.acanx.module.aha.desktop.markdown.MarkdownBlock.Align;
import com.acanx.module.aha.desktop.markdown.MarkdownBlock.Fence;
import com.acanx.module.aha.desktop.markdown.MarkdownBlock.Heading;
import com.acanx.module.aha.desktop.markdown.MarkdownBlock.ListBlock;
import com.acanx.module.aha.desktop.markdown.MarkdownBlock.ListItem;
import com.acanx.module.aha.desktop.markdown.MarkdownBlock.Paragraph;
import com.acanx.module.aha.desktop.markdown.MarkdownBlock.Quote;
import com.acanx.module.aha.desktop.markdown.MarkdownBlock.Rule;
import com.acanx.module.aha.desktop.markdown.MarkdownBlock.TableBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MarkdownParser} 块级解析测试（issue #413 / tracking #408 S1.1）。
 *
 * <p>只断言 AST，不碰 JavaFX：解析器的价值就在于能在无头环境把结构钉死，
 * 渲染层（S1.5）再单独验节点。</p>
 *
 * @since 0.2.0
 */
class MarkdownParserTest {

    private final MarkdownParser parser = new MarkdownParser();

    @Test
    void parsesHeadingsOfAllLevels() {
        List<MarkdownBlock> blocks = parser.parse("# 一级\n###### 六级\n");

        assertThat(blocks).containsExactly(new Heading(1, "一级"), new Heading(6, "六级"));
    }

    @Test
    void sevenHashesIsNotAHeading() {
        // CommonMark：最多 6 级，7 个 `#` 按段落处理
        assertThat(parser.parse("####### 七级\n")).containsExactly(new Paragraph("####### 七级"));
    }

    @Test
    void parsesThreeKindsOfRules() {
        assertThat(parser.parse("---\n***\n___\n"))
                .containsExactly(new Rule(), new Rule(), new Rule());
    }

    @Test
    void parsesFenceWithLanguage() {
        List<MarkdownBlock> blocks = parser.parse("```java\nint a = 1;\n```\n");

        assertThat(blocks).containsExactly(new Fence("java", "int a = 1;"));
    }

    @Test
    void unclosedFenceRunsToTheEnd() {
        List<MarkdownBlock> blocks = parser.parse("```\nabc\n");

        assertThat(blocks).hasSize(1);
        assertThat(blocks.get(0)).isInstanceOf(Fence.class);
        assertThat(((Fence) blocks.get(0)).content()).contains("abc");
    }

    @Test
    void fenceContentIsNeverParsed() {
        List<MarkdownBlock> blocks = parser.parse("```\n# 不是标题\n**x**\n| a |\n```\n");

        assertThat(blocks).hasSize(1);
        Fence fence = (Fence) blocks.get(0);
        assertThat(fence.content()).contains("# 不是标题").contains("**x**").contains("| a |");
    }

    @Test
    void paragraphsAreSeparatedByBlankLines() {
        assertThat(parser.parse("第一行\n第二行\n\n第二段\n"))
                .containsExactly(new Paragraph("第一行\n第二行"), new Paragraph("第二段"));
    }

    @Test
    void parsesUnorderedListWithIndent() {
        List<MarkdownBlock> blocks = parser.parse("- 一\n  - 一.一\n- 二\n");

        assertThat(blocks).containsExactly(new ListBlock(false, 1, List.of(
                new ListItem(0, "一"), new ListItem(2, "一.一"), new ListItem(0, "二"))));
    }

    @Test
    void parsesOrderedListKeepingTheStartNumber() {
        assertThat(parser.parse("3. 三\n4. 四\n"))
                .containsExactly(new ListBlock(true, 3, List.of(
                        new ListItem(0, "三"), new ListItem(0, "四"))));
    }

    @Test
    void parsesNestedQuote() {
        List<MarkdownBlock> blocks = parser.parse("> 外层\n>\n> > 内层\n");

        assertThat(blocks).hasSize(1);
        Quote quote = (Quote) blocks.get(0);
        assertThat(quote.blocks()).containsExactly(
                new Paragraph("外层"),
                new Quote(List.of(new Paragraph("内层"))));
    }

    @Test
    void parsesTableWithAlignmentAndMissingCell() {
        List<MarkdownBlock> blocks = parser.parse("| a | b | c |\n|:---|:---:|---:|\n| 1 | 2 |\n");

        assertThat(blocks).containsExactly(new TableBlock(
                List.of("a", "b", "c"),
                List.of(Align.LEFT, Align.CENTER, Align.RIGHT),
                List.of(List.of("1", "2", ""))));
    }

    @Test
    void blankLineTerminatesTable() {
        // 与 #346 同语义：空行是表格边界，两张表各自成块
        List<MarkdownBlock> blocks = parser.parse("| a |\n|---|\n| 1 |\n\n| b |\n|---|\n| 2 |\n");

        assertThat(blocks).hasSize(2);
        assertThat(blocks.get(0)).isInstanceOf(TableBlock.class);
        assertThat(blocks.get(1)).isInstanceOf(TableBlock.class);
    }

    @Test
    void pipeWithoutSeparatorRowStaysParagraph() {
        // 没有分隔行就不是表格：两行都留在段落里，不吞内容
        assertThat(parser.parse("| a | b |\n没有分隔行\n"))
                .containsExactly(new Paragraph("| a | b |\n没有分隔行"));
    }

    @Test
    void mixedDocumentKeepsBlockOrder() {
        String markdown = """
                # 标题

                段落一。

                - 列表项

                | a |
                |---|
                | 1 |

                ```java
                int a = 1;
                ```
                """;

        assertThat(parser.parse(markdown)).containsExactly(
                new Heading(1, "标题"),
                new Paragraph("段落一。"),
                new ListBlock(false, 1, List.of(new ListItem(0, "列表项"))),
                new TableBlock(List.of("a"), List.of(Align.LEFT), List.of(List.of("1"))),
                new Fence("java", "int a = 1;"));
    }
}
