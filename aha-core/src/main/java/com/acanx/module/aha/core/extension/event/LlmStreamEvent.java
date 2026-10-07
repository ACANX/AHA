package com.acanx.module.aha.core.extension.event;

import com.acanx.module.aha.core.llm.protocol.StreamEvent;
import com.acanx.module.aha.extension.api.event.ExtensionEvent;

/**
 * LLM 流式事件（WATERFALL，可变换）。
 *
 * @param sessionId  会话 ID
 * @param streamEvent 流式事件
 * @since 0.1.0
 */
public record LlmStreamEvent(String sessionId, StreamEvent streamEvent) implements ExtensionEvent {
}
