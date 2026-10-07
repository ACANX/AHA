package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.common.AppVersion;
import com.acanx.module.aha.common.model.ToolDescriptor;
import com.acanx.module.aha.common.tool.ToolKind;
import com.acanx.module.aha.desktop.chat.ChatView;
import com.acanx.module.aha.desktop.fx.FxBridge;
import com.acanx.module.aha.desktop.fx.FxDispatcher;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 桌面端界面骨架：菜单栏 + 三栏 + 底部状态栏。
 *
 * <p>形态口径见 {@code GUIDesign.md} 第 2 节：单窗口、三栏 + 底部状态栏，
 * 「左栏管去哪、中栏管聊什么、右栏管现在什么情况」。本类只负责**搭骨架与接线**，
 * 不碰 Agent：一条消息发出去只落到本地消息流，并明确提示尚未接入模型。</p>
 *
 * <p><strong>折叠</strong>：左右两栏都能折叠，且**三条路都能走**——
 * 菜单（视图 → 折叠左栏 / 折叠右栏）、快捷键（{@code Ctrl+B} / {@code Ctrl+J}）、
 * 以及栏边的**常驻窄条按钮**。故意保留常驻窄条而不是把整列藏没：只留快捷键的话，
 * 鼠标用户折叠后就没有任何办法展开。三条路最终都调用同一对方法，
 * 不存在「菜单能用、按钮不能用」的分叉。</p>
 *
 * <p>本类不调用 {@code Platform} 的任何线程 API（线程契约由
 * {@code FxThreadContractTest} 扫描钉住）。</p>
 *
 * @since 0.2.0
 */
public final class DesktopShell implements ChatView {

    /** 菜单栏 id。 */
    public static final String MENU_BAR_ID = "aha.menubar";

    /** 左栏容器 id。 */
    public static final String LEFT_ID = "aha.left";

    /** 左栏折叠手柄 id（常驻，可点）。 */
    public static final String LEFT_TOGGLE_ID = "aha.left.toggle";

    /** 左栏内容区 id。 */
    public static final String LEFT_CONTENT_ID = "aha.left.content";

    /** 中栏容器 id。 */
    public static final String CENTER_ID = "aha.center";

    /** 消息流容器 id。 */
    public static final String MESSAGES_ID = "aha.messages";

    /** 输入区 id。 */
    public static final String COMPOSER_ID = "aha.composer";

    /** 发送按钮 id。 */
    public static final String SEND_ID = "aha.send";

    /** 右栏容器 id。 */
    public static final String RIGHT_ID = "aha.right";

    /** 右栏折叠手柄 id（常驻，可点）。 */
    public static final String RIGHT_TOGGLE_ID = "aha.right.toggle";

    /** 右栏内容区 id。 */
    public static final String RIGHT_CONTENT_ID = "aha.right.content";

    /** 底部状态栏 id。 */
    public static final String STATUS_BAR_ID = "aha.statusbar";

    /** 底部状态栏里「后台状态」标签的 id（由桥接更新）。 */
    public static final String STATUS_ID = "aha.status";

    /** 配置摘要标签 id（D-11 的真机自证）。 */
    public static final String CONFIG_ID = "aha.config";

    /** 用量标签 id。 */
    public static final String USAGE_ID = "aha.usage";

    /** 空会话状态节点 id（标志 + 提示，出现首条消息时整体移除）。 */
    public static final String EMPTY_ID = "aha.empty";

    /** 标志节点 id。 */
    public static final String LOGO_ID = "aha.logo";

    /** 消息流为空时的提示。 */
    public static final String EMPTY_HINT = "还没有对话。在下面输入，Enter 发送。";

    /** 状态标签初始文案。 */
    public static final String STATUS_INITIAL = "正在启动…";

    /** 输入区占位提示（与设计稿一致）。 */
    public static final String COMPOSER_PROMPT =
            "继续输入…（Enter 发送 / Shift+Enter 换行，/ 命令，@ 引用文件）";


    /** UI 线程投递器（助手流式文本的合流要用它）。 */
    private final FxDispatcher dispatcher;

    /** 退出动作（由宿主提供，通常是关窗）。 */
    private final Runnable onExit;

    /** 配置摘要提供者（帮助 → 关于 里展示）。 */
    private final Supplier<String> configSummary;

    private final VBox messages = new VBox(ShellLayout.GAP);

    /** 空态节点；首条消息到来时整体移除，清空对话后重建。 */
    private VBox emptyState;

    private final Label status = new Label(STATUS_INITIAL);

    private final Label config = new Label();

    private final TextArea composer = new TextArea();

    private final VBox leftContent;

    private final VBox rightContent;

    /** 助手流式文本的合流器：每个 token 一次界面更新会拖慢界面，这里合并成每帧一次。 */
    private FxBridge<String> assistantBridge;

    /** 正在流式写入的助手消息（本轮）。 */
    private Label currentAssistant;

    /** 本轮助手文本缓冲。 */
    private final StringBuilder assistantBuffer = new StringBuilder();

    /** 工具卡片的状态行（句柄 = 下标 + 1）。 */
    private final List<Label> toolStatus = new ArrayList<>();

    /** 发送按钮（生成中禁用）。 */
    private Button sendButton;

    /** 底栏用量标签。 */
    private Label usageLabel;

    /** 发送动作（由宿主接到对话内核）。 */
    private Consumer<String> onSend = text -> { };

    /** 中断动作。 */
    private Runnable onCancel = () -> { };

    /** 新建会话动作。 */
    private Runnable onNewSession = () -> { };

    /** 工具来源（工具列表对话框用）。 */
    private Supplier<List<ToolDescriptor>> toolSource = List::of;

    /** 供应商对话框。 */
    private ProviderDialog providerDialog;

    private final HBox leftBox;

    private final HBox rightBox;

    private final CheckMenuItem collapseLeft;

    private final CheckMenuItem collapseRight;

    private final Button leftToggle = new Button();

    private final Button rightToggle = new Button();

    /** 左栏折叠状态（菜单 / Ctrl+B / 窄条按钮共用）。 */
    private final FoldState leftFold = new FoldState("左栏", ShellLayout.LEFT_WIDTH, true);

    /** 右栏折叠状态；默认按「本轮是否有数据」决定，空会话时收起。 */
    private final FoldState rightFold =
            new FoldState("右栏", ShellLayout.RIGHT_WIDTH, ShellLayout.rightPaneVisibleByDefault(false));

    /**
     * @param onExit        退出动作
     * @param configSummary 配置摘要（帮助 → 关于 用）
     */
    public DesktopShell(FxDispatcher dispatcher, Runnable onExit, Supplier<String> configSummary) {
        this.dispatcher = dispatcher;
        this.onExit = onExit;
        this.configSummary = configSummary;
        this.collapseLeft = new CheckMenuItem("折叠左栏");
        this.collapseRight = new CheckMenuItem("折叠右栏");
        this.leftContent = buildLeftContent();
        this.rightContent = buildRightContent();
        this.leftBox = buildLeftColumn();
        this.rightBox = buildRightColumn();
        this.assistantBridge = new FxBridge<>(dispatcher, text -> {
            if (currentAssistant != null) {
                currentAssistant.setText(text);
            }
        });
        applyPaneState();
    }

    /**
     * 构建窗口根节点。
     *
     * @return 根节点
     */
    public Parent buildRoot() {
        BorderPane root = new BorderPane();
        root.setStyle(darkTheme());
        root.setTop(new VBox(buildMenuBar()));
        root.setCenter(buildMiddleRow());
        root.setBottom(buildStatusBar());
        return root;
    }

    /**
     * 折叠 / 展开左栏（菜单、快捷键、窄条按钮共用）。
     *
     * @return 折叠后的展开状态
     */
    public boolean toggleLeft() {
        boolean expanded = leftFold.toggle();
        applyPaneState();
        return expanded;
    }

    /**
     * 折叠 / 展开右栏（菜单、快捷键、窄条按钮共用）。
     *
     * @return 折叠后的展开状态
     */
    public boolean toggleRight() {
        boolean expanded = rightFold.toggle();
        applyPaneState();
        return expanded;
    }

    /**
     * 左栏是否展开。
     *
     * @return 展开返回 {@code true}
     */
    public boolean isLeftExpanded() {
        return leftFold.isExpanded();
    }

    /**
     * 右栏是否展开。
     *
     * @return 展开返回 {@code true}
     */
    public boolean isRightExpanded() {
        return rightFold.isExpanded();
    }

    /**
     * 状态标签（供 {@code FxBridge} 在 UI 线程更新）。
     *
     * @return 状态标签
     */
    public Label statusLabel() {
        return status;
    }

    /**
     * 设置配置摘要文本（D-11：改 Aha.yaml 后这里要跟着变）。
     *
     * @param text 摘要文本
     */
    public void setConfigSummary(String text) {
        config.setText(text);
    }

    /**
     * 把输入区当前内容作为用户消息发出。
     *
     * <p>尚未接入 Agent：只落到本地消息流并追加一条说明，避免让人误以为已发给模型。</p>
     *
     * @return 有内容并已发送返回 {@code true}；输入为空返回 {@code false}
     */
    public boolean submitComposer() {
        String text = composer.getText() == null ? "" : composer.getText().strip();
        if (text.isEmpty()) {
            return false;
        }
        composer.clear();
        // 真正的发送交给对话内核（ChatController）；界面只负责把文本递出去
        onSend.accept(text);
        return true;
    }

    /**
     * 清空对话流。
     */
    public void clearConversation() {
        messages.getChildren().clear();
        messages.getChildren().add(createEmptyState());
    }

    /**
     * 当前消息流里的节点数（含空态提示）。
     *
     * @return 节点数
     */
    public int messageNodeCount() {
        return messages.getChildren().size();
    }

    /**
     * 输入区（测试与「聚焦输入」用）。
     *
     * @return 输入框
     */
    public TextArea composer() {
        return composer;
    }

    // ---------------------------------------------------------------- 接线

    /**
     * 设置发送动作（由宿主接到 ChatController）。
     *
     * @param handler 动作
     */
    public void onSend(Consumer<String> handler) {
        this.onSend = handler == null ? text -> { } : handler;
    }

    /**
     * 设置中断动作。
     *
     * @param handler 动作
     */
    public void onCancel(Runnable handler) {
        this.onCancel = handler == null ? () -> { } : handler;
    }

    /**
     * 设置「新建会话」动作。
     *
     * @param handler 动作
     */
    public void onNewSession(Runnable handler) {
        this.onNewSession = handler == null ? () -> { } : handler;
    }

    /**
     * 设置工具来源（工具列表对话框用）。
     *
     * @param source 工具来源
     */
    public void tools(Supplier<List<ToolDescriptor>> source) {
        this.toolSource = source == null ? List::of : source;
    }

    /**
     * 设置供应商对话框。
     *
     * @param dialog 对话框
     */
    public void providerDialog(ProviderDialog dialog) {
        this.providerDialog = dialog;
    }

    /**
     * 把键盘焦点交给输入框（开窗后调用）。
     *
     * <p>聊天类应用启动后应当能直接打字；不设这一步，焦点会落在左栏第一个按钮上，
     * 用户敲的第一个空格就把那个按钮按下去了（真机验证时踩到）。</p>
     */
    public void focusComposer() {
        composer.requestFocus();
    }

    /**
     * 关闭内部桥接（关窗时调用）。
     */
    public void closeBridges() {
        if (assistantBridge != null) {
            assistantBridge.close();
        }
    }

    // ------------------------------------------------------- ChatView 实现

    @Override
    public void appendUser(String text) {
        appendMessage("你", text, Palette.READ);
    }

    @Override
    public void appendAssistant(String delta) {
        if (currentAssistant == null) {
            streamBufferReset();
            // 先建标签再提交文本：两者进的是同一个 UI 队列，顺序有保证
            dispatcher.dispatch(() -> {
                currentAssistant = new Label();
                currentAssistant.setWrapText(true);
                currentAssistant.setStyle("-fx-text-fill: " + Palette.FOREGROUND + ";");
                addRow("助手", currentAssistant, Palette.SUCCESS);
            });
        }
        assistantBuffer.append(delta);
        // 每个 token 一次界面更新会拖慢界面；FxBridge 合并成每帧至多一次
        assistantBridge.submit(assistantBuffer.toString());
    }

    @Override
    public void endAssistant() {
        currentAssistant = null;
        assistantBuffer.setLength(0);
    }

    @Override
    public void appendNotice(String text) {
        appendMessage("系统", text, Palette.MUTED);
    }

    @Override
    public void showError(String code, String message) {
        appendMessage("错误 " + code, message == null ? "" : message, Palette.FAILURE);
    }

    @Override
    public int beginToolCall(String kindLabel, String toolName, String target) {
        String accent = Palette.forToolKind(ToolKind.of(toolName));
        Label head = new Label(kindLabel + "  " + toolName
                + (target == null || target.isBlank() ? "" : "  " + target));
        head.setStyle("-fx-text-fill: " + accent + ";");
        Label state = new Label("运行中…");
        state.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        VBox card = new VBox(2, head, state);
        card.setStyle("-fx-background-color: " + Palette.BLOCK_BACKGROUND
                + "; -fx-background-radius: 6; -fx-padding: 6;");
        removeEmptyState();
        messages.getChildren().add(card);
        toolStatus.add(state);
        return toolStatus.size();
    }

    @Override
    public void finishToolCall(int handle, boolean success, String summary, String output) {
        int index = handle - 1;
        if (index < 0 || index >= toolStatus.size()) {
            return;
        }
        Label state = toolStatus.get(index);
        state.setText(summary);
        state.setStyle("-fx-text-fill: " + (success ? Palette.SUCCESS : Palette.FAILURE) + ";");
        if (output != null && !output.isBlank()) {
            state.setTooltip(new Tooltip(output.length() > 2000
                    ? output.substring(0, 2000) + "…" : output));
        }
    }

    @Override
    public void setUsage(int promptTokens, int completionTokens) {
        if (usageLabel != null) {
            usageLabel.setText("输入 " + promptTokens + " / 输出 " + completionTokens + " tok");
        }
    }

    @Override
    public void setBusy(boolean busy) {
        if (sendButton != null) {
            sendButton.setDisable(busy);
        }
        status.setText(busy ? "生成中…（Esc 中断）" : "就绪");
    }

    private void streamBufferReset() {
        assistantBuffer.setLength(0);
    }

    private void openProviderDialog() {
        if (providerDialog == null) {
            appendNotice("供应商配置尚不可用（未接线）。");
            return;
        }
        providerDialog.show(composer.getScene() == null ? null : composer.getScene().getWindow(),
                onNewSession);
    }

    private void openToolDialog() {
        new ToolListDialog(toolSource)
                .show(composer.getScene() == null ? null : composer.getScene().getWindow());
    }

    /**
     * 左栏导航按钮。
     *
     * @param text    文案
     * @param action  点击动作
     * @return 按钮
     */
    private static Button navButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setStyle("-fx-background-color: transparent; -fx-text-fill: "
                + Palette.FOREGROUND + ";");
        button.setOnAction(event -> action.run());
        return button;
    }

    /**
     * 暗色主题（{@code GUIDesign.md} 第 3.1 节：暗色优先，亮色作为等价映射）。
     *
     * <p>直接给根节点设 JavaFX 的「被查色」（looked-up colors），子节点自动继承——
     * 不引入额外 CSS 文件，也就不会在 JPMS 下遇到「样式表找不到」的问题。
     * 第一次在 Windows 上启动时没上主题，浅色文字压在浅灰底上几乎看不见。</p>
     *
     * @return 内联样式
     */
    private static String darkTheme() {
        return "-fx-base: #1E1E1E;"
                + "-fx-background: #1E1E1E;"
                + "-fx-control-inner-background: #252526;"
                + "-fx-text-background-color: " + Palette.FOREGROUND + ";"
                + "-fx-accent: " + Palette.FOCUS_BORDER + ";";
    }

    private MenuBar buildMenuBar() {
        MenuBar bar = new MenuBar();
        bar.setId(MENU_BAR_ID);

        Menu file = new Menu("文件(_F)");
        MenuItem exit = new MenuItem("退出");
        exit.setOnAction(event -> onExit.run());
        file.getItems().add(exit);

        // 编辑与工具菜单随功能接入；先保留占位项，避免出现「点了没反应」的假动作
        Menu edit = new Menu("编辑(_E)");
        MenuItem copy = new MenuItem("复制（待接入）");
        copy.setDisable(true);
        edit.getItems().add(copy);

        Menu session = new Menu("会话(_S)");
        MenuItem clear = new MenuItem("清空对话");
        clear.setOnAction(event -> clearConversation());
        session.getItems().add(clear);

        Menu tools = new Menu("工具(_T)");
        MenuItem toolList = new MenuItem("工具列表（待接入）");
        toolList.setDisable(true);
        tools.getItems().add(toolList);

        Menu view = new Menu("视图(_V)");
        collapseLeft.setSelected(!leftFold.isExpanded());
        collapseLeft.setAccelerator(new KeyCodeCombination(KeyCode.B, KeyCombination.SHORTCUT_DOWN));
        collapseLeft.setOnAction(event -> toggleLeft());
        collapseRight.setSelected(!rightFold.isExpanded());
        collapseRight.setAccelerator(new KeyCodeCombination(KeyCode.J, KeyCombination.SHORTCUT_DOWN));
        collapseRight.setOnAction(event -> toggleRight());
        view.getItems().addAll(collapseLeft, collapseRight);

        Menu help = new Menu("帮助(_H)");
        MenuItem about = new MenuItem("关于 AHA");
        about.setOnAction(event -> showAbout());
        help.getItems().add(about);

        bar.getMenus().addAll(file, edit, session, tools, view, help);
        return bar;
    }

    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("关于 AHA");
        alert.setHeaderText(AppVersion.DISPLAY + " 桌面端");
        alert.setContentText(configSummary.get() + "\n\n" + System.getProperty("os.name")
                + " / " + System.getProperty("os.arch") + " · Java " + Runtime.version());
        alert.showAndWait();
    }

    private HBox buildMiddleRow() {
        HBox row = new HBox();
        row.setId(CENTER_ID + ".row");
        row.getChildren().addAll(leftBox, buildCenterColumn(), rightBox);
        HBox.setHgrow(row.getChildren().get(1), Priority.ALWAYS);
        return row;
    }

    private VBox buildLeftContent() {
        VBox box = new VBox(ShellLayout.GAP);
        box.setId(LEFT_CONTENT_ID);
        box.setPadding(new Insets(ShellLayout.GAP));
        box.setPrefWidth(ShellLayout.LEFT_WIDTH);
        box.setMinWidth(ShellLayout.LEFT_WIDTH);
        box.setMaxWidth(ShellLayout.LEFT_WIDTH);
        // 与中栏之间给一条分隔线，否则暗色下三栏会糊成一片
        box.setStyle("-fx-border-color: #3A3A3A; -fx-border-width: 0 1 0 0;");

        TextArea search = new TextArea();
        search.setPromptText("⌕ 搜索（待会话数据接入）");
        search.setPrefRowCount(1);
        search.setDisable(true);

        VBox.setVgrow(new Region(), Priority.ALWAYS);
        box.getChildren().addAll(search,
                navButton("＋ 新建会话", () -> onNewSession.run()),
                navButton("供应商", this::openProviderDialog),
                navButton("工具", this::openToolDialog),
                navButton("记忆（0.2 后续）", () -> appendNotice("记忆面板将在 0.2 后续接入。")),
                navButton("扩展（0.2 后续）", () -> appendNotice("扩展面板将在 0.2 后续接入。")),
                navButton("日志（0.2 后续）", () -> appendNotice("日志面板将在 0.2 后续接入。")),
                spacer(), settings());
        return box;
    }

    private VBox buildRightContent() {
        VBox box = new VBox(ShellLayout.GAP);
        box.setId(RIGHT_CONTENT_ID);
        box.setPadding(new Insets(ShellLayout.GAP));
        box.setPrefWidth(ShellLayout.RIGHT_WIDTH);
        box.setMinWidth(ShellLayout.RIGHT_WIDTH);
        box.setMaxWidth(ShellLayout.RIGHT_WIDTH);
        box.setStyle("-fx-border-color: #3A3A3A; -fx-border-width: 0 0 0 1;");
        Label title = new Label("本轮");
        box.getChildren().addAll(title, emptyNote("暂无本轮数据（用量 / 工具调用 / 记忆命中）"));
        return box;
    }

    private HBox buildLeftColumn() {
        HBox box = new HBox();
        box.setId(LEFT_ID);

        leftToggle.setId(LEFT_TOGGLE_ID);
        leftToggle.setOnAction(event -> toggleLeft());
        leftToggle.setPrefWidth(ShellLayout.TOGGLE_STRIP_WIDTH);
        leftToggle.setMinWidth(ShellLayout.TOGGLE_STRIP_WIDTH);
        leftToggle.setMaxWidth(ShellLayout.TOGGLE_STRIP_WIDTH);
        // 不要拉满整列高度：否则 ‹ 会飘到栏底，看不出它属于哪一栏
        leftToggle.setMaxHeight(Region.USE_PREF_SIZE);

        HBox strip = new HBox(leftToggle);
        strip.setAlignment(Pos.TOP_CENTER);
        strip.setPadding(new Insets(ShellLayout.GAP, 0, 0, 0));
        strip.setPrefWidth(ShellLayout.TOGGLE_STRIP_WIDTH);

        box.getChildren().addAll(strip, leftContent);
        return box;
    }

    private HBox buildRightColumn() {
        HBox box = new HBox();
        box.setId(RIGHT_ID);

        rightToggle.setId(RIGHT_TOGGLE_ID);
        rightToggle.setOnAction(event -> toggleRight());
        rightToggle.setPrefWidth(ShellLayout.TOGGLE_STRIP_WIDTH);
        rightToggle.setMinWidth(ShellLayout.TOGGLE_STRIP_WIDTH);
        rightToggle.setMaxWidth(ShellLayout.TOGGLE_STRIP_WIDTH);
        rightToggle.setMaxHeight(Region.USE_PREF_SIZE);

        HBox strip = new HBox(rightToggle);
        strip.setAlignment(Pos.TOP_CENTER);
        strip.setPadding(new Insets(ShellLayout.GAP, 0, 0, 0));
        strip.setPrefWidth(ShellLayout.TOGGLE_STRIP_WIDTH);

        box.getChildren().addAll(rightContent, strip);
        return box;
    }

    private VBox buildCenterColumn() {
        VBox center = new VBox(ShellLayout.GAP);
        center.setId(CENTER_ID);
        center.setPadding(new Insets(ShellLayout.GAP));

        messages.setId(MESSAGES_ID);
        messages.getChildren().add(createEmptyState());
        ScrollPane scroll = new ScrollPane(messages);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        // 关键：ScrollPane 默认最小高度由内容撑开，会把下面的输入区顶出窗口
        // （第一次启动就是只能看到消息区，输入框不见踪影）。
        scroll.setMinHeight(0);

        center.getChildren().addAll(scroll, buildComposerBox());
        return center;
    }

    private VBox buildComposerBox() {
        composer.setId(COMPOSER_ID);
        composer.setPromptText(COMPOSER_PROMPT);
        composer.setWrapText(true);
        composer.setPrefRowCount(3);
        // Enter 发送、Shift+Enter 换行（若直接放行，TextArea 会把 Enter 当换行）
        composer.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ENTER && !event.isShiftDown()) {
                event.consume();
                submitComposer();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                event.consume();
                onCancel.run();
            }
        });

        sendButton = new Button("发送");
        sendButton.setId(SEND_ID);
        sendButton.setOnAction(event -> submitComposer());

        HBox actions = new HBox(ShellLayout.GAP, sendButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(ShellLayout.GAP, composer, actions);
        return box;
    }

    private HBox buildStatusBar() {
        HBox bar = new HBox(ShellLayout.GAP * 2);
        bar.setId(STATUS_BAR_ID);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(0, ShellLayout.GAP, 0, ShellLayout.GAP));
        bar.setPrefHeight(ShellLayout.STATUS_BAR_HEIGHT);
        bar.setMinHeight(ShellLayout.STATUS_BAR_HEIGHT);
        bar.setMaxHeight(ShellLayout.STATUS_BAR_HEIGHT);

        Label connection = new Label("● 未连接");
        connection.setStyle("-fx-text-fill: " + Palette.FAILURE + ";");
        status.setId(STATUS_ID);
        status.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        config.setId(CONFIG_ID);
        config.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        usageLabel = new Label("");
        usageLabel.setId(USAGE_ID);
        usageLabel.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        Label hint = new Label("[Esc] 中断");
        hint.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        Label version = new Label("v" + AppVersion.version());
        version.setStyle("-fx-text-fill: " + Palette.MUTED + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(connection, status, config, spacer, usageLabel, hint, version);
        return bar;
    }

    /**
     * 把两侧的折叠状态应用到界面。
     *
     * <p>折叠时同时 {@code setVisible(false)} 与 {@code setManaged(false)}：只隐藏不解除布局，
     * 中栏不会因此变宽（设计中「右栏收起时中栏变宽」正是靠这一条）。</p>
     */
    private void applyPaneState() {
        applyFold(leftFold, leftContent, leftToggle);
        applyFold(rightFold, rightContent, rightToggle);
        collapseLeft.setSelected(!leftFold.isExpanded());
        collapseRight.setSelected(!rightFold.isExpanded());
    }

    private static void applyFold(FoldState fold, Region content, Button toggle) {
        content.setVisible(fold.isContentVisible());
        content.setManaged(fold.isContentManaged());
        content.setPrefWidth(fold.expandedWidth());
        toggle.setText(fold.glyph());
        toggle.setTooltip(new Tooltip(fold.tooltip()));
    }

    private void appendMessage(String who, String text, String accent) {
        Label body = new Label(text);
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: " + Palette.FOREGROUND + ";");
        addRow(who, body, accent);
    }

    /**
     * 加一行「左侧竖条 + 标签 + 内容」的消息（与会话流一致，不用气泡）。
     *
     * @param who    说话人标签
     * @param body   内容控件
     * @param accent 强调色
     */
    private void addRow(String who, javafx.scene.Node body, String accent) {
        removeEmptyState();
        Region bar = new Region();
        bar.setMinWidth(2);
        bar.setPrefWidth(2);
        bar.setStyle("-fx-background-color: " + accent + ";");

        Label whoLabel = new Label(who);
        whoLabel.setStyle("-fx-text-fill: " + accent + "; -fx-font-size: 12px;");

        VBox column = new VBox(2, whoLabel, body);
        HBox.setHgrow(column, Priority.ALWAYS);
        HBox row = new HBox(ShellLayout.GAP, bar, column);
        messages.getChildren().add(row);
    }

    private void removeEmptyState() {
        if (emptyState != null) {
            messages.getChildren().remove(emptyState);
            emptyState = null;
        }
    }

    /**
     * 空会话状态：标志 + 一句提示。
     *
     * <p>标志用 {@code Logo.svg} 渲染出的 PNG（见 {@link LogoImage}）。图片缺失时只显示提示，
     * 不让「少一张图」把窗口搞崩。</p>
     *
     * @return 空态节点
     */
    private VBox createEmptyState() {
        VBox box = new VBox(ShellLayout.GAP, emptyNote(EMPTY_HINT));
        box.setId(EMPTY_ID);
        box.setAlignment(Pos.CENTER);
        // 占满宽度再居中，否则整块会贴在右边（父容器默认靠左，但空态宽度只由内容决定）
        box.setMaxWidth(Double.MAX_VALUE);
        ImageView logo = LogoImage.view(LogoImage.DISPLAY_SIZE);
        if (logo != null) {
            logo.setId(LOGO_ID);
            box.getChildren().add(0, logo);
        }
        emptyState = box;
        return box;
    }

    private static Label emptyNote(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        return label;
    }

    private static Label settings() {
        Label label = new Label("⚙ 设置");
        label.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        return label;
    }

    private static Region spacer() {
        Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        return region;
    }
}
