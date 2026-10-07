package com.acanx.module.aha.common.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link RuntimeDescriptor} 与 {@link RuntimeKind} 测试。
 *
 * @since 0.1.0
 */
class RuntimeDescriptorTest {

    @Test
    void localDescriptorDescribesCurrentProcess() {
        RuntimeDescriptor descriptor = RuntimeDescriptor.local();

        assertThat(descriptor.id()).isEqualTo(RuntimeDescriptor.LOCAL_ID);
        assertThat(descriptor.kind()).isEqualTo(RuntimeKind.LOCAL);
        assertThat(descriptor.remote()).isFalse();
        assertThat(descriptor.isLocal()).isTrue();
        assertThat(descriptor.os()).isNotBlank();
        assertThat(descriptor.arch()).isNotBlank();
        assertThat(descriptor.workingDir()).isNotBlank();
    }

    @Test
    void attributesAreNormalizedToImmutableMap() {
        RuntimeDescriptor descriptor = new RuntimeDescriptor(
                "wsl:Ubuntu", RuntimeKind.WSL, "linux", "amd64", "/home/me", true, null);

        assertThat(descriptor.attributes()).isEmpty();
        assertThatThrownBy(() -> descriptor.attributes().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void remoteDescriptorIsNotLocal() {
        RuntimeDescriptor descriptor = new RuntimeDescriptor(
                "ssh:build-01", RuntimeKind.SSH, "linux", "amd64", "/srv", true,
                Map.of("User", "deploy"));

        assertThat(descriptor.isLocal()).isFalse();
        assertThat(descriptor.remote()).isTrue();
        assertThat(descriptor.kind()).isEqualTo(RuntimeKind.SSH);
        assertThat(descriptor.attributes()).containsEntry("User", "deploy");
    }

    @Test
    void localKindWithRemoteFlagIsNotLocal() {
        RuntimeDescriptor descriptor = new RuntimeDescriptor(
                "container:abc", RuntimeKind.LOCAL, "linux", "amd64", "/", true, Map.of());

        assertThat(descriptor.isLocal()).isFalse();
    }

    @Test
    void runtimeKindCoversPlannedEnvironments() {
        assertThat(RuntimeKind.values()).containsExactly(
                RuntimeKind.LOCAL,
                RuntimeKind.WSL,
                RuntimeKind.CONTAINER,
                RuntimeKind.SSH,
                RuntimeKind.SERVERLESS,
                RuntimeKind.DAEMON);
    }
}
