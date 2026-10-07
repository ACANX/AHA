package com.acanx.module.aha.core.extension.event;

import com.acanx.module.aha.extension.api.event.DispatchMode;
import com.acanx.module.aha.extension.api.event.EventBus;
import com.acanx.module.aha.extension.api.event.EventKey;
import com.acanx.module.aha.extension.api.event.ExtensionEvent;
import com.acanx.module.aha.extension.api.event.ExtensionEventListener;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 事件总线实现。
 *
 * @since 0.1.0
 */
public final class EventBusImpl implements EventBus {

    private final Map<String, List<ExtensionEventListener<?>>> listeners = new ConcurrentHashMap<>();

    @Override
    public <E extends ExtensionEvent> void emit(EventKey<E> key, E event) {
        for (ExtensionEventListener<?> listener : listeners.getOrDefault(key.key(), List.of())) {
            invoke(listener, event);
        }
    }

    @Override
    public <E extends ExtensionEvent> void on(EventKey<E> key, ExtensionEventListener<E> listener) {
        listeners.computeIfAbsent(key.key(), k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    @Override
    public <E extends ExtensionEvent> E waterfall(EventKey<E> key, E event) {
        // TODO(0.3): 实现 (event, next) 瀑布语义，支持变换、短路、包装
        if (key.mode() == DispatchMode.WATERFALL) {
            E current = event;
            for (ExtensionEventListener<?> listener : listeners.getOrDefault(key.key(), List.of())) {
                invoke(listener, current);
            }
            return current;
        }
        emit(key, event);
        return event;
    }

    @SuppressWarnings("unchecked")
    private static <E extends ExtensionEvent> void invoke(ExtensionEventListener<?> listener, E event) {
        ((ExtensionEventListener<E>) listener).onEvent(event);
    }
}
