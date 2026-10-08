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
    private static final String FALLBACK_VERSION = "0.1.1-dev";

    /** 注入失败时的标记，用于识别未过滤的占位符。 */
    private static final String PLACEHOLDER = "${";

    /** 不加 {@code AHA } 前缀的版本号，如 {@code 0.1.1}。 */
    private static final String VERSION = loadVersion();

    /** {@code AHA x.y.z} 形式的完整显示值。 */
    public static final String DISPLAY = "AHA " + VERSION;

    private AppVersion() {
    }

    /**
     * 取版本号（不含产品名前缀）。
     *
     * <p>供窗口标题、日志头、「关于」页等需要纯版本号的地方使用。</p>
     *
     * @return 版本号，如 {@code 0.1.1}
     */
    public static String version() {
        return VERSION;
    }

    /**
     * 读取注入的版本号。
     *
     * @return 版本号；资源缺失或未过滤时返回兜底值
     */
    private static String loadVersion() {
        try (InputStream in = AppVersion.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                return FALLBACK_VERSION;
            }
            Properties properties = new Properties();
            properties.load(in);
            String version = properties.getProperty("version");
            if (version == null || version.isBlank() || version.startsWith(PLACEHOLDER)) {
                return FALLBACK_VERSION;
            }
            return version;
        } catch (IOException e) {
            return FALLBACK_VERSION;
        }
    }
}
