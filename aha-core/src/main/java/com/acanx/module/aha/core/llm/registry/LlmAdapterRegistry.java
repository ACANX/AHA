package com.acanx.module.aha.core.llm.registry;

import com.acanx.module.aha.core.llm.LlmProviderAdapter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * LLM 适配器注册表，基于 {@link ServiceLoader} 发现内置与第三方适配器。
 *
 * @since 0.1.0
 */
public final class LlmAdapterRegistry {

    private final Map<String, LlmProviderAdapter> adapters = new LinkedHashMap<>();

    /**
     * 构造并加载全部适配器。
     */
    public LlmAdapterRegistry() {
        ServiceLoader.load(LlmProviderAdapter.class)
                .forEach(adapter -> adapters.put(adapter.apiType(), adapter));
    }

    /**
     * 注册适配器。
     *
     * @param adapter 适配器
     */
    public void register(LlmProviderAdapter adapter) {
        adapters.put(adapter.apiType(), adapter);
    }

    /**
     * 按 API 类型（与配置 {@code ApiType} 取值一致）查找适配器。
     *
     * @param apiType API 类型 ID
     * @return 适配器
     */
    public Optional<LlmProviderAdapter> find(String apiType) {
        return Optional.ofNullable(adapters.get(apiType));
    }

    /**
     * 全部 API 类型。
     *
     * @return API 类型集合
     */
    public Set<String> apiTypes() {
        return Collections.unmodifiableSet(adapters.keySet());
    }

    /**
     * 全部供应商 ID。
     *
     * <p>名字与语义不符：集合里装的是适配器 / API 类型（{@code openai-compatible} 等）。</p>
     *
     * @return API 类型集合
     * @deprecated 改用 {@link #apiTypes()}（issue #361）
     */
    @Deprecated
    public Set<String> providerIds() {
        return apiTypes();
    }
}
