package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.config.RateLimitConfig;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ProviderForm} 测试：供应商表单的校验与写回（无图形环境）。
 *
 * @since 0.2.0
 */
class ProviderFormTest {

    private static final ProviderConfig DEEPSEEK = new ProviderConfig(
            "openai-compatible", "https://api.deepseek.com/v1", "sk-abcdef123456",
            "deepseek-chat", 60, 2, null, Map.of("k", "v"));

    @Test
    void draftFromExistingConfigKeepsValues() {
        ProviderForm.Draft draft = ProviderForm.of("DeepSeek", DEEPSEEK);

        assertThat(draft.id()).isEqualTo("DeepSeek");
        assertThat(draft.adapter()).isEqualTo("openai-compatible");
        assertThat(draft.baseUrl()).isEqualTo("https://api.deepseek.com/v1");
        assertThat(draft.model()).isEqualTo("deepseek-chat");
        assertThat(draft.timeoutSeconds()).isEqualTo("60");
        assertThat(draft.maxRetries()).isEqualTo("2");
    }

    @Test
    void draftFromNullGivesUsableDefaults() {
        ProviderForm.Draft draft = ProviderForm.of("New", null);

        assertThat(draft.adapter()).isEqualTo(ProviderForm.ADAPTERS[0]);
        assertThat(draft.timeoutSeconds()).isEqualTo("60");
        assertThat(draft.maxRetries()).isEqualTo("2");
        assertThat(ProviderForm.validate(draft))
                .as("只缺 baseUrl/model 时应只报这两项")
                .containsOnlyKeys("baseUrl", "model");
    }

    @Test
    void validateRejectsBadInput() {
        ProviderForm.Draft bad = new ProviderForm.Draft(
                "有 空格", "openai-compatible", "api.deepseek.com", "k", "", "abc", "-1");

        Map<String, String> errors = ProviderForm.validate(bad);

        assertThat(errors).containsKeys("id", "baseUrl", "model", "timeoutSeconds", "maxRetries");
    }

    @Test
    void validateAcceptsGoodInput() {
        assertThat(ProviderForm.validate(ProviderForm.of("DeepSeek", DEEPSEEK))).isEmpty();
    }

    @Test
    void toConfigTrimsAndPreservesUntouchedFields() {
        ProviderForm.Draft draft = new ProviderForm.Draft(
                "DeepSeek", " openai-compatible ", " https://x/v1 ", " sk-1 ", " m1 ", "30", "3");

        ProviderConfig config = ProviderForm.toConfig(draft, DEEPSEEK);

        assertThat(config.adapter()).isEqualTo("openai-compatible");
        assertThat(config.baseUrl()).isEqualTo("https://x/v1");
        assertThat(config.apiKey()).isEqualTo("sk-1");
        assertThat(config.model()).isEqualTo("m1");
        assertThat(config.timeoutSeconds()).isEqualTo(30);
        assertThat(config.maxRetries()).isEqualTo(3);
        // 表单没涉及的字段不能因为保存而丢掉
        assertThat(config.extra()).isEqualTo(DEEPSEEK.extra());
    }

    @Test
    void toConfigWorksForBrandNewProvider() {
        ProviderForm.Draft draft = ProviderForm.of("Fresh", null);
        ProviderConfig config = ProviderForm.toConfig(
                new ProviderForm.Draft("Fresh", "gemini", "https://g/v1", "", "gemini-2.5-flash", "10", "0"),
                null);

        assertThat(config.adapter()).isEqualTo("gemini");
        assertThat(config.rateLimit()).isNull();
        assertThat(config.extra()).isNull();
    }

    @Test
    void maskHidesKeyBodies() {
        assertThat(ProviderForm.mask(null)).isEqualTo("（未设置）");
        assertThat(ProviderForm.mask("short")).isEqualTo("（已设置，短值）");
        assertThat(ProviderForm.mask("sk-abcdef123456")).isEqualTo("sk-a…3456");
    }

    @Test
    void presetsMatchRepositoryDefaults() {
        // 预设内容必须与 aha-core/src/main/resources/ModelDefault.yml 一致（模型名不得编造）
        assertThat(ProviderForm.preset("DeepSeek").baseUrl()).isEqualTo("https://api.deepseek.com/v1");
        assertThat(ProviderForm.preset("DeepSeek").model()).isEqualTo("deepseek-chat");
        assertThat(ProviderForm.preset("OpenAI").model()).isEqualTo("gpt-4o");
        assertThat(ProviderForm.preset("Anthropic").adapter()).isEqualTo("anthropic");
        assertThat(ProviderForm.preset("Gemini").adapter()).isEqualTo("gemini");
        assertThat(ProviderForm.preset("BigModelCN").model()).isEqualTo("glm-4.6");
        assertThat(ProviderForm.preset("Qwen").model()).isEqualTo("qwen-max");
    }

    @Test
    void presetDraftIsValidExceptApiKey() {
        // 预设新建后只差密钥：其余字段应当直接可用
        assertThat(ProviderForm.validate(ProviderForm.preset("Gemini"))).isEmpty();
    }

    @Test
    void unknownPresetFallsBackToBlankTemplate() {
        ProviderForm.Draft draft = ProviderForm.preset("MyOwnGateway");

        assertThat(draft.id()).isEqualTo("MyOwnGateway");
        assertThat(draft.adapter()).isEqualTo(ProviderForm.ADAPTERS[0]);
        assertThat(draft.model()).isEmpty();
        assertThat(ProviderForm.modelsFor("MyOwnGateway")).isEmpty();
    }

    @Test
    void modelCandidatesAreAvailableForKnownProviders() {
        assertThat(ProviderForm.modelsFor("DeepSeek")).contains("deepseek-chat");
        assertThat(ProviderForm.modelsFor("OpenAI")).contains("gpt-4o");
        assertThat(ProviderForm.modelsFor(null)).isEmpty();
        assertThat(ProviderForm.knownProviders()).contains("DeepSeek", "Qwen");
    }

    @Test
    void rateLimitIsTyped() {
        ProviderConfig withLimit = new ProviderConfig(
                "openai-compatible", "https://x/v1", "k", "m", 60, 2,
                new RateLimitConfig(10, 60), null);
        ProviderForm.Draft draft = ProviderForm.of("X", withLimit);

        assertThat(ProviderForm.toConfig(draft, withLimit).rateLimit())
                .isEqualTo(new RateLimitConfig(10, 60));
    }
}
