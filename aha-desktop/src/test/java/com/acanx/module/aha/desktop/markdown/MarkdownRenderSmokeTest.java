package com.acanx.module.aha.desktop.markdown;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JavaFX 渲染节点的可用性骨架（issue #412 / tracking #408）。
 *
 * <p>桌面端 Markdown 渲染器要到 S1.5 才有，但「用哪些节点承载块级/行内结构」可以现在就钉住：
 * 行内用 {@link TextFlow} + {@link Text}，表格用 {@link GridPane}，纯文本兜底用 {@link Label}。
 * 后续阶段只需把 AST 映射到这些节点，不必再验证节点模型本身。</p>
 *
 * <p>需要图形环境：与 {@code AhaDesktopSmokeTest} 同一开关（{@code -Daha.ui.tests=true}，
 * Linux 上配合 xvfb）。CI 默认跳过。</p>
 *
 * @since 0.2.0
 */
@EnabledIfSystemProperty(named = "aha.ui.tests", matches = "true")
class MarkdownRenderSmokeTest {

    private static final long TIMEOUT_SECONDS = 30;

    @Test
    void textFlowHostsStyledTextForInlineMarkup() throws Exception {
        startToolkit();

        TextFlow flow = new TextFlow();
        Text plain = new Text("普通 ");
        Text bold = new Text("粗体");
        bold.setStyle("-fx-font-weight: bold;");
        Text code = new Text("code");
        code.setStyle("-fx-font-family: monospace;");

        onUi(() -> flow.getChildren().addAll(plain, bold, code));

        List<Node> children = new ArrayList<>(flow.getChildren());
        assertThat(children).hasSize(3);
        assertThat(children.get(1)).isInstanceOf(Text.class);
        assertThat(((Text) children.get(1)).getStyle()).contains("-fx-font-weight: bold");
        assertThat(((Text) children.get(2)).getStyle()).contains("monospace");
    }

    @Test
    void gridPaneHostsTableCellsForStage2() throws Exception {
        startToolkit();

        GridPane table = new GridPane();
        onUi(() -> {
            table.add(new Label("表头 1"), 0, 0);
            table.add(new Label("表头 2"), 1, 0);
            table.add(new Label("值"), 0, 1);
        });

        assertThat(table.getChildren()).hasSize(3);
        assertThat(GridPane.getColumnIndex(table.getChildren().get(1))).isEqualTo(1);
        assertThat(GridPane.getRowIndex(table.getChildren().get(2))).isEqualTo(1);
    }

    @Test
    void rendererMapsBlocksToExpectedNodeTypes() throws Exception {
        startToolkit();

        MarkdownRenderer renderer = new MarkdownRenderer(
                (node, style) -> node.setStyle(style.get()), text -> { });
        Node root = uiGet(() -> renderer.render("""
                # 标题

                段落 **粗** 与 `code`

                - 列表项

                > 引用

                ```java
                int a = 1;
                ```

                | a | b |
                |---|---|
                | 1 | 2 |
                """));

        assertThat(root).isInstanceOf(VBox.class);
        VBox box = (VBox) root;
        assertThat(box.getChildren()).extracting(child -> child.getClass().getSimpleName())
                .containsExactly("TextFlow", "TextFlow", "VBox", "HBox", "VBox", "ScrollPane");
        // 行内：粗体仍是 TextFlow 里的 Text，链接渲染为 Hyperlink
        assertThat(((TextFlow) box.getChildren().get(1)).getChildren())
                .anyMatch(node -> node instanceof Text);
    }

    @Test
    void rendererRendersLinkAsHyperlink() throws Exception {
        startToolkit();

        MarkdownRenderer renderer = new MarkdownRenderer(
                (node, style) -> node.setStyle(style.get()), text -> { });
        Node root = uiGet(() -> renderer.render("见 [文档](https://x/y)。\n"));

        TextFlow flow = (TextFlow) ((VBox) root).getChildren().get(0);
        assertThat(flow.getChildren()).anyMatch(node -> node instanceof Hyperlink);
    }

    @Test
    void labelRemainsTheFallbackForPlainText() throws Exception {
        startToolkit();

        Label label = new Label("纯文本兜底");
        onUi(() -> label.setWrapText(true));

        assertThat(label.isWrapText()).isTrue();
        assertThat(label.getText()).isEqualTo("纯文本兜底");
    }

    // ------------------------------------------------------------------

    private static void startToolkit() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyStarted) {
            ready.countDown();
        }
        assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    private static <T> T uiGet(Callable<T> supplier) throws Exception {
        AtomicReference<T> reference = new AtomicReference<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        onUi(() -> {
            try {
                reference.set(supplier.call());
            } catch (Exception e) {
                failure.set(e);
            }
        });
        if (failure.get() != null) {
            throw failure.get();
        }
        return reference.get();
    }

    private static void onUi(Runnable task) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                task.run();
            } finally {
                done.countDown();
            }
        });
        assertThat(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }
}
