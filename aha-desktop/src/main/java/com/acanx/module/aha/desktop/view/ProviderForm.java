package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.core.config.ModelConfigStore;
import com.acanx.module.aha.core.config.ModelTier;
import com.acanx.module.aha.core.config.ProviderConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * 已知供应商的预设（ID → 适配器 / 基础地址 / Standard 档模型）。
     *
     * <p>内容与 {@code aha-core/src/main/resources/ModelDefault.yml} 保持一致，
     * 模型名一律用该文件里的**真实**名字——不编造模型名，否则用户照着填会拿到 404。</p>
     */
    private static final Map<String, String[]> PRESETS = new LinkedHashMap<>();

    /**
     * 各供应商的候选模型（可下拉选，也可自己填）。按档位从强到弱排列。
     *
     * <p>同样只收真实模型：Standard 与 {@code ModelDefault.yml} 一致，其余是同一份预设里的
     * 其余档位。列表只是「省打字」，输入框本身是可编辑的，不限制用户填别的。</p>
     */
    private static final Map<String, String[]> MODELS = new LinkedHashMap<>();

    static {
        PRESETS.put("OpenAI", new String[]{"openai-compatible", "https://api.openai.com/v1", "gpt-6-sol"});
        PRESETS.put("Anthropic", new String[]{"anthropic", "https://api.anthropic.com", "claude-sonnet-5-5"});
        PRESETS.put("Gemini", new String[]{"gemini", "https://generativelanguage.googleapis.com", "gemini-3.8-flash"});
        PRESETS.put("DeepSeek", new String[]{"openai-compatible", "https://api.deepseek.com/v1", "deepseek-v4-flash"});
        PRESETS.put("BigModelCN", new String[]{"openai-compatible", "https://open.bigmodel.cn/api/paas/v4", "glm-5.3"});
        PRESETS.put("Qwen", new String[]{"openai-compatible", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3.7-plus"});
        PRESETS.put("Moonshot", new String[]{"openai-compatible", "https://api.moonshot.cn/v1", "kimi-k2.7-code"});
        PRESETS.put("MiniMax", new String[]{"openai-compatible", "https://api.minimax.chat/v1", "MiniMax-M2.7"});

        MODELS.put("OpenAI", new String[]{"gpt-6-astra", "gpt-6.1-sol", "gpt-6-sol", "gpt-6-luna"});
        MODELS.put("Anthropic", new String[]{"claude-fable-5-1", "claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"});
        MODELS.put("Gemini", new String[]{"gemini-3.1-pro-preview", "gemini-3.8-flash", "gemini-flash-lite-latest"});
        MODELS.put("DeepSeek", new String[]{"deepseek-v4-pro", "deepseek-v4-flash"});
        MODELS.put("BigModelCN", new String[]{"glm-5.3", "glm-5.3-flashx", "glm-5.3-flash"});
        MODELS.put("Qwen", new String[]{"qwen3.8-max", "qwen3.7-plus", "qwen3.8-flash"});
        MODELS.put("Moonshot", new String[]{"kimi-k3", "kimi-k2.7-code-highspeed", "kimi-k2.7-code", "kimi-k2.6"});
        MODELS.put("MiniMax", new String[]{"MiniMax-M3", "MiniMax-M2.7", "MiniMax-M2.5", "MiniMax-M2.1"});
    }

    /**
     * 新增时检查 ID 是否已被占用。
     *
     * <p>新增与编辑是两件事：ID 已存在应当明确报出来并让用户去「编辑」区，
     * 而不是静默覆盖掉一个能用的配置。</p>
     *
     * @param id       待新增的 ID
     * @param existing 现有 ID 集合
     * @return 已存在返回 {@code true}
     */
    public static boolean idExists(String id, java.util.Collection<String> existing) {
        if (id == null || existing == null) {
            return false;
        }
        return existing.contains(id.trim());
    }

    /**
     * 打开对话框时应当选中哪个供应商。
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
            return new Draft(id, ADAPTERS[0], "https://", "", "", Map.of(), null, "60", "2");
        }
        return new Draft(id, values[0], values[1], "", values[2], Map.of(), null, "60", "2");
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
     * @param model          默认模型名（老规则下即生效模型）
     * @param tierModels     五档模型（档位名 → 模型名，可空）
     * @param defaultTier    默认档位名（可空）
     * @param timeoutSeconds 超时秒数
     * @param maxRetries     重试次数
     */
    public record Draft(String id, String adapter, String baseUrl, String apiKey, String model,
                        Map<String, String> tierModels, String defaultTier,
                        String timeoutSeconds, String maxRetries) {

        /**
         * 兼容构造：不含档位表（老规则表单）。
         *
         * @param id             供应商 ID
         * @param adapter        适配器
         * @param baseUrl        基础地址
         * @param apiKey         API Key
         * @param model          模型名
         * @param timeoutSeconds 超时秒数
         * @param maxRetries     重试次数
         */
        public Draft(String id, String adapter, String baseUrl, String apiKey, String model,
                     String timeoutSeconds, String maxRetries) {
            this(id, adapter, baseUrl, apiKey, model, Map.of(), null, timeoutSeconds, maxRetries);
        }
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
                source == null ? Map.of() : new LinkedHashMap<>(source.modelsOrEmpty()),
                source == null ? null : source.defaultTier(),
                String.valueOf(source == null || source.timeoutSeconds() <= 0 ? 60 : source.timeoutSeconds()),
                String.valueOf(source == null || source.maxRetries() < 0 ? 2 : source.maxRetries()));
    }

    /**
     * 与 core 的 {@code ConfigLoader} 保持一致的大驼峰规则。
     */
    private static final java.util.regex.Pattern PASCAL_CASE =
            java.util.regex.Pattern.compile("^[A-Z][a-zA-Z0-9]*$");

    /**
     * 校验草稿。
     *
     * <p>含 {@code Models} 档位时，{@code Standard} 为唯一必填档；默认模型必须落在五档内。
     * 未填任何档位的草稿按老规则处理（只要求默认模型非空），保证旧配置可无缝编辑。</p>
     *
     * @param draft 草稿
     * @return 字段名 → 错误说明；为空表示通过
     */
    public static Map<String, String> validate(Draft draft) {
        Map<String, String> errors = new LinkedHashMap<>();
        Map<String, String> tiers = normalizedTiers(draft.tierModels());
        if (blank(draft.id())) {
            errors.put("id", "供应商 ID 不能为空");
        } else if (!PASCAL_CASE.matcher(draft.id().trim()).matches()) {
            errors.put("id", "供应商 ID 必须是大驼峰，如 DeepSeek（core 按 YAML 字段规范校验）");
        }
        if (blank(draft.adapter())) {
            errors.put("adapter", "适配器不能为空");
        }
        if (tiers.isEmpty()) {
            if (blank(draft.model())) {
                errors.put("model", "模型名不能为空（如 deepseek-chat）");
            }
        } else if (!tiers.containsKey(ModelTier.STANDARD.configName())) {
            errors.put("tierStandard", "Standard 档为必填档，不能为空");
        }
        if (!blank(draft.defaultTier())
                && ModelTier.fromConfigName(draft.defaultTier().trim()).isEmpty()) {
            errors.put("defaultTier", "默认档位必须是 Ultra / Pro / Standard / Flash / Fallback");
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
     * <p>保留原配置里表单不涉及的字段（限流、扩展参数）。含档位时把 {@code Model} 同步为
     * {@code DefaultTier}（缺省 Standard）对应的模型，保证写盘后两者一致。</p>
     *
     * @param draft    草稿
     * @param original 原配置，可为 {@code null}
     * @return 新配置
     */
    public static ProviderConfig toConfig(Draft draft, ProviderConfig original) {
        String adapter = draft.adapter().trim();
        String baseUrl = draft.baseUrl().trim();
        String apiKey = draft.apiKey() == null ? "" : draft.apiKey().trim();
        int timeout = Integer.parseInt(draft.timeoutSeconds().trim());
        int retries = Integer.parseInt(draft.maxRetries().trim());
        Map<String, String> tiers = normalizedTiers(draft.tierModels());
        if (tiers.isEmpty()) {
            // 老规则：仅 Model，行为与升级前一致
            return new ProviderConfig(adapter, baseUrl, apiKey, null, null,
                    draft.model() == null ? "" : draft.model().trim(), timeout, retries,
                    original == null ? null : original.rateLimit(),
                    original == null ? null : original.extra());
        }
        ModelTier defaultTier = ModelTier.fromConfigName(draft.defaultTier())
                .orElse(ModelTier.DEFAULT);
        String model = tiers.getOrDefault(defaultTier.configName(),
                tiers.get(ModelTier.STANDARD.configName()));
        return new ProviderConfig(adapter, baseUrl, apiKey, tiers, defaultTier.configName(), model,
                timeout, retries,
                original == null ? null : original.rateLimit(),
                original == null ? null : original.extra());
    }

    /**
     * 去除空档位项，只保留非空的五档。
     *
     * @param tiers 原始档位表
     * @return 规范化后的档位表
     */
    private static Map<String, String> normalizedTiers(Map<String, String> tiers) {
        Map<String, String> result = new LinkedHashMap<>();
        if (tiers == null) {
            return result;
        }
        for (ModelTier tier : ModelTier.strongestFirst()) {
            String value = tiers.get(tier.configName());
            if (value != null && !value.isBlank()) {
                result.put(tier.configName(), value.trim());
            }
        }
        return result;
    }

    /**
     * 安全读取的结果。
     *
     * @param config 配置；读取失败时为 {@code null}
     * @param error  失败说明；成功时为 {@code null}
     */
    public record LoadResult(com.acanx.module.aha.core.config.ModelConfig config, String error) {

        /**
         * 是否读取成功。
         *
         * @return 成功返回 {@code true}
         */
        public boolean ok() {
            return error == null;
        }
    }

    /**
     * 安全读取 Model.yml：**坏配置不能让界面崩掉**。
     *
     * @param store 存取器
     * @return 读取结果
     */
    public static LoadResult loadSafely(com.acanx.module.aha.core.config.ModelConfigStore store) {
        try {
            return new LoadResult(store.load(), null);
        } catch (RuntimeException e) {
            return new LoadResult(null, String.valueOf(e.getMessage()));
        }
    }

    /**
     * 落盘：新增或修改一个供应商，并**读回自检**。
     *
     * @param store  存取器
     * @param draft  表单草稿
     * @param create {@code true} 表示新增（ID 已存在则拒绝），{@code false} 表示修改
     * @return 成功返回 {@code null}；失败返回给用户看的说明
     */
    public static String save(ModelConfigStore store, Draft draft, boolean create) {
        Map<String, String> errors = validate(draft);
        if (!errors.isEmpty()) {
            return String.join("；", errors.values());
        }
        LoadResult current = loadSafely(store);
        if (!current.ok()) {
            return "配置读取失败，无法保存：" + current.error();
        }
        String id = draft.id().trim();
        ProviderConfig existing = current.config().providersOrEmpty().get(id);
        if (create && existing != null) {
            return "供应商 ID 已存在：" + id + "。改已有配置请用上面的「编辑」区。";
        }
        if (!create && existing == null) {
            return "供应商不存在：" + id;
        }
        ProviderConfig converted = toConfig(draft, existing);
        store.putProvider(id, converted);
        // 读回自检：写完立刻确认这份配置能被读出来（否则「点了没反应」会再次静默发生）
        LoadResult reloaded = loadSafely(store);
        ProviderConfig saved = reloaded.ok() ? reloaded.config().providersOrEmpty().get(id) : null;
        if (saved == null
                || !Objects.equals(saved.effectiveModel(null), converted.effectiveModel(null))) {
            return "写入后读回校验失败：" + id + "（请检查 " + store.path() + " 的写权限）";
        }
        return null;
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
