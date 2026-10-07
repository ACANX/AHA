package com.acanx.module.aha.extension.api.event;

/**
 * 类型化事件键。
 *
 * @param <E> 事件类型
 * @since 0.1.0
 */
public final class EventKey<E extends ExtensionEvent> {

    private final String key;
    private final Class<E> type;
    private final DispatchMode mode;

    private EventKey(String key, Class<E> type, DispatchMode mode) {
        this.key = key;
        this.type = type;
        this.mode = mode;
    }

    /**
     * 创建事件键。
     *
     * @param key  键名
     * @param type 事件类型
     * @param mode 分发模式
     * @param <E>  事件类型
     * @return 事件键
     */
    public static <E extends ExtensionEvent> EventKey<E> of(String key, Class<E> type, DispatchMode mode) {
        return new EventKey<>(key, type, mode);
    }

    /**
     * 键名。
     *
     * @return 键名
     */
    public String key() {
        return key;
    }

    /**
     * 事件类型。
     *
     * @return 类型
     */
    public Class<E> type() {
        return type;
    }

    /**
     * 分发模式。
     *
     * @return 模式
     */
    public DispatchMode mode() {
        return mode;
    }
}
