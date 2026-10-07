package com.acanx.module.aha.cli;

import com.acanx.module.aha.core.config.LoggingConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * {@link LoggingSetup} 测试。
 *
 * <p>只覆盖纯函数（级别与路径解析）。{@code apply} 会写全局 log4j2 配置，
 * 由端到端手工验收覆盖。</p>
 *
 * @since 0.1.0
 */
class LoggingSetupTest {

    @Test
    void defaultsToInfoWhenUnset() {
        assertThat(LoggingSetup.resolveLevel(null)).isEqualTo("INFO");
        assertThat(LoggingSetup.resolveLevel(new LoggingConfig(null, null))).isEqualTo("INFO");
        assertThat(LoggingSetup.resolveLevel(new LoggingConfig("   ", null))).isEqualTo("INFO");
    }

    @Test
    void normalisesLevelCase() {
        assertThat(LoggingSetup.resolveLevel(new LoggingConfig("debug", null))).isEqualTo("DEBUG");
        assertThat(LoggingSetup.resolveLevel(new LoggingConfig("Warn", null))).isEqualTo("WARN");
    }

    @Test
    void fallsBackToInfoOnInvalidLevel() {
        // 非法级别不能导致日志系统配置失败
        assertThat(LoggingSetup.resolveLevel(new LoggingConfig("VERBOSE", null))).isEqualTo("INFO");
        assertThat(LoggingSetup.resolveLevel(new LoggingConfig("12345", null))).isEqualTo("INFO");
    }

    @Test
    void expandsTildeInConfiguredPath() {
        Path resolved = LoggingSetup.resolveLogFile(new LoggingConfig(null, "~/custom/aha.log"));

        assertThat(resolved.isAbsolute()).isTrue();
        assertThat(resolved.toString()).doesNotContain("~");
        assertThat(resolved.getFileName().toString()).isEqualTo("aha.log");
    }

    @Test
    void resolvesConfiguredPathToAbsolute() {
        Path resolved = LoggingSetup.resolveLogFile(new LoggingConfig(null, "./log/x.log"));

        assertThat(resolved.isAbsolute()).isTrue();
        assertThat(resolved.getFileName().toString()).isEqualTo("x.log");
    }

    @Test
    void buildConfigWithFileContainsRollingFileAppender() {
        String xml = LoggingSetup.xml("DEBUG", Path.of("/tmp/aha.log"));

        assertThat(xml).contains("<RollingFile name=\"File\"");
        assertThat(xml).contains("fileName=\"/tmp/aha.log\"");
        // 切分文件命名契约：<基名>-yyyy-MM-dd-NN.log，序号补零，且不压缩
        assertThat(xml).contains("filePattern=\"/tmp/aha-%d{yyyy-MM-dd}-%02i.log\"");
        assertThat(xml).doesNotContain(".gz");
        assertThat(xml).contains("<Root level=\"DEBUG\">");
        // 滚动策略必须完整，否则文件会无限增长
        assertThat(xml).contains("SizeBasedTriggeringPolicy");
        assertThat(xml).contains("DefaultRolloverStrategy");
        // console 只输出 ERROR 以免打断交互
        assertThat(xml).contains("<AppenderRef ref=\"Console\" level=\"ERROR\"/>");
    }

    @Test
    void buildConfigWithoutFileOmitsFileAppender() {
        // 日志目录不可创建时应退化为仅 console，而不是生成非法 XML
        String xml = LoggingSetup.xml("INFO", null);

        assertThat(xml).doesNotContain("RollingFile");
        assertThat(xml).doesNotContain("AppenderRef ref=\"File\"");
        assertThat(xml).contains("<Root level=\"INFO\">");
    }

    @Test
    void logFormatIncludesFullLoggerMethodAndLine() {
        // 早期用 %logger{1.}（每包首字母缩写）无法定位调用点，
        // 调试期需要完整类名 + 方法名 + 行号
        String xml = LoggingSetup.xml("INFO", Path.of("/tmp/aha.log"));

        assertThat(xml).contains("%logger.%method:%line");
        assertThat(xml).doesNotContain("%logger{1.}");
    }

    @Test
    void escapesXmlSpecialCharacters() {
        assertThat(LoggingSetup.escape("a&b<c>d\"e"))
                .isEqualTo("a&amp;b&lt;c&gt;d&quot;e");
    }

    @Test
    void escapesSpecialCharactersInPath() {
        String xml = LoggingSetup.xml("INFO", Path.of("/tmp/a&b/c<d>.log"));

        assertThat(xml).contains("/tmp/a&amp;b/c&lt;d&gt;.log");
        assertThat(xml).doesNotContain("/tmp/a&b");
    }

    /**
     * 日志必须真的写进文件。
     *
     * <p>本测试盯的是一个真实踩过的坑：{@code apply} 曾经调用
     * {@code Configurator.initialize(null, source)}，该调用被编译到
     * {@code initialize(ClassLoader, ConfigurationSource)} 重载上，配置落到了
     * 「按类加载器查到的」另一个 LoggerContext，于是表面无异常、日志永久不落盘。
     * 单测此前只验 {@code xml} 字符串，完全覆盖不到这一点。</p>
     *
     * <p>刻意不用 {@code @TempDir}：log4j2 会一直持有文件句柄，Windows 上删除会失败，
     * 让测试变得不稳定。写在 {@code target/} 下由 {@code clean} 清理。</p>
     */
    @Test
    void installWritesLogFileToDisk() throws Exception {
        Path logFile = Path.of("target", "logging-test", "AHA.log").toAbsolutePath();
        Files.createDirectories(logFile.getParent());
        Files.deleteIfExists(logFile);

        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        LoggingSetup.install(context, LoggingSetup.xml("DEBUG", logFile));
        Logger log = LogManager.getLogger("logging-setup-test");
        log.info("落盘标记 {}", "MARKER-9F3");

        String content = null;
        for (int i = 0; i < 50 && content == null; i++) {
            Thread.sleep(100);
            if (Files.exists(logFile)) {
                String text = Files.readString(logFile);
                if (text.contains("MARKER-9F3")) {
                    content = text;
                }
            }
        }

        assertThat(content)
                .as("日志必须真的落到 %s", logFile)
                .isNotNull()
                .contains("MARKER-9F3");
    }

    /**
     * 切分文件命名：在扩展名前插入日期与补零序号。
     */
    @Test
    void rolledPathInsertsDateAndPaddedIndexBeforeExtension() {
        assertThat(LoggingSetup.rolledPath(Path.of("/home/x/.aha/Log/AHA.log")))
                .isEqualTo("/home/x/.aha/Log/AHA-%d{yyyy-MM-dd}-%02i.log");
        // 无扩展名时直接追加
        assertThat(LoggingSetup.rolledPath(Path.of("/tmp/AHA")))
                .isEqualTo("/tmp/AHA-%d{yyyy-MM-dd}-%02i");
        // 其它扩展名同样保留
        assertThat(LoggingSetup.rolledPath(Path.of("/tmp/x.txt")))
                .isEqualTo("/tmp/x-%d{yyyy-MM-dd}-%02i.txt");
    }

    /**
     * 切分文件必须真的按 {@code <基名>-yyyy-MM-dd-NN.log} 生成，序号从 01 开始递增。
     *
     * <p>用 2 KB 阈值触发真实滚动——只断言 XML 字符串是不够的，
     * 「序号有没有补零、会不会回绕」只有真滚一次才看得出来。</p>
     */
    @Test
    void rollsIntoNamesWithPaddedIncrementingIndex() throws Exception {
        Path dir = Path.of("target", "logging-rotate").toAbsolutePath();
        Files.createDirectories(dir);
        try (var existing = Files.list(dir)) {
            existing.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // 句柄可能仍被上一个测试持有的 appender 占着，忽略
                }
            });
        }

        Path logFile = dir.resolve("AHA.log");
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        LoggingSetup.install(context, LoggingSetup.xml("DEBUG", logFile, "2 KB", "1000"));

        Logger log = LogManager.getLogger("logging-rotate-test");
        String today = java.time.LocalDate.now().toString();
        for (int i = 0; i < 300; i++) {
            log.info("填充 {} {}", i, "x".repeat(60));
        }

        Path first = dir.resolve("AHA-" + today + "-01.log");
        for (int i = 0; i < 50 && !Files.exists(first); i++) {
            Thread.sleep(100);
        }

        try (var list = Files.list(dir)) {
            List<String> names = list.map(p -> p.getFileName().toString()).sorted().toList();
            assertThat(names)
                    .as("切分文件应形如 AHA-yyyy-MM-dd-NN.log 且从 01 开始")
                    .contains("AHA-" + today + "-01.log")
                    .contains("AHA-" + today + "-02.log")
                    .allSatisfy(n -> assertThat(n).matches("AHA(-" + today + "-\\d{2})?\\.log"));
        }
    }
}
