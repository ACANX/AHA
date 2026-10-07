package com.acanx.module.aha.core.extension.event;

import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.extension.api.event.ExtensionEvent;

/**
 * LLM 请求事件（WATERFALL，可修改）。
 *
 * @param sessionId 会话 ID
 * @param request   IR 请求
 * @since 0.1.0
 */
public record RequestEvent(String sessionId, ChatRequest request) implements ExtensionEvent {
}
