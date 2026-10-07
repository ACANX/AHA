/**
 * AHA Core 模块：Agent 推理引擎、LLM 适配体系、记忆存储、配置加载、
 * 安全存储、MCP 适配器接口、扩展运行时。
 */
module com.acanx.module.aha.core {
    requires transitive com.acanx.module.aha.common;
    requires transitive com.acanx.module.aha.extension.api;

    requires org.xerial.sqlitejdbc;
    requires tools.jackson.databind;
    requires tools.jackson.dataformat.yaml;
    requires com.fasterxml.jackson.annotation;
    requires org.slf4j;
    requires java.net.http;
    requires java.sql;

    exports com.acanx.module.aha.core.agent;
    exports com.acanx.module.aha.core.service;
    exports com.acanx.module.aha.core.runtime;
    exports com.acanx.module.aha.core.task;
    exports com.acanx.module.aha.core.memory;
    exports com.acanx.module.aha.core.config;
    exports com.acanx.module.aha.core.security;
    exports com.acanx.module.aha.core.mcp;
    exports com.acanx.module.aha.core.extension;
    exports com.acanx.module.aha.core.extension.event;
    exports com.acanx.module.aha.core.llm;
    exports com.acanx.module.aha.core.llm.protocol;

    uses com.acanx.module.aha.common.tool.ToolProvider;
    uses com.acanx.module.aha.core.llm.LlmProviderAdapter;
    uses com.acanx.module.aha.core.service.AgentServiceProvider;
    uses com.acanx.module.aha.extension.api.AhaExtension;

    provides com.acanx.module.aha.core.llm.LlmProviderAdapter
            with com.acanx.module.aha.core.llm.openai.OpenAiAdapter,
                 com.acanx.module.aha.core.llm.anthropic.AnthropicAdapter,
                 com.acanx.module.aha.core.llm.gemini.GeminiAdapter;

    opens com.acanx.module.aha.core.config to tools.jackson.databind;
    opens com.acanx.module.aha.core.llm.protocol to tools.jackson.databind;
    opens com.acanx.module.aha.core.task to tools.jackson.databind;
}
