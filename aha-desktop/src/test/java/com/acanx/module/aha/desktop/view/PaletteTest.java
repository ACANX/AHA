package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.common.tool.ToolKind;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Palette} 测试：两套主题的配色与语义映射。
 *
 * <p>暗色的值来自 {@code GUIDesign.md} 第 3.1 节；亮色是另一张表，同样由测试钉住——
 * 「亮色主题只是把暗色调亮」这种退化会在这里被抓住（对比度不够的颜色上不了白底）。</p>
 *
 * @since 0.2.0
 */
class PaletteTest {

    @AfterEach
    void restoreDark() {
        // Palette 的颜色是可变静态字段（见其 Javadoc），测试必须自己收尾，
        // 否则会把主题泄漏给同一 JVM 里的其它测试
        Palette.setTheme(Theme.DARK);
    }

    @Test
    void darkSemanticColorsMatchDesignTable() {
        Palette.setTheme(Theme.DARK);

        assertThat(Palette.current()).isEqualTo(Theme.DARK);
        assertThat(Palette.READ).isEqualTo("#87AFD7");
        assertThat(Palette.WRITE).isEqualTo("#D7AF5F");
        assertThat(Palette.EXEC).isEqualTo("#FF875F");
        assertThat(Palette.NETWORK).isEqualTo("#5FD7D7");
        assertThat(Palette.OTHER).isEqualTo("#BCBCBC");
        assertThat(Palette.SUCCESS).isEqualTo("#7FD37F");
        assertThat(Palette.FAILURE).isEqualTo("#FF5F5F");
        assertThat(Palette.BLOCK_BACKGROUND).isEqualTo("#303030");
    }

    @Test
    void lightThemeIsAReadableTableNotTheDarkOne() {
        Palette.setTheme(Theme.LIGHT);

        assertThat(Palette.current()).isEqualTo(Theme.LIGHT);
        assertThat(Palette.FOREGROUND).as("亮底上必须是深色文字").isEqualTo("#1F1F1F");
        assertThat(Palette.BLOCK_BACKGROUND).isEqualTo("#F0F0F0");
        assertThat(Palette.FAILURE).as("高饱和红在白底上对比度不足，必须换深色版")
                .isNotEqualTo("#FF5F5F");
        assertThat(Palette.READ).isNotEqualTo("#87AFD7");
    }

    @Test
    void allColorsAreSixDigitHexInBothThemes() {
        Palette.setTheme(Theme.DARK);
        assertThat(Palette.READ).matches("#[0-9A-F]{6}");
        assertThat(Palette.FAILURE).matches("#[0-9A-F]{6}");
        assertThat(Palette.theme()).contains(Palette.FOREGROUND).contains(Palette.BASE);

        Palette.setTheme(Theme.LIGHT);
        assertThat(Palette.READ).matches("#[0-9A-F]{6}");
        assertThat(Palette.FAILURE).matches("#[0-9A-F]{6}");
        assertThat(Palette.theme()).contains(Palette.FOREGROUND).contains(Palette.BASE);
    }

    @Test
    void themeStyleCarriesTheCurrentPalette() {
        Palette.setTheme(Theme.DARK);
        assertThat(Palette.theme()).contains("-fx-base: #1E1E1E").contains("-fx-accent: #AF87FF");

        Palette.setTheme(Theme.LIGHT);
        assertThat(Palette.theme()).contains("-fx-base: #F4F4F4").contains("-fx-accent: #6A3FD0");
    }

    @Test
    void themeStylePinsTextColorsInsteadOfRelyingOnLadderLookup() {
        Palette.setTheme(Theme.DARK);
        assertThat(Palette.theme())
                .as("文字色必须显式钉死：不能依赖 modena 的 ladder(-fx-base) 推导")
                .contains("-fx-text-base-color: #E4E4E4")
                .contains("-fx-text-background-color: #E4E4E4");

        Palette.setTheme(Theme.LIGHT);
        assertThat(Palette.theme())
                .contains("-fx-text-base-color: #1F1F1F")
                .contains("-fx-text-background-color: #1F1F1F");
    }

    @Test
    void dialogThemeCarriesAnExplicitBackground() {
        Palette.setTheme(Theme.LIGHT);
        assertThat(Palette.dialogTheme())
                .as("对话框不能再依赖 .dialog-pane 的 -fx-background 查表")
                .contains("-fx-background-color: #F4F4F4");

        Palette.setTheme(Theme.DARK);
        assertThat(Palette.dialogTheme()).contains("-fx-background-color: #1E1E1E");
    }

    @Test
    void themeStyleNeverUsesCssMathFunctions() {
        // 原生镜像下 derive() / ladder() / linear-gradient() 求值不可靠，
        // 只要样式串里出现它们，控件就可能整块没有背景（issue #49）
        for (Theme theme : new Theme[] {Theme.DARK, Theme.LIGHT}) {
            Palette.setTheme(theme);
            assertThat(Palette.theme())
                    .as("%s：主题样式串不得含 CSS 函数", theme)
                    .doesNotContain("derive(")
                    .doesNotContain("ladder(")
                    .doesNotContain("linear-gradient(");
        }
    }

    @Test
    void lightThemeKeepsControlSurfacesLight() {
        // 输入框 / 下拉框 / 滚动条在亮色下必须是浅色——它们发黑时用户根本看不清内容
        Palette.setTheme(Theme.LIGHT);

        assertThat(Palette.CONTROL_INNER).isEqualTo("#FFFFFF");
        assertThat(Palette.SCROLL_TRACK).isEqualTo("#F0F0F0");
        assertThat(Palette.SCROLL_THUMB).isEqualTo("#B8B8B8");
        assertThat(Palette.theme())
                .contains("-fx-aha-control-bg: #FFFFFF")
                .contains("-fx-text-box-border: #C8C8C8")
                .contains("-fx-body-color: #F4F4F4");
    }

    @Test
    void patchStylesheetIsPackagedAndAlsoFreeOfCssMathFunctions() throws Exception {
        // 这份表是原生镜像下控件背景的唯一来源（见 aha-theme.css 头部说明），
        // 既要在 classpath 上，也不能再引入 derive / gradient
        String path = "/com/acanx/module/aha/desktop/view/aha-theme.css";
        try (InputStream in = PaletteTest.class.getResourceAsStream(path)) {
            assertThat(in).as("主题补丁样式表必须在 classpath 上：%s", path).isNotNull();
            String css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(css)
                    .contains(".text-input")
                    .contains(".scroll-bar > .thumb")
                    .contains("-fx-aha-control-bg");
            // 注释里可以提这些函数名（说明用），但**声明**里不能出现
            String declarations = css.replaceAll("(?s)/\\*.*?\\*/", "");
            assertThat(declarations)
                    .as("补丁表里不得使用原生镜像下求值不可靠的 CSS 函数")
                    .doesNotContain("derive(")
                    .doesNotContain("ladder(")
                    .doesNotContain("linear-gradient(");
        }
    }

    @Test
    void toolKindMapsToSemanticColorInEitherTheme() {
        Palette.setTheme(Theme.DARK);
        assertThat(Palette.forToolKind(ToolKind.READ)).isEqualTo(Palette.READ);
        assertThat(Palette.forToolKind(ToolKind.EXEC)).isEqualTo(Palette.EXEC);
        assertThat(Palette.forToolKind(ToolKind.OTHER)).isEqualTo(Palette.OTHER);

        Palette.setTheme(Theme.LIGHT);
        // 映射关系不变，取值随主题
        assertThat(Palette.forToolKind(ToolKind.READ)).isEqualTo(Palette.READ);
        assertThat(Palette.forToolKind(ToolKind.NETWORK)).isEqualTo(Palette.NETWORK);
    }

    @Test
    void nullThemeFallsBackToDark() {
        Palette.setTheme(null);
        assertThat(Palette.current()).isEqualTo(Theme.DARK);
    }

    @Test
    void themeStyleIsRecomputedFromCurrentFields() {
        // 样式串必须与当前色表实时一致：早先它被缓存成一个静态字段，
        // 原生镜像下出现过「底色已换暗、文字色仍是旧主题」的不同步（issue #49）
        Palette.setTheme(Theme.DARK);
        String dark = Palette.theme();
        Palette.setTheme(Theme.LIGHT);
        assertThat(Palette.theme())
                .as("切换到亮色后，样式串必须立刻反映新的底色与前景")
                .isNotEqualTo(dark)
                .contains(Palette.BASE)
                .contains(Palette.FOREGROUND);
        Palette.setTheme(Theme.DARK);
        assertThat(Palette.theme()).isEqualTo(dark);
    }
}
