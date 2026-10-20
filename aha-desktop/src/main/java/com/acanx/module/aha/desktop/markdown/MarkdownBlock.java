package com.acanx.module.aha.desktop.markdown;

import java.util.List;

/**
 * 桌面端 Markdown 的块级 AST（issue #413 / tracking #408 S1.1）。
 *
 * <p>只描述**结构**，不背任何 JavaFX 类型：解析器因此能在无头环境单测，
 * 渲染器（S1.5）再把这里映射成 {@code TextFlow} / {@code GridPane}。
 * 与 CLI 的流式渲染不同，桌面端拿到的是全文，所以这里做的是「一次性解析」，不是状态机推进。</p>
 *
 * <p>行内标记（粗体 / 代码 / 链接…）本阶段**保留原文**，由 S1.2 在 {@link Paragraph}、
 * {@link ListItem}、表头/单元格、{@link Heading} 的文本上再做行内解析。</p>
 *
 * @since 0.2.0
 */
public sealed interface MarkdownBlock {

    /**
     * 标题。
     *
     * @param level 级别（1–6）
     * @param text  标题文本（不含 `#` 标记）
     */
    record Heading(int level, String text) implements MarkdownBlock {
    }

    /**
     * 段落：连续非空行，行间以 {@code \n} 分隔（软换行）。
     *
     * @param text 段落文本
     */
    record Paragraph(String text) implements MarkdownBlock {
    }

    /**
     * 分隔线。
     */
    record Rule() implements MarkdownBlock {
    }

    /**
     * 围栏代码块。
     *
     * @param language 语言标签（可为空串）
     * @param content  代码内容（不含围栏行）
     */
    record Fence(String language, String content) implements MarkdownBlock {
    }

    /**
     * 列表（有序 / 无序）。
     *
     * @param ordered 是否有序
     * @param start   有序列表的起始号（无序列表为 1）
     * @param items   列表项（按出现顺序，含缩进）
     */
    record ListBlock(boolean ordered, int start, List<ListItem> items) implements MarkdownBlock {
    }

    /**
     * 一个列表项。
     *
     * <p>嵌套用 {@code indent} 表示层级，渲染时按相邻缩进分组（S1.3），
     * 不在 AST 里做递归嵌套——扁平结构更容易断言，也避免缩进异常时构造出深树。</p>
     *
     * @param indent 行首空格数
     * @param text   文本
     */
    record ListItem(int indent, String text) {
    }

    /**
     * 引用：内部可以再包含任意块（含嵌套引用）。
     *
     * @param blocks 引用内容
     */
    record Quote(List<MarkdownBlock> blocks) implements MarkdownBlock {
    }

    /**
     * 表格。
     *
     * @param header 表头单元格
     * @param aligns 每列对齐
     * @param rows   数据行（每行按列补齐）
     */
    record TableBlock(List<String> header, List<Align> aligns, List<List<String>> rows)
            implements MarkdownBlock {
    }

    /**
     * 列对齐。
     */
    enum Align {
        /** 默认左对齐。 */
        LEFT,
        /** 居中（`:--:`）。 */
        CENTER,
        /** 右对齐（`--:`）。 */
        RIGHT
    }
}
