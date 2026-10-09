package com.acanx.module.aha.desktop;

import com.acanx.module.aha.common.AppVersion;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.tool.ToolPermission;
import com.acanx.module.aha.core.boot.AhaBootstrap;
import com.acanx.module.aha.core.config.ConfigLoader;
import com.acanx.module.aha.core.config.ModelConfig;
import com.acanx.module.aha.core.config.ModelConfigStore;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.config.SystemPromptLoader;
import com.acanx.module.aha.core.service.AgentService;
import com.acanx.module.aha.core.service.AgentServiceFactory;
import com.acanx.module.aha.desktop.chat.ChatController;
import com.acanx.module.aha.desktop.chat.DesktopToolApprover;
import com.acanx.module.aha.desktop.chat.SessionExport;
import com.acanx.module.aha.desktop.log.LogCapture;
import com.acanx.module.aha.desktop.view.ProviderDialog;
import com.acanx.module.aha.desktop.fx.FxBridge;
import com.acanx.module.aha.desktop.fx.FxDispatcher;
import com.acanx.module.aha.desktop.fx.PlatformFxDispatcher;
import com.acanx.module.aha.desktop.view.DesktopShell;
import com.acanx.module.aha.desktop.view.Palette;
import com.acanx.module.aha.desktop.view.SettingsDialog;
import com.acanx.module.aha.desktop.view.SystemTheme;
import com.acanx.module.aha.desktop.view.Theme;
import com.acanx.module.aha.desktop.view.DesktopSettings;
import com.acanx.module.aha.desktop.view.LogPanel;
import com.acanx.module.aha.desktop.view.LogoImage;
import com.acanx.module.aha.desktop.view.ShellLayout;
import javafx.application.Application;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
                AppVersion.buildVersion(), Runtime.version(),
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

        // 先定色表、再建界面：反过来不行——颜色是内联在样式串里的，界面建完再换主题
        // 只能靠逐个重刷，那条路径一旦漏了某个控件，就会出现「日志说生效亮色、界面还是暗的」
        // （真机上就是这么撞到的）。先定色的意思是：新节点天生是对的，重刷只是补充。
        DesktopSettings settings = DesktopSettings.load(DesktopSettings.defaultFile());
        Theme effective = Palette.setTheme(settings.theme());
        LOG.info("界面设置：主题 {}（生效 {}），字号 {}px，前景 {} / 底色 {}，文件 {}",
                settings.theme().label(), effective.label(), settings.fontSize(),
                Palette.FOREGROUND, Palette.BASE,
                settings.path() == null ? "不可用" : settings.path());

        // 骨架：菜单栏 + 三栏 + 底部状态栏（形态见 GUIDesign.md 第 2 节）
        DesktopShell shell = new DesktopShell(dispatcher, stage::close, this::configSummary);
        shell.setConfigSummary(configSummary());

        // Agent 服务：与 CLI 用同一个工厂、同一份配置；工具授权走桌面端弹窗
        DesktopToolApprover approver = new DesktopToolApprover(
                (toolName, permission, arguments, alreadyAllowed) -> askApproval(
                        stage, dispatcher, toolName, permission, arguments, alreadyAllowed));
        AgentService service = AgentServiceFactory.local(
                boot == null ? null : boot.config(), approver);
        shell.onRevokeApprovals(() -> {
            int revoked = approver.clearSessionAllowed();
            shell.appendNotice(revoked == 0
                    ? "本会话没有已放行的权限。"
                    : "已撤销本会话的 " + revoked + " 项授权，后续同类操作会重新询问。");
        });

        // 供应商配置来自 Model.yml（与 CLI 同一份），默认供应商的模型决定本轮会话用哪个模型
        ModelConfigStore modelStore =
                new ModelConfigStore(ConfigLoader.resolveModelPath(boot == null ? null : boot.config()));
        shell.providerDialog(new ProviderDialog(modelStore.path()));

        // 日志面板：先把内存 appender 挂上（越早越好，启动阶段的事件也想看到），
        // 再把面板交给界面。挂不上不影响使用，面板会显示「未接入日志系统」。
        if (!LogCapture.install()) {
            LOG.warn("日志面板未能接入日志系统：面板仍可打开，但没有实时数据");
        }
        shell.logPanel(new LogPanel(boot == null ? null : boot.loggingLevel()));

        // 记得改的文件：上面已经按设置定好色表，这里只把字号落到根节点上
        shell.fontSize(settings.fontSize());

        // 主题切换：/theme、视图菜单、设置面板三条路径都走这里
        shell.onThemeCycle(() -> {
            Theme next = Palette.current().next();
            Theme resolved = shell.applyTheme(next);
            settings.theme(next);
            settings.save();
            shell.appendNotice("主题已切换到「" + next.label() + "」"
                    + (next == Theme.SYSTEM ? "（生效：" + resolved.label() + "）" : "") + "。");
        });

        // 「跟随系统」时，用户在系统设置里切换深色 / 浅色皮肤要实时跟上（issue #44）。
        // 回调在界面线程触发；只有主题仍处于「跟随系统」时才应用，避免覆盖用户的手动选择。
        SystemTheme.onColorSchemeChanged(() -> {
            if (settings.theme() == Theme.SYSTEM) {
                Theme resolved = shell.applyTheme(Theme.SYSTEM);
                LOG.info("系统配色变化，跟随系统主题生效：{}", resolved.label());
            }
        });
        shell.onOpenSettings(() -> new SettingsDialog(
                settings,
                this::configSummary,
                () -> shell.openProviderDialogForSettings(),
                shell::applyTheme,
                shell::fontSize).show(stage));

        ChatController controller = new ChatController(shell, service,
                new SessionConfig(activeModel(modelStore), null, null),
                ChatController.VIRTUAL_THREADS, dispatcher::dispatch);
        shell.onSend(controller::send);
        shell.onCancel(controller::cancel);
        shell.onNewSession(() -> {
            controller.newSession();
            shell.clearConversation();
            shell.appendNotice("已新建会话（供应商 / 模型改变后需要新建会话才生效）。");
            refreshSessions(shell, controller);
        });

        // 会话列表：左栏展示 + 切换 + 重命名 + 删除 + 导出（GUIDesign 第 4.5 节）
        shell.onSessionSelected(sessionId -> {
            int shown = controller.openSession(sessionId);
            shell.appendNotice("已切换到会话 " + shortId(sessionId) + "，回放 " + shown + " 条消息。");
            refreshSessions(shell, controller);
        });
        shell.onSessionRename((sessionId, title) -> {
            controller.renameSession(sessionId, title);
            refreshSessions(shell, controller);
        });
        shell.onSessionDelete(sessionId -> {
            controller.deleteSession(sessionId);
            controller.newSession();
            shell.clearConversation();
            shell.appendNotice("已删除会话 " + shortId(sessionId) + " 及其消息。");
            refreshSessions(shell, controller);
        });
        shell.onSessionExport((sessionId, format) ->
                exportSession(stage, shell, controller, sessionId, format));
        shell.tools(service::listTools);

        // @ 文件引用要有个扫描起点：与 CLI 用同一套项目根探测（有 .git / pom.xml 的那一层）。
        // 只算一次：每敲一个字符都走一遍目录树是没必要的。
        Path projectRoot = SystemPromptLoader.findProjectRoot(
                Path.of(System.getProperty("user.dir", ".")));
        shell.projectRoot(() -> projectRoot);
        LOG.info("项目根：{}", projectRoot == null ? "未找到（@ 引用将没有候选）" : projectRoot);

        // 首屏就把已有会话列出来（进程重启后仍能看到之前的会话）
        refreshSessions(shell, controller);

        // 先建骨架、再建桥接：渲染动作就是把值写进底栏的状态标签（在 UI 线程执行）
        FxBridge<String> bridge = new FxBridge<>(dispatcher, shell.statusLabel()::setText);

        Parent root = shell.buildRoot();
        // 构建期用的是「当时」的色表：这里幂等地再刷一次，保证所有登记过的节点与当前色表一致。
        // 原生镜像里出现过「部分节点停在构建期主题」的混合状态——底色一套、文字另一套（issue #49），
        // 这一步把「构建期取值」这个不确定性直接抹掉。
        shell.applyTheme(settings.theme());
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
            LogCapture.uninstall();
        });
        stage.show();

        // 焦点给输入框：聊天应用启动后应当能直接打字
        shell.focusComposer();
        startStatusProbe(bridge);
    }

    /**
     * 刷新左栏会话列表。
     *
     * <p>列表刷新失败不该把界面搞崩（比如存储被占用）：记一条日志，界面保持上一次的内容。</p>
     *
     * @param shell      界面
     * @param controller 对话内核
     */
    private static void refreshSessions(DesktopShell shell, ChatController controller) {
        try {
            shell.refreshSessions(controller.sessions(), controller.sessionId());
        } catch (RuntimeException e) {
            LOG.warn("刷新会话列表失败：{}", e.getMessage());
        }
    }

    /** 会话 ID 前 8 位（提示里够用，且不必把整串 UUID 摆到对话流里）。 */
    private static String shortId(String sessionId) {
        if (sessionId == null) {
            return "";
        }
        return sessionId.length() <= 8 ? sessionId : sessionId.substring(0, 8);
    }

    /**
     * 导出会话：先在内存里生成文本，再让用户选保存位置。
     *
     * <p>顺序刻意如此——先生成，会话为空时直接提示，避免用户选完路径才发现没内容可导。</p>
     *
     * @param owner      父窗口（文件对话框用）
     * @param shell      界面（提示落在这里）
     * @param controller 对话内核
     * @param sessionId  会话 ID
     * @param format     {@code md} / {@code json}
     */
    private static void exportSession(Stage owner, DesktopShell shell, ChatController controller,
                                      String sessionId, String format) {
        boolean markdown = !"json".equalsIgnoreCase(format);
        String text;
        String title;
        try {
            text = controller.export(sessionId, markdown);
            title = controller.titleOf(sessionId);
        } catch (RuntimeException e) {
            LOG.warn("导出会话失败", e);
            shell.appendNotice("导出失败：" + e.getMessage());
            return;
        }
        if (text.isBlank()) {
            shell.appendNotice("该会话没有可导出的内容。");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle(markdown ? "导出会话（Markdown）" : "导出会话（JSON）");
        chooser.setInitialFileName(SessionExport.fileName(title, markdown ? "md" : "json"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                markdown ? "Markdown" : "JSON", markdown ? "*.md" : "*.json"));
        File target = chooser.showSaveDialog(owner);
        if (target == null) {
            shell.appendNotice("已取消导出。");
            return;
        }
        try {
            Files.writeString(target.toPath(), text, StandardCharsets.UTF_8);
            shell.appendNotice("已导出到 " + target.getAbsolutePath());
        } catch (IOException e) {
            LOG.warn("写入导出文件失败：{}", e.getMessage());
            shell.appendNotice("写入失败：" + e.getMessage());
        }
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
                                                            Map<String, Object> arguments,
                                                            java.util.Set<ToolPermission> alreadyAllowed) {
        AtomicReference<DesktopToolApprover.Decision> choice =
                new AtomicReference<>(DesktopToolApprover.Decision.DENY);
        CountDownLatch answered = new CountDownLatch(1);
        dispatcher.dispatch(() -> {
            try {
                // 工具线程在这里阻塞等待用户选择；弹窗本身在 UI 线程（见 DesktopDesign 第 6 节）
                choice.set(new com.acanx.module.aha.desktop.view.ApprovalDialog(
                        toolName, permission, arguments, alreadyAllowed).show(owner));
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
     * <p>用 {@code launch(AhaDesktopApp.class, args)} 而不是 {@code launch(args)}：
     * 后者靠栈帧推断调用类名、再用 {@code Class.forName} 反射加载——原生镜像下
     * 这是 closed-world 看不到的路径（issue #26）。显式传 {@code Class} 去掉这一跳，
     * 但它内部仍用 {@code getConstructor().newInstance()} 实例化应用类，
     * 因此 {@code reachability-metadata.json} 里的构造器注册仍不可少。</p>
     *
     * @param args 参数
     */
    public static void main(String[] args) {
        launch(AhaDesktopApp.class, args);
    }
}
