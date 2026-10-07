package com.acanx.module.aha.common.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ToolResult} 测试。
 *
 * @since 0.1.0
 */
class ToolResultTest {

    @Test
    void successCarriesOutput() {
        ToolResult result = ToolResult.success("hello");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("hello");
        assertThat(result.error()).isNull();
    }

    @Test
    void failureCarriesError() {
        ToolResult result = ToolResult.failure("boom");

        assertThat(result.success()).isFalse();
        assertThat(result.output()).isNull();
        assertThat(result.error()).isEqualTo("boom");
    }
}
