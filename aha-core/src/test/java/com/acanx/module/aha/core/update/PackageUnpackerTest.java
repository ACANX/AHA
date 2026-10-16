package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 解包器：正常解包 + **zip-slip 必须被拦**（issue #51 §4.3）。
 *
 * <p>条目名可以写成 {@code ../..}，朴素的解包会把文件写到目标目录之外。这条用例就是为它写的：
 * 宁可整包拒绝，也不要往目录外写文件。</p>
 *
 * @since 0.2.0
 */
class PackageUnpackerTest {

    @TempDir
    Path tempDir;

    private Path zipOf(String... names) throws IOException {
        Path zip = tempDir.resolve("pkg.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (String name : names) {
                out.putNextEntry(new ZipEntry(name));
                out.write(("content-of-" + name).getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        return zip;
    }

    @Test
    void unpacksFlatAndNestedEntries() throws IOException {
        Path zip = zipOf("aha-desktop.jar", "bin/aha.sh", "AHA-PACKAGE.json");
        Path target = tempDir.resolve("new");

        int count = PackageUnpacker.unpack(zip, target);

        assertThat(count).isEqualTo(3);
        assertThat(target.resolve("aha-desktop.jar")).exists();
        assertThat(target.resolve("bin").resolve("aha.sh")).exists();
        assertThat(target.resolve("AHA-PACKAGE.json")).exists();
    }

    @Test
    void refusesEntriesThatEscapeTheTargetDirectory() throws IOException {
        Path target = tempDir.resolve("new");

        for (String escaping : new String[] {"../evil.txt", "a/../../evil.txt", "/abs/evil.txt"}) {
            Path zip = zipOf(escaping);
            assertThatThrownBy(() -> PackageUnpacker.unpack(zip, target))
                    .as("条目 %s 必须被拒绝", escaping)
                    .isInstanceOf(UpdateException.class)
                    .satisfies(error -> assertThat(((UpdateException) error).code())
                            .isEqualTo(UpdateException.CODE_PACKAGE_INVALID));
        }
        // 目录外一个文件都不该出现（zip-slip 的真实危害就在这儿）
        assertThat(tempDir.resolve("evil.txt")).doesNotExist();
    }

    @Test
    void refusesAMissingPackage() {
        assertThatThrownBy(() -> PackageUnpacker.unpack(tempDir.resolve("nope.zip"),
                tempDir.resolve("new")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("包不存在");
    }

    @Test
    void overwritesExistingFilesSoRepeatedStagingIsIdempotent() throws IOException {
        Path zip = zipOf("aha-desktop.jar");
        Path target = tempDir.resolve("new");
        Files.createDirectories(target);
        Files.writeString(target.resolve("aha-desktop.jar"), "STALE");

        PackageUnpacker.unpack(zip, target);
        PackageUnpacker.unpack(zip, target);

        assertThat(Files.readString(target.resolve("aha-desktop.jar")))
                .isEqualTo("content-of-aha-desktop.jar");
        assertThat(List.of(target.toFile().listFiles())).hasSize(1);
    }
}
