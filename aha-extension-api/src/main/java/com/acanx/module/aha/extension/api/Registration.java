package com.acanx.module.aha.extension.api;

/**
 * 注册句柄，关闭时回滚注册。
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface Registration extends AutoCloseable {

    /**
     * 关闭并回滚注册。
     */
    @Override
    void close();
}
