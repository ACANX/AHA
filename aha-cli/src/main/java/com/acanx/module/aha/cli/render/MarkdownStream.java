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

    /** 围栏代码是否显示行号。 */
    private final boolean lineNumbers;

    private final StringBuilder out = new StringBuilder();
    private final StringBuilder prefix = new StringBuilder();
    private final StringBuilder pending = new StringBuilder();

    private LineKind kind = LineKind.PLAIN;
    private int headingLevel;
    private boolean prefixDone;
    private boolean inFence;

    /** 围栏内当前行号（从 1 开始）。 */
    private int fenceLine = 1;
    /** 当前围栏的语言标识（用于语法高亮）；非围栏时为 {@code null}。 */
    private String fenceLanguage;
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
        this(style, unicode, width, style != null && style.colored() && unicode);
    }

    /**
     * 创建渲染器，并显式指定围栏代码是否显示行号。
     *
     * @param style       样式
     * @param unicode     编码是否支持制表符与扩展符号
     * @param width       终端宽度；{@code <= 0} 表示未知
     * @param lineNumbers 围栏代码是否显示行号
     */
    MarkdownStream(Style style, boolean unicode, int width, boolean lineNumbers) {
        this.style = style;
        this.bullet = unicode ? "•" : "-";
        this.box = unicode;
        this.width = width;
        this.lineNumbers = lineNumbers;
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
        if (inFence) {
            // 最后一行若已由 endLine 处理（pending 为空），不能再补一个空行号
            if (pending.length() > 0) {
                appendFenceLine();
            }
        } else {
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
            if (tableLine.length() == 0) {
                // 空行是 GFM 的表格终止符（issue #346）：结算当前表格，再把这一空行按普通行
                // 输出出去，让表格块与后文之间保留一个空行（与表格前的空行对称）。
                // 若不在这里终止，空行会被收进 tableRows（渲染时又被跳过），
                // 于是「表格后没有空行」，且两张表被并成一张。
                flushTable();
                endLine();
                return;
            }
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
     *
     * <p><b>结束时必须复位行级状态</b>：表格内的换行由 {@link #stepTable(int)} 自己消费，
     * 从不经过 {@link #endLine()}，而进入表格时 {@code prefixDone} 被置为 {@code true}。
     * 若不复位，紧跟表格的第一行会带着「行首已判定」的旧状态进入行内流程——
     * 标题 / 列表 / 引用 / 围栏的标记全被当成正文原样吐出（只坏这一行，下一行就恢复正常）。</p>
     */
    private void flushTable() {
        if (tableLine.length() > 0) {
            tableRows.add(tableLine.toString());
            tableLine.setLength(0);
        }
        inTable = false;
        if (!tableRows.isEmpty()) {
            out.append(TableRenderer.render(tableRows, style, width, box));
            tableRows.clear();
            // 表格渲染结果以换行结尾：下一个字符属于新行，与 endLine() 的收尾保持一致
            lastEmitted = '\n';
        }
        resetLineState();
    }

    /** 复位行级状态：行结束或整块构造（表格）结算后调用。 */
    private void resetLineState() {
        kind = LineKind.PLAIN;
        headingLevel = 0;
        prefix.setLength(0);
        prefixDone = false;
        pendingHasDelimiter = false;
    }

    /**
     * 输出一行围栏代码：可选行号 + 语法高亮。
     */
    private void appendFenceLine() {
        String code = pending.toString();
        String prefix = lineNumberPrefix();
        int prefixWidth = TextWidth.width(prefix);
        int limit = width > 0 ? Math.max(8, width - 1 - prefixWidth) : 0;
        // 代码行折行：续行用行号前缀等宽的空格占位，保持代码列对齐
        java.util.List<String> parts = limit > 0
                ? TextWidth.wrap(code, limit)
                : java.util.List.of(code);
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(i == 0 ? style.lineNumber(prefix) : " ".repeat(prefixWidth));
            out.append(CodeHighlighter.highlight(parts.get(i), fenceLanguage, style));
        }
        pending.setLength(0);
    }

    /**
     * 行号前缀：3 位右对齐 + 分隔符；未开启行号时返回空串。
     *
     * @return 行号前缀
     */
    private String lineNumberPrefix() {
        if (!lineNumbers) {
            return "";
        }
        String number = String.format("%3d", fenceLine++);
        return number + (box ? " │ " : " | ");
    }

    /**
     * 从围栏语言标识中取第一个 token（去掉 `title="…"` 等附加信息）。
     *
     * @param info 反引号之后的整段文本
     * @return 语言标识；没有时返回 {@code null}
     */
    private static String normalizeLanguage(String info) {
        if (info == null) {
            return null;
        }
        String text = info.trim();
        if (text.isEmpty()) {
            return null;
        }
        int end = 0;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return end == 0 ? null : text.substring(0, end);
    }

    private void endLine() {
        if (!prefixDone) {
            String p = prefix.toString();
            int ticks = leadingCount(p, ch -> ch == '`');
            if (!inFence && ticks >= 3) {
                // 打开围栏：语言标识取反引号之后的部分（可能带 title 等附加信息）
                fenceLanguage = normalizeLanguage(p.substring(ticks));
                fenceLine = 1;
                out.append(style.muted(p));
                prefix.setLength(0);
                resetLineState();
                inFence = true;
                out.append('\n');
                lastEmitted = '\n';
                return;
            }
            // 未判定的行首按纯文本吐出（例如只有一个 "#"）
            out.append(prefix);
            prefix.setLength(0);
            prefixDone = true;
        }
        // 行尾允许闭定界符落在缓冲区末尾（如 _italic_ 后紧跟换行）；
        // 围栏内部不做行内解析，否则代码里的 ** 会被当成强调
        if (inFence) {
            appendFenceLine();
        } else {
            resolve(true);
        }
        flushPending();
        out.append('\n');
        lastEmitted = '\n';
        resetLineState();
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
        // 围栏内部：行首缓冲直接并入 pending，整行在 endLine 时统一加行号 / 高亮。
        // 不能在这里按 MAX_PREFIX 提前 flush，否则行号会插到代码中间。
        if (inFence) {
            decideWhileInFence(p);
            return;
        }
        int ticks = leadingCount(p, ch -> ch == '`');
        // 围栏行需要完整缓冲到行尾才能拿到语言标识；普通行仍按 MAX_PREFIX 早判
        if (p.length() > MAX_PREFIX && ticks < 3) {
            flushPrefix();
            return;
        }
        if (p.length() > 64) {
            flushPrefix();
            return;
        }
        if (inFence) {
            decideWhileInFence(p);
            return;
        }
        // 嵌套列表：行首缩进表示层级。缩进原样保留（层级信号），符号统一成配置的 bullet，
        // 否则顶层是 `• `、嵌套还是 `- `，看起来像两套记号。
        int indent = 0;
        while (indent < p.length() && p.charAt(indent) == ' ') {
            indent++;
        }
        if (indent > 0) {
            if (indent >= p.length()) {
                // 目前全是空格：还不知道是「缩进列表」还是「缩进正文」，等下一个字符
                return;
            }
            decideIndented(p, indent);
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
            // 内容行：把行首缓冲并入 pending，整行在 endLine 时统一加行号 / 高亮
            pending.append(prefix);
            prefix.setLength(0);
            prefixDone = true;
            return;
        }
        if (p.length() >= 3) {
            kind = LineKind.QUOTE;
            flushPrefixMuted();
            inFence = false;
            fenceLanguage = null;
            fenceLine = 1;
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
        int ticks = leadingCount(p, ch -> ch == '`');
        if (ticks >= 3) {
            // 继续缓冲整行：endLine 时解析语言标识并进入围栏
            return;
        }
        // 还不足 3 个反引号：继续缓冲（可能还在围栏标记内），
        // 长度达到 3 才说明这只是一段普通行内代码/文本
        if (p.length() >= 3) {
            flushPrefix();
        }
    }

    /**
     * 缩进（嵌套）列表行首判定。
     *
     * @param p      行首缓冲
     * @param indent 前导空格数
     */
    private void decideIndented(String p, int indent) {
        char mark = p.charAt(indent);
        if (mark == '-' || mark == '*' || mark == '+') {
            if (p.length() <= indent + 1) {
                return;
            }
            if (p.length() == indent + 2 && p.charAt(indent + 1) == ' ') {
                out.append(" ".repeat(indent)).append(style.marker(bullet)).append(' ');
                lastEmitted = ' ';
                prefix.setLength(0);
                prefixDone = true;
                return;
            }
            // 可能是缩进的分隔线（如 "***"），交给常规判定
            flushPrefix();
            return;
        }
        if (Character.isDigit(mark)) {
            int digits = indent + leadingCount(p.substring(indent), Character::isDigit);
            if (p.length() <= digits || p.charAt(digits) != '.') {
                if (p.length() > digits) {
                    flushPrefix();
                }
                return;
            }
            if (p.length() == digits + 1) {
                return;
            }
            if (p.charAt(digits + 1) != ' ') {
                flushPrefix();
                return;
            }
            out.append(" ".repeat(indent))
                    .append(style.marker(p.substring(indent, digits + 1))).append(' ');
            lastEmitted = ' ';
            prefix.setLength(0);
            prefixDone = true;
            return;
        }
        flushPrefix();
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
