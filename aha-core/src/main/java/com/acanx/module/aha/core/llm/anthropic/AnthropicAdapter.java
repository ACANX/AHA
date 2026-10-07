package com.acanx.module.aha.core.llm.anthropic;

import com.acanx.module.aha.common.tool.JsonSchema;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.llm.LlmProviderAdapter;
import com.acanx.module.aha.core.llm.openai.OpenAiAdapter;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;
import com.acanx.module.aha.core.llm.protocol.Choice;
import com.acanx.module.aha.core.llm.protocol.IrVersion;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.StreamEvent;
import com.acanx.module.aha.core.llm.protocol.StreamEventType;
import com.acanx.module.aha.core.llm.protocol.ToolCall;
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
 * Anthropic Messages 适配器。
 *
 * <p>关键转换：system 提取为顶层字段；content 字符串转 block 数组；
 * tool_calls 转 tool_use；role=tool 转 tool_result（置于 user content 最前）。</p>
 *
 * @since 0.1.0
 */
public final class AnthropicAdapter implements LlmProviderAdapter {

    /** 供应商 ID。 */
    public static final String PROVIDER_ID = "anthropic";

    private static final String API_VERSION = "2023-06-01";

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

        // system 用**块数组**而不是拼接字符串：每个身份片段各占一个 text block，
        // 模型才能看出内容来自哪个文件。拼成一段后边界就永久丢失了。
        ArrayNode system = JSON.createArrayNode();
        ArrayNode messages = root.putArray("messages");

        for (ChatMessage message : safe(ir.messages())) {
            switch (message.role() == null ? Role.USER : message.role()) {
                case SYSTEM -> {
                    // 每个身份片段各占一个 text block：Anthropic 的 system 支持块数组，
                    // 拆开才能让模型看出内容来自哪个文件（拼成一段后边界永久丢失）
                    if (message.content() != null && !message.content().isBlank()) {
                        system.addObject().put("type", "text").put("text", message.content());
                    }
                }
                case USER -> messages.add(userMessage(message));
                case ASSISTANT -> messages.add(assistantMessage(message));
                case TOOL -> messages.add(toolResultMessage(message));
            }
        }

        if (!system.isEmpty()) {
            root.set("system", system);
        }

        if (ir.tools() != null && !ir.tools().isEmpty()) {
            ArrayNode tools = root.putArray("tools");
            for (ToolDefinition definition : ir.tools()) {
                ObjectNode tool = tools.addObject();
                tool.put("name", definition.name());
                if (definition.description() != null) {
                    tool.put("description", definition.description());
                }
                tool.set("input_schema", OpenAiAdapter.schemaToNode(definition.parameters()));
            }
        }

        root.put("max_tokens", ir.maxTokens() > 0 ? ir.maxTokens() : 4096);
        if (ir.temperature() > 0.0d) {
            root.put("temperature", ir.temperature());
        }
        if (ir.stream()) {
            root.put("stream", true);
        }
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

        StringBuilder content = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        JsonNode blocks = root.path("content");
        if (blocks.isArray()) {
            for (JsonNode block : blocks) {
                String type = text(block, "type");
                if ("text".equals(type)) {
                    content.append(text(block, "text"));
                } else if ("tool_use".equals(type)) {
                    toolCalls.add(new ToolCall(
                            text(block, "id"),
                            text(block, "name"),
                            toMap(block.path("input"))));
                }
            }
        }

        ChatMessage message = new ChatMessage(Role.ASSISTANT, content.toString(), toolCalls, null, null);
        Choice choice = new Choice(0, message, textOrNull(root, "stop_reason"));
        return new ChatResponse(id, model, List.of(choice), parseUsage(root.path("usage")), Map.of());
    }

    @Override
    public StreamEvent convertStreamEvent(String rawEvent, ProviderConfig config) {
        String payload = extractData(rawEvent);
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return done(null);
        }
        JsonNode root = JSON.readTree(payload);
        String type = text(root, "type");
        return switch (type) {
            case "content_block_delta" -> contentDelta(root.path("delta"));
            case "message_delta" -> done(textOrNull(root.path("delta"), "stop_reason"));
            case "message_stop" -> done(null);
            case "error" -> new StreamEvent(StreamEventType.ERROR, null, null, null, null, null,
                    "ANTHROPIC_ERROR", text(root.path("error"), "message"));
            default -> done(null);
        };
    }

    @Override
    public String buildUrl(ProviderConfig config) {
        String base = config.baseUrl();
        if (base.endsWith("/v1")) {
            return base + "/messages";
        }
        return base + "/v1/messages";
    }

    @Override
    public Map<String, String> buildHeaders(ProviderConfig config) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "text/event-stream");
        headers.put("x-api-key", config.apiKey());
        headers.put("anthropic-version", API_VERSION);
        return headers;
    }

    // ------------------------------------------------------------------

    private static ObjectNode userMessage(ChatMessage message) {
        ObjectNode node = JSON.createObjectNode();
        node.put("role", "user");
        ArrayNode content = node.putArray("content");
        if (message.content() != null && !message.content().isEmpty()) {
            content.addObject().put("type", "text").put("text", message.content());
        }
        return node;
    }

    private static ObjectNode assistantMessage(ChatMessage message) {
        ObjectNode node = JSON.createObjectNode();
        node.put("role", "assistant");
        ArrayNode content = node.putArray("content");
        if (message.content() != null && !message.content().isEmpty()) {
            content.addObject().put("type", "text").put("text", message.content());
        }
        if (message.toolCalls() != null) {
            for (ToolCall call : message.toolCalls()) {
                ObjectNode use = content.addObject();
                use.put("type", "tool_use");
                use.put("id", call.id());
                use.put("name", call.name());
                use.set("input", JSON.valueToTree(call.arguments() == null ? Map.of() : call.arguments()));
            }
        }
        return node;
    }

    private static ObjectNode toolResultMessage(ChatMessage message) {
        ObjectNode node = JSON.createObjectNode();
        node.put("role", "user");
        ArrayNode content = node.putArray("content");
        ObjectNode result = content.addObject();
        result.put("type", "tool_result");
        result.put("tool_use_id", message.toolCallId());
        result.put("content", message.content() == null ? "" : message.content());
        return node;
    }

    private static StreamEvent contentDelta(JsonNode delta) {
        String type = text(delta, "type");
        String text = textOrNull(delta, "text");
        if ("thinking_delta".equals(type)) {
            return new StreamEvent(StreamEventType.REASONING_DELTA, null,
                    textOrNull(delta, "thinking"), null, null, null, null, null);
        }
        if (text != null) {
            return new StreamEvent(StreamEventType.CONTENT_DELTA, text, null, null,
                    null, null, null, null);
        }
        return done(null);
    }

    private static Usage parseUsage(JsonNode usage) {
        if (usage == null || !usage.isObject()) {
            return new Usage(0, 0, 0, null);
        }
        int input = usage.path("input_tokens").asInt(0);
        int output = usage.path("output_tokens").asInt(0);
        return new Usage(input, output, input + output, null);
    }

    private static String extractData(String rawEvent) {
        if (rawEvent == null) {
            return "";
        }
        for (String line : rawEvent.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("data:")) {
                return trimmed.substring("data:".length()).trim();
            }
        }
        return rawEvent.trim();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return JSON.convertValue(node, Map.class);
    }

    private static StreamEvent done(String finishReason) {
        return new StreamEvent(StreamEventType.DONE, null, null, null, null, finishReason, null, null);
    }

    private static List<ChatMessage> safe(List<ChatMessage> messages) {
        return messages == null ? List.of() : messages;
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
