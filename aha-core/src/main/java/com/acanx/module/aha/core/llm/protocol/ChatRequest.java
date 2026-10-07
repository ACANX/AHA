package com.acanx.module.aha.core.llm.protocol;

import java.util.List;
import java.util.Map;

/**
 * IR 聊天请求。
 *
 * @param model       模型
 * @param messages    消息列表
 * @param tools       工具定义
 * @param temperature 温度
 * @param maxTokens   最大 token 数
 * @param stream      是否流式
 * @param extensions  供应商扩展参数
 * @since 0.1.0
 */
public record ChatRequest(
        String model,
        List<ChatMessage> messages,
        List<ToolDefinition> tools,
        double temperature,
        int maxTokens,
        boolean stream,
        Map<String, Object> extensions
) {
}
