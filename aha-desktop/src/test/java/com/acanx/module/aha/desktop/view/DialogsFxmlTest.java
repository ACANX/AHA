package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 批次 4 四个对话框（{@code *-dialog.fxml}）与各自控制器的一致性。
 *
 * <p>本批把「一个视图一个测试类」改成**循环过一遍四份视图**：通用对账（含新增的
 * 「fxml 必须是良构 XML」）在 {@link FxmlViewChecks} 里，这里只声明
 * 「资源名 + 控制器类」的对应关系——正是这个助手存在的意义。</p>
 *
 * @since 0.1.3
 */
class DialogsFxmlTest {

    /** 一个视图：骨架资源名 + 它的控制器（同时是 id 常量的持有者）。 */
    private record View(String resource, Class<?> controller) {

        /** 短名（用于断言消息）：取路径最后一段并去掉扩展名。 */
        String name() {
            String file = resource.substring(resource.lastIndexOf('/') + 1);
            return file.replace(".fxml", "");
        }
    }

    /** 资源路径统一来自 {@link UiResources}——它也是「目录改动只改一处」的落点。 */
    private static final List<View> VIEWS = List.of(
            new View(UiResources.SETTINGS_DIALOG_FXML, SettingsDialog.class),
            new View(UiResources.APPROVAL_DIALOG_FXML, ApprovalDialog.class),
            new View(UiResources.OUTPUT_DIALOG_FXML, OutputDialog.class),
            new View(UiResources.TOOL_LIST_DIALOG_FXML, ToolListDialog.class));

    @Test
    void everyViewIsPackagedNextToItsController() throws IOException {
        for (View view : VIEWS) {
            assertThat(FxmlViewChecks.read(view.controller(), view.resource()))
                    .as("%s 必须与 %s 同包", view.resource(), view.controller().getSimpleName())
                    .contains("fx:id=");
        }
    }

    @Test
    void everyViewIsWellFormedXml() throws IOException {
        for (View view : VIEWS) {
            FxmlViewChecks.assertWellFormedXml(view.name(),
                    FxmlViewChecks.read(view.controller(), view.resource()));
        }
    }

    @Test
    void everyElementTypeIsImported() throws IOException {
        for (View view : VIEWS) {
            FxmlViewChecks.assertElementsImported(view.name(),
                    FxmlViewChecks.read(view.controller(), view.resource()));
        }
    }

    @Test
    void everyFxmlInstantiatedTypeRegistersItsConstructor() throws IOException {
        for (View view : VIEWS) {
            FxmlViewChecks.assertConstructorsRegistered(view.name(),
                    FxmlViewChecks.read(view.controller(), view.resource()));
        }
    }

    @Test
    void everyFxIdHasAnInjectedField() throws IOException {
        for (View view : VIEWS) {
            FxmlViewChecks.assertFxIdsInjected(view.name(),
                    FxmlViewChecks.read(view.controller(), view.resource()), view.controller());
        }
    }

    @Test
    void everyInjectedFieldIsUsedByTheFxml() throws IOException {
        for (View view : VIEWS) {
            FxmlViewChecks.assertInjectedFieldsUsed(view.name(),
                    FxmlViewChecks.read(view.controller(), view.resource()), view.controller());
        }
    }

    @Test
    void everyNodeIdMatchesAPublicConstant() throws IOException {
        for (View view : VIEWS) {
            FxmlViewChecks.assertNodeIdsArePublicConstants(view.name(),
                    FxmlViewChecks.read(view.controller(), view.resource()), view.controller());
        }
    }

    @Test
    void everyResourceIsWhitelistedForNativeImage() throws IOException {
        for (View view : VIEWS) {
            FxmlViewChecks.assertResourceWhitelisted(UiResources.relative(view.resource()));
        }
    }
}
