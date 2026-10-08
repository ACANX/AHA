package com.acanx.module.aha.core.service;

import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.agent.InMemoryMemoryStore;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SessionManager} 测试。
 *
 * @since 0.1.0
 */
class SessionManagerTest {

    @Test
    void createsSessionWithGeneratedId() {
        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        SessionManager manager = new SessionManager(memory);

        String first = manager.create(new SessionConfig("m", "sys", Map.of()));
        String second = manager.create(new SessionConfig("m", null, Map.of()));

        assertThat(first).isNotBlank().isNotEqualTo(second);
        assertThat(manager.exists(first)).isTrue();
        assertThat(manager.get(first)).contains(new SessionConfig("m", "sys", Map.of()));
        // 会话已落库
        assertThat(memory.createdSessions()).contains(first, second);
    }

    @Test
    void updatePersistsNewConfigAndKeepsSession() {
        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        SessionManager manager = new SessionManager(memory);
        String id = manager.create(new SessionConfig("m", "身份", Map.of("k", "v")));

        boolean updated = manager.update(id, new SessionConfig("m2", "身份", Map.of("k", "v")));

        assertThat(updated).isTrue();
        assertThat(manager.get(id)).contains(new SessionConfig("m2", "身份", Map.of("k", "v")));
        // 已落盘：清掉内存缓存后仍能恢复（走的是 MemoryStore 的默认实现）
        manager.close(id);
        assertThat(manager.get(id)).contains(new SessionConfig("m2", "身份", Map.of("k", "v")));
    }

    @Test
    void updateOnUnknownSessionIsRejected() {
        SessionManager manager = new SessionManager(new InMemoryMemoryStore());
        assertThat(manager.update("no-such-session", new SessionConfig("m", null, Map.of()))).isFalse();
    }

    @Test
    void closeReleasesActiveSessionButKeepsItRecoverable() {
        // 语义：close = 结束活跃会话（释放内存），而非删除记录。
        // 持久化记录保留，以便 `--session <id>` 续用。
        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        SessionManager manager = new SessionManager(memory);
        String id = manager.create(new SessionConfig("m", "身份", Map.of()));

        manager.close(id);

        assertThat(manager.exists(id)).isFalse();
        assertThat(manager.get(id)).contains(new SessionConfig("m", "身份", Map.of()));
    }

    @Test
    void unknownSessionIsAbsent() {
        SessionManager manager = new SessionManager(new InMemoryMemoryStore());

        assertThat(manager.get("nope")).isEmpty();
        assertThat(manager.exists("nope")).isFalse();
    }

    @Test
    void sessionConfigIsPersistedOnCreate() {
        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        SessionManager manager = new SessionManager(memory);

        String id = manager.create(new SessionConfig("deepseek-chat", "你是审查员", Map.of()));

        assertThat(memory.sessionConfig(id))
                .contains("\"SystemPrompt\"")
                .contains("你是审查员")
                .contains("deepseek-chat");
    }

    @Test
    void getRestoresSessionConfigFromStorage() {
        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        SessionManager manager = new SessionManager(memory);
        String id = manager.create(new SessionConfig("m", "固化的身份", Map.of()));

        // 模拟进程重启：新管理器仅有持久化存储，无内存缓存
        manager.close(id);
        SessionManager restarted = new SessionManager(memory);

        assertThat(restarted.exists(id)).isFalse();
        assertThat(restarted.get(id)).contains(new SessionConfig("m", "固化的身份", Map.of()));
        // 恢复后进入缓存
        assertThat(restarted.exists(id)).isTrue();
    }


    @Test
    void listRenameAndDeleteDelegateToStore() {
        InMemoryMemoryStore memory = new InMemoryMemoryStore();
        SessionManager manager = new SessionManager(memory);
        String id = manager.create(new SessionConfig("m", null, Map.of()));

        assertThat(manager.list()).isEmpty();

        manager.rename(id, "新标题");
        manager.delete(id);

        // 删除后不再是活跃会话，也不应再能取到配置
        assertThat(manager.exists(id)).isFalse();
        assertThat(memory.loadSession(id)).isNull();
    }
}
