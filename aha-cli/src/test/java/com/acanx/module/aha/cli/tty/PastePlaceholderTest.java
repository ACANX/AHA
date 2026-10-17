package com.acanx.module.aha.cli.tty;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PastePlaceholder} 测试：折叠门槛、展开、以及两类「不能踩的坑」
 * （用户改过的占位符仍要能认；不是我们插的同形文本绝不能被吞掉）。
 *
 * @since 0.2.0
 */
class PastePlaceholderTest {

    @Test
    void shortContentGoesInAsIs() {
        PastePlaceholder placeholders = new PastePlaceholder();

        assertThat(placeholders.compact("git log --oneline")).isEqualTo("git log --oneline");
        assertThat(placeholders.compact("a\nb\nc")).isEqualTo("a\nb\nc");
        assertThat(placeholders.pending()).as("没折叠就不该留内容").isZero();
    }

    @Test
    void longContentBecomesAMarker() {
        PastePlaceholder placeholders = new PastePlaceholder();
        String tenLines = String.join("\n",
                IntStream.rangeClosed(1, 10).mapToObj(i -> "第 " + i + " 行").toList());
        String thirteenLines = String.join("\n",
                IntStream.rangeClosed(1, 13).mapToObj(i -> "第 " + i + " 行").toList());

        // 门槛与 pi / Claude Code 同一条：> 10 行
        assertThat(placeholders.compact(tenLines)).isEqualTo(tenLines);
        assertThat(placeholders.compact(thirteenLines)).isEqualTo("[paste #1 +13 lines]");
        assertThat(placeholders.pending()).isEqualTo(1);
    }

    @Test
    void singleHugeLineBecomesACharCountMarker() {
        PastePlaceholder placeholders = new PastePlaceholder();
        String huge = "x".repeat(PastePlaceholder.MIN_CHARS + 1);

        assertThat(placeholders.compact(huge)).isEqualTo("[paste #1 " + huge.length() + " chars]");
    }

    @Test
    void expandsMarkersBackToTheOriginalContent() {
        PastePlaceholder placeholders = new PastePlaceholder();
        String first = String.join("\n", IntStream.rangeClosed(1, 12).mapToObj(i -> "A" + i).toList());
        String second = String.join("\n", IntStream.rangeClosed(1, 20).mapToObj(i -> "B" + i).toList());

        String line = placeholders.compact(first) + "\n这段报错怎么修？\n" + placeholders.compact(second);
        String expanded = placeholders.expand(line);

        assertThat(line).isEqualTo("[paste #1 +12 lines]\n这段报错怎么修？\n[paste #2 +20 lines]");
        assertThat(expanded).isEqualTo(first + "\n这段报错怎么修？\n" + second);
        assertThat(placeholders.pending()).as("展开后不该再握着内容").isZero();
    }

    @Test
    void markerEditedByTheUserStillExpands() {
        PastePlaceholder placeholders = new PastePlaceholder();
        String content = String.join("\n", IntStream.rangeClosed(1, 30).mapToObj(i -> "L" + i).toList());
        String marker = placeholders.compact(content);

        // 用户可能把计数改掉（甚至删掉整段计数）：只认编号
        assertThat(placeholders.expand(marker.replace("+30 lines", "+3 lines"))).isEqualTo(content);

        PastePlaceholder again = new PastePlaceholder();
        String marker2 = again.compact(content);
        assertThat(again.expand(marker2.replace("+30 lines", ""))).isEqualTo(content);
    }

    @Test
    void sameLookingTextTypedByTheUserIsLeftAlone() {
        PastePlaceholder placeholders = new PastePlaceholder();

        // 没有对应内容的同形文本：原样保留，绝不替换成空串（那会静默吞掉用户写的内容）
        assertThat(placeholders.expand("请解释 [paste #7 +3 lines] 是什么意思")).isEqualTo(
                "请解释 [paste #7 +3 lines] 是什么意思");
    }

    @Test
    void contentContainingMarkerLikeTextIsNotExpandedTwice() {
        PastePlaceholder placeholders = new PastePlaceholder();
        String content = String.join("\n",
                IntStream.rangeClosed(1, 15)
                        .mapToObj(i -> i == 3 ? "[paste #1 +15 lines]" : "L" + i)
                        .toList());
        String marker = placeholders.compact(content);

        // 单遍替换：内容里恰好写着同形文本时，不能被再展开一次（展开结果必须等于原文）
        assertThat(placeholders.expand(marker)).isEqualTo(content);
    }

    @Test
    void startsWithMarkerOnlyWhenTheWholeLineIsPasted() {
        PastePlaceholder placeholders = new PastePlaceholder();
        String marker = placeholders.compact(
                String.join("\n", IntStream.rangeClosed(1, 12).mapToObj(i -> "L" + i).toList()));

        assertThat(placeholders.startsWithMarker(marker)).isTrue();
        assertThat(placeholders.startsWithMarker("/paste " + marker)).isFalse();
        assertThat(placeholders.startsWithMarker("/help")).isFalse();
    }

    @Test
    void emptyContentIsHandled() {
        PastePlaceholder placeholders = new PastePlaceholder();

        assertThat(placeholders.compact("")).isEmpty();
        assertThat(placeholders.compact(null)).isEmpty();
        assertThat(placeholders.expand("原样")).isEqualTo("原样");
    }
}
