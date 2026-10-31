package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 桌面端主窗口骨架（{@code desktop-shell.fxml}）与控制器的一致性。
 *
 * <p>通用对账（import 表 / 构造器登记 / fx:id ↔ 字段 / 资源白名单）见
 * {@link FxmlViewChecks}，这里只放本视图特有的一条：**节点 id 必须来自公开常量**。</p>
 *
 * @since 0.1.3
 */
class DesktopShellFxmlTest {

    private static final String FXML = UiResources.SHELL_FXML;

    @Test
    void fxmlShipsNextToTheController() throws IOException {
        assertThat(FxmlViewChecks.read(DesktopShell.class, FXML))
                .as("%s 必须与 DesktopShell 同包（FXMLLoader 用相对资源名装载）", FXML)
                .contains("<BorderPane")
                .contains("fx:id=\"root\"");
    }

    @Test
    void everyFxIdHasAnInjectedField() throws IOException {
        FxmlViewChecks.assertFxIdsInjected("desktop-shell", FxmlViewChecks.read(DesktopShell.class, FXML),
                DesktopShell.class);
    }

    @Test
    void everyInjectedFieldIsUsedByTheFxml() throws IOException {
        FxmlViewChecks.assertInjectedFieldsUsed("desktop-shell",
                FxmlViewChecks.read(DesktopShell.class, FXML), DesktopShell.class);
    }

    @Test
    void everyElementTypeIsImported() throws IOException {
        FxmlViewChecks.assertElementsImported("desktop-shell",
                FxmlViewChecks.read(DesktopShell.class, FXML));
    }

    @Test
    void everyFxmlInstantiatedTypeRegistersItsConstructor() throws IOException {
        FxmlViewChecks.assertConstructorsRegistered("desktop-shell",
                FxmlViewChecks.read(DesktopShell.class, FXML));
    }

    @Test
    void resourceIsWhitelistedForNativeImage() throws IOException {
        FxmlViewChecks.assertResourceWhitelisted(UiResources.relative(FXML));
    }

    /**
     * 节点 id 必须取自 {@link DesktopShell} 的公开常量（详见
     * {@link FxmlViewChecks#assertNodeIdsArePublicConstants}）。
     */
    @Test
    void everyNodeIdMatchesAPublicConstant() throws IOException {
        FxmlViewChecks.assertNodeIdsArePublicConstants("desktop-shell",
                FxmlViewChecks.read(DesktopShell.class, FXML), DesktopShell.class);
    }
}
