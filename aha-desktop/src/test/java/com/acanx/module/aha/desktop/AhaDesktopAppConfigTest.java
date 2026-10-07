package com.acanx.module.aha.desktop;

import com.acanx.module.aha.core.boot.AhaBootstrap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 桌面端配置引导测试（{@code D-11}）：桌面端真的会读 {@code Aha.yaml}。
 *
 * <p>不需要 JavaFX 工具箱——{@code bootConfig} 与 {@code configSummary} 都不碰界面，
 * 因此这条验收在无图形环境的机器上也能跑。</p>
 *
 * <p>注意：{@code bootConfig} 会替换进程级日志配置，故本模块的 surefire 配了
 * {@code reuseForks=false}，让每个测试类各用独立 JVM，避免影响其它用例的输出。</p>
 *
 * @since 0.1.1
 */
class AhaDesktopAppConfigTest {

    @TempDir
    Path tempDir;

    @Test
    void bootReadsConfigAndSummaryShowsLevel() throws IOException {
        Files.createDirectories(tempDir.resolve("Log"));
        Path config = tempDir.resolve("Aha.yaml");
        Files.writeString(config, """
                Aha:
                  Logging:
                    Level: WARN
                    File: '%s'
                """.formatted(tempDir.resolve("Log/AHA.log").toString().replace('\\', '/')),
                StandardCharsets.UTF_8);

        AhaDesktopApp app = new AhaDesktopApp();
        // 未引导前不装作知道配置
        assertThat(app.configSummary()).isEqualTo("配置：尚未加载");

        AhaBootstrap.Result result = app.bootConfig(config);

        assertThat(result.fromFile()).isTrue();
        assertThat(result.loggingLevel()).isEqualTo("WARN");
        assertThat(app.configSummary()).contains(config.toString()).contains("WARN");
    }

    @Test
    void summaryReportsBuiltInDefaultWhenProjectConfigMissing() {
        AhaDesktopApp app = new AhaDesktopApp();

        AhaBootstrap.Result result = app.bootConfig(tempDir.resolve("nope.yaml"));

        assertThat(result.fromFile()).isFalse();
        assertThat(app.configSummary()).contains("内置默认").contains("DEBUG");
    }
}
