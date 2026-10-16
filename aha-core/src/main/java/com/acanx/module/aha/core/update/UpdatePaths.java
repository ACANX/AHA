package com.acanx.module.aha.core.update;

import java.nio.file.Path;

/**
 * 升级相关的目录布局：一切都在 {@code <AHA_HOME>/update/} 之下（issue #51 §4.2 / §4.4 / §4.5）。
 *
 * <p>布局（`&lt;根&gt;` = `~/.aha/update`）：</p>
 *
 * <pre>
 * &lt;根&gt;/&lt;版本&gt;/                    本次升级的暂存目录（下载的包 + 解压出来的新包）
 * &lt;根&gt;/&lt;版本&gt;/pkg.zip            下载的资产（先有 .part，校验通过才改名）
 * &lt;根&gt;/&lt;版本&gt;/new/              解压后的新包（落地器的来源）
 * &lt;根&gt;/backup/&lt;旧版本&gt;/          被替换下来的旧文件（含被删掉的文件）
 * &lt;根&gt;/update-plan.json          换装计划（交给 helper 的唯一入参）
 * &lt;根&gt;/update-result.json        helper 执行结果（下次启动读它决定静默还是弹窗）
 * &lt;根&gt;/update.log                追加式日志
 * </pre>
 *
 * <p><b>为什么不往安装目录写临时文件</b>：安装目录可能被占用（Windows 上正在运行的 exe / jar）
 * 或只读（解压到 Program Files 之类），而且改到一半失败会留下「半个安装」。暂存与备份都放在用户目录，
 * 安装目录里只在换装那一刻被改。</p>
 *
 * <p>家目录解析沿用仓库既有口径：环境变量 {@code AHA_HOME} → 系统属性 {@code AHA_HOME}
 * → {@code ~/.aha}（与 `ConfigLoader` / `SecretStores` 一致；系统属性在前是为了让测试与集成场景
 * 能确定性地覆盖）。测试一律用 {@link #at(Path)} 注入临时目录，不触碰真实家目录
 * （`TestingSpec.md` §5.1）。</p>
 *
 * @param root 升级根目录（{@code <AHA_HOME>/update}）
 * @since 0.2.0
 */
public record UpdatePaths(Path root) {

    /** 家目录下的子目录名。 */
    public static final String DIR_NAME = "update";

    /** 暂存目录里「解压后的新包」的子目录名。 */
    public static final String NEW_PACKAGE_DIR = "new";

    /** 备份根目录名。 */
    public static final String BACKUP_DIR = "backup";

    /** 换装计划文件名。 */
    public static final String PLAN_FILE = "update-plan.json";

    /** 换装结果文件名。 */
    public static final String RESULT_FILE = "update-result.json";

    /** 日志文件名。 */
    public static final String LOG_FILE = "update.log";

    /**
     * 默认布局：{@code <AHA_HOME>/update}。
     *
     * @return 路径布局
     */
    public static UpdatePaths defaultPaths() {
        return new UpdatePaths(ahaHome().resolve(DIR_NAME));
    }

    /**
     * 注入布局（测试与集成场景）。
     *
     * @param root 升级根目录
     * @return 路径布局
     */
    public static UpdatePaths at(Path root) {
        return new UpdatePaths(root);
    }

    /**
     * 解出 AHA 家目录。
     *
     * @return {@code AHA_HOME}（环境变量优先，其次系统属性），都没有则是 {@code ~/.aha}
     */
    public static Path ahaHome() {
        String fromEnv = System.getenv("AHA_HOME");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return Path.of(fromEnv);
        }
        String fromProperty = System.getProperty("AHA_HOME");
        if (fromProperty != null && !fromProperty.isBlank()) {
            return Path.of(fromProperty);
        }
        return Path.of(System.getProperty("user.home"), ".aha");
    }

    /**
     * 某个版本的暂存目录（{@code <根>/<版本>}）。
     *
     * @param version 目标版本
     * @return 暂存目录
     */
    public Path versionDir(AhaVersion version) {
        return root.resolve(version.value());
    }

    /**
     * 某个版本的下载落地路径（{@code <根>/<版本>/pkg.zip}）。
     *
     * @param version 目标版本
     * @return zip 路径
     */
    public Path packageZip(AhaVersion version) {
        return versionDir(version).resolve("pkg.zip");
    }

    /**
     * 某个版本的「解压后的新包」目录（{@code <根>/<版本>/new}）。
     *
     * @param version 目标版本
     * @return 新包根目录
     */
    public Path newPackageDir(AhaVersion version) {
        return versionDir(version).resolve(NEW_PACKAGE_DIR);
    }

    /**
     * 某个版本的备份目录（{@code <根>/backup/<版本>}）。{@code version} 是**被替换掉的旧版本**。
     *
     * @param version 被替换下来的版本
     * @return 备份目录
     */
    public Path backupDir(AhaVersion version) {
        return backupRoot().resolve(version.value());
    }

    /**
     * 备份根目录。
     *
     * @return {@code <根>/backup}
     */
    public Path backupRoot() {
        return root.resolve(BACKUP_DIR);
    }

    /**
     * 换装计划文件。
     *
     * @return {@code <根>/update-plan.json}
     */
    public Path planFile() {
        return root.resolve(PLAN_FILE);
    }

    /**
     * 换装结果文件。
     *
     * @return {@code <根>/update-result.json}
     */
    public Path resultFile() {
        return root.resolve(RESULT_FILE);
    }

    /**
     * 日志文件。
     *
     * @return {@code <根>/update.log}
     */
    public Path logFile() {
        return root.resolve(LOG_FILE);
    }
}
