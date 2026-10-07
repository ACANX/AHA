package com.acanx.module.aha.cli.render;

/**
 * 行内 Markdown 扫描器：行内代码与强调（粗体 / 斜体）。
 *
 * <p><b>难点是歧义</b>：{@code *} 既可能是强调定界符，也可能是字面量
 * （{@code 2 * 3 * 4}）；{@code _} 还可能是标识符的一部分（{@code a_b_c}）。
 * 因此采用 CommonMark 的<b>侧翼</b>思路做一个可解释的子集：</p>
 *
 * <table border="1">
 *   <caption>定界符规则</caption>
 *   <tr><th>定界符</th><th>开定界符要求</th><th>闭定界符要求</th></tr>
 *   <tr><td>{@code *}</td><td>后一个字符非空白</td><td>前一个字符非空白</td></tr>
 *   <tr><td>{@code _}</td><td>后一个字符非空白，且<b>前一个字符非字母数字</b></td>
 *       <td>前一个字符非空白，且<b>后一个字符非字母数字</b></td></tr>
 * </table>
 *
 * <p>这样 {@code 2 * 3} 不会误判为斜体，{@code a_b_c} 也不会。</p>
 *
 * <p>{@code *} / {@code _} 连用 1 / 2 / 3 个分别表示斜体 / 粗体 / 粗斜体；</p>
 * <p>4 个及以上按字面量输出。</p>
 *
 * <p>解析可能需要<b>更多输入</b>才能判定（例如只收到一个前导 {@code *}），此时返回
 * {@code null}，由调用方决定继续缓冲。因此本扫描器同时服务于流式与非流式两种场景：
 * 前者遇到 {@code null} 就等，后者把 {@code null} 当字面量。</p>
 *
 * @since 0.1.0
 */
final class InlineScanner {

    /** 行内代码定界符。 */
    private static final char CODE = '`';

    /** 星号强调定界符。 */
    private static final char ASTERISK = '*';

    /** 下划线强调定界符。 */
    private static final char UNDERSCORE = '_';

    /** 链接起始符。 */
    private static final char LBRACKET = '[';

    /** 允许的最大定界符串长度；超过即按字面量处理。 */
    private static final int MAX_RUN = 3;

    /** 递归深度上限：防止构造输入把栈打穿。 */
    private static final int MAX_DEPTH = 4;

    private InlineScanner() {
    }

    /**
     * 解析上下文。
     *
     * @param style   样式
     * @param limit   单个构造允许缓冲的最大字符数
     * @param lineEnd 当前是否处于行尾；行尾时允许闭定界符落在缓冲区末尾
     * @param depth   递归深度
     */
    record Context(Style style, int limit, boolean lineEnd, int depth) {

        Context deeper() {
            return new Context(style, limit, lineEnd, depth + 1);
        }
    }

    /**
     * 一次解析结果。
     *
     * @param styled 可直接写出的文本（可能含 SGR 序列）
     * @param raw    消耗掉的原始文本，用于追踪上下文
     * @param end    解析结束位置
     */
    record Scan(String styled, String raw, int end) {
    }

    /**
     * 查找下一个行内定界符。
     *
     * @param text 文本
     * @param from 起始位置
     * @return 位置；未找到返回 {@code -1}
     */
    static int nextDelimiter(CharSequence text, int from) {
        for (int i = from; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == CODE || c == ASTERISK || c == UNDERSCORE || c == LBRACKET) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 是否为行内定界符。
     *
     * @param codePoint 码点
     * @return 是否为定界符
     */
    static boolean isDelimiter(int codePoint) {
        return codePoint == CODE || codePoint == ASTERISK || codePoint == UNDERSCORE
                || codePoint == LBRACKET;
    }

    /**
     * 解析 {@code pos} 处的行内构造。
     *
     * @param text     文本
     * @param pos      定界符位置
     * @param previous 定界符前一个码点（{@code 0} 表示无前置字符）
     * @param context  解析上下文
     * @return 解析结果；尚需更多输入时返回 {@code null}
     */
    static Scan parse(String text, int pos, int previous, Context context) {
        if (text.charAt(pos) == CODE) {
            return parseCode(text, pos, context);
        }
        if (text.charAt(pos) == LBRACKET) {
            return parseLink(text, pos, context);
        }
        return parseEmphasis(text, pos, previous, context);
    }

    /**
     * 渲染一段<b>完整</b>文本中的行内构造（递归处理嵌套）。
     *
     * @param text  完整文本
     * @param style 样式
     * @return 渲染后的文本
     */
    static String render(String text, Style style) {
        return renderInline(text, new Context(style, Integer.MAX_VALUE, true, 0));
    }

    private static String renderInline(String text, Context context) {
        if (context.depth() > MAX_DEPTH) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int next = nextDelimiter(text, index);
            if (next < 0) {
                result.append(text, index, text.length());
                break;
            }
            if (next > index) {
                result.append(text, index, next);
            }
            int previous = next > 0 ? text.codePointBefore(next) : 0;
            Scan scan = parse(text, next, previous, context.deeper());
            if (scan == null) {
                // 文本已完整，不存在「等待更多输入」：按字面量处理
                result.append(text.charAt(next));
                index = next + 1;
            } else {
                result.append(scan.styled());
                index = scan.end();
            }
        }
        return result.toString();
    }

    /**
     * 解析链接 {@code [文本](地址)}。
     *
     * <p>不生成 OSC 8 超链接：它对终端有要求，重定向到文件后还会变成一堆乱码。
     * 这里保留「文本 + 地址」，地址弱化——既能在支持的终端里被识别，也不影响其它场景。</p>
     *
     * <p>文本与地址相同时只显示一次，避免 `[http://x](http://x)` 这类冗余重复。</p>
     */
    private static Scan parseLink(String text, int pos, Context context) {
        int close = text.indexOf(']', pos + 1);
        if (close < 0) {
            return text.length() - pos > context.limit() ? literal("[", pos) : null;
        }
        if (close + 1 >= text.length()) {
            // 还没看到 「(」，需要更多输入才能判定
            return null;
        }
        if (text.charAt(close + 1) != '(') {
            // 不是链接语法（如数组下标 a[0]），按字面量处理
            return literal("[", pos);
        }
        int end = text.indexOf(')', close + 2);
        if (end < 0) {
            return text.length() - pos > context.limit() ? literal("[", pos) : null;
        }
        String label = text.substring(pos + 1, close);
        String url = text.substring(close + 2, end);
        String styled = context.style().link(renderInline(label, context.deeper()));
        if (!label.equals(url)) {
            styled = styled + " " + context.style().linkUrl("<" + url + ">");
        }
        return new Scan(styled, text.substring(pos, end + 1), end + 1);
    }

    private static Scan parseCode(String text, int pos, Context context) {
        int close = text.indexOf(CODE, pos + 1);
        if (close >= 0) {
            String inner = text.substring(pos + 1, close);
            return new Scan(context.style().code(inner), text.substring(pos, close + 1), close + 1);
        }
        if (text.length() - pos > context.limit()) {
            // 未闭合的反引号按字面量处理，否则会把后续内容一直扣住
            return literal("`", pos);
        }
        return null;
    }

    private static Scan parseEmphasis(String text, int pos, int previous, Context context) {
        char mark = text.charAt(pos);
        int run = runLength(text, pos, mark);
        int afterOpen = pos + run;

        // 定界符串可能还没收完（例如 ** 分两片到达）
        if (afterOpen >= text.length()) {
            return null;
        }
        if (run > MAX_RUN || Character.isWhitespace(text.charAt(afterOpen))) {
            return literal(text.substring(pos, afterOpen), pos);
        }
        if (mark == UNDERSCORE && Character.isLetterOrDigit(previous)) {
            // _ 不允许在词内开强调，否则 a_b_c 会被误判
            return literal(text.substring(pos, afterOpen), pos);
        }

        int search = afterOpen;
        while (true) {
            int close = text.indexOf(mark, search);
            if (close < 0) {
                break;
            }
            int closeRun = runLength(text, close, mark);
            if (close > afterOpen && closeRun >= run
                    && !Character.isWhitespace(text.charAt(close - 1))
                    && closerAllowed(mark, text, close + closeRun, context)) {
                String inner = text.substring(afterOpen, close);
                return new Scan(
                        context.style().emphasis(run, renderInline(inner, context.deeper())),
                        text.substring(pos, close + run),
                        close + run);
            }
            search = close + closeRun;
        }

        if (text.length() - pos > context.limit()) {
            return literal(text.substring(pos, afterOpen), pos);
        }
        return null;
    }

    /**
     * 闭定界符是否允许。
     *
     * <p>{@code _} 的闭定界符右边不能紧跟字母数字（否则仍属词内）。该字符若尚未来到，
     * 只有处于行尾时才能确定它是空白 / 行尾。</p>
     */
    private static boolean closerAllowed(char mark, String text, int afterClose, Context context) {
        if (mark != UNDERSCORE) {
            return true;
        }
        if (afterClose >= text.length()) {
            return context.lineEnd();
        }
        return !Character.isLetterOrDigit(text.charAt(afterClose));
    }

    private static Scan literal(String raw, int pos) {
        return new Scan(raw, raw, pos + raw.length());
    }

    private static int runLength(String text, int pos, char mark) {
        int count = 0;
        while (pos + count < text.length() && text.charAt(pos + count) == mark && count <= MAX_RUN) {
            count++;
        }
        return count;
    }
}
