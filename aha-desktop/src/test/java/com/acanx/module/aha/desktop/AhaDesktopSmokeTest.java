package com.acanx.module.aha.desktop;

import com.acanx.module.aha.common.AppVersion;
import com.acanx.module.aha.common.model.SessionSummary;
import com.acanx.module.aha.desktop.chat.DesktopToolApprover;
import com.acanx.module.aha.desktop.fx.PlatformFxDispatcher;
import com.acanx.module.aha.desktop.log.LogBuffer;
import com.acanx.module.aha.desktop.log.LogLevel;
import com.acanx.module.aha.desktop.log.LogLine;
import com.acanx.module.aha.desktop.view.ApprovalDialog;
import com.acanx.module.aha.desktop.view.LogPanel;
import com.acanx.module.aha.desktop.view.Palette;
import com.acanx.module.aha.desktop.view.Theme;
import com.acanx.module.aha.desktop.view.DesktopShell;
import com.acanx.module.aha.desktop.view.ShellLayout;
import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.VBox;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Map;
import java.util.Set;
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
        assertThat(uiGet(() -> byId(stage.getScene(), DesktopShell.STATUS_ID)))
                .isInstanceOf(Label.class);
        // D-11：窗口里必须能看到配置来源与生效日志级别
        assertThat(uiGet(() -> byId(stage.getScene(), DesktopShell.CONFIG_ID)))
                .isInstanceOf(Label.class);

        // 三栏骨架与输入区都在
        assertThat(uiGet(() -> byId(stage.getScene(), DesktopShell.MENU_BAR_ID))).isNotNull();
        assertThat(uiGet(() -> byId(stage.getScene(), DesktopShell.LEFT_CONTENT_ID))).isNotNull();
        assertThat(uiGet(() -> byId(stage.getScene(), DesktopShell.RIGHT_CONTENT_ID))).isNotNull();
        assertThat(uiGet(() -> byId(stage.getScene(), DesktopShell.MESSAGES_ID))).isInstanceOf(VBox.class);
        assertThat(uiGet(() -> byId(stage.getScene(), DesktopShell.COMPOSER_ID))).isInstanceOf(TextArea.class);
        // 右栏默认收起（本轮无数据），中栏因此更宽
        assertThat(uiGet(() -> byId(stage.getScene(), DesktopShell.RIGHT_CONTENT_ID).isVisible()))
                .isFalse();

        // 折叠 → 展开：走的是窄条按钮本身，也就是鼠标用户点的那颗
        Button leftToggle = (Button) uiGet(() -> byId(stage.getScene(), DesktopShell.LEFT_TOGGLE_ID));
        assertThat(leftToggle).isNotNull();
        onUi(leftToggle::fire);
        assertThat(uiGet(() -> byId(stage.getScene(), DesktopShell.LEFT_CONTENT_ID).isVisible()))
                .isFalse();
        assertThat(uiGet(() -> byId(stage.getScene(), DesktopShell.LEFT_CONTENT_ID).isManaged()))
                .isFalse();
        // 折叠后按钮必须还在，否则用户没有任何办法展开（本次要钉住的正是这件事）
        assertThat(uiGet(leftToggle::isVisible)).isTrue();
        onUi(leftToggle::fire);
        assertThat(uiGet(() -> byId(stage.getScene(), DesktopShell.LEFT_CONTENT_ID).isVisible()))
                .isTrue();
        assertThat(uiGet(() -> byId(stage.getScene(), DesktopShell.LEFT_CONTENT_ID).isManaged()))
                .isTrue();

        // 右栏同理：收起的栏也能从窄条按钮拉回来
        Button rightToggle = (Button) uiGet(() -> byId(stage.getScene(), DesktopShell.RIGHT_TOGGLE_ID));
        onUi(rightToggle::fire);
        assertThat(uiGet(() -> byId(stage.getScene(), DesktopShell.RIGHT_CONTENT_ID).isVisible()))
                .isTrue();

        // Enter 发送 / Shift+Enter 换行
        TextArea composer = (TextArea) uiGet(() -> byId(stage.getScene(), DesktopShell.COMPOSER_ID));
        VBox messages = (VBox) uiGet(() -> byId(stage.getScene(), DesktopShell.MESSAGES_ID));
        int before = uiGet(() -> messageRows(messages));
        onUi(() -> {
            composer.setText("你好");
            composer.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                    KeyCode.ENTER, true, false, false, false));
        });
        assertThat(uiGet(() -> messageRows(messages)))
                .as("Shift+Enter 只换行，不发送")
                .isEqualTo(before);
        onUi(() -> composer.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                KeyCode.ENTER, false, false, false, false)));
        assertThat(uiGet(() -> messageRows(messages)))
                .as("Enter 应把输入作为消息发出（注意：首条消息会移除空态，所以 children 总数并不变大）")
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
        // 必须真的放进一个**已显示**的 Scene：否则布局根本不会发生，下面两条
        // 「宽度不能超出左栏」的断言会拿到 0 而**空过**（0 ≤ 220 永远成立）——
        // 那是「看着有断言、其实什么也没保证」。
        AtomicReference<Stage> sessionStage = new AtomicReference<>();
        onUi(() -> {
            Stage extra = new Stage();
            extra.setScene(new Scene(sessionShell.buildRoot(), 1280, 800));
            extra.show();
            sessionStage.set(extra);
            sessionShell.refreshSessions(List.of(
                    new SessionSummary("AAA11111", "看看代码", created, 12),
                    new SessionSummary("BBB22222", "写文档", created, 1)), "BBB22222");
        });
        assertThat(uiGet(sessionShell::sessionRowCount)).isEqualTo(2);
        assertThat(uiGet(sessionShell::sessionRows))
                .anySatisfy(row -> assertThat(row).contains("看看代码"))
                .anySatisfy(row -> assertThat(row).contains("12 条").contains("2 分钟前"));
        assertThat(uiGet(() -> ((javafx.scene.control.ListView<?>) byId(sessionShell.buildRoot(), DesktopShell.SESSION_LIST_ID)) == null))
                .as("会话列表必须真的在左栏里（且有右键菜单）")
                .isFalse();
        assertThat(uiGet(() -> ((javafx.scene.control.ListView<?>) byId(sessionShell.buildRoot(), DesktopShell.SESSION_LIST_ID)).getContextMenu().getItems().size()))
                .isGreaterThanOrEqualTo(4);
        // 左栏宽度是硬约束：控件溢出会盖住中栏（真机截图里搜索框曾经横向溢出）
        assertThat(uiGet(() -> byId(sessionShell.buildRoot(), DesktopShell.SESSION_SEARCH_ID).getLayoutBounds().getWidth()))
                .as("搜索框不能超出左栏").isLessThanOrEqualTo(ShellLayout.LEFT_WIDTH);
        assertThat(uiGet(() -> byId(sessionShell.buildRoot(), DesktopShell.SESSION_LIST_ID).getLayoutBounds().getWidth()))
                .as("会话列表不能超出左栏").isLessThanOrEqualTo(ShellLayout.LEFT_WIDTH);

        onUi(() -> sessionShell.searchSessions("文档"));
        assertThat(uiGet(sessionShell::sessionRowCount)).isEqualTo(1);
        onUi(() -> sessionShell.searchSessions(""));
        assertThat(uiGet(sessionShell::sessionRowCount)).isEqualTo(2);
        onUi(() -> sessionStage.get().close());

        // 输入区增强（GUIDesign 第 4.3 节）：/ 命令面板、↑↓ 历史、@ 文件引用
        DesktopShell inputShell = new DesktopShell(new PlatformFxDispatcher(), () -> { }, () -> "冒烟");
        AtomicReference<Stage> inputStage = new AtomicReference<>();
        onUi(() -> {
            Stage extra = new Stage();
            extra.setScene(new Scene(inputShell.buildRoot(), 900, 600));
            extra.show();
            inputStage.set(extra);
        });
        TextArea input = (TextArea) uiGet(() -> byId(inputStage.get().getScene(), DesktopShell.COMPOSER_ID));
        onUi(() -> {
            input.setText("/he");
            input.positionCaret(3);
        });
        assertThat(uiGet(inputShell::completionVisible)).as("打了 /he 应当弹出命令候选").isTrue();
        assertThat(uiGet(inputShell::completionItems)).anySatisfy(item ->
                assertThat(item).contains("/help"));
        onUi(inputShell::acceptCompletion);
        assertThat(uiGet(() -> input.getText())).isEqualTo("/help");
        assertThat(uiGet(inputShell::completionVisible)).isFalse();

        // Enter 执行命令：/help 应当把命令一览写进对话流，而不是发给模型
        onUi(inputShell::submitComposer);
        assertThat(uiGet(() -> messageRows(
                (VBox) byId(inputStage.get().getScene(), DesktopShell.MESSAGES_ID))))
                .as("/help 的输出应当写进对话流（同样按「非空态节点」计数）")
                .isGreaterThan(0);
        assertThat(uiGet(() -> inputShell.history().size())).isEqualTo(1);
        assertThat(uiGet(inputShell::helpTextContainsCatalog))
                .as("/help 的输出应当列出命令").isTrue();

        // ↑ / ↓ 翻历史，且不丢草稿
        //
        // 注意：必须先发一条**普通内容**进历史，不能拿 /help 当历史项——
        // 文本一旦以 / 开头，候选弹层会立刻弹出，而**弹层打开时 ↑/↓ 归弹层用**
        // （设计行为，见 DesktopShell 那个事件过滤器的注释）。
        // 拿 /help 当历史项会把「翻历史」测成「翻候选」——那是断言写错，不是产品缺陷。
        onUi(() -> {
            input.setText("上一条普通内容");
            input.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "",
                    KeyCode.ENTER, false, false, false, false));
        });
        onUi(() -> {
            input.setText("随手打的草稿");
            input.positionCaret(input.getText().length());
        });
        onUi(() -> input.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.UP,
                false, false, false, false)));
        assertThat(uiGet(() -> input.getText())).isEqualTo("上一条普通内容");
        onUi(() -> input.fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.DOWN,
                false, false, false, false)));
        assertThat(uiGet(() -> input.getText())).as("翻回草稿").isEqualTo("随手打的草稿");

        // @ 文件引用：项目里一定有 pom.xml
        onUi(() -> {
            input.setText("@pom");
            input.positionCaret(4);
        });
        assertThat(uiGet(inputShell::completionVisible)).isTrue();
        assertThat(uiGet(inputShell::completionItems)).anySatisfy(item ->
                assertThat(item).contains("pom.xml"));
        onUi(inputShell::acceptCompletion);
        assertThat(uiGet(() -> input.getText())).contains("pom.xml");
        onUi(() -> inputShell.applyTheme(Theme.LIGHT));
        assertThat(uiGet(() -> inputStage.get().getScene().getRoot().getStyle()))
                .as("换主题要连根节点的样式一起换").contains(Palette.BASE);
        // 注意这里用**输入框**而不是发送按钮来验「样式被重刷」：发送按钮没有内联样式
        // （它的外观由 CSS 补丁表的 .button 规则负责，getStyle() 本来就是空串），
        // 拿它断言永远不成立——这是套件从未真跑过时留下的又一处错设。
        assertThat(uiGet(() -> byId(inputStage.get().getScene(), DesktopShell.COMPOSER_ID).getStyle()))
                .as("已建好的控件也必须被重刷，而不是只换新控件").contains(Palette.FOREGROUND);
        onUi(() -> inputShell.applyTheme(Theme.DARK));
        onUi(() -> inputStage.get().close());

        // 日志面板：级别过滤 + 渲染
        LogPanel panel = new LogPanel("DEBUG");
        LogBuffer buffer = com.acanx.module.aha.desktop.log.LogCapture.buffer();
        onUi(() -> {
            panel.build();
            buffer.clear();
            buffer.add(new LogLine(LogLevel.DEBUG, "com.acanx.module.aha.X", "调试行", null, 0));
            buffer.add(new LogLine(LogLevel.ERROR, "com.acanx.module.aha.Y", "错误行", null, 0));
            panel.setFilter(LogLevel.ERROR);
        });
        assertThat(uiGet(panel::text)).contains("错误行").doesNotContain("调试行");
        onUi(panel::close);

        // 授权弹窗：三个按钮 + 权限名写进按钮 + 默认拒绝
        AtomicReference<javafx.scene.control.Dialog<DesktopToolApprover.Decision>> approve =
                new AtomicReference<>();
        onUi(() -> approve.set(new ApprovalDialog("file-write",
                com.acanx.module.aha.common.tool.ToolPermission.WRITE,
                Map.of("path", "a.txt"), Set.of()).build()));
        assertThat(uiGet(() -> approve.get().getDialogPane().lookupButton(
                approve.get().getDialogPane().getButtonTypes().get(0)).getId()))
                .isEqualTo(ApprovalDialog.DENY_ID);
        assertThat(uiGet(() -> ((Button) approve.get().getDialogPane().lookupButton(
                approve.get().getDialogPane().getButtonTypes().get(2))).getText()))
                .as("按钮上要写出被放宽的权限名").contains("WRITE");
        assertThat(uiGet(() -> ((TextArea) byId(
                approve.get().getDialogPane(), ApprovalDialog.ARGS_ID)).getText()))
                .contains("path: a.txt");

        assertThat(awaitStatus(stage)).startsWith("后台线程已就绪");

        onUi(stage::close);
    }

    /** 反复读状态标签，直到后台虚拟线程的值经桥接落到 UI 线程。 */
    private static String awaitStatus(Stage stage) throws Exception {
        String text = DesktopShell.STATUS_INITIAL;
        for (int i = 0; i < POLL_ROUNDS && DesktopShell.STATUS_INITIAL.equals(text); i++) {
            text = uiGet(() -> ((Label) byId(stage.getScene(), DesktopShell.STATUS_ID)).getText());
            if (DesktopShell.STATUS_INITIAL.equals(text)) {
                Thread.sleep(50);
            }
        }
        return text;
    }

    /**
     * 按**节点 id** 取节点。
     *
     * <p><strong>为什么不用 {@code scene.lookup("#" + id)}</strong>：本项目的节点 id 形如
     * {@code aha.status}（带点），而 CSS 选择器里 {@code .} 是**类选择器**——
     * {@code lookup("#aha.status")} 会被解析成「id=aha **且** class=status」，永远找不到。
     * 这不是产品缺陷（id 只是个字符串），而是「用 CSS 选择器表达带点 id」的固有限制。</p>
     *
     * <p><strong>实测（2026-10-13，ui-smoke 首次运行）</strong>：本套件里 25 处
     * {@code lookup("#…")} 全部返回 {@code null}——也就是说这套断言写好后
     * <em>从未真正通过过</em>，因为它从未被真正执行（一直开着
     * {@code -Daha.ui.tests=true} 这个门）。第一个真跑它的就是那个新的 CI 作业。</p>
     *
     * <p>这里改为遍历场景图、直接比 {@link Node#getId()}——不经过 CSS 选择器，
     * 因此对含点的 id 安全。</p>
     *
     * @param scene 场景（可为 {@code null}）
     * @param id    节点 id
     * @return 节点；找不到返回 {@code null}
     */
    private static Node byId(Scene scene, String id) {
        return scene == null ? null : byId(scene.getRoot(), id);
    }

    /**
     * 按节点 id 在某个父节点下取节点（与 {@link #byId(Scene, String)} 同理，
     * 供对话框的 {@code DialogPane} 这类非 Scene 根使用）。
     *
     * @param root 父节点（可为 {@code null}）
     * @param id   节点 id
     * @return 节点；找不到返回 {@code null}
     */
    private static Node byId(Parent root, String id) {
        if (root == null) {
            return null;
        }
        return root.lookupAll("*").stream()
                .filter(node -> id.equals(node.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 消息流里的「**消息行**」数量（**不含**空态提示节点）。
     *
     * <p>为什么不直接数 {@code messages.getChildren().size()}：<strong>首条消息会「移除空态（−1）
     * + 加入消息行（+1）」，总数不变</strong>。实测（2026-10-13，ui-smoke 第二次运行）：
     * 断言「Enter 后总数变大」永远不成立（actual 1 不大于 1）——这一条从一开始就是错的，
     * 只是从未被执行过。数「非空态」的节点才表达得出「真的多了一条消息」。</p>
     *
     * @param messages 消息流容器
     * @return 消息行数
     */
    private static int messageRows(VBox messages) {
        return (int) messages.getChildren().stream()
                .filter(node -> !DesktopShell.EMPTY_ID.equals(node.getId()))
                .count();
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
