package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.common.AppVersion;
import com.acanx.module.aha.common.exception.UpdateException;
import com.acanx.module.aha.core.update.AhaVersion;
import com.acanx.module.aha.core.update.AssetInfo;
import com.acanx.module.aha.core.update.AssetSelector;
import com.acanx.module.aha.core.update.InstallLayout;
import com.acanx.module.aha.core.update.PackageDownloader;
import com.acanx.module.aha.core.update.PlatformTag;
import com.acanx.module.aha.core.update.ReleaseChannel;
import com.acanx.module.aha.core.update.ReleaseFilter;
import com.acanx.module.aha.core.update.UpdateKind;
import com.acanx.module.aha.core.update.UpdateLauncher;
import com.acanx.module.aha.core.update.UpdatePaths;
import com.acanx.module.aha.core.update.UpdateService;
import com.acanx.module.aha.desktop.fx.FxDispatcher;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 设置里的「升级」tab（issue #51 §3.1 ~ §3.4）。
 *
 * <p>独立成类而不是塞进 {@link SettingsDialog}：那个类已经在管主题 / 字号 / 模型三块，
 * 再加一条完整的状态机（检查 → 列表 → 确认 → 下载 → 校验 → 暂存 → 换装）会失控。</p>
 *
 * <p>原则（与 `GUIDesign.md` 一致）：</p>
 * <ul>
 *   <li><b>不让用户点了才发现不行</b>：平台发不了包时按钮置灰，并给出「怎么办」的手动取件指引；</li>
 *   <li><b>不出现点了没反应的控件</b>：检查更新在任何平台都可用（它只是读发布页）；</li>
 *   <li><b>失败要能读懂</b>：状态行写清「哪里错、下一步」，不丢栈；</li>
 *   <li><b>切通道立即作废旧结果</b>：否则会出现「切了通道还显示旧通道版本」。</li>
 * </ul>
 *
 * <p>线程：检查与下载跑在虚拟线程上，界面更新一律经 {@link FxDispatcher}
 * （桌面端不直接碰 JavaFX 线程 API，见 {@code FxThreadContractTest}）。</p>
 *
 * @since 0.2.0
 */
public final class UpgradePane {

    private static final Logger LOG = LoggerFactory.getLogger(UpgradePane.class);

    /** 「升级」tab 的 id（契约见 issue #51 §3.1）。 */
    public static final String ID_TAB = "aha.settings.tab.upgrade";

    /** 通道选择区的 id。 */
    public static final String ID_CHANNEL = "aha.upgrade.channel";

    /** 当前版本行的 id。 */
    public static final String ID_CURRENT = "aha.upgrade.current";

    /** 「检查更新」按钮的 id。 */
    public static final String ID_CHECK = "aha.upgrade.check";

    /** 版本列表的 id。 */
    public static final String ID_LIST = "aha.upgrade.list";

    /** 状态行的 id。 */
    public static final String ID_STATUS = "aha.upgrade.status";

    /** 升级 / 降级按钮的 id（契约之外，界面需要）。 */
    public static final String ID_ACTION = "aha.upgrade.action";

    /** 进度条的 id（契约之外，界面需要）。 */
    public static final String ID_PROGRESS = "aha.upgrade.progress";

    /** 数据来源说明：状态行里带上，让用户知道「这是从哪来的」。 */
    private static final String SOURCE = "GitHub 发布页";

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final DesktopSettings settings;

    private final FxDispatcher dispatcher;

    private final Runnable onRequestExit;

    private final UpdateService service;

    private final PlatformTag platform;

    private final UpdateKind kind;

    private final AhaVersion current;

    /** 本平台是否已实现应用内自更新；{@code null} 表示支持，否则是「为什么不支持」。 */
    private final String unsupportedReason;

    private final ToggleGroup channels = new ToggleGroup();

    private final RadioButton stableButton = new RadioButton(ReleaseChannel.STABLE.label());

    private final RadioButton betaButton = new RadioButton(ReleaseChannel.BETA.label());

    private final RadioButton alphaButton = new RadioButton(ReleaseChannel.ALPHA.label());

    private final HBox channelRow = new HBox(stableButton, betaButton, alphaButton);

    private final HBox checkRow = new HBox();

    private final HBox actionRow = new HBox();

    private final Label channelNote = new Label();

    private final Label currentLabel = new Label();

    private final Button checkButton = new Button("检查更新");

    private final ListView<Row> versionList = new ListView<>();

    private final Label statusLabel = new Label();

    private final Button actionButton = new Button("升级");

    private final ProgressBar progress = new ProgressBar();

    private VBox root;

    private boolean busy;

    /**
     * 构造。
     *
     * @param settings      设置（通道、备份份数等都从这里读）
     * @param dispatcher    UI 线程投递器
     * @param onRequestExit 换装前要求 App 退出（宿主实现：显示「正在替换并重启…」后退出）
     * @param service       升级服务；{@code null} 时按默认路径构造
     */
    public UpgradePane(DesktopSettings settings, FxDispatcher dispatcher, Runnable onRequestExit,
                       UpdateService service) {
        this.settings = settings;
        this.dispatcher = dispatcher;
        this.onRequestExit = onRequestExit == null ? () -> { } : onRequestExit;
        this.service = service == null ? new UpdateService(UpdatePaths.defaultPaths()) : service;
        this.platform = PlatformTag.detect();
        InstallLayout layout = detectLayout();
        this.kind = layout.jarBased() ? UpdateKind.DESKTOP_JVM : UpdateKind.DESKTOP_NATIVE;
        this.current = UpdateService.runningVersion();
        this.unsupportedReason = UpdateLauncher.unsupportedReason();
    }

    /**
     * 构建界面（可重复调用，返回同一份节点）。
     *
     * @return 内容节点
     */
    public VBox build() {
        if (root != null) {
            return root;
        }
        channelRow.setId(ID_CHANNEL);
        channelRow.setAlignment(Pos.CENTER_LEFT);
        channelRow.setSpacing(12);
        checkRow.setAlignment(Pos.CENTER_LEFT);
        actionRow.setAlignment(Pos.CENTER_LEFT);
        checkRow.getChildren().add(checkButton);
        actionRow.getChildren().add(actionButton);

        channelNote.setWrapText(true);
        ThemePaint.themedForever(channelNote,
                () -> "-fx-text-fill: " + Palette.MUTED + "; -fx-font-size: 11px;");
        currentLabel.setId(ID_CURRENT);
        currentLabel.setText("当前版本：AHA " + AppVersion.buildVersion() + "（"
                + kind.targetWord() + " / " + platform.value() + "）");
        ThemePaint.themedForever(currentLabel, () -> "-fx-text-fill: " + Palette.MUTED + ";");

        checkButton.setId(ID_CHECK);
        checkButton.setOnAction(event -> checkNow());
        actionButton.setId(ID_ACTION);
        actionButton.setDisable(true);
        actionButton.setOnAction(event -> startSwap());
        if (unsupportedReason != null) {
            // 门要挡在**下载之前**：Linux / macOS 暂时没有换装脚本（issue #51 §7 P4），
            // 照实说不支持 + 给手动取件指引，比让用户下完 55 MB 再看到「该包不完整」好
            // （2026-10-16 审查 P1-7）。
            checkButton.setDisable(false);
        }
        progress.setId(ID_PROGRESS);
        progress.setVisible(false);
        progress.setMaxWidth(Double.MAX_VALUE);
        statusLabel.setId(ID_STATUS);
        statusLabel.setWrapText(true);
        ThemePaint.themedForever(statusLabel, () -> "-fx-text-fill: " + Palette.MUTED + ";");

        versionList.setId(ID_LIST);
        versionList.setPrefHeight(200);
        versionList.setPlaceholder(new Label("还没有列出可用版本，点上面的「检查更新」。"));
        versionList.setCellFactory(view -> new RowCell());
        versionList.getSelectionModel().selectedItemProperty()
                .addListener((observable, old, now) -> onSelectionChanged(now));
        VBox.setVgrow(versionList, Priority.ALWAYS);

        wireChannelButtons();
        selectChannelInUi(settings.updateChannel());
        updateChannelNote();

        root = new VBox(8, channelRow, channelNote, currentLabel, checkRow, versionList, actionRow,
                progress, statusLabel);
        root.setId(ID_TAB);
        if (unsupportedReason != null) {
            statusLine(unsupportedReason + "；可手动到发布页取件（「检查更新」仍可用，便于看有哪些版本）。");
        } else {
            statusLine("点「检查更新」从 " + SOURCE + " 取可用版本。默认通道 "
                    + ReleaseChannel.DEFAULT.label() + "；Stable 目前还没有带资产的正式发版。"
                    + "下载与替换可能需要几十秒，期间请不要关闭程序。");
        }
        return root;
    }

    /**
     * 检查更新（帮助菜单的入口也走这里：切到本 tab 后自动调一次）。
     *
     * <p>检查只读发布页，任何平台都能跑（§3.4）。</p>
     */
    public void checkNow() {
        if (busy) {
            return;
        }
        ReleaseChannel channel = selectedChannel();
        busy = true;
        checkButton.setDisable(true);
        actionButton.setDisable(true);
        versionList.getItems().clear();
        statusLine("正在检查「" + channel.label() + "」通道的可用版本…");
        settings.lastCheckAt(Instant.now().toString());
        settings.save();
        Thread.ofVirtual().name("aha-upgrade-check").start(() -> {
            try {
                List<Row> rows = catalog(channel);
                dispatcher.dispatch(() -> {
                    versionList.getItems().setAll(rows);
                    statusLine(rows.isEmpty() ? emptyReason(channel)
                            : "检查完成，共 " + rows.size() + " 个版本（来源：" + SOURCE + "）");
                    busy = false;
                    checkButton.setDisable(false);
                });
            } catch (UpdateException e) {
                dispatcher.dispatch(() -> {
                    statusLine(failure(e));
                    busy = false;
                    checkButton.setDisable(false);
                });
            } catch (RuntimeException e) {
                LOG.warn("检查更新时出现未预期的问题", e);
                dispatcher.dispatch(() -> {
                    statusLine("检查失败：" + e.getClass().getSimpleName() + ": " + e.getMessage());
                    busy = false;
                    checkButton.setDisable(false);
                });
            }
        });
    }

    /**
     * 把候选版本整理成列表行。
     *
     * <p>可用性<b>逐版本</b>判定（缺本平台资产 ⇒ 灰显并说明原因，§3.2）。这里刻意用
     * {@link AssetSelector#select} 并捕异常取消息：那条消息会列出该版本**实际有哪些资产**，
     * 正是用户需要看到的「为什么这个版本不能用」。</p>
     */
    private List<Row> catalog(ReleaseChannel channel) {
        List<ReleaseFilter.ChannelRelease> candidates = service.list(channel, kind);
        List<Row> rows = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            ReleaseFilter.ChannelRelease release = candidates.get(index);
            AssetInfo asset = null;
            String reason = "";
            try {
                asset = AssetSelector.select(release, channel, kind, platform);
            } catch (UpdateException e) {
                reason = e.getMessage();
            }
            rows.add(new Row(release, asset, reason, current, index == 0,
                    release.version().compareTo(current) > 0));
        }
        return rows;
    }

    /** 通道单选：切了就作废旧列表（否则会「切了通道还显示旧通道版本」）。 */
    private void wireChannelButtons() {
        for (RadioButton button : List.of(stableButton, betaButton, alphaButton)) {
            button.setToggleGroup(channels);
        }
        channels.selectedToggleProperty().addListener((observable, old, now) -> {
            if (now == null) {
                return;
            }
            ReleaseChannel channel = selectedChannel();
            if (channel == ReleaseChannel.ALPHA && !settings.alphaAcknowledged() && !confirmAlpha()) {
                // 用户没确认「Alpha 不是交付物」⇒ 退回原来的通道，不让选择停在那儿
                selectChannelInUi(settings.updateChannel());
                return;
            }
            settings.updateChannel(channel);
            settings.save();
            updateChannelNote();
            versionList.getItems().clear();
            actionButton.setDisable(true);
            statusLine("已切到「" + channel.label() + "」通道，点「检查更新」刷新列表。");
        });
    }

    /** 通道说明：写清这个通道是什么、有多稳。 */
    private void updateChannelNote() {
        channelNote.setText("通道说明：" + selectedChannel().description());
    }

    /** Alpha 首次切换的一次性确认（§2.4 / §10 D4）。 */
    private boolean confirmAlpha() {
        CheckBox never = new CheckBox("不再提示");
        boolean confirmed = simpleConfirm("切到快照 Alpha 通道",
                "Alpha 是某个未合并 PR 的试包，不是交付物，7 天后会被自动清理。",
                "我知道了", never);
        if (confirmed && never.isSelected()) {
            settings.alphaAcknowledged(true);
            settings.save();
        }
        return confirmed;
    }

    /** 降级二次确认（§4.6）：勾选「我了解」才允许确认。 */
    private boolean confirmDowngrade(Row row) {
        CheckBox aware = new CheckBox("我了解：~/.aha 里的配置与数据不会跟着回滚");
        return simpleConfirm("确认降级",
                "将把 AHA 从 " + current.value() + " 降到 " + row.version().value() + "。",
                "降级", aware);
    }

    /**
     * 一个带「必须勾选才可确认」的单选确认框。
     *
     * @param title       标题
     * @param header      说明
     * @param confirmText 确认按钮文字
     * @param required    必须勾选的复选框；{@code null} 表示不需要勾选
     * @return 是否确认
     */
    private boolean simpleConfirm(String title, String header, String confirmText, CheckBox required) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        ButtonType confirm = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(confirm, ButtonType.CANCEL);
        if (required != null) {
            dialog.getDialogPane().setContent(required);
        }
        ThemePaint.dialog(dialog.getDialogPane());
        if (required != null) {
            Node confirmButton = dialog.getDialogPane().lookupButton(confirm);
            confirmButton.setDisable(true);
            required.selectedProperty().addListener(
                    (observable, old, now) -> confirmButton.setDisable(!now));
        }
        return dialog.showAndWait().filter(confirm::equals).isPresent();
    }

    /** 选中一行：按钮变成「升级到 X」/「降级到 X」；不可用时说清为什么。 */
    private void onSelectionChanged(Row row) {
        if (row == null) {
            actionButton.setDisable(true);
            return;
        }
        if (!row.available()) {
            actionButton.setDisable(true);
            statusLine(row.unavailableReason());
            return;
        }
        if (unsupportedReason != null) {
            actionButton.setDisable(true);
            statusLine(unsupportedReason + "；可手动到发布页取件。");
            return;
        }
        actionButton.setDisable(busy);
        actionButton.setText((row.newer() ? "升级到 " : "降级到 ") + row.version().value());
        statusLine(row.newer()
                ? "将升级到 " + row.version().value() + "（" + row.assetName() + "）"
                : "将降级到 " + row.version().value() + "（" + row.assetName() + "）；"
                        + "降级不回滚 ~/.aha 里的用户数据，需要二次确认。");
    }

    /** 换装：下载 → 校验 → 解包 → 写计划 → 拉起 helper → 退出（§3.3 状态机）。 */
    private void startSwap() {
        Row row = versionList.getSelectionModel().getSelectedItem();
        if (row == null || !row.available() || busy) {
            return;
        }
        if (unsupportedReason != null) {
            // 兜底：界面已置灰，这里再挡一次（「不让用户点了才发现不行」）
            statusLine(unsupportedReason + "；可手动到发布页取件。");
            return;
        }
        if (kind.hasPlatform() && !platform.hasPublishedPackages()) {
            // 兜底：界面已置灰，这里再挡一次（「不让用户点了才发现不行」）
            statusLine(platform.unpublishedReason() + "；可手动到发布页取件");
            return;
        }
        if (!row.newer() && !confirmDowngrade(row)) {
            return;
        }
        ReleaseChannel channel = selectedChannel();
        busy = true;
        checkButton.setDisable(true);
        actionButton.setDisable(true);
        progress.setVisible(true);
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        statusLine("正在下载 " + row.assetName() + "…");
        Thread.ofVirtual().name("aha-upgrade-swap").start(() -> {
            try {
                UpdateService.Check check = new UpdateService.Check(channel, kind, platform, current,
                        row.release(), row.asset(), row.newer());
                UpdateService.Staged staged = service.stage(check, (bytes, total) -> {
                    if (total > 0) {
                        double value = Math.min(1.0, (double) bytes / total);
                        dispatcher.dispatch(() -> progress.setProgress(value));
                    }
                }, PackageDownloader.CancelSignal.NEVER);
                Path plan = service.prepareSwap(staged, InstallLayout.detect(), current);
                // 拉起 helper 并**等它自报启动**：这条等待不能放在界面线程上（会卡界面），
                // 更不能省——helper 没起来就退出，用户会面对「App 关了、什么都没发生」
                // （2026-10-16 审查 P1-8）。
                service.launchHelper(plan);
                String checksumNote = staged.checksumVerified()
                        ? ""
                        : "；该版本未提供发布页校验和，已用包内清单逐项核对";
                dispatcher.dispatch(() -> {
                    statusLine("正在替换并重启…" + checksumNote + "（换装日志：~/.aha/update/update.log）");
                    onRequestExit.run();
                });
            } catch (UpdateException e) {
                dispatcher.dispatch(() -> resetAfterFailure(failure(e)));
            } catch (RuntimeException e) {
                // 只 catch UpdateException 的话，其它异常会让按钮永远灰着、进度条一直转
                // （2026-10-16 审查 P2-9）
                LOG.warn("换装流程异常", e);
                dispatcher.dispatch(() -> resetAfterFailure("失败：" + e));
            }
        });
    }

    /** 失败后把界面恢复成可重试（并如实说明）。 */
    private void resetAfterFailure(String message) {
        progress.setVisible(false);
        statusLine(message);
        busy = false;
        checkButton.setDisable(false);
        actionButton.setDisable(unsupportedReason != null);
    }

    /** 失败文案：分清「限流（稍后再试）」与其它失败，并给下一步。 */
    private String failure(UpdateException e) {
        if (e.rateLimited()) {
            return "检查失败：" + e.getMessage() + "（发布页接口有频率限制，过一会儿再试即可）";
        }
        if (UpdateException.CODE_ASSET_MISSING.equals(e.code())) {
            return "没有可用版本：" + e.getMessage();
        }
        if (UpdateException.CODE_PLATFORM_UNPUBLISHED.equals(e.code())) {
            return "本平台暂不支持应用内升级：" + e.getMessage();
        }
        if (UpdateException.CODE_HELPER_NOT_STARTED.equals(e.code())) {
            return "换装程序没有启动：" + e.getMessage();
        }
        return "失败：" + e.getMessage();
    }

    /** 空列表的三种原因（§3.2）：不能都写成「已是最新」。 */
    private String emptyReason(ReleaseChannel channel) {
        if (!kind.hasPlatform() || platform.hasPublishedPackages()) {
            return "「" + channel.label() + "」通道暂无可列出的版本：可能还没有带资产的发版（"
                    + SOURCE + "），或该形态在该通道不发版；Alpha 的版本也可能已被清理"
                    + "（快照包 7 天有效期）。";
        }
        return platform.unpublishedReason() + "；可手动到发布页取件";
    }

    private void selectChannelInUi(ReleaseChannel channel) {
        switch (channel) {
            case STABLE -> stableButton.setSelected(true);
            case ALPHA -> alphaButton.setSelected(true);
            default -> betaButton.setSelected(true);
        }
    }

    private ReleaseChannel selectedChannel() {
        if (stableButton.isSelected()) {
            return ReleaseChannel.STABLE;
        }
        if (alphaButton.isSelected()) {
            return ReleaseChannel.ALPHA;
        }
        return ReleaseChannel.BETA;
    }

    private void statusLine(String text) {
        statusLabel.setText("状态：" + text);
    }

    private static InstallLayout detectLayout() {
        try {
            return InstallLayout.detect();
        } catch (UpdateException e) {
            // 推不出安装根（少见）：按 JVM 便携包处理，界面照旧可用；真正的动作会被服务层挡下
            LOG.warn("推不出安装根，按 JVM 便携包处理：{}", e.getMessage());
            return InstallLayout.of(Path.of("."));
        }
    }

    /**
     * 列表行：一个候选版本 + 它在这台机器上的可用性。
     *
     * @param release           候选版本
     * @param asset             命中本平台本形态的资产；不可用时为 {@code null}
     * @param unavailableReason 不可用的原因（说明「这个版本为什么不能用」）
     * @param current           当前运行版本
     * @param latest            是否是本通道最新（列表首行）
     * @param newer             是否比当前新
     * @since 0.2.0
     */
    private record Row(ReleaseFilter.ChannelRelease release, AssetInfo asset,
                       String unavailableReason, AhaVersion current, boolean latest, boolean newer) {

        AhaVersion version() {
            return release.version();
        }

        boolean available() {
            return asset != null;
        }

        String assetName() {
            return asset == null ? "" : asset.name();
        }

        boolean isCurrent() {
            return release.version().equals(current);
        }

        Instant publishedAt() {
            return release.publishedAt();
        }

        /** 单行文本：标记 + 版本 + 相对关系 + 时间（§3.2 的四个问题里的三个）。 */
        @Override
        public String toString() {
            String mark = isCurrent() ? "◐" : (latest ? "●" : "○");
            String relative = isCurrent() ? "当前版本" : (newer ? "升级" : "降级");
            String time = publishedAt() == null ? "" : "（" + TIME_FORMAT.format(publishedAt()) + "）";
            String tail = available() ? "" : "   不可用：" + unavailableReason;
            return mark + " " + release.version().value() + "  " + relative + "  " + time + tail;
        }
    }

    /** 行渲染：不可用的版本灰显并给悬浮说明（§3.2「不能则灰掉并说明原因」）。 */
    private static final class RowCell extends ListCell<Row> {

        @Override
        protected void updateItem(Row row, boolean empty) {
            super.updateItem(row, empty);
            if (empty || row == null) {
                setText(null);
                setTooltip(null);
                setDisable(false);
                return;
            }
            setText(row.toString());
            setDisable(!row.available());
            setTooltip(row.available() ? null : new Tooltip(row.unavailableReason()));
        }
    }
}
