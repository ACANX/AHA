package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 供应商配置。
 *
 * @param adapter        适配器 ID
 * @param baseUrl        Base URL
 * @param apiKey         API Key
 * @param model          模型
 * @param timeoutSeconds 超时秒数
 * @param maxRetries     最大重试次数
 * @param rateLimit      速率限制
 * @param extra          扩展参数
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderConfig(
        @JsonProperty("Adapter") String adapter,
        @JsonProperty("BaseUrl") String baseUrl,
        @JsonProperty("ApiKey") String apiKey,
        @JsonProperty("Model") String model,
        @JsonProperty("TimeoutSeconds") int timeoutSeconds,
        @JsonProperty("MaxRetries") int maxRetries,
        @JsonProperty("RateLimit") RateLimitConfig rateLimit,
        @JsonProperty("Extra") Map<String, Object> extra
) {
}
