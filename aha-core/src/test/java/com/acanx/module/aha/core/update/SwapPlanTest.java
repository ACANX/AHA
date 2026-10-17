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
 * 落地器：**只动清单内文件**、旧文件进备份、备份可还原（issue #51 §4.4）。
 *
 * <p>两条关键用例写在这里：①**手写文件不被删** —— `Dist/` 里除了产物还有被跟踪的手写入口
 * （`UpdateJvm.ps1` / `Update*.cmd` / `README.commands.md`）与用户自己放的东西，
 * 整个目录替换会误删；②**备份可还原** —— 失败时要能回到换装前的样子。</p>
 *
 * @since 0.2.0
 */
class SwapPlanTest {

    private static final AhaVersion OLD_VERSION = AhaVersion.parse("0.1.3.00242");

    private static final AhaVersion NEW_VERSION = AhaVersion.parse("0.1.3.00243");

    @TempDir
    Path tempDir;

    private Path installRoot;

    private Path backupDir;

    private Path packageRoot;

    /** 铺一个「已经装着 00242」的安装根：一个 jar、一个将被新包删掉的文件，外加两个**不属于包**的文件。 */
    private void givenInstalledPackage() throws IOException {
        installRoot = tempDir.resolve("install");
        backupDir = tempDir.resolve("backup");
        packageRoot = tempDir.resolve("new");
        Files.createDirectories(installRoot);
        Files.createDirectories(packageRoot);
        Files.writeString(installRoot.resolve("aha-desktop.jar"), "OLD-JAR");
        Files.writeString(installRoot.resolve("removed-by-new-version.txt"), "OLD-EXTRA");
        // 这两个都不在两份清单里：手写入口与用户自己的东西
        Files.writeString(installRoot.resolve("UpdateJvm.ps1"), "HAND-WRITTEN");
        Files.writeString(installRoot.resolve("notes.txt"), "MY-NOTES");
    }

    private PackageManifest manifest(AhaVersion version, String... paths) {
        String files = java.util.Arrays.stream(paths)
                .map(path -> "{\"path\":\"" + path + "\",\"sha256\":\""
                        + "0".repeat(64) + "\",\"size\":1}")
                .reduce((left, right) -> left + "," + right).orElse("");
        return PackageManifest.parse("""
                {"kind":"desktop-jvm","version":"%s","platform":"windows-amd64","jdk":25,"files":[%s]}
                """.formatted(version.value(), files));
    }

    @Test
    void replacesAndRemovesOnlyWhatTheManifestsSay() throws IOException {
        givenInstalledPackage();
        Files.writeString(packageRoot.resolve("aha-desktop.jar"), "NEW-JAR");
        Files.createDirectories(packageRoot.resolve("bin"));
        Files.writeString(packageRoot.resolve("bin").resolve("aha.sh"), "NEW-SCRIPT");

        PackageManifest installed = manifest(OLD_VERSION, "aha-desktop.jar", "removed-by-new-version.txt");
        PackageManifest fresh = manifest(NEW_VERSION, "aha-desktop.jar", "bin/aha.sh");
        List<UpdatePlan.Action> actions = SwapPlan.actions(fresh, installed);

        assertThat(actions).extracting(UpdatePlan.Action::path)
                .containsExactly("aha-desktop.jar", "bin/aha.sh", "removed-by-new-version.txt");
        assertThat(actions.get(2).deleting()).isTrue();

        SwapPlan.apply(installRoot, packageRoot, backupDir, actions);

        assertThat(Files.readString(installRoot.resolve("aha-desktop.jar"))).isEqualTo("NEW-JAR");
        assertThat(Files.readString(installRoot.resolve("bin").resolve("aha.sh")))
                .isEqualTo("NEW-SCRIPT");
        assertThat(installRoot.resolve("removed-by-new-version.txt")).doesNotExist();
        // 不在清单里的一律不碰
        assertThat(Files.readString(installRoot.resolve("UpdateJvm.ps1"))).isEqualTo("HAND-WRITTEN");
        assertThat(Files.readString(installRoot.resolve("notes.txt"))).isEqualTo("MY-NOTES");
        // 旧文件与「被删掉的文件」都留了备份
        assertThat(Files.readString(backupDir.resolve("aha-desktop.jar"))).isEqualTo("OLD-JAR");
        assertThat(Files.readString(backupDir.resolve("removed-by-new-version.txt")))
                .isEqualTo("OLD-EXTRA");
    }

    @Test
    void rollbackRestoresThePreviousStateIncludingRemovedFiles() throws IOException {
        givenInstalledPackage();
        Files.writeString(packageRoot.resolve("aha-desktop.jar"), "NEW-JAR");
        Files.createDirectories(packageRoot.resolve("bin"));
        Files.writeString(packageRoot.resolve("bin").resolve("aha.sh"), "NEW-SCRIPT");

        PackageManifest installed = manifest(OLD_VERSION, "aha-desktop.jar", "removed-by-new-version.txt");
        PackageManifest fresh = manifest(NEW_VERSION, "aha-desktop.jar", "bin/aha.sh");
        List<UpdatePlan.Action> actions = SwapPlan.actions(fresh, installed);

        SwapPlan.apply(installRoot, packageRoot, backupDir, actions);
        SwapPlan.rollback(installRoot, backupDir, actions);

        assertThat(Files.readString(installRoot.resolve("aha-desktop.jar"))).isEqualTo("OLD-JAR");
        assertThat(Files.readString(installRoot.resolve("removed-by-new-version.txt")))
                .isEqualTo("OLD-EXTRA");
        // 本次新增的文件在回滚时要被删掉（备份里没有它）
        assertThat(installRoot.resolve("bin").resolve("aha.sh")).doesNotExist();
        assertThat(Files.readString(installRoot.resolve("notes.txt"))).isEqualTo("MY-NOTES");
    }

    @Test
    void plansANativeSingleFileSwapTheSameWay() throws IOException {
        // 原生形态（Windows 单文件自更新，issue #51 §7 P4）：包里只有一个可执行文件。
        // 动作列表的算法与 JVM 形态**完全一致**（只看两份清单），所以这里断言「就是一条 replace
        // + 一条 delete」即可 —— helper 与计划格式都不需要为原生形态另开分支。
        givenInstalledPackage();
        String executable = InstallLayout.isWindows() ? "aha-desktop-native.exe" : "aha-desktop-native";
        Files.writeString(installRoot.resolve(executable), "OLD-EXE");
        Files.createDirectories(installRoot.resolve("old-native-helper"));
        Files.writeString(installRoot.resolve("old-native-helper").resolve("x.txt"), "GONE-IN-NEW");
        Files.writeString(packageRoot.resolve(executable), "NEW-EXE");

        PackageManifest installed = manifest(OLD_VERSION, executable, "old-native-helper/x.txt");
        PackageManifest fresh = manifest(NEW_VERSION, executable);
        List<UpdatePlan.Action> actions = SwapPlan.actions(fresh, installed);

        assertThat(actions).extracting(UpdatePlan.Action::path)
                .containsExactly(executable, "old-native-helper/x.txt");
        assertThat(actions.get(0).deleting()).isFalse();
        assertThat(actions.get(1).deleting()).isTrue();

        SwapPlan.apply(installRoot, packageRoot, backupDir, actions);
        assertThat(Files.readString(installRoot.resolve(executable))).isEqualTo("NEW-EXE");
        // 被新包删掉的文件要消失；它留下的**空目录**保留（落地器只动清单里的文件，
        // 不去猜哪些目录已经空了 —— 猜错就是删用户目录）
        assertThat(installRoot.resolve("old-native-helper").resolve("x.txt")).doesNotExist();

        SwapPlan.rollback(installRoot, backupDir, actions);
        assertThat(Files.readString(installRoot.resolve(executable))).isEqualTo("OLD-EXE");
        assertThat(Files.readString(installRoot.resolve("old-native-helper").resolve("x.txt")))
                .isEqualTo("GONE-IN-NEW");
    }

    @Test
    void worksWithoutAnInstalledManifest() throws IOException {
        givenInstalledPackage();

        // 老版本没有包内清单：只能新增，不能判断谁该删 —— 于是只做 replace
        assertThat(SwapPlan.actions(manifest(NEW_VERSION, "aha-desktop.jar"), null))
                .extracting(UpdatePlan.Action::path).containsExactly("aha-desktop.jar");
        assertThat(SwapPlan.actions(manifest(NEW_VERSION, "aha-desktop.jar"), null))
                .allSatisfy(action -> assertThat(action.deleting()).isFalse());
    }

    @Test
    void refusesWhenThePackageIsMissingAFileTheManifestLists() throws IOException {
        givenInstalledPackage();

        assertThatThrownBy(() -> SwapPlan.apply(installRoot, packageRoot, backupDir,
                SwapPlan.actions(manifest(NEW_VERSION, "aha-desktop.jar"), null)))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("新包里没有这个文件")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_PACKAGE_INVALID));
    }

    @Test
    void applyIsIdempotentForAnAlreadyReplacedFile() throws IOException {
        givenInstalledPackage();
        Files.writeString(packageRoot.resolve("aha-desktop.jar"), "NEW-JAR");
        List<UpdatePlan.Action> actions = SwapPlan.actions(
                manifest(NEW_VERSION, "aha-desktop.jar"), manifest(OLD_VERSION, "aha-desktop.jar"));

        SwapPlan.apply(installRoot, packageRoot, backupDir, actions);
        SwapPlan.apply(installRoot, packageRoot, backupDir, actions);

        assertThat(Files.readString(installRoot.resolve("aha-desktop.jar"))).isEqualTo("NEW-JAR");
        // 第二次执行时备份里已经是「上一次替换下来的旧内容」；这不影响结果，只说明备份会被刷新
        assertThat(SwapPlan.hasBackup(backupDir)).isTrue();
    }
}
