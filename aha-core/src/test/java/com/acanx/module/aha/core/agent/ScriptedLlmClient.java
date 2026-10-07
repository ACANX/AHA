package com.acanx.module.aha.core.agent;

import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.core.llm.LlmClient;
import com.acanx.module.aha.core.llm.StreamEventListener;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;
import com.acanx.module.aha.core.llm.protocol.Choice;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.ToolCall;
import com.acanx.module.aha.core.llm.protocol.Usage;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 脚本化假 LLM 客户端（测试辅助）。
 *
 * @since 0.1.0
 */
public final class ScriptedLlmClient implements LlmClient {

    private final Deque<ChatResponse> responses = new ArrayDeque<>();
    private final List<ChatRequest> requests = new ArrayList<>();
    private StreamScript streamScript;
    private final java.util.Queue<StreamScript> streamScripts = new java.util.ArrayDeque<>();
    private CancellationToken lastToken;

    /**
     * 流式事件脚本。
     *
     * @since 0.1.0
     */
    @FunctionalInterface
    public interface StreamScript {
        /**
         * 产生事件。
         *
         * @param listener 监听器
         * @param token    取消令牌
         */
        void run(StreamEventListener listener, CancellationToken token);
    }

    /**
     * 入队一个响应。
     *
     * @param response 响应
     */
    public void enqueue(ChatResponse response) {
        responses.add(response);
    }

    /**
     * 已收到的请求。
     *
     * @return 请求列表
     */
    public List<ChatRequest> requests() {
        return List.copyOf(requests);
    }

    /**
     * 最近一次流式调用收到的取消令牌。
     *
     * <p>用于验证令牌确实从服务层透传到客户端，而不是在中途被丢掉。</p>
     *
     * @return 取消令牌；未调用过流式时返回 {@code null}
     */
    public CancellationToken cancellationToken() {
        return lastToken;
    }

    /**
     * 入队一个流式脚本。
     *
     * <p>每轮 {@code streamChat} 取用一个，可模拟“工具调用→再推理”的多轮流程。
     * 队列取空后回退到 {@link #scriptStream}（若已配置）。</p>
     *
     * @param script 脚本
     */
    public void enqueueStream(StreamScript script) {
        streamScripts.add(script);
    }

    /**
     * 配置流式脚本（单脚本，每次调用都执行）。
     *
     * @param script 脚本
     */
    public void scriptStream(StreamScript script) {
        this.streamScript = script;
    }

    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        requests.add(request);
        ChatResponse next = responses.poll();
        if (next == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("脚本中无更多响应"));
        }
        return CompletableFuture.completedFuture(next);
    }

    @Override
    public void streamChat(ChatRequest request, StreamEventListener listener, CancellationToken token) {
        requests.add(request);
        lastToken = token;
        StreamScript script = streamScripts.poll();
        if (script == null) {
            script = streamScript;
        }
        if (script == null) {
            throw new UnsupportedOperationException("未配置流式脚本");
        }
        script.run(listener, token);
    }

    /**
     * 构造纯文本响应。
     *
     * @param content 内容
     * @return 响应
     */
    public static ChatResponse text(String content) {
        return new ChatResponse("id", "m",
                List.of(new Choice(0, ChatMessage.text(Role.ASSISTANT, content), "stop")),
                new Usage(1, 1, 2, null), Map.of());
    }

    /**
     * 构造工具调用响应。
     *
     * @param calls 工具调用
     * @return 响应
     */
    public static ChatResponse toolCalls(List<ToolCall> calls) {
        return new ChatResponse("id", "m",
                List.of(new Choice(0, new ChatMessage(Role.ASSISTANT, null, calls, null, null),
                        "tool_calls")),
                new Usage(1, 1, 2, null), Map.of());
    }

    /**
     * 构造单个工具调用响应。
     *
     * @param call 工具调用
     * @return 响应
     */
    public static ChatResponse toolCall(ToolCall call) {
        return toolCalls(List.of(call));
    }
}
