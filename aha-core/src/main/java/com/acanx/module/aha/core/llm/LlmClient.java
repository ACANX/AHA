package com.acanx.module.aha.core.llm;

import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;

import java.util.concurrent.CompletableFuture;

/**
 * LLM 客户端。
 *
 * @since 0.1.0
 */
public interface LlmClient {

    /**
     * 非流式对话。
     *
     * @param request 请求
     * @return 响应
     */
    CompletableFuture<ChatResponse> chat(ChatRequest request);

    /**
     * 流式对话。
     *
     * @param request  请求
     * @param listener 事件监听器
     * @param token    取消令牌
     */
    void streamChat(ChatRequest request, StreamEventListener listener, CancellationToken token);
}
