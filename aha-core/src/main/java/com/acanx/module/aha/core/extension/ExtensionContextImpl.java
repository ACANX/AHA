package com.acanx.module.aha.core.extension;

import com.acanx.module.aha.extension.api.ExtensionConfig;
import com.acanx.module.aha.extension.api.ExtensionContext;
import com.acanx.module.aha.extension.api.Registration;
import com.acanx.module.aha.extension.api.event.EventBus;

import java.lang.System.Logger;
import java.nio.file.Path;

/**
 * 扩展上下文实现。
 *
 * @since 0.1.0
 */
public final class ExtensionContextImpl implements ExtensionContext {

    private final EventBus eventBus;
    private final ExtensionConfig config;
    private final Logger logger;
    private final Path dataDir;
    private final RegistrationTracker tracker;

    /**
     * 构造扩展上下文。
     *
     * @param eventBus 事件总线
     * @param config   配置
     * @param logger   日志器
     * @param dataDir  数据目录
     * @param tracker  注册追踪器
     */
    public ExtensionContextImpl(EventBus eventBus, ExtensionConfig config, Logger logger,
                             Path dataDir, RegistrationTracker tracker) {
        this.eventBus = eventBus;
        this.config = config;
        this.logger = logger;
        this.dataDir = dataDir;
        this.tracker = tracker;
    }

    @Override
    public <T> Registration register(Class<T> extensionPoint, T implementation) {
        return tracker.track(extensionPoint, implementation);
    }

    @Override
    public EventBus events() {
        return eventBus;
    }

    @Override
    public ExtensionConfig config() {
        return config;
    }

    @Override
    public Logger logger() {
        return logger;
    }

    @Override
    public Path dataDir() {
        return dataDir;
    }
}
