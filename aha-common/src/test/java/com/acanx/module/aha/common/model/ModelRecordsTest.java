package com.acanx.module.aha.common.model;

import com.acanx.module.aha.common.tool.JsonSchema;
import com.acanx.module.aha.common.tool.ToolPermission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 共享模型 record 测试。
 *
 * @since 0.1.0
 */
class ModelRecordsTest {

    @Test
    void memoryEntryIsValueObject() {
        MemoryEntry entry = new MemoryEntry("key", "value", 123L);

        assertThat(entry.key()).isEqualTo("key");
        assertThat(entry.value()).isEqualTo("value");
        assertThat(entry.createdAt()).isEqualTo(123L);
        assertThat(entry).isEqualTo(new MemoryEntry("key", "value", 123L));
        assertThat(entry.hashCode()).isEqualTo(new MemoryEntry("key", "value", 123L).hashCode());
        assertThat(entry).isNotEqualTo(new MemoryEntry("key", "other", 123L));
        assertThat(entry.toString()).contains("key");
    }

    @Test
    void sessionConfigIsValueObject() {
        SessionConfig config = new SessionConfig("gpt-4o", "you are helpful", Map.of("k", 1));

        assertThat(config.model()).isEqualTo("gpt-4o");
        assertThat(config.systemPrompt()).isEqualTo("you are helpful");
        assertThat(config.extras()).containsEntry("k", 1);
        assertThat(config).isEqualTo(new SessionConfig("gpt-4o", "you are helpful", Map.of("k", 1)));
    }

    @Test
    void toolDescriptorIsValueObject() {
        JsonSchema schema = JsonSchema.object(Map.of("path", JsonSchema.string("路径")), List.of("path"));
        ToolDescriptor descriptor = new ToolDescriptor("file-read", "读取", schema, ToolPermission.READ);

        assertThat(descriptor.name()).isEqualTo("file-read");
        assertThat(descriptor.description()).isEqualTo("读取");
        assertThat(descriptor.parameters()).isSameAs(schema);
        assertThat(descriptor.permission()).isEqualTo(ToolPermission.READ);
        assertThat(descriptor).isEqualTo(
                new ToolDescriptor("file-read", "读取", schema, ToolPermission.READ));
    }

    @Test
    void toolPermissionExposesAllLevels() {
        assertThat(ToolPermission.values()).containsExactly(
                ToolPermission.READ,
                ToolPermission.WRITE,
                ToolPermission.NETWORK,
                ToolPermission.EXECUTE,
                ToolPermission.ADMIN);
        assertThat(ToolPermission.valueOf("EXECUTE")).isEqualTo(ToolPermission.EXECUTE);
        assertThat(ToolPermission.READ.ordinal()).isZero();
    }
}
