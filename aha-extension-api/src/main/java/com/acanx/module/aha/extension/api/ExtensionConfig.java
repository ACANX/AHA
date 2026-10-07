package com.acanx.module.aha.extension.api;

import java.util.Map;
import java.util.Optional;

/**
 * 扩展配置视图。
 *
 * @since 0.1.0
 */
public interface ExtensionConfig {

    /**
     * 读取字符串。
     *
     * @param key 键
     * @return 值
     */
    Optional<String> getString(String key);

    /**
     * 读取整数。
     *
     * @param key 键
     * @return 值
     */
    Optional<Integer> getInt(String key);

    /**
     * 读取布尔值。
     *
     * @param key 键
     * @return 值
     */
    Optional<Boolean> getBoolean(String key);

    /**
     * 转为 Map 视图。
     *
     * @return Map
     */
    Map<String, Object> asMap();
}
