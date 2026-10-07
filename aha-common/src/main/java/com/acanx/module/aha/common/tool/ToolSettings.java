package com.acanx.module.aha.common.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工具配置视图。
 *
 * <p>工具由 {@code ServiceLoader} 创建，无法通过构造器接收配置。
 * 此前 {@code Tools.Shell.*} 等配置项因此从未生效——工具根本没有获取配置的入口。</p>
 *
 * <p>本类型刻意保持中立：{@code aha-common} 不依赖 {@code aha-core}，
 * 因此不能直接传递 {@code ToolsConfig}。工具按需读取自身关心的键，
 * 例如 {@code "Shell.TimeoutSeconds"}。键名沿用 YAML 的点分路径，便于对照配置。</p>
 *
 * @param values 配置键值，键为点分路径
 * @since 0.1.0
 */
public record ToolSettings(Map<String, Object> values) {

    /**
     * 构造空配置。
     *
     * @return 空配置视图
     */
    public static ToolSettings empty() {
        return new ToolSettings(Map.of());
    }

    /**
     * 构造配置视图。
     *
     * @param values 键值，可为 {@code null}
     * @return 配置视图
     */
    public static ToolSettings of(Map<String, Object> values) {
        return values == null || values.isEmpty() ? empty() : new ToolSettings(Map.copyOf(values));
    }

    /**
     * 读取整数值。
     *
     * @param key          键
     * @param defaultValue 缺省值，同时用于非法值兜底
     * @return 配置值；缺失或不可解析时返回缺省值
     */
    public int integer(String key, int defaultValue) {
        Object value = values.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    /**
     * 读取字符串列表。
     *
     * <p>兼容 YAML 列表与逗号分隔字符串两种写法，并过滤空白项。</p>
     *
     * @param key 键
     * @return 列表；缺失时为空列表
     */
    public List<String> stringList(String key) {
        Object value = values.get(key);
        List<String> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                addIfPresent(result, item);
            }
        } else if (value instanceof String text) {
            for (String item : text.split(",")) {
                addIfPresent(result, item);
            }
        }
        return List.copyOf(result);
    }

    /**
     * 读取字符串。
     *
     * @param key          键
     * @param defaultValue 缺省值
     * @return 配置值；缺失时返回缺省值
     */
    public String string(String key, String defaultValue) {
        Object value = values.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    /**
     * 是否包含指定键。
     *
     * @param key 键
     * @return 是否包含
     */
    public boolean has(String key) {
        return values.containsKey(key);
    }

    private static void addIfPresent(List<String> target, Object item) {
        if (item == null) {
            return;
        }
        String text = String.valueOf(item).trim();
        if (!text.isEmpty()) {
            target.add(text);
        }
    }
}
