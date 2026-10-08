package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ShellLayout} 测试：把设计文档里的尺寸与规则钉住。
 *
 * <p>数值来自 {@code GUIDesign.md} 第 2 节的形态总览表。改设计必须同时改这里，
 * 否则「文档说 220px、代码写 200px」会无声发生。</p>
 *
 * @since 0.2.0
 */
class ShellLayoutTest {

    @Test
    void columnSizesMatchDesign() {
        assertThat(ShellLayout.LEFT_WIDTH).isEqualTo(220);
        assertThat(ShellLayout.RIGHT_WIDTH).isEqualTo(280);
        assertThat(ShellLayout.STATUS_BAR_HEIGHT).isEqualTo(24);
        assertThat(ShellLayout.GAP).isEqualTo(8);
    }

    @Test
    void collapsedPaneTakesNoContentSpace() {
        assertThat(ShellLayout.leftContentWidth(true)).isEqualTo(ShellLayout.LEFT_WIDTH);
        assertThat(ShellLayout.leftContentWidth(false)).isZero();
        assertThat(ShellLayout.rightContentWidth(true)).isEqualTo(ShellLayout.RIGHT_WIDTH);
        assertThat(ShellLayout.rightContentWidth(false)).isZero();
    }

    @Test
    void toggleStripRemainsWhenCollapsed() {
        // 折叠后不能把整列藏没：窄条是鼠标用户唯一的展开入口
        assertThat(ShellLayout.TOGGLE_STRIP_WIDTH).isPositive();
    }

    @Test
    void rightPaneFollowsRoundPresence() {
        assertThat(ShellLayout.rightPaneVisibleByDefault(false)).isFalse();
        assertThat(ShellLayout.rightPaneVisibleByDefault(true)).isTrue();
    }
}
