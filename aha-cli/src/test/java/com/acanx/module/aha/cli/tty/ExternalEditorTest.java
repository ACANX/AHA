package com.acanx.module.aha.cli.tty;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ExternalEditor} 测试。
 *
 * <p>只测命令解析：真正启动编辑器需要交互式终端，不适合放进单测。</p>
 *
 * @since 0.1.0
 */
class ExternalEditorTest {

    /** 常见编辑器名，用于验证“先试整串、再按空白切”的行为。 */
    private static final Set<String> KNOWN =
            Set.of("code", "emacs", "vim", "nano", "vi", "notepad", "open");

    /** 只认得常见编辑器名，其余（含带空格整串）视为不可用。 */
    private static final java.util.function.Predicate<String> ON_PATH = KNOWN::contains;

    /** 全部命令都不可用。 */
    private static final java.util.function.Predicate<String> NONE_AVAILABLE = name -> false;

    private static final Set<String> NANO = Set.of("nano");

    @Test
    void explicitPropertyWinsOverEnvironment() {
        Optional<List<String>> command = ExternalEditor.command(
                Map.of(ExternalEditor.VISUAL, "vim", ExternalEditor.EDITOR, "nano"),
                "Linux", ON_PATH, "code -w");

        assertThat(command).contains(List.of("code", "-w"));
    }

    @Test
    void visualWinsOverEditor() {
        Optional<List<String>> command = ExternalEditor.command(
                Map.of(ExternalEditor.VISUAL, "emacs", ExternalEditor.EDITOR, "nano"),
                "Linux", ON_PATH, null);

        assertThat(command).contains(List.of("emacs"));
    }

    @Test
    void editorIsUsedWhenVisualIsAbsent() {
        Optional<List<String>> command = ExternalEditor.command(
                Map.of(ExternalEditor.EDITOR, "code -w"), "Linux", ON_PATH, null);

        assertThat(command).contains(List.of("code", "-w"));
    }

    @Test
    void treatsWholeValueAsOneCommandWhenPathContainsSpaces() {
        // C:\Program Files\... 这类路径无条件按空白切会被切碎
        Optional<List<String>> command = ExternalEditor.command(
                Map.of(ExternalEditor.EDITOR, "C:\\Program Files\\Editor\\edit.exe"),
                "Windows", name -> name.startsWith("C:\\Program"), null);

        assertThat(command).contains(List.of("C:\\Program Files\\Editor\\edit.exe"));
    }

    @Test
    void skipsUnavailableEnvironmentValueAndFallsBack() {
        // 用户设了 EDITOR 但没装：跳过而不是直接失败
        Optional<List<String>> command = ExternalEditor.command(
                Map.of(ExternalEditor.EDITOR, "no-such-editor"), "Linux", NANO::contains, null);

        assertThat(command).contains(List.of("nano"));
    }

    @Test
    void fallsBackToPlatformDefaults() {
        assertThat(ExternalEditor.command(Map.of(), "Windows 11", ON_PATH, null))
                .contains(List.of("notepad"));
        assertThat(ExternalEditor.command(Map.of(), "Mac OS X", ON_PATH, null))
                .contains(List.of("open", "-W", "-t"));
        assertThat(ExternalEditor.command(Map.of(), "Linux", ON_PATH, null))
                .contains(List.of("nano"));
    }

    @Test
    void prefersViWhenNanoAndVimAreMissing() {
        assertThat(ExternalEditor.command(Map.of(), "Linux", "vi"::equals, null))
                .contains(List.of("vi"));
    }

    @Test
    void returnsEmptyWhenNothingIsAvailable() {
        assertThat(ExternalEditor.command(Map.of(), "Linux", NONE_AVAILABLE, "  ")).isEmpty();
    }

    @Test
    void openingWithoutEditorFails() {
        // 解析不到编辑器时给出可操作的提示，而不是抛 NPE
        assertThat(ExternalEditor.command(Map.of(), "Linux", NONE_AVAILABLE, null)).isEmpty();
    }
}
