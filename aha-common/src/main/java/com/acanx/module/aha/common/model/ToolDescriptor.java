package com.acanx.module.aha.common.model;

import com.acanx.module.aha.common.tool.JsonSchema;
import com.acanx.module.aha.common.tool.ToolPermission;

/**
 * 工具描述符。
 *
 * @param name       工具名
 * @param description 描述
 * @param parameters  参数 Schema
 * @param permission  所需权限
 * @since 0.1.0
 */
public record ToolDescriptor(String name, String description, JsonSchema parameters, ToolPermission permission) {
}
