package com.acanx.module.aha.extension.api;

import com.acanx.module.aha.extension.api.event.EventBus;

import java.lang.System.Logger;
import java.nio.file.Path;

/**
 * 扩展上下文，由宿主注入。
 *
 * @since 0.1.0
 */
public interface ExtensionContext {

    /**
     * 注册扩展点实现。
     *
     * @param extensionPoint 扩展点类型
     * @param implementation 实现
     * @param <T>            扩展点类型
     * @return 注册句柄
     */
    <T> Registration register(Class<T> extensionPoint, T implementation);

    /**
     * 事件总线。
     *
     * @return 事件总线
     */
    EventBus events();

    /**
     * 扩展配置。
     *
     * @return 配置
     */
    ExtensionConfig config();

    /**
     * 扩展日志器。
     *
     * @return 日志器
     */
    Logger logger();

    /**
     * 扩展数据目录。
     *
     * @return 数据目录
     */
    Path dataDir();
}
