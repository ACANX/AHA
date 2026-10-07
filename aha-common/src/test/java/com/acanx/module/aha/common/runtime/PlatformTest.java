package com.acanx.module.aha.common.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link Platform} 测试。
 *
 * <p>这里的判断是三处展示的<b>唯一</b>来源（工具实际启动的 shell、提示词里的环境说明、
 * 渲染出的「终端」行）。一旦不一致，展示就是在骗人，因此钉住格式。</p>
 *
 * @since 0.1.0
 */
class PlatformTest {

    @Test
    void shellCommandMatchesTheActualInterpreter() {
        // 必须与 ShellExecTool 真正启动的进程一致
        if (Platform.isWindows()) {
            assertThat(Platform.shellCommand()).isEqualTo("cmd.exe /c");
        } else {
            assertThat(Platform.shellCommand()).isEqualTo("/bin/sh -c");
        }
    }

    @Test
    void describeIncludesNameAndArchitecture() {
        assertThat(Platform.describe())
                .contains(System.getProperty("os.name"))
                .contains(System.getProperty("os.arch"));
    }

    @Test
    void workingDirectoryIsAbsolute() {
        assertThat(Platform.workingDirectory())
                .isEqualTo(System.getProperty("user.dir"));
    }
}
