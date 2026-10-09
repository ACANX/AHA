package com.acanx.module.aha.desktop.view;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 设置面板：主题、字号、默认模型入口（{@code GUIDesign.md} 第 4.5 节「⚙ 设置」）。
 *
 * <p>改动**立即生效并立即保存**：设置面板里没有「应用」按钮——用户改完主题看不到变化，
 * 还得去找一个确定键，是没必要的摩擦。存不下来只记日志，不影响本次使用。</p>
 *
 * <p>门槛上的取舍：设计稿里的「记忆策略」「身份文件查看 / 编辑」还没有对应实现，
 * 因此**不放进面板**（放一个点了没反应的项比少一个功能更糟）。这里只写真实可用的三块。</p>
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

    private final DesktopSettings settings;

    private final Supplier<String> configSummary;

    private final Runnable openProviderDialog;

    private final Consumer<Theme> onTheme;

    private final java.util.function.IntConsumer onFontSize;

    /**
     * @param settings           设置（读写并持久化）
     * @param configSummary      配置摘要（显示当前配置来源）
     * @param openProviderDialog 打开供应商配置的动作（「默认模型」入口）
     * @param onTheme            主题变更回调
     * @param onFontSize         字号变更回调
     */
    public SettingsDialog(DesktopSettings settings, Supplier<String> configSummary,
                          Runnable openProviderDialog, Consumer<Theme> onTheme,
                          java.util.function.IntConsumer onFontSize) {
        this.settings = settings;
        this.configSummary = configSummary == null ? () -> "" : configSummary;
        this.openProviderDialog = openProviderDialog == null ? () -> { } : openProviderDialog;
        this.onTheme = onTheme == null ? theme -> { } : onTheme;
        this.onFontSize = onFontSize == null ? size -> { } : onFontSize;
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

        ComboBox<Theme> theme = new ComboBox<>();
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

        ComboBox<Integer> font = new ComboBox<>();
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

        Button model = new Button("打开供应商配置…");
        model.setId(MODEL_ID);
        model.setOnAction(event -> openProviderDialog.run());

        GridPane grid = new GridPane();
        grid.setHgap(ShellLayout.GAP);
        grid.setVgap(ShellLayout.GAP);
        int row = 0;
        row = row(grid, row, "主题", theme,
                "「跟随系统」按操作系统的浅色 / 深色偏好解析");
        row = row(grid, row, "字号", font, "默认 13px");
        HBox modelRow = new HBox(model);
        modelRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        row = row(grid, row, "默认模型", modelRow, "供应商与模型在同一个对话框里改");

        Label config = new Label(configSummary.get());
        config.setWrapText(true);
        config.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        Label todo = new Label("记忆策略、身份文件查看 / 编辑：0.2 后续"
                + "（现在改身份请直接编辑 ~/.aha/AHA.md 或项目的 AHA.md）");
        todo.setWrapText(true);
        todo.setStyle("-fx-text-fill: " + Palette.MUTED + ";");

        Label path = new Label(settings.path() == null
                ? "设置文件：不可用（未取到用户目录）"
                : "设置文件：" + settings.path());
        path.setWrapText(true);
        path.setStyle("-fx-text-fill: " + Palette.MUTED + ";");

        VBox content = new VBox(ShellLayout.GAP, grid, path, config, todo);
        content.setPadding(new Insets(4));
        dialog.getDialogPane().setContent(content);
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

    private static int row(GridPane grid, int index, String label, javafx.scene.Node control,
                          String hint) {
        Label key = new Label(label);
        key.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        Label note = new Label(hint);
        note.setStyle("-fx-text-fill: " + Palette.MUTED + "; -fx-font-size: 11px;");
        VBox right = new VBox(2, control, note);
        grid.add(key, 0, index);
        grid.add(right, 1, index);
        GridPane.setHgrow(right, Priority.ALWAYS);
        return index + 1;
    }
}
