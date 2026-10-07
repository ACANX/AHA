package com.acanx.module.aha.core.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 任务请求：跨进程 / 跨网络 / 跨云统一下发的任务描述。
 *
 * <p>作为远程调用、定时任务、云函数事件、协议适配器的公共入参契约。
 * 序列化采用 PascalCase，便于作为 JSON 载荷在不同形态间传递
 * （与 IR 的 {@code ChatRequest} 保持一致的命名约定）。</p>
 *
 * @param id          任务 ID；为空时由接收方生成（用于幂等与链路追踪）
 * @param parentId    父任务 ID（子任务编排场景）
 * @param source      任务来源
 * @param sessionId   会话 ID；为空时由接收方创建（多轮任务需复用）
 * @param input       任务输入（必填）
 * @param systemPrompt 系统提示词，可为 {@code null}
 * @param model       指定模型，可为 {@code null}（使用默认/兜底模型）
 * @param context     任务上下文（环境变量、回调地址、租户信息等）
 * @param deadlineEpochMillis 截止时间（epoch millis）；{@code null} 或 0 表示不限制。
 *                            使用包装类型以保证跨进程 JSON 缺省该字段时仍可解析
 * @param extensions  扩展字段（透传给 LLM 请求的 {@code extensions}）
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskRequest(
        @JsonProperty("Id") String id,
        @JsonProperty("ParentId") String parentId,
        @JsonProperty("Source") TaskSource source,
        @JsonProperty("SessionId") String sessionId,
        @JsonProperty("Input") String input,
        @JsonProperty("SystemPrompt") String systemPrompt,
        @JsonProperty("Model") String model,
        @JsonProperty("Context") Map<String, Object> context,
        @JsonProperty("DeadlineEpochMillis") Long deadlineEpochMillis,
        @JsonProperty("Extensions") Map<String, Object> extensions
) {

    /**
     * 构造请求（Map 字段归一化）。
     *
     * @param id                  任务 ID
     * @param parentId            父任务 ID
     * @param source              任务来源
     * @param sessionId           会话 ID
     * @param input               任务输入
     * @param systemPrompt        系统提示词
     * @param model               模型
     * @param context             任务上下文
     * @param deadlineEpochMillis 截止时间
     * @param extensions          扩展字段
     */
    public TaskRequest {
        context = context == null ? Map.of() : Map.copyOf(context);
        extensions = extensions == null ? Map.of() : Map.copyOf(extensions);
        source = source == null ? TaskSource.INTERACTIVE : source;
    }

    /**
     * 构造单次 CLI / 交互式任务。
     *
     * @param input 输入
     * @return 任务请求
     */
    public static TaskRequest of(String input) {
        return new TaskRequest(null, null, TaskSource.CLI, null, input,
                null, null, Map.of(), 0L, Map.of());
    }

    /**
     * 是否为定时 / 远程下发的非交互任务。
     *
     * @return 是否自动化任务
     */
    public boolean isAutomated() {
        return source == TaskSource.SCHEDULED
                || source == TaskSource.REMOTE_CALL
                || source == TaskSource.SERVERLESS
                || source == TaskSource.MCP
                || source == TaskSource.ACP
                || source == TaskSource.WEBHOOK;
    }

    /**
     * 截止时间（epoch millis），未设置时返回 0。
     *
     * @return 截止时间
     */
    public long deadlineMillis() {
        return deadlineEpochMillis == null ? 0L : deadlineEpochMillis;
    }
}
