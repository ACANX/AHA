package com.acanx.module.aha.desktop.chat;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AssistantSegments} 测试：分段、快照、幂等、跨线程累积。
 *
 * <p>这些断言不碰 JavaFX，因此在无图形环境的 CI 与 WSL 上照常执行（issue #403）。</p>
 *
 * @since 0.2.0
 */
class AssistantSegmentsTest {

    @Test
    void accumulatesWithinOneSegment() {
        AssistantSegments segments = new AssistantSegments();

        assertThat(segments.append("He").text()).isEqualTo("He");
        assertThat(segments.append("llo").text()).isEqualTo("Hello");
        // 段号不变：同一段内的增量要续写同一个气泡
        assertThat(segments.append("!").seq()).isZero();
    }

    @Test
    void endClosesSegmentAndNextAppendStartsANewOne() {
        AssistantSegments segments = new AssistantSegments();
        segments.append("开场白");

        AssistantSegments.Segment last = segments.end();
        assertThat(last).isEqualTo(new AssistantSegments.Segment(0, 0, "开场白"));

        // 收尾之后的第一条增量必须属于新段，且**不含**上一段的文本（跨段串字的反例）
        AssistantSegments.Segment next = segments.append("总结");
        assertThat(next.seq()).isEqualTo(1);
        assertThat(next.text()).isEqualTo("总结");
    }

    @Test
    void endIsIdempotent() {
        // 段边界之后还会来一次「整轮结束」的收尾（DoneEvent），
        // 若 end() 不幂等，同一段正文会被当成新段再渲染一遍
        AssistantSegments segments = new AssistantSegments();
        segments.append("只有一段");

        assertThat(segments.end()).isNotNull();
        assertThat(segments.end()).isNull();
        // 幂等之外还有一层：空段不占段号（PR #404 审查 P3），
        // 所以 seq 等于「有正文的段数」，而不是「end() 被调用了几次」
        assertThat(segments.seq()).isEqualTo(1);
    }

    @Test
    void emptySegmentReturnsNullInsteadOfBuildingAnEmptyBubble() {
        AssistantSegments segments = new AssistantSegments();
        // 模型直接调工具、没有任何开场白：不该冒出空气泡，也不该凭空占掉一个段号
        assertThat(segments.end()).isNull();
        assertThat(segments.seq()).isZero();
        assertThat(segments.append("工具之后的正文").seq()).isZero();
    }

    @Test
    void segmentEndedOnEmptyDeltaDoesNotMergeWithTheNextOne() {
        // 极端情形：上一段由空增量收尾。判据若写成「缓冲是否为空」，两段会被并成一段
        AssistantSegments segments = new AssistantSegments();
        segments.append("A");
        segments.append("");
        segments.end();
        assertThat(segments.append("B")).isEqualTo(new AssistantSegments.Segment(0, 1, "B"));
    }

    @Test
    void resetReturnsToTheFirstSegment() {
        AssistantSegments segments = new AssistantSegments();
        segments.append("旧会话");
        segments.end();

        segments.reset();

        assertThat(segments.seq()).isZero();
        // 段号归零，但代际号前进：复位前的在途快照因此能与新会话的第一段区分开
        assertThat(segments.append("新会话")).isEqualTo(new AssistantSegments.Segment(1, 0, "新会话"));
    }

    @Test
    void snapshotsAreFullTextSoCoalescingCannotLoseContent() {
        // 渲染侧会合并「帧内多次提交」，只保留最后一次；因此每次快照都必须是全文
        AssistantSegments segments = new AssistantSegments();
        List<AssistantSegments.Segment> snapshots = new ArrayList<>();
        for (String piece : List.of("一", "二", "三")) {
            snapshots.add(segments.append(piece));
        }

        assertThat(snapshots.getLast().text()).isEqualTo("一二三");
        assertThat(snapshots).allSatisfy(snapshot -> assertThat(snapshot.seq()).isZero());
    }

    @Test
    void resetAdvancesGenerationSoInFlightSnapshotsAreDistinguishable() {
        // 切换会话时 reset() 把段号归零，而上一轮推理可能还在后台产出：
        // 它手里的快照与新会话的第一段段号相同。代际号是唯一能区分两者的信息
        // （PR #404 审查 P2-2）。
        AssistantSegments segments = new AssistantSegments();
        segments.append("旧会话第一段");
        segments.end();
        AssistantSegments.Segment inFlight = segments.append("旧会话第二段");

        segments.reset();

        assertThat(inFlight.generation()).isZero();
        assertThat(segments.generation()).isEqualTo(1);
        assertThat(segments.append("新会话"))
                .isEqualTo(new AssistantSegments.Segment(1, 0, "新会话"));
    }

    @Test
    void segmentSnapshotsCarryTheGenerationTheyWereTakenIn() {
        AssistantSegments segments = new AssistantSegments();
        assertThat(segments.append("a").generation()).isZero();
        assertThat(segments.end().generation()).isZero();

        segments.reset();

        assertThat(segments.append("b").generation()).isEqualTo(1);
        assertThat(segments.end().generation()).isEqualTo(1);
    }

    @Test
    void concurrentAppendsAreNotLostByTheEndRace() throws InterruptedException {
        // 推理线程追加、UI 线程收尾：锁若只保护一半，就会出现「收尾后仍追加」或丢字
        AssistantSegments segments = new AssistantSegments();
        int threads = 4;
        int perThread = 250;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            Thread worker = Thread.ofVirtual().start(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < perThread; i++) {
                    segments.append("x");
                }
            });
            workers.add(worker);
        }

        start.countDown();
        for (Thread worker : workers) {
            worker.join(TimeUnit.SECONDS.toMillis(10));
        }

        AssistantSegments.Segment last = segments.end();
        assertThat(last).isNotNull();
        assertThat(last.text()).hasSize(threads * perThread);
    }
}
