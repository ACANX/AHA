package com.acanx.module.aha.core.boot;

import com.acanx.module.aha.common.exception.Exceptions;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.config.ConfigLoader;
import com.acanx.module.aha.core.config.LoggingConfig;
import com.acanx.module.aha.core.logging.LoggingSetup;
import com.acanx.module.aha.core.security.SecretStore;
import com.acanx.module.aha.core.security.SecretStores;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 应用启动引导：读取主配置 → 装配日志 → 装密钥库回退源。
 *
 * <p>抽到 {@code aha-core} 的原因：CLI 与桌面端**都要**走这三步，而两者
 * <strong>不得互相依赖</strong>（{@code Constitution.md} 第 4 条的依赖矩阵）。
 * 各写一遍的代价不只是重复，更是**口径分叉**——桌面端曾长期用
 * {@code LoggingSetup.apply(null)}，于是 {@code Aha.Logging.*} 在桌面端完全失效。</p>
 *
 * <p>两类方法分工明确：</p>
 * <ul>
 *   <li>{@link #load(Path)}：纯解析，无副作用，便于测试与「先看配置再决定」；</li>
 *   <li>{@link #boot()} / {@link #boot(Path)}：在 {@code load} 之上装配日志、装密钥库
 *       ——这两步是**进程级**副作用（日志配置被替换、静态回退源被注册），故只应调用一次。</li>
 * </ul>
 *
 * <p>失败一律**不阻断启动**：配置读不到就退回内置默认并记一条 warning，
 * 由调用方决定怎么呈现（CLI 打到 stderr，桌面端记日志）。</p>
 *
 * @since 0.1.1
 */
public final class AhaBootstrap {

    private AhaBootstrap() {
    }

    /**
     * 引导结果。
     *
     * @param config     生效配置；仅当内置默认都读不到时才为 {@code null}
     * @param configFile 实际读取的配置文件；用内置默认时为 {@code null}
     * @param warnings   启动期警告（配置不可用、密钥库不可用等）
     */
    public record Result(AhaConfig config, Path configFile, List<String> warnings) {

        /**
         * 配置是否来自磁盘文件。
         *
         * @return 来自文件返回 {@code true}，用内置默认返回 {@code false}
         */
        public boolean fromFile() {
            return configFile != null;
        }

        /**
         * 日志配置段。
         *
         * @return 日志配置，可能为 {@code null}
         */
        public LoggingConfig logging() {
            return config == null ? null : config.logging();
        }

        /**
         * 生效的日志级别。
         *
         * <p>注意区分两件事：{@code AhaDefault.yaml} 的内置默认是 {@code DEBUG}（有意设计），
         * 而配置缺失或级别名非法时由 {@link LoggingSetup#resolveLevel} 兜底为 {@code INFO}。</p>
         *
         * @return 级别名，如 {@code DEBUG}
         */
        public String loggingLevel() {
            return LoggingSetup.resolveLevel(logging());
        }
    }

    /**
     * 按项目配置 {@code ./Aha.yaml} 引导（不存在时用内置默认）。
     *
     * @return 引导结果
     */
    public static Result boot() {
        return boot(Path.of(ConfigLoader.CONFIG_FILE_NAME));
    }

    /**
     * 按指定配置文件引导，并装配日志与密钥库回退源。
     *
     * @param configFile 主配置路径；不存在时使用内置默认
     * @return 引导结果
     */
    public static Result boot(Path configFile) {
        Objects.requireNonNull(configFile, "configFile");
        List<String> warnings = new ArrayList<>();
        Result loaded = load(configFile, warnings);
        LoggingSetup.apply(loaded.logging());
        installSecretResolver(loaded.config(), warnings);
        return new Result(loaded.config(), loaded.configFile(), List.copyOf(warnings));
    }

    /**
     * 只解析配置，不做任何装配（无副作用）。
     *
     * @param configFile 主配置路径；不存在时使用内置默认
     * @return 引导结果，{@code warnings} 含解析期问题
     */
    public static Result load(Path configFile) {
        Objects.requireNonNull(configFile, "configFile");
        List<String> warnings = new ArrayList<>();
        Result result = load(configFile, warnings);
        return new Result(result.config(), result.configFile(), List.copyOf(warnings));
    }

    private static Result load(Path configFile, List<String> warnings) {
        if (!Files.exists(configFile)) {
            return new Result(defaultConfig(warnings), null, warnings);
        }
        try {
            return new Result(ConfigLoader.load(configFile), configFile, warnings);
        } catch (RuntimeException e) {
            warnings.add("主配置不可用，已退回内置默认值：" + configFile
                    + "（" + Exceptions.message(e) + "）");
            return new Result(defaultConfig(warnings), null, warnings);
        }
    }

    private static AhaConfig defaultConfig(List<String> warnings) {
        try {
            return ConfigLoader.loadDefault();
        } catch (RuntimeException e) {
            // 连 classpath 里的 AhaDefault.yaml 都读不到：属打包事故，交给调用方降级处理
            warnings.add("内置默认配置不可用：" + Exceptions.message(e));
            return null;
        }
    }

    /**
     * 按 {@code Security.*} 创建密钥库并注册为占位符回退源。
     *
     * <p>密钥库不可用（如未设 {@code AHA_MASTER_PASSWORD}）时记 warning 并跳过，
     * 此时凭据只能来自环境变量；占位符未解析时保持原样，便于诊断。</p>
     *
     * @param config   配置，可为 {@code null}
     * @param warnings 警告收集器
     */
    private static void installSecretResolver(AhaConfig config, List<String> warnings) {
        try {
            SecretStore store = SecretStores.create(config);
            if (store != null) {
                ConfigLoader.setSecretResolver(store::retrieve);
            }
        } catch (RuntimeException e) {
            warnings.add("密钥库不可用: " + Exceptions.message(e));
        }
    }
}
