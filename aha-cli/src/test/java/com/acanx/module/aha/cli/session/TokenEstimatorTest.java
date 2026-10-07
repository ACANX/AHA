package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.Role;
import com.acanx.module.aha.core.llm.protocol.ToolCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TokenEstimator} 测试。
 *
 * <p>估算口径被 {@code /context}、{@code /compact} 与 {@code /autocompact} 共用，
 * 因此数值本身就是契约：算法一变，自动压缩的触发点就跟着变。</p>
 *
 * @since 0.1.0
 */
class TokenEstimatorTest {

    @Test
    void estimatesTokensByCharacterWidth() {
        assertThat(TokenEstimator.estimate(null)).isZero();
        assertThat(TokenEstimator.estimate("")).isZero();
        // CJK 按 1 字 1 token
        assertThat(TokenEstimator.estimate("你好世界")).isEqualTo(4);
        // 拉丁按 4 字符 1 token，向上取整
        assertThat(TokenEstimator.estimate("abcd")).isEqualTo(1);
        assertThat(TokenEstimator.estimate("abcde")).isEqualTo(2);
        // 混排：2 个汉字 + 4 个拉丁字符
        assertThat(TokenEstimator.estimate("你好abcd")).isEqualTo(3);
    }

    @Test
    void countsPerMessageOverheadAndSystemPrompt() {
        List<ChatMessage> history = List.of(
                ChatMessage.text(Role.USER, "你好"),
                ChatMessage.text(Role.ASSISTANT, "abcd"));

        // 系统提示词 4 + （2 + 开销 4）+ （1 + 开销 4）
        assertThat(TokenEstimator.estimateMessages("一二三四", history)).isEqualTo(15);
        assertThat(TokenEstimator.estimateMessages(null, history)).isEqualTo(11);
        assertThat(TokenEstimator.estimateMessages(null, null)).isZero();
    }

    @Test
    void countsToolCallNamesAndArguments() {
        ChatMessage plain = ChatMessage.text(Role.ASSISTANT, "ok");
        ChatMessage withTool = new ChatMessage(Role.ASSISTANT, "ok",
                List.of(new ToolCall("id1", "file-read", Map.of("path", "/tmp/a"))), null, null);

        long withoutTool = TokenEstimator.estimateMessages(null, List.of(plain));
        long withToolTokens = TokenEstimator.estimateMessages(null, List.of(withTool));

        // 工具名与参数都必须计入，否则上下文用量会被系统性低估，
        // 自动压缩的触发点也就跟着偏晚
        assertThat(TokenEstimator.estimate("file-read")).isEqualTo(3);
        assertThat(TokenEstimator.estimate("{path=/tmp/a}")).isEqualTo(4);
        assertThat(withoutTool).isEqualTo(5);
        assertThat(withToolTokens).isEqualTo(12);
    }

    @Test
    void describesWithAndWithoutSystemPrompt() {
        assertThat(TokenEstimator.describe(12, true)).isEqualTo("约 12 tokens（含系统提示词）");
        assertThat(TokenEstimator.describe(12, false)).isEqualTo("约 12 tokens（未含系统提示词）");
    }
}
