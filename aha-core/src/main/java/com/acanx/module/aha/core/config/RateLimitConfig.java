package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 速率限制配置。
 *
 * @param rpm 每分钟请求数
 * @param tpm 每分钟 token 数
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RateLimitConfig(
        @JsonProperty("Rpm") int rpm,
        @JsonProperty("Tpm") int tpm
) {
}
