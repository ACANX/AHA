package com.acanx.module.aha.cli.render;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.acanx.module.aha.common.tool.ToolKind;

/**
 * 工具调用的区块化展示。
 *
 * <p>区块结构：</p>
 *
 * <pre>
 *  执行  shell-exec                                  ← 首行：类别 + 工具名（色带）
 *  终端  cmd.exe /c                                  ← 明细：在哪个终端
 *  目录  E:\repo                                     ← 明细：在哪个目录
 *  命令  powershell -NoProfile -Command "…"          ← 明细：完整命令（折行，不截断）
 *  结果                                              ← 结果区
 *     …输出…
 *  ✓ 完成 · 1.2s · 3 行                              ← 末行：状态 + 用时 + 规模
 * </pre>
 *
 * <p><b>命令与输出一律折行，绝不截断</b>：此前长命令被截到 80 字符（还常常连终端宽度都过不了），
 * 用户完全看不到到底执行了什么，「为什么失败」也就无从判断。只有几百行级别的超长命令才在
 * <b>中间</b>省略——首尾通常才是关键信息。</p>
 *
 * @since 0.1.0
 */
final class ToolBlock {

    /** 区块之间留一个空行。 */
    static final String SEPARATOR = "";

    /** 成功时结果预览最大行数（折行之后）。 */
    static final int PREVIEW_LINES = 5;

    /** 失败时错误正文最大行数。 */
    static final int FAILURE_LINES = 40;

    /** 命令行数超过该值时中间省略。 */
    static final int COMMAND_ELIDE_LINES = 40;

    /** 省略时保留的首部行数。 */
    private static final int ELIDE_HEAD = 20;

    /** 省略时保留的尾部行数。 */
    private static final int ELIDE_TAIL = 10;

    /** 参数值超过该长度时只报规模，避免把整个文件内容铺到终端。 */
    static final int ARG_VALUE_LIMIT = 200;

    /** 明细行缩进（显示宽度）。 */
    static final int INDENT = 2;

    /** 明细行标签列宽（显示宽度）。 */
    private static final int LABEL_WIDTH = 6;

    /** 命令换行后的续行缩进（显示宽度）。 */
    private static final int CONTINUATION_INDENT = 2;

    private ToolBlock() {
    }

    /**
     * 首行：类别 + 工具名（+ 命令类操作的程序名）。
     *
     * <p>只放「一眼能分清是什么操作」的短标识：色带行必须定宽铺满，放不下就只剩截断一条路。
     * 长内容（命令全文、文件路径）走各自的明细行，那里可以折行。</p>
     *
     * @param toolName 工具名
     * @param args     调用参数，可为 {@code null}
     * @param style    样式
     * @param width    终端宽度；{@code <= 0} 时不铺底
     * @return 已着色文本
     */
    static String head(String toolName, Map<String, Object> args, Style style, int width) {
        ToolKind kind = ToolKind.of(toolName);
        String program = kind.programOf(args);
        String tail = program == null ? "" : "  " + program;
        // 无色时没有色带可依，改用结构标记区分类型
        String body = style.colored()
                ? kind.label() + "  " + toolName + tail
                : "[" + kind.label() + "] " + toolName + tail;
        return style.toolBand(kind, body, width);
    }

    /**
     * 命令行：还原成「在终端里敲下这条命令」的样子。
     *
     * <p>形如 {@code E:\repo> powershell -NoProfile ...}，整行高亮白。用户排查问题时
     * 第一个要看的就是这一行：在哪个目录、用什么终端、跑了什么命令。</p>
     *
     * @param command 命令原文
     * @param style   样式
     * @param env     执行环境，可为 {@code null}
     * @param width   终端宽度
     * @return 已着色文本（可能含换行）
     */
    static String command(String command, Style style, ToolEnvironment env, int width) {
        String prompt = env == null ? "" : env.prompt();
        // 留出续行缩进，保证首行也不会顶到终端边界
        int limit = width > 0 ? Math.max(8, width - 1 - CONTINUATION_INDENT) : 0;
        List<String> parts = TextWidth.wrap(prompt + command, limit);
        if (parts.size() > COMMAND_ELIDE_LINES) {
            // 几百行的超长命令全铺出来会淹没整屏；首尾通常才是关键信息
            int hidden = parts.size() - ELIDE_HEAD - ELIDE_TAIL;
            List<String> trimmed = new ArrayList<>(parts.subList(0, ELIDE_HEAD));
            trimmed.add("… 中间省略 " + hidden + " 行 …");
            trimmed.addAll(parts.subList(parts.size() - ELIDE_TAIL, parts.size()));
            parts = trimmed;
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                text.append('\n').append(" ".repeat(CONTINUATION_INDENT));
            }
            text.append(parts.get(i));
        }
        return style.command(text.toString());
    }

    /**
     * 明细行：讲清「在哪儿、用什么、执行了什么」。
     *
     * <p>命令类工具还原成终端视角（终端 / 目录 / 命令）；其余工具逐条列出参数，
     * 超长值只报规模——把整个文件内容铺出来没有意义，那是模型该看的东西。</p>
     *
     * @param toolName 工具名
     * @param args     调用参数
     * @param style    样式
     * @param width    终端宽度
     * @param env      执行环境，可为 {@code null}
     * @return 明细行
     */
    static List<String> details(String toolName, Map<String, Object> args, Style style,
                                int width, ToolEnvironment env) {
        List<String> lines = new ArrayList<>();
        // 命令类不走这里：它的命令全文由 ToolBlock#command 单独一行展示
        if (ToolKind.of(toolName) == ToolKind.EXEC) {
            return lines;
        }
        for (Map.Entry<String, String> entry : ToolKind.summary(args).entrySet()) {
            String value = entry.getValue();
            if (value != null && value.length() > ARG_VALUE_LIMIT) {
                value = "<" + value.length() + " 字符，已省略>";
            }
            add(lines, entry.getKey(), value, style, width, false);
        }
        return lines;
    }

    /**
     * 工具结果行。
     *
     * @param success 是否成功
     * @param detail  摘要（耗时、输出规模、或错误信息）
     * @param style   样式
     * @param width   终端宽度
     * @param unicode 是否可用符号
     * @return 已着色文本
     */
    static String result(boolean success, String detail, Style style, int width, boolean unicode) {
        String mark = success
                ? (unicode ? "✓" : "[ok]")
                : (unicode ? "✗" : "[fail]");
        String body = mark + " " + (success ? "完成" : "失败")
                + (detail == null || detail.isBlank() ? "" : " · " + detail);
        return style.resultBand(success, body, width);
    }

    /**
     * 输出正文：按显示宽度折行，绝不截断。
     *
     * @param output   输出
     * @param style    样式
     * @param width    终端宽度
     * @param maxLines 最大行数
     * @param dim      是否弱化
     * @return 正文行（含可能的省略提示行）
     */
    static List<String> body(Object output, Style style, int width, int maxLines, boolean dim) {
        if (output == null) {
            return List.of();
        }
        String content = String.valueOf(output);
        if (content.isBlank()) {
            return List.of();
        }
        String[] source = content.split("\n", -1);
        int limit = available(width);
        List<String> lines = new ArrayList<>();
        boolean truncated = false;
        outer:
        for (String line : source) {
            for (String part : TextWidth.wrap(line, limit)) {
                if (lines.size() >= maxLines) {
                    truncated = true;
                    break outer;
                }
                String indented = " ".repeat(INDENT) + part;
                lines.add(dim ? style.muted(indented) : indented);
            }
        }
        if (truncated) {
            lines.add(style.muted(" ".repeat(INDENT) + "… 输出共 " + source.length
                    + " 行，此处显示前 " + maxLines + " 行"));
        }
        return lines;
    }

    /**
     * 结果摘要：按输出规模或错误信息生成一行说明。
     *
     * @param success 是否成功
     * @param output  工具输出
     * @param millis  耗时（毫秒）
     * @return 摘要文本
     */
    static String detail(boolean success, Object output, long millis) {
        StringBuilder text = new StringBuilder();
        // 不足 100ms 就别显示「0.0s」——那不是信息，是噪音
        if (millis >= 100) {
            text.append(String.format("%.1fs", millis / 1000.0));
        }
        String scale = scaleOf(output);
        if (scale != null) {
            if (text.length() > 0) {
                text.append(" · ");
            }
            text.append(scale);
        }
        return text.toString();
    }

    /**
     * 失败时的错误摘要（单行）。
     *
     * @param output 工具输出（失败时为错误信息）
     * @return 单行摘要
     */
    static String failureDetail(Object output) {
        return failureDetail(output, 0);
    }

    /**
     * 失败时的错误摘要（带耗时）。
     *
     * <p>耗时总在区块末行——失败时同样要给：它本身就是排查线索
     * （秒级失败多半是参数或环境问题，超时才失败往往是命令本身卡住）。</p>
     *
     * @param output 工具输出（失败时为错误信息）
     * @param millis 耗时（毫秒）
     * @return 单行摘要
     */
    static String failureDetail(Object output, long millis) {
        StringBuilder text = new StringBuilder();
        if (millis >= 100) {
            text.append(String.format("%.1fs", millis / 1000.0)).append(" · ");
        }
        if (output == null) {
            return text.toString();
        }
        text.append(TextWidth.truncate(String.valueOf(output).replace('\n', ' ').trim(), 80));
        return text.toString();
    }

    /**
     * 追加一行「标签 + 值」，超宽折行且续行对齐。
     *
     * @param lines 结果列表
     * @param label 标签
     * @param value 值
     * @param style 样式
     * @param width 终端宽度
     * @param elide 超长时是否中间省略
     */
    private static void add(List<String> lines, String label, String value, Style style,
                            int width, boolean elide) {
        if (value == null || value.isBlank()) {
            return;
        }
        String head = TextWidth.pad(label, LABEL_WIDTH) + "  ";
        int indent = INDENT + TextWidth.width(head);
        int limit = width > 0 ? Math.max(8, width - indent - 1) : 0;
        List<String> wrapped = TextWidth.wrap(value, limit);
        if (elide && wrapped.size() > COMMAND_ELIDE_LINES) {
            int hidden = wrapped.size() - ELIDE_HEAD - ELIDE_TAIL;
            List<String> trimmed = new ArrayList<>(wrapped.subList(0, ELIDE_HEAD));
            trimmed.add("… 中间省略 " + hidden + " 行 …");
            trimmed.addAll(wrapped.subList(wrapped.size() - ELIDE_TAIL, wrapped.size()));
            wrapped = trimmed;
        }
        for (int i = 0; i < wrapped.size(); i++) {
            String prefix = i == 0 ? head : " ".repeat(TextWidth.width(head));
            lines.add(style.muted(" ".repeat(INDENT) + prefix + wrapped.get(i)));
        }
    }

    /**
     * 正文可用宽度。
     *
     * @param width 终端宽度
     * @return 可用宽度；{@code 0} 表示不折行
     */
    private static int available(int width) {
        return width > 0 ? Math.max(8, width - INDENT - 1) : 0;
    }

    /**
     * 输出规模描述。
     *
     * @param output 输出
     * @return 描述；无内容时返回 {@code null}
     */
    private static String scaleOf(Object output) {
        if (output == null) {
            return null;
        }
        String text = String.valueOf(output);
        if (text.isBlank()) {
            return "无输出";
        }
        int lines = text.split("\n", -1).length;
        return lines > 1 ? lines + " 行" : text.length() + " 字符";
    }

    /**
     * 取值文本。
     *
     * @param value 值
     * @return 文本；空值返回 {@code null}
     */
    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
