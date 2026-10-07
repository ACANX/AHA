package com.acanx.module.aha.tool;

import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.tool.ToolProvider;

import java.util.List;

/**
 * 文件工具提供者。
 *
 * @since 0.1.0
 */
public final class FileToolProvider implements ToolProvider {

    @Override
    public List<Tool> tools() {
        return List.of(new FileReadTool(), new FileWriteTool(), new FileListTool());
    }
}
