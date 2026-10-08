package com.acanx.module.aha.desktop.fx;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 记录式 {@link FxDispatcher}：把任务排队，由测试手工推进。
 *
 * <p>这样就能在没有图形环境的机器上验证线程模型——CI 与无显示的 WSL 都能跑。</p>
 *
 * @since 0.2.0
 */
final class RecordingFxDispatcher implements FxDispatcher {

    private final Deque<Runnable> queue = new ArrayDeque<>();

    /** 投递次数（含合并后仍会重复投递的那些）。 */
    private int dispatchCount;

    @Override
    public boolean onUiThread() {
        return false;
    }

    @Override
    public void dispatch(Runnable task) {
        dispatchCount++;
        queue.add(task);
    }

    /**
     * 投递次数。
     *
     * @return 次数
     */
    int dispatchCount() {
        return dispatchCount;
    }

    /**
     * 待执行任务数。
     *
     * @return 队列长度
     */
    int pending() {
        return queue.size();
    }

    /**
     * 推进 UI 线程：执行队列里的全部任务。
     *
     * <p>注意它会连带执行执行期间新排入的任务（{@code runLater} 的真实语义）。</p>
     */
    void drain() {
        while (!queue.isEmpty()) {
            queue.poll().run();
        }
    }

    /**
     * 只执行调用时刻已入队的任务，不捎带执行期间新排入的。
     *
     * <p>用于观察「一帧之内」的中间状态。</p>
     */
    void drainCurrent() {
        int count = queue.size();
        for (int i = 0; i < count; i++) {
            queue.poll().run();
        }
    }
}
