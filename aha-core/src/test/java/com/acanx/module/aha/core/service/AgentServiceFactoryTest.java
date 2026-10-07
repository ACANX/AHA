package com.acanx.module.aha.core.service;

import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.common.runtime.Endpoint;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AgentServiceFactory} 测试（端点分派与 SPI 发现）。
 *
 * @since 0.1.0
 */
class AgentServiceFactoryTest {

    @TempDir
    static Path home;

    private static String originalHome;

    @BeforeAll
    static void redirectHome() {
        originalHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        System.setProperty("AHA_HOME", home.toString());
    }

    @AfterAll
    static void restoreHome() {
        if (originalHome != null) {
            System.setProperty("user.home", originalHome);
        }
        System.clearProperty("AHA_HOME");
    }

    @Test
    void connectLocalEndpointReturnsLocalService() {
        AgentService service = AgentServiceFactory.connect(Endpoint.local(), null);
        try {
            assertThat(service).isInstanceOf(LocalAgentService.class);
            assertThat(service.runtime().isLocal()).isTrue();
        } finally {
            service.shutdown();
        }
    }

    @Test
    void connectNullEndpointFallsBackToLocal() {
        AgentService service = AgentServiceFactory.connect((Endpoint) null, null);
        try {
            assertThat(service).isInstanceOf(LocalAgentService.class);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void connectLocalUriReturnsLocalService() {
        AgentService service = AgentServiceFactory.connect("local://", null);
        try {
            assertThat(service).isInstanceOf(LocalAgentService.class);
        } finally {
            service.shutdown();
        }
    }

    @Test
    void connectRemoteSchemeWithoutProviderReportsPlannedSupport() {
        Endpoint endpoint = Endpoint.parse("ssh://deploy@build-01:22");

        assertThatThrownBy(() -> AgentServiceFactory.connect(endpoint, null))
                .isInstanceOf(AhaException.class)
                .hasMessageContaining("ssh://")
                .hasMessageContaining("1.0")
                .extracting(e -> ((AhaException) e).code())
                .isEqualTo("NO_PROVIDER_FOR_ENDPOINT");
    }

    @Test
    void connectWslSchemeWithoutProviderReportsPlannedSupport() {
        assertThatThrownBy(() -> AgentServiceFactory.connect("wsl://Ubuntu", null))
                .isInstanceOf(AhaException.class)
                .hasMessageContaining("wsl://");
    }

    @Test
    void supportedSchemesAlwaysContainsLocal() {
        assertThat(AgentServiceFactory.supportedSchemes()).contains("local");
    }

    @Test
    void providersEmptyWhenNoImplementationOnClasspath() {
        assertThat(AgentServiceFactory.providers()).isEmpty();
    }
}
