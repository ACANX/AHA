package com.acanx.module.aha.desktop.markdown;

/**
 * 任务列表项识别（issue #421 / tracking #408 S2.3）。
 *
 * <p>只做「认标记」这一件事，不改 {@link MarkdownBlock.ListItem} 的结构：任务项与普通项
 * 在 AST 里是同一种节点，区别只在文本前缀。这样 {@code MarkdownParser} 不必多背一个状态，
 * 渲染器也能在没有 JavaFX 的环境里被单测（这个类是纯逻辑）。</p>
 *
 * <p>支持 {@code [ ]}、{@code [x]}、{@code [X]} 三种写法；方括号后允许一个空格。
 * 其它形式（{@code [y]}、{@code []}、没有方括号）一律当普通列表项，不做猜测。</p>
 *
 * @since 0.2.0
 */
public final class MarkdownTask {

    private MarkdownTask() {
    }

    /**
     * 识别结果。
     *
     * @param checked 是否已勾选
     * @param text    去掉标记后的文本
     */
    public record Task(boolean checked, String text) {
    }

    /**
     * 识别任务列表项。
     *
     * @param text 列表项文本（已去掉 {@code -} / {@code *} 等前缀）
     * @return 任务项；不是任务项时返回 {@code null}
     */
    public static Task of(String text) {
        if (text == null || text.length() < 3) {
            return null;
        }
        if (text.charAt(0) != '[' || text.charAt(2) != ']') {
            return null;
        }
        char mark = text.charAt(1);
        if (mark != ' ' && mark != 'x' && mark != 'X') {
            return null;
        }
        int start = 3;
        if (start < text.length() && text.charAt(start) == ' ') {
            start++;
        }
        return new Task(mark != ' ', text.substring(start));
    }
}
