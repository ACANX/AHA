package com.acanx.module.aha.core.extension.event;

import com.acanx.module.aha.extension.api.event.ExtensionEvent;

/**
 * 会话落盘事件（PARALLEL）。
 *
 * @param sessionId 会话 ID
 * @since 0.1.0
 */
public record SessionFlushEvent(String sessionId) implements ExtensionEvent {
}
