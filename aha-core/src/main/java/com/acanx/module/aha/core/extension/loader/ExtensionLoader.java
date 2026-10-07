package com.acanx.module.aha.core.extension.loader;

import com.acanx.module.aha.extension.api.ExtensionDescriptor;

import java.io.IOException;
import java.lang.ModuleLayer;
import java.nio.file.Path;
import java.util.List;

/**
 * 扩展加载器（0.3 静态加载骨架）。
 *
 * @since 0.1.0
 */
public final class ExtensionLoader {

    /**
     * 扫描扩展目录并读取描述符。
     *
     * @param extensionDir 扩展目录
     * @return 描述符列表
     */
    public List<ExtensionDescriptor> scanExtensionDir(Path extensionDir) {
        // TODO(0.3): 扫描 AhaExtension.yaml 并解析描述符
        return List.of();
    }

    /**
     * 加载全部扩展。
     *
     * @param extensionDir   扩展目录
     * @param parentLayer 父模块层
     * @return 扩展层
     * @throws IOException 加载失败
     */
    public ExtensionLayer loadAll(Path extensionDir, ModuleLayer parentLayer) throws IOException {
        // TODO(0.3): 依赖解析与拓扑排序 + 为每个扩展创建独立 ModuleLayer
        List<ExtensionDescriptor> descriptors = scanExtensionDir(extensionDir);
        return new ExtensionLayer(parentLayer, descriptors);
    }
}
