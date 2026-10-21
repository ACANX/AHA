package com.acanx.module.aha.desktop.markdown;

import com.acanx.module.aha.core.markdown.MarkdownSemanticsSamples;
import com.acanx.module.aha.core.markdown.MarkdownSemanticsSamples.Sample;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 共享语义基线的桌面端消费方（issue #409 / #413，tracking #408）。
 *
 * <p>样本定义在 {@code MarkdownSemanticsSamples}，与 CLI 侧同一份。这里断言解析出的 AST：
 * 每条样本必须产出对应类型的块，且不抛异常——S1.2 起再补行内断言。</p>
 *
 * @since 0.2.0
 */
class MarkdownParserSemanticsTest {

    private final MarkdownParser parser = new MarkdownParser();

    @Test
    void parserConsumesEveryActiveSample() {
        for (Sample sample : MarkdownSemanticsSamples.active()) {
            List<MarkdownBlock> blocks = parser.parse(sample.input());

            assertThat(blocks).as(sample.id()).isNotEmpty();
            assertThat(blocks).as(sample.id()).allMatch(MarkdownParserSemanticsTest::isKnownBlock);
        }
    }

    @Test
    void tableSamplesProduceTableBlocks() {
        assertThat(blocksOf("table-boundary"))
                .allSatisfy(blocks -> assertThat(blocks).anyMatch(MarkdownBlock.TableBlock.class::isInstance));
    }

    @Test
    void fenceSamplesProduceFenceBlocks() {
        assertThat(blocksOf("fence-literal"))
                .allSatisfy(blocks -> assertThat(blocks).anyMatch(MarkdownBlock.Fence.class::isInstance));
    }

    @Test
    void inlineSamplesStayParagraphsUntilInlineParsingLands() {
        // 行内解析是 S1.2 的事：这一阶段只要求这些输入不被误判成块级结构
        assertThat(blocksOf("inline-flanking"))
                .allSatisfy(blocks -> assertThat(blocks).allMatch(MarkdownBlock.Paragraph.class::isInstance));
    }

    private List<List<MarkdownBlock>> blocksOf(String rule) {
        return MarkdownSemanticsSamples.active().stream()
                .filter(sample -> rule.equals(sample.rule()))
                .map(sample -> parser.parse(sample.input()))
                .toList();
    }

    private static boolean isKnownBlock(MarkdownBlock block) {
        return block instanceof MarkdownBlock.Heading
                || block instanceof MarkdownBlock.Paragraph
                || block instanceof MarkdownBlock.Rule
                || block instanceof MarkdownBlock.Fence
                || block instanceof MarkdownBlock.ListBlock
                || block instanceof MarkdownBlock.Quote
                || block instanceof MarkdownBlock.TableBlock;
    }
}
