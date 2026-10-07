package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * AHA 根配置。
 *
 * @param llm      LLM 配置
 * @param memory   记忆配置
 * @param tools    工具配置
 * @param security 安全配置
 * @param logging  日志配置
 * @param extensions 扩展配置
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AhaConfig(
        @JsonProperty("Llm") LlmConfig llm,
        @JsonProperty("Memory") MemoryConfig memory,
        @JsonProperty("Tools") ToolsConfig tools,
        @JsonProperty("Security") SecurityConfig security,
        @JsonProperty("Logging") LoggingConfig logging,
        @JsonProperty("Extension") ExtensionsConfig extensions,
        @JsonProperty("Agent") AgentConfig agent
) {

    /**
     * 向后兼容构造器：不带 {@code Agent} 段。
     *
     * <p>保留旧调用方式，避免为新增可选配置段改动全部调用点。</p>
     *
     * @param llm      LLM 配置
     * @param memory   记忆配置
     * @param tools    工具配置
     * @param security 安全配置
     * @param logging  日志配置
     * @param extensions 扩展配置
     */
    public AhaConfig(LlmConfig llm, MemoryConfig memory, ToolsConfig tools,
                     SecurityConfig security, LoggingConfig logging, ExtensionsConfig extensions) {
        this(llm, memory, tools, security, logging, extensions, null);
    }
}
