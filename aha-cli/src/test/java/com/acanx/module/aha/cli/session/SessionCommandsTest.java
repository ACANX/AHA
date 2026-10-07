package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.LlmConfig;
import com.acanx.module.aha.core.config.MemoryConfig;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.config.RateLimitConfig;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.service.LocalAgentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 会话内命令 {@code /model}、{@code /clear}、{@code /tool}、{@code /session} 测试。
 *
 * <p>使用真实 {@link LocalAgentService}（临时 SQLite、不发网络请求），
 * 以便同时验证状态变更是否真正持久化。</p>
 *
 * @since 0.1.0
 */
class SessionCommandsTest {

    @TempDir
    Path tempDir;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private LocalAgentService service;
    private AhaConfig config;
    private SessionConfig initial;

    @BeforeEach
    void setUp() {
        config = new AhaConfig(
                new LlmConfig("DeepSeek",
                        Map.of("DeepSeek", provider("deepseek-chat"),
                                "OpenAI", provider("gpt-4o")),
                        null, null),
                new MemoryConfig("sqlite", tempDir.resolve("s.db").toString(), 50),
                null, null, null, null);
        service = new LocalAgentService(config);
        initial = new SessionConfig("deepseek-chat", "你是 AHA。", Map.of());
    }

    @AfterEach
    void tearDown() {
        if (service != null) {
            service.shutdown();
        }
    }

    private static ProviderConfig provider(String model) {
        return new ProviderConfig("openai-compatible", "https://example.com/v1",
                "${AHA_API_KEY_X}", model, 60, 2, new RateLimitConfig(10, 0), Map.of());
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

    private String newSession() {
        return service.createSession(initial);
    }

    // ── /model ──────────────────────────────────────────────────────────

    @Test
    void modelWithoutArgsShowsCurrentAndAvailableProviders() {
        String sessionId = newSession();
        dispatch(sessionId, "/model");

        assertThat(output())
                .contains("模型")
                .contains("deepseek-chat")
                .contains("DeepSeek")
                .contains("OpenAI")
                .contains("← 当前")
                .contains("aha provider use <id>");
    }

    @Test
    void modelSwitchesSessionModelAndPersists() {
        String sessionId = newSession();
        dispatch(sessionId, "/model deepseek-reasoner");

        assertThat(output()).contains("模型已切换").contains("deepseek-reasoner");
        assertThat(service.sessionManager().get(sessionId))
                .get().extracting(SessionConfig::model).isEqualTo("deepseek-reasoner");

        // 真正的持久化验证：清掉内存缓存后仍能从存储恢复
        service.sessionManager().close(sessionId);
        assertThat(service.sessionManager().get(sessionId))
                .get().extracting(SessionConfig::model).isEqualTo("deepseek-reasoner");
    }

    @Test
    void modelSwitchKeepsIdentityAndExtras() {
        String sessionId = service.createSession(
                new SessionConfig("deepseek-chat", "你是 AHA。", Map.of("k", "v")));
        dispatch(sessionId, "/model another-model");

        SessionConfig updated = service.sessionManager().get(sessionId).orElseThrow();
        assertThat(updated.systemPrompt()).isEqualTo("你是 AHA。");
        assertThat(updated.extras()).containsEntry("k", "v");
        // 已知模型之外只提示，不阻断
        assertThat(output()).contains("不在已知模型列表中");
    }

    @Test
    void modelSwitchToProviderIdIsHinted() {
        String sessionId = newSession();
        dispatch(sessionId, "/model OpenAI");
        assertThat(output()).contains("是供应商 ID").contains("aha provider use OpenAI");
    }

    @Test
    void modelSwitchToSameModelIsReported() {
        String sessionId = newSession();
        dispatch(sessionId, "/model deepseek-chat");
        assertThat(output()).contains("已是当前模型");
    }

    // ── /clear ──────────────────────────────────────────────────────────

    @Test
    void clearRemovesHistoryButKeepsSession() {
        String sessionId = newSession();
        service.memoryStore().appendMessage(sessionId, ChatMessage.text(Role.USER, "你好"));
        service.memoryStore().appendMessage(sessionId, ChatMessage.text(Role.ASSISTANT, "你好！"));

        dispatch(sessionId, "/clear");

        assertThat(output()).contains("上下文已清空").contains("2 条消息");
        assertThat(service.memoryStore().countMessages(sessionId)).isZero();
        // 会话记录与身份设定保留
        assertThat(service.sessionManager().get(sessionId))
                .get().extracting(SessionConfig::systemPrompt).isEqualTo("你是 AHA。");
    }

    @Test
    void clearOnEmptyHistoryIsNoop() {
        String sessionId = newSession();
        dispatch(sessionId, "/clear");
        assertThat(output()).contains("本来就是空的");
        assertThat(service.sessionManager().get(sessionId)).isPresent();
    }

    // ── /tool ───────────────────────────────────────────────────────────

    @Test
    void toolListsEnabledToolsWithApprovalMode() {
        String sessionId = newSession();
        dispatch(sessionId, "/tool");

        assertThat(output()).contains("工具（").contains("file-read").contains("AUTO");
        // file-write 需要 WRITE 权限，默认策略不自动放行
        assertThat(output()).contains("file-write").contains("CONFIRM");
    }

    @Test
    void toolDetailShowsPermissionAndParameters() {
        String sessionId = newSession();
        dispatch(sessionId, "/tool file-write");

        assertThat(output())
                .contains("工具 file-write")
                .contains("WRITE")
                .contains("已启用")
                .contains("CONFIRM")
                .contains("参数");
    }

    @Test
    void toolDetailReportsDisabledTool() {
        // 只启用 file-read，shell-exec 即为禁用
        service.engine().toolRegistry().setEnabled(List.of("file-read"));
        String sessionId = newSession();
        dispatch(sessionId, "/tool shell-exec");

        assertThat(output()).contains("已禁用").contains("Tools.Enabled");
    }

    @Test
    void unknownToolIsReported() {
        String sessionId = newSession();
        dispatch(sessionId, "/tool no-such-tool");
        assertThat(output()).contains("未找到工具").contains("no-such-tool");
    }

    // ── /session ────────────────────────────────────────────────────────

    @Test
    void sessionInfoShowsIdIdentityAndResumeCommand() {
        String sessionId = newSession();
        service.memoryStore().appendMessage(sessionId, ChatMessage.text(Role.USER, "hi"));
        dispatch(sessionId, "/session");

        assertThat(output())
                .contains(sessionId)
                .contains("deepseek-chat")
                .contains("内置默认身份")
                .contains(initial.systemPrompt().length() + " 字符")
                .contains("1 条已存储")
                .contains("s.db")
                .contains("aha chat --session " + sessionId);
    }

    @Test
    void sessionInfoShowsProjectScopedMemoryLocation() {
        String sessionId = newSession();
        dispatch(sessionId, "/session");

        // 记忆按项目存放（与 Claude Code 的项目 ID 规则一致），
        // 这里借用本仓库根——它含有 .git / AGENTS.md，会被认作项目根
        assertThat(output())
                .contains("项目")
                .contains("记忆")
                .contains("Project")
                .contains("Memory");
    }
}
