package com.acanx.module.aha.core.service;

import com.acanx.module.aha.core.llm.protocol.ToolCall;
import com.acanx.module.aha.core.llm.protocol.Usage;

import java.util.List;

/**
 * Agent 响应。
 *
 * @param sessionId    会话 ID
 * @param content      内容
 * @param toolCalls    工具调用
 * @param usage        用量
 * @param finishReason 结束原因
 * @since 0.1.0
 */
public record AgentResponse(
        String sessionId,
        String content,
        List<ToolCall> toolCalls,
        Usage usage,
        String finishReason
) {
}
