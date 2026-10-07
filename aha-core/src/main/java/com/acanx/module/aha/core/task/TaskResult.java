package com.acanx.module.aha.core.task;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;

/**
 * 任务执行结果。
 *
 * @param taskId         任务 ID
 * @param success        是否成功
 * @param output         最终输出（成功时）
 * @param errorCode      错误码（失败时）
 * @param errorMessage   错误信息（失败时）
 * @param toolCalls      本次任务调用的工具名序列（审计用）
 * @param durationMillis 执行耗时（毫秒）；使用包装类型以保证跨进程 JSON 缺省该字段时仍可解析
 * @param metadata       附加信息（token 用量、运行时 ID、模型名等）
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskResult(
        @JsonProperty("TaskId") String taskId,
        @JsonProperty("Success") boolean success,
        @JsonProperty("Output") String output,
        @JsonProperty("ErrorCode") String errorCode,
        @JsonProperty("ErrorMessage") String errorMessage,
        @JsonProperty("ToolCalls") List<String> toolCalls,
        @JsonProperty("DurationMillis") Long durationMillis,
        @JsonProperty("Metadata") Map<String, Object> metadata
) {

    /**
     * 构造结果（集合字段归一化）。
     *
     * @param taskId         任务 ID
     * @param success        是否成功
     * @param output         输出
     * @param errorCode      错误码
     * @param errorMessage   错误信息
     * @param toolCalls      工具调用序列
     * @param durationMillis 耗时
     * @param metadata       附加信息
     */
    public TaskResult {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /**
     * 构造成功结果。
     *
     * @param taskId 任务 ID
     * @param output 输出
     * @return 结果
     */
    public static TaskResult success(String taskId, String output) {
        return new TaskResult(taskId, true, output, null, null, List.of(), 0L, Map.of());
    }

    /**
     * 构造失败结果。
     *
     * @param taskId  任务 ID
     * @param code    错误码
     * @param message 错误信息
     * @return 结果
     */
    public static TaskResult failure(String taskId, String code, String message) {
        return new TaskResult(taskId, false, null, code, message, List.of(), 0L, Map.of());
    }
}
