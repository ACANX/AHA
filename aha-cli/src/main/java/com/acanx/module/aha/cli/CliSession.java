package com.acanx.module.aha.cli;

import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.AgentConfig;
import com.acanx.module.aha.core.config.ModelTier;
import com.acanx.module.aha.core.config.SystemPromptLoader;
import com.acanx.module.aha.core.service.AgentService;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * CLI 会话开启辅助：解析系统提示词并创建会话。
 *
 * <p>系统提示词（Agent 身份）在<b>会话创建时</b>解析并固化到会话记录中，
 * 同一会话内的后续轮次直接复用，不会重复读取文件或重复解析。</p>
 *
 * @since 0.1.0
 */
final class CliSession {

    private CliSession() {
    }

    /**
     * 已开启的会话。
     *
     * @param sessionId    会话 ID
     * @param promptSource 系统提示词来源
     * @param promptChars  系统提示词字符数
     */
    record Opened(String sessionId, String promptSource, int promptChars) {
    }

    /**
     * 创建会话（或复用已有会话）并固化系统提示词。
     *
     * <p>显式传入 {@code sessionId} 时视为复用已有会话：该会话的身份设定已在首次创建时
     * 固化，此处不再覆盖。</p>
     *
     * @param service      Agent 服务
     * @param sessionId    复用会话 ID，可为 {@code null}
     * @param model        模型名，可为 {@code null}
     * @param systemText   显式提示词文本，可为 {@code null}
     * @param systemFile   显式提示词文件，可为 {@code null}
     * @return 会话信息
     */
    static Opened open(AgentService service, String sessionId, String model,
                       String systemText, Path systemFile) {
        if (sessionId != null && !sessionId.isBlank()) {
            return new Opened(sessionId, "existing-session", 0);
        }

        AhaConfig config = CliContext.config();
        AgentConfig agent = config == null ? null : config.agent();
        List<String> promptFiles = agent == null ? null : agent.promptFiles();
        String inlinePrompt = agent == null ? null : agent.systemPrompt();

        Path workingDir = Path.of(System.getProperty("user.dir", "."));
        SystemPromptLoader.ResolvedPrompt resolved = SystemPromptLoader.resolve(
                systemText, systemFile, workingDir, promptFiles, inlinePrompt);

        // 身份片段固化到会话配置：请求拼装时按片段逐条发送 system 消息，
        // 而不是拼成一整段（拼接会让模型看不出内容来自哪个文件）
        Map<String, Object> extras = new java.util.LinkedHashMap<>();
        extras.put(SystemPromptLoader.SEGMENTS_KEY, resolved.segments());
        // --model 可传档位名（大小写敏感）或具体模型名：档位写 tier，由 LLM 客户端按配置解析
        String modelName = model;
        String tier = null;
        if (model != null && ModelTier.isTierName(model)) {
            tier = model;
            modelName = null;
        }
        String created = service.createSession(
                new SessionConfig(modelName, tier, resolved.text(), extras));
        return new Opened(created, resolved.source(),
                resolved.text() == null ? 0 : resolved.text().length());
    }
}
