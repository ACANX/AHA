package com.acanx.module.aha.core.config;

import com.acanx.module.aha.common.exception.ConfigException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 配置加载器：加载 YAML、校验字段名、映射为 {@link AhaConfig}。
 *
 * <p>配置分为两份文件：</p>
 * <ul>
 *   <li>主配置（{@code Aha.yaml} / classpath {@code AhaDefault.yaml}）：运行参数与兜底模型</li>
 *   <li>模型配置（{@code Model.yml} / classpath {@code ModelDefault.yml}）：供应商明细与当前默认供应商</li>
 * </ul>
 *
 * <p>合并优先级（后者覆盖前者）：内置预设 → 主配置内联 {@code Llm.Providers} → {@code Model.yml}。</p>
 *
 * @since 0.1.0
 */
public final class ConfigLoader {

    /** 模型供应商配置文件名。 */
    public static final String MODEL_FILE_NAME = "Model.yml";

    /** 主配置文件名。 */
    public static final String CONFIG_FILE_NAME = "Aha.yaml";

    private static final Logger LOG = LoggerFactory.getLogger(ConfigLoader.class);

    /**
     * 密钥库回退解析器。
     *
     * <p>占位符未命中环境变量与系统属性时，由此读取加密密钥库中的同名条目。</p>
     */
    private static volatile java.util.function.Function<String, char[]> secretResolver = key -> null;

    /**
     * 设置密钥库回退解析器。
     *
     * <p>与 {@code Security.KeyStore} 配合：环境变量优先，未命中则查密钥库。
     * 未设置（或密钥库不可用）时保持原有行为——保留未解析占位符。</p>
     *
     * @param resolver 解析器；传 {@code null} 时清除
     */
    public static void setSecretResolver(java.util.function.Function<String, char[]> resolver) {
        secretResolver = resolver == null ? key -> null : resolver;
    }

    private static final ObjectMapper YAML_MAPPER = YAMLMapper.builder()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .build();

    private static final Pattern PASCAL_CASE = Pattern.compile("^[A-Z][a-zA-Z0-9]*$");

    private static final Pattern ENV_PATTERN =
            Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::-(.*?))?}");

    private ConfigLoader() {
    }

    /**
     * 从 classpath 加载内置默认主配置，并合并模型配置。
     *
     * @return 配置对象
     */
    public static AhaConfig loadDefault() {
        AhaConfig config = readDefaultConfig();
        LOG.info("已加载内置默认配置 classpath:AhaDefault.yaml");
        return mergeWithModel(config, resolveModelPath(config));
    }

    /**
     * 从 YAML 文件加载主配置，并合并模型配置。
     *
     * @param configPath 主配置文件路径
     * @return 配置对象
     */
    public static AhaConfig load(Path configPath) {
        AhaConfig config = readConfig(configPath);
        LOG.info("已加载主配置 {}", configPath.toAbsolutePath().normalize());
        return mergeWithModel(config, resolveModelPath(config));
    }

    /**
     * 从指定主配置与模型配置文件加载并合并。
     *
     * @param configPath 主配置文件路径
     * @param modelPath  模型配置文件路径，可为 {@code null}
     * @return 配置对象
     */
    public static AhaConfig load(Path configPath, Path modelPath) {
        return mergeWithModel(readConfig(configPath), modelPath);
    }

    /**
     * 加载模型供应商配置。
     *
     * @param modelPath 模型配置文件路径
     * @return 模型配置，文件不存在或内容为空时返回 {@code null}
     */
    public static ModelConfig loadModel(Path modelPath) {
        if (modelPath == null || !Files.exists(modelPath)) {
            LOG.info("模型配置不存在，使用内置默认 ModelDefault.yml: {}", modelPath);
            return null;
        }
        LOG.info("已加载模型配置 {}", modelPath.toAbsolutePath().normalize());
        try {
            String yaml = resolveEnv(Files.readString(modelPath));
            return parseModel(yaml, "无法加载模型配置: " + modelPath);
        } catch (IOException e) {
            throw new ConfigException("MODEL_CONFIG_LOAD_FAILED", "无法加载模型配置: " + modelPath, e);
        }
    }

    /**
     * 加载内置模型供应商配置（classpath {@code ModelDefault.yml}）。
     *
     * @return 模型配置
     */
    public static ModelConfig loadModelDefault() {
        return ProviderPresets.builtinModel();
    }

    /**
     * 将模型配置合并进主配置。
     *
     * @param config 主配置
     * @param model  模型配置，可为 {@code null}
     * @return 合并后的配置
     */
    public static AhaConfig merge(AhaConfig config, ModelConfig model) {
        LlmConfig llm = config == null ? null : config.llm();
        Map<String, ProviderConfig> merged = new LinkedHashMap<>(ProviderPresets.builtin());
        if (llm != null && llm.providers() != null) {
            merged.putAll(llm.providers());
        }
        if (model != null && model.providers() != null) {
            merged.putAll(model.providers());
        }
        String defaultProvider = model != null && model.defaultProvider() != null
                ? model.defaultProvider()
                : (llm != null && llm.defaultProvider() != null ? llm.defaultProvider() : null);
        if (defaultProvider == null) {
            defaultProvider = "OpenAI";
        }
        LlmConfig mergedLlm = new LlmConfig(defaultProvider, merged,
                llm == null ? null : llm.fallback(),
                llm == null ? null : llm.modelFile());
        return new AhaConfig(mergedLlm,
                config == null ? null : config.memory(),
                config == null ? null : config.tools(),
                config == null ? null : config.security(),
                config == null ? null : config.logging(),
                config == null ? null : config.extensions(),
                config == null ? null : config.agent());
    }

    /**
     * 解析模型配置文件路径。
     *
     * <p>解析顺序（用户级优先：模型配置含 API Key，属凭据类配置，不放工作目录）：</p>
     * <ol>
     *   <li>主配置 {@code Llm.ModelFile}（内置默认值 {@code ${AHA_HOME:-~/.aha}/Model.yml}）</li>
     *   <li>{@code $AHA_HOME/Model.yml}</li>
     *   <li>{@code ~/.aha/Model.yml}</li>
     * </ol>
     *
     * <p><b>不再查找工作目录下的 {@code ./Model.yml}</b>：相对路径会随 CWD 变化，
     * 且读不到时静默回退内置默认，难以排查。需要项目级配置时，请在 {@code Aha.yaml}
     * 中<b>显式</b>设置 {@code Llm.ModelFile}（如 {@code ./Model.yml}）。</p>
     *
     * @param config 主配置
     * @return 模型配置文件路径，永不为 {@code null}
     */
    public static Path resolveModelPath(AhaConfig config) {
        String configured = config == null || config.llm() == null ? null : config.llm().modelFile();
        if (configured != null && !configured.isBlank()) {
            // 先展开 ${ENV} 占位符再当路径用：内置默认值是 ${AHA_HOME:-~/.aha}/Model.yml，
            // 直接交给 Path.of 会在 Windows 上因 ':' 抛 InvalidPathException，
            // 在 Linux 上则静默变成一个名为 "${AHA_HOME:-~/.aha}" 的相对路径。
            String expanded = resolveEnv(configured);
            if (expanded.indexOf("${") < 0) {
                try {
                    return expandHome(expanded);
                } catch (InvalidPathException e) {
                    // 值含平台非法字符：不当作路径，回退到默认位置
                }
            }
        }
        String home = System.getenv("AHA_HOME");
        if (home == null || home.isBlank()) {
            home = System.getProperty("AHA_HOME");
        }
        if (home != null && !home.isBlank()) {
            return Path.of(home, MODEL_FILE_NAME);
        }
        return Path.of(System.getProperty("user.home"), ".aha", MODEL_FILE_NAME);
    }

    /**
     * 将路径开头的 {@code ~} 展开为用户主目录。
     *
     * <p>支持 {@code ~}、{@code ~/x} 与 Windows 的 {@code ~\x}。
     * {@link Path#of(String, String...)} 不会展开 {@code ~}，必须显式处理，
     * 否则会创建出名为 {@code ~} 的字面目录。</p>
     *
     * @param value 原始路径
     * @return 展开后的路径
     */
    public static Path expandHome(String value) {
        if (value == null || value.isEmpty()) {
            return Path.of("");
        }
        String home = System.getProperty("user.home");
        if (value.equals("~")) {
            return Path.of(home);
        }
        if (value.startsWith("~/") || value.startsWith("~\\")) {
            return Path.of(home, value.substring(2));
        }
        return Path.of(value);
    }

    /**
     * 解析配置值中的占位符与主目录，用于展示与诊断。
     *
     * <p>顺序：先展开 {@code ${ENV}} / {@code ${ENV:-默认}}，再展开路径开头的 {@code ~}。
     * 组件在各自的使用点解析，本方法<b>不修改配置对象</b>，也不解析密钥库
     * （避免把凭据打印到终端）。</p>
     *
     * @param value 原始配置值，可为 {@code null}
     * @return 展开后的值；{@code null} 原样返回
     */
    public static String resolveValue(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        String resolved = resolveEnv(value);
        try {
            return expandHome(resolved).toString();
        } catch (InvalidPathException e) {
            // 值不是合法路径（含 Windows 非法字符等）时保留展开后的文本
            return resolved;
        }
    }

    /**
     * 解析 {@code ${ENV}} 与 {@code ${ENV:-default}} 占位符。
     *
     * <p>取值顺序：系统属性 {@code -DNAME} → 环境变量 {@code NAME} → 密钥库 → 默认值。
     * 系统属性在前，使测试与集成场景能确定性地覆盖 {@code AHA_HOME} 等变量
     * （与 {@code SystemPromptLoader.resolveUserHome()} 保持同一顺序）。</p>
     *
     * <p>未设置且未提供默认值时保留原占位符（便于诊断与提示所需环境变量），
     * 而非静默替换为空串。</p>
     *
     * @param text 原始文本
     * @return 替换后的文本
     */
    static String resolveEnv(String text) {
        Matcher matcher = ENV_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String fallback = matcher.group(2);
            String value = System.getProperty(name);
            if (value == null) {
                value = System.getenv(name);
            }
            if (value == null) {
                value = fromSecretStore(name);
            }
            if (value == null) {
                value = fallback != null ? fallback : matcher.group(0);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 解析字符串中的占位符（含密钥库回退）。
     *
     * <p>供使用点调用：配置加载发生在密钥库就绪之前（{@code Security} 段自身是明文），
     * 因此 API Key 等敏感字段在加载时可能仍为未解析占位符。
     * 在真正构造请求头/凭据时再调用本方法，即可完成密钥库回退。</p>
     *
     * @param value 原始值
     * @return 解析后的值；无占位符时原样返回
     */
    public static String resolveSecrets(String value) {
        return value == null || value.indexOf("${") < 0 ? value : resolveEnv(value);
    }

    /**
     * 从密钥库读取密钥。
     *
     * <p>密钥库不可用（未设主密码、文件损坏等）时返回 {@code null}，
     * 由调用方回退到保留占位符；不因密钥库问题阻断配置加载。</p>
     *
     * @param name 键名
     * @return 密钥；不存在或不可用时返回 {@code null}
     */
    private static String fromSecretStore(String name) {
        try {
            char[] secret = secretResolver.apply(name);
            return secret == null || secret.length == 0 ? null : new String(secret);
        } catch (RuntimeException e) {
            LOG.debug("密钥库读取失败，回退到占位符: {} ({})", name, e.getMessage());
            return null;
        }
    }

    /**
     * 占位符是否仍未被解析。
     *
     * @param value 配置值
     * @return 是否未解析
     */
    public static boolean isUnresolvedPlaceholder(String value) {
        return value != null && value.startsWith("${") && value.endsWith("}");
    }

    /**
     * 序列化模型配置为 YAML 文本（含 {@code Model:} 根）。
     *
     * @param model 模型配置
     * @return YAML 文本
     */
    static String toYaml(ModelConfig model) {
        try {
            return YAML_MAPPER.writeValueAsString(Map.of("Model", model));
        } catch (RuntimeException e) {
            throw new ConfigException("MODEL_CONFIG_WRITE_FAILED", "无法序列化模型配置", e);
        }
    }

    // ------------------------------------------------------------------

    private static AhaConfig mergeWithModel(AhaConfig config, Path modelPath) {
        ModelConfig model = loadModel(modelPath);
        if (model == null) {
            model = loadModelDefault();
        }
        return merge(config, model);
    }

    private static AhaConfig readDefaultConfig() {
        try (InputStream in = ConfigLoader.class.getResourceAsStream("/AhaDefault.yaml")) {
            if (in == null) {
                throw new ConfigException("CONFIG_NOT_FOUND", "未找到内置配置 AhaDefault.yaml");
            }
            String yaml = resolveEnv(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            JsonNode root = YAML_MAPPER.readTree(yaml);
            JsonNode aha = root.has("Aha") ? root.get("Aha") : root;
            validateFieldNames(aha);
            return YAML_MAPPER.treeToValue(aha, AhaConfig.class);
        } catch (IOException e) {
            throw new ConfigException("CONFIG_LOAD_FAILED", "无法加载内置配置", e);
        }
    }

    private static AhaConfig readConfig(Path configPath) {
        try {
            String yaml = resolveEnv(Files.readString(configPath));
            JsonNode root = YAML_MAPPER.readTree(yaml);
            JsonNode aha = root.has("Aha") ? root.get("Aha") : root;
            validateFieldNames(aha);
            return YAML_MAPPER.treeToValue(aha, AhaConfig.class);
        } catch (IOException e) {
            throw new ConfigException("CONFIG_LOAD_FAILED", "无法加载配置: " + configPath, e);
        }
    }

    private static ModelConfig parseModel(String yaml, String errorPrefix) {
        JsonNode root = YAML_MAPPER.readTree(yaml);
        JsonNode model = root.has("Model") ? root.get("Model") : root;
        validateFieldNames(model);
        return YAML_MAPPER.treeToValue(model, ModelConfig.class);
    }

    private static void validateFieldNames(JsonNode node) {
        for (Map.Entry<String, JsonNode> entry : node.properties()) {
            if (!PASCAL_CASE.matcher(entry.getKey()).matches()) {
                throw new ConfigException("INVALID_FIELD_NAME",
                        "YAML 字段必须为 PascalCase: " + entry.getKey());
            }
            if (entry.getValue().isObject()) {
                validateFieldNames(entry.getValue());
            }
        }
    }
}
