package com.acanx.module.aha.core.boot;

import com.acanx.module.aha.core.config.ConfigLoader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
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
     * 唯一允许调用 {@code boot} 的用例：断言配置里的级别**真的**成了 log4j2 的生效级别。
     *
     * <p>这正是 {@code D-11} 的验收标准在无图形环境下的可执行版本。</p>
     */
    @Test
    void appliesLoggingLevelFromConfig() throws IOException {
        Path logDir = Files.createDirectories(tempDir.resolve("Log"));
        Path config = writeConfig("""
                Aha:
                  Logging:
                    Level: WARN
                    File: '%s'
                  Agent:
                    SystemPrompt: 来自测试配置
                """.formatted(path(logDir.resolve("AHA.log"))));

        AhaBootstrap.Result result = AhaBootstrap.boot(config);

        assertThat(result.fromFile()).isTrue();
        assertThat(result.configFile()).isEqualTo(config);
        assertThat(result.config().logging().level()).isEqualTo("WARN");
        assertThat(result.config().agent().systemPrompt()).isEqualTo("来自测试配置");
        assertThat(result.loggingLevel()).isEqualTo("WARN");
        assertThat(result.warnings()).isEmpty();
        // 真的装进去了，而不只是「解析出来了」
        assertThat(appliedRootLevel()).isEqualTo(Level.WARN);
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
