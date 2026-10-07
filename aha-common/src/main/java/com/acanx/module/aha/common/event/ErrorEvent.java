package com.acanx.module.aha.common.event;

/**
 * 错误事件。
 *
 * @param sessionId 会话 ID
 * @param code      错误码
 * @param message   错误信息
 * @since 0.1.0
 */
public record ErrorEvent(String sessionId, String code, String message) implements AgentEvent {
}
