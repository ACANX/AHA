package com.acanx.module.aha.core.agent;

import com.acanx.module.aha.common.event.AgentEvent;
import com.acanx.module.aha.common.event.ContentEvent;
import com.acanx.module.aha.common.event.DoneEvent;
import com.acanx.module.aha.common.event.ToolCallEvent;
import com.acanx.module.aha.common.event.ToolResultEvent;
import com.acanx.module.aha.common.event.UsageEvent;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.common.tool.JsonSchema;
import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.tool.ToolPermission;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.StreamEvent;
import com.acanx.module.aha.core.llm.protocol.StreamEventType;
import com.acanx.module.aha.core.llm.protocol.ToolCallDelta;
import com.acanx.module.aha.core.llm.protocol.Usage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentEngine#stream} 测试。
 *
 * @since 0.1.0
 */
class AgentEngineStreamTest {

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
            return "回显";
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

    private static StreamEvent content(String delta) {
        return new StreamEvent(StreamEventType.CONTENT_DELTA, delta, null, null, null, null, null, null);
    }

    private static StreamEvent usage(int prompt, int completion) {
        return new StreamEvent(StreamEventType.USAGE, null, null, null,
                new Usage(prompt, completion, prompt + completion, null), null, null, null);
    }

    private static StreamEvent toolDelta(int index, String id, String name, String args) {
        return new StreamEvent(StreamEventType.TOOL_CALL_DELTA, null, null,
                new ToolCallDelta(index, id, name, args), null, null, null, null);
    }

    private static StreamEvent done(String finishReason) {
        return new StreamEvent(StreamEventType.DONE, null, null, null, null, finishReason, null, null);
    }

    @Test
    void streamEmitsContentToolAndDoneEvents() {
        ScriptedLlmClient llm = new ScriptedLlmClient();
        // 第 1 轮：输出内容并发起工具调用
        llm.enqueueStream((listener, token) -> {
            listener.onEvent(content("He"));
            listener.onEvent(content("llo"));
            listener.onEvent(toolDelta(0, "call_1", "echo", "{\"text\":"));
            listener.onEvent(toolDelta(0, null, null, "\"hi\"}"));
            listener.onEvent(usage(1, 2));
            listener.onEvent(done("tool_calls"));
        });
        // 第 2 轮：基于工具结果给出最终回答
        llm.enqueueStream((listener, token) -> {
            listener.onEvent(content("最终回答"));
            listener.onEvent(done("stop"));
        });

        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        ToolRegistry tools = new ToolRegistry();
        tools.register(new EchoTool());
        AgentEngine engine = new AgentEngine(llm, tools, memory);

        List<AgentEvent> events = new ArrayList<>();
        engine.stream("s1", new SessionConfig("m", "sys", Map.of()), "m", "hi",
                events::add, new CancellationToken());

        assertThat(events.stream().filter(ContentEvent.class::isInstance)
                .map(e -> ((ContentEvent) e).delta()).toList())
                .containsExactly("He", "llo", "最终回答");
        assertThat(events.stream().filter(ToolCallEvent.class::isInstance)
                .map(e -> ((ToolCallEvent) e).toolName()).toList())
                .containsExactly("echo");
        assertThat(events.stream().filter(ToolResultEvent.class::isInstance)
                .map(e -> ((ToolResultEvent) e).result()).toList())
                .containsExactly("echo:hi");
        assertThat(events.stream().filter(UsageEvent.class::isInstance)).hasSize(1);
        assertThat(events.stream().filter(DoneEvent.class::isInstance)
                .map(e -> ((DoneEvent) e).finishReason()).toList())
                .containsExactly("stop");

        // user + assistant(带工具调用) + tool + assistant(最终回答)
        assertThat(memory.loadHistory("s1", 10)).hasSize(4);
    }

    @Test
    void streamFeedsToolResultBackToModel() {
        // 回归：流式路径曾缺少工具调用循环，执行工具后直接结束，
        // 模型永远看不到工具结果，表现为“只打印工具输出、不给回答”。
        ScriptedLlmClient llm = new ScriptedLlmClient();
        llm.enqueueStream((listener, token) -> {
            listener.onEvent(toolDelta(0, "call_1", "echo", "{\"text\":\"hi\"}"));
            listener.onEvent(done("tool_calls"));
        });
        llm.enqueueStream((listener, token) -> {
            listener.onEvent(content("基于工具结果的回答"));
            listener.onEvent(done("stop"));
        });

        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        ToolRegistry tools = new ToolRegistry();
        tools.register(new EchoTool());
        AgentEngine engine = new AgentEngine(llm, tools, memory);

        List<AgentEvent> events = new ArrayList<>();
        engine.stream("s1", new SessionConfig("m", "sys", Map.of()), "m", "hi",
                events::add, new CancellationToken());

        // 必须发生第二轮 LLM 调用
        assertThat(llm.requests()).hasSize(2);

        // 第二轮请求中，工具结果已回灌：assistant(tool_calls) + tool 消息
        var second = llm.requests().get(1).messages();
        assertThat(second.stream().filter(m -> m.role() == Role.TOOL)
                .map(ChatMessage::content).toList()).containsExactly("echo:hi");
        assertThat(second.stream()
                .filter(m -> m.role() == Role.ASSISTANT && !m.toolCalls().isEmpty())
                .count()).isEqualTo(1);

        // 模型最终给出了回答
        assertThat(events.stream().filter(ContentEvent.class::isInstance)
                .map(e -> ((ContentEvent) e).delta()).toList())
                .containsExactly("基于工具结果的回答");
    }

    @Test
    void streamStopsAtIterationLimitWhenModelKeepsCallingTools() {
        // 模型无限请求工具时，循环应受 MAX_TOOL_ITERATIONS 限制而非挂死
        ScriptedLlmClient llm = new ScriptedLlmClient();
        llm.scriptStream((listener, token) -> {
            listener.onEvent(toolDelta(0, "call_1", "echo", "{\"text\":\"x\"}"));
            listener.onEvent(done("tool_calls"));
        });

        ToolRegistry tools = new ToolRegistry();
        tools.register(new EchoTool());
        AgentEngine engine = new AgentEngine(llm, tools, new InMemoryMemoryStore());

        List<AgentEvent> events = new ArrayList<>();
        engine.stream("s1", new SessionConfig("m", "sys", Map.of()), "m", "hi",
                events::add, new CancellationToken());

        assertThat(llm.requests()).hasSize(AgentEngine.MAX_TOOL_ITERATIONS);
        assertThat(events.stream().filter(DoneEvent.class::isInstance)).hasSize(1);
    }

    @Test
    void streamWithoutToolCallsEmitsDoneOnly() {
        ScriptedLlmClient llm = new ScriptedLlmClient();
        llm.scriptStream((listener, token) -> {
            listener.onEvent(content("plain"));
            listener.onEvent(done("stop"));
        });

        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        AgentEngine engine = new AgentEngine(llm, new ToolRegistry(), memory);

        List<AgentEvent> events = new ArrayList<>();
        engine.stream("s1", new SessionConfig("m", null, Map.of()), "m", "hi",
                events::add, new CancellationToken());

        assertThat(events.stream().filter(ToolCallEvent.class::isInstance)).isEmpty();
        assertThat(events.stream().filter(DoneEvent.class::isInstance)).hasSize(1);
        assertThat(memory.loadHistory("s1", 10)).hasSize(2);
    }

    @Test
    void streamHandlesToolFailureGracefully() {
        ScriptedLlmClient llm = new ScriptedLlmClient();
        llm.scriptStream((listener, token) -> {
            listener.onEvent(toolDelta(0, "call_1", "missing-tool", "{}"));
            listener.onEvent(done("tool_calls"));
        });

        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        AgentEngine engine = new AgentEngine(llm, new ToolRegistry(), memory);

        List<AgentEvent> events = new ArrayList<>();
        engine.stream("s1", new SessionConfig("m", null, Map.of()), "m", "hi",
                events::add, new CancellationToken());

        // 事件携带的是**给人看**的结果：失败时为错误信息本身，而不是回灌给模型用的
        // "ERROR: " 前缀（那是协议形态，展示层不该去剥它）
        List<ToolResultEvent> results = events.stream().filter(ToolResultEvent.class::isInstance)
                .map(ToolResultEvent.class::cast).toList();
        assertThat(results).isNotEmpty();
        assertThat(results).allSatisfy(result -> assertThat(result.success()).isFalse());
        assertThat(results.stream().map(r -> String.valueOf(r.result())).toList())
                .anyMatch(text -> text.contains("不存在") || text.contains("missing-tool"));
        // 回灌给模型的消息仍带前缀：模型需要明确的失败标记
        assertThat(memory.loadHistory("s1", 10).stream().map(ChatMessage::content).toList())
                .anyMatch(text -> text.startsWith("ERROR:"));
    }

    @Test
    void streamingIgnoresReasoningAndErrorEvents() {
        ScriptedLlmClient llm = new ScriptedLlmClient();
        llm.scriptStream((listener, token) -> {
            listener.onEvent(new StreamEvent(StreamEventType.REASONING_DELTA, null, "thinking",
                    null, null, null, null, null));
            listener.onEvent(new StreamEvent(StreamEventType.ERROR, null, null, null, null,
                    null, "E1", "boom"));
            listener.onEvent(done("stop"));
        });

        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        AgentEngine engine = new AgentEngine(llm, new ToolRegistry(), memory);

        List<AgentEvent> events = new ArrayList<>();
        engine.stream("s1", new SessionConfig("m", null, Map.of()), "m", "hi",
                events::add, new CancellationToken());

        // 仅 DoneEvent（reasoning 与 error 不透出为 Agent 事件）
        assertThat(events).hasSize(1);
        assertThat(events.getFirst()).isInstanceOf(DoneEvent.class);
    }
}
