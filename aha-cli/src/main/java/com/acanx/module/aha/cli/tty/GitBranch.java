package com.acanx.module.aha.cli.tty;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 读取工作目录所在的 Git 分支（issue #353）。
 *
 * <p>只读文件、**不起子进程**：从目录向上找 `.git`（目录，或 worktree 的 `.git` 文件里
 * `gitdir:` 指向），再读该目录下的 `HEAD`——`ref: refs/heads/<分支>` 取分支名，
 * 否则视为 detached HEAD 取 7 位短 sha。</p>
 *
 * <p>任何一步失败都返回空串：状态栏不因「不在仓库 / 权限不足 / 格式意外」而报错或阻塞启动。
 * 不走 `git` 子进程是有意的——状态栏在每次启动都要算，进程创建的开销与 PATH 依赖都不划算。</p>
 *
 * @since 0.1.5
 */
public final class GitBranch {

    private GitBranch() {
    }

    /**
     * 探测分支名。
     *
     * @param start 起始目录；{@code null} 表示未知
     * @return 分支名 / 7 位短 sha；不在 git 仓库时为空串
     */
    public static String detect(Path start) {
        if (start == null) {
            return "";
        }
        Path gitDir = findGitDir(start.toAbsolutePath().normalize());
        if (gitDir == null) {
            return "";
        }
        try {
            String head = Files.readString(gitDir.resolve("HEAD"), StandardCharsets.UTF_8).trim();
            if (head.startsWith("ref:")) {
                String ref = head.substring("ref:".length()).trim();
                // feature/x 这类带斜杠的分支名要完整保留
                String prefix = "refs/heads/";
                return ref.startsWith(prefix) ? ref.substring(prefix.length()) : ref;
            }
            // detached HEAD：HEAD 里就是提交 sha
            return head.length() >= 7 ? head.substring(0, 7) : head;
        } catch (IOException | RuntimeException e) {
            return "";
        }
    }

    /**
     * 从起始目录向上查找 `.git`（目录或 worktree 的 `.git` 文件）。
     *
     * @param start 起始目录
     * @return git 目录；找不到返回 {@code null}
     */
    private static Path findGitDir(Path start) {
        for (Path dir = start; dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(".git");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            if (Files.isRegularFile(candidate)) {
                try {
                    String line = Files.readString(candidate, StandardCharsets.UTF_8).trim();
                    if (!line.startsWith("gitdir:")) {
                        return null;
                    }
                    String target = line.substring("gitdir:".length()).trim();
                    Path resolved = Path.of(target);
                    return resolved.isAbsolute() ? resolved : dir.resolve(resolved).normalize();
                } catch (IOException | RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }
}
