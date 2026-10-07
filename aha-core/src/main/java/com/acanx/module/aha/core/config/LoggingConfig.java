package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 日志配置。
 *
 * @param level 日志级别
 * @param file  日志文件
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoggingConfig(
        @JsonProperty("Level") String level,
        @JsonProperty("File") String file
) {
}
