package com.acanx.module.aha.core.llm.protocol;

/**
 * IR Token 用量。
 *
 * @param promptTokens     提示 token 数
 * @param completionTokens 补全 token 数
 * @param totalTokens      总 token 数
 * @param reasoningTokens  思维链 token 数（可空）
 * @since 0.1.0
 */
public record Usage(int promptTokens, int completionTokens, int totalTokens, Integer reasoningTokens) {
}
