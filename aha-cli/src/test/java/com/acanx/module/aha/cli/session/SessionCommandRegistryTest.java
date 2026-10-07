package com.acanx.module.aha.cli.session;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SessionCommandRegistry} 测试：命令识别、分派与未知命令处理。
 *
 * @since 0.1.0
 */
class SessionCommandRegistryTest {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private SessionContext context() {
        return new SessionContext("s-1", null, null,
                new PrintStream(buffer, true, StandardCharsets.UTF_8), "内置默认身份", 0);
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void registersBuiltinCommands() {
        List<String> names = new SessionCommandRegistry().commands().stream()
                .map(SessionCommand::name).toList();
        assertThat(names).containsExactly("autocompact", "clear", "compact", "config", "context",
                "exit", "help", "model", "memory", "session", "tool");
    }

    @Test
    void helpListsEveryCommandIncludingItself() {
        Optional<SessionCommand.Outcome> outcome =
                new SessionCommandRegistry().dispatch(context(), "/help");

        assertThat(outcome).contains(SessionCommand.Outcome.CONTINUE);
        assertThat(output())
                .contains("/config")
                .contains("/context")
                .contains("/exit")
                .contains("/help");
    }

    @Test
    void plainInputIsNotACommand() {
        SessionCommandRegistry registry = new SessionCommandRegistry();
        assertThat(registry.dispatch(context(), "你好")).isEmpty();
        assertThat(registry.dispatch(context(), "")).isEmpty();
        assertThat(registry.dispatch(context(), "/")).isEmpty();
    }

    @Test
    void absolutePathIsNotMistakenForCommand() {
        // /usr/bin/ls 的命令名部分后跟 '/'，应作为普通消息发给模型
        assertThat(new SessionCommandRegistry().dispatch(context(), "/usr/bin/ls")).isEmpty();
    }

    @Test
    void unknownCommandIsReportedWithHelp() {
        Optional<SessionCommand.Outcome> outcome =
                new SessionCommandRegistry().dispatch(context(), "/nope now");

        assertThat(outcome).contains(SessionCommand.Outcome.CONTINUE);
        assertThat(output()).contains("未知命令: /nope").contains("/config");
    }

    @Test
    void commandNameIsCaseInsensitive() {
        assertThat(new SessionCommandRegistry().dispatch(context(), "/EXIT"))
                .contains(SessionCommand.Outcome.EXIT);
    }

    @Test
    void exitEndsSession() {
        assertThat(new SessionCommandRegistry().dispatch(context(), "/exit"))
                .contains(SessionCommand.Outcome.EXIT);
    }

    @Test
    void argumentsAreSplitByWhitespace() {
        List<String> captured = new ArrayList<>();
        SessionCommandRegistry registry = new SessionCommandRegistry();
        registry.register(new SessionCommand() {
            @Override
            public String name() {
                return "echo";
            }

            @Override
            public String usage() {
                return "/echo <a> <b>";
            }

            @Override
            public String description() {
                return "测试用";
            }

            @Override
            public Outcome execute(SessionContext ctx, List<String> args) {
                captured.addAll(args);
                return Outcome.CONTINUE;
            }
        });

        assertThat(registry.dispatch(context(), "  /echo   alpha   beta  "))
                .contains(SessionCommand.Outcome.CONTINUE);
        assertThat(captured).containsExactly("alpha", "beta");
    }

    @Test
    void configCommandPrintsSourcesAndValues() {
        SessionContext context = new SessionContext("s-1", null, null,
                new PrintStream(buffer, true, StandardCharsets.UTF_8), "内置默认身份", 0);

        assertThat(new SessionCommandRegistry().dispatch(context, "/config"))
                .contains(SessionCommand.Outcome.CONTINUE);
        assertThat(output())
                .contains("配置来源")
                .contains("有效配置")
                .contains("Memory.Path")
                .contains("AHA_HOME");
    }

    @Test
    void findIgnoresUnknownName() {
        SessionCommandRegistry registry = new SessionCommandRegistry();
        assertThat(registry.find("context")).isPresent();
        assertThat(registry.find("CONTEXT")).isPresent();
        assertThat(registry.find("missing")).isEmpty();
        assertThat(registry.find(null)).isEmpty();
    }
}
