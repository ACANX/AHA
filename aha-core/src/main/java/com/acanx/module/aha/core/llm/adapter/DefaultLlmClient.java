package com.acanx.module.aha.core.llm.adapter;

import com.acanx.module.aha.common.exception.LlmException;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.core.config.ConfigLoader;
import com.acanx.module.aha.core.config.LlmConfig;
import com.acanx.module.aha.core.config.ModelTier;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 默认 LLM 客户端实现。
 *
 * <p>职责：选择适配器、构建并发送 HTTP 请求、指数退避重试、连接/读取超时、
 * 令牌桶限流、SSE 事件解析、取消传播，以及<b>档位降级重试链</b>（§3.1）。</p>
 *
 * <p>降级链单向、有终态、无循环：</p>
 * <ol>
 *   <li>同一模型内先按 {@code MaxRetries} 指数退避重试；</li>
 *   <li>失败后先降 {@link ModelTier#STANDARD}（当前已是则跳过）；</li>
 *   <li>再降 {@link ModelTier#FALLBACK} 档（已配置时）；</li>
 *   <li>仍失败走全局 {@code Llm.Fallback}（跨供应商）；</li>
 *   <li>最终抛<b>原始错误</b>，不被降级过程掩盖。</li>
 * </ol>
 *
 * <p>401/403 与欠费类错误不触发档位降级（换档无用），直接走全局兜底 / 报错；
 * 流式在首个事件到达后中断不自动重放，避免用户看到重复内容。</p>
 *
 * @since 0.1.0
 */
public final class DefaultLlmClient implements LlmClient, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultLlmClient.class);

    private static final String EXT_PROVIDER = "Provider";
    private static final String EXT_TIER = "Tier";

    private final LlmAdapterRegistry registry;
    private final LlmConfig llmConfig;
    private final HttpClient httpClient;
    private final ExecutorService executor;
    private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

    /**
     * 降级监听器：在档位 / 供应商发生降级时回调，供 CLI / 桌面端给出用户可见提示。
     *
     * @since 0.1.0
     */
    @FunctionalInterface
    public interface DegradationListener {

        /**
         * 降级发生。
         *
         * @param fromTier  起始档位名
         * @param fromModel 起始模型
         * @param toTier    目标档位名
         * @param toModel   目标模型
         * @param reason    原因
         */
        void onDegraded(String fromTier, String fromModel, String toTier, String toModel, String reason);
    }

    private volatile DegradationListener degradationListener;

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

    /**
     * 设置降级监听器。
     *
     * @param listener 监听器；{@code null} 时清除
     */
    public void setDegradationListener(DegradationListener listener) {
        this.degradationListener = listener;
    }

    @Override
    public CompletableFuture<ChatResponse> chat(ChatRequest request) {
        return CompletableFuture.supplyAsync(() -> chatSync(request), executor);
    }

    @Override
    public void streamChat(ChatRequest request, StreamEventListener listener, CancellationToken token) {
        if (llmConfig == null || llmConfig.providers() == null || llmConfig.providers().isEmpty()) {
            throw new LlmException("NO_PROVIDER", "未配置任何 LLM 供应商");
        }
        String providerKey = providerKey(request);
        ProviderConfig provider = providerKey == null ? null : llmConfig.providers().get(providerKey);
        if (provider == null) {
            streamGlobalFallback(providerKey, request, listener, token, null);
            return;
        }
        provider = withResolvedSecret(provider);
        String globalTier = llmConfig.defaultTier();
        ModelTier tier = resolveTier(request, provider, globalTier);
        String model = resolveModel(request, provider, tier, globalTier);

        LlmException original = null;
        boolean[] emitted = {false};
        for (Attempt attempt : candidates(provider, tier, model)) {
            try {
                streamAttempt(request, provider, attempt, listener, token, emitted);
                notifyDegraded(tier, model, attempt, null);
                return;
            } catch (LlmException e) {
                if (original == null) {
                    original = e;
                }
                if (emitted[0]) {
                    // 首个事件已到达：不自动重放，避免重复内容
                    throw e;
                }
                if (!isDegradable(e)) {
                    break;
                }
                LOG.warn("档位 {} 模型 {} 不可用（{}），尝试下一档 {}", attempt.tier().configName(),
                        attempt.model(), rootMessage(e), "降级");
            }
        }
        streamGlobalFallback(providerKey, request, listener, token, original);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    // ------------------------------------------------------------------

    private ChatResponse chatSync(ChatRequest request) {
        if (llmConfig == null || llmConfig.providers() == null || llmConfig.providers().isEmpty()) {
            throw new LlmException("NO_PROVIDER", "未配置任何 LLM 供应商");
        }
        String providerKey = providerKey(request);
        ProviderConfig provider = providerKey == null ? null : llmConfig.providers().get(providerKey);
        if (provider == null) {
            return chatGlobalFallback(providerKey, request, null);
        }
        provider = withResolvedSecret(provider);
        String globalTier = llmConfig.defaultTier();
        ModelTier tier = resolveTier(request, provider, globalTier);
        String model = resolveModel(request, provider, tier, globalTier);

        LlmException original = null;
        for (Attempt attempt : candidates(provider, tier, model)) {
            try {
                ChatResponse response = chatAttempt(request, provider, attempt.model());
                notifyDegraded(tier, model, attempt, null);
                return response;
            } catch (LlmException e) {
                if (original == null) {
                    original = e;
                }
                if (!isDegradable(e)) {
                    break;
                }
                LOG.warn("档位 <{}> 模型 <{}> 不可用（{}），尝试下一档 {}", attempt.tier().configName(),
                        attempt.model(), rootMessage(e), "降级");
            }
        }
        return chatGlobalFallback(providerKey, request, original);
    }

    /**
     * 单次（含同模型重试）非流式调用。
     *
     * @param request  请求
     * @param provider 供应商
     * @param model    本次模型
     * @return 响应
     */
    private ChatResponse chatAttempt(ChatRequest request, ProviderConfig provider, String model) {
        LlmProviderAdapter adapter = resolveAdapter(provider);
        String url = adapter.buildUrl(provider);
        String body = adapter.convertRequest(withModel(withStream(request, false), model), provider);

        int maxRetries = provider.maxRetries() > 0 ? provider.maxRetries() : 3;
        LlmException last = null;
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                limiter(provider).acquire();
                HttpRequest httpRequest = newRequest(url, adapter.buildHeaders(provider), body, provider);
                LOG.debug("LLM chat -> {} ({}) model={} attempt={}", url, adapter.providerId(),
                        model, attempt);
                HttpResponse<String> response =
                        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (isImmediateModelFailure(status, response.body())) {
                    // 模型不存在 / 参数不接受：立即降级，不再同模型重试
                    throw new LlmException("LLM_HTTP_" + status,
                            "请求失败: HTTP " + status + formatDetail(response.body()));
                }
                if ((status >= 500 || status == 429) && attempt < maxRetries) {
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

    /**
     * 单次（含同模型重试）流式调用。
     *
     * @param request  请求
     * @param provider 供应商
     * @param attempt  本次档位与模型
     * @param listener 监听器
     * @param token    取消令牌
     * @param emitted  是否已产出事件（跨尝试共享）
     */
    private void streamAttempt(ChatRequest request, ProviderConfig provider, Attempt attempt,
                               StreamEventListener listener, CancellationToken token, boolean[] emitted) {
        LlmProviderAdapter adapter = resolveAdapter(provider);
        String url = streamUrl(adapter, provider);
        String body = adapter.convertRequest(withModel(withStream(request, true), attempt.model()), provider);

        HttpRequest httpRequest = newRequest(url, adapter.buildHeaders(provider), body, provider);
        StreamEventListener guarded = event -> {
            emitted[0] = true;
            listener.onEvent(event);
        };
        try {
            LOG.debug("LLM stream -> {} ({}) model={}", url, adapter.providerId(), attempt.model());
            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() >= 400) {
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
                    dispatchLine(adapter, provider, line, guarded, toolArguments);
                }
            }
        } catch (IOException e) {
            throw new LlmException("LLM_STREAM_IO", "流式请求 I/O 失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("LLM_STREAM_INTERRUPTED", "流式请求被中断", e);
        }
    }

    /**
     * 全局兜底的跨供应商调用（非流式）。
     *
     * @param providerKey 原供应商标识（日志用）
     * @param request     请求
     * @param original    原始错误，可为 {@code null}
     * @return 响应
     */
    private ChatResponse chatGlobalFallback(String providerKey, ChatRequest request, LlmException original) {
        Attempt fallback = globalFallbackAttempt();
        if (fallback != null) {
            try {
                LOG.warn("供应商 {} 不可用，降级回退到兜底供应商 {}", providerKey, fallback.providerId());
                ChatResponse response = chatAttempt(request, fallback.provider(), fallback.model());
                notifyDegraded(null, null, fallback, null);
                return response;
            } catch (LlmException e) {
                LOG.warn("全局兜底供应商仍失败: {}", rootMessage(e));
            }
        }
        if (original != null) {
            throw original;
        }
        throw new LlmException("PROVIDER_NOT_FOUND", "未找到供应商配置: " + providerKey);
    }

    /**
     * 全局兜底的跨供应商调用（流式）。
     *
     * @param providerKey 原供应商标识
     * @param request     请求
     * @param listener    监听器
     * @param token       取消令牌
     * @param original    原始错误，可为 {@code null}
     */
    private void streamGlobalFallback(String providerKey, ChatRequest request,
                                      StreamEventListener listener, CancellationToken token,
                                      LlmException original) {
        Attempt fallback = globalFallbackAttempt();
        if (fallback != null) {
            boolean[] emitted = {false};
            try {
                LOG.warn("供应商 {} 不可用，降级回退到兜底供应商 {}", providerKey, fallback.providerId());
                streamAttempt(request, fallback.provider(), fallback, listener, token, emitted);
                return;
            } catch (LlmException e) {
                LOG.warn("全局兜底供应商仍失败: {}", rootMessage(e));
                if (original == null) {
                    original = e;
                }
            }
        }
        if (original != null) {
            throw original;
        }
        throw new LlmException("PROVIDER_NOT_FOUND", "未找到供应商配置: " + providerKey);
    }

    private Attempt globalFallbackAttempt() {
        String fallbackKey = llmConfig.fallbackProvider();
        ProviderConfig fallback = fallbackKey == null ? null : llmConfig.providers().get(fallbackKey);
        if (fallback == null) {
            return null;
        }
        fallback = withResolvedSecret(fallback);
        String fallbackModel = llmConfig.fallbackModel() != null
                ? llmConfig.fallbackModel()
                : fallback.effectiveModel(llmConfig.defaultTier());
        return new Attempt(fallbackKey, ModelTier.FALLBACK, fallback, fallbackModel);
    }

    /**
     * 构造降级候选链（去重、单向、不回头）。
     *
     * @param provider 供应商
     * @param tier     当前档位
     * @param model    当前模型
     * @return 候选链
     */
    private static List<Attempt> candidates(ProviderConfig provider, ModelTier tier, String model) {
        List<Attempt> list = new ArrayList<>();
        list.add(new Attempt(provider.adapter(), tier, provider, model));
        // 当前不是 Standard 且不是 Fallback：先降 Standard
        if (tier != ModelTier.STANDARD && tier != ModelTier.FALLBACK) {
            String standard = provider.modelFor(ModelTier.STANDARD);
            if (standard != null && !standard.equals(model)) {
                list.add(new Attempt(provider.adapter(), ModelTier.STANDARD, provider, standard));
            }
        }
        // 再降供应商 Fallback 档
        if (tier != ModelTier.FALLBACK) {
            String fallbackModel = provider.configuredModel(ModelTier.FALLBACK);
            if (fallbackModel != null && !containsModel(list, fallbackModel)) {
                list.add(new Attempt(provider.adapter(), ModelTier.FALLBACK, provider, fallbackModel));
            }
        }
        return list;
    }

    private static boolean containsModel(List<Attempt> attempts, String model) {
        return attempts.stream().anyMatch(attempt -> model.equals(attempt.model()));
    }

    /**
     * 解析供应商级 / 会话级 / 请求级档位。
     *
     * @param request    请求
     * @param provider   供应商
     * @param globalTier 全局默认档
     * @return 生效档位
     */
    private static ModelTier resolveTier(ChatRequest request, ProviderConfig provider, String globalTier) {
        Optional<ModelTier> byModel = ModelTier.fromConfigName(request.model());
        if (byModel.isPresent()) {
            return byModel.get();
        }
        Object extTier = request.extensions() == null ? null : request.extensions().get(EXT_TIER);
        if (extTier != null) {
            Optional<ModelTier> parsed = ModelTier.fromConfigName(extTier.toString());
            if (parsed.isPresent()) {
                return parsed.get();
            }
        }
        Optional<ModelTier> byConfiguredModel = provider.tierOf(request.model());
        if (byConfiguredModel.isPresent()) {
            return byConfiguredModel.get();
        }
        return provider.effectiveTier(globalTier);
    }

    /**
     * 解析本次请求实际使用的模型。
     *
     * @param request    请求
     * @param provider   供应商
     * @param tier       生效档位
     * @param globalTier 全局默认档
     * @return 模型名
     */
    private static String resolveModel(ChatRequest request, ProviderConfig provider, ModelTier tier,
                                       String globalTier) {
        if (request.model() != null && !request.model().isBlank()) {
            Optional<ModelTier> byModel = ModelTier.fromConfigName(request.model());
            if (byModel.isPresent()) {
                String mapped = provider.modelFor(byModel.get());
                return mapped != null ? mapped : request.model();
            }
            return request.model();
        }
        String byTier = provider.modelFor(tier);
        return byTier != null ? byTier : provider.effectiveModel(globalTier);
    }

    private String providerKey(ChatRequest request) {
        Object extProvider = request.extensions() == null ? null : request.extensions().get(EXT_PROVIDER);
        if (extProvider != null) {
            return extProvider.toString();
        }
        return llmConfig.defaultProvider();
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

    /**
     * 是否属于「模型不可用、换模型即可解决」的立即降级错误。
     *
     * @param status HTTP 状态码
     * @param body   响应体
     * @return 立即降级返回 {@code true}
     */
    private static boolean isImmediateModelFailure(int status, String body) {
        if (status == 404 || status == 422 || status == 400) {
            return true;
        }
        if (body == null) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("model not found") || lower.contains("invalid model")
                || lower.contains("unknown model") || lower.contains("does not exist");
    }

    /**
     * 该错误是否允许档位降级（401/403 换档无用，不降级）。
     *
     * @param e 异常
     * @return 可降级返回 {@code true}
     */
    private static boolean isDegradable(LlmException e) {
        String code = e.code();
        return !"LLM_HTTP_401".equals(code) && !"LLM_HTTP_403".equals(code);
    }

    private static String rootMessage(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    private void notifyDegraded(ModelTier fromTier, String fromModel, Attempt attempt, String reason) {
        // 未发生档位变化时不提示
        if (fromTier == attempt.tier() && fromModel != null && fromModel.equals(attempt.model())) {
            return;
        }
        DegradationListener listener = degradationListener;
        if (listener != null) {
            listener.onDegraded(fromTier == null ? "-" : fromTier.configName(),
                    fromModel == null ? "-" : fromModel,
                    attempt.tier().configName(), attempt.model(),
                    reason == null ? "" : reason);
        }
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
        return provider.withApiKey(resolved);
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

    private static ChatRequest withModel(ChatRequest request, String model) {
        if (model == null || model.equals(request.model())) {
            return request;
        }
        return new ChatRequest(model, request.messages(), request.tools(),
                request.temperature(), request.maxTokens(), request.stream(), request.extensions());
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
     * 一次尝试的档位 / 供应商 / 模型。
     *
     * @param providerId 供应商标识
     * @param tier       档位
     * @param provider   供应商配置
     * @param model      模型
     * @since 0.1.0
     */
    private record Attempt(String providerId, ModelTier tier, ProviderConfig provider, String model) {
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
