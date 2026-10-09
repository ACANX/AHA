package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 供应商配置。
 *
 * <p>支持两种配置形态，用「是否含 {@code Models}」区分：</p>
 * <ul>
 *   <li><b>老规则</b>：只有 {@code Model}，行为与升级前一致，{@code Model} 即默认模型；</li>
 *   <li><b>新规则</b>：含 {@code Models} 档位表，{@code DefaultTier} 优先，{@code Model}
 *       保留为「用户配置的默认模型」的记录，写盘时与 {@code DefaultTier} 保持一致。</li>
 * </ul>
 *
 * <p>档位解析回退逻辑收敛在本类，避免散落在服务 / 客户端 / CLI / 桌面端各处。</p>
 *
 * @param adapter        适配器 ID
 * @param baseUrl        Base URL
 * @param apiKey         API Key
 * @param models         档位 → 模型名（{@code Standard} 为必填档）
 * @param defaultTier    供应商级默认档（新规则下优先于 {@code Model}）
 * @param model          用户配置的默认模型（老规则下即生效模型；新规则下为记录）
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
        @JsonProperty("Models") Map<String, String> models,
        @JsonProperty("DefaultTier") String defaultTier,
        @JsonProperty("Model") String model,
        @JsonProperty("TimeoutSeconds") int timeoutSeconds,
        @JsonProperty("MaxRetries") int maxRetries,
        @JsonProperty("RateLimit") RateLimitConfig rateLimit,
        @JsonProperty("Extra") Map<String, Object> extra
) {

    /**
     * 兼容构造：仅含单模型的老形态（{@code Models} / {@code DefaultTier} 为空）。
     *
     * @param adapter        适配器 ID
     * @param baseUrl        Base URL
     * @param apiKey         API Key
     * @param model          模型
     * @param timeoutSeconds 超时秒数
     * @param maxRetries     最大重试次数
     * @param rateLimit      速率限制
     * @param extra          扩展参数
     */
    public ProviderConfig(String adapter, String baseUrl, String apiKey, String model,
                          int timeoutSeconds, int maxRetries, RateLimitConfig rateLimit,
                          Map<String, Object> extra) {
        this(adapter, baseUrl, apiKey, null, null, model, timeoutSeconds, maxRetries, rateLimit, extra);
    }

    /**
     * 是否为新规则配置（含 {@code Models} 且非空）。
     *
     * @return 含档位表返回 {@code true}
     */
    public boolean hasModels() {
        return models != null && !models.isEmpty();
    }

    /**
     * 档位表副本。
     *
     * @return 档位表；未配置时为空表
     */
    public Map<String, String> modelsOrEmpty() {
        return models == null ? Map.of() : new LinkedHashMap<>(models);
    }

    /**
     * 精确取某档配置的模型（不回退）。
     *
     * @param tier 档位
     * @return 模型名；该档未配置时返回 {@code null}
     */
    public String configuredModel(ModelTier tier) {
        if (tier == null) {
            return null;
        }
        if (!hasModels()) {
            // 老规则没有档位概念：Standard 视为 Model，其余档不可直接选用
            return tier == ModelTier.STANDARD ? blankToNull(model) : null;
        }
        return blankToNull(models.get(tier.configName()));
    }

    /**
     * 取某档模型：可选档缺失时回退到 {@link ModelTier#STANDARD}。
     *
     * @param tier 档位
     * @return 模型名；新规则下 {@code Standard} 也缺失时返回 {@code null}（非法配置）
     */
    public String modelFor(ModelTier tier) {
        String direct = configuredModel(tier);
        if (direct != null) {
            return direct;
        }
        if (tier == ModelTier.STANDARD) {
            return null;
        }
        return standardModel();
    }

    /**
     * {@code Standard} 档模型（唯一必填档）。
     *
     * @return 模型名；老配置下为 {@code Model}，新规则下缺失时为 {@code null}
     */
    public String standardModel() {
        return configuredModel(ModelTier.STANDARD);
    }

    /**
     * 解析后的供应商级默认档。
     *
     * <p>新规则下取 {@code DefaultTier}；非法或未设置时由调用方回退到全局默认档。</p>
     *
     * @return 默认档；未设置或非法时返回 {@link Optional#empty()}
     */
    public Optional<ModelTier> resolvedDefaultTier() {
        return ModelTier.fromConfigName(defaultTier);
    }

    /**
     * 解析某模型名所属的档位（用于 {@code aha model use <模型名>} 反查）。
     *
     * <p>多档同值时按强弱取第一个（{@code Ultra → Pro → Standard → Flash → Fallback}）。</p>
     *
     * @param modelName 模型名
     * @return 所属档位；不在五档内时返回 {@link Optional#empty()}
     */
    public Optional<ModelTier> tierOf(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return Optional.empty();
        }
        for (ModelTier tier : ModelTier.strongestFirst()) {
            if (modelName.equals(configuredModel(tier))) {
                return Optional.of(tier);
            }
        }
        return Optional.empty();
    }

    /**
     * 该供应商所有已配置的档位（按强弱顺序）。
     *
     * @return 已配置档位
     */
    public java.util.List<ModelTier> configuredTiers() {
        java.util.List<ModelTier> tiers = new java.util.ArrayList<>();
        for (ModelTier tier : ModelTier.strongestFirst()) {
            if (configuredModel(tier) != null) {
                tiers.add(tier);
            }
        }
        return tiers;
    }

    /**
     * 解析该供应商的生效模型。
     *
     * <p>老规则（无 {@code Models}）下 {@code Model} 直接生效；新规则下按
     * {@code DefaultTier → 全局 DefaultTier → Standard} 解析档位并取模型。</p>
     *
     * @param globalDefaultTier 全局默认档（{@code Model.DefaultTier}），可为 {@code null}
     * @return 生效模型名；无法解析时返回 {@code null}
     */
    public String effectiveModel(String globalDefaultTier) {
        if (!hasModels()) {
            return blankToNull(model);
        }
        return modelFor(effectiveTier(globalDefaultTier));
    }

    /**
     * 解析该供应商的生效档位。
     *
     * @param globalDefaultTier 全局默认档，可为 {@code null}
     * @return 生效档位；老规则下返回 {@link ModelTier#STANDARD}
     */
    public ModelTier effectiveTier(String globalDefaultTier) {
        return resolvedDefaultTier()
                .or(() -> ModelTier.fromConfigName(globalDefaultTier))
                .orElse(ModelTier.DEFAULT);
    }

    /**
     * 返回把 {@code Model} 同步为某档模型的副本（写盘时保持两者一致）。
     *
     * @param tier 目标档位
     * @return 新配置；该档未配置时原样返回
     */
    public ProviderConfig withDefaultTier(ModelTier tier) {
        if (tier == null) {
            return this;
        }
        String resolved = modelFor(tier);
        if (resolved == null) {
            return this;
        }
        Map<String, String> nextModels = models == null ? null : new LinkedHashMap<>(models);
        return new ProviderConfig(adapter, baseUrl, apiKey, nextModels, tier.configName(), resolved,
                timeoutSeconds, maxRetries, rateLimit, extra);
    }

    /**
     * 返回替换 {@code ApiKey} 的副本（解析占位符后用）。
     *
     * @param resolvedKey 已解析的密钥
     * @return 新配置
     */
    public ProviderConfig withApiKey(String resolvedKey) {
        return new ProviderConfig(adapter, baseUrl, resolvedKey, models, defaultTier, model,
                timeoutSeconds, maxRetries, rateLimit, extra);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
