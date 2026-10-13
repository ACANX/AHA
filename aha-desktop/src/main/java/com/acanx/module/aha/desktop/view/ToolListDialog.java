package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.common.model.ToolDescriptor;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/**
 * 工具列表对话框：显示当前可调用的工具与所需权限。
 *
 * <p>{@code GUIDesign.md} 第 4.5 节要求工具面板显示权限标注，
 * 让用户事先知道哪些操作会被拦下来。只读展示，不做开关——开关留给后续迭代。</p>
 *
 * <p><strong>内容完全由数据驱动</strong>：每个工具一行 {@code 名称 · 权限}，行数与内容都来自
 * {@link Supplier}。所以 {@code tool-list-dialog.fxml} 里只有一个 {@code VBox}——
 * 这是本批四个对话框里 FXML 收益最小的一个，放进来的唯一理由是四者口径一致。</p>
 *
 * @since 0.2.0
 */
public final class ToolListDialog {

    /** FXML 骨架资源（与本类同包）。 */
    private static final String FXML_VIEW = "tool-list-dialog.fxml";

    private final Supplier<List<ToolDescriptor>> tools;

    /** 工具行容器（FXML 注入；行本身在 Java 里按数据生成）。 */
    @FXML
    private VBox box;

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
        ThemePaint.dialog(alert.getDialogPane());
        List<ToolDescriptor> list;
        try {
            list = tools.get();
        } catch (RuntimeException e) {
            list = List.of();
        }
        alert.setHeaderText("可调用工具（" + list.size() + " 个）");
        alert.getDialogPane().setContent(loadView(list));
        alert.showAndWait();
    }

    /**
     * 装载 FXML 骨架并接线。
     *
     * <p>与其余视图同款：本类既是数据持有者，也是它的 FXML 控制器。装载失败时打 ASCII 标记
     * 并显式 flush——标记必须印在**真的会执行到**的 catch 里（TS-2610-032 第三轮）。</p>
     *
     * @param list 工具列表
     * @return 内容根节点
     */
    private Parent loadView(List<ToolDescriptor> list) {
        FXMLLoader loader = new FXMLLoader(ToolListDialog.class.getResource(FXML_VIEW));
        loader.setController(this);
        Parent loaded;
        try {
            loaded = loader.load();
        } catch (IOException e) {
            System.err.println("[ui-fxml-load-failed] " + e);
            System.err.flush();
            throw new IllegalStateException("工具列表界面装载失败：" + FXML_VIEW, e);
        }
        wireView(list);
        return loaded;
    }

    /** 按数据装配工具行（静态结构在 fxml，行在 Java）。 */
    private void wireView(List<ToolDescriptor> list) {
        box.setSpacing(4);
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
    }
}
