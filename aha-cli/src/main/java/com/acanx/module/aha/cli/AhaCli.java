package com.acanx.module.aha.cli;

import com.acanx.module.aha.common.exception.Exceptions;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.ConfigLoader;
import com.acanx.module.aha.core.security.SecretStore;
import com.acanx.module.aha.core.security.SecretStores;
import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * AHA 命令行主入口。
 *
 * @since 0.1.0
 */
@Command(
        name = "aha",
        mixinStandardHelpOptions = true,
        versionProvider = AppVersion.VersionProvider.class,
        description = "AHA - Agent Harness（无参数时进入交互式对话）",
        subcommands = {
                InitCommand.class,
                ChatCommand.class,
                RunCommand.class,
                ToolCommand.class,
                ProviderCommand.class,
                ExtensionCommand.class,
                ConfigCommand.class,
                SecretCommand.class,
                VersionCommand.class
        })
public final class AhaCli implements Runnable {

    /**
     * 程序入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        // 日志配置要尽早生效，故放在 picocli 之前。
        // 注意：ConfigLoader 自身有 INFO 日志，读配置时 log4j2 已按默认配置（仅 console、ERROR）
        // 初始化过一次——因此那几行「已加载 xx 配置」只出现在控制台，不会进日志文件。
        // 这不影响正确性：LoggingSetup 是**替换**当前 context 的配置，而不是假设它尚未创建。
        AhaConfig loaded = loadConfig();
        LoggingSetup.apply(loaded == null ? null : loaded.logging());
        // 密钥库必须在解析 ${ENV} 占位符之前就绪：
        // 环境变量未命中时需回退到加密密钥库（见 SecurityDesign.md 第 3 节）
        installSecretResolver(loaded);
        // 复用已加载的配置，避免 CliContext 再读一次（否则日志出现重复条目）
        CliContext.preload(loaded);
        int exitCode = new CommandLine(new AhaCli()).execute(args);
        System.exit(exitCode);
    }

    /**
     * 按 {@code Security.*} 创建密钥库并注册为占位符回退源。
     *
     * <p>密钥库不可用（如未设 {@code AHA_MASTER_PASSWORD}）时静默跳过，
     * 此时凭据只能来自环境变量，占位符未解析时保持原样以便诊断。</p>
     *
     * @param config 配置，可为 {@code null}
     */
    private static void installSecretResolver(AhaConfig config) {
        try {
            SecretStore store = SecretStores.create(config);
            if (store != null) {
                ConfigLoader.setSecretResolver(store::retrieve);
            }
        } catch (RuntimeException e) {
            // 密钥库配置错误不应阻断启动，例如未知的 KeyStore 实现
            System.err.println("[warn] 密钥库不可用: " + Exceptions.message(e));
        }
    }

    /**
     * 读取主配置。
     *
     * <p>任何异常都退回 {@code null}，由调用方使用内置默认值，
     * 避免“配置读不到”反过来阻断 CLI 启动。</p>
     *
     * @return 配置，不可用时为 {@code null}
     */
    private static AhaConfig loadConfig() {
        try {
            Path path = Path.of(ConfigLoader.CONFIG_FILE_NAME);
            return Files.exists(path) ? ConfigLoader.load(path) : ConfigLoader.loadDefault();
        } catch (RuntimeException e) {
            return null;
        }
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
        CommandLine.usage(this, System.out);
    }
}
