package com.acanx.module.aha.cli.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StatusBar} 测试：字段顺序、缺失占位、完整路径与超长中间省略、宽度压缩（issue #330 / #353）。
 *
 * @since 0.1.5
 */
class StatusBarTest {

    private static StatusBar sample() {
        return StatusBar.of(StatusBar.Mode.AUTO, "DeepSeek", "Standard", "model-x", "-",
                "/a/b/c/d", "dev");
    }

    @Test
    void textFollowsTheAgreedOrder() {
        // 模式 → 供应商 → 档位 → 模型 → 强度 → 目录 (分支)；路径不足 128 字符时完整展示
        StatusBar bar = StatusBar.of(StatusBar.Mode.AUTO, "DeepSeek", "Standard",
                "model-x", "high", "C:\\work\\AHA\\GitRepo\\ACANX", "dev");

        assertThat(bar.text(0)).isEqualTo(
                "自动 · DeepSeek · Standard · model-x · high · C:/work/AHA/GitRepo/ACANX (dev)");
    }

    @Test
    void missingProviderAndModelFallBackToDash() {
        StatusBar bar = StatusBar.of(StatusBar.Mode.STRICT, null, null, null, "", "/a/b/c/d", "");

        assertThat(bar.text(0)).isEqualTo("严格 · - · - · - · /a/b/c/d");
    }

    @Test
    void cwdIsOmittedWhenEmpty() {
        StatusBar bar = StatusBar.of(StatusBar.Mode.AUTO, "p", "Pro", "m", "-", "", "");

        assertThat(bar.text(0)).isEqualTo("自动 · p · Pro · m · -");
    }

    @Test
    void pathIsCompleteWhenBelowTheLimit() {
        // 完整路径优先：不做「保留末 N 层」的头部省略
        assertThat(StatusBar.shorten("/home/acanx/Agent/CNXNC/GitRepo/ACANX/AHA"))
                .isEqualTo("/home/acanx/Agent/CNXNC/GitRepo/ACANX/AHA");
        assertThat(StatusBar.shorten("/a/b")).isEqualTo("/a/b");
        assertThat(StatusBar.shorten("C:\\a\\b")).isEqualTo("C:/a/b");
        assertThat(StatusBar.shorten("")).isEqualTo("-");
    }

    @Test
    void longPathIsElidedInTheMiddle() {
        String longPath = "/" + "a".repeat(200);

        String out = StatusBar.shorten(longPath);

        assertThat(out).hasSize(StatusBar.MAX_PATH_CHARS);
        assertThat(out).contains("…");
        assertThat(out).startsWith("/aaa");
        assertThat(out).endsWith("aaa");
    }

    @Test
    void wideEnoughKeepsEveryField() {
        assertThat(sample().text(80))
                .isEqualTo("自动 · DeepSeek · Standard · model-x · - · /a/b/c/d (dev)");
    }

    @Test
    void narrowWidthDropsDirectoryThenEffortThenTier() {
        // 55 列：目录 + 分支先丢，强度与档位保留
        String middle = sample().text(55);
        assertThat(middle).doesNotContain("(dev)");
        assertThat(middle).contains("自动 · DeepSeek · Standard · model-x · -");

        // 35 列：强度与档位也丢，仍以「自动 · DeepSeek」开头
        String narrow = sample().text(35);
        assertThat(narrow).startsWith("自动 · DeepSeek");
        assertThat(narrow).doesNotContain("Standard");
        assertThat(TextWidth.width(narrow)).isLessThanOrEqualTo(34);
    }

    @Test
    void modeSwitchIsReflectedImmediately() {
        StatusBar bar = StatusBar.of(StatusBar.Mode.AUTO, "p", "Pro", "m", "-", "/a/b/c/d", "");

        bar.setMode(StatusBar.Mode.FULL);

        assertThat(bar.context().mode()).isEqualTo(StatusBar.Mode.FULL);
        assertThat(bar.text(0)).startsWith("完全授权");
    }

    @Test
    void legacyKeepsTheSingleValue() {
        assertThat(StatusBar.legacy("model-x").text(80)).isEqualTo("model-x");
    }

    @Test
    void modeLabelsAreTheThreeAgreedNames() {
        assertThat(StatusBar.Mode.AUTO.label()).isEqualTo("自动");
        assertThat(StatusBar.Mode.FULL.label()).isEqualTo("完全授权");
        assertThat(StatusBar.Mode.STRICT.label()).isEqualTo("严格");
    }
}
