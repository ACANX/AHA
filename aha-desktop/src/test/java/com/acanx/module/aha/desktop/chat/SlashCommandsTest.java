package com.acanx.module.aha.desktop.chat;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SlashCommands} 测试：命令目录与解析。
 *
 * <p>命令名必须与 CLI 的会话内命令同名（GUIDesign 第 3.3 节「概念不因端而异」），
 * 因此这里对目录本身也做检查：不允许两条同名、不允许空说明。</p>
 *
 * @since 0.2.0
 */
class SlashCommandsTest {

    @Test
    void catalogHasNoDuplicatesAndNoEmptyDescriptions() {
        assertThat(SlashCommands.CATALOG).isNotEmpty();
        assertThat(new HashSet<>(SlashCommands.CATALOG.stream()
                .map(SlashCommands.Command::name).toList())).hasSameSizeAs(SlashCommands.CATALOG);
        assertThat(SlashCommands.CATALOG).allSatisfy(command -> {
            assertThat(command.label()).isNotBlank();
            assertThat(command.description()).isNotBlank();
            assertThat(command.name()).doesNotContain("/");
        });
    }

    @Test
    void parseAcceptsOnlyAWholeCommandLine() {
        assertThat(SlashCommands.parse("/help")).isPresent()
                .get().extracting(SlashCommands.Command::name).isEqualTo("help");
        assertThat(SlashCommands.parse("  /HELP  ")).isPresent();
        assertThat(SlashCommands.parse("/help me")).as("带参数就不算命令").isEmpty();
        assertThat(SlashCommands.parse("/unknown")).isEmpty();
        assertThat(SlashCommands.parse("你好")).isEmpty();
        assertThat(SlashCommands.parse("/")).isEmpty();
        assertThat(SlashCommands.parse(null)).isEmpty();
    }

    @Test
    void matchFiltersByPrefix() {
        assertThat(SlashCommands.match("")).hasSize(SlashCommands.CATALOG.size());
        assertThat(SlashCommands.match("he")).extracting(SlashCommands.Command::name)
                .containsExactly("help");
        assertThat(SlashCommands.match("log")).extracting(SlashCommands.Command::name)
                .containsExactly("log");
        assertThat(SlashCommands.match("zzz")).isEmpty();
    }

    @Test
    void displayAndCompletionText() {
        SlashCommands.Command help = SlashCommands.byName("help").orElseThrow();
        assertThat(help.display()).startsWith("/help").contains("帮助");
        assertThat(SlashCommands.completionText(help)).isEqualTo("/help");
    }

    @Test
    void cliSharedCommandsArePresent() {
        // 与 CLI 同名的部分（其余命令桌面端还没有对应动作，因此不进目录）
        assertThat(SlashCommands.CATALOG.stream().map(SlashCommands.Command::name))
                .contains("help", "clear", "model", "tools", "exit");
    }
}
