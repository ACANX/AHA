package com.acanx.module.aha.extension.api;

/**
 * 扩展依赖。
 *
 * @param id       依赖扩展 ID
 * @param version  版本约束
 * @param optional 是否可选
 * @since 0.1.0
 */
public record ExtensionDependency(String id, String version, boolean optional) {
}
