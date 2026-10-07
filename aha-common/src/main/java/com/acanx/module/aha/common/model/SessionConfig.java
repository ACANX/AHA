package com.acanx.module.aha.common.model;

import java.util.Map;

/**
 * 会话配置。
 *
 * @param model        模型名
 * @param systemPrompt 系统提示词
 * @param extras       附加配置
 * @since 0.1.0
 */
public record SessionConfig(String model, String systemPrompt, Map<String, Object> extras) {
}
