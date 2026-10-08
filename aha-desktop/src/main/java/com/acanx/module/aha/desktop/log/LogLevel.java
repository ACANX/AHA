package com.acanx.module.aha.desktop.log;

import java.util.List;
import java.util.Locale;

/**
 * 日志级别（面板的显示过滤用）。
 *
 * <p>刻意不用 log4j 的 {@code Level}：这里的级别只回答「面板要不要显示这一行」，
 * 与日志实现无关。纯枚举意味着过滤规则可以在没有图形环境、没有日志实现的机器上测。</p>
 *
 * @since 0.2.0
 */
public enum LogLevel {

    /** 追踪。 */
    TRACE(0),

    /** 调试。 */
    DEBUG(1),

    /** 信息。 */
    INFO(2),

    /** 警告。 */
    WARN(3),

    /** 错误。 */
    ERROR(4);

    /** 「全部」过滤器用的级别（比 TRACE 还低）。 */
    public static final LogLevel ALL = TRACE;

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    /**
     * 严重程度。
     *
     * @return 数值越大越严重
     */
    public int severity() {
        return severity;
    }

    /**
     * 从文本解析。
     *
     * <p>无法识别时返回 {@link #INFO}：日志面板把未知级别当信息处理，
     * 既不隐藏也不抬升它的严重性。</p>
     *
     * @param text 文本（大小写不敏感）
     * @return 级别
     */
    public static LogLevel of(String text) {
        if (text == null || text.isBlank()) {
            return INFO;
        }
        try {
            return valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return INFO;
        }
    }

    /**
     * 本级别是否达到过滤门槛。
     *
     * @param threshold 门槛级别
     * @return 达到返回 {@code true}
     */
    public boolean atLeast(LogLevel threshold) {
        return threshold == null || severity >= threshold.severity();
    }

    /** 过滤下拉里的顺序（由低到高）。 */
    public static final List<LogLevel> DISPLAY_ORDER =
            List.of(TRACE, DEBUG, INFO, WARN, ERROR);
}
