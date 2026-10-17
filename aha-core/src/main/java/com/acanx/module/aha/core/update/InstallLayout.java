package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 安装根与「当前是哪种形态」的判定（issue #51 §2.3 / §4.4）。
 *
 * <p>安装根不靠用户配置，靠**运行期实情**推出来：</p>
 *
 * <ul>
 *   <li>JVM 便携包：单体 jar 所在目录（{@code aha-desktop.jar} 就在那儿，包是平铺的）；</li>
 *   <li>原生单文件：可执行文件所在目录（native-image 下没有 jar，code source 也不指向文件）。</li>
 * </ul>
 *
 * <p>形态判定是<b>硬约束</b>（§2.3）：JVM 包不能升到原生包、原生包不能升到 JVM 包。
 * 判定依据是「当前 code source 是不是 **jar**」，而不是「目录里有没有 jar」—— 后者在两者并存
 * （用户手边既有便携包又有 exe）的目录里会判错。</p>
 *
 * <p>【为什么必须是「jar」而不只是「普通文件」（2026-10-16 真机 bug）】原生镜像里 code source
 * **不是 null**，它指向**exe 自己**（`file:/…/AhaDesktopNative.exe`）。原先只判
 * `Files.isRegularFile`，于是「在 `Dist\` 里双击原生 exe（同目录恰好还有上次取来的
 * `aha-desktop.jar`）」就被判成 JVM 便携包 ⇒ 点「检查更新」去下 JVM 包，换完 exe 自己
 * 一个字节没变。现在两道保险：① GraalVM 的运行期属性 {@value #NATIVE_IMAGE_PROPERTY}
 * 说明「我在原生镜像里」；② code source 必须是 `.jar` 文件才算 JVM 形态。</p>
 *
 * @param root     安装根（绝对路径）
 * @param jarBased 是否 JVM 便携包形态（code source 是 jar）
 * @since 0.2.0
 */
public record InstallLayout(Path root, boolean jarBased) {

    /** JVM 便携包里的单体 jar 名（平铺在包根，无版本号）。 */
    public static final String DESKTOP_JAR = "aha-desktop.jar";

    /** POSIX 启动脚本名。 */
    public static final String POSIX_LAUNCHER = "AhaDesktop.sh";

    /** Windows 启动脚本名。 */
    public static final String WINDOWS_LAUNCHER = "AhaDesktop.bat";

    /** 原生单文件的基础名（Windows 上是 `<基础名>.exe`）。 */
    public static final String NATIVE_EXE_BASE = "aha-desktop-native";

    /**
     * GraalVM 原生镜像在运行期会设这个属性（值 {@code runtime}；构建期是 {@code buildtime}）。
     *
     * <p>用它判断「我在不在原生镜像里」是 GraalVM 的官方做法，且不依赖任何 SVM API
     * （JVM 构建里照样能编译）。</p>
     */
    public static final String NATIVE_IMAGE_PROPERTY = "org.graalvm.nativeimage.imagecode";

    /**
     * 探测当前运行的安装布局。
     *
     * <p>先看 code source（JVM 便携包会指向那个 jar）；拿不到（native-image 下为 {@code null}）
     * 再退回进程自身的可执行文件路径（{@link ProcessHandle#info()}）。两者都拿不到时抛
     * {@link UpdateException} —— 宁可明确报错，也不要猜一个目录出来乱改。</p>
     *
     * @return 安装布局
     * @throws UpdateException 推不出安装根
     */
    public static InstallLayout detect() {
        return detect(isNativeImage());
    }

    /**
     * 探测安装布局（「在不在原生镜像里」可注入，便于两端都测）。
     *
     * @param nativeImage 是否运行在原生镜像里
     * @return 安装布局
     * @throws UpdateException 推不出安装根
     */
    static InstallLayout detect(boolean nativeImage) {
        if (!nativeImage) {
            CodeSource source = InstallLayout.class.getProtectionDomain().getCodeSource();
            URL location = source == null ? null : source.getLocation();
            if (location != null) {
                try {
                    // `of` 只把 **.jar** 文件认成 JVM 便携包：原生镜像里 code source 指向 exe 自己，
                    // 判成 jar 形态就会去下 JVM 包（见类注释里的真机 bug）。
                    InstallLayout fromSource = of(Path.of(location.toURI()));
                    if (fromSource.jarBased()) {
                        return fromSource;
                    }
                } catch (Exception ignored) {
                    // 拿不到就继续走进程自身那条路
                }
            }
        }
        return ProcessHandle.current().info().command()
                .map(Path::of)
                .map(path -> path.toAbsolutePath().getParent())
                .map(parent -> new InstallLayout(parent, false))
                .orElseThrow(() -> new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                        "推不出安装根：既拿不到 code source，也拿不到进程自身的可执行文件路径"));
    }

    /**
     * 是否运行在 GraalVM 原生镜像里（{@value #NATIVE_IMAGE_PROPERTY} 非空）。
     *
     * @return 原生镜像返回 {@code true}
     */
    public static boolean isNativeImage() {
        return !System.getProperty(NATIVE_IMAGE_PROPERTY, "").isEmpty();
    }

    /**
     * 注入构造（测试）。
     *
     * @param codeSource 运行期 code source（jar 文件或目录）
     * @return 安装布局
     */
    public static InstallLayout of(Path codeSource) {
        Path absolute = codeSource.toAbsolutePath();
        // 只有 **.jar** 文件才算 JVM 便携包：原生镜像的 code source 指向 exe 自己，
        // 只判「是普通文件」会把 exe 认成 JVM 形态（2026-10-16 真机 bug）。
        if (Files.isRegularFile(absolute)
                && absolute.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            return new InstallLayout(absolute.getParent(), true);
        }
        return new InstallLayout(absolute, false);
    }

    /**
     * 单体 jar 路径。
     *
     * @return {@code <安装根>/aha-desktop.jar}
     */
    public Path jar() {
        return root.resolve(DESKTOP_JAR);
    }

    /**
     * 启动脚本（按平台优先：Windows 找 {@code .bat}，其余找 {@code .sh}）。
     *
     * @return 存在的启动脚本；找不到为空
     */
    public Optional<Path> launcher() {
        String preferred = isWindows() ? WINDOWS_LAUNCHER : POSIX_LAUNCHER;
        String fallback = isWindows() ? POSIX_LAUNCHER : WINDOWS_LAUNCHER;
        for (String name : new String[] {preferred, fallback}) {
            Path candidate = root.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * 原生单文件路径。
     *
     * @return {@code <安装根>/aha-desktop-native[.exe]}
     */
    public Path nativeExecutable() {
        String name = isWindows() ? NATIVE_EXE_BASE + ".exe" : NATIVE_EXE_BASE;
        return root.resolve(name);
    }

    /**
     * 换装后拉起 App 的命令（供 {@link UpdatePlan#restart()} 用）。
     *
     * <p>顺序：启动脚本优先（它是包的一部分，会带上正确的 JVM 参数），其次原生单文件；
     * 两者都没有（只剩一个单体 jar 的布局）时返回空 —— helper 会把「请手动启动应用」写进回执，
     * 界面则用 {@link UpdateLauncher#unsupportedReason(InstallLayout)} 提前拦住这种形态。</p>
     *
     * @return 命令列表；拿不出任何可用命令时为空
     */
    public List<String> restartCommand() {
        List<String> command = new ArrayList<>();
        Optional<Path> launcher = launcher();
        if (launcher.isPresent()) {
            command.add(launcher.get().toAbsolutePath().toString());
            return List.copyOf(command);
        }
        if (!jarBased) {
            Path executable = nativeExecutable();
            if (Files.isRegularFile(executable)) {
                command.add(executable.toAbsolutePath().toString());
                return List.copyOf(command);
            }
        }
        // 只有单体 jar 的布局：**不给兜底**，返回空。
        // 计划（`update-plan.json` 的 `restart`）的契约是「每个元素 = 一个可执行文件」——
        // helper 逐个 `Start-Process -FilePath` 拉起，而 `java -jar x.jar` 是「命令 + 参数」，
        // 塞进这个列表过不了 `UpdatePlan` 的绝对路径校验（`-jar` 不是绝对路径），
        // 计划根本写不出来（2026-10-16 第二轮审查 P1-①：上一轮改成
        // `<java.home>/bin/java` 只修了「第一个元素不绝对」，参数仍在列表里）。
        // 空列表是**合法**的：helper 会往回执里写「请手动启动应用」；
        // 而界面会用 {@link UpdateLauncher#unsupportedReason(InstallLayout)} 提前拦住这种形态，
        // 宁可不换，也不换完不回来。
        return List.copyOf(command);
    }

    /**
     * 是否 Windows。
     *
     * @return Windows 返回 {@code true}
     */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    /**
     * 入口是否存在（换装「自证」用：入口在）——
     * 启动脚本缺失不算失败：原生单文件形态不需要它。
     *
     * @throws UpdateException 入口缺失
     */
    public void requireEntryPoints() {
        if (jarBased && !Files.isRegularFile(jar())) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "安装根里没有 " + DESKTOP_JAR + "：" + root);
        }
        if (!jarBased && !Files.isRegularFile(nativeExecutable())) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "安装根里没有原生单文件：" + nativeExecutable());
        }
    }
}
