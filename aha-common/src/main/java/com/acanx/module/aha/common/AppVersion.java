package com.acanx.module.aha.common;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * 版本号：由 Maven 在构建时注入，运行时读取。
 *
 * <p>此前版本号硬编码在两处，发布时改 {@code pom.xml} 不生效——发行包会报出旧版本。
 * 现在唯一来源是 {@code pom.xml}，经资源过滤写入 {@code version.properties}。</p>
 *
 * <p>放在 {@code aha-common} 而不是入口模块：CLI 与桌面端都要显示版本，而两者
 * <strong>不得互相依赖</strong>（见 {@code Constitution.md} 第 4 条的依赖矩阵）。
 * 本类不依赖任何外部库，与 {@code aha-common} 的「零外部依赖」约定一致；
 * picocli 的注解属性要求编译期常量，那部分适配留在 CLI 侧（{@code CliVersionProvider}）。</p>
 *
 * <p><strong>两个层次</strong>（issue #46）：</p>
 * <ul>
 *   <li>{@link #version()}——基线版本，即 POM 的 {@code <version>}（如 {@code 0.1.1}）。
 *       正式发行版用它，不强制带构建号。</li>
 *   <li>{@link #buildVersion()}——构建版本。PR 合并到 {@code dev} 后自动构建的预发行包，
 *       由工作流注入构建号（形如 {@code 0.1.1.00046}，末段是该 PR 的编号）；正式构建不注入，
 *       此时与基线版本相同。查版本时优先用它，才能精确定位到是哪一次构建。</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class AppVersion {

    /** 资源文件名，与本类同包。 */
    private static final String RESOURCE = "version.properties";

    /**
     * 取不到注入值时的兜底版本（如在 IDE 中直接运行、资源未过滤）。
     *
     * <p>这是全仓**唯一**一个写死的版本字面量：正常构建走资源过滤取 POM 的
     * {@code <version>}（见 {@code ReleaseProcess.md} 第 2 节），故改版本号时除根 POM 与
     * 六个子 POM 的 {@code <parent>} 外，只此一处需要同步。</p>
     */
    private static final String FALLBACK_VERSION = "0.1.2-dev";

    /** 注入失败时的标记，用于识别未过滤的占位符。 */
    private static final String PLACEHOLDER = "${";

    /** 基线版本（不含构建号），如 {@code 0.1.1}。 */
    private static final String VERSION = load("version", FALLBACK_VERSION);

    /** 构建版本，如 {@code 0.1.1.00046}；未注入构建号时等于基线版本。 */
    private static final String BUILD = load("build", VERSION);

    /** {@code AHA x.y.z} 形式的完整显示值；预发行版会带上构建号。 */
    public static final String DISPLAY = "AHA " + BUILD;

    private AppVersion() {
    }

    /**
     * 基线版本（不含构建号）。
     *
     * <p>供窗口标题、日志头、「关于」页等需要纯版本号的地方使用。</p>
     *
     * @return 版本号，如 {@code 0.1.1}
     */
    public static String version() {
        return VERSION;
    }

    /**
     * 构建版本（预发行版带构建号）。
     *
     * <p>问题排查时应当显示这一个：它能区分「同一条 dev 分支上不同 PR 构建出来的包」。</p>
     *
     * @return 形如 {@code 0.1.1.00046}；正式构建时等于 {@link #version()}
     */
    public static String buildVersion() {
        return BUILD;
    }

    /**
     * 是否为带构建号的预发行版本。
     *
     * @return 构建号与基线版本不同时返回 {@code true}
     */
    public static boolean isPreRelease() {
        return !BUILD.equals(VERSION);
    }

    /**
     * 读取注入值。
     *
     * @param key      属性名
     * @param fallback 兜底值
     * @return 值；资源缺失、键缺失或未过滤时返回兜底值
     */
    private static String load(String key, String fallback) {
        try (InputStream in = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return fallback;
            }
            Properties properties = new Properties();
            properties.load(in);
            String value = properties.getProperty(key);
            if (value == null || value.isBlank() || value.startsWith(PLACEHOLDER)) {
                return fallback;
            }
            return value;
        } catch (IOException e) {
            return fallback;
        }
    }
}
