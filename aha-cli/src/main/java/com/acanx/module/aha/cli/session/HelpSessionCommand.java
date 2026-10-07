package com.acanx.module.aha.cli.session;

import java.util.List;
import java.util.function.Supplier;

/**
 * 会话内帮助命令 {@code /help}。
 *
 * @since 0.1.0
 */
final class HelpSessionCommand implements SessionCommand {

    /** 命令来源，延迟取用避免构造期循环引用。 */
    private final Supplier<List<SessionCommand>> supplier;

    /**
     * 创建命令。
     *
     * @param supplier 已注册命令的来源
     */
    HelpSessionCommand(Supplier<List<SessionCommand>> supplier) {
        this.supplier = supplier;
    }

    @Override
    public String name() {
        return "help";
    }

    @Override
    public String usage() {
        return "/help";
    }

    @Override
    public String description() {
        return "显示本帮助";
    }

    @Override
    public Outcome execute(SessionContext context, List<String> args) {
        context.out().print(format(supplier.get()));
        return Outcome.CONTINUE;
    }

    /**
     * 格式化命令列表。
     *
     * @param commands 命令列表
     * @return 帮助文本
     */
    static String format(List<SessionCommand> commands) {
        StringBuilder text = new StringBuilder();
        text.append("会话内命令：").append(System.lineSeparator());
        for (SessionCommand command : commands) {
            text.append(String.format("  %-12s %s%n", command.usage(), command.description()));
        }
        return text.toString();
    }
}
