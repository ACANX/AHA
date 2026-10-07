package com.acanx.module.aha.core.llm;

import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.llm.openai.OpenAiAdapter;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.StreamEvent;
import com.acanx.module.aha.core.llm.protocol.StreamEventType;
import com.acanx.module.aha.core.llm.protocol.ToolCall;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OpenAiAdapter} 测试。
 *
 * @since 0.1.0
 */
class OpenAiAdapterTest {

    private final OpenAiAdapter adapter = new OpenAiAdapter();

    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/Fixtures", name));
    }

    @Test
    void convertsRequestToOpenAiFormat() {
        ChatRequest request = new ChatRequest("gpt-4o",
                List.of(ChatMessage.text(Role.USER, "hi")),
                List.of(), 0.7, 100, false, Map.of());

        String json = adapter.convertRequest(request, null);

        assertThat(json)
                .contains("\"model\":\"gpt-4o\"")
                .contains("\"role\":\"user\"")
                .contains("\"max_tokens\":100")
                .contains("\"stream\":false");
    }

    @Test
    void parsesChatResponse() throws IOException {
        ChatResponse response = adapter.convertResponse(fixture("OpenAiChatResponse.json"), null);

        assertThat(response.choices()).hasSize(1);
        assertThat(response.choices().get(0).message().content()).isEqualTo("Hello from OpenAI");
        assertThat(response.usage().promptTokens()).isEqualTo(10);
        assertThat(response.usage().completionTokens()).isEqualTo(5);
    }

    @Test
    void mapsSystemAndToolRolesOnRequest() {
        ChatMessage assistant = new ChatMessage(Role.ASSISTANT, null,
                List.of(new ToolCall("call_1", "file-read", Map.of("path", "/tmp/a"))), null, null);
        ChatRequest request = new ChatRequest("gpt-4o",
                List.of(ChatMessage.text(Role.SYSTEM, "sys"), assistant,
                        new ChatMessage(Role.TOOL, "content", List.of(), "call_1", null)),
                List.of(), 0.0, 0, false, Map.of());

        String json = adapter.convertRequest(request, null);

        assertThat(json).contains("\"role\":\"system\"")
                .contains("\"role\":\"assistant\"")
                .contains("\"tool_calls\"")
                .contains("\"role\":\"tool\"")
                .contains("\"tool_call_id\":\"call_1\"");
    }

    @Test
    void parsesDoneMarkerAndMissingDone() {
        assertThat(adapter.convertStreamEvent("[DONE]", null).type())
                .isEqualTo(StreamEventType.DONE);
        // 中转站可能缺失 [DONE]，finish_reason 收尾
        StreamEvent finish = adapter.convertStreamEvent(
                "{\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}", null);
        assertThat(finish.type()).isEqualTo(StreamEventType.DONE);
        assertThat(finish.finishReason()).isEqualTo("stop");
    }

    @Test
    void parsesContentAndReasoningDelta() {
        StreamEvent content = adapter.convertStreamEvent(
                "{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hi\"}}]}", null);
        assertThat(content.type()).isEqualTo(StreamEventType.CONTENT_DELTA);
        assertThat(content.contentDelta()).isEqualTo("Hi");

        StreamEvent reasoning = adapter.convertStreamEvent(
                "{\"choices\":[{\"index\":0,\"delta\":{\"reasoning_content\":\"think\"}}]}", null);
        assertThat(reasoning.type()).isEqualTo(StreamEventType.REASONING_DELTA);
        assertThat(reasoning.reasoningDelta()).isEqualTo("think");
    }

    @Test
    void defaultsUsageWhenMissing() {
        ChatResponse response = adapter.convertResponse(
                "{\"id\":\"x\",\"model\":\"m\",\"choices\":[]}", null);
        assertThat(response.usage().totalTokens()).isZero();
    }

    private static ProviderConfig provider(String baseUrl) {
        return new ProviderConfig("openai-compatible", baseUrl, "k", "m", 120, 3, null, Map.of());
    }

    @Test
    void buildUrlAppendsChatCompletions() {
        // 回归：BaseUrl 按惯例只是基础地址，必须由适配器拼接端点路径，
        // 否则请求会打到 /v1 而返回 404（内置 openai-compatible 供应商全部不可用）
        assertThat(adapter.buildUrl(provider("https://api.openai.com/v1")))
                .isEqualTo("https://api.openai.com/v1/chat/completions");
        assertThat(adapter.buildUrl(provider("https://api.deepseek.com/v1")))
                .isEqualTo("https://api.deepseek.com/v1/chat/completions");
        // 中转站常见形式：域名 + /v1
        assertThat(adapter.buildUrl(provider("https://proxy.example/v1")))
                .isEqualTo("https://proxy.example/v1/chat/completions");
    }

    @Test
    void buildUrlToleratesTrailingSlashAndFullEndpoint() {
        assertThat(adapter.buildUrl(provider("https://api.deepseek.com/v1/")))
                .isEqualTo("https://api.deepseek.com/v1/chat/completions");
        // 已是完整端点时不重复拼接
        assertThat(adapter.buildUrl(provider("https://x/v1/chat/completions")))
                .isEqualTo("https://x/v1/chat/completions");
        assertThat(adapter.buildUrl(provider("https://x/v1/completions")))
                .isEqualTo("https://x/v1/completions");
        assertThat(adapter.buildUrl(provider(null))).isNull();
    }
}
