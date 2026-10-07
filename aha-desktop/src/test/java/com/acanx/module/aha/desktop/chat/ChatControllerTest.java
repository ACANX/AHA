package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.common.event.ContentEvent;
import com.acanx.module.aha.common.event.DoneEvent;
import com.acanx.module.aha.common.event.ErrorEvent;
import com.acanx.module.aha.common.event.ToolCallEvent;
import com.acanx.module.aha.common.event.ToolResultEvent;
import com.acanx.module.aha.common.event.UsageEvent;
import com.acanx.module.aha.common.model.SessionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ChatController} 测试：事件流 → 界面动作。
 *
 * <p>执行器与 UI 投递器都注入「同步执行」，于是整条对话链路（发送 → 事件 → 界面动作）
 * 在没有图形环境的机器上也能被完整验证。这是「界面不可本地运行」这一约束下的固定应对。</p>
 *
 * @since 0.2.0
 */
class ChatControllerTest {

    private FakeAgentService service;

    private RecordingChatView view;

    private ChatController controller;

    @BeforeEach
    void setUp() {
        service = new FakeAgentService();
        view = new RecordingChatView();
        controller = new ChatController(view, service,
                new SessionConfig("gpt-4o", null, null), Runnable::run, Runnable::run);
    }

    @Test
    void streamsAssistantTextAndClearsBusyOnDone() {
        service.script.add(new ContentEvent("s1", "你好"));
        service.script.add(new ContentEvent("s1", "，世界"));
        service.script.add(new DoneEvent("s1", "stop"));

        controller.send("  帮我看看代码  ");

        assertThat(view.users).containsExactly("帮我看看代码");
        assertThat(view.deltas).containsExactly("你好", "，世界");
        assertThat(view.ends).isEqualTo(1);
        assertThat(view.busy).containsExactly(true, false);
        assertThat(service.inputs).containsExactly("帮我看看代码");
        assertThat(service.created).hasSize(1);
    }

    @Test
    void toolCallBecomesCardAndResultUpdatesIt() {
        service.script.add(new ToolCallEvent("s1", "file-read", Map.of("path", "AgentEngine.java")));
        service.script.add(new ToolResultEvent("s1", "file-read", "第一行\n第二行\n第三行", true));
        service.script.add(new DoneEvent("s1", "stop"));

        controller.send("读一下文件");

        assertThat(view.cards).containsExactly("读取文件|file-read|AgentEngine.java");
        assertThat(view.cardResults).hasSize(1);
        assertThat(view.cardResults.get(0)).contains("|true|").contains("3 行");
    }

    @Test
    void errorEventShowsMessageAndClearsBusy() {
        service.script.add(new ErrorEvent("s1", "LLM_ERROR", "模型返回 401"));

        controller.send("hi");

        assertThat(view.errors).containsExactly("LLM_ERROR: 模型返回 401");
        assertThat(view.busy).containsExactly(true, false);
    }

    @Test
    void usageEventIsForwarded() {
        service.script.add(new UsageEvent("s1", 12430, 1208));
        service.script.add(new DoneEvent("s1", "stop"));

        controller.send("hi");

        assertThat(view.usage).isEqualTo("12430/1208");
    }

    @Test
    void blankInputIsIgnored() {
        controller.send("   ");
        controller.send(null);

        assertThat(view.users).isEmpty();
        assertThat(service.created).isEmpty();
    }

    @Test
    void cancelCancelsTheTokenOfCurrentTurn() {
        service.script.add(new ContentEvent("s1", "部分"));
        controller.send("hi");
        assertThat(service.lastToken).isNotNull();

        controller.cancel();

        assertThat(service.lastToken.isCancelled()).isTrue();
    }

    @Test
    void sendFailureIsReportedInsteadOfCrashing() {
        service.failOnSend = true;

        controller.send("hi");

        assertThat(view.errors).hasSize(1);
        assertThat(view.errors.get(0)).startsWith("SEND_FAILED");
        assertThat(view.busy).containsExactly(true, false);
    }

    @Test
    void newSessionClosesPreviousSession() {
        controller.send("first");
        String first = controller.sessionId();

        String second = controller.newSession();

        assertThat(service.closed).containsExactly(first);
        assertThat(second).isNotEqualTo(first);
        assertThat(service.created).hasSize(2);
    }

    @Test
    void closeReleasesSession() {
        controller.send("first");
        String created = controller.sessionId();

        controller.close();

        assertThat(service.closed).containsExactly(created);
        assertThat(controller.sessionId()).isNull();
    }

    @Test
    void sessionIsReusedAcrossTurns() {
        controller.send("one");
        controller.send("two");

        assertThat(service.created).hasSize(1);
        assertThat(service.inputs).containsExactly("one", "two");
    }

    @Test
    void toolResultWithoutCardIsIgnored() {
        service.script.add(new ToolResultEvent("s1", "file-read", "x", true));

        controller.send("hi");

        assertThat(view.cardResults).isEmpty();
    }
}
