package com.acanx.module.aha.core.runtime;

import com.acanx.module.aha.common.event.AgentEvent;
import com.acanx.module.aha.common.event.ContentEvent;
import com.acanx.module.aha.common.event.DoneEvent;
import com.acanx.module.aha.common.event.ErrorEvent;
import com.acanx.module.aha.common.runtime.RuntimeDescriptor;
import com.acanx.module.aha.common.runtime.RuntimeKind;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.core.task.TaskRequest;
import com.acanx.module.aha.core.task.TaskResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AgentRuntime} 默认行为测试（远程实现的前向兼容基线）。
 *
 * @since 0.1.0
 */
class AgentRuntimeTest {

    /**
     * 仅实现同步提交的测试运行时。
     *
     * @param result 固定返回值
     */
    private record StubRuntime(TaskResult result) implements AgentRuntime {

        @Override
        public RuntimeDescriptor descriptor() {
            return new RuntimeDescriptor("ssh:build-01", RuntimeKind.SSH,
                    "linux", "amd64", "/srv", true, java.util.Map.of());
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public TaskResult submit(TaskRequest request, CancellationToken token) {
            return result;
        }
    }

    @Test
    void defaultStreamingWrapsSuccessfulResult() {
        StubRuntime runtime = new StubRuntime(TaskResult.success("t1", "output"));
        List<AgentEvent> events = new ArrayList<>();

        runtime.submitStreaming(TaskRequest.of("hi"), events::add, new CancellationToken());

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isEqualTo(new ContentEvent("t1", "output"));
        assertThat(events.get(1)).isEqualTo(new DoneEvent("t1", "stop"));
    }

    @Test
    void defaultStreamingWrapsFailure() {
        StubRuntime runtime = new StubRuntime(TaskResult.failure("t2", "E_CODE", "boom"));
        List<AgentEvent> events = new ArrayList<>();

        runtime.submitStreaming(TaskRequest.of("hi"), events::add, new CancellationToken());

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isEqualTo(new ErrorEvent("t2", "E_CODE", "boom"));
        assertThat(events.get(1)).isEqualTo(new DoneEvent("t2", "error"));
    }

    @Test
    void descriptorExposesRemoteRuntime() {
        StubRuntime runtime = new StubRuntime(TaskResult.success("t", "o"));

        assertThat(runtime.isAvailable()).isTrue();
        assertThat(runtime.descriptor().kind()).isEqualTo(RuntimeKind.SSH);
        assertThat(runtime.descriptor().isLocal()).isFalse();
    }

    @Test
    void closeIsOptionalByDefault() {
        StubRuntime runtime = new StubRuntime(TaskResult.success("t", "o"));

        runtime.close();

        assertThat(runtime.descriptor().id()).isEqualTo("ssh:build-01");
    }
}
