package com.acanx.module.aha.common.event;

/**
 * 正文段结束事件：本轮（一次 LLM 调用）产出的正文到此为止。
 *
 * <p>一轮对话里模型会多次产出正文：工具调用之前的开场白、工具结果回灌之后的分析、
 * 收尾的总结陈述。它们<b>分属不同的显示段</b>，但事件流里原本只有 {@link ContentEvent}
 * 与「整轮结束」的 {@link DoneEvent}，没有段边界——于是桌面端只能把整轮的正文塞进
 * 同一个气泡，收尾总结凭空出现在开场白之后、工具卡片之前（issue #403）。</p>
 *
 * <p>本事件就是那条缺失的边界：<b>段结束、工具执行之前</b>发出。
 * 它不带任何载荷——段内文本已由 {@link ContentEvent} 逐 token 送达，
 * 再加「段 ID / 段序号」只会把协议复杂度抬到没有收益的高度
 * （{@link DoneEvent} 已经是天然的终点）。</p>
 *
 * <p>与 {@link DoneEvent} 的区别：本事件可以在同一轮里出现多次（有几段正文就几次），
 * {@link DoneEvent} 一轮只出现一次。消费方忽略本事件也不会出错——
 * 那只是退化成本次修复之前的显示行为。</p>
 *
 * @param sessionId 会话 ID
 * @since 0.2.0
 */
public record ContentSegmentEndEvent(String sessionId) implements AgentEvent {
}
