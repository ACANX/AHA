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
     * 供应商 ID。
     *
     * @return 供应商 ID
     */
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
