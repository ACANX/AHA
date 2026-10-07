package com.acanx.module.aha.cli;

import com.acanx.module.aha.cli.render.LogoArt;
import com.acanx.module.aha.cli.render.Renderer;
import com.acanx.module.aha.cli.render.StartupBanner;
import com.acanx.module.aha.cli.render.StartupLogo;
import com.acanx.module.aha.cli.render.StartupPixelLogo;
import com.acanx.module.aha.cli.render.Style;
import com.acanx.module.aha.cli.render.Renderers;
import com.acanx.module.aha.cli.render.TextWidth;
import com.acanx.module.aha.cli.session.AutoCompactor;
import com.acanx.module.aha.cli.session.SessionCommand;
import com.acanx.module.aha.cli.session.SessionCommandCompleter;
import com.acanx.module.aha.cli.session.SessionCommandHighlighter;
import com.acanx.module.aha.cli.session.SessionCommandRegistry;
import com.acanx.module.aha.cli.session.SessionContext;
import com.acanx.module.aha.cli.tty.AboveStream;
import com.acanx.module.aha.cli.tty.JLineStatusSurface;
import com.acanx.module.aha.cli.tty.OutputEncoding;
import com.acanx.module.aha.cli.tty.StatusSurface;
import com.acanx.module.aha.cli.tty.TerminalCapabilities;
import com.acanx.module.aha.cli.tty.TerminalHandover;
import com.acanx.module.aha.common.exception.Exceptions;
import com.acanx.module.aha.common.runtime.RuntimeDescriptor;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.SystemPromptLoader;
import com.acanx.module.aha.core.service.AgentService;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 交互式对话命令（JLine）。
 *
 * @since 0.1.0
 */
@Command(name = "chat", description = "启动交互式对话", mixinStandardHelpOptions = true)
public final class ChatCommand implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ChatCommand.class);

    /** 供应商 ID。 */
    @Option(names = "--provider", description = "供应商 ID")
    String provider;

    /** 模型名。 */
    @Option(names = "--model", description = "模型名")
    String model;

    /** 会话 ID。 */
    @Option(names = "--session", description = "复用已有会话 ID（身份设定沿用该会话）")
    String session;

    /** 显式系统提示词。 */
    @Option(names = "--system", description = "系统提示词文本（覆盖文件加载）")
    String systemText;

    /** 显式系统提示词文件。 */
    @Option(names = "--system-file", description = "系统提示词文件（覆盖约定文件）")
    Path systemFile;

    /** 是否自动授权高风险工具。 */
    @Option(names = {"-y", "--yes"},
            description = "自动授权 WRITE/EXECUTE/ADMIN 工具，不再逐次确认")
    boolean autoYes;

    /** 关闭 ANSI 颜色。 */
    @Option(names = "--no-color", description = "关闭 ANSI 颜色（也受 NO_COLOR 环境变量影响）")
    boolean noColor;

    /** 启动标志样式：auto / pixel / ascii / off。 */
    @Option(names = "--logo", paramLabel = "MODE",
            description = "启动标志：auto / pixel / ascii / off（默认 auto）")
    String logo;

    @Override
    public void run() {
        AgentService service = CliContext.service();
        // 先确保全局记忆文件存在，再解析身份：否则本轮解析不到它，
        // 启动信息与 /memory view 会各说一套（一个只报项目级、一个列出两份）。
        // 只动用户级目录，绝不碰项目目录。
        SystemPromptLoader.ensureUserIdentity(null);
        CliSession.Opened opened = CliSession.open(
                service, session, model, systemText, systemFile);
        String sessionId = opened.sessionId();

        // 启动横幅不在这里打印：右侧标志要与信息栏并排，就必须先知道终端宽度，
        // 而宽度只有 JLine 终端能给。挪到能力探测之后（见下方 banner 组装）。
        SessionCommandRegistry registry = new SessionCommandRegistry();

        // 退出时输出会话 ID 与续接命令，否则用户无法从上次位置继续。
        // 同时注册 shutdown hook，覆盖 Ctrl+C / kill 等强制退出。
        AtomicBoolean hintPrinted = new AtomicBoolean();
        Runnable exitHint = () -> {
            if (hintPrinted.compareAndSet(false, true)) {
                printExitHint(sessionId);
            }
        };
        Thread hook = new Thread(exitHint, "aha-exit-hint");
        Runtime.getRuntime().addShutdownHook(hook);

        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {
            // JLine 是终端信息的更权威来源：它能识别 dumb 终端与实际列数。
            // 探测的是能力而非平台，因此不必为 CMD / PowerShell / Windows Terminal /
            // WSL / Linux 或第三方终端（Tabby、Xshell 等）分别适配。
            TerminalCapabilities capabilities = TerminalCapabilities.detect(noColor)
                    .withTty(!"dumb".equals(terminal.getType()))
                    .withWidth(terminal.getWidth());
            LOG.info("终端能力: tty={} width={} color={} {}", capabilities.tty(),
                    capabilities.width(), capabilities.colored(), capabilities.description());

            // 控制台编码探测：决定提示符符号是否降级，以及是否需要提醒切换编码。
            // Windows 控制台按代码页解释字节，代码页之外的字符会变成 '?'。
            OutputEncoding encoding = OutputEncoding.detect();
            LOG.info("输出编码: {} cjk={} symbols={}", encoding.charsetName(),
                    encoding.cjk(), encoding.symbols());
            if (capabilities.tty() && encoding.recommendUtf8()) {
                // 中文无法表示时，提示必须用纯 ASCII——否则提示本身也读不了
                System.out.println();
                System.out.println("[warn] Console output encoding " + encoding.charsetName()
                        + " cannot display AHA's Chinese output.");
                System.out.println("       Switch the console to UTF-8, then restart:");
                System.out.println("         CMD:        chcp 65001");
                System.out.println("         PowerShell: [Console]::OutputEncoding = [Text.Encoding]::UTF8");
            }
            // 启动横幅：分两块输出。
            //  · 顶栏（标题 + 供应商/模型/端点/工作目录）与右侧 ASCII 标志并排——
            //    这几行是「一眼要看清」的，且都不长；
            //  · 其余细节（项目根、身份来源与加载顺序等）在标志下方整宽打印：
            //    它们带长绝对路径，实测最宽 78 列，会把标志整个挤出 80 列屏幕。
            // 并排必须先知道终端宽度，而宽度只有 JLine 终端能给，所以只能放在能力探测之后。
            Style bannerStyle = Style.of(capabilities.colored()
                    ? capabilities.depth() : TerminalCapabilities.ColorDepth.NONE);
            StartupBanner.compose(mastheadRows(service), capabilities.width(),
                    startupLogos(encoding.symbols(), capabilities.colored(), bannerStyle))
                    .forEach(System.out::println);
            detailRows(sessionId, opened).forEach(System.out::println);
            System.out.println();
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    // 会话内命令补全与实时着色：Tab 出候选菜单（带用法说明），
                    // 输入过程中命令名直接染成绿（已支持）或红（未知）——
                    // 此前只能盲敲，按下回车才知道命令对不对
                    .completer(new SessionCommandCompleter(registry))
                    .highlighter(new SessionCommandHighlighter(registry, capabilities.colored()))
                    // 允许多行粘贴：终端支持 bracketed paste 时，粘贴内容作为整体插入缓冲区，
                    // 换行不会立即提交（否则多行文本会被拆成多条消息发给模型）
                    .option(LineReader.Option.BRACKETED_PASTE, true)
                    // 菜单式补全：Tab 列出全部候选并允许循环选择
                    .option(LineReader.Option.AUTO_MENU_LIST, true)
                    .option(LineReader.Option.LIST_PACKED, true)
                    // 容错匹配：/cmpct 也能匹配到 /compact
                    .option(LineReader.Option.COMPLETE_MATCHER_TYPO, true)
                    .build();

            // 主线程常驻 readLine，回合跑在虚拟线程上——这是「流式期间输入可用」的正解：
            //   · 输入始终可用：主线程一直在读，敲下的内容排队，回合结束后依次处理
            //   · 输出经 printAbove 打到输入行上方，输入行始终留在底部
            //   · Ctrl+C 由 JLine 在主线程抛出，能可靠地取消当前回合
            //
            // 反过来（把 readLine 放到后台线程）实测会让 JLine 的 SIGINT 处理失效：
            // Ctrl+C 按默认行为直接结束 JVM，整个会话被杀。
            // 输入行用上下分隔线框住（像 pi），并去掉 `> ` 标记：线本身已经说明这里可以输入。
            // 上分隔线来自提示符；下分隔线来自状态行的边框——状态行因此不能真被“隐藏”，
            // 回合结束时它会换成空闲文案（见 StatusLine#idle）。
            // 上下两条线同色（高亮紫）：上边线来自提示符，下边线由状态行提供
            // 非 TTY（管道 / CI）不着色：转义序列会混进被重定向的文件里
            Style frameStyle = Style.of(capabilities.colored()
                    ? capabilities.depth() : TerminalCapabilities.ColorDepth.NONE);
            String prompt = "\n" + rule(encoding.symbols(), capabilities.width(), frameStyle) + "\n";
            PrintStream out = capabilities.tty()
                    ? new PrintStream(new AboveStream(reader::printAbove, encoding.charset()), false,
                            encoding.charset())
                    : System.out;
            // 状态行：终端底部常驻一行，回合期间持续刷新（转圈 / 耗时 / 用量）。
            // 它靠滚动区域实现，终端不支持时自动退化为不显示；
            // 非 TTY（管道 / CI）没有可保留的区域，直接用空实现。
            StatusSurface statusSurface = capabilities.tty()
                    ? JLineStatusSurface.create(terminal, capabilities.colored())
                    : StatusSurface.NONE;
            SessionContext sessionContext = new SessionContext(
                    sessionId, service, CliContext.config(), out,
                    describePromptSource(opened), opened.promptChars(),
                    handover(terminal, statusSurface));
            // 回合输出经 printAbove 上抛，状态行走底部常驻区，两者互不干扰
            Renderer renderer = Renderers.of(capabilities, encoding, statusModel(service), out,
                    statusSurface);

            // 工具授权发生在回合线程上，而 reader 由主线程持有，
            // 因此授权问答走队列：主线程把接下来的输入递给它
            BlockingQueue<String> answers = new LinkedBlockingQueue<>();
            AtomicBoolean awaitingAnswer = new AtomicBoolean();
            CliContext.service(autoYes
                    ? (toolName, permission, arguments) -> true
                    : new ConsoleToolApprover(question -> askForAnswer(out, answers, awaitingAnswer,
                            question)));

            // 单线程执行器：回合串行执行，期间的输入排队
            ExecutorService turns = Executors.newSingleThreadExecutor(
                    Thread.ofVirtual().name("aha-turn-", 0).factory());
            AtomicReference<CancellationToken> currentTurn = new AtomicReference<>();
            try {
                while (true) {
                    String line;
                    try {
                        line = reader.readLine(prompt);
                    } catch (UserInterruptException e) {
                        // 正在生成 → 取消本轮；空闲 → JLine 已清空输入行，继续读下一条
                        // （此前空闲时 Ctrl+C 会直接退出整个会话，与 shell 惯例不符）
                        CancellationToken token = currentTurn.get();
                        if (token != null) {
                            token.cancel();
                        }
                        continue;
                    } catch (EndOfFileException e) {
                        break;
                    } catch (RuntimeException e) {
                        // 读取失败（终端能力限制、粘贴内容含非常规控制字符等）不应终止整轮对话。
                        // JLine 在不同终端下抛出的异常类型不可枚举，因此宽幅兜底。
                        LOG.warn("读取输入失败: {}", e.getMessage());
                        say(out, "[warn] 读取输入失败，已忽略本次输入: " + Exceptions.message(e));
                        say(out, "       若因粘贴多行文本导致，可改用文件传参或逐行输入。");
                        continue;
                    }
                    if (line == null) {
                        break;
                    }
                    // 工具授权期间，敲下的是「回答」而不是新消息
                    if (awaitingAnswer.get()) {
                        answers.put(line);
                        continue;
                    }
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    // 仅当整行就是 exit/quit（单行、无内嵌换行）时才退出。
                    // 否则粘贴一段包含 exit 的文本会意外结束对话。
                    if (!trimmed.contains("\n") && isExitWord(trimmed)) {
                        break;
                    }
                    // /exit 不产出任何输出，直接在主线程处理，不必绕回合线程
                    if ("/exit".equalsIgnoreCase(trimmed)) {
                        break;
                    }
                    String input = trimmed;
                    turns.submit(() -> runTurn(input, sessionContext, registry, renderer,
                            service, sessionId, currentTurn, out));
                }
            } finally {
                turns.shutdownNow();
                statusSurface.close();
            }
        } catch (Exception e) {
            System.out.println("[error] " + Exceptions.message(e));
        } finally {
            service.closeSession(sessionId);
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException ignored) {
                // JVM 已在关闭中，hook 会自行执行
            }
            exitHint.run();
        }
    }

    /**
     * 执行一个回合：会话内命令，或一次流式推理。
     *
     * <p>在主线程之外的虚拟线程上运行，输出经 {@code printAbove} 上抛，
     * 因此回合进行中主线程仍可读入下一行。</p>
     *
     * @param input        用户输入（已 trim）
     * @param sessionContext 会话上下文
     * @param registry     会话内命令注册表
     * @param renderer     渲染器
     * @param service      Agent 服务
     * @param sessionId    会话 ID
     * @param currentTurn  当前回合的取消令牌（供主线程 Ctrl+C 使用）
     * @param out          输出流
     */
    private static void runTurn(String input, SessionContext sessionContext,
                                SessionCommandRegistry registry, Renderer renderer,
                                AgentService service, String sessionId,
                                AtomicReference<CancellationToken> currentTurn, PrintStream out) {
        CancellationToken token = new CancellationToken();
        currentTurn.set(token);
        try {
            Optional<SessionCommand.Outcome> outcome = registry.dispatch(sessionContext, input);
            if (outcome.isPresent()) {
                return;
            }
            renderer.beginTurn();
            service.streamChat(sessionId, input, renderer::onEvent, token);
            renderer.endTurn();
            if (token.isCancelled()) {
                // 被打断的说明由回合线程输出：此刻主线程正回到 readLine，
                // 只有这里调 printAbove 才落在正确的位置
                out.println("[已中断]");
            }
            // 放在每轮回答之后：先让用户看到本轮结果，再来处理上下文
            AutoCompactor.runIfNeeded(sessionContext, registry);
        } catch (RuntimeException e) {
            // 未预期的失败要连堆栈一起打出来：只给一句摘要，排查时等于没线索
            LOG.error("回合执行失败 session={}", sessionId, e);
            out.println("[error] " + Exceptions.message(e));
            out.println(stackTrace(e));
        } finally {
            // 收尾：流式正文最后一行往往没有换行符，不 flush 就一直压在缓冲里
            out.flush();
            currentTurn.set(null);
        }
    }

    /**
     * 工具授权问答：输出问题后，等主线程把下一行输入递过来。
     *
     * <p>回合线程不能去读 {@code LineReader}（主线程正占着），否则两者争用同一把读锁。</p>
     *
     * @param out            输出流
     * @param answers        主线程递入的输入队列
     * @param awaitingAnswer 标记：主线程据此把下一行当作回答而不是新消息
     * @param question       问题文本
     * @return 回答；{@code null} 表示未取到（应视为拒绝）
     */
    private static String askForAnswer(PrintStream out, BlockingQueue<String> answers,
                                       AtomicBoolean awaitingAnswer, String question) {
        out.println(question);
        out.flush();
        awaitingAnswer.set(true);
        try {
            // 超时只是防止异常情况下永久挂住；正常等待期间用户可以随时输入
            return answers.poll(30, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            awaitingAnswer.set(false);
        }
    }

    /**
     * 异常堆栈文本。
     *
     * @param error 异常
     * @return 堆栈文本
     */
    private static String stackTrace(Throwable error) {
        java.io.StringWriter writer = new java.io.StringWriter();
        error.printStackTrace(new java.io.PrintWriter(writer));
        return writer.toString();
    }

    /**
     * 生成输入行的分隔线。
     *
     * <p>端子宽度取 {@code width - 1}：满宽会触发终端自动换行，把分隔线挤成两行。</p>
     *
     * @param unicode 编码是否支持制表符
     * @param width   终端宽度；{@code <= 0} 时用固定长度
     * @param style   样式（上边线与下边线必须同色）
     * @return 分隔线
     */
    private static String rule(boolean unicode, int width, Style style) {
        int cells = width > 0 ? width - 1 : 40;
        return style.frame((unicode ? "─" : "-").repeat(Math.max(1, cells)));
    }

    /**
     * 是否为退出词。
     *
     * <p>仅接受单行且无内嵌换行的输入，否则粘贴一段包含 {@code exit} 的文本会意外结束对话。</p>
     *
     * @param trimmed 已 trim 的输入
     * @return 是退出词时为 {@code true}
     */
    private static boolean isExitWord(String trimmed) {
        return "exit".equalsIgnoreCase(trimmed) || "quit".equalsIgnoreCase(trimmed);
    }

    /**
     * 尽力输出一行。
     *
     * @param out     输出流
     * @param message 文本
     */
    private static void say(PrintStream out, String message) {
        try {
            out.println(message);
            out.flush();
        } catch (RuntimeException e) {
            LOG.debug("输出失败: {}", e.getMessage());
        }
    }

    /**
     * 构造终端让位器：让出前恢复终端属性，完成后重新进入 raw mode。
     *
     * <p>JLine 处于 raw mode 时外部编辑器无法正常读写终端。终端不支持暂停
     * （如 dumb 终端）时直接执行，不再强行折腾属性。</p>
     *
     * <p>同时隐藏状态行：它靠滚动区域占着底部一行，不藏起来会把编辑器的画面切碎。</p>
     *
     * @param terminal JLine 终端
     * @param status   状态行载体
     * @return 让位器
     */
    private static TerminalHandover handover(Terminal terminal, StatusSurface status) {
        if (!terminal.canPauseResume()) {
            return TerminalHandover.DIRECT;
        }
        return action -> {
            terminal.flush();
            if (status != null) {
                status.hide();
            }
            terminal.pause();
            try {
                return action.get();
            } finally {
                terminal.resume();
                terminal.flush();
            }
        };
    }

    /**
     * 状态行展示的模型名：优先命令行 {@code --model}，否则取运行时默认值。
     *
     * @param service Agent 服务
     * @return 模型名，可能为 {@code null}
     */
    private String statusModel(AgentService service) {
        if (model != null && !model.isBlank()) {
            return model;
        }
        return service.runtime().attributes().get("Model");
    }

    /**
     * 打印退出提示：会话 ID 与续接命令。
     *
     * <p>会话虽然已持久化，但若不在退出时告知 ID，用户下次就无从续接。</p>
     *
     * @param sessionId 会话 ID
     */
    private void printExitHint(String sessionId) {
        System.out.println();
        System.out.println("会话已保存：" + sessionId);
        System.out.println("续接本次会话：aha chat --session " + sessionId);
    }

    /**
     * 按 {@code --logo} 与终端能力组装候选标志（前者优先）。
     *
     * <p>像素风更接近 {@code Logo.svg} 的观感，但依赖 {@code \u2580} 与 256 色；控制台不支持符号时
     * 不赌运气，直接回退线框图——启动横幅烂成乱码，比朴素更糟。</p>
     *
     * @param symbols 控制台能否显示符号
     * @param colored 是否着色
     * @param style   样式
     * @return 候选标志，按优先级排列
     */
    private List<LogoArt> startupLogos(boolean symbols, boolean colored, Style style) {
        String mode = logo == null || logo.isBlank()
                ? "auto" : logo.trim().toLowerCase(Locale.ROOT);
        List<LogoArt> arts = new ArrayList<>();
        // 像素风靠颜色成形（透明处走终端默认色），因此不着色时不给它机会；
        // 48x48 放不下时退到 24x24（由同一份网格降采样，形状不走样）
        if (symbols && colored && ("auto".equals(mode) || "pixel".equals(mode))) {
            arts.add(StartupPixelLogo.art());
            arts.add(StartupPixelLogo.smallArt());
        }
        if (!"pixel".equals(mode) && !"off".equals(mode)) {
            arts.add(StartupLogo.art(StartupLogo.Size.FULL, style));
            arts.add(StartupLogo.art(StartupLogo.Size.COMPACT, style));
        }
        return arts;
    }

    /**
     * 组装顶栏行（与右侧 ASCII 标志并排）。
     *
     * <p>这几行刻意只放「一眼要看清」且不会很长的字段：它们决定标志能否在 80 列终端里
     * 站得住。带长绝对路径的细节全部放到 {@link #detailRows}。</p>
     *
     * @param service Agent 服务
     * @return 顶栏行（未着色；空字段已跳过）
     */
    private static List<String> mastheadRows(AgentService service) {
        List<String> rows = new ArrayList<>();
        rows.add("AHA 交互式对话（/help 查看命令，exit / quit 退出）");
        RuntimeDescriptor runtime = service.runtime();
        Map<String, String> attributes = runtime.attributes();
        field(rows, "供应商", attributes.get("Provider"));
        field(rows, "模型", attributes.get("Model"));
        field(rows, "端点", attributes.get("Endpoint"));
        field(rows, "工作目录", runtime.workingDir());
        return rows;
    }

    /**
     * 组装细节行（在标志下方整宽打印）。
     *
     * <p>会话开始前明确告知“连的是哪个供应商/模型/端点”，避免回答不符合预期时
     * 只能靠“问模型自己是什么模型”来判断（模型自述并不可靠）。</p>
     *
     * @param sessionId 会话 ID
     * @param opened    会话信息
     * @return 细节行（未着色；空字段已跳过）
     */
    private List<String> detailRows(String sessionId, CliSession.Opened opened) {
        List<String> rows = new ArrayList<>();
        Path projectRoot = SystemPromptLoader.findProjectRoot(
                Path.of(System.getProperty("user.dir", ".")));
        if (projectRoot != null) {
            field(rows, "项目根", projectRoot.toString());
        }
        Path globalHome = SystemPromptLoader.resolveUserHome();
        if (globalHome != null) {
            Path identity = SystemPromptLoader.ensureUserIdentity(null);
            field(rows, "全局记忆", identity == null ? globalHome.toString() : identity.toString());
        }
        field(rows, "身份来源", describePromptSource(opened));
        identityOrderRows(rows, opened);
        field(rows, "会话", sessionId);
        return rows;
    }

    /**
     * 追加身份文件的加载顺序行。
     *
     * <p>身份是「多层级叠加」的，顺序直接决定谁更具体、谁覆盖谁。不列出来，用户就无从判断
     * 自己刚写的那份到底有没有生效——所以刻意做成每次启动都可见，而不是埋在日志里。
     * 桌面端应同样提供这块信息。</p>
     *
     * @param rows   行集合
     * @param opened 会话信息
     */
    private void identityOrderRows(List<String> rows, CliSession.Opened opened) {
        List<Path> files = SystemPromptLoader.filePaths(opened.promptSource());
        if (files.isEmpty()) {
            String source = opened.promptSource();
            String what = source == null || "builtin".equals(source)
                    ? "内置默认身份"
                    : "Aha.Agent.SystemPrompt（配置内联）";
            rows.add(StartupBanner.field("身份顺序", what + "（未发现任何身份文件）"));
            return;
        }
        rows.add(StartupBanner.field("身份顺序", "后加载者优先级更高；同一层级按候选名顺序"));
        for (int i = 0; i < files.size(); i++) {
            Path file = files.get(i);
            // 层级标签按显示宽度补位（3 个汉字 = 6 列），否则路径列会错开
            rows.add(String.format("    %d. %s %s（%d 字符）", i + 1,
                    TextWidth.pad(SystemPromptLoader.isUserLevel(file) ? "用户级" : "项目级", 6),
                    file, charCount(file)));
        }
        // 续行缩进 = 2 + 标签宽 8 + 1 个空格，正好落到取值列
        rows.add("            Aha.Agent.SystemPrompt（配置内联）仅在无身份文件时兜底，本次未使用");
        unlistedCompatRows(rows, opened);
    }

    /**
     * 追加「存在但未列入候选」的兼容身份文件提示。
     *
     * <p>静默忽略是最糟的处理方式：用户把 {@code CLAUDE.md} 放进项目、发现没生效，
     * 却没有任何线索。这里主动指出来，并给出把它加进候选列表的方法。</p>
     *
     * @param rows   行集合
     * @param opened 会话信息
     */
    private static void unlistedCompatRows(List<String> rows, CliSession.Opened opened) {
        AhaConfig config = CliContext.config();
        List<String> names = config == null || config.agent() == null
                ? null : config.agent().promptFiles();
        // 已加载的不能再报成「未加载」——探测必须用实际配置的候选列表，并排除已加载的路径
        List<Path> loaded = SystemPromptLoader.filePaths(opened.promptSource());
        List<Path> unlisted = SystemPromptLoader
                .unlistedCompatFiles(Path.of(System.getProperty("user.dir", ".")), names)
                .stream().filter(path -> !loaded.contains(path)).toList();
        if (unlisted.isEmpty()) {
            return;
        }
        for (Path file : unlisted) {
            rows.add(StartupBanner.field("提示",
                    "发现 " + file + "，但不在 Agent.PromptFiles 中，本次未加载"));
        }
        rows.add("           加入候选：Aha.yaml 的 Aha.Agent.PromptFiles 追加该文件名");
    }

    /**
     * 文件字符数（读不到时报 0，不影响启动信息）。
     *
     * @param file 文件
     * @return 字符数
     */
    private static long charCount(Path file) {
        try {
            return java.nio.file.Files.readString(file).length();
        } catch (java.io.IOException | RuntimeException e) {
            return 0;
        }
    }

    /**
     * 提示词来源描述。
     *
     * @param opened 会话信息
     * @return 描述文本
     */
    private String describePromptSource(CliSession.Opened opened) {
        String source = opened.promptSource();
        if (source == null) {
            return null;
        }
        return switch (source) {
            case "builtin" -> "内置默认身份";
            case "existing-session" -> "沿用已有会话";
            case "--system" -> "命令行 --system";
            default -> {
                if (com.acanx.module.aha.core.config.SystemPromptLoader.isMerged(source)) {
                    yield "用户级 + 项目级（叠加，" + opened.promptChars() + " 字符）";
                }
                yield source.startsWith("file:")
                        ? source.substring("file:".length()) + "（" + opened.promptChars() + " 字符）"
                        : source;
            }
        };
    }

    /**
     * 追加一个字段行（空值整行跳过）。
     *
     * <p>补位必须按显示宽度：{@code printf("%-8s")} 数的是字符数，中文标签
     * 「供应商」（6 列）与「工作目录」（8 列）会被补成不同宽度，取值列于是错开。</p>
     *
     * @param rows  行集合
     * @param label 标签
     * @param value 取值
     */
    private static void field(List<String> rows, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        rows.add(StartupBanner.field(label, value));
    }

    /**
     * 从当前目录向上查找项目根。
     *
     * @return 项目根路径；未找到时返回 {@code null}
     */
    static Path findProjectRoot() {
        return SystemPromptLoader.findProjectRoot(
                Path.of(System.getProperty("user.dir", ".")));
    }
}
