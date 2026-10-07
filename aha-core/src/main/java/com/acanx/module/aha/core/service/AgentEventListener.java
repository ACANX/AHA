package com.acanx.module.aha.core.service;

import com.acanx.module.aha.common.event.AgentEvent;

/**
 * Agent 事件监听器。
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface AgentEventListener {

    /**
     * 处理事件。
     *
     * @param event 事件
     */
    void onEvent(AgentEvent event);
}
