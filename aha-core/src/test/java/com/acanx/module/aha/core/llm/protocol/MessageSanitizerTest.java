package com.acanx.module.aha.core.llm.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link MessageSanitizer} 测试（issue #391）。
 *
 * <p>锁定四条规则：孤立 tool 丢弃、未响应的 tool_calls 剔除、正文保留策略、
 * 正常序列不被改动。</p>
 *
 * @since 0.1.5
 */
class MessageSanitizerTest {

    private static ToolCall call(String id) {
        return new ToolCall(id, "file-read", Map.of("path", "AhaUpdate.sh"));
    }

    private static ChatMessage assistant(String content, String... callIds) {
        List<ToolCall> calls = java.util.Arrays.stream(callIds).map(MessageSanitizerTest::call).toList();
        return new ChatMessage(Role.ASSISTANT, content, calls, null, null);
    }

    private static ChatMessage tool(String id) {
        return new ChatMessage(Role.TOOL, "result-" + id, List.of(), id, null);
    }

    @Test
    void dropsOrphanToolMessageAtWindowStart() {
        // loadHistory 的 LIMIT 窗口把 assistant 截在窗外，只剩两条 tool
        List<ChatMessage> result = MessageSanitizer.sanitize(List.of(
                tool("call-1"), tool("call-2")));

        assertThat(result).isEmpty();
    }

    @Test
    void dropsToolWhoseIdIsNotInPrecedingToolCalls() {
        List<ChatMessage> result = MessageSanitizer.sanitize(List.of(
                ChatMessage.text(Role.USER, "看看文件"),
                assistant("", "call-1"),
                tool("call-1"),
                tool("call-999")));

        assertThat(result).extracting(ChatMessage::role)
                .containsExactly(Role.USER, Role.ASSISTANT, Role.TOOL);
        assertThat(result.get(2).toolCallId()).isEqualTo("call-1");
    }

    @Test
    void keepsCompletePairUntouched() {
        List<ChatMessage> messages = List.of(
                ChatMessage.text(Role.SYSTEM, "sys"),
                ChatMessage.text(Role.USER, "读文件"),
                assistant("", "call-1", "call-2"),
                tool("call-1"),
                tool("call-2"),
                ChatMessage.text(Role.ASSISTANT, "读完了"));

        List<ChatMessage> result = MessageSanitizer.sanitize(messages);

        assertThat(result).containsExactlyElementsOf(messages);
    }

    @Test
    void trimsUnansweredToolCalls() {
        // 中断场景：assistant 带了两个 tool_calls 落库，只有一个 tool 结果
        List<ChatMessage> result = MessageSanitizer.sanitize(List.of(
                ChatMessage.text(Role.USER, "读两个文件"),
                assistant("", "call-1", "call-2"),
                tool("call-1")));

        assertThat(result).hasSize(3);
        assertThat(result.get(1).toolCalls()).extracting(ToolCall::id).containsExactly("call-1");
    }

    @Test
    void degradesToTextAssistantWhenNoToolWasAnswered() {
        // 正文非空：保留 assistant，但清掉没人回应的 tool_calls
        List<ChatMessage> result = MessageSanitizer.sanitize(List.of(
                ChatMessage.text(Role.USER, "继续"),
                assistant("我先看看", "call-1")));

        assertThat(result).hasSize(2);
        assertThat(result.get(1).content()).isEqualTo("我先看看");
        assertThat(result.get(1).toolCalls()).isEmpty();
    }

    @Test
    void removesEmptyAssistantWithoutAnyToolAnswer() {
        List<ChatMessage> result = MessageSanitizer.sanitize(List.of(
                ChatMessage.text(Role.USER, "继续"),
                assistant("", "call-1")));

        assertThat(result).extracting(ChatMessage::role).containsExactly(Role.USER);
    }

    @Test
    void userMessageBreaksPairing() {
        // tool 必须紧跟它所属的 assistant；中间夹了 user 就说明序列已经坏了
        List<ChatMessage> result = MessageSanitizer.sanitize(List.of(
                assistant("我先看看", "call-1"),
                ChatMessage.text(Role.USER, "等一下"),
                tool("call-1")));

        assertThat(result).extracting(ChatMessage::role)
                .containsExactly(Role.ASSISTANT, Role.USER);
        assertThat(result.get(0).toolCalls()).isEmpty();
    }

    @Test
    void ignoresNullEntriesAndNullRoles() {
        List<ChatMessage> result = MessageSanitizer.sanitize(java.util.Arrays.asList(
                null,
                new ChatMessage(null, "x", List.of(), null, null),
                ChatMessage.text(Role.USER, "hi")));

        assertThat(result).extracting(ChatMessage::role).containsExactly(Role.USER);
    }

    @Test
    void emptyAndNullInputStayEmpty() {
        assertThat(MessageSanitizer.sanitize(null)).isEmpty();
        assertThat(MessageSanitizer.sanitize(List.of())).isEmpty();
    }

    @Test
    void isIdempotent() {
        List<ChatMessage> once = MessageSanitizer.sanitize(List.of(
                ChatMessage.text(Role.USER, "u"),
                assistant("", "call-1", "call-2"),
                tool("call-2"),
                ChatMessage.text(Role.ASSISTANT, "done")));

        assertThat(MessageSanitizer.sanitize(once)).containsExactlyElementsOf(once);
    }
}
