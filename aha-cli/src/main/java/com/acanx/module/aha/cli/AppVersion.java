package com.acanx.module.aha.cli;

import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 版本号：由 Maven 在构建时注入，运行时读取。
 *
 * <p>此前版本号硬编码在两个地方（{@code @Command(version = ...)} 与 {@code VersionCommand}），
 * 发布时改 {@code pom.xml} 不会生效——发行包会报出旧版本。现在唯一来源是
 * {@code pom.xml}，经资源过滤写入 {@code version.properties}。</p>
 *
 * <p>写成资源而不是 Java 常量，是因为注解属性要求编译期常量，无法从文件取值；
 * 因此 picocli 侧改用 {@link VersionProvider}（运行时求值）。</p>
 *
 * @since 0.1.0
 */
final class AppVersion {

    /** 资源文件名，与本类同包。 */
    private static final String RESOURCE = "version.properties";

    /**
     * 取不到注入值时的兜底显示（如在 IDE 中直接运行、资源未过滤）。
     *
     * <p>这是全仓**唯一**一个写死的版本字面量：正常构建走资源过滤，取 POM 的
     * {@code <version>}（见 ReleaseProcess.md 第 2 节），故改版本号时除根 POM 与六
     * 个子 POM 的 {@code <parent>} 外，只此一处需要同步。</p>
     */
    private static final String FALLBACK = "AHA 0.1.1-dev";

    /** 注入失败时的标记，用于识别未过滤的占位符。 */
    private static final String PLACEHOLDER = "${";

    /** {@code AHA x.y.z} 形式的完整显示值。 */
    static final String DISPLAY = load();

    private AppVersion() {
    }

    /**
     * 读取注入的版本号。
     *
     * @return 显示值
     */
    private static String load() {
        try (InputStream in = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return FALLBACK;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank() || version.startsWith(PLACEHOLDER)) {
                return FALLBACK;
            }
            return "AHA " + version;
        } catch (IOException e) {
            return FALLBACK;
        }
    }

    /**
     * picocli 版本提供者。
     *
     * <p>{@code @Command(version = ...)} 只接受编译期常量，因此这里改用版本提供者，
     * 让构建期注入的值真正生效（{@code aha --version} 与 {@code aha -V}）。</p>
     *
     * @since 0.1.0
     */
    public static final class VersionProvider implements CommandLine.IVersionProvider {

        @Override
        public String[] getVersion() {
            return new String[] {DISPLAY};
        }
    }
}
