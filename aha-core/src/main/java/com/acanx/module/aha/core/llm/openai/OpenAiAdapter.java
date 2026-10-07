package com.acanx.module.aha.core.llm.openai;

import com.acanx.module.aha.common.tool.JsonSchema;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.llm.LlmProviderAdapter;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;
import com.acanx.module.aha.core.llm.protocol.Choice;
import com.acanx.module.aha.core.llm.protocol.IrVersion;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.StreamEvent;
import com.acanx.module.aha.core.llm.protocol.StreamEventType;
import com.acanx.module.aha.core.llm.protocol.ToolCall;
import com.acanx.module.aha.core.llm.protocol.ToolCallDelta;
import com.acanx.module.aha.core.llm.protocol.ToolDefinition;
import com.acanx.module.aha.core.llm.protocol.Usage;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容适配器。
 *
 * <p>覆盖 OpenAI、DeepSeek、智谱 GLM、通义千问、Kimi、Groq、Ollama、vLLM，
 * 以及 sub2api / new-api 等中转站。转换逻辑为显式转换，而非直接序列化。</p>
 *
 * @since 0.1.0
 */
public final class OpenAiAdapter implements LlmProviderAdapter {

    /** 供应商 ID。 */
    public static final String PROVIDER_ID = "openai-compatible";

    /** OpenAI 兼容的对话补全路径。 */
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public String supportedIrVersion() {
        return IrVersion.CURRENT;
    }

    @Override
    public String convertRequest(ChatRequest ir, ProviderConfig config) {
        ObjectNode root = JSON.createObjectNode();
        root.put("model", firstNonNull(ir.model(), config == null ? null : config.model()));

        ArrayNode messages = root.putArray("messages");
        if (ir.messages() != null) {
            for (ChatMessage message : ir.messages()) {
                messages.add(messageToNode(message));
            }
        }

        if (ir.tools() != null && !ir.tools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolDefinition definition : ir.tools()) {
                ObjectNode tool = tools.addObject();
                tool.put("type", "function");
                ObjectNode function = tool.putObject("function");
                function.put("name", definition.name());
                if (definition.description() != null) {
                    function.put("description", definition.description());
                }
                function.set("parameters", schemaToNode(definition.parameters()));
            }
        }

        if (ir.temperature() > 0.0d) {
            root.put("temperature", ir.temperature());
        }
        if (ir.maxTokens() > 0) {
            root.put("max_tokens", ir.maxTokens());
        }
        root.put("stream", ir.stream());

        // 供应商特有参数透传（含中转站自定义字段，如 enable_thinking、stream_options）
        if (ir.extensions() != null) {
            for (Map.Entry<String, Object> entry : ir.extensions().entrySet()) {
                root.set(entry.getKey(), JSON.valueToTree(entry.getValue()));
            }
        }
        return root.toString();
    }

    @Override
    public ChatResponse convertResponse(String rawResponse, ProviderConfig config) {
        JsonNode root = JSON.readTree(rawResponse);
        String id = text(root, "id");
        String model = text(root, "model");

        List<Choice> choices = new ArrayList<>();
        JsonNode choicesNode = root.path("choices");
        if (choicesNode.isArray()) {
            for (JsonNode choiceNode : choicesNode) {
                JsonNode messageNode = choiceNode.path("message");
                ChatMessage message = new ChatMessage(
                        Role.ASSISTANT,
                        messageContent(messageNode),
                        parseToolCalls(messageNode.path("tool_calls")),
                        null,
                        textOrNull(messageNode, "reasoning_content"));
                choices.add(new Choice(
                        choiceNode.path("index").asInt(0),
                        message,
                        textOrNull(choiceNode, "finish_reason")));
            }
        }

        Usage usage = parseUsage(root.path("usage"));
        return new ChatResponse(id, model, choices, usage, Map.of());
    }

    @Override
    public StreamEvent convertStreamEvent(String rawEvent, ProviderConfig config) {
        String payload = rawEvent == null ? "" : rawEvent.trim();
        if (payload.isEmpty()) {
            return done(null, null);
        }
        // 容忍中转站缺失 [DONE]：显式 DONE 标记
        if ("[DONE]".equals(payload)) {
            return done(null, null);
        }
        if (payload.startsWith("data:")) {
            payload = payload.substring("data:".length()).trim();
        }
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return done(null, null);
        }

        JsonNode root = JSON.readTree(payload);

        JsonNode choicesNode = root.path("choices");
        if (choicesNode.isArray() && choicesNode.size() > 0) {
            JsonNode choiceNode = choicesNode.get(0);
            JsonNode delta = choiceNode.path("delta");
            String finish = textOrNull(choiceNode, "finish_reason");

            String content = textOrNull(delta, "content");
            if (content != null && !content.isEmpty()) {
                return new StreamEvent(StreamEventType.CONTENT_DELTA, content, null, null,
                        null, finish, null, null);
            }
            String reasoning = textOrNull(delta, "reasoning_content");
            if (reasoning != null && !reasoning.isEmpty()) {
                return new StreamEvent(StreamEventType.REASONING_DELTA, null, reasoning, null,
                        null, finish, null, null);
            }
            ToolCallDelta deltaCall = parseToolCallDelta(delta.path("tool_calls"));
            if (deltaCall != null) {
                return new StreamEvent(StreamEventType.TOOL_CALL_DELTA, null, null, deltaCall,
                        null, finish, null, null);
            }
            if (finish != null) {
                return done(finish, parseUsage(root.path("usage")));
            }
        }

        JsonNode usageNode = root.path("usage");
        if (usageNode.isObject()) {
            return new StreamEvent(StreamEventType.USAGE, null, null, null,
                    parseUsage(usageNode), null, null, null);
        }
        return done(null, null);
    }

    /**
     * 构建请求 URL。
     *
     * <p>{@code BaseUrl} 按行业惯例视为<b>基础地址</b>（如
     * {@code https://api.openai.com/v1}、{@code https://api.deepseek.com/v1}），
     * 由适配器拼接 {@code /chat/completions}，与 Anthropic / Gemini 适配器一致。</p>
     *
     * <p>为兼容旧的“完整端点”写法，若地址已包含 {@code /chat/completions}
     * 或 {@code /completions} 后缀，则不再拼接。</p>
     *
     * @param config 供应商配置
     * @return 请求 URL
     */
    @Override
    public String buildUrl(ProviderConfig config) {
        String base = config == null ? null : config.baseUrl();
        if (base == null || base.isBlank()) {
            return base;
        }
        String trimmed = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        if (trimmed.endsWith(CHAT_COMPLETIONS_PATH) || trimmed.endsWith("/completions")) {
            return trimmed;
        }
        return trimmed + CHAT_COMPLETIONS_PATH;
    }

    @Override
    public Map<String, String> buildHeaders(ProviderConfig config) {
        String authHeader = "Authorization";
        String authScheme = "Bearer";
        if (config.extra() != null) {
            if (config.extra().get("AuthHeader") != null) {
                authHeader = config.extra().get("AuthHeader").toString();
            }
            if (config.extra().get("AuthScheme") != null) {
                authScheme = config.extra().get("AuthScheme").toString();
            }
        }
        String value = (authScheme == null || authScheme.isBlank())
                ? config.apiKey()
                : authScheme + " " + config.apiKey();

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "text/event-stream");
        headers.put(authHeader, value);
        return headers;
    }

    // ------------------------------------------------------------------
    // 内部辅助
    // ------------------------------------------------------------------

    private static ObjectNode messageToNode(ChatMessage message) {
        ObjectNode node = JSON.createObjectNode();
        node.put("role", roleName(message.role()));
        if (message.content() != null) {
            node.put("content", message.content());
        }
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            ArrayNode calls = node.putArray("tool_calls");
            for (ToolCall call : message.toolCalls()) {
                ObjectNode callNode = calls.addObject();
                callNode.put("id", call.id());
                callNode.put("type", "function");
                ObjectNode function = callNode.putObject("function");
                function.put("name", call.name());
                function.put("arguments", JSON.writeValueAsString(
                        call.arguments() == null ? Map.of() : call.arguments()));
            }
        }
        if (message.toolCallId() != null) {
            node.put("tool_call_id", message.toolCallId());
        }
        return node;
    }

    /**
     * IR 角色映射为 OpenAI 角色。
     *
     * @param role IR 角色
     * @return OpenAI 角色字符串
     */
    public static String roleName(Role role) {
        if (role == null) {
            return "user";
        }
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case TOOL -> "tool";
        };
    }

    /**
     * JsonSchema 转换为 JSON 节点。
     *
     * @param schema Schema
     * @return 节点
     */
    public static ObjectNode schemaToNode(JsonSchema schema) {
        ObjectNode node = JSON.createObjectNode();
        if (schema == null) {
            node.put("type", "object");
            return node;
        }
        node.put("type", schema.type() == null ? "object" : schema.type());
        if (schema.description() != null) {
            node.put("description", schema.description());
        }
        if (schema.properties() != null && !schema.properties().isEmpty()) {
            ObjectNode properties = node.putObject("properties");
            schema.properties().forEach((key, value) -> properties.set(key, schemaToNode(value)));
        }
        if (schema.required() != null && !schema.required().isEmpty()) {
            ArrayNode required = node.putArray("required");
            schema.required().forEach(required::add);
        }
        return node;
    }

    private static List<ToolCall> parseToolCalls(JsonNode array) {
        if (array == null || !array.isArray() || array.size() == 0) {
            return List.of();
        }
        List<ToolCall> calls = new ArrayList<>();
        for (JsonNode node : array) {
            JsonNode function = node.path("function");
            calls.add(new ToolCall(
                    textOrNull(node, "id"),
                    textOrNull(function, "name"),
                    parseArguments(textOrNull(function, "arguments"))));
        }
        return calls;
    }

    private static ToolCallDelta parseToolCallDelta(JsonNode array) {
        if (array == null || !array.isArray() || array.size() == 0) {
            return null;
        }
        JsonNode node = array.get(0);
        JsonNode function = node.path("function");
        return new ToolCallDelta(
                node.path("index").asInt(0),
                textOrNull(node, "id"),
                textOrNull(function, "name"),
                textOrNull(function, "arguments"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseArguments(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return (Map<String, Object>) JSON.readValue(json, Map.class);
        } catch (RuntimeException e) {
            // 中转站可能返回被截断/非标准 JSON，降级保留原文
            return Map.of("_raw", json);
        }
    }

    /**
     * 解析 usage，缺失时以 0 值降级。
     *
     * @param usage usage 节点
     * @return IR Usage
     */
    public static Usage parseUsage(JsonNode usage) {
        if (usage == null || !usage.isObject()) {
            return new Usage(0, 0, 0, null);
        }
        int prompt = usage.path("prompt_tokens").asInt(0);
        int completion = usage.path("completion_tokens").asInt(0);
        int total = usage.path("total_tokens").asInt(prompt + completion);
        JsonNode reasoningNode = usage.path("completion_tokens_details").path("reasoning_tokens");
        Integer reasoning = reasoningNode.isNumber() ? reasoningNode.asInt() : null;
        return new Usage(prompt, completion, total, reasoning);
    }

    private static String messageContent(JsonNode messageNode) {
        JsonNode content = messageNode.path("content");
        if (content.isMissingNode() || content.isNull()) {
            return null;
        }
        if (content.isTextual()) {
            return content.asString();
        }
        // 部分实现返回 content 数组
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : content) {
                JsonNode text = part.path("text");
                if (text.isTextual()) {
                    sb.append(text.asString());
                }
            }
            return sb.toString();
        }
        return content.asString(null);
    }

    private static StreamEvent done(String finishReason, Usage usage) {
        return new StreamEvent(StreamEventType.DONE, null, null, null,
                usage, finishReason, null, null);
    }

    private static String text(JsonNode node, String field) {
        return node.path(field).asString("");
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString(null);
    }

    private static String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }
}
