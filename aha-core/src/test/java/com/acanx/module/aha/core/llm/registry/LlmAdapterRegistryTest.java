package com.acanx.module.aha.core.llm.registry;

import com.acanx.module.aha.core.llm.LlmProviderAdapter;
import com.acanx.module.aha.core.llm.anthropic.AnthropicAdapter;
import com.acanx.module.aha.core.llm.gemini.GeminiAdapter;
import com.acanx.module.aha.core.llm.openai.OpenAiAdapter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LlmAdapterRegistry} 与 SPI 命名迁移测试（issue #361）。
 *
 * <p>阶段 1 的约束：新名（{@code apiType()} / {@code apiTypes()} / {@code API_TYPE}）
 * 与旧名完全等价，旧名只是 deprecated 委托——两者不一致就说明迁移改错了。</p>
 *
 * @since 0.1.5
 */
@SuppressWarnings("deprecation")
class LlmAdapterRegistryTest {

    @Test
    void builtInAdaptersExposeApiTypeAndLegacyNameConsistently() {
        for (LlmProviderAdapter adapter : List.of(
                new OpenAiAdapter(), new AnthropicAdapter(), new GeminiAdapter())) {
            assertThat(adapter.apiType())
                    .as("%s 的 apiType() 必须与 deprecated providerId() 一致", adapter.getClass().getName())
                    .isEqualTo(adapter.providerId());
        }
    }

    @Test
    void registryKeysAreApiTypes() {
        LlmAdapterRegistry registry = new LlmAdapterRegistry();

        assertThat(registry.apiTypes())
                .containsExactlyInAnyOrder(OpenAiAdapter.API_TYPE,
                        AnthropicAdapter.API_TYPE, GeminiAdapter.API_TYPE);
        assertThat(registry.find(OpenAiAdapter.API_TYPE)).isPresent();
        assertThat(registry.find(AnthropicAdapter.API_TYPE)).isPresent();
        assertThat(registry.find(GeminiAdapter.API_TYPE)).isPresent();
    }

    @Test
    void legacyProviderIdsDelegatesToApiTypes() {
        LlmAdapterRegistry registry = new LlmAdapterRegistry();

        assertThat(registry.providerIds()).isEqualTo(registry.apiTypes());
    }

    @Test
    void registerUsesApiTypeAsKey() {
        LlmAdapterRegistry registry = new LlmAdapterRegistry();
        LlmProviderAdapter openai = new OpenAiAdapter();

        registry.register(openai);

        assertThat(registry.find(OpenAiAdapter.API_TYPE).orElseThrow()).isSameAs(openai);
    }
}
