package com.acanx.module.aha.core.service;

import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.common.exception.ConfigException;
import com.acanx.module.aha.common.exception.ToolExecutionException;
import com.acanx.module.aha.common.model.MemoryEntry;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.model.ToolDescriptor;
import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.runtime.RuntimeDescriptor;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.common.tool.ToolApprover;
import com.acanx.module.aha.common.tool.ToolPermission;
import com.acanx.module.aha.common.tool.ToolSettings;
import com.acanx.module.aha.core.config.ShellConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.acanx.module.aha.core.agent.AgentEngine;
import com.acanx.module.aha.core.agent.ContextCompactor;
import com.acanx.module.aha.core.agent.PermissionPolicy;
import com.acanx.module.aha.core.agent.ToolRegistry;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.ConfigLoader;
import com.acanx.module.aha.core.config.LlmConfig;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.config.ProviderPresets;
import com.acanx.module.aha.core.config.ToolsConfig;
import com.acanx.module.aha.core.llm.LlmClient;
import com.acanx.module.aha.core.llm.LlmProviderAdapter;
import com.acanx.module.aha.core.llm.adapter.DefaultLlmClient;
import com.acanx.module.aha.core.llm.registry.LlmAdapterRegistry;
import com.acanx.module.aha.core.memory.MemoryStore;
import com.acanx.module.aha.core.memory.SqliteMemoryStore;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 本地 Agent 服务实现。
 *
 * @since 0.1.0
 */
public final class LocalAgentService implements AgentService {

    private static final Logger LOG = LoggerFactory.getLogger(LocalAgentService.class);

    private final AhaConfig config;
    private final ToolRegistry toolRegistry;
    private final MemoryStore memoryStore;
    private final LlmClient llmClient;
    private final SessionManager sessionManager;
    private final AgentEngine engine;
    private final ContextCompactor compactor;
    private final String defaultModel;
    private final ExecutorService executor;
    private final LlmConfig llmConfig;
    private final LlmAdapterRegistry adapterRegistry;

    /**
     * 构造本地服务。
     *
     * @param config 配置，可为 {@code null}
     */
    public LocalAgentService(AhaConfig config) {
        this(config, null, null, null);
    }

    /**
     * 构造本地服务并指定工具授权裁决器。
     *
     * @param config   配置，可为 {@code null}
     * @param approver 工具授权裁决器，为 {@code null} 时一律拒绝
     */
    public LocalAgentService(AhaConfig config, ToolApprover approver) {
        this(config, null, null, null);
        this.toolRegistry.setApprover(approver);
    }

    /**
     * 构造本地服务（可注入依赖，便于测试与嵌入）。
     *
     * @param config       配置，可为 {@code null}
     * @param llmClient    LLM 客户端，为 {@code null} 时按配置创建
     * @param toolRegistry 工具注册表，为 {@code null} 时按配置创建
     * @param dbPath       SQLite 路径，为 {@code null} 时按配置解析
     */
    public LocalAgentService(AhaConfig config, LlmClient llmClient, ToolRegistry toolRegistry,
                             Path dbPath) {
        this.config = config;
        this.toolRegistry = toolRegistry != null ? toolRegistry : new ToolRegistry();
        // 无论是否注入注册表，均按配置应用权限、启用集合与工具配置
        this.toolRegistry.setPermissionPolicy(resolvePermissionPolicy(config));
        this.toolRegistry.setEnabled(resolveEnabledTools(config));
        this.toolRegistry.configureAll(resolveToolSettings(config));
        this.memoryStore = new SqliteMemoryStore(dbPath != null ? dbPath : resolveDbPath(config));
        this.memoryStore.init();

        LlmConfig llmConfig = ProviderPresets.applyDefaults(config == null ? null : config.llm());
        this.llmConfig = llmConfig;
        this.adapterRegistry = new LlmAdapterRegistry();
        this.llmClient = llmClient != null
                ? llmClient
                : new DefaultLlmClient(adapterRegistry, llmConfig);
        this.sessionManager = new SessionManager(memoryStore);
        this.defaultModel = resolveDefaultModel(llmConfig);
        // 必须传已初始化的字段（this.*）：构造器参数可能为 null，
        // 直接传参会让 AgentEngine 拿到 null 依赖，在 list()/invoke() 时抛 NPE
        this.engine = new AgentEngine(this.llmClient, this.toolRegistry, this.memoryStore,
                resolveMaxContext(config));
        this.compactor = new ContextCompactor(this.llmClient, this.memoryStore);
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @Override
    public String createSession(SessionConfig sessionConfig) {
        String sessionId = sessionManager.create(sessionConfig);
        LOG.info("创建会话 session={} 模型={} 身份={} 字符",
                sessionId,
                sessionConfig == null ? null : sessionConfig.model(),
                sessionConfig == null || sessionConfig.systemPrompt() == null
                        ? 0 : sessionConfig.systemPrompt().length());
        return sessionId;
    }

    @Override
    public void closeSession(String sessionId) {
        sessionManager.close(sessionId);
        LOG.info("关闭会话 session={}（记录保留，可用 --session 续接）", sessionId);
    }

    @Override
    public CompletableFuture<AgentResponse> chat(String sessionId, String input) {
        SessionConfig session = sessionManager.get(sessionId)
                .orElseThrow(() -> new AhaException("SESSION_NOT_FOUND", "会话不存在: " + sessionId));
        LOG.info("推理开始（非流式）session={} 输入={} 字符", sessionId,
                input == null ? 0 : input.length());
        return CompletableFuture.supplyAsync(
                () -> engine.run(sessionId, session, defaultModel, input, new CancellationToken()),
                executor);
    }

    @Override
    public void streamChat(String sessionId, String input, AgentEventListener listener) {
        streamChat(sessionId, input, listener, new CancellationToken());
    }

    @Override
    public void streamChat(String sessionId, String input, AgentEventListener listener,
                          CancellationToken token) {
        SessionConfig session = sessionManager.get(sessionId)
                .orElseThrow(() -> new AhaException("SESSION_NOT_FOUND", "会话不存在: " + sessionId));
        LOG.info("推理开始（流式）session={} 输入={} 字符", sessionId,
                input == null ? 0 : input.length());
        engine.stream(sessionId, session, defaultModel, input, listener,
                token == null ? new CancellationToken() : token);
    }

    @Override
    public List<ToolDescriptor> listTools() {
        return toolRegistry.list();
    }

    /**
     * 运行时描述，附带当前供应商、模型、适配器与端点。
     *
     * <p>用于 CLI 启动时展示“我到底连的是哪里”，避免用户只能靠问模型来判断身份。
     * 端点解析失败不影响服务可用，仅略去该属性。</p>
     *
     * @return 运行时描述
     */
    @Override
    public RuntimeDescriptor runtime() {
        RuntimeDescriptor base = AgentService.super.runtime();
        Map<String, String> attributes = new LinkedHashMap<>();
        String providerId = llmConfig == null ? null : llmConfig.defaultProvider();
        ProviderConfig provider = providerId == null || llmConfig.providers() == null
                ? null
                : llmConfig.providers().get(providerId);
        if (provider != null) {
            attributes.put("Provider", providerId);
            attributes.put("Model", provider.model() == null ? "" : provider.model());
            attributes.put("Adapter", provider.adapter() == null ? "" : provider.adapter());
            String endpoint = resolveEndpoint(provider);
            if (endpoint != null) {
                attributes.put("Endpoint", endpoint);
            }
        }
        return new RuntimeDescriptor(base.id(), base.kind(), base.os(), base.arch(),
                base.workingDir(), base.remote(), attributes);
    }

    /**
     * 解析供应商的实际请求端点（与传输层一致）。
     *
     * @param provider 供应商配置
     * @return 端点 URL；无法解析时返回 {@code null}
     */
    private String resolveEndpoint(ProviderConfig provider) {
        try {
            return adapterRegistry.find(provider.adapter())
                    .map(adapter -> adapter.buildUrl(provider))
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /**
     * 设置工具授权裁决器。
     *
     * <p>交互式 CLI 在创建终端后调用；未设置时需授权的工具一律拒绝。</p>
     *
     * @param approver 裁决器
     */
    public void setToolApprover(ToolApprover approver) {
        toolRegistry.setApprover(approver);
    }

    @Override
    public ToolResult invokeTool(String toolName, Map<String, Object> params) {
        return toolRegistry.invoke(toolName, params, new CancellationToken());
    }

    @Override
    public ToolResult invokeTool(String toolName, Map<String, Object> params, boolean userConfirmed) {
        if (!userConfirmed) {
            return invokeTool(toolName, params);
        }
        // 已确认仅跳过权限裁决，仍需经过启用过滤
        return toolRegistry.invokeApproved(toolName, params, new CancellationToken());
    }

    @Override
    public List<MemoryEntry> recall(String sessionId, String query, int limit) {
        return memoryStore.recall(sessionId, query, limit);
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
        memoryStore.close();
        if (llmClient instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new AhaException("CLIENT_CLOSE_FAILED", "关闭 LLM 客户端失败", e);
            }
        }
    }

    /**
     * 返回配置。
     *
     * @return 配置
     */
    public AhaConfig config() {
        return config;
    }

    /**
     * 返回记忆存储。
     *
     * @return 记忆存储
     */
    public MemoryStore memoryStore() {
        return memoryStore;
    }

    /**
     * 返回会话管理器。
     *
     * <p>供上层（CLI 的 {@code /context} 等）读取会话已固化的配置（模型、系统提示词）。</p>
     *
     * @return 会话管理器
     */
    public SessionManager sessionManager() {
        return sessionManager;
    }

    /**
     * 返回推理引擎。
     *
     * @return 引擎
     */
    public AgentEngine engine() {
        return engine;
    }

    /**
     * 压缩会话上下文。
     *
     * <p>保留下最近 {@code keepRecent} 条消息，更早的部分交给模型压成一条摘要。
     * 摘要生成失败时历史不会被改动。</p>
     *
     * @param sessionId  会话 ID
     * @param keepRecent 期望保留的最近消息条数
     * @return 压缩结果
     */
    public ContextCompactor.Outcome compact(String sessionId, int keepRecent) {
        SessionConfig session = sessionManager.get(sessionId).orElse(null);
        ContextCompactor.Outcome outcome = compactor.compact(sessionId, session, defaultModel, keepRecent);
        if (outcome.changed()) {
            LOG.info("上下文已压缩 session={} 压缩={} 条 保留={} 条 模型={}",
                    sessionId, outcome.removed(), outcome.kept(), outcome.model());
        }
        return outcome;
    }

    // ------------------------------------------------------------------

    private static Path resolveDbPath(AhaConfig config) {
        String path = config == null || config.memory() == null ? null : config.memory().path();
        if (path != null && !path.isBlank()) {
            // 默认值为 ${AHA_HOME:-~/.aha}/Data/Aha.db，展开后的值可能仍含 ~
            // （Path.of 不会展开 ~，否则会创建出名为 ~ 的字面目录）
            return ConfigLoader.expandHome(path);
        }
        String home = System.getenv("AHA_HOME");
        Path base = home != null && !home.isBlank()
                ? Path.of(home)
                : Path.of(System.getProperty("user.home"), ".aha");
        return base.resolve("Data").resolve("Aha.db");
    }

    private static int resolveMaxContext(AhaConfig config) {
        return config == null || config.memory() == null ? 100 : config.memory().maxContextEntries();
    }

    /**
     * 解析启用的工具名。
     *
     * @param config 配置
     * @return 工具名；未配置时为空列表（表示不过滤）
     */
    private static List<String> resolveEnabledTools(AhaConfig config) {
        ToolsConfig tools = config == null ? null : config.tools();
        return tools == null || tools.enabled() == null ? List.of() : tools.enabled();
    }

    /**
     * 将 {@link ToolsConfig} 转为工具可见的中立配置视图。
     *
     * <p>工具位于 {@code aha-tool}，不能依赖 {@code aha-core} 的配置类型，
     * 因此在此转换为 {@link ToolSettings}。键名沿用 YAML 的点分路径。</p>
     *
     * @param config 配置
     * @return 工具配置视图
     */
    private static ToolSettings resolveToolSettings(AhaConfig config) {
        ToolsConfig tools = config == null ? null : config.tools();
        if (tools == null) {
            return ToolSettings.empty();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        if (tools.enabled() != null) {
            values.put("Enabled", tools.enabled());
        }
        if (tools.autoApprove() != null) {
            values.put("AutoApprove", tools.autoApprove());
        }
        ShellConfig shell = tools.shell();
        if (shell != null) {
            if (shell.allowedCommands() != null) {
                values.put("Shell.AllowedCommands", shell.allowedCommands());
            }
            if (shell.timeoutSeconds() > 0) {
                values.put("Shell.TimeoutSeconds", shell.timeoutSeconds());
            }
        }
        return ToolSettings.of(values);
    }

    private static PermissionPolicy resolvePermissionPolicy(AhaConfig config) {
        ToolsConfig tools = config == null ? null : config.tools();
        if (tools == null || tools.autoApprove() == null) {
            return PermissionPolicy.defaultPolicy();
        }
        Set<ToolPermission> extra = EnumSet.noneOf(ToolPermission.class);
        for (String name : tools.autoApprove()) {
            if (name == null || name.isBlank()) {
                continue;
            }
            String normalized = name.trim().toUpperCase(Locale.ROOT);
            if ("ALL".equals(normalized)) {
                return PermissionPolicy.allowAll();
            }
            try {
                extra.add(ToolPermission.valueOf(normalized));
            } catch (IllegalArgumentException e) {
                throw new ConfigException("INVALID_PERMISSION", "无法识别的工具权限: " + name);
            }
        }
        return PermissionPolicy.defaultPlus(extra);
    }

    private static String resolveDefaultModel(LlmConfig llmConfig) {
        if (llmConfig == null || llmConfig.providers() == null) {
            return llmConfig == null ? null : llmConfig.fallbackModel();
        }
        ProviderConfig provider = llmConfig.providers().get(llmConfig.defaultProvider());
        if (provider != null) {
            return provider.model();
        }
        // 默认供应商不可用：降级到兜底模型
        ProviderConfig fallback = llmConfig.fallbackProvider() == null
                ? null
                : llmConfig.providers().get(llmConfig.fallbackProvider());
        if (llmConfig.fallbackModel() != null) {
            return llmConfig.fallbackModel();
        }
        return fallback == null ? null : fallback.model();
    }
}
