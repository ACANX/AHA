package com.acanx.module.aha.extension.api.point;

import com.acanx.module.aha.common.event.AgentEvent;

/**
 * Agent 事件监听扩展点。
 *
 * @since 0.1.0
 */
public interface EventListenerProvider {

    /**
     * 处理 Agent 事件。
     *
     * @param event 事件
     */
    void onAgentEvent(AgentEvent event);
}
