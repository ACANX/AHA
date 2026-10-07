package com.acanx.module.aha.core.llm.protocol;

import com.acanx.module.aha.common.tool.JsonSchema;

/**
 * IR 工具定义。
 *
 * @param name        工具名
 * @param description 描述
 * @param parameters  参数 Schema
 * @since 0.1.0
 */
public record ToolDefinition(String name, String description, JsonSchema parameters) {
}
