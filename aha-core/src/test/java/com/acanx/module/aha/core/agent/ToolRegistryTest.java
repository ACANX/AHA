package com.acanx.module.aha.core.agent;

import com.acanx.module.aha.common.exception.ToolExecutionException;
import com.acanx.module.aha.common.model.ToolDescriptor;
import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.common.tool.JsonSchema;
import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.tool.JsonSchema;
import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.tool.ToolPermission;
import com.acanx.module.aha.common.tool.ToolSettings;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ToolRegistry} 测试。
 *
 * @since 0.1.0
 */
class ToolRegistryTest {

    /**
     * 可配置权限的测试工具。
     *
     * @param name       名称
     * @param permission 权限
     * @param calls      调用计数
     */
    private record FakeTool(String name, ToolPermission permission, AtomicInteger calls) implements Tool {

        @Override
        public String description() {
            return "fake";
        }

        @Override
        public JsonSchema parameters() {
            return JsonSchema.object(Map.of(), List.of());
        }

        @Override
        public ToolResult execute(Map<String, Object> params, CancellationToken token) {
            calls.incrementAndGet();
            return ToolResult.success(name + "-ok");
        }

        @Override
        public ToolPermission requiredPermission() {
            return permission;
        }
    }

    @Test
    void registersAndListsTools() {
        ToolRegistry registry = new ToolRegistry(PermissionPolicy.allowAll());
        registry.register(new FakeTool("a", ToolPermission.READ, new AtomicInteger()));
        registry.register(new FakeTool("b", ToolPermission.WRITE, new AtomicInteger()));

        List<ToolDescriptor> descriptors = registry.list();

        assertThat(descriptors).hasSize(2);
        assertThat(descriptors.get(0).name()).isEqualTo("a");
        assertThat(registry.contains("b")).isTrue();
        assertThat(registry.find("missing")).isEmpty();
    }

    @Test
    void registrationWithSameNameReplaces() {
        ToolRegistry registry = new ToolRegistry(PermissionPolicy.allowAll());
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        registry.register(new FakeTool("x", ToolPermission.READ, first));
        registry.register(new FakeTool("x", ToolPermission.READ, second));

        registry.invoke("x", Map.of(), new CancellationToken());

        assertThat(first.get()).isZero();
        assertThat(second.get()).isEqualTo(1);
    }

    @Test
    void invokesAllowedTool() {
        ToolRegistry registry = new ToolRegistry(PermissionPolicy.allowAll());
        AtomicInteger calls = new AtomicInteger();
        registry.register(new FakeTool("a", ToolPermission.READ, calls));

        ToolResult result = registry.invoke("a", Map.of(), new CancellationToken());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("a-ok");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void rejectsUnknownTool() {
        ToolRegistry registry = new ToolRegistry(PermissionPolicy.allowAll());

        assertThatThrownBy(() -> registry.invoke("nope", Map.of(), new CancellationToken()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("未知工具");
    }

    @Test
    void deniesToolWithoutPermission() {
        ToolRegistry registry = new ToolRegistry(PermissionPolicy.defaultPolicy());
        AtomicInteger calls = new AtomicInteger();
        registry.register(new FakeTool("danger", ToolPermission.EXECUTE, calls));

        assertThatThrownBy(() -> registry.invoke("danger", Map.of(), new CancellationToken()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("未获授权")
                .extracting(e -> ((ToolExecutionException) e).code())
                .isEqualTo("PERMISSION_DENIED");
        assertThat(calls.get()).isZero();
    }

    @Test
    void approverCanAuthoriseDeniedTool() {
        // 交互式对话场景：权限未被自动放行时，由裁决器询问用户
        ToolRegistry registry = new ToolRegistry(PermissionPolicy.defaultPolicy());
        AtomicInteger calls = new AtomicInteger();
        registry.register(new FakeTool("w", ToolPermission.WRITE, calls));
        List<String> asked = new ArrayList<>();
        registry.setApprover((name, permission, args) -> {
            asked.add(name + ":" + permission);
            return true;
        });

        assertThat(registry.invoke("w", Map.of(), new CancellationToken()).success()).isTrue();
        assertThat(asked).containsExactly("w:WRITE");
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void approverRejectionKeepsToolUnexecuted() {
        ToolRegistry registry = new ToolRegistry(PermissionPolicy.defaultPolicy());
        AtomicInteger calls = new AtomicInteger();
        registry.register(new FakeTool("w", ToolPermission.WRITE, calls));
        registry.setApprover((name, permission, args) -> false);

        assertThatThrownBy(() -> registry.invoke("w", Map.of(), new CancellationToken()))
                .isInstanceOf(ToolExecutionException.class)
                .extracting(e -> ((ToolExecutionException) e).code())
                .isEqualTo("PERMISSION_DENIED");
        assertThat(calls.get()).isZero();
    }

    @Test
    void approverIsNotConsultedForAutoApprovedTools() {
        // 已自动放行的权限不应产生多余的确认提示
        ToolRegistry registry = new ToolRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.register(new FakeTool("r", ToolPermission.READ, calls));
        AtomicInteger asked = new AtomicInteger();
        registry.setApprover((name, permission, args) -> {
            asked.incrementAndGet();
            return true;
        });

        registry.invoke("r", Map.of(), new CancellationToken());

        assertThat(asked.get()).isZero();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void clearingApproverRestoresDenyAll() {
        ToolRegistry registry = new ToolRegistry(PermissionPolicy.defaultPolicy());
        registry.register(new FakeTool("w", ToolPermission.WRITE, new AtomicInteger()));
        registry.setApprover((name, permission, args) -> true);
        registry.setApprover(null);

        assertThatThrownBy(() -> registry.invoke("w", Map.of(), new CancellationToken()))
                .isInstanceOf(ToolExecutionException.class);
    }

    @Test
    void allowsReadAndNetworkByDefault() {
        ToolRegistry registry = new ToolRegistry();
        AtomicInteger readCalls = new AtomicInteger();
        AtomicInteger networkCalls = new AtomicInteger();
        registry.register(new FakeTool("r", ToolPermission.READ, readCalls));
        registry.register(new FakeTool("n", ToolPermission.NETWORK, networkCalls));

        registry.invoke("r", Map.of(), new CancellationToken());
        registry.invoke("n", Map.of(), new CancellationToken());

        assertThat(readCalls.get()).isEqualTo(1);
        assertThat(networkCalls.get()).isEqualTo(1);
    }

    @Test
    void policyCanBeReplacedAtRuntime() {
        ToolRegistry registry = new ToolRegistry(PermissionPolicy.denyAll());
        AtomicInteger calls = new AtomicInteger();
        registry.register(new FakeTool("a", ToolPermission.READ, calls));

        assertThatThrownBy(() -> registry.invoke("a", Map.of(), new CancellationToken()))
                .isInstanceOf(ToolExecutionException.class);

        registry.setPermissionPolicy(PermissionPolicy.allowAll());
        assertThat(registry.invoke("a", Map.of(), new CancellationToken()).success()).isTrue();
        assertThat(registry.permissionPolicy()).isNotNull();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void configureAllReachesEveryTool() {
        // 回归：Tool/ToolProvider 无配置入口，Tools.Shell.* 等配置从未生效
        ToolRegistry registry = new ToolRegistry();
        List<ToolSettings> received = new ArrayList<>();
        registry.register(new Tool() {
            @Override
            public String name() {
                return "configured";
            }

            @Override
            public String description() {
                return "d";
            }

            @Override
            public JsonSchema parameters() {
                return JsonSchema.object(Map.of(), List.of());
            }

            @Override
            public ToolResult execute(Map<String, Object> params, CancellationToken token) {
                return ToolResult.success("ok");
            }

            @Override
            public ToolPermission requiredPermission() {
                return ToolPermission.READ;
            }

            @Override
            public void configure(ToolSettings settings) {
                received.add(settings);
            }
        });

        registry.configureAll(ToolSettings.of(Map.of("Shell.TimeoutSeconds", 5)));

        assertThat(received).hasSize(1);
        assertThat(received.get(0).integer("Shell.TimeoutSeconds", 30)).isEqualTo(5);
    }

    @Test
    void configureAllToleratesNullSettings() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new FakeTool("a", ToolPermission.READ, new AtomicInteger()));

        registry.configureAll(null);

        assertThat(registry.invoke("a", Map.of(), new CancellationToken()).success()).isTrue();
    }

    @Test
    void disabledToolsAreHiddenAndRejected() {
        // Tools.Enabled 此前从未生效：list() 与 invoke() 都不过滤
        ToolRegistry registry = new ToolRegistry();
        AtomicInteger calls = new AtomicInteger();
        registry.register(new FakeTool("kept", ToolPermission.READ, calls));
        registry.register(new FakeTool("dropped", ToolPermission.READ, new AtomicInteger()));
        registry.setEnabled(List.of("kept"));

        assertThat(registry.list()).extracting("name").containsExactly("kept");
        assertThat(registry.isEnabled("dropped")).isFalse();
        assertThatThrownBy(() -> registry.invoke("dropped", Map.of(), new CancellationToken()))
                .isInstanceOf(ToolExecutionException.class)
                .extracting(e -> ((ToolExecutionException) e).code())
                .isEqualTo("TOOL_DISABLED");

        assertThat(registry.invoke("kept", Map.of(), new CancellationToken()).success()).isTrue();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void emptyOrNullEnabledListDoesNotFilter() {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new FakeTool("a", ToolPermission.READ, new AtomicInteger()));
        registry.setEnabled(List.of());
        registry.setEnabled(null);

        assertThat(registry.list()).hasSize(1);
    }

    @Test
    void invokeApprovedStillHonoursEnabledFilter() {
        // 回归：invokeTool(confirmed=true) 曾直接调 tool.execute()，绕过启用过滤
        ToolRegistry registry = new ToolRegistry();
        registry.register(new FakeTool("off", ToolPermission.WRITE, new AtomicInteger()));
        registry.setEnabled(List.of("other"));

        assertThatThrownBy(() -> registry.invokeApproved("off", Map.of(), new CancellationToken()))
                .isInstanceOf(ToolExecutionException.class)
                .extracting(e -> ((ToolExecutionException) e).code())
                .isEqualTo("TOOL_DISABLED");
    }

    @Test
    void invokeApprovedSkipsPermissionButExecutes() {
        ToolRegistry registry = new ToolRegistry(PermissionPolicy.defaultPolicy());
        AtomicInteger calls = new AtomicInteger();
        registry.register(new FakeTool("w", ToolPermission.WRITE, calls));

        assertThat(registry.invokeApproved("w", Map.of(), new CancellationToken()).success()).isTrue();
        assertThat(calls.get()).isEqualTo(1);
    }
}
