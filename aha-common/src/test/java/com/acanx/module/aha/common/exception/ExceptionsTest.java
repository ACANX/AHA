package com.acanx.module.aha.common.exception;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Exceptions} 测试。
 *
 * @since 0.1.0
 */
class ExceptionsTest {

    @Test
    void unwrapsCompletionException() {
        var root = new LlmException("LLM_HTTP_422", "流式请求失败: HTTP 422");
        var wrapped = new CompletionException(root);

        assertThat(Exceptions.unwrap(wrapped)).isSameAs(root);
        assertThat(Exceptions.message(wrapped)).isEqualTo("流式请求失败: HTTP 422");
    }

    @Test
    void unwrapsExecutionException() {
        var root = new AhaException("SESSION_NOT_FOUND", "会话不存在: abc");

        assertThat(Exceptions.message(new ExecutionException(root))).isEqualTo("会话不存在: abc");
    }

    @Test
    void unwrapsInvocationTargetException() {
        var root = new IllegalStateException("反射内部错误");

        assertThat(Exceptions.message(new InvocationTargetException(root))).isEqualTo("反射内部错误");
    }

    @Test
    void unwrapsNestedWrappers() {
        var root = new AhaException("E", "最内层");
        var nested = new CompletionException(new ExecutionException(root));

        assertThat(Exceptions.unwrap(nested)).isSameAs(root);
        assertThat(Exceptions.message(nested)).isEqualTo("最内层");
    }

    @Test
    void plainExceptionIsReturnedAsIs() {
        var plain = new AhaException("E", "原始错误");

        assertThat(Exceptions.unwrap(plain)).isSameAs(plain);
        assertThat(Exceptions.message(plain)).isEqualTo("原始错误");
    }

    @Test
    void doesNotStripBusinessCause() {
        // 业务异常自身的 cause 可能承载上下文，不应被抹掉
        var cause = new RuntimeException("底层原因");
        var business = new AhaException("E", "业务错误", cause);

        assertThat(Exceptions.unwrap(business)).isSameAs(business);
        assertThat(Exceptions.message(business)).isEqualTo("业务错误");
    }

    @Test
    void messageFallsBackToClassNameWhenBlank() {
        assertThat(Exceptions.message(new AhaException("E", null))).isEqualTo("AhaException");
        assertThat(Exceptions.message(new AhaException("E", "   "))).isEqualTo("AhaException");
    }

    @Test
    void handlesNullGracefully() {
        assertThat(Exceptions.unwrap(null)).isNull();
        assertThat(Exceptions.message(null)).isEqualTo("未知错误");
    }
}
