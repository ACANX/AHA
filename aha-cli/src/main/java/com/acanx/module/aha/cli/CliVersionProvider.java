package com.acanx.module.aha.cli;

import com.acanx.module.aha.common.AppVersion;
import picocli.CommandLine;

/**
 * picocli 版本提供者。
 *
 * <p>{@code @Command(version = ...)} 只接受编译期常量，因此这里改用版本提供者，
 * 让构建期注入的值真正生效（{@code aha --version} 与 {@code aha -V}）。
 * 版本号本身由 {@link AppVersion} 提供——它放在 {@code aha-common}，
 * 因为桌面端也要用，且 cli 与 desktop 不得互相依赖。</p>
 *
 * @since 0.1.0
 */
public final class CliVersionProvider implements CommandLine.IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[] {AppVersion.DISPLAY};
    }
}
