package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 记忆配置。
 *
 * @param storage          存储类型
 * @param path             存储路径
 * @param maxContextEntries 最大上下文条目数
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MemoryConfig(
        @JsonProperty("Storage") String storage,
        @JsonProperty("Path") String path,
        @JsonProperty("MaxContextEntries") int maxContextEntries
) {
}
