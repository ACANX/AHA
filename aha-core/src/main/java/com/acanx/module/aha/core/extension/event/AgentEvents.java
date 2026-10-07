package com.acanx.module.aha.core.extension.event;

import com.acanx.module.aha.extension.api.event.DispatchMode;
import com.acanx.module.aha.extension.api.event.EventKey;

/**
 * AHA 预定义事件键。
 *
 * @since 0.1.0
 */
public final class AgentEvents {

    /** 推理步进前。 */
    public static final EventKey<PreStepEvent> PRE_STEP =
            EventKey.of("agent/pre-step", PreStepEvent.class, DispatchMode.WATERFALL);

    /** LLM 请求。 */
    public static final EventKey<RequestEvent> REQUEST =
            EventKey.of("agent/request", RequestEvent.class, DispatchMode.WATERFALL);

    /** 工具执行前。 */
    public static final EventKey<ToolPreExecuteEvent> TOOL_PRE_EXECUTE =
            EventKey.of("tools/pre-execute", ToolPreExecuteEvent.class, DispatchMode.WATERFALL);

    /** 工具执行后。 */
    public static final EventKey<ToolPostExecuteEvent> TOOL_POST_EXECUTE =
            EventKey.of("tools/post-execute", ToolPostExecuteEvent.class, DispatchMode.WATERFALL);

    /** LLM 流式。 */
    public static final EventKey<LlmStreamEvent> LLM_STREAM =
            EventKey.of("llm/stream", LlmStreamEvent.class, DispatchMode.WATERFALL);

    /** 系统提示词组装。 */
    public static final EventKey<SystemPromptEvent> SYSTEM_PROMPT_ASSEMBLE =
            EventKey.of("system-prompt/assemble", SystemPromptEvent.class, DispatchMode.WATERFALL);

    /** 会话落盘。 */
    public static final EventKey<SessionFlushEvent> SESSION_FLUSH =
            EventKey.of("session/flush", SessionFlushEvent.class, DispatchMode.PARALLEL);

    /** 生命周期。 */
    public static final EventKey<LifecycleEvent> LIFECYCLE =
            EventKey.of("lifecycle", LifecycleEvent.class, DispatchMode.EMIT);

    private AgentEvents() {
    }
}
