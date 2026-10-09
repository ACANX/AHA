package com.acanx.module.aha.core.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 档位解析与回退测试（{@link ProviderConfig} / {@link ModelTier}）。
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
    void legacyConfigUsesModelField() {
        ProviderConfig legacy = new ProviderConfig("openai-compatible", "https://example.com/v1",
                "key", "deepseek-chat", 120, 3, null, Map.of());

        assertThat(legacy.hasModels()).isFalse();
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
    void effectiveModelFollowsDefaultTier() {
        ProviderConfig provider = newStyle("Pro", null,
                "Ultra", "ultra-model",
                "Pro", "pro-model",
                "Standard", "std-model");

        assertThat(provider.effectiveTier("Standard")).isEqualTo(ModelTier.PRO);
        assertThat(provider.effectiveModel("Standard")).isEqualTo("pro-model");
        // 供应商未设默认档时看全局
        ProviderConfig noDefault = newStyle(null, null, "Standard", "std-model", "Ultra", "ultra-model");
        assertThat(noDefault.effectiveTier("Ultra")).isEqualTo(ModelTier.ULTRA);
        assertThat(noDefault.effectiveModel("Ultra")).isEqualTo("ultra-model");
        // 全局也没有时兜底 Standard
        assertThat(noDefault.effectiveTier(null)).isEqualTo(ModelTier.STANDARD);
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
    void withDefaultTierSyncsModelField() {
        ProviderConfig provider = newStyle("Standard", "std-model",
                "Ultra", "ultra-model",
                "Standard", "std-model");

        ProviderConfig updated = provider.withDefaultTier(ModelTier.ULTRA);
        assertThat(updated.defaultTier()).isEqualTo("Ultra");
        assertThat(updated.model()).isEqualTo("ultra-model");
    }
}
