package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.cli.tty.StatusSurface;
import com.acanx.module.aha.cli.tty.TerminalCapabilities.ColorDepth;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StatusLine} 测试。
 *
 * <p>状态行不再直接写输出流，而是交给 {@link StatusSurface}——它在终端底部保留一行并支持
 * 原地重绘，所以这里断言的是「载体收到了什么」，不再关心 {@code \r} 之类的字符流细节。</p>
 *
 * @since 0.1.0
 */
class StatusLineTest {

    private final RecordingStatusSurface surface = new RecordingStatusSurface();

    @Test
    void disabledStatusPaintsNothing() {
        StatusLine status = new StatusLine(surface, false, true, 80, StatusBar.legacy("m"), Style.of(false));

        status.show("等待响应…");
        status.paint();

        assertThat(surface.painted()).isEmpty();
    }

    @Test
    void unknownWidthDisablesStatus() {
        // 宽度未知时无法判断是否会长出两行，宁可不显示
        StatusLine status = new StatusLine(surface, true, true, 0, StatusBar.legacy("m"), Style.of(false));

        status.show("等待响应…");

        assertThat(surface.painted()).isEmpty();
    }

    @Test
    void unavailableSurfaceDisablesStatus() {
        // 终端不支持滚动区域时载体是 NONE，此处不应有任何绘制
        StatusLine status = new StatusLine(StatusSurface.NONE, true, true, 80, StatusBar.legacy("m"), Style.of(false));

        status.show("等待响应…");
        status.paint();

        assertThat(surface.painted()).isEmpty();
    }

    @Test
    void showPaintsFramePhaseModelAndElapsed() {
        StatusLine status = new StatusLine(surface, true, true, 80, StatusBar.legacy("model-x"), Style.of(false));

        status.show("等待响应…");

        // 只看第一帧：定时器最迟 120ms 后就会追上第二帧，断言末帧会不稳
        assertThat(surface.painted()).isNotEmpty();
        // 状态行只回答「在干什么」：耗时与用量归工具区块的末行
        assertThat(surface.painted().get(0)).contains("⠋ 等待响应…");
    }

    @Test
    void asciiFramesAreUsedWhenUnicodeIsUnavailable() {
        StatusLine status = new StatusLine(surface, true, false, 40, StatusBar.legacy(null), Style.of(false));

        status.show("等待响应…");

        assertThat(surface.painted().get(0)).contains("| 等待响应…");
    }

    @Test
    void longTextIsTruncatedByDisplayWidth() {
        StatusLine status = new StatusLine(surface, true, true, 12, StatusBar.legacy("model"), Style.of(false));

        status.show("这是一个非常长的阶段描述文本");

        assertThat(TextWidth.width(surface.last())).isLessThanOrEqualTo(11);
    }

    @Test
    void idleShowsTheModelInsteadOfReleasingTheArea() {
        // 底部那一行兼着输入行下边框的职责，回合结束不能撤掉
        StatusLine status = new StatusLine(surface, true, true, 80, StatusBar.legacy("m"), Style.of(false));
        status.show("等待响应…");

        status.idle();

        assertThat(surface.hides()).isZero();
        assertThat(surface.staticLine()).contains("m");
    }

    @Test
    void idleShowsTheThreeItemsWhenStatusBarIsUsed() {
        // issue #330 / #353：模式 → 供应商 → 档位 → 模型 → 强度 → 目录 (分支)
        StatusBar bar = StatusBar.of(StatusBar.Mode.AUTO, "DeepSeek", "Standard",
                "model-x", "-", "/a/b/c/d", "dev");
        StatusLine status = new StatusLine(surface, true, true, 80, bar, Style.of(false));

        status.idle();

        assertThat(surface.staticLine()).contains("/a/b/c/d (dev)")
                .contains("自动 · DeepSeek · Standard · model-x · -");
    }

    @Test
    void modeSwitchIsVisibleOnTheNextIdleFrame() {
        StatusBar bar = StatusBar.of(StatusBar.Mode.AUTO, "p", "Pro", "m", "-", "/a/b/c/d", "");
        StatusLine status = new StatusLine(surface, true, true, 80, bar, Style.of(false));
        status.idle();

        bar.setMode(StatusBar.Mode.FULL);
        status.idle();

        assertThat(surface.staticLine()).contains("完全授权");
    }

    @Test
    void busyFrameKeepsThePhaseWhenWidthIsScarce() {
        StatusBar bar = StatusBar.of(StatusBar.Mode.AUTO, "DeepSeek", "Standard",
                "model-x", "-", "/a/b/c/d", "");
        StatusLine status = new StatusLine(surface, true, true, 20, bar, Style.of(false));

        status.show("等待响应…");

        assertThat(surface.painted().get(0)).contains("⠋ 等待响应…");
    }

    @Test
    void idleWithoutShowStillShowsIdleText() {
        StatusLine status = new StatusLine(surface, true, true, 80, StatusBar.legacy("m"), Style.of(false));

        status.idle();

        assertThat(surface.staticLine()).contains("m");
    }

    @Test
    void repaintAdvancesTheFrame() {
        StatusLine status = new StatusLine(surface, true, true, 80, StatusBar.legacy("m"), Style.of(false));

        status.show("等待响应…");
        status.paint();

        assertThat(surface.painted()).hasSize(2);
        assertThat(surface.painted().get(0)).isNotEqualTo(surface.painted().get(1));
    }

    @Test
    void paintAfterIdleDoesNothing() {
        StatusLine status = new StatusLine(surface, true, true, 80, StatusBar.legacy("m"), Style.of(false));
        status.show("等待响应…");
        status.idle();
        int painted = surface.painted().size();

        status.paint();

        assertThat(surface.painted()).hasSize(painted);
    }
    @Test
    void generatingAndApprovalStatesHaveTheirOwnLabels() {
        StatusLine status = new StatusLine(surface, true, true, 80, StatusBar.legacy("m"),
                Style.of(false));

        status.runState(RunState.GENERATING, null);
        assertThat(surface.last()).contains("生成中…");

        status.runState(RunState.AWAITING_APPROVAL, null);
        assertThat(surface.last()).contains("等待审批…");
    }

    @Test
    void doneAndFailedAreVisibleTerminalStates() {
        StatusLine status = new StatusLine(surface, true, true, 80, StatusBar.legacy("m"),
                Style.of(false));

        status.finish(RunState.DONE, null);
        assertThat(surface.last()).contains("✓ 完成");

        status.finish(RunState.FAILED, "连接超时\n第二行不展示");
        assertThat(surface.last()).contains("✗ 失败 · 连接超时");
    }

    @Test
    void interruptedIsVisibleTerminalState() {
        StatusLine status = new StatusLine(surface, true, true, 80, StatusBar.legacy("m"),
                Style.of(false));

        status.finish(RunState.INTERRUPTED, null);

        assertThat(surface.last()).contains("⏹ 已中断");
    }

    @Test
    void fieldLineShowsSessionId() {
        StatusLine status = new StatusLine(surface, true, true, 80, StatusBar.legacy("m"),
                Style.of(false), "SESSION-1");

        status.idle();

        assertThat(surface.staticLine()).contains("SESSION-1");
    }

    @Test
    void surfaceAlwaysReceivesAFramedBorderLine() {
        StatusLine status = new StatusLine(surface, true, true, 40, StatusBar.legacy("model-x"),
                Style.of(ColorDepth.ANSI256));

        status.show("等待响应…");
        status.paint();
        String busy = surface.borders().get(0);

        status.idle();
        String idle = surface.borders().get(surface.borders().size() - 1);

        // 下边框是输入行的下边线，与上边线同色；空闲时不能消失，否则行数会收缩
        assertThat(busy).contains("\u001B[38;5;141m");
        assertThat(idle).contains("\u001B[38;5;141m").doesNotContain("⠋");
    }

    @Test
    void frameFallsBackToBrightMagentaOn16ColourTerminals() {
        StatusLine status = new StatusLine(surface, true, true, 40, StatusBar.legacy("m"), Style.of(ColorDepth.ANSI16));

        status.show("等待响应…");
        status.paint();

        assertThat(surface.borders().get(0)).contains("\u001B[95m");
    }

    @Test
    void borderIsNotStyledWhenColourIsOff() {
        StatusLine status = new StatusLine(surface, true, true, 40, StatusBar.legacy("m"), Style.of(false));

        status.show("等待响应…");
        status.paint();

        // 管道/重定向时不能往输出里塞转义序列
        assertThat(surface.borders().get(0)).doesNotContain("\u001B");
    }

}
