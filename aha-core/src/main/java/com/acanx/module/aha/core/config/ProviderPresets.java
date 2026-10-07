package com.acanx.module.aha.core.config;

import com.acanx.module.aha.common.exception.ConfigException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 内置供应商预设。
 *
 * <p>数据源为 classpath 的 {@code ModelDefault.yml}，与用户级 {@code Model.yml} 结构一致。
 * DeepSeek、智谱 GLM、通义千问（Qwen）均提供 OpenAI 兼容端点，由 {@code OpenAiAdapter} 统一处理。</p>
 *
 * @since 0.1.0
 */
public final class ProviderPresets {

    /** 预设：DeepSeek。 */
    public static final String DEEPSEEK = "DeepSeek";
    /** 预设：智谱 BigModel（国内站）。 */
    public static final String BIG_MODEL_CN = "BigModelCN";
    /** 预设：通义千问。 */
    public static final String QWEN = "Qwen";
    /** 内置模型配置文件名。 */
    public static final String MODEL_DEFAULT_RESOURCE = "/ModelDefault.yml";

    private static final ObjectMapper YAML = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private ProviderPresets() {
    }

    /**
     * 返回内置预设（来自 classpath {@code ModelDefault.yml}）。
     *
     * @return 预设表
     */
    public static Map<String, ProviderConfig> builtin() {
        return builtinModel().providersOrEmpty();
    }

    /**
     * 返回内置模型配置（含默认供应商键名）。
     *
     * @return 模型配置
     */
    public static ModelConfig builtinModel() {
        try (InputStream in = ProviderPresets.class.getResourceAsStream(MODEL_DEFAULT_RESOURCE)) {
            if (in == null) {
                return new ModelConfig(null, Map.of());
            }
            String yaml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JsonNode root = YAML.readTree(yaml);
            JsonNode model = root.has("Model") ? root.get("Model") : root;
            return YAML.treeToValue(model, ModelConfig.class);
        } catch (IOException e) {
            throw new ConfigException("MODEL_DEFAULT_LOAD_FAILED",
                    "无法加载内置模型配置 " + MODEL_DEFAULT_RESOURCE, e);
        }
    }

    /**
     * 将内置预设合并进用户配置（用户同名配置优先）。
     *
     * @param user 用户 LLM 配置，可为 {@code null}
     * @return 合并后的配置
     */
    public static LlmConfig applyDefaults(LlmConfig user) {
        ModelConfig builtin = builtinModel();
        Map<String, ProviderConfig> merged = new LinkedHashMap<>(builtin.providersOrEmpty());
        if (user != null && user.providers() != null) {
            merged.putAll(user.providers());
        }
        String defaultProvider = user != null && user.defaultProvider() != null
                ? user.defaultProvider()
                : builtin.defaultProvider();
        if (defaultProvider == null) {
            defaultProvider = "OpenAI";
        }
        return new LlmConfig(defaultProvider, merged,
                user == null ? null : user.fallback(),
                user == null ? null : user.modelFile());
    }
}
