package com.acanx.module.aha.cli.session;

import java.util.List;

/**
 * 会话内命令 {@code /exit}。
 *
 * @since 0.1.0
 */
final class ExitSessionCommand implements SessionCommand {

    @Override
    public String name() {
        return "exit";
    }

    @Override
    public String usage() {
        return "/exit";
    }

    @Override
    public String description() {
        return "结束会话（同 exit / quit / Ctrl+D）";
    }

    @Override
    public Outcome execute(SessionContext context, List<String> args) {
        return Outcome.EXIT;
    }
}
