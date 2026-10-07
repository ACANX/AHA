package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.common.model.SessionConfig;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 自动压缩阈值的解析与固化。
 *
 * <p>阈值存在会话配置的 {@code Extras} 里而不是进程内存里，这样 {@code aha chat --session <id>}
 * 续接同一会话时设置仍在——用户设定的是「这个会话的规则」，不是「这次进程的规则」。</p>
 *
 * @since 0.1.0
 */
public final class AutoCompactSetting {

    /** 会话配置 Extras 中的键名。 */
    public static final String KEY = "AutoCompact";

    /** 关闭自动压缩的阈值。 */
    public static final long DISABLED = 0L;

    private AutoCompactSetting() {
    }

    /**
     * 读取会话的自动压缩阈值。
     *
     * <p>Extras 经 JSON 往返后可能是字符串，也可能是数值节点，因此宽容解析；
     * 无法解析时按「未设置」处理，不让一个坏值把启动流程打断。</p>
     *
     * @param config 会话配置，可为 {@code null}
     * @return 阈值；未设置或已关闭时返回 {@link #DISABLED}
     */
    public static long thresholdOf(SessionConfig config) {
        if (config == null || config.extras() == null) {
            return DISABLED;
        }
        Object value = config.extras().get(KEY);
        if (value == null) {
            return DISABLED;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return DISABLED;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return DISABLED;
        }
    }

    /**
     * 写入自动压缩阈值，返回新的会话配置。
     *
     * <p>关闭时移除该键而不是写入 0：会话配置里不该留下无效字段。</p>
     *
     * @param config    原会话配置
     * @param threshold 阈值；{@link #DISABLED} 表示关闭
     * @return 新的会话配置
     */
    public static SessionConfig withThreshold(SessionConfig config, long threshold) {
        Map<String, Object> extras = new LinkedHashMap<>();
        if (config != null && config.extras() != null) {
            extras.putAll(config.extras());
        }
        if (threshold > DISABLED) {
            extras.put(KEY, String.valueOf(threshold));
        } else {
            extras.remove(KEY);
        }
        return new SessionConfig(config == null ? null : config.model(),
                config == null ? null : config.systemPrompt(), extras);
    }

    /**
     * 解析用户输入的阈值。
     *
     * <p>接受 {@code off} / {@code 0}（关闭）、纯数字，以及 {@code k} / {@code m} 后缀
     * （如 {@code 285k} = 285000、{@code 1M} = 1000000）。</p>
     *
     * @param raw 用户输入
     * @return 阈值；{@link #DISABLED} 表示关闭
     * @throws IllegalArgumentException 输入无法识别
     */
    public static long parse(String raw) {
        String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("缺少阈值");
        }
        if ("off".equals(text) || "none".equals(text)) {
            return DISABLED;
        }
        long multiplier = 1L;
        if (text.endsWith("k")) {
            multiplier = 1_000L;
            text = text.substring(0, text.length() - 1);
        } else if (text.endsWith("m")) {
            multiplier = 1_000_000L;
            text = text.substring(0, text.length() - 1);
        }
        long value;
        try {
            value = Long.parseLong(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无法识别的阈值: " + raw);
        }
        if (value < 0) {
            throw new IllegalArgumentException("阈值不能为负数: " + raw);
        }
        if (value == 0) {
            return DISABLED;
        }
        return value * multiplier;
    }

    /**
     * 格式化阈值。
     *
     * @param threshold 阈值
     * @return 描述文本
     */
    public static String describe(long threshold) {
        return threshold > DISABLED ? threshold + " tokens" : "已关闭";
    }
}
