package com.acanx.module.aha.cli;

import com.acanx.module.aha.common.AppVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CliVersionProvider} 测试：picocli 侧透出的是同一份版本号。
 *
 * @since 0.1.0
 */
class CliVersionProviderTest {

    @Test
    void providerReturnsAppVersion() {
        assertThat(new CliVersionProvider().getVersion())
                .containsExactly(AppVersion.DISPLAY);
    }
}
