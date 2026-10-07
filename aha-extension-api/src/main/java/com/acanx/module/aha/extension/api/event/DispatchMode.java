package com.acanx.module.aha.extension.api.event;

/**
 * 事件分发模式。
 *
 * @since 0.1.0
 */
public enum DispatchMode {
    /** 广播，忽略返回。 */
    EMIT,
    /** 瀑布，可变换/短路。 */
    WATERFALL,
    /** 串行。 */
    SERIAL,
    /** 并行。 */
    PARALLEL
}
