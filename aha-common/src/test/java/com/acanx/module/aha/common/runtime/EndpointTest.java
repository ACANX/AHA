package com.acanx.module.aha.common.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link Endpoint} 测试（远程寻址契约）。
 *
 * @since 0.1.0
 */
class EndpointTest {

    @Test
    void localEndpointIsLocal() {
        Endpoint endpoint = Endpoint.local();

        assertThat(endpoint.scheme()).isEqualTo("local");
        assertThat(endpoint.isLocal()).isTrue();
        assertThat(endpoint.host()).isNull();
        assertThat(endpoint.port()).isZero();
    }

    @Test
    void blankUriFallsBackToLocal() {
        assertThat(Endpoint.parse(null).isLocal()).isTrue();
        assertThat(Endpoint.parse("  ").isLocal()).isTrue();
    }

    @Test
    void parsesSshEndpoint() {
        Endpoint endpoint = Endpoint.parse("ssh://deploy@build-01:2222/opt/aha");

        assertThat(endpoint.scheme()).isEqualTo("ssh");
        assertThat(endpoint.user()).isEqualTo("deploy");
        assertThat(endpoint.host()).isEqualTo("build-01");
        assertThat(endpoint.port()).isEqualTo(2222);
        assertThat(endpoint.path()).isEqualTo("/opt/aha");
        assertThat(endpoint.isLocal()).isFalse();
    }

    @Test
    void parsesWslEndpointWithQueryParams() {
        Endpoint endpoint = Endpoint.parse("wsl://Ubuntu?user=me&distro=Ubuntu-24.04");

        assertThat(endpoint.scheme()).isEqualTo("wsl");
        assertThat(endpoint.host()).isEqualTo("Ubuntu");
        assertThat(endpoint.params())
                .containsEntry("user", "me")
                .containsEntry("distro", "Ubuntu-24.04");
    }

    @Test
    void parsesServerlessAndRelayEndpoints() {
        assertThat(Endpoint.parse("https://cloud.example.com/fn/aha").scheme()).isEqualTo("https");
        assertThat(Endpoint.parse("daemon://127.0.0.1:8787").port()).isEqualTo(8787);
        assertThat(Endpoint.parse("ws://relay.example.com/agent").scheme()).isEqualTo("ws");
    }

    @Test
    void schemeIsLowerCasedAndRoundTrips() {
        Endpoint endpoint = Endpoint.parse("SSH://deploy@build-01:22");

        assertThat(endpoint.scheme()).isEqualTo("ssh");
        assertThat(endpoint.toUri()).isEqualTo("ssh://deploy@build-01:22");
    }

    @Test
    void toUriRendersQueryParams() {
        Endpoint endpoint = new Endpoint("wsl", "Ubuntu", 0, null, "me", Map.of("k", "v"));

        assertThat(endpoint.toUri()).isEqualTo("wsl://me@Ubuntu?k=v");
    }

    @Test
    void paramsAreImmutable() {
        Endpoint endpoint = Endpoint.parse("wsl://Ubuntu?a=1");

        assertThatThrownBy(() -> endpoint.params().put("b", "2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void normalizesNullSchemeAndParams() {
        Endpoint endpoint = new Endpoint(null, null, 0, null, null, null);

        assertThat(endpoint.scheme()).isEqualTo("local");
        assertThat(endpoint.params()).isEmpty();
    }
}
