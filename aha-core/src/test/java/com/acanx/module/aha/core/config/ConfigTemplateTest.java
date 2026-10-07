package com.acanx.module.aha.core.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内置配置模板的健壮性测试。
 *
 * @since 0.1.0
 */
class ConfigTemplateTest {

    /** 双引号包裹的占位符：Windows 路径的反斜杠会被 YAML 当作转义序列。 */
    private static final Pattern QUOTED_PLACEHOLDER =
            Pattern.compile("\"[^\"\\n]*\\$\\{[^\"\\n]*\"");

    @Test
    void defaultConfigCarriesTheAgentIdentity() {
        // 回归：Agent.SystemPrompt 曾是空串，于是 /config 的 Agent.SystemPrompt 一栏显示
        // 「（未设置）」，用户无从确认 AHA 到底是什么身份
        AhaConfig config = ConfigLoader.loadDefault();

        assertThat(config.agent()).isNotNull();
        assertThat(config.agent().systemPrompt()).isNotBlank();
        assertThat(config.agent().systemPrompt())
                .contains("你是 AHA（Agent Harness）驱动的编码助手")
                .contains("工具与权限");
        // 提示词里不该出现环境相关描述：系统、shell、工作目录由 AgentEngine 每轮附带
        assertThat(config.agent().systemPrompt()).doesNotContain("- 命令执行方式：");
    }

    @Test
    void defaultPromptFilesMatchTheJavaConstant() {
        // 两处不一致时，「已加载哪些」与「哪些未加载」会各说一套（这个 bug 真实发生过）
        AhaConfig config = ConfigLoader.loadDefault();

        assertThat(config.agent().promptFiles())
                .isEqualTo(SystemPromptLoader.DEFAULT_PROMPT_FILES);
    }

    @Test
    void defaultConfigHasNoDoubleQuotedPlaceholders() throws Exception {
        // 回归：AhaDefault.yaml 曾用双引号包裹 ${AHA_HOME:-.}/...，
        // 当 AHA_HOME=C:\Users\x\.aha 时展开为 "C:\Users\x\.aha/Model.yml"，
        // 其中 \U 不是合法 YAML 转义，导致启动即抛 JacksonYAMLParseException。
        assertThat(placeholdersIn("/AhaDefault.yaml")).isEmpty();
    }

    @Test
    void defaultModelConfigHasNoDoubleQuotedPlaceholders() throws Exception {
        assertThat(placeholdersIn("/ModelDefault.yml")).isEmpty();
    }

    @Test
    void windowsStylePathSurvivesSingleQuotedScalar() {
        // 单引号标量不做转义处理，反斜杠原样保留
        String yaml = "Aha:\n  Llm:\n    ModelFile: 'C:\\Users\\ACANX\\.aha/Model.yml'\n";

        assertThat(parse(yaml).get("Aha").get("Llm").get("ModelFile").asText())
                .isEqualTo("C:\\Users\\ACANX\\.aha/Model.yml");
    }

    @Test
    void windowsStylePathBreaksDoubleQuotedScalar() {
        // 说明为什么必须用单引号：\U 会被当作非法转义
        String yaml = "Aha:\n  Llm:\n    ModelFile: \"C:\\Users\\ACANX\\.aha/Model.yml\"\n";

        assertThatThrownBy(() -> parse(yaml))
                .hasMessageContaining("escape");
    }

    @Test
    void builtinDefaultsLoadWithWindowsStyleHome() {
        // 端到端：内置默认配置必须可解析（真实路径由 loadDefault 走 resolveEnv）
        assertThat(ConfigLoader.loadDefault()).isNotNull();
    }

    @Test
    void defaultPathsFallBackToUserHomeNotWorkingDirectory() throws Exception {
        // 回归：fallback 曾为 '.'（当前工作目录），导致数据库与日志默认写进项目目录。
        // 用户未自定义任何路径时，应当落到用户级目录。
        String text = resourceText("/AhaDefault.yaml");

        assertThat(text).doesNotContain("${AHA_HOME:-.}");
        assertThat(text).contains("${AHA_HOME:-~/.aha}");
    }

    @Test
    void defaultKeyStoreDirIsSingular() throws Exception {
        // 目录名：单数 + 大驼峰（与 YAML 字段风格一致）
        assertThat(resourceText("/AhaDefault.yaml"))
                .contains("/Key/Aha.keystore")
                .doesNotContain("/keys/Aha.keystore");
    }

    /**
     * 读取资源文本。
     *
     * @param resource classpath 资源路径
     * @return 文本内容
     * @throws Exception 读取失败
     */
    private static String resourceText(String resource) throws Exception {
        try (InputStream in = ConfigTemplateTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("资源存在: " + resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * 解析 YAML 文本。
     *
     * @param yaml YAML 文本
     * @return 根节点
     */
    private static tools.jackson.databind.JsonNode parse(String yaml) {
        return tools.jackson.dataformat.yaml.YAMLMapper.builder().build().readTree(yaml);
    }

    /**
     * 读取资源中双引号包裹的占位符。
     *
     * @param resource classpath 资源路径
     * @return 匹配到的片段列表
     * @throws Exception 读取失败
     */
    private static java.util.List<String> placeholdersIn(String resource) throws Exception {
        try (InputStream in = ConfigTemplateTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("资源存在: " + resource).isNotNull();
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return QUOTED_PLACEHOLDER.matcher(text).results()
                    .map(m -> m.group())
                    .toList();
        }
    }
}
