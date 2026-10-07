package com.acanx.module.aha.core.extension.event;

import com.acanx.module.aha.extension.api.event.ExtensionEvent;

/**
 * 推理步进前事件（WATERFALL）。
 *
 * @param sessionId 会话 ID
 * @since 0.1.0
 */
public record PreStepEvent(String sessionId) implements ExtensionEvent {
}
