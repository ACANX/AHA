package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 包内清单 {@code AHA-PACKAGE.json} 的解析与逐项核对（issue #51 §4.3 第②层、§6）。
 *
 * <p>这些用例守的是「宁可拒绝、不可带病落地」：路径能逃出包根、文件被改过、大小不对、
 * 版本或形态对不上 —— 任一项都必须抛 {@code UPDATE_PACKAGE_INVALID} 中止落地。</p>
 *
 * @since 0.2.0
 */
class PackageManifestTest {

    private static final AhaVersion VERSION = AhaVersion.parse("0.1.3.00242");

    private static final PlatformTag WINDOWS = PlatformTag.of("Windows 11", "amd64");

    @TempDir
    Path packageRoot;

    @Test
    void verifiesAManifestThatMatchesThePackage() throws IOException {
        String jarDigest = write("aha-desktop.jar", "jar-bytes");
        String scriptDigest = write("bin/aha.sh", "#!/bin/sh\n");

        manifest("desktop-jvm", "0.1.3.00242", "windows-amd64",
                entry("aha-desktop.jar", jarDigest, 9),
                entry("bin/aha.sh", scriptDigest, 10))
                .verify(packageRoot, UpdateKind.DESKTOP_JVM, VERSION, WINDOWS);
    }

    @Test
    void ignoresUnknownFieldsAndParsesFromDisk() throws IOException {
        String digest = write("aha-desktop.jar", "jar-bytes");
        Path file = packageRoot.resolve("AHA-PACKAGE.json");
        Files.writeString(file, """
                {
                  "kind": "desktop-jvm",
                  "version": "0.1.3.00242",
                  "platform": "windows-amd64",
                  "jdk": 25,
                  "builtBy": "Release.yml",
                  "files": [
                    {"path": "aha-desktop.jar", "sha256": "%s", "size": 9, "mode": "0755"}
                  ]
                }
                """.formatted(digest));

        PackageManifest manifest = PackageManifest.read(file);

        assertThat(manifest.updateKind()).contains(UpdateKind.DESKTOP_JVM);
        assertThat(manifest.fileList()).hasSize(1);
        manifest.verify(packageRoot, UpdateKind.DESKTOP_JVM, VERSION, WINDOWS);
    }

    @Test
    void readRejectsAMissingManifestFile() {
        assertThatThrownBy(() -> PackageManifest.read(packageRoot.resolve("AHA-PACKAGE.json")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("包里没有 AHA-PACKAGE.json")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_PACKAGE_INVALID));
    }

    @Test
    void parseRejectsBrokenJson() {
        assertThatThrownBy(() -> PackageManifest.parse("{ not json"))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("无法解析")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_PACKAGE_INVALID));
    }

    @Test
    void rejectsAFileWhoseChecksumDiffers() throws IOException {
        String digest = write("aha-desktop.jar", "jar-bytes");
        // 落地之后文件被改动了（或被截断后又被拼回同样大小）
        Files.writeString(packageRoot.resolve("aha-desktop.jar"), "JAR-BYTES");

        assertThatThrownBy(() -> manifest("desktop-jvm", "0.1.3.00242", "windows-amd64",
                entry("aha-desktop.jar", digest, 9))
                .verify(packageRoot, UpdateKind.DESKTOP_JVM, VERSION, WINDOWS))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("包内文件校验和不符")
                .hasMessageContaining("已拒绝落地")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_PACKAGE_INVALID));
    }

    @Test
    void rejectsAFileWhoseSizeDiffers() throws IOException {
        String digest = write("aha-desktop.jar", "jar-bytes-longer");

        assertThatThrownBy(() -> manifest("desktop-jvm", "0.1.3.00242", "windows-amd64",
                entry("aha-desktop.jar", digest, 9))
                .verify(packageRoot, UpdateKind.DESKTOP_JVM, VERSION, WINDOWS))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("包内文件大小不符")
                .hasMessageContaining("清单 9")
                .hasMessageContaining("实际 16");
    }

    @Test
    void rejectsAMissingFile() {
        assertThatThrownBy(() -> manifest("desktop-jvm", "0.1.3.00242", "windows-amd64",
                entry("aha-desktop.jar", "0".repeat(64), 9))
                .verify(packageRoot, UpdateKind.DESKTOP_JVM, VERSION, WINDOWS))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("文件不存在");
    }

    @Test
    void rejectsPathsThatEscapeThePackage() throws IOException {
        write("aha-desktop.jar", "jar-bytes");
        String digest = Checksums.sha256(packageRoot.resolve("aha-desktop.jar"));

        // 包要被解压到安装目录：条目路径能逃出包根，就等于允许往任意位置写文件
        for (String escaping : new String[] {"../evil.txt", "/etc/passwd", "lib\\x.jar",
                "a/../../b.jar", "C:/windows/system32/x.dll"}) {
            assertThatThrownBy(() -> manifest("desktop-jvm", "0.1.3.00242", "windows-amd64",
                    entry(escaping, digest, 9))
                    .verify(packageRoot, UpdateKind.DESKTOP_JVM, VERSION, WINDOWS))
                    .as("路径 %s 必须被拒绝", escaping)
                    .isInstanceOf(UpdateException.class)
                    .satisfies(error -> assertThat(((UpdateException) error).code())
                            .isEqualTo(UpdateException.CODE_PACKAGE_INVALID));
        }
    }

    @Test
    void rejectsMismatchedKindVersionOrPlatform() throws IOException {
        String digest = write("aha-desktop.jar", "jar-bytes");

        assertThatThrownBy(() -> manifest("cli-jvm", "0.1.3.00242", "windows-amd64",
                entry("aha-desktop.jar", digest, 9))
                .verify(packageRoot, UpdateKind.DESKTOP_JVM, VERSION, WINDOWS))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("包的形态对不上");

        assertThatThrownBy(() -> manifest("desktop-jvm", "0.1.3.00241", "windows-amd64",
                entry("aha-desktop.jar", digest, 9))
                .verify(packageRoot, UpdateKind.DESKTOP_JVM, VERSION, WINDOWS))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("包的版本对不上");

        assertThatThrownBy(() -> manifest("desktop-jvm", "0.1.3.00242", "ubuntu-amd64",
                entry("aha-desktop.jar", digest, 9))
                .verify(packageRoot, UpdateKind.DESKTOP_JVM, VERSION, WINDOWS))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("包的平台对不上");

        assertThatThrownBy(() -> manifest("web-前端", "0.1.3.00242", "windows-amd64",
                entry("aha-desktop.jar", digest, 9))
                .verify(packageRoot, UpdateKind.DESKTOP_JVM, VERSION, WINDOWS))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("kind 不认识");
    }

    @Test
    void rejectsAnEmptyFileList() {
        assertThatThrownBy(() -> manifest("desktop-jvm", "0.1.3.00242", "windows-amd64")
                .verify(packageRoot, UpdateKind.DESKTOP_JVM, VERSION, WINDOWS))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("没有任何文件条目");
    }

    @Test
    void platformWithoutPlatformSegmentDoesNotRequireOne() throws IOException {
        String digest = write("aha-cli.jar", "jar-bytes");

        // 平台无关的形态（CLI JVM）：清单里不写 platform 也算合格
        manifest("cli-jvm", "0.1.3.00242", null, entry("aha-cli.jar", digest, 9))
                .verify(packageRoot, UpdateKind.CLI_JVM, VERSION, WINDOWS);
    }

    /** 在包根下写一个文件，返回它的摘要。 */
    private String write(String relative, String content) throws IOException {
        Path file = packageRoot.resolve(relative);
        Files.createDirectories(file.getParent() == null ? packageRoot : file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return Checksums.sha256(file);
    }

    private static String entry(String path, String sha256, long size) {
        return "{\"path\":\"" + path + "\",\"sha256\":\"" + sha256 + "\",\"size\":" + size + "}";
    }

    private static PackageManifest manifest(String kind, String version, String platform,
                                            String... entries) {
        return PackageManifest.parse("""
                {"kind":"%s","version":"%s","platform":%s,"jdk":25,"files":[%s]}
                """.formatted(kind, version,
                platform == null ? "null" : "\"" + platform + "\"",
                String.join(",", entries)));
    }
}
