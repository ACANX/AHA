package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.cli.tty.TerminalCapabilities.ColorDepth;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StartupBanner} 测试。
 *
 * <p>终端宽度是唯一变量，因此这里把「宽度 → 版面」的契约钉死：宽终端并排且标志贴右边距、
 * 窄终端原样降级、宽度未知不做并排。同时钉住中文标签必须按<b>显示宽度</b>补位——
 * 按字符数补位会让取值列错开一列，这是启动信息块曾经真实存在过的缺陷。</p>
 *
 * @since 0.1.0
 */
class StartupBannerTest {

    private static final String ANSI = "\u001B\\[[0-9;]*m";

    private static final Style PLAIN = Style.of(false);

    /** 纯 ASCII 候选：完整 → 紧凑。 */
    private static final List<LogoArt> ASCII = List.of(
            StartupLogo.art(StartupLogo.Size.FULL, PLAIN),
            StartupLogo.art(StartupLogo.Size.COMPACT, PLAIN));

    private static LogoArt ascii(StartupLogo.Size size) {
        return StartupLogo.art(size, PLAIN);
    }

    private static String strip(String text) {
        return text.replaceAll(ANSI, "");
    }

    private static List<String> info() {
        return List.of(
                "  供应商   DeepSeek",
                "  模型     deepseek-chat",
                "  工作目录 E:\\repo");
    }

    private static int leftWidth(List<String> info) {
        return info.stream().mapToInt(TextWidth::width).max().orElse(0);
    }

    /**
     * 断言某一档标志被并排到右边距。
     *
     * @param lines  组装结果
     * @param info   左栏
     * @param total  终端宽度
     * @param size   期望使用的标志档位
     */
    private static void assertSideBySide(List<String> lines, List<String> info, int total,
            LogoArt art) {
        int left = leftWidth(info);
        int logoWidth = art.width();
        List<String> logo = art.lines();
        assertThat(lines).hasSize(Math.max(info.size(), logo.size()));
        for (int i = 0; i < logo.size(); i++) {
            String line = strip(lines.get(i));
            // 左栏内容一个字符都不能丢；标志比信息栏高时，多出来的行只有标志
            if (i < info.size()) {
                assertThat(line).startsWith(info.get(i));
            }
            // 标志行右对齐：短行的右边距更大，正好等于宽度差
            assertThat(line).endsWith(strip(logo.get(i)));
            assertThat(TextWidth.width(line))
                    .isEqualTo(total - StartupBanner.RIGHT_MARGIN
                            - (logoWidth - TextWidth.width(strip(logo.get(i)))));
        }
        for (int i = logo.size(); i < lines.size(); i++) {
            // 标志结束后只剩左栏原样输出，不补空白
            assertThat(lines.get(i)).isEqualTo(info.get(i));
        }
    }

    @Test
    void wideTerminalPlacesTheFullLogoAtTheRightMargin() {
        List<String> info = info();
        List<String> lines = StartupBanner.compose(info, 100, ASCII);
        assertSideBySide(lines, info, 100, ascii(StartupLogo.Size.FULL));
    }

    @Test
    void narrowTerminalKeepsTheInfoBlockUnchanged() {
        List<String> info = info();
        // 恰好差 1 列放不下紧凑标志（判定式：total >= 左栏 + 标志 + 间隔 + 右边距）
        int justTooNarrow = leftWidth(info) + StartupLogo.width(StartupLogo.Size.COMPACT)
                + StartupBanner.GAP + StartupBanner.RIGHT_MARGIN - 1;
        assertThat(StartupBanner.compose(info, justTooNarrow, ASCII)).isEqualTo(info);
        assertThat(StartupBanner.compose(info, 20, ASCII)).isEqualTo(info);
    }

    @Test
    void unknownWidthDisablesTheLogo() {
        // 非 TTY / 管道 / CI：宽度未知时不能瞎猜，否则标志会落到被截断的位置
        List<String> info = info();
        assertThat(StartupBanner.compose(info, 0, ASCII)).isEqualTo(info);
        assertThat(StartupBanner.compose(info, -1, ASCII)).isEqualTo(info);
    }

    @Test
    void emptyInfoProducesNothing() {
        assertThat(StartupBanner.compose(List.of(), 100, ASCII)).isEmpty();
        assertThat(StartupBanner.compose(null, 100, ASCII)).isEmpty();
    }

    @Test
    void fullLogoIsReplacedByTheCompactOneWhenItDoesNotFit() {
        List<String> info = info();
        int full = StartupLogo.width(StartupLogo.Size.FULL);
        int compact = StartupLogo.width(StartupLogo.Size.COMPACT);
        assertThat(full).isGreaterThan(compact);
        // 恰好放下紧凑档：完整档必然放不下
        int width = leftWidth(info) + compact + StartupBanner.GAP + StartupBanner.RIGHT_MARGIN;
        assertSideBySide(StartupBanner.compose(info, width, ASCII), info, width,
                ascii(StartupLogo.Size.COMPACT));
    }

    @Test
    void infoTallerThanTheLogoLeavesTheRestUnchanged() {
        List<String> info = new ArrayList<>();
        for (int i = 0; i < StartupLogo.height(StartupLogo.Size.FULL) + 2; i++) {
            info.add("  字段" + i + " 值" + i);
        }
        List<String> lines = StartupBanner.compose(info, 120, ASCII);
        assertSideBySide(lines, info, 120, ascii(StartupLogo.Size.FULL));
    }

    @Test
    void noLineCarriesTrailingWhitespace() {
        // 行尾留白没有意义，还会让重定向输出变脏
        for (String line : StartupBanner.compose(info(), 100, ASCII)) {
            assertThat(line).isEqualTo(line.stripTrailing());
        }
        for (String line : StartupBanner.compose(info(), 100, List.of(StartupLogo.art(StartupLogo.Size.FULL, Style.of(ColorDepth.ANSI256))))) {
            assertThat(line).isEqualTo(line.stripTrailing());
        }
    }

    @Test
    void colorlessStyleLeavesNoEscapeSequences() {
        for (String line : StartupBanner.compose(info(), 100, ASCII)) {
            assertThat(line).doesNotContain("\u001B[");
        }
        // 非 TTY 走的是 Style.of(false)，与「管道里混进转义序列」是同一个坑
        for (String line : StartupBanner.compose(info(), 100, null)) {
            assertThat(line).doesNotContain("\u001B[");
        }
    }

    @Test
    void labelsArePaddedByDisplayWidthSoValuesLineUp() {
        // 两个标签的字符数不同（3 与 4），显示宽度也不同（6 与 8）：
        // 用 printf("%-8s") 会按字符数补位，取值列于是差 1 列
        String shortLabel = StartupBanner.field("供应商", "v");
        String longLabel = StartupBanner.field("工作目录", "v");
        int shortPrefix = TextWidth.width(shortLabel.substring(0, shortLabel.indexOf('v')));
        int longPrefix = TextWidth.width(longLabel.substring(0, longLabel.indexOf('v')));
        assertThat(shortPrefix).isEqualTo(longPrefix);
        assertThat(shortPrefix).isEqualTo(StartupBanner.LABEL_WIDTH + 3);
    }

    @Test
    void fieldKeepsTheDocumentedShape() {
        assertThat(StartupBanner.field("供应商", "DeepSeek")).isEqualTo("  供应商   DeepSeek");
        assertThat(StartupBanner.field("工作目录", "E:\\repo")).isEqualTo("  工作目录 E:\\repo");
    }
}
