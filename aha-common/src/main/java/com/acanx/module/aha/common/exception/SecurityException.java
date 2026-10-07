package com.acanx.module.aha.common.exception;

/**
 * 安全存储异常。
 *
 * @since 0.1.0
 */
public class SecurityException extends AhaException {

    public SecurityException(String code, String message) {
        super(code, message);
    }

    public SecurityException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
