package com.acanx.module.aha.desktop.fx;

import javafx.application.Platform;

/**
 * 基于 JavaFX {@link Platform} 的 {@link FxDispatcher} 实现。
 *
 * <p><strong>这是全仓唯一允许调用 {@code Platform.runLater} 的地方。</strong>
 * 其它代码一律通过 {@link FxDispatcher} 间接投递，理由有两条：</p>
 *
 * <ol>
 *   <li>可测：单测不需要启动 JavaFX 运行时；</li>
 *   <li>可审计：线程模型只有一处入口，静态扫描即可保证不被绕过。</li>
 * </ol>
 *
 * @since 0.2.0
 */
public final class PlatformFxDispatcher implements FxDispatcher {

    @Override
    public boolean onUiThread() {
        return Platform.isFxApplicationThread();
    }

    @Override
    public void dispatch(Runnable task) {
        if (Platform.isFxApplicationThread()) {
            // 已在 UI 线程：直接执行，避免无谓绕一圈队列（也让调用序更容易推理）
            task.run();
            return;
        }
        Platform.runLater(task);
    }
}
