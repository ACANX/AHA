package com.acanx.module.aha.cli.tty;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 输入行里的「粘贴占位符」：大块内容折叠成 {@code [paste #1 +42 lines]}，提交前再展开。
 *
 * <p>为什么需要它：多行内容直接铺在输入行里会把一行撑成几十行，光标、补全、着色全都跟着变乱；
 * 折叠成占位符后，输入行只占一行，内容仍在（提交时原样展开），用户可以在占位符前后继续写问题。</p>
 *
 * <p>折叠门槛与 pi / Claude Code 同一口径：**行数 &gt; 10** 或 **字符数 &gt; 1000** 才折叠，
 * 短内容原样插入（否则「粘贴一小段命令」也会莫名其妙变成占位符）。</p>
 *
 * <p>两个必须守住的细节（都来自别家踩过的坑）：</p>
 * <ul>
 *   <li><b>展开是单遍替换</b>：替换进去的内容不再被扫描，粘贴内容里恰好写着同形文本也不会被二次展开；</li>
 *   <li><b>认不出来就原样留着</b>：占位符里的行数/字符数被用户改过仍能认（只认 {@code #编号}）；
 *       而用户自己敲出来的同形文本（没有对应内容）原样保留，绝不留下悬空的空串。</li>
 * </ul>
 *
 * @see ClipboardPaste
 * @since 0.2.0
 */
public final class PastePlaceholder {

    /** 超过这么多行才折叠。 */
    public static final int MIN_LINES = 10;

    /** 单行但超过这么多字符也折叠（例如粘一整段 JSON）。 */
    public static final int MIN_CHARS = 1000;

    /**
     * 占位符形状：{@code [paste #1]} / {@code [paste #1 +42 lines]} / {@code [paste #1 1234 chars]}。
     *
     * <p>中间的计数部分**可选且宽松**（用户可能在编辑时改掉它，甚至只删剩 {@code [paste #1 ]}），
     * 编号必须精确匹配已有内容。空白只认空格、不认换行 —— 否则 {@code \s} 会把换行也吃进来，
     * 用户手动分行的输入会被悄悄并成一行。</p>
     */
    private static final Pattern MARKER =
            Pattern.compile("\\[paste #(\\d+)(?: \\+?\\d+ +(?:lines|chars))? *\\]");

    private final Map<Integer, String> entries = new LinkedHashMap<>();

    private int counter;

    /**
     * 折叠：小内容原样返回，大内容存起来并返回占位符。
     *
     * @param text 插入输入行的内容
     * @return 实际写入输入行的文本（可能是占位符）
     */
    public String compact(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int lines = lineCount(text);
        String marker;
        if (lines > MIN_LINES) {
            marker = "[paste #" + (counter + 1) + " +" + lines + " lines]";
        } else if (text.length() > MIN_CHARS) {
            marker = "[paste #" + (counter + 1) + " " + text.length() + " chars]";
        } else {
            return text;
        }
        counter++;
        entries.put(counter, text);
        return marker;
    }

    /**
     * 展开：把输入行里的占位符换回内容，并丢弃没被引用的内容。
     *
     * <p>提交前调用一次即可（同一份内容不会被提交两次）。</p>
     *
     * @param text 输入行上的文本
     * @return 展开后的文本；没有占位符时原样返回
     */
    public String expand(String text) {
        if (text == null || text.isEmpty() || entries.isEmpty() || !text.contains("[paste #")) {
            return text;
        }
        Matcher matcher = MARKER.matcher(text);
        StringBuilder expanded = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            String content = entries.get(Integer.valueOf(matcher.group(1)));
            if (content == null) {
                continue;   // 用户自己敲的同形文本：留着，不动
            }
            expanded.append(text, cursor, matcher.start()).append(content);
            cursor = matcher.end();
        }
        expanded.append(text, cursor, text.length());
        entries.clear();   // 已交给上层：不再握着这些大字符串
        return expanded.toString();
    }

    /**
     * 是否还有未提交的折叠内容。
     *
     * @return 占位符个数
     */
    public int pending() {
        return entries.size();
    }

    /**
     * 该行是否**整行都由占位符开头**（用于判断「这一行是粘贴出来的内容，不是命令」）。
     *
     * @param text 输入行上的文本
     * @return 以占位符开头返回 {@code true}
     */
    public boolean startsWithMarker(String text) {
        return text != null && MARKER.matcher(text).lookingAt();
    }

    private static int lineCount(String text) {
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }
}
