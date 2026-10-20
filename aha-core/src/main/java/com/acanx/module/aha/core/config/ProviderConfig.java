package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 供应商配置。
 *
 * <p>新格式由三块组成：</p>
 * <ul>
 *   <li>{@code Models}：模型 ID → {@link ModelDefinition}（能力 + 实际模型名）；</li>
 *   <li>{@code Tier}：五档 → 模型 ID，{@code Standard} 作为默认档；</li>
 *   <li>{@code ApiType}：适配器类型（{@code openai-compatible} / {@code anthropic-messages} / {@code gemini}）。</li>
 * </ul>
 *
 * <p>旧格式（{@code Models} 的值为字符串、{@code Adapter}、{@code Model}、{@code DefaultTier}）
 * 在 {@code ConfigLoader} 读取时归一化为新结构；{@link ModelDefinition} 也接受字符串值，
 * 因此即使未经归一化也能读出「档位 → 模型名」的旧语义。</p>
 *
 * <p>默认档固定为 {@link ModelTier#STANDARD}：{@code Standard} 未配置时按
 * 全局 {@code DefaultTier} → 档位强弱顺序兜底，全部缺失才返回 {@code null}（由校验层报错）。</p>
 *
 * @param apiType        适配器类型（写盘为 {@code ApiType}，读取兼容旧键 {@code Adapter}）
 * @param baseUrl        Base URL
 * @param apiKey         API Key
 * @param models         模型 ID → 模型定义
 * @param tier           档位 → 模型 ID
 * @param timeoutSeconds 超时秒数
 * @param maxRetries     最大重试次数
 * @param rateLimit      速率限制
 * @param extra          扩展参数
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProviderConfig(
        @JsonProperty("ApiType")
        @JsonAlias("Adapter")
        String apiType,
        @JsonProperty("BaseUrl") String baseUrl,
        @JsonProperty("ApiKey") String apiKey,
        @JsonProperty("Models") Map<String, ModelDefinition> models,
        @JsonProperty("Tier") Map<String, String> tier,
        @JsonProperty("TimeoutSeconds") int timeoutSeconds,
        @JsonProperty("MaxRetries") int maxRetries,
        @JsonProperty("RateLimit") RateLimitConfig rateLimit,
        @JsonProperty("Extra") Map<String, Object> extra
) {

    /** 旧版 Anthropic 适配器枚举值。 */
    public static final String LEGACY_ANTHROPIC = "anthropic";

    /** 新 Anthropic 适配器枚举值（与 pi agent 对齐）。 */
    public static final String ANTHROPIC_MESSAGES = "anthropic-messages";

    /**
     * 归一化：旧键 {@code Adapter} 已由 {@link JsonAlias} 接收；旧枚举值
     * {@code anthropic} 统一为 {@code anthropic-messages}。
     *
     * <p>旧格式只有 {@code Models}（字符串值）时，自动把合法档位键回填到 {@code Tier}，
     * 保证未经过 {@code ConfigLoader} 归一化的对象也保持旧语义。</p>
     */
    public ProviderConfig {
        apiType = normalizeApiType(apiType);
        if (tier == null && models != null) {
            tier = deriveTier(models);
        }
    }

    /**
     * 兼容构造：仅含单模型的老形态。
     *
     * @param adapter        适配器 ID
     * @param baseUrl        Base URL
     * @param apiKey         API Key
     * @param model          模型名
     * @param timeoutSeconds 超时秒数
     * @param maxRetries     最大重试次数
     * @param rateLimit      速率限制
     * @param extra          扩展参数
     */
    public ProviderConfig(String adapter, String baseUrl, String apiKey, String model,
                          int timeoutSeconds, int maxRetries, RateLimitConfig rateLimit,
                          Map<String, Object> extra) {
        this(adapter, baseUrl, apiKey,
                singleModel(model),
                singleTier(model),
                timeoutSeconds, maxRetries, rateLimit, extra);
    }

    /**
     * 兼容构造：档位 → 模型名（旧 {@code Models} 形态）。
     *
     * <p>模型 ID 直接取档位名，{@code Name} 为传入的模型名；{@code defaultTier} 与
     * {@code model} 仅用于保持旧调用方的语义，写盘时不再输出。</p>
     *
     * @param adapter        适配器 ID
     * @param baseUrl        Base URL
     * @param apiKey         API Key
     * @param tierModels     档位 → 模型名
     * @param defaultTier    旧默认档（忽略）
     * @param model          旧默认模型（忽略）
     * @param timeoutSeconds 超时秒数
     * @param maxRetries     最大重试次数
     * @param rateLimit      速率限制
     * @param extra          扩展参数
     */
    public ProviderConfig(String adapter, String baseUrl, String apiKey,
                          Map<String, String> tierModels, String defaultTier, String model,
                          int timeoutSeconds, int maxRetries, RateLimitConfig rateLimit,
                          Map<String, Object> extra) {
        this(adapter, baseUrl, apiKey,
                definitionsOf(tierModels, model),
                tierOf(tierModels, model),
                timeoutSeconds, maxRetries, rateLimit, extra);
    }

    /**
     * 是否配置了模型表。
     *
     * @return 含模型定义返回 {@code true}
     */
    public boolean hasModels() {
        return models != null && !models.isEmpty();
    }

    /**
     * 模型表副本。
     *
     * @return 模型 ID → 定义；未配置时为空表
     */
    public Map<String, ModelDefinition> modelsOrEmpty() {
        return models == null ? new LinkedHashMap<>() : new LinkedHashMap<>(models);
    }

    /**
     * 档位表副本（档位 → 模型 ID）。
     *
     * @return 档位表；未配置时为空表
     */
    public Map<String, String> tierOrEmpty() {
        return tier == null ? new LinkedHashMap<>() : new LinkedHashMap<>(tier);
    }

    /**
     * 档位 → 模型定义（仅已配置且引用有效的档位）。
     *
     * @return 档位 → 定义
     */
    public Map<String, ModelDefinition> tierDefinitions() {
        Map<String, ModelDefinition> result = new LinkedHashMap<>();
        tierOrEmpty().forEach((name, id) -> {
            ModelDefinition definition = modelsOrEmpty().get(id);
            if (definition != null) {
                result.put(name, definition);
            }
        });
        return result;
    }

    /**
     * 档位 → 实际模型名（{@code Name}）。
     *
     * @return 档位 → 模型名
     */
    public Map<String, String> tierModelNames() {
        Map<String, String> result = new LinkedHashMap<>();
        tierDefinitions().forEach((name, definition) -> {
            if (definition.name() != null) {
                result.put(name, definition.name());
            }
        });
        return result;
    }

    /**
     * 取某档引用的模型 ID（不回退）。
     *
     * @param tier 档位
     * @return 模型 ID；该档未配置时返回 {@code null}
     */
    public String modelIdFor(ModelTier tier) {
        if (tier == null) {
            return null;
        }
        String id = tierOrEmpty().get(tier.configName());
        return blankToNull(id);
    }

    /**
     * 按模型 ID 取定义。
     *
     * @param id 模型 ID
     * @return 定义；不存在时为空
     */
    public Optional<ModelDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(modelsOrEmpty().get(id));
    }

    /**
     * 精确取某档的实际模型名（不回退）。
     *
     * @param tier 档位
     * @return 模型名；该档未配置时返回 {@code null}
     */
    public String configuredModel(ModelTier tier) {
        return definition(modelIdFor(tier)).map(ModelDefinition::name).orElse(null);
    }

    /**
     * 取某档模型：可选档缺失时回退到 {@link ModelTier#STANDARD}，再回退到强弱顺序第一个可用档。
     *
     * @param tier 档位
     * @return 模型名；无任何可用模型时返回 {@code null}
     */
    public String modelFor(ModelTier tier) {
        String direct = configuredModel(tier);
        if (direct != null) {
            return direct;
        }
        if (tier != ModelTier.STANDARD) {
            String standard = standardModel();
            if (standard != null) {
                return standard;
            }
        }
        return firstConfiguredModel();
    }

    /**
     * {@code Standard} 档模型。
     *
     * @return 模型名；未配置时返回 {@code null}
     */
    public String standardModel() {
        return configuredModel(ModelTier.STANDARD);
    }

    /**
     * 解析后的供应商默认档。
     *
     * <p>新格式默认档固定 {@code Standard}；{@code Standard} 未配置时返回空，
     * 由 {@link #effectiveTier(String)} 按全局与强弱顺序兜底。</p>
     *
     * @return 默认档；未配置 {@code Standard} 时为空
     */
    public Optional<ModelTier> resolvedDefaultTier() {
        return configuredModel(ModelTier.STANDARD) == null
                ? Optional.empty()
                : Optional.of(ModelTier.STANDARD);
    }

    /**
     * 解析某模型名 / 模型 ID 所属的档位（用于 {@code aha model use} 反查）。
     *
     * <p>多档同值时按强弱取第一个（{@code Ultra → Pro → Standard → Flash → Fallback}）。</p>
     *
     * @param modelName 模型名或模型 ID
     * @return 所属档位；不在五档内时返回 {@link Optional#empty()}
     */
    public Optional<ModelTier> tierOf(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return Optional.empty();
        }
        for (ModelTier tier : ModelTier.strongestFirst()) {
            String id = modelIdFor(tier);
            if (modelName.equals(id)) {
                return Optional.of(tier);
            }
            String name = configuredModel(tier);
            if (modelName.equals(name)) {
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
     * @param globalDefaultTier 全局默认档（{@code Model.DefaultTier}），可为 {@code null}
     * @return 生效模型名；无法解析时返回 {@code null}
     */
    public String effectiveModel(String globalDefaultTier) {
        return modelFor(effectiveTier(globalDefaultTier));
    }

    /**
     * 解析该供应商的生效档位。
     *
     * <p>顺序：{@code Standard} → 全局 {@code DefaultTier}（该档可用时）→
     * 强弱顺序第一个已配置档 → {@code Standard}（无任何配置）。</p>
     *
     * @param globalDefaultTier 全局默认档，可为 {@code null}
     * @return 生效档位
     */
    public ModelTier effectiveTier(String globalDefaultTier) {
        if (configuredModel(ModelTier.STANDARD) != null) {
            return ModelTier.STANDARD;
        }
        Optional<ModelTier> global = ModelTier.fromConfigName(globalDefaultTier);
        if (global.isPresent() && configuredModel(global.get()) != null) {
            return global.get();
        }
        for (ModelTier tier : ModelTier.strongestFirst()) {
            if (configuredModel(tier) != null) {
                return tier;
            }
        }
        return ModelTier.DEFAULT;
    }

    /**
     * 返回把某档设为默认（{@code Tier.Standard}）的副本。
     *
     * <p>新格式不再写 {@code DefaultTier} / {@code Model}：默认档固定 {@code Standard}，
     * 因此这里把目标档引用的模型 ID 挪到 {@code Standard}。</p>
     *
     * @param tier 目标档位
     * @return 新配置；该档未配置时原样返回
     */
    public ProviderConfig withDefaultTier(ModelTier tier) {
        String id = modelIdFor(tier);
        if (id == null) {
            return this;
        }
        Map<String, String> nextTier = tierOrEmpty();
        nextTier.put(ModelTier.STANDARD.configName(), id);
        return new ProviderConfig(apiType, baseUrl, apiKey, models, nextTier,
                timeoutSeconds, maxRetries, rateLimit, extra);
    }

    /**
     * 返回替换 {@code ApiKey} 的副本（解析占位符后用）。
     *
     * @param resolvedKey 已解析的密钥
     * @return 新配置
     */
    public ProviderConfig withApiKey(String resolvedKey) {
        return new ProviderConfig(apiType, baseUrl, resolvedKey, models, tier,
                timeoutSeconds, maxRetries, rateLimit, extra);
    }

    /**
     * 旧字段兼容：适配器类型。
     *
     * @return {@code ApiType}
     * @deprecated 名字与配置不一致：直接用 {@link #apiType()}（issue #361）
     */
    @Deprecated
    public String adapter() {
        return apiType;
    }

    /**
     * 旧字段兼容：默认模型名（{@code Tier.Standard} 的实际模型名）。
     *
     * @return 模型名；未配置时返回 {@code null}
     */
    public String model() {
        return standardModel();
    }

    /**
     * 旧字段兼容：默认档名（固定 {@code Standard}）。
     *
     * @return {@code Standard}；未配置档位表时返回 {@code null}
     */
    public String defaultTier() {
        return configuredModel(ModelTier.STANDARD) == null ? null : ModelTier.STANDARD.configName();
    }

    /**
     * 当前生效档位的能力定义。
     *
     * @param globalDefaultTier 全局默认档
     * @return 定义；无可用模型时为空
     */
    public Optional<ModelDefinition> effectiveDefinition(String globalDefaultTier) {
        return definition(modelIdFor(effectiveTier(globalDefaultTier)));
    }

    /**
     * 是否支持推理（取生效档）。
     *
     * @param globalDefaultTier 全局默认档
     * @return 支持返回 {@code true}
     */
    public boolean reasoning(String globalDefaultTier) {
        return effectiveDefinition(globalDefaultTier).map(ModelDefinition::reasoning).orElse(false);
    }

    /**
     * 上下文窗口（取生效档）。
     *
     * @param globalDefaultTier 全局默认档
     * @return token 数；未知时返回 {@code null}
     */
    public Integer contextWindow(String globalDefaultTier) {
        return effectiveDefinition(globalDefaultTier).map(ModelDefinition::contextWindow).orElse(null);
    }

    /**
     * 最大输出 token（取生效档）。
     *
     * @param globalDefaultTier 全局默认档
     * @return token 数；未知时返回 {@code null}
     */
    public Integer maxTokens(String globalDefaultTier) {
        return effectiveDefinition(globalDefaultTier).map(ModelDefinition::maxTokens).orElse(null);
    }

    /**
     * 归一化适配器枚举值。
     *
     * @param value 原始值
     * @return {@code anthropic} 统一为 {@code anthropic-messages}，其余原样
     */
    public static String normalizeApiType(String value) {
        if (value == null) {
            return null;
        }
        return LEGACY_ANTHROPIC.equalsIgnoreCase(value) ? ANTHROPIC_MESSAGES : value;
    }

    private String firstConfiguredModel() {
        for (ModelTier tier : ModelTier.strongestFirst()) {
            String model = configuredModel(tier);
            if (model != null) {
                return model;
            }
        }
        return null;
    }

    private static Map<String, ModelDefinition> singleModel(String model) {
        return model == null || model.isBlank()
                ? null
                : Map.of(model, ModelDefinition.ofName(model));
    }

    private static Map<String, String> singleTier(String model) {
        return model == null || model.isBlank()
                ? null
                : Map.of(ModelTier.STANDARD.configName(), model);
    }

    private static Map<String, ModelDefinition> definitionsOf(Map<String, String> tierModels,
                                                              String model) {
        if (tierModels != null && !tierModels.isEmpty()) {
            Map<String, ModelDefinition> result = new LinkedHashMap<>();
            tierModels.forEach((tier, name) -> {
                if (name != null && !name.isBlank()) {
                    result.put(tier, ModelDefinition.ofName(name));
                }
            });
            return result;
        }
        return singleModel(model);
    }

    private static Map<String, String> tierOf(Map<String, String> tierModels, String model) {
        if (tierModels != null && !tierModels.isEmpty()) {
            Map<String, String> result = new LinkedHashMap<>();
            tierModels.forEach((tier, name) -> {
                if (name != null && !name.isBlank()) {
                    result.put(tier, tier);
                }
            });
            return result;
        }
        return singleTier(model);
    }

    /**
     * 旧格式（模型 ID 即档位名）自动回填 {@code Tier}。
     *
     * @param models 模型表
     * @return 档位表；未发现档位键时返回 {@code null}
     */
    private static Map<String, String> deriveTier(Map<String, ModelDefinition> models) {
        Map<String, String> result = new LinkedHashMap<>();
        for (ModelTier tier : ModelTier.strongestFirst()) {
            if (models.containsKey(tier.configName())) {
                result.put(tier.configName(), tier.configName());
            }
        }
        return result.isEmpty() ? null : result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
