package com.acanx.module.aha.common.event;

/**
 * Token 用量事件。
 *
 * @param sessionId        会话 ID
 * @param promptTokens     提示 token 数
 * @param completionTokens 补全 token 数
 * @since 0.1.0
 */
public record UsageEvent(String sessionId, int promptTokens, int completionTokens) implements AgentEvent {
}
