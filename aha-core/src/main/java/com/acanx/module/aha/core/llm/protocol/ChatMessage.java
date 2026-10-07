package com.acanx.module.aha.core.llm.protocol;

import java.util.List;

/**
 * IR 聊天消息。
 *
 * @param role             角色
 * @param content          内容
 * @param toolCalls        工具调用
 * @param toolCallId       工具调用 ID
 * @param reasoningContent 思维链内容
 * @since 0.1.0
 */
public record ChatMessage(
        Role role,
        String content,
        List<ToolCall> toolCalls,
        String toolCallId,
        String reasoningContent
) {
    /**
     * 构造纯文本消息。
     *
     * @param role    角色
     * @param content 内容
     * @return 消息
     */
    public static ChatMessage text(Role role, String content) {
        return new ChatMessage(role, content, List.of(), null, null);
    }
}
