package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.desktop.log.LogBuffer;
import com.acanx.module.aha.desktop.log.LogCapture;
import com.acanx.module.aha.desktop.log.LogLevel;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.io.IOException;

/**
 * 日志面板：实时日志 + 级别过滤（{@code GUIDesign.md} 第 4.5 节「日志（排错用）」）。
 *
 * <p>两个刻意的设计：</p>
 * <ul>
 *   <li><strong>数据源是日志系统的真实输出</strong>，不是重放一遍消息。所以配置里级别是 WARN 时，
 *       面板里就是看不到 DEBUG——这一点写在面板头部，免得被当成 bug；
 *   <li><strong>过滤在读的时候做</strong>（见 {@link LogBuffer}）：把门槛从 WARN 调回 DEBUG，
 *       之前的行还在，不需要「复现一次才看得到」。</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class LogPanel {

    /** 日志文本区 id。 */
    public static final String AREA_ID = "aha.log.text";

    /** 级别过滤下拉 id。 */
    public static final String FILTER_ID = "aha.log.filter";

    /** 刷新按钮 id（也用于测试触发一次渲染）。 */
    public static final String REFRESH_ID = "aha.log.refresh";

    /** 清空按钮 id。 */
    public static final String CLEAR_ID = "aha.log.clear";

    /** 复制按钮 id。 */
    public static final String COPY_ID = "aha.log.copy";

    /** 自动刷新间隔（毫秒）。 */
    public static final long TICK_MS = 500;

    /** 面板里最多渲染多少行（再多没有阅读价值，反而卡界面）。 */
    public static final int MAX_LINES = 3000;

    /** FXML 骨架资源（与本类同包）。 */
    private static final String FXML_VIEW = "log-panel.fxml";

    private final String effectiveLevel;

    // ── FXML 注入的部件 ──────────────────────────────────────────────────────
    // 字段名必须与 log-panel.fxml 的 fx:id 一一对应；原生镜像下这些**私有字段**由
    // FXMLLoader 反射写入，因此 LogPanel 的字段必须登记进 reachability-metadata。

    /** 工具栏 + 日志区所在的根。 */
    @FXML
    private VBox root;

    @FXML
    private HBox toolbar;

    /** 级别过滤下拉。 */
    @FXML
    private ComboBox<LogLevel> filter;

    @FXML
    private Button refresh;

    @FXML
    private Button copy;

    @FXML
    private Button clear;

    /** 把右侧的统计行推到栏尾的撑高垫。 */
    @FXML
    private Region spacer;

    /** 统计行（共 N 行 · 显示 M 行 · 上限 X 行）。 */
    @FXML
    private Label stats;

    /** 只读日志区。 */
    @FXML
    private TextArea area;

    private Timeline ticker;

    private long renderedTotal = -1;

    /**
     * @param effectiveLevel 当前生效的日志级别（配置决定），用于面板头部的说明
     */
    public LogPanel(String effectiveLevel) {
        this.effectiveLevel = effectiveLevel == null ? "未知" : effectiveLevel;
    }

    /**
     * 打开面板（模态）。
     *
     * @param owner 父窗口，可为 {@code null}
     */
    public void show(Window owner) {
        Dialog<ButtonType> dialog = build();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        // 关掉对话框就停掉刷新：否则一个不可见的面板会一直空转
        dialog.setOnHidden(event -> stopTicker());
        dialog.showAndWait();
    }

    /**
     * 构建对话框（不显示；测试可以用它检查控件行为）。
     *
     * @return 对话框
     */
    public Dialog<ButtonType> build() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("日志");
        dialog.setHeaderText("实时日志（配置级别：" + effectiveLevel
                + "）——低于该级别的事件不会被日志系统产生，因此这里也看不到");

        ButtonType close = new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().add(close);
        dialog.getDialogPane().setPrefWidth(1000);
        dialog.getDialogPane().setPrefHeight(640);
        ThemePaint.dialog(dialog.getDialogPane());

        dialog.getDialogPane().setContent(loadView());

        refresh();
        startTicker();
        return dialog;
    }

    /**
     * 装载 FXML 骨架并接线。
     *
     * <p>与 {@code DesktopShell} / {@code ProviderDialog} 同款：本类既是面板状态的持有者，
     * 也是它的 **FXML 控制器**。</p>
     *
     * <p>装载失败时打 ASCII 标记并显式 flush；标记必须印在**真的会执行到**的 catch 里
     * （TS-2610-032 第三轮：写在「证不出会抛」的位置会被静态分析当死代码删掉）。</p>
     *
     * @return 对话框内容根节点
     */
    private Parent loadView() {
        FXMLLoader loader = new FXMLLoader(LogPanel.class.getResource(FXML_VIEW));
        loader.setController(this);
        Parent loaded;
        try {
            loaded = loader.load();
        } catch (IOException e) {
            System.err.println("[ui-fxml-load-failed] " + e);
            System.err.flush();
            throw new IllegalStateException("日志面板界面装载失败：" + FXML_VIEW, e);
        }
        wireView();
        return loaded;
    }

    /** 把注入的节点接起来：样式、选项与事件。 */
    private void wireView() {
        root.setSpacing(ShellLayout.GAP);
        root.setPadding(new Insets(4));

        toolbar.setSpacing(ShellLayout.GAP);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(spacer, Priority.ALWAYS);

        stats.setStyle("-fx-text-fill: " + Palette.MUTED + ";");

        filter.setId(FILTER_ID);
        filter.getItems().setAll(LogLevel.DISPLAY_ORDER);
        filter.setValue(LogLevel.INFO);
        filter.setConverter(new StringConverter<>() {
            @Override
            public String toString(LogLevel level) {
                return level == null ? "全部" : level.name();
            }

            @Override
            public LogLevel fromString(String text) {
                return LogLevel.of(text);
            }
        });
        filter.valueProperty().addListener((observable, old, now) -> refresh());

        refresh.setId(REFRESH_ID);
        refresh.setOnAction(event -> refresh());
        copy.setId(COPY_ID);
        copy.setOnAction(event -> stats.setText(Clipboards.copy(area.getText())
                ? "已复制当前视图（" + area.getText().length() + " 字符）"
                : "复制失败：剪贴板不可用"));
        clear.setId(CLEAR_ID);
        clear.setOnAction(event -> {
            LogCapture.buffer().clear();
            refresh();
        });

        area.setId(AREA_ID);
        area.setEditable(false);
        area.setWrapText(false);
        area.setStyle("-fx-font-family: 'Consolas', 'DejaVu Sans Mono', monospace;"
                + "-fx-font-size: 12px;");
        VBox.setVgrow(area, Priority.ALWAYS);
    }

    /**
     * 停止自动刷新（关窗时，或测试用完时）。
     *
     * <p>公开出来是因为 {@link #build()} 也会启动刷新：只 build 不 show 的调用方
     * （测试、将来的预览）必须能自己收尾，否则会留下一个一直在跑的定时器。</p>
     */
    public void close() {
        stopTicker();
    }

    /**
     * 设置级别门槛（等价于用户在下拉里选）。
     *
     * @param level 级别
     */
    public void setFilter(LogLevel level) {
        filter.setValue(level);
    }

    /**
     * 当前视图里的文本（测试用）。
     *
     * @return 文本
     */
    public String text() {
        return area.getText();
    }

    /**
     * 重新渲染（有变化才动界面，避免自动刷新把光标位置与滚动位置弄乱）。
     */
    void refresh() {
        LogBuffer buffer = LogCapture.buffer();
        LogLevel threshold = filter.getValue();
        stats.setText("共 " + buffer.size() + " 行 · 显示 " + buffer.snapshot(threshold).size()
                + " 行 · 上限 " + buffer.capacity() + " 行"
                + (LogCapture.installed() ? "" : " · 未接入日志系统"));
        long total = buffer.totalReceived();
        if (total == renderedTotal) {
            return;
        }
        renderedTotal = total;
        String text = buffer.render(threshold, MAX_LINES);
        boolean follow = area.getScrollTop() >= area.getMaxHeight() - 1
                || area.getText().isEmpty();
        area.setText(text);
        // 跟随尾部：用户没往上翻的时候，把它拉到最新一行
        if (follow) {
            area.positionCaret(text.length());
            area.setScrollTop(Double.MAX_VALUE);
        }
    }

    private void startTicker() {
        if (ticker != null) {
            return;
        }
        ticker = new Timeline(new KeyFrame(Duration.millis(TICK_MS),
                (ActionEvent event) -> refresh()));
        ticker.setCycleCount(Animation.INDEFINITE);
        ticker.play();
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.stop();
            ticker = null;
        }
    }
}
