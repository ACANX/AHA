package com.acanx.module.aha.cli.session;

import org.jline.reader.Highlighter;
import org.jline.reader.LineReader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;

import java.util.Locale;

/**
 * 会话内命令实时着色：命令是否被支持，敲下去就看得见。
 *
 * <p>此前只能按回车才知道命令对不对——错了就变成一句「未知命令」，
 * 输入过程中毫无反馈。这里把命令名着成绿色（已支持）或红色（未知），
 * 在<b>回车之前</b>就能判断。</p>
 *
 * <p>只用前景色，不改动文本本身：缓冲区内容始终是用户敲下的原文。</p>
 *
 * @since 0.1.0
 */
public final class SessionCommandHighlighter implements Highlighter {

    /** 已支持的命令。 */
    private static final AttributedStyle VALID =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.GREEN);

    /** 未知命令。 */
    private static final AttributedStyle UNKNOWN =
            AttributedStyle.DEFAULT.foreground(AttributedStyle.RED);

    private final SessionCommandRegistry registry;

    private final boolean color;

    /**
     * 创建高亮器。
     *
     * @param registry 命令注册表
     * @param color    终端是否支持颜色
     */
    public SessionCommandHighlighter(SessionCommandRegistry registry, boolean color) {
        this.registry = registry;
        this.color = color;
    }

    @Override
    public AttributedString highlight(LineReader reader, String buffer) {
        if (!color || buffer == null || buffer.length() < 2 || buffer.charAt(0) != '/') {
            return new AttributedString(buffer == null ? "" : buffer);
        }
        int end = 1;
        while (end < buffer.length() && isNameChar(buffer.charAt(end))) {
            end++;
        }
        if (end == 1) {
            // 只敲了 `/`：还没有可判断的命令名
            return new AttributedString(buffer);
        }
        // 命令名之后必须跟空白或行尾，否则是 /usr/bin/ls 这类路径，不当作命令判断
        if (end < buffer.length() && !Character.isWhitespace(buffer.charAt(end))) {
            return new AttributedString(buffer);
        }
        boolean valid = registry.find(buffer.substring(1, end)).isPresent();
        return new AttributedString(buffer, valid ? VALID : UNKNOWN);
    }

    /**
     * 是否为命令名允许的字符（与注册表的解析保持一致）。
     *
     * @param c 字符
     * @return 允许时为 {@code true}
     */
    private static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '-';
    }

    /**
     * 命令名小写（供扩展点复用）。
     *
     * @param name 命令名
     * @return 小写
     */
    static String normalize(String name) {
        return name.toLowerCase(Locale.ROOT);
    }
}
