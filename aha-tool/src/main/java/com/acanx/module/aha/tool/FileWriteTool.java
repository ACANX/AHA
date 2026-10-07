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
 * 文件写入工具（需用户确认）。
 *
 * @since 0.1.0
 */
public final class FileWriteTool implements Tool {

    @Override
    public String name() {
        return "file-write";
    }

    @Override
    public String description() {
        return "写入文件，需用户确认";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(
                Map.of(
                        "path", JsonSchema.string("文件路径"),
                        "content", JsonSchema.string("写入内容")),
                List.of("path", "content"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params, CancellationToken token) {
        Object path = params.get("path");
        Object content = params.get("content");
        if (path == null || content == null) {
            return ToolResult.failure("缺少参数: path / content");
        }
        try {
            Files.writeString(Path.of(path.toString()), content.toString());
            return ToolResult.success("写入成功: " + path);
        } catch (IOException e) {
            return ToolResult.failure("写入失败: " + e.getMessage());
        }
    }

    @Override
    public ToolPermission requiredPermission() {
        return ToolPermission.WRITE;
    }
}
