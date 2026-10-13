package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.core.config.ModelConfig;
import com.acanx.module.aha.core.config.ModelConfigStore;
import com.acanx.module.aha.core.config.ModelTier;
import com.acanx.module.aha.core.config.ProviderConfig;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    /** FXML 骨架资源（路径集中在 {@link UiResources}）。 */
    private static final String FXML_VIEW = UiResources.PROVIDER_DIALOG_FXML;

    /** 分区标题样式（fxml 里只放文案，样式统一在控制器）。 */
    private static final String SECTION_TITLE_STYLE =
            "-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: " + Palette.FOCUS_BORDER + ";";

    /** 底部提示文案：较长且含换行，留在 Java 里 setText。 */
    private static final String HINT_TEXT = """
            改密钥的三种方式（推荐前两种，直接写文件是明文）：
              1) 环境变量  AHA_API_KEY_<供应商ID大写>      例如 AHA_API_KEY_DEEPSEEK
              2) 密钥库    aha secret set <供应商ID>
              3) 上面的 API Key 字段（会写入 Model.yml，明文保存）""";

    private final ModelConfigStore store;

    // ── FXML 注入的部件 ──────────────────────────────────────────────────────
    // 字段名必须与 provider-dialog.fxml 的 fx:id 一一对应；原生镜像下这些**私有字段**
    // 由 FXMLLoader 反射写入，因此 ProviderDialog 的字段必须登记进 reachability-metadata。

    /** 对话框内容根（VBox）。 */
    @FXML
    private VBox root;

    /** 滚动区（内容一多，对话框会长过屏幕，「新增」按钮会被切在屏幕外）。 */
    @FXML
    private ScrollPane scroll;

    @FXML
    private VBox content;

    /** 左列表 + 右两区所在的那一行。 */
    @FXML
    private HBox body;

    @FXML
    private VBox leftColumn;

    @FXML
    private VBox rightColumn;

    @FXML
    private Label editSectionTitle;

    @FXML
    private GridPane editGrid;

    @FXML
    private Label createSectionTitle;

    @FXML
    private GridPane createGrid;

    @FXML
    private Label hintLabel;

    @FXML
    private ListView<String> list;

    @FXML
    private Label status;

    /** 「新增」区自己的反馈行：错误必须显示在按钮旁边，否则等于没提示。 */
    @FXML
    private Label createStatus;

    private String activeId;

    // ---- ① 编辑区（改已有供应商） ----
    @FXML
    private Label editIdLabel;

    @FXML
    private ChoiceBox<String> editAdapter;

    @FXML
    private TextField editBaseUrl;

    @FXML
    private TextField editModel;

    @FXML
    private MenuButton editModelPresets;

    @FXML
    private HBox editModelRow;

    @FXML
    private TextField editTierUltra;

    @FXML
    private TextField editTierPro;

    @FXML
    private TextField editTierStandard;

    @FXML
    private TextField editTierFlash;

    @FXML
    private TextField editTierFallback;

    /** 编辑区五档输入（档位名 → 输入框），由 {@link #setTierInputs} 按枚举顺序装配。 */
    private final Map<ModelTier, TextField> editTiers = new LinkedHashMap<>();

    @FXML
    private ChoiceBox<String> editDefaultTier;

    @FXML
    private PasswordField editApiKey;

    @FXML
    private TextField editApiKeyPlain;

    @FXML
    private Button editKeyToggle;

    @FXML
    private HBox editKeyRow;

    @FXML
    private TextField editTimeout;

    @FXML
    private TextField editRetries;

    @FXML
    private HBox editActions;

    @FXML
    private Button editSave;

    @FXML
    private Button setDefault;

    @FXML
    private Button remove;

    // ---- ② 新增区（加一个新供应商，字段与按钮完全独立） ----
    @FXML
    private HBox presetRow;

    @FXML
    private ComboBox<String> presetBox;

    @FXML
    private Button usePreset;

    @FXML
    private TextField newId;

    @FXML
    private ChoiceBox<String> newAdapter;

    @FXML
    private TextField newBaseUrl;

    @FXML
    private TextField newModel;

    @FXML
    private MenuButton newModelPresets;

    @FXML
    private HBox newModelRow;

    @FXML
    private TextField newTierUltra;

    @FXML
    private TextField newTierPro;

    @FXML
    private TextField newTierStandard;

    @FXML
    private TextField newTierFlash;

    @FXML
    private TextField newTierFallback;

    /** 新增区五档输入（档位名 → 输入框）。 */
    private final Map<ModelTier, TextField> newTiers = new LinkedHashMap<>();

    @FXML
    private ChoiceBox<String> newDefaultTier;

    @FXML
    private PasswordField newApiKey;

    @FXML
    private TextField newApiKeyPlain;

    @FXML
    private Button newKeyToggle;

    @FXML
    private HBox newKeyRow;

    @FXML
    private TextField newTimeout;

    @FXML
    private TextField newRetries;

    @FXML
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
        dialog.getDialogPane().setContent(loadView(onChanged));
        // 主题必须铺到对话框：Dialog 有自己的场景根，不继承主窗口样式
        ThemePaint.dialog(dialog.getDialogPane());
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

    /**
     * 装载 FXML 骨架并接线。
     *
     * <p>与 {@code DesktopShell} 同款：本类既是对话框状态的持有者，也是它的 **FXML 控制器**
     * （{@code setController(this)}）。这样现有方法（含包内可见的测试接口）一个都不用改，
     * 而「长什么样」搬进了 {@code provider-dialog.fxml}。</p>
     *
     * <p>装载失败时打 ASCII 标记并显式 flush。注意**标记必须印在真的会执行到的那个 catch 里**
     * ——TS-2610-032 第三轮的教训：守卫写在「证不出会抛」的位置，会被静态分析当死代码
     * 连同字符串一起删掉，看起来像守卫、实际什么也不做。</p>
     *
     * @param onChanged 配置变化后的回调
     * @return 对话框内容根节点
     */
    private Parent loadView(Runnable onChanged) {
        FXMLLoader loader = new FXMLLoader(ProviderDialog.class.getResource(FXML_VIEW));
        loader.setController(this);
        Parent loaded;
        try {
            loaded = loader.load();
        } catch (IOException e) {
            System.err.println("[ui-fxml-load-failed] " + e);
            System.err.flush();
            throw new IllegalStateException("供应商对话框界面装载失败：" + FXML_VIEW, e);
        }
        wireView(onChanged);
        return loaded;
    }

    /** 把注入的节点接起来：样式、事件、监听（原来在建节点时顺手做的那些）。 */
    private void wireView(Runnable onChanged) {
        // 主题必须铺到对话框：Dialog 有自己的场景根，不继承主窗口样式
        ThemePaint.themed(root, Palette.dialogTheme());

        content.setSpacing(8);
        content.setPadding(new Insets(6));
        ThemePaint.themed(content, Palette.dialogTheme());

        scroll.setFitToWidth(true);
        ThemePaint.themed(scroll, Palette.dialogTheme());
        VBox.setVgrow(scroll, Priority.ALWAYS);

        body.setSpacing(14);
        leftColumn.setSpacing(6);
        rightColumn.setSpacing(10);
        HBox.setHgrow(rightColumn, Priority.ALWAYS);

        status.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.SUCCESS + ";");

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

        editSectionTitle.setStyle(SECTION_TITLE_STYLE);
        createSectionTitle.setStyle(SECTION_TITLE_STYLE);

        hintLabel.setText(HINT_TEXT);
        hintLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + Palette.MUTED + ";");

        wireEditGrid(onChanged);
        wireCreateGrid(onChanged);
    }

    // ---------------------------------------------------------------- ① 编辑

    /** ① 编辑区：给注入的控件装配选项、样式与动作。 */
    private void wireEditGrid(Runnable onChanged) {
        editGrid.setHgap(10);
        editGrid.setVgap(6);

        editIdLabel.setStyle("-fx-text-fill: " + Palette.FOREGROUND + ";");
        editAdapter.getItems().addAll(ProviderForm.ADAPTERS);
        editBaseUrl.setPrefWidth(460);
        editModel.setPrefWidth(360);
        editModelPresets.setPrefWidth(96);
        editModelRow.setSpacing(6);
        setTierInputs(editTiers, editTierUltra, editTierPro, editTierStandard,
                editTierFlash, editTierFallback);
        editDefaultTier.getItems().addAll(tierNames());
        editDefaultTier.setValue(ModelTier.STANDARD.configName());
        editApiKey.setPrefWidth(400);
        wireKeyRow(editApiKey, editApiKeyPlain, editKeyToggle);
        editKeyRow.setSpacing(8);
        editActions.setSpacing(8);

        editSave.setOnAction(event -> {
            String id = list.getSelectionModel().getSelectedItem();
            if (id == null) {
                warn("请先在左侧选择一个供应商");
                return;
            }
            ProviderForm.Draft draft = new ProviderForm.Draft(id, editAdapter.getValue(),
                    editBaseUrl.getText(), editApiKey.getText(), editModel.getText(),
                    tierValues(editTiers), editDefaultTier.getValue(),
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
            ThemePaint.dialog(confirm.getDialogPane());
            confirm.setTitle("删除供应商");
            confirm.showAndWait().filter(picked -> picked == ButtonType.OK).ifPresent(picked -> {
                store.removeProvider(id);
                reload(null);
                ok("已删除：" + id);
                onChanged.run();
            });
        });
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
        editTiers.forEach((tier, field) -> field.setText(
                draft.tierModels().getOrDefault(tier.configName(), "")));
        editDefaultTier.setValue(draft.defaultTier() == null
                ? ModelTier.STANDARD.configName() : draft.defaultTier());
        fillModelMenu(editModelPresets, editModel, id);
        editApiKey.setText(draft.apiKey());
        editTimeout.setText(draft.timeoutSeconds());
        editRetries.setText(draft.maxRetries());
        setDefault.setDisable(config.defaultProvider() != null && config.defaultProvider().equals(id));
    }

    // ---------------------------------------------------------------- ② 新增

    /** ② 新增区：给注入的控件装配选项、样式与动作（与①完全独立）。 */
    private void wireCreateGrid(Runnable onChanged) {
        createGrid.setHgap(10);
        createGrid.setVgap(6);

        presetBox.getItems().add("（自定义）");
        presetBox.getItems().addAll(ProviderForm.knownProviders());
        presetBox.setValue("（自定义）");
        presetRow.setSpacing(8);

        newId.setPromptText("例如 MyGateway");
        newAdapter.getItems().addAll(ProviderForm.ADAPTERS);
        newAdapter.setValue(ProviderForm.ADAPTERS[0]);
        newBaseUrl.setPromptText("https://…");
        newBaseUrl.setPrefWidth(460);
        newModel.setPrefWidth(360);
        newModel.setPromptText("模型名，如 deepseek-chat");
        newModelPresets.setPrefWidth(96);
        newModelRow.setSpacing(6);
        setTierInputs(newTiers, newTierUltra, newTierPro, newTierStandard,
                newTierFlash, newTierFallback);
        newDefaultTier.getItems().addAll(tierNames());
        newDefaultTier.setValue(ModelTier.STANDARD.configName());
        newApiKey.setPrefWidth(400);
        wireKeyRow(newApiKey, newApiKeyPlain, newKeyToggle);
        newKeyRow.setSpacing(8);
        newTimeout.setText("60");
        newRetries.setText("2");

        createStatus.setStyle("-fx-font-size: 12px; -fx-text-fill: " + Palette.MUTED + ";");
        createStatus.setWrapText(true);

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
                    tierValues(newTiers), newDefaultTier.getValue(),
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
        newTiers.forEach((tier, field) -> field.setText(
                draft.tierModels().getOrDefault(tier.configName(), "")));
        newDefaultTier.setValue(draft.defaultTier() == null
                ? ModelTier.STANDARD.configName() : draft.defaultTier());
        fillModelMenu(newModelPresets, newModel, draft.id());
        newApiKey.setText("");
        newTimeout.setText(draft.timeoutSeconds());
        newRetries.setText(draft.maxRetries());
    }

    // ---------------------------------------------------------------- 公共

    /**
     * 密钥输入行：默认打码，点「显示」切到明文。
     *
     * <p>三个控件（打码框 / 明文框 / 按钮）由 fxml 提供，这里只负责接线。</p>
     *
     * @param masked 打码输入框
     * @param plain  明文输入框
     * @param toggle 「显示 / 隐藏」按钮
     */
    private static void wireKeyRow(PasswordField masked, TextField plain, Button toggle) {
        plain.setVisible(false);
        plain.setManaged(false);
        plain.setPrefWidth(masked.getPrefWidth());
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
    }

    /**
     * 按 {@link ModelTier#strongestFirst()} 的顺序把 fxml 里的档位输入框装进映射表。
     *
     * <p><strong>档位行在 fxml 里是写死的</strong>（Ultra / Pro / Standard / Flash / Fallback）：
     * 它是产品级设定，写在界面上整张表单一眼可读。为防「枚举改了、fxml 忘了改」这种口径分裂，
     * 这里做一次**硬校验**：数量不符直接抛，而不是静默少一两档。</p>
     *
     * @param target 目标映射（编辑区 / 新增区各一份）
     * @param fields fxml 里的档位输入框，顺序需与枚举一致
     */
    private static void setTierInputs(Map<ModelTier, TextField> target, TextField... fields) {
        ModelTier[] tiers = ModelTier.strongestFirst();
        if (tiers.length != fields.length) {
            throw new IllegalStateException("档位数与界面不一致：ModelTier 有 " + tiers.length
                    + " 档，provider-dialog.fxml 给了 " + fields.length
                    + " 个输入框——改枚举时要同步改 fxml");
        }
        for (int i = 0; i < tiers.length; i++) {
            fields[i].setPrefWidth(360);
            fields[i].setPromptText(tiers[i] == ModelTier.STANDARD
                    ? "必填档" : "可选，留空回退 Standard");
            target.put(tiers[i], fields[i]);
        }
    }

    /**
     * 五档名列表（用于默认档下拉）。
     *
     * @return 档位名列表
     */
    private static List<String> tierNames() {
        List<String> names = new ArrayList<>();
        for (ModelTier tier : ModelTier.strongestFirst()) {
            names.add(tier.configName());
        }
        return names;
    }

    /**
     * 收集档位输入框的值。
     *
     * @param fields 档位输入框
     * @return 档位名 → 输入值
     */
    private static Map<String, String> tierValues(Map<ModelTier, TextField> fields) {
        Map<String, String> values = new LinkedHashMap<>();
        fields.forEach((tier, field) -> values.put(tier.configName(), field.getText()));
        return values;
    }

    private String modelOf(String providerId) {
        ProviderForm.LoadResult loaded = ProviderForm.loadSafely(store);
        if (!loaded.ok()) {
            return null;
        }
        ProviderConfig provider = loaded.config().providersOrEmpty().get(providerId);
        return provider == null ? null : provider.effectiveModel(loaded.config().defaultTier());
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
                : "\u25cf 已启用：" + activeId + "   "
                        + active.effectiveTier(config.defaultTier()).configName() + " · "
                        + active.effectiveModel(config.defaultTier())
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
