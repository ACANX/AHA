package com.acanx.module.aha.common.tool;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolSettings} 测试。
 *
 * @since 0.1.0
 */
class ToolSettingsTest {

    @Test
    void emptyViewHasNoValues() {
        assertThat(ToolSettings.empty().values()).isEmpty();
        assertThat(ToolSettings.of(null).values()).isEmpty();
        assertThat(ToolSettings.of(Map.of()).values()).isEmpty();
    }

    @Test
    void readsIntegerFromNumber() {
        ToolSettings settings = ToolSettings.of(Map.of("Shell.TimeoutSeconds", 15));

        assertThat(settings.integer("Shell.TimeoutSeconds", 30)).isEqualTo(15);
    }

    @Test
    void readsIntegerFromString() {
        // YAML 未加引号时可能被解析为字符串
        assertThat(ToolSettings.of(Map.of("k", "7")).integer("k", 30)).isEqualTo(7);
    }

    @Test
    void integerFallsBackOnMissingOrInvalid() {
        ToolSettings settings = ToolSettings.of(Map.of("bad", "abc", "blank", "  "));

        assertThat(settings.integer("missing", 30)).isEqualTo(30);
        assertThat(settings.integer("bad", 30)).isEqualTo(30);
        assertThat(settings.integer("blank", 30)).isEqualTo(30);
    }

    @Test
    void readsStringListFromYamlList() {
        ToolSettings settings = ToolSettings.of(Map.of("Shell.AllowedCommands", List.of("ls", "git")));

        assertThat(settings.stringList("Shell.AllowedCommands")).containsExactly("ls", "git");
    }

    @Test
    void readsStringListFromCommaSeparatedText() {
        assertThat(ToolSettings.of(Map.of("k", "ls, git ,cat")).stringList("k"))
                .containsExactly("ls", "git", "cat");
    }

    @Test
    void stringListFiltersBlanksAndMissingKey() {
        ToolSettings settings = ToolSettings.of(Map.of("k", List.of("ls", "", "  ", "git")));

        assertThat(settings.stringList("k")).containsExactly("ls", "git");
        assertThat(settings.stringList("missing")).isEmpty();
    }

    @Test
    void readsStringWithFallback() {
        ToolSettings settings = ToolSettings.of(Map.of("a", "x", "b", "   "));

        assertThat(settings.string("a", "d")).isEqualTo("x");
        assertThat(settings.string("b", "d")).isEqualTo("d");
        assertThat(settings.string("missing", "d")).isEqualTo("d");
    }

    @Test
    void hasDetectsPresence() {
        ToolSettings settings = ToolSettings.of(Map.of("a", 1));

        assertThat(settings.has("a")).isTrue();
        assertThat(settings.has("b")).isFalse();
    }

    @Test
    void valuesAreDefensivelyCopied() {
        // 配置在运行期可能被并发读取，构造时拷贝避免外部修改
        java.util.Map<String, Object> source = new java.util.HashMap<>();
        source.put("a", 1);
        ToolSettings settings = ToolSettings.of(source);
        source.put("b", 2);

        assertThat(settings.has("b")).isFalse();
        assertThat(settings.has("a")).isTrue();
    }
}
