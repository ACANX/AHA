package com.acanx.module.aha.desktop;

import com.acanx.module.aha.common.AppVersion;
import com.acanx.module.aha.core.logging.LoggingSetup;
import com.acanx.module.aha.desktop.fx.FxBridge;
import com.acanx.module.aha.desktop.fx.FxDispatcher;
import com.acanx.module.aha.desktop.fx.PlatformFxDispatcher;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AHA 桌面端主类。
 *
 * <p>当前是 <strong>0.2 的第一步</strong>：只搭窗口骨架，并把「线程桥接契约」跑通
 * ——后台虚拟线程产生状态，经 {@link FxBridge} 合流后在 UI 线程更新标签。
 * 三栏布局、会话列表、对话流、工具卡片、授权弹窗、输入区等在 0.2 内逐步落地
 * （见 {@code GUIDesign.md}）。</p>
 *
 * <p>注意：本类<strong>不直接触碰 JavaFX 的线程 API</strong>（既不调
 * {@code Platform.runLater} 也不判断当前线程），只通过 {@link FxDispatcher}
 * 间接投递；该约束由 {@code FxThreadContractTest} 静态扫描主源码钉住。</p>
 *
 * @since 0.1.0
 */
public final class AhaDesktopApp extends Application {

    private static final Logger LOG = LoggerFactory.getLogger(AhaDesktopApp.class);

    /** 状态标签的 id，供测试定位（{@code #aha.status}）。 */
    static final String STATUS_ID = "aha.status";

    /** 状态标签的初始文案。 */
    static final String STATUS_INITIAL = "正在启动…";

    /** 窗口初始宽度。 */
    private static final int WIDTH = 900;

    /** 窗口初始高度。 */
    private static final int HEIGHT = 600;

    /** 内边距。 */
    private static final int PADDING = 24;

    @Override
    public void init() {
        // 日志配置逻辑已下移到 aha-core（D-10），CLI 与桌面端共用同一份实现。
        // 这里先用默认配置；读取 Aha.yaml 的完整配置随真实界面一起落地。
        LoggingSetup.apply(null);
        LOG.info("AHA Desktop 启动，版本 {}，JVM {}",
                AppVersion.version(), Runtime.version());
    }

    @Override
    public void start(Stage stage) {
        FxDispatcher dispatcher = new PlatformFxDispatcher();

        // 先建控件、再建桥接：渲染动作就是把值写进状态标签（在 UI 线程执行）
        Label status = new Label(STATUS_INITIAL);
        status.setId(STATUS_ID);
        FxBridge<String> bridge = new FxBridge<>(dispatcher, status::setText);

        Parent root = buildRoot(status);
        stage.setTitle(AppVersion.DISPLAY + " 桌面端（0.2 开发中）");
        stage.setScene(new Scene(root, WIDTH, HEIGHT));
        // 关窗联动：关闭桥接，之后到达的后台更新一律丢弃
        stage.setOnCloseRequest(event -> bridge.close());
        stage.show();

        startStatusProbe(bridge);
    }

    /**
     * 构建窗口根节点。
     *
     * @param status 状态标签（由桥接在 UI 线程更新）
     * @return 根节点
     */
    Parent buildRoot(Label status) {
        Label heading = new Label(AppVersion.DISPLAY + " 桌面端");
        heading.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        Label note = new Label("""
                当前为 0.2 第一步：窗口骨架 + 线程桥接契约。
                后续按 GUIDesign.md 落地：会话列表、对话流、工具卡片、授权弹窗、输入区。""");
        note.setWrapText(true);

        VBox center = new VBox(12, heading, status, note);
        center.setPadding(new Insets(PADDING));

        Label footer = new Label("系统 " + System.getProperty("os.name")
                + " / " + System.getProperty("os.arch")
                + " · Java " + Runtime.version());
        footer.setPadding(new Insets(8, PADDING, 12, PADDING));

        BorderPane root = new BorderPane(center);
        root.setBottom(footer);
        return root;
    }

    /**
     * 启动一条虚拟线程作为状态探针。
     *
     * <p>它演示并验证线程模型：后台线程<strong>不</strong>直接改界面，而是把值交给
     * {@link FxBridge}，由 {@link PlatformFxDispatcher} 投递到 UI 线程。</p>
     *
     * @param bridge 状态桥接
     */
    void startStatusProbe(FxBridge<String> bridge) {
        Thread.ofVirtual().name("aha-desktop-status-probe").start(() -> {
            LOG.debug("状态探针线程启动：{}", Thread.currentThread());
            bridge.submit("后台线程已就绪：" + Thread.currentThread());
        });
    }

    /**
     * 桌面端入口。
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        launch(args);
    }
}
