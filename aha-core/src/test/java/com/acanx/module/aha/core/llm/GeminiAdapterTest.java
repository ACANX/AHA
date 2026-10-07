package com.acanx.module.aha.core.llm;

import com.acanx.module.aha.core.llm.gemini.GeminiAdapter;
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
 * {@link GeminiAdapter} 测试。
 *
 * @since 0.1.0
 */
class GeminiAdapterTest {

    private final GeminiAdapter adapter = new GeminiAdapter();

    private static String fixture(String name) throws IOException {
        return Files.readString(Path.of("src/test/resources/Fixtures", name));
    }

    @Test
    void buildsContentsAndSystemInstruction() {
        ChatRequest request = new ChatRequest("gemini-2.5-flash",
                List.of(ChatMessage.text(Role.SYSTEM, "be brief"),
                        ChatMessage.text(Role.USER, "hi")),
                List.of(), 0.0, 256, false, Map.of());

        String json = adapter.convertRequest(request, null);

        assertThat(json).contains("\"role\":\"user\"")
                .contains("\"systemInstruction\"")
                .contains("\"maxOutputTokens\":256");
    }

    @Test
    void eachSystemSegmentBecomesItsOwnPart() {
        ChatRequest request = new ChatRequest("gemini-3.5-flash",
                List.of(ChatMessage.text(Role.SYSTEM, "片段1"),
                        ChatMessage.text(Role.SYSTEM, "片段2"),
                        ChatMessage.text(Role.USER, "hi")),
                List.of(), 0.0, 256, false, Map.of());

        String json = adapter.convertRequest(request, null);

        // 每个身份片段各占一个 part，不拼成一段——拼了边界就永久丢失
        assertThat(json).contains("\"text\":\"片段1\"").contains("\"text\":\"片段2\"")
                .contains("systemInstruction");
    }

    @Test
    void parsesCandidateResponse() throws IOException {
        ChatResponse response = adapter.convertResponse(fixture("GeminiChatResponse.json"), null);

        assertThat(response.choices().get(0).message().content()).isEqualTo("Hello from Gemini");
        assertThat(response.usage().promptTokens()).isEqualTo(10);
    }

    @Test
    void parsesStreamTextDelta() throws IOException {
        String events = fixture("GeminiStreamEvents.json");
        StreamEvent event = adapter.convertStreamEvent(
                "data: {\"candidates\":[{\"content\":{\"role\":\"model\","
                        + "\"parts\":[{\"text\":\"Hello\"}]}}]}", null);

        assertThat(events).isNotBlank();
        assertThat(event.type()).isEqualTo(StreamEventType.CONTENT_DELTA);
        assertThat(event.contentDelta()).isEqualTo("Hello");
    }
}
