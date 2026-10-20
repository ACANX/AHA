package com.acanx.module.aha.core.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ModelDefinition} 的思考强度判定（issue #355）。
 *
 * <p>口径：显式 {@code ThinkingLevel} 优先；未配置时取 {@code ThinkingLevelMap} 的第一个键
 * （YAML 保序）；两者都没有时为 {@code null}（状态栏显示 {@code -}）。</p>
 *
 * @since 0.1.5
 */
class ModelDefinitionTest {

    @Test
    void explicitThinkingLevelWinsOverMap() {
        ModelDefinition definition = new ModelDefinition("m", true, null, null, null,
                "High", levels());

        assertThat(definition.effectiveThinkingLevel()).isEqualTo("High");
    }

    @Test
    void fallsBackToFirstMappedLevelKeepingYamlOrder() {
        ModelDefinition definition = new ModelDefinition("m", true, null, null, null,
                null, levels());

        assertThat(definition.effectiveThinkingLevel()).isEqualTo("Max");
    }

    @Test
    void blankThinkingLevelFallsBackToMap() {
        ModelDefinition definition = new ModelDefinition("m", true, null, null, null,
                "  ", levels());

        assertThat(definition.effectiveThinkingLevel()).isEqualTo("Max");
    }

    @Test
    void noThinkingConfigYieldsNull() {
        ModelDefinition definition = new ModelDefinition("m", false, null, null, null,
                null, null);

        assertThat(definition.effectiveThinkingLevel()).isNull();
        assertThat(definition.hasThinkingLevels()).isFalse();
    }

    private static Map<String, String> levels() {
        // Map.of 不保证顺序；「第一个键」的语义依赖 YAML 保序，这里用 LinkedHashMap 显式固定
        Map<String, String> levels = new LinkedHashMap<>();
        levels.put("Max", "max");
        levels.put("Low", "low");
        return levels;
    }
}
