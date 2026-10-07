package com.acanx.module.aha.common.model;

/**
 * 记忆条目。
 *
 * @param key       键
 * @param value     值
 * @param createdAt 创建时间（epoch millis）
 * @since 0.1.0
 */
public record MemoryEntry(String key, String value, long createdAt) {
}
