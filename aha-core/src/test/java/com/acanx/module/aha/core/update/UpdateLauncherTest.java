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
        assertThat(command.get(2))
                .contains("nohup")
                .contains("'/tmp")
                .contains(UpdateLauncher.POSIX_ENTRY)
                .contains(UpdatePaths.PLAN_FILE)
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
    void installHelpersFailsLoudlyWhenThePackageShipsNoHelper() throws IOException {
        Path installRoot = tempDir.resolve("install");
        Files.createDirectories(installRoot);
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));

        assertThatThrownBy(() -> UpdateLauncher.installHelpers(installRoot, paths))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("没有换装 helper")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_PACKAGE_INVALID));
    }
}
