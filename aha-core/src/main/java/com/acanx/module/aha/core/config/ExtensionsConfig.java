package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 扩展配置。
 *
 * @param enabled  是否启用
 * @param path     扩展目录
 * @param autoLoad 是否自动加载
 * @param disabled 禁用列表（扩展 ID）
 * @param settings 扩展设置
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtensionsConfig(
        @JsonProperty("Enabled") boolean enabled,
        @JsonProperty("Path") String path,
        @JsonProperty("AutoLoad") boolean autoLoad,
        @JsonProperty("Disabled") List<String> disabled,
        @JsonProperty("Settings") Map<String, Map<String, Object>> settings
) {
}
