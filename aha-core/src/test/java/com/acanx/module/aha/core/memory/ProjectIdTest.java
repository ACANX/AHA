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
        assertThat(ProjectId.of(Path.of("/tmp/aha-id-test/"))).isEqualTo("-tmp-aha-id-test");
        assertThat(ProjectId.of(Path.of("/tmp/aha-id-test/./sub/..")))
                .isEqualTo("-tmp-aha-id-test");
    }

    @Test
    void buildsProjectScopedMemoryDirectory() {
        Path dir = ProjectId.memoryDir(Path.of("/home/me/.aha"), Path.of("/home/me/proj"));

        assertThat(dir.toString().replace('\\', '/'))
                .isEqualTo("/home/me/.aha/Project/-home-me-proj/Memory");
    }

    @Test
    void memoryDirFallsBackToDotWhenHomeIsNull() {
        Path dir = ProjectId.memoryDir(null, Path.of("/home/me/proj"));

        assertThat(dir.toString().replace('\\', '/'))
                .isEqualTo("./Project/-home-me-proj/Memory");
    }
}
