package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.core.markdown.CodeLexer;
import com.acanx.module.aha.core.markdown.CodeToken;

/**
 * 围栏代码的轻量语法高亮（issue #336）。
 *
 * <p>只做<b>单行</b>内的着色：注释、字符串、数字、关键字。不引入跨行状态机，
 * 因此行数与换行保持不变，不会与表格 / 折叠逻辑互相干扰。</p>
 *
 * <p>词法规则在 {@link CodeLexer}（issue #420）：本类只负责「{@link CodeToken.Kind} → ANSI」的映射，
 * 桌面端复用同一份词法结果换成 JavaFX 样式，两边的高亮口径因此不会分叉。</p>
 *
 * <p>未知语言或未标语言时原样返回；无色终端（{@code Style.of(false)}）同样原样返回，
 * 不产生任何 ANSI 序列。</p>
 *
 * @since 0.1.5
 */
final class CodeHighlighter {

    private CodeHighlighter() {
    }

    /**
     * 高亮一行代码。
     *
     * @param line     该行内容（不含换行）
     * @param language 围栏语言标识，可为 {@code null}
     * @param style    样式
     * @return 高亮后的文本；未知语言 / 无色时原样返回
     */
    static String highlight(String line, String language, Style style) {
        if (line == null || line.isEmpty() || style == null || !style.colored()) {
            return line;
        }
        if (!CodeLexer.isSupported(language)) {
            return line;
        }
        StringBuilder out = new StringBuilder(line.length() + 16);
        for (CodeToken token : CodeLexer.tokenize(line, language)) {
            out.append(switch (token.kind()) {
                case KEYWORD -> style.keyword(token.text());
                case STRING -> style.string(token.text());
                case NUMBER -> style.number(token.text());
                case COMMENT -> style.comment(token.text());
                case PLAIN -> token.text();
            });
        }
        return out.toString();
    }
}
