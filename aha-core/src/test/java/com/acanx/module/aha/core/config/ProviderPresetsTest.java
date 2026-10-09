package com.acanx.module.aha.core.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ProviderPresets} 测试。
 *
 * @since 0.1.0
 */
class ProviderPresetsTest {

    @Test
    void builtinContainsDomesticProviders() {
        var presets = ProviderPresets.builtin();

        assertThat(presets).containsKeys(ProviderPresets.DEEPSEEK, ProviderPresets.BIG_MODEL_CN, ProviderPresets.QWEN);
        assertThat(presets).containsKeys(ProviderPresets.MOONSHOT, ProviderPresets.MINI_MAX);
        assertThat(presets.get(ProviderPresets.DEEPSEEK).adapter()).isEqualTo("openai-compatible");
        assertThat(presets.get(ProviderPresets.DEEPSEEK).baseUrl()).contains("deepseek.com");
        assertThat(presets.get(ProviderPresets.BIG_MODEL_CN).baseUrl()).contains("bigmodel.cn");
        assertThat(presets.get(ProviderPresets.QWEN).baseUrl()).contains("dashscope.aliyuncs.com");
        assertThat(presets.values()).allSatisfy(provider -> {
            // 新规则：每家的 Standard 档必须具名
            assertThat(provider.standardModel()).isNotBlank();
            assertThat(provider.apiKey()).startsWith("${");
            assertThat(provider.timeoutSeconds()).isPositive();
            assertThat(provider.maxRetries()).isPositive();
        });
    }

    @Test
    void userConfigOverridesPreset() {
        ProviderConfig custom = new ProviderConfig("openai-compatible",
                "https://custom.example/v1", "key", "custom-model", 30, 1,
                new RateLimitConfig(10, 100), java.util.Map.of());
        LlmConfig user = new LlmConfig("DeepSeek",
                java.util.Map.of(ProviderPresets.DEEPSEEK, custom), null, null);

        LlmConfig merged = ProviderPresets.applyDefaults(user);

        assertThat(merged.defaultProvider()).isEqualTo("DeepSeek");
        assertThat(merged.providers().get(ProviderPresets.DEEPSEEK).baseUrl())
                .isEqualTo("https://custom.example/v1");
        assertThat(merged.providers()).containsKeys(ProviderPresets.BIG_MODEL_CN, ProviderPresets.QWEN);
    }

    @Test
    void defaultsAppliedWhenUserConfigMissing() {
        LlmConfig merged = ProviderPresets.applyDefaults(null);

        assertThat(merged.defaultProvider()).isEqualTo("OpenAI");
        assertThat(merged.providers()).containsKeys(
                ProviderPresets.DEEPSEEK, ProviderPresets.BIG_MODEL_CN, ProviderPresets.QWEN);
    }
}
