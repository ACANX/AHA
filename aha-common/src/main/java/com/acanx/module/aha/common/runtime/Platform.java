package com.acanx.module.aha.common.runtime;

import java.nio.file.Path;
import java.util.Locale;

/**
 * 运行平台判定。
 *
 * <p>此前 {@code os.name} 的判断散落在工具、提示词、渲染三处，各自一份逻辑 ——
 * 一处改了另一处不改就会出现「提示词说 cmd.exe、实际渲染写成 /bin/sh」这种自相矛盾。
 * 集中到这里，只保留事实查询，不做任何平台适配。</p>
 *
 * @since 0.1.0
 */
public final class Platform {

    private Platform() {
    }

    /**
     * 是否为 Windows。
     *
     * @return Windows 上为 {@code true}
     */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * 执行 shell 命令时实际使用的解释器前缀。
     *
     * <p>与 {@code ShellExecTool} 真正启动的进程一致：Windows 是 {@code cmd.exe /c}，
     * 其它平台是 {@code /bin/sh -c}。展示层据此告诉用户「命令在哪个终端里执行」，
     * 如果这里与实际不一致，展示就是在骗人。</p>
     *
     * @return shell 前缀描述
     */
    public static String shellCommand() {
        return isWindows() ? "cmd.exe /c" : "/bin/sh -c";
    }

    /**
     * 操作系统的展示名（含架构）。
     *
     * @return 例如 {@code Windows 11 (amd64)}
     */
    public static String describe() {
        return System.getProperty("os.name", "未知") + " (" + System.getProperty("os.arch", "未知") + ")";
    }

    /**
     * 当前工作目录。
     *
     * <p>取进程工作目录（{@code user.dir}）而不是项目根：shell 命令继承的是进程工作目录，
     * 两者可能不同，展示时必须用前者。</p>
     *
     * @return 绝对路径；无法确定时返回 {@code user.dir} 原值
     */
    public static String workingDirectory() {
        try {
            return Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize().toString();
        } catch (RuntimeException e) {
            return System.getProperty("user.dir", ".");
        }
    }
}
