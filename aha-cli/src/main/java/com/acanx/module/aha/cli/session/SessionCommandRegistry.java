package com.acanx.module.aha.cli.session;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 会话内命令注册表。
 *
 * <p>负责解析 {@code /name [args]} 形式的输入并分派到已注册的 {@link SessionCommand}。
 * 输入不构成命令（不含 {@code /} 前缀、或形如 {@code /usr/bin/ls}）时返回
 * {@link Optional#empty()}，由调用方按普通消息处理。</p>
 *
 * <p>0.1 只注册内置命令；0.3 起扩展点 {@code CommandProvider} 注册的命令将并入同一注册表，
 * 使 CLI 与未来的其他前端共用同一命令面。</p>
 *
 * @since 0.1.0
 */
public final class SessionCommandRegistry {

    private final Map<String, SessionCommand> commands = new LinkedHashMap<>();

    /**
     * 创建注册表并注册全部内置命令。
     */
    public SessionCommandRegistry() {
        register(new AutoCompactSessionCommand());
        register(new ClearSessionCommand());
        register(new CompactSessionCommand());
        register(new ConfigSessionCommand());
        register(new ContextSessionCommand());
        register(new ExitSessionCommand());
        register(new HelpSessionCommand(this::commands));
        register(new ModelSessionCommand());
        register(new MemorySessionCommand());
        register(new SessionInfoCommand());
        register(new ToolSessionCommand());
    }

    /**
     * 注册命令。同名命令后者覆盖前者。
     *
     * @param command 命令
     */
    public void register(SessionCommand command) {
        commands.put(command.name().toLowerCase(Locale.ROOT), command);
    }

    /**
     * 已注册命令（按注册顺序）。
     *
     * @return 命令列表
     */
    public List<SessionCommand> commands() {
        return List.copyOf(commands.values());
    }

    /**
     * 按名查找命令（忽略大小写）。
     *
     * @param name 命令名（不含 {@code /}）
     * @return 命令
     */
    public Optional<SessionCommand> find(String name) {
        return name == null || name.isBlank()
                ? Optional.empty()
                : Optional.ofNullable(commands.get(name.toLowerCase(Locale.ROOT)));
    }

    /**
     * 尝试按会话内命令处理输入。
     *
     * <p>未知命令不会当作普通消息发给模型，而是提示错误并列出可用命令——
     * 否则用户会以为指令生效了。</p>
     *
     * @param context 执行上下文
     * @param input   用户输入
     * @return 命中时的执行结果；未命中返回 {@link Optional#empty()}
     */
    public Optional<SessionCommand.Outcome> dispatch(SessionContext context, String input) {
        String line = input == null ? "" : input.strip();
        if (line.length() < 2 || line.charAt(0) != '/') {
            return Optional.empty();
        }
        int end = 1;
        while (end < line.length() && isNameChar(line.charAt(end))) {
            end++;
        }
        // 命令名后面必须是空白或行尾。这样 /usr/bin/ls、/etc/hosts 这类输入会落到
        // 「发给模型」分支，而不是被误判为未知命令。
        if (end == 1 || (end < line.length() && !Character.isWhitespace(line.charAt(end)))) {
            return Optional.empty();
        }
        String name = line.substring(1, end);
        List<String> args = splitArgs(line.substring(end));
        Optional<SessionCommand> command = find(name);
        if (command.isEmpty()) {
            context.out().println("[error] 未知命令: /" + name);
            context.out().print(HelpSessionCommand.format(commands()));
            return Optional.of(SessionCommand.Outcome.CONTINUE);
        }
        return Optional.of(command.get().execute(context, args));
    }

    private static boolean isNameChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '-';
    }

    private static List<String> splitArgs(String rest) {
        String trimmed = rest == null ? "" : rest.strip();
        return trimmed.isEmpty() ? List.of() : List.of(trimmed.split("\\s+"));
    }
}
