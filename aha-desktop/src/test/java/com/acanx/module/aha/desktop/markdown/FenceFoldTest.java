package com.acanx.module.aha.desktop.markdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link FenceFold} 测试（issue #422）：阈值边界 29 / 30 / 31。
 *
 * @since 0.2.0
 */
class FenceFoldTest {

    @Test
    void belowAndAtThresholdIsNotCollapsible() {
        FenceFold at29 = new FenceFold(29);
        FenceFold at30 = new FenceFold(30);

        assertThat(at29.collapsible()).isFalse();
        assertThat(at29.visibleLines()).isEqualTo(29);
        assertThat(at29.hiddenLines()).isZero();

        assertThat(at30.collapsible()).isFalse();
        assertThat(at30.visibleLines()).isEqualTo(30);
        assertThat(at30.isExpanded()).isTrue();
    }

    @Test
    void aboveThresholdStartsCollapsed() {
        FenceFold at31 = new FenceFold(31);

        assertThat(at31.collapsible()).isTrue();
        assertThat(at31.isExpanded()).isFalse();
        assertThat(at31.visibleLines()).isEqualTo(30);
        assertThat(at31.hiddenLines()).isEqualTo(1);
    }

    @Test
    void toggleExpandsAndCollapses() {
        FenceFold fold = new FenceFold(100);

        assertThat(fold.toggle()).isTrue();
        assertThat(fold.visibleLines()).isEqualTo(100);
        assertThat(fold.hiddenLines()).isZero();

        assertThat(fold.toggle()).isFalse();
        assertThat(fold.visibleLines()).isEqualTo(30);
        assertThat(fold.hiddenLines()).isEqualTo(70);
    }

    @Test
    void customThresholdAndInvalidArguments() {
        assertThat(new FenceFold(10, 5).visibleLines()).isEqualTo(5);
        assertThatThrownBy(() -> new FenceFold(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FenceFold(3, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
