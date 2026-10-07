package com.acanx.module.aha.desktop;

import com.acanx.module.aha.common.AppVersion;
import com.acanx.module.aha.common.model.SessionSummary;
import com.acanx.module.aha.desktop.fx.PlatformFxDispatcher;
import com.acanx.module.aha.desktop.view.DesktopShell;
import com.acanx.module.aha.desktop.view.ShellLayout;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 桌面端窗口冒烟测试：真的启动 JavaFX 工具箱、真的开窗。
 *
 * <p><strong>默认不运行。</strong>它需要图形环境（X11/Wayland + GTK；Windows/macOS 原生），
 * 而 CI 与无显示的 WSL 都没有——这也是本项目「测试必须环境无关」的必然结果：
 * 需要图形环境的检查只能显式开启。启用方式：</p>
 *
 * <pre>{@code ./mvnw -pl aha-desktop test -Daha.ui.tests=true}</pre>
 *
 * <p>Linux 上还需 Xvfb 之类的虚拟显示（{@code xvfb-run}）。何时在 CI 打开、
 * 用 TestFX 还是纯 JUnit，属 {@code TODO.md} 的 {@code D-04}。</p>
 *
 * @since 0.2.0
 */
@EnabledIfSystemProperty(named = "aha.ui.tests", matches = "true")
class AhaDesktopSmokeTest {

    private static final long TIMEOUT_SECONDS = 30;

    private static final int POLL_ROUNDS = 40;

    @Test
    void windowStartsAndBridgeReachesUiThread() throws Exception {
        startToolkit();

        AtomicReference<Stage> stageRef = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        onUi(() -> {
            try {
                AhaDesktopApp app = new AhaDesktopApp();
                app.init();
                Stage stage = new Stage();
                app.start(stage);
                stageRef.set(stage);
            } catch (Throwable t) {
                failure.set(t);
            }
        });

        assertThat(failure.get()).isNull();
        Stage stage = stageRef.get();
        assertThat(stage).isNotNull();
        assertThat(stage.getTitle()).contains(AppVersion.version());
        assertThat(uiGet(() -> stage.getScene().lookup("#" + DesktopShell.STATUS_ID)))
                .isInstanceOf(Label.class);
        // D-11：窗口里必须能看到配置来源与生效日志级别
        assertThat(uiGet(() -> stage.getScene().lookup("#" + DesktopShell.CONFIG_ID)))
                .isInstanceOf(Label.class);

        // 三栏骨架与输入区都在
        assertThat(uiGet(() -> stage.getScene().lookup("#" + DesktopShell.MENU_BAR_ID))).isNotNull();
        assertThat(uiGet(() -> stage.getScene().lookup("#" + DesktopShell.LEFT_CONTENT_ID))).isNotNull();
        assertThat(uiGet(() -> stage.getScene().lookup("#" + DesktopShell.RIGHT_CONTENT_ID))).isNotNull();
        assertThat(uiGet(() -> stage.getScene().lookup("#" + DesktopShell.MESSAGES_ID))).isInstanceOf(VBox.class);
        assertThat(uiGet(() -> stage.getScene().lookup("#" + DesktopShell.COMPOSER_ID))).isInstanceOf(TextArea.class);
        // 右栏默认收起（本轮无数据），中栏因此更宽
        assertThat(uiGet(() -> stage.getScene().lookup("#" + DesktopShell.RIGHT_CONTENT_ID).isVisible()))
                .isFalse();

        // 折叠 → 展开：走的是窄条按钮本身，也就是鼠标用户点的那颗
        Button leftToggle = (Button) uiGet(() -> stage.getScene().lookup("#" + DesktopShell.LEFT_TOGGLE_ID));
        assertThat(leftToggle).isNotNull();
        onUi(leftToggle::fire);
        assertThat(uiGet(() -> stage.getScene().lookup("#" + DesktopShell.LEFT_CONTENT_ID).isVisible()))
                .isFalse();
        assertThat(uiGet(() -> stage.getScene().lookup("#" + DesktopShell.LEFT_CONTENT_ID).isManaged()))
                .isFalse();
        // 折叠后按钮必须还在，否则用户没有任何办法展开（本次要钉住的正是这件事）
        assertThat(uiGet(leftToggle::isVisible)).isTrue();
        onUi(leftToggle::fire);
        assertThat(uiGet(() -> stage.getScene().lookup("#" + DesktopShell.LEFT_CONTENT_ID).isVisible()))
                .isTrue();
        assertThat(uiGet(() -> stage.getScene().lookup("#" + DesktopShell.LEFT_CONTENT_ID).isManaged()))
                .isTrue();

        // 右栏同理：收起的栏也能从窄条按钮拉回来
        Button rightToggle = (Button) uiGet(() -> stage.getScene().lookup("#" + DesktopShell.RIGHT_TOGGLE_ID));
        onUi(rightToggle::fire);
        assertThat(uiGet(() -> stage.getScene().lookup("#" + DesktopShell.RIGHT_CONTENT_ID).isVisible()))
                .isTrue();

        // Enter 发送 / Shift+Enter 换行
        TextArea composer = (TextArea) uiGet(() -> stage.getScene().lookup("#" + DesktopShell.COMPOSER_ID));
        VBox messages = (VBox) uiGet(() -> stage.getScene().lookup("#" + DesktopShell.MESSAGES_ID));
        int before = uiGet(() -> messages.getChildren().size());
        onUi(() -> {
            composer.setText("你好");
            composer.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                    KeyCode.ENTER, true, false, false, false));
        });
        assertThat(uiGet(() -> messages.getChildren().size()))
                .as("Shift+Enter 只换行，不发送")
                .isEqualTo(before);
        onUi(() -> composer.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                KeyCode.ENTER, false, false, false, false)));
        assertThat(uiGet(() -> messages.getChildren().size()))
                .as("Enter 应把输入作为消息发出")
                .isGreaterThan(before);

        // 工具卡片（GUIDesign 第 4.2 节）：默认折叠 → 可展开 → 失败卡片直接摊开且带重试入口
        DesktopShell cards = new DesktopShell(new PlatformFxDispatcher(), () -> { }, () -> "冒烟");
        int[] handles = new int[2];
        onUi(() -> {
            cards.buildRoot();
            handles[0] = cards.beginToolCall("读取", "file-read", "AgentEngine.java",
                    Map.of("path", "AgentEngine.java"));
            cards.finishToolCall(handles[0], true, "a\nb\nc", 250);
            handles[1] = cards.beginToolCall("执行", "shell-exec", "rm -rf build",
                    Map.of("command", "rm -rf build"));
            cards.finishToolCall(handles[1], false, "permission denied", 120);
        });
        assertThat(uiGet(() -> cards.toolCardCount())).isEqualTo(2);
        assertThat(uiGet(() -> cards.isToolCardBodyVisible(handles[0])))
                .as("卡片默认折叠：长输出不能淹没对话").isFalse();
        assertThat(uiGet(() -> cards.toolCardResultText(handles[0])))
                .isEqualTo("✓ 完成 · 0.3s · 3 行");
        onUi(() -> cards.toggleToolCard(handles[0]));
        assertThat(uiGet(() -> cards.isToolCardBodyVisible(handles[0]))).isTrue();
        assertThat(uiGet(() -> cards.isToolCardExpanded(handles[0]))).isTrue();
        assertThat(uiGet(() -> cards.isToolCardBodyVisible(handles[1])))
                .as("失败卡片直接展开，用户不必再点一次才知道发生了什么").isTrue();
        assertThat(uiGet(() -> cards.toolCardResultText(handles[1])))
                .isEqualTo("✗ 失败 · 0.1s · 1 行");

        // 会话列表（GUIDesign 第 4.5 节）：当前会话高亮、搜索可用、右键菜单在位
        DesktopShell sessionShell = new DesktopShell(new PlatformFxDispatcher(), () -> { }, () -> "冒烟");
        long created = System.currentTimeMillis() - 120_000;
        onUi(() -> {
            sessionShell.buildRoot();
            sessionShell.refreshSessions(List.of(
                    new SessionSummary("AAA11111", "看看代码", created, 12),
                    new SessionSummary("BBB22222", "写文档", created, 1)), "BBB22222");
        });
        assertThat(uiGet(sessionShell::sessionRowCount)).isEqualTo(2);
        assertThat(uiGet(sessionShell::sessionRows))
                .anySatisfy(row -> assertThat(row).contains("看看代码"))
                .anySatisfy(row -> assertThat(row).contains("12 条").contains("2 分钟前"));
        assertThat(uiGet(() -> ((javafx.scene.control.ListView<?>) sessionShell.buildRoot()
                .lookup("#" + DesktopShell.SESSION_LIST_ID)) == null))
                .as("会话列表必须真的在左栏里（且有右键菜单）")
                .isFalse();
        assertThat(uiGet(() -> ((javafx.scene.control.ListView<?>) sessionShell.buildRoot()
                .lookup("#" + DesktopShell.SESSION_LIST_ID)).getContextMenu().getItems().size()))
                .isGreaterThanOrEqualTo(4);
        // 左栏宽度是硬约束：控件溢出会盖住中栏（真机截图里搜索框曾经横向溢出）
        assertThat(uiGet(() -> sessionShell.buildRoot()
                .lookup("#" + DesktopShell.SESSION_SEARCH_ID).getLayoutBounds().getWidth()))
                .as("搜索框不能超出左栏").isLessThanOrEqualTo(ShellLayout.LEFT_WIDTH);
        assertThat(uiGet(() -> sessionShell.buildRoot()
                .lookup("#" + DesktopShell.SESSION_LIST_ID).getLayoutBounds().getWidth()))
                .as("会话列表不能超出左栏").isLessThanOrEqualTo(ShellLayout.LEFT_WIDTH);

        onUi(() -> sessionShell.searchSessions("文档"));
        assertThat(uiGet(sessionShell::sessionRowCount)).isEqualTo(1);
        onUi(() -> sessionShell.searchSessions(""));
        assertThat(uiGet(sessionShell::sessionRowCount)).isEqualTo(2);

        assertThat(awaitStatus(stage)).startsWith("后台线程已就绪");

        onUi(stage::close);
    }

    /** 反复读状态标签，直到后台虚拟线程的值经桥接落到 UI 线程。 */
    private static String awaitStatus(Stage stage) throws Exception {
        String text = DesktopShell.STATUS_INITIAL;
        for (int i = 0; i < POLL_ROUNDS && DesktopShell.STATUS_INITIAL.equals(text); i++) {
            text = uiGet(() -> ((Label) stage.getScene()
                    .lookup("#" + DesktopShell.STATUS_ID)).getText());
            if (DesktopShell.STATUS_INITIAL.equals(text)) {
                Thread.sleep(50);
            }
        }
        return text;
    }

    private static void startToolkit() throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyStarted) {
            // 同一 JVM 内只允许启动一次工具箱
            ready.countDown();
        }
        assertThat(ready.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();
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

    private static <T> T uiGet(Callable<T> supplier) throws Exception {
        AtomicReference<T> ref = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        onUi(() -> {
            try {
                ref.set(supplier.call());
            } catch (Exception e) {
                error.set(e);
            }
        });
        if (error.get() != null) {
            throw error.get();
        }
        return ref.get();
    }
}
