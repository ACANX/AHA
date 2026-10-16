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
import com.acanx.module.aha.core.update.AhaVersion;
import com.acanx.module.aha.core.update.GitHubReleaseClient;
import com.acanx.module.aha.core.update.InstallLayout;
import com.acanx.module.aha.core.update.PackageDownloader;
import com.acanx.module.aha.core.update.UpdatePaths;
import com.acanx.module.aha.core.update.UpdateService;
import com.acanx.module.aha.desktop.view.ThemePaint;
import com.acanx.module.aha.desktop.view.DesktopSettings;
import com.acanx.module.aha.desktop.view.DesktopShell;
import com.acanx.module.aha.desktop.view.SettingsDialog;
import com.acanx.module.aha.desktop.view.UpgradePane;
import com.acanx.module.aha.desktop.view.ShellLayout;
import org.junit.jupiter.api.Assumptions;

import javafx.application.Platform;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

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

        // 线程模型自证：后台虚拟线程的值必须经 FxBridge 落到 UI 线程。
        //
        // 【必须在发消息之前验】状态标签是同一处被反复覆写的：只要发生过一次发送，
        // ChatController 就会把它改成「生成中…（Esc 中断）」；而 CI 里没有配置供应商，
        // 流不会结束，这个值就再也回不来了。
        // 原实现在整个用例**末尾**才验它——那时早已被覆盖（这是套件从未真跑过时
        // 留下的又一处错设）。
        assertThat(awaitStatus(stage)).startsWith("后台线程已就绪");

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
        // ── 皮肤内部基面（issue #153，2026-10-15）────────────────────────────────
        // 上面那条只看**控件本体**的样式串，而输入框**可见的底色是 `.content` 画的**
        // （modena：`.text-area > .content { -fx-background-color: -fx-control-inner-background }`）
        // ⇒ 本体被重刷不代表内部基面也换了。真机现象是「日志区与输入框的底色切换前后不变、
        // 重启却正常」，套件却一直是绿的 —— 这一条才是能抓住它的断言。
        assertThat(uiGet(() -> skinSurface(inputStage.get().getScene(), DesktopShell.COMPOSER_ID, ".content")))
                .as("输入框的皮肤内部基面必须跟着换主题（只换本体不够）")
                .isEqualTo(normalize(Palette.CONTROL_INNER));
        onUi(() -> inputShell.applyTheme(Theme.DARK));
        assertThat(uiGet(() -> skinSurface(inputStage.get().getScene(), DesktopShell.COMPOSER_ID, ".content")))
                .as("切回暗色同样要换回来（否则就是「切一次对、再切就错」）")
                .isEqualTo(normalize(Palette.CONTROL_INNER));
        // ── 输入框的轮廓线（issue #229，2026-10-15）─────────────────────────────
        // 原生镜像 exe 里「输入框的轮廓线丢失，纯色界面下分不出输入框的范围」：
        // modena 画那圈线的规则带 derive() / linear-gradient()，原生镜像下**整条声明被丢弃**
        // （TS-2610-029）。现在轮廓线由 ThemePaint.paintBorder 走属性 API 落到 Region 上，
        // 这条断言读的就是那个属性（不是样式串）——且必须跟着主题换。
        assertThat(uiGet(() -> borderColor(inputStage.get().getScene(), DesktopShell.COMPOSER_ID)))
                .as("输入框必须有轮廓线（原生镜像下 CSS 那条会被丢弃，纯色界面里就分不出范围）")
                .isEqualTo(normalize(Palette.BORDER));
        onUi(() -> inputShell.applyTheme(Theme.LIGHT));
        assertThat(uiGet(() -> borderColor(inputStage.get().getScene(), DesktopShell.COMPOSER_ID)))
                .as("切到亮色后轮廓线也要跟着换（否则又是一处「切主题不跟随」）")
                .isEqualTo(normalize(Palette.BORDER));
        onUi(() -> inputStage.get().close());

        // 日志面板：级别过滤 + 渲染
        LogPanel panel = new LogPanel("DEBUG");
        LogBuffer buffer = com.acanx.module.aha.desktop.log.LogCapture.buffer();
        AtomicReference<Dialog<ButtonType>> logDialog = new AtomicReference<>();
        onUi(() -> {
            logDialog.set(panel.build());
            buffer.clear();
            buffer.add(new LogLine(LogLevel.DEBUG, "com.acanx.module.aha.X", "调试行", null, 0));
            buffer.add(new LogLine(LogLevel.ERROR, "com.acanx.module.aha.Y", "错误行", null, 0));
            panel.setFilter(LogLevel.ERROR);
        });
        assertThat(uiGet(panel::text)).contains("错误行").doesNotContain("调试行");
        // 非可编辑 TextArea 在 JavaFX 里**没有**默认右键菜单 ⇒ 真机反馈「日志复制不出来」：
        // 钉住我们自己加的那个（复制选中 / 复制全部）
        assertThat(uiGet(() -> {
            TextArea logArea = (TextArea) byId(
                    (Parent) logDialog.get().getDialogPane().getContent(), LogPanel.AREA_ID);
            return hasMenuItem(logArea.getContextMenu(), LogPanel.COPY_SELECTED_MENU_ID)
                    && hasMenuItem(logArea.getContextMenu(), LogPanel.COPY_ALL_MENU_ID);
        })).as("日志区要有右键复制菜单").isTrue();
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

        // 注：状态探针的断言已移到用例开头（发消息之前）——在这里它是被覆盖过的值。

        onUi(stage::close);
    }

    /**
     * 列表 / 下拉列表里**选中行**的实际配色（issue #227，2026-10-15）。
     *
     * <p>下拉列表里「当前那一项」曾经是<strong>白底白字</strong> —— 看不见，也没完全隐藏
     * （ACANX 真机截图：ComboBox 展开后「亮色」那一行是空白）。根因是 CSS 特异性：
     * 基面规则用 <strong>5 个类</strong>的选择器（{@code .list-view > .virtual-flow > .clipped-container
     * > .sheet > .list-cell}）设背景色，把 {@code .list-cell:filled:selected}（1 类 + 2 伪类）盖住了；
     * 而选中态**文字**色来自 {@code .list-cell:filled:selected .text}（1 类 + 2 伪类 + 1 类）却能生效
     * ⇒ 亮色主题下两者同色。</p>
     *
     * <p>为什么必须读运行期颜色：颜色全由补丁样式表给，选中格的 {@code getStyle()} 本来就是空的
     * —— 只看样式串的断言对这个 bug 是瞎的。这里读的是**选中格画出来的背景**与它里面
     * {@code .text} 节点的填充色，两者必须不同。</p>
     *
     * @throws Exception 取不到 UI 线程结果时抛出
     */
    @Test
    void listSelectionSurfaceIsReadableInBothThemes() throws Exception {
        startToolkit();
        for (Theme theme : List.of(Theme.LIGHT, Theme.DARK)) {
            AtomicReference<Stage> stageRef = new AtomicReference<>();
            onUi(() -> {
                Palette.setTheme(theme);
                ListView<Theme> list = new ListView<>();
                list.setId(LIST_ID);
                list.getItems().setAll(Theme.values());
                list.getSelectionModel().select(Theme.DARK);
                Stage stage = new Stage();
                Scene scene = new Scene(new StackPane(list), 220, 140);
                ThemePaint.install(scene);
                stage.setScene(scene);
                stage.show();
                stageRef.set(stage);
            });
            try {
                Region cell = awaitSelectedCell(stageRef.get());
                assertThat(cell).as("选中格应当能被找到（%s）", theme).isNotNull();
                assertThat(hexOf(backgroundOf(cell)))
                        .as("选中行底色必须是强调色（%s）：修前它停在基面底色上，与白字同色", theme)
                        .isEqualTo(normalize(Palette.FOCUS_BORDER));
                assertThat(hexOf(textFillOf(cell)))
                        .as("选中行文字色必须是 ON_ACCENT（%s）", theme)
                        .isEqualTo(normalize(Palette.ON_ACCENT));
                assertThat(hexOf(textFillOf(cell)))
                        .as("文字色与背景色不能相同（%s）——「看不见但也没完全隐藏」就是这个", theme)
                        .isNotEqualTo(hexOf(backgroundOf(cell)));
            } finally {
                onUi(() -> stageRef.get().close());
            }
        }
    }

    /**
     * 反复读状态标签，直到后台虚拟线程的值经桥接落到 UI 线程。
     *
     * <p><strong>只在发消息之前调用</strong>：状态标签会被 {@code setBusy(true)} 覆写成
     * 「生成中…（Esc 中断）」，而 CI 里通常没有配置供应商、流不会结束——那个值回不来。</p>
     */
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
    /**
     * 设置面板：两个 tab（界面 / 升级）+ 升级 tab 的契约控件（issue #51 §3.1）。
     *
     * <p>断言走 {@code byId}：点号节点 id 会被 CSS 当成类选择器，`lookup("#aha.upgrade.list")`
     * 实测全返回 null。tab 本身不是节点，所以从 {@code TabPane.getTabs()} 取 id 断言 ——
     * 这不依赖皮肤（没有场景时皮肤不建，未选中的 tab 内容也不在节点树上）。</p>
     *
     * @param tempDir 临时目录（设置文件与升级暂存都放这里，不碰真实家目录）
     */
    @Test
    void settingsDialogHasBothTabsAndTheUpgradeControls(@TempDir Path tempDir) throws Exception {
        DesktopSettings settings = DesktopSettings.load(tempDir.resolve(DesktopSettings.FILE_NAME));
        UpgradePane pane = uiGet(() -> new UpgradePane(settings, new PlatformFxDispatcher(),
                () -> { }, new UpdateService(UpdatePaths.at(tempDir.resolve("update")))));
        Dialog<ButtonType> dialog = uiGet(() -> new SettingsDialog(settings, () -> "配置：测试",
                () -> { }, theme -> { }, size -> { }, pane).build());
        // byId 的签名是 (Parent, String)：对话框内容根是 fxml 里的 VBox，这里显式转成 Parent
        Parent content = (Parent) dialog.getDialogPane().getContent();

        assertThat(dialog.isResizable()).as("设置面板的宽高要能调节（真机反馈）").isTrue();
        TabPane tabs = (TabPane) byId(content, SettingsDialog.TABS_ID);
        assertThat(tabs).as("设置面板必须是 TabPane").isNotNull();
        assertThat(tabs.getMaxHeight()).as("拉大窗口时 tab 内容要跟着长，而不是留白")
                .isEqualTo(Double.MAX_VALUE);
        // 关闭按钮策略必须在 **Java 里**设：FXML 写枚举属性在原生镜像里会 Unable to coerce
        // （2026-10-16 真机自测发现；这条断言同时钉住「改了哪一边」）
        assertThat(tabs.getTabClosingPolicy())
                .as("tab 的关闭按钮必须隐藏，且是在 Java 里设的")
                .isEqualTo(TabPane.TabClosingPolicy.UNAVAILABLE);
        assertThat(tabs.getTabs()).extracting(Tab::getId)
                .containsExactly(SettingsDialog.INTERFACE_TAB_ID, UpgradePane.ID_TAB);

        // 界面 tab 的原有三块设置仍在（纯重构，行为不回退）。
        // 注意：Tab 的内容**不在** TabPane 的节点子树里（要等皮肤物化才挂上去），
        // 所以从 Tab.getContent() 取，而不是从 TabPane 往下找。
        Parent interfaceContent = (Parent) tabs.getTabs().get(0).getContent();
        assertThat(byId(interfaceContent, SettingsDialog.THEME_ID)).isNotNull();
        assertThat(byId(interfaceContent, SettingsDialog.FONT_ID)).isNotNull();
        assertThat(byId(interfaceContent, SettingsDialog.MODEL_ID)).isNotNull();

        // 升级 tab 的契约控件：断言它自己的根节点（不依赖 tab 是否选中）
        VBox upgradeRoot = uiGet(pane::build);
        assertThat(byId(upgradeRoot, UpgradePane.ID_CHANNEL)).isNotNull();
        // 通道区：标题说明这三个选项是干什么的，且**与选项同一行**（标题在左、选项在右）。
        // 真机反馈（2026-10-16）：标题原先独占一行、下面才是选项，两行看不出是一件事；
        // 标题里的括号说明也是多余的。
        assertThat(byId(upgradeRoot, UpgradePane.ID_CHANNEL_TITLE))
                .as("通道区要有标题").isInstanceOf(Label.class);
        Label channelTitle = (Label) byId(upgradeRoot, UpgradePane.ID_CHANNEL_TITLE);
        assertThat(channelTitle.getText()).as("标题文案").isEqualTo("版本发布通道");
        assertThat(channelTitle.getParent()).as("标题与选项必须在同一行").isInstanceOf(HBox.class);
        assertThat(((HBox) channelTitle.getParent()).getChildren())
                .as("同一行里要装着三个选项那一组")
                .contains(channelRowOf(upgradeRoot));
        // 「当前版本」那一行也要能复制（真机反馈）：与状态行同一处理 —— 只读 TextArea，能拖选；
        // 右键另有直达入口「复制版本号」。
        assertThat(byId(upgradeRoot, UpgradePane.ID_CURRENT)).isInstanceOf(TextArea.class);
        TextArea currentRow = (TextArea) byId(upgradeRoot, UpgradePane.ID_CURRENT);
        assertThat(currentRow.isEditable()).as("只读，但可选中").isFalse();
        assertThat(uiGet(() -> {
            currentRow.selectAll();
            return currentRow.getSelectedText();
        })).as("当前版本行必须能整个选中（选中就能 Ctrl+C）").isEqualTo(uiGet(currentRow::getText));
        assertThat(hasMenuItem(currentRow.getContextMenu(), UpgradePane.ID_CURRENT_COPY))
                .as("要有「复制版本号」入口").isTrue();
        assertThat(byId(upgradeRoot, UpgradePane.ID_CHECK)).isInstanceOf(Button.class);
        assertThat(byId(upgradeRoot, UpgradePane.ID_LIST)).isInstanceOf(ListView.class);
        // 失败信息就显示在状态行上：**必须能选中**（真机反馈两轮：Label 既选不中也复制不出来），
        // 所以这里断言的是「只读 TextArea」这个类型 + 选中行为，而不是「有没有右键菜单」
        assertThat(byId(upgradeRoot, UpgradePane.ID_STATUS)).isInstanceOf(TextArea.class);
        TextArea upgradeStatus = (TextArea) byId(upgradeRoot, UpgradePane.ID_STATUS);
        assertThat(upgradeStatus.isEditable()).as("只读，但可选中").isFalse();
        assertThat(uiGet(() -> {
            upgradeStatus.setText("状态：失败示例");
            upgradeStatus.selectAll();
            return upgradeStatus.getSelectedText();
        })).as("状态行必须能选中（选中就能 Ctrl+C）").isEqualTo("状态：失败示例");
        assertThat(hasMenuItem(upgradeStatus.getContextMenu(), UpgradePane.ID_STATUS_COPY))
                .as("状态行右键也要能复制（次要入口）").isTrue();
        assertThat(pane.upgradeInfoForCopy()).as("一键复制上下文里的关键几行")
                .contains("形态").contains("通道").contains("状态");
        assertThat(byId(upgradeRoot, UpgradePane.ID_ACTION)).isInstanceOf(Button.class);
    }

    /** 取「三个通道选项」那一组所在的容器（`ID_CHANNEL`）：用来断言它与标题同处一行。 */
    private static Parent channelRowOf(Parent root) {
        return (Parent) byId(root, UpgradePane.ID_CHANNEL);
    }

    private static Node byId(Scene scene, String id) {
        return scene == null ? null : byId(scene.getRoot(), id);
    }

    /**
     * 读「皮肤内部基面」实际画出来的底色（十六进制，便于与 {@link Palette} 的字符串直接比对）。
     *
     * <p>为什么要专门读内部节点：输入类控件的底色由 {@code .content} / {@code .viewport} 绘制，
     * 控件本体的属性与样式串都不能代表屏幕上的颜色（issue #153 的教训）。</p>
     *
     * @param scene    场景
     * @param id       控件 id
     * @param selector 内部节点选择器（如 {@code .content}）
     * @return {@code #RRGGBB}；取不到时返回 {@code null}
     */
    private static String skinSurface(Scene scene, String id, String selector) {
        Node node = byId(scene, id);
        if (node == null) {
            return null;
        }
        // lookup 的结果只在 CSS 处理之后有效（JavaFX 的约定）
        node.applyCss();
        if (!(node.lookup(selector) instanceof Region region)) {
            return null;
        }
        Background background = region.getBackground();
        if (background == null || background.getFills().isEmpty()
                || !(background.getFills().get(0).getFill() instanceof Color color)) {
            return null;
        }
        return String.format("#%02X%02X%02X",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    /**
     * 把 {@link Palette} 里的色值串规范成 {@code #RRGGBB}（大写，便于与本测试的读数比对）。
     *
     * @param hex 色值串（如 {@code #1E1E1E}）
     * @return 规范化后的串
     */
    private static String normalize(String hex) {
        return hex == null ? null : hex.trim().toUpperCase();
    }

    /** 本测试自建的列表 id（不与生产里的 id 冲突）。 */
    private static final String LIST_ID = "aha.smoke.list";

    /**
     * 等列表把选中格建出来（虚拟流是懒建的，第一帧之后才有单元格）。
     *
     * <p>每一轮都经 {@code onUi} 往返一次，顺便把绘制脉冲让给 JavaFX。</p>
     *
     * @param stage 已显示的窗口
     * @return 选中的单元格；始终建不出来时返回 {@code null}
     * @throws Exception 取不到 UI 线程结果时抛出
     */
    private static Region awaitSelectedCell(Stage stage) throws Exception {
        for (int round = 0; round < POLL_ROUNDS; round++) {
            Region cell = uiGet(() -> selectedCell(stage));
            if (cell != null) {
                return cell;
            }
        }
        return null;
    }

    private static Region selectedCell(Stage stage) {
        if (stage == null || stage.getScene() == null) {
            return null;
        }
        Parent root = stage.getScene().getRoot();
        root.applyCss();
        root.layout();
        for (Node node : root.lookupAll(".list-cell")) {
            if (node instanceof ListCell<?> cell && cell.isSelected() && cell.getBackground() != null) {
                return cell;
            }
        }
        return null;
    }

    private static Color backgroundOf(Region region) {
        Background background = region.getBackground();
        if (background == null || background.getFills().isEmpty()) {
            return null;
        }
        return background.getFills().get(0).getFill() instanceof Color color ? color : null;
    }

    private static Color textFillOf(Region cell) {
        Node text = cell.lookup(".text");
        return text instanceof Text node && node.getFill() instanceof Color color ? color : null;
    }

    private static String hexOf(Color color) {
        return color == null ? null : String.format("#%02X%02X%02X",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }

    /**
     * 读控件**实际生效的**边框色（{@code Region.getBorder()}，不是样式串）。
     *
     * <p>issue #229 的判据落在它上面：原生镜像下补丁样式表里那条 {@code -fx-border-color}
     * 对 {@code TextArea} 不可靠，因此轮廓线必须由属性 API 落到这个属性上。</p>
     *
     * @param scene 场景
     * @param id    控件 id
     * @return {@code #RRGGBB}；没有边框时返回 {@code null}
     */
    private static String borderColor(Scene scene, String id) {
        Node node = byId(scene, id);
        if (!(node instanceof Region region) || region.getBorder() == null
                || region.getBorder().getStrokes().isEmpty()) {
            return null;
        }
        Paint stroke = region.getBorder().getStrokes().get(0).getTopStroke();
        if (!(stroke instanceof Color color)) {
            return null;
        }
        // 就地格式化（不依赖别处的小工具，避免分支间交叉引用）
        return String.format("#%02X%02X%02X",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
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

    /** 等消息流里出现某段文字（启动收尾在虚拟线程上跑，结果回界面线程显示）。 */
    private static String awaitMessagesText(Stage stage, String needle) throws Exception {
        String text = "";
        for (int round = 0; round < POLL_ROUNDS; round++) {
            text = uiGet(() -> messagesText((VBox) byId(stage.getScene(), DesktopShell.MESSAGES_ID)));
            if (text.contains(needle)) {
                return text;
            }
            Thread.sleep(100);
        }
        return text;
    }

    /** 把消息流里的所有 Label 文本拼起来（断言「界面到底说了什么」）。 */
    private static String messagesText(VBox messages) {
        return messages == null ? "" : DesktopShell.collectText(messages);
    }

    /** 子树里有没有带右键菜单的控件（消息流里每条消息都该有「复制这条」）。 */
    private static boolean anyControlHasContextMenu(Parent parent) {
        for (Node node : parent.getChildrenUnmodifiable()) {
            if (node instanceof javafx.scene.control.Control control && control.getContextMenu() != null) {
                return true;
            }
            if (node instanceof Parent child && anyControlHasContextMenu(child)) {
                return true;
            }
        }
        return false;
    }

    /** 菜单里有没有指定 id 的那一项。 */
    private static boolean hasMenuItem(ContextMenu menu, String id) {
        if (menu == null) {
            return false;
        }
        for (MenuItem item : menu.getItems()) {
            if (id.equals(item.getId())) {
                return true;
            }
        }
        return false;
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
        // 关掉「最后一个窗口关闭即退出工具箱」这个隐式行为：
        // 本套件里有用例只开自己的窗口（例如 issue #227 的选中行配色用例），窗口一关、
        // 它就成了「最后一个窗口」，默认行为会把整个工具箱结束掉；紧随其后的用例
        // 会卡在 `Platform.runLater` 上直到超时（2026-10-15 实测：
        // `windowStartsAndBridgeReachesUiThread` 在 `onUi` 等了 30s 后失败，
        //  而先跑的 `listSelectionSurfaceIsReadableInBothThemes` 正常）。
        // 放在共享入口里，不管 JUnit 先跑哪个用例都不会再被这个坑绊到。
        onUi(() -> Platform.setImplicitExit(false));
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
    /**
     * 启动收尾必须真的接线（issue #51 审查 P1-5）：回执要出现在消息流里，暂存要被清掉。
     *
     * <p>为什么用整台 App 来测：这两件事以前在**生产代码里没有调用点**，单元测试各自都是绿的，
     * 用户却什么都看不到、磁盘也一直在涨。所以这里注入 {@code AHA_HOME}，把回执与残留暂存
     * 摆好，再启动真实 App，断言「消息流里那句话」与「暂存目录消失」。</p>
     *
     * @param tempDir 临时目录（充当 AHA_HOME，不碰真实家目录）
     */
    @Test
    void startupShowsTheLastSwapResultAndCleansStaging(@TempDir Path tempDir) throws Exception {
        startToolkit();
        Path home = tempDir.resolve("aha-home");
        Path updateRoot = home.resolve("update");
        AhaVersion leftover = AhaVersion.parse("0.1.3.00241");
        Path leftoverDir = updateRoot.resolve(leftover.value());
        Files.createDirectories(leftoverDir);
        Files.writeString(leftoverDir.resolve("pkg.zip"), "leftover");
        // 清理有**年龄下限**（10 分钟内动过的一律不删），所以这里把残留做成「20 分钟前」的
        ageEverything(leftoverDir, Duration.ofMinutes(20));
        Files.writeString(updateRoot.resolve("update-result.json"), """
                {"ok":true,"version":"0.1.3.00242","previousVersion":"0.1.3.00241","reason":"",
                 "rolledBack":false,"at":"2026-10-16T03:30:00Z"}
                """);
        String previousHome = System.getProperty("AHA_HOME");
        System.setProperty("AHA_HOME", home.toString());
        try {
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

            String text = awaitMessagesText(stage, "上次换装结果");
            assertThat(text).as("启动时必须把上次换装结果告诉用户")
                    .contains("上次换装结果").contains("0.1.3.00242");
            // 真机反馈「报错只能手打」：消息流里每条消息都要能右键复制（这里正好有一条）
            assertThat(uiGet(() -> anyControlHasContextMenu(
                    (VBox) byId(stage.getScene(), DesktopShell.MESSAGES_ID))))
                    .as("消息流里的每条消息都要能右键复制").isTrue();
            assertThat(updateRoot.resolve("update-result.json")).as("回执读完即消费").doesNotExist();
            assertThat(updateRoot.resolve(leftover.value())).as("残留暂存必须被清掉").doesNotExist();
        } finally {
            if (previousHome == null) {
                System.clearProperty("AHA_HOME");
            } else {
                System.setProperty("AHA_HOME", previousHome);
            }
        }
    }

    /**
     * 换装可能还在跑时，启动收尾**什么都不动**，并把这件事说出来（第二轮审查 P1-②）。
     *
     * <p>背景：换装那几秒里用户再启动一次 App，helper 正要从 {@code <版本>/new} 拷文件、
     * 回滚还要用 {@code backup/}；以前这里无条件清，会把本次升级毁掉，而回执把锅甩给包。</p>
     *
     * @param tempDir 临时目录（不碰真实家目录）
     */
    @Test
    void startupLeavesAnInFlightSwapAloneAndSaysSo(@TempDir Path tempDir) throws Exception {
        startToolkit();
        Path home = tempDir.resolve("aha-home");
        Path updateRoot = home.resolve("update");
        AhaVersion inFlight = AhaVersion.parse("0.1.3.00243");
        Files.createDirectories(updateRoot.resolve(inFlight.value()).resolve("new"));
        Files.writeString(updateRoot.resolve(inFlight.value()).resolve("pkg.zip"), "in-flight");
        Files.writeString(updateRoot.resolve("update-plan.json"), "{}");
        Files.writeString(updateRoot.resolve("helper-started"), "2026-10-16T03:30:00Z");
        String previousHome = System.getProperty("AHA_HOME");
        System.setProperty("AHA_HOME", home.toString());
        try {
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

            String text = awaitMessagesText(stage, "可能还在进行");
            assertThat(text).as("必须告诉用户「本次没动它」").contains("可能还在进行");
            assertThat(updateRoot.resolve(inFlight.value())).as("helper 还要从这里拷文件")
                    .exists();
            assertThat(updateRoot.resolve("update-plan.json")).as("helper 可能还没读计划")
                    .exists();
        } finally {
            if (previousHome == null) {
                System.clearProperty("AHA_HOME");
            } else {
                System.setProperty("AHA_HOME", previousHome);
            }
        }
    }

    /**
     * 检查进行中切通道：「检查更新」必须**立刻**可用（真机反馈的「切了通道后点不动」）。
     *
     * <p>做法：把升级服务指向一个**接了就不回话**的本机 socket，于是「检查在跑」这个状态是
     * 确定的（不是碰运气）；切完通道后立刻断言按钮可用，再让那个 socket 放行、断言按钮**仍然**可用
     * （旧通道那次检查回来也不许把界面糊住）。全程不需要外网。</p>
     *
     * @param tempDir 临时目录（设置文件与暂存都放这里）
     */
    @Test
    void switchingChannelReleasesTheCheckButtonWhileACheckIsRunning(@TempDir Path tempDir)
            throws Exception {
        startToolkit();
        try (ServerSocket hold = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread holder = Thread.ofVirtual().start(() -> {
                try (Socket socket = hold.accept()) {
                    socket.getInputStream().readNBytes(1);   // 拿到请求就挂着：检查会一直「在跑」
                    Thread.sleep(800);
                } catch (IOException | InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            DesktopSettings settings = DesktopSettings.load(tempDir.resolve(DesktopSettings.FILE_NAME));
            UpdateService slow = new UpdateService(UpdatePaths.at(tempDir.resolve("update")),
                    GitHubReleaseClient.forApiBase(
                            URI.create("http://127.0.0.1:" + hold.getLocalPort() + "/")),
                    new PackageDownloader());
            UpgradePane pane = uiGet(() -> new UpgradePane(settings, new PlatformFxDispatcher(),
                    () -> { }, slow));
            VBox root = uiGet(pane::build);
            Button check = (Button) byId(root, UpgradePane.ID_CHECK);
            Button action = (Button) byId(root, UpgradePane.ID_ACTION);
            TextArea status = (TextArea) byId(root, UpgradePane.ID_STATUS);
            RadioButton stable = (RadioButton) byId(root, UpgradePane.ID_CHANNEL_STABLE);

            uiGet(() -> {
                check.fire();
                return null;
            });
            assertThat(uiGet(check::isDisabled)).as("检查期间按钮应置灰（状态确实在跑）").isTrue();

            uiGet(() -> {
                stable.setSelected(true);
                return null;
            });

            assertThat(uiGet(check::isDisabled))
                    .as("切了通道就该能立刻重新点「检查更新」，不必等旧检查跑完").isFalse();
            assertThat(uiGet(() -> status.getText())).contains("已切到");
            assertThat(uiGet(action::isDisabled)).isTrue();

            holder.interrupt();
            // 那次旧检查回来（连接断了）也不许把界面糊住：稍等片刻，按钮仍然可用
            Thread.sleep(1200);
            assertThat(uiGet(check::isDisabled)).as("旧检查收尾后按钮仍然可用").isFalse();
        }
    }

    /**
     * 刚出炉的残留**刻意不删**，并如实告诉用户（清理判据的第二道保险）。
     *
     * <p>为什么：换装真正在用的 `pkg.zip` / `new/` / 备份都是刚创建的，而「换装还在不在跑」
     * 只能靠进程号判断（还可能判断失误）。按文件年龄兜一层最硬 —— 代价只是残留多留十几分钟。
     * 这条在界面上必须说出来，否则用户会以为东西丢了。</p>
     *
     * @param tempDir 临时目录（不碰真实家目录）
     */
    @Test
    void startupKeepsRecentLeftoversAndSaysSo(@TempDir Path tempDir) throws Exception {
        startToolkit();
        Path home = tempDir.resolve("aha-home");
        Path updateRoot = home.resolve("update");
        AhaVersion recent = AhaVersion.parse("0.1.3.00243");
        Files.createDirectories(updateRoot.resolve(recent.value()));
        Files.writeString(updateRoot.resolve(recent.value()).resolve("pkg.zip"), "just-downloaded");
        String previousHome = System.getProperty("AHA_HOME");
        System.setProperty("AHA_HOME", home.toString());
        try {
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

            String text = awaitMessagesText(stage, "没动");
            assertThat(text).as("跳过太新的残留必须说出来").contains("没动");
            assertThat(updateRoot.resolve(recent.value())).as("刚出炉的残留刻意不删").exists();
        } finally {
            if (previousHome == null) {
                System.clearProperty("AHA_HOME");
            } else {
                System.setProperty("AHA_HOME", previousHome);
            }
        }
    }

    /** 把一棵子树里所有条目的修改时间都设成「now - age」（测试年龄下限用）。 */
    private static void ageEverything(Path root, Duration age) throws IOException {
        Instant when = Instant.now().minus(age);
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path each : walk.toList()) {
                Files.setLastModifiedTime(each, FileTime.from(when));
            }
        }
    }

    /**
     * 本平台没实现换装脚本时，动作按钮必须**提前**置灰并说明原因（issue #51 审查 P1-7）。
     *
     * <p>以前是「下完 55 MB 才报该包不完整」——把锅甩给包，还白等一场。</p>
     *
     * @param tempDir 临时目录（设置文件与暂存都放这里）
     */
    @Test
    void upgradeTabGatesInAppUpdateWhereNoHelperExists(@TempDir Path tempDir) throws Exception {
        Assumptions.assumeFalse(InstallLayout.isWindows(),
                "Windows 上换装 helper 是齐的，本用例只针对没有 helper 的平台");
        DesktopSettings settings = DesktopSettings.load(tempDir.resolve(DesktopSettings.FILE_NAME));
        UpgradePane pane = uiGet(() -> new UpgradePane(settings, new PlatformFxDispatcher(),
                () -> { }, new UpdateService(UpdatePaths.at(tempDir.resolve("update")))));
        VBox root = uiGet(pane::build);

        Button action = (Button) byId(root, UpgradePane.ID_ACTION);
        TextArea status = (TextArea) byId(root, UpgradePane.ID_STATUS);
        assertThat(action.isDisabled()).as("没有换装 helper 的平台，动作按钮必须置灰").isTrue();
        assertThat(status.getText()).as("置灰的同时要说清为什么、以及怎么办")
                .contains("Windows").contains("发布页");

        // 置灰要有**看得见**的原因：真机反馈过一次「按钮坏了」——门生效时选中任何版本也不会
        // 变可点（设计如此），而原因只写在底部灰色状态行里，容易被忽略。
        Label notice = (Label) byId(root, UpgradePane.ID_NOTICE);
        assertThat(notice.isVisible()).as("门生效时提示必须可见").isTrue();
        assertThat(notice.isManaged()).as("可见的提示要占位（不是藏起来还留着空隙）").isTrue();
        assertThat(notice.getText()).as("提示要说清为什么 + 列表只是看看")
                .contains("不支持应用内升级").contains("只是让你看看有哪些版本");
        // 「检查更新」在**非原生形态**的门下仍然可用（便于看有哪些版本，据此去发布页手动取）——
        // 这条是既有决定，别被顺手改掉。（原生镜像那条分支会连列表一起禁用，见 UpgradePane；
        // 它只能在原生包里验，属真机验收项。）
        assertThat(((Button) byId(root, UpgradePane.ID_CHECK)).isDisabled())
                .as("非原生形态的门生效时，「检查更新」仍可用").isFalse();
    }

}
