package com.acanx.module.aha.desktop.chat;

import com.acanx.module.aha.core.llm.protocol.ChatMessage;
import com.acanx.module.aha.core.llm.protocol.Role;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SessionExport} 测试：两种导出格式与转义。
 *
 * <p>JSON 是手写的，因此转义必须有测试钉住——这是不引依赖的代价，也是它的底线。</p>
 *
 * @since 0.2.0
 */
class SessionExportTest {

    private static final long NOW = 1_760_000_000_000L;

    private static final List<ChatMessage> HISTORY = List.of(
            ChatMessage.text(Role.USER, "看看 AgentEngine"),
            ChatMessage.text(Role.ASSISTANT, "问题是 `MAX_TOOL_ITERATIONS` 之后直接继续"),
            new ChatMessage(Role.TOOL, "第 1 行\n第 2 行", List.of(), "call_1", null),
            new ChatMessage(Role.SYSTEM, "系统提示", List.of(), null, null));

    @Test
    void markdownHasHeadingMetaAndSections() {
        String md = SessionExport.markdown("看看代码", "ABC123", HISTORY, NOW);

        assertThat(md).startsWith("# 看看代码\n");
        assertThat(md).contains("会话 ID：`ABC123`");
        assertThat(md).contains("消息条数：4");
        assertThat(md).contains("## 你").contains("## 助手").contains("## 工具 · call_1")
                .contains("## 系统");
        assertThat(md).contains("看看 AgentEngine");
    }

    @Test
    void markdownSkipsBlankBodies() {
        String md = SessionExport.markdown("t", "id",
                List.of(ChatMessage.text(Role.ASSISTANT, "   ")), NOW);
        assertThat(md).doesNotContain("## 助手");
    }

    @Test
    void jsonIsEscapedAndStructured() {
        String json = SessionExport.json("标\"题", "id-1",
                List.of(ChatMessage.text(Role.USER, "第一行\n第二行\t制表")), NOW);

        assertThat(json).contains("\"title\": \"标\\\"题\"");
        assertThat(json).contains("\"sessionId\": \"id-1\"");
        assertThat(json).contains("\"exportedAt\": " + NOW);
        assertThat(json).contains("\"role\": \"USER\"");
        assertThat(json).contains("第一行\\n第二行\\t制表");
        assertThat(json).startsWith("{").endsWith("}\n");
    }

    @Test
    void jsonNullContentAndToolCallIdBecomeNull() {
        String json = SessionExport.json("t", "id",
                List.of(new ChatMessage(Role.ASSISTANT, null, List.of(), null, null)), NOW);
        assertThat(json).contains("\"content\": null").contains("\"toolCallId\": null");
    }

    @Test
    void jsonEscapesControlCharacters() {
        assertThat(SessionExport.quote("a\u0001b")).isEqualTo("\"a\\u0001b\"");
        assertThat(SessionExport.quote(null)).isEqualTo("null");
        assertThat(SessionExport.quote("反\\斜杠")).isEqualTo("\"反\\\\斜杠\"");
    }

    @Test
    void fileNameStripsIllegalCharacters() {
        assertThat(SessionExport.fileName("看看 代码/测试", "md")).isEqualTo("aha-看看_代码_测试.md");
        assertThat(SessionExport.fileName("a:b*c?d\"e<f>g|h", "json"))
                .isEqualTo("aha-a_b_c_d_e_f_g_h.json");
        assertThat(SessionExport.fileName("   ", "md")).isEqualTo("aha-（未命名会话）.md");
    }

    @Test
    void fileNameIsCapped() {
        String name = SessionExport.fileName("字".repeat(120), "md");
        assertThat(name.length()).isLessThanOrEqualTo("aha-".length() + SessionExport.NAME_MAX + 3);
    }
}
