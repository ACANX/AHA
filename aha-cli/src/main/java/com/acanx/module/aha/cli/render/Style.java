package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.cli.tty.TerminalCapabilities.ColorDepth;

/**
 * 文字样式。
 *
 * <p>只用基础 SGR 序列：颜色、加粗、斜体、弱化，以及工具区块用的背景色带。
 * 不碰光标控制序列——终端行为差异太大，且会让输出无法安全地重定向。</p>
 *
 * <p>配色分两档：</p>
 * <ul>
 *   <li><b>256 色 / 真彩</b>：暗灰底（{@code 48;5;236}）整行铺底 + 类型色标签，
 *       长时间阅读不刺眼，接近 pi / Claude Code 的观感</li>
 *   <li><b>16 色</b>：亮底 + 显式前景色。16 色下亮底配默认前景在浅色终端上几乎不可读，
 *       因此必须同时指定前景</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class Style {

    private static final String RESET = "\u001B[0m";

    private static final String BOLD = "\u001B[1m";

    private static final String DIM = "\u001B[2m";

    private static final String ITALIC = "\u001B[3m";

    private static final String RED = "\u001B[31m";

    private static final String GREEN = "\u001B[32m";

    private static final String CYAN = "\u001B[36m";

    private static final String BRIGHT_CYAN = "\u001B[96m";

    private static final String UNDERLINE = "\u001B[4m";

    /** 256/真彩下的工具区块底色（暗灰）。 */
    private static final String BAND_BG = "\u001B[48;5;236m";

    /** 256/真彩下工具区块的文字色。 */
    private static final String BAND_FG = "\u001B[38;5;253m";

    /** 256/真彩下失败区块的底色（暗红）。 */
    private static final String FAIL_BG = "\u001B[48;5;52m";

    /** 256/真彩下失败区块的文字色。 */
    private static final String FAIL_FG = "\u001B[38;5;224m";

    /** 16 色下失败区块：亮红底 + 亮白字。 */
    private static final String FAIL_16 = "\u001B[41;97m";

    /** 输入框边框色（256 色 / 真彩）：高亮紫。 */
    private static final String FRAME_256 = "\u001B[38;5;141m";

    /** 输入框边框色（16 色）：亮品红。 */
    private static final String FRAME_16 = "\u001B[95m";

    /** 启动标志「光线」色（256 色 / 真彩）：琥珀 —— 取自 Logo.svg 的射线。 */
    private static final String LOGO_RAY_256 = "\u001B[38;5;214m";

    /** 启动标志「光线」色（16 色）：亮黄。 */
    private static final String LOGO_RAY_16 = "\u001B[93m";

    /** 启动标志「灯泡」色（256 色 / 真彩）：淡琥珀 —— 光线更弱，才能看出明暗层次。 */
    private static final String LOGO_BULB_256 = "\u001B[38;5;180m";

    /** 启动标志「灯泡」色（16 色）：黄。 */
    private static final String LOGO_BULB_16 = "\u001B[33m";

    /** 启动标志「灯座」色（256 色 / 真彩）：中灰。 */
    private static final String LOGO_BASE_256 = "\u001B[38;5;245m";

    /** 启动标志「灯座」色（16 色）：暗灰。 */
    private static final String LOGO_BASE_16 = "\u001B[90m";

    /** 启动标志「AHA」字样色（256 色 / 真彩）：加粗亮琥珀。 */
    private static final String LOGO_WORD_256 = "\u001B[1;38;5;220m";

    /** 启动标志「AHA」字样色（16 色）：加粗亮黄。 */
    private static final String LOGO_WORD_16 = "\u001B[1;93m";

    /** 16 色下中性区块：亮黑底 + 亮白字。 */
    private static final String BAND_16 = "\u001B[100;97m";

    private final ColorDepth depth;

    private final boolean color;

    private Style(ColorDepth depth) {
        this.depth = depth == null ? ColorDepth.NONE : depth;
        this.color = this.depth != ColorDepth.NONE;
    }

    /**
     * 按是否着色创建（测试与降级路径用）。
     *
     * @param color 是否着色
     * @return 样式
     */
    public static Style of(boolean color) {
        return new Style(color ? ColorDepth.ANSI16 : ColorDepth.NONE);
    }

    /**
     * 按色彩深度创建。
     *
     * @param depth 色彩深度
     * @return 样式
     */
    public static Style of(ColorDepth depth) {
        return new Style(depth);
    }

    /**
     * 是否着色。
     *
     * @return 着色时为 {@code true}
     */
    public boolean colored() {
        return color;
    }

    /**
     * 工具调用信息块的首行：整行铺底，用类型色区分操作类别。
     *
     * <p>无色时退化为 {@code [读取] file-read  path} 这样的结构标记——
     * 没有色带时必须靠结构本身可辨。</p>
     *
     * @param kind     工具类别
     * @param text     文本（不含色带）
     * @param width    终端宽度；{@code <= 0} 时不铺底
     * @return 已着色文本
     */
    public String toolBand(ToolKind kind, String text, int width) {
        if (!color) {
            return text;
        }
        // 加粗 + 更鲜艳的类型色：这是「谁做了什么」的主语，要第一眼抓住注意
        String sgr = depth == ColorDepth.ANSI16
                ? BAND_16 + BOLD + "\u001B[" + ansi16Foreground(kind) + "m"
                : BAND_BG + BOLD + "\u001B[38;5;" + accent(kind) + "m";
        return fill(sgr, text, width);
    }

    /**
     * 工具结果行：成功保持中性，失败用红底引起注意。
     *
     * @param success 是否成功
     * @param text    文本
     * @param width   终端宽度；{@code <= 0} 时不铺底
     * @return 已着色文本
     */
    public String resultBand(boolean success, String text, int width) {
        if (!color) {
            return text;
        }
        String sgr = success
                ? (depth == ColorDepth.ANSI16 ? BAND_16 : BAND_BG + BAND_FG)
                : (depth == ColorDepth.ANSI16 ? FAIL_16 : FAIL_BG + FAIL_FG);
        return fill(sgr, text, width);
    }

    /**
     * 加粗。
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String bold(String text) {
        return paint(BOLD, text);
    }

    /**
     * 行内代码。
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String code(String text) {
        return paint(GREEN, text);
    }

    /**
     * 强调（Markdown 的斜体 / 粗体 / 粗斜体）。
     *
     * @param level 强调级别：1 斜体、2 粗体、3 粗斜体
     * @param text  文本
     * @return 已着色文本
     */
    public String emphasis(int level, String text) {
        return switch (level) {
            case 2 -> paint(BOLD, text);
            case 3 -> paint(BOLD + ITALIC, text);
            default -> paint(ITALIC, text);
        };
    }

    /**
     * 标记（列表符号、围栏标记等）。
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String marker(String text) {
        return paint(CYAN, text);
    }

    /**
     * 错误。
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String error(String text) {
        return paint(RED, text);
    }

    /**
     * 弱化（次要信息）。
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String muted(String text) {
        return paint(DIM, text);
    }

    /**
     * 工具调用（旧版单行展示，保留给非区块场景）。
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String toolCall(String text) {
        return paint(CYAN, text);
    }

    /**
     * 工具结果（旧版单行展示）。
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String toolResult(String text) {
        return paint(DIM, text);
    }

    /**
     * 类型主色（256 色）。
     *
     * @param kind 工具类别
     * @return 色号
     */
    private static int accent(ToolKind kind) {
        return switch (kind) {
            case READ -> 117;
            case WRITE -> 221;
            case EXEC -> 215;
            case NETWORK -> 123;
            case OTHER -> 255;
        };
    }

    /**
     * 类型主色（16 色前景 SGR 参数）。
     *
     * @param kind 工具类别
     * @return SGR 参数
     */
    private static String ansi16Foreground(ToolKind kind) {
        return switch (kind) {
            case READ -> "97";
            case WRITE -> "30";
            case EXEC -> "97";
            case NETWORK -> "30";
            case OTHER -> "97";
        };
    }

    /**
     * 铺底到指定宽度并复位。
     *
     * <p>截断必须发生在<b>上色之前</b>：{@code TextWidth} 数的是显示宽度，
     * 若把含 SGR 的串交进去，转义序列会被当成普通字符，宽度算错、色带也跟着断。</p>
     *
     * @param sgr   SGR 前缀
     * @param text  纯文本
     * @param width 目标宽度；{@code <= 0} 时只上色不铺底
     * @return 定宽着色文本
     */
    private String fill(String sgr, String text, int width) {
        String body = text;
        if (width > 0) {
            // 背景色带必须自己铺满：只给颜色不铺底，看起来就是一段彩色文字而不是“区块”
            int limit = Math.max(0, width - 1);
            body = TextWidth.pad(body, limit);
        }
        return sgr + body + RESET;
    }

    /**
     * 包装一段文本。
     *
     * @param sgr  SGR 前缀
     * @param text 文本
     * @return 已着色文本
     */
    private String paint(String sgr, String text) {
        if (!color || text == null || text.isEmpty()) {
            return text;
        }
        return sgr + text + RESET;
    }
    /**
     * 命令行的展示样式：高亮白。
     *
     * <p>命令是用户最需要看清的一行（排查问题时全靠它），因此用最亮的白字单独拎出来，
     * 与上方「执行命令」栏的彩色、下方结果区的常规色都区分开。</p>
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String command(String text) {
        return paint(depth == ColorDepth.ANSI16 ? "\u001B[97m" : "\u001B[38;5;255m", text);
    }

    /**
     * 输入框边框（上下两条水平线）。
     *
     * <p>用高亮紫色把输入区框出来：它是「这里可以打字」的唯一提示（行首已无 {@code >} 标记），
     * 需要一眼可见但不该抢正文视线。</p>
     *
     * @param text 线文本
     * @return 已着色文本
     */
    public String frame(String text) {
        return paint(depth == ColorDepth.ANSI16 ? FRAME_16 : FRAME_256, text);
    }

    /**
     * 启动标志：四周光线。
     *
     * <p>整套标志色取自 {@code Logo.svg} 的琥珀色系：亮（光线）→ 中（灯泡）→ 暗（灯座）。
     * 纯 ASCII 图形本身没有明暗，只能靠这三档色阶把层次拉开。</p>
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String logoRay(String text) {
        return paint(depth == ColorDepth.ANSI16 ? LOGO_RAY_16 : LOGO_RAY_256, text);
    }

    /**
     * 启动标志：灯泡轮廓与表情。
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String logoBulb(String text) {
        return paint(depth == ColorDepth.ANSI16 ? LOGO_BULB_16 : LOGO_BULB_256, text);
    }

    /**
     * 启动标志：灯座。
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String logoBase(String text) {
        return paint(depth == ColorDepth.ANSI16 ? LOGO_BASE_16 : LOGO_BASE_256, text);
    }

    /**
     * 启动标志：{@code AHA} 字样。
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String logoWord(String text) {
        return paint(depth == ColorDepth.ANSI16 ? LOGO_WORD_16 : LOGO_WORD_256, text);
    }

    /**
     * 章节标题。
     *
     * <p>六个级别各有可辨的字重与明度：一级最重（粗 + 下划线 + 亮青），逐级递减到
     * 六级（弱化 + 斜体）。此前三级只有加粗、四级往后全是同一种青色，等于三级之后
     * 就没再渲染——「这是几级标题」实际读不出来。</p>
     *
     * @param level 级别 1~6
     * @param text  文本
     * @return 已着色文本
     */
    public String heading(int level, String text) {
        return switch (level) {
            case 1 -> paint(BOLD + UNDERLINE + BRIGHT_CYAN, text);
            case 2 -> paint(BOLD + BRIGHT_CYAN, text);
            case 3 -> paint(BOLD + CYAN, text);
            case 4 -> paint(CYAN, text);
            case 5 -> paint(DIM + CYAN, text);
            default -> paint(DIM + ITALIC + CYAN, text);
        };
    }

    /**
     * 表格边框。
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String tableBorder(String text) {
        return paint(DIM, text);
    }

    /**
     * 表格表头。
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String tableHeader(String text) {
        return paint(BOLD, text);
    }

    /**
     * 链接文本。
     *
     * @param text 文本
     * @return 已着色文本
     */
    public String link(String text) {
        return paint(CYAN + UNDERLINE, text);
    }

    /**
     * 链接地址。
     *
     * @param text 地址
     * @return 已着色文本
     */
    public String linkUrl(String text) {
        return paint(DIM, text);
    }

}
