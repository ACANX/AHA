package com.acanx.module.aha.common.model;

import java.util.Map;

/**
 * 会话配置。
 *
 * @param model        会话级显式模型名（优先级最高；{@code /model <模型名>} 写入）
 * @param tier         会话级显式档位名（{@code /model <档位>} 写入，优先级低于 {@code model}）
 * @param systemPrompt 系统提示词
 * @param extras       附加配置
 * @since 0.1.0
 */
public record SessionConfig(String model, String tier, String systemPrompt,
                            Map<String, Object> extras) {

    /**
     * 兼容构造：不含会话级档位。
     *
     * @param model        模型名
     * @param systemPrompt 系统提示词
     * @param extras       附加配置
     */
    public SessionConfig(String model, String systemPrompt, Map<String, Object> extras) {
        this(model, null, systemPrompt, extras);
    }
}
