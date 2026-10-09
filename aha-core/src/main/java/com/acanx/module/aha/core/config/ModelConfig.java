package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模型供应商配置（{@code Model.yml} 的根模型）。
 *
 * <p>与主配置 {@code Aha.yaml} 分离：主配置只保留兜底模型与文件路径，
 * 供应商明细集中在本文件，默认保存路径为 {@code Model.yml}。</p>
 *
 * @param defaultProvider 当前默认（选中）的供应商键名
 * @param defaultTier     全局默认档位（可选，缺省 {@link ModelTier#DEFAULT Standard}）
 * @param providers       供应商表，键名建议 PascalCase（受字段名校验约束）
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelConfig(
        @JsonProperty("Default") String defaultProvider,
        @JsonProperty("DefaultTier") String defaultTier,
        @JsonProperty("Providers") Map<String, ProviderConfig> providers
) {

    /**
     * 兼容构造：未声明全局默认档。
     *
     * @param defaultProvider 默认供应商键名
     * @param providers       供应商表
     */
    public ModelConfig(String defaultProvider, Map<String, ProviderConfig> providers) {
        this(defaultProvider, null, providers);
    }

    /**
     * 可变的供应商表副本。
     *
     * @return 供应商表
     */
    public Map<String, ProviderConfig> providersOrEmpty() {
        return providers == null ? new LinkedHashMap<>() : new LinkedHashMap<>(providers);
    }

    /**
     * 全局默认档。
     *
     * @return 全局默认档；未设置或非法时返回 {@link ModelTier#DEFAULT}
     */
    public ModelTier effectiveDefaultTier() {
        return ModelTier.fromConfigName(defaultTier).orElse(ModelTier.DEFAULT);
    }

    /**
     * 返回仅含指定供应商的新配置。
     *
     * @param id       供应商键名
     * @param provider 供应商配置
     * @return 新配置
     */
    public ModelConfig withProvider(String id, ProviderConfig provider) {
        Map<String, ProviderConfig> merged = providersOrEmpty();
        merged.put(id, provider);
        return new ModelConfig(defaultProvider, defaultTier, merged);
    }

    /**
     * 返回移除指定供应商后的新配置。
     *
     * @param id 供应商键名
     * @return 新配置
     */
    public ModelConfig withoutProvider(String id) {
        Map<String, ProviderConfig> merged = providersOrEmpty();
        merged.remove(id);
        String nextDefault = id.equals(defaultProvider) ? null : defaultProvider;
        return new ModelConfig(nextDefault, defaultTier, merged);
    }

    /**
     * 返回切换默认供应商后的新配置。
     *
     * @param id 供应商键名
     * @return 新配置
     */
    public ModelConfig withDefault(String id) {
        return new ModelConfig(id, defaultTier, providers);
    }

    /**
     * 返回设置全局默认档后的新配置。
     *
     * @param tier 默认档位
     * @return 新配置
     */
    public ModelConfig withDefaultTier(ModelTier tier) {
        return new ModelConfig(defaultProvider, tier == null ? null : tier.configName(), providers);
    }
}
