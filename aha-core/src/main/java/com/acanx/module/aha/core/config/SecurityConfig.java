package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 安全配置。
 *
 * @param keyStore     密钥库类型
 * @param keyStorePath 密钥库路径
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SecurityConfig(
        @JsonProperty("KeyStore") String keyStore,
        @JsonProperty("KeyStorePath") String keyStorePath
) {
}
