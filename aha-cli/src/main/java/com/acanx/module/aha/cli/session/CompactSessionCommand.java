package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.core.agent.ContextCompactor;
import com.acanx.module.aha.core.service.LocalAgentService;

import java.io.PrintStream;
import java.util.List;

/**
 * 会话内命令 {@code /compact}：压缩上下文。
 *
 * <p>把较早的对话交给模型摘要，再用摘要替换掉那批消息，只保留最近若干条。
 * 这是<b>不可逆</b>的操作，因此摘要生成失败时历史不会被改动。</p>
 *
 * <p>压缩的价值不只是省 token：会话一旦超过上下文窗口，最早的历史本来就会被
 * 丢弃，压缩是把「静默丢弃」换成「有损但留痕」。</p>
 *
 * @since 0.1.0
 */
final class CompactSessionCommand implements SessionCommand {

    /** 摘要预览的字符上限。 */
    private static final int PREVIEW_CHARS = 400;

    @Override
    public String name() {
        return "compact";
    }

    @Override
    public String usage() {
        return "/compact [保留条数]";
    }

    @Override
    public String description() {
        return "压缩上下文：用模型摘要替换较早的对话";
    }

    @Override
    public Outcome execute(SessionContext context, List<String> args) {
        PrintStream out = context.out();
        LocalAgentService local = context.local();
        if (local == null) {
            out.println("[error] 上下文压缩仅支持本地运行时");
            return Outcome.CONTINUE;
        }
        int keep = ContextCompactor.DEFAULT_KEEP_MESSAGES;
        if (!args.isEmpty()) {
            keep = parseKeep(args.get(0));
            if (keep < 1) {
                out.println("[error] 保留条数必须是正整数: " + args.get(0));
                out.println("       用法 " + usage());
                return Outcome.CONTINUE;
            }
        }

        long tokensBefore = ContextSnapshot.of(context).map(ContextSnapshot::tokens).orElse(0L);
        ContextCompactor.Outcome outcome;
        try {
            outcome = local.compact(context.sessionId(), keep);
        } catch (AhaException e) {
            out.println("[error] " + e.getMessage());
            return Outcome.CONTINUE;
        }

        if (!outcome.changed()) {
            out.println("无需压缩");
            SessionOutput.field(out, "历史", outcome.kept() + " 条，未超过保留条数 " + keep);
            return Outcome.CONTINUE;
        }

        long tokensAfter = ContextSnapshot.of(context).map(ContextSnapshot::tokens).orElse(0L);
        out.println("上下文已压缩");
        SessionOutput.field(out, "摘要",
                outcome.summary().length() + " 字符，由 " + outcome.model() + " 生成");
        SessionOutput.field(out, "消息",
                (outcome.removed() + outcome.kept()) + " 条 → " + outcome.total()
                        + " 条（摘要 1 条 + 保留 " + outcome.kept() + " 条）");
        SessionOutput.field(out, "估算",
                "约 " + tokensBefore + " tokens → 约 " + tokensAfter + " tokens");
        SessionOutput.field(out, "预览", preview(outcome.summary()));
        SessionOutput.field(out, "说明", "摘要已作为系统消息写回历史开头，下一轮即生效");
        return Outcome.CONTINUE;
    }

    /**
     * 解析保留条数。
     *
     * @param raw 用户输入
     * @return 条数；无法解析时返回 -1
     */
    private static int parseKeep(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * 生成一行摘要预览。
     *
     * <p>摘要正文会写回历史并影响后续回答，用户应当有机会看到它长什么样；
     * 但整段贴进 REPL 太吵，因此折叠空白并截断。</p>
     *
     * @param summary 摘要正文
     * @return 预览文本
     */
    private static String preview(String summary) {
        String flat = summary == null ? "" : summary.replaceAll("\\s+", " ").trim();
        return flat.length() <= PREVIEW_CHARS
                ? flat
                : flat.substring(0, PREVIEW_CHARS) + "…";
    }
}
