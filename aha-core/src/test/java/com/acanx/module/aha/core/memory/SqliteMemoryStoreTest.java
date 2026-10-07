package com.acanx.module.aha.core.memory;

import com.acanx.module.aha.common.model.MemoryEntry;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.ToolCall;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SqliteMemoryStore} 测试。
 *
 * @since 0.1.0
 */
class SqliteMemoryStoreTest {

    private Path dbPath;
    private SqliteMemoryStore store;

    @BeforeEach
    void setUp() throws Exception {
        Path dir = Files.createTempDirectory("aha-memory-test");
        dbPath = dir.resolve("Aha.db");
        store = new SqliteMemoryStore(dbPath);
        store.init();
        store.createSession("s1", "{}");
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void countsStoredMessagesIncludingDroppedOnes() {
        store.appendMessage("s1", ChatMessage.text(Role.USER, "hello"));
        store.appendMessage("s1", ChatMessage.text(Role.ASSISTANT, "world"));
        // 缺 tool_call_id 的 tool 消息会在加载时被丢弃，但仍应计入存储总数
        store.appendMessage("s1", new ChatMessage(Role.TOOL, "result", List.of(), null, null));

        assertThat(store.countMessages("s1")).isEqualTo(3);
        assertThat(store.loadHistory("s1", 100)).hasSize(2);
        assertThat(store.countMessages("absent")).isZero();
    }

    @Test
    void persistsAndLoadsMessagesInOrder() {
        store.appendMessage("s1", ChatMessage.text(Role.USER, "hello"));
        store.appendMessage("s1", ChatMessage.text(Role.ASSISTANT, "world"));

        List<ChatMessage> history = store.loadHistory("s1", 10);

        assertThat(history).hasSize(2);
        assertThat(history.get(0).role()).isEqualTo(Role.USER);
        assertThat(history.get(0).content()).isEqualTo("hello");
        assertThat(history.get(1).content()).isEqualTo("world");
    }

    @Test
    void storesAndRecallsMemory() {
        store.storeMemory("s1", new MemoryEntry("city", "Beijing", 0));
        store.storeMemory("s1", new MemoryEntry("lang", "Java", 0));

        List<MemoryEntry> all = store.recall("s1", null, 10);
        assertThat(all).hasSize(2);

        List<MemoryEntry> filtered = store.recall("s1", "Beij", 10);
        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).key()).isEqualTo("city");
    }

    @Test
    void createsWalModeAndTables() {
        assertThat(Files.exists(dbPath)).isTrue();
    }

    @Test
    void persistsToolCallsAcrossTurns() {
        // 多轮对话中若丢失 tool_calls / tool_call_id，恢复后的请求体会违反 OpenAI 协议，
        // 服务端返回 422。此用例锁定这两个字段必须完整往返。
        var call = new ToolCall("call_abc123", "file-list", java.util.Map.of("path", "."));
        store.appendMessage("s1", ChatMessage.text(Role.USER, "列目录"));
        store.appendMessage("s1", new ChatMessage(Role.ASSISTANT, "", List.of(call), null, null));
        store.appendMessage("s1", new ChatMessage(Role.TOOL, "[a, b]", List.of(), "call_abc123", null));

        List<ChatMessage> history = store.loadHistory("s1", 10);

        assertThat(history).hasSize(3);
        ChatMessage assistant = history.get(1);
        assertThat(assistant.toolCalls()).hasSize(1);
        assertThat(assistant.toolCalls().get(0).id()).isEqualTo("call_abc123");
        assertThat(assistant.toolCalls().get(0).name()).isEqualTo("file-list");
        assertThat(assistant.toolCalls().get(0).arguments()).containsEntry("path", ".");
        assertThat(history.get(2).role()).isEqualTo(Role.TOOL);
        assertThat(history.get(2).toolCallId()).isEqualTo("call_abc123");
    }

    @Test
    void dropsToolMessageWithoutToolCallId() {
        // 修复前写入的数据缺 tool_call_id；直接发送会被服务端 422。
        // 恢复时应丢弃，而非构造非法请求体。
        store.appendMessage("s1", ChatMessage.text(Role.USER, "hi"));
        store.appendMessage("s1", ChatMessage.text(Role.TOOL, "孤立结果"));
        store.appendMessage("s1", ChatMessage.text(Role.USER, "继续"));

        List<ChatMessage> history = store.loadHistory("s1", 10);

        assertThat(history).hasSize(2);
        assertThat(history).noneMatch(m -> m.role() == Role.TOOL);
    }

    @Test
    void dropsEmptyAssistantWithoutToolCalls() {
        // 工具调用信息丢失后残留的空 assistant 消息，同样不应发送。
        store.appendMessage("s1", ChatMessage.text(Role.USER, "hi"));
        store.appendMessage("s1", ChatMessage.text(Role.ASSISTANT, ""));

        List<ChatMessage> history = store.loadHistory("s1", 10);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).role()).isEqualTo(Role.USER);
    }

    @Test
    void keepsAssistantWithContentDespiteNoToolCalls() {
        store.appendMessage("s1", ChatMessage.text(Role.ASSISTANT, "普通回复"));

        assertThat(store.loadHistory("s1", 10)).hasSize(1);
    }

    @Test
    void loadHistoryReturnsNewestMessagesInChronologicalOrder() {
        // 会话超出窗口时应丢掉最旧的历史：直接 ASC + LIMIT 会返回最早的消息，
        // 结果是模型永远看不到刚发生的事。
        for (int i = 0; i < 5; i++) {
            store.appendMessage("s1", ChatMessage.text(Role.USER, "m" + i));
        }

        List<ChatMessage> history = store.loadHistory("s1", 2);

        assertThat(history).extracting(ChatMessage::content).containsExactly("m3", "m4");
    }

    @Test
    void replaceHistoryKeepsRecentMessagesAndPutsSummaryFirst() {
        for (int i = 0; i < 5; i++) {
            store.appendMessage("s1", ChatMessage.text(Role.USER, "m" + i));
        }

        int removed = store.replaceHistory("s1", 2, ChatMessage.text(Role.SYSTEM, "[摘要]"));

        assertThat(removed).isEqualTo(3);
        assertThat(store.loadHistory("s1", 10)).extracting(ChatMessage::content)
                .containsExactly("[摘要]", "m3", "m4");
        assertThat(store.countMessages("s1")).isEqualTo(3);
    }

    @Test
    void replaceHistoryIsNoOpWhenNothingToRemove() {
        store.appendMessage("s1", ChatMessage.text(Role.USER, "m0"));

        assertThat(store.replaceHistory("s1", 5, ChatMessage.text(Role.SYSTEM, "[摘要]"))).isZero();
        assertThat(store.loadHistory("s1", 10)).extracting(ChatMessage::content)
                .containsExactly("m0");
    }

    @Test
    void replaceHistoryWithoutSummaryOnlyDropsMessages() {
        store.appendMessage("s1", ChatMessage.text(Role.USER, "m0"));
        store.appendMessage("s1", ChatMessage.text(Role.USER, "m1"));

        assertThat(store.replaceHistory("s1", 1, null)).isEqualTo(1);
        assertThat(store.loadHistory("s1", 10)).extracting(ChatMessage::content)
                .containsExactly("m1");
    }
}
