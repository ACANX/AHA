package com.acanx.module.aha.desktop.fx;

/**
 * 把任务投递到 UI 线程的唯一入口。
 *
 * <p>抽成接口而不是到处直接调 {@code Platform.runLater}，是为了让
 * <strong>线程模型可测</strong>：单测注入一个同步执行的实现，就能在没有图形环境的机器上
 * （CI、无显示的 WSL）验证「谁在什么时候更新界面」。这也是
 * {@code DesktopDesign.md} 线程模型一节的可执行契约。</p>
 *
 * <p>全仓只允许 {@link PlatformFxDispatcher} 触碰 JavaFX 的线程 API，
 * 该规则由 {@code FxThreadContractTest} 静态扫描主源码钉住。</p>
 *
 * @since 0.2.0
 */
public interface FxDispatcher {

    /**
     * 当前线程是否是 UI 线程。
     *
     * @return UI 线程返回 {@code true}
     */
    boolean onUiThread();

    /**
     * 投递任务：已在 UI 线程时立即执行，否则排到 UI 线程队列尾。
     *
     * @param task 待执行任务
     */
    void dispatch(Runnable task);
}
