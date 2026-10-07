package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.LlmConfig;
import com.acanx.module.aha.core.config.MemoryConfig;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.config.RateLimitConfig;
import com.acanx.module.aha.core.llm.LlmClient;
import com.acanx.module.aha.core.llm.StreamEventListener;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;
import com.acanx.module.aha.core.llm.protocol.Choice;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.Usage;
import com.acanx.module.aha.core.service.LocalAgentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code /compact} 与 {@code /autocompact} 测试。
 *
 * <p>用真实 {@link LocalAgentService}（临时 SQLite）搭配脚本化 LLM，因此既能验证
 * 消息确实被替换、阈值确实落了盘，又不需要网络。</p>
 *
 * @since 0.1.0
 */
class CompactCommandsTest {

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private StubLlm llm;
    private LocalAgentService service;
    private AhaConfig config;

    @BeforeEach
    void setUp() {
        llm = new StubLlm();
        config = new AhaConfig(
                new LlmConfig("DeepSeek",
                        Map.of("DeepSeek", new ProviderConfig("openai-compatible",
                                "https://example.com/v1", "${AHA_API_KEY_X}", "deepseek-chat",
                                60, 2, new RateLimitConfig(10, 0), Map.of())),
                        null, null),
                new MemoryConfig("sqlite", tempDir.resolve("s.db").toString(), 50),
                null, null, null, null);
        service = new LocalAgentService(config, llm, null, tempDir.resolve("s.db"));
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private SessionContext context(String sessionId) {
        return new SessionContext(sessionId, service, config,
                new PrintStream(buffer, true, StandardCharsets.UTF_8), "内置默认身份", 0);
    }

    private SessionCommand.Outcome dispatch(String sessionId, String input) {
        return new SessionCommandRegistry().dispatch(context(sessionId), input).orElseThrow();
    }

    private String sessionWithMessages(int count) {
        String sessionId = service.createSession(new SessionConfig("deepseek-chat", "你是 AHA。", Map.of()));
        for (int i = 0; i < count; i++) {
            service.memoryStore().appendMessage(sessionId,
                    ChatMessage.text(i % 2 == 0 ? Role.USER : Role.ASSISTANT, "消息" + i));
        }
        return sessionId;
    }

    // ── /compact ────────────────────────────────────────────────────────

    @Test
    void compactReplacesOlderMessagesWithSummary() {
        llm.reply("用户要求实现压缩；已确认保留最近若干条消息。");
        String sessionId = sessionWithMessages(8);

        dispatch(sessionId, "/compact 2");

        assertThat(output())
                .contains("上下文已压缩")
                .contains("8 条 → 3 条")
                .contains("摘要 1 条 + 保留 2 条")
                .contains("已作为系统消息写回历史开头")
                .contains("用户要求实现压缩");

        List<ChatMessage> history = service.memoryStore().loadHistory(sessionId, 50);
        assertThat(history).hasSize(3);
        assertThat(history.get(0).role()).isEqualTo(Role.SYSTEM);
        assertThat(history.get(0).content()).contains("[此前对话摘要]");
        assertThat(history.get(1).content()).isEqualTo("消息6");
        assertThat(history.get(2).content()).isEqualTo("消息7");
    }

    @Test
    void compactSendsTranscriptWithoutTools() {
        llm.reply("摘要");
        String sessionId = sessionWithMessages(4);

        dispatch(sessionId, "/compact 1");

        assertThat(llm.requests).hasSize(1);
        ChatRequest request = llm.requests.get(0);
        assertThat(request.tools()).isEmpty();
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).role()).isEqualTo(Role.SYSTEM);
        assertThat(request.messages().get(1).content())
                .contains("[用户] 消息0")
                .contains("[助手] 消息1");
    }

    @Test
    void compactIsNoopWhenHistoryIsShort() {
        String sessionId = sessionWithMessages(3);

        dispatch(sessionId, "/compact");

        assertThat(output()).contains("无需压缩").contains("未超过保留条数");
        assertThat(service.memoryStore().countMessages(sessionId)).isEqualTo(3);
        assertThat(llm.requests).isEmpty();
    }

    @Test
    void compactRejectsInvalidKeepCount() {
        String sessionId = sessionWithMessages(4);

        dispatch(sessionId, "/compact abc");
        assertThat(output()).contains("[error]").contains("用法 /compact");

        dispatch(sessionId, "/compact 0");
        assertThat(output()).contains("保留条数必须是正整数");
    }

    @Test
    void compactKeepsHistoryWhenSummaryFails() {
        String sessionId = sessionWithMessages(8);

        dispatch(sessionId, "/compact 2");

        assertThat(output()).contains("[error]").contains("历史未改动");
        assertThat(service.memoryStore().countMessages(sessionId)).isEqualTo(8);
    }

    @Test
    void compactKeepsHistoryWhenSummaryIsBlank() {
        llm.reply("   ");
        String sessionId = sessionWithMessages(8);

        dispatch(sessionId, "/compact 2");

        assertThat(output()).contains("[error]").contains("未返回摘要内容");
        assertThat(service.memoryStore().countMessages(sessionId)).isEqualTo(8);
    }

    // ── /autocompact ────────────────────────────────────────────────────

    @Test
    void autoCompactWithoutArgsShowsStatus() {
        String sessionId = sessionWithMessages(2);

        dispatch(sessionId, "/autocompact");

        assertThat(output()).contains("自动压缩").contains("已关闭").contains("当前")
                .contains("tokens").contains("/autocompact 285k");
    }

    @Test
    void autoCompactSetsThresholdAndPersistsIt() {
        String sessionId = sessionWithMessages(2);

        dispatch(sessionId, "/autocompact 285k");

        assertThat(output()).contains("自动压缩已开启").contains("285000 tokens")
                .contains("保留").contains("/autocompact off");
        assertThat(service.sessionManager().get(sessionId)).get()
                .extracting(s -> AutoCompactSetting.thresholdOf(s)).isEqualTo(285_000L);

        // 真正的持久化验证：清掉内存缓存后仍能从存储恢复
        service.sessionManager().close(sessionId);
        assertThat(service.sessionManager().get(sessionId)).get()
                .extracting(s -> AutoCompactSetting.thresholdOf(s)).isEqualTo(285_000L);
    }

    @Test
    void autoCompactOffRemovesThreshold() {
        String sessionId = sessionWithMessages(2);
        dispatch(sessionId, "/autocompact 285k");

        dispatch(sessionId, "/autocompact off");

        assertThat(output()).contains("自动压缩已关闭");
        SessionConfig session = service.sessionManager().get(sessionId).orElseThrow();
        assertThat(AutoCompactSetting.thresholdOf(session)).isZero();
        assertThat(session.extras()).doesNotContainKey(AutoCompactSetting.KEY);
        // 身份与模型不受影响
        assertThat(session.systemPrompt()).isEqualTo("你是 AHA。");
    }

    @Test
    void autoCompactRejectsInvalidInput() {
        String sessionId = sessionWithMessages(2);

        dispatch(sessionId, "/autocompact abc");
        assertThat(output()).contains("无法识别").contains("例如 /autocompact 285k");

        dispatch(sessionId, "/autocompact 285 k");
        assertThat(output()).contains("不支持空格分隔");
    }

    // ── 自动触发 ────────────────────────────────────────────────────────

    @Test
    void autoCompactorTriggersOnlyWhenAboveThreshold() {
        llm.reply("摘要");
        // 消息数需超过默认保留条数（10），否则压缩本身就是空操作
        String sessionId = sessionWithMessages(14);
        SessionCommandRegistry registry = new SessionCommandRegistry();
        SessionContext context = context(sessionId);

        // 阈值远高于当前用量：不触发
        service.sessionManager().update(sessionId,
                AutoCompactSetting.withThreshold(service.sessionManager().get(sessionId).orElseThrow(),
                        1_000_000));
        assertThat(AutoCompactor.runIfNeeded(context, registry)).isFalse();
        assertThat(service.memoryStore().countMessages(sessionId)).isEqualTo(14);

        // 阈值降到 1：触发，并复用 /compact 的输出
        service.sessionManager().update(sessionId,
                AutoCompactSetting.withThreshold(service.sessionManager().get(sessionId).orElseThrow(), 1));
        assertThat(AutoCompactor.runIfNeeded(context, registry)).isTrue();
        assertThat(output()).contains("已达自动压缩阈值").contains("上下文已压缩");
        assertThat(service.memoryStore().countMessages(sessionId)).isEqualTo(11);
    }

    @Test
    void autoCompactorStaysSilentWhenDisabled() {
        String sessionId = sessionWithMessages(8);
        SessionContext context = context(sessionId);

        assertThat(AutoCompactor.runIfNeeded(context, new SessionCommandRegistry())).isFalse();
        assertThat(output()).isEmpty();
    }

    /**
     * 脚本化假 LLM 客户端：按序返回预设回答，并记录收到的请求。
     *
     * @since 0.1.0
     */
    private static final class StubLlm implements LlmClient {

        private final Deque<String> replies = new ArrayDeque<>();

        private final java.util.ArrayList<ChatRequest> requests = new java.util.ArrayList<>();

        /**
         * 入队一条回答。
         *
         * @param content 回答正文
         */
        void reply(String content) {
            replies.add(content);
        }

        @Override
        public CompletableFuture<ChatResponse> chat(ChatRequest request) {
            requests.add(request);
            String content = replies.poll();
            if (content == null) {
                return CompletableFuture.failedFuture(new IllegalStateException("脚本中无更多回答"));
            }
            return CompletableFuture.completedFuture(new ChatResponse("id", "m",
                    List.of(new Choice(0, ChatMessage.text(Role.ASSISTANT, content), "stop")),
                    new Usage(1, 1, 2, null), Map.of()));
        }

        @Override
        public void streamChat(ChatRequest request, StreamEventListener listener,
                               CancellationToken token) {
            throw new UnsupportedOperationException("本测试不使用流式");
        }
    }
}
