package com.acanx.module.aha.cli.render;

import java.io.PrintStream;

/**
 * 按显示宽度折行的写出器。
 *
 * <p>流式输出是<b>增量</b>的：模型正文以任意长度的分片到达，因此换行判断必须跨分片
 * 记住当前列号，不能对每次分片单独处理。</p>
 *
 * <p>宽度为 {@code 0}（未知，或输出被重定向）时不折行——脚本与管道不应被插入硬换行。</p>
 *
 * @since 0.1.0
 */
public final class LineWrapper {

    /** 转义序列起始符。 */
    private static final char ESC = '\u001B';

    private final PrintStream out;
    private final int width;
    private final String newline;
    private int column;

    /**
     * 创建写出器。
     *
     * @param out   输出流
     * @param width 折行宽度；{@code <= 0} 表示不折行
     */
    public LineWrapper(PrintStream out, int width) {
        this(out, width, System.lineSeparator());
    }

    /**
     * 创建写出器（可指定换行符，便于测试）。
     *
     * @param out     输出流
     * @param width   折行宽度；{@code <= 0} 表示不折行
     * @param newline 换行符
     */
    public LineWrapper(PrintStream out, int width, String newline) {
        this.out = out;
        this.width = Math.max(width, 0);
        this.newline = newline;
    }

    /**
     * 写入文本，必要时折行。
     *
     * @param text 文本，可为 {@code null}
     */
    public void write(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\n') {
                newline();
                index++;
                continue;
            }
            if (current == '\r') {
                index++;
                continue;
            }
            if (current == ESC) {
                // 跳过转义序列：不计宽度，也不在序列中间折行
                int end = skipEscape(text, index);
                out.append(text, index, end);
                index = end;
                continue;
            }
            int codePoint = text.codePointAt(index);
            int count = Character.charCount(codePoint);
            int cellWidth = TextWidth.charWidth(codePoint);
            // column > 0 条件避免「单个宽字符比整行还宽」时死循环
            if (width > 0 && column > 0 && column + cellWidth > width) {
                newline();
            }
            out.append(text, index, index + count);
            column += cellWidth;
            index += count;
        }
    }

    /**
     * 换行并重置列号。
     */
    public void newline() {
        out.print(newline);
        column = 0;
    }

    /**
     * 当前列号。
     *
     * @return 列号；{@code 0} 表示位于行首
     */
    public int column() {
        return column;
    }

    /**
     * 若不在行首则换行。
     */
    public void ensureLineStart() {
        if (column > 0) {
            newline();
        }
    }

    /**
     * 跳过一段 ANSI 转义序列。
     *
     * <p>形如 {@code ESC [ 参数 终止字母}。只需支持 SGR 等 CSI 序列；
     * 遇到无法识别的内容时退化为跳过 {@code ESC} 本身。</p>
     *
     * @param text  文本
     * @param start {@code ESC} 所在下标
     * @return 序列结束后的下标
     */
    private static int skipEscape(String text, int start) {
        int index = start + 1;
        if (index < text.length() && text.charAt(index) == '[') {
            index++;
            while (index < text.length()) {
                char c = text.charAt(index++);
                if (c >= '@' && c <= '~') {
                    return index;
                }
            }
            return text.length();
        }
        return Math.min(index, text.length());
    }
}
