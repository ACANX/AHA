package com.acanx.module.aha.extension.api.event;

/**
 * 事件总线。
 *
 * @since 0.1.0
 */
public interface EventBus {

    /**
     * 广播事件。
     *
     * @param key   事件键
     * @param event 事件
     * @param <E>   事件类型
     */
    <E extends ExtensionEvent> void emit(EventKey<E> key, E event);

    /**
     * 注册监听器。
     *
     * @param key      事件键
     * @param listener 监听器
     * @param <E>      事件类型
     */
    <E extends ExtensionEvent> void on(EventKey<E> key, ExtensionEventListener<E> listener);

    /**
     * 瀑布式分发事件。
     *
     * @param key   事件键
     * @param event 事件
     * @param <E>   事件类型
     * @return 变换后的事件
     */
    <E extends ExtensionEvent> E waterfall(EventKey<E> key, E event);
}
