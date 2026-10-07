package com.acanx.module.aha.extension.api.event;

/**
 * 扩展事件监听器。
 *
 * @param <E> 事件类型
 * @since 0.1.0
 */
@FunctionalInterface
public interface ExtensionEventListener<E extends ExtensionEvent> {

    /**
     * 处理事件。
     *
     * @param event 事件
     */
    void onEvent(E event);
}
