package com.acanx.module.aha.core.llm;

import com.acanx.module.aha.core.llm.protocol.StreamEvent;

/**
 * 流式事件监听器。
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface StreamEventListener {

    /**
     * 处理流式事件。
     *
     * @param event 事件
     */
    void onEvent(StreamEvent event);
}
