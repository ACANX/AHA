package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 拉起换装 helper：把「谁去替换文件」这件事交给一个**外部**进程（issue #51 §4.5）。
 *
 * <p>为什么必须外部：正在运行的进程不能可靠地替换自己的文件 —— Windows 上被使用的 {@code .exe}
 * / {@code .jar} 通常被占用；即便改名成功，也只剩「下次启动才生效」的半程。所以：</p>
 *
 * <ol>
 *   <li>先把 helper 两个文件从安装根复制到 {@code <AHA_HOME>/update/}（<b>不放安装根</b>：
 *       它用完就该消失，也不该出现在被替换的目录里）；换装后 `.ps1` 自己删自己，
 *       `.cmd` 是正在运行的批处理删不掉，由 App 下次启动的收尾一并删掉（见
 *       {@code UpdateService#onStartup}）；</li>
 *   <li>用脱离式命令拉起它（Windows {@code cmd /c start "" /b …}，POSIX {@code sh -c 'nohup … &'}）——
 *       与 App 无父子句柄依赖，App 退出不会带走它；</li>
 *   <li>App 自己退出（界面显示「正在替换并重启…」之后）。</li>
 * </ol>
 *
 * <p>helper 的唯一入参是计划文件路径；它不解析任何用户输入（防注入，§4.7）。</p>
 *
 * @since 0.2.0
 */
public final class UpdateLauncher {

    /** Windows 入口脚本名（ASCII + CRLF，由 helper 仓库侧模板随包分发）。 */
    public static final String WINDOWS_ENTRY = "apply-update.cmd";

    /** Windows 实现脚本名（PowerShell，UTF-8 BOM）。 */
    public static final String WINDOWS_SCRIPT = "AhaApplyUpdate.ps1";

    /** POSIX 入口脚本名（P4 后置实现；现在只在包里有模板时才会被复制）。 */
    public static final String POSIX_ENTRY = "apply-update.sh";

    private UpdateLauncher() {
    }

    /**
     * 把 helper 复制到 {@code <AHA_HOME>/update/}。
     *
     * @param installRoot 安装根（helper 模板随包分发在这里）
     * @param paths       路径布局
     * @return helper 所在目录（即 {@code <根>}）
     * @throws UpdateException 模板缺失或复制失败
     */
    public static Path installHelpers(Path installRoot, UpdatePaths paths) {
        return installHelpers(installRoot, installRoot, paths);
    }

    /**
     * 把 helper 装到 {@code <AHA_HOME>/update/}：**先看新包，再看安装根**。
     *
     * <p>【为什么要看新包（2026-10-16 真机实测）】helper 是**随包分发**的，所以新包里一定有；
     * 而「安装根」是**正在运行的那一版**，如果它是更早的安装（helper 还没随包分发之前打的包，
     * 或者手工摆的布局），安装根里就什么都没有 —— 只认安装根时，用户会看到「安装根里没有换装
     * helper（该包不完整，无法自更新）」，**把锅甩给一个其实完整的新包**，而且升级就此卡住。
     * 新包优先，正好让「从任意旧安装升级」都能走通。</p>
     *
     * @param installRoot    安装根（兜底来源）
     * @param newPackageRoot 新包解压后的根目录（首选来源）
     * @param paths          路径布局
     * @return helper 所在目录（即 {@code <根>}）
     * @throws UpdateException 两处都没有 helper，或复制失败
     */
    public static Path installHelpers(Path installRoot, Path newPackageRoot, UpdatePaths paths) {
        List<String> names = new ArrayList<>();
        if (InstallLayout.isWindows()) {
            names.add(WINDOWS_ENTRY);
            names.add(WINDOWS_SCRIPT);
        } else {
            names.add(POSIX_ENTRY);
        }
        try {
            Files.createDirectories(paths.root());
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "创建 helper 目录失败：" + paths.root() + "（" + e.getMessage() + "）", e);
        }
        for (String name : names) {
            Path fromPackage = newPackageRoot.resolve(name);
            Path fromInstall = installRoot.resolve(name);
            Path source = Files.isRegularFile(fromPackage) ? fromPackage
                    : (Files.isRegularFile(fromInstall) ? fromInstall : null);
            if (source == null) {
                String reason = unsupportedReason();
                throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                        reason == null
                                ? "换装脚本缺失：新包里没有 " + name + "（" + fromPackage
                                        + "），这台机器上的安装里也没有（" + fromInstall
                                        + "）—— 请到发布页下载完整包，手动覆盖安装一次；"
                                        + "之后的升级就能在应用内完成"
                                : "本平台暂不支持应用内自更新：" + reason);
            }
            Path target = paths.root().resolve(name);
            try {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                ensureExecutable(target);
            } catch (IOException e) {
                throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                        "复制 helper 失败：" + source + " → " + target + "（" + e.getMessage() + "）", e);
            }
        }
        return paths.root();
    }

    /**
     * 本平台的应用内自更新是否已经实现。
     *
     * <p>Windows 有实现（`apply-update.cmd` + `AhaApplyUpdate.ps1`）；POSIX 的换装脚本
     * 尚未实现（issue #51 §7 P4），因此现在**明确返回原因**，由界面提前拦住，
     * 而不是让用户下完包才看到「该包不完整」（那会把锅甩给包，2026-10-16 审查 P1-7）。</p>
     *
     * @return 不支持的原因；支持则返回 {@code null}
     */
    public static String unsupportedReason() {
        if (InstallLayout.isWindows()) {
            return null;
        }
        return "应用内自更新目前只在 Windows 上实现（换装脚本的 POSIX 版本尚未实现，issue #51 §7 P4）";
    }

    /**
     * 平台 + **安装形态**两层的能力门：任一不满足就返回原因，界面据此提前置灰。
     *
     * <p>形态这一层是「换装后能不能自己回来」：启动命令为空（安装根里既没有启动脚本、
     * 也没有原生单文件，只剩一个单体 jar）时，换装成功后得用户自己去点 —— 那比不换更差，
     * 所以直接拦在下载之前（2026-10-16 第二轮审查 P1-①）。</p>
     *
     * @param layout 当前安装布局
     * @return 不支持的原因；支持则返回 {@code null}
     */
    public static String unsupportedReason(InstallLayout layout) {
        // 【曾经在这里拦过「原生镜像」一层，2026-10-16 撤掉】
        // 当时的理由是「原生包不在升级通道里、形态切换不做」——实测发现**第一条是错的**：
        // 原生线本来就有自己的 tag（`V<版本>-aha-desktop-native`）与资产
        // （`AHA-Desktop-Native-<版本>-<平台>-jdk<JKV>.zip`），都在 `ChannelContract` 里，
        // 资产选择器也支持（平台 + 默认 jdk25）；而 `supports()` 只把「原生包 + Stable 通道」
        // 这一种组合排除在外（原生线还没发正式版）。换装与重启机制与 JVM 同构：
        // 包内清单 + helper 随包分发（`dist-native.xml`）+ `restartCommand()` 返回 exe。
        // ⇒ 真正缺的只是**形态判定**（原生 exe 曾被判成 JVM 形态，那是真 bug，已修），
        //   而不是「整条链路没实现」。多拦一层的代价就是用户看到「升级按钮永远是灰的」。
        // 唯一**确实**做不到的组合仍由平台那条挡住（POSIX 换装脚本未实现）。
        String platform = unsupportedReason();
        if (platform != null) {
            return platform;
        }
        if (layout.restartCommand().isEmpty()) {
            return "这个安装形态没有可用的启动脚本（安装根里没有 AhaDesktop.bat / 原生单文件，"
                    + "只剩一个单体 jar），换装后无法自动重启，因此不在界面里做自更新";
        }
        return null;
    }

    /**
     * 生成「脱离式拉起 helper」的命令（<b>只生成，不执行</b>：真实拉起由 {@link #launch} 做，
     * 测试只断言这里的命令行与计划内容）。
     *
     * @param helperDir helper 所在目录
     * @param planFile  计划文件
     * @param windows   是否 Windows（可注入，便于两端都测）
     * @return 命令列表
     */
    public static List<String> launchCommand(Path helperDir, Path planFile, boolean windows) {
        Path plan = planFile.toAbsolutePath();
        if (windows) {
            // `start ""` 的第一个参数是窗口标题，必须占位；`/b` = 不新开窗口
            return List.of("cmd", "/c", "start", "", "/b",
                    helperDir.resolve(WINDOWS_ENTRY).toAbsolutePath().toString(),
                    plan.toString());
        }
        String command = "nohup " + quote(helperDir.resolve(POSIX_ENTRY).toAbsolutePath().toString())
                + " " + quote(plan.toString()) + " >/dev/null 2>&1 &";
        return List.of("sh", "-c", command);
    }

    /**
     * 真实拉起 helper（不等待它结束）。
     *
     * @param helperDir helper 所在目录
     * @param planFile  计划文件
     * @return 已启动的进程
     * @throws UpdateException 拉起失败
     */
    public static Process launch(Path helperDir, Path planFile) {
        List<String> command = launchCommand(helperDir, planFile, InstallLayout.isWindows());
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            return builder.start();
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "拉起换装 helper 失败：" + String.join(" ", command) + "（" + e.getMessage() + "）", e);
        }
    }

    /** POSIX 下给脚本补可执行位（复制会丢掉它；Windows 上不需要）。 */
    private static void ensureExecutable(Path file) {
        if (InstallLayout.isWindows()) {
            return;
        }
        try {
            var permissions = Files.getPosixFilePermissions(file);
            permissions.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE);
            Files.setPosixFilePermissions(file, permissions);
        } catch (IOException | UnsupportedOperationException ignored) {
            // 補不上也不阻断：调用方可以用 `sh <脚本>` 跑
        }
    }

    /** POSIX 命令串里的单引号转义（路径可能含空格）。 */
    private static String quote(String text) {
        return "'" + text.replace("'", "'\\''") + "'";
    }
}
