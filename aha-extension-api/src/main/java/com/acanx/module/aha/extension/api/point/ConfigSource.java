package com.acanx.module.aha.extension.api.point;

import java.util.Map;

/**
 * 配置来源扩展点。
 *
 * @since 0.1.0
 */
public interface ConfigSource {

    /**
     * 加载配置。
     *
     * @return 配置 Map
     */
    Map<String, Object> load();

    /**
     * 优先级，数值越大优先级越高。
     *
     * @return 优先级
     */
    int priority();
}
