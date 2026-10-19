package com.acanx.module.aha.cli.render;

/**
 * 运行状态位（issue #342）：状态行只回答「现在在干什么」。
 *
 * <p>状态迁移由事件驱动，见 {@code StreamRenderer}：
 * 回合开始 → {@link #REQUESTING}；首个正文增量 → {@link #GENERATING}；
 * 工具调用 → {@link #TOOL_RUNNING}；工具结果 → 回到 {@link #GENERATING}；
 * 审批问答 → {@link #AWAITING_APPROVAL}；流结束 → {@link #DONE}；
 * 异常 → {@link #FAILED}；用户中断（ESC / Ctrl+C）→ {@link #INTERRUPTED}；
 * 结束态短暂保留后 → {@link #IDLE}。</p>
 *
 * @since 0.1.5
 */
enum RunState {

    /** 空闲：显示静态三项。 */
    IDLE,

    /** 请求中：请求已发出、尚未收到首个正文增量（显示耗时）。 */
    REQUESTING,

    /** 生成中：已收到正文增量（不显示耗时，避免与工具耗时混淆）。 */
    GENERATING,

    /** 工具执行中：工具已开始调用（显示耗时）。 */
    TOOL_RUNNING,

    /** 等待审批：工具需要用户确认。 */
    AWAITING_APPROVAL,

    /** 完成：回合正常结束（短暂显示后回到空闲）。 */
    DONE,

    /** 失败：回合异常结束（单行原因摘要，短暂显示后回到空闲）。 */
    FAILED,

    /** 已中断：用户主动 ESC / Ctrl+C（不显示原因，短暂显示后回到空闲）。 */
    INTERRUPTED
}
