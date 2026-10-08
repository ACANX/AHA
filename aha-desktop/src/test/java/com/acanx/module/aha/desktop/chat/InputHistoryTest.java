package com.acanx.module.aha.desktop.chat;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InputHistory} 测试：↑ / ↓ 的行为口径。
 *
 * @since 0.2.0
 */
class InputHistoryTest {

    @Test
    void blankAndDuplicateEntriesAreSkipped() {
        InputHistory history = new InputHistory();
        history.add("   ");
        history.add(null);
        assertThat(history.size()).isZero();

        history.add("第一句");
        history.add("第一句");
        assertThat(history.size()).as("连续重复不必占两格").isEqualTo(1);
    }

    @Test
    void previousWalksBackAndKeepsTheDraft() {
        InputHistory history = new InputHistory();
        history.add("第一句");
        history.add("第二句");

        assertThat(history.previous("正在打的内容")).isEqualTo("第二句");
        assertThat(history.previous("第二句")).isEqualTo("第一句");
        // 翻到头之后停在最早那条，不会越界
        assertThat(history.previous("第一句")).isEqualTo("第一句");
        // 再往下翻回到草稿
        assertThat(history.next()).isEqualTo("第二句");
        assertThat(history.next()).isEqualTo("正在打的内容");
        assertThat(history.atDraft()).isTrue();
    }

    @Test
    void addResetsCursor() {
        InputHistory history = new InputHistory();
        history.add("甲");
        history.previous("");
        history.add("乙");

        assertThat(history.atDraft()).isTrue();
        assertThat(history.previous("")).isEqualTo("乙");
    }

    @Test
    void capacityDropsOldest() {
        InputHistory history = new InputHistory(3);
        history.add("1");
        history.add("2");
        history.add("3");
        history.add("4");

        assertThat(history.size()).isEqualTo(3);
        assertThat(history.entries()).containsExactly("4", "3", "2");
    }

    @Test
    void previousOnEmptyHistoryKeepsCurrentText() {
        InputHistory history = new InputHistory();
        assertThat(history.previous("草稿")).isEqualTo("草稿");
        assertThat(history.next()).isEmpty();
    }
}
