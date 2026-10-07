package com.acanx.module.aha.core.llm.protocol;

/**
 * IR 流式事件。
 *
 * @param type           事件类型
 * @param contentDelta   内容增量
 * @param reasoningDelta 思维链增量
 * @param toolCallDelta  工具调用增量
 * @param usage          用量
 * @param finishReason   结束原因
 * @param errorCode      错误码
 * @param errorMessage   错误信息
 * @since 0.1.0
 */
public record StreamEvent(
        StreamEventType type,
        String contentDelta,
        String reasoningDelta,
        ToolCallDelta toolCallDelta,
        Usage usage,
        String finishReason,
        String errorCode,
        String errorMessage
) {
}
