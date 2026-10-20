package com.acanx.module.aha.desktop.markdown;

import com.acanx.module.aha.desktop.markdown.MarkdownBlock.Fence;
import com.acanx.module.aha.desktop.markdown.MarkdownBlock.Paragraph;
import com.acanx.module.aha.desktop.markdown.MarkdownInline.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 不支持语法的兜底测试（issue #426 / tracking #408 S3.3）。
 *
 * <p>四类明确不做的扩展各一条：脚注、定义列表、数学公式、mermaid 围栏。
 * 断言的重点不是「渲染成什么」，而是**原文完整可见**——不支持可以，吞内容不行。</p>
 *
 * @since 0.2.0
 */
class MarkdownUnsupportedTest {

    private final MarkdownInlineParser inlineParser = new MarkdownInlineParser();

    private final MarkdownParser blockParser = new MarkdownParser();

    @Test
    void footnoteStaysLiteral() {
        List<MarkdownInline> inlines = inlineParser.parse("正文[^1]");

        assertThat(inlines).containsExactly(new Text("正文[^1]"));
    }

    @Test
    void definitionListStaysAParagraph() {
        List<MarkdownBlock> blocks = blockParser.parse("术语\n: 定义\n");

        assertThat(blocks).containsExactly(new Paragraph("术语\n: 定义"));
    }

    @Test
    void mathStaysLiteral() {
        assertThat(inlineParser.parse("$x^2 + y^2$")).containsExactly(new Text("$x^2 + y^2$"));
        assertThat(inlineParser.parse("$$\nE = mc^2\n$$"))
                .containsExactly(new Text("$$\nE = mc^2\n$$"));
    }

    @Test
    void mermaidFenceKeepsItsWholeContent() {
        List<MarkdownBlock> blocks = blockParser.parse("```mermaid\ngraph TD;\n  A-->B;\n```\n");

        assertThat(blocks).containsExactly(new Fence("mermaid", "graph TD;\n  A-->B;"));
    }
}
