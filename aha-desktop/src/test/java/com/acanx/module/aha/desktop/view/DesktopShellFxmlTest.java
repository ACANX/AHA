package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

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

    private static final String FXML = "desktop-shell.fxml";

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
        FxmlViewChecks.assertResourceWhitelisted(
                "com/acanx/module/aha/desktop/view/" + FXML);
    }

    /**
     * 节点 id 必须取自 {@link DesktopShell} 的公开常量。
     *
     * <p>冒烟测试与真机排查都按 {@code lookup("#" + CONSTANT)} 定位；id 写成字面量时，
     * 常量改了、fxml 没改，所有 lookup 会**静默落空**（不报错，只是什么也找不到）。</p>
     */
    @Test
    void everyNodeIdMatchesAPublicConstant() throws IOException {
        Set<String> allowed = constantValues();
        Set<String> unknown = new TreeSet<>();
        for (String id : FxmlViewChecks.nodeIds(FxmlViewChecks.read(DesktopShell.class, FXML))) {
            if (!allowed.contains(id)) {
                unknown.add(id);
            }
        }
        assertThat(unknown)
                .as("节点 id 必须取自 DesktopShell 的公开常量；新增栏 / 部件时先定义常量，再写进 fxml")
                .isEmpty();
    }

    private static Set<String> constantValues() {
        Set<String> values = new TreeSet<>();
        for (java.lang.reflect.Field field : DesktopShell.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && java.lang.reflect.Modifier.isPublic(field.getModifiers())
                    && field.getType() == String.class) {
                try {
                    if (field.get(null) instanceof String text) {
                        values.add(text);
                    }
                } catch (IllegalAccessException e) {
                    throw new AssertionError("读取常量失败：" + field.getName(), e);
                }
            }
        }
        return values;
    }
}
