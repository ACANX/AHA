package com.acanx.module.aha.cli.session;

import java.util.List;

/**
 * 会话内命令（斜杠命令）。
 *
 * <p>与进程级命令（picocli 子命令，如 {@code aha init}）区分：本接口的命令只在
 * {@code aha chat} 的<b>会话生命周期内</b>有效，由 {@link SessionCommandRegistry} 分派，
 * 不经过 picocli 解析。</p>
 *
 * <p>命令名不含前缀 {@code /}；使用方式为 {@code /name [args]}。</p>
 *
 * @since 0.1.0
 */
public interface SessionCommand {

    /**
     * 命令执行结果。
     *
     * @since 0.1.0
     */
    enum Outcome {
        /** 继续会话。 */
        CONTINUE,
        /** 结束会话。 */
        EXIT
    }

    /**
     * 命令名（不含 {@code /}）。
     *
     * @return 命令名
     */
    String name();

    /**
     * 用法（含参数占位），用于帮助列表。
     *
     * @return 用法文本
     */
    String usage();

    /**
     * 一句话说明。
     *
     * @return 说明
     */
    String description();

    /**
     * 执行命令。
     *
     * @param context 执行上下文
     * @param args    参数列表（不含命令名），无参数时为空列表
     * @return 执行结果
     */
    Outcome execute(SessionContext context, List<String> args);
}
