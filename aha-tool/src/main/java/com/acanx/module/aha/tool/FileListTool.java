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
import java.util.stream.Stream;

/**
 * 目录列举工具。
 *
 * @since 0.1.0
 */
public final class FileListTool implements Tool {

    @Override
    public String name() {
        return "file-list";
    }

    @Override
    public String description() {
        return "列出目录内容";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(
                Map.of("path", JsonSchema.string("目录路径")),
                List.of("path"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params, CancellationToken token) {
        Object path = params.get("path");
        if (path == null) {
            return ToolResult.failure("缺少参数: path");
        }
        try (Stream<Path> entries = Files.list(Path.of(path.toString()))) {
            return ToolResult.success(entries.map(Path::toString).toList());
        } catch (IOException e) {
            return ToolResult.failure("列举失败: " + e.getMessage());
        }
    }

    @Override
    public ToolPermission requiredPermission() {
        return ToolPermission.READ;
    }
}
