package com.acanx.module.aha.core.boot;

import com.acanx.module.aha.core.config.ConfigLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.slf4j.LoggerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AhaBootstrap} 测试。
 *
 * <p>两类方法分开测：{@code load} 无副作用，可放心多测；{@code boot} 会替换进程级
 * 日志配置（{@code LoggingSetup.apply} 带一次性开关），因此**全 JVM 只用一个用例**
 * 断言「级别真的装进 log4j2 了」——见 {@link #appliesLoggingLevelFromConfig}。</p>
 *
 * @since 0.1.1
 */
class AhaBootstrapTest {

    @TempDir
    Path tempDir;

    /**
     * 唯一允许调用 {@code boot} 的用例：断言配置里的级别**真的**成了 log4j2 的生效级别，
     * 并且**真的写到了配置指定的文件**。
     *
     * <p>这正是 {@code D-11} 的验收标准在无图形环境下的可执行版本。</p>
     *
     * <p>日志文件刻意不放 {@code @TempDir}：log4j2 会把文件句柄留到 JVM 结束，
     * 而 Windows 不允许删除仍被打开的文件——测试通过后 JUnit 清理临时目录会失败。
     * 这正是本用例第一次在 Windows CI 上挂掉的原因（Linux / macOS 允许删除打开的
     * 文件，所以本机看不出来）。改为写进模块的 {@code target/test-logs/}：
     * {@code mvn clean} 会清理，JUnit 不会去删。</p>
     */
    @Test
    void appliesLoggingLevelFromConfig() throws IOException {
        Path logFile = logFileInTarget("AhaBootstrapTest-AHA.log");
        Path config = writeConfig("""
                Aha:
                  Logging:
                    Level: WARN
                    File: '%s'
                  Agent:
                    SystemPrompt: 来自测试配置
                """.formatted(path(logFile)));

        AhaBootstrap.Result result = AhaBootstrap.boot(config);

        assertThat(result.fromFile()).isTrue();
        assertThat(result.configFile()).isEqualTo(config);
        assertThat(result.config().logging().level()).isEqualTo("WARN");
        assertThat(result.config().agent().systemPrompt()).isEqualTo("来自测试配置");
        assertThat(result.loggingLevel()).isEqualTo("WARN");
        assertThat(result.warnings()).isEmpty();
        // 真的装进去了，而不只是「解析出来了」
        assertThat(appliedRootLevel()).isEqualTo(Level.WARN);

        // 级别生效的旁证：WARN 会写、INFO 不会——只有配置真的生效才会出现这一行
        LoggerFactory.getLogger(AhaBootstrapTest.class).warn("引导生效探针");
        assertThat(logFile).exists();
        assertThat(Files.readString(logFile, StandardCharsets.UTF_8)).contains("引导生效探针");
    }

    @Test
    void missingConfigFallsBackToBuiltInDefaults() {
        AhaBootstrap.Result result = AhaBootstrap.load(tempDir.resolve("nope.yaml"));

        assertThat(result.fromFile()).isFalse();
        assertThat(result.configFile()).isNull();
        assertThat(result.config()).isNotNull();
        // AhaDefault.yaml 的内置默认是有意设计成 DEBUG 的
        assertThat(result.loggingLevel()).isEqualTo("DEBUG");
        // 文件不存在不是错误，不该刷警告
        assertThat(result.warnings()).isEmpty();
    }

    @Test
    void projectConfigIsLookedUpRelativeToWorkingDirectory() {
        // 权威口径：Aha.yaml 是**项目级**配置（./Aha.yaml），缺省才用 classpath 的 AhaDefault.yaml
        AhaBootstrap.Result result = AhaBootstrap.load(Path.of(ConfigLoader.CONFIG_FILE_NAME));

        assertThat(result.config()).isNotNull();
        // 测试运行时模块目录下没有 Aha.yaml
        assertThat(result.fromFile()).isFalse();
    }

    @Test
    void brokenConfigWarnsAndFallsBackToDefaults() throws IOException {
        Path config = writeConfig("Aha: [这不是映射\n");

        AhaBootstrap.Result result = AhaBootstrap.load(config);

        assertThat(result.config()).isNotNull();
        assertThat(result.fromFile()).isFalse();
        assertThat(result.warnings()).hasSize(1);
        assertThat(result.warnings().get(0)).contains("退回内置默认");
    }

    /**
     * 取模块 {@code target} 下的日志文件路径。
     *
     * <p>不放系统临时目录：log4j2 会一直持有文件句柄，Windows 上会因此让
     * {@code @TempDir} 的清理失败（见 {@link #appliesLoggingLevelFromConfig}）。</p>
     *
     * @param name 文件名
     * @return 绝对路径
     */
    private static Path logFileInTarget(String name) throws IOException {
        Path dir = Path.of(System.getProperty("basedir", System.getProperty("user.dir")),
                "target", "test-logs");
        Files.createDirectories(dir);
        return dir.resolve(name);
    }

    private static Level appliedRootLevel() {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        return context.getConfiguration().getRootLogger().getLevel();
    }

    private Path writeConfig(String content) throws IOException {
        Path file = tempDir.resolve("Aha.yaml");
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private static String path(Path path) {
        // YAML 里用正斜杠，避免 Windows 反斜杠被当转义
        return path.toString().replace('\\', '/');
    }
}
