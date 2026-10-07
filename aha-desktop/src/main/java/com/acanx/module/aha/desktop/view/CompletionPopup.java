package com.acanx.module.aha.desktop.view;

import javafx.collections.FXCollections;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import javafx.stage.Popup;

import java.util.List;

/**
 * 输入框下方的候选弹层（{@code /} 命令与 {@code @} 文件共用）。
 *
 * <p>用 {@link Popup} + {@code ListView} 而不是 {@code ContextMenu}：后者拿到键盘焦点后
 * 会把方向键、Enter 一并吞掉，用户没法在「打字 → 选候选 → 回车确认」之间自然过渡。
 * 这个弹层**从不抢焦点**，所有按键都由输入框的过滤器处理（见 {@code DesktopShell}），
 * 于是输入体验与常见编辑器一致。</p>
 *
 * @since 0.2.0
 */
public final class CompletionPopup {

    /** 候选列表 id（测试与真机定位用）。 */
    public static final String LIST_ID = "aha.completion.list";

    /** 一行高度（px）。 */
    private static final double ROW_HEIGHT = 24;

    /** 弹层最大高度（px）。 */
    private static final double MAX_HEIGHT = 240;

    /**
     * 一个候选。
     *
     * @param display 显示文本（可以带说明）
     * @param value   接受时真正写进输入框的文本
     */
    public record Item(String display, String value) {
    }

    private final Popup popup = new Popup();

    private final ListView<Item> list = new ListView<>();

    private List<Item> items = List.of();

    /** 弹层是否已显示。 */
    public CompletionPopup() {
        list.setId(LIST_ID);
        list.setFocusTraversable(false);
        list.setStyle("-fx-background-color: " + Palette.BLOCK_BACKGROUND + ";"
                + "-fx-control-inner-background: " + Palette.BLOCK_BACKGROUND + ";"
                + "-fx-border-color: #3A3A3A;");
        list.setCellFactory(view -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.display());
                setStyle(empty ? "" : "-fx-text-fill: " + Palette.FOREGROUND + ";");
            }
        });
        popup.getContent().add(list);
        popup.setAutoHide(false);
        popup.setHideOnEscape(false);
    }

    /**
     * 显示候选（空列表时自动隐藏）。
     *
     * @param anchor 对齐的控件（通常是输入框）
     * @param items  候选
     */
    public void show(Region anchor, List<Item> items) {
        this.items = items == null ? List.of() : List.copyOf(items);
        if (this.items.isEmpty() || anchor.getScene() == null) {
            hide();
            return;
        }
        list.setItems(FXCollections.observableArrayList(this.items));
        list.getSelectionModel().select(0);
        double width = Math.max(240, anchor.getWidth());
        list.setPrefWidth(width);
        list.setPrefHeight(Math.min(MAX_HEIGHT, this.items.size() * ROW_HEIGHT + 2));
        Bounds bounds = anchor.localToScreen(anchor.getBoundsInLocal());
        if (bounds == null) {
            hide();
            return;
        }
        // 挂在输入框**上方**：输入框通常贴着窗口底部，往下弹会被窗口裁掉
        Point2D point = new Point2D(bounds.getMinX(), Math.max(0, bounds.getMinY() - list.getPrefHeight() - 2));
        popup.show(anchor, point.getX(), point.getY());
    }

    /** 隐藏。 */
    public void hide() {
        if (popup.isShowing()) {
            popup.hide();
        }
    }

    /**
     * 是否正在显示。
     *
     * @return 显示返回 {@code true}
     */
    public boolean isVisible() {
        return popup.isShowing();
    }

    /**
     * 当前候选（显示文本）。
     *
     * @return 候选列表
     */
    public List<String> displays() {
        return items.stream().map(Item::display).toList();
    }

    /**
     * 移动选择。
     *
     * @param delta 偏移（+1 向下，-1 向上），到头后循环
     */
    public void moveSelection(int delta) {
        if (items.isEmpty()) {
            return;
        }
        int size = items.size();
        int index = Math.floorMod(list.getSelectionModel().getSelectedIndex() + delta, size);
        list.getSelectionModel().select(index);
        list.scrollTo(index);
    }

    /**
     * 当前选中的候选。
     *
     * @return 候选；没有候选时返回 {@code null}
     */
    public Item selected() {
        int index = list.getSelectionModel().getSelectedIndex();
        return index < 0 || index >= items.size() ? null : items.get(index);
    }
}
