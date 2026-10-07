package com.acanx.module.aha.common.exception;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * 异常解包与呈现工具。
 *
 * <p>{@link java.util.concurrent.CompletableFuture#join()} 会把业务异常包装为
 * {@link CompletionException}，而 {@code getMessage()} 返回的是
 * {@code cause.toString()}（带完整类名前缀），导致 CLI 输出形如
 * {@code com.acanx...LlmException: 请求失败: HTTP 422 {...}}，既冗长又与
 * 其他路径的呈现不一致。此处统一解包到根因。</p>
 *
 * @since 0.1.0
 */
public final class Exceptions {

    private Exceptions() {
    }

    /**
     * 解包异步与反射包装异常，返回根因。
     *
     * <p>仅解除 {@link CompletionException}、{@link ExecutionException}、
     * {@link InvocationTargetException} 三类包装，不穿透业务异常自身的 cause
     * （业务异常链可能承载上下文，不应被抹掉）。</p>
     *
     * @param throwable 原始异常，可为 {@code null}
     * @return 根因；入参为 {@code null} 时返回 {@code null}
     */
    public static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null && isWrapper(current)) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * 提取适合展示给用户的错误信息。
     *
     * @param throwable 原始异常，可为 {@code null}
     * @return 根因的 message；为空时退化为根因类名
     */
    public static String message(Throwable throwable) {
        Throwable root = unwrap(throwable);
        if (root == null) {
            return "未知错误";
        }
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    private static boolean isWrapper(Throwable throwable) {
        return throwable instanceof CompletionException
                || throwable instanceof ExecutionException
                || throwable instanceof InvocationTargetException;
    }
}
