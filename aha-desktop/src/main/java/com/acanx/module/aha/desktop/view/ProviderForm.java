package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.core.config.ProviderConfig;

import java.util.LinkedHashMap;
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
