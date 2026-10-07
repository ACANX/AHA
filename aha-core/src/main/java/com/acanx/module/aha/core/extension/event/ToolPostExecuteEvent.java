package com.acanx.module.aha.core.extension.event;

import com.acanx.module.aha.extension.api.event.ExtensionEvent;

/**
 * 工具执行后事件（WATERFALL）。
 *
 * @param sessionId 会话 ID
 * @param toolName  工具名
 * @param result    结果
 * @since 0.1.0
 */
public record ToolPostExecuteEvent(String sessionId, String toolName, Object result) implements ExtensionEvent {
}
