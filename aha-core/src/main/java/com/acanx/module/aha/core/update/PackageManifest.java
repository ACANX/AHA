package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * 包内清单 {@code AHA-PACKAGE.json}：包根那份「这个包里有哪些文件、各自多大、哈希多少」
 * （issue #51 §4.3 第②层、§6）。
 *
 * <p>它同时解决三件事：① 替换时知道<b>哪些文件属于包</b>；② 版本升级时能识别<b>新包删掉的旧文件</b>
 * （否则会留下孤儿文件）；③ 解压后逐文件核对大小与哈希，比「解压出来试着启动一下」更早发现问题。</p>
 *
 * <p>本类的 {@link #verify} 是<b>硬门</b>：任一项不符就抛 {@code UPDATE_PACKAGE_INVALID}，
 * 调用方据此中止落地。路径安全（拒绝绝对路径与 {@code ..}）也在这里拦 —— 包是要被解压到安装目录的，
 * 条目路径能逃出包根就等同于允许任意写文件。</p>
 *
 * @param kind     形态（{@code desktop-jvm} / {@code desktop-native} / {@code cli-jvm} / {@code cli-native}）
 * @param version  版本
 * @param platform 平台标签（平台无关的形态可为空）
 * @param jdk      JDK 大版本
 * @param files    文件条目
 * @since 0.2.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PackageManifest(
        @JsonProperty("kind") String kind,
        @JsonProperty("version") String version,
        @JsonProperty("platform") String platform,
        @JsonProperty("jdk") int jdk,
        @JsonProperty("files") List<FileEntry> files) {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    /**
     * 清单里的一个文件。
     *
     * @param path   包内相对路径（{@code aha-desktop.jar}、{@code lib/foo.jar}）
     * @param sha256 小写十六进制摘要
     * @param size   字节数
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FileEntry(
            @JsonProperty("path") String path,
            @JsonProperty("sha256") String sha256,
            @JsonProperty("size") long size) {
    }

    /**
     * 解析清单 JSON。
     *
     * @param json JSON 文本
     * @return 清单
     * @throws UpdateException 内容无法解析（{@link UpdateException#CODE_PACKAGE_INVALID}）
     */
    public static PackageManifest parse(String json) {
        try {
            PackageManifest manifest = JSON.readValue(json, new TypeReference<PackageManifest>() {
            });
            if (manifest == null) {
                throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                        "AHA-PACKAGE.json 内容为空");
            }
            return manifest;
        } catch (RuntimeException e) {
            if (e instanceof UpdateException update) {
                throw update;
            }
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "AHA-PACKAGE.json 无法解析：" + e.getClass().getSimpleName() + ": " + e.getMessage(),
                    e);
        }
    }

    /**
     * 读取清单文件。
     *
     * @param file 清单文件路径
     * @return 清单
     * @throws UpdateException 文件不存在（{@link UpdateException#CODE_PACKAGE_INVALID}）或内容无法解析
     */
    public static PackageManifest read(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "包里没有 AHA-PACKAGE.json：" + file + "（该包不合格，不能用来自我替换）");
        }
        try {
            return parse(Files.readString(file));
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "读取 AHA-PACKAGE.json 失败：" + file + "（" + e.getMessage() + "）", e);
        }
    }

    /**
     * 清单声明的形态。
     *
     * @return 形态；清单里写了不认识的形态时为空
     */
    public Optional<UpdateKind> updateKind() {
        return Optional.ofNullable(UpdateKind.of(kind));
    }

    /**
     * 文件条目（永不为 {@code null}）。
     *
     * @return 不可变列表
     */
    public List<FileEntry> fileList() {
        return files == null ? List.of() : List.copyOf(files);
    }

    /**
     * 逐项核对：形态 / 版本（/ 平台）与期望一致，且每个文件都真实存在且大小、哈希相符。
     *
     * @param root           包根目录（解压后的目录）
     * @param expectedKind   期望形态
     * @param expectedVersion 期望版本
     * @param expectedPlatform 期望平台标签；形态不带平台段时传 {@code null}
     * @throws UpdateException 任一项不符（{@link UpdateException#CODE_PACKAGE_INVALID}）
     */
    public void verify(Path root, UpdateKind expectedKind, AhaVersion expectedVersion,
                       PlatformTag expectedPlatform) {
        verifyDeclarations(expectedKind, expectedVersion, expectedPlatform);
        if (fileList().isEmpty()) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "AHA-PACKAGE.json 里没有任何文件条目，无法据此落地");
        }
        for (FileEntry entry : fileList()) {
            verifyEntry(root, entry);
        }
    }

    /** 名称三件套（形态 / 版本 / 平台）必须与期望一致 —— 防「挂错包 / 版本对不上」。 */
    private void verifyDeclarations(UpdateKind expectedKind, AhaVersion expectedVersion,
                                    PlatformTag expectedPlatform) {
        UpdateKind declared = updateKind().orElseThrow(() -> new UpdateException(
                UpdateException.CODE_PACKAGE_INVALID,
                "AHA-PACKAGE.json 的 kind 不认识：" + kind + "（期望 " + expectedKind.targetWord() + "）"));
        if (declared != expectedKind) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "包的形态对不上：清单写 " + kind + "，当前运行的是 " + expectedKind.targetWord());
        }
        AhaVersion declaredVersion = AhaVersion.parse(version);
        if (!declaredVersion.equals(expectedVersion)) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "包的版本对不上：清单写 " + version + "，期望 " + expectedVersion.value());
        }
        if (expectedPlatform != null && expectedKind.hasPlatform()
                && !expectedPlatform.value().equals(platform)) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "包的平台对不上：清单写 " + platform + "，本机是 " + expectedPlatform.value());
        }
    }

    /** 单个条目：路径安全 → 存在 → 大小 → 哈希。 */
    private void verifyEntry(Path root, FileEntry entry) {
        Path relative = safeRelativePath(entry);
        Path file = root.resolve(relative);
        if (!file.normalize().startsWith(root.normalize())) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "包内清单的路径逃出了包根：" + entry.path());
        }
        if (!Files.isRegularFile(file)) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "包内清单里的文件不存在：" + entry.path() + "（该包不完整，可能是解压失败或被截断）");
        }
        try {
            long size = Files.size(file);
            if (size != entry.size()) {
                throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                        "包内文件大小不符：" + entry.path() + "；清单 " + entry.size()
                                + "，实际 " + size);
            }
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "读取包内文件大小失败：" + entry.path() + "（" + e.getMessage() + "）", e);
        }
        String actual = Checksums.sha256(file);
        if (entry.sha256() == null || !actual.equalsIgnoreCase(entry.sha256())) {
            throw new UpdateException(UpdateException.CODE_PACKAGE_INVALID,
                    "包内文件校验和不符：" + entry.path() + "；清单 " + entry.sha256()
                            + "，实际 " + actual + "（该包不可信，已拒绝落地）");
        }
    }

    /** 路径安全：复用 {@link PathSafety}（包内清单与换装计划用同一套底线）。 */
    private static Path safeRelativePath(FileEntry entry) {
        return PathSafety.requireRelative(entry.path(), "包内清单的路径");
    }
}
