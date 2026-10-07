package com.acanx.module.aha.core.llm.protocol;

/**
 * IR 响应选项。
 *
 * @param index        序号
 * @param message      消息
 * @param finishReason 结束原因
 * @since 0.1.0
 */
public record Choice(int index, ChatMessage message, String finishReason) {
}
