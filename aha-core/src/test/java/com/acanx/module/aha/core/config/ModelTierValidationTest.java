package com.acanx.module.aha.core.config;

import com.acanx.module.aha.common.exception.ConfigException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code Model.yml} 档位校验测试：含 {@code Models} 时 {@code Standard} 必填。
 *
 * @since 0.1.0
 */
class ModelTierValidationTest {

    @TempDir
    Path tempDir;

    private Path write(String yaml) throws IOException {
        Path path = tempDir.resolve("Model.yml");
        Files.writeString(path, yaml);
        return path;
    }

    @Test
    void newStyleRequiresStandardTier() throws IOException {
        Path path = write("""
                Model:
                  Providers:
                    Demo:
                      Adapter: openai-compatible
                      BaseUrl: https://example.com/v1
                      ApiKey: k
                      Models:
                        Ultra: ultra-model
                        Pro: pro-model
                """);

        assertThatThrownBy(() -> ConfigLoader.loadModel(path))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("Demo")
                .hasMessageContaining("Standard");
    }

    @Test
    void newStyleRejectsModelOutsideTiers() throws IOException {
        Path path = write("""
                Model:
                  Providers:
                    Demo:
                      Adapter: openai-compatible
                      BaseUrl: https://example.com/v1
                      ApiKey: k
                      Models:
                        Standard: std-model
                      Model: other-model
                """);

        assertThatThrownBy(() -> ConfigLoader.loadModel(path))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("other-model");
    }

    @Test
    void legacyStyleWithoutModelsIsAccepted() throws IOException {
        Path path = write("""
                Model:
                  Providers:
                    Demo:
                      Adapter: openai-compatible
                      BaseUrl: https://example.com/v1
                      ApiKey: k
                      Model: legacy-model
                """);

        ModelConfig config = ConfigLoader.loadModel(path);
        ProviderConfig provider = config.providersOrEmpty().get("Demo");
        assertThat(provider.hasModels()).isFalse();
        assertThat(provider.model()).isEqualTo("legacy-model");
    }

    @Test
    void newStyleWithStandardIsAccepted() throws IOException {
        Path path = write("""
                Model:
                  DefaultTier: Pro
                  Providers:
                    Demo:
                      Adapter: openai-compatible
                      BaseUrl: https://example.com/v1
                      ApiKey: k
                      Models:
                        Ultra: ultra-model
                        Pro: pro-model
                        Standard: std-model
                      DefaultTier: Pro
                      Model: pro-model
                """);

        ModelConfig config = ConfigLoader.loadModel(path);
        assertThat(config.effectiveDefaultTier()).isEqualTo(ModelTier.PRO);
        assertThat(config.providersOrEmpty().get("Demo").effectiveModel("Pro"))
                .isEqualTo("pro-model");
    }
}
