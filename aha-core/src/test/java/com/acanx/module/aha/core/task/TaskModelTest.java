package com.acanx.module.aha.core.task;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 任务模型测试（含跨进程序列化契约）。
 *
 * @since 0.1.0
 */
class TaskModelTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private static TaskRequest fullRequest() {
        return new TaskRequest("task-1", null, TaskSource.SCHEDULED, "session-1",
                "汇总今日错误日志", "你是运维助手", "gpt-4o",
                Map.of("Tenant", "acme"), 0L, Map.of("EnableThinking", true));
    }

    @Test
    void requestNormalizesDefaults() {
        TaskRequest request = new TaskRequest(null, null, null, null, "hi",
                null, null, null, 0L, null);

        assertThat(request.source()).isEqualTo(TaskSource.INTERACTIVE);
        assertThat(request.context()).isEmpty();
        assertThat(request.extensions()).isEmpty();
    }

    @Test
    void requestMapsAreImmutable() {
        TaskRequest request = fullRequest();

        assertThatThrownBy(() -> request.context().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void ofBuildsCliTask() {
        TaskRequest request = TaskRequest.of("跑个测试");

        assertThat(request.source()).isEqualTo(TaskSource.CLI);
        assertThat(request.input()).isEqualTo("跑个测试");
        assertThat(request.isAutomated()).isFalse();
    }

    @Test
    void detectsAutomatedSources() {
        assertThat(tag(TaskSource.INTERACTIVE)).isFalse();
        assertThat(tag(TaskSource.CLI)).isFalse();
        assertThat(tag(TaskSource.SCHEDULED)).isTrue();
        assertThat(tag(TaskSource.REMOTE_CALL)).isTrue();
        assertThat(tag(TaskSource.SERVERLESS)).isTrue();
        assertThat(tag(TaskSource.MCP)).isTrue();
        assertThat(tag(TaskSource.ACP)).isTrue();
        assertThat(tag(TaskSource.WEBHOOK)).isTrue();
    }

    private static boolean tag(TaskSource source) {
        return new TaskRequest(null, null, source, null, "x",
                null, null, Map.of(), 0L, Map.of()).isAutomated();
    }

    @Test
    void resultFactoriesAndNormalization() {
        TaskResult ok = TaskResult.success("t1", "done");
        assertThat(ok.success()).isTrue();
        assertThat(ok.output()).isEqualTo("done");
        assertThat(ok.toolCalls()).isEmpty();

        TaskResult failed = TaskResult.failure("t1", "E_CODE", "boom");
        assertThat(failed.success()).isFalse();
        assertThat(failed.errorCode()).isEqualTo("E_CODE");

        TaskResult withNulls = new TaskResult("t", true, "o", null, null, null, 5L, null);
        assertThat(withNulls.toolCalls()).isEmpty();
        assertThat(withNulls.metadata()).isEmpty();
    }

    @Test
    void requestSerializesWithPascalCaseAndRoundTrips() {
        TaskRequest request = fullRequest();

        String json = JSON.writeValueAsString(request);

        assertThat(json)
                .contains("\"Id\":\"task-1\"")
                .contains("\"Source\":\"SCHEDULED\"")
                .contains("\"SessionId\":\"session-1\"")
                .contains("\"SystemPrompt\"");
        TaskRequest back = JSON.readValue(json, TaskRequest.class);
        assertThat(back).isEqualTo(request);
    }

    @Test
    void requestToleratesUnknownFieldsFromNewerPeers() {
        String json = "{\"Id\":\"x\",\"Input\":\"hi\",\"FutureField\":123}";

        TaskRequest request = JSON.readValue(json, TaskRequest.class);

        assertThat(request.id()).isEqualTo("x");
        assertThat(request.input()).isEqualTo("hi");
    }

    @Test
    void resultRoundTrips() {
        TaskResult result = new TaskResult("t9", true, "ok", null, null,
                java.util.List.of("file-read"), 42L, Map.of("Runtime", "local"));

        TaskResult back = JSON.readValue(JSON.writeValueAsString(result), TaskResult.class);

        assertThat(back).isEqualTo(result);
        assertThat(back.toolCalls()).containsExactly("file-read");
    }

    @Test
    void resultDeserializesMutableMaps() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("Runtime", "local");
        TaskResult result = new TaskResult("t", true, "o", null, null, null, 0L, metadata);

        assertThat(result.metadata()).containsEntry("Runtime", "local");
    }
}
