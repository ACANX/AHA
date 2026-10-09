package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * LLM 配置。
 *
 * <p>自 0.1.0 起，供应商明细与主配置分离：主配置只声明兜底模型（{@code Fallback}）
 * 与模型配置文件路径（{@code ModelFile}），供应商定义集中在 {@code Model.yml}。
 * {@code Providers}/{@code DefaultProvider} 保留用于内联覆盖与向后兼容。</p>
 *
 * @param defaultProvider 默认供应商键名（内联兼容；通常由 {@code Model.yml} 的 {@code Default} 提供）
 * @param defaultTier     全局默认档位（通常由 {@code Model.yml} 的 {@code DefaultTier} 提供）
 * @param providers       内联供应商表（兼容用；与 {@code Model.yml} 合并，后者优先）
 * @param fallback        兜底（降级回退）模型配置
 * @param modelFile       模型供应商配置文件路径，支持 {@code ${ENV}} 占位
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LlmConfig(
        @JsonProperty("DefaultProvider") String defaultProvider,
        @JsonProperty("DefaultTier") String defaultTier,
        @JsonProperty("Providers") Map<String, ProviderConfig> providers,
        @JsonProperty("Fallback") FallbackConfig fallback,
        @JsonProperty("ModelFile") String modelFile
) {

    /**
     * 兼容构造：未声明全局默认档。
     *
     * @param defaultProvider 默认供应商键名
     * @param providers       内联供应商表
     * @param fallback        兜底模型配置
     * @param modelFile       模型配置文件路径
     */
    public LlmConfig(String defaultProvider, Map<String, ProviderConfig> providers,
                     FallbackConfig fallback, String modelFile) {
        this(defaultProvider, null, providers, fallback, modelFile);
    }

    /**
     * 兜底供应商键名。
     *
     * @return 供应商键名，未配置时为 {@code null}
     */
    public String fallbackProvider() {
        return fallback == null ? null : fallback.provider();
    }

    /**
     * 兜底模型名。
     *
     * @return 模型名，未配置时为 {@code null}
     */
    public String fallbackModel() {
        return fallback == null ? null : fallback.model();
    }

    /**
     * 全局默认档（非法或未设置时回退到 {@link ModelTier#DEFAULT Standard}）。
     *
     * @return 全局默认档
     */
    public ModelTier effectiveDefaultTier() {
        return ModelTier.fromConfigName(defaultTier).orElse(ModelTier.DEFAULT);
    }
}
