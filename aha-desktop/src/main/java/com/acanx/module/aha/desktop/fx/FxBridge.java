package com.acanx.module.aha.desktop.fx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 把「高频的生产者」与「UI 线程的渲染」合流：只保留最新值，至多一次在途 UI 任务。
 *
 * <p>模型流式输出时，内容以很高的频率到达（每个 token / 每个增量事件一次）。
 * 若每次都投递一个 UI 任务，队列会被塞满、界面反而卡顿。这里的策略是
 * <strong>合并（coalescing）</strong>：</p>
 *
 * <ul>
 *   <li>{@link #submit} 只把值写进 {@code pending} 并置「已排程」标记，不排队；</li>
 *   <li>只有第一个到达者真正投递一次 UI 任务，后续值覆盖前值；</li>
 *   <li>渲染时取走最新值并清空标记，因此<strong>最后一个值一定会被渲染</strong>。</li>
 * </ul>
 *
 * <p>注意「合并」与「丢失」的区别：被合并掉的是<em>中间态</em>，不是数据本身。
 * 累积正文由渲染侧自己的缓冲负责（见 {@code TUIDesign.md} 的同类做法），
 * 这里只保证「界面最终与最新状态一致」。</p>
 *
 * <p>关窗后（{@link #close()}）所有 {@code submit} 静默丢弃，<strong>已排程但尚未渲染的
 * 值也一并丢弃</strong>：正在关窗，渲染已无意义；而 JavaFX 工具箱停止后再投递任务会抛异常。</p>
 *
 * @param <T> 待渲染的值类型
 * @since 0.2.0
 */
public final class FxBridge<T> {

    private static final Logger LOG = LoggerFactory.getLogger(FxBridge.class);

    private final FxDispatcher dispatcher;
    private final Consumer<T> renderer;
    private final AtomicReference<T> pending = new AtomicReference<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();

    /**
     * @param dispatcher UI 线程投递器
     * @param renderer   渲染动作，保证在 UI 线程执行
     */
    public FxBridge(FxDispatcher dispatcher, Consumer<T> renderer) {
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.renderer = Objects.requireNonNull(renderer, "renderer");
    }

    /**
     * 提交一个待渲染值（可从任意线程调用）。
     *
     * @param value 最新状态；为 {@code null} 时忽略
     */
    public void submit(T value) {
        if (value == null || closed.get()) {
            return;
        }
        pending.set(value);
        if (scheduled.compareAndSet(false, true)) {
            dispatcher.dispatch(this::flush);
        }
    }

    /** 关闭桥接：后续 {@link #submit} 一律丢弃（供关窗联动）。 */
    public void close() {
        closed.set(true);
        pending.set(null);
    }

    /**
     * 是否已关闭。
     *
     * @return 已关闭返回 {@code true}
     */
    public boolean isClosed() {
        return closed.get();
    }

    private void flush() {
        // 先清排程标记再取值：这样渲染期间新到的值会重新排程，不会被吞掉
        scheduled.set(false);
        if (closed.get()) {
            return;
        }
        T value = pending.getAndSet(null);
        if (value == null) {
            return;
        }
        try {
            renderer.accept(value);
        } catch (RuntimeException e) {
            // 单次渲染失败不应终止应用：记录后继续，下一次 submit 照常工作
            LOG.error("UI 渲染失败", e);
        }
    }
}
