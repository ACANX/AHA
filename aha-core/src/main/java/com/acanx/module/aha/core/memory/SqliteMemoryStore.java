package com.acanx.module.aha.core.memory;

import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.common.model.MemoryEntry;
import com.acanx.module.aha.common.model.SessionSummary;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.ToolCall;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 基于 SQLite 的记忆存储。
 *
 * <p>表名与字段名使用 snake_case，表名使用单数形式（{@code session} / {@code message} / {@code memory}），
 * 时间字段沿用 {@code gmt_create}。</p>
 *
 * @since 0.1.0
 */
public final class SqliteMemoryStore implements MemoryStore {

    /** 工具调用列表的 JSON 序列化器。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path dbPath;
    private Connection connection;

    /**
     * 构造 SQLite 存储。
     *
     * @param dbPath 数据库文件路径
     */
    public SqliteMemoryStore(Path dbPath) {
        this.dbPath = dbPath;
    }

    @Override
    public void init() {
        try {
            if (dbPath.getParent() != null) {
                Files.createDirectories(dbPath.getParent());
            }
            if (connection == null || connection.isClosed()) {
                connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            }
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("PRAGMA journal_mode=WAL");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS session (
                            id          TEXT PRIMARY KEY,
                            gmt_create  INTEGER NOT NULL,
                            config_json TEXT NOT NULL
                        )""");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS message (
                            id           INTEGER PRIMARY KEY AUTOINCREMENT,
                            session_id   TEXT NOT NULL REFERENCES session(id),
                            role         TEXT NOT NULL,
                            content      TEXT NOT NULL,
                            tool_calls   TEXT,
                            tool_call_id TEXT,
                            gmt_create   INTEGER NOT NULL
                        )""");
                // 兼容早期版本创建的表（缺 tool_calls / tool_call_id 列）。
                // 这两个字段必须持久化，否则多轮对话从历史恢复后，
                // assistant 的 tool_calls 与 tool 消息的 tool_call_id 丢失，
                // 请求体违反 OpenAI 协议（tool 消息必须带 tool_call_id），服务端返回 422。
                ensureColumn(st, "message", "tool_calls", "TEXT");
                ensureColumn(st, "message", "tool_call_id", "TEXT");
                // 会话标题（用户重命名用）。老实现在这里新增列，靠 ensureColumn 迁移；
                // 会话表原本没有标题列，而 CREATE TABLE IF NOT EXISTS 对已存在的表不生效。
                ensureColumn(st, "session", "title", "TEXT");
                st.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS memory (
                            id          INTEGER PRIMARY KEY AUTOINCREMENT,
                            session_id  TEXT NOT NULL REFERENCES session(id),
                            key         TEXT NOT NULL,
                            value       TEXT NOT NULL,
                            embedding   BLOB,
                            gmt_create  INTEGER NOT NULL
                        )""");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_message_session "
                        + "ON message(session_id, gmt_create)");
                st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_memory_session "
                        + "ON memory(session_id, key)");
            }
        } catch (SQLException | IOException e) {
            throw new AhaException("DB_INIT_FAILED", "SQLite 初始化失败: " + dbPath, e);
        }
    }

    /**
     * 返回底层连接。
     *
     * @return 连接
     */
    public Connection connection() {
        return connection;
    }

    @Override
    public void createSession(String sessionId, String configJson) {
        String sql = "INSERT OR REPLACE INTO session(id, gmt_create, config_json) VALUES(?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, configJson == null ? "{}" : configJson);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AhaException("DB_SESSION_FAILED", "写入会话失败: " + sessionId, e);
        }
    }

    @Override
    public String loadSession(String sessionId) {
        String sql = "SELECT config_json FROM session WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("config_json") : null;
            }
        } catch (SQLException e) {
            throw new AhaException("DB_SESSION_FAILED", "读取会话失败: " + sessionId, e);
        }
    }

    @Override
    public void appendMessage(String sessionId, ChatMessage message) {
        String sql = "INSERT INTO message(session_id, role, content, tool_calls, tool_call_id, gmt_create) "
                + "VALUES(?,?,?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, message.role() == null ? Role.USER.name() : message.role().name());
            ps.setString(3, message.content() == null ? "" : message.content());
            ps.setString(4, serializeToolCalls(message.toolCalls()));
            ps.setString(5, message.toolCallId());
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AhaException("DB_APPEND_FAILED", "写入消息失败: " + sessionId, e);
        }
    }

    @Override
    public List<ChatMessage> loadHistory(String sessionId, int limit) {
        // 先按时间倒序取最近 limit 条，再翻回正序：直接 ASC + LIMIT 会得到最旧的历史，
        // 会话超出窗口后模型就再也看不到新消息。
        String sql = "SELECT role, content, tool_calls, tool_call_id FROM ("
                + "SELECT id, role, content, tool_calls, tool_call_id, gmt_create FROM message "
                + "WHERE session_id = ? ORDER BY gmt_create DESC, id DESC LIMIT ?) "
                + "ORDER BY gmt_create ASC, id ASC";
        List<ChatMessage> history = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setInt(2, limit > 0 ? limit : 100);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Role role = parseRole(rs.getString("role"));
                    String content = rs.getString("content");
                    List<ToolCall> calls = deserializeToolCalls(rs.getString("tool_calls"));
                    String toolCallId = rs.getString("tool_call_id");

                    // 丢弃无法完整重建的消息，避免向服务端发送非法请求体：
                    //   - tool 消息缺 tool_call_id（早版本未持久化）
                    //   - assistant 既无 tool_calls 又无正文（工具调用信息丢失后的残留）
                    // 二者都会触发服务端 422，且无法通过重试恢复。
                    if (role == Role.TOOL && (toolCallId == null || toolCallId.isBlank())) {
                        continue;
                    }
                    if (role == Role.ASSISTANT && calls.isEmpty()
                            && (content == null || content.isBlank())) {
                        continue;
                    }
                    history.add(new ChatMessage(role, content, calls, toolCallId, null));
                }
            }
        } catch (SQLException e) {
            throw new AhaException("DB_LOAD_FAILED", "读取消息失败: " + sessionId, e);
        }
        return history;
    }

    @Override
    public void clearHistory(String sessionId) {
        try (PreparedStatement ps = connection.prepareStatement(
                "DELETE FROM message WHERE session_id = ?")) {
            ps.setString(1, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AhaException("DB_DELETE_FAILED", "清空消息失败: " + sessionId, e);
        }
    }

    @Override
    public void updateSessionConfig(String sessionId, String configJson) {
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE session SET config_json = ? WHERE id = ?")) {
            ps.setString(1, configJson == null ? "{}" : configJson);
            ps.setString(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AhaException("DB_SESSION_FAILED", "更新会话配置失败: " + sessionId, e);
        }
    }

    @Override
    public int countMessages(String sessionId) {
        String sql = "SELECT COUNT(*) FROM message WHERE session_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new AhaException("DB_LOAD_FAILED", "统计消息数量失败: " + sessionId, e);
        }
    }

    @Override
    public void storeMemory(String sessionId, MemoryEntry entry) {
        String sql = "INSERT INTO memory(session_id, key, value, gmt_create) VALUES(?,?,?,?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sessionId);
            ps.setString(2, entry.key());
            ps.setString(3, entry.value());
            ps.setLong(4, entry.createdAt() > 0 ? entry.createdAt() : System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AhaException("DB_MEMORY_FAILED", "写入记忆失败: " + sessionId, e);
        }
    }

    @Override
    public List<MemoryEntry> recall(String sessionId, String query, int limit) {
        boolean hasQuery = query != null && !query.isBlank();
        String sql = hasQuery
                ? "SELECT key, value, gmt_create FROM memory WHERE session_id = ? "
                        + "AND (key LIKE ? OR value LIKE ?) ORDER BY gmt_create DESC LIMIT ?"
                : "SELECT key, value, gmt_create FROM memory WHERE session_id = ? "
                        + "ORDER BY gmt_create DESC LIMIT ?";
        List<MemoryEntry> entries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int index = 1;
            ps.setString(index++, sessionId);
            if (hasQuery) {
                String like = "%" + query + "%";
                ps.setString(index++, like);
                ps.setString(index++, like);
            }
            ps.setInt(index, limit > 0 ? limit : 10);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new MemoryEntry(
                            rs.getString("key"), rs.getString("value"), rs.getLong("gmt_create")));
                }
            }
        } catch (SQLException e) {
            throw new AhaException("DB_RECALL_FAILED", "检索记忆失败: " + sessionId, e);
        }
        return entries;
    }

    @Override
    public List<SessionSummary> listSessions() {
        // 标题：用户重命名过就用它；否则取该会话的首条用户消息（比显示 UUID 有用）。
        // 一次查询取全，避免 N+1——列表每次刷新都会跑它。
        String sql = "SELECT s.id AS id, s.gmt_create AS created, s.title AS title,"
                + " (SELECT COUNT(*) FROM message m WHERE m.session_id = s.id) AS cnt,"
                + " (SELECT m2.content FROM message m2 WHERE m2.session_id = s.id"
                + "   AND m2.role = 'USER' ORDER BY m2.id LIMIT 1) AS first_user"
                + " FROM session s ORDER BY s.gmt_create DESC";
        List<SessionSummary> summaries = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String explicit = rs.getString("title");
                String title = explicit == null || explicit.isBlank()
                        ? rs.getString("first_user")
                        : explicit;
                summaries.add(new SessionSummary(
                        rs.getString("id"), title, rs.getLong("created"), rs.getInt("cnt")));
            }
        } catch (SQLException e) {
            throw new AhaException("DB_LIST_FAILED", "列出会话失败", e);
        }
        return summaries;
    }

    @Override
    public void updateSessionTitle(String sessionId, String title) {
        String sql = "UPDATE session SET title = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, title == null || title.isBlank() ? null : title.strip());
            ps.setString(2, sessionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new AhaException("DB_RENAME_FAILED", "重命名会话失败: " + sessionId, e);
        }
    }

    /**
     * 删除会话及其消息与记忆。
     *
     * @param sessionId 会话 ID
     */
    @Override
    public void deleteSession(String sessionId) {
        try (PreparedStatement m = connection.prepareStatement("DELETE FROM message WHERE session_id = ?");
             PreparedStatement mem = connection.prepareStatement("DELETE FROM memory WHERE session_id = ?");
             PreparedStatement s = connection.prepareStatement("DELETE FROM session WHERE id = ?")) {
            m.setString(1, sessionId);
            m.executeUpdate();
            mem.setString(1, sessionId);
            mem.executeUpdate();
            s.setString(1, sessionId);
            s.executeUpdate();
        } catch (SQLException e) {
            throw new AhaException("DB_DELETE_FAILED", "删除会话失败: " + sessionId, e);
        }
    }

    @Override
    public void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new AhaException("DB_CLOSE_FAILED", "SQLite 关闭失败", e);
            }
        }
    }

    /**
     * 兼容增减列：若表中不存在该列则追加。
     *
     * <p>{@code CREATE TABLE IF NOT EXISTS} 对已存在的表不生效，因此新增字段需单独迁移。</p>
     *
     * @param st     语句对象
     * @param table  表名
     * @param column 列名
     * @param type   列类型
     * @throws SQLException 迁移失败
     */
    private static void ensureColumn(Statement st, String table, String column, String type)
            throws SQLException {
        try (ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("name"))) {
                    return;
                }
            }
        }
        st.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
    }

    /**
     * 序列化工具调用列表。
     *
     * @param calls 工具调用，可为 {@code null}
     * @return JSON 字符串；无调用时返回 {@code null}
     */
    private static String serializeToolCalls(List<ToolCall> calls) {
        if (calls == null || calls.isEmpty()) {
            return null;
        }
        return JSON.writeValueAsString(calls);
    }

    /**
     * 反序列化工具调用列表。
     *
     * <p>损坏或旧格式数据退化为“无工具调用”，不让整个会话不可用。</p>
     *
     * @param json JSON 字符串
     * @return 工具调用列表，无数据时为空列表
     */
    private static List<ToolCall> deserializeToolCalls(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<ToolCall>>() { });
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    private static Role parseRole(String role) {
        if (role == null) {
            return Role.USER;
        }
        try {
            return Role.valueOf(role);
        } catch (IllegalArgumentException e) {
            return Role.USER;
        }
    }
}
