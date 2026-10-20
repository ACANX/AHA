package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.common.event.AgentEvent;
import com.acanx.module.aha.common.event.ContentEvent;
import com.acanx.module.aha.common.event.ContentSegmentEndEvent;
import com.acanx.module.aha.common.event.DoneEvent;
import com.acanx.module.aha.common.event.ErrorEvent;
import com.acanx.module.aha.common.event.ToolCallEvent;
import com.acanx.module.aha.common.event.ToolResultEvent;
import com.acanx.module.aha.common.event.UsageEvent;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.model.SessionSummary;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.common.tool.ToolKind;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * 对话内核：把 {@link AgentService} 的事件流翻译成界面动作。
 *
 * <p>不引用任何 JavaFX 类型，界面动作经 {@link ChatView} 与「UI 线程投递器」注入，
 * 因此可以在**没有图形环境**的机器上完整测试（见 {@code ChatControllerTest}）。</p>
 *
 * <p>线程契约（{@code DesktopDesign.md} 第 6 节）：除了 {@link ChatView#appendAssistant}
 * 与 {@link ChatView#endAssistant}——它们由视图内部合流（分段累积器带锁、渲染桥可从任意
 * 线程提交），可在推理线程直接调用——其余界面动作一律经 {@code ui} 投递到 UI 线程。</p>
 *
 * <p>这两者必须走同一条通道：正文增量直达视图、段收尾却绕 {@code ui} 队列时，两条通道
 * 各自有序、合起来无序，下一段的增量会赶在 {@code endAssistant} 之前落进上一段
 * （PR #404 审查 P1-1）。</p>
 *
 * @since 0.2.0
 */
public final class ChatController {

    private static final Logger LOG = LoggerFactory.getLogger(ChatController.class);

    /** 切换会话时回放的历史条数上限（再长的历史用导出看，不必塞满界面）。 */
    public static final int HISTORY_LIMIT = 200;

    /** 默认执行器：每一轮对话跑在一条虚拟线程上。 */
    public static final Executor VIRTUAL_THREADS =
            task -> Thread.ofVirtual().name("aha-desktop-turn").start(task);

    private final ChatView view;

    private final AgentService service;

    private final SessionConfig sessionConfig;

    /** 下一次新建会话时要绑定的工作目录（{@code null} 表示纯聊天）。 */
    private String pendingWorkspace;

    /** 会话真正落库后通知宿主刷新列表（懒创建：发送首条消息时才创建）。 */
    private Runnable onSessionChanged = () -> { };

    private final Executor executor;

    /** UI 线程投递器（生产环境是 {@code FxDispatcher::dispatch}）。 */
    private final Consumer<Runnable> ui;

    /** 同一轮内同名工具的卡片句柄（工具调用是顺序的，先进先出匹配结果）。 */
    private final Map<String, Deque<Integer>> openCards = new ConcurrentHashMap<>();

    /** 卡片句柄 → 调用开始的纳秒时刻（耗时只在结果行展示，不参与业务逻辑）。 */
    private final Map<Integer, Long> cardStartedAt = new ConcurrentHashMap<>();

    /** 计时源；注入以便在没有时间流逝的测试里断言耗时口径。 */
    private final LongSupplier clock;

    private String sessionId;

    private volatile CancellationToken token = new CancellationToken();

    /**
     * @param view          对话界面
     * @param service       Agent 服务
     * @param sessionConfig 会话配置（模型、系统提示词等）
     * @param executor      执行器（每轮一个任务）
     * @param ui            UI 线程投递器
     */
    public ChatController(ChatView view, AgentService service, SessionConfig sessionConfig,
                          Executor executor, Consumer<Runnable> ui) {
        this(view, service, sessionConfig, executor, ui, System::nanoTime);
    }

    /**
     * @param view          对话界面
     * @param service       Agent 服务
     * @param sessionConfig 会话配置（模型、系统提示词等）
     * @param executor      执行器（每轮一个任务）
     * @param ui            UI 线程投递器
     * @param clock         计时源（纳秒）；测试注入固定值即可断言耗时口径
     */
    public ChatController(ChatView view, AgentService service, SessionConfig sessionConfig,
                          Executor executor, Consumer<Runnable> ui, LongSupplier clock) {
        this.view = Objects.requireNonNull(view, "view");
        this.service = Objects.requireNonNull(service, "service");
        this.sessionConfig = sessionConfig;
        this.executor = Objects.requireNonNull(executor, "executor");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * 发送一条用户输入。
     *
     * @param text 输入内容；空白输入被忽略
     */
    public void send(String text) {
        String input = text == null ? "" : text.strip();
        if (input.isEmpty()) {
            return;
        }
        ui.accept(() -> view.appendUser(input));
        ui.accept(() -> view.setBusy(true));
        CancellationToken current = new CancellationToken();
        token = current;
        executor.execute(() -> {
            try {
                service.streamChat(ensureSession(), input, this::onEvent, current);
            } catch (RuntimeException e) {
                LOG.warn("对话失败", e);
                // 兜底收尾（issue #403 / PR #404 审查 P1-2）：引擎在异常路径已补发段边界，
                // 但 AgentService 是可替换实现，不保证每位都补——残段留在缓冲里的话，
                // 用户下一次提问会续写到失败前那条气泡上。endAssistant 幂等，重复调用无副作用。
                view.endAssistant();
                ui.accept(() -> view.setBusy(false));
                ui.accept(() -> view.showError("SEND_FAILED", String.valueOf(e.getMessage())));
            } finally {
                // 消息落库后才刷新：此时标题 / 条数 / 工作空间分组才是最终值。
                // 不能在 ensureSession() 里立刻刷新——那时会话还没有任何消息，
                // listSessions() 的空会话清理会把它当垃圾删掉。
                if (sessionId() != null) {
                    onSessionChanged.run();
                }
            }
        });
    }

    /**
     * 中断当前这一轮。
     */
    public void cancel() {
        token.cancel();
    }

    /**
     * 开启新会话（换供应商 / 换模型后调用）。
     *
     * <p><b>懒创建</b>：这里只关闭旧会话并把当前会话置空，真正的记录在首次发送时
     * 才由 {@link #ensureSession()} 落库——否则每开一次窗口、每点一次「新建会话」
     * 都会留下一条没有标题、没有消息的空会话。</p>
     *
     * @return 始终为 {@code null}（尚未创建）；保留返回值以便调用方兼容
     */
    public synchronized String newSession() {
        this.pendingWorkspace = null;
        return closeAndReset();
    }

    /** 关闭当前会话并把当前会话置空（懒创建）。 */
    private String closeAndReset() {
        if (sessionId != null) {
            service.closeSession(sessionId);
            sessionId = null;
        }
        return null;
    }

    /**
     * 在指定工作空间下新建会话。
     *
     * @param workspace 工作目录；{@code null} 表示纯聊天
     * @return 新会话 ID
     */
    public synchronized String newSession(String workspace) {
        // 保留 pendingWorkspace 到 ensureSession() 真正创建为止：
        // 之前在这里 finally 清空，导致发送首条消息时工作空间已经丢了。
        this.pendingWorkspace = workspace;
        return closeAndReset();
    }

    /**
     * 会话创建 / 切换后的回调（宿主用它刷新会话列表）。
     *
     * @param handler 回调；{@code null} 表示忽略
     */
    public void onSessionChanged(Runnable handler) {
        this.onSessionChanged = handler == null ? () -> { } : handler;
    }

    /**
     * 当前会话 ID。
     *
     * @return 会话 ID；尚未创建时为 {@code null}
     */
    public synchronized String sessionId() {
        return sessionId;
    }

    /**
     * 列出会话摘要（最近创建的在前）。
     *
     * @return 会话摘要
     */
    public List<SessionSummary> sessions() {
        return service.listSessions();
    }

    /**
     * 切换到某个已有会话：关掉当前会话，把它的历史回放进对话流。
     *
     * <p>回放是**只读**的：不回写存储、不重新推理。工具消息只作为一行系统提示出现——
     * 它们是给模型的中间产物，不是对话内容。</p>
     *
     * @param sessionId 会话 ID
     * @return 回放进界面的消息条数
     */
    public synchronized int openSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return 0;
        }
        if (this.sessionId != null && !this.sessionId.equals(sessionId)) {
            service.closeSession(this.sessionId);
        }
        this.sessionId = sessionId;
        this.pendingWorkspace = null;
        List<ChatMessage> history = service.loadHistory(sessionId, HISTORY_LIMIT);
        ui.accept(view::clearConversation);
        int shown = 0;
        for (ChatMessage message : history) {
            String text = message.content();
            if (text == null || text.isBlank()) {
                continue;
            }
            switch (message.role()) {
                case USER -> {
                    ui.accept(() -> view.appendUser(text));
                    shown++;
                }
                case ASSISTANT -> {
                    ui.accept(() -> {
                        view.appendAssistant(text);
                        view.endAssistant();
                    });
                    shown++;
                }
                case TOOL -> {
                    ui.accept(() -> view.appendNotice("工具输出：" + oneLine(text)));
                    shown++;
                }
                default -> {
                    ui.accept(() -> view.appendNotice(text));
                    shown++;
                }
            }
        }
        ui.accept(() -> view.setBusy(false));
        return shown;
    }

    /**
     * 重命名会话。
     *
     * @param sessionId 会话 ID
     * @param title     新标题
     */
    public void renameSession(String sessionId, String title) {
        service.renameSession(sessionId, title);
    }

    /**
     * 删除会话。
     *
     * <p>删掉的正是当前会话时，同时把它从内存会话里摘掉，避免后续消息写进一个已删除的会话。</p>
     *
     * @param sessionId 会话 ID
     */
    public synchronized void deleteSession(String sessionId) {
        service.deleteSession(sessionId);
        if (sessionId != null && sessionId.equals(this.sessionId)) {
            this.sessionId = null;
        }
    }

    /**
     * 导出会话文本。
     *
     * @param sessionId 会话 ID
     * @param markdown  {@code true} 导出 Markdown，否则导出 JSON
     * @return 文本；会话不存在时返回空串
     */
    public String export(String sessionId, boolean markdown) {
        List<ChatMessage> history = service.loadHistory(sessionId, 0);
        String title = titleOf(sessionId);
        long now = System.currentTimeMillis();
        return markdown
                ? SessionExport.markdown(title, sessionId, history, now)
                : SessionExport.json(title, sessionId, history, now);
    }

    /**
     * 会话标题（找不到时用占位）。
     *
     * @param sessionId 会话 ID
     * @return 标题
     */
    public String titleOf(String sessionId) {
        return sessions().stream()
                .filter(summary -> summary.id().equals(sessionId))
                .map(SessionSummary::title)
                .findFirst()
                .orElse(SessionSummary.UNTITLED);
    }

    /**
     * 结束会话（关窗时调用）。
     */
    public synchronized void close() {
        if (sessionId != null) {
            service.closeSession(sessionId);
            sessionId = null;
        }
    }

    /**
     * 懒创建会话。
     *
     * @return 会话 ID
     */
    public synchronized String ensureSession() {
        if (sessionId == null) {
            sessionId = service.createSession(configWithWorkspace());
            LOG.info("已创建会话 {}", sessionId);
            this.pendingWorkspace = null;
        }
        return sessionId;
    }

    /** 当前配置 + 待绑定工作目录。 */
    private SessionConfig configWithWorkspace() {
        if (pendingWorkspace == null || (sessionConfig.extras() != null
                && sessionConfig.extras().containsKey("workspace"))) {
            return sessionConfig;
        }
        Map<String, Object> extras = new LinkedHashMap<>(
                sessionConfig.extras() == null ? Map.of() : sessionConfig.extras());
        extras.put("workspace", pendingWorkspace);
        return new SessionConfig(sessionConfig.model(), sessionConfig.tier(),
                sessionConfig.systemPrompt(), extras);
    }

    /** 单行化：工具输出可能是几十行，回放时压成一行提示。 */
    private static String oneLine(String text) {
        String flat = text.replace('\n', ' ').replace('\r', ' ').strip();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "…";
    }

    private void onEvent(AgentEvent event) {
        switch (event) {
            case ContentEvent content -> view.appendAssistant(content.delta());
            case ContentSegmentEndEvent ignored -> {
                // 一段正文到此为止（issue #403）：立刻结束当前气泡，后续正文另起一条。
                //
                // 【为什么直接调用、不经 ui 投递】正文增量走 view.appendAssistant（视图内部用
                // FxBridge 合流），本事件若改走 ui 队列，两条通道各自有序、合起来无序：
                // 推理线程投递完段边界会立刻处理下一条增量，而队列里的 endAssistant 可能还没跑，
                // 于是下一段的增量落进上一段、段号不推进，界面回到 #403 修复前的样子。
                // endAssistant 与 appendAssistant 同属「视图内部自行合流」（分段累积器带锁、
                // FxBridge 可从任意线程提交），直接调用才能保证「结束旧段」排在「下一段增量」之前。
                // PR #404 审查 P1-1。
                view.endAssistant();
            }
            case ToolCallEvent call -> onToolCall(call);
            case ToolResultEvent result -> onToolResult(result);
            case UsageEvent usage -> ui.accept(() -> view.setUsage(usage.promptTokens(), usage.completionTokens()));
            case ErrorEvent error -> {
                ui.accept(() -> view.showError(error.code(), error.message()));
                ui.accept(() -> view.setBusy(false));
            }
            case DoneEvent ignored -> {
                // 段边界已由 ContentSegmentEndEvent 收尾；这里再收一次是幂等的空操作，
                // 保留它是为了「没有段边界的旧实现 / 第三方监听器」也能正确结束气泡。
                // 同样直接调用：与正文同源，才不会在新一轮的增量之后才迟到收尾。
                view.endAssistant();
                ui.accept(() -> view.setBusy(false));
            }
            default -> LOG.debug("忽略事件 {}", event.getClass().getSimpleName());
        }
    }

    private void onToolCall(ToolCallEvent call) {
        ToolKind kind = ToolKind.of(call.toolName());
        String target = kind.targetOf(call.args());
        long startedAt = clock.getAsLong();
        ui.accept(() -> {
            int handle = view.beginToolCall(kind.label(), call.toolName(), target, call.args());
            cardStartedAt.put(handle, startedAt);
            openCards.computeIfAbsent(call.toolName(), name -> new ArrayDeque<>()).addLast(handle);
        });
    }

    private void onToolResult(ToolResultEvent result) {
        Deque<Integer> handles = openCards.get(result.toolName());
        Integer handle = handles == null ? null : handles.pollFirst();
        if (handle == null) {
            LOG.debug("工具 {} 的结果没有对应卡片，忽略", result.toolName());
            return;
        }
        String output = ToolCard.textOf(result.result());
        Long startedAt = cardStartedAt.remove(handle);
        long millis = startedAt == null ? 0 : Math.max(0, (clock.getAsLong() - startedAt) / 1_000_000);
        ui.accept(() -> view.finishToolCall(handle, result.success(), output, millis));
    }
}
