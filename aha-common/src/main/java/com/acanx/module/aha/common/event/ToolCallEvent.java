package com.acanx.module.aha.common.event;

import java.util.Map;

/**
 * 工具调用事件。
 *
 * @param sessionId 会话 ID
 * @param toolName  工具名
 * @param args      参数
 * @since 0.1.0
 */
public record ToolCallEvent(String sessionId, String toolName, Map<String, Object> args) implements AgentEvent {
}
