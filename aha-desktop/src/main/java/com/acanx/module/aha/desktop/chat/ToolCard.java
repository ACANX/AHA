package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.common.tool.ToolKind;

import java.util.Locale;
import java.util.Map;

/**
 * 工具卡片的文字、截断与折叠口径（纯函数）。
 *
 * <p>口径来自 {@code GUIDesign.md} 第 4.2 节——工具卡片是桌面端出现得最频繁的组件，
 * 因此把它「说什么、显示多少、何时折叠」全部抽成不引用 JavaFX 的纯逻辑，
 * 界面只负责摆控件（与 {@code FoldState} / {@code ShellLayout} 同一个思路）。</p>
 *
 * <p>三处硬规则：</p>
 * <ul>
 *   <li><strong>默认折叠</strong>：长输出不能淹没对话；</li>
 *   <li><strong>不足 100ms 不显示耗时</strong>：与 CLI 同规则，避免出现「0.0s」这种噪音；</li>
 *   <li><strong>预览截断到 {@value #PREVIEW_LINES} 行</strong>，并明确写出「前 N 行，共 M 行」，
 *       而不是悄悄截断——用户必须知道自己看的不是全部。</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class ToolCard {

    /** 展开时预览的最大行数（设计稿为 200 行）。 */
    public static final int PREVIEW_LINES = 200;

    /** 耗时显示门槛（毫秒）：不足此值不显示耗时。 */
    public static final long DURATION_THRESHOLD_MS = 100;

    /** 折叠态箭头。 */
    public static final String CARET_COLLAPSED = "▸";

    /** 展开态箭头。 */
    public static final String CARET_EXPANDED = "▾";

    /** 折叠按钮提示。 */
    public static final String TOOLTIP_EXPAND = "展开详情（参数与输出）";

    /** 展开按钮的提示。 */
    public static final String TOOLTIP_COLLAPSE = "收起详情";

    /** 运行中占位文案。 */
    public static final String RUNNING = "运行中…";

    /** 无参数占位文案。 */
    public static final String NO_ARGS = "（无参数）";

    private ToolCard() {
    }

    /**
     * 首行：类别 + 工具名 + 目标。
     *
     * @param kindLabel 类别标签（如「读取」）
     * @param toolName  工具名
     * @param target    操作目标，可为 {@code null}
     * @return 形如「读取  file-read  AgentEngine.java」
     */
    public static String headline(String kindLabel, String toolName, String target) {
        StringBuilder text = new StringBuilder();
        text.append(kindLabel == null ? ToolKind.OTHER.label() : kindLabel)
                .append("  ")
                .append(toolName == null ? "(未知工具)" : toolName);
        if (target != null && !target.isBlank()) {
            text.append("  ").append(target);
        }
        return text.toString();
    }

    /**
     * 结果行：状态 + 耗时 + 规模。
     *
     * <p>顺序与设计稿一致：「✓ 完成 · 0.1s · 412 行」。耗时不足
     * {@link #DURATION_THRESHOLD_MS} 毫秒不显示；没有输出时不显示规模。</p>
     *
     * @param finished 是否已结束
     * @param success  是否成功（未结束时忽略）
     * @param millis   耗时（毫秒）
     * @param lines    输出行数
     * @return 结果行文本
     */
    public static String resultLine(boolean finished, boolean success, long millis, int lines) {
        if (!finished) {
            return RUNNING;
        }
        StringBuilder text = new StringBuilder(success ? "✓ 完成" : "✗ 失败");
        if (millis >= DURATION_THRESHOLD_MS) {
            text.append(" · ").append(seconds(millis));
        }
        if (lines > 0) {
            text.append(" · ").append(lines).append(" 行");
        }
        return text.toString();
    }

    /**
     * 耗时文本。
     *
     * @param millis 毫秒
     * @return 形如 {@code 0.1s} 或 {@code 12.3s}
     */
    public static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0, millis) / 1000.0);
    }

    /**
     * 参数区文本：每行一个「键: 值」。
     *
     * @param args 调用参数，可为 {@code null}
     * @return 参数文本；无参数时返回 {@link #NO_ARGS}
     */
    public static String params(Map<String, Object> args) {
        if (args == null || args.isEmpty()) {
            return NO_ARGS;
        }
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, Object> entry : args.entrySet()) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append(entry.getKey()).append(": ").append(entry.getValue());
        }
        return text.toString();
    }

    /**
     * 输出区标题。
     *
     * <p>截断必须说出来：用户看到「前 200 行，共 412 行」才知道还有 212 行没显示
     * （点「全部」可以看完整内容）。</p>
     *
     * @param totalLines 输出总行数
     * @return 标题；无输出时返回空串
     */
    public static String outputTitle(int totalLines) {
        if (totalLines <= 0) {
            return "";
        }
        if (totalLines <= PREVIEW_LINES) {
            return "输出（共 " + totalLines + " 行）";
        }
        return "输出（前 " + PREVIEW_LINES + " 行，共 " + totalLines + " 行）";
    }

    /**
     * 取输出的前 {@value #PREVIEW_LINES} 行。
     *
     * @param output 输出文本，可为 {@code null}
     * @return 预览文本；空输入返回空串
     */
    public static String preview(String output) {
        if (output == null || output.isEmpty()) {
            return "";
        }
        int lines = 0;
        for (int i = 0; i < output.length(); i++) {
            if (output.charAt(i) == '\n') {
                lines++;
                if (lines >= PREVIEW_LINES) {
                    return output.substring(0, i);
                }
            }
        }
        return output;
    }

    /**
     * 给每一行加行号（设计稿的展示形态）。
     *
     * <p>行号宽度按总行数对齐，右侧留两格，便于与正文区分。</p>
     *
     * @param text 文本，可为 {@code null}
     * @return 带行号的文本；空输入返回空串
     */
    public static String numbered(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        int width = String.valueOf(lines(text)).length();
        String[] rows = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < rows.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(String.format(Locale.ROOT, "%" + width + "d  %s", i + 1, rows[i]));
        }
        return out.toString();
    }

    /**
     * 文本行数。
     *
     * @param text 文本，可为 {@code null}
     * @return 行数；空文本返回 0
     */
    public static int lines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        return lines;
    }

    /**
     * 把工具结果转成可展示文本。
     *
     * @param result 结果对象，可为 {@code null}
     * @return 文本；空返回 {@code null}
     */
    public static String textOf(Object result) {
        if (result == null) {
            return null;
        }
        String text = String.valueOf(result);
        return text.isBlank() ? null : text;
    }

    /**
     * 失败卡片「重试」按钮发出的消息。
     *
     * <p>刻意**不**在本地重放这次工具调用：重放会绕过模型对当前上下文的判断，
     * 也可能与上一轮的意图脱节。这里把失败事实与原参数交回模型，由它决定是否重试
     * （因此卡片上给的是「重试」入口，而不是一条偷偷执行的命令）。</p>
     *
     * @param toolName 工具名
     * @param args     原参数
     * @return 用户消息文本
     */
    public static String retryMessage(String toolName, Map<String, Object> args) {
        return "上一次 " + (toolName == null ? "工具" : toolName) + " 调用失败了，"
                + "请用同样的参数重试一次。原参数：\n" + params(args);
    }

    /**
     * 折叠态下的箭头。
     *
     * @param expanded 是否展开
     * @return 箭头字符
     */
    public static String caret(boolean expanded) {
        return expanded ? CARET_EXPANDED : CARET_COLLAPSED;
    }

    /**
     * 折叠按钮的提示文本。
     *
     * @param expanded 是否展开
     * @return 提示文本（写明动作，而不是只给一个含义不明的箭头）
     */
    public static String tooltip(boolean expanded) {
        return expanded ? TOOLTIP_COLLAPSE : TOOLTIP_EXPAND;
    }
}
