package com.acanx.module.aha.common.event;

/**
 * Agent 事件（密封接口）。
 *
 * @since 0.1.0
 */
public sealed interface AgentEvent
        permits ContentEvent, ToolCallEvent, ToolResultEvent,
                UsageEvent, ErrorEvent, DoneEvent {
}
