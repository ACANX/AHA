package com.acanx.module.aha.core.llm.protocol;

/**
 * IR 流式事件类型。
 *
 * @since 0.1.0
 */
public enum StreamEventType {
    CONTENT_DELTA, REASONING_DELTA, TOOL_CALL_DELTA,
    USAGE, DONE, ERROR
}
