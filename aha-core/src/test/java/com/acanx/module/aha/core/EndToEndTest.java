package com.acanx.module.aha.core;

import com.acanx.module.aha.common.model.MemoryEntry;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.common.tool.JsonSchema;
import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.tool.ToolPermission;
import com.acanx.module.aha.core.agent.AgentEngine;
import com.acanx.module.aha.core.agent.ToolRegistry;
import com.acanx.module.aha.core.llm.LlmClient;
import com.acanx.module.aha.core.llm.StreamEventListener;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;
import com.acanx.module.aha.core.llm.protocol.Choice;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.ToolCall;
import com.acanx.module.aha.core.llm.protocol.Usage;
import com.acanx.module.aha.core.memory.SqliteMemoryStore;
import com.acanx.module.aha.core.service.AgentResponse;
import com.acanx.module.aha.core.service.SessionManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端测试：会话 → 推理 → 工具调用 → 持久化 → 检索。
 *
 * <p>使用真实 {@link SqliteMemoryStore} 与假 LLM 客户端，
 * 覆盖 V0.1 的核心链路上所有本地组件。</p>
 *
 * @since 0.1.0
 */
class EndToEndTest {

    @TempDir
    Path tempDir;

    /**
     * 按序返回预置响应的假客户端。
     */
    private static final class ScriptedLlmClient implements LlmClient {
        private final Deque<ChatResponse> responses = new ArrayDeque<>();
        private final List<ChatRequest> requests = new java.util.ArrayList<>();

        void enqueue(ChatResponse response) {
            responses.add(response);
        }

        @Override
        public CompletableFuture<ChatResponse> chat(ChatRequest request) {
            requests.add(request);
            return CompletableFuture.completedFuture(responses.poll());
        }

        @Override
        public void streamChat(ChatRequest request, StreamEventListener listener, CancellationToken token) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * 回显工具。
     */
    private static final class EchoTool implements Tool {
        @Override
        public String name() {
            return "echo";
        }

        @Override
        public String description() {
            return "回显输入";
        }

        @Override
        public JsonSchema parameters() {
            return JsonSchema.object(Map.of("text", JsonSchema.string("文本")), List.of("text"));
        }

        @Override
        public ToolResult execute(Map<String, Object> params, CancellationToken token) {
            return ToolResult.success("echo:" + params.get("text"));
        }

        @Override
        public ToolPermission requiredPermission() {
            return ToolPermission.READ;
        }
    }

    private static ChatResponse text(String content) {
        return new ChatResponse("id", "m",
                List.of(new Choice(0, ChatMessage.text(Role.ASSISTANT, content), "stop")),
                new Usage(1, 1, 2, null), Map.of());
    }

    private static ChatResponse toolCall(ToolCall call) {
        return new ChatResponse("id", "m",
                List.of(new Choice(0, new ChatMessage(Role.ASSISTANT, null, List.of(call), null, null),
                        "tool_calls")),
                new Usage(1, 1, 2, null), Map.of());
    }

    @Test
    void fullConversationWithToolCallAndPersistence() {
        Path db = tempDir.resolve("Data").resolve("Aha.db");
        SqliteMemoryStore memory = new SqliteMemoryStore(db);
        memory.init();

        ScriptedLlmClient llm = new ScriptedLlmClient();
        llm.enqueue(toolCall(new ToolCall("call_1", "echo", Map.of("text", "ping"))));
        llm.enqueue(text("pong"));

        ToolRegistry tools = new ToolRegistry();
        tools.register(new EchoTool());

        SessionManager sessions = new SessionManager(memory);
        String sessionId = sessions.create(new SessionConfig("m", "你是助手", Map.of()));

        AgentEngine engine = new AgentEngine(llm, tools, memory);
        AgentResponse response = engine.run(sessionId, sessions.get(sessionId).orElseThrow(),
                "m", "请回显 ping", new CancellationToken());

        assertThat(response.content()).isEqualTo("pong");
        assertThat(response.toolCalls()).hasSize(1);
        assertThat(response.toolCalls().get(0).name()).isEqualTo("echo");
        assertThat(llm.requests).hasSize(2);
        assertThat(llm.requests.get(0).tools()).hasSize(1);

        // user + assistant(tool_call) + tool + assistant(final)
        List<ChatMessage> history = memory.loadHistory(sessionId, 100);
        assertThat(history).hasSize(4);
        assertThat(history.get(2).role()).isEqualTo(Role.TOOL);
        assertThat(history.get(2).content()).isEqualTo("echo:ping");
        memory.close();

        // 重新打开：验证持久化
        SqliteMemoryStore reopened = new SqliteMemoryStore(db);
        reopened.init();
        assertThat(reopened.loadHistory(sessionId, 100)).hasSize(4);
        reopened.close();
    }

    @Test
    void memoryStoreSupportsStoreAndRecall() {
        SqliteMemoryStore memory = new SqliteMemoryStore(tempDir.resolve("mem.db"));
        memory.init();
        memory.createSession("s1", "{}");

        memory.storeMemory("s1", new MemoryEntry("lang", "用户偏好简体中文", System.currentTimeMillis()));
        memory.storeMemory("s1", new MemoryEntry("editor", "用户使用 IntelliJ", System.currentTimeMillis()));

        List<MemoryEntry> hits = memory.recall("s1", "简体中文", 10);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).key()).isEqualTo("lang");

        memory.close();
    }

    @Test
    void multipleSessionsAreIsolated() {
        SqliteMemoryStore memory = new SqliteMemoryStore(tempDir.resolve("iso.db"));
        memory.init();

        ScriptedLlmClient llm = new ScriptedLlmClient();
        llm.enqueue(text("a1"));
        llm.enqueue(text("b1"));

        AgentEngine engine = new AgentEngine(llm, new ToolRegistry(), memory);
        SessionManager sessions = new SessionManager(memory);
        String first = sessions.create(new SessionConfig("m", null, Map.of()));
        String second = sessions.create(new SessionConfig("m", null, Map.of()));

        engine.run(first, sessions.get(first).orElseThrow(), "m", "one", new CancellationToken());
        engine.run(second, sessions.get(second).orElseThrow(), "m", "two", new CancellationToken());

        assertThat(memory.loadHistory(first, 100)).hasSize(2);
        assertThat(memory.loadHistory(second, 100)).hasSize(2);
        assertThat(memory.loadHistory(first, 100).get(1).content()).isEqualTo("a1");
        assertThat(memory.loadHistory(second, 100).get(1).content()).isEqualTo("b1");
        memory.close();
    }
}
