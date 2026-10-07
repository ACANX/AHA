package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.LlmConfig;
import com.acanx.module.aha.core.config.MemoryConfig;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.service.LocalAgentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ContextSessionCommand} 测试。
 *
 * <p>用真实 {@link LocalAgentService}（临时 SQLite、不发网络请求）验证上下文摘要，
 * 并用纯函数验证 Token 估算。</p>
 *
 * @since 0.1.0
 */
class ContextSessionCommandTest {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void printsContextSummary(@TempDir Path tempDir) {
        AhaConfig config = new AhaConfig(
                new LlmConfig("DeepSeek", null, null, null),
                new MemoryConfig("sqlite", tempDir.resolve("ctx.db").toString(), 50),
                null, null, null, null);
        LocalAgentService service = new LocalAgentService(config);
        try {
            String sessionId = service.createSession(
                    new SessionConfig("deepseek-chat", "你是 AHA。", Map.of()));
            service.memoryStore().appendMessage(sessionId, ChatMessage.text(Role.USER, "你好"));

            SessionContext context = new SessionContext(sessionId, service, config,
                    new PrintStream(buffer, true, StandardCharsets.UTF_8), "/tmp/AHA.md（6 字符）", 6);

            SessionCommand.Outcome outcome = new SessionCommandRegistry()
                    .dispatch(context, "/context").orElseThrow();

            assertThat(outcome).isEqualTo(SessionCommand.Outcome.CONTINUE);
            assertThat(output())
                    .contains("上下文")
                    .contains(sessionId)
                    .contains("deepseek-chat（DeepSeek）")
                    .contains("窗口上限 50")
                    .contains("1 条")
                    .contains("/tmp/AHA.md（6 字符）")
                    .contains("含系统提示词");
        } finally {
            service.shutdown();
        }
    }

    @Test
    void reportsStoredCountWhenMessagesWereDropped(@TempDir Path tempDir) {
        AhaConfig config = new AhaConfig(
                new LlmConfig("DeepSeek", null, null, null),
                new MemoryConfig("sqlite", tempDir.resolve("ctx.db").toString(), 50),
                null, null, null, null);
        LocalAgentService service = new LocalAgentService(config);
        try {
            String sessionId = service.createSession(new SessionConfig(null, null, Map.of()));
            service.memoryStore().appendMessage(sessionId, ChatMessage.text(Role.USER, "hi"));
            // 缺 tool_call_id 的 tool 消息会在加载时被丢弃，但存储总数仍应包含它
            service.memoryStore().appendMessage(sessionId,
                    new ChatMessage(Role.TOOL, "result", java.util.List.of(), null, null));

            SessionContext context = new SessionContext(sessionId, service, config,
                    new PrintStream(buffer, true, StandardCharsets.UTF_8), null, 0);
            new SessionCommandRegistry().dispatch(context, "/context").orElseThrow();

            assertThat(output()).contains("存储 2 条").contains("1 条在窗口之外或无法完整重建");
            // 未提供身份来源时不应输出该行，且系统提示词未知时需说明
            assertThat(output()).doesNotContain("身份").contains("未含系统提示词");
        } finally {
            service.shutdown();
        }
    }
}
