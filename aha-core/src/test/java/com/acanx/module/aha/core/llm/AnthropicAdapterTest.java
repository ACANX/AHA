package com.acanx.module.aha.core.llm;

import com.acanx.module.aha.core.llm.anthropic.AnthropicAdapter;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.StreamEvent;
import com.acanx.module.aha.core.llm.protocol.StreamEventType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AnthropicAdapter} 测试。
 *
 * @since 0.1.0
 */
class AnthropicAdapterTest {

    private final AnthropicAdapter adapter = new AnthropicAdapter();

    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/Fixtures", name));
    }

    @Test
    void eachSystemSegmentBecomesItsOwnBlock() {
        ChatRequest request = new ChatRequest("claude-sonnet-5-1",
                List.of(ChatMessage.text(Role.SYSTEM, "用户级约定"),
                        ChatMessage.text(Role.SYSTEM, "项目级约定"),
                        ChatMessage.text(Role.USER, "hi")),
                List.of(), 0.0, 256, false, Map.of());

        String json = adapter.convertRequest(request, null);

        assertThat(json).contains("\"text\":\"用户级约定\"").contains("\"text\":\"项目级约定\"");
    }

    @Test
    void extractsSystemToTopLevel() {
        ChatRequest request = new ChatRequest("claude-sonnet-5-1",
                List.of(ChatMessage.text(Role.SYSTEM, "be brief"),
                        ChatMessage.text(Role.USER, "hi")),
                List.of(), 0.0, 256, false, Map.of());

        String json = adapter.convertRequest(request, null);

        // system 是**块数组**而不是拼接字符串：每个身份片段各占一个 block，
        // 拆开才能让模型看出内容来自哪个文件
        assertThat(json).contains("\"system\":[{\"type\":\"text\",\"text\":\"be brief\"}]")
                .contains("\"role\":\"user\"")
                .contains("\"max_tokens\":256");
    }

    @Test
    void parsesTextResponse() throws IOException {
        ChatResponse response = adapter.convertResponse(fixture("AnthropicChatResponse.json"), null);

        assertThat(response.choices().get(0).message().content()).isEqualTo("Hello from Anthropic");
        assertThat(response.usage().promptTokens()).isEqualTo(10);
        assertThat(response.usage().completionTokens()).isEqualTo(5);
    }

    @Test
    void parsesStreamTextDelta() throws IOException {
        String events = fixture("AnthropicStreamEvents.json");
        // fixture 是数组，取第一个元素对应的 data
        StreamEvent event = adapter.convertStreamEvent(
                "data: {\"type\":\"content_block_delta\",\"index\":0,"
                        + "\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}", null);

        assertThat(events).isNotBlank();
        assertThat(event.type()).isEqualTo(StreamEventType.CONTENT_DELTA);
        assertThat(event.contentDelta()).isEqualTo("Hello");
    }
}
