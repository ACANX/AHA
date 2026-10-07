package com.acanx.module.aha.core.logging;

import com.acanx.module.aha.core.config.ConfigLoader;
import com.acanx.module.aha.core.config.LoggingConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.ConfigurationSource;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日志系统初始化：按 {@code Aha.Logging.*} 程序化配置 Log4j2。
 *
 * <p>放在 {@code aha-core} 而不是入口模块：CLI 与桌面端都要配置日志，而两者
 * <strong>不得互相依赖</strong>。Log4j2 的实现（{@code log4j-core}）在本模块为
 * {@code compile} scope——本类要用它的 API；绑定实现（{@code log4j-slf4j2-impl}）
 * 仍由各入口模块以 {@code runtime} scope 提供。</p>
 *
 *
 * <p>{@code Aha.Logging.Level} 与 {@code Aha.Logging.File} 此前只是被读取展示，
 * 从未真正配置日志系统：项目内没有 log4j2 配置文件，log4j2 退回默认配置
 * （仅 console、ERROR 级），因此 {@code log/} 目录始终为空。</p>
 *
 * <p>此处按解析后的配置值程序化配置 log4j2。不使用 {@code log4j2.xml} 的原因：
 * JPMS 下 log4j2 通过 {@code ClassLoader.getResources} 查找配置文件，
 * 而该方法不搜索模块路径，配置文件可能不被发现。</p>
 *
 * <p>必须在首个 logger 创建之前调用。{@code ConfigLoader} 与 {@code CliContext}
 * 均不记录日志，因此在入口处调用是安全的。</p>
 *
 * @since 0.1.0
 */
public final class LoggingSetup {

    /**
     * 日志行格式。
     *
     * <p>使用完整 logger 名 + 方法名 + 行号，便于早期调试时定位调用点。
     * {@code %method} / {@code %line} 需要运行时获取调用者信息（遍历栈），
     * 有性能开销，已确认在高频路径上可接受。</p>
     */
    private static final String PATTERN =
            "%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%t] %logger.%method:%line - %msg%n";

    /** 单文件大小上限，超出即切分。 */
    private static final String MAX_SIZE = "10 MB";

    /**
     * 同一天最多保留的切分文件数。
     *
     * <p>取值必须**足够大**：{@code DefaultRolloverStrategy} 达到上限后会删除最旧的切分文件
     * 并**复用其序号**，序号就会出现「回到 01」而不是继续递增。想要序号只增不回绕，
     * 只能把上限设到实际用不满的高度。代价是历史文件**不会自动清理**。</p>
     */
    private static final String MAX_ROLLED_FILES = "1000";

    /**
     * 切分文件的命名模板。
     *
     * <p>{@code %d{yyyy-MM-dd}} 取**滚动发生时刻**的日期，{@code %02i} 为补零的序号
     * （01、02…，个位数补零需要写成 {@code %02i}，写成 {@code %i} 会得到 1、2…）。</p>
     */
    private static final String ROLL_PATTERN = "%d{yyyy-MM-dd}-%02i";

    /** 默认日志级别。 */
    private static final String DEFAULT_LEVEL = "INFO";

    /** Console appender 的固定级别：只输出 ERROR，避免 WARN 打断对话输出。 */
    private static final String CONSOLE_LEVEL = "ERROR";

    /** 默认日志文件名。 */
    private static final String LOG_FILE_NAME = "AHA.log";

    private static final AtomicBoolean APPLIED = new AtomicBoolean();

    private LoggingSetup() {
    }

    /**
     * 按配置初始化日志系统；重复调用只生效一次。
     *
     * @param logging 日志配置，可为 {@code null}
     */
    public static void apply(LoggingConfig logging) {
        if (!APPLIED.compareAndSet(false, true)) {
            return;
        }
        String level = resolveLevel(logging);
        Path file = resolveLogFile(logging);
        try {
            install((LoggerContext) LogManager.getContext(false), xml(level, file));
        } catch (RuntimeException | java.io.IOException e) {
            // 日志初始化失败不应阻断应用启动。
            // 此处刻意不用 slf4j：那会触发 log4j2 自举，正是本方法要解决的前提。
            System.err.println("[warn] 日志系统初始化失败，退回默认配置: " + e.getMessage());
        }
    }

    /**
     * 把配置应用到给定的 LoggerContext。
     *
     * <p>抽成独立方法是为了可测：{@link #apply(LoggingConfig)} 带一次性开关，
     * 单测无法重复触发，而「日志到底有没有真的写进文件」正是最需要钉住的行为
     * （曾经因为配置落到另一个 context 上，日志长期静默不落盘）。</p>
     *
     * @param context 目标 context，通常取自 {@code LogManager.getContext(false)}
     * @param xml     log4j2 XML 配置
     * @throws java.io.IOException 读取配置源失败（{@code ConfigurationSource} 构造即读流）
     */
    static void install(LoggerContext context, String xml) throws java.io.IOException {
        ConfigurationSource source = new ConfigurationSource(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        context.start(new XmlConfiguration(context, source));
    }

    /**
     * 生成 log4j2 配置。
     *
     * <p>Console appender 指向 stderr 且只输出 {@code ERROR} 及以上：stdout 专供对话内容，
     * INFO / WARN 日志若写入 stdout 会打断交互输入。级别取自 {@link #CONSOLE_LEVEL}。</p>
     *
     * @param level 级别
     * @param file  日志文件；为 {@code null} 时只保留 console appender
     * @return XML 配置
     */
    static String xml(String level, Path file) {
        return xml(level, file, MAX_SIZE, MAX_ROLLED_FILES);
    }

    /**
     * 生成 log4j2 配置（可指定切分参数）。
     *
     * @param level          日志级别
     * @param file           日志文件；为 {@code null} 时只保留 console appender
     * @param maxSize        单文件大小上限
     * @param maxRolledFiles 同日最多保留的切分文件数
     * @return XML 配置
     */
    static String xml(String level, Path file, String maxSize, String maxRolledFiles) {
        String console = """
                <Console name="Console" target="SYSTEM_ERR">
                    <PatternLayout pattern="%s"/>
                </Console>""".formatted(PATTERN);

        String fileAppender = "";
        String fileRef = "";
        if (file != null) {
            String target = escape(file.toString());
            fileAppender = """
                    <RollingFile name="File" fileName="%s" filePattern="%s">
                        <PatternLayout pattern="%s"/>
                        <Policies>
                            <SizeBasedTriggeringPolicy size="%s"/>
                        </Policies>
                        <DefaultRolloverStrategy max="%s"/>
                    </RollingFile>""".formatted(
                    target, escape(rolledPath(file)), PATTERN, maxSize, maxRolledFiles);
            fileRef = System.lineSeparator() + "        <AppenderRef ref=\"File\"/>";
        }

        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <Configuration status="WARN">
                    <Appenders>
                %s
                %s
                    </Appenders>
                    <Loggers>
                        <Root level="%s">%s
                            <AppenderRef ref="Console" level="%s"/>
                        </Root>
                    </Loggers>
                </Configuration>""".formatted(indent(console), indent(fileAppender), level, fileRef,
                CONSOLE_LEVEL);
    }

    /**
     * 解析日志级别。
     *
     * @param logging 日志配置
     * @return 级别名；无效时退回 INFO
     */
    static String resolveLevel(LoggingConfig logging) {
        String level = logging == null ? null : logging.level();
        if (level == null || level.isBlank()) {
            return DEFAULT_LEVEL;
        }
        return switch (level.trim().toUpperCase()) {
            case "TRACE", "DEBUG", "INFO", "WARN", "ERROR", "FATAL", "OFF" -> level.trim().toUpperCase();
            default -> DEFAULT_LEVEL;
        };
    }

    /**
     * 解析日志文件路径，并确保父目录存在。
     *
     * @param logging 日志配置
     * @return 绝对路径；未配置或无法创建目录时返回 {@code null}
     */
    static Path resolveLogFile(LoggingConfig logging) {
        String configured = logging == null ? null : logging.file();
        Path path;
        if (configured == null || configured.isBlank()) {
            String home = System.getenv("AHA_HOME");
            path = Path.of(home == null || home.isBlank() ? "." : home, "Log", LOG_FILE_NAME);
        } else {
            // 占位符已由 ConfigLoader.resolveEnv 解析，此处只需展开 ~
            path = ConfigLoader.expandHome(configured);
        }
        path = path.toAbsolutePath().normalize();
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return path;
        } catch (Exception e) {
            System.err.println("[warn] 无法创建日志目录，仅输出到控制台: " + path + " (" + e.getMessage() + ")");
            return null;
        }
    }

    /**
     * 由日志文件名推导切分文件的模板。
     *
     * <p>在扩展名之前插入 {@link #ROLL_PATTERN}，因此：
     * {@code AHA.log} → {@code AHA-%d{yyyy-MM-dd}-%02i.log}（即 {@code AHA-2026-10-07-01.log}）；
     * 无扩展名时直接追加（{@code AHA} → {@code AHA-%d{...}-%02i}）。
     * 切分文件与主文件同目录，且**不再压缩**——压缩包无法直接用编辑器看，
     * 排查问题时反而多一步解压。</p>
     *
     * @param file 主日志文件
     * @return 切分文件模板的字符串形式
     */
    static String rolledPath(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot <= 0 ? name : name.substring(0, dot);
        String ext = dot <= 0 ? "" : name.substring(dot);
        Path parent = file.getParent();
        String rolled = base + "-" + ROLL_PATTERN + ext;
        return parent == null ? rolled : parent.resolve(rolled).toString();
    }

    /**
     * 转义 XML 特殊字符。
     *
     * @param text 原始文本
     * @return 转义结果
     */
    static String escape(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * 为多行文本增加缩进，使生成的 XML 便于阅读。
     *
     * @param text 原始文本
     * @return 缩进后的文本
     */
    private static String indent(String text) {
        return text.lines()
                .filter(line -> !line.isBlank())
                .map(line -> "        " + line)
                .reduce((a, b) -> a + System.lineSeparator() + b)
                .orElse("");
    }
}
