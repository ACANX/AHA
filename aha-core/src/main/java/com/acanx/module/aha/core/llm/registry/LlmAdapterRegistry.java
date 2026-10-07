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
                .forEach(adapter -> adapters.put(adapter.providerId(), adapter));
    }

    /**
     * 注册适配器。
     *
     * @param adapter 适配器
     */
    public void register(LlmProviderAdapter adapter) {
        adapters.put(adapter.providerId(), adapter);
    }

    /**
     * 按供应商 ID 查找适配器。
     *
     * @param providerId 供应商 ID
     * @return 适配器
     */
    public Optional<LlmProviderAdapter> find(String providerId) {
        return Optional.ofNullable(adapters.get(providerId));
    }

    /**
     * 全部供应商 ID。
     *
     * @return 供应商 ID 集合
     */
    public Set<String> providerIds() {
        return Collections.unmodifiableSet(adapters.keySet());
    }
}
