package com.acanx.module.aha.core.runtime;

import com.acanx.module.aha.common.event.AgentEvent;
import com.acanx.module.aha.common.event.ContentEvent;
import com.acanx.module.aha.common.event.DoneEvent;
import com.acanx.module.aha.common.event.ErrorEvent;
import com.acanx.module.aha.common.runtime.RuntimeDescriptor;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.core.task.TaskRequest;
import com.acanx.module.aha.core.task.TaskResult;

import java.util.function.Consumer;

/**
 * Agent 运行时：在某个环境中执行任务的能力抽象。
 *
 * <p>0.1 只有进程内实现（{@code LocalAgentService} 直接承担）；
 * 1.0 起的远程 / WSL / 容器 / 云函数形态均实现本接口，
 * 上层（CLI、调度器、协议适配器、云函数入口）面向本接口编程，不感知传输细节。</p>
 *
 * @since 0.1.0
 */
public interface AgentRuntime extends AutoCloseable {

    /**
     * 运行时描述符。
     *
     * @return 描述符
     */
    RuntimeDescriptor descriptor();

    /**
     * 运行时当前是否可用。
     *
     * @return 是否可用
     */
    boolean isAvailable();

    /**
     * 同步提交任务。
     *
     * @param request 任务请求
     * @param token   取消令牌
     * @return 任务结果
     */
    TaskResult submit(TaskRequest request, CancellationToken token);

    /**
     * 流式提交任务。
     *
     * <p>默认实现将同步结果转换为事件序列；具备原生流式能力的实现应覆写。</p>
     *
     * @param request 任务请求
     * @param sink    事件接收器
     * @param token   取消令牌
     */
    default void submitStreaming(TaskRequest request, Consumer<AgentEvent> sink, CancellationToken token) {
        TaskResult result = submit(request, token);
        if (result.success()) {
            sink.accept(new ContentEvent(result.taskId(), result.output()));
            sink.accept(new DoneEvent(result.taskId(), "stop"));
        } else {
            sink.accept(new ErrorEvent(result.taskId(), result.errorCode(), result.errorMessage()));
            sink.accept(new DoneEvent(result.taskId(), "error"));
        }
    }

    /**
     * 释放运行时资源。
     */
    @Override
    default void close() {
    }
}
