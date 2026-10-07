package com.acanx.module.aha.core.task;

/**
 * 任务来源。
 *
 * <p>用于审计、限流与权限判定；也为后续协议适配器（ACP / MCP / Webhook）预留统一标识。</p>
 *
 * @since 0.1.0
 */
public enum TaskSource {

    /** 交互式会话（CLI chat / 桌面端对话框）。 */
    INTERACTIVE,

    /** 单次命令行调用（{@code aha run}）。 */
    CLI,

    /** 定时任务（cron / 调度器）。 */
    SCHEDULED,

    /** 远程调用（RPC / HTTP / 消息队列）。 */
    REMOTE_CALL,

    /** 云函数 / Serverless 事件触发。 */
    SERVERLESS,

    /** 通过 MCP 协议下发。 */
    MCP,

    /** 通过 ACP 协议下发。 */
    ACP,

    /** 外部系统 Webhook。 */
    WEBHOOK,

    /** 由扩展发起。 */
    PLUGIN
}
