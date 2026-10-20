package com.acanx.module.aha.common.event;

/**
 * Agent 事件（密封接口）。
 *
 * <p>{@link ContentSegmentEndEvent} 是「一段正文结束」的边界（issue #403）：
 * 同一轮里出现多次，{@link DoneEvent} 只在整轮结束时出现一次。</p>
 *
 * @since 0.1.0
 */
public sealed interface AgentEvent
        permits ContentEvent, ContentSegmentEndEvent, ToolCallEvent, ToolResultEvent,
                UsageEvent, ErrorEvent, DoneEvent {
}
