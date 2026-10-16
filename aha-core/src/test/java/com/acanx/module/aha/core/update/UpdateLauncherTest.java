package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 换装 helper 的安装与拉起命令（issue #51 §4.5）。
 *
 * <p><b>只断言命令行与文件，不真跑 helper</b>：真跑要等 App 退出、要替换文件、要重新拉起，
 * 那是「跑起来才知道」的部分，按 §11.3 由 ACANX 在 Windows 上验（P2-4）。</p>
 *
 * @since 0.2.0
 */
class UpdateLauncherTest {

    @TempDir
    Path tempDir;

    @Test
    void windowsCommandStartsTheHelperDetached() {
        Path helperDir = tempDir.resolve("update");
        Path plan = helperDir.resolve(UpdatePaths.PLAN_FILE);

        List<String> command = UpdateLauncher.launchCommand(helperDir, plan, true);

        // `start ""` 的第一个参数是窗口标题，必须占位；`/b` = 不新开窗口；
        // 与 App 无父子句柄依赖 ⇒ App 退出不会带走 helper
        assertThat(command).containsExactly("cmd", "/c", "start", "", "/b",
                helperDir.resolve(UpdateLauncher.WINDOWS_ENTRY).toAbsolutePath().toString(),
                plan.toAbsolutePath().toString());
    }

    @Test
    void posixCommandUsesNohupAndQuotesEveryPath() {
        Path helperDir = tempDir.resolve("update dir");
        Path plan = helperDir.resolve(UpdatePaths.PLAN_FILE);

        List<String> command = UpdateLauncher.launchCommand(helperDir, plan, false);

        assertThat(command).hasSize(3);
        assertThat(command.get(0)).isEqualTo("sh");
        assertThat(command.get(1)).isEqualTo("-c");
        // 不假设临时目录长什么样（Windows runner 上是 C:\Users\…）：只断言「每个路径都被单引号包住」，
        // 否则路径里的空格会把参数切开 —— 这条与平台无关，也正是要守的东西
        assertThat(command.get(2))
                .contains("nohup ")
                .contains("'" + helperDir.resolve(UpdateLauncher.POSIX_ENTRY).toAbsolutePath() + "'")
                .contains("'" + plan.toAbsolutePath() + "'")
                .endsWith(">/dev/null 2>&1 &");
    }

    @Test
    void installHelpersCopiesThisPlatformsTemplatesNextToThePlan() throws IOException {
        Path installRoot = tempDir.resolve("install");
        Files.createDirectories(installRoot);
        for (String name : List.of(UpdateLauncher.WINDOWS_ENTRY, UpdateLauncher.WINDOWS_SCRIPT,
                UpdateLauncher.POSIX_ENTRY)) {
            Files.writeString(installRoot.resolve(name), "template");
        }
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));

        Path helperDir = UpdateLauncher.installHelpers(installRoot, paths);

        List<String> expected = InstallLayout.isWindows()
                ? List.of(UpdateLauncher.WINDOWS_ENTRY, UpdateLauncher.WINDOWS_SCRIPT)
                : List.of(UpdateLauncher.POSIX_ENTRY);
        assertThat(helperDir).isEqualTo(paths.root());
        assertThat(expected).allSatisfy(
                name -> assertThat(paths.root().resolve(name)).exists());
        // 另一个平台的模板不该被复制过去（helper 放安装根之外：它会自删）
        String other = InstallLayout.isWindows()
                ? UpdateLauncher.POSIX_ENTRY : UpdateLauncher.WINDOWS_ENTRY;
        assertThat(paths.root().resolve(other)).doesNotExist();
    }

    @Test
    void unsupportedReasonDoesNotBlockTheNativeShape() throws IOException {
        // 【曾经在这里拦过原生形态，2026-10-16 撤掉】当初的理由是「原生包不在升级通道里」——
        // 实测发现那条不成立：原生线有自己的 tag（`V<版本>-aha-desktop-native`）与资产
        // （`AHA-Desktop-Native-…`），资产选择器也支持（平台 + 默认 jdk25）；换装与重启机制与
        // JVM 同构（包内清单 + helper 随包分发 + `restartCommand()` 返回 exe）。
        // 多拦一层的代价就是用户看到「升级按钮永远是灰的」（真机反馈）。
        Path root = tempDir.resolve("native-install");
        Files.createDirectories(root);
        Path exe = root.resolve(InstallLayout.NATIVE_EXE_BASE
                + (InstallLayout.isWindows() ? ".exe" : ""));
        Files.writeString(exe, "MZ");
        InstallLayout layout = InstallLayout.of(root);

        String reason = UpdateLauncher.unsupportedReason(layout);

        if (InstallLayout.isWindows()) {
            assertThat(reason).as("Windows 上原生形态可以应用内自更新").isNull();
            assertThat(layout.restartCommand()).as("换装后拉起的就是这个 exe")
                    .containsExactly(exe.toAbsolutePath().toString());
        } else {
            assertThat(reason).as("POSIX 仍被平台那条拦住（换装脚本未实现）")
                    .contains("只在 Windows");
        }
    }

    @Test
    void unsupportedReasonExplainsWhicheverLayerBlocks() throws IOException {
        // 两层门：平台（POSIX 脚本还没实现）+ 安装形态（只剩单体 jar ⇒ 换装后回不来）
        Path root = tempDir.resolve("only-jar");
        Files.createDirectories(root);
        Path jar = root.resolve("aha-desktop.jar");
        Files.writeString(jar, "jar");

        String jarOnly = UpdateLauncher.unsupportedReason(InstallLayout.of(jar));
        if (InstallLayout.isWindows()) {
            assertThat(jarOnly).as("只剩单体 jar 的布局要被提前拦住")
                    .contains("没有可用的启动脚本");
        } else {
            assertThat(jarOnly).as("POSIX 仍先因平台被拦（脚本未实现）").contains("只在 Windows");
        }

        // 补上启动脚本：Windows 上就没话说了；POSIX 仍然被平台拦（脚本未实现，见 TD-00020）
        Files.writeString(root.resolve(InstallLayout.isWindows()
                ? InstallLayout.WINDOWS_LAUNCHER : InstallLayout.POSIX_LAUNCHER), "launcher");
        String withLauncher = UpdateLauncher.unsupportedReason(InstallLayout.of(jar));
        if (InstallLayout.isWindows()) {
            assertThat(withLauncher).isNull();
        } else {
            assertThat(withLauncher).contains("只在 Windows");
        }
    }

    @Test
    void installHelpersPrefersTheNewPackageOverTheInstallRoot() throws IOException {
        // 2026-10-16 真机实测：正在运行的安装是「helper 还没随包分发」之前打的包（安装根里没有），
        // 而新包里带着 —— 只认安装根就会报「该包不完整、无法自更新」，把升级卡死。
        Path installRoot = tempDir.resolve("old-install");
        Files.createDirectories(installRoot);
        Path newPackage = tempDir.resolve("new-package");
        Files.createDirectories(newPackage);
        Files.writeString(newPackage.resolve(UpdateLauncher.WINDOWS_SCRIPT), "from-new-package");
        Files.writeString(newPackage.resolve(UpdateLauncher.WINDOWS_ENTRY), "from-new-package");
        Files.writeString(newPackage.resolve(UpdateLauncher.POSIX_ENTRY), "from-new-package");
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));

        Path helperDir = UpdateLauncher.installHelpers(installRoot, newPackage, paths);

        assertThat(helperDir).isEqualTo(paths.root());
        String expected = "from-new-package";
        String name = InstallLayout.isWindows() ? UpdateLauncher.WINDOWS_SCRIPT : UpdateLauncher.POSIX_ENTRY;
        assertThat(paths.root().resolve(name)).hasContent(expected);
    }

    @Test
    void installHelpersFailsLoudlyWhenThePackageShipsNoHelper() throws IOException {
        Path installRoot = tempDir.resolve("install");
        Files.createDirectories(installRoot);
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));

        // 文案分两种：Windows 上是「新包与这台机器的安装都没有换装脚本」（**不再**说成
        // 「该包不完整」—— 真机实测过：新包是完整的，缺的是更早那个安装，锅不能扣在包上），
        // POSIX 上是「本平台尚未实现」（界面会提前置灰，见审查 P1-7）
        assertThatThrownBy(() -> UpdateLauncher.installHelpers(installRoot, paths))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining(InstallLayout.isWindows() ? "换装脚本缺失" : "暂不支持应用内自更新")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_PACKAGE_INVALID));
    }
}
