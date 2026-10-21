package com.acanx.module.aha.desktop.markdown;

import java.util.List;

/**
 * 行内 Markdown 的 AST（issue #414 / tracking #408 S1.2）。
 *
 * <p>与 {@link MarkdownBlock} 一样不含 JavaFX 类型：行内解析的误判（`2 * 3 * 4`、
 * `snake_case_name`、未闭合定界符）值得用单测钉死，而渲染只是把结果映射成 {@code Text}。</p>
 *
 * <p>嵌套用 children 表示（`**粗 *斜* 体**`），递归深度在解析器里限死（4 层），
 * 避免构造输入把栈打穿。</p>
 *
 * @since 0.2.0
 */
public sealed interface MarkdownInline {

    /**
     * 纯文本。
     *
     * @param text 文本
     */
    record Text(String text) implements MarkdownInline {
    }

    /**
     * 行内代码（内容不再解析）。
     *
     * @param text 代码文本
     */
    record Code(String text) implements MarkdownInline {
    }

    /**
     * 粗体。
     *
     * @param children 内部行内元素
     */
    record Strong(List<MarkdownInline> children) implements MarkdownInline {
    }

    /**
     * 斜体。
     *
     * @param children 内部行内元素
     */
    record Em(List<MarkdownInline> children) implements MarkdownInline {
    }

    /**
     * 删除线。
     *
     * @param children 内部行内元素
     */
    record Strike(List<MarkdownInline> children) implements MarkdownInline {
    }

    /**
     * 链接。
     *
     * @param text 显示文本
     * @param url  目标地址
     */
    record Link(String text, String url) implements MarkdownInline {
    }

    /**
     * 图片（issue #423 / S2.5）。
     *
     * <p>解析阶段只记录 alt 与地址，**不加载**：加载与否由渲染器按白名单与用户点击决定，
     * 这样「远程图片不点不请求」在 AST 层就是成立的，不依赖渲染器自觉。</p>
     *
     * @param alt 替代文本
     * @param url 图片地址
     */
    record Image(String alt, String url) implements MarkdownInline {
    }

    /**
     * 换行（issue #424 / S3.1）：来自白名单 HTML 的 {@code <br>}。
     *
     * <p>表格单元格在 GFM 里写不进真实换行，{@code <br>} 是唯一可用的换行写法；
     * 白名单外的一切标签都不产生节点，按字面量留在 {@link Text} 里。</p>
     */
    record Break() implements MarkdownInline {
    }

    /**
     * 白名单 HTML（issue #424 / S3.1）。
     *
     * <p>只支持 {@code sub} 与 {@code sup}，渲染为缩小字号 + 基线偏移。
     * 其余标签（{@code <script>}、{@code <span>}…）**不进 AST**，解析器把它们退化成
     * 普通文本，因此内容不会被吞掉，也不会被执行或套样式。</p>
     *
     * @param tag      标签名（小写，仅 {@code sub} / {@code sup}）
     * @param children 内部行内元素
     */
    record Html(String tag, List<MarkdownInline> children) implements MarkdownInline {
    }
}
