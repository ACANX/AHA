package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.model.ToolDescriptor;
import com.acanx.module.aha.common.runtime.RuntimeDescriptor;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.memory.MemoryStore;
import com.acanx.module.aha.core.service.LocalAgentService;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会话上下文快照。
 *
 * <p>{@code /context}、{@code /compact} 与 {@code /autocompact} 都要回答「现在这个会话
 * 带着什么上下文」——三处共用同一份快照，口径才不会漂移。</p>
 *
 * @param model        实际生效模型（会话固化值优先于运行时默认值）
 * @param provider     供应商 ID
 * @param endpoint     端点
 * @param systemPrompt 系统提示词
 * @param history      本轮实际加载的历史消息
 * @param stored       存储中的消息总数
 * @param window       上下文窗口上限
 * @param tools        已启用工具名
 * @since 0.1.0
 */
public record ContextSnapshot(
        String model,
        String provider,
        String endpoint,
        String systemPrompt,
        List<ChatMessage> history,
        int stored,
        int window,
        List<String> tools) {

    /** 缺少配置时的默认上下文窗口。 */
    public static final int DEFAULT_WINDOW = 100;

    /**
     * 估算本轮上下文的 Token 数。
     *
     * @return 估算值
     */
    public long tokens() {
        return TokenEstimator.estimateMessages(systemPrompt, history);
    }

    /**
     * 是否含系统提示词。
     *
     * @return 含系统提示词时为 {@code true}
     */
    public boolean hasSystemPrompt() {
        return systemPrompt != null && !systemPrompt.isBlank();
    }

    /**
     * 解析上下文窗口上限。
     *
     * @param config 配置，可为 {@code null}
     * @return 窗口上限
     */
    public static int resolveWindow(AhaConfig config) {
        if (config == null || config.memory() == null || config.memory().maxContextEntries() <= 0) {
            return DEFAULT_WINDOW;
        }
        return config.memory().maxContextEntries();
    }

    /**
     * 采集会话上下文快照。
     *
     * <p>记忆存储、工具注册表与会话记录只有本地运行时可直接读取；远程形态下返回
     * {@link Optional#empty()}，由调用方退回运行时属性，不臆测远端状态。</p>
     *
     * @param context 会话上下文
     * @return 快照；非本地运行时为空
     */
    public static Optional<ContextSnapshot> of(SessionContext context) {
        LocalAgentService local = context.local();
        if (local == null) {
            return Optional.empty();
        }
        RuntimeDescriptor runtime = context.service().runtime();
        Map<String, String> attributes = runtime.attributes();
        int window = resolveWindow(context.config());

        MemoryStore store = local.memoryStore();
        List<ChatMessage> history = store.loadHistory(context.sessionId(), window);
        int stored = store.countMessages(context.sessionId());
        List<String> tools = local.listTools().stream().map(ToolDescriptor::name).toList();

        String model = attributes.get("Model");
        String systemPrompt = null;
        Optional<SessionConfig> session = local.sessionManager().get(context.sessionId());
        if (session.isPresent()) {
            systemPrompt = session.get().systemPrompt();
            String sessionModel = session.get().model();
            if (sessionModel != null && !sessionModel.isBlank()) {
                // 会话固化的模型优先于运行时默认值，它才是本轮真正使用的模型
                model = sessionModel;
            }
        }
        return Optional.of(new ContextSnapshot(model, attributes.get("Provider"),
                attributes.get("Endpoint"), systemPrompt, history, stored, window, tools));
    }
}
