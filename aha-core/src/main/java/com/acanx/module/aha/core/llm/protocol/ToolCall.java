package com.acanx.module.aha.core.llm.protocol;

import java.util.Map;

/**
 * IR 工具调用。
 *
 * @param id        调用 ID
 * @param name      工具名
 * @param arguments 参数
 * @since 0.1.0
 */
public record ToolCall(String id, String name, Map<String, Object> arguments) {
}
