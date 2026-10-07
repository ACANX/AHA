package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.common.model.SessionSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SessionList} 测试：标题截断、相对时间、搜索过滤。
 *
 * @since 0.2.0
 */
class SessionListTest {

    private static final long NOW = 1_760_000_000_000L;

    @Test
    void itemCarriesTitleAndMeta() {
        SessionSummary summary = new SessionSummary("0123456789ABCDEF", "看看代码", NOW - 120_000, 12);

        SessionList.Item item = SessionList.item(summary, NOW);

        assertThat(item.title()).isEqualTo("看看代码");
        assertThat(item.meta()).isEqualTo("12 条 · 2 分钟前");
        assertThat(item.line()).isEqualTo("看看代码　12 条 · 2 分钟前");
        assertThat(item.tooltip()).contains("看看代码").contains("0123456789ABCDEF");
    }

    @Test
    void longTitleIsTruncatedWithEllipsis() {
        String longTitle = "一".repeat(40);
        assertThat(SessionList.trim(longTitle)).hasSize(SessionList.TITLE_MAX).endsWith("…");
        assertThat(SessionList.trim("短")).isEqualTo("短");
        assertThat(SessionList.trim(null)).isEqualTo(SessionSummary.UNTITLED);
    }

    @Test
    void titleIsFlattenedToOneLine() {
        assertThat(SessionList.trim("第一行\n第二行")).isEqualTo("第一行 第二行");
    }

    @Test
    void relativeTimeSpeaksInHumanTerms() {
        assertThat(SessionList.relativeTime(NOW - 1_000, NOW)).isEqualTo("刚刚");
        assertThat(SessionList.relativeTime(NOW - 90_000, NOW)).isEqualTo("1 分钟前");
        assertThat(SessionList.relativeTime(NOW - 3 * 3600_000L, NOW)).isEqualTo("3 小时前");
        assertThat(SessionList.relativeTime(NOW - 30 * 3600_000L, NOW)).isEqualTo("昨天");
        assertThat(SessionList.relativeTime(NOW - 3 * 24 * 3600_000L, NOW)).isEqualTo("3 天前");
        // 超过 7 天落到绝对日期，不再说「9 天前」
        assertThat(SessionList.relativeTime(NOW - 9L * 24 * 3600_000L, NOW)).doesNotContain("天前");
    }

    @Test
    void clockSkewIsReportedAsJustNow() {
        // 时钟回拨不该显示「-3 分钟前」
        assertThat(SessionList.relativeTime(NOW + 180_000, NOW)).isEqualTo("刚刚");
    }

    @Test
    void filterMatchesTitleAndId() {
        List<SessionSummary> sessions = List.of(
                new SessionSummary("AAA11111", "看看代码", NOW, 3),
                new SessionSummary("BBB22222", "写文档", NOW, 1));

        assertThat(SessionList.filter(sessions, "")).hasSize(2);
        assertThat(SessionList.filter(sessions, null)).hasSize(2);
        assertThat(SessionList.filter(sessions, "代码")).extracting(SessionSummary::id)
                .containsExactly("AAA11111");
        assertThat(SessionList.filter(sessions, "bbb")).extracting(SessionSummary::id)
                .containsExactly("BBB22222");
        assertThat(SessionList.filter(sessions, "没有这个")).isEmpty();
        assertThat(SessionList.filter(null, "x")).isEmpty();
    }
}
