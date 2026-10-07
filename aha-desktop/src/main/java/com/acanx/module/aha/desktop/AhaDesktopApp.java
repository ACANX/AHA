package com.acanx.module.aha.desktop;

import com.acanx.module.aha.common.AppVersion;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.tool.ToolPermission;
import com.acanx.module.aha.core.boot.AhaBootstrap;
import com.acanx.module.aha.core.config.ConfigLoader;
import com.acanx.module.aha.core.config.ModelConfig;
import com.acanx.module.aha.core.config.ModelConfigStore;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.service.AgentService;
import com.acanx.module.aha.core.service.AgentServiceFactory;
import com.acanx.module.aha.desktop.chat.ChatController;
import com.acanx.module.aha.desktop.chat.DesktopToolApprover;
import com.acanx.module.aha.desktop.view.ProviderDialog;
import com.acanx.module.aha.desktop.fx.FxBridge;
import com.acanx.module.aha.desktop.fx.FxDispatcher;
import com.acanx.module.aha.desktop.fx.PlatformFxDispatcher;
import com.acanx.module.aha.desktop.view.DesktopShell;
import com.acanx.module.aha.desktop.view.LogoImage;
import com.acanx.module.aha.desktop.view.ShellLayout;
import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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

    /** 引导结果，供界面显示配置来源与生效日志级别（{@code D-11}）。 */
    private AhaBootstrap.Result boot;

    /** 窗口最多占屏幕可用区域的这个比例，留出任务栏与边框的余量。 */
    private static final double SCREEN_USAGE = 0.9;

    @Override
    public void init() {
        bootConfig(Path.of(ConfigLoader.CONFIG_FILE_NAME));
    }

    /**
     * 读配置 → 装配日志 → 装密钥库回退源，与 CLI 共用 {@link AhaBootstrap}。
     *
     * <p>配置路径作参数而非写死，是为了让测试能用临时目录里的 {@code Aha.yaml}
     * 确定性地验证「配置真的生效」，不必依赖进程的工作目录。</p>
     *
     * @param configFile 主配置路径
     * @return 引导结果
     */
    AhaBootstrap.Result bootConfig(Path configFile) {
        AhaBootstrap.Result result = AhaBootstrap.boot(configFile);
        boot = result;
        LOG.info("AHA Desktop 启动：版本 {}，JVM {}，配置 {}，日志级别 {}",
                AppVersion.version(), Runtime.version(),
                result.fromFile() ? result.configFile().toString() : "内置默认",
                result.loggingLevel());
        result.warnings().forEach(LOG::warn);
        return result;
    }

    /**
     * 配置摘要（窗口显示）。
     *
     * <p>同时是 {@code D-11} 的真机自证：改项目 {@code Aha.yaml} 的
     * {@code Aha.Logging.Level} 后，这一行必须跟着变。</p>
     *
     * @return 形如「配置：/path/Aha.yaml · 日志级别：DEBUG」
     */
    String configSummary() {
        if (boot == null) {
            return "配置：尚未加载";
        }
        return "配置：" + (boot.fromFile() ? boot.configFile().toString() : "内置默认")
                + " · 日志级别：" + boot.loggingLevel();
    }

    @Override
    public void start(Stage stage) {
        FxDispatcher dispatcher = new PlatformFxDispatcher();

        // 骨架：菜单栏 + 三栏 + 底部状态栏（形态见 GUIDesign.md 第 2 节）
        DesktopShell shell = new DesktopShell(dispatcher, stage::close, this::configSummary);
        shell.setConfigSummary(configSummary());

        // Agent 服务：与 CLI 用同一个工厂、同一份配置；工具授权走桌面端弹窗
        AgentService service = AgentServiceFactory.local(
                boot == null ? null : boot.config(),
                new DesktopToolApprover((toolName, permission, arguments) ->
                        askApproval(stage, dispatcher, toolName, permission, arguments)));

        // 供应商配置来自 Model.yml（与 CLI 同一份），默认供应商的模型决定本轮会话用哪个模型
        ModelConfigStore modelStore =
                new ModelConfigStore(ConfigLoader.resolveModelPath(boot == null ? null : boot.config()));
        shell.providerDialog(new ProviderDialog(modelStore.path()));

        ChatController controller = new ChatController(shell, service,
                new SessionConfig(activeModel(modelStore), null, null),
                ChatController.VIRTUAL_THREADS, dispatcher::dispatch);
        shell.onSend(controller::send);
        shell.onCancel(controller::cancel);
        shell.onNewSession(() -> {
            controller.newSession();
            shell.appendNotice("已新建会话（供应商 / 模型改变后需要新建会话才生效）。");
        });
        shell.tools(service::listTools);

        // 先建骨架、再建桥接：渲染动作就是把值写进底栏的状态标签（在 UI 线程执行）
        FxBridge<String> bridge = new FxBridge<>(dispatcher, shell.statusLabel()::setText);

        Parent root = shell.buildRoot();
        stage.setTitle(AppVersion.DISPLAY + " 桌面端（0.2 开发中）");

        // 窗口 / 任务栏图标：Logo.svg 的位图版本（见 LogoImage 的说明）
        Image logo = LogoImage.load();
        if (logo != null) {
            stage.getIcons().add(logo);
        } else {
            LOG.warn("标志资源 logo.png 缺失，窗口将使用系统默认图标");
        }

        // 尺寸按**屏幕可用区域**算，而不是写死 1280×800：
        // 高 DPI 缩放下写死的 800 逻辑像素会变成 1000+ 物理像素，把底栏与输入框顶出屏幕
        // （第一次在 Windows 上启动就踩到了：只能看到消息区，输入框不见了）。
        // Screen 报的是**物理**像素，而场景尺寸是**逻辑**像素：必须除以缩放比，
        // 否则 800 逻辑像素在 150% 缩放下变成 1200 物理像素，把底栏与输入区顶出屏幕
        // （第一次在 Windows 上启动就撞到了，日志里能看到 1707x1067 与 1280x800 并存）。
        Screen screen = Screen.getPrimary();
        Rectangle2D bounds = screen.getVisualBounds();
        double scaleX = screen.getOutputScaleX() > 0 ? screen.getOutputScaleX() : 1;
        double scaleY = screen.getOutputScaleY() > 0 ? screen.getOutputScaleY() : 1;
        double availWidth = bounds.getWidth() / scaleX;
        double availHeight = bounds.getHeight() / scaleY;
        double width = Math.min(ShellLayout.WINDOW_WIDTH, availWidth * SCREEN_USAGE);
        double height = Math.min(ShellLayout.WINDOW_HEIGHT, availHeight * SCREEN_USAGE);
        stage.setScene(new Scene(root, width, height));
        stage.setX(bounds.getMinX() / scaleX + (availWidth - width) / 2);
        stage.setY(bounds.getMinY() / scaleY + (availHeight - height) / 2);
        // 尺寸下限：否则用户把窗口拖小后，输入区与底栏会被挤没
        stage.setMinWidth(ShellLayout.MIN_WIDTH);
        stage.setMinHeight(ShellLayout.MIN_HEIGHT);
        // 写进日志：出问题时不必猜，直接看日志
        LOG.info("窗口：{}×{}（逻辑），屏幕可用 {}×{}（物理），缩放 {}x/{}x",
                (int) width, (int) height, (int) bounds.getWidth(), (int) bounds.getHeight(),
                scaleX, scaleY);
        // 关窗联动：关闭桥接与对话内核，之后到达的后台更新一律丢弃
        stage.setOnCloseRequest(event -> {
            shell.closeBridges();
            controller.close();
            service.shutdown();
            bridge.close();
        });
        stage.show();

        // 焦点给输入框：聊天应用启动后应当能直接打字
        shell.focusComposer();
        startStatusProbe(bridge);
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
     * 取当前默认供应商的模型名。
     *
     * <p>取不到就返回 {@code null}，由 core 按自己的兜底规则解析——
     * 桌面端不在这里重复实现一套模型解析。</p>
     *
     * @param store 模型配置存取器
     * @return 模型名，可为 {@code null}
     */
    private static String activeModel(ModelConfigStore store) {
        try {
            if (!store.exists()) {
                return null;
            }
            ModelConfig config = store.load();
            String id = config.defaultProvider();
            if (id == null || id.isBlank()) {
                return null;
            }
            ProviderConfig provider = config.providersOrEmpty().get(id);
            return provider == null ? null : provider.model();
        } catch (RuntimeException e) {
            LOG.warn("读取 {} 失败：{}", store.path(), e.getMessage());
            return null;
        }
    }

    /**
     * 询问用户是否允许一次工具调用（在 UI 线程弹窗，阻塞工具线程等待选择）。
     *
     * <p>三种选择与 CLI 一致：本次允许 / 本会话内始终允许该权限 / 拒绝。</p>
     *
     * @param owner      父窗口
     * @param dispatcher UI 线程投递器
     * @param toolName   工具名
     * @param permission 所需权限
     * @param arguments  调用参数
     * @return 用户选择
     */
    private static DesktopToolApprover.Decision askApproval(Stage owner, FxDispatcher dispatcher,
                                                            String toolName, ToolPermission permission,
                                                            Map<String, Object> arguments) {
        AtomicReference<DesktopToolApprover.Decision> choice =
                new AtomicReference<>(DesktopToolApprover.Decision.DENY);
        CountDownLatch answered = new CountDownLatch(1);
        dispatcher.dispatch(() -> {
            try {
                ButtonType once = new ButtonType("本次允许", ButtonBar.ButtonData.YES);
                ButtonType always = new ButtonType("本会话始终允许", ButtonBar.ButtonData.APPLY);
                ButtonType deny = new ButtonType("拒绝", ButtonBar.ButtonData.NO);
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                        String.valueOf(arguments == null ? "（无参数）" : arguments), once, always, deny);
                alert.initOwner(owner);
                alert.setTitle("工具授权");
                alert.setHeaderText(permission + "  " + toolName);
                alert.showAndWait().ifPresent(picked -> choice.set(picked == once
                        ? DesktopToolApprover.Decision.ALLOW_ONCE
                        : picked == always
                        ? DesktopToolApprover.Decision.ALLOW_SESSION
                        : DesktopToolApprover.Decision.DENY));
            } finally {
                answered.countDown();
            }
        });
        try {
            if (!answered.await(5, TimeUnit.MINUTES)) {
                LOG.warn("工具授权等待超时，按拒绝处理：{}", toolName);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return choice.get();
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
