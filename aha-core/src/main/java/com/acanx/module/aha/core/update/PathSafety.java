package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;

import java.nio.file.Path;

/**
 * 路径安全底线：包内清单与换装计划里的路径都必须落在根目录之内（issue #51 §4.3 / §4.4）。
 *
 * <p>包是要被解压到安装目录的，换装计划是要被外部 helper 照着执行的 —— 两处的路径一旦能逃出根，
 * 就等于允许往任意位置写 / 删文件。所以这条检查只放一处，谁用谁调。</p>
 *
 * @since 0.2.0
 */
final class PathSafety {

    private PathSafety() {
    }

    /**
     * 校验并返回「根内相对路径」。
     *
     * <p>拒绝：空、绝对路径、反斜杠、盘符冒号、任何 {@code ..} 段。
     * 反斜杠单独拒是因为契约要求用 {@code /}：Windows 上 {@code ..\\..\\x} 与
     * {@code C:/x} 都能逃出去，而在这之前就把它们挡掉最省事。</p>
     *
     * @param text 待校验的路径文本，可为 {@code null}
     * @param what 用于错误信息的说明（如「包内清单的路径」）
     * @return 相对路径
     * @throws UpdateException 不合法（{@link UpdateException#CODE_PACKAGE_INVALID}）
     */
    static Path requireRelative(String text, String what) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            throw invalid(what + "为空");
        }
        if (value.contains("\\") || value.contains(":")) {
            throw invalid(what + "含 Windows 分隔符或盘符（契约要求用 `/` 的相对路径）：" + value);
        }
        Path relative;
        try {
            relative = Path.of(value);
        } catch (RuntimeException e) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    what + "不合法：" + value, e);
        }
        if (relative.isAbsolute()) {
            throw invalid(what + "是绝对路径（契约要求相对路径）：" + value);
        }
        for (Path segment : relative) {
            if ("..".equals(segment.toString())) {
                throw invalid(what + "含 `..` 段（会写到根外）：" + value);
            }
        }
        return relative;
    }

    /**
     * 校验并返回「绝对路径」（换装计划里的安装根、备份目录、拉起命令必须绝对）。
     *
     * @param text 待校验的路径文本
     * @param what 用于错误信息的说明
     * @return 绝对路径
     * @throws UpdateException 不是绝对路径（{@link UpdateException#CODE_PACKAGE_INVALID}）
     */
    static Path requireAbsolute(String text, String what) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty() || !Path.of(value).isAbsolute()) {
            throw invalid(what + "必须是绝对路径（换装 helper 不依赖工作目录）：" + value);
        }
        return Path.of(value);
    }

    private static UpdateException invalid(String message) {
        return new UpdateException(UpdateException.CODE_PACKAGE_INVALID, message);
    }
}
