package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.common.event.ContentEvent;
import com.acanx.module.aha.common.event.ContentSegmentEndEvent;
import com.acanx.module.aha.common.event.DoneEvent;
import com.acanx.module.aha.common.event.ErrorEvent;
import com.acanx.module.aha.common.event.ToolCallEvent;
import com.acanx.module.aha.common.event.ToolResultEvent;
import com.acanx.module.aha.common.event.UsageEvent;
import com.acanx.module.aha.common.model.SessionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

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

    /** 可控计时源：默认不流逝，于是耗时断言与机器快慢无关。 */
    private final AtomicLong nanos = new AtomicLong();

    @BeforeEach
    void setUp() {
        service = new FakeAgentService();
        view = new RecordingChatView();
        controller = new ChatController(view, service,
                new SessionConfig("gpt-4o", null, null), Runnable::run, Runnable::run, nanos::get);
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

    /**
     * 段边界把一轮正文切开（issue #403）。
     *
     * <p>回归的是「总结被追加到开场白之后、工具卡片之上」：旧实现只在 DoneEvent 才
     * {@code endAssistant()}，于是 A 与 C 落进同一段，C 的位置被 A 的气泡钉死在卡片之前。
     * 现在段边界一到就结束当前段，视图据此另起气泡，顺序等于时间顺序。</p>
     */
    @Test
    void segmentBoundarySplitsPreambleFromConclusionAroundTheToolCard() {
        service.script.add(new ContentEvent("s1", "先看看代码"));
        service.script.add(new ContentSegmentEndEvent("s1"));
        service.script.add(new ToolCallEvent("s1", "file-read", Map.of("path", "AgentEngine.java")));
        service.script.add(new ToolResultEvent("s1", "file-read", "第一行", true));
        service.script.add(new ContentEvent("s1", "看完了，结论是……"));
        service.script.add(new ContentSegmentEndEvent("s1"));
        service.script.add(new DoneEvent("s1", "stop"));

        controller.send("读一下文件");

        // 开场白与总结分属两段：正文增量不混段。
        // 段结束共 3 次 = 两条段边界 + Done 的幂等收尾（见 AssistantSegmentsTest#endIsIdempotent）
        assertThat(view.deltas).containsExactly("先看看代码", "看完了，结论是……");
        assertThat(view.ends).isEqualTo(3);
        // 顺序：开场白段结束 → 卡片 → 总结段结束（Done 再收一次尾，是幂等的）
        assertThat(view.timeline)
                .containsExactly("end-assistant", "tool-card", "end-assistant", "end-assistant");
    }

    /**
     * 连续两段纯正文（中间没有工具调用）也必须分段。
     *
     * <p>这正是「方案 2（用工具事件推断边界）」会失效的场景：模型有时先说一句、
     * 再说一段，中间并不调工具。</p>
     */
    @Test
    void consecutiveTextSegmentsWithoutToolsAreStillSplit() {
        service.script.add(new ContentEvent("s1", "第一段"));
        service.script.add(new ContentSegmentEndEvent("s1"));
        service.script.add(new ContentEvent("s1", "第二段"));
        service.script.add(new ContentSegmentEndEvent("s1"));
        service.script.add(new DoneEvent("s1", "stop"));

        controller.send("说两句");

        assertThat(view.deltas).containsExactly("第一段", "第二段");
        // 段边界两次 + Done 一次
        assertThat(view.ends).isEqualTo(3);
    }

    /**
     * 正文增量与段收尾必须走**同一条通道**（PR #404 审查 P1-1）。
     *
     * <p>旧实现里正文直达视图、段收尾绕 {@code ui} 队列。两条通道各自有序、合起来无序：
     * 推理线程投递完段边界会立刻处理下一条增量，而队列里的 endAssistant 还没跑，
     * 于是下一段的增量落进上一段、段号不推进——界面退回 #403 修复前的样子。</p>
     *
     * <p>这里用一个「只排队、不立即执行」的投递器模拟真实的 UI 线程：若段收尾还在队列里，
     * {@code view.endAssistant} 的调用次数就不会在增量到达之前增加。</p>
     */
    @Test
    void segmentBoundaryReachesTheViewBeforeTheNextSegmentDelta() {
        Deque<Runnable> uiQueue = new ArrayDeque<>();
        ChatController queued = new ChatController(view, service,
                new SessionConfig("gpt-4o", null, null), Runnable::run, uiQueue::add, nanos::get);
        service.script.add(new ContentEvent("s1", "第一段"));
        service.script.add(new ContentSegmentEndEvent("s1"));
        service.script.add(new ContentEvent("s1", "第二段"));
        service.script.add(new DoneEvent("s1", "stop"));

        queued.send("说两句");

        // 段收尾不排队：它必须已经生效，否则第二段的增量会落进第一段
        assertThat(view.ends).isGreaterThanOrEqualTo(2);
        assertThat(view.deltas).containsExactly("第一段", "第二段");
        // 排队的是其余界面动作（用户消息、忙碌状态、错误提示等），不是段收尾
        assertThat(uiQueue).isNotEmpty();
    }

    /**
     * 发送失败也要收尾当前段（PR #404 审查 P1-2）。
     *
     * <p>残段留在缓冲里的话，用户修好网络再发一条，新正文会续写到失败前那条气泡上。</p>
     */
    @Test
    void sendFailureStillClosesThePendingSegment() {
        // 传到一半断线：正文已经进了气泡，但段边界可能来不及发（真正的引擎把它放进了
        // finally，AgentService 的可替换实现则不保证）——控制器必须自己兜底收尾
        service.script.add(new ContentEvent("s1", "说到一半就断了"));
        service.failMidStream = true;

        controller.send("hi");

        assertThat(view.deltas).containsExactly("说到一半就断了");
        assertThat(view.errors).hasSize(1);
        assertThat(view.errors.get(0)).startsWith("SEND_FAILED");
        // 兜底收尾一次：残段不会留着，下一条提问不会续写到失败前的气泡上
        assertThat(view.ends).isEqualTo(1);
        assertThat(view.busy).containsExactly(true, false);
    }

    @Test
    void toolCallBecomesCardAndResultUpdatesIt() {
        service.script.add(new ToolCallEvent("s1", "file-read", Map.of("path", "AgentEngine.java")));
        service.script.add(new ToolResultEvent("s1", "file-read", "第一行\n第二行\n第三行", true));
        service.script.add(new DoneEvent("s1", "stop"));

        controller.send("读一下文件");

        assertThat(view.cards)
                .containsExactly("读取文件|file-read|AgentEngine.java|{path=AgentEngine.java}");
        assertThat(view.cardResults).hasSize(1);
        // 句柄 | 成功 | 耗时毫秒 | 输出行数
        assertThat(view.cardResults.get(0)).isEqualTo("1|true|0|3");
    }

    @Test
    void toolDurationIsMeasuredFromCallToResult() {
        // 每次读时钟都前进 125ms：于是「调用 → 结果」之间恰好 125ms。
        // 用可控时钟而不是 sleep，测试才不会随机器快慢漂移。
        ChatController ticking = new ChatController(view, service,
                new SessionConfig("gpt-4o", null, null), Runnable::run, Runnable::run,
                () -> nanos.addAndGet(125_000_000L));
        service.script.add(new ToolCallEvent("s1", "file-read", Map.of("path", "a.txt")));
        service.script.add(new ToolResultEvent("s1", "file-read", "x", true));
        service.script.add(new DoneEvent("s1", "stop"));

        ticking.send("读一下文件");

        assertThat(view.cardResults).containsExactly("1|true|125|1");
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
        // 懒创建：newSession 不落库，首次发送时才创建
        assertThat(second).isNull();
        assertThat(controller.sessionId()).isNull();
        assertThat(service.created).hasSize(1);
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
    void openSessionReplaysHistoryIntoTheView() {
        service.histories.put("S1", java.util.List.of(
                com.acanx.module.aha.core.llm.protocol.ChatMessage.text(
                        com.acanx.module.aha.core.llm.protocol.Role.USER, "第一句"),
                com.acanx.module.aha.core.llm.protocol.ChatMessage.text(
                        com.acanx.module.aha.core.llm.protocol.Role.ASSISTANT, "第一答"),
                new com.acanx.module.aha.core.llm.protocol.ChatMessage(
                        com.acanx.module.aha.core.llm.protocol.Role.TOOL, "输出内容",
                        java.util.List.of(), "call_1", null),
                com.acanx.module.aha.core.llm.protocol.ChatMessage.text(
                        com.acanx.module.aha.core.llm.protocol.Role.SYSTEM, "系统提示")));

        int shown = controller.openSession("S1");

        assertThat(shown).isEqualTo(4);
        assertThat(view.clears).isEqualTo(1);
        assertThat(view.users).containsExactly("第一句");
        assertThat(view.deltas).containsExactly("第一答");
        assertThat(view.notices).containsExactly("工具输出：输出内容", "系统提示");
        assertThat(view.ends).isEqualTo(1);
        assertThat(controller.sessionId()).isEqualTo("S1");
    }

    @Test
    void openSessionSkipsMessagesWithoutBody() {
        service.histories.put("S2", java.util.List.of(
                new com.acanx.module.aha.core.llm.protocol.ChatMessage(
                        com.acanx.module.aha.core.llm.protocol.Role.ASSISTANT, "  ",
                        java.util.List.of(
                                new com.acanx.module.aha.core.llm.protocol.ToolCall(
                                        "call_1", "file-read", java.util.Map.of())),
                        null, null)));

        assertThat(controller.openSession("S2")).isZero();
        assertThat(view.notices).isEmpty();
    }

    @Test
    void switchingSessionClosesThePreviousOne() {
        controller.send("hello");
        String first = controller.sessionId();

        controller.openSession("S9");

        assertThat(service.closed).contains(first);
        assertThat(controller.sessionId()).isEqualTo("S9");
    }

    @Test
    void sessionListAndRenameAndDeleteAreForwarded() {
        service.sessions.add(new com.acanx.module.aha.common.model.SessionSummary(
                "S1", "看看代码", 0, 3));

        assertThat(controller.sessions()).hasSize(1);
        assertThat(controller.titleOf("S1")).isEqualTo("看看代码");
        assertThat(controller.titleOf("不存在"))
                .isEqualTo(com.acanx.module.aha.common.model.SessionSummary.UNTITLED);

        controller.renameSession("S1", "新名字");
        assertThat(service.renamed).containsExactly("S1=新名字");

        controller.send("hi");
        String current = controller.sessionId();
        controller.deleteSession("S1");
        controller.deleteSession(current);
        assertThat(service.deleted).containsExactly("S1", current);
        assertThat(controller.sessionId())
                .as("删掉的正是当前会话时，不能再把消息写进一个已删除的会话")
                .isNull();
    }

    @Test
    void exportProducesMarkdownAndJson() {
        service.sessions.add(new com.acanx.module.aha.common.model.SessionSummary(
                "S1", "看看代码", 0, 1));
        service.histories.put("S1", java.util.List.of(
                com.acanx.module.aha.core.llm.protocol.ChatMessage.text(
                        com.acanx.module.aha.core.llm.protocol.Role.USER, "你好")));

        assertThat(controller.export("S1", true))
                .contains("# 看看代码").contains("## 你").contains("你好");
        assertThat(controller.export("S1", false))
                .contains("\"sessionId\": \"S1\"").contains("你好");
    }

    @Test
    void toolResultWithoutCardIsIgnored() {
        service.script.add(new ToolResultEvent("s1", "file-read", "x", true));

        controller.send("hi");

        assertThat(view.cardResults).isEmpty();
    }
}
