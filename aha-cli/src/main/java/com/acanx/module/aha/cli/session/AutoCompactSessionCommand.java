package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.common.model.SessionConfig;
import com.acanx.module.aha.core.agent.ContextCompactor;
import com.acanx.module.aha.core.service.LocalAgentService;

import java.io.PrintStream;
import java.util.List;
import java.util.Optional;

/**
 * 会话内命令 {@code /autocompact}：设置自动压缩阈值。
 *
 * <p>与 {@code /compact} 的分工：{@code /compact} 是「现在就压」，{@code /autocompact}
 * 是「超过多少就自己压」。后者每轮回答后检查一次估算用量，达到阈值即执行一次
 * {@code /compact}，输出与手动执行完全一致。</p>
 *
 * <p>阈值固化在会话配置里，因此 {@code aha chat --session <id>} 续接后设置仍在。</p>
 *
 * @since 0.1.0
 */
final class AutoCompactSessionCommand implements SessionCommand {

    @Override
    public String name() {
        return "autocompact";
    }

    @Override
    public String usage() {
        return "/autocompact [阈值|off]";
    }

    @Override
    public String description() {
        return "设置自动压缩阈值（如 285k、1M），off 关闭";
    }

    @Override
    public Outcome execute(SessionContext context, List<String> args) {
        PrintStream out = context.out();
        LocalAgentService local = context.local();
        if (local == null) {
            out.println("[error] 自动压缩仅支持本地运行时");
            return Outcome.CONTINUE;
        }
        Optional<SessionConfig> session = local.sessionManager().get(context.sessionId());
        if (session.isEmpty()) {
            out.println("[error] 会话不存在: " + context.sessionId());
            return Outcome.CONTINUE;
        }
        if (args.isEmpty()) {
            printStatus(context, session.get());
            return Outcome.CONTINUE;
        }

        long threshold;
        try {
            threshold = AutoCompactSetting.parse(args.get(0));
        } catch (IllegalArgumentException e) {
            out.println("[error] " + e.getMessage());
            out.println("       用法 " + usage() + "，例如 /autocompact 285k");
            return Outcome.CONTINUE;
        }
        if (args.size() > 1) {
            out.println("[error] 阈值写法不支持空格分隔（如 285 k），请写成 285k");
            return Outcome.CONTINUE;
        }

        SessionConfig updated = AutoCompactSetting.withThreshold(session.get(), threshold);
        if (!local.sessionManager().update(context.sessionId(), updated)) {
            out.println("[error] 写入会话配置失败: " + context.sessionId());
            return Outcome.CONTINUE;
        }

        out.println(threshold > AutoCompactSetting.DISABLED ? "自动压缩已开启" : "自动压缩已关闭");
        if (threshold > AutoCompactSetting.DISABLED) {
            SessionOutput.field(out, "阈值", AutoCompactSetting.describe(threshold));
            SessionOutput.field(out, "当前", currentTokens(context));
            SessionOutput.field(out, "触发", "每轮回答后估算上下文，达到阈值即自动压缩");
            SessionOutput.field(out, "保留", "最近 " + ContextCompactor.DEFAULT_KEEP_MESSAGES + " 条消息");
            SessionOutput.field(out, "关闭", "/autocompact off");
        }
        return Outcome.CONTINUE;
    }

    /**
     * 打印当前设置与用量。
     *
     * @param context 会话上下文
     * @param session 会话配置
     */
    private void printStatus(SessionContext context, SessionConfig session) {
        PrintStream out = context.out();
        long threshold = AutoCompactSetting.thresholdOf(session);
        out.println("自动压缩");
        SessionOutput.field(out, "状态",
                threshold > AutoCompactSetting.DISABLED ? "已开启" : "已关闭");
        SessionOutput.field(out, "阈值", AutoCompactSetting.describe(threshold));
        SessionOutput.field(out, "当前", currentTokens(context));
        SessionOutput.field(out, "保留",
                "最近 " + ContextCompactor.DEFAULT_KEEP_MESSAGES + " 条消息（/compact N 可指定）");
        if (threshold <= AutoCompactSetting.DISABLED) {
            SessionOutput.field(out, "开启", "/autocompact 285k");
        } else {
            SessionOutput.field(out, "关闭", "/autocompact off");
        }
    }

    /**
     * 当前上下文的估算用量。
     *
     * @param context 会话上下文
     * @return 描述文本
     */
    private static String currentTokens(SessionContext context) {
        Optional<ContextSnapshot> snapshot = ContextSnapshot.of(context);
        if (snapshot.isEmpty()) {
            return null;
        }
        return TokenEstimator.describe(snapshot.get().tokens(), snapshot.get().hasSystemPrompt());
    }
}
