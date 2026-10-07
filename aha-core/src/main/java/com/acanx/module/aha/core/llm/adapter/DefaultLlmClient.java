package com.acanx.module.aha.core.llm.adapter;

import com.acanx.module.aha.common.exception.LlmException;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.core.config.ConfigLoader;
import com.acanx.module.aha.core.config.LlmConfig;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.llm.LlmClient;
import com.acanx.module.aha.core.llm.LlmProviderAdapter;
import com.acanx.module.aha.core.llm.StreamEventListener;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;
import com.acanx.module.aha.core.llm.protocol.StreamEvent;
import com.acanx.module.aha.core.llm.protocol.StreamEventType;
import com.acanx.module.aha.core.llm.registry.LlmAdapterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 默认 LLM 客户端实现。
 *
 * <p>职责：选择适配器、构建并发送 HTTP 请求、指数退避重试、连接/读取超时、
 * 令牌桶限流、SSE 事件解析、取消传播。</p>
 *
 * @since 0.1.0
 */
public final class DefaultLlmClient implements LlmClient, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultLlmClient.class);

    private static final String EXT_PROVIDER = "Provider";

    private final LlmAdapterRegistry registry;
    private final LlmConfig llmConfig;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    /**
     * 构造客户端。
     *
     * @param registry  适配器注册表
     * @param llmConfig LLM 配置
     */
    public DefaultLlmClient(LlmAdapterRegistry registry, LlmConfig llmConfig) {
        this.registry = registry;
        this.llmConfig = llmConfig;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    /**
     * 适配器注册表。
     *
     * @return 注册表
     */
    public LlmAdapterRegistry registry() {
        return registry;
    }

    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        return CompletableFuture.supplyAsync(() -> chatSync(request), executor);
    }

    @Override
    public void streamChat(ChatRequest request, StreamEventListener listener, CancellationToken token) {
        ProviderConfig provider = resolveProvider(request);
        LlmProviderAdapter adapter = resolveAdapter(provider);
        String url = streamUrl(adapter, provider);
        String body = adapter.convertRequest(withStream(request, true), provider);

        HttpRequest httpRequest = newRequest(url, adapter.buildHeaders(provider), body, provider);
        try {
            LOG.debug("LLM stream -> {} ({})", url, adapter.providerId());
            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
                // 服务端返回的具体原因（模型不存在、额度不足、内容审核等）就在响应体里。
                // 早期只报状态码，导致用户只能看到“HTTP 422”而无从排查。
                String detail;
                try (java.util.stream.Stream<String> errLines = response.body()) {
                    detail = errLines.limit(ERROR_BODY_MAX_LINES)
                            .collect(java.util.stream.Collectors.joining(" "))
                            .trim();
                }
                throw new LlmException("LLM_HTTP_" + response.statusCode(),
                        "流式请求失败: HTTP " + response.statusCode() + formatDetail(detail));
            }
            Map<Integer, StringBuilder> toolArguments = new LinkedHashMap<>();
            try (java.util.stream.Stream<String> lines = response.body()) {
                var iterator = lines.iterator();
                while (iterator.hasNext()) {
                    if (token.isCancelled()) {
                        break;
                    }
                    String line = iterator.next();
                    if (line == null || line.isBlank()) {
                        continue;
                    }
                    dispatchLine(adapter, provider, line, listener, toolArguments);
                }
            }
        } catch (IOException e) {
            throw new LlmException("LLM_STREAM_IO", "流式请求 I/O 失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM_STREAM_INTERRUPTED", "流式请求被中断", e);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------

    private ChatResponse chatSync(ChatRequest request) {
        ProviderConfig provider = resolveProvider(request);
        LlmProviderAdapter adapter = resolveAdapter(provider);
        String url = adapter.buildUrl(provider);
        String body = adapter.convertRequest(withStream(request, false), provider);

        int maxRetries = provider.maxRetries() > 0 ? provider.maxRetries() : 3;
        LlmException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                limiter(provider).acquire();
                HttpRequest httpRequest = newRequest(url, adapter.buildHeaders(provider), body, provider);
                LOG.debug("LLM chat -> {} ({}) attempt={}", url, adapter.providerId(), attempt);
                HttpResponse<String> response =
                        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status >= 500 && attempt < maxRetries) {
                    backoff(attempt);
                    continue;
                }
                if (status >= 400) {
                    throw new LlmException("LLM_HTTP_" + status,
                            "请求失败: HTTP " + status + formatDetail(response.body()));
                }
                return adapter.convertResponse(response.body(), provider);
            } catch (LlmException e) {
                throw e;
            } catch (IOException e) {
                last = new LlmException("LLM_IO", "请求 I/O 失败", e);
                if (attempt < maxRetries) {
                    backoff(attempt);
                    continue;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LlmException("LLM_INTERRUPTED", "请求被中断", e);
            }
        }
        throw last != null ? last : new LlmException("LLM_FAILED", "请求失败");
    }

    /** 错误响应体的最大字符数，避免超长报文淹没终端与日志。 */
    private static final int ERROR_BODY_MAX_CHARS = 800;
    /** 错误响应体最多读取的行数。 */
    private static final long ERROR_BODY_MAX_LINES = 20;

    /**
     * 格式化错误响应体：去空白、截断超长内容，为空时返回空串。
     *
     * @param detail 原始响应体
     * @return 可直接拼接到错误信息后的文本（含前导空格），无内容时为空串
     */
    private static String formatDetail(String detail) {
        if (detail == null) {
            return "";
        }
        String trimmed = detail.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() > ERROR_BODY_MAX_CHARS) {
            trimmed = trimmed.substring(0, ERROR_BODY_MAX_CHARS) + "…（已截断）";
        }
        return " " + trimmed;
    }

    private void dispatchLine(LlmProviderAdapter adapter, ProviderConfig provider, String line,
                              StreamEventListener listener, Map<Integer, StringBuilder> toolArguments) {
        String trimmed = line.trim();
        // Anthropic 的 event: 行不携带数据
        if (trimmed.startsWith("event:") || trimmed.startsWith(":")) {
            return;
        }
        if (!trimmed.startsWith("data:") && !trimmed.startsWith("{")) {
            return;
        }
        StreamEvent event;
        try {
            event = adapter.convertStreamEvent(trimmed, provider);
        } catch (RuntimeException e) {
            // 中转站可能返回非标准 JSON，记录并跳过而非中断整条流
            LOG.warn("忽略无法解析的流式事件: {}", trimmed, e);
            return;
        }
        if (event == null) {
            return;
        }
        if (event.type() == StreamEventType.TOOL_CALL_DELTA && event.toolCallDelta() != null) {
            StringBuilder buffer = toolArguments.computeIfAbsent(
                    event.toolCallDelta().index(), k -> new StringBuilder());
            if (event.toolCallDelta().argumentsDelta() != null) {
                buffer.append(event.toolCallDelta().argumentsDelta());
            }
        }
        listener.onEvent(event);
    }

    private ProviderConfig resolveProvider(ChatRequest request) {
        if (llmConfig == null || llmConfig.providers() == null || llmConfig.providers().isEmpty()) {
            throw new LlmException("NO_PROVIDER", "未配置任何 LLM 供应商");
        }
        String key = llmConfig.defaultProvider();
        if (request.extensions() != null && request.extensions().get(EXT_PROVIDER) != null) {
            key = request.extensions().get(EXT_PROVIDER).toString();
        }
        ProviderConfig provider = key == null ? null : llmConfig.providers().get(key);
        if (provider == null) {
            ProviderConfig fallback = fallbackProvider();
            if (fallback != null) {
                LOG.warn("供应商 {} 不可用，降级回退到兜底供应商 {}", key, llmConfig.fallbackProvider());
                return withResolvedSecret(fallback);
            }
            throw new LlmException("PROVIDER_NOT_FOUND", "未找到供应商配置: " + key);
        }
        return withResolvedSecret(provider);
    }

    /**
     * 解析供应商凭据中的占位符。
     *
     * <p>配置加载发生在密钥库就绪之前（{@code Security} 段自身是明文），
     * 因此 {@code ApiKey} 在此时可能仍是 {@code ${AHA_API_KEY_XXX}}。
     * 在真正构造请求前解析，才能完成「环境变量 → 密钥库」的回退。</p>
     *
     * @param provider 原始供应商配置
     * @return 已解析凭据的配置；无需变更时返回原对象
     */
    private static ProviderConfig withResolvedSecret(ProviderConfig provider) {
        if (provider == null || provider.apiKey() == null || provider.apiKey().indexOf("${") < 0) {
            return provider;
        }
        String resolved = ConfigLoader.resolveSecrets(provider.apiKey());
        if (resolved.equals(provider.apiKey())) {
            return provider;
        }
        return new ProviderConfig(provider.adapter(), provider.baseUrl(), resolved, provider.model(),
                provider.timeoutSeconds(), provider.maxRetries(), provider.rateLimit(), provider.extra());
    }

    /**
     * 解析兜底（降级回退）供应商。
     *
     * @return 兜底供应商配置，未配置时为 {@code null}
     */
    private ProviderConfig fallbackProvider() {
        String fallbackKey = llmConfig.fallbackProvider();
        return fallbackKey == null ? null : llmConfig.providers().get(fallbackKey);
    }

    private LlmProviderAdapter resolveAdapter(ProviderConfig provider) {
        String adapterId = provider.adapter();
        return registry.find(adapterId)
                .orElseThrow(() -> new LlmException("ADAPTER_NOT_FOUND",
                        "未找到适配器: " + adapterId));
    }

    private String streamUrl(LlmProviderAdapter adapter, ProviderConfig provider) {
        String url = adapter.buildUrl(provider);
        // Gemini 流式端点与生成端点不同
        if ("gemini".equals(adapter.providerId())) {
            return url.replace(":generateContent", ":streamGenerateContent?alt=sse");
        }
        return url;
    }

    private HttpRequest newRequest(String url, Map<String, String> headers, String body,
                                   ProviderConfig provider) {
        int timeout = provider.timeoutSeconds() > 0 ? provider.timeoutSeconds() : 120;
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(timeout))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        headers.forEach(builder::header);
        return builder.build();
    }

    private static ChatRequest withStream(ChatRequest request, boolean stream) {
        return new ChatRequest(request.model(), request.messages(), request.tools(),
                request.temperature(), request.maxTokens(), stream, request.extensions());
    }

    private RateLimiter limiter(ProviderConfig provider) {
        int rpm = provider.rateLimit() == null ? 0 : provider.rateLimit().rpm();
        return limiters.computeIfAbsent(provider.adapter() + "@" + rpm, k -> new RateLimiter(rpm));
    }

    private static void backoff(int attempt) {
        try {
            long millis = (long) Math.min(8000, Math.pow(2, attempt) * 500);
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM_INTERRUPTED", "退避等待被中断", e);
        }
    }

    /**
     * 简单每分钟令牌限流器。
     *
     * @since 0.1.0
     */
    private static final class RateLimiter {

        private final int rpm;
        private final long intervalNanos;
        private long nextAllowedNanos;

        RateLimiter(int rpm) {
            this.rpm = rpm;
            this.intervalNanos = rpm > 0 ? TimeUnit.MINUTES.toNanos(1) / rpm : 0L;
        }

        synchronized void acquire() throws InterruptedException {
            if (rpm <= 0) {
                return;
            }
            long now = System.nanoTime();
            if (now < nextAllowedNanos) {
                long wait = nextAllowedNanos - now;
                TimeUnit.NANOSECONDS.sleep(wait);
                now = System.nanoTime();
            }
            nextAllowedNanos = Math.max(now, nextAllowedNanos) + intervalNanos;
        }
    }
}
