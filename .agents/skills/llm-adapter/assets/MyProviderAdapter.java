package com.acanx.module.aha.core.llm.myprovider;

import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.llm.LlmProviderAdapter;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;
import com.acanx.module.aha.core.llm.protocol.IrVersion;
import com.acanx.module.aha.core.llm.protocol.StreamEvent;

import java.util.Map;

/**
 * 供应商适配器模板。
 *
 * <p>放置位置：{@code aha-core/src/main/java/com/acanx/module/aha/core/llm/<provider>/}。
 * 复制后重命名类与包名，并把 {@link #PROVIDER_ID} 改为实际供应商 ID。</p>
 *
 * <p>完成后还需：</p>
 * <ol>
 *   <li>在 {@code aha-core/src/main/java/module-info.java} 的 {@code provides} 中追加本类</li>
 *   <li>在 {@code aha-core/src/main/resources/META-INF/services/}
 *       {@code com.acanx.module.aha.core.llm.LlmProviderAdapter} 中追加全限定类名</li>
 *   <li>在 {@code aha-core/src/main/resources/ModelDefault.yml} 中增加供应商预设</li>
 * </ol>
 *
 * <p>实现参考：{@code OpenAiAdapter}（OpenAI 兼容）、{@code AnthropicAdapter}、
 * {@code GeminiAdapter}。</p>
 *
 * @since 0.1.0
 */
public final class MyProviderAdapter implements LlmProviderAdapter {

    /** 供应商 ID，需与 {@code ModelDefault.yml} 中的键一致。 */
    public static final String PROVIDER_ID = "my-provider";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public String supportedIrVersion() {
        return IrVersion.CURRENT;
    }

    /**
     * IR 请求 → 供应商原始请求体（JSON 字符串）。
     *
     * <p>注意：{@code model} 优先取 IR 中的值，为空时回退到 {@code config.model()}。</p>
     */
    @Override
    public String convertRequest(ChatRequest ir, ProviderConfig config) {
        // TODO 用 ObjectMapper 构造供应商请求体
        throw new UnsupportedOperationException("convertRequest 未实现");
    }

    /**
     * 供应商原始响应 → IR 响应。
     *
     * <p>非流式路径：解析 choices、usage、tool_calls。</p>
     */
    @Override
    public ChatResponse convertResponse(String rawResponse, ProviderConfig config) {
        // TODO 解析为 ChatResponse
        throw new UnsupportedOperationException("convertResponse 未实现");
    }

    /**
     * 供应商流式事件 → IR 流式事件。
     *
     * <p>需处理：分片不完整、多字节字符跨片、终止标记（如 {@code [DONE]}）、
     * 空事件与心跳。无法映射的事件应返回可忽略的事件而非抛异常。</p>
     */
    @Override
    public StreamEvent convertStreamEvent(String rawEvent, ProviderConfig config) {
        // TODO 解析为 StreamEvent
        throw new UnsupportedOperationException("convertStreamEvent 未实现");
    }

    /**
     * 请求 URL。通常为 {@code config.baseUrl()} 与供应商路径后缀的拼接。
     */
    @Override
    public String buildUrl(ProviderConfig config) {
        // TODO 拼接 baseUrl 与路径
        throw new UnsupportedOperationException("buildUrl 未实现");
    }

    /**
     * 请求头。鉴权头从 {@code config.apiKey()} 构造；
     * 不要把 Key 写入日志或异常信息。
     */
    @Override
    public Map<String, String> buildHeaders(ProviderConfig config) {
        // TODO 构造鉴权与内容类型头
        throw new UnsupportedOperationException("buildHeaders 未实现");
    }
}
