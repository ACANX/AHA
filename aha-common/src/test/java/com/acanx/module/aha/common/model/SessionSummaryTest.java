package com.acanx.module.aha.common.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SessionSummary} 测试：标题兜底与短 ID。
 *
 * @since 0.2.0
 */
class SessionSummaryTest {

    @Test
    void blankTitleFallsBackToPlaceholder() {
        assertThat(new SessionSummary("id", null, 0, 0).title())
                .isEqualTo(SessionSummary.UNTITLED);
        assertThat(new SessionSummary("id", "   ", 0, 0).title())
                .isEqualTo(SessionSummary.UNTITLED);
    }

    @Test
    void titleIsStripped() {
        assertThat(new SessionSummary("id", "  你好  ", 0, 0).title()).isEqualTo("你好");
    }

    @Test
    void shortIdTakesFirstEightChars() {
        assertThat(new SessionSummary("0123456789ABCDEF", "t", 0, 1).shortId())
                .isEqualTo("01234567");
        assertThat(new SessionSummary("abc", "t", 0, 1).shortId()).isEqualTo("abc");
        assertThat(new SessionSummary(null, "t", 0, 0).shortId()).isEmpty();
    }

    @Test
    void hasMessagesReflectsCount() {
        assertThat(new SessionSummary("id", "t", 0, 0).hasMessages()).isFalse();
        assertThat(new SessionSummary("id", "t", 0, 3).hasMessages()).isTrue();
    }
}
