package com.acanx.module.aha.common.event;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 事件模型测试（含密封接口穷尽性）。
 *
 * @since 0.1.0
 */
class AgentEventTest {

    @Test
    void contentEventCarriesDelta() {
        AgentEvent event = new ContentEvent("s1", "hi");
        assertThat(event).isInstanceOf(ContentEvent.class);
        assertThat(((ContentEvent) event).delta()).isEqualTo("hi");
    }

    @Test
    void toolCallEventCarriesArguments() {
        ToolCallEvent event = new ToolCallEvent("s1", "file-read", Map.of("path", "/tmp/x"));
        assertThat(event.toolName()).isEqualTo("file-read");
        assertThat(event.args()).containsEntry("path", "/tmp/x");
    }

    @Test
    void toolResultAndUsageAndDoneAndError() {
        assertThat(new ToolResultEvent("s1", "file-read", "content").result()).isEqualTo("content");
        assertThat(new UsageEvent("s1", 10, 20).promptTokens()).isEqualTo(10);
        assertThat(new DoneEvent("s1", "stop").finishReason()).isEqualTo("stop");
        assertThat(new ErrorEvent("s1", "E1", "boom").code()).isEqualTo("E1");
    }

    @Test
    void sealedHierarchyAllowsExhaustiveSwitch() {
        AgentEvent event = new DoneEvent("s1", "stop");
        String kind = switch (event) {
            case ContentEvent e -> "content";
            case ContentSegmentEndEvent e -> "content-segment-end";
            case ToolCallEvent e -> "tool-call";
            case ToolResultEvent e -> "tool-result";
            case UsageEvent e -> "usage";
            case ErrorEvent e -> "error";
            case DoneEvent e -> "done";
        };
        assertThat(kind).isEqualTo("done");
    }

    /**
     * 段边界事件（issue #403）：一轮可多次出现，且是值对象。
     */
    @Test
    void contentSegmentEndIsAPerRoundBoundaryMarker() {
        AgentEvent event = new ContentSegmentEndEvent("s1");
        assertThat(event).isInstanceOf(ContentSegmentEndEvent.class);
        assertThat(((ContentSegmentEndEvent) event).sessionId()).isEqualTo("s1");
        // 无多余载荷：段内文本已由 ContentEvent 逐 token 送达，这里不必再带一次
        assertThat(ContentSegmentEndEvent.class.getRecordComponents()).hasSize(1);
        assertThat(new ContentSegmentEndEvent("s1")).isEqualTo(new ContentSegmentEndEvent("s1"));
        assertThat(new ContentSegmentEndEvent("s1")).isNotEqualTo(new DoneEvent("s1", "stop"));
    }

    @Test
    void eventsAreValueObjects() {
        assertThat(new ContentEvent("s1", "a")).isEqualTo(new ContentEvent("s1", "a"));
        assertThat(new UsageEvent("s1", 1, 2)).isNotEqualTo(new UsageEvent("s1", 2, 1));
        assertThat(new AgentEvent[]{new ErrorEvent("s", "c", "m")}).hasSize(1);
    }

    @Test
    void toolCallEventExposesImmutableArgs() {
        ToolCallEvent event = new ToolCallEvent("s1", "t", Map.of());
        assertThat(event.args()).isEqualTo(Map.of());
        assertThat(List.of(event.toolName())).containsExactly("t");
    }
}
