package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.common.tool.ToolKind;

import java.util.Map;

/**
 * 工具卡片上的两行文字（纯函数，便于测试）。
 *
 * <p>口径来自 {@code GUIDesign.md} 第 4.2 节：首行是「类别 + 工具名 + 目标」，
 * 结果行是「状态 + 规模」。与 CLI 的区块结构一致（第 3.3 节要求两端同构）。</p>
 *
 * @since 0.2.0
 */
public final class ToolSummary {

    private ToolSummary() {
    }

    /**
     * 首行：类别 + 工具名 + 目标。
     *
     * @param toolName 工具名
     * @param args     调用参数，可为 {@code null}
     * @return 形如「读取  file-read  AgentEngine.java」
     */
    public static String head(String toolName, Map<String, Object> args) {
        ToolKind kind = ToolKind.of(toolName);
        String target = kind.targetOf(args);
        String prefix = kind == ToolKind.EXEC ? "" : target == null ? "  " : "  ";
        StringBuilder text = new StringBuilder();
        text.append(kind.label()).append("  ").append(toolName == null ? "(未知工具)" : toolName);
        if (target != null && !target.isBlank()) {
            text.append(prefix).append(target);
        }
        return text.toString();
    }

    /**
     * 结果行：状态 + 规模。
     *
     * @param success 是否成功
     * @param output  输出文本，可为 {@code null}
     * @return 形如「✓ 完成 · 412 行」
     */
    public static String result(boolean success, String output) {
        int lines = lines(output);
        String state = success ? "✓ 完成" : "✗ 失败";
        return lines > 0 ? state + " · " + lines + " 行" : state;
    }

    /**
     * 文本行数。
     *
     * @param text 文本，可为 {@code null}
     * @return 行数；空文本返回 0
     */
    public static int lines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /**
     * 把工具结果转成可展示文本。
     *
     * @param result 结果对象，可为 {@code null}
     * @return 文本；空返回 {@code null}
     */
    public static String textOf(Object result) {
        if (result == null) {
            return null;
        }
        String text = String.valueOf(result);
        return text.isBlank() ? null : text;
    }
}
