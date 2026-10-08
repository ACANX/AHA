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
        Path config = tempDir.resolve("Aha.yaml");
        Files.writeString(config, """
                Aha:
                  Logging:
                    Level: WARN
                    File: '%s'
                """.formatted(logFileInTarget("AhaDesktopAppConfigTest-AHA.log")),
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

    /**
     * 取模块 {@code target} 下的日志文件路径（正斜杠，YAML 里免转义）。
     *
     * <p>刻意不放 {@code @TempDir}：log4j2 会把文件句柄留到 JVM 结束，而 Windows 不允许
     * 删除仍被打开的文件——测试通过后 JUnit 清理临时目录会失败。这正是本用例第一次在
     * Windows CI 上挂掉的原因（Linux / macOS 允许删除已打开的文件，本机看不出来）。
     * 改为写进模块的 {@code target/test-logs/}：{@code mvn clean} 会清理，JUnit 不会去删。</p>
     *
     * @param name 文件名
     * @return 正斜杠形式的绝对路径
     */
    private static String logFileInTarget(String name) throws IOException {
        Path dir = Path.of(System.getProperty("basedir", System.getProperty("user.dir")),
                "target", "test-logs");
        Files.createDirectories(dir);
        return dir.resolve(name).toString().replace('\\', '/');
    }
}
