package com.acanx.module.aha.common.exception;

/**
 * 工具执行异常。
 *
 * @since 0.1.0
 */
public class ToolExecutionException extends AhaException {

    public ToolExecutionException(String code, String message) {
        super(code, message);
    }

    public ToolExecutionException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
