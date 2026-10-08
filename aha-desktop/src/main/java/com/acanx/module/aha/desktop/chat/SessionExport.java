package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.Role;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * 会话导出（纯函数）。
 *
 * <p>两种格式各有用处：Markdown 给人看、JSON 给脚本与后续工具看。
 * 两者都在这里生成，界面只负责选路径与写文件——于是「导出内容对不对」可以脱离图形环境断言。</p>
 *
 * <p>JSON 是**手写**的：为了一个导出功能给桌面模块加上 Jackson 的 JPMS 依赖不值得，
 * 而转义规则就那么几条（引号、反斜杠、控制字符、换行），用测试钉住比引依赖更划算。</p>
 *
 * @since 0.2.0
 */
public final class SessionExport {

    /** 文件名里不允许出现的字符（Windows 最严格，按它来）。 */
    private static final String ILLEGAL = "[\\\\/:*?\"<>|\\s]+";

    /** 文件名主体长度上限。 */
    public static final int NAME_MAX = 60;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private SessionExport() {
    }

    /**
     * 导出为 Markdown。
     *
     * @param title     会话标题
     * @param sessionId 会话 ID
     * @param history   历史消息
     * @param nowMillis 导出时刻
     * @return Markdown 文本
     */
    public static String markdown(String title, String sessionId,
                                  List<ChatMessage> history, long nowMillis) {
        List<ChatMessage> messages = history == null ? List.of() : history;
        StringBuilder out = new StringBuilder();
        out.append("# ").append(SessionList.trim(title)).append("\n\n");
        out.append("- 会话 ID：`").append(sessionId == null ? "" : sessionId).append("`\n");
        out.append("- 导出时间：").append(STAMP.format(Instant.ofEpochMilli(nowMillis))).append('\n');
        out.append("- 消息条数：").append(messages.size()).append("\n\n");
        for (ChatMessage message : messages) {
            String text = message.content();
            if (text == null || text.isBlank()) {
                continue;
            }
            out.append("---\n\n## ").append(heading(message)).append("\n\n")
                    .append(text.strip()).append("\n\n");
        }
        return out.toString();
    }

    /**
     * 导出为 JSON。
     *
     * @param title     会话标题
     * @param sessionId 会话 ID
     * @param history   历史消息
     * @param nowMillis 导出时刻
     * @return JSON 文本
     */
    public static String json(String title, String sessionId,
                              List<ChatMessage> history, long nowMillis) {
        List<ChatMessage> messages = history == null ? List.of() : history;
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        out.append("  \"title\": ").append(quote(SessionList.trim(title))).append(",\n");
        out.append("  \"sessionId\": ").append(quote(sessionId)).append(",\n");
        out.append("  \"exportedAt\": ").append(nowMillis).append(",\n");
        out.append("  \"messages\": [\n");
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            out.append("    {\"role\": ").append(quote(String.valueOf(message.role())))
                    .append(", \"content\": ").append(quote(message.content()))
                    .append(", \"toolCallId\": ").append(quote(message.toolCallId()))
                    .append('}');
            out.append(i + 1 < messages.size() ? "," : "").append('\n');
        }
        out.append("  ]\n");
        out.append("}\n");
        return out.toString();
    }

    /**
     * 建议的文件名（去掉非法字符）。
     *
     * @param title     标题
     * @param extension 扩展名（不含点）
     * @return 形如 {@code aha-会话标题.md}
     */
    public static String fileName(String title, String extension) {
        String base = title == null || title.isBlank()
                ? SessionList.trim(title)
                : title.strip().replaceAll(ILLEGAL, "_");
        if (base.isBlank()) {
            base = "aha-session";
        }
        if (base.length() > NAME_MAX) {
            base = base.substring(0, NAME_MAX);
        }
        return "aha-" + base + "." + extension;
    }

    /**
     * Markdown 里那一节的标题。
     *
     * @param message 消息
     * @return 「你」/「助手」/「系统」/「工具 <toolCallId>」
     */
    static String heading(ChatMessage message) {
        Role role = message.role();
        if (role == Role.USER) {
            return "你";
        }
        if (role == Role.ASSISTANT) {
            return "助手";
        }
        if (role == Role.TOOL) {
            return "工具" + (message.toolCallId() == null || message.toolCallId().isBlank()
                    ? "" : " · " + message.toolCallId());
        }
        return "系统";
    }

    /**
     * JSON 字符串字面量（含转义）。
     *
     * @param text 文本，可为 {@code null}
     * @return 带引号的字面量
     */
    static String quote(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
