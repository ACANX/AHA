package com.acanx.module.aha.cli;

import com.acanx.module.aha.core.boot.AhaBootstrap;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Help;

/**
 * AHA 命令行主入口。
 *
 * @since 0.1.0
 */
@Command(
        name = "aha",
        mixinStandardHelpOptions = true,
        versionProvider = CliVersionProvider.class,
        description = "AHA - Agent Harness（无参数时进入交互式对话）",
        subcommands = {
                InitCommand.class,
                ChatCommand.class,
                RunCommand.class,
                ToolCommand.class,
                ProviderCommand.class,
                ModelCommand.class,
                ExtensionCommand.class,
                ConfigCommand.class,
                SecretCommand.class,
                VersionCommand.class
        })
public final class AhaCli implements Runnable {

    /**
     * 构造 CLI（{@code main} 与测试共用），套用统一 ANSI 策略。
     *
     * @return 已配置的 {@link CommandLine}
     */
    public static CommandLine commandLine() {
        return commandLine(new AhaCli());
    }

    /**
     * 为任意命令构造 {@link CommandLine}，套用统一 ANSI 策略。
     *
     * @param command 命令对象（主命令或子命令）
     * @return 已配置的 {@link CommandLine}
     */
    public static CommandLine commandLine(Object command) {
        return new CommandLine(command).setColorScheme(Help.defaultColorScheme(ansi()));
    }

    /**
     * 当前终端的 ANSI 能力。
     *
     * <p>picocli 默认的 {@code Ansi.AUTO} 把 Windows 一律当作支持 ANSI，
     * 于是 {@code aha --help > help.txt} 也会把转义序列写进文件；
     * 这里统一以 {@link System#console()} 判定，非交互场景一律关闭。</p>
     *
     * @return {@link Help.Ansi#ON} 或 {@link Help.Ansi#OFF}
     */
    public static Help.Ansi ansi() {
        return System.console() != null ? Help.Ansi.ON : Help.Ansi.OFF;
    }

    /**
     * 程序入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 读配置 → 装配日志 → 装密钥库回退源：与桌面端共用同一份引导（AhaBootstrap）。
        // 日志要尽早生效，故放在 picocli 之前。
        // 注意：ConfigLoader 自身有 INFO 日志，读配置时 log4j2 已按默认配置（仅 console、ERROR）
        // 初始化过一次——因此那几行「已加载 xx 配置」只出现在控制台，不会进日志文件。
        // 这不影响正确性：引导里的日志装配是**替换**当前 context 的配置，而不是假设它尚未创建。
        AhaBootstrap.Result boot = AhaBootstrap.boot();
        // 密钥库在解析 ${ENV} 占位符之前就已由引导装好（见 SecurityDesign.md 第 3 节）；
        // 失败只降级为警告，不阻断启动
        boot.warnings().forEach(warning -> System.err.println("[warn] " + warning));
        // 复用已加载的配置，避免 CliContext 再读一次（否则日志出现重复条目）
        CliContext.preload(boot.config());
        int exitCode = commandLine().execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // 无参数时的默认行为：
        //   - 交互式终端：直接进入对话，等价于 `aha chat`
        //   - 非交互（管道 / 脚本 / CI）：打印帮助，避免挂起等待输入
        // 与 node / python / claude / aider 等 CLI 的惯例一致。
        if (System.console() != null) {
            new ChatCommand().run();
            return;
        }
        commandLine(this).usage(System.out);
    }
}
