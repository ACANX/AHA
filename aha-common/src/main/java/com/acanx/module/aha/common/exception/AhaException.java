package com.acanx.module.aha.common.exception;

/**
 * AHA 统一异常基类。
 *
 * @since 0.1.0
 */
public class AhaException extends RuntimeException {

    private final String code;

    /**
     * 构造异常。
     *
     * @param code    错误码
     * @param message 错误信息
     */
    public AhaException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 构造异常。
     *
     * @param code    错误码
     * @param message 错误信息
     * @param cause   原因
     */
    public AhaException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 返回错误码。
     *
     * @return 错误码
     */
    public String code() {
        return code;
    }
}
