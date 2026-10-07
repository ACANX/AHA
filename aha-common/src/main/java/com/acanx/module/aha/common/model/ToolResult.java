package com.acanx.module.aha.common.model;

/**
 * 工具执行结果。
 *
 * @param success 是否成功
 * @param output  输出
 * @param error   错误信息
 * @since 0.1.0
 */
public record ToolResult(boolean success, Object output, String error) {

    /**
     * 构造成功结果。
     *
     * @param output 输出
     * @return 成功结果
     */
    public static ToolResult success(Object output) {
        return new ToolResult(true, output, null);
    }

    /**
     * 构造失败结果。
     *
     * @param error 错误信息
     * @return 失败结果
     */
    public static ToolResult failure(String error) {
        return new ToolResult(false, null, error);
    }
}
