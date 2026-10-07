package com.acanx.module.aha.core.agent;

import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.config.SystemPromptLoader;
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
import com.acanx.module.aha.core.service.AgentResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentEngine} 测试（使用假 LLM 客户端）。
 *
 * @since 0.1.0
 */
class AgentEngineTest {

    /**
     * 依次返回预置响应的假客户端。
     */
    private static final class FakeLlmClient implements LlmClient {
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

    private static ChatResponse textResponse(String content) {
        return new ChatResponse("id", "m",
                List.of(new Choice(0, ChatMessage.text(Role.ASSISTANT, content), "stop")),
                new Usage(1, 1, 2, null), Map.of());
    }

    private static ChatResponse toolCallResponse(ToolCall call) {
        ChatMessage message = new ChatMessage(Role.ASSISTANT, null, List.of(call), null, null);
        return new ChatResponse("id", "m",
                List.of(new Choice(0, message, "tool_calls")),
                new Usage(1, 1, 2, null), Map.of());
    }

    @Test
    void runsSimpleConversation() {
        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        FakeLlmClient llm = new FakeLlmClient();
        llm.enqueue(textResponse("hello there"));
        AgentEngine engine = new AgentEngine(llm, new ToolRegistry(), memory);

        AgentResponse response = engine.run("s1",
                new SessionConfig("m", "be nice", Map.of()), "m", "hi", new CancellationToken());

        assertThat(response.content()).isEqualTo("hello there");
        assertThat(response.finishReason()).isEqualTo("stop");
        // user + assistant 已持久化
        assertThat(memory.loadHistory("s1", 10)).hasSize(2);
    }

    @Test
    void executesToolCallThenFinishes() {
        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        FakeLlmClient llm = new FakeLlmClient();
        ToolCall call = new ToolCall("call_1", "file-read", Map.of("path", "/tmp/x"));
        llm.enqueue(toolCallResponse(call));
        llm.enqueue(textResponse("done"));

        ToolRegistry tools = new ToolRegistry();
        tools.register(new com.acanx.module.aha.common.tool.Tool() {
            @Override
            public String name() {
                return "file-read";
            }

            @Override
            public String description() {
                return "read";
            }

            @Override
            public com.acanx.module.aha.common.tool.JsonSchema parameters() {
                return com.acanx.module.aha.common.tool.JsonSchema.object(Map.of(), List.of());
            }

            @Override
            public com.acanx.module.aha.common.model.ToolResult execute(
                    Map<String, Object> params, CancellationToken token) {
                return com.acanx.module.aha.common.model.ToolResult.success("file-content");
            }

            @Override
            public com.acanx.module.aha.common.tool.ToolPermission requiredPermission() {
                return com.acanx.module.aha.common.tool.ToolPermission.READ;
            }
        });

        AgentEngine engine = new AgentEngine(llm, tools, memory);
        AgentResponse response = engine.run("s1", new SessionConfig("m", null, Map.of()),
                "m", "read file", new CancellationToken());

        assertThat(response.content()).isEqualTo("done");
        assertThat(response.toolCalls()).hasSize(1);
        // user + assistant(tool_call) + tool + assistant(final)
        assertThat(memory.loadHistory("s1", 10)).hasSize(4);
        assertThat(llm.requests).hasSize(2);
    }

    @Test
    void passesToolsInRequest() {
        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        FakeLlmClient llm = new FakeLlmClient();
        llm.enqueue(textResponse("ok"));
        ToolRegistry tools = new ToolRegistry();
        AgentEngine engine = new AgentEngine(llm, tools, memory);

        engine.run("s1", new SessionConfig("m", null, Map.of()), "m", "hi", new CancellationToken());

        assertThat(llm.requests).isNotEmpty();
        assertThat(llm.requests.get(0).model()).isEqualTo("m");
    }
    @Test
    void systemMessageCarriesRuntimeEnvironment() {
        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        FakeLlmClient llm = new FakeLlmClient();
        llm.enqueue(textResponse("ok"));
        AgentEngine engine = new AgentEngine(llm, new ToolRegistry(), memory);

        engine.run("s1", new SessionConfig("m", "你是助手", Map.of()), "m", "hi",
                new CancellationToken());

        var messages = llm.requests.get(0).messages();
        // 身份与环境**各自成条** system 消息，不拼成一段
        assertThat(messages.get(0).role()).isEqualTo(Role.SYSTEM);
        assertThat(messages.get(0).content()).isEqualTo("你是助手");
        assertThat(messages.get(1).role()).isEqualTo(Role.SYSTEM);
        // 没有这段信息时模型会发 Unix 命令，在 cmd.exe 下失败且报错看不出是平台问题
        assertThat(messages.get(1).content()).contains("运行环境").contains("命令执行方式");
    }

    @Test
    void eachIdentitySegmentBecomesItsOwnSystemMessage() {
        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        FakeLlmClient llm = new FakeLlmClient();
        llm.enqueue(textResponse("ok"));
        AgentEngine engine = new AgentEngine(llm, new ToolRegistry(), memory);
        SessionConfig config = new SessionConfig("m", "整体文本", Map.of(
                SystemPromptLoader.SEGMENTS_KEY, List.of("用户级约定", "项目级约定")));

        engine.run("s1", config, "m", "hi", new CancellationToken());

        var messages = llm.requests.get(0).messages();
        // 系统提示词（第一片段）→ 各身份文件 → 运行环境，逐条独立
        assertThat(messages.get(0).content()).isEqualTo("用户级约定");
        assertThat(messages.get(1).content()).isEqualTo("项目级约定");
        assertThat(messages.get(2).content()).contains("运行环境");
        // 三条 system 之后才是对话内容
        assertThat(messages.get(0).role()).isEqualTo(Role.SYSTEM);
        assertThat(messages.get(1).role()).isEqualTo(Role.SYSTEM);
        assertThat(messages.get(2).role()).isEqualTo(Role.SYSTEM);
        assertThat(messages.get(3).role()).isEqualTo(Role.USER);
    }

    @Test
    void environmentBlockIsPresentEvenWithoutIdentity() {
        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        FakeLlmClient llm = new FakeLlmClient();
        llm.enqueue(textResponse("ok"));
        AgentEngine engine = new AgentEngine(llm, new ToolRegistry(), memory);

        engine.run("s1", new SessionConfig("m", null, Map.of()), "m", "hi",
                new CancellationToken());

        assertThat(llm.requests.get(0).messages().get(0).content()).contains("运行环境");
    }

}
