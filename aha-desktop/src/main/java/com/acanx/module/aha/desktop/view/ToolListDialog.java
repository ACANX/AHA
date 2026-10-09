package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.common.model.ToolDescriptor;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.function.Supplier;

/**
 * 工具列表对话框：显示当前可调用的工具与所需权限。
 *
 * <p>{@code GUIDesign.md} 第 4.5 节要求工具面板显示权限标注，
 * 让用户事先知道哪些操作会被拦下来。只读展示，不做开关——开关留给后续迭代。</p>
 *
 * @since 0.2.0
 */
public final class ToolListDialog {

    private final Supplier<List<ToolDescriptor>> tools;

    /**
     * @param tools 工具来源
     */
    public ToolListDialog(Supplier<List<ToolDescriptor>> tools) {
        this.tools = tools;
    }

    /**
     * 打开对话框。
     *
     * @param owner 父窗口
     */
    public void show(Window owner) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initOwner(owner);
        alert.setTitle("工具");
        ThemePaint.themed(alert.getDialogPane(), Palette.dialogTheme());
        List<ToolDescriptor> list;
        try {
            list = tools.get();
        } catch (RuntimeException e) {
            list = List.of();
        }
        alert.setHeaderText("可调用工具（" + list.size() + " 个）");
        VBox box = new VBox(4);
        for (ToolDescriptor tool : list) {
            Label line = new Label("%s  ·  %s".formatted(tool.name(),
                    tool.permission() == null ? "权限未声明" : tool.permission().name()));
            line.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.FOREGROUND + ";");
            box.getChildren().add(line);
        }
        if (list.isEmpty()) {
            Label empty = new Label("（没有可用工具：检查 Aha.Tools.Enabled 与 aha-tool 依赖）");
            empty.setStyle("-fx-text-fill: " + Palette.FOREGROUND + ";");
            box.getChildren().add(empty);
        }
        alert.getDialogPane().setContent(box);
        alert.showAndWait();
    }
}
