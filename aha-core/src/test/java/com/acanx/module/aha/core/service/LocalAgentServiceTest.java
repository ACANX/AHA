package com.acanx.module.aha.core.service;

import com.acanx.module.aha.common.event.AgentEvent;
import com.acanx.module.aha.common.event.ContentEvent;
import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.common.exception.ConfigException;
import com.acanx.module.aha.common.model.MemoryEntry;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.model.ToolDescriptor;
import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.common.tool.JsonSchema;
import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.tool.ToolPermission;
import com.acanx.module.aha.core.agent.ScriptedLlmClient;
import com.acanx.module.aha.core.agent.ToolRegistry;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.LlmConfig;
import com.acanx.module.aha.core.config.MemoryConfig;
import com.acanx.module.aha.core.config.ToolsConfig;
import com.acanx.module.aha.core.llm.protocol.StreamEvent;
import com.acanx.module.aha.core.llm.protocol.StreamEventType;
import com.acanx.module.aha.core.task.TaskRequest;
import com.acanx.module.aha.core.task.TaskResult;
import com.acanx.module.aha.core.task.TaskSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LocalAgentService} 测试（注入假 LLM 与内存工具表）。
 *
 * @since 0.1.0
 */
class LocalAgentServiceTest {

    @TempDir
    Path tempDir;

    private ScriptedLlmClient llm;
    private ToolRegistry tools;
    private LocalAgentService service;

    /**
     * 构造被测服务。
     *
     * @param config 配置
     * @return 服务
     */
    private LocalAgentService newService(AhaConfig config) {
        llm = new ScriptedLlmClient();
        tools = new ToolRegistry();
        service = new LocalAgentService(config, llm, tools, tempDir.resolve("aha.db"));
        return service;
    }

    /**
     * 指定权限的测试工具。
     *
     * @param name       名称
     * @param permission 权限
     */
    private record FakeTool(String name, ToolPermission permission) implements Tool {
        @Override
        public String description() {
            return "fake";
        }

        @Override
        public JsonSchema parameters() {
            return JsonSchema.object(Map.of(), List.of());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, CancellationToken token) {
            return ToolResult.success(name + "-ok");
        }

        @Override
        public ToolPermission requiredPermission() {
            return permission;
        }
    }

    @Test
    void chatReturnsResponseAndPersistsHistory() {
        newService(null);
        llm.enqueue(ScriptedLlmClient.text("hi there"));
        String sessionId = service.createSession(new SessionConfig("m", null, Map.of()));

        AgentResponse response = service.chat(sessionId, "hello").join();

        assertThat(response.sessionId()).isEqualTo(sessionId);
        assertThat(response.content()).isEqualTo("hi there");
        assertThat(response.finishReason()).isEqualTo("stop");
        assertThat(service.memoryStore().loadHistory(sessionId, 10)).hasSize(2);
        service.shutdown();
    }

    @Test
    void chatOnUnknownSessionFails() {
        newService(null);

        assertThatThrownBy(() -> service.chat("missing", "hi"))
                .isInstanceOf(AhaException.class)
                .hasMessageContaining("会话不存在");
        service.shutdown();
    }

    @Test
    void closeSessionReleasesActiveSessionButKeepsItRecoverable() {
        // 语义：closeSession = 结束活跃会话（释放资源），持久化记录保留，
        // 因而 `--session <id>` 可通过配置恢复继续该会话。
        newService(null);
        String sessionId = service.createSession(new SessionConfig("m", "身份", Map.of()));
        service.closeSession(sessionId);

        // 关闭后仍可从存储恢复会话配置并继续对话（不抛 SESSION_NOT_FOUND）
        llm.enqueue(ScriptedLlmClient.text("ok"));
        assertThat(service.chat(sessionId, "hi").join().content()).isEqualTo("ok");
        service.shutdown();
    }

    @Test
    void streamChatEmitsContentEvents() {
        newService(null);
        llm.scriptStream((listener, token) -> {
            listener.onEvent(new StreamEvent(StreamEventType.CONTENT_DELTA, "abc", null, null,
                    null, null, null, null));
            listener.onEvent(new StreamEvent(StreamEventType.DONE, null, null, null, null,
                    "stop", null, null));
        });
        String sessionId = service.createSession(new SessionConfig("m", null, Map.of()));
        List<AgentEvent> events = new ArrayList<>();

        service.streamChat(sessionId, "hi", events::add);

        assertThat(events.stream().filter(ContentEvent.class::isInstance)
                .map(e -> ((ContentEvent) e).delta()).toList()).containsExactly("abc");
        service.shutdown();
    }

    @Test
    void streamChatOnUnknownSessionFails() {
        newService(null);

        assertThatThrownBy(() -> service.streamChat("missing", "hi", event -> {
        })).isInstanceOf(AhaException.class);
        service.shutdown();
    }

    @Test
    void streamChatAcceptsCancellationToken() {
        // CLI 的「流式期间保持输入可用」靠这个重载把 Ctrl+C 传进来；
        // 默认实现把它默默丢掉，一旦退回就会造成“取消了但模型仍在跑”
        newService(null);
        llm.scriptStream((listener, token) -> {
            listener.onEvent(new StreamEvent(StreamEventType.CONTENT_DELTA, "abc", null, null,
                    null, null, null, null));
            listener.onEvent(new StreamEvent(StreamEventType.DONE, null, null, null, null,
                    "stop", null, null));
        });
        String sessionId = service.createSession(new SessionConfig("m", null, Map.of()));
        CancellationToken token = new CancellationToken();
        List<AgentEvent> events = new ArrayList<>();

        service.streamChat(sessionId, "hi", events::add, token);

        // 令牌透传到了引擎与 LLM 客户端：回调里拿到的就是同一个实例
        assertThat(llm.cancellationToken()).isSameAs(token);
        assertThat(events.stream().filter(ContentEvent.class::isInstance).count()).isEqualTo(1);
        service.shutdown();
    }

    @Test
    void streamChatStopsImmediatelyWhenTokenAlreadyCancelled() {
        newService(null);
        llm.scriptStream((listener, token) ->
                listener.onEvent(new StreamEvent(StreamEventType.CONTENT_DELTA, "abc", null, null,
                        null, null, null, null)));
        String sessionId = service.createSession(new SessionConfig("m", null, Map.of()));
        CancellationToken token = new CancellationToken();
        token.cancel();
        List<AgentEvent> events = new ArrayList<>();

        service.streamChat(sessionId, "hi", events::add, token);

        // 已取消则不再发起推理，也不会漏出内容
        assertThat(events.stream().filter(ContentEvent.class::isInstance)).isEmpty();
        service.shutdown();
    }

    @Test
    void listToolsReflectsRegistry() {
        newService(null);
        tools.register(new FakeTool("alpha", ToolPermission.READ));
        tools.register(new FakeTool("beta", ToolPermission.NETWORK));

        List<ToolDescriptor> descriptors = service.listTools();

        assertThat(descriptors).extracting(ToolDescriptor::name)
                .containsExactly("alpha", "beta");
        service.shutdown();
    }

    @Test
    void invokeToolAppliesDefaultPermissionPolicy() {
        newService(null);
        tools.register(new FakeTool("reader", ToolPermission.READ));
        tools.register(new FakeTool("writer", ToolPermission.WRITE));

        assertThat(service.invokeTool("reader", Map.of()).success()).isTrue();
        assertThatThrownBy(() -> service.invokeTool("writer", Map.of()))
                .hasMessageContaining("未获授权");
        // 显式确认后放行
        assertThat(service.invokeTool("writer", Map.of(), true).success()).isTrue();
        service.shutdown();
    }

    @Test
    void invokeUnknownToolFails() {
        newService(null);

        assertThatThrownBy(() -> service.invokeTool("nope", Map.of()))
                .hasMessageContaining("未知工具");
        assertThatThrownBy(() -> service.invokeTool("nope", Map.of(), true))
                .hasMessageContaining("未知工具");
        service.shutdown();
    }

    @Test
    void configAutoApproveExtendsDefaultPolicy() {
        // Enabled 传 null：本用例验证 AutoApprove 语义，不应受启用列表干扰
        AhaConfig config = new AhaConfig(null, null,
                new ToolsConfig(null, List.of("WRITE"), null), null, null, null);
        newService(config);
        tools.register(new FakeTool("writer", ToolPermission.WRITE));
        tools.register(new FakeTool("reader", ToolPermission.READ));

        assertThat(service.invokeTool("writer", Map.of()).success()).isTrue();
        assertThat(service.invokeTool("reader", Map.of()).success()).isTrue();
        service.shutdown();
    }

    @Test
    void configEnabledRestrictsTools() {
        // Tools.Enabled 此前从未生效：未列出的工具仍可被调用
        AhaConfig config = new AhaConfig(null, null,
                new ToolsConfig(List.of("reader"), List.of(), null), null, null, null);
        newService(config);
        tools.register(new FakeTool("reader", ToolPermission.READ));
        tools.register(new FakeTool("writer", ToolPermission.WRITE));

        assertThat(service.invokeTool("reader", Map.of()).success()).isTrue();
        assertThatThrownBy(() -> service.invokeTool("writer", Map.of()))
                .hasMessageContaining("未在 Tools.Enabled 中启用");
        // 显式确认也不应绕过启用过滤
        assertThatThrownBy(() -> service.invokeTool("writer", Map.of(), true))
                .hasMessageContaining("未在 Tools.Enabled 中启用");
        service.shutdown();
    }

    @Test
    void configAutoApproveAllPermitsEverything() {
        AhaConfig config = new AhaConfig(null, null,
                new ToolsConfig(null, List.of("ALL"), null), null, null, null);
        newService(config);
        tools.register(new FakeTool("admin", ToolPermission.ADMIN));

        assertThat(service.invokeTool("admin", Map.of()).success()).isTrue();
        service.shutdown();
    }

    @Test
    void configRejectsUnknownPermission() {
        AhaConfig config = new AhaConfig(null, null,
                new ToolsConfig(null, List.of("BOGUS"), null), null, null, null);

        assertThatThrownBy(() -> new LocalAgentService(config, new ScriptedLlmClient(),
                new ToolRegistry(), tempDir.resolve("bad.db")))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("BOGUS");
    }

    @Test
    void recallDelegatesToMemoryStore() {
        newService(null);
        String sessionId = service.createSession(new SessionConfig("m", null, Map.of()));
        service.memoryStore().storeMemory(sessionId,
                new MemoryEntry("k", "记忆内容", System.currentTimeMillis()));

        List<MemoryEntry> hits = service.recall(sessionId, "记忆", 5);

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().key()).isEqualTo("k");
        service.shutdown();
    }

    @Test
    void accessorsExposeComponents() {
        AhaConfig config = new AhaConfig(null, null, null, null, null, null);
        newService(config);

        assertThat(service.config()).isSameAs(config);
        assertThat(service.engine()).isNotNull();
        assertThat(service.memoryStore()).isNotNull();
        service.shutdown();
    }

    @Test
    void factoryCreatesWorkingService() {
        String originalHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        try {
            AgentService created = AgentServiceFactory.local(null);
            assertThat(created).isInstanceOf(LocalAgentService.class);
            assertThat(created.listTools()).isNotNull();
            assertThat(created.runtime().isLocal()).isTrue();
            created.shutdown();
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }

    @Test
    void submitCreatesAndClosesSessionWhenAbsent() {
        newService(null);
        llm.enqueue(ScriptedLlmClient.text("task done"));

        TaskResult result = service.submit(TaskRequest.of("do work"), new CancellationToken());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("task done");
        assertThat(result.metadata()).containsKey("SessionId");
        assertThat(result.metadata()).containsEntry("Runtime", "local");
        service.shutdown();
    }

    @Test
    void submitReusesProvidedSession() {
        newService(null);
        String sessionId = service.createSession(new SessionConfig("m", null, Map.of()));
        llm.enqueue(ScriptedLlmClient.text("again"));
        TaskRequest request = new TaskRequest("t1", null, TaskSource.REMOTE_CALL, sessionId,
                "continue", null, null, Map.of(), 0L, Map.of());

        TaskResult result = service.submit(request, new CancellationToken());

        assertThat(result.taskId()).isEqualTo("t1");
        assertThat(result.success()).isTrue();
        // 会话仍存在（不由 submit 关闭）
        assertThat(service.chat(sessionId, "hi")).isNotNull();
        service.shutdown();
    }

    @Test
    void submitReportsFailureWithoutThrowing() {
        newService(null);
        TaskRequest request = new TaskRequest("t2", null, TaskSource.SERVERLESS, "missing-session",
                "x", null, null, Map.of(), 0L, Map.of());

        TaskResult result = service.submit(request, new CancellationToken());

        assertThat(result.success()).isFalse();
        assertThat(result.errorCode()).isEqualTo("SESSION_NOT_FOUND");
        service.shutdown();
    }

    @Test
    void singleArgConstructorWiresEngineDependencies() {        // 回归：AgentServiceFactory.local(config) 走此路径。
        // 之前把构造器参数（此处全为 null）直接传给 AgentEngine，
        // 导致 chat / run 时 toolRegistry.list() 抛 NPE。
        AhaConfig config = new AhaConfig(null,
                new MemoryConfig("sqlite", tempDir.resolve("auto.db").toString(), 10),
                null, null, null, null);
        LocalAgentService local = new LocalAgentService(config);
        try {
            assertThat(local.engine().toolRegistry()).isNotNull();
            assertThat(local.engine().llmClient()).isNotNull();
            assertThat(local.engine().memoryStore()).isNotNull();
            assertThat(local.listTools()).isNotNull();
        } finally {
            local.shutdown();
        }
    }

    @Test
    void nullDependenciesFallBackToDefaults() {
        // 显式传 null 的注入构造器同样不应产生 null 依赖
        AhaConfig config = new AhaConfig(null,
                new MemoryConfig("sqlite", tempDir.resolve("nulls.db").toString(), 10),
                null, null, null, null);
        LocalAgentService local = new LocalAgentService(config, null, null,
                tempDir.resolve("nulls.db"));
        try {
            assertThat(local.engine().toolRegistry()).isNotNull();
            assertThat(local.engine().llmClient()).isNotNull();
        } finally {
            local.shutdown();
        }
    }

    @Test
    void runtimeExposesProviderModelAndEndpoint() {
        AhaConfig config = new AhaConfig(
                new LlmConfig("DeepSeek", null, null, null),
                new MemoryConfig("sqlite", tempDir.resolve("rt.db").toString(), 10),
                null, null, null, null);
        LocalAgentService local = new LocalAgentService(config);
        try {
            var attributes = local.runtime().attributes();
            assertThat(attributes)
                    .containsEntry("Provider", "DeepSeek")
                    .containsEntry("Model", "deepseek-chat")
                    .containsEntry("Adapter", "openai-compatible");
            // 端点必须与传输层一致（含 /chat/completions）
            assertThat(attributes.get("Endpoint"))
                    .isEqualTo("https://api.deepseek.com/v1/chat/completions");
        } finally {
            local.shutdown();
        }
    }

    @Test
    void runtimeOmitsAttributesWhenNoProvider() {
        AhaConfig config = new AhaConfig(null,
                new MemoryConfig("sqlite", tempDir.resolve("np.db").toString(), 10),
                null, null, null, null);
        LocalAgentService local = new LocalAgentService(config);
        try {
            // 默认供应商仍来自内置预设，但端点解析不应抛异常
            assertThat(local.runtime()).isNotNull();
            assertThat(local.runtime().isLocal()).isTrue();
        } finally {
            local.shutdown();
        }
    }
}
