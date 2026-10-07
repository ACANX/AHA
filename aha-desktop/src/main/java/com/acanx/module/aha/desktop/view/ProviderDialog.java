package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.core.config.ModelConfig;
import com.acanx.module.aha.core.config.ModelConfigStore;
import com.acanx.module.aha.core.config.ProviderConfig;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 供应商配置：**查看 + 修改 + 保存**（不是只让你挑一个）。
 *
 * <p>左侧是供应商列表，右侧是可编辑表单（适配器 / 基础地址 / 模型 / API Key / 超时 / 重试）。
 * 支持新增、删除、设为默认；保存写回 {@code Model.yml}（与 CLI 的 {@code aha provider} 同一份文件、
 * 同一个 {@link ModelConfigStore}，因此两边互通）。</p>
 *
 * <p>表单的校验与转换在 {@link ProviderForm}（纯逻辑，无图形环境可测），本类只做控件装配。</p>
 *
 * <p>关于密钥：这里允许直接编辑，因为用户的 {@code Model.yml} 本来就是这么配的。
 * 但对话框会明确提示——直接写文件是**明文**，更稳妥的是环境变量
 * {@code AHA_API_KEY_<PROVIDER>} 或 {@code aha secret set}（密钥库）。默认输入框是打码的，
 * 需要改按「显示」。</p>
 *
 * @since 0.2.0
 */
public final class ProviderDialog {

    private final ModelConfigStore store;

    private ListView<String> list;

    private ChoiceBox<String> adapter;

    private TextField idField;

    private TextField baseUrl;

    private TextField model;

    private PasswordField apiKey;

    private TextField apiKeyPlain;

    private TextField timeout;

    private TextField retries;

    private Label status;

    private Button setDefault;

    /**
     * @param path Model.yml 路径
     */
    public ProviderDialog(Path path) {
        this.store = new ModelConfigStore(path);
    }

    /**
     * 打开编辑器。
     *
     * @param owner     父窗口
     * @param onChanged 配置变化后的回调（用于开新会话）
     */
    public void show(Window owner, Runnable onChanged) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("供应商配置");
        dialog.setHeaderText("查看与修改供应商（保存后写回 " + store.path() + "）");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(buildBody(dialog, onChanged));
        reload(null);
        dialog.showAndWait();
    }

    private VBox buildBody(Dialog<ButtonType> dialog, Runnable onChanged) {
        list = new ListView<>();
        list.setPrefWidth(180);
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> {
            if (now != null) {
                load(now);
            }
        });

        HBox body = new HBox(12, list, buildForm(dialog, onChanged));
        HBox.setHgrow(body.getChildren().get(1), Priority.ALWAYS);
        VBox root = new VBox(8, body, hint());
        root.setPadding(new Insets(4, 4, 0, 4));
        return root;
    }

    private GridPane buildForm(Dialog<ButtonType> dialog, Runnable onChanged) {
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(6);

        idField = new TextField();
        adapter = new ChoiceBox<>();
        adapter.getItems().addAll(ProviderForm.ADAPTERS);
        baseUrl = new TextField();
        model = new TextField();
        apiKey = new PasswordField();
        apiKeyPlain = new TextField();
        apiKeyPlain.setVisible(false);
        apiKeyPlain.setManaged(false);
        timeout = new TextField();
        retries = new TextField();

        Button toggle = new Button("显示");
        toggle.setOnAction(event -> {
            boolean showPlain = !apiKeyPlain.isVisible();
            if (showPlain) {
                apiKeyPlain.setText(apiKey.getText());
            } else {
                apiKey.setText(apiKeyPlain.getText());
            }
            apiKey.setVisible(!showPlain);
            apiKey.setManaged(!showPlain);
            apiKeyPlain.setVisible(showPlain);
            apiKeyPlain.setManaged(showPlain);
            toggle.setText(showPlain ? "隐藏" : "显示");
        });
        apiKey.textProperty().addListener((obs, old, now) -> apiKeyPlain.setText(now));
        apiKeyPlain.textProperty().addListener((obs, old, now) -> apiKey.setText(now));

        Button save = new Button("保存");
        setDefault = new Button("设为默认");
        Button add = new Button("新增");
        Button remove = new Button("删除");

        int row = 0;
        grid.addRow(row++, new Label("供应商 ID"), idField);
        grid.addRow(row++, new Label("适配器"), adapter);
        grid.addRow(row++, new Label("基础地址"), baseUrl);
        grid.addRow(row++, new Label("模型"), model);
        grid.addRow(row++, new Label("API Key"), new HBox(6, apiKey, apiKeyPlain, toggle));
        grid.addRow(row++, new Label("超时（秒）"), timeout);
        grid.addRow(row++, new Label("重试次数"), retries);

        status = new Label();
        status.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Palette.MUTED + ";");
        status.setWrapText(true);
        grid.add(status, 0, row++, 2, 1);
        grid.add(new HBox(8, save, setDefault, add, remove), 0, row, 2, 1);

        save.setOnAction(event -> {
            Map<String, String> errors = ProviderForm.validate(draft());
            if (!errors.isEmpty()) {
                status.setStyle("-fx-text-fill: " + Palette.FAILURE + ";");
                status.setText(String.join("；", errors.values()));
                return;
            }
            String id = idField.getText().trim();
            ModelConfig config = store.load();
            ProviderConfig original = config.providersOrEmpty().get(list.getSelectionModel().getSelectedItem());
            store.putProvider(id, ProviderForm.toConfig(draft(), original));
            status.setStyle("-fx-text-fill: " + Palette.SUCCESS + ";");
            status.setText("已保存：" + id);
            reload(id);
            onChanged.run();
        });

        setDefault.setOnAction(event -> {
            String id = list.getSelectionModel().getSelectedItem();
            if (id == null) {
                return;
            }
            store.setDefault(id);
            status.setStyle("-fx-text-fill: " + Palette.SUCCESS + ";");
            status.setText("已设为默认：" + id + "（新建会话后生效）");
            reload(id);
            onChanged.run();
        });

        add.setOnAction(event -> {
            list.getSelectionModel().clearSelection();
            idField.setText("NewProvider");
            adapter.setValue(ProviderForm.ADAPTERS[0]);
            baseUrl.setText("https://");
            model.setText("");
            apiKey.setText("");
            timeout.setText("60");
            retries.setText("2");
            status.setText("填好后点「保存」即新增");
        });

        remove.setOnAction(event -> {
            String id = list.getSelectionModel().getSelectedItem();
            if (id == null) {
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "确定删除供应商 " + id + " 吗？", ButtonType.OK, ButtonType.CANCEL);
            confirm.initOwner(dialog.getOwner());
            confirm.setTitle("删除供应商");
            confirm.showAndWait().filter(picked -> picked == ButtonType.OK).ifPresent(picked -> {
                store.removeProvider(id);
                reload(null);
                status.setText("已删除：" + id);
                onChanged.run();
            });
        });
        return grid;
    }

    private Label hint() {
        Label label = new Label("""
                改密钥的三种方式（推荐前两种，直接写文件是明文）：
                  1) 环境变量  AHA_API_KEY_<供应商ID大写>      例如 AHA_API_KEY_DEEPSEEK
                  2) 密钥库    aha secret set <供应商ID>
                  3) 本对话框的 API Key 字段（会写入 Model.yml，明文保存）""");
        label.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Palette.MUTED + ";");
        return label;
    }

    private ProviderForm.Draft draft() {
        return new ProviderForm.Draft(idField.getText(), adapter.getValue(), baseUrl.getText(),
                apiKey.getText(), model.getText(), timeout.getText(), retries.getText());
    }

    private void load(String id) {
        ModelConfig config = store.load();
        ProviderConfig provider = config.providersOrEmpty().get(id);
        ProviderForm.Draft draft = ProviderForm.of(id, provider);
        idField.setText(draft.id());
        adapter.setValue(draft.adapter());
        baseUrl.setText(draft.baseUrl());
        model.setText(draft.model());
        apiKey.setText(draft.apiKey());
        timeout.setText(draft.timeoutSeconds());
        retries.setText(draft.maxRetries());
        setDefault.setDisable(config.defaultProvider() != null && config.defaultProvider().equals(id));
    }

    private void reload(String selectId) {
        ModelConfig config = store.load();
        List<String> ids = new ArrayList<>(config.providersOrEmpty().keySet());
        list.getItems().setAll(ids);
        String target = selectId != null ? selectId : config.defaultProvider();
        if (target != null && ids.contains(target)) {
            list.getSelectionModel().select(target);
        }
        status.setText("默认供应商：" + (config.defaultProvider() == null ? "（未设置）" : config.defaultProvider())
                + " · 密钥：" + ProviderForm.mask(store.load().providersOrEmpty()
                        .getOrDefault(config.defaultProvider(), new ProviderConfig(null, null, null, null, 0, 0, null, null))
                        .apiKey()));
    }
}
