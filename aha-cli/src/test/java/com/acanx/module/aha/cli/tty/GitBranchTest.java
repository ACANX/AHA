package com.acanx.module.aha.cli.tty;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GitBranch} 测试：普通仓库、带斜杠的分支名、detached HEAD、worktree、非仓库（issue #353）。
 *
 * @since 0.1.5
 */
class GitBranchTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    @Test
    void readsTheBranchName(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".git").resolve("HEAD"), "ref: refs/heads/dev\n");

        assertThat(GitBranch.detect(dir)).isEqualTo("dev");
    }

    @Test
    void keepsSlashesInBranchNames(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".git").resolve("HEAD"),
                "ref: refs/heads/feature/status-bar\n");

        assertThat(GitBranch.detect(dir)).isEqualTo("feature/status-bar");
    }

    @Test
    void detachedHeadShowsShortSha(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".git").resolve("HEAD"), SHA + "\n");

        assertThat(GitBranch.detect(dir)).isEqualTo(SHA.substring(0, 7));
    }

    @Test
    void followsTheGitdirFileOfAWorktree(@TempDir Path root) throws IOException {
        // worktree：.git 是文件，内容 `gitdir: <绝对路径>`
        Path realGit = root.resolve("real.git");
        Files.createDirectories(realGit);
        Files.writeString(realGit.resolve("HEAD"), "ref: refs/heads/wt\n");
        Path worktree = root.resolve("wt");
        Files.createDirectories(worktree);
        Files.writeString(worktree.resolve(".git"), "gitdir: " + realGit + "\n");

        assertThat(GitBranch.detect(worktree)).isEqualTo("wt");
    }

    @Test
    void findsRepositoryFromASubdirectory(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve(".git"));
        Files.writeString(dir.resolve(".git").resolve("HEAD"), "ref: refs/heads/dev\n");
        Path deep = dir.resolve("a/b/c");
        Files.createDirectories(deep);

        assertThat(GitBranch.detect(deep)).isEqualTo("dev");
    }

    @Test
    void nonRepositoryIsEmpty(@TempDir Path dir) {
        assertThat(GitBranch.detect(dir)).isEmpty();
    }

    @Test
    void nullOrMissingPathIsEmpty() {
        assertThat(GitBranch.detect(null)).isEmpty();
        assertThat(GitBranch.detect(Path.of("/definitely/not/a/repo/xyz"))).isEmpty();
    }
}
