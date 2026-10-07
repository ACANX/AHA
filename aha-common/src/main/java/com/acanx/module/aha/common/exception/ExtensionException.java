package com.acanx.module.aha.common.exception;

/**
 * 扩展加载、注册与运行异常。
 *
 * @since 0.1.0
 */
public class ExtensionException extends AhaException {

    public ExtensionException(String code, String message) {
        super(code, message);
    }

    public ExtensionException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
