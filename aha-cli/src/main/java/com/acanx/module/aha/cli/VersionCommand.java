package com.acanx.module.aha.cli;

import picocli.CommandLine.Command;

/**
 * 版本信息命令。
 *
 * @since 0.1.0
 */
@Command(name = "version", description = "版本信息", mixinStandardHelpOptions = true)
public final class VersionCommand implements Runnable {

    @Override
    public void run() {
        System.out.println(AppVersion.DISPLAY);
    }
}
