package com.acanx.module.aha.core.extension;

import com.acanx.module.aha.extension.api.AhaExtension;
import com.acanx.module.aha.extension.api.ExtensionDescriptor;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扩展管理器（微内核骨架）。
 *
 * @since 0.1.0
 */
public final class ExtensionManager {

    private final Map<String, AhaExtension> extensions = new ConcurrentHashMap<>();
    private final RegistrationTracker tracker = new RegistrationTracker();

    /**
     * 加载扩展目录。
     *
     * @param extensionDir 扩展目录
     * @return 加载的描述符
     */
    public List<ExtensionDescriptor> load(Path extensionDir) {
        // TODO(0.3): 通过 ExtensionLoader 加载并启动扩展
        return List.of();
    }

    /**
     * 启动扩展。
     *
     * @param extension 扩展
     */
    public void start(AhaExtension extension) {
        extensions.put(extension.descriptor().id(), extension);
    }

    /**
     * 停止扩展并回滚注册。
     *
     * @param extensionId 扩展 ID
     */
    public void stop(String extensionId) {
        AhaExtension extension = extensions.remove(extensionId);
        if (extension != null) {
            extension.onStop();
        }
        tracker.closeAll();
    }

    /**
     * 已加载的扩展。
     *
     * @return 扩展映射
     */
    public Map<String, AhaExtension> extensions() {
        return extensions;
    }
}
