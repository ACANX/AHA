package com.acanx.module.aha.core.memory;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ProjectId} 测试。
 *
 * <p>项目 ID 是记忆的存储位置，一旦定下就不能随意变动（否则历史记忆会「找不到」），
 * 因此这里把格式钉死。</p>
 *
 * <p><b>平台无关</b>：格式用纯函数 {@link ProjectId#sanitize} 钉（UNIX 与 Windows 形式都覆盖）；
 * 需要经过 {@link ProjectId#of} 的用例一律用平台自身的路径构造期望值——
 * {@code Path.of("/home/me")} 在 Windows 上是「当前盘的绝对路径」，会得到 {@code E:\home\me}。</p>
 *
 * @since 0.1.0
 */
class ProjectIdTest {

    @Test
    void replacesSeparatorsWithHyphen() {
        assertThat(ProjectId.sanitize("/home/me/proj")).isEqualTo("-home-me-proj");
        assertThat(ProjectId.sanitize("/Users/alice/my-project")).isEqualTo("-Users-alice-my-project");
    }

    @Test
    void uppercasesWindowsDriveLetter() {
        // 盘符转大写，':' 与 '\' 各产生一个连接符
        assertThat(ProjectId.sanitize("e:\\GitRepo\\AHA")).isEqualTo("E--GitRepo-AHA");
        assertThat(ProjectId.sanitize("E:\\GitRepo\\GitHub\\ACANX\\AHA"))
                .isEqualTo("E--GitRepo-GitHub-ACANX-AHA");
    }

    @Test
    void handlesForwardSlashesOnWindowsStylePaths() {
        // 同一个项目用 / 还是 \ 表示，应当得到同一个 ID
        assertThat(ProjectId.sanitize("C:/work/proj")).isEqualTo("C--work-proj");
        assertThat(ProjectId.sanitize("C:\\work\\proj")).isEqualTo("C--work-proj");
    }

    @Test
    void keepsNonAsciiAndSpaces() {
        assertThat(ProjectId.sanitize("/home/me/我的 项目")).isEqualTo("-home-me-我的 项目");
    }

    @Test
    void handlesRootAndNull() {
        assertThat(ProjectId.sanitize("/")).isEqualTo("-");
        assertThat(ProjectId.sanitize(null)).isEmpty();
        assertThat(ProjectId.of(null)).isEmpty();
    }

    @Test
    void ignoresTrailingSeparatorAndRelativeSegments() {
        // 不要写 POSIX 字面量：Windows 上 Path.of("/tmp/x") 是「当前盘下的绝对路径」，
        // 规范化后会带上盘符（E:\tmp\x），期望值随之不同。这里改用平台自身的路径。
        Path base = Path.of(System.getProperty("java.io.tmpdir"), "aha-id-test");
        String expected = ProjectId.sanitize(base.toAbsolutePath().normalize().toString());

        assertThat(ProjectId.of(Path.of(base + "/"))).isEqualTo(expected);
        assertThat(ProjectId.of(base.resolve("./sub/.."))).isEqualTo(expected);
    }

    @Test
    void buildsProjectScopedMemoryDirectory() {
        Path home = Path.of(System.getProperty("user.home"), "aha-home");
        Path project = Path.of(System.getProperty("user.home"), "aha-proj");

        Path dir = ProjectId.memoryDir(home, project);

        // 形状：<home>/Project/<项目 ID>/Memory
        assertThat(dir.getFileName().toString()).isEqualTo(ProjectId.MEMORY_DIR);
        assertThat(dir.getParent().getFileName().toString()).isEqualTo(ProjectId.of(project));
        assertThat(dir.getParent().getParent().getFileName().toString())
                .isEqualTo(ProjectId.PROJECT_DIR);
        assertThat(dir.getParent().getParent().getParent()).isEqualTo(home);
    }

    @Test
    void memoryDirFallsBackToDotWhenHomeIsNull() {
        Path project = Path.of(System.getProperty("user.home"), "aha-proj");

        Path dir = ProjectId.memoryDir(null, project);

        // ahaHome 为 null 时基准目录退化为「当前目录」，路径以 ./Project 开头
        String shown = dir.toString().replace('\\', '/');
        assertThat(shown).startsWith("./" + ProjectId.PROJECT_DIR + "/")
                .endsWith("/" + ProjectId.MEMORY_DIR)
                .contains("/" + ProjectId.of(project) + "/");
    }
}
