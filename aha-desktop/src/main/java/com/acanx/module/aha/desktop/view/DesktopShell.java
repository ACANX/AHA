package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.common.AppVersion;
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
public final class DesktopShell {

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

    /** 发送后追加的说明：当前尚未接入模型，避免让人误以为「已发送给 AI」。 */
    public static final String NOT_WIRED_HINT =
            "（尚未接入 Agent：0.2 后续会把这条发给模型并流式显示回复）";

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
    public DesktopShell(Runnable onExit, Supplier<String> configSummary) {
        this.onExit = onExit;
        this.configSummary = configSummary;
        this.collapseLeft = new CheckMenuItem("折叠左栏");
        this.collapseRight = new CheckMenuItem("折叠右栏");
        this.leftContent = buildLeftContent();
        this.rightContent = buildRightContent();
        this.leftBox = buildLeftColumn();
        this.rightBox = buildRightColumn();
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
        appendMessage("你", text, Palette.READ);
        appendMessage("系统", NOT_WIRED_HINT, Palette.MUTED);
        composer.clear();
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

        Label sessions = new Label("会话");
        Label memory = new Label("记忆");
        Label tools = new Label("工具");
        Label extensions = new Label("扩展");
        Label providers = new Label("供应商");
        Label logs = new Label("日志");
        VBox.setVgrow(new Region(), Priority.ALWAYS);
        box.getChildren().addAll(search, sessions, emptyNote("暂无会话"),
                memory, tools, extensions, providers, logs, spacer(), settings());
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
            }
        });

        Button send = new Button("发送");
        send.setId(SEND_ID);
        send.setOnAction(event -> submitComposer());

        HBox actions = new HBox(ShellLayout.GAP, send);
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
        Label hint = new Label("[Esc] 中断（待接入）");
        hint.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        Label version = new Label("v" + AppVersion.version());
        version.setStyle("-fx-text-fill: " + Palette.MUTED + ";");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(connection, status, config, spacer, hint, version);
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
        if (emptyState != null) {
            // 首条消息到来：把空态（标志 + 提示）整体撤掉
            messages.getChildren().remove(emptyState);
            emptyState = null;
        }
        Region bar = new Region();
        bar.setMinWidth(2);
        bar.setPrefWidth(2);
        bar.setStyle("-fx-background-color: " + accent + ";");

        Label whoLabel = new Label(who);
        whoLabel.setStyle("-fx-text-fill: " + accent + "; -fx-font-size: 12px;");
        Label body = new Label(text);
        body.setWrapText(true);
        body.setStyle("-fx-text-fill: " + Palette.FOREGROUND + ";");

        VBox column = new VBox(2, whoLabel, body);
        HBox.setHgrow(column, Priority.ALWAYS);
        HBox row = new HBox(ShellLayout.GAP, bar, column);
        messages.getChildren().add(row);
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
