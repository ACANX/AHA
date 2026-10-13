package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.common.tool.ToolKind;
import com.acanx.module.aha.common.tool.ToolPermission;
import com.acanx.module.aha.desktop.chat.ApprovalText;
import com.acanx.module.aha.desktop.chat.DesktopToolApprover;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
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

import java.io.IOException;
import java.util.List;
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
 * <p>三个决定按钮是 {@code Dialog} 自己的 {@code ButtonType}（不进 fxml）：控制器给它们挂上 id，
 * 并把选中项映射成 {@link DesktopToolApprover.Decision}。</p>
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

    /** FXML 骨架资源（路径集中在 {@link UiResources}）。 */
    private static final String FXML_VIEW = UiResources.APPROVAL_DIALOG_FXML;

    /** 小标题样式（「调用参数」）。 */
    private static final String SECTION_STYLE =
            "-fx-text-fill: " + Palette.MUTED + "; -fx-font-size: 11px;";

    /** 等宽样式（参数区：执行类是完整命令，必须一眼可分）。 */
    private static final String CODE_STYLE =
            "-fx-font-family: 'Consolas', 'DejaVu Sans Mono', monospace; -fx-font-size: 12px;";

    private final String toolName;

    private final ToolPermission permission;

    private final Map<String, Object> arguments;

    private final Set<ToolPermission> sessionAllowed;

    // ── FXML 注入的部件 ──────────────────────────────────────────────────────

    @FXML
    private VBox content;

    /** 首行：「读取 · /path/to/file」这类，按操作类别着色。 */
    @FXML
    private Label headline;

    @FXML
    private GridPane grid;

    /** 左侧设置名（原来由 `row(...)` 逐行套 MUTED 色）。 */
    @FXML
    private Label permissionKey;

    @FXML
    private Label targetKey;

    @FXML
    private Label permissionValue;

    @FXML
    private Label targetValue;

    @FXML
    private Label argsSection;

    @FXML
    private TextArea args;

    /** 「本会话已放行」提示；无内容时由控制器把它从容器里移除。 */
    @FXML
    private Label noteLabel;

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

        String target = ToolKind.of(toolName).targetOf(arguments);
        ButtonType denyType = new ButtonType(ApprovalText.DENY, ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType onceType = new ButtonType(ApprovalText.ALLOW_ONCE, ButtonBar.ButtonData.OK_DONE);
        ButtonType alwaysType = new ButtonType(ApprovalText.alwaysLabel(permission),
                ButtonBar.ButtonData.APPLY);
        dialog.getDialogPane().getButtonTypes().addAll(denyType, onceType, alwaysType);
        ThemePaint.dialog(dialog.getDialogPane());
        dialog.getDialogPane().setPrefWidth(680);
        dialog.getDialogPane().setContent(loadView(target));

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

    /**
     * 装载 FXML 骨架并接线。
     *
     * <p>装载失败时打 ASCII 标记并显式 flush——标记必须印在**真的会执行到**的 catch 里
     * （TS-2610-032 第三轮：写在「证不出会抛」的位置会被静态分析当死代码删掉）。</p>
     *
     * @param target 操作目标（由工具类别解析）
     * @return 内容根节点
     */
    private Parent loadView(String target) {
        FXMLLoader loader = new FXMLLoader(ApprovalDialog.class.getResource(FXML_VIEW));
        loader.setController(this);
        Parent loaded;
        try {
            loaded = loader.load();
        } catch (IOException e) {
            System.err.println("[ui-fxml-load-failed] " + e);
            System.err.flush();
            throw new IllegalStateException("授权弹窗界面装载失败：" + FXML_VIEW, e);
        }
        wireView(target);
        return loaded;
    }

    /** 把注入的节点接起来：着色、文案与参数区样式。 */
    private void wireView(String target) {
        content.setSpacing(ShellLayout.GAP);
        content.setPadding(new Insets(4));

        String accent = Palette.forToolKind(ToolKind.of(toolName));
        headline.setText(ApprovalText.headline(toolName, target));
        headline.setStyle("-fx-text-fill: " + accent + "; -fx-font-size: 14px;");

        grid.setHgap(ShellLayout.GAP);
        grid.setVgap(ShellLayout.GAP / 2.0);
        // 左侧的「键」标签与右侧的「值」标签都要显式套色：不能只靠根节点的 looked-up colors，
        // 否则暗色下对比度不足（issue #44 / #49 的老症状）
        for (Label key : List.of(permissionKey, targetKey)) {
            key.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        }
        permissionValue.setText(ApprovalText.permissionLabel(permission));
        targetValue.setText(target == null || target.isBlank() ? "（未提供）" : target);
        for (Label value : List.of(permissionValue, targetValue)) {
            value.setWrapText(true);
            value.setStyle("-fx-text-fill: " + Palette.FOREGROUND + ";");
            // 值列吃掉剩余宽度：原来在 row() 里逐行 setHgrow
            GridPane.setHgrow(value, Priority.ALWAYS);
        }

        argsSection.setStyle(SECTION_STYLE);

        String argumentsText = ApprovalText.argumentsText(arguments);
        args.setId(ARGS_ID);
        args.setText(argumentsText);
        args.setEditable(false);
        args.setWrapText(true);
        args.setPrefRowCount(Math.min(8, Math.max(2, argumentsText.split("\n").length)));
        args.setStyle(CODE_STYLE);

        // 原逻辑是「有内容才添加这个节点」；fxml 里它总是存在，所以这里等价地把它移除
        String note = ApprovalText.sessionAllowedNote(sessionAllowed, permission);
        if (note == null) {
            content.getChildren().remove(noteLabel);
        } else {
            noteLabel.setText(note);
            noteLabel.setWrapText(true);
            noteLabel.setStyle("-fx-text-fill: " + Palette.MUTED + ";");
        }
    }
}
