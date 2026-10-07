package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.config.LlmConfig;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.service.LocalAgentService;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会话内命令 {@code /model}：查看或切换本会话使用的模型。
 *
 * <p><b>只改模型，不改供应商</b>：会话配置里固化的是模型名，供应商由主配置 / {@code Model.yml}
 * 的 {@code Default} 决定（进程级）。因此切换供应商不属于本命令，而应使用
 * {@code aha provider use <id>}——两者边界在输出里明确提示，避免用户误以为换了供应商。</p>
 *
 * @since 0.1.0
 */
final class ModelSessionCommand implements SessionCommand {

    @Override
    public String name() {
        return "model";
    }

    @Override
    public String usage() {
        return "/model [模型名]";
    }

    @Override
    public String description() {
        return "查看或切换本会话模型（不改供应商）";
    }

    @Override
    public Outcome execute(SessionContext context, List<String> args) {
        PrintStream out = context.out();
        LocalAgentService local = context.local();
        if (local == null) {
            out.println("[error] 当前运行时为远程形态，暂不支持修改会话模型。");
            return Outcome.CONTINUE;
        }

        LlmConfig llm = context.config() == null ? null : context.config().llm();
        String providerId = llm == null ? null : llm.defaultProvider();
        String current = currentModel(context, local, llm, providerId);

        if (args.isEmpty()) {
            describe(out, context, llm, providerId, current);
            return Outcome.CONTINUE;
        }

        String target = args.get(0);
        switchModel(context, local, llm, providerId, current, target);
        return Outcome.CONTINUE;
    }

    private void describe(PrintStream out, SessionContext context, LlmConfig llm,
                         String providerId, String current) {
        out.println("模型");
        SessionOutput.field(out, "当前", model(current, providerId));
        String endpoint = context.service().runtime().attributes().get("Endpoint");
        SessionOutput.field(out, "端点", endpoint);
        out.println();

        Map<String, ProviderConfig> providers =
                llm == null || llm.providers() == null ? Map.of() : llm.providers();
        if (!providers.isEmpty()) {
            out.println("可选供应商（Model.yml / 内置预设）");
            providers.forEach((id, provider) -> out.printf("  %-18s %s%s%n", id,
                    provider.model() == null ? "-" : provider.model(),
                    id.equals(providerId) ? "   ← 当前" : ""));
        }
        out.println();
        out.println("用法  /model <模型名>        切换本会话模型（仅模型，不改供应商）");
        out.println("      aha provider use <id>   切换供应商（进程级，写入 Model.yml）");
    }

    private void switchModel(SessionContext context, LocalAgentService local, LlmConfig llm,
                            String providerId, String current, String target) {
        PrintStream out = context.out();
        if (target.equals(current)) {
            out.println("已是当前模型：" + target);
            return;
        }

        Optional<SessionConfig> session = local.sessionManager().get(context.sessionId());
        if (session.isEmpty()) {
            out.println("[error] 会话不存在：" + context.sessionId());
            return;
        }
        SessionConfig config = session.get();
        SessionConfig updated = new SessionConfig(target, config.systemPrompt(), config.extras());
        if (!local.sessionManager().update(context.sessionId(), updated)) {
            out.println("[error] 更新会话配置失败：" + context.sessionId());
            return;
        }

        out.println("模型已切换：" + (current == null ? "（未设置）" : current) + " → " + target);
        SessionOutput.field(out, "作用", "仅本会话，已写入会话配置");
        SessionOutput.field(out, "供应", (providerId == null ? "未知" : providerId) + "（未变）");

        Map<String, ProviderConfig> providers =
                llm == null || llm.providers() == null ? Map.of() : llm.providers();
        if (providers.containsKey(target)) {
            out.println("提示：" + target + " 是供应商 ID，不是模型名；切换供应商请用"
                    + " aha provider use " + target);
        } else if (!isKnownModel(providers, target)) {
            // 模型名是自由文本（服务端自行校验），这里只做善意提醒，不阻断
            out.println("提示：该名称不在已知模型列表中，若服务端不支持会返回 404 / 422。");
        }
    }

    private String currentModel(SessionContext context, LocalAgentService local,
                                LlmConfig llm, String providerId) {
        Optional<SessionConfig> session = local.sessionManager().get(context.sessionId());
        String model = session.map(SessionConfig::model).orElse(null);
        if (model != null && !model.isBlank()) {
            return model;
        }
        ProviderConfig provider = llm == null || llm.providers() == null || providerId == null
                ? null : llm.providers().get(providerId);
        if (provider != null && provider.model() != null) {
            return provider.model();
        }
        return context.service().runtime().attributes().get("Model");
    }

    private static boolean isKnownModel(Map<String, ProviderConfig> providers, String model) {
        return providers.values().stream()
                .anyMatch(provider -> model.equals(provider.model()));
    }

    private static String model(String value, String providerId) {
        String name = value == null || value.isBlank() ? "（未设置）" : value;
        return providerId == null || providerId.isBlank() ? name : name + "（供应商 " + providerId + "）";
    }
}
