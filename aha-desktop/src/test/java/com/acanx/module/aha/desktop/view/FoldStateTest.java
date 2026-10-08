package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FoldState} 测试：**折叠之后必须还能展开**。
 *
 * <p>这条是本类存在的理由。桌面端界面在 CI 与无显示的 WSL 上都跑不起来，
 * 如果把折叠逻辑写在视图里，「折叠后展不回来」这种事故只能在用户机器上发现。
 * 抽成纯状态机后，往返行为在无图形环境下也能被钉住。</p>
 *
 * @since 0.2.0
 */
class FoldStateTest {

    @Test
    void collapsedThenExpandedReturnsToInitialState() {
        FoldState fold = new FoldState("左栏", 220, true);

        assertThat(fold.toggle()).isFalse();
        assertThat(fold.isExpanded()).isFalse();
        assertThat(fold.contentWidth()).isZero();
        assertThat(fold.isContentVisible()).isFalse();
        assertThat(fold.isContentManaged()).isFalse();

        // 折叠之后必须能展开（用户鼠标点窄条按钮走的就是这里）
        assertThat(fold.toggle()).isTrue();
        assertThat(fold.isExpanded()).isTrue();
        assertThat(fold.contentWidth()).isEqualTo(220);
        assertThat(fold.isContentVisible()).isTrue();
        assertThat(fold.isContentManaged()).isTrue();
    }

    @Test
    void repeatedTogglingStaysConsistent() {
        FoldState fold = new FoldState("右栏", 280, false);

        for (int i = 0; i < 10; i++) {
            boolean expected = i % 2 == 0;
            assertThat(fold.toggle()).isEqualTo(expected);
            assertThat(fold.isExpanded()).isEqualTo(expected);
            assertThat(fold.contentWidth()).isEqualTo(expected ? 280 : 0);
        }
    }

    @Test
    void glyphAndTooltipTellHowToGetItBack() {
        FoldState fold = new FoldState("左栏", 220, true);

        assertThat(fold.glyph()).isEqualTo("‹");
        assertThat(fold.tooltip()).isEqualTo("折叠左栏");

        fold.toggle();
        // 折叠后，按钮必须明确告诉用户「可以展开」，而不只是一个含义不明的箭头
        assertThat(fold.glyph()).isEqualTo("›");
        assertThat(fold.tooltip()).isEqualTo("展开左栏");
    }

    @Test
    void rightPaneStartsCollapsedWhenNoRoundData() {
        FoldState right = new FoldState("右栏", 280,
                ShellLayout.rightPaneVisibleByDefault(false));
        assertThat(right.isExpanded()).isFalse();

        FoldState withData = new FoldState("右栏", 280,
                ShellLayout.rightPaneVisibleByDefault(true));
        assertThat(withData.isExpanded()).isTrue();
    }
}
