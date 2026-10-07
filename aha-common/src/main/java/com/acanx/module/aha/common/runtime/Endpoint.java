package com.acanx.module.aha.common.runtime;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 运行时端点：定位 Agent 能力所在的环境。
 *
 * <p>统一寻址语法（scheme 决定后续由哪个 {@code AgentServiceProvider} 处理）：</p>
 *
 * <pre>
 * local://
 * wsl://Ubuntu?user=me
 * ssh://user@build-01:22
 * daemon://127.0.0.1:8787
 * ws://relay.example.com/agent
 * https://cloud.example.com/fn/aha
 * serverless://aliyun/fc/aha-fn
 * </pre>
 *
 * @param scheme   协议/形态，如 {@code local}、{@code wsl}、{@code ssh}、{@code daemon}
 * @param host     主机名或发行版名，可为 {@code null}
 * @param port     端口，0 表示未指定
 * @param path     路径，可为 {@code null}
 * @param user     用户，可为 {@code null}
 * @param params   查询参数
 * @since 0.1.0
 */
public record Endpoint(
        String scheme,
        String host,
        int port,
        String path,
        String user,
        Map<String, String> params
) {

    /**
     * 构造端点（scheme 归一化为小写，params 归一化为不可变 Map）。
     *
     * @param scheme 协议/形态
     * @param host   主机名
     * @param port   端口
     * @param path   路径
     * @param user   用户
     * @param params 查询参数
     */
    public Endpoint {
        scheme = scheme == null ? "local" : scheme.toLowerCase(Locale.ROOT);
        params = params == null ? Map.of() : Map.copyOf(params);
    }

    /**
     * 本机端点。
     *
     * @return 端点
     */
    public static Endpoint local() {
        return new Endpoint("local", null, 0, null, null, Map.of());
    }

    /**
     * 解析端点 URI。
     *
     * @param uri 形如 {@code ssh://user@host:22} 的地址
     * @return 端点
     */
    public static Endpoint parse(String uri) {
        if (uri == null || uri.isBlank()) {
            return local();
        }
        String trimmed = uri.trim();
        int separator = trimmed.indexOf("://");
        if (separator < 0) {
            // 仅有 scheme，如 "local" / "local:"
            return new Endpoint(trimmed.replace(":", ""), null, 0, null, null, Map.of());
        }
        String scheme = trimmed.substring(0, separator);
        if ("local".equalsIgnoreCase(scheme)) {
            // local:// 无 authority，交给 URI 解析会报错
            return local();
        }
        URI parsed = URI.create(trimmed);
        String host = parsed.getHost();
        String user = parsed.getUserInfo();
        if (host == null && parsed.getAuthority() != null) {
            // wsl://Ubuntu、wsl://me@Ubuntu 这类地址不带端口信息
            String authority = parsed.getAuthority();
            int at = authority.indexOf('@');
            if (at >= 0) {
                if (user == null) {
                    user = authority.substring(0, at);
                }
                authority = authority.substring(at + 1);
            }
            int colon = authority.lastIndexOf(':');
            if (colon >= 0) {
                authority = authority.substring(0, colon);
            }
            host = authority;
        }
        Map<String, String> params = new LinkedHashMap<>();
        if (parsed.getQuery() != null) {
            for (String pair : parsed.getQuery().split("&")) {
                if (pair.isBlank()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                params.put(eq < 0 ? pair : pair.substring(0, eq),
                        eq < 0 ? "" : pair.substring(eq + 1));
            }
        }
        return new Endpoint(scheme, host, parsed.getPort(), parsed.getPath(), user, params);
    }

    /**
     * 是否为本地端点。
     *
     * @return 是否本地
     */
    public boolean isLocal() {
        return "local".equals(scheme);
    }

    /**
     * 还原为 URI 文本。
     *
     * @return URI 文本
     */
    public String toUri() {
        StringBuilder sb = new StringBuilder(scheme).append("://");
        if (user != null) {
            sb.append(user).append('@');
        }
        if (host != null) {
            sb.append(host);
        }
        if (port > 0) {
            sb.append(':').append(port);
        }
        if (path != null) {
            sb.append(path);
        }
        if (!params.isEmpty()) {
            sb.append('?').append(params.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .reduce((a, b) -> a + "&" + b)
                    .orElse(""));
        }
        return sb.toString();
    }
}
