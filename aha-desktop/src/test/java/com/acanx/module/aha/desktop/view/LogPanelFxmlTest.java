package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 日志面板（{@code log-panel.fxml}）与控制器的一致性。
 *
 * <p>通用对账见 {@link FxmlViewChecks}；本视图额外沿用「节点 id 必须来自公开常量」——
 * {@code LogPanel} 的四个 id（{@code AREA_ID} / {@code FILTER_ID} / {@code REFRESH_ID} /
 * {@code CLEAR_ID} / {@code COPY_ID}）本来就是 {@code public static final}，
 * 测试与将来的自证都按它们 lookup。</p>
 *
 * @since 0.1.3
 */
class LogPanelFxmlTest {

    private static final String FXML = UiResources.LOG_PANEL_FXML;

    @Test
    void copyTakesSelectionWhenThereIsOneOtherwiseEverything() {
        // 右键「复制选中（没有选中则复制全部）」和 Ctrl+C 共用这条规则（纯函数，不需要剪贴板）
        assertThat(LogPanel.textToCopy("", "全部")).isEqualTo("全部");
        assertThat(LogPanel.textToCopy(null, "全部")).isEqualTo("全部");
        assertThat(LogPanel.textToCopy("选中", "全部")).isEqualTo("选中");
    }

    @Test
    void fxmlShipsNextToTheController() throws IOException {
        assertThat(FxmlViewChecks.read(LogPanel.class, FXML))
                .as("%s 必须与 LogPanel 同包", FXML)
                .contains("fx:id=\"root\"")
                .contains("fx:id=\"toolbar\"")
                .contains("fx:id=\"area\"");
    }

    @Test
    void everyFxIdHasAnInjectedField() throws IOException {
        FxmlViewChecks.assertFxIdsInjected("log-panel",
                FxmlViewChecks.read(LogPanel.class, FXML), LogPanel.class);
    }

    @Test
    void everyInjectedFieldIsUsedByTheFxml() throws IOException {
        FxmlViewChecks.assertInjectedFieldsUsed("log-panel",
                FxmlViewChecks.read(LogPanel.class, FXML), LogPanel.class);
    }

    @Test
    void everyElementTypeIsImported() throws IOException {
        FxmlViewChecks.assertElementsImported("log-panel",
                FxmlViewChecks.read(LogPanel.class, FXML));
    }

    @Test
    void everyFxmlInstantiatedTypeRegistersItsConstructor() throws IOException {
        FxmlViewChecks.assertConstructorsRegistered("log-panel",
                FxmlViewChecks.read(LogPanel.class, FXML));
    }

    @Test
    void everyNodeIdMatchesAPublicConstant() throws IOException {
        FxmlViewChecks.assertNodeIdsArePublicConstants("log-panel",
                FxmlViewChecks.read(LogPanel.class, FXML), LogPanel.class);
    }

    @Test
    void resourceIsWhitelistedForNativeImage() throws IOException {
        FxmlViewChecks.assertResourceWhitelisted(UiResources.relative(FXML));
    }
}
