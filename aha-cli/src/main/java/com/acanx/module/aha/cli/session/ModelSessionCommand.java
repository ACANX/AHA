package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.config.LlmConfig;
import com.acanx.module.aha.core.config.ModelTier;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.service.LocalAgentService;

import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 会话内命令 {@code /model}：查看或切换本会话使用的档位 / 模型。
 *
 * <p><b>只改本会话</b>：会话配置里固化档位或模型名，供应商由主配置 / {@code Model.yml}
 * 的 {@code Default} 决定（进程级）。切换供应商应使用 {@code aha provider use <id>}。</p>
 *
 * <p>两种规则分别处理：含 {@code Models} 的新配置下档位判定用五值白名单精确匹配且
 * 大小写敏感，{@code /model <模型名>} 仅接受五档内模型；只有 {@code Model} 的老配置
 * 维持升级前行为（任意模型名自由切换）。</p>
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
        return "/model [档位|模型名]";
    }

    @Override
    public String description() {
        return "查看或切换本会话档位 / 模型（不改供应商）";
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
        ProviderConfig provider = providerId == null || llm.providers() == null
                ? null : llm.providers().get(providerId);
        Optional<SessionConfig> session = local.sessionManager().get(context.sessionId());
        boolean tiered = provider != null && provider.hasModels();

        if (args.isEmpty()) {
            if (tiered) {
                describeTiered(out, providerId, provider, session.orElse(null), llm);
            } else {
                describeLegacy(out, providerId, provider, llm, session.orElse(null));
            }
            return Outcome.CONTINUE;
        }

        if (!tiered) {
            // 老配置：维持升级前行为（任意模型名自由切换）
            switchLegacy(context, local, llm, providerId, session.orElse(null), args.get(0));
            return Outcome.CONTINUE;
        }

        String target = args.get(0);
        Optional<ModelTier> tier = ModelTier.fromConfigName(target);
        if (tier.isEmpty()) {
            Optional<ModelTier> byModel = provider.tierOf(target);
            if (byModel.isEmpty()) {
                out.println("[error] 模型名不在五档内：" + target);
                out.println("提示：/model 仅接受档位（大小写敏感：Ultra / Pro / Standard / Flash / Fallback）"
                        + "或五档内已配置的模型名。");
                return Outcome.CONTINUE;
            }
            tier = byModel;
        }
        switchTiered(context, local, provider, session.orElse(null), tier.get());
        return Outcome.CONTINUE;
    }

    // ── 新规则（含 Models） ────────────────────────────────────────────

    private void describeTiered(PrintStream out, String providerId, ProviderConfig provider,
                                SessionConfig session, LlmConfig llm) {
        out.println("模型");
        SessionOutput.field(out, "供应商", providerId == null ? "（未设置）" : providerId);
        String globalTier = llm == null ? null : llm.defaultTier();
        SessionOutput.field(out, "默认档位", provider.effectiveTier(globalTier).configName());
        SessionOutput.field(out, "默认模型", provider.effectiveModel(globalTier));
        String currentTier = session == null ? null : session.tier();
        String currentModel = session == null ? null : session.model();
        SessionOutput.field(out, "当前使用", currentTier != null
                ? currentTier + " 档"
                : (currentModel != null ? currentModel : "（默认）"));
        out.println();

        out.println("可选档位");
        for (ModelTier tier : ModelTier.strongestFirst()) {
            String configured = provider.configuredModel(tier);
            out.printf("  %-12s %s%s%n", tier.configName(),
                    configured == null ? "（未配置）" : configured,
                    tier.configName().equals(currentTier) ? "   ← 当前" : "");
        }
        out.println();
        out.println("用法  /model <档位>          切换本会话档位（大小写敏感，如 Standard）");
        out.println("      /model <模型名>        仅接受五档内的模型名");
        out.println("      aha provider use <id>  切换供应商（进程级，写入 Model.yml）");
    }

    private void switchTiered(SessionContext context, LocalAgentService local, ProviderConfig provider,
                             SessionConfig session, ModelTier tier) {
        PrintStream out = context.out();
        if (session == null) {
            out.println("[error] 会话不存在：" + context.sessionId());
            return;
        }
        String model = provider.modelFor(tier);
        String previous = session.tier() != null
                ? session.tier()
                : (session.model() == null ? "（默认）" : session.model());
        SessionConfig updated = new SessionConfig(null, tier.configName(),
                session.systemPrompt(), session.extras());
        if (!local.sessionManager().update(context.sessionId(), updated)) {
            out.println("[error] 更新会话配置失败：" + context.sessionId());
            return;
        }
        out.println("档位已切换：" + previous + " → " + tier.configName()
                + "（模型 " + (model == null ? "（未配置）" : model) + "）");
        SessionOutput.field(out, "作用", "仅本会话，已写入会话配置");
    }

    // ── 老规则（只有 Model） ───────────────────────────────────────────

    private void describeLegacy(PrintStream out, String providerId, ProviderConfig provider,
                                LlmConfig llm, SessionConfig session) {
        out.println("模型");
        SessionOutput.field(out, "当前", model(currentModel(provider, session), providerId));
        out.println();

        Map<String, ProviderConfig> providers =
                llm == null || llm.providers() == null ? Map.of() : llm.providers();
        if (!providers.isEmpty()) {
            out.println("可选供应商（Model.yml / 内置预设）");
            providers.forEach((id, value) -> out.printf("  %-18s %s%s%n", id,
                    value.effectiveModel(llm.defaultTier()),
                    id.equals(providerId) ? "   ← 当前" : ""));
        }
        out.println();
        out.println("用法  /model <模型名>        切换本会话模型（仅模型，不改供应商）");
        out.println("      aha provider use <id>   切换供应商（进程级，写入 Model.yml）");
    }

    private void switchLegacy(SessionContext context, LocalAgentService local, LlmConfig llm,
                             String providerId, SessionConfig session, String target) {
        PrintStream out = context.out();
        if (session == null) {
            out.println("[error] 会话不存在：" + context.sessionId());
            return;
        }
        String current = currentModel(null, session);
        if (target.equals(current)) {
            out.println("已是当前模型：" + target);
            return;
        }
        SessionConfig updated = new SessionConfig(target, null,
                session.systemPrompt(), session.extras());
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
        } else if (!isKnownModel(providers, target, llm)) {
            out.println("提示：该名称不在已知模型列表中，若服务端不支持会返回 404 / 422。");
        }
    }

    private static String currentModel(ProviderConfig provider, SessionConfig session) {
        if (session != null && session.model() != null && !session.model().isBlank()) {
            return session.model();
        }
        return provider == null ? null : provider.effectiveModel(null);
    }

    private static boolean isKnownModel(Map<String, ProviderConfig> providers, String model, LlmConfig llm) {
        String globalTier = llm == null ? null : llm.defaultTier();
        return providers.values().stream()
                .anyMatch(provider -> model.equals(provider.effectiveModel(globalTier)));
    }

    private static String model(String value, String providerId) {
        String name = value == null || value.isBlank() ? "（未设置）" : value;
        return providerId == null || providerId.isBlank() ? name : name + "（供应商 " + providerId + "）";
    }
}
