package com.acanx.module.aha.common.tool;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 取消令牌，用于传播取消信号。
 *
 * @since 0.1.0
 */
public final class CancellationToken {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    /**
     * 取消。
     */
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            listeners.forEach(Runnable::run);
        }
    }

    /**
     * 是否已取消。
     *
     * @return 是否取消
     */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * 注册取消监听器。
     *
     * @param listener 监听器
     */
    public void onCancel(Runnable listener) {
        if (cancelled.get()) {
            listener.run();
        } else {
            listeners.add(listener);
        }
    }
}
