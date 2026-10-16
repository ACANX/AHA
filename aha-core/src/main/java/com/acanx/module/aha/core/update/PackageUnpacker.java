package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 解包器：把下载好的 zip 解到暂存目录的 {@code new/} 下（issue #51 §4.3 第②层的前半）。
 *
 * <p><b>zip-slip 必须在这里拦</b>：条目名可以写成 {@code ../../……}，朴素的解包会把文件写到目标目录
 * 之外（历史上多起同类漏洞就是这么来的）。本类逐条校验解出来的绝对路径必须仍在目标目录之下，
 * 否则整包拒绝 —— 宁可这个包不能用，也不要往目录外写文件。</p>
 *
 * <p>只做解包，不做校验：校验交给 {@link PackageManifest#verify}（解包之后逐文件核对大小与哈希，
 * 并且能顺带发现「清单里有、包里没有」的文件）。</p>
 *
 * @since 0.2.0
 */
public final class PackageUnpacker {

    private PackageUnpacker() {
    }

    /**
     * 解包到目标目录（目录不存在则创建；已存在的同名文件会被覆盖）。
     *
     * @param zip         包
     * @param targetDir   目标目录
     * @return 解出来的文件数
     * @throws UpdateException 条目名逃出目标目录、或读写失败（{@link UpdateException#CODE_PACKAGE_INVALID}）
     */
    public static int unpack(Path zip, Path targetDir) {
        if (!Files.isRegularFile(zip)) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID, "包不存在：" + zip);
        }
        Path root = targetDir.toAbsolutePath().normalize();
        int count = 0;
        try {
            Files.createDirectories(root);
            try (ZipFile archive = new ZipFile(zip.toFile())) {
                Enumeration<? extends ZipEntry> entries = archive.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.isDirectory()) {
                        continue;
                    }
                    Path target = resolveInside(root, entry.getName());
                    // 目录条目之外的条目名仍可能是 "a/../b"：resolveInside 已经判过，
                    // 这里只保证父目录存在
                    Files.createDirectories(target.getParent());
                    try (InputStream in = archive.getInputStream(entry)) {
                        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                    count++;
                }
            }
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "解包失败：" + zip + "（" + e.getMessage() + "）", e);
        }
        return count;
    }

    /**
     * 把条目名解析成目标目录内的绝对路径。
     *
     * @param root 目标目录（绝对、已规范化）
     * @param name 条目名
     * @return 绝对路径
     * @throws UpdateException 条目名是绝对路径、含反斜杠或解出来逃出目标目录
     */
    private static Path resolveInside(Path root, String name) {
        String text = name == null ? "" : name.replace('\\', '/');
        if (text.isBlank()) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID, "包里有空条目名");
        }
        if (text.startsWith("/") || text.contains(":")) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "包里的条目是绝对路径：" + name);
        }
        Path resolved = root.resolve(text).toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "包里的条目逃出了目标目录（zip-slip）：" + name);
        }
        return resolved;
    }
}
