package com.acanx.module.aha.cli.tty;

import java.io.Console;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 终端能力探测结果。
 *
 * <p>探测的是<b>能力</b>而非平台或终端品牌：无论 Windows Terminal、Tabby、iTerm2
 * 还是 Xshell，对进程暴露的都是「一个 TTY + 一组环境变量」，因此只需覆盖
 * TTY / 颜色深度 / 宽度三个维度，无需为任何终端单独适配。</p>
 *
 * <p>渲染层据此降级：非 TTY（管道、CI、重定向）输出纯文本，避免出现 ANSI 转义垃圾。</p>
 *
 * @param tty         标准输出是否连接到终端
 * @param depth       色彩深度
 * @param width       终端宽度（列）；{@code 0} 表示未知，此时不折行
 * @param description 探测依据摘要，用于日志与问题排查
 * @since 0.1.0
 */
public record TerminalCapabilities(boolean tty, ColorDepth depth, int width, String description) {

    /** 色彩深度。 */
    public enum ColorDepth {
        /** 不支持颜色。 */
        NONE,
        /** 16 色。 */
        ANSI16,
        /** 256 色。 */
        ANSI256,
        /** 真彩色。 */
        TRUECOLOR
    }

    // 说明：渲染层目前只用基础 SGR（16 色），因此深度仅决定「是否着色」。
    // 保留完整深度是为了日志诊断与后续 Markdown 高亮（可能需要更多颜色）。

    /** 标准约定的「禁用颜色」环境变量。 */
    public static final String NO_COLOR = "NO_COLOR";

    /**
     * 探测当前终端能力。
     *
     * @param noColor 用户是否显式要求关闭颜色（{@code --no-color}），或设置了 {@code NO_COLOR}
     * @return 能力描述
     */
    public static TerminalCapabilities detect(boolean noColor) {
        Map<String, String> env = System.getenv();
        boolean disableColor = noColor || hasValue(env.get(NO_COLOR));
        ColorDepth depth = disableColor ? ColorDepth.NONE : detectDepth(env);
        return new TerminalCapabilities(detectTty(), depth, detectWidth(env), describe(env, depth));
    }

    /**
     * 是否输出颜色。
     *
     * <p>三者必须同时成立：连接到终端、未被显式禁用、终端支持颜色。
     * 管道输出时即便终端支持颜色也要关闭。</p>
     *
     * @return 是否着色
     */
    public boolean colored() {
        return tty && depth != ColorDepth.NONE;
    }

    /**
     * 覆盖 TTY 判定。
     *
     * <p>JLine 是终端信息的更权威来源（它能识别 {@code dumb} 与平台差异），
     * 由调用方在创建 Terminal 后回填。</p>
     *
     * @param value 是否为终端
     * @return 新的能力描述
     */
    public TerminalCapabilities withTty(boolean value) {
        return new TerminalCapabilities(value, depth, width, description);
    }

    /**
     * 覆盖宽度。
     *
     * @param value 终端宽度；{@code <= 0} 表示未知
     * @return 新的能力描述
     */
    public TerminalCapabilities withWidth(int value) {
        return new TerminalCapabilities(tty, depth, Math.max(value, 0), description);
    }

    /**
     * 探测色彩深度。
     *
     * <p>包级可见以便测试注入环境变量。</p>
     *
     * @param env 环境变量
     * @return 色彩深度
     */
    static ColorDepth detectDepth(Map<String, String> env) {
        String term = lower(env.get("TERM"));
        String colorTerm = lower(env.get("COLORTERM"));
        if ("dumb".equals(term)) {
            return ColorDepth.NONE;
        }
        if (colorTerm.contains("truecolor") || colorTerm.contains("24bit")) {
            return ColorDepth.TRUECOLOR;
        }
        // TERM 是最可靠的依据，优先于控制台标识：
        // tmux / screen 会把 TERM 改写为 screen-256color 之类，此时它就是事实
        if (term.contains("256color")) {
            return ColorDepth.ANSI256;
        }
        // Windows 传统控制台：TERM 往往为空，只能靠控制台标识判断。
        // WT_SESSION=Windows Terminal；ConEmuANSI=ON；ANSICON=旧版 ANSI 注入器；
        // TERM_PROGRAM 为 Tabby / iTerm2 等第三方终端所设
        if (term.isEmpty()
                && (hasValue(env.get("WT_SESSION")) || "on".equalsIgnoreCase(env.get("ConEmuANSI"))
                        || hasValue(env.get("ANSICON")) || hasValue(env.get("TERM_PROGRAM")))) {
            return ColorDepth.TRUECOLOR;
        }
        return ColorDepth.ANSI16;
    }

    private static boolean detectTty() {
        Console console = System.console();
        if (console == null) {
            return false;
        }
        // JDK 22 起 Console.isTerminal() 才真正区分「有 Console」与「连的是终端」
        try {
            return console.isTerminal();
        } catch (UnsupportedOperationException e) {
            return true;
        }
    }

    private static int detectWidth(Map<String, String> env) {
        String columns = env.get("COLUMNS");
        if (columns == null || columns.isBlank()) {
            return 0;
        }
        try {
            int value = Integer.parseInt(columns.trim());
            return Math.max(value, 0);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String describe(Map<String, String> env, ColorDepth depth) {
        Map<String, String> parts = new LinkedHashMap<>();
        putIfPresent(parts, "TERM", env.get("TERM"));
        putIfPresent(parts, "COLORTERM", env.get("COLORTERM"));
        putIfPresent(parts, "TERM_PROGRAM", env.get("TERM_PROGRAM"));
        putIfPresent(parts, "WT_SESSION", env.get("WT_SESSION"));
        parts.put("depth", depth.name());
        return parts.toString();
    }

    private static void putIfPresent(Map<String, String> parts, String key, String value) {
        if (hasValue(value)) {
            parts.put(key, value);
        }
    }

    private static boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT);
    }
}
