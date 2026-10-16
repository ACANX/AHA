package com.acanx.module.aha.cli.tty;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * {@link ConsoleEncoding} 的可移植性断言。
 *
 * <p>真正的 Win32 行为只能在 Windows 真机上验证（见 issue #235 的验收清单）；这里锁定一条
 * 在<b>任何平台</b>都必须成立的性质——调用不抛异常。这样若将来 FFM 初始化在
 * Linux / macOS 或 CI 上被误触发，测试会先拦住。</p>
 *
 * @since 0.1.3
 */
class ConsoleEncodingTest {

    @Test
    void prepareIsSafeOnEveryPlatform() {
        assertThatCode(ConsoleEncoding::prepare).doesNotThrowAnyException();
    }
}
