package com.acanx.module.aha.cli.render;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Markdown 表格渲染。
 *
 * <p>把 {@code | a | b |} 这类行还原成对齐的表格。之所以要缓冲整个表格：不先扫一遍
 * 就不知道每列该多宽，逐行输出必然是参差不齐的——那还不如不渲染。</p>
 *
 * <p>表格宽度超出终端时<b>压缩列宽</b>而不是让它折行：表格一旦折行，行列对应关系
 * 就彻底乱了，比挤一点更难读。</p>
 *
 * <p>框线字符需要终端支持制表符，不支持时退化为 ASCII（{@code +---+} / {@code | a |}）。</p>
 *
 * @since 0.1.0
 */
final class TableRenderer {

    /** 判定分隔行（{@code |---|:--:|}）。 */
    private static final Pattern SEPARATOR_CELL = Pattern.compile("^:?-{1,}:?$");

    /** 左对齐（{@code :---}）。 */
    private static final Pattern ALIGN_LEFT = Pattern.compile("^:-+$");

    /** 右对齐（{@code ---:}）。 */
    private static final Pattern ALIGN_RIGHT = Pattern.compile("^-+:$");

    /** 居中（{@code :---:}）。 */
    private static final Pattern ALIGN_CENTER = Pattern.compile("^:-+:$");

    /** 单列最小宽度，低于它内容就完全不可读了。 */
    private static final int MIN_COLUMN_WIDTH = 3;

    private TableRenderer() {
    }

    /** 列对齐方式。 */
    private enum Align {
        /** 左对齐。 */
        LEFT,
        /** 居中。 */
        CENTER,
        /** 右对齐。 */
        RIGHT
    }

    /**
     * 渲染表格。
     *
     * @param rawRows 原始行（含表头与分隔行）
     * @param style   样式
     * @param width   终端宽度；{@code <= 0} 表示不限宽
     * @param box     是否可用制表符
     * @return 渲染结果（以换行结尾）；无有效行时返回空串
     */
    static String render(List<String> rawRows, Style style, int width, boolean box) {
        List<List<String>> rows = new ArrayList<>();
        int separatorIndex = -1;
        for (String raw : rawRows) {
            List<String> cells = split(raw);
            // 跳过全空行：Markdown 表格后面常跟一个换行，否则会多出一整行空框
            if (cells.stream().allMatch(String::isEmpty)) {
                continue;
            }
            // 只认第一个分隔行：它是表头与表体的分界，后面的分隔行按普通内容处理
            if (separatorIndex < 0 && isSeparator(cells)) {
                separatorIndex = rows.size();
            }
            rows.add(cells);
        }
        List<List<String>> body = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            if (i != separatorIndex) {
                body.add(rows.get(i));
            }
        }
        if (body.isEmpty()) {
            return "";
        }

        int columns = 0;
        for (List<String> row : body) {
            columns = Math.max(columns, row.size());
        }
        if (columns == 0) {
            return "";
        }

        int[] widths = new int[columns];
        for (List<String> row : body) {
            for (int i = 0; i < row.size(); i++) {
                widths[i] = Math.max(widths[i], renderedWidth(row.get(i), style));
            }
        }
        fit(widths, width);
        Align[] aligns = aligns(separatorIndex >= 0 ? rows.get(separatorIndex) : List.of(), columns);
        boolean hasHeader = separatorIndex > 0;

        String[] c = box
                ? new String[]{"┌", "┬", "┐", "├", "┼", "┤", "└", "┴", "┘", "─", "│"}
                : new String[]{"+", "+", "+", "+", "+", "+", "+", "+", "+", "-", "|"};

        StringBuilder out = new StringBuilder();
        out.append(style.tableBorder(border(widths, c, 0, 1, 2))).append('\n');
        for (int i = 0; i < body.size(); i++) {
            boolean header = hasHeader && i == 0;
            out.append(row(body.get(i), widths, aligns, c[10], style, header)).append('\n');
            if (header) {
                out.append(style.tableBorder(border(widths, c, 3, 4, 5))).append('\n');
            }
        }
        out.append(style.tableBorder(border(widths, c, 6, 7, 8))).append('\n');
        return out.toString();
    }

    /**
     * 拆分一行。
     *
     * @param raw 原始行
     * @return 单元格
     */
    private static List<String> split(String raw) {
        String text = raw.trim();
        if (text.startsWith("|")) {
            text = text.substring(1);
        }
        if (text.endsWith("|")) {
            text = text.substring(0, text.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        for (String cell : text.split("\\|", -1)) {
            cells.add(cell.trim());
        }
        return cells;
    }

    /**
     * 是否全为分隔单元格。
     *
     * @param cells 单元格
     * @return 是分隔行时为 {@code true}
     */
    private static boolean isSeparator(List<String> cells) {
        if (cells.isEmpty()) {
            return false;
        }
        for (String cell : cells) {
            if (!SEPARATOR_CELL.matcher(cell).matches()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 从分隔行解析对齐方式。
     *
     * @param separator 分隔行的单元格
     * @param columns   列数
     * @return 每列对齐方式
     */
    private static Align[] aligns(List<String> separator, int columns) {
        Align[] result = new Align[columns];
        for (int i = 0; i < columns; i++) {
            String cell = i < separator.size() ? separator.get(i) : "---";
            if (ALIGN_CENTER.matcher(cell).matches()) {
                result[i] = Align.CENTER;
            } else if (ALIGN_RIGHT.matcher(cell).matches()) {
                result[i] = Align.RIGHT;
            } else if (ALIGN_LEFT.matcher(cell).matches()) {
                result[i] = Align.LEFT;
            } else {
                result[i] = Align.LEFT;
            }
        }
        return result;
    }

    /**
     * 压缩列宽以适配终端。
     *
     * @param widths 列宽（原地修改）
     * @param width  终端宽度；{@code <= 0} 表示不限宽
     */
    private static void fit(int[] widths, int width) {
        if (width <= 0) {
            return;
        }
        // 每列各占「左右边框 + 内容」，再加最左最右两根竖线
        while (total(widths) + 3 * widths.length + 1 > width) {
            int widest = 0;
            for (int i = 1; i < widths.length; i++) {
                if (widths[i] > widths[widest]) {
                    widest = i;
                }
            }
            if (widths[widest] <= MIN_COLUMN_WIDTH) {
                break;
            }
            widths[widest]--;
        }
    }

    /**
     * 列宽之和。
     *
     * @param widths 列宽
     * @return 总和
     */
    private static int total(int[] widths) {
        int sum = 0;
        for (int w : widths) {
            sum += w;
        }
        return sum;
    }

    /**
     * 生成一条横线。
     *
     * @param widths 列宽
     * @param c      框线字符表
     * @param left   左端字符下标
     * @param middle 交点字符下标
     * @param right  右端字符下标
     * @return 横线
     */
    private static String border(int[] widths, String[] c, int left, int middle, int right) {
        StringBuilder text = new StringBuilder(c[left]);
        for (int i = 0; i < widths.length; i++) {
            if (i > 0) {
                text.append(c[middle]);
            }
            text.append(c[9].repeat(widths[i] + 2));
        }
        return text.append(c[right]).toString();
    }

    /**
     * 生成一行内容。
     *
     * @param cells  单元格
     * @param widths 列宽
     * @param aligns 对齐方式
     * @param vbar   竖线字符
     * @param style  样式
     * @param header 是否表头
     * @return 行文本
     */
    private static String row(List<String> cells, int[] widths, Align[] aligns, String vbar,
                              Style style, boolean header) {
        StringBuilder text = new StringBuilder(style.tableBorder(vbar));
        for (int i = 0; i < widths.length; i++) {
            String cell = i < cells.size() ? cells.get(i) : "";
            // 先补齐再上色：把含 SGR 的串交给 TextWidth 会把转义序列当普通字符，宽度算错
            text.append(' ').append(pad(cell, widths[i], aligns[i], style, header)).append(' ')
                    .append(style.tableBorder(vbar));
        }
        return text.toString();
    }

    /**
     * 按对齐方式补齐到列宽。
     *
     * @param cell   单元格内容
     * @param width  列宽
     * @param align  对齐方式
     * @return 定宽文本
     */
    private static String pad(String cell, int width, Align align, Style style, boolean header) {
        // 顺序很重要：先渲染行内样式，再判断放不放得下。
        // 反过来先按列宽截断纯文本，会把 ` 或 ** 切成两半——解析不出闭合定界符，
        // 于是整格退回字面量，屏幕上就出现了没被渲染的星号和反引号。
        String rendered = render(cell, style, header);
        int used = TextWidth.width(stripSgr(rendered));
        if (used > width) {
            // 真放不下（超宽链接会展开成「文本 <地址>」）：以对齐为先，退回纯文本
            rendered = TextWidth.truncate(stripSgr(rendered), width);
            used = TextWidth.width(rendered);
        }
        int padding = Math.max(0, width - used);
        return switch (align) {
            case RIGHT -> " ".repeat(padding) + rendered;
            case CENTER -> {
                int left = padding / 2;
                yield " ".repeat(left) + rendered + " ".repeat(padding - left);
            }
            default -> rendered + " ".repeat(padding);
        };
    }

    /**
     * 渲染单元格内容（行内代码、强调、链接）。
     *
     * @param cell   纯文本单元格
     * @param style  样式
     * @param header 是否表头
     * @return 已着色文本
     */
    private static String render(String cell, Style style, boolean header) {
        String text = InlineScanner.render(cell, style);
        // 表头加粗只在没有行内样式时叠加：行内样式自带 [0m 复位，会把外层加粗一起清掉
        return header && !hasInlineMarkup(cell) ? style.tableHeader(text) : text;
    }

    /**
     * 单元格解析行内样式之后的显示宽度。
     *
     * @param cell  纯文本单元格
     * @param style 样式
     * @return 显示宽度
     */
    private static int renderedWidth(String cell, Style style) {
        return TextWidth.width(stripSgr(render(cell, style, false)));
    }

    /**
     * 去掉 SGR 序列。
     *
     * @param text 文本
     * @return 纯文本
     */
    private static String stripSgr(String text) {
        return text.replaceAll("\u001B\\[[0-9;]*m", "");
    }

    /**
     * 是否含行内标记。
     *
     * @param text 文本
     * @return 含标记时为 {@code true}
     */
    private static boolean hasInlineMarkup(String text) {
        return text.indexOf('`') >= 0 || text.indexOf('*') >= 0 || text.indexOf('_') >= 0
                || text.indexOf('[') >= 0;
    }
}
