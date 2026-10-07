package com.acanx.module.aha.core.llm.protocol;

/**
 * 流式工具调用增量。
 *
 * @param index          序号
 * @param id             调用 ID（可能缺失）
 * @param name           工具名（可能缺失）
 * @param argumentsDelta 参数增量
 * @since 0.1.0
 */
public record ToolCallDelta(int index, String id, String name, String argumentsDelta) {
}
