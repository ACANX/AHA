package com.acanx.module.aha.desktop.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * 待渲染的**段快照批次**（issue #403 / PR #404 审查 P2-1）。
 *
 * <p>渲染桥 {@code FxBridge} 的策略是「只保留最新值，一帧渲染一次」：旧实现提交的是
 * 「整轮全文」，被覆盖掉的是同一段正文的中间态，安全。分段之后每段的快照是**独立全文**，
 * 若一段收尾后紧接着下一段开始（段间没有慢操作），后者的快照会覆盖前者，
 * 前一段就整段没渲染出来——界面上凭空少一段正文。</p>
 *
 * <p>这个类因此改成「批」：{@link #merge} 把新快照并进批次（同段号替换为最新全文，
 * 因为快照本身就是全文），渲染侧取整批按序渲染，渲染完用 {@link #drop} 摘掉已渲染的部分。
 * 批次里因此最多只有一个未渲染的「进行中段」，长度不随对话变长而增长。</p>
 *
 * <p>纯逻辑、无 JavaFX 依赖，可在无图形环境直接断言（与 {@link AssistantSegments} 同一思路）。</p>
 *
 * @since 0.2.0
 */
public final class AssistantFrameBuffer {

    private final Object lock = new Object();

    /** 尚未渲染的段快照，按段号不降序；同段号只留最新一份。 */
    private final List<AssistantSegments.Segment> pending = new ArrayList<>();

    /**
     * 并入一份快照。
     *
     * @param segment 段快照（{@code null} 忽略）
     * @return 当前批次的不可变视图，供渲染桥投递
     */
    public List<AssistantSegments.Segment> merge(AssistantSegments.Segment segment) {
        synchronized (lock) {
            if (segment != null) {
                // 同段号的在途快照直接换成最新一份：快照是全文，替换不丢内容，
                // 也不会让批次随 token 数增长
                int index = lastIndexOfSameSegment(segment);
                if (index >= 0) {
                    pending.set(index, segment);
                } else {
                    pending.add(segment);
                }
            }
            return List.copyOf(pending);
        }
    }

    /**
     * 摘掉已渲染的部分。
     *
     * @param generation 当前代际；其他代际的残留项一并丢弃
     * @param uptoSeq    已渲染到的段号（含）
     */
    public void drop(int generation, int uptoSeq) {
        synchronized (lock) {
            pending.removeIf(segment -> segment.generation() != generation
                    || segment.seq() <= uptoSeq);
        }
    }

    /**
     * 清空（切换会话 / 清空对话流）。
     */
    public void clear() {
        synchronized (lock) {
            pending.clear();
        }
    }

    /**
     * 当前批次大小（供测试与日志）。
     *
     * @return 未渲染的条目数
     */
    public int size() {
        synchronized (lock) {
            return pending.size();
        }
    }

    /** 末尾同段号项的下标；没有则 -1。 */
    private int lastIndexOfSameSegment(AssistantSegments.Segment segment) {
        for (int i = pending.size() - 1; i >= 0; i--) {
            AssistantSegments.Segment existing = pending.get(i);
            if (existing.generation() == segment.generation() && existing.seq() == segment.seq()) {
                return i;
            }
        }
        return -1;
    }
}
