package com.acanx.module.aha.core.agent;

import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ContextCompactor} 测试。
 *
 * @since 0.1.0
 */
class ContextCompactorTest {

    private final InMemoryMemoryStore store = new InMemoryMemoryStore();

    private final ScriptedLlmClient llm = new ScriptedLlmClient();

    private final ContextCompactor compactor = new ContextCompactor(llm, store);

    /**
     * 造一段交替的用户 / 助手对话。
     *
     * @param count 条数
     * @return 消息列表
     */
    private List<ChatMessage> conversation(int count) {
        List<ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ChatMessage message = ChatMessage.text(i % 2 == 0 ? Role.USER : Role.ASSISTANT, "m" + i);
            messages.add(message);
            store.appendMessage("s1", message);
        }
        return messages;
    }

    @Test
    void replacesOlderMessagesWithSummary() {
        llm.enqueue(ScriptedLlmClient.text("  摘要正文  "));
        conversation(6);

        ContextCompactor.Outcome outcome = compactor.compact(
                "s1", new SessionConfig("session-model", null, Map.of()), "runtime-model", 2);

        assertThat(outcome.changed()).isTrue();
        assertThat(outcome.removed()).isEqualTo(4);
        assertThat(outcome.kept()).isEqualTo(2);
        assertThat(outcome.total()).isEqualTo(3);
        assertThat(outcome.summary()).isEqualTo("摘要正文");
        // 会话固化的模型优先于运行时默认模型
        assertThat(outcome.model()).isEqualTo("session-model");

        List<ChatMessage> history = store.loadHistory("s1", 100);
        assertThat(history).hasSize(3);
        assertThat(history.get(0).role()).isEqualTo(Role.SYSTEM);
        assertThat(history.get(0).content())
                .contains(ContextCompactor.SUMMARY_HEADER)
                .contains("已压缩 4 条")
                .contains("摘要正文");
        assertThat(history.get(1).content()).isEqualTo("m4");
        assertThat(history.get(2).content()).isEqualTo("m5");
    }

    @Test
    void summarisesWithoutToolsAndPassesRoleLabelledTranscript() {
        llm.enqueue(ScriptedLlmClient.text("摘要"));
        conversation(4);

        compactor.compact("s1", new SessionConfig(null, null, Map.of()), "runtime-model", 1);

        List<ChatRequest> requests = llm.requests();
        assertThat(requests).hasSize(1);
        ChatRequest request = requests.get(0);
        assertThat(request.model()).isEqualTo("runtime-model");
        assertThat(request.tools()).isEmpty();
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).role()).isEqualTo(Role.SYSTEM);
        assertThat(request.messages().get(1).content())
                .contains("[用户] m0")
                .contains("[助手] m1");
    }

    @Test
    void isNoOpWhenHistoryFitsWithinKeepCount() {
        conversation(3);

        ContextCompactor.Outcome outcome = compactor.compact(
                "s1", new SessionConfig(null, null, Map.of()), "m", 10);

        assertThat(outcome.changed()).isFalse();
        assertThat(outcome.removed()).isZero();
        assertThat(outcome.kept()).isEqualTo(3);
        assertThat(outcome.total()).isEqualTo(3);
        assertThat(store.loadHistory("s1", 100)).hasSize(3);
        // 无需压缩就不该调用模型
        assertThat(llm.requests()).isEmpty();
    }

    @Test
    void doesNotSplitToolCallFromItsResults() {
        // 以 tool 消息开头的历史会被服务端拒绝，因此切割点必须回退到发起调用的助手消息
        store.appendMessage("s1", ChatMessage.text(Role.USER, "列目录"));
        store.appendMessage("s1", new ChatMessage(Role.ASSISTANT, "",
                List.of(new ToolCall("call_1", "file-list", Map.of("path", "."))), null, null));
        store.appendMessage("s1", new ChatMessage(Role.TOOL, "[a, b]", List.of(), "call_1", null));
        store.appendMessage("s1", ChatMessage.text(Role.ASSISTANT, "目录里有两个文件"));
        llm.enqueue(ScriptedLlmClient.text("摘要"));

        ContextCompactor.Outcome outcome = compactor.compact(
                "s1", new SessionConfig(null, null, Map.of()), "m", 2);

        assertThat(outcome.removed()).isEqualTo(1);
        List<ChatMessage> history = store.loadHistory("s1", 100);
        // 摘要 + 助手(工具调用) + 工具结果 + 助手
        assertThat(history).hasSize(4);
        assertThat(history.get(1).toolCalls()).hasSize(1);
        assertThat(history.get(2).role()).isEqualTo(Role.TOOL);
    }

    @Test
    void keepsHistoryWhenModelCallFails() {
        conversation(6);

        assertThatThrownBy(() -> compactor.compact(
                "s1", new SessionConfig(null, null, Map.of()), "m", 2))
                .isInstanceOf(AhaException.class)
                .hasMessageContaining("历史未改动");

        assertThat(store.loadHistory("s1", 100)).hasSize(6);
    }

    @Test
    void keepsHistoryWhenSummaryIsBlank() {
        llm.enqueue(ScriptedLlmClient.text("   "));
        conversation(6);

        assertThatThrownBy(() -> compactor.compact(
                "s1", new SessionConfig(null, null, Map.of()), "m", 2))
                .isInstanceOf(AhaException.class)
                .hasMessageContaining("未返回摘要内容");

        assertThat(store.loadHistory("s1", 100)).hasSize(6);
    }

    @Test
    void keepsHistoryWhenResponseHasNoChoices() {
        llm.enqueue(new com.acanx.module.aha.core.llm.protocol.ChatResponse(
                "id", "m", List.of(), null, Map.of()));
        conversation(6);

        assertThatThrownBy(() -> compactor.compact(
                "s1", new SessionConfig(null, null, Map.of()), "m", 2))
                .isInstanceOf(AhaException.class);

        assertThat(store.loadHistory("s1", 100)).hasSize(6);
    }

    @Test
    void rendersTranscriptWithRoleLabelsAndToolCalls() {
        String text = ContextCompactor.renderTranscript(List.of(
                ChatMessage.text(Role.USER, "问题"),
                ChatMessage.text(Role.ASSISTANT, "回答"),
                new ChatMessage(Role.SYSTEM, "背景", List.of(), null, null),
                new ChatMessage(Role.TOOL, "结果", List.of(), "call_1", null),
                new ChatMessage(Role.ASSISTANT, null,
                        List.of(new ToolCall("call_2", "file-read", Map.of("path", "/tmp/a"))),
                        null, null)));

        assertThat(text)
                .contains("[用户] 问题")
                .contains("[助手] 回答")
                .contains("[系统] 背景")
                .contains("[工具结果] 结果")
                .contains("调用工具 file-read");
    }

    @Test
    void truncatesOverlongMessagesInTranscript() {
        String huge = "x".repeat(5000);

        String text = ContextCompactor.renderTranscript(List.of(ChatMessage.text(Role.USER, huge)));

        assertThat(text).contains("…（截断）");
        assertThat(text.length()).isLessThan(huge.length());
    }
}
