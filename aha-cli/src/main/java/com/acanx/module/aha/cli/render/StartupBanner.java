package com.acanx.module.aha.cli.render;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动横幅：左侧信息栏 + 右侧 ASCII 标志的并排组装。
 *
 * <p>只做组装，不读终端：给一个终端宽度，返回应当打印的行；放不下时原样返回信息行。
 * 因而可以脱开真实终端做单元测试（终端宽度是唯一变量）。</p>
 *
 * <p><b>为什么先算宽度再上色</b>：{@link TextWidth} 数的是显示宽度，把含 SGR 的串交给它，
 * 转义序列会被当成普通字符。因此左栏补位一律对<b>未着色</b>文本做，标志行则先着色、
 * 之后只做拼接、再不测量。这条约束与 {@link ToolBlock} 一致。</p>
 *
 * @since 0.1.0
 */
public final class StartupBanner {

    /** 左栏与标志之间的最小空白列数。 */
    public static final int GAP = 4;

    /** 右侧留白：满宽会触发终端自动换行，把标志挤到下一行。 */
    public static final int RIGHT_MARGIN = 1;

    /** 字段标签的显示宽度（列）：4 个汉字，足够放下最长标签「工作目录」。 */
    public static final int LABEL_WIDTH = 8;

    /** 标签与取值之间的缩进。 */
    private static final String INDENT = "  ";

    private StartupBanner() {
    }

    /**
     * 组装一个「标签 + 取值」字段行。
     *
     * <p>必须按显示宽度补位：{@code String.format("%-8s")} 数的是字符数，
     * 中文标签「供应商」（3 字符 / 6 列）与「工作目录」（4 字符 / 8 列）会被补成不同宽度，
     * 取值列于是错位。</p>
     *
     * @param label 标签
     * @param value 取值；空白或 {@code null} 时由调用方决定跳过
     * @return 字段行
     */
    public static String field(String label, String value) {
        return INDENT + TextWidth.pad(label, LABEL_WIDTH) + " " + value;
    }

    /**
     * 组装启动横幅，按顺序挑选第一个放得下的标志。
     *
     * <p>候选顺序即优先顺序，由调用方决定（像素风优先 / 纯 ASCII 优先 / 只给线框图）。</p>
     *
     * @param info          左栏信息行（未着色）
     * @param terminalWidth 终端宽度（列）；{@code <= 0} 表示未知
     * @param candidates    候选标志，按优先级排列；可为空
     * @return 应当逐行打印的内容
     */
    public static List<String> compose(List<String> info, int terminalWidth,
            List<LogoArt> candidates) {
        if (info == null || info.isEmpty()) {
            return List.of();
        }
        if (terminalWidth > 0 && candidates != null) {
            int leftWidth = info.stream().mapToInt(TextWidth::width).max().orElse(0);
            for (LogoArt logo : candidates) {
                int pad = terminalWidth - RIGHT_MARGIN - logo.width() - leftWidth;
                if (pad >= GAP) {
                    return merge(info, leftWidth, logo.lines(), pad);
                }
            }
        }
        // 都放不下（或宽度未知）：信息照常打印，标志不显示
        return List.copyOf(info);
    }

    /**
     * 并排合并两栏。
     *
     * @param info      左栏（未着色）
     * @param leftWidth 左栏显示宽度
     * @param logo      右栏（已着色）
     * @param pad       左栏与右栏之间的空白列数
     * @return 合并后的行
     */
    private static List<String> merge(List<String> info, int leftWidth, List<String> logo, int pad) {
        List<String> merged = new ArrayList<>(Math.max(info.size(), logo.size()));
        String gap = " ".repeat(pad);
        for (int i = 0; i < Math.max(info.size(), logo.size()); i++) {
            String left = i < info.size() ? info.get(i) : "";
            String right = i < logo.size() ? logo.get(i) : null;
            String padded = TextWidth.pad(left, leftWidth);
            // 标志比信息栏高时，多出来的行左栏为空，但必须照旧补位：
            // 不补的话标志会从左边冒出来，与上方各行不在同一列
            // 标志结束后不再补空白：行尾留白没有意义，还会让重定向输出变脏
            merged.add(right == null ? left : padded + gap + right);
        }
        return merged;
    }
}
