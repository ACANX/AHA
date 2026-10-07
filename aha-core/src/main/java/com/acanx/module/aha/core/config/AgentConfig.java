package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Agent 身份与系统提示词配置。
 *
 * <p>用于定义 AHA 扮演的角色。提示词既可从约定文件读取（推荐，便于随项目走），
 * 也可在此内联声明。</p>
 *
 * @param promptFiles  提示词文件名列表，按顺序查找取第一个存在的；为空时使用
 *                     {@link SystemPromptLoader#DEFAULT_PROMPT_FILES}
 * @param systemPrompt 内联系统提示词；仅当未找到任何提示词文件时生效
 * @since 0.1.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentConfig(
        @JsonProperty("PromptFiles") List<String> promptFiles,
        @JsonProperty("SystemPrompt") String systemPrompt
) {
}
