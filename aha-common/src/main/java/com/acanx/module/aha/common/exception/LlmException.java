package com.acanx.module.aha.common.exception;

/**
 * LLM 调用与适配异常。
 *
 * @since 0.1.0
 */
public class LlmException extends AhaException {

    public LlmException(String code, String message) {
        super(code, message);
    }

    public LlmException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
