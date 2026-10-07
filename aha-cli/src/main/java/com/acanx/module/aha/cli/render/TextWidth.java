package com.acanx.module.aha.cli.render;

/**
 * 字符显示宽度（East Asian Width 的实用子集）。
 *
 * <p>折行与对齐必须按<b>显示宽度</b>而非 {@code String.length()}：一个汉字、
 * 一个全角标点在终端占 2 列，而 Java 里只是 1 个 {@code char}。按长度计算必然错行，
 * 这是终端渲染最常被踩的坑。</p>
 *
 * @since 0.1.0
 */
public final class TextWidth {

    private TextWidth() {
    }

    /**
     * 计算字符串的显示宽度。
     *
     * @param text 文本，可为 {@code null}
     * @return 显示列数
     */
    public static int width(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int total = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            total += charWidth(codePoint);
            index += Character.charCount(codePoint);
        }
        return total;
    }

    /**
     * 计算单个码点的显示宽度。
     *
     * @param codePoint Unicode 码点
     * @return 0（组合符 / 控制符）、2（宽字符）或 1
     */
    public static int charWidth(int codePoint) {
        if (codePoint == 0) {
            return 0;
        }
        // 控制字符不占显示列
        if (codePoint < 0x20 || (codePoint >= 0x7F && codePoint < 0xA0)) {
            return 0;
        }
        // 组合记号、零宽字符、变体选择符
        if ((codePoint >= 0x0300 && codePoint <= 0x036F)
                || (codePoint >= 0x200B && codePoint <= 0x200F)
                || (codePoint >= 0xFE00 && codePoint <= 0xFE0F)
                || codePoint == 0xFEFF) {
            return 0;
        }
        return isWide(codePoint) ? 2 : 1;
    }

    /**
     * 是否为占两列的宽字符。
     *
     * @param codePoint Unicode 码点
     * @return 是否宽字符
     */
    /**
     * 按显示宽度截断文本。
     *
     * <p>按 {@code String.length()} 截断会让中文与 emoji 出现半字或超出预期列数，
     * 状态行与工具区块都要在有限宽度内成行，因此统一走这里。</p>
     *
     * @param text  文本，可为 {@code null}
     * @param limit 最大显示宽度；{@code <= 0} 时返回空串
     * @return 截断后的文本
     */
    public static String truncate(String text, int limit) {
        if (text == null || limit <= 0) {
            return "";
        }
        if (width(text) <= limit) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        int used = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int cell = charWidth(codePoint);
            if (used + cell > limit) {
                break;
            }
            result.appendCodePoint(codePoint);
            used += cell;
            index += Character.charCount(codePoint);
        }
        return result.toString();
    }

    /**
     * 用空格补齐到指定显示宽度（超出则先截断）。
     *
     * @param text  文本
     * @param width 目标显示宽度
     * @return 定宽文本
     */
    public static String pad(String text, int width) {
        String truncated = truncate(text, width);
        return truncated + " ".repeat(Math.max(0, width - width(truncated)));
    }

    public static boolean isWide(int codePoint) {
        return (codePoint >= 0x1100 && codePoint <= 0x115F)
                || (codePoint >= 0x2E80 && codePoint <= 0x303E)
                || (codePoint >= 0x3041 && codePoint <= 0x33FF)
                || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
                || (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
                || (codePoint >= 0xA000 && codePoint <= 0xA4CF)
                || (codePoint >= 0xAC00 && codePoint <= 0xD7A3)
                || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
                || (codePoint >= 0xFE30 && codePoint <= 0xFE6F)
                || (codePoint >= 0xFF00 && codePoint <= 0xFF60)
                || (codePoint >= 0xFFE0 && codePoint <= 0xFFE6)
                || (codePoint >= 0x1F300 && codePoint <= 0x1F64F)
                || (codePoint >= 0x1F900 && codePoint <= 0x1F9FF)
                || (codePoint >= 0x20000 && codePoint <= 0x3FFFD);
    }
    /**
     * 按显示宽度折行。
     *
     * <p>按码点折、不按词折——与流式输出用的 {@code LineWrapper} 保持一致。
     * 命令、路径、URL 这类没有空格可折的内容本来也只能按字符断。</p>
     *
     * @param text  文本，可为 {@code null}
     * @param width 每行最大显示宽度；{@code <= 0} 表示不折行
     * @return 行列表
     */
    public static java.util.List<String> wrap(String text, int width) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        if (width <= 0) {
            lines.add(text);
            return lines;
        }
        StringBuilder line = new StringBuilder();
        int used = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int count = Character.charCount(codePoint);
            index += count;
            if (codePoint == '\n') {
                lines.add(line.toString());
                line.setLength(0);
                used = 0;
                continue;
            }
            if (codePoint == '\r') {
                continue;
            }
            int cell = charWidth(codePoint);
            if (used > 0 && used + cell > width) {
                lines.add(line.toString());
                line.setLength(0);
                used = 0;
            }
            line.appendCodePoint(codePoint);
            used += cell;
        }
        lines.add(line.toString());
        return lines;
    }

}
