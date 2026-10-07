package com.acanx.module.aha.common.exception;

/**
 * 配置加载与校验异常。
 *
 * @since 0.1.0
 */
public class ConfigException extends AhaException {

    public ConfigException(String code, String message) {
        super(code, message);
    }

    public ConfigException(String code, String message, Throwable cause) {
        super(code, message, cause);
    }
}
