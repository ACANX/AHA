package com.acanx.module.aha.common.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 工具调用的类别：决定区块配色与展示文案。
 *
 * <p><strong>CLI 与桌面端共用这一份判定。</strong>{@code GUIDesign.md} 第 3.1 节要求两端
 * 使用同一套语义（CLI 用 256 色、GUI 用十六进制，但「哪个工具算写入」必须一致），
 * 所以放在 {@code aha-common}：一侧改了、另一侧不会悄悄分叉。
 * 本类只依赖 JDK，符合 {@code aha-common} 的零外部依赖约定。</p>
 *
 * <p>读文件、写文件、执行命令、发请求这四类操作的「后果」完全不同——读取是安全的、
 * 写入会改数据、执行会跑命令。用颜色把它们分开，用户扫一眼就知道刚才发生了什么，
 * 不必逐行读工具名。</p>
 *
 * @since 0.1.0
 */
public enum ToolKind {

    /** 读取文件。 */
    READ("读取文件", "path", List.of("file-read", "file-list", "read")),

    /** 写入文件。 */
    WRITE("写入文件", "path", List.of("file-write", "write", "edit")),

    /** 执行命令。 */
    EXEC("执行命令", "command", List.of("shell-exec", "shell", "exec", "bash")),

    /** 请求网络。 */
    NETWORK("请求网络", "url", List.of("http-get", "http", "fetch")),

    /** 调用工具：未识别的一律归此，保持中性。 */
    OTHER("调用工具", null, List.of());

    private final String label;

    private final String targetKey;

    private final List<String> names;

    ToolKind(String label, String targetKey, List<String> names) {
        this.label = label;
        this.targetKey = targetKey;
        this.names = names;
    }

    /**
     * 中文标签（如「读取」）。
     *
     * @return 标签
     */
    public String label() {
        return label;
    }

    /**
     * 按工具名归类。
     *
     * @param toolName 工具名，可为 {@code null}
     * @return 类别；未识别时返回 {@link #OTHER}
     */
    public static ToolKind of(String toolName) {
        if (toolName == null) {
            return OTHER;
        }
        String name = toolName.toLowerCase(Locale.ROOT);
        for (ToolKind kind : values()) {
            if (kind.names.contains(name)) {
                return kind;
            }
        }
        return OTHER;
    }

    /**
     * 取最能说明「操作了什么」的参数值。
     *
     * <p>按类别优先取 {@code path} / {@code command} / {@code url}；取不到时退回第一个参数，
     * 让未识别的工具也有个可读的主语。</p>
     *
     * @param args 调用参数，可为 {@code null}
     * @return 目标描述；无法判断时返回 {@code null}
     */
    public String targetOf(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return null;
        }
        if (targetKey != null) {
            Object value = args.get(targetKey);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        Map<String, Object> ordered = new LinkedHashMap<>(args);
        for (Map.Entry<String, Object> entry : ordered.entrySet()) {
            if (entry.getValue() != null && !String.valueOf(entry.getValue()).isBlank()) {
                return String.valueOf(entry.getValue());
            }
        }
        return null;
    }

    /**
     * 首行第三段：本次操作最醒目的那个东西。
     *
     * <p>命令类取<b>程序名</b>（命令的第一个词），而不是整条命令——整条命令太长，
     * 它有自己的展示行（见 {@code ToolBlock.command}）。其它类别返回 {@code null}，
     * 目标值走明细行，那里可以折行、不会被截断。</p>
     *
     * @param args 调用参数，可为 {@code null}
     * @return 程序名；不适用时返回 {@code null}
     */
    public String programOf(Map<String, Object> args) {
        if (this != EXEC || args == null) {
            return null;
        }
        Object value = args.get("command");
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).strip();
        if (text.isEmpty()) {
            return null;
        }
        int end = 0;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return text.substring(0, end);
    }

    /**
     * 参数摘要（供区块第二行展示）。
     *
     * @param args 调用参数
     * @return 有序参数
     */
    public static Map<String, String> summary(Map<String, Object> args) {
        Map<String, String> result = new LinkedHashMap<>();
        if (args == null) {
            return result;
        }
        args.forEach((key, value) -> result.put(key, value == null ? "null" : String.valueOf(value)));
        return result;
    }
}
