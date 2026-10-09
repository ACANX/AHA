package com.acanx.module.aha.core.agent;

import com.acanx.module.aha.common.event.AgentEvent;
import com.acanx.module.aha.common.exception.Exceptions;
import com.acanx.module.aha.common.exception.ToolExecutionException;
import com.acanx.module.aha.common.event.ContentEvent;
import com.acanx.module.aha.common.event.DoneEvent;
import com.acanx.module.aha.common.event.ToolCallEvent;
import com.acanx.module.aha.common.event.ToolResultEvent;
import com.acanx.module.aha.common.event.UsageEvent;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.model.ToolDescriptor;
import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.core.config.SystemPromptLoader;
import com.acanx.module.aha.core.llm.LlmClient;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;
import com.acanx.module.aha.core.llm.protocol.Choice;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.StreamEvent;
import com.acanx.module.aha.core.llm.protocol.StreamEventType;
import com.acanx.module.aha.core.llm.protocol.ToolCall;
import com.acanx.module.aha.core.llm.protocol.ToolCallDelta;
import com.acanx.module.aha.core.llm.protocol.ToolDefinition;
import com.acanx.module.aha.core.llm.protocol.Usage;
import com.acanx.module.aha.core.memory.MemoryStore;
import com.acanx.module.aha.core.service.AgentEventListener;
import com.acanx.module.aha.core.service.AgentResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent 推理引擎。
 *
 * <p>推理循环：加载历史 → 构建请求 → 调用 LLM → 工具调用循环 → 持久化 → 返回。</p>
 *
 * @since 0.1.0
 */
public final class AgentEngine {

    private static final Logger LOG = LoggerFactory.getLogger(AgentEngine.class);

    /** 单次推理允许的最大工具调用轮数。 */
    public static final int MAX_TOOL_ITERATIONS = 8;

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final MemoryStore memoryStore;
    private final int maxContextEntries;

    /**
     * 构造引擎。
     *
     * @param llmClient    LLM 客户端
     * @param toolRegistry 工具注册表
     * @param memoryStore  记忆存储
     */
    public AgentEngine(LlmClient llmClient, ToolRegistry toolRegistry, MemoryStore memoryStore) {
        this(llmClient, toolRegistry, memoryStore, 100);
    }

    /**
     * 构造引擎。
     *
     * @param llmClient         LLM 客户端
     * @param toolRegistry      工具注册表
     * @param memoryStore       记忆存储
     * @param maxContextEntries 最大上下文条目数
     */
    public AgentEngine(LlmClient llmClient, ToolRegistry toolRegistry, MemoryStore memoryStore,
                       int maxContextEntries) {
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.memoryStore = memoryStore;
        this.maxContextEntries = maxContextEntries > 0 ? maxContextEntries : 100;
    }

    /**
     * 非流式推理。
     *
     * @param sessionId 会话 ID
     * @param config    会话配置
     * @param model     默认模型
     * @param input     用户输入
     * @param token     取消令牌
     * @return 响应
     */
    public AgentResponse run(String sessionId, SessionConfig config, String model,
                             String input, CancellationToken token) {
        List<ChatMessage> messages = prepare(sessionId, config, input);
        List<ToolDefinition> tools = toolDefinitions();

        List<ToolCall> allToolCalls = new ArrayList<>();
        String content = null;
        String finishReason = null;
        Usage usage = new Usage(0, 0, 0, null);
        String effectiveModel = effectiveModel(config, model);

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            if (token.isCancelled()) {
                break;
            }
            ChatRequest request = new ChatRequest(effectiveModel, messages, tools,
                    0.0d, 0, false, extensions(config));
            ChatResponse response = llmClient.chat(request).join();
            if (response.choices() == null || response.choices().isEmpty()) {
                break;
            }
            usage = response.usage() == null ? usage : response.usage();
            Choice choice = response.choices().get(0);
            ChatMessage assistant = choice.message();
            content = assistant.content();
            finishReason = choice.finishReason();

            messages.add(assistant);
            memoryStore.appendMessage(sessionId, assistant);

            if (assistant.toolCalls() == null || assistant.toolCalls().isEmpty()) {
                break;
            }
            allToolCalls.addAll(assistant.toolCalls());
            for (ToolCall call : assistant.toolCalls()) {
                ChatMessage toolMessage = executeTool(sessionId, call, token).message();
                messages.add(toolMessage);
                memoryStore.appendMessage(sessionId, toolMessage);
            }
        }
        return new AgentResponse(sessionId, content, allToolCalls, usage, finishReason);
    }

    /**
     * 流式推理。
     *
     * <p>与非流式 {@link #run} 一致，进入工具调用循环：
     * 推理 → 执行工具 → 将结果回灌给模型 → 再推理，直至模型不再请求工具。
     * 缺少该循环会剥夺模型看到工具结果的机会，表现为“只打印工具输出、不给回答”。</p>
     *
     * <p>循环位于流式回调<b>返回之后</b>，回调内只做事件累积，因此不存在递归阻塞。</p>
     *
     * @param sessionId 会话 ID
     * @param config    会话配置
     * @param model     默认模型
     * @param input     用户输入
     * @param listener  事件监听器
     * @param token     取消令牌
     */
    public void stream(String sessionId, SessionConfig config, String model,
                       String input, AgentEventListener listener, CancellationToken token) {
        List<ChatMessage> messages = prepare(sessionId, config, input);
        List<ToolDefinition> tools = toolDefinitions();
        String effectiveModel = effectiveModel(config, model);

        String finishReason = null;

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            if (token.isCancelled()) {
                break;
            }

            // 每轮重置累积器，避免上一轮内容与工具调用泄漏到本轮
            StringBuilder content = new StringBuilder();
            Map<Integer, AccumulatedToolCall> toolCalls = new LinkedHashMap<>();
            String[] roundFinishReason = {null};

            ChatRequest request = new ChatRequest(effectiveModel, messages, tools,
                    0.0d, 0, true, extensions(config));

            llmClient.streamChat(request, (StreamEvent event) -> {
                switch (event.type()) {
                    case CONTENT_DELTA -> {
                        content.append(event.contentDelta() == null ? "" : event.contentDelta());
                        listener.onEvent(new ContentEvent(sessionId, event.contentDelta()));
                    }
                    case REASONING_DELTA -> {
                        // 思维链暂不透出为 Agent 事件，仅记录
                        LOG.debug("reasoning delta: {}", event.reasoningDelta());
                    }
                    case TOOL_CALL_DELTA -> accumulate(toolCalls, event.toolCallDelta());
                    case USAGE -> {
                        if (event.usage() != null) {
                            listener.onEvent(new UsageEvent(sessionId,
                                    event.usage().promptTokens(), event.usage().completionTokens()));
                        }
                    }
                    case DONE -> roundFinishReason[0] = event.finishReason();
                    case ERROR -> LOG.warn("LLM 流式错误: {} {}", event.errorCode(), event.errorMessage());
                    default -> {
                    }
                }
            }, token);

            finishReason = roundFinishReason[0];

            ChatMessage assistant = new ChatMessage(Role.ASSISTANT, content.toString(),
                    toToolCalls(toolCalls), null, null);
            // 必须并入本轮上下文：否则下一轮看不到模型自己发起的工具调用，
            // tool 消息也会失去所依附的 assistant 消息（违反 OpenAI 协议）
            messages.add(assistant);
            memoryStore.appendMessage(sessionId, assistant);

            if (assistant.toolCalls().isEmpty()) {
                break;
            }

            for (ToolCall call : assistant.toolCalls()) {
                listener.onEvent(new ToolCallEvent(sessionId, call.name(), call.arguments()));
                ToolOutcome outcome = executeTool(sessionId, call, token);
                messages.add(outcome.message());
                memoryStore.appendMessage(sessionId, outcome.message());
                listener.onEvent(new ToolResultEvent(sessionId, call.name(), outcome.display(),
                        outcome.success()));
            }
        }
        listener.onEvent(new DoneEvent(sessionId, finishReason));
    }

    /**
     * LLM 客户端。
     *
     * @return 客户端
     */
    public LlmClient llmClient() {
        return llmClient;
    }

    /**
     * 工具注册表。
     *
     * @return 注册表
     */
    public ToolRegistry toolRegistry() {
        return toolRegistry;
    }

    /**
     * 记忆存储。
     *
     * @return 存储
     */
    public MemoryStore memoryStore() {
        return memoryStore;
    }

    // ------------------------------------------------------------------

    private List<ChatMessage> prepare(String sessionId, SessionConfig config, String input) {
        List<ChatMessage> messages = new ArrayList<>();
        // 身份**各自成段**：系统提示词（第一个片段）在前，之后每个身份文件各占一条 system 消息，
        // 最后是运行环境块。不拼成一整段——拼接会让模型完全看不出哪些内容来自哪个文件。
        // 顺序即优先级，越靠后越具体。
        for (String segment : identitySegments(config)) {
            messages.add(ChatMessage.text(Role.SYSTEM, segment));
        }
        // 运行环境：模型必须知道自己在哪个平台、用哪个 shell，否则会发出 Unix 风格命令，
        // 在 cmd.exe 下失败且报错看不出是平台不匹配。用户没配身份时也要带上。
        messages.add(ChatMessage.text(Role.SYSTEM, SystemPromptLoader.environmentBlock()));
        messages.addAll(memoryStore.loadHistory(sessionId, maxContextEntries));
        ChatMessage userMessage = ChatMessage.text(Role.USER, input);
        messages.add(userMessage);
        memoryStore.appendMessage(sessionId, userMessage);
        return messages;
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
     * 身份片段（按优先级从低到高）。
     *
     * <p>取自会话配置里固化的 `PromptSegments`；旧会话没有这个键时退回单段（整体文本），
     * 保证向后兼容。</p>
     *
     * @param config 会话配置，可为 {@code null}
     * @return 片段列表；无身份时为空
     */
    private static List<String> identitySegments(SessionConfig config) {
        if (config == null) {
            return List.of();
        }
        Object raw = config.extras() == null ? null
                : config.extras().get(SystemPromptLoader.SEGMENTS_KEY);
        if (raw instanceof List<?> list) {
            List<String> segments = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    segments.add(String.valueOf(item));
                }
            }
            if (!segments.isEmpty()) {
                return segments;
            }
        }
        String identity = config.systemPrompt();
        return identity == null || identity.isBlank() ? List.of() : List.of(identity);
    }

    private List<ToolDefinition> toolDefinitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (ToolDescriptor descriptor : toolRegistry.list()) {
            definitions.add(new ToolDefinition(
                    descriptor.name(), descriptor.description(), descriptor.parameters()));
        }
        return definitions;
    }

    private ToolOutcome executeTool(String sessionId, ToolCall call, CancellationToken token) {
        ToolResult result;
        try {
            result = toolRegistry.invoke(call.name(), call.arguments(), token);
        } catch (RuntimeException e) {
            // 工具层的**已知拒绝**（未知工具 / 未启用 / 未获授权）与 ToolResult.failure 是同一类：
            // 都是预期内的业务结果，不是故障（见 LoggingDesign.md 第 5 节）。因此：
            //   - 记 INFO 且不带堆栈。Console appender 的阈值是 ERROR，记 ERROR 会把整段堆栈
            //     打到 stderr，正好违背「不打扰对话」的既定设计：模型偶尔叫错工具名，
            //     用户就会在终端刷到一大段栈；
            //   - 回灌给模型的文本也只给原因。这里的原因本身已自解释（「未知工具: x」），
            //     堆栈只会白占 token。
            // 只有**未预期**的异常才记 ERROR 并连堆栈：那时堆栈既是排障依据，也是给模型的线索。
            boolean businessOutcome = e instanceof ToolExecutionException;
            String detail = Exceptions.message(e);
            if (businessOutcome) {
                LOG.info("工具未执行 session={} tool={} args={} 原因={}", sessionId, call.name(),
                        call.arguments(), detail);
            } else {
                LOG.error("工具执行异常 session={} tool={} args={}", sessionId, call.name(),
                        call.arguments(), e);
                detail = detail + System.lineSeparator() + stackTrace(e);
            }
            result = ToolResult.failure(detail);
        }
        if (result.success()) {
            LOG.info("工具执行成功 session={} tool={} args={} 输出={} 字符",
                    sessionId, call.name(), call.arguments(),
                    result.output() == null ? 0 : String.valueOf(result.output()).length());
            LOG.debug("工具输出详情 tool={} output={}", call.name(), result.output());
        } else {
            // 工具失败属于预期业务结果（如用户拒绝授权、参数不合法），
            // 记 INFO 而非 WARN：console appender 只输出 ERROR，避免打断对话
            LOG.info("工具执行失败 session={} tool={} args={} 错误={}",
                    sessionId, call.name(), call.arguments(), result.error());
        }
        String content = result.success()
                ? String.valueOf(result.output())
                : "ERROR: " + result.error();
        // 成败单独带出去：展示层要按成败着色，而从 "ERROR: " 前缀反推既脆弱又会被文案改动破坏
        return new ToolOutcome(new ChatMessage(Role.TOOL, content, List.of(), call.id(), null),
                result.success(), result.success() ? result.output() : result.error());
    }

    /**
     * 工具执行产物。
     *
     * @param message 回灌给模型的消息
     * @param success 是否成功
     * @param display 供人阅读的结果（成功为输出，失败为错误信息）
     */
    private record ToolOutcome(ChatMessage message, boolean success, Object display) {
    }

    private static Map<String, Object> extensions(SessionConfig config) {
        if (config == null) {
            return Map.of();
        }
        Map<String, Object> values = config.extras() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(config.extras());
        // 会话级档位通过扩展参数传给 LLM 客户端解析（无会话级显式模型名时生效）
        if (config.tier() != null && !config.tier().isBlank()) {
            values.put("Tier", config.tier());
        }
        return values;
    }

    /**
     * 解析本轮实际使用的模型。
     *
     * <p>优先级：会话级显式模型名 → 会话级档位（交由 LLM 客户端解析，此处返回
     * {@code null}）→ 运行时默认模型。</p>
     *
     * @param config 会话配置
     * @param model  运行时默认模型
     * @return 模型名；返回 {@code null} 表示由 LLM 客户端按档位解析
     */
    private static String effectiveModel(SessionConfig config, String model) {
        if (config == null) {
            return model;
        }
        if (config.model() != null && !config.model().isBlank()) {
            return config.model();
        }
        if (config.tier() != null && !config.tier().isBlank()) {
            return null;
        }
        return model;
    }

    private static void accumulate(Map<Integer, AccumulatedToolCall> toolCalls, ToolCallDelta delta) {
        if (delta == null) {
            return;
        }
        AccumulatedToolCall acc = toolCalls.computeIfAbsent(delta.index(), k -> new AccumulatedToolCall());
        if (delta.id() != null) {
            acc.id = delta.id();
        }
        if (delta.name() != null) {
            acc.name = delta.name();
        }
        if (delta.argumentsDelta() != null) {
            acc.arguments.append(delta.argumentsDelta());
        }
    }

    private static List<ToolCall> toToolCalls(Map<Integer, AccumulatedToolCall> toolCalls) {
        List<ToolCall> result = new ArrayList<>();
        for (AccumulatedToolCall acc : toolCalls.values()) {
            result.add(new ToolCall(acc.id, acc.name, parseArguments(acc.arguments.toString())));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return (Map<String, Object>) JSON.readValue(json, Map.class);
        } catch (RuntimeException e) {
            return Map.of("_raw", json);
        }
    }

    /**
     * 累积中的工具调用。
     *
     * @since 0.1.0
     */
    private static final class AccumulatedToolCall {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }
}
