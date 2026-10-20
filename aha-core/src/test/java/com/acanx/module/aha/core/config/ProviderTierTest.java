package com.acanx.module.aha.core.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 档位解析与回退测试（{@link ProviderConfig} / {@link ModelTier}）。
 *
 * <p>新格式默认档固定 {@code Standard}；{@code Standard} 缺失时按
 * 全局 {@code DefaultTier} → 档位强弱顺序兜底。</p>
 *
 * @since 0.1.0
 */
class ProviderTierTest {

    private static ProviderConfig newStyle(String defaultTier, String model, String... tiers) {
        Map<String, String> models = new LinkedHashMap<>();
        for (int i = 0; i < tiers.length; i += 2) {
            models.put(tiers[i], tiers[i + 1]);
        }
        return new ProviderConfig("openai-compatible", "https://example.com/v1", "key",
                models, defaultTier, model, 120, 3, null, Map.of());
    }

    @Test
    void tierNameIsCaseSensitiveWhitelist() {
        assertThat(ModelTier.fromConfigName("Standard")).contains(ModelTier.STANDARD);
        assertThat(ModelTier.fromConfigName("standard")).isEmpty();
        assertThat(ModelTier.fromConfigName("pro")).isEmpty();
        assertThat(ModelTier.isTierName("MiniMax-M3")).isFalse();
        assertThat(ModelTier.strongestFirst()).containsExactly(
                ModelTier.ULTRA, ModelTier.PRO, ModelTier.STANDARD, ModelTier.FLASH, ModelTier.FALLBACK);
    }

    @Test
    void legacySingleModelIsMigratedButKeepsBehavior() {
        ProviderConfig legacy = new ProviderConfig("openai-compatible", "https://example.com/v1",
                "key", "deepseek-chat", 120, 3, null, Map.of());

        // 单模型构造归一化为「一个模型定义 + Tier.Standard」
        assertThat(legacy.hasModels()).isTrue();
        assertThat(legacy.effectiveModel("Standard")).isEqualTo("deepseek-chat");
        assertThat(legacy.standardModel()).isEqualTo("deepseek-chat");
        assertThat(legacy.modelFor(ModelTier.ULTRA)).isEqualTo("deepseek-chat");
        assertThat(legacy.effectiveTier("Standard")).isEqualTo(ModelTier.STANDARD);
    }

    @Test
    void optionalTierFallsBackToStandard() {
        ProviderConfig provider = newStyle("Standard", null,
                "Standard", "std-model",
                "Ultra", "ultra-model");

        assertThat(provider.modelFor(ModelTier.STANDARD)).isEqualTo("std-model");
        assertThat(provider.modelFor(ModelTier.ULTRA)).isEqualTo("ultra-model");
        // Pro / Flash / Fallback 未配置：回退 Standard，不报错
        assertThat(provider.modelFor(ModelTier.PRO)).isEqualTo("std-model");
        assertThat(provider.modelFor(ModelTier.FLASH)).isEqualTo("std-model");
        assertThat(provider.modelFor(ModelTier.FALLBACK)).isEqualTo("std-model");
        // 精确取值仍能区分「未配置」
        assertThat(provider.configuredModel(ModelTier.PRO)).isNull();
    }

    @Test
    void effectiveModelPrefersStandard() {
        ProviderConfig provider = newStyle("Pro", null,
                "Ultra", "ultra-model",
                "Pro", "pro-model",
                "Standard", "std-model");

        // 供应商旧的 DefaultTier 不再生效：默认档固定 Standard
        assertThat(provider.effectiveTier("Standard")).isEqualTo(ModelTier.STANDARD);
        assertThat(provider.effectiveModel("Standard")).isEqualTo("std-model");

        // Standard 缺失：先看全局 DefaultTier
        ProviderConfig noStandard = newStyle("Pro", null,
                "Ultra", "ultra-model",
                "Pro", "pro-model");
        assertThat(noStandard.effectiveTier("Pro")).isEqualTo(ModelTier.PRO);
        assertThat(noStandard.effectiveModel("Pro")).isEqualTo("pro-model");

        // 全局也没有：按强弱顺序取第一个
        assertThat(noStandard.effectiveTier(null)).isEqualTo(ModelTier.ULTRA);
        assertThat(noStandard.effectiveModel(null)).isEqualTo("ultra-model");
    }

    @Test
    void tierOfReturnsStrongestOnTies() {
        ProviderConfig provider = newStyle("Standard", null,
                "Ultra", "same",
                "Pro", "same",
                "Standard", "same",
                "Flash", "flash-model");

        assertThat(provider.tierOf("same")).contains(ModelTier.ULTRA);
        assertThat(provider.tierOf("flash-model")).contains(ModelTier.FLASH);
        assertThat(provider.tierOf("unknown")).isEmpty();
    }

    @Test
    void withDefaultTierMovesModelToStandard() {
        ProviderConfig provider = newStyle("Standard", "std-model",
                "Ultra", "ultra-model",
                "Standard", "std-model");

        ProviderConfig updated = provider.withDefaultTier(ModelTier.ULTRA);
        assertThat(updated.defaultTier()).isEqualTo("Standard");
        assertThat(updated.model()).isEqualTo("ultra-model");
        assertThat(updated.standardModel()).isEqualTo("ultra-model");
    }

    @Test
    void apiTypeNormalizesLegacyAnthropic() {
        ProviderConfig legacy = new ProviderConfig("anthropic", "https://api.anthropic.com", "k",
                "claude-sonnet", 60, 2, null, Map.of());

        assertThat(legacy.apiType()).isEqualTo("anthropic-messages");
        assertThat(legacy.apiType()).isEqualTo("anthropic-messages");
    }

    @Test
    void newFormatReadsDefinitionsAndCapabilities() {
        ModelDefinition definition = new ModelDefinition("real-model", true,
                List.of("text", "image"), 100000, 8000, Map.of("Max", "max"));
        ProviderConfig provider = new ProviderConfig("openai-compatible", "https://example.com/v1",
                "k", Map.of("fast", definition), Map.of("Standard", "fast"),
                60, 2, null, Map.of());

        assertThat(provider.configuredModel(ModelTier.STANDARD)).isEqualTo("real-model");
        assertThat(provider.tierModelNames()).containsEntry("Standard", "real-model");
        assertThat(provider.reasoning("Standard")).isTrue();
        assertThat(provider.contextWindow("Standard")).isEqualTo(100000);
        assertThat(provider.maxTokens("Standard")).isEqualTo(8000);
        assertThat(provider.definition("fast").orElseThrow().thinkingValue("Max")).contains("max");
        assertThat(provider.definition("fast").orElseThrow().supportsImage()).isTrue();
    }
}
