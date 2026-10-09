package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.ConfigLoader;
import com.acanx.module.aha.core.config.LlmConfig;
import com.acanx.module.aha.core.config.ModelTier;
import com.acanx.module.aha.core.config.ProviderConfig;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 配置视图：把 {@link AhaConfig} 解析为「键 → 展示值」。
 *
 * <p>同时供进程级命令 {@code aha config get} 与会话内命令 {@code /config} 使用，
 * 避免两处取值逻辑各自漂移。</p>
 *
 * <p>取值时对路径类字段展开 {@code ${ENV}} 占位符与 {@code ~}，因为用户真正关心的
 * 是「实际读到哪个文件」。本类<b>不解析密钥库</b>，因此不会把凭据打印到终端。</p>
 *
 * @since 0.1.0
 */
public final class ConfigView {

    /** 会话内 {@code /config} 的展示顺序。 */
    public static final List<String> KEYS = List.of(
            "Llm.DefaultProvider", "Llm.ModelFile",
            "Llm.Fallback.Provider", "Llm.Fallback.Model",
            "Memory.Storage", "Memory.Path", "Memory.MaxContextEntries",
            "Tools.Enabled", "Tools.AutoApprove", "Tools.Shell.TimeoutSeconds",
            "Security.KeyStore", "Security.KeyStorePath",
            "Logging.Level", "Logging.File",
            "Extension.Enabled", "Extension.Path",
            "Agent.PromptFiles", "Agent.SystemPrompt");

    /** 未设置值的展示文本。 */
    public static final String UNSET = "（未设置）";

    /** 空集合的展示文本。 */
    public static final String EMPTY = "（空）";

    /** 过长值的截断长度。 */
    private static final int MAX_LENGTH = 72;

    private ConfigView() {
    }

    /**
     * 配置项。
     *
     * @param key   配置键
     * @param value 展示值
     * @since 0.1.0
     */
    public record Entry(String key, String value) {
    }

    /**
     * 按 {@link #KEYS} 的顺序列出全部配置项。
     *
     * @param config 主配置，可为 {@code null}
     * @return 配置项列表
     */
    public static List<Entry> entries(AhaConfig config) {
        List<Entry> entries = new ArrayList<>(KEYS.size());
        for (String key : KEYS) {
            entries.add(new Entry(key, resolve(config, key)));
        }
        return entries;
    }

    /**
     * 解析单个配置键。
     *
     * @param config 主配置，可为 {@code null}
     * @param key    配置键
     * @return 展示值；未知键返回提示文本
     */
    public static String resolve(AhaConfig config, String key) {
        if (config == null) {
            return "<未加载>";
        }
        // 新增：Model.Providers.<Id>.Model / .DefaultTier / .Models.<档位> 可读
        if (key != null && key.startsWith("Model.Providers.")) {
            return resolveProviderField(config, key);
        }
        return switch (key) {
            case "Llm.DefaultProvider" -> text(config.llm() == null ? null : config.llm().defaultProvider());
            case "Llm.ModelFile" -> describeModelFile(config);
            case "Llm.Fallback.Provider" -> text(config.llm() == null ? null : config.llm().fallbackProvider());
            case "Llm.Fallback.Model" -> text(config.llm() == null ? null : config.llm().fallbackModel());
            case "Memory.Storage" -> text(config.memory() == null ? null : config.memory().storage());
            case "Memory.Path" -> path(config.memory() == null ? null : config.memory().path());
            case "Memory.MaxContextEntries" ->
                    text(config.memory() == null ? null : String.valueOf(config.memory().maxContextEntries()));
            case "Tools.Enabled" -> list(config.tools() == null ? null : config.tools().enabled());
            case "Tools.AutoApprove" -> list(config.tools() == null ? null : config.tools().autoApprove());
            case "Tools.Shell.TimeoutSeconds" -> text(config.tools() == null || config.tools().shell() == null
                    ? null : String.valueOf(config.tools().shell().timeoutSeconds()));
            case "Security.KeyStore" -> text(config.security() == null ? null : config.security().keyStore());
            case "Security.KeyStorePath" -> path(config.security() == null ? null : config.security().keyStorePath());
            case "Logging.Level" -> text(config.logging() == null ? null : config.logging().level());
            case "Logging.File" -> path(config.logging() == null ? null : config.logging().file());
            case "Extension.Enabled" -> text(config.extensions() == null
                    ? null : String.valueOf(config.extensions().enabled()));
            case "Extension.Path" -> path(config.extensions() == null ? null : config.extensions().path());
            case "Agent.PromptFiles" -> list(config.agent() == null ? null : config.agent().promptFiles());
            case "Agent.SystemPrompt" -> truncate(text(config.agent() == null ? null : config.agent().systemPrompt()));
            default -> "<未知配置键: " + key + ">";
        };
    }

    /**
     * 解析 {@code Model.Providers.<Id>.<字段>}。
     *
     * @param config 主配置
     * @param key    配置键
     * @return 展示值
     */
    private static String resolveProviderField(AhaConfig config, String key) {
        LlmConfig llm = config.llm();
        String rest = key.substring("Model.Providers.".length());
        int dot = rest.indexOf('.');
        if (dot <= 0) {
            return "<未知配置键: " + key + ">";
        }
        String id = rest.substring(0, dot);
        String field = rest.substring(dot + 1);
        ProviderConfig provider = llm == null || llm.providers() == null
                ? null : llm.providers().get(id);
        if (provider == null) {
            return UNSET;
        }
        String globalTier = llm.defaultTier();
        return switch (field) {
            case "Model" -> text(provider.effectiveModel(globalTier));
            case "DefaultTier" -> text(provider.effectiveTier(globalTier).configName());
            default -> resolveProviderTier(provider, field, key);
        };
    }

    private static String resolveProviderTier(ProviderConfig provider, String field, String key) {
        if (!field.startsWith("Models.")) {
            return "<未知配置键: " + key + ">";
        }
        Optional<ModelTier> tier = ModelTier.fromConfigName(field.substring("Models.".length()));
        return tier.map(value -> text(provider.configuredModel(value)))
                .orElse("<未知配置键: " + key + ">");
    }

    /**
     * 主配置来源描述。
     *
     * @return 描述文本
     */
    public static String describeMainConfig() {
        Path path = Path.of(ConfigLoader.CONFIG_FILE_NAME).toAbsolutePath().normalize();
        return Files.exists(path)
                ? path.toString()
                : path + "（不存在，当前使用内置默认 AhaDefault.yaml）";
    }

    /**
     * 模型配置文件来源描述。
     *
     * <p>输出解析后的<b>绝对路径</b>而非原始配置值，因为用户真正需要知道的是
     * 「实际读到哪一个文件」；不存在时追加提示，避免「配置没生效」却无线索。</p>
     *
     * @param config 主配置，可为 {@code null}
     * @return 描述文本
     */
    public static String describeModelFile(AhaConfig config) {
        Path path = ConfigLoader.resolveModelPath(config);
        Path absolute = path.toAbsolutePath().normalize();
        return Files.exists(absolute)
                ? absolute.toString()
                : absolute + "（不存在，当前使用内置默认 ModelDefault.yml）";
    }

    private static String text(String value) {
        return value == null || value.isBlank() ? UNSET : value;
    }

    private static String path(String value) {
        if (value == null || value.isBlank()) {
            return UNSET;
        }
        try {
            return ConfigLoader.resolveValue(value);
        } catch (InvalidPathException e) {
            // 值不是合法路径（含平台非法字符）时退回原始文本
            return value;
        }
    }

    private static String list(List<String> values) {
        if (values == null) {
            return UNSET;
        }
        if (values.isEmpty()) {
            return EMPTY;
        }
        return String.join("、", values);
    }

    /**
     * 截断展示值。
     *
     * <p>多行值（如系统提示词）只给首行加总字数：一屏配置里塞进整段提示词会把其余项挤没，
     * 而用户真正需要的是「它有没有配、有多长」。完整正文用 {@code /memory view} 看。</p>
     *
     * @param value 原始值
     * @return 展示值
     */
    private static String truncate(String value) {
        if (value == null) {
            return UNSET;
        }
        int newline = value.indexOf('\n');
        if (newline >= 0) {
            String head = value.substring(0, newline).strip();
            return head + " …（共 " + value.strip().length() + " 字符，完整内容用 /memory view）";
        }
        if (value.length() <= MAX_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LENGTH) + "…（共 " + value.length() + " 字符）";
    }
}
