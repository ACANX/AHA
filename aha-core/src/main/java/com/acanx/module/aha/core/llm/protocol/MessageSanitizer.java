package com.acanx.module.aha.core.llm.protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 消息序列协议校验：修掉「tool 消息与 assistant.tool_calls 不配对」的历史（issue #391）。
 *
 * <p>【为什么需要（2026-10-20 真机）】{@code loadHistory} 按 {@code LIMIT} 取最近 N 条再翻回正序，
 * 切割边界可能正好落在 tool 消息上：{@code assistant(tool_calls)} 被截在窗口外、它的 tool 结果
 * 留在窗口内。下一轮请求因此以 {@code role=tool} 开头，上游直接 400：</p>
 *
 * <pre>
 * Messages with role 'tool' must be a response to a preceding message with 'tool_calls'
 * </pre>
 *
 * <p>同一约束在 {@code ContextCompactor#boundary()} 里已经处理过（压缩时不把工具调用与结果切开），
 * 但 {@code loadHistory} 的窗口没有 —— 同一规则两处实现，漏了一处。本类把这条规则收敛成
 * 一个纯函数，所有「历史 → 请求体」的路径共用。</p>
 *
 * <p>处理规则（输出保证可被 OpenAI / Anthropic / Gemini 兼容上游接受）：</p>
 * <ol>
 *   <li>tool 消息的 {@code tool_call_id} 必须能在**它之前**的 assistant.tool_calls 中找到，
 *       否则丢弃（窗口截断、id 损坏、或用户/系统消息打断了配对）；</li>
 *   <li>assistant.tool_calls 里没有被任何 tool 消息回应的调用被剔除；若剔除后一个不剩：
 *       有正文则退化为纯文本 assistant，无正文则整条丢弃（工具调用信息已不可用）；</li>
 *   <li>user / system 消息视为协议边界：越过边界后不再认领前面的 tool 消息。</li>
 * </ol>
 *
 * <p>纯函数、无副作用、幂等：对已经合法的序列调用不会改动任何消息（对象引用也保持不变）。</p>
 *
 * @since 0.1.5
 */
public final class MessageSanitizer {

    private MessageSanitizer() {
    }

    /**
     * 按工具调用配对规则清洗消息序列。
     *
     * @param messages 原始消息（可为 {@code null}）
     * @return 清洗后的新列表；已合法时与原列表逐条相同
     */
    public static List<ChatMessage> sanitize(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> result = new ArrayList<>(messages.size());
        // 当前活跃 assistant 在 result 中的下标 → 已收到响应的 tool_call id
        Map<Integer, Set<String>> answered = new LinkedHashMap<>();
        Set<String> expected = new LinkedHashSet<>();
        int assistantIndex = -1;

        for (ChatMessage message : messages) {
            if (message == null || message.role() == null) {
                continue;
            }
            if (message.role() == Role.TOOL) {
                String id = message.toolCallId();
                if (id == null || id.isBlank() || !expected.remove(id)) {
                    // 孤立 tool：前置 assistant.tool_calls 被窗口截断，或 id 根本不在其中
                    continue;
                }
                answered.computeIfAbsent(assistantIndex, key -> new LinkedHashSet<>()).add(id);
                result.add(message);
                continue;
            }

            // 遇到非 tool 消息：先结算上一个 assistant 的 tool_calls 缺口，再开新的配对段
            settle(result, assistantIndex, answered);
            assistantIndex = -1;
            expected.clear();
            if (message.role() == Role.ASSISTANT && message.toolCalls() != null
                    && !message.toolCalls().isEmpty()) {
                expected.addAll(callIds(message.toolCalls()));
                assistantIndex = result.size();
            }
            result.add(message);
        }
        settle(result, assistantIndex, answered);
        return result;
    }

    /**
     * 结算一条 assistant：把没被响应的 tool_calls 剔除。
     *
     * @param result   正在构建的结果列表
     * @param index    assistant 在结果列表中的下标；{@code < 0} 表示没有待结算的 assistant
     * @param answered 每个 assistant 已收到响应的调用 ID
     */
    private static void settle(List<ChatMessage> result, int index, Map<Integer, Set<String>> answered) {
        if (index < 0 || index >= result.size()) {
            return;
        }
        ChatMessage assistant = result.get(index);
        List<ToolCall> calls = assistant.toolCalls();
        if (calls == null || calls.isEmpty()) {
            return;
        }
        Set<String> replied = answered.getOrDefault(index, Set.of());
        List<ToolCall> kept = calls.stream()
                .filter(call -> call != null && call.id() != null && replied.contains(call.id()))
                .toList();
        if (kept.size() == calls.size()) {
            return;
        }
        if (kept.isEmpty()) {
            if (assistant.content() == null || assistant.content().isBlank()) {
                // 完全没被响应、也没有正文：整条丢弃（它后面不会留下有效的 tool 消息）
                result.remove(index);
            } else {
                result.set(index, new ChatMessage(assistant.role(), assistant.content(),
                        List.of(), null, assistant.reasoningContent()));
            }
            return;
        }
        result.set(index, new ChatMessage(assistant.role(), assistant.content(),
                kept, null, assistant.reasoningContent()));
    }

    private static List<String> callIds(List<ToolCall> calls) {
        List<String> ids = new ArrayList<>(calls.size());
        for (ToolCall call : calls) {
            if (call != null && call.id() != null && !call.id().isBlank()) {
                ids.add(call.id());
            }
        }
        return ids;
    }
}
