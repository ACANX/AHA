package com.acanx.module.aha.cli.render;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StatusBar} 测试：字段顺序、缺失占位、目录截断与宽度压缩（issue #330 / #353）。
 *
 * @since 0.1.5
 */
class StatusBarTest {

    private static StatusBar sample() {
        return StatusBar.of(StatusBar.Mode.AUTO, "deepseek", "Standard", "deepseek-chat", "-",
                "/a/b/c/d", "dev");
    }

    @Test
    void textFollowsTheAgreedOrder() {
        // 模式 → 供应商 → 档位 → 模型 → 强度 → 目录 (分支)
        StatusBar bar = StatusBar.of(StatusBar.Mode.AUTO, "deepseek", "Standard",
                "deepseek-chat", "high", "C:\\work\\AHA\\GitRepo\\ACANX", "dev");

        assertThat(bar.text(0)).isEqualTo(
                "自动 · deepseek · Standard · deepseek-chat · high · …\\AHA\\GitRepo\\ACANX (dev)");
    }

    @Test
    void missingProviderAndModelFallBackToDash() {
        StatusBar bar = StatusBar.of(StatusBar.Mode.STRICT, null, null, null, "", "/a/b/c/d", "");

        assertThat(bar.text(0)).isEqualTo("严格 · - · - · - · …/b/c/d");
    }

    @Test
    void cwdIsOmittedWhenEmpty() {
        StatusBar bar = StatusBar.of(StatusBar.Mode.AUTO, "p", "Pro", "m", "-", "", "");

        assertThat(bar.text(0)).isEqualTo("自动 · p · Pro · m · -");
    }

    @Test
    void shortCwdKeepsTheTailLevels() {
        // 保留末 3 层：GitRepo / ACANX / AHA
        assertThat(StatusBar.shorten("/home/acanx/Agent/CNXNC/GitRepo/ACANX/AHA", 3))
                .isEqualTo("…/GitRepo/ACANX/AHA");
        assertThat(StatusBar.shorten("/a/b", 3)).isEqualTo("/a/b");
        assertThat(StatusBar.shorten("", 3)).isEqualTo("-");
        assertThat(StatusBar.shorten("C:\\a\\b\\c\\d", 2)).isEqualTo("…\\c\\d");
    }

    @Test
    void wideEnoughKeepsEveryField() {
        assertThat(sample().text(70))
                .isEqualTo("自动 · deepseek · Standard · deepseek-chat · - · …/b/c/d (dev)");
    }

    @Test
    void narrowWidthDropsDirectoryThenEffortThenTier() {
        // 45 列：目录 + 分支先丢，强度随后丢，模式/供应商/档位/模型保留
        String middle = sample().text(45);
        assertThat(middle).doesNotContain("…");
        assertThat(middle).doesNotContain("(dev)");
        assertThat(middle).doesNotContain(" · -");
        assertThat(middle).contains("自动 · deepseek · Standard · deepseek-chat");

        // 30 列：档位也丢，仍以「自动 · deepseek」开头
        String narrow = sample().text(30);
        assertThat(narrow).startsWith("自动 · deepseek");
        assertThat(narrow).doesNotContain("Standard");
        assertThat(TextWidth.width(narrow)).isLessThanOrEqualTo(29);
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
        assertThat(StatusBar.legacy("deepseek-chat").text(80)).isEqualTo("deepseek-chat");
    }

    @Test
    void modeLabelsAreTheThreeAgreedNames() {
        assertThat(StatusBar.Mode.AUTO.label()).isEqualTo("自动");
        assertThat(StatusBar.Mode.FULL.label()).isEqualTo("完全授权");
        assertThat(StatusBar.Mode.STRICT.label()).isEqualTo("严格");
    }
}
