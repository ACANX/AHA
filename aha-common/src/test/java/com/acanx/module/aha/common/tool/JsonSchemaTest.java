package com.acanx.module.aha.common.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link JsonSchema} 测试。
 *
 * @since 0.1.0
 */
class JsonSchemaTest {

    @Test
    void buildsObjectSchema() {
        JsonSchema schema = JsonSchema.object(
                Map.of("path", JsonSchema.string("文件路径")),
                List.of("path"));

        assertThat(schema.type()).isEqualTo("object");
        assertThat(schema.properties()).containsKey("path");
        assertThat(schema.required()).containsExactly("path");
        assertThat(schema.description()).isNull();
    }

    @Test
    void buildsScalarSchemas() {
        assertThat(JsonSchema.string("desc").type()).isEqualTo("string");
        assertThat(JsonSchema.string("desc").description()).isEqualTo("desc");
        assertThat(JsonSchema.integer("count").type()).isEqualTo("integer");
        assertThat(JsonSchema.integer("count").properties()).isEmpty();
    }
}
