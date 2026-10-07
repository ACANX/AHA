package com.acanx.module.aha.cli.render;

import com.acanx.module.aha.common.runtime.Platform;

/**
 * 工具执行环境：把「命令在哪个终端、哪个目录里跑」如实还原出来。
 *
 * <p>用户排查问题时第一个要问的就是「它到底在哪儿、用什么跑的」，而这在输出里
 * 往往是缺失的——只看到一条报错，不知道是 Windows 的 cmd 还是 Linux 的 sh，
 * 也不知道相对路径是相对谁。</p>
 *
 * @param shell     命令解释器（如 {@code cmd.exe /c}）
 * @param directory 执行目录（进程工作目录，不是项目根）
 * @since 0.1.0
 */
public record ToolEnvironment(String shell, String directory) {

    /**
     * 当前真实环境。
     *
     * @return 环境
     */
    public static ToolEnvironment current() {
        return new ToolEnvironment(Platform.shellCommand(), Platform.workingDirectory());
    }

    /**
     * 空环境（测试用）。
     *
     * @return 环境
     */
    public static ToolEnvironment none() {
        return new ToolEnvironment(null, null);
    }

    /**
     * 终端提示符：目录 + 提示符字符。
     *
     * <p>命令行的展示要像在终端里敲下去的样子，所以前缀是 {@code <目录>> }（Windows）
     * 或 {@code <目录>$ }（POSIX）。提示符字符本身就是平台信号——用户看到
     * {@code E:\repo> powershell ...} 立刻知道这是在 cmd 里跑的。</p>
     *
     * @return 提示符；环境未知时返回空串
     */
    public String prompt() {
        String dir = directory == null ? "" : directory;
        if (dir.isEmpty() && shell == null) {
            return "";
        }
        boolean windows = shell != null && shell.toLowerCase(java.util.Locale.ROOT).contains("cmd");
        return dir + (windows ? "> " : "$ ");
    }
}
