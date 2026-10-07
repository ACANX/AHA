package com.acanx.module.aha.core.extension.event;

import com.acanx.module.aha.extension.api.event.ExtensionEvent;

/**
 * 生命周期事件（EMIT）。
 *
 * @param type 生命周期阶段
 * @since 0.1.0
 */
public record LifecycleEvent(String type) implements ExtensionEvent {
}
