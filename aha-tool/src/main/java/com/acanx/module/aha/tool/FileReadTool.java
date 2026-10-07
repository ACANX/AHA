package com.acanx.module.aha.tool;

import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.common.tool.JsonSchema;
import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.tool.ToolPermission;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * 文件读取工具。
 *
 * @since 0.1.0
 */
public final class FileReadTool implements Tool {

    @Override
    public String name() {
        return "file-read";
    }

    @Override
    public String description() {
        return "读取文件内容";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(
                Map.of("path", JsonSchema.string("文件路径")),
                List.of("path"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params, CancellationToken token) {
        Object path = params.get("path");
        if (path == null) {
            return ToolResult.failure("缺少参数: path");
        }
        try {
            return ToolResult.success(Files.readString(Path.of(path.toString())));
        } catch (IOException e) {
            return ToolResult.failure("读取失败: " + e.getMessage());
        }
    }

    @Override
    public ToolPermission requiredPermission() {
        return ToolPermission.READ;
    }
}
