package com.acanx.module.aha.desktop.markdown;

import java.util.ArrayList;
import java.util.List;

/**
 * 行内 Markdown 解析器（issue #414 / tracking #408 S1.2）。
 *
 * <p>规则与 CLI {@code InlineScanner} 一致（共享样本见 {@code MarkdownSemanticsSamples}）：
 * 定界符必须满足 CommonMark 的**侧翼**条件，{@code 2 * 3 * 4} 与 {@code snake_case_name}
 * 因此不会被误判；4 个及以上连续定界符按字面量。</p>
 *
 * <p>与 CLI 的差别只在输出：这里输出 {@link MarkdownInline} 树，CLI 输出带 SGR 的字符串。
 * 解析器同样不引用任何 JavaFX 类型，能在无头环境直接断言。</p>
 *
 * @since 0.2.0
 */
public final class MarkdownInlineParser {

    /** 递归深度上限。 */
    private static final int MAX_DEPTH = 4;

    /**
     * 解析一段文本。
     *
     * @param text 文本，可为 {@code null}
     * @return 行内元素列表；空输入返回空列表
     */
    public List<MarkdownInline> parse(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return parseRange(text, 0, text.length(), 0);
    }

    private List<MarkdownInline> parseRange(String text, int from, int to, int depth) {
        List<MarkdownInline> result = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int index = from;
        while (index < to) {
            char current = text.charAt(index);

            if (current == '`') {
                int end = text.indexOf('`', index + 1);
                if (end > index + 1 && end < to) {
                    flush(result, literal);
                    result.add(new MarkdownInline.Code(text.substring(index + 1, end)));
                    index = end + 1;
                    continue;
                }
            }

            if (current == '*' || current == '_') {
                int run = countRun(text, index, to, current);
                if (run >= 4) {
                    // 4 个及以上按字面量整体输出：不能只跳 1 个，否则剩下的 3 个会被当成粗斜体
                    literal.append(text, index, index + run);
                    index += run;
                    continue;
                }
                boolean opener = openerFlanks(text, index, run, to, current);
                int closer = opener ? findCloser(text, index + run, to, current, run) : -1;
                if (closer > 0) {
                    flush(result, literal);
                    List<MarkdownInline> children = depth < MAX_DEPTH
                            ? parseRange(text, index + run, closer, depth + 1)
                            : List.of(new MarkdownInline.Text(text.substring(index + run, closer)));
                    result.add(wrap(run, children));
                    index = closer + run;
                    continue;
                }
            }

            if (current == '~' && index + 1 < to && text.charAt(index + 1) == '~') {
                int end = text.indexOf("~~", index + 2);
                if (end > index + 2 && end < to) {
                    flush(result, literal);
                    List<MarkdownInline> children = depth < MAX_DEPTH
                            ? parseRange(text, index + 2, end, depth + 1)
                            : List.of(new MarkdownInline.Text(text.substring(index + 2, end)));
                    result.add(new MarkdownInline.Strike(children));
                    index = end + 2;
                    continue;
                }
            }

            if (current == '[') {
                int[] end = {-1};
                MarkdownInline.Link link = readLink(text, index, to, end);
                if (link != null) {
                    flush(result, literal);
                    result.add(link);
                    index = end[0];
                    continue;
                }
            }

            literal.append(current);
            index++;
        }
        flush(result, literal);
        return result;
    }

    private static void flush(List<MarkdownInline> result, StringBuilder literal) {
        if (literal.length() == 0) {
            return;
        }
        String text = literal.toString();
        literal.setLength(0);
        if (!result.isEmpty() && result.getLast() instanceof MarkdownInline.Text previous) {
            // 相邻字面量合并，AST 更干净（断言时也不用关心分段）
            result.set(result.size() - 1, new MarkdownInline.Text(previous.text() + text));
            return;
        }
        result.add(new MarkdownInline.Text(text));
    }

    private static int countRun(String text, int start, int to, char marker) {
        int count = 0;
        while (start + count < to && text.charAt(start + count) == marker) {
            count++;
        }
        return count;
    }

    /** 开定界符：后一个字符非空白；{@code _} 还要求前一个字符非字母数字。 */
    private static boolean openerFlanks(String text, int start, int run, int to, char marker) {
        int after = start + run;
        if (after >= to || Character.isWhitespace(text.charAt(after))) {
            return false;
        }
        if (marker != '_') {
            return true;
        }
        return start == 0 || !Character.isLetterOrDigit(text.charAt(start - 1));
    }

    /**
     * 找闭定界符：{@code run} 个连续标记，前一个字符非空白；{@code _} 还要求后一个字符非字母数字。
     *
     * @return 闭定界符起始位置；找不到返回 {@code -1}
     */
    private static int findCloser(String text, int from, int to, char marker, int run) {
        int index = from;
        while (index < to) {
            if (text.charAt(index) != marker) {
                index++;
                continue;
            }
            int length = countRun(text, index, to, marker);
            if (length >= run
                    && index > 0
                    && !Character.isWhitespace(text.charAt(index - 1))
                    && (marker != '_'
                    || index + length >= to
                    || !Character.isLetterOrDigit(text.charAt(index + length)))) {
                return index;
            }
            index += length;
        }
        return -1;
    }

    private static MarkdownInline wrap(int run, List<MarkdownInline> children) {
        return switch (run) {
            case 1 -> new MarkdownInline.Em(children);
            case 2 -> new MarkdownInline.Strong(children);
            default -> new MarkdownInline.Strong(List.of(new MarkdownInline.Em(children)));
        };
    }

    /**
     * 读取 `[文本](url)`。
     *
     * @return 链接；不是完整链接返回 {@code null}
     */
    private static MarkdownInline.Link readLink(String text, int start, int to, int[] endOut) {
        int closeBracket = text.indexOf(']', start + 1);
        if (closeBracket < 0 || closeBracket + 1 >= to || text.charAt(closeBracket + 1) != '(') {
            return null;
        }
        int closeParen = text.indexOf(')', closeBracket + 2);
        if (closeParen < 0 || closeParen >= to) {
            return null;
        }
        String label = text.substring(start + 1, closeBracket);
        String url = text.substring(closeBracket + 2, closeParen).strip();
        if (label.isEmpty() || url.isEmpty()) {
            return null;
        }
        endOut[0] = closeParen + 1;
        return new MarkdownInline.Link(label, url);
    }
}
