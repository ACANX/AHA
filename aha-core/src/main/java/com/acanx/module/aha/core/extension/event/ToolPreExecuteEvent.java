package com.acanx.module.aha.core.extension.event;

import com.acanx.module.aha.extension.api.event.ExtensionEvent;

import java.util.Map;

/**
 * 工具执行前事件（WATERFALL）。
 *
 * @param sessionId 会话 ID
 * @param toolName  工具名
 * @param args      参数
 * @since 0.1.0
 */
public record ToolPreExecuteEvent(String sessionId, String toolName, Map<String, Object> args) implements ExtensionEvent {
}
