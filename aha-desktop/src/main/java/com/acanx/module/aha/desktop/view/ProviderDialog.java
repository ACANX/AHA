package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.core.config.ModelConfig;
import com.acanx.module.aha.core.config.ModelConfigStore;
import com.acanx.module.aha.core.config.ProviderConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 供应商配置：**两个区域，两件事**。
 *
 * <pre>
 * ┌────────────────┬──────────────────────────────────────────────┐
 * │ 供应商列表     │ ① 编辑「已选中」的供应商                      │
 * │ ● DeepSeek     │    适配器 / 基础地址 / 模型 / 密钥 / 超时 / 重试│
 * │ ○ OpenAI       │    [保存修改] [设为默认] [删除]                │
 * │ ○ ...          │ ──────────────────────────────────────────── │
 * │                │ ② 新增供应商（独立表单，与上面互不影响）        │
 * │                │    预设 / ID / 适配器 / 基础地址 / 模型 / ...  │
 * │                │    [新增]                                     │
 * └────────────────┴──────────────────────────────────────────────┘
 * </pre>
 *
 * <p>为什么必须分开：**改已有供应商**与**加一个新供应商**是两个层级的操作。
 * 混在同一组控件里，用户点「保存」时无法判断自己是在改还是在增，误操作代价是
 * 覆盖掉一个能用的配置。分开之后，两边的字段、按钮、校验都各自独立。</p>
 *
 * <p>列表里正在启用的那家用绿色加粗的 {@code ●}（并附其模型名），其余为 {@code ○}
 * 且使用正常前景色——不用灰色低透明度，那玩意费眼。</p>
 *
 * @since 0.2.0
 */
public final class ProviderDialog {

    private final ModelConfigStore store;

    private ListView<String> list;

    private Label status;

    /** 「新增」区自己的反馈行：错误必须显示在按钮旁边，否则等于没提示。 */
    private Label createStatus;

    private String activeId;

    // ---- ① 编辑区（改已有供应商） ----
    private Label editIdLabel;

    private ChoiceBox<String> editAdapter;

    private TextField editBaseUrl;

    private TextField editModel;

    private MenuButton editModelPresets;

    private PasswordField editApiKey;

    private TextField editApiKeyPlain;

    private TextField editTimeout;

    private TextField editRetries;

    private Button editSave;

    private Button setDefault;

    private Button remove;

    // ---- ② 新增区（加一个新供应商，字段与按钮完全独立） ----
    private ComboBox<String> presetBox;

    private TextField newId;

    private ChoiceBox<String> newAdapter;

    private TextField newBaseUrl;

    private TextField newModel;

    private MenuButton newModelPresets;

    private PasswordField newApiKey;

    private TextField newApiKeyPlain;

    private TextField newTimeout;

    private TextField newRetries;

    private Button create;

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
        // 主题必须铺到对话框：Dialog 有自己的场景根，不继承主窗口样式
        dialog.getDialogPane().setStyle(Palette.theme());
        dialog.getDialogPane().setPrefWidth(1280);
        dialog.getDialogPane().setMinWidth(1280);
        // 限高 + 可滚动：内容一多，对话框会长过屏幕，「新增」按钮被切在屏幕外
        // （真机上就撞到了：按钮只露一半，用户以为点了没反应）
        dialog.getDialogPane().setPrefHeight(720);
        dialog.getDialogPane().setMaxHeight(900);
        try {
            reload(null);
        } catch (RuntimeException e) {
            // 兜底：任何意外都不该让「打开供应商」变成一次报错
            warn("打开配置失败：" + e.getMessage());
        }
        dialog.showAndWait();
    }

    private VBox buildBody(Dialog<ButtonType> dialog, Runnable onChanged) {
        list = new ListView<>();
        list.setPrefWidth(360);
        list.setPrefHeight(400);
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(String id, boolean empty) {
                super.updateItem(id, empty);
                if (empty || id == null) {
                    setText(null);
                    setStyle("");
                    return;
                }
                boolean active = id.equals(activeId);
                String model = modelOf(id);
                setText((active ? "\u25cf " : "\u25cb ") + id + (model == null ? "" : "   " + model));
                if (active) {
                    setStyle("-fx-text-fill: " + Palette.SUCCESS + "; -fx-font-weight: bold;");
                } else {
                    // 正常前景色：之前用弱化色 + 白底，等于让人眯眼看
                    setStyle("-fx-text-fill: " + Palette.FOREGROUND + ";");
                }
            }
        });
        list.getSelectionModel().selectedItemProperty().addListener((obs, old, now) -> {
            if (now != null) {
                loadForEdit(now);
            }
        });

        status = new Label();
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.SUCCESS + ";");

        VBox right = new VBox(10,
                sectionTitle("① 编辑已选中的供应商"),
                buildEditGrid(onChanged),
                new Separator(),
                sectionTitle("② 新增供应商（独立于上面的编辑）"),
                buildCreateGrid(dialog, onChanged),
                new Separator(),
                hint());
        HBox.setHgrow(right, Priority.ALWAYS);

        HBox body = new HBox(14, new VBox(6, status, list), right);
        VBox content = new VBox(8, body);
        content.setPadding(new Insets(6));
        content.setStyle(Palette.theme());

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setStyle(Palette.theme() + "-fx-background: #1E1E1E;");
        VBox root = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        root.setStyle(Palette.theme());
        return root;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: "
                + Palette.FOCUS_BORDER + ";");
        return label;
    }

    // ---------------------------------------------------------------- ① 编辑

    private GridPane buildEditGrid(Runnable onChanged) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);

        editIdLabel = new Label("（未选择）");
        editIdLabel.setStyle("-fx-text-fill: " + Palette.FOREGROUND + ";");
        editAdapter = new ChoiceBox<>();
        editAdapter.getItems().addAll(ProviderForm.ADAPTERS);
        editBaseUrl = new TextField();
        editBaseUrl.setPrefWidth(460);
        editModel = new TextField();
        editModel.setPrefWidth(360);
        editModelPresets = new MenuButton("候选");
        editModelPresets.setPrefWidth(96);
        editApiKey = new PasswordField();
        editApiKey.setPrefWidth(400);
        editApiKeyPlain = new TextField();
        editTimeout = new TextField();
        editRetries = new TextField();

        editSave = new Button("保存修改");
        setDefault = new Button("设为默认");
        remove = new Button("删除");

        int row = 0;
        grid.addRow(row++, new Label("供应商 ID"), editIdLabel);
        grid.addRow(row++, new Label("适配器"), editAdapter);
        grid.addRow(row++, new Label("基础地址"), editBaseUrl);
        grid.addRow(row++, new Label("模型"), new HBox(6, editModel, editModelPresets));
        grid.addRow(row++, new Label("API Key"), keyRow(editApiKey, editApiKeyPlain));
        grid.addRow(row++, new Label("超时（秒）"), editTimeout);
        grid.addRow(row++, new Label("重试次数"), editRetries);
        grid.add(new HBox(8, editSave, setDefault, remove), 0, row, 2, 1);

        editSave.setOnAction(event -> {
            String id = list.getSelectionModel().getSelectedItem();
            if (id == null) {
                warn("请先在左侧选择一个供应商");
                return;
            }
            ProviderForm.Draft draft = new ProviderForm.Draft(id, editAdapter.getValue(),
                    editBaseUrl.getText(), editApiKey.getText(), editModel.getText(),
                    editTimeout.getText(), editRetries.getText());
            String error = ProviderForm.save(store, draft, false);
            if (error != null) {
                warn(error);
                return;
            }
            reload(id);
            ok("已保存修改：" + id);
            onChanged.run();
        });

        setDefault.setOnAction(event -> {
            String id = list.getSelectionModel().getSelectedItem();
            if (id == null) {
                warn("请先在左侧选择一个供应商");
                return;
            }
            store.setDefault(id);
            reload(id);
            ok("已设为默认：" + id + "（新建会话后生效）");
            onChanged.run();
        });

        remove.setOnAction(event -> {
            String id = list.getSelectionModel().getSelectedItem();
            if (id == null) {
                warn("请先在左侧选择一个供应商");
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "确定删除供应商 " + id + " 吗？", ButtonType.OK, ButtonType.CANCEL);
            confirm.initOwner(editSave.getScene() == null ? null : editSave.getScene().getWindow());
            confirm.getDialogPane().setStyle(Palette.theme());
            confirm.setTitle("删除供应商");
            confirm.showAndWait().filter(picked -> picked == ButtonType.OK).ifPresent(picked -> {
                store.removeProvider(id);
                reload(null);
                ok("已删除：" + id);
                onChanged.run();
            });
        });
        return grid;
    }

    private void loadForEdit(String id) {
        ProviderForm.LoadResult loaded = ProviderForm.loadSafely(store);
        if (!loaded.ok()) {
            warn("配置读取失败：" + loaded.error());
            return;
        }
        ModelConfig config = loaded.config();
        ProviderConfig provider = config.providersOrEmpty().get(id);
        ProviderForm.Draft draft = ProviderForm.of(id, provider);
        editIdLabel.setText(draft.id());
        editAdapter.setValue(draft.adapter());
        editBaseUrl.setText(draft.baseUrl());
        editModel.setText(draft.model());
        fillModelMenu(editModelPresets, editModel, id);
        editApiKey.setText(draft.apiKey());
        editTimeout.setText(draft.timeoutSeconds());
        editRetries.setText(draft.maxRetries());
        setDefault.setDisable(config.defaultProvider() != null && config.defaultProvider().equals(id));
    }

    // ---------------------------------------------------------------- ② 新增

    private GridPane buildCreateGrid(Dialog<ButtonType> dialog, Runnable onChanged) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(6);

        presetBox = new ComboBox<>();
        presetBox.getItems().add("（自定义）");
        presetBox.getItems().addAll(ProviderForm.knownProviders());
        presetBox.setValue("（自定义）");
        Button usePreset = new Button("套用预设");

        newId = new TextField();
        newId.setPromptText("例如 MyGateway");
        newAdapter = new ChoiceBox<>();
        newAdapter.getItems().addAll(ProviderForm.ADAPTERS);
        newAdapter.setValue(ProviderForm.ADAPTERS[0]);
        newBaseUrl = new TextField();
        newBaseUrl.setPromptText("https://…");
        newBaseUrl.setPrefWidth(460);
        newModel = new TextField();
        newModel.setPrefWidth(360);
        newModel.setPromptText("模型名，如 deepseek-chat");
        newModelPresets = new MenuButton("候选");
        newModelPresets.setPrefWidth(96);
        newApiKey = new PasswordField();
        newApiKey.setPrefWidth(400);
        newApiKeyPlain = new TextField();
        newTimeout = new TextField("60");
        newRetries = new TextField("2");

        create = new Button("新增");

        int row = 0;
        grid.addRow(row++, new Label("套用预设"), new HBox(8, presetBox, usePreset));
        grid.addRow(row++, new Label("供应商 ID"), newId);
        grid.addRow(row++, new Label("适配器"), newAdapter);
        grid.addRow(row++, new Label("基础地址"), newBaseUrl);
        grid.addRow(row++, new Label("模型"), new HBox(6, newModel, newModelPresets));
        grid.addRow(row++, new Label("API Key"), keyRow(newApiKey, newApiKeyPlain));
        grid.addRow(row++, new Label("超时（秒）"), newTimeout);
        grid.addRow(row++, new Label("重试次数"), newRetries);
        createStatus = new Label();
        createStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.MUTED + ";");
        createStatus.setWrapText(true);
        grid.addRow(row++, new Label("结果"), createStatus);
        grid.add(create, 0, row, 2, 1);

        // 预设只影响「新增」区，不碰上面的编辑区——两者互不影响
        usePreset.setOnAction(event -> {
            String picked = presetBox.getValue();
            ProviderForm.Draft draft = "（自定义）".equals(picked) || picked == null
                    ? ProviderForm.preset("")
                    : ProviderForm.preset(picked);
            fillCreate(draft);
            ok("已套用预设" + ("（自定义）".equals(picked) ? "" : "：" + picked) + "，补上 API Key 后点「新增」");
        });

        create.setOnAction(event -> {
            ProviderForm.Draft draft = new ProviderForm.Draft(newId.getText(), newAdapter.getValue(),
                    newBaseUrl.getText(), newApiKey.getText(), newModel.getText(),
                    newTimeout.getText(), newRetries.getText());
            String error = ProviderForm.save(store, draft, true);
            if (error != null) {
                warn(error);
                createWarn(error);
                return;
            }
            String id = draft.id().trim();
            reload(id);
            list.getSelectionModel().select(id);
            fillCreate(ProviderForm.preset(""));
            ok("已新增：" + id + "（可在上面的编辑区继续调整，或点「设为默认」启用）");
            createOk("已新增 " + id + "，已写入 " + store.path());
            onChanged.run();
        });
        return grid;
    }

    /**
     * 把候选模型装进下拉菜单。
     *
     * @param menu  菜单按钮
     * @param model 模型输入框
     * @param id    供应商 ID
     */
    private static void fillModelMenu(MenuButton menu, TextField model, String id) {
        menu.getItems().clear();
        String[] candidates = ProviderForm.modelsFor(id);
        if (candidates.length == 0) {
            MenuItem none = new MenuItem("（无候选，直接输入）");
            none.setDisable(true);
            menu.getItems().add(none);
            return;
        }
        for (String candidate : candidates) {
            MenuItem item = new MenuItem(candidate);
            item.setOnAction(event -> model.setText(candidate));
            menu.getItems().add(item);
        }
    }

    private void createOk(String message) {
        createStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.SUCCESS + ";");
        createStatus.setText("\u2713 " + message);
    }

    private void createWarn(String message) {
        createStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.FAILURE + ";");
        createStatus.setText("\u2717 " + message);
    }

    private void fillCreate(ProviderForm.Draft draft) {
        newId.setText(draft.id());
        newAdapter.setValue(draft.adapter());
        newBaseUrl.setText(draft.baseUrl());
        newModel.setText(draft.model());
        fillModelMenu(newModelPresets, newModel, draft.id());
        newApiKey.setText("");
        newTimeout.setText(draft.timeoutSeconds());
        newRetries.setText(draft.maxRetries());
    }

    // ---------------------------------------------------------------- 公共

    /**
     * 密钥输入行：默认打码，点「显示」切到明文。
     *
     * @param masked 打码输入框
     * @param plain  明文输入框
     * @return 一行控件
     */
    private static HBox keyRow(PasswordField masked, TextField plain) {
        plain.setVisible(false);
        plain.setManaged(false);
        plain.setPrefWidth(masked.getPrefWidth());
        Button toggle = new Button("显示");
        toggle.setOnAction(event -> {
            boolean showPlain = !plain.isVisible();
            if (showPlain) {
                plain.setText(masked.getText());
            } else {
                masked.setText(plain.getText());
            }
            masked.setVisible(!showPlain);
            masked.setManaged(!showPlain);
            plain.setVisible(showPlain);
            plain.setManaged(showPlain);
            toggle.setText(showPlain ? "隐藏" : "显示");
        });
        masked.textProperty().addListener((obs, old, now) -> plain.setText(now));
        plain.textProperty().addListener((obs, old, now) -> masked.setText(now));
        return new HBox(8, masked, plain, toggle);
    }

    private Label hint() {
        Label label = new Label("""
                改密钥的三种方式（推荐前两种，直接写文件是明文）：
                  1) 环境变量  AHA_API_KEY_<供应商ID大写>      例如 AHA_API_KEY_DEEPSEEK
                  2) 密钥库    aha secret set <供应商ID>
                  3) 上面的 API Key 字段（会写入 Model.yml，明文保存）""");
        label.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Palette.MUTED + ";");
        return label;
    }

    private String modelOf(String providerId) {
        ProviderForm.LoadResult loaded = ProviderForm.loadSafely(store);
        if (!loaded.ok()) {
            return null;
        }
        ProviderConfig provider = loaded.config().providersOrEmpty().get(providerId);
        return provider == null ? null : provider.model();
    }

    private void reload(String selectId) {
        ProviderForm.LoadResult loaded = ProviderForm.loadSafely(store);
        if (!loaded.ok()) {
            // 配置坏了也要能打开对话框，并把坏在哪说清楚（不许再抛异常）
            list.getItems().clear();
            list.getSelectionModel().clearSelection();
            warn("配置无法读取：" + loaded.error()
                    + "　→　请修正 " + store.path() + "（供应商 ID 必须是大驼峰，如 DeepSeek）");
            return;
        }
        ModelConfig config = loaded.config();
        activeId = config.defaultProvider();
        List<String> ids = new ArrayList<>(config.providersOrEmpty().keySet());
        list.getItems().setAll(ids);
        String target = selectId != null && ids.contains(selectId)
                ? selectId
                : ProviderForm.selectedOnOpen(activeId, ids);
        if (target != null) {
            list.getSelectionModel().select(target);
            loadForEdit(target);
        } else {
            list.getSelectionModel().clearSelection();
        }
        list.refresh();
        ProviderConfig active = activeId == null ? null : config.providersOrEmpty().get(activeId);
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.SUCCESS + ";");
        status.setText(active == null
                ? "\u25cb 未设置默认供应商"
                : "\u25cf 已启用：" + activeId + "   " + active.model()
                        + "   密钥 " + ProviderForm.mask(active.apiKey()));
    }

    private void ok(String message) {
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.SUCCESS + ";");
        status.setText("\u2713 " + message);
    }

    private void warn(String message) {
        status.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.FAILURE + ";");
        status.setText("\u2717 " + message);
    }
}
