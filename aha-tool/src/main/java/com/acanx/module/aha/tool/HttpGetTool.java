package com.acanx.module.aha.tool;

import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.common.tool.JsonSchema;
import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.tool.ToolPermission;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * HTTP GET 工具。
 *
 * @since 0.1.0
 */
public final class HttpGetTool implements Tool {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public String name() {
        return "http-get";
    }

    @Override
    public String description() {
        return "执行 HTTP GET 请求";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(
                Map.of("url", JsonSchema.string("请求 URL")),
                List.of("url"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params, CancellationToken token) {
        Object url = params.get("url");
        if (url == null) {
            return ToolResult.failure("缺少参数: url");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return ToolResult.success(response.body());
        } catch (Exception e) {
            return ToolResult.failure("请求失败: " + e.getMessage());
        }
    }

    @Override
    public ToolPermission requiredPermission() {
        return ToolPermission.NETWORK;
    }
}
