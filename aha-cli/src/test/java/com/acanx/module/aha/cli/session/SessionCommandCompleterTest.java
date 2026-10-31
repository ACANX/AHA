package com.acanx.module.aha.cli.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SessionCommandCompleter} 的候选触发条件（issue #333）。
 *
 * <p>补全器本身依赖 JLine 的 {@code ParsedLine}，无终端环境下不易构造，
 * 因此把「什么时候该给候选」抽成 {@link SessionCommandCompleter#shouldComplete(String)} 单独测。</p>
 *
 * @since 0.1.5
 */
class SessionCommandCompleterTest {

    @Test
    void completesRightAfterSlash() {
        assertThat(SessionCommandCompleter.shouldComplete("/")).isTrue();
    }

    @Test
    void completesWhileTypingCommandName() {
        assertThat(SessionCommandCompleter.shouldComplete("/co")).isTrue();
        assertThat(SessionCommandCompleter.shouldComplete("/compact")).isTrue();
    }

    @Test
    void doesNotCompleteOnceArgumentsStarted() {
        assertThat(SessionCommandCompleter.shouldComplete("/model ")).isFalse();
        assertThat(SessionCommandCompleter.shouldComplete("/model deepseek")).isFalse();
    }

    @Test
    void doesNotCompletePlainMessagesOrNull() {
        assertThat(SessionCommandCompleter.shouldComplete("hello")).isFalse();
        assertThat(SessionCommandCompleter.shouldComplete(" echo /x")).isFalse();
        assertThat(SessionCommandCompleter.shouldComplete(null)).isFalse();
    }
}
