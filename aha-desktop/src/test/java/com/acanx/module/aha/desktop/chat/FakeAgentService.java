package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.common.event.AgentEvent;
import com.acanx.module.aha.common.model.MemoryEntry;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.model.ToolDescriptor;
import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.core.service.AgentEventListener;
import com.acanx.module.aha.core.service.AgentResponse;
import com.acanx.module.aha.core.service.AgentService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 假的 {@link AgentService}：按脚本发事件，供对话内核的单测使用。
 *
 * @since 0.2.0
 */
final class FakeAgentService implements AgentService {

    /** 收到的事件脚本（{@code streamChat} 时按序回调）。 */
    final List<AgentEvent> script = new ArrayList<>();

    /** 创建过的会话配置。 */
    final List<SessionConfig> created = new ArrayList<>();

    /** 关闭过的会话。 */
    final List<String> closed = new ArrayList<>();

    /** 收到的输入。 */
    final List<String> inputs = new ArrayList<>();

    /** 最近一次收到的取消令牌。 */
    CancellationToken lastToken;

    /** 置为 true 时 {@code streamChat} 抛异常。 */
    boolean failOnSend;

    @Override
    public String createSession(SessionConfig config) {
        created.add(config);
        return "session-" + created.size();
    }

    @Override
    public void closeSession(String sessionId) {
        closed.add(sessionId);
    }

    @Override
    public CompletableFuture<AgentResponse> chat(String sessionId, String input) {
        return CompletableFuture.completedFuture(
                new AgentResponse(sessionId, input, List.of(), null, "stop"));
    }

    @Override
    public void streamChat(String sessionId, String input, AgentEventListener listener) {
        streamChat(sessionId, input, listener, null);
    }

    @Override
    public void streamChat(String sessionId, String input, AgentEventListener listener,
                           CancellationToken token) {
        inputs.add(input);
        lastToken = token;
        if (failOnSend) {
            throw new IllegalStateException("模拟发送失败");
        }
        script.forEach(listener::onEvent);
    }

    @Override
    public List<ToolDescriptor> listTools() {
        return List.of();
    }

    @Override
    public ToolResult invokeTool(String toolName, Map<String, Object> params) {
        return ToolResult.success("ok");
    }

    @Override
    public List<MemoryEntry> recall(String sessionId, String query, int limit) {
        return List.of();
    }

    @Override
    public void shutdown() {
        // 测试替身无需清理
    }
}
