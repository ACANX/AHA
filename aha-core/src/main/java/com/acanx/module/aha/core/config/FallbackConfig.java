package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 兜底（降级回退）模型配置。
 *
 * <p>当 {@code Model.yml} 缺失、默认供应商不存在或调用失败时，回退到该供应商与模型。</p>
 *
 * @param provider 兜底供应商键名
 * @param model    兜底模型名，为 {@code null} 时使用供应商自身配置的模型
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FallbackConfig(
        @JsonProperty("Provider") String provider,
        @JsonProperty("Model") String model
) {
}
