package com.acanx.module.aha.common.event;

/**
 * 内容增量事件。
 *
 * @param sessionId 会话 ID
 * @param delta     内容增量
 * @since 0.1.0
 */
public record ContentEvent(String sessionId, String delta) implements AgentEvent {
}
