package com.acanx.module.aha.desktop.view;

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
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 设置面板：两个 tab ——「界面」（主题 / 字号 / 默认模型）与「升级」（issue #51 §3.1）。
 *
 * <p>改动**立即生效并立即保存**：设置面板里没有「应用」按钮——用户改完主题看不到变化，
 * 还得去找一个确定键，是没必要的摩擦。存不下来只记日志，不影响本次使用。</p>
 *
 * <p>门槛上的取舍：设计稿里的「记忆策略」「身份文件查看 / 编辑」还没有对应实现，
 * 因此**不放进面板**（放一个点了没反应的项比少一个功能更糟）。这里只写真实可用的几块。</p>
 *
 * <p>升级 tab 的内容由 {@link UpgradePane} 程序化构建：那条状态机（检查 → 列表 → 确认 →
 * 下载 → 校验 → 暂存 → 换装）有自己的类，本对话框只负责把它放进 tab。</p>
 *
 * @since 0.2.0
 */
public final class SettingsDialog {

    /** 主题下拉 id。 */
    public static final String THEME_ID = "aha.settings.theme";

    /** 字号下拉 id。 */
    public static final String FONT_ID = "aha.settings.font";

    /** 默认模型入口 id。 */
    public static final String MODEL_ID = "aha.settings.model";

    /** 「界面」tab 的 id（契约见 issue #51 §3.1）。 */
    public static final String INTERFACE_TAB_ID = "aha.settings.tab.interface";

    /** 承载两个 tab 的 TabPane 的 id（冒烟测试按它取 tab 列表：tab 不是节点，不能用节点查找）。 */
    public static final String TABS_ID = "aha.settings.tabs";

    /** FXML 骨架资源（路径集中在 {@link UiResources}）。 */
    private static final String FXML_VIEW = UiResources.SETTINGS_DIALOG_FXML;

    /** 辅助说明的样式（设置名与底部三段说明）。 */

    /** 每行设置下方那行小字（字号略小）。 */

    private final DesktopSettings settings;

    private final Supplier<String> configSummary;

    private final Runnable openProviderDialog;

    private final Consumer<Theme> onTheme;

    private final java.util.function.IntConsumer onFontSize;

    /** 升级 tab 的内容（独立类，避免本对话框膨胀）。 */
    private final UpgradePane upgradePane;

    // ── FXML 注入的部件 ──────────────────────────────────────────────────────

    @FXML
    private VBox root;

    @FXML
    private TabPane tabs;

    @FXML
    private Tab interfaceTab;

    @FXML
    private Tab upgradeTab;

    @FXML
    private GridPane grid;

    /** 左侧设置名（原来由 `row(...)` 逐行套 MUTED 色）。 */
    @FXML
    private Label themeKey;

    @FXML
    private Label fontKey;

    @FXML
    private Label modelKey;

    /** 右侧「控件 + 说明」那一列（原来逐行 setHgrow）。 */
    @FXML
    private VBox themeBox;

    @FXML
    private VBox fontBox;

    @FXML
    private VBox modelBox;

    @FXML
    private ComboBox<Theme> theme;

    @FXML
    private ComboBox<Integer> font;

    @FXML
    private HBox modelRow;

    @FXML
    private Button model;

    @FXML
    private Label themeNote;

    @FXML
    private Label fontNote;

    @FXML
    private Label modelNote;

    @FXML
    private Label pathLabel;

    @FXML
    private Label configLabel;

    @FXML
    private Label todoLabel;

    /**
     * @param settings           设置（读写并持久化）
     * @param configSummary      配置摘要（显示当前配置来源）
     * @param openProviderDialog 打开供应商配置的动作（「默认模型」入口）
     * @param onTheme            主题变更回调
     * @param onFontSize         字号变更回调
     * @param upgradePane        升级 tab 的内容（{@code null} 时该 tab 显示可读说明，不留空页）
     */
    public SettingsDialog(DesktopSettings settings, Supplier<String> configSummary,
                          Runnable openProviderDialog, Consumer<Theme> onTheme,
                          java.util.function.IntConsumer onFontSize, UpgradePane upgradePane) {
        this.settings = settings;
        this.configSummary = configSummary == null ? () -> "" : configSummary;
        this.openProviderDialog = openProviderDialog == null ? () -> { } : openProviderDialog;
        this.onTheme = onTheme == null ? theme -> { } : onTheme;
        this.onFontSize = onFontSize == null ? size -> { } : onFontSize;
        this.upgradePane = upgradePane;
    }

    /**
     * 构建对话框（不显示）。
     *
     * @return 对话框
     */
    public Dialog<ButtonType> build() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("设置");
        dialog.setHeaderText("界面设置（改动立即生效）");
        dialog.getDialogPane().getButtonTypes().add(
                new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE));
        ThemePaint.dialog(dialog.getDialogPane());
        dialog.getDialogPane().setPrefWidth(620);
        dialog.getDialogPane().setContent(loadView());
        return dialog;
    }

    /**
     * 打开设置面板。
     *
     * @param owner 父窗口，可为 {@code null}
     */
    public void show(Window owner) {
        Dialog<ButtonType> dialog = build();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        dialog.showAndWait();
    }

    /**
     * 装载 FXML 骨架并接线。
     *
     * <p>装载失败时打 ASCII 标记并显式 flush——标记必须印在**真的会执行到**的 catch 里
     * （TS-2610-032 第三轮）。</p>
     *
     * @return 内容根节点
     */
    private Parent loadView() {
        FXMLLoader loader = new FXMLLoader(SettingsDialog.class.getResource(FXML_VIEW));
        loader.setController(this);
        Parent loaded;
        try {
            loaded = loader.load();
        } catch (IOException e) {
            System.err.println("[ui-fxml-load-failed] " + e);
            System.err.flush();
            throw new IllegalStateException("设置面板界面装载失败：" + FXML_VIEW, e);
        }
        wireView();
        wireTabs();
        return loaded;
    }

    /** 两个 tab：id 按 §3.1 的契约设好；升级 tab 的内容交给 {@link UpgradePane}。 */
    private void wireTabs() {
        interfaceTab.setId(INTERFACE_TAB_ID);
        upgradeTab.setId(UpgradePane.ID_TAB);
        if (upgradePane == null) {
            upgradeTab.setContent(new Label("升级面板未接线（内置说明：可在 ~/.aha/update/ 查看日志）。"));
        } else {
            upgradeTab.setContent(upgradePane.build());
        }
    }

    /**
     * 打开设置并**直接切到升级 tab、自动检查一次**（帮助菜单「检查更新」入口，§3.1）。
     *
     * @param owner 父窗口，可为 {@code null}
     */
    public void showUpgradeTab(Window owner) {
        Dialog<ButtonType> dialog = build();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        tabs.getSelectionModel().select(upgradeTab);
        if (upgradePane != null) {
            upgradePane.checkNow();
        }
        dialog.showAndWait();
    }
    }

    /** 把注入的节点接起来：样式、选项、监听与文案。 */
    private void wireView() {
        root.setSpacing(ShellLayout.GAP);
        root.setPadding(new Insets(4));

        grid.setHgap(ShellLayout.GAP);
        grid.setVgap(ShellLayout.GAP);
        // 三行都是「左列设置名 + 右列 VBox(控件, 说明)」；右列吃掉剩余宽度。
        // 每一列都要显式套色：不能只靠根节点的 looked-up colors，否则暗色下对比度不足
        // （issue #44 / #49 的老症状）。
        for (Label key : List.of(themeKey, fontKey, modelKey)) {
            ThemePaint.themedForever(key, () -> "-fx-text-fill: " + Palette.MUTED + ";");
        }
        for (VBox box : List.of(themeBox, fontBox, modelBox)) {
            box.setSpacing(2);
            GridPane.setHgrow(box, Priority.ALWAYS);
        }
        for (Label note : List.of(themeNote, fontNote, modelNote)) {
            ThemePaint.themedForever(note, () -> "-fx-text-fill: " + Palette.MUTED + "; -fx-font-size: 11px;");
        }
        modelRow.setAlignment(Pos.CENTER_LEFT);

        theme.setId(THEME_ID);
        theme.getItems().setAll(Theme.values());
        theme.setValue(settings.theme());
        theme.setConverter(new StringConverter<>() {
            @Override
            public String toString(Theme value) {
                return value == null ? "" : value.label();
            }

            @Override
            public Theme fromString(String text) {
                return Theme.of(text);
            }
        });
        theme.valueProperty().addListener((observable, old, now) -> {
            settings.theme(now);
            settings.save();
            onTheme.accept(now);
        });

        font.setId(FONT_ID);
        for (int size = DesktopSettings.MIN_FONT_SIZE;
                size <= DesktopSettings.MAX_FONT_SIZE; size++) {
            font.getItems().add(size);
        }
        font.setValue(settings.fontSize());
        font.valueProperty().addListener((observable, old, now) -> {
            if (now == null) {
                return;
            }
            settings.fontSize(now);
            settings.save();
            onFontSize.accept(now);
        });

        model.setId(MODEL_ID);
        model.setOnAction(event -> openProviderDialog.run());

        pathLabel.setText(settings.path() == null
                ? "设置文件：不可用（未取到用户目录）"
                : "设置文件：" + settings.path());
        pathLabel.setWrapText(true);
        ThemePaint.themedForever(pathLabel, () -> "-fx-text-fill: " + Palette.MUTED + ";");

        configLabel.setText(configSummary.get());
        configLabel.setWrapText(true);
        ThemePaint.themedForever(configLabel, () -> "-fx-text-fill: " + Palette.MUTED + ";");

        todoLabel.setText("记忆策略、身份文件查看 / 编辑：0.2 后续"
                + "（现在改身份请直接编辑 ~/.aha/AHA.md 或项目的 AHA.md）");
        todoLabel.setWrapText(true);
        ThemePaint.themedForever(todoLabel, () -> "-fx-text-fill: " + Palette.MUTED + ";");
    }
}
