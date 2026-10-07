package com.acanx.module.aha.cli.render;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * 流式 Markdown 轻量渲染。
 *
 * <p>处理项：<b>标题</b>（分级配色）、<b>列表</b>、<b>引用</b>、<b>分隔线</b>、
 * <b>表格</b>、<b>围栏代码</b>、<b>行内代码</b>、<b>链接</b>、<b>粗体 / 斜体</b>。
 * 行内构造的歧义判定见 {@link InlineScanner}，表格排版见 {@link TableRenderer}。</p>
 *
 * <p><b>为什么不牺牲流式输出</b>：块级构造只取决于<b>行首最多 8 个字符</b>
 * （`#`、`- `、`1. `、`> `、三个反引号的围栏标记、表格的 `|`），缓冲这么长即可判定。
 * 行内的跨片构造（代码、强调、链接）才需要缓冲，上限 200 字符——超出即按纯文本吐出，
 * 否则一个未闭合的定界符会把后续内容一直扣住。</p>
 *
 * <p><b>表格是唯一会延迟的构造</b>：不先扫一遍就不知道每列多宽，逐行输出必然参差不齐。
 * 因此表格在遇到第一个非表格行（或流结束时）整体输出——表格通常很短，这点延迟无感。</p>
 *
 * <p>围栏代码内部<b>原样输出</b>：代码里的 `#`、反引号、`-`、`*` 都不应被当成 Markdown，
 * 否则读代码的人会被错误高亮误导。</p>
 *
 * @since 0.1.0
 */
final class MarkdownStream {

    /** 行首判定块级构造的最大缓冲长度。 */
    private static final int MAX_PREFIX = 8;

    /** 行内构造的最大缓冲长度，超出即按纯文本吐出。 */
    private static final int MAX_PENDING = 200;

    private final Style style;
    private final String bullet;
    private final boolean box;
    private final int width;

    private final StringBuilder out = new StringBuilder();
    private final StringBuilder prefix = new StringBuilder();
    private final StringBuilder pending = new StringBuilder();

    private LineKind kind = LineKind.PLAIN;
    private int headingLevel;
    private boolean prefixDone;
    private boolean inFence;
    private boolean pendingHasDelimiter;

    /** 表格缓冲。 */
    private final List<String> tableRows = new ArrayList<>();
    private final StringBuilder tableLine = new StringBuilder();
    private boolean inTable;

    /** 已写出文本的最后一个码点，用于 `_` 的词内判定。 */
    private int lastEmitted;

    /**
     * 创建渲染器。
     *
     * @param style   样式
     * @param unicode 编码是否支持制表符与扩展符号
     * @param width   终端宽度；{@code <= 0} 表示未知
     */
    MarkdownStream(Style style, boolean unicode, int width) {
        this.style = style;
        this.bullet = unicode ? "•" : "-";
        this.box = unicode;
        this.width = width;
    }

    /** 行级样式。 */
    private enum LineKind {
        /** 无行级样式。 */
        PLAIN,
        /** 标题：按级别配色。 */
        HEADING,
        /** 引用 / 分隔线：整行弱化。 */
        QUOTE
    }

    /**
     * 处理一段增量，返回可直接写出的文本。
     *
     * @param delta 增量
     * @return 渲染后的文本；无输出时返回空串
     */
    String feed(String delta) {
        if (delta == null || delta.isEmpty()) {
            return "";
        }
        out.setLength(0);
        int index = 0;
        while (index < delta.length()) {
            int codePoint = delta.codePointAt(index);
            step(codePoint);
            index += Character.charCount(codePoint);
        }
        // 每段增量结束即解析一次：既不按长度攒批（会有可感知延迟），
        // 也不逐字符出头（会在标题 / 引用行上产生大量重复 SGR）。
        resolve(false);
        return out.toString();
    }

    /**
     * 一轮结束：吐出未决内容（不加换行，由调用方决定）。
     *
     * @return 渲染后的文本
     */
    String flush() {
        out.setLength(0);
        if (inTable) {
            flushTable();
        }
        if (!prefixDone) {
            out.append(prefix);
            prefix.setLength(0);
            prefixDone = true;
        }
        if (!inFence) {
            resolve(true);
        }
        flushPending();
        return out.toString();
    }

    private void step(int codePoint) {
        if (inTable) {
            stepTable(codePoint);
            return;
        }
        if (codePoint == '\n') {
            endLine();
            return;
        }
        if (!prefixDone) {
            prefix.appendCodePoint(codePoint);
            decidePrefix();
            return;
        }
        if (inFence) {
            // 围栏内部不做行内解析，但仍按当前行样式批量输出
            pending.appendCodePoint(codePoint);
            return;
        }
        pending.appendCodePoint(codePoint);
        if (InlineScanner.isDelimiter(codePoint)) {
            pendingHasDelimiter = true;
        }
        // 只有缓冲区里确实还挂着定界符时才需要即时重试解析
        if (pendingHasDelimiter) {
            resolve(false);
        }
    }

    /**
     * 表格模式下的逐字符处理。
     *
     * <p>表格何时结束只有看到下一行开头才知道，所以这里在「行首第一个字符不是 {@code |}」
     * 时才结算——届时整张表已经收齐，可以算列宽了。</p>
     *
     * @param codePoint 码点
     */
    private void stepTable(int codePoint) {
        if (codePoint == '\n') {
            tableRows.add(tableLine.toString());
            tableLine.setLength(0);
            return;
        }
        if (codePoint == '\r') {
            return;
        }
        if (tableLine.length() == 0 && codePoint != '|') {
            flushTable();
            // 本行不属于表格，交回常规流程（此时 prefix/prefixDone 已复位）
            step(codePoint);
            return;
        }
        tableLine.appendCodePoint(codePoint);
    }

    /**
     * 结算表格并输出。
     */
    private void flushTable() {
        if (tableLine.length() > 0) {
            tableRows.add(tableLine.toString());
            tableLine.setLength(0);
        }
        inTable = false;
        if (tableRows.isEmpty()) {
            return;
        }
        out.append(TableRenderer.render(tableRows, style, width, box));
        tableRows.clear();
    }

    private void endLine() {
        if (!prefixDone) {
            // 未判定的行首按纯文本吐出（例如只有一个 "#"）
            out.append(prefix);
            prefix.setLength(0);
            prefixDone = true;
        }
        // 行尾允许闭定界符落在缓冲区末尾（如 _italic_ 后紧跟换行）；
        // 围栏内部不做行内解析，否则代码里的 ** 会被当成强调
        if (!inFence) {
            resolve(true);
        }
        flushPending();
        out.append('\n');
        lastEmitted = '\n';
        kind = LineKind.PLAIN;
        headingLevel = 0;
        prefixDone = false;
        pendingHasDelimiter = false;
    }

    /**
     * 尝试把 {@code pending} 中可确定的部分写出去，尾部留待更多输入。
     *
     * @param lineEnd 是否处于行尾
     */
    private void resolve(boolean lineEnd) {
        String text = pending.toString();
        InlineScanner.Context context =
                new InlineScanner.Context(style, MAX_PENDING, lineEnd, 0);
        int index = 0;
        while (index < text.length()) {
            int next = InlineScanner.nextDelimiter(text, index);
            if (next < 0) {
                emitText(text.substring(index));
                index = text.length();
                break;
            }
            if (next > index) {
                emitText(text.substring(index, next));
            }
            int previous = next > 0 ? text.codePointBefore(next) : lastEmitted;
            InlineScanner.Scan scan = InlineScanner.parse(text, next, previous, context);
            if (scan == null) {
                if (text.length() - next > MAX_PENDING) {
                    // 超界：当字面量处理，避免一直扣住后续内容
                    emitText(text.substring(next, next + 1));
                    index = next + 1;
                    continue;
                }
                index = next;
                break;
            }
            emitScan(scan);
            index = scan.end();
        }
        pending.setLength(0);
        if (index < text.length()) {
            pending.append(text, index, text.length());
        }
        pendingHasDelimiter = InlineScanner.nextDelimiter(pending, 0) >= 0;
    }

    private void decidePrefix() {
        String p = prefix.toString();
        if (p.length() > MAX_PREFIX) {
            flushPrefix();
            return;
        }
        if (inFence) {
            decideWhileInFence(p);
            return;
        }
        char first = p.charAt(0);
        switch (first) {
            case '#' -> decideHeading(p);
            case '-', '*', '+' -> decideListOrRule(p, first);
            case '>' -> decideQuote(p);
            case '`' -> decideFence(p);
            case '|' -> decideTable(p);
            default -> {
                if (Character.isDigit(first)) {
                    decideOrderedList(p);
                } else {
                    flushPrefix();
                }
            }
        }
    }

    /**
     * 表格行首：只见到一个 {@code |} 就进入表格模式，后续字符全部归表格缓冲。
     *
     * @param p 已缓冲的行首
     */
    private void decideTable(String p) {
        if (p.length() != 1) {
            flushPrefix();
            return;
        }
        inTable = true;
        tableRows.clear();
        tableLine.setLength(0);
        tableLine.append('|');
        prefix.setLength(0);
        prefixDone = true;
    }

    /**
     * 围栏内部：只有整行以三个及以上反引号开头才算闭合围栏。
     */
    private void decideWhileInFence(String p) {
        if (!allSame(p, '`')) {
            emitPrefixLiteral();
            return;
        }
        if (p.length() >= 3) {
            kind = LineKind.QUOTE;
            flushPrefixMuted();
            inFence = false;
        }
    }

    private void decideHeading(String p) {
        int hashes = leadingCount(p, ch -> ch == '#');
        if (hashes > 6) {
            flushPrefix();
            return;
        }
        if (p.length() <= hashes) {
            return;
        }
        if (p.charAt(hashes) != ' ') {
            flushPrefix();
            return;
        }
        kind = LineKind.HEADING;
        headingLevel = hashes;
        // 有色时不打 `#` 标记：颜色与字重已经把级别说清楚了，再留一个弱化的 `#`
        // 只是噪音。无色时标记是**唯一**的层级信号，必须保留。
        if (!style.colored()) {
            out.append("#".repeat(hashes)).append(' ');
            lastEmitted = ' ';
        }
        prefix.setLength(0);
        prefixDone = true;
    }

    private void decideListOrRule(String p, char mark) {
        if (p.length() == 2 && p.charAt(1) == ' ') {
            out.append(style.marker(bullet)).append(' ');
            lastEmitted = ' ';
            prefix.setLength(0);
            prefixDone = true;
            return;
        }
        if (allSame(p, mark)) {
            // 可能是 --- / *** / ___ 分隔线；不足 3 个时继续缓冲
            if (p.length() >= 3) {
                kind = LineKind.QUOTE;
                flushPrefixMuted();
            }
            return;
        }
        flushPrefix();
    }

    private void decideQuote(String p) {
        if (p.length() == 1) {
            return;
        }
        if (p.charAt(1) != ' ') {
            flushPrefix();
            return;
        }
        kind = LineKind.QUOTE;
        flushPrefixMuted();
    }

    private void decideFence(String p) {
        if (!allSame(p, '`')) {
            flushPrefix();
            return;
        }
        if (p.length() >= 3) {
            // 围栏行本身弱化；其内部内容原样输出
            kind = LineKind.QUOTE;
            flushPrefixMuted();
            inFence = true;
        }
    }

    private void decideOrderedList(String p) {
        int digits = leadingCount(p, Character::isDigit);
        if (p.length() <= digits || p.charAt(digits) != '.') {
            if (p.length() > digits) {
                flushPrefix();
            }
            return;
        }
        if (p.length() == digits + 1) {
            // 需要再看一个字符确认是 "N. "
            return;
        }
        if (p.charAt(digits + 1) != ' ') {
            flushPrefix();
            return;
        }
        out.append(style.marker(p.substring(0, digits + 1))).append(' ');
        lastEmitted = ' ';
        prefix.setLength(0);
        prefixDone = true;
    }

    /**
     * 判定结果：本行不是块级构造，把已缓冲的行首交回行内处理。
     *
     * <p>关键：不能直接当纯文本输出——`**粗**` 的首两个字符与列表 / 分隔线前缀
     * 完全相同，必须交给 {@link InlineScanner} 才能正确识别为强调。</p>
     */
    private void flushPrefix() {
        pending.append(prefix);
        prefix.setLength(0);
        prefixDone = true;
        pendingHasDelimiter = InlineScanner.nextDelimiter(pending, 0) >= 0;
        resolve(false);
    }

    /** 围栏内部的行首：原样输出，不做行内解析。 */
    private void emitPrefixLiteral() {
        out.append(prefix);
        remember(prefix);
        prefix.setLength(0);
        prefixDone = true;
    }

    private void flushPrefixMuted() {
        out.append(style.muted(prefix.toString()));
        remember(prefix);
        prefix.setLength(0);
        prefixDone = true;
    }

    private void flushPending() {
        if (pending.length() > 0) {
            emitText(pending.toString());
            pending.setLength(0);
        }
        pendingHasDelimiter = false;
    }

    private void emitText(String text) {
        if (text.isEmpty()) {
            return;
        }
        out.append(switch (kind) {
            case HEADING -> style.heading(headingLevel, text);
            case QUOTE -> style.muted(text);
            case PLAIN -> text;
        });
        lastEmitted = text.codePointBefore(text.length());
    }

    private void emitScan(InlineScanner.Scan scan) {
        out.append(scan.styled());
        remember(scan.raw());
    }

    private void remember(CharSequence text) {
        if (text.length() > 0) {
            lastEmitted = Character.codePointBefore(text, text.length());
        }
    }

    private static int leadingCount(String text, IntPredicate test) {
        int index = 0;
        while (index < text.length() && test.test(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean allSame(String text, char expected) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != expected) {
                return false;
            }
        }
        return true;
    }
}
