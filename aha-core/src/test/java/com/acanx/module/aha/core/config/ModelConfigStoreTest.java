package com.acanx.module.aha.core.config;

import com.acanx.module.aha.common.exception.ConfigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ModelConfigStore} 与模型配置分离测试。
 *
 * @since 0.1.0
 */
class ModelConfigStoreTest {

    @TempDir
    Path tempDir;

    private static ProviderConfig provider(String model) {
        return new ProviderConfig("openai-compatible", "https://example.com/v1",
                "${AHA_API_KEY_X}", model, 60, 2, new RateLimitConfig(10, 0), Map.of());
    }

    @Test
    void loadFallsBackToBuiltinWhenFileMissing() {
        ModelConfigStore store = new ModelConfigStore(tempDir.resolve("Model.yml"));

        assertThat(store.exists()).isFalse();
        ModelConfig config = store.load();

        assertThat(config.defaultProvider()).isEqualTo("OpenAI");
        assertThat(config.providersOrEmpty())
                .containsKeys("OpenAI", "Anthropic", "Gemini",
                        ProviderPresets.DEEPSEEK, ProviderPresets.BIG_MODEL_CN, ProviderPresets.QWEN);
    }

    @Test
    void setDefaultPersistsAndIsReloadable() throws Exception {
        Path file = tempDir.resolve("Model.yml");
        ModelConfigStore store = new ModelConfigStore(file);

        ModelConfig next = store.setDefault(ProviderPresets.DEEPSEEK);

        assertThat(next.defaultProvider()).isEqualTo(ProviderPresets.DEEPSEEK);
        assertThat(file).exists();
        assertThat(new ModelConfigStore(file).load().defaultProvider())
                .isEqualTo(ProviderPresets.DEEPSEEK);
        assertThat(Files.readString(file)).contains("Model:").contains("Default");
    }

    @Test
    void setDefaultRejectsUnknownProvider() {
        ModelConfigStore store = new ModelConfigStore(tempDir.resolve("Model.yml"));

        assertThatThrownBy(() -> store.setDefault("NoSuchProvider"))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("NoSuchProvider");
    }

    @Test
    void putProviderAddsAndOverrides() {
        Path file = tempDir.resolve("Model.yml");
        ModelConfigStore store = new ModelConfigStore(file);

        store.putProvider("MyProxy", provider("gpt-4o-mini"));
        ModelConfigStore reopened = new ModelConfigStore(file);
        assertThat(reopened.load().providersOrEmpty().get("MyProxy").model())
                .isEqualTo("gpt-4o-mini");

        store.putProvider("MyProxy", provider("gpt-4o"));
        assertThat(reopened.load().providersOrEmpty().get("MyProxy").model())
                .isEqualTo("gpt-4o");
    }

    @Test
    void removeProviderClearsDefaultWhenItWasSelected() {
        Path file = tempDir.resolve("Model.yml");
        ModelConfigStore store = new ModelConfigStore(file);
        store.putProvider("MyProxy", provider("m"));
        store.setDefault("MyProxy");

        ModelConfig next = store.removeProvider("MyProxy");

        assertThat(next.providersOrEmpty()).doesNotContainKey("MyProxy");
        assertThat(next.defaultProvider()).isNull();
        assertThat(store.load().defaultProvider()).isNull();
    }

    @Test
    void savedFileIsLoadableByConfigLoader() {
        Path file = tempDir.resolve("Model.yml");
        ModelConfigStore store = new ModelConfigStore(file);
        store.putProvider("MyProxy", provider("gpt-4o-mini"));
        store.setDefault("MyProxy");

        ModelConfig parsed = ConfigLoader.loadModel(file);

        assertThat(parsed).isNotNull();
        assertThat(parsed.defaultProvider()).isEqualTo("MyProxy");
        assertThat(parsed.providersOrEmpty()).containsKey("MyProxy");
    }

    @Test
    void mergeGivesModelFilePriorityOverInlineProviders() {
        Path modelFile = tempDir.resolve("Model.yml");
        ModelConfigStore store = new ModelConfigStore(modelFile);
        store.putProvider(ProviderPresets.DEEPSEEK, provider("model-file-model"));
        store.setDefault(ProviderPresets.DEEPSEEK);

        LlmConfig inline = new LlmConfig("OpenAI",
                Map.of(ProviderPresets.DEEPSEEK, provider("inline-model")), null, null);
        AhaConfig base = new AhaConfig(inline, null, null, null, null, null);

        AhaConfig merged = ConfigLoader.merge(base, ConfigLoader.loadModel(modelFile));

        assertThat(merged.llm().defaultProvider()).isEqualTo(ProviderPresets.DEEPSEEK);
        assertThat(merged.llm().providers().get(ProviderPresets.DEEPSEEK).model())
                .isEqualTo("model-file-model");
    }

    @Test
    void mergeFallsBackToInlineDefaultWhenModelHasNone() {
        LlmConfig inline = new LlmConfig("BigModelCN", Map.of("BigModelCN", provider("glm-4.6")), null, null);
        AhaConfig base = new AhaConfig(inline, null, null, null, null, null);

        AhaConfig merged = ConfigLoader.merge(base, new ModelConfig(null, Map.of()));

        assertThat(merged.llm().defaultProvider()).isEqualTo("BigModelCN");
        // 内置预设始终存在
        assertThat(merged.llm().providers()).containsKey(ProviderPresets.DEEPSEEK);
    }

    @Test
    void fallbackConfigIsExposedFromLlmConfig() {
        LlmConfig config = new LlmConfig("DeepSeek", Map.of(),
                new FallbackConfig("OpenAI", "gpt-4o"), "Model.yml");

        assertThat(config.fallbackProvider()).isEqualTo("OpenAI");
        assertThat(config.fallbackModel()).isEqualTo("gpt-4o");
        assertThat(config.modelFile()).isEqualTo("Model.yml");
        assertThat(new LlmConfig(null, null, null, null).fallbackProvider()).isNull();
    }

    @Test
    void resolveModelPathPrefersConfiguredValue() {
        AhaConfig config = new AhaConfig(
                new LlmConfig(null, null, null, tempDir.resolve("Custom.yml").toString()),
                null, null, null, null, null);

        assertThat(ConfigLoader.resolveModelPath(config))
                .isEqualTo(tempDir.resolve("Custom.yml"));
    }

    @Test
    void resolveModelPathFallsBackToUserLevelDirectory() {
        AhaConfig config = new AhaConfig(
                new LlmConfig(null, null, null, null), null, null, null, null, null);

        Path resolved = ConfigLoader.resolveModelPath(config);

        assertThat(resolved.getFileName().toString()).isEqualTo(ConfigLoader.MODEL_FILE_NAME);
        // 不再依赖工作目录：结果必须是绝对路径或 $AHA_HOME 下的路径
        assertThat(resolved.isAbsolute() || System.getenv("AHA_HOME") != null).isTrue();
        if (System.getenv("AHA_HOME") == null) {
            assertThat(resolved).isEqualTo(
                    Path.of(System.getProperty("user.home"), ".aha", ConfigLoader.MODEL_FILE_NAME));
        }
    }

    @Test
    void resolveModelPathExpandsTilde() {
        String home = System.getProperty("user.home");

        assertThat(ConfigLoader.expandHome("~"))
                .isEqualTo(Path.of(home));
        assertThat(ConfigLoader.expandHome("~/.aha/Model.yml"))
                .isEqualTo(Path.of(home, ".aha", "Model.yml"));
        // Windows 风格的 ~\x 也展开。在类 Unix 上 \ 是合法文件名字符而非分隔符，
        // 故此处只断言前缀，不断言完整路径。
        assertThat(ConfigLoader.expandHome("~\\.aha\\Model.yml").toString())
                .startsWith(home);
        // 不以 ~ 开头的路径保持原样
        assertThat(ConfigLoader.expandHome("/etc/aha/Model.yml"))
                .isEqualTo(Path.of("/etc/aha/Model.yml"));
        assertThat(ConfigLoader.expandHome("./Model.yml"))
                .isEqualTo(Path.of("./Model.yml"));
    }

    @Test
    void resolveModelPathExpandsTildeFromConfiguredValue() {
        AhaConfig config = new AhaConfig(
                new LlmConfig(null, null, null, "~/.aha/Model.yml"), null, null, null, null, null);

        assertThat(ConfigLoader.resolveModelPath(config))
                .isEqualTo(Path.of(System.getProperty("user.home"), ".aha", "Model.yml"));
    }

    @Test
    void toYamlProducesPascalCaseModelRoot() {
        String yaml = ConfigLoader.toYaml(new ModelConfig("DeepSeek", Map.of("DeepSeek", provider("m"))));

        assertThat(yaml).contains("Model:").contains("Default").contains("Providers").contains("Adapter");
    }
}
