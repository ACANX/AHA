package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 工具配置。
 *
 * @param enabled     启用的工具
 * @param autoApprove 在默认策略（READ/NETWORK）之上额外自动放行的权限，
 *                    取值如 {@code WRITE}/{@code EXECUTE}/{@code ADMIN}/{@code ALL}；
 *                    为 {@code null} 时仅使用默认策略
 * @param shell       Shell 配置
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ToolsConfig(
        @JsonProperty("Enabled") List<String> enabled,
        @JsonProperty("AutoApprove") List<String> autoApprove,
        @JsonProperty("Shell") ShellConfig shell
) {
}
