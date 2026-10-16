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
 * 安装根的解析与形态判定（issue #51 §2.3 / §4.4）。
 *
 * <p>判定依据是「当前 code source 是不是 jar」，不是「目录里有没有 jar」：
 * 用户手边往往既有便携包又有原生 exe，后者会判错。形态判错＝跨形态升级，
 * 那是硬约束禁止的事（§2.3）。</p>
 *
 * @since 0.2.0
 */
class InstallLayoutTest {

    @TempDir
    Path tempDir;

    @Test
    void jarCodeSourceMeansJarBasedInstall() throws IOException {
        Path jar = tempDir.resolve("aha-desktop.jar");
        Files.writeString(jar, "jar");

        InstallLayout layout = InstallLayout.of(jar);

        assertThat(layout.jarBased()).isTrue();
        assertThat(layout.root()).isEqualTo(tempDir);
        assertThat(layout.jar()).isEqualTo(jar);
    }

    @Test
    void directoryCodeSourceIsNotJarBased() {
        InstallLayout layout = InstallLayout.of(tempDir);

        assertThat(layout.jarBased()).isFalse();
        assertThat(layout.root()).isEqualTo(tempDir);
    }

    @Test
    void launcherPrefersTheScriptsOfThisPlatform() throws IOException {
        Files.writeString(tempDir.resolve(InstallLayout.WINDOWS_LAUNCHER), "@echo off\n");
        Files.writeString(tempDir.resolve(InstallLayout.POSIX_LAUNCHER), "#!/bin/sh\n");

        Path launcher = InstallLayout.of(tempDir).launcher().orElseThrow();

        String expected = InstallLayout.isWindows()
                ? InstallLayout.WINDOWS_LAUNCHER : InstallLayout.POSIX_LAUNCHER;
        assertThat(launcher.getFileName().toString()).isEqualTo(expected);
    }

    @Test
    void restartCommandUsesTheLauncherThenFallsBackToJava() throws IOException {
        Files.writeString(tempDir.resolve("aha-desktop.jar"), "jar");
        InstallLayout layout = InstallLayout.of(tempDir.resolve("aha-desktop.jar"));

        // 只有 jar：退回 `java -jar`（java 不是绝对路径，因此这种情况不会被写进计划，
        // 见 UpdatePlan 的校验 —— helper 按 PATH 解析它）
        assertThat(layout.restartCommand()).containsExactly("java", "-jar",
                tempDir.resolve("aha-desktop.jar").toString());

        // 有启动脚本：优先用它（脚本里带着正确的 JVM 参数）
        Path launcher = tempDir.resolve(InstallLayout.isWindows()
                ? InstallLayout.WINDOWS_LAUNCHER : InstallLayout.POSIX_LAUNCHER);
        Files.writeString(launcher, "launcher");
        assertThat(layout.restartCommand()).containsExactly(launcher.toString());
    }

    @Test
    void restartCommandUsesTheNativeExecutableWhenNotJarBased() throws IOException {
        Path executable = InstallLayout.of(tempDir).nativeExecutable();
        Files.writeString(executable, "exe");

        assertThat(InstallLayout.of(tempDir).restartCommand()).containsExactly(executable.toString());
    }

    @Test
    void requireEntryPointsChecksTheEntryOfTheCurrentForm() throws IOException {
        Path jar = tempDir.resolve("aha-desktop.jar");
        // 先让它存在，才会被判成 JVM 形态（判定看的是「磁盘上真是文件」，不是名字）
        Files.writeString(jar, "jar");
        InstallLayout jarBased = InstallLayout.of(jar);
        assertThat(jarBased.jarBased()).isTrue();

        // 再删掉：入口缺失必须报出来
        Files.delete(jar);
        assertThatThrownBy(jarBased::requireEntryPoints)
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("aha-desktop.jar");

        Files.writeString(jar, "jar");
        jarBased.requireEntryPoints();

        InstallLayout nativeBased = InstallLayout.of(tempDir);
        assertThatThrownBy(nativeBased::requireEntryPoints)
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("原生单文件");

        Files.writeString(nativeBased.nativeExecutable(), "exe");
        nativeBased.requireEntryPoints();
    }

    @Test
    void detectNeverInventsAnInstallRoot() {
        // 本测试跑在 JVM 上（code source 是 classes 目录或 jar），detect 必须给出一个绝对路径，
        // 且入口文件就在它下面
        InstallLayout layout = InstallLayout.detect();

        assertThat(layout.root().isAbsolute()).isTrue();
        Path entry = layout.jarBased() ? layout.jar() : layout.nativeExecutable();
        assertThat(entry.getParent()).isEqualTo(layout.root());
    }
    @Test
    void restartFallsBackToAnAbsoluteJavaPath() throws IOException {
        // 只有单体 jar、没有启动脚本与原生单文件：兜底是 <java.home>/bin/java -jar
        // （必须是绝对路径：计划校验会挡下裸 `java`，2026-10-16 审查 P1-6）
        Path root = tempDir.resolve("bare");
        Files.createDirectories(root);
        Files.writeString(root.resolve("aha-desktop.jar"), "jar");

        List<String> command = InstallLayout.of(root.resolve("aha-desktop.jar")).restartCommand();

        assertThat(command).hasSize(3);
        assertThat(Path.of(command.get(0))).isAbsolute();
        assertThat(Path.of(command.get(0))).exists();
        assertThat(command.get(0)).endsWith(InstallLayout.isWindows() ? "java.exe" : "java");
        assertThat(command.get(1)).isEqualTo("-jar");
        assertThat(command.get(2)).endsWith("aha-desktop.jar");
    }

}
