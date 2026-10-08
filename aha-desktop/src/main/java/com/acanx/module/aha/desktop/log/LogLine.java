package com.acanx.module.aha.desktop.log;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 日志面板里的一行（纯数据 + 纯渲染）。
 *
 * <p>渲染口径与文件日志保持一致的地方是「完整类名 + 方法」，不同的是面板里**取了短类名**：
 * 面板宽度有限，而排错时真正要的信息是「谁 + 说了什么」，完整类名可以看文件日志。
 * 这条差异是刻意的，写在这里而不是散在界面代码里。</p>
 *
 * @param level     级别
 * @param logger    记录器名（完整类名）
 * @param message   正文
 * @param thrown    异常摘要（可为 {@code null}）
 * @param timestamp 时间戳（毫秒）
 * @since 0.2.0
 */
public record LogLine(LogLevel level, String logger, String message, String thrown,
                      long timestamp) {

    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    /** 记录器名在面板里保留的最大长度。 */
    public static final int LOGGER_MAX = 26;

    /**
     * 渲染为一行文本。
     *
     * @return 形如 {@code 14:03:22.145  INFO   ChatController        已创建会话 8F3A}
     */
    public String render(int loggerWidth) {
        StringBuilder out = new StringBuilder();
        out.append(TIME.format(Instant.ofEpochMilli(timestamp)))
                .append("  ")
                .append(pad(level.name(), 5))
                .append("  ")
                .append(pad(shortLogger(), Math.max(1, loggerWidth)))
                .append("  ")
                .append(message == null ? "" : message);
        if (thrown != null && !thrown.isBlank()) {
            out.append('\n').append("      ").append(thrown);
        }
        return out.toString();
    }

    /**
     * 渲染为一行文本（用默认的记录器列宽）。
     *
     * @return 文本
     */
    public String render() {
        return render(LOGGER_MAX);
    }

    /**
     * 短记录器名：保留最后一段类名。
     *
     * @return 短名
     */
    public String shortLogger() {
        if (logger == null || logger.isBlank()) {
            return "-";
        }
        int dot = logger.lastIndexOf('.');
        return dot < 0 ? logger : logger.substring(dot + 1);
    }

    /**
     * 从异常对象取摘要。
     *
     * @param throwable 异常，可为 {@code null}
     * @return 形如 {@code java.io.IOException: 文件不存在}；无异常返回 {@code null}
     */
    public static String summaryOf(Throwable throwable) {
        if (throwable == null) {
            return null;
        }
        String type = throwable.getClass().getName();
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? type : type + ": " + message;
    }

    private static String pad(String text, int width) {
        String value = text == null ? "" : text;
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }
}
