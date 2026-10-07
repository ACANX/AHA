package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.core.config.ProviderConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 供应商表单的纯逻辑：草稿 ↔ 配置、校验、密钥打码。
 *
 * <p>不引用任何 JavaFX 类型，于是「哪些输入算非法、保存后写回什么」可以在无图形环境下测试；
 * 对话框只负责把控件里的文本装进 {@link Draft}。</p>
 *
 * @since 0.2.0
 */
public final class ProviderForm {

    /** 适配器可选值（与 core 的适配器实现对应）。 */
    public static final String[] ADAPTERS = {"openai-compatible", "anthropic", "gemini"};

    /**
     * 已知供应商的预设（ID → 适配器 / 基础地址 / 默认模型）。
     *
     * <p>内容与 {@code aha-core/src/main/resources/ModelDefault.yml} 保持一致，
     * 模型名一律用该文件里的**真实**名字——不编造模型名，否则用户照着填会拿到 404。</p>
     */
    private static final Map<String, String[]> PRESETS = new LinkedHashMap<>();

    /**
     * 各供应商的候选模型（可下拉选，也可自己填）。
     *
     * <p>同样只收真实模型：默认那个来自 {@code ModelDefault.yml}，其余是同系列里广为人知的版本。
     * 列表只是「省打字」，输入框本身是可编辑的，不限制用户填别的。</p>
     */
    private static final Map<String, String[]> MODELS = new LinkedHashMap<>();

    static {
        PRESETS.put("OpenAI", new String[]{"openai-compatible", "https://api.openai.com/v1", "gpt-4o"});
        PRESETS.put("Anthropic", new String[]{"anthropic", "https://api.anthropic.com", "claude-sonnet-5-1"});
        PRESETS.put("Gemini", new String[]{"gemini", "https://generativelanguage.googleapis.com", "gemini-3.5-flash"});
        PRESETS.put("DeepSeek", new String[]{"openai-compatible", "https://api.deepseek.com/v1", "deepseek-chat"});
        PRESETS.put("BigModelCN", new String[]{"openai-compatible", "https://open.bigmodel.cn/api/paas/v4", "glm-4.6"});
        PRESETS.put("Qwen", new String[]{"openai-compatible", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-max"});

        MODELS.put("OpenAI", new String[]{"gpt-4o", "gpt-4o-mini", "gpt-4.1"});
        MODELS.put("Anthropic", new String[]{"claude-sonnet-5-1", "claude-opus-4-1", "claude-haiku-4-5"});
        MODELS.put("Gemini", new String[]{"gemini-3.5-flash", "gemini-3.5-pro", "gemini-2.5-flash"});
        MODELS.put("DeepSeek", new String[]{"deepseek-chat", "deepseek-reasoner"});
        MODELS.put("BigModelCN", new String[]{"glm-4.6", "glm-4.5-air"});
        MODELS.put("Qwen", new String[]{"qwen-max", "qwen-plus", "qwen-turbo"});
    }

    /**
     * 打开对话框时应当选中哪个供应商。
     *
     * <p>规则：优先选中**当前启用**的那个（用户最关心的就是「现在用的是谁」）；
     * 它不在列表里（例如刚被删掉）或未设置时，退回第一个；列表为空返回 {@code null}。
     * 抽成纯函数是为了让这条规则在无图形环境下也有测试。</p>
     *
     * @param activeId 当前启用的供应商 ID，可为 {@code null}
     * @param ids      列表里的全部 ID
     * @return 应当选中的 ID；无可选时返回 {@code null}
     */
    public static String selectedOnOpen(String activeId, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return null;
        }
        return activeId != null && ids.contains(activeId) ? activeId : ids.get(0);
    }

    /**
     * 已知供应商 ID 列表（用于「从预设新建」）。
     *
     * @return 不可变的 ID 列表
     */
    public static java.util.List<String> knownProviders() {
        return List.copyOf(PRESETS.keySet());
    }

    /**
     * 取某个供应商的候选模型；未知供应商返回空数组（仍可自由填写）。
     *
     * @param providerId 供应商 ID，可为 {@code null}
     * @return 候选模型
     */
    public static String[] modelsFor(String providerId) {
        if (providerId == null) {
            return new String[0];
        }
        return MODELS.getOrDefault(providerId.trim(), new String[0]);
    }

    /**
     * 按预设生成一份草稿（新增供应商时用）。
     *
     * @param providerId 供应商 ID；未知 ID 时只填 ID 与默认适配器
     * @return 草稿
     */
    public static Draft preset(String providerId) {
        String id = providerId == null ? "" : providerId.trim();
        String[] values = PRESETS.get(id);
        if (values == null) {
            return new Draft(id, ADAPTERS[0], "https://", "", "", "60", "2");
        }
        return new Draft(id, values[0], values[1], "", values[2], "60", "2");
    }

    private ProviderForm() {
    }

    /**
     * 表单草稿：全是字符串，因为用户可能填了非法值，需要校验后再落到配置。
     *
     * @param id             供应商 ID
     * @param adapter        适配器
     * @param baseUrl        基础地址
     * @param apiKey         API Key（可含 ${ENV} 占位符）
     * @param model          模型名
     * @param timeoutSeconds 超时秒数
     * @param maxRetries     重试次数
     */
    public record Draft(String id, String adapter, String baseUrl, String apiKey, String model,
                        String timeoutSeconds, String maxRetries) {
    }

    /**
     * 由已有配置生成草稿。
     *
     * @param id      供应商 ID
     * @param config  配置，可为 {@code null}（新增场景）
     * @return 草稿
     */
    public static Draft of(String id, ProviderConfig config) {
        ProviderConfig source = config;
        return new Draft(
                id == null ? "" : id,
                source == null || source.adapter() == null ? ADAPTERS[0] : source.adapter(),
                source == null || source.baseUrl() == null ? "" : source.baseUrl(),
                source == null || source.apiKey() == null ? "" : source.apiKey(),
                source == null || source.model() == null ? "" : source.model(),
                String.valueOf(source == null || source.timeoutSeconds() <= 0 ? 60 : source.timeoutSeconds()),
                String.valueOf(source == null || source.maxRetries() < 0 ? 2 : source.maxRetries()));
    }

    /**
     * 校验草稿。
     *
     * @param draft 草稿
     * @return 字段名 → 错误说明；为空表示通过
     */
    public static Map<String, String> validate(Draft draft) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (blank(draft.id())) {
            errors.put("id", "供应商 ID 不能为空");
        } else if (!draft.id().trim().matches("[A-Za-z0-9_.-]+")) {
            errors.put("id", "ID 只允许字母、数字、下划线、点与减号");
        }
        if (blank(draft.adapter())) {
            errors.put("adapter", "适配器不能为空");
        }
        if (blank(draft.model())) {
            errors.put("model", "模型名不能为空（如 deepseek-chat）");
        }
        if (blank(draft.baseUrl())) {
            errors.put("baseUrl", "基础地址不能为空（如 https://api.deepseek.com/v1）");
        } else if (!draft.baseUrl().trim().startsWith("http")) {
            errors.put("baseUrl", "基础地址应以 http/https 开头");
        }
        if (!isPositiveInt(draft.timeoutSeconds())) {
            errors.put("timeoutSeconds", "超时必须是正整数（秒）");
        }
        if (!isNonNegativeInt(draft.maxRetries())) {
            errors.put("maxRetries", "重试次数必须是非负整数");
        }
        return errors;
    }

    /**
     * 草稿 → 配置。
     *
     * <p>保留原配置里表单不涉及的字段（限流、扩展参数），避免「改一个字段把别的丢掉」。</p>
     *
     * @param draft    草稿
     * @param original 原配置，可为 {@code null}
     * @return 新配置
     */
    public static ProviderConfig toConfig(Draft draft, ProviderConfig original) {
        return new ProviderConfig(
                draft.adapter().trim(),
                draft.baseUrl().trim(),
                draft.apiKey() == null ? "" : draft.apiKey().trim(),
                draft.model().trim(),
                Integer.parseInt(draft.timeoutSeconds().trim()),
                Integer.parseInt(draft.maxRetries().trim()),
                original == null ? null : original.rateLimit(),
                original == null ? null : original.extra());
    }

    /**
     * 密钥打码：只留头尾各 4 位。
     *
     * @param key 密钥，可为 {@code null}
     * @return 打码结果
     */
    public static String mask(String key) {
        if (blank(key)) {
            return "（未设置）";
        }
        String value = key.trim();
        if (value.length() <= 8) {
            return "（已设置，短值）";
        }
        return value.substring(0, 4) + "…" + value.substring(value.length() - 4);
    }

    /**
     * 是否为空。
     *
     * @param text 文本
     * @return 空返回 {@code true}
     */
    public static boolean blank(String text) {
        return text == null || text.isBlank();
    }

    private static boolean isPositiveInt(String text) {
        try {
            return Integer.parseInt(text.trim()) > 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static boolean isNonNegativeInt(String text) {
        try {
            return Integer.parseInt(text.trim()) >= 0;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
