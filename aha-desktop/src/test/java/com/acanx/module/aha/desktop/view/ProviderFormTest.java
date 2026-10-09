package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.config.RateLimitConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void saveWritesProviderThatCoreCanReadBack(@TempDir java.nio.file.Path tempDir) throws Exception {
        // 这条测试直接对着「点了新增没反应」这个事故：不仅写入，还要能被 core 读回来
        java.nio.file.Path file = tempDir.resolve("Model.yml");
        com.acanx.module.aha.core.config.ModelConfigStore store =
                new com.acanx.module.aha.core.config.ModelConfigStore(file);

        String error = ProviderForm.save(store, new ProviderForm.Draft(
                "MyGateway", "openai-compatible", "https://x/v1", "sk-12345678",
                "my-model", "60", "2"), true);

        assertThat(error).isNull();
        assertThat(store.load().providersOrEmpty()).containsKey("MyGateway");
        // core 自己的加载路径也必须认这份文件（含字段名 PascalCase 校验）
        com.acanx.module.aha.core.config.ModelConfig loaded =
                com.acanx.module.aha.core.config.ConfigLoader.loadModel(file);
        assertThat(loaded.providersOrEmpty()).containsKey("MyGateway");
        assertThat(loaded.providersOrEmpty().get("MyGateway").model()).isEqualTo("my-model");
    }

    @Test
    void saveRejectsDuplicateOnCreateButAllowsUpdate(@TempDir java.nio.file.Path tempDir) {
        com.acanx.module.aha.core.config.ModelConfigStore store =
                new com.acanx.module.aha.core.config.ModelConfigStore(tempDir.resolve("Model.yml"));
        assertThat(ProviderForm.save(store, new ProviderForm.Draft(
                "Dup", "openai-compatible", "https://x/v1", "", "m1", "60", "2"), true)).isNull();

        String error = ProviderForm.save(store, new ProviderForm.Draft(
                "Dup", "openai-compatible", "https://x/v1", "", "m2", "60", "2"), true);
        assertThat(error).contains("已存在");

        assertThat(ProviderForm.save(store, new ProviderForm.Draft(
                "Dup", "openai-compatible", "https://x/v1", "", "m3", "60", "2"), false)).isNull();
        assertThat(store.load().providersOrEmpty().get("Dup").model()).isEqualTo("m3");
    }

    @Test
    void saveReportsValidationErrorsInsteadOfSilentlyDoingNothing(@TempDir java.nio.file.Path tempDir) {
        com.acanx.module.aha.core.config.ModelConfigStore store =
                new com.acanx.module.aha.core.config.ModelConfigStore(tempDir.resolve("Model.yml"));

        // 模型为空 —— 正是这次事故的现象：校验拦住了，但界面当初没在按钮旁提示
        String error = ProviderForm.save(store, new ProviderForm.Draft(
                "Ok", "openai-compatible", "https://x/v1", "", "", "60", "2"), true);

        assertThat(error).contains("模型名不能为空");
        assertThat(store.load().providersOrEmpty()).doesNotContainKey("Ok");
    }

    @Test
    void loadSafelyReportsBadConfigInsteadOfThrowing(@TempDir java.nio.file.Path tempDir)
            throws Exception {
        // 复现用户遇到的那次崩溃：文件里有个小写键名，core 拒绝加载整份配置。
        // 界面必须能打开并指出问题，而不是抛异常。
        java.nio.file.Path file = tempDir.resolve("Model.yml");
        java.nio.file.Files.writeString(file, """
                Model:
                  Default: "qqqqq"
                  Providers:
                    qqqqq:
                      Adapter: "anthropic"
                      BaseUrl: "https://AAA.com/"
                      ApiKey: ""
                      Model: "aaaaaa"
                      TimeoutSeconds: 60
                      MaxRetries: 2
                """, java.nio.charset.StandardCharsets.UTF_8);
        com.acanx.module.aha.core.config.ModelConfigStore store =
                new com.acanx.module.aha.core.config.ModelConfigStore(file);

        ProviderForm.LoadResult result = ProviderForm.loadSafely(store);

        assertThat(result.ok()).isFalse();
        assertThat(result.config()).isNull();
        assertThat(result.error()).contains("PascalCase").contains("qqqqq");
    }

    @Test
    void saveRefusesWhenConfigIsBroken(@TempDir java.nio.file.Path tempDir) throws Exception {
        java.nio.file.Path file = tempDir.resolve("Model.yml");
        java.nio.file.Files.writeString(file, """
                Model:
                  Providers:
                    qqqqq:
                      Model: "m"
                """, java.nio.charset.StandardCharsets.UTF_8);
        com.acanx.module.aha.core.config.ModelConfigStore store =
                new com.acanx.module.aha.core.config.ModelConfigStore(file);

        String error = ProviderForm.save(store, new ProviderForm.Draft(
                "Good", "openai-compatible", "https://x/v1", "", "m", "60", "2"), true);

        assertThat(error).contains("配置读取失败");
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
        assertThat(ProviderForm.preset("DeepSeek").model()).isEqualTo("deepseek-v4-flash");
        assertThat(ProviderForm.preset("OpenAI").model()).isEqualTo("gpt-6-sol");
        assertThat(ProviderForm.preset("Anthropic").adapter()).isEqualTo("anthropic");
        assertThat(ProviderForm.preset("Gemini").adapter()).isEqualTo("gemini");
        assertThat(ProviderForm.preset("BigModelCN").model()).isEqualTo("glm-5.3");
        assertThat(ProviderForm.preset("Qwen").model()).isEqualTo("qwen3.7-plus");
        assertThat(ProviderForm.preset("Moonshot").model()).isEqualTo("kimi-k2.7-code");
        assertThat(ProviderForm.preset("MiniMax").model()).isEqualTo("MiniMax-M2.7");
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
    void idMustBePascalCaseLikeCoreRequires() {
        // 这条曾经漏掉：界面允许小写 ID，而 core 按 YAML 字段规范校验，
        // 于是写出的配置让 CLI 直接拒绝加载整份文件（「YAML 字段必须为 PascalCase」）
        assertThat(ProviderForm.validate(new ProviderForm.Draft(
                "qqqqq", "openai-compatible", "https://x/v1", "", "m", "60", "2")))
                .containsKey("id");
        assertThat(ProviderForm.validate(new ProviderForm.Draft(
                "My_Gateway", "openai-compatible", "https://x/v1", "", "m", "60", "2")))
                .containsKey("id");

        assertThat(ProviderForm.validate(new ProviderForm.Draft(
                "MyGateway2", "openai-compatible", "https://x/v1", "", "m", "60", "2")))
                .isEmpty();
    }

    @Test
    void duplicateIdIsDetectedForCreateForm() {
        java.util.List<String> existing = java.util.List.of("OpenAI", "DeepSeek");

        assertThat(ProviderForm.idExists("DeepSeek", existing)).isTrue();
        assertThat(ProviderForm.idExists("  DeepSeek  ", existing)).isTrue();
        assertThat(ProviderForm.idExists("NewOne", existing)).isFalse();
        assertThat(ProviderForm.idExists(null, existing)).isFalse();
    }

    @Test
    void modelCandidatesAreAvailableForKnownProviders() {
        assertThat(ProviderForm.modelsFor("DeepSeek")).contains("deepseek-v4-pro");
        assertThat(ProviderForm.modelsFor("OpenAI")).contains("gpt-6-sol");
        assertThat(ProviderForm.modelsFor(null)).isEmpty();
        assertThat(ProviderForm.knownProviders()).contains("DeepSeek", "Qwen", "Moonshot", "MiniMax");
    }

    @Test
    void tieredDraftRequiresStandardTier() {
        ProviderForm.Draft draft = new ProviderForm.Draft(
                "X", "openai-compatible", "https://x/v1", "", "",
                Map.of("Ultra", "u1"), "Ultra", "60", "2");

        assertThat(ProviderForm.validate(draft)).containsKey("tierStandard");
    }

    @Test
    void tieredConfigKeepsTiersAndSyncsModelWithDefaultTier() {
        ProviderForm.Draft draft = new ProviderForm.Draft(
                "X", "openai-compatible", "https://x/v1", "", "",
                Map.of("Ultra", "u1", "Standard", "s1", "Fallback", "f1"),
                "Fallback", "60", "2");

        ProviderConfig config = ProviderForm.toConfig(draft, null);

        assertThat(config.hasModels()).isTrue();
        assertThat(config.configuredModel(com.acanx.module.aha.core.config.ModelTier.ULTRA))
                .isEqualTo("u1");
        assertThat(config.defaultTier()).isEqualTo("Fallback");
        assertThat(config.model()).isEqualTo("f1");
        assertThat(config.effectiveModel(null)).isEqualTo("f1");
        // 未配置的档位回退 Standard
        assertThat(config.modelFor(com.acanx.module.aha.core.config.ModelTier.PRO)).isEqualTo("s1");
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
