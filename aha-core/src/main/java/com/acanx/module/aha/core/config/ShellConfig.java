package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Shell 工具配置。
 *
 * @param allowedCommands 允许的命令
 * @param timeoutSeconds  超时秒数
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ShellConfig(
        @JsonProperty("AllowedCommands") List<String> allowedCommands,
        @JsonProperty("TimeoutSeconds") int timeoutSeconds
) {
}
