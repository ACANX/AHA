package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.cli.tty.TerminalCapabilities.ColorDepth;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StartupLogo} 测试。
 *
 * <p>标志是用户最先看到的东西，因此断言集中在三件「调样式时不能破」的契约上：
 * 必须是纯 ASCII（否则非 UTF-8 控制台会整片退化成 {@code ?}）、行宽不能超过对外宣告的宽度
 * （否则并排会挤到下一行）、着色前后文本必须可还原。</p>
 *
 * @since 0.1.0
 */
class StartupLogoTest {

    private static final String ANSI = "\u001B\\[[0-9;]*m";

    private static String strip(String text) {
        return text.replaceAll(ANSI, "");
    }

    @Test
    void artIsPureAsciiSoNoCodePageCanBreakIt() {
        // 非 UTF-8 代码页下，Unicode 方块字符会变成 '?'——整个标志会烂成一排问号
        for (StartupLogo.Size size : StartupLogo.Size.values()) {
            for (StartupLogo.Row row : StartupLogo.rows(size)) {
                assertThat(row.text()).as("%s 必须为纯 ASCII", size)
                        .matches("[\\x20-\\x7E]*");
            }
        }
    }

    @Test
    void noRowExceedsTheDeclaredWidth() {
        for (StartupLogo.Size size : StartupLogo.Size.values()) {
            int width = StartupLogo.width(size);
            assertThat(width).isPositive();
            for (StartupLogo.Row row : StartupLogo.rows(size)) {
                // 超出宣告宽度会让 StartupBanner 的右对齐算错，标志被挤到下一行
                assertThat(row.text().length()).isLessThanOrEqualTo(width);
            }
            // 至少要有一行真的达到该宽度，否则 width() 报的是虚高的值
            assertThat(StartupLogo.rows(size))
                    .anyMatch(row -> row.text().length() == width);
        }
    }

    @Test
    void declaredWidthMatchesTheLongestRow() {
        for (StartupLogo.Size size : StartupLogo.Size.values()) {
            int longest = StartupLogo.rows(size).stream()
                    .mapToInt(row -> row.text().length()).max().orElse(0);
            // 纯 ASCII 下，长度即显示宽度
            assertThat(StartupLogo.width(size)).isEqualTo(longest);
        }
    }

    @Test
    void compactIsSmallerThanFullSoItCanServeAsADowngrade() {
        assertThat(StartupLogo.width(StartupLogo.Size.COMPACT))
                .isLessThan(StartupLogo.width(StartupLogo.Size.FULL));
        assertThat(StartupLogo.height(StartupLogo.Size.COMPACT))
                .isLessThan(StartupLogo.height(StartupLogo.Size.FULL));
    }

    @Test
    void heightMatchesTheRowCount() {
        for (StartupLogo.Size size : StartupLogo.Size.values()) {
            assertThat(StartupLogo.height(size)).isEqualTo(StartupLogo.rows(size).size());
        }
    }

    @Test
    void styledWithoutColorReturnsTheRawArt() {
        List<StartupLogo.Row> rows = StartupLogo.rows(StartupLogo.Size.FULL);
        List<String> styled = StartupLogo.styled(StartupLogo.Size.FULL, Style.of(false));
        assertThat(styled).containsExactlyElementsOf(rows.stream()
                .map(StartupLogo.Row::text).toList());
    }

    @Test
    void styledWithColorPaintsEveryRowAndStripsBackToRaw() {
        List<StartupLogo.Row> rows = StartupLogo.rows(StartupLogo.Size.FULL);
        List<String> styled = StartupLogo.styled(StartupLogo.Size.FULL,
                Style.of(ColorDepth.ANSI256));
        assertThat(styled).hasSameSizeAs(rows);
        for (int i = 0; i < rows.size(); i++) {
            assertThat(styled.get(i)).contains("\u001B[");
            // 着色只加转义序列，不改文本：去掉 SGR 必须逐字回到原文
            assertThat(strip(styled.get(i))).isEqualTo(rows.get(i).text());
        }
    }

    @Test
    void raysBulbAndBaseUseDifferentColors() {
        Style style = Style.of(ColorDepth.ANSI256);
        // 纯 ASCII 图形本身没有明暗，层次全靠色阶拉开
        assertThat(style.logoRay("x")).isNotEqualTo(style.logoBulb("x"));
        assertThat(style.logoBulb("x")).isNotEqualTo(style.logoBase("x"));
        assertThat(style.logoBase("x")).isNotEqualTo(style.logoWord("x"));
    }
}
