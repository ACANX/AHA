package com.acanx.module.aha.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 异常层次测试。
 *
 * @since 0.1.0
 */
class AhaExceptionTest {

    @Test
    void carriesCodeAndMessage() {
        AhaException ex = new AhaException("E_CODE", "boom");

        assertThat(ex.code()).isEqualTo("E_CODE");
        assertThat(ex.getMessage()).isEqualTo("boom");
        assertThat(ex.getCause()).isNull();
    }

    @Test
    void carriesCause() {
        IllegalStateException cause = new IllegalStateException("root");
        AhaException ex = new AhaException("E_CODE", "boom", cause);

        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void subclassesShareHierarchy() {
        assertThat(new ConfigException("C", "m")).isInstanceOf(AhaException.class);
        assertThat(new LlmException("L", "m")).isInstanceOf(AhaException.class);
        assertThat(new ExtensionException("P", "m")).isInstanceOf(AhaException.class);
        assertThat(new SecurityException("S", "m")).isInstanceOf(AhaException.class);
        assertThat(new ToolExecutionException("T", "m")).isInstanceOf(AhaException.class);
    }

    @Test
    void subclassesCarryCodeAndCause() {
        IllegalStateException cause = new IllegalStateException("root");
        AhaException[] exceptions = {
                new ConfigException("C", "m", cause),
                new LlmException("L", "m", cause),
                new ExtensionException("P", "m", cause),
                new SecurityException("S", "m", cause),
                new ToolExecutionException("T", "m", cause),
        };

        for (AhaException exception : exceptions) {
            assertThat(exception.getCause()).isSameAs(cause);
            assertThat(exception.code()).isNotBlank();
            assertThat(exception.getMessage()).isEqualTo("m");
        }
    }
}
