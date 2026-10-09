package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.desktop.chat.ToolCard;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * 「查看全部」输出的对话框。
 *
 * <p>工具卡片的预览只显示前 200 行（{@code GUIDesign.md} 第 4.2 节），点「全部」到这里看完整内容。
 * 文本区只读但可选中，另给一个「复制」按钮——把长输出从界面搬走是这个对话框存在的唯一理由。</p>
 *
 * @since 0.2.0
 */
public final class OutputDialog {

    /** 文本区 id（测试与真机定位用）。 */
    public static final String AREA_ID = "aha.output.text";

    /** 复制按钮 id。 */
    public static final String COPY_ID = "aha.output.copy";

    private final String title;

    private final String text;

    /**
     * @param title 标题
     * @param text  完整文本
     */
    public OutputDialog(String title, String text) {
        this.title = title == null ? "输出" : title;
        this.text = text == null ? "" : text;
    }

    /**
     * 打开对话框（模态）。
     *
     * @param owner 父窗口，可为 {@code null}
     */
    public void show(Window owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header(false));
        if (owner != null) {
            dialog.initOwner(owner);
        }
        ButtonType copy = new ButtonType("复制", ButtonBar.ButtonData.LEFT);
        ButtonType close = new ButtonType("关闭", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(copy, close);
        dialog.getDialogPane().setPrefWidth(900);
        dialog.getDialogPane().setPrefHeight(680);
        ThemePaint.themed(dialog.getDialogPane(), Palette.dialogTheme());

        TextArea area = new TextArea(text);
        area.setId(AREA_ID);
        area.setEditable(false);
        area.setWrapText(false);
        area.setStyle("-fx-font-family: 'Consolas', 'DejaVu Sans Mono', monospace;"
                + "-fx-font-size: 12px;");

        Label hint = new Label("文本可选中，也可直接 Ctrl+C。");
        hint.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        HBox bottom = new HBox(hint);
        bottom.setAlignment(Pos.CENTER_LEFT);
        bottom.setPadding(new Insets(4, 0, 0, 0));

        VBox content = new VBox(6, area, bottom);
        VBox.setVgrow(area, Priority.ALWAYS);
        dialog.getDialogPane().setContent(content);

        // 点「复制」不关闭对话框：搬走内容后往往还要回来对照原文
        Button copyButton = (Button) dialog.getDialogPane().lookupButton(copy);
        copyButton.setId(COPY_ID);
        copyButton.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            dialog.setHeaderText(header(Clipboards.copy(text)));
        });
        dialog.showAndWait();
    }

    /**
     * 标题栏文本。
     *
     * @param copied 是否刚复制过
     * @return 文本
     */
    String header(boolean copied) {
        String base = title + "（共 " + ToolCard.lines(text) + " 行）";
        return copied ? base + "　已复制到剪贴板" : base;
    }
}
