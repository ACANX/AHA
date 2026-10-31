package com.acanx.module.aha.desktop.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AssistantFrameBuffer} 测试：同段合并、跨段累积、已渲染摘除、代际丢弃。
 *
 * <p>回归的是 PR #404 审查 P2-1：渲染桥只保留最新值，分段之后每段是独立全文，
 * 单个值被覆盖时前一段就整段不渲染。</p>
 *
 * @since 0.2.0
 */
class AssistantFrameBufferTest {

    private static AssistantSegments.Segment seg(int generation, int seq, String text) {
        return new AssistantSegments.Segment(generation, seq, text);
    }

    @Test
    void sameSegmentSnapshotsReplaceInsteadOfAccumulating() {
        // 快照本身就是全文：同段号的在途项直接替换，批次才不会随 token 数增长
        AssistantFrameBuffer buffer = new AssistantFrameBuffer();

        buffer.merge(seg(0, 0, "一"));
        buffer.merge(seg(0, 0, "一二"));
        List<AssistantSegments.Segment> batch = buffer.merge(seg(0, 0, "一二三"));

        assertThat(batch).containsExactly(seg(0, 0, "一二三"));
        assertThat(buffer.size()).isEqualTo(1);
    }

    @Test
    void consecutiveSegmentsAreAllKeptSoNoneIsDropped() {
        // 这条就是 P2-1 的反例：段间没有慢操作时，两段的快照会撞在同一帧里。
        // 若桥只保留最新值，前一段整段不渲染。
        AssistantFrameBuffer buffer = new AssistantFrameBuffer();

        buffer.merge(seg(0, 0, "开场白"));
        buffer.merge(seg(0, 0, "开场白"));   // 段 0 收尾（同段替换）
        List<AssistantSegments.Segment> batch = buffer.merge(seg(0, 1, "总结"));

        assertThat(batch).containsExactly(seg(0, 0, "开场白"), seg(0, 1, "总结"));
    }

    @Test
    void renderedSegmentsAreDroppedSoTheBatchDoesNotGrow() {
        AssistantFrameBuffer buffer = new AssistantFrameBuffer();
        buffer.merge(seg(0, 0, "第一段"));
        buffer.merge(seg(0, 1, "第二段"));

        buffer.drop(0, 0);
        assertThat(buffer.size()).isEqualTo(1);

        buffer.drop(0, 1);
        assertThat(buffer.size()).isZero();
    }

    @Test
    void staleGenerationIsDropped() {
        // 切换会话后，上一会话在途的旧段不该继续占着批次（PR #404 审查 P2-2）
        AssistantFrameBuffer buffer = new AssistantFrameBuffer();
        buffer.merge(seg(0, 2, "旧会话"));

        buffer.drop(1, -1);

        assertThat(buffer.size()).isZero();
    }

    @Test
    void clearEmptiesTheBatch() {
        AssistantFrameBuffer buffer = new AssistantFrameBuffer();
        buffer.merge(seg(0, 0, "x"));

        buffer.clear();

        assertThat(buffer.size()).isZero();
        // 清空后仍可正常并入（clear 不是 close）
        assertThat(buffer.merge(seg(1, 0, "y"))).containsExactly(seg(1, 0, "y"));
    }

    @Test
    void nullSegmentIsIgnored() {
        AssistantFrameBuffer buffer = new AssistantFrameBuffer();

        assertThat(buffer.merge(null)).isEmpty();
        assertThat(buffer.size()).isZero();
    }
}
