package com.acanx.module.aha.core.service;

import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.common.model.SessionSummary;
import com.acanx.module.aha.core.memory.MemoryStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理器：会话的创建、缓存与持久化恢复。
 *
 * <p>会话配置（含系统提示词与模型）在创建时<b>固化</b>并写入 {@code session.config_json}，
 * 因此：</p>
 * <ul>
 *   <li>同一会话内的每一轮推理复用同一份提示词，不会重复读取文件或重复解析</li>
 *   <li>进程重启后仍可恢复该会话的身份设定（{@link #get} 会从存储回填缓存）</li>
 * </ul>
 *
 * <p>注意：LLM API 是无状态的，每一轮请求仍需携带 system 消息，否则模型会失去身份设定。
 * “不重复注入”指的是<b>不重复配置</b>，而非不发送。</p>
 *
 * @since 0.1.0
 */
public final class SessionManager {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private final Map<String, SessionConfig> sessions = new ConcurrentHashMap<>();
    private final MemoryStore memoryStore;

    /**
     * 构造会话管理器。
     *
     * @param memoryStore 记忆存储
     */
    public SessionManager(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    /**
     * 创建会话并持久化配置。
     *
     * @param config 会话配置，可为 {@code null}
     * @return 会话 ID
     */
    public String create(SessionConfig config) {
        String sessionId = UUID.randomUUID().toString().toUpperCase();
        SessionConfig resolved = config == null
                ? new SessionConfig(null, null, Map.of())
                : config;
        sessions.put(sessionId, resolved);
        memoryStore.createSession(sessionId, serialize(resolved));
        return sessionId;
    }

    /**
     * 取会话配置：先查内存缓存，未命中则从存储恢复。
     *
     * @param sessionId 会话 ID
     * @return 会话配置
     */
    public Optional<SessionConfig> get(String sessionId) {
        SessionConfig cached = sessions.get(sessionId);
        if (cached != null) {
            return Optional.of(cached);
        }
        String json = memoryStore.loadSession(sessionId);
        if (json == null) {
            return Optional.empty();
        }
        SessionConfig restored = deserialize(json);
        sessions.put(sessionId, restored);
        return Optional.of(restored);
    }

    /**
     * 更新会话配置并持久化。
     *
     * <p>用于会话内修改（如 {@code /model} 切换模型）：只改配置，不动历史消息。</p>
     *
     * @param sessionId 会话 ID
     * @param config    新配置
     * @return 会话不存在时返回 {@code false}
     */
    public boolean update(String sessionId, SessionConfig config) {
        if (get(sessionId).isEmpty()) {
            return false;
        }
        sessions.put(sessionId, config);
        memoryStore.updateSessionConfig(sessionId, serialize(config));
        return true;
    }

    /**
     * 会话是否处于活跃状态（内存缓存中）。
     *
     * <p>注意：{@link #close} 后本方法返回 {@code false}，但持久化记录仍在，
     * 可通过 {@link #get} 恢复。</p>
     *
     * @param sessionId 会话 ID
     * @return 是否活跃
     */
    public boolean exists(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * 列出会话摘要（最近创建的在前），供会话列表展示。
     *
     * @return 会话摘要
     */
    public List<SessionSummary> list() {
        return memoryStore.listSessions();
    }

    /**
     * 重命名会话。
     *
     * @param sessionId 会话 ID
     * @param title     新标题；空白视为清除自定义标题（回退到首条用户消息）
     */
    public void rename(String sessionId, String title) {
        memoryStore.updateSessionTitle(sessionId, title);
    }

    /**
     * 删除会话（消息与记忆一并删除）。
     *
     * <p>与 {@link #close} 的区别：{@code close} 只是从内存缓存移除，记录仍在；
     * {@code delete} 是**不可恢复**的删除，因此界面上必须二次确认。</p>
     *
     * @param sessionId 会话 ID
     */
    public void delete(String sessionId) {
        memoryStore.deleteSession(sessionId);
        sessions.remove(sessionId);
    }

    /**
     * 关闭会话（仅移除内存缓存，保留持久化记录以供恢复）。
     *
     * @param sessionId 会话 ID
     */
    public void close(String sessionId) {
        sessions.remove(sessionId);
    }

    private static String serialize(SessionConfig config) {
        ObjectNode node = JSON.createObjectNode();
        if (config.model() != null) {
            node.put("Model", config.model());
        }
        if (config.systemPrompt() != null) {
            node.put("SystemPrompt", config.systemPrompt());
        }
        node.set("Extras", JSON.valueToTree(config.extras() == null ? Map.of() : config.extras()));
        return JSON.writeValueAsString(node);
    }

    private static SessionConfig deserialize(String json) {
        JsonNode node = JSON.readTree(json);
        String model = node.hasNonNull("Model") ? node.get("Model").asString() : null;
        String systemPrompt = node.hasNonNull("SystemPrompt")
                ? node.get("SystemPrompt").asString()
                : null;
        Map<String, Object> extras = new LinkedHashMap<>();
        JsonNode extrasNode = node.get("Extras");
        if (extrasNode != null && extrasNode.isObject()) {
            extrasNode.properties().forEach(entry ->
                    extras.put(entry.getKey(), entry.getValue().isString()
                            ? entry.getValue().asString()
                            : entry.getValue()));
        }
        return new SessionConfig(model, systemPrompt, extras);
    }
}
