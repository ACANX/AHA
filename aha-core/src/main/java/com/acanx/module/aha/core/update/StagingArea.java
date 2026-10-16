package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 暂存区：下载与解压的落脚点、备份的保管处（issue #51 §4.2 / §4.4）。
 *
 * <p>它只做三件事，且每件都整在该做的目录里：给出路径、按需建目录、按保留份数清理旧备份。
 * <b>不碰安装目录，也不碰 {@code ~/.aha} 下除 {@code update/} 以外的任何东西</b>
 * （用户数据永不触碰，§4.4）。</p>
 *
 * @since 0.2.0
 */
public final class StagingArea {

    private final UpdatePaths paths;

    /**
     * 构造。
     *
     * @param paths 路径布局
     */
    public StagingArea(UpdatePaths paths) {
        this.paths = paths;
    }

    /**
     * 升效根目录。
     *
     * @return {@code <根>}
     */
    public Path root() {
        return paths.root();
    }

    /**
     * 准备某个版本的暂存目录（幂等）。
     *
     * <p>只会创建，<b>不会清空</b>：已下载好的 {@code .part} 要留着续传，
     * 已下好的包也要留着复用 —— 清理由用户显式触发（见 {@link #clear(AhaVersion)}）。</p>
     *
     * @param version 目标版本
     * @return 该版本的暂存目录
     * @throws UpdateException 建目录失败（{@link UpdateException#CODE_DOWNLOAD_FAILED}）
     */
    public Path prepare(AhaVersion version) {
        Path dir = paths.versionDir(version);
        try {
            Files.createDirectories(dir);
            Files.createDirectories(paths.newPackageDir(version));
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED,
                    "创建暂存目录失败：" + dir + "（" + e.getMessage() + "）", e);
        }
        return dir;
    }

    /**
     * 某个版本的下载落地路径。
     *
     * @param version 目标版本
     * @return {@code <根>/<版本>/pkg.zip}
     */
    public Path packageZip(AhaVersion version) {
        return paths.packageZip(version);
    }

    /**
     * 某个版本解压后的新包目录。
     *
     * @param version 目标版本
     * @return {@code <根>/<版本>/new}
     */
    public Path newPackageDir(AhaVersion version) {
        return paths.newPackageDir(version);
    }

    /**
     * 某个旧版本的备份目录。
     *
     * @param version 被替换下来的版本
     * @return {@code <根>/backup/<版本>}
     */
    public Path backupDir(AhaVersion version) {
        return paths.backupDir(version);
    }

    /**
     * 删除某个版本的整个暂存目录（幂等）。
     *
     * @param version 目标版本
     * @throws UpdateException 删除失败（{@link UpdateException#CODE_DOWNLOAD_FAILED}）
     */
    public void clear(AhaVersion version) {
        deleteRecursively(paths.versionDir(version));
    }

    /**
     * 只保留最近 {@code keep} 份备份，其余删除（按版本号从新到旧）。
     *
     * <p>对应配置键 {@code Update.BackupKeep}（默认 2）：留着是为了能「再切回去」，
     * 但不能无限涨 —— 一个包 25–55 MB。</p>
     *
     * @param keep 保留份数；≤ 0 时清空全部备份
     * @return 实际删掉的备份目录数
     * @throws UpdateException 清理失败（{@link UpdateException#CODE_DOWNLOAD_FAILED}）
     */
    public int pruneBackups(int keep) {
        Path backupRoot = paths.backupRoot();
        if (!Files.isDirectory(backupRoot)) {
            return 0;
        }
        List<Path> backups;
        try (Stream<Path> entries = Files.list(backupRoot)) {
            backups = entries.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED,
                    "列出备份目录失败：" + backupRoot + "（" + e.getMessage() + "）", e);
        }
        // 目录名就是版本号：解析不出来的（人工放进来的东西）排在最后，不动它
        List<Path> ordered = backups.stream()
                .sorted(Comparator.comparing((Path path) -> parseOrNull(path.getFileName().toString()),
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int removed = 0;
        int kept = 0;
        for (Path backup : ordered) {
            if (parseOrNull(backup.getFileName().toString()) == null) {
                continue;
            }
            if (kept < keep) {
                kept++;
                continue;
            }
            deleteRecursively(backup);
            removed++;
        }
        return removed;
    }

    private static AhaVersion parseOrNull(String text) {
        try {
            return AhaVersion.parse(text);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 递归删除（不跟随符号链接；不存在即视为成功）。 */
    private static void deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path each : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(each);
            }
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED,
                    "删除失败：" + path + "（" + e.getMessage() + "）", e);
        }
    }
}
