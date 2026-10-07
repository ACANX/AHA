package com.acanx.module.aha.core.llm.gemini;

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
import java.util.Locale;
import java.util.Map;

/**
 * Google Gemini generateContent 适配器。
 *
 * <p>关键转换：messages[] 转 contents[]（role 为 user / model）；
 * system 转 systemInstruction；tools[] 转 functionDeclarations[]。</p>
 *
 * @since 0.1.0
 */
public final class GeminiAdapter implements LlmProviderAdapter {

    /** 供应商 ID。 */
    public static final String PROVIDER_ID = "gemini";

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
        ArrayNode contents = root.putArray("contents");
        // 同 Anthropic：每个身份片段各占一个 part，不拼成一段
        ArrayNode systemParts = JSON.createArrayNode();

        for (ChatMessage message : safe(ir.messages())) {
            switch (message.role() == null ? Role.USER : message.role()) {
                case SYSTEM -> {
                    // 同 Anthropic：每个身份片段各占一个 part，不拼成一段
                    if (message.content() != null && !message.content().isBlank()) {
                        systemParts.addObject().put("text", message.content());
                    }
                }
                case USER -> contents.add(textContent("user", message.content()));
                case ASSISTANT -> contents.add(textContent("model", message.content()));
                case TOOL -> contents.add(functionResponse(message));
            }
        }

        if (!systemParts.isEmpty()) {
            root.putObject("systemInstruction").set("parts", systemParts);
        }

        if (ir.tools() != null && !ir.tools().isEmpty()) {
            ArrayNode declarations = root.putArray("tools")
                    .addObject()
                    .putArray("functionDeclarations");
            for (ToolDefinition definition : ir.tools()) {
                ObjectNode declaration = declarations.addObject();
                declaration.put("name", definition.name());
                if (definition.description() != null) {
                    declaration.put("description", definition.description());
                }
                declaration.set("parameters", geminiSchema(definition.parameters()));
            }
        }

        ObjectNode generation = root.putObject("generationConfig");
        if (ir.temperature() > 0.0d) {
            generation.put("temperature", ir.temperature());
        }
        if (ir.maxTokens() > 0) {
            generation.put("maxOutputTokens", ir.maxTokens());
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
        String model = text(root, "model");

        StringBuilder content = new StringBuilder();
        List<ToolCall> toolCalls = new ArrayList<>();
        String finishReason = null;

        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && candidates.size() > 0) {
            JsonNode candidate = candidates.get(0);
            finishReason = textOrNull(candidate, "finishReason");
            JsonNode parts = candidate.path("content").path("parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    if (part.path("text").isTextual()) {
                        content.append(part.path("text").asString());
                    }
                    JsonNode call = part.path("functionCall");
                    if (call.isObject()) {
                        toolCalls.add(new ToolCall(null, text(call, "name"), toMap(call.path("args"))));
                    }
                }
            }
        }

        ChatMessage message = new ChatMessage(Role.ASSISTANT, content.toString(), toolCalls, null, null);
        Choice choice = new Choice(0, message, finishReason);
        return new ChatResponse(null, model, List.of(choice), parseUsage(root.path("usageMetadata")), Map.of());
    }

    @Override
    public StreamEvent convertStreamEvent(String rawEvent, ProviderConfig config) {
        String payload = extractData(rawEvent);
        if (payload.isEmpty()) {
            return done(null);
        }
        JsonNode root = JSON.readTree(payload);
        JsonNode candidates = root.path("candidates");
        if (candidates.isArray() && candidates.size() > 0) {
            JsonNode candidate = candidates.get(0);
            String finish = textOrNull(candidate, "finishReason");
            JsonNode parts = candidate.path("content").path("parts");
            if (parts.isArray()) {
                for (JsonNode part : parts) {
                    if (part.path("text").isTextual()) {
                        return new StreamEvent(StreamEventType.CONTENT_DELTA,
                                part.path("text").asString(), null, null, null, finish, null, null);
                    }
                }
            }
            if (finish != null) {
                return done(finish);
            }
        }
        JsonNode usage = root.path("usageMetadata");
        if (usage.isObject()) {
            return new StreamEvent(StreamEventType.USAGE, null, null, null,
                    parseUsage(usage), null, null, null);
        }
        return done(null);
    }

    @Override
    public String buildUrl(ProviderConfig config) {
        return config.baseUrl() + "/v1beta/models/" + config.model() + ":generateContent";
    }

    @Override
    public Map<String, String> buildHeaders(ProviderConfig config) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("Accept", "text/event-stream");
        headers.put("x-goog-api-key", config.apiKey());
        return headers;
    }

    // ------------------------------------------------------------------

    private static ObjectNode textContent(String role, String text) {
        ObjectNode node = JSON.createObjectNode();
        node.put("role", role);
        if (text != null && !text.isEmpty()) {
            node.putArray("parts").addObject().put("text", text);
        } else {
            node.putArray("parts");
        }
        return node;
    }

    private static ObjectNode functionResponse(ChatMessage message) {
        ObjectNode node = JSON.createObjectNode();
        node.put("role", "user");
        ObjectNode part = node.putArray("parts").addObject();
        ObjectNode response = part.putObject("functionResponse");
        response.put("name", message.toolCallId() == null ? "tool" : message.toolCallId());
        response.putObject("response").put("content", message.content() == null ? "" : message.content());
        return node;
    }

    private static ObjectNode geminiSchema(JsonSchema schema) {
        ObjectNode node = JSON.createObjectNode();
        if (schema == null) {
            node.put("type", "OBJECT");
            return node;
        }
        node.put("type", schema.type() == null ? "OBJECT" : schema.type().toUpperCase(Locale.ROOT));
        if (schema.description() != null) {
            node.put("description", schema.description());
        }
        if (schema.properties() != null && !schema.properties().isEmpty()) {
            ObjectNode properties = node.putObject("properties");
            schema.properties().forEach((key, value) -> properties.set(key, geminiSchema(value)));
        }
        if (schema.required() != null && !schema.required().isEmpty()) {
            ArrayNode required = node.putArray("required");
            schema.required().forEach(required::add);
        }
        return node;
    }

    private static Usage parseUsage(JsonNode usage) {
        if (usage == null || !usage.isObject()) {
            return new Usage(0, 0, 0, null);
        }
        int prompt = usage.path("promptTokenCount").asInt(0);
        int completion = usage.path("candidatesTokenCount").asInt(0);
        int total = usage.path("totalTokenCount").asInt(prompt + completion);
        return new Usage(prompt, completion, total, null);
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
}
