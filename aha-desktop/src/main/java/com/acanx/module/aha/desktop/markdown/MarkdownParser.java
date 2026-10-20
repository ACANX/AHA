package com.acanx.module.aha.desktop.markdown;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 桌面端 Markdown 块级解析器（issue #413 / tracking #408 S1.1）。
 *
 * <p>输入全文、输出 {@link MarkdownBlock} 列表。刻意不引用任何 JavaFX 类型：
 * 解析行为因此能在无头环境直接断言（与 {@code AssistantSegments}、{@code FoldState} 同一思路），
 * 渲染交给 S1.5 的 {@code MarkdownRenderer}。</p>
 *
 * <p>与 CLI {@code MarkdownStream} 共用语义基线（{@code MarkdownSemanticsSamples}）：
 * 空行终止表格、围栏内不解析、行内定界符侧翼规则都由两端一起守住。</p>
 *
 * <p>本阶段只做块级切分；行内标记（粗体 / 代码 / 链接…）保留原文，由 S1.2 再解析。
 * 代码块内的内容不再被任何规则解释。</p>
 *
 * @since 0.2.0
 */
public final class MarkdownParser {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})[ \\t]+(.*)$");

    private static final Pattern RULE = Pattern.compile("^\\s{0,3}((\\*\\s*){3,}|(-\\s*){3,}|(_\\s*){3,})$");

    private static final Pattern UNORDERED = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");

    private static final Pattern ORDERED = Pattern.compile("^(\\s*)(\\d+)[.)]\\s+(.*)$");

    private static final Pattern SEPARATOR_CELL = Pattern.compile("^:?-{3,}:?$");

    /**
     * 解析 Markdown 全文。
     *
     * @param markdown Markdown 文本，可为 {@code null}
     * @return 块列表（按出现顺序）；空输入返回空列表
     */
    public List<MarkdownBlock> parse(String markdown) {
        List<MarkdownBlock> blocks = new ArrayList<>();
        if (markdown == null || markdown.isEmpty()) {
            return blocks;
        }
        List<String> lines = splitLines(markdown);
        int index = 0;
        while (index < lines.size()) {
            String line = lines.get(index);
            if (line.isBlank()) {
                index++;
                continue;
            }

            int next = parseFence(lines, index, blocks);
            if (next > index) {
                index = next;
                continue;
            }
            next = parseHeading(line, index, blocks);
            if (next > index) {
                index = next;
                continue;
            }
            next = parseRule(line, index, blocks);
            if (next > index) {
                index = next;
                continue;
            }
            next = parseTable(lines, index, blocks);
            if (next > index) {
                index = next;
                continue;
            }
            next = parseQuote(lines, index, blocks);
            if (next > index) {
                index = next;
                continue;
            }
            next = parseList(lines, index, blocks);
            if (next > index) {
                index = next;
                continue;
            }
            index = parseParagraph(lines, index, blocks);
        }
        return blocks;
    }

    // ------------------------------------------------------------------ 围栏

    /**
     * 围栏代码块：起始行 ≥3 个反引号或波浪线，结束行为同字符、长度不小于起始行。
     *
     * @param lines 全部行
     * @param start 起始下标
     * @param out   块列表
     * @return 下一个下标；不是围栏时返回 {@code start}
     */
    private int parseFence(List<String> lines, int start, List<MarkdownBlock> out) {
        String line = lines.get(start);
        String stripped = line.stripLeading();
        FenceMarker marker = FenceMarker.of(stripped);
        if (marker == null) {
            return start;
        }
        String language = stripped.substring(marker.length()).strip();
        List<String> content = new ArrayList<>();
        int index = start + 1;
        while (index < lines.size()) {
            String current = lines.get(index).stripLeading();
            if (FenceMarker.isClosing(current, marker)) {
                index++;
                break;
            }
            content.add(lines.get(index));
            index++;
        }
        out.add(new MarkdownBlock.Fence(language, String.join("\n", content)));
        return index;
    }

    /**
     * 围栏标记（字符 + 长度）。
     *
     * @param character 反引号或波浪线
     * @param length    连写长度
     */
    private record FenceMarker(char character, int length) {

        static FenceMarker of(String stripped) {
            if (stripped.isEmpty()) {
                return null;
            }
            char first = stripped.charAt(0);
            if (first != '`' && first != '~') {
                return null;
            }
            int count = 0;
            while (count < stripped.length() && stripped.charAt(count) == first) {
                count++;
            }
            return count >= 3 ? new FenceMarker(first, count) : null;
        }

        static boolean isClosing(String stripped, FenceMarker marker) {
            if (stripped.isEmpty() || stripped.charAt(0) != marker.character()) {
                return false;
            }
            int count = 0;
            while (count < stripped.length() && stripped.charAt(count) == marker.character()) {
                count++;
            }
            return count >= marker.length() && stripped.substring(count).isBlank();
        }
    }

    // ------------------------------------------------------------------ 单行块

    private int parseHeading(String line, int start, List<MarkdownBlock> out) {
        Matcher matcher = HEADING.matcher(line);
        if (!matcher.matches()) {
            return start;
        }
        out.add(new MarkdownBlock.Heading(matcher.group(1).length(), matcher.group(2).strip()));
        return start + 1;
    }

    private int parseRule(String line, int start, List<MarkdownBlock> out) {
        if (!RULE.matcher(line).matches()) {
            return start;
        }
        out.add(new MarkdownBlock.Rule());
        return start + 1;
    }

    // ------------------------------------------------------------------ 表格

    /**
     * 表格：行首为 {@code |} 的连续行，第二行必须是分隔行。
     *
     * <p>空行终止表格（与 #346 同语义）：数据行遇到空行即结束，后文另起块。</p>
     */
    private int parseTable(List<String> lines, int start, List<MarkdownBlock> out) {
        if (!lines.get(start).stripLeading().startsWith("|")) {
            return start;
        }
        List<String> rawRows = new ArrayList<>();
        int index = start;
        while (index < lines.size()) {
            String current = lines.get(index);
            if (current.isBlank() || !current.stripLeading().startsWith("|")) {
                break;
            }
            rawRows.add(current);
            index++;
        }
        if (rawRows.size() < 2) {
            return start;
        }
        List<String> header = splitRow(rawRows.get(0));
        List<String> separator = splitRow(rawRows.get(1));
        if (header.isEmpty() || separator.size() != header.size() || !isSeparator(separator)) {
            return start;
        }
        int columns = header.size();
        List<MarkdownBlock.Align> aligns = new ArrayList<>(columns);
        for (String cell : separator) {
            aligns.add(alignOf(cell));
        }
        List<List<String>> rows = new ArrayList<>();
        for (int i = 2; i < rawRows.size(); i++) {
            rows.add(fit(splitRow(rawRows.get(i)), columns));
        }
        out.add(new MarkdownBlock.TableBlock(header, aligns, rows));
        return index;
    }

    private static List<String> splitRow(String row) {
        String trimmed = row.strip();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isBlank()) {
            return List.of();
        }
        String[] cells = trimmed.split("\\|", -1);
        List<String> result = new ArrayList<>(cells.length);
        for (String cell : cells) {
            result.add(cell.strip());
        }
        return result;
    }

    private static boolean isSeparator(List<String> cells) {
        if (cells.isEmpty()) {
            return false;
        }
        return cells.stream().allMatch(cell -> SEPARATOR_CELL.matcher(cell).matches());
    }

    private static MarkdownBlock.Align alignOf(String cell) {
        String trimmed = cell.strip();
        boolean left = trimmed.startsWith(":");
        boolean right = trimmed.endsWith(":");
        if (left && right) {
            return MarkdownBlock.Align.CENTER;
        }
        return right ? MarkdownBlock.Align.RIGHT : MarkdownBlock.Align.LEFT;
    }

    private static List<String> fit(List<String> cells, int columns) {
        List<String> fitted = new ArrayList<>(columns);
        for (int i = 0; i < columns; i++) {
            fitted.add(i < cells.size() ? cells.get(i) : "");
        }
        return fitted;
    }

    // ------------------------------------------------------------------ 引用

    /**
     * 引用：连续以 {@code >} 开头的行，去掉标记后递归解析（支持嵌套引用）。
     */
    private int parseQuote(List<String> lines, int start, List<MarkdownBlock> out) {
        if (!isQuote(lines.get(start))) {
            return start;
        }
        List<String> inner = new ArrayList<>();
        int index = start;
        while (index < lines.size() && isQuote(lines.get(index))) {
            inner.add(unquote(lines.get(index)));
            index++;
        }
        out.add(new MarkdownBlock.Quote(parse(String.join("\n", inner))));
        return index;
    }

    private static boolean isQuote(String line) {
        return line.stripLeading().startsWith(">");
    }

    private static String unquote(String line) {
        String stripped = line.stripLeading().substring(1);
        return stripped.startsWith(" ") ? stripped.substring(1) : stripped;
    }

    // ------------------------------------------------------------------ 列表

    /**
     * 列表：连续的无序 / 有序列表项；缩进记录在 {@link MarkdownBlock.ListItem#indent()}。
     */
    private int parseList(List<String> lines, int start, List<MarkdownBlock> out) {
        ListItemMatch first = ListItemMatch.of(lines.get(start));
        if (first == null) {
            return start;
        }
        List<MarkdownBlock.ListItem> items = new ArrayList<>();
        int index = start;
        while (index < lines.size()) {
            ListItemMatch match = ListItemMatch.of(lines.get(index));
            if (match == null) {
                break;
            }
            items.add(new MarkdownBlock.ListItem(match.indent(), match.text()));
            index++;
        }
        out.add(new MarkdownBlock.ListBlock(first.ordered(), first.start(), items));
        return index;
    }

    /**
     * 一行列表项。
     *
     * @param indent  行首空格数
     * @param ordered 是否有序
     * @param start   有序列表的起始号
     * @param text    文本
     */
    private record ListItemMatch(int indent, boolean ordered, int start, String text) {

        static ListItemMatch of(String line) {
            Matcher ordered = ORDERED.matcher(line);
            if (ordered.matches()) {
                return new ListItemMatch(ordered.group(1).length(), true,
                        Integer.parseInt(ordered.group(2)), ordered.group(3).strip());
            }
            Matcher unordered = UNORDERED.matcher(line);
            if (unordered.matches()) {
                return new ListItemMatch(unordered.group(1).length(), false, 1,
                        unordered.group(2).strip());
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ 段落

    /**
     * 段落：连续的非空行，遇到空行或下一个块的起始行即结束。
     */
    private int parseParagraph(List<String> lines, int start, List<MarkdownBlock> out) {
        List<String> text = new ArrayList<>();
        int index = start;
        while (index < lines.size()) {
            String line = lines.get(index);
            if (line.isBlank() || startsBlock(line)) {
                break;
            }
            text.add(line);
            index++;
        }
        if (index == start) {
            // 兜底：当前行确实无法识别为任何块，按原样收进段落，避免死循环
            text.add(lines.get(start));
            index = start + 1;
        }
        out.add(new MarkdownBlock.Paragraph(String.join("\n", text)));
        return index;
    }

    /** 段落是否应在此行之前结束。 */
    private static boolean startsBlock(String line) {
        return FenceMarker.of(line.stripLeading()) != null
                || HEADING.matcher(line).matches()
                || RULE.matcher(line).matches()
                || isQuote(line)
                || ListItemMatch.of(line) != null;
    }

    // ------------------------------------------------------------------ 工具

    private static List<String> splitLines(String markdown) {
        String normalized = markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] parts = normalized.split("\n", -1);
        List<String> lines = new ArrayList<>(parts.length);
        for (String part : parts) {
            lines.add(part);
        }
        return lines;
    }
}
