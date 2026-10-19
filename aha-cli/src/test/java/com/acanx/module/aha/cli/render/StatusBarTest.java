package com.acanx.module.aha.cli.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StatusBar} 测试：静态三项的文案、目录截断与宽度压缩（issue #330）。
 *
 * @since 0.1.5
 */
class StatusBarTest {

    @Test
    void textContainsModeModelTierAndShortCwd() {
        StatusBar bar = StatusBar.of("deepseek-chat", "Standard",
                "C:\\work\\AHA\\GitRepo\\ACANX", StatusBar.Mode.AUTO);

        assertThat(bar.text(0))
                .isEqualTo("自动 · deepseek-chat/Standard · …\\AHA\\GitRepo\\ACANX");
    }

    @Test
    void shortCwdKeepsTheTailLevels() {
        assertThat(StatusBar.shorten("/home/acanx/Agent/CNXNC/GitRepo/ACANX/AHA", 3))
                .isEqualTo("…/CNXNC/GitRepo/ACANX/AHA");
        assertThat(StatusBar.shorten("/a/b", 3)).isEqualTo("/a/b");
        assertThat(StatusBar.shorten("", 3)).isEqualTo("-");
        assertThat(StatusBar.shorten("C:\\a\\b\\c\\d", 2)).isEqualTo("…\\c\\d");
    }

    @Test
    void emptyModelFallsBackToDash() {
        assertThat(StatusBar.of(null, null, "/a/b/c/d", StatusBar.Mode.STRICT).text(0))
                .isEqualTo("严格 · - · …/b/c/d");
    }

    @Test
    void narrowWidthKeepsModeAndDropsDirectoryFirst() {
        StatusBar bar = StatusBar.of("deepseek-chat", "Standard", "/a/b/c/d",
                StatusBar.Mode.FULL);

        String text = bar.text(24);

        assertThat(text).startsWith("完全授权");
        assertThat(TextWidth.width(text)).isLessThanOrEqualTo(23);
    }

    @Test
    void modeSwitchIsReflectedImmediately() {
        StatusBar bar = StatusBar.of("m", "", "/a/b/c/d", StatusBar.Mode.AUTO);

        bar.setMode(StatusBar.Mode.FULL);

        assertThat(bar.context().mode()).isEqualTo(StatusBar.Mode.FULL);
        assertThat(bar.text(0)).startsWith("完全授权");
    }

    @Test
    void legacyKeepsTheSingleValue() {
        assertThat(StatusBar.legacy("deepseek-chat").text(80)).isEqualTo("deepseek-chat");
    }

    @Test
    void modeLabelsAreTheThreeAgreedNames() {
        assertThat(StatusBar.Mode.AUTO.label()).isEqualTo("自动");
        assertThat(StatusBar.Mode.FULL.label()).isEqualTo("完全授权");
        assertThat(StatusBar.Mode.STRICT.label()).isEqualTo("严格");
    }
}
