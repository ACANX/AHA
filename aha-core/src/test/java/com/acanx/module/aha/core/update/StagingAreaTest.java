package com.acanx.module.aha.core.update;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 暂存区：目录布局、建目录幂等、清理与「备份保留 N 份」（issue #51 §4.2 / §4.4）。
 *
 * <p>全部在 {@code @TempDir} 里跑（`TestingSpec.md` §5.1：不触碰真实家目录 ——
 * {@code StagingArea} 的根是注入的，默认布局那一步才去碰 {@code AHA_HOME}）。</p>
 *
 * @since 0.2.0
 */
class StagingAreaTest {

    @TempDir
    Path tempDir;

    private StagingArea staging() {
        return new StagingArea(UpdatePaths.at(tempDir.resolve("update")));
    }

    @Test
    void layoutMatchesTheDocumentedShape() {
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        AhaVersion version = AhaVersion.parse("0.1.3.00243");

        assertThat(paths.versionDir(version)).isEqualTo(paths.root().resolve("0.1.3.00243"));
        assertThat(paths.packageZip(version))
                .isEqualTo(paths.root().resolve("0.1.3.00243").resolve("pkg.zip"));
        assertThat(paths.newPackageDir(version))
                .isEqualTo(paths.root().resolve("0.1.3.00243").resolve("new"));
        assertThat(paths.backupDir(version))
                .isEqualTo(paths.root().resolve("backup").resolve("0.1.3.00243"));
        assertThat(paths.planFile()).isEqualTo(paths.root().resolve("update-plan.json"));
        assertThat(paths.resultFile()).isEqualTo(paths.root().resolve("update-result.json"));
        assertThat(paths.logFile()).isEqualTo(paths.root().resolve("update.log"));
    }

    @Test
    void prepareCreatesDirectoriesAndIsIdempotent() throws IOException {
        AhaVersion version = AhaVersion.parse("0.1.3.00243");
        StagingArea area = staging();

        area.prepare(version);
        // 已下载好的 .part 不能被 prepare 清掉（要留着续传）
        Path part = area.packageZip(version).resolveSibling("pkg.zip.part");
        Files.writeString(part, "half");
        area.prepare(version);

        assertThat(area.newPackageDir(version)).isDirectory();
        assertThat(part).exists();
    }

    @Test
    void clearRemovesOnlyThatVersion() throws IOException {
        StagingArea area = staging();
        AhaVersion target = AhaVersion.parse("0.1.3.00243");
        AhaVersion other = AhaVersion.parse("0.1.3.00242");
        area.prepare(target);
        area.prepare(other);
        area.backupDir(AhaVersion.parse("0.1.3.00241"));
        Files.createDirectories(area.backupDir(AhaVersion.parse("0.1.3.00241")));

        area.clear(target);

        assertThat(area.root().resolve("0.1.3.00243")).doesNotExist();
        assertThat(area.root().resolve("0.1.3.00242")).isDirectory();
        assertThat(area.root().resolve("backup").resolve("0.1.3.00241")).isDirectory();
    }

    @Test
    void clearOthersRemovesEveryOtherVersionButKeepsTheGivenOneAndStrayDirs() throws IOException {
        UpdatePaths paths = UpdatePaths.at(tempDir.resolve("update"));
        StagingArea staging = new StagingArea(paths);
        AhaVersion running = AhaVersion.parse("0.1.3.00243");
        AhaVersion failedTarget = AhaVersion.parse("0.1.3.00244");
        Files.createDirectories(paths.versionDir(running));
        Files.writeString(paths.packageZip(running), "running");
        // 换装失败留下的目标版本暂存（pkg.zip + 解包副本，一个包 25–55 MB）
        Files.createDirectories(paths.newPackageDir(failedTarget));
        Files.writeString(paths.packageZip(failedTarget), "failed");
        // 人工放进来的目录、以及 backup/ 都不该被当成「版本目录」
        Files.createDirectories(paths.root().resolve("手工目录"));
        Files.createDirectories(paths.backupRoot().resolve(running.value()));

        int removed = staging.clearOthers(running);

        assertThat(removed).isEqualTo(1);
        assertThat(paths.versionDir(running)).exists();
        assertThat(paths.versionDir(failedTarget)).doesNotExist();
        assertThat(paths.root().resolve("手工目录")).exists();
        assertThat(paths.backupRoot().resolve(running.value())).exists();
    }

    @Test
    void pruneBackupsKeepsTheNewestOnesAndIgnoresForeignDirectories() throws IOException {
        StagingArea area = staging();
        for (String version : new String[] {"0.1.3.00238", "0.1.3.00241", "0.1.3.00242"}) {
            Files.createDirectories(area.backupDir(AhaVersion.parse(version)));
        }
        // 人工放进来的目录（名字不是版本号）：不该被当成备份删掉
        Files.createDirectories(area.root().resolve("backup").resolve("manual-notes"));

        int removed = area.pruneBackups(2);

        assertThat(removed).isEqualTo(1);
        assertThat(area.backupDir(AhaVersion.parse("0.1.3.00238"))).doesNotExist();
        assertThat(area.backupDir(AhaVersion.parse("0.1.3.00241"))).isDirectory();
        assertThat(area.backupDir(AhaVersion.parse("0.1.3.00242"))).isDirectory();
        assertThat(area.root().resolve("backup").resolve("manual-notes")).isDirectory();
    }

    @Test
    void pruneBackupsOnAMissingDirectoryIsANoOp() {
        assertThat(staging().pruneBackups(2)).isZero();
    }
}
