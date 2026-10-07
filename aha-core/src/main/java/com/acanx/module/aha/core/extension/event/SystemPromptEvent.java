package com.acanx.module.aha.core.extension.event;

import com.acanx.module.aha.extension.api.event.ExtensionEvent;

/**
 * 系统提示词组装事件（WATERFALL）。
 *
 * @param sessionId 会话 ID
 * @param prompt    提示词
 * @since 0.1.0
 */
public record SystemPromptEvent(String sessionId, String prompt) implements ExtensionEvent {
}
