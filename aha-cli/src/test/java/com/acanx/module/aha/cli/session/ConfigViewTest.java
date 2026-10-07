package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.ConfigLoader;
import com.acanx.module.aha.core.config.AgentConfig;
import com.acanx.module.aha.core.config.ExtensionsConfig;
import com.acanx.module.aha.core.config.LlmConfig;
import com.acanx.module.aha.core.config.LoggingConfig;
import com.acanx.module.aha.core.config.MemoryConfig;
import com.acanx.module.aha.core.config.SecurityConfig;
import com.acanx.module.aha.core.config.ToolsConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ConfigView} 测试：键解析、路径展开与来源描述。
 *
 * @since 0.1.0
 */
class ConfigViewTest {

    private static AhaConfig config() {
        return new AhaConfig(
                new LlmConfig("DeepSeek", null, null, "${AHA_HOME:-~/.aha}/Model.yml"),
                new MemoryConfig("sqlite", "${AHA_HOME:-~/.aha}/Data/Aha.db", 100),
                new ToolsConfig(List.of("file-read"), List.of(), null),
                new SecurityConfig("encrypted-file", "${AHA_HOME:-~/.aha}/Key/Aha.keystore"),
                new LoggingConfig("DEBUG", "${AHA_HOME:-~/.aha}/Log/AHA.log"),
                new ExtensionsConfig(true, "${AHA_HOME:-~/.aha}/Extension", true, List.of(), null),
                new AgentConfig(List.of("AHA.md", "AGENTS.md"), "内联身份"));
    }

    @Test
    void entriesCoverEveryDeclaredKey() {
        List<ConfigView.Entry> entries = ConfigView.entries(config());
        assertThat(entries).hasSize(ConfigView.KEYS.size());
        assertThat(entries).extracting(ConfigView.Entry::key).containsExactlyElementsOf(ConfigView.KEYS);
    }

    @Test
    void pathValuesAreExpanded() {
        // ${ENV:-默认} 先展开，再展开 ~；用户才能看到「实际读到哪个文件」
        String path = ConfigView.resolve(config(), "Memory.Path");
        String tail = Path.of("Data", "Aha.db").toString();
        assertThat(path).doesNotContain("${AHA_HOME").doesNotContain("~").endsWith(tail);
    }

    @Test
    void plainValuesAreKeptAsIs() {
        assertThat(ConfigView.resolve(config(), "Memory.Storage")).isEqualTo("sqlite");
        assertThat(ConfigView.resolve(config(), "Logging.Level")).isEqualTo("DEBUG");
        assertThat(ConfigView.resolve(config(), "Extension.Enabled")).isEqualTo("true");
    }

    @Test
    void listsAreJoinedAndEmptinessIsDistinguished() {
        assertThat(ConfigView.resolve(config(), "Tools.Enabled")).isEqualTo("file-read");
        assertThat(ConfigView.resolve(config(), "Tools.AutoApprove")).isEqualTo(ConfigView.EMPTY);
        assertThat(ConfigView.resolve(config(), "Agent.PromptFiles")).isEqualTo("AHA.md、AGENTS.md");
    }

    @Test
    void missingValuesAndUnknownKeysAreReported() {
        AhaConfig sparse = new AhaConfig(null, null, null, null, null, null);
        assertThat(ConfigView.resolve(sparse, "Memory.Storage")).isEqualTo(ConfigView.UNSET);
        assertThat(ConfigView.resolve(sparse, "No.Such.Key")).contains("未知配置键");
        assertThat(ConfigView.resolve(null, "Memory.Storage")).isEqualTo("<未加载>");
    }

    @Test
    void longValuesAreTruncated() {
        AhaConfig verbose = new AhaConfig(null, null, null, null, null, null,
                new AgentConfig(null, "x".repeat(200)));
        String value = ConfigView.resolve(verbose, "Agent.SystemPrompt");
        assertThat(value).contains("共 200 字符").startsWith("x".repeat(72)).doesNotContain("x".repeat(73));
    }

    @Test
    void modelFileShowsResolvedPath() {
        assertThat(ConfigView.describeModelFile(config())).contains("Model.yml");
    }

    @Test
    void mainConfigOriginIsDescribed() {
        // 仓库根目录下不存在 Aha.yaml，应说明回退到内置默认
        assertThat(ConfigView.describeMainConfig()).contains("Aha.yaml");
    }
    @Test
    void multiLineSystemPromptShowsFirstLineAndLength() {
        AhaConfig config = ConfigLoader.loadDefault();

        String value = ConfigView.resolve(config, "Agent.SystemPrompt");

        // 一屏配置里塞进整段提示词会把其余项挤没；这里给首行 + 总字数 + 完整内容入口
        assertThat(value).startsWith("你是 AHA（Agent Harness）驱动的编码助手");
        assertThat(value).contains("共 ").contains("字符");
        assertThat(value).contains("/memory view");
        assertThat(value).doesNotContain("\n");
    }

}
