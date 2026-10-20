package com.acanx.module.aha.core.markdown;

import java.util.List;

/**
 * Markdown 语义基线样本（issue #409 / tracking #408）。
 *
 * <p>同一份样本供 CLI（{@code MarkdownStream}）与桌面端（{@code MarkdownParser}，S1.1）
 * 共用，避免两端各写一套、语义漂移。样本只描述「输入 → 可观察输出」，不绑定实现：
 * CLI 侧断言渲染后的文本 / 结构，桌面端断言解析出的 AST。</p>
 *
 * <p><b>为什么放在 main 而不是 test</b>：Maven 的测试类不跨模块可见，而 CLI 与桌面端
 * 两个模块都要读同一份样本——放 test 源码里必然要复制一份，正是要避免的事。</p>
 *
 * <p><b>pending 样本</b>：当前实现尚未支持的语义（例如反斜杠转义）只记录目标行为，
 * 不参与断言，避免把「已知未实现」固化成期望值（见 PR #404 审查 P3-2 的同类教训）。</p>
 *
 * @since 0.2.0
 */
public final class MarkdownSemanticsSamples {

    /** 断言类型。 */
    public enum Type {
        /** 子串出现次数等于 {@code times}。 */
        COUNT,
        /** 必须包含子串。 */
        CONTAINS,
        /** 必须不包含子串。 */
        NOT_CONTAINS,
        /** 必须匹配正则（find 语义）。 */
        MATCHES
    }

    /**
     * 一条断言。
     *
     * @param type  类型
     * @param value 子串或正则
     * @param times 仅 {@link Type#COUNT} 使用
     */
    public record Expectation(Type type, String value, int times) {

        static Expectation count(String value, int times) {
            return new Expectation(Type.COUNT, value, times);
        }

        static Expectation contains(String value) {
            return new Expectation(Type.CONTAINS, value, 0);
        }

        static Expectation notContains(String value) {
            return new Expectation(Type.NOT_CONTAINS, value, 0);
        }

        static Expectation matches(String regex) {
            return new Expectation(Type.MATCHES, regex, 0);
        }
    }

    /**
     * 一条样本。
     *
     * @param id      唯一标识（断言失败时的定位信息）
     * @param rule    所属规则：table-boundary / fence-literal / inline-flanking / escape-literal
     * @param note    说明
     * @param pending {@code true} 表示当前实现尚未支持，只记录目标行为、不参与断言
     * @param input   输入 Markdown
     * @param cli     CLI 侧断言
     */
    public record Sample(String id, String rule, String note, boolean pending, String input,
                         List<Expectation> cli) {
    }

    private static final List<Sample> ALL = List.of(
            // ------------------------------------------------ 规则 1：表格边界（#346）
            new Sample("table-blank-line-splits", "table-boundary",
                    "空行终止表格：两张表各自成块，列宽独立", false,
                    "| a | b |\n|---|---|\n| 1 | 2 |\n\n| c |\n|---|\n| 3 |\n",
                    List.of(Expectation.count("┌", 2), Expectation.count("┘", 2))),

            new Sample("table-blank-line-kept", "table-boundary",
                    "表格与后文之间保留一个空行（与表格前的空行对称）", false,
                    "| a |\n|---|\n| 1 |\n\n段落\n",
                    List.of(Expectation.matches("└[^\\n]*\\n\\n段落"))),

            new Sample("table-direct-body", "table-boundary",
                    "表格后直接接正文（无空行）仍正常结算，不吞后文", false,
                    "| a |\n|---|\n| 1 |\n紧跟正文\n",
                    List.of(Expectation.count("┌", 1), Expectation.contains("紧跟正文"))),

            // ------------------------------------------------ 规则 2：围栏内不解析
            new Sample("fence-keeps-emphasis", "fence-literal",
                    "围栏内的强调标记按字面量输出", false,
                    "```\n**not bold**\n*not italic*\n```\n",
                    List.of(Expectation.contains("**not bold**"))),

            new Sample("fence-keeps-table", "fence-literal",
                    "围栏内的管道行不被当成表格", false,
                    "```\n| a | b |\n```\n",
                    List.of(Expectation.notContains("┌"))),

            new Sample("fence-keeps-strike", "fence-literal",
                    "围栏内的删除线标记按字面量输出", false,
                    "```\n~~x~~\n```\n",
                    List.of(Expectation.contains("~~x~~"))),

            // ------------------------------------------------ 规则 3：行内定界符侧翼
            new Sample("multiplication-not-emphasis", "inline-flanking",
                    "开定界符后跟空白 ⇒ 不是强调", false,
                    "2 * 3 * 4\n",
                    List.of(Expectation.contains("2 * 3 * 4"))),

            new Sample("snake-case-not-emphasis", "inline-flanking",
                    "标识符里的下划线不构成强调", false,
                    "snake_case_name\n",
                    List.of(Expectation.contains("snake_case_name"))),

            new Sample("bold-drops-markers", "inline-flanking",
                    "粗体成立时去掉定界符", false,
                    "**粗体**\n",
                    List.of(Expectation.contains("粗体"), Expectation.notContains("**"))),

            new Sample("four-delimiters-literal", "inline-flanking",
                    "连续 4 个及以上定界符按字面量输出（用前后缀避开行首 `*` 的列表试探，见 #429）", false,
                    "前缀 ****粗**** 后缀\n",
                    List.of(Expectation.contains("****粗****"))),

            // ------------------------------------------------ 规则 4：反斜杠转义（当前未实现）
            new Sample("escaped-star", "escape-literal",
                    "反斜杠转义（S1.2 实现后启用）：\\*x\\* 应输出字面量 *x*", true,
                    "\\*x\\*\n",
                    List.of(Expectation.contains("*x*"), Expectation.notContains("\\*"))),

            new Sample("escaped-underscore", "escape-literal",
                    "反斜杠转义（S1.2 实现后启用）：\\_x\\_ 应输出字面量 _x_", true,
                    "\\_x\\_\n",
                    List.of(Expectation.contains("_x_"), Expectation.notContains("\\_"))),

            new Sample("escaped-backtick", "escape-literal",
                    "反斜杠转义（S1.2 实现后启用）：\\`x\\` 应输出字面量 `x`", true,
                    "\\`x\\`\n",
                    List.of(Expectation.contains("`x`"), Expectation.notContains("\\`")))
    );

    /**
     * 全部样本（含 pending）。
     *
     * @return 样本列表
     */
    public static List<Sample> all() {
        return ALL;
    }

    /**
     * 当前实现应当通过的样本（过滤掉 pending）。
     *
     * @return 样本列表
     */
    public static List<Sample> active() {
        return ALL.stream().filter(sample -> !sample.pending()).toList();
    }

    private MarkdownSemanticsSamples() {
    }
}
