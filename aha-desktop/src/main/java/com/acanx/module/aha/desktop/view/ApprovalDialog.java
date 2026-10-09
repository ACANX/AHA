package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.common.tool.ToolPermission;
import com.acanx.module.aha.desktop.chat.ApprovalText;
import com.acanx.module.aha.desktop.chat.DesktopToolApprover;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.Map;
import java.util.Set;

/**
 * 授权弹窗（危险操作的唯一拦截点）。
 *
 * <p>比系统默认 {@code Alert} 多出来的、都是「不看就会点错」的东西（{@code GUIDesign.md} 第 4.4 节）：</p>
 * <ul>
 *   <li>首行写清「哪个工具、动什么」，按操作类别着色；</li>
 *   <li>权限单独一行并附人话说明（{@code WRITE（修改、新建文件与目录）}）；</li>
 *   <li>完整参数（执行类是完整命令）——{@code rm -rf build} 与 {@code rm -rf /} 必须一眼可分；</li>
 *   <li>「本会话内始终允许 **权限名**」，而不是笼统的「始终允许」；</li>
 *   <li>默认焦点在**拒绝**，且 {@code Esc} 也是拒绝（把 {@code Esc} 绑到取消按钮上）。</li>
 * </ul>
 *
 * @since 0.2.0
 */
public final class ApprovalDialog {

    /** 参数区 id。 */
    public static final String ARGS_ID = "aha.approval.args";

    /** 拒绝按钮 id。 */
    public static final String DENY_ID = "aha.approval.deny";

    /** 本次允许按钮 id。 */
    public static final String ONCE_ID = "aha.approval.once";

    /** 本会话始终允许按钮 id。 */
    public static final String ALWAYS_ID = "aha.approval.always";

    private final String toolName;

    private final ToolPermission permission;

    private final Map<String, Object> arguments;

    private final Set<ToolPermission> sessionAllowed;

    private Button deny;

    /**
     * @param toolName       工具名
     * @param permission     所需权限
     * @param arguments      调用参数
     * @param sessionAllowed 本会话已放行的权限
     */
    public ApprovalDialog(String toolName, ToolPermission permission,
                          Map<String, Object> arguments, Set<ToolPermission> sessionAllowed) {
        this.toolName = toolName;
        this.permission = permission;
        this.arguments = arguments;
        this.sessionAllowed = sessionAllowed == null ? Set.of() : Set.copyOf(sessionAllowed);
    }

    /**
     * 构建对话框（不显示；测试可以直接检查控件）。
     *
     * @return 对话框，结果类型为用户选择
     */
    public Dialog<DesktopToolApprover.Decision> build() {
        Dialog<DesktopToolApprover.Decision> dialog = new Dialog<>();
        dialog.setTitle(ApprovalText.TITLE);
        dialog.setHeaderText(ApprovalText.TITLE);

        String target = com.acanx.module.aha.common.tool.ToolKind.of(toolName)
                .targetOf(arguments);
        ButtonType denyType = new ButtonType(ApprovalText.DENY, ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType onceType = new ButtonType(ApprovalText.ALLOW_ONCE, ButtonBar.ButtonData.OK_DONE);
        ButtonType alwaysType = new ButtonType(ApprovalText.alwaysLabel(permission),
                ButtonBar.ButtonData.APPLY);
        dialog.getDialogPane().getButtonTypes().addAll(denyType, onceType, alwaysType);
        ThemePaint.dialog(dialog.getDialogPane());
        dialog.getDialogPane().setPrefWidth(680);

        String accent = Palette.forToolKind(com.acanx.module.aha.common.tool.ToolKind.of(toolName));
        Label headline = new Label(ApprovalText.headline(toolName, target));
        headline.setStyle("-fx-text-fill: " + accent + "; -fx-font-size: 14px;");

        GridPane grid = new GridPane();
        grid.setHgap(ShellLayout.GAP);
        grid.setVgap(ShellLayout.GAP / 2.0);
        row(grid, 0, "所需权限", ApprovalText.permissionLabel(permission));
        row(grid, 1, "目标", target == null || target.isBlank() ? "（未提供）" : target);

        TextArea args = new TextArea(ApprovalText.argumentsText(arguments));
        args.setId(ARGS_ID);
        args.setEditable(false);
        args.setWrapText(true);
        args.setPrefRowCount(Math.min(8, Math.max(2, ApprovalText.argumentsText(arguments)
                .split("\n").length)));
        args.setStyle("-fx-font-family: 'Consolas', 'DejaVu Sans Mono', monospace;"
                + "-fx-font-size: 12px;");

        VBox content = new VBox(ShellLayout.GAP, headline, grid, section("调用参数"), args);
        content.setPadding(new Insets(4));
        String note = ApprovalText.sessionAllowedNote(sessionAllowed, permission);
        if (note != null) {
            Label noteLabel = new Label(note);
            noteLabel.setWrapText(true);
            noteLabel.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
            content.getChildren().add(noteLabel);
        }
        dialog.getDialogPane().setContent(content);

        deny = (Button) dialog.getDialogPane().lookupButton(denyType);
        deny.setId(DENY_ID);
        ((Button) dialog.getDialogPane().lookupButton(onceType)).setId(ONCE_ID);
        ((Button) dialog.getDialogPane().lookupButton(alwaysType)).setId(ALWAYS_ID);

        dialog.setResultConverter(picked -> {
            if (picked == onceType) {
                return DesktopToolApprover.Decision.ALLOW_ONCE;
            }
            if (picked == alwaysType) {
                return DesktopToolApprover.Decision.ALLOW_SESSION;
            }
            return DesktopToolApprover.Decision.DENY;
        });
        return dialog;
    }

    /**
     * 打开并等待用户选择（模态）。
     *
     * @param owner 父窗口，可为 {@code null}
     * @return 用户选择；直接关窗按拒绝处理
     */
    public DesktopToolApprover.Decision show(Window owner) {
        Dialog<DesktopToolApprover.Decision> dialog = build();
        if (owner != null) {
            dialog.initOwner(owner);
        }
        // 默认焦点给「拒绝」：回车不该变成一次授权
        if (deny != null) {
            dialog.setOnShown(event -> deny.requestFocus());
        }
        return dialog.showAndWait().orElse(DesktopToolApprover.Decision.DENY);
    }

    private static void row(GridPane grid, int index, String label, String value) {
        Label key = new Label(label);
        key.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        Label text = new Label(value);
        text.setWrapText(true);
        text.setStyle("-fx-text-fill: " + Palette.FOREGROUND + ";");
        grid.add(key, 0, index);
        grid.add(text, 1, index);
        GridPane.setHgrow(text, Priority.ALWAYS);
    }

    private static Label section(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: " + Palette.MUTED + "; -fx-font-size: 11px;");
        return label;
    }

}
