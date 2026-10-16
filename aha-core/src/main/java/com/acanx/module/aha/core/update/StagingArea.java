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
     * <p>只会创建，<b>不会清空</b>：已下载好的 {@code .part} 要留着（重试会重新下载，但它也是排查现场），
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
     * @return 真的删了东西返回 {@code true}（原本就不存在则 {@code false}）——
     *         启动收尾要用它给用户一个**如实**的清理计数（2026-10-16 第二轮审查 P2-⑤）
     * @throws UpdateException 删除失败（{@link UpdateException#CODE_DOWNLOAD_FAILED}）
     */
    public boolean clear(AhaVersion version) {
        return deleteRecursively(paths.versionDir(version));
    }

    /**
     * 清掉**不是** {@code keep} 的其它版本暂存目录（幂等）。
     *
     * <p>为什么需要它：换装失败（已回滚或未回滚）时，留下的是**目标版本**的暂存目录
     * （`pkg.zip` + 解包副本，一个包 25–55 MB，合计约 50–110 MB），而 {@link #clear} 清的是
     * 「本次运行的版本」——两者在失败路径上不是同一个，于是每次失败都白占一份磁盘。
     * 启动时顺手清掉，重试时会重新下载。</p>
     *
     * <p>不认得的目录名（人工放进来的东西）一律不动；{@code backup} 也解析不成版本号，因此安全。</p>
     *
     * @param keep 保留哪个版本（通常是本次运行的版本）
     * @return 实际清掉的目录数
     * @throws UpdateException 清理失败
     */
    public int clearOthers(AhaVersion keep) {
        Path parent = paths.root();
        if (!Files.isDirectory(parent)) {
            return 0;
        }
        List<Path> entries;
        try (Stream<Path> listing = Files.list(parent)) {
            entries = listing.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED,
                    "列出暂存目录失败：" + parent + "（" + e.getMessage() + "）", e);
        }
        int removed = 0;
        for (Path entry : entries) {
            AhaVersion version = parseOrNull(entry.getFileName().toString());
            if (version == null || version.equals(keep)) {
                continue;
            }
            deleteRecursively(entry);
            removed++;
        }
        return removed;
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

    /** 递归删除（不跟随符号链接）；返回是否真的删了（路径不存在时 {@code false}）。 */
    private static boolean deleteRecursively(Path path) {
        if (!Files.exists(path)) {
            return false;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            for (Path each : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(each);
            }
            return true;
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED,
                    "删除失败：" + path + "（" + e.getMessage() + "）", e);
        }
    }
}
