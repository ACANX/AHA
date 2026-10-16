package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 落地器：算出「要动哪些文件」，并按计划替换 / 删除 / 备份 / 还原（issue #51 §4.4）。
 *
 * <p><b>核心规矩：只动清单里的文件。</b> {@code Dist/} 里除了构建产物还有<b>被跟踪的手写入口</b>
 * （`UpdateJvm.ps1`、`Update*.cmd`、`README.commands.md`），以及用户自己放的东西 ——
 * 整个目录替换会误删（`DesktopNativeUpdateDesign.md` 已记录过 `Dist/` 的双重身份）。
 * 所以判定只看两份清单：</p>
 *
 * <ul>
 *   <li>新包清单里有 ⇒ {@code replace}（新增或覆盖）；</li>
 *   <li>已装清单里有、新包清单里没有 ⇒ {@code delete}（移入备份，不直接删）；</li>
 *   <li><b>两份清单里都没有 ⇒ 一律不碰</b>。</li>
 * </ul>
 *
 * <p>替换前先把旧文件搬进备份目录；删除也走「移入备份」而不是真删，所以失败可以直接回滚
 * （{@link #rollback} 就是照着同一份动作列表把备份搬回去）。被替换下来的**新版**文件照常进备份 ——
 * 降级时想再切回去就有东西可切（§4.6）。</p>
 *
 * <p><b>实现位置说明</b>：Windows 上真正执行换装的是外部 helper（`Dist/apply-update.cmd` +
 * `Script/PowerShell/AhaApplyUpdate.ps1`）—— 正在运行的进程不能可靠替换自己的文件（§4.5）。
 * 本类的 {@link #apply} / {@link #rollback} 是同一份动作列表的**参考实现**：单测靠它验证语义
 * （「手写文件不被删」「备份可还原」），POSIX 平台的 helper（§7 P4，后置）也用它。
 * 两边必须一致，因此动作列表由 {@link #actions} 单点产出、写进计划文件，helper 只执行不判断。</p>
 *
 * @since 0.2.0
 */
public final class SwapPlan {

    /** 动作名：替换 / 新增。 */
    public static final String KIND_REPLACE = "replace";

    /** 动作名：删除（其实是移入备份）。 */
    public static final String KIND_DELETE = "delete";

    private SwapPlan() {
    }

    /**
     * 算出动作列表。
     *
     * @param newManifest 新包的包内清单
     * @param installed   当前已装版本的包内清单；拿不到（老版本没清单）时传 {@code null}
     * @return 动作列表（先 replace 后 delete，各自按路径排序，保证可复现）
     */
    public static List<UpdatePlan.Action> actions(PackageManifest newManifest,
                                                  PackageManifest installed) {
        Set<String> newPaths = paths(newManifest);
        Set<String> installedPaths = installed == null ? Set.of() : paths(installed);
        List<UpdatePlan.Action> actions = new ArrayList<>();
        newPaths.stream().sorted().forEach(path -> actions.add(UpdatePlan.Action.replace(path)));
        installedPaths.stream().filter(path -> !newPaths.contains(path)).sorted()
                .forEach(path -> actions.add(UpdatePlan.Action.delete(path)));
        return List.copyOf(actions);
    }

    /**
     * 执行动作列表：先备份旧文件，再落新文件。
     *
     * @param installRoot   安装根
     * @param newPackageRoot 解压后的新包根目录
     * @param backupDir     备份目录（动作涉及的文件都搬到这里，保持相对路径）
     * @param actions       动作列表
     * @throws UpdateException 过程中任一步失败（调用方应据此走 {@link #rollback}）
     */
    public static void apply(Path installRoot, Path newPackageRoot, Path backupDir,
                             List<UpdatePlan.Action> actions) {
        for (UpdatePlan.Action action : actions) {
            Path relative = PathSafety.requireRelative(action.path(), "换装动作的路径");
            Path target = installRoot.resolve(relative);
            if (action.deleting()) {
                // 先备份，再从安装根真的删掉（只备份不删，包内被移除的旧文件会一直留在那儿）
                backup(target, backupDir, relative);
                try {
                    Files.deleteIfExists(target);
                } catch (IOException e) {
                    throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                            "删除文件失败：" + target + "（" + e.getMessage() + "）", e);
                }
                continue;
            }
            Path source = newPackageRoot.resolve(relative);
            if (!Files.isRegularFile(source)) {
                throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                        "新包里没有这个文件（清单与包不一致）：" + action.path());
            }
            // 先备份再落新：顺序反了的话，落完再备份就等于把新文件备份了
            backup(target, backupDir, relative);
            try {
                Files.createDirectories(target.getParent());
                // 刻意**不带** COPY_ATTRIBUTES：把旧文件的只读属性带到落地文件上，会让
                // **下一次**升级同一文件在 Windows 上失败（2026-10-16 审查 P2-12）。
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                        "写入文件失败：" + target + "（" + e.getMessage() + "）", e);
            }
        }
    }

    /**
     * 回滚：照着同一份动作列表把备份搬回去（没有备份的说明是这次新增的，删掉）。
     *
     * @param installRoot 安装根
     * @param backupDir   备份目录
     * @param actions     动作列表
     * @throws UpdateException 过程中任一步失败
     */
    public static void rollback(Path installRoot, Path backupDir, List<UpdatePlan.Action> actions) {
        for (UpdatePlan.Action action : actions) {
            Path relative = PathSafety.requireRelative(action.path(), "换装动作的路径");
            Path target = installRoot.resolve(relative);
            Path backup = backupDir.resolve(relative);
            try {
                if (Files.isRegularFile(backup)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    // 备份里没有 ⇒ 这个文件是本次新增的，回滚就等于把它删掉
                    Files.deleteIfExists(target);
                }
            } catch (IOException e) {
                throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                        "回滚失败：" + target + "（" + e.getMessage() + "）", e);
            }
        }
    }

    /**
     * 备份里是否还有内容（helper 自证用：替换后备份目录不该是空的，除非这次只新增文件）。
     *
     * @param backupDir 备份目录
     * @return 有文件返回 {@code true}
     */
    public static boolean hasBackup(Path backupDir) {
        if (!Files.isDirectory(backupDir)) {
            return false;
        }
        try (var walk = Files.walk(backupDir)) {
            return walk.anyMatch(Files::isRegularFile);
        } catch (IOException e) {
            return false;
        }
    }

    private static void backup(Path target, Path backupDir, Path relative) {
        if (!Files.exists(target)) {
            return;
        }
        Path backup = backupDir.resolve(relative);
        try {
            Files.createDirectories(backup.getParent());
            Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "备份旧文件失败：" + target + " → " + backup + "（" + e.getMessage() + "）", e);
        }
    }

    private static Set<String> paths(PackageManifest manifest) {
        Set<String> result = new LinkedHashSet<>();
        for (PackageManifest.FileEntry entry : manifest.fileList()) {
            // 校验用 Path 做，但入集用**原始文本**：Path 在 Windows 上会把 `/` 规范化成 `\`，
            // 而清单与计划的契约一律是 `/`（两边不一致会让「同一个文件」被判成两个）。
            PathSafety.requireRelative(entry.path(), "包内清单的路径");
            result.add(entry.path().trim());
        }
        return result;
    }
}
