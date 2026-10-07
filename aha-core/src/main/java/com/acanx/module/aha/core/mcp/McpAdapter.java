package com.acanx.module.aha.core.mcp;

import com.acanx.module.aha.common.model.ToolDescriptor;
import com.acanx.module.aha.common.model.ToolResult;

import java.util.List;
import java.util.Map;

/**
 * MCP 适配器接口（0.1 仅定义接口，不提供实现）。
 *
 * @since 0.1.0
 */
public interface McpAdapter {

    /**
     * 连接 MCP 服务器。
     *
     * @param serverUrl 服务器地址
     */
    void connect(String serverUrl);

    /**
     * 发现工具。
     *
     * @return 工具描述符
     */
    List<ToolDescriptor> discoverTools();

    /**
     * 调用工具。
     *
     * @param toolName 工具名
     * @param params   参数
     * @return 结果
     */
    ToolResult invokeTool(String toolName, Map<String, Object> params);

    /**
     * 断开连接。
     */
    void disconnect();
}
