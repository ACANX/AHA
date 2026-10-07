package com.acanx.module.aha.cli.session;

import com.acanx.module.aha.core.memory.MemoryStore;

import java.io.PrintStream;
import java.util.List;

/**
 * 会话内命令 {@code /clear}：清空本会话的上下文（历史消息）。
 *
 * <p><b>保留</b>会话记录与配置（会话 ID、系统提示词、模型），因此清空后仍是同一个会话，
 * 身份设定不会丢；也<b>不影响</b>已存储的记忆条目——那些是跨会话的长期信息，
 * 与「本轮上下文」是两回事。</p>
 *
 * <p>生效时机：每轮推理都会重新从存储加载历史，因此下一条消息即基于空上下文。</p>
 *
 * @since 0.1.0
 */
final class ClearSessionCommand implements SessionCommand {

    @Override
    public String name() {
        return "clear";
    }

    @Override
    public String usage() {
        return "/clear";
    }

    @Override
    public String description() {
        return "清空本会话上下文（保留会话与身份）";
    }

    @Override
    public Outcome execute(SessionContext context, List<String> args) {
        PrintStream out = context.out();
        var local = context.local();
        if (local == null) {
            out.println("[error] 当前运行时为远程形态，暂不支持清空上下文。");
            return Outcome.CONTINUE;
        }

        MemoryStore store = local.memoryStore();
        int removed = store.countMessages(context.sessionId());
        if (removed == 0) {
            out.println("本会话上下文本来就是空的。");
            return Outcome.CONTINUE;
        }
        store.clearHistory(context.sessionId());

        out.println("上下文已清空");
        out.printf("  %s  %d 条消息%n", "已删除", removed);
        out.printf("  %s  %s（会话记录与身份设定保留）%n", "会话", context.sessionId());
        out.printf("  %s  %s%n", "说明", "已存储的记忆条目不受影响");
        return Outcome.CONTINUE;
    }
}
