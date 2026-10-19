package com.acanx.module.aha.core.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模型定义（{@code Model.yml} 中 {@code Models.<id>} 的值）。
 *
 * <p>模型 ID（配置键）是配置内唯一的引用名，供 {@code Tier} 节点引用；
 * {@link #name()} 才是实际下发给供应商的模型名。两者可以不同，
 * 便于同一模型在多个档位复用或对接中转站别名。</p>
 *
 * @param name            实际请求模型名（必填）
 * @param reasoning       是否支持推理 / 思考
 * @param input           输入模态（{@code text} / {@code image} / {@code audio}），为空时按 {@code text}
 * @param contextWindow   上下文窗口（token），未知时为 {@code null}
 * @param maxTokens       最大输出 token，未知时为 {@code null}
 * @param thinkingLevelMap 思考强度 → 供应商参数值（如 {@code Max: "max"}）
 * @since 0.1.5
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ModelDefinition(
        @JsonProperty("Name") String name,
        @JsonProperty("Reasoning") boolean reasoning,
        @JsonProperty("Input") List<String> input,
        @JsonProperty("ContextWindow") Integer contextWindow,
        @JsonProperty("MaxTokens") Integer maxTokens,
        @JsonProperty("ThinkingLevelMap") Map<String, String> thinkingLevelMap
) {

    /** 默认输入模态。 */
    public static final String INPUT_TEXT = "text";
    /** 图片输入模态。 */
    public static final String INPUT_IMAGE = "image";

    /**
     * 仅含实际模型名的定义（旧配置迁移用）。
     *
     * @param name 实际模型名
     * @return 模型定义
     */
    public static ModelDefinition ofName(String name) {
        return new ModelDefinition(name, false, null, null, null, null);
    }

    /**
     * 输入模态副本；未配置时返回 {@code text}。
     *
     * @return 输入模态
     */
    public List<String> inputOrText() {
        if (input == null || input.isEmpty()) {
            return List.of(INPUT_TEXT);
        }
        return new ArrayList<>(input);
    }

    /**
     * 是否支持文本输入。
     *
     * @return 支持返回 {@code true}
     */
    public boolean supportsText() {
        return inputOrText().stream().anyMatch(INPUT_TEXT::equalsIgnoreCase);
    }

    /**
     * 是否支持图片输入。
     *
     * @return 支持返回 {@code true}
     */
    public boolean supportsImage() {
        return inputOrText().stream().anyMatch(INPUT_IMAGE::equalsIgnoreCase);
    }

    /**
     * 思考强度映射副本。
     *
     * @return 映射；未配置时为空表
     */
    public Map<String, String> thinkingLevelMapOrEmpty() {
        return thinkingLevelMap == null ? Map.of() : new LinkedHashMap<>(thinkingLevelMap);
    }

    /**
     * 取某个思考强度对应的供应商参数值。
     *
     * @param level 强度名（如 {@code Max} / {@code High}）
     * @return 参数值；未映射时为空
     */
    public java.util.Optional<String> thinkingValue(String level) {
        if (level == null) {
            return java.util.Optional.empty();
        }
        String value = thinkingLevelMapOrEmpty().get(level);
        return value == null || value.isBlank()
                ? java.util.Optional.empty()
                : java.util.Optional.of(value);
    }

    /**
     * 是否配置了思考强度映射。
     *
     * @return 配置了返回 {@code true}
     */
    public boolean hasThinkingLevels() {
        return !thinkingLevelMapOrEmpty().isEmpty();
    }
}
