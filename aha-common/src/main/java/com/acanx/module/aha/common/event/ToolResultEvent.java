package com.acanx.module.aha.common.event;

/**
 * 工具执行结果事件。
 *
 * <p>{@code success} 是显式字段而不是从 {@code result} 文本猜测：展示层要按成败着色
 * （失败用红色区块引起注意），靠字符串前缀判断既脆弱又会随工具文案变化而失效。</p>
 *
 * @param sessionId 会话 ID
 * @param toolName  工具名
 * @param result    工具输出（失败时为错误信息）
 * @param success   是否执行成功
 * @since 0.1.0
 */
public record ToolResultEvent(String sessionId, String toolName, Object result, boolean success)
        implements AgentEvent {

    /**
     * 兼容构造：默认视为成功。
     *
     * @param sessionId 会话 ID
     * @param toolName  工具名
     * @param result    工具输出
     */
    public ToolResultEvent(String sessionId, String toolName, Object result) {
        this(sessionId, toolName, result, true);
    }
}
