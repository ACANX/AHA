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
 * {@code Model.yml} 档位校验与旧格式迁移测试。
 *
 * <p>新格式默认档固定 {@code Standard}，不再强制 {@code Standard} 必填；
 * 旧格式（字符串值 {@code Models}、{@code Adapter}、{@code Model}）读取时自动迁移。</p>
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
    void missingTierNodeIsRejected() throws IOException {
        Path path = write("""
                Model:
                  Providers:
                    Demo:
                      ApiType: openai-compatible
                      BaseUrl: https://example.com/v1
                      ApiKey: k
                      Models:
                        fast:
                          Name: fast-model
                """);

        assertThatThrownBy(() -> ConfigLoader.loadModel(path))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("Demo")
                .hasMessageContaining("Tier");
    }

    @Test
    void tierReferencingUnknownModelIsRejected() throws IOException {
        Path path = write("""
                Model:
                  Providers:
                    Demo:
                      ApiType: openai-compatible
                      BaseUrl: https://example.com/v1
                      ApiKey: k
                      Models:
                        fast:
                          Name: fast-model
                      Tier:
                        Standard: nope
                """);

        assertThatThrownBy(() -> ConfigLoader.loadModel(path))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void missingStandardFallsBackToStrongestTier() throws IOException {
        Path path = write("""
                Model:
                  Providers:
                    Demo:
                      ApiType: openai-compatible
                      BaseUrl: https://example.com/v1
                      ApiKey: k
                      Models:
                        ultra:
                          Name: ultra-model
                      Tier:
                        Ultra: ultra
                """);

        ProviderConfig provider = ConfigLoader.loadModel(path).providersOrEmpty().get("Demo");

        assertThat(provider.effectiveTier(null)).isEqualTo(ModelTier.ULTRA);
        assertThat(provider.effectiveModel(null)).isEqualTo("ultra-model");
    }

    @Test
    void legacyStringModelsAreMigrated() throws IOException {
        Path path = write("""
                Model:
                  Providers:
                    Demo:
                      Adapter: openai-compatible
                      BaseUrl: https://example.com/v1
                      ApiKey: k
                      Models:
                        Standard: legacy-model
                """);

        ProviderConfig provider = ConfigLoader.loadModel(path).providersOrEmpty().get("Demo");

        assertThat(provider.apiType()).isEqualTo("openai-compatible");
        assertThat(provider.hasModels()).isTrue();
        assertThat(provider.standardModel()).isEqualTo("legacy-model");
        assertThat(provider.model()).isEqualTo("legacy-model");
    }

    @Test
    void legacySingleModelIsMigrated() throws IOException {
        Path path = write("""
                Model:
                  Providers:
                    Demo:
                      Adapter: openai-compatible
                      BaseUrl: https://example.com/v1
                      ApiKey: k
                      Model: legacy-model
                """);

        ProviderConfig provider = ConfigLoader.loadModel(path).providersOrEmpty().get("Demo");

        assertThat(provider.hasModels()).isTrue();
        assertThat(provider.standardModel()).isEqualTo("legacy-model");
        assertThat(provider.effectiveModel(null)).isEqualTo("legacy-model");
    }

    @Test
    void anthropicAdapterValueIsNormalized() throws IOException {
        Path path = write("""
                Model:
                  Providers:
                    Demo:
                      Adapter: anthropic
                      BaseUrl: https://api.anthropic.com
                      ApiKey: k
                      Models:
                        sonnet:
                          Name: claude-sonnet
                      Tier:
                        Standard: sonnet
                """);

        ProviderConfig provider = ConfigLoader.loadModel(path).providersOrEmpty().get("Demo");

        assertThat(provider.apiType()).isEqualTo("anthropic-messages");
    }

    @Test
    void invalidCapabilityIsRejected() throws IOException {
        Path path = write("""
                Model:
                  Providers:
                    Demo:
                      ApiType: openai-compatible
                      BaseUrl: https://example.com/v1
                      ApiKey: k
                      Models:
                        fast:
                          Name: fast-model
                          ContextWindow: -1
                      Tier:
                        Standard: fast
                """);

        assertThatThrownBy(() -> ConfigLoader.loadModel(path))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("ContextWindow");
    }

    @Test
    void newStyleWithStandardIsAccepted() throws IOException {
        Path path = write("""
                Model:
                  DefaultTier: Pro
                  Providers:
                    Demo:
                      ApiType: openai-compatible
                      BaseUrl: https://example.com/v1
                      ApiKey: k
                      Models:
                        ultra:
                          Name: ultra-model
                        pro:
                          Name: pro-model
                        std:
                          Name: std-model
                      Tier:
                        Ultra: ultra
                        Pro: pro
                        Standard: std
                """);

        ModelConfig config = ConfigLoader.loadModel(path);

        assertThat(config.effectiveDefaultTier()).isEqualTo(ModelTier.PRO);
        assertThat(config.providersOrEmpty().get("Demo").effectiveModel("Pro"))
                .isEqualTo("std-model");
    }
}
