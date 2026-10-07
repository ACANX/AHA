package com.acanx.module.aha.cli;

import com.acanx.module.aha.cli.session.ConfigView;
import com.acanx.module.aha.core.config.AhaConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.concurrent.Callable;

/**
 * 配置管理命令。
 *
 * @since 0.1.0
 */
@Command(
        name = "config",
        description = "配置管理",
        subcommands = {
                ConfigCommand.GetSub.class,
                ConfigCommand.SetSub.class,
                ConfigCommand.EditSub.class},
        mixinStandardHelpOptions = true)
public final class ConfigCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("用法: aha config [get|set|edit]");
    }

    /**
     * 读取配置。
     *
     * @since 0.1.0
     */
    @Command(name = "get", description = "读取配置", mixinStandardHelpOptions = true)
    public static final class GetSub implements Callable<Integer> {
        @Parameters(index = "0", description = "配置键（如 Llm.DefaultProvider）")
        String key;

        @Override
        public Integer call() {
            AhaConfig config = CliContext.config();
            System.out.println(ConfigView.resolve(config, key));
            return 0;
        }
    }

    /**
     * 设置配置（写入 ./Aha.yaml）。
     *
     * @since 0.1.0
     */
    @Command(name = "set", description = "设置配置", mixinStandardHelpOptions = true)
    public static final class SetSub implements Callable<Integer> {
        @Parameters(index = "0", description = "配置键")
        String key;
        @Parameters(index = "1", description = "配置值")
        String value;

        @Override
        public Integer call() {
            System.out.println("提示: 请直接编辑项目配置文件 " + CliContext.PROJECT_CONFIG
                    + " 以设置 " + key + " = " + value);
            return 0;
        }
    }

    /**
     * 编辑配置。
     *
     * @since 0.1.0
     */
    @Command(name = "edit", description = "编辑配置", mixinStandardHelpOptions = true)
    public static final class EditSub implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println("配置文件: " + java.nio.file.Path.of(CliContext.PROJECT_CONFIG).toAbsolutePath());
            return 0;
        }
    }

}

