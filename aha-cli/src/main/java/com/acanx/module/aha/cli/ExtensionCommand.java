package com.acanx.module.aha.cli;

import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * 扩展管理命令（扩展运行时自 0.3 起提供）。
 *
 * @since 0.1.0
 */
@Command(
        name = "extension",
        description = "扩展管理",
        subcommands = {
                ExtensionCommand.ListSub.class,
                ExtensionCommand.EnableSub.class,
                ExtensionCommand.DisableSub.class,
                ExtensionCommand.InfoSub.class},
        mixinStandardHelpOptions = true)
public final class ExtensionCommand implements Runnable {

    private static final String NOT_AVAILABLE = "扩展运行时自 AHA 0.3 起提供";

    @Override
    public void run() {
        System.out.println("用法: aha extension [list|enable|disable|info]");
    }

    /**
     * 列出扩展。
     *
     * @since 0.1.0
     */
    @Command(name = "list", description = "列出扩展", mixinStandardHelpOptions = true)
    public static final class ListSub implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println(NOT_AVAILABLE);
            return 0;
        }
    }

    /**
     * 启用扩展。
     *
     * @since 0.1.0
     */
    @Command(name = "enable", description = "启用扩展", mixinStandardHelpOptions = true)
    public static final class EnableSub implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println(NOT_AVAILABLE);
            return 0;
        }
    }

    /**
     * 禁用扩展。
     *
     * @since 0.1.0
     */
    @Command(name = "disable", description = "禁用扩展", mixinStandardHelpOptions = true)
    public static final class DisableSub implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println(NOT_AVAILABLE);
            return 0;
        }
    }

    /**
     * 查看扩展信息。
     *
     * @since 0.1.0
     */
    @Command(name = "info", description = "查看扩展信息", mixinStandardHelpOptions = true)
    public static final class InfoSub implements Callable<Integer> {
        @Override
        public Integer call() {
            System.out.println(NOT_AVAILABLE);
            return 0;
        }
    }
}
