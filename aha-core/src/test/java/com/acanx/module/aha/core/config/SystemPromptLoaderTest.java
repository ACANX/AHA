package com.acanx.module.aha.core.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 系统提示词加载测试。
 *
 * @since 0.1.0
 */
class SystemPromptLoaderTest {

    @TempDir
    Path tempDir;

    private String savedAhaHome;
    private String savedUserHome;

    /**
     * 隔离用户级目录：本类所有用例都把 {@code AHA_HOME} 与 {@code user.home} 指向临时目录。
     *
     * <p>不隔离就会读到开发机上真实的 {@code ~/.aha/AHA.md}：只要用户写过全局身份文件，
     * 这里拿到的就是与预期完全无关的内容（在 Windows 上五个用例首跑即红）。</p>
     */
    @BeforeEach
    void isolateUserLevelDirectory() throws Exception {
        savedAhaHome = System.getProperty("AHA_HOME");
        savedUserHome = System.getProperty("user.home");

        Path userHome = Files.createDirectories(tempDir.resolve("home"));
        System.setProperty("user.home", userHome.toString());
        System.setProperty("AHA_HOME", userHome.resolve(".aha").toString());
    }

    @AfterEach
    void restoreUserLevelDirectory() {
        restoreProperty("AHA_HOME", savedAhaHome);
        restoreProperty("user.home", savedUserHome);
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    @Test
    void explicitTextWinsOverEverything() throws Exception {
        Files.writeString(tempDir.resolve("AHA.md"), "来自文件");

        var resolved = SystemPromptLoader.resolve(
                "来自命令行", null, tempDir, null, "来自内联");

        assertThat(resolved.text()).isEqualTo("来自命令行");
        assertThat(resolved.source()).isEqualTo("--system");
        assertThat(resolved.fromFile()).isFalse();
    }

    @Test
    void loadsPromptFileFromWorkingDirectory() throws Exception {
        Files.writeString(tempDir.resolve("AHA.md"), "  你是审查员  \n");

        var resolved = SystemPromptLoader.resolve(null, null, tempDir, null, null);

        assertThat(resolved.text()).isEqualTo("你是审查员");
        assertThat(resolved.source()).isEqualTo("file:" + tempDir.resolve("AHA.md"));
        assertThat(resolved.fromFile()).isTrue();
    }

    @Test
    void searchesParentDirectoriesUpwards() throws Exception {
        Files.writeString(tempDir.resolve("AHA.md"), "项目级身份");
        Path deep = Files.createDirectories(tempDir.resolve("a/b/c"));

        var resolved = SystemPromptLoader.resolve(null, null, deep, null, null);

        assertThat(resolved.text()).isEqualTo("项目级身份");
    }

    @Test
    void nearestFileWinsOverAncestor() throws Exception {
        Files.writeString(tempDir.resolve("AHA.md"), "项目级");
        Path sub = Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(sub.resolve("AHA.md"), "子目录级");

        var resolved = SystemPromptLoader.resolve(null, null, sub, null, null);

        assertThat(resolved.text()).isEqualTo("子目录级");
    }

    @Test
    void fallsBackThroughConfiguredFileNames() throws Exception {
        // AHA.md 缺失时应回退到 AGENTS.md
        Files.writeString(tempDir.resolve("AGENTS.md"), "通用约定");

        var resolved = SystemPromptLoader.resolve(null, null, tempDir, null, null);

        assertThat(resolved.text()).isEqualTo("通用约定");
        assertThat(resolved.source()).endsWith("AGENTS.md");
    }

    @Test
    void sameLevelFilesAreAllInjectedInCandidateOrder() throws Exception {
        // 同一层级允许并存：AHA.md 表达 AHA 自己的约定，AGENTS.md 表达跨工具约定，
        // 只取第一个会让用户以为写的那份没生效
        Files.writeString(tempDir.resolve("AHA.md"), "AHA 优先");
        Files.writeString(tempDir.resolve("AGENTS.md"), "AGENTS 其次");

        var resolved = SystemPromptLoader.resolve(
                null, null, tempDir, List.of("AGENTS.md", "AHA.md"), null);

        // 顺序即 PromptFiles 顺序（后加载者更具体）
        assertThat(resolved.text()).isEqualTo("AGENTS 其次\n\nAHA 优先");
        assertThat(SystemPromptLoader.filePaths(resolved.source()))
                .containsExactly(tempDir.resolve("AGENTS.md"), tempDir.resolve("AHA.md"));
    }

    @Test
    void fallsBackToInlineThenBuiltin() {
        var inline = SystemPromptLoader.resolve(null, null, tempDir, null, "内联身份");
        assertThat(inline.text()).isEqualTo("内联身份");
        assertThat(inline.source()).isEqualTo("Aha.Agent.SystemPrompt");

        var builtin = SystemPromptLoader.resolve(null, null, tempDir, null, null);
        assertThat(builtin.text()).isEqualTo(SystemPromptLoader.BUILTIN_IDENTITY);
        assertThat(builtin.source()).isEqualTo("builtin");
    }

    @Test
    void findByProjectRootDetectsPromptFileAsMarker() throws Exception {
        Files.writeString(tempDir.resolve("AHA.md"), "身份");
        Path deep = Files.createDirectories(tempDir.resolve("x/y"));

        assertThat(SystemPromptLoader.findProjectRoot(deep)).isEqualTo(tempDir);
    }

    @Test
    void findByProjectRootDetectsGitDirectory() throws Exception {
        Files.createDirectories(tempDir.resolve(".git"));
        Path deep = Files.createDirectories(tempDir.resolve("x"));

        assertThat(SystemPromptLoader.findProjectRoot(deep)).isEqualTo(tempDir);
    }

    @Test
    void emptyFilesAreIgnoredInFavourOfLaterCandidates() throws Exception {
        // 空文件不构成有效提示词来源
        Files.writeString(tempDir.resolve("AHA.md"), "   \n  ");

        var resolved = SystemPromptLoader.resolve(null, null, tempDir, null, "兜底内联");

        assertThat(resolved.source()).isEqualTo("Aha.Agent.SystemPrompt");
    }
    @Test
    void userLevelKeepsAllCandidatesBeforeProjectLevel() throws Exception {
        // 用户级 AHA.md → 用户级 AGENTS.md → 项目级（就近目录）
        Path project = tempDir.resolve("proj");
        Files.createDirectories(project);
        Path home = tempDir.resolve("home");
        Files.createDirectories(home);
        Files.writeString(home.resolve("AHA.md"), "用户级 AHA");
        Files.writeString(home.resolve("AGENTS.md"), "用户级 AGENTS");
        Files.writeString(project.resolve("AGENTS.md"), "项目级 AGENTS");

        String previous = System.getProperty("AHA_HOME");
        System.setProperty("AHA_HOME", home.toString());
        try {
            var resolved = SystemPromptLoader.resolve(null, null, project, null, null);

            assertThat(resolved.text())
                    .isEqualTo("用户级 AHA\n\n用户级 AGENTS\n\n项目级 AGENTS");
            assertThat(SystemPromptLoader.isUserLevel(home.resolve("AHA.md"))).isTrue();
            assertThat(SystemPromptLoader.isUserLevel(project.resolve("AGENTS.md"))).isFalse();
        } finally {
            if (previous == null) {
                System.clearProperty("AHA_HOME");
            } else {
                System.setProperty("AHA_HOME", previous);
            }
        }
    }

    @Test
    void environmentBlockDescribesPlatformAndShell() {
        String block = SystemPromptLoader.environmentBlock();

        assertThat(block).contains("运行环境");
        assertThat(block).contains(System.getProperty("os.name"));
        assertThat(block).contains("工作目录");
        // 平台与 shell 必须分清：这是「Unix 命令在 cmd.exe 下失败」的根因
        boolean windows = System.getProperty("os.name", "")
                .toLowerCase(java.util.Locale.ROOT).contains("win");
        assertThat(block).contains(windows ? "cmd.exe /c" : "/bin/sh -c");
    }

}
