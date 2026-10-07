package com.acanx.module.aha.core.memory;

import com.acanx.module.aha.common.model.MemoryEntry;
import com.acanx.module.aha.common.model.SessionSummary;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;

import java.util.List;

/**
 * 记忆存储。
 *
 * @since 0.1.0
 */
public interface MemoryStore {

    /**
     * 初始化存储（建表等）。
     */
    void init();

    /**
     * 创建会话记录。
     *
     * @param sessionId  会话 ID
     * @param configJson 会话配置 JSON
     */
    void createSession(String sessionId, String configJson);

    /**
     * 读取会话配置 JSON。
     *
     * <p>用于会话恢复：进程重启后仍能取回该会话固化的系统提示词与模型。
     * 会话不存在时返回 {@code null}。</p>
     *
     * @param sessionId 会话 ID
     * @return 会话配置 JSON；不存在时返回 {@code null}
     */
    String loadSession(String sessionId);

    /**
     * 追加消息。
     *
     * @param sessionId 会话 ID
     * @param message   消息
     */
    void appendMessage(String sessionId, ChatMessage message);

    /**
     * 加载会话历史。
     *
     * <p>取的是<b>最近</b>的 {@code limit} 条而非最早的若干条：会话超出上下文窗口时
     * 应丢掉最旧的历史，否则模型永远看不到新发生的对话。返回结果按时间正序。</p>
     *
     * @param sessionId 会话 ID
     * @param limit     数量上限
     * @return 消息列表（时间正序）
     */
    List<ChatMessage> loadHistory(String sessionId, int limit);

    /**
     * 列出会话摘要，最近创建的在前。
     *
     * <p>默认实现返回空列表：不是每个存储都有「会话»这个概念（内存实现可能只按 ID 存消息）。
     * 真正支持会话列表的实现（{@code SqliteMemoryStore}）覆写它。</p>
     *
     * @return 会话摘要
     */
    default List<SessionSummary> listSessions() {
        return List.of();
    }

    /**
     * 重命名会话。
     *
     * <p>默认实现什么都不做：标题是展示层信息，丢了不影响会话可用性。</p>
     *
     * @param sessionId 会话 ID
     * @param title     新标题
     */
    default void updateSessionTitle(String sessionId, String title) {
    }

    /**
     * 删除会话及其消息与记忆。
     *
     * <p>默认实现只清空历史（保留会话记录）。存储实现应覆写为连会话记录一起删，
     * 否则删除后它仍会出现在会话列表里。</p>
     *
     * @param sessionId 会话 ID
     */
    default void deleteSession(String sessionId) {
        clearHistory(sessionId);
    }

    /**
     * 清空会话的历史消息（保留会话记录与配置）。
     *
     * @param sessionId 会话 ID
     */
    void clearHistory(String sessionId);

    /**
     * 用一条摘要替换较早的历史，仅保留最近 {@code keepRecent} 条消息。
     *
     * <p>摘要写入后位于保留消息之前，因此 {@link #loadHistory} 的顺序仍是
     * 「摘要、保留的最近消息」。实现为「整段重写」，因此无法被 {@code loadHistory}
     * 重建的不完整消息会被一并丢弃——它们本就未进入模型上下文。</p>
     *
     * @param sessionId  会话 ID
     * @param keepRecent 保留的最近消息条数
     * @param summary    摘要消息；为 {@code null} 时只删除不写入
     * @return 被替换掉的消息条数；无可替换内容时返回 0
     */
    default int replaceHistory(String sessionId, int keepRecent, ChatMessage summary) {
        List<ChatMessage> history = loadHistory(sessionId, Integer.MAX_VALUE);
        int keep = Math.max(0, Math.min(keepRecent, history.size()));
        int removed = history.size() - keep;
        if (removed <= 0) {
            return 0;
        }
        List<ChatMessage> kept = List.copyOf(history.subList(removed, history.size()));
        clearHistory(sessionId);
        if (summary != null) {
            appendMessage(sessionId, summary);
        }
        for (ChatMessage message : kept) {
            appendMessage(sessionId, message);
        }
        return removed;
    }

    /**
     * 更新会话配置 JSON。
     *
     * <p>默认实现复用 {@link #createSession}；存储实现应覆写为 {@code UPDATE}，
     * 避免连带重写会话创建时间。</p>
     *
     * @param sessionId  会话 ID
     * @param configJson 配置 JSON
     */
    default void updateSessionConfig(String sessionId, String configJson) {
        createSession(sessionId, configJson);
    }

    /**
     * 统计会话在存储中的消息条数。
     *
     * <p>与 {@link #loadHistory} 的长度未必相等：后者会丢弃无法完整重建的消息
     * （缺 {@code tool_call_id} 的 tool 消息等），因此本方法返回的是<b>存储总量</b>。</p>
     *
     * <p>默认实现退化为「加载后计数」，存储实现应覆写为计数查询，
     * 避免为了一次计数把全部消息读入内存。</p>
     *
     * @param sessionId 会话 ID
     * @return 消息条数
     */
    default int countMessages(String sessionId) {
        return loadHistory(sessionId, Integer.MAX_VALUE).size();
    }

    /**
     * 存储记忆。
     *
     * @param sessionId 会话 ID
     * @param entry     记忆条目
     */
    void storeMemory(String sessionId, MemoryEntry entry);

    /**
     * 检索记忆。
     *
     * @param sessionId 会话 ID
     * @param query     查询
     * @param limit     数量上限
     * @return 记忆条目
     */
    List<MemoryEntry> recall(String sessionId, String query, int limit);

    /**
     * 关闭存储。
     */
    void close();
}
