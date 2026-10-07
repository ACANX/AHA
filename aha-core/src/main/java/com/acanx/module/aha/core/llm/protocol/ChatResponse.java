package com.acanx.module.aha.core.llm.protocol;

import java.util.List;
import java.util.Map;

/**
 * IR 聊天响应。
 *
 * @param id         响应 ID
 * @param model      模型
 * @param choices    选项列表
 * @param usage      用量
 * @param extensions 供应商扩展
 * @since 0.1.0
 */
public record ChatResponse(
        String id,
        String model,
        List<Choice> choices,
        Usage usage,
        Map<String, Object> extensions
) {
}
