package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.common.event.AgentEvent;
import com.acanx.module.aha.common.event.ContentEvent;
import com.acanx.module.aha.common.event.DoneEvent;
import com.acanx.module.aha.common.event.ErrorEvent;
import com.acanx.module.aha.common.event.ToolCallEvent;
import com.acanx.module.aha.common.event.ToolResultEvent;
import com.acanx.module.aha.common.event.UsageEvent;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.common.tool.ToolKind;
import com.acanx.module.aha.core.service.AgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * 对话内核：把 {@link AgentService} 的事件流翻译成界面动作。
 *
 * <p>不引用任何 JavaFX 类型，界面动作经 {@link ChatView} 与「UI 线程投递器」注入，
 * 因此可以在**没有图形环境**的机器上完整测试（见 {@code ChatControllerTest}）。</p>
 *
 * <p>线程契约（{@code DesktopDesign.md} 第 6 节）：除了 {@link ChatView#appendAssistant}
 * ——它由视图内部用 {@code FxBridge} 合流，可从任意线程调用——其余界面动作一律经
 * {@code ui} 投递到 UI 线程。</p>
 *
 * @since 0.2.0
 */
public final class ChatController {

    private static final Logger LOG = LoggerFactory.getLogger(ChatController.class);

    /** 默认执行器：每一轮对话跑在一条虚拟线程上。 */
    public static final Executor VIRTUAL_THREADS =
            task -> Thread.ofVirtual().name("aha-desktop-turn").start(task);

    private final ChatView view;

    private final AgentService service;

    private final SessionConfig sessionConfig;

    private final Executor executor;

    /** UI 线程投递器（生产环境是 {@code FxDispatcher::dispatch}）。 */
    private final Consumer<Runnable> ui;

    /** 同一轮内同名工具的卡片句柄（工具调用是顺序的，先进先出匹配结果）。 */
    private final Map<String, Deque<Integer>> openCards = new ConcurrentHashMap<>();

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
        this.view = Objects.requireNonNull(view, "view");
        this.service = Objects.requireNonNull(service, "service");
        this.sessionConfig = sessionConfig;
        this.executor = Objects.requireNonNull(executor, "executor");
        this.ui = Objects.requireNonNull(ui, "ui");
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
                ui.accept(() -> view.setBusy(false));
                ui.accept(() -> view.showError("SEND_FAILED", String.valueOf(e.getMessage())));
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
     * @return 新会话 ID
     */
    public synchronized String newSession() {
        if (sessionId != null) {
            service.closeSession(sessionId);
            sessionId = null;
        }
        return ensureSession();
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
            sessionId = service.createSession(sessionConfig);
            LOG.info("已创建会话 {}", sessionId);
        }
        return sessionId;
    }

    private void onEvent(AgentEvent event) {
        switch (event) {
            case ContentEvent content -> view.appendAssistant(content.delta());
            case ToolCallEvent call -> onToolCall(call);
            case ToolResultEvent result -> onToolResult(result);
            case UsageEvent usage -> ui.accept(() -> view.setUsage(usage.promptTokens(), usage.completionTokens()));
            case ErrorEvent error -> {
                ui.accept(() -> view.showError(error.code(), error.message()));
                ui.accept(() -> view.setBusy(false));
            }
            case DoneEvent ignored -> {
                ui.accept(view::endAssistant);
                ui.accept(() -> view.setBusy(false));
            }
            default -> LOG.debug("忽略事件 {}", event.getClass().getSimpleName());
        }
    }

    private void onToolCall(ToolCallEvent call) {
        ToolKind kind = ToolKind.of(call.toolName());
        String target = kind.targetOf(call.args());
        ui.accept(() -> {
            int handle = view.beginToolCall(kind.label(), call.toolName(), target);
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
        String output = ToolSummary.textOf(result.result());
        String summary = ToolSummary.result(result.success(), output);
        ui.accept(() -> view.finishToolCall(handle, result.success(), summary, output));
    }
}
