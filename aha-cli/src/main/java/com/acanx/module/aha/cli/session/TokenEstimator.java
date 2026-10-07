package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.cli.render.TextWidth;
import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.ToolCall;

import java.util.List;

/**
 * 上下文 Token 估算。
 *
 * <p>不引入 tokenizer 依赖：CJK 与全角字符按「1 字 ≈ 1 token」，其余按
 * 「4 字符 ≈ 1 token」。目的是给用户一个量级判断，不是精确计费。</p>
 *
 * <p>{@code /context}、{@code /compact} 与 {@code /autocompact} 共用本类，
 * 保证三处口径一致——否则「自动压缩触发了吗」这种问题会因算法不同而无从判断。</p>
 *
 * @since 0.1.0
 */
public final class TokenEstimator {

    /** 每条消息的角色与结构开销。 */
    public static final int MESSAGE_OVERHEAD = 4;

    private TokenEstimator() {
    }

    /**
     * 粗略估算文本的 Token 数。
     *
     * @param text 文本，可为 {@code null}
     * @return 估算值
     */
    public static long estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        long wide = 0;
        long narrow = 0;
        int index = 0;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            if (TextWidth.isWide(codePoint)) {
                wide++;
            } else {
                narrow++;
            }
            index += Character.charCount(codePoint);
        }
        return wide + (narrow + 3) / 4;
    }

    /**
     * 估算一份完整上下文的 Token 数：系统提示词 + 历史消息（含工具调用参数）。
     *
     * @param systemPrompt 系统提示词，可为 {@code null}
     * @param history      历史消息
     * @return 估算值
     */
    public static long estimateMessages(String systemPrompt, List<ChatMessage> history) {
        long tokens = estimate(systemPrompt);
        if (history == null) {
            return tokens;
        }
        for (ChatMessage message : history) {
            tokens += estimate(message.content()) + MESSAGE_OVERHEAD;
            if (message.toolCalls() != null) {
                for (ToolCall call : message.toolCalls()) {
                    tokens += estimate(call.name()) + estimate(String.valueOf(call.arguments()));
                }
            }
        }
        return tokens;
    }

    /**
     * 格式化 Token 估算结果。
     *
     * @param tokens          估算值
     * @param withSystemPrompt 是否含系统提示词
     * @return 描述文本
     */
    public static String describe(long tokens, boolean withSystemPrompt) {
        return "约 " + tokens + " tokens" + (withSystemPrompt ? "（含系统提示词）" : "（未含系统提示词）");
    }
}
