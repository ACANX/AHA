package com.acanx.module.aha.common.event;

/**
 * 完成事件。
 *
 * @param sessionId    会话 ID
 * @param finishReason 结束原因
 * @since 0.1.0
 */
public record DoneEvent(String sessionId, String finishReason) implements AgentEvent {
}
