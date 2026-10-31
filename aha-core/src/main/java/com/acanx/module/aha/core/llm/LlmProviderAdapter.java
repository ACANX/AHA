package com.acanx.module.aha.core.llm;

import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.llm.protocol.ChatRequest;
import com.acanx.module.aha.core.llm.protocol.ChatResponse;
import com.acanx.module.aha.core.llm.protocol.StreamEvent;

import java.util.Map;

/**
 * LLM 供应商适配器 SPI：IR 与外部协议之间的唯一桥梁。
 *
 * @since 0.1.0
 */
public interface LlmProviderAdapter {

    /**
     * 适配器 / API 类型 ID（与配置 {@code Model.Providers.<Id>.ApiType} 的取值一致）。
     *
     * <p>默认委托 {@link #providerId()}，已有实现无需改动即可同时提供新旧两个名字；
     * 新代码一律用它（issue #361）。</p>
     *
     * @return API 类型 ID（如 {@code openai-compatible} / {@code anthropic-messages} / {@code gemini}）
     * @since 0.1.5
     */
    @SuppressWarnings("deprecation")
    default String apiType() {
        return providerId();
    }

    /**
     * 供应商 ID。
     *
     * <p>名字与语义不符：它其实是**适配器 / API 类型**，不是 {@code Providers.<Id>} 里的供应商键。</p>
     *
     * @return 供应商 ID
     * @deprecated 改用 {@link #apiType()}；本方法至少保留一个 minor 版本（issue #361）
     */
    @Deprecated
    String providerId();

    /**
     * 支持的 IR 版本。
     *
     * @return IR 版本
     */
    String supportedIrVersion();

    /**
     * 将 IR 请求转换为供应商原始请求体。
     *
     * @param irRequest IR 请求
     * @param config    供应商配置
     * @return 原始请求体（JSON 字符串）
     */
    String convertRequest(ChatRequest irRequest, ProviderConfig config);

    /**
     * 将供应商原始响应转换为 IR 响应。
     *
     * @param rawResponse 原始响应
     * @param config      供应商配置
     * @return IR 响应
     */
    ChatResponse convertResponse(String rawResponse, ProviderConfig config);

    /**
     * 将供应商流式事件转换为 IR 流式事件。
     *
     * @param rawEvent 原始事件
     * @param config   供应商配置
     * @return IR 流式事件
     */
    StreamEvent convertStreamEvent(String rawEvent, ProviderConfig config);

    /**
     * 构建请求 URL。
     *
     * @param config 供应商配置
     * @return URL
     */
    String buildUrl(ProviderConfig config);

    /**
     * 构建请求头。
     *
     * @param config 供应商配置
     * @return 请求头
     */
    Map<String, String> buildHeaders(ProviderConfig config);
}
