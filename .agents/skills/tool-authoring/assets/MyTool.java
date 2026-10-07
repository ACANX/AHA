package com.acanx.module.aha.tool;

import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.common.tool.JsonSchema;
import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.tool.ToolPermission;

import java.util.List;
import java.util.Map;

/**
 * 工具实现模板。
 *
 * <p>放置位置：{@code aha-tool/src/main/java/com/acanx/module/aha/tool/}。</p>
 *
 * <p>完成后还需：</p>
 * <ol>
 *   <li>把本类加入某个 {@code ToolProvider.tools()}</li>
 *   <li>若新增了 Provider，需在 {@code aha-tool/src/main/java/module-info.java}
 *       的 {@code provides} 与 {@code META-INF/services} 中同步声明</li>
 * </ol>
 *
 * <p>实现参考：{@code FileReadTool}、{@code HttpGetTool}、{@code ShellExecTool}。</p>
 *
 * @since 0.1.0
 */
public final class MyTool implements Tool {

    @Override
    public String name() {
        return "my-tool";
    }

    @Override
    public String description() {
        return "描述这个工具做什么，以及模型在什么情况下应该调用它";
    }

    /**
     * 参数 Schema：必须与 {@link #execute} 实际读取的键一致。
     */
    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(
                Map.of(
                        "path", JsonSchema.string("文件路径"),
                        "limit", JsonSchema.integer("最大读取行数（可选）")),
                List.of("path"));
    }

    /**
     * 执行工具。
     *
     * <p>约束：</p>
     * <ul>
     *   <li>参数缺失或非法时返回 {@link ToolResult#failure(String)}，不抛异常</li>
     *   <li>长耗时操作需检查 {@code token}，可被取消</li>
     *   <li>失败信息不要包含敏感数据（API Key、文件内容全文等）</li>
     * </ul>
     */
    @Override
    public ToolResult execute(Map<String, Object> params, CancellationToken token) {
        Object path = params.get("path");
        if (path == null) {
            return ToolResult.failure("缺少参数: path");
        }
        // TODO 实现
        return ToolResult.success(null);
    }

    /**
     * 所需权限：{@code READ} / {@code NETWORK} 默认放行；
     * {@code WRITE} / {@code EXECUTE} / {@code ADMIN} 需要显式授权。
     */
    @Override
    public ToolPermission requiredPermission() {
        return ToolPermission.READ;
    }
}
