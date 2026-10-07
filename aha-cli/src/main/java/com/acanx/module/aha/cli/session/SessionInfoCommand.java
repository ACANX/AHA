package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.config.SystemPromptLoader;
import com.acanx.module.aha.core.memory.ProjectId;
import com.acanx.module.aha.core.service.LocalAgentService;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 会话内命令 {@code /session}：查看会话记录本身——ID、固化的身份与模型、存储位置与续接方式。
 *
 * <p>与 {@code /context} 的分工：{@code /context} 回答「这一轮会带什么上下文发给模型」，
 * {@code /session} 回答「这条会话记录存在哪里、怎么再次续接」。</p>
 *
 * @since 0.1.0
 */
final class SessionInfoCommand implements SessionCommand {

    @Override
    public String name() {
        return "session";
    }

    @Override
    public String usage() {
        return "/session";
    }

    @Override
    public String description() {
        return "查看会话 ID、身份、存储位置与续接方式";
    }

    @Override
    public Outcome execute(SessionContext context, List<String> args) {
        PrintStream out = context.out();
        out.println("会话");
        SessionOutput.field(out, "ID", context.sessionId());

        LocalAgentService local = context.local();
        if (local == null) {
            SessionOutput.field(out, "身份", MemorySessionCommand.describeSource(context.promptSource()));
            SessionOutput.field(out, "续接", "aha chat --session " + context.sessionId());
            return Outcome.CONTINUE;
        }

        Optional<SessionConfig> session = local.sessionManager().get(context.sessionId());
        String model = session.map(SessionConfig::model).orElse(null);
        if (model == null || model.isBlank()) {
            model = context.service().runtime().attributes().get("Model");
        }
        SessionOutput.field(out, "模型", model);

        // 身份在会话创建时固化，因此这里读的是会话记录里的文本，而不是当前文件内容；
        // 来源要读 Extras（/prompt 改过之后就与 SessionContext 里的快照不一致了）
        String prompt = session.map(SessionConfig::systemPrompt).orElse(null);
        SessionOutput.field(out, "身份",
                describeIdentity(MemorySessionCommand.source(context), prompt));

        int stored = local.memoryStore().countMessages(context.sessionId());
        SessionOutput.field(out, "消息", stored + " 条已存储");
        SessionOutput.field(out, "存储", ConfigView.resolve(context.config(), "Memory.Path"));
        // 记忆按项目存放（与会话无关），这里给出实际位置以便核对
        Path projectRoot = SystemPromptLoader.findProjectRoot(
                Path.of(System.getProperty("user.dir", ".")));
        if (projectRoot != null) {
            SessionOutput.field(out, "项目", ProjectId.of(projectRoot));
            SessionOutput.field(out, "记忆",
                    ProjectId.memoryDir(projectRoot) + "（0.6 启用）");
        }
        SessionOutput.field(out, "续接", "aha chat --session " + context.sessionId());
        out.println();
        out.println("提示  该 ID 可在其他终端续接；身份与模型沿用本条会话记录。");
        return Outcome.CONTINUE;
    }

    /**
     * 身份描述。
     *
     * @param source 来源描述，可为 {@code null}
     * @param prompt 会话固化的系统提示词，可为 {@code null}
     * @return 描述文本
     */
    private static String describeIdentity(String source, String prompt) {
        String base = source == null || source.isBlank() ? "未知来源" : source;
        if (prompt == null || prompt.isBlank()) {
            return base;
        }
        return base + "（" + prompt.length() + " 字符）";
    }
}
