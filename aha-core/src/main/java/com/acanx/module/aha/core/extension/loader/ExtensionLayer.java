package com.acanx.module.aha.core.extension.loader;

import com.acanx.module.aha.extension.api.ExtensionDescriptor;

import java.lang.ModuleLayer;
import java.util.List;

/**
 * 扩展层：承载已加载的 {@link ModuleLayer} 与描述符。
 *
 * @param layer       模块层
 * @param descriptors 描述符列表
 * @since 0.1.0
 */
public record ExtensionLayer(ModuleLayer layer, List<ExtensionDescriptor> descriptors) {
}
