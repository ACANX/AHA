package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.core.markdown.MarkdownSemanticsSamples;
import com.acanx.module.aha.core.markdown.MarkdownSemanticsSamples.Expectation;
import com.acanx.module.aha.core.markdown.MarkdownSemanticsSamples.Sample;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 共享语义基线的 CLI 侧消费方（issue #409 / tracking #408）。
 *
 * <p>样本定义在 {@link MarkdownSemanticsSamples}，CLI 与桌面端（S1.1 起）读同一份：
 * 这里断言 {@link MarkdownStream} 的渲染结果，桌面端断言解析出的 AST。
 * 两端若有一边改了语义，这份样本会先红——这正是它存在的意义。</p>
 *
 * @since 0.2.0
 */
class MarkdownSemanticsTest {

    @Test
    void cliRendererMatchesSharedSemanticsSamples() {
        for (Sample sample : MarkdownSemanticsSamples.active()) {
            MarkdownStream stream = new MarkdownStream(Style.of(false), true, 0);
            String out = stream.feed(sample.input()) + stream.flush();

            for (Expectation expectation : sample.cli()) {
                String where = sample.id() + " / " + expectation.type() + " " + expectation.value();
                switch (expectation.type()) {
                    case COUNT -> assertThat(countOf(out, expectation.value()))
                            .as(where).isEqualTo(expectation.times());
                    case CONTAINS -> assertThat(out).as(where).contains(expectation.value());
                    case NOT_CONTAINS -> assertThat(out).as(where).doesNotContain(expectation.value());
                    case MATCHES -> assertThat(out).as(where).containsPattern(expectation.value());
                }
            }
        }
    }

    @Test
    void samplesCoverEveryRuleWithAtLeastThreeCases() {
        // 防止样本被删到只剩一两条：每条规则至少 3 条，否则基线名存实亡
        Map<String, Long> byRule = MarkdownSemanticsSamples.all().stream()
                .collect(Collectors.groupingBy(Sample::rule, Collectors.counting()));

        assertThat(byRule).containsKeys("table-boundary", "fence-literal", "inline-flanking", "escape-literal");
        assertThat(byRule.values()).allSatisfy(count -> assertThat(count).isGreaterThanOrEqualTo(3));
    }

    @Test
    void pendingSamplesAreDocumentedButNotAsserted() {
        // pending 样本只记录「目标行为」；它们在 active() 里必须消失，否则会把未实现固化成期望
        assertThat(MarkdownSemanticsSamples.active())
                .noneMatch(sample -> sample.pending() || "escape-literal".equals(sample.rule()));
        assertThat(MarkdownSemanticsSamples.all())
                .filteredOn(sample -> "escape-literal".equals(sample.rule()))
                .hasSize(3)
                .allMatch(Sample::pending);
    }

    private static int countOf(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
