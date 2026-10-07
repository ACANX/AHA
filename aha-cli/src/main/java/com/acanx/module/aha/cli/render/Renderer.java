package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.common.event.AgentEvent;

/**
 * Agent 事件渲染器。
 *
 * <p>把 {@link AgentEvent} 流渲染到终端。这是核心与终端之间唯一的缝合点：
 * 核心只发事件，不关心颜色、宽度与平台差异。</p>
 *
 * @since 0.1.0
 */
public interface Renderer {

    /**
     * 开始一轮：用于展示等待状态（首次响应到达前、工具执行期间）。
     *
     * <p>不发出任何事件也应调用，否则「已发出请求但还没有任何输出」这段等待
     * 会完全没有反馈。</p>
     */
    void beginTurn();

    /**
     * 渲染一个事件。
     *
     * @param event 事件
     */
    void onEvent(AgentEvent event);

    /**
     * 一轮结束：补齐换行等收尾。
     */
    void endTurn();
}
