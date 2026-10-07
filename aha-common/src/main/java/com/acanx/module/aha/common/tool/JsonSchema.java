package com.acanx.module.aha.common.tool;

import java.util.List;
import java.util.Map;

/**
 * 最小 JSON Schema 表示。
 *
 * @param type        类型
 * @param properties  属性
 * @param required    必需属性
 * @param description 描述
 * @since 0.1.0
 */
public record JsonSchema(
        String type,
        Map<String, JsonSchema> properties,
        List<String> required,
        String description
) {

    /**
     * 构造 object Schema。
     *
     * @param props    属性
     * @param required 必需属性
     * @return Schema
     */
    public static JsonSchema object(Map<String, JsonSchema> props, List<String> required) {
        return new JsonSchema("object", props, required, null);
    }

    /**
     * 构造 string Schema。
     *
     * @param desc 描述
     * @return Schema
     */
    public static JsonSchema string(String desc) {
        return new JsonSchema("string", Map.of(), List.of(), desc);
    }

    /**
     * 构造 integer Schema。
     *
     * @param desc 描述
     * @return Schema
     */
    public static JsonSchema integer(String desc) {
        return new JsonSchema("integer", Map.of(), List.of(), desc);
    }
}
