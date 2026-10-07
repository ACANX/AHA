package com.acanx.module.aha.cli.render;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TableRenderer} 测试。
 *
 * <p>表格渲染的价值全在「对齐」上——行列错位比不渲染更糟，因此断言集中在
 * 各列宽度一致、行数与内容完整、以及超宽时压缩而不是折行。</p>
 *
 * @since 0.1.0
 */
class TableRendererTest {

    private static final Style PLAIN = Style.of(false);

    /**
     * 去掉 SGR 序列，得到纯文本。
     *
     * @param text 文本
     * @return 纯文本
     */
    private static String strip(String text) {
        return text.replaceAll("\u001B\\[[0-9;]*m", "");
    }

    @Test
    void rendersAlignedBox() {
        String out = TableRenderer.render(
                List.of("| 字段 | 说明 |", "|---|---|", "| Name | 名称 |"), PLAIN, 0, true);

        String[] lines = out.split("\n");
        assertThat(lines).hasSize(5);
        assertThat(lines[0]).startsWith("┌").endsWith("┐");
        assertThat(lines[1]).contains("字段").contains("说明");
        assertThat(lines[2]).startsWith("├").endsWith("┤");
        assertThat(lines[3]).contains("Name").contains("名称");
        assertThat(lines[4]).startsWith("└").endsWith("┘");

        // 每一行的显示宽度必须一致，否则终端里就是歪的
        int width = TextWidth.width(lines[0]);
        for (String line : lines) {
            assertThat(TextWidth.width(line)).isEqualTo(width);
        }
    }

    @Test
    void degradesToAsciiWithoutBoxCharacters() {
        String out = TableRenderer.render(
                List.of("| a | b |", "|---|---|", "| 1 | 2 |"), PLAIN, 0, false);

        assertThat(out).contains("+---").contains("| a |").doesNotContain("┌").doesNotContain("│");
    }

    @Test
    void honoursAlignmentFromSeparator() {
        // 表头比内容宽，才留得出可比较的填充空格
        String right = TableRenderer.render(
                List.of("| 列名 |", "|---:|", "| cc |"), PLAIN, 0, true).split("\n")[3];
        String left = TableRenderer.render(
                List.of("| 列名 |", "|:---|", "| dd |"), PLAIN, 0, true).split("\n")[3];

        // 同一列宽下，右对齐的内容左侧空格更多（内容贴右边）
        int rightLeading = right.indexOf("c") - right.indexOf("│");
        int leftLeading = left.indexOf("d") - left.indexOf("│");
        assertThat(rightLeading).isGreaterThan(leftLeading);
        // 右对齐贴右边、左对齐贴左边
        assertThat(right).endsWith("cc │");
        assertThat(left).contains("│ dd");
    }

    @Test
    void shrinksColumnsInsteadOfWrapping() {
        String long1 = "x".repeat(80);
        String out = TableRenderer.render(
                List.of("| A | B |", "|---|---|", "| " + long1 + " | y |"), PLAIN, 40, true);

        for (String line : out.split("\n")) {
            // 表格一旦折行，行列对应就彻底乱了，宁可挤也不能超宽
            assertThat(TextWidth.width(line)).isLessThanOrEqualTo(40);
        }
    }

    @Test
    void padsRowsThatHaveFewerCells() {
        String out = TableRenderer.render(
                List.of("| A | B | C |", "|---|---|---|", "| 1 |"), PLAIN, 0, true);

        // 缺列不能导致框线对不齐
        String[] lines = out.split("\n");
        int width = TextWidth.width(lines[0]);
        for (String line : lines) {
            assertThat(TextWidth.width(line)).isEqualTo(width);
        }
    }

    @Test
    void trailingBlankLineDoesNotAddAnEmptyRow() {
        String out = TableRenderer.render(
                List.of("| a |", "|---|", "| 1 |", ""), PLAIN, 0, true);

        // Markdown 表格后常跟一个换行，不能因此多出一整行空框
        assertThat(out.split("\n")).hasSize(5);
    }

    @Test
    void inlineStylesInsideCellsAreRendered() {
        Style style = Style.of(true);

        String out = TableRenderer.render(
                List.of("| 字段 | 说明 |", "|---|---|", "| `ModelWrite` | **写入策略** |"),
                style, 0, true);

        // 单元格里的行内代码与强调要解析：此前原样输出反引号和星号
        assertThat(out).contains("\u001B[32mModelWrite\u001B[0m");
        assertThat(out).contains("\u001B[1m写入策略\u001B[0m");
        assertThat(out).doesNotContain("`").doesNotContain("**");
    }

    @Test
    void inlineStylesDoNotBreakAlignment() {
        // 上色后仍必须按显示宽度对齐：转义序列不能被当成字符计数
        String out = TableRenderer.render(
                List.of("| A | B |", "|---|---|", "| `x` | **yy** |"),
                Style.of(true), 0, true);

        String[] lines = out.split("\n");
        // 基准宽度必须先剥掉 SGR：转义序列不占显示列，直接数会把宽度算大
        int width = TextWidth.width(strip(lines[0]));
        for (String line : lines) {
            assertThat(TextWidth.width(strip(line))).isEqualTo(width);
        }
    }

    @Test
    void inlineStylesSurviveColumnShrinking() {
        String long1 = "`" + "x".repeat(60) + "`";
        String out = TableRenderer.render(
                List.of("| A |", "|---|", "| " + long1 + " |"), Style.of(true), 30, true);

        // 撑宽时退回纯文本，宁可丢样式也不能让表格错位
        for (String line : out.split("\n")) {
            assertThat(TextWidth.width(strip(line))).isLessThanOrEqualTo(30);
        }
    }

    @Test
    void ignoresEmptyInput() {
        assertThat(TableRenderer.render(List.of(), PLAIN, 0, true)).isEmpty();
        // 全是空单元格的「表格」没有任何信息，画出来只会是噪音
        assertThat(TableRenderer.render(List.of("|   |   |"), PLAIN, 0, true)).isEmpty();
    }
}
