package com.acanx.module.aha.common.tool;

import com.acanx.module.aha.common.model.ToolResult;

import java.util.Map;

/**
 * 工具 SPI。
 *
 * @since 0.1.0
 */
public interface Tool {

    /**
     * 工具名。
     *
     * @return 工具名
     */
    String name();

    /**
     * 工具描述。
     *
     * @return 描述
     */
    String description();

    /**
     * 参数 Schema。
     *
     * @return JSON Schema
     */
    JsonSchema parameters();

    /**
     * 执行工具。
     *
     * @param params 参数
     * @param token  取消令牌
     * @return 执行结果
     */
    ToolResult execute(Map<String, Object> params, CancellationToken token);

    /**
     * 所需权限。
     *
     * @return 权限
     */
    ToolPermission requiredPermission();

    /**
     * 注入工具配置。
     *
     * <p>工具由 {@code ServiceLoader} 创建，构造器无法接收参数，
     * 因此需要在此接收配置；{@code ToolRegistry} 在初始化后统一调用。</p>
     *
     * <p>默认实现忽略配置，仅需配置的工具覆盖。此前 {@code Tools.Shell.*}
     * 等配置项从未生效，正是因为缺少这一入口。</p>
     *
     * @param settings 配置视图
     */
    default void configure(ToolSettings settings) {
    }
}
