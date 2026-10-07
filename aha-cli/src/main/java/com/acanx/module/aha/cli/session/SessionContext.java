package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.cli.tty.TerminalHandover;
import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.service.AgentService;
import com.acanx.module.aha.core.service.LocalAgentService;

import java.io.PrintStream;

/**
 * 会话内命令的执行上下文。
 *
 * <p>由 {@code ChatCommand} 构造并传入，使命令实现不必依赖 CLI 的包私有状态
 * （{@code CliContext} / {@code CliSession}），同时也便于测试注入内存输出流。</p>
 *
 * @param sessionId    会话 ID
 * @param service      Agent 服务
 * @param config       主配置，可为 {@code null}
 * @param out          输出流
 * @param promptSource 系统提示词来源描述，可为 {@code null}
 * @param promptChars  系统提示词字符数（沿用已有会话时为 0）
 * @param handover     终端让位器，供唤起外部编辑器等场景使用
 * @since 0.1.0
 */
public record SessionContext(
        String sessionId,
        AgentService service,
        AhaConfig config,
        PrintStream out,
        String promptSource,
        int promptChars,
        TerminalHandover handover) {

    /**
     * 不让出终端的构造方式。
     *
     * <p>测试与非交互场景（{@code aha run}）无需终端让位，用这个重载免得处处传参。</p>
     *
     * @param sessionId    会话 ID
     * @param service      Agent 服务
     * @param config       主配置
     * @param out          输出流
     * @param promptSource 系统提示词来源描述
     * @param promptChars  系统提示词字符数
     */
    public SessionContext(String sessionId, AgentService service, AhaConfig config,
                          PrintStream out, String promptSource, int promptChars) {
        this(sessionId, service, config, out, promptSource, promptChars, TerminalHandover.DIRECT);
    }

    /**
     * 本地服务实例。
     *
     * <p>记忆存储、工具注册表与会话记录只有本地运行时可直接读取；
     * 远程形态下这些访问器返回 {@code null}，命令应相应降级。</p>
     *
     * @return 本地服务；非本地运行时返回 {@code null}
     */
    public LocalAgentService local() {
        return service instanceof LocalAgentService local ? local : null;
    }

    /**
     * 当前会话固化的配置。
     *
     * @return 会话配置；非本地运行时或会话不存在时返回 {@code null}
     */
    public SessionConfig sessionConfig() {
        LocalAgentService local = local();
        return local == null ? null : local.sessionManager().get(sessionId).orElse(null);
    }
}
