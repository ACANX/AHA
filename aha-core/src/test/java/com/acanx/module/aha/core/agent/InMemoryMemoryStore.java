package com.acanx.module.aha.core.agent;

import com.acanx.module.aha.common.model.MemoryEntry;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.memory.MemoryStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试用内存记忆存储。
 *
 * @since 0.1.0
 */
public final class InMemoryMemoryStore implements MemoryStore {

    private final List<ChatMessage> messages = new ArrayList<>();
    private final List<MemoryEntry> memories = new ArrayList<>();
    private final List<String> createdSessions = new ArrayList<>();
    private final Map<String, String> sessionConfigs = new LinkedHashMap<>();

    @Override
    public void init() {
    }

    @Override
    public void createSession(String sessionId, String configJson) {
        createdSessions.add(sessionId);
        sessionConfigs.put(sessionId, configJson == null ? "{}" : configJson);
    }

    @Override
    public String loadSession(String sessionId) {
        return sessionConfigs.get(sessionId);
    }

    /**
     * 已持久化的会话配置 JSON。
     *
     * @param sessionId 会话 ID
     * @return 配置 JSON；不存在时返回 {@code null}
     */
    public String sessionConfig(String sessionId) {
        return sessionConfigs.get(sessionId);
    }

    /**
     * 已创建的会话 ID。
     *
     * @return 会话 ID 列表
     */
    public List<String> createdSessions() {
        return new ArrayList<>(createdSessions);
    }

    @Override
    public void appendMessage(String sessionId, ChatMessage message) {
        messages.add(message);
    }

    @Override
    public List<ChatMessage> loadHistory(String sessionId, int limit) {
        return new ArrayList<>(messages);
    }

    @Override
    public void deleteSession(String sessionId) {
        // 这个测试替身只有一个平铺的消息表，因此「删除会话」= 清空消息 + 忘掉配置
        messages.clear();
        memories.clear();
        createdSessions.remove(sessionId);
        sessionConfigs.remove(sessionId);
    }

    @Override
    public void clearHistory(String sessionId) {
        messages.clear();
    }

    @Override
    public void storeMemory(String sessionId, MemoryEntry entry) {
        memories.add(entry);
    }

    @Override
    public List<MemoryEntry> recall(String sessionId, String query, int limit) {
        return new ArrayList<>(memories);
    }

    @Override
    public void close() {
    }
}
