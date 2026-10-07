package com.acanx.module.aha.cli.session;

import java.io.PrintStream;

/**
 * 会话内命令的输出工具。
 *
 * @since 0.1.0
 */
public final class SessionOutput {

    private SessionOutput() {
    }

    /**
     * 输出一行「标签 值」。
     *
     * <p>标签统一为 2 个汉字（显示宽度一致），因此无需按字符数补齐——
     * 用 {@code %-Ns} 对齐中文会因宽度计算按 char 而非显示列而错位。</p>
     *
     * @param out   输出流
     * @param label 标签
     * @param value 值，为 {@code null} / 空时跳过
     */
    public static void field(PrintStream out, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        out.printf("  %s  %s%n", label, value);
    }
}
