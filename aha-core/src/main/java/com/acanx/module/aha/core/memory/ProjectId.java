package com.acanx.module.aha.core.memory;

import com.acanx.module.aha.core.config.SystemPromptLoader;

import java.nio.file.Path;

/**
 * 项目 ID 与项目级数据目录。
 *
 * <p>记忆按项目存放，而不是按会话：知识应当跟着代码走，且必须在跨会话时可见。
 * 因此需要一个稳定的「项目标识」——由项目根的绝对路径推导而来：</p>
 *
 * <ul>
 *   <li>分隔符（{@code /}、{@code \}、{@code :}）一律替换为 {@code -}</li>
 *   <li>Windows 盘符转为大写</li>
 * </ul>
 *
 * <pre>
 *   /home/me/proj                 → -home-me-proj
 *   /Users/alice/my-project       → -Users-alice-my-project
 *   E:\GitRepo\GitHub\ACANX\AHA   → E--GitRepo-GitHub-ACANX-AHA
 * </pre>
 *
 * <p>该形式与 Claude Code 的 {@code ~/.claude/projects/<项目ID>/} 一致，
 * 便于两边并存与迁移。</p>
 *
 * <p>项目根由 {@link SystemPromptLoader#findProjectRoot} 判定
 * （{@code .git} / {@code .aha} / {@code Aha.yaml} / {@code AHA.md} / {@code AGENTS.md}），
 * 找不到时退回当前工作目录。</p>
 *
 * @since 0.1.0
 */
public final class ProjectId {

    /** 项目级数据目录名。 */
    public static final String PROJECT_DIR = "Project";

    /** 项目级记忆目录名。 */
    public static final String MEMORY_DIR = "Memory";

    private ProjectId() {
    }

    /**
     * 由项目根推导项目 ID。
     *
     * @param projectRoot 项目根；为 {@code null} 时返回空串
     * @return 项目 ID
     */
    public static String of(Path projectRoot) {
        if (projectRoot == null) {
            return "";
        }
        return sanitize(projectRoot.toAbsolutePath().normalize().toString());
    }

    /**
     * 路径到项目 ID 的纯转换。
     *
     * <p>独立成 {@code String} 入参是为了可测：{@code Path.of("E:\\a\\b")} 在 Linux 上
     * 会被当成单个相对路径段，直接对它做 {@code toAbsolutePath()} 会拼上当前目录，
     * 得到错误的 ID。走纯函数则可以在任何平台上验证 Windows 形式。</p>
     *
     * @param absolutePath 已规范化的绝对路径
     * @return 项目 ID
     */
    static String sanitize(String absolutePath) {
        if (absolutePath == null) {
            return "";
        }
        boolean drive = absolutePath.length() > 1 && absolutePath.charAt(1) == ':';
        StringBuilder id = new StringBuilder(absolutePath.length());
        for (int i = 0; i < absolutePath.length(); i++) {
            char c = absolutePath.charAt(i);
            if (c == '/' || c == '\\' || c == ':') {
                id.append('-');
            } else if (drive && i == 0) {
                id.append(Character.toUpperCase(c));
            } else {
                id.append(c);
            }
        }
        return id.toString();
    }

    /**
     * 项目级记忆目录。
     *
     * @param ahaHome     AHA 用户级目录（如 {@code ~/.aha}）
     * @param projectRoot 项目根
     * @return 形如 {@code <ahaHome>/Project/<项目ID>/Memory}
     */
    public static Path memoryDir(Path ahaHome, Path projectRoot) {
        Path base = ahaHome == null ? Path.of(".") : ahaHome;
        return base.resolve(PROJECT_DIR).resolve(of(projectRoot)).resolve(MEMORY_DIR);
    }

    /**
     * 项目级记忆目录（用户级目录取自 {@code AHA_HOME} 或 {@code ~/.aha}）。
     *
     * @param projectRoot 项目根
     * @return 记忆目录
     */
    public static Path memoryDir(Path projectRoot) {
        return memoryDir(SystemPromptLoader.resolveUserHome(), projectRoot);
    }
}
