package com.acanx.module.aha.tool;

import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.tool.ToolProvider;

import java.util.List;

/**
 * HTTP 工具提供者。
 *
 * @since 0.1.0
 */
public final class HttpToolProvider implements ToolProvider {

    @Override
    public List<Tool> tools() {
        return List.of(new HttpGetTool());
    }
}
