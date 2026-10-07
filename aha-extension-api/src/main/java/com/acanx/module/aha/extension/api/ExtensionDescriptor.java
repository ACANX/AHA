package com.acanx.module.aha.extension.api;

import java.util.List;

/**
 * 扩展描述符。
 *
 * @param id           扩展 ID
 * @param name         名称
 * @param version      版本
 * @param apiVersion   API 版本
 * @param mainModule   模块名
 * @param mainClass    主类
 * @param permissions  权限
 * @param dependencies 依赖
 * @since 0.1.0
 */
public record ExtensionDescriptor(
        String id,
        String name,
        String version,
        String apiVersion,
        String mainModule,
        String mainClass,
        List<ExtensionPermission> permissions,
        List<ExtensionDependency> dependencies
) {
}
