package com.acanx.module.aha.cli.session;

import java.util.Optional;

/**
 * 自动压缩：每轮回答后检查一次用量，达到阈值就执行一次 {@code /compact}。
 *
 * <p>压缩动作直接复用 {@code /compact} 命令，不另写一套——否则「手动压缩」与
 * 「自动压缩」的提示、保留策略、失败处理迟早会分叉。</p>
 *
 * <p>阈值每轮从会话配置重新读取，而不是缓存在进程里，这样 {@code /autocompact off}
 * 之后下一轮就停。</p>
 *
 * @since 0.1.0
 */
public final class AutoCompactor {

    private AutoCompactor() {
    }

    /**
     * 达到阈值时压缩上下文。
     *
     * @param context  会话上下文
     * @param registry 会话内命令注册表
     * @return 本次是否触发了压缩
     */
    public static boolean runIfNeeded(SessionContext context, SessionCommandRegistry registry) {
        long threshold = AutoCompactSetting.thresholdOf(context.sessionConfig());
        if (threshold <= AutoCompactSetting.DISABLED) {
            return false;
        }
        Optional<ContextSnapshot> snapshot = ContextSnapshot.of(context);
        if (snapshot.isEmpty() || snapshot.get().tokens() < threshold) {
            return false;
        }
        context.out().println();
        context.out().println("上下文约 " + snapshot.get().tokens() + " tokens 已达自动压缩阈值 "
                + threshold + "，正在压缩…");
        registry.dispatch(context, "/compact");
        return true;
    }
}
