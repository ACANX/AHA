package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import com.acanx.module.aha.core.update.ReleaseFilter.ChannelRelease;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 挑资产：形态 → 平台标签 → JDK 轴（issue #51 §2.1、§4.1）。
 *
 * <p>本类的核心用例是 <b>issue #210 的反例</b>：最新版缺本平台的腿时，必须**响亮报错并列出该版本
 * 实际资产**，绝不能静默退到更旧的版本 —— 那会表现为「升级成功、版本还是旧的」。</p>
 *
 * <p>fixture 与 {@link ReleaseFilterTest} 共用（Beta 为真实投影，Alpha 为合成）。</p>
 *
 * @since 0.2.0
 */
class AssetSelectorTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private static final PlatformTag WINDOWS = PlatformTag.of("Windows 11", "amd64");

    private static final PlatformTag WINDOWS_ARM = PlatformTag.of("Windows 11", "aarch64");

    private static final PlatformTag MACOS_ARM = PlatformTag.of("Mac OS X", "aarch64");

    private static final List<ChannelRelease> BETA_DESKTOP_JVM =
            candidates("GitHubReleasesBeta.json", ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM);

    private static final List<ChannelRelease> BETA_DESKTOP_NATIVE =
            candidates("GitHubReleasesBeta.json", ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE);

    private static final List<ChannelRelease> BETA_CLI_JVM =
            candidates("GitHubReleasesBeta.json", ReleaseChannel.BETA, UpdateKind.CLI_JVM);

    private static final List<ChannelRelease> BETA_CLI_NATIVE =
            candidates("GitHubReleasesBeta.json", ReleaseChannel.BETA, UpdateKind.CLI_NATIVE);

    private static final List<ChannelRelease> ALPHA_DESKTOP_JVM =
            candidates("GitHubReleasesAlphaSynthesized.json", ReleaseChannel.ALPHA, UpdateKind.DESKTOP_JVM);

    private static List<ChannelRelease> candidates(String fixture, ReleaseChannel channel,
                                                   UpdateKind kind) {
        return ReleaseFilter.select(load(fixture), channel, kind);
    }

    private static List<ReleaseInfo> load(String fixture) {
        try (InputStream in = AssetSelectorTest.class.getResourceAsStream("/Fixtures/" + fixture)) {
            assertThat(in).as("fixture 必须存在：%s", fixture).isNotNull();
            return read(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("读取 fixture 失败：" + fixture, e);
        }
    }

    private static List<ReleaseInfo> read(String json) {
        return JSON.readValue(json, new TypeReference<List<ReleaseInfo>>() {
        });
    }

    private static ChannelRelease release(String tag, String version, String... assetNames) {
        return new ReleaseFilter.ChannelRelease(tag, AhaVersion.parse(version), true, null,
                Arrays.stream(assetNames).map(AssetSelectorTest::uploadedAsset).toList());
    }

    private static AssetInfo uploadedAsset(String name) {
        return new AssetInfo(name, 1024, "https://example.invalid/" + name, "uploaded");
    }

    @Test
    void picksTheWindowsVariantOnTheDefaultJdkAxis() {
        AssetInfo asset = AssetSelector.select(BETA_DESKTOP_JVM.get(0), ReleaseChannel.BETA,
                UpdateKind.DESKTOP_JVM, WINDOWS);

        // 该版本同时有 `…-windows-amd64.zip` 与 `…-windows-amd64-jdk27.zip`：默认必须取前者
        assertThat(asset.name()).isEqualTo("aha-desktop-0.1.3.00242-windows-amd64.zip");
    }

    @Test
    void picksTheUbuntuAndMacosVariantsOfTheSameRelease() {
        ChannelRelease release = BETA_DESKTOP_JVM.get(0);

        assertThat(AssetSelector.select(release, ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM, WINDOWS).name())
                .isEqualTo("aha-desktop-0.1.3.00242-windows-amd64.zip");
        assertThat(AssetSelector.select(release, ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                PlatformTag.of("Linux", "x86_64")).name())
                .isEqualTo("aha-desktop-0.1.3.00242-ubuntu-amd64.zip");
        assertThat(AssetSelector.select(release, ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM, MACOS_ARM).name())
                .isEqualTo("aha-desktop-0.1.3.00242-macos-arm64.zip");
    }

    @Test
    void cliJvmAssetIsPlatformFree() {
        ChannelRelease release = BETA_CLI_JVM.get(0);

        String onWindows = AssetSelector.select(release, ReleaseChannel.BETA, UpdateKind.CLI_JVM, WINDOWS).name();
        String onMacos = AssetSelector.select(release, ReleaseChannel.BETA, UpdateKind.CLI_JVM, MACOS_ARM).name();

        // 0.1.3 的 CLI-JVM 是平台无关的一份包（issue #269 起新包才带平台段）
        assertThat(onWindows).isEqualTo("aha-cli-0.1.3.00242.zip");
        assertThat(onMacos).isEqualTo(onWindows);
        // issue #269 起 CLI-JVM 按平台拆名（三平台），Windows ARM64 不在发布范围：
        // 与桌面包同一口径，明确报「本平台未发布」，而不是静默给出别的平台的包。
        assertThatThrownBy(() -> AssetSelector.select(release, ReleaseChannel.BETA,
                UpdateKind.CLI_JVM, WINDOWS_ARM))
                .isInstanceOf(UpdateException.class)
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_PLATFORM_UNPUBLISHED));
        // 形态不混：快照线的桌面端包不能被 CLI 形态挑走
        assertThat(AssetSelector.resolve(ALPHA_DESKTOP_JVM.get(0), ReleaseChannel.ALPHA,
                UpdateKind.CLI_JVM, WINDOWS)).isEmpty();
    }

    @Test
    void picksTheNativeExecutableAndIgnoresTheReportZip() {
        ChannelRelease release = BETA_DESKTOP_NATIVE.get(0);

        assertThat(AssetSelector.select(release, ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE, WINDOWS).name())
                .isEqualTo("AHA-Desktop-Native-0.1.3.00242-windows-amd64-jdk25.zip");
        // 同族的 `-Report.zip` 是构建报告，不是升级资产：它压根解析不出（名字不匹配），因此不在候选里
        assertThat(AssetSelector.resolve(release, ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE, WINDOWS))
                .isPresent()
                .get()
                .satisfies(asset -> assertThat(asset.name()).doesNotContain("-Report"));
    }

    @Test
    void cliNativeHasMacosButDesktopNativeDoesNot() {
        // 实查：`-aha-cli-native` 有 macOS 的腿，`-aha-desktop-native` 没有（只出 ubuntu / windows）
        assertThat(AssetSelector.resolve(BETA_CLI_NATIVE.get(0), ReleaseChannel.BETA,
                UpdateKind.CLI_NATIVE, MACOS_ARM)).isPresent();
        assertThatThrownBy(() -> AssetSelector.select(BETA_DESKTOP_NATIVE.get(0), ReleaseChannel.BETA,
                UpdateKind.DESKTOP_NATIVE, MACOS_ARM))
                .isInstanceOf(UpdateException.class)
                // 报错点名的是**本平台**的规范名（aarch64），不是资产名里的历史写法
                .hasMessageContaining("macos-aarch64")
                .hasMessageContaining("AHA-Desktop-Native-0.1.3.00242-windows-amd64-jdk25.zip");
    }

    @Test
    void acceptsBothAarch64AndLegacyArm64Spellings() {
        // 新产出一律 `macos-aarch64`；历史资产名 `macos-arm64` 仍要能选中（读取兼容，issue #287）。
        String json = """
                [
                  {"tag_name": "V0.1.3.00244-aha-jvm", "prerelease": true, "draft": false, "assets": [
                    {"name": "aha-desktop-jvm-0.1.3.00244-macos-aarch64-jdk25.zip", "size": 1,
                     "browser_download_url": "https://example.invalid/a.zip", "state": "uploaded"}]},
                  {"tag_name": "V0.1.3.00243-aha-jvm", "prerelease": true, "draft": false, "assets": [
                    {"name": "aha-desktop-0.1.3.00243-macos-arm64.zip", "size": 1,
                     "browser_download_url": "https://example.invalid/b.zip", "state": "uploaded"}]}
                ]
                """;
        List<ChannelRelease> candidates = ReleaseFilter.select(read(json),
                ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM);

        assertThat(AssetSelector.select(candidates.get(0), ReleaseChannel.BETA,
                UpdateKind.DESKTOP_JVM, MACOS_ARM).name())
                .isEqualTo("aha-desktop-jvm-0.1.3.00244-macos-aarch64-jdk25.zip");
        assertThat(AssetSelector.select(candidates.get(1), ReleaseChannel.BETA,
                UpdateKind.DESKTOP_JVM, MACOS_ARM).name())
                .isEqualTo("aha-desktop-0.1.3.00243-macos-arm64.zip");
    }

    @Test
    void picksTheSnapshotAsset() {
        assertThat(AssetSelector.select(ALPHA_DESKTOP_JVM.get(0), ReleaseChannel.ALPHA,
                UpdateKind.DESKTOP_JVM, WINDOWS).name())
                .isEqualTo("AHA-Desktop-JVM-0.1.3.00233.10161125-windows-amd64-jdk25.zip");
    }

    @Test
    void newestVersionMissingThisPlatformsLegMustFailInsteadOfFallingBack() {
        // 最新版 `00243` 只出了 ubuntu / macos 的腿，windows 的腿还没出来（构建中或上传中）
        List<ChannelRelease> candidates = ReleaseFilter.select(read("""
                [
                  {"tag_name": "V0.1.3.00243-aha-jvm", "prerelease": true, "draft": false, "assets": [
                    {"name": "aha-desktop-jvm-0.1.3.00243-ubuntu-amd64-jdk25.zip", "size": 1,
                     "browser_download_url": "https://example.invalid/u.zip", "state": "uploaded"},
                    {"name": "aha-desktop-0.1.3.00243-macos-arm64.zip", "size": 1,
                     "browser_download_url": "https://example.invalid/m.zip", "state": "uploaded"}]},
                  {"tag_name": "V0.1.3.00242-aha-jvm", "prerelease": true, "draft": false, "assets": [
                    {"name": "aha-desktop-jvm-0.1.3.00242-windows-amd64-jdk25.zip", "size": 1,
                     "browser_download_url": "https://example.invalid/w.zip", "state": "uploaded"}]}
                ]
                """), ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM);

        assertThat(candidates).extracting(ChannelRelease::tag)
                .containsExactly("V0.1.3.00243-aha-jvm", "V0.1.3.00242-aha-jvm");

        // 检查流程必须解析「最新的候选版本」⇒ 响亮报错，并列出该版本实际有哪些资产
        assertThatThrownBy(() -> AssetSelector.select(candidates.get(0), ReleaseChannel.BETA,
                UpdateKind.DESKTOP_JVM, WINDOWS))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("V0.1.3.00243-aha-jvm")
                .hasMessageContaining("aha-desktop-jvm-0.1.3.00243-ubuntu-amd64-jdk25.zip")
                .hasMessageContaining("aha-desktop-0.1.3.00243-macos-arm64.zip")
                .hasMessageContaining("不会退回到更旧的版本")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_ASSET_MISSING));

        // 列表界面拿到的是「装了不亏」的集合：最新可用是 00242 ——
        // 但它**不能**被当成「最新版本」（这正是 #210 的坑，所以判断有无更新必须用 select）
        assertThat(AssetSelector.available(candidates, ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM, WINDOWS))
                .extracting(AssetSelector.Available::tag)
                .containsExactly("V0.1.3.00242-aha-jvm");
    }

    @Test
    void reportsPlatformWithoutPublishedPackagesForPlatformBoundKinds() {
        assertThatThrownBy(() -> AssetSelector.select(BETA_DESKTOP_JVM.get(0), ReleaseChannel.BETA,
                UpdateKind.DESKTOP_JVM, WINDOWS_ARM))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("Windows ARM64")
                .hasMessageContaining("OpenJFX")
                .hasMessageContaining("手动到发布页取件")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_PLATFORM_UNPUBLISHED));
    }

    @Test
    void reportsMissingAssetWhenAReleaseHasNoNativePackage() {
        // 0.1.4 起 Stable 支持原生形态（issue #269）；但某个具体 release 没出原生包时，
        // 仍要响亮报错并列出该版本实际有哪些资产，绝不退到更旧的版本。
        ChannelRelease stable = release("V0.1.2", "0.1.2", "aha-desktop-jvm-0.1.2-windows-amd64-jdk25.zip");

        assertThatThrownBy(() -> AssetSelector.select(stable, ReleaseChannel.STABLE,
                UpdateKind.DESKTOP_NATIVE, WINDOWS))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("该版本没有本平台可用的包")
                .hasMessageContaining("aha-desktop-jvm-0.1.2-windows-amd64-jdk25.zip")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_ASSET_MISSING));
    }

    @Test
    void reportsDuplicateMatchesAsInvalidResponse() {
        // `-jdk25` 后缀本不该出现在默认变体上；一旦同时存在两个等价资产，命名契约就是重复的
        ChannelRelease release = release("V0.1.3.00242-aha-jvm", "0.1.3.00242",
                "aha-desktop-jvm-0.1.3.00242-windows-amd64-jdk25.zip",
                "aha-desktop-0.1.3.00242-windows-amd64-jdk25.zip");

        assertThatThrownBy(() -> AssetSelector.select(release, ReleaseChannel.BETA,
                UpdateKind.DESKTOP_JVM, WINDOWS))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("命名契约重复")
                .hasMessageContaining("aha-desktop-jvm-0.1.3.00242-windows-amd64-jdk25.zip")
                .hasMessageContaining("aha-desktop-0.1.3.00242-windows-amd64-jdk25.zip")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_INVALID_RESPONSE));
    }

    @Test
    void ignoresAssetsWhoseVersionDoesNotMatchTheTag() {
        ChannelRelease release = release("V0.1.3.00242-aha-jvm", "0.1.3.00242",
                "aha-desktop-jvm-0.1.3.00999-windows-amd64-jdk25.zip");

        assertThat(AssetSelector.resolve(release, ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM, WINDOWS))
                .isEmpty();
        assertThatThrownBy(() -> AssetSelector.select(release, ReleaseChannel.BETA,
                UpdateKind.DESKTOP_JVM, WINDOWS))
                .hasMessageContaining("aha-desktop-jvm-0.1.3.00999-windows-amd64-jdk25.zip");
    }

    @Test
    void ignoresAssetsThatAreNotUploadedYet() {
        ChannelRelease release = new ReleaseFilter.ChannelRelease("V0.1.3.00242-aha-jvm",
                AhaVersion.parse("0.1.3.00242"), true, null, List.of(
                new AssetInfo("aha-desktop-jvm-0.1.3.00242-windows-amd64-jdk25.zip", 0, "https://example.invalid/w.zip",
                        "starting")));

        assertThat(AssetSelector.resolve(release, ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM, WINDOWS))
                .isEmpty();
    }

    @Test
    void hintsWhenOnlyTheExperimentalJdkVariantExists() {
        ChannelRelease release = release("V0.1.3.00242-aha-jvm", "0.1.3.00242",
                "aha-desktop-jvm-0.1.3.00242-windows-amd64-jdk27.zip");

        // 「资产列表里明明有本平台的包却挑不到」时必须说清原因（jdk27 是试验分支）
        assertThatThrownBy(() -> AssetSelector.select(release, ReleaseChannel.BETA,
                UpdateKind.DESKTOP_JVM, WINDOWS))
                .hasMessageContaining("只有 jdk27 变体")
                .hasMessageContaining("jdk25");
    }

    @Test
    void availableKeepsNewestFirstAndSkipsVersionsWithoutThisPlatformsLeg() {
        assertThat(AssetSelector.available(BETA_DESKTOP_JVM, ReleaseChannel.BETA,
                UpdateKind.DESKTOP_JVM, WINDOWS))
                .extracting(AssetSelector.Available::tag)
                .containsExactly("V0.1.3.00242-aha-jvm", "V0.1.3.00241-aha-jvm");
        assertThat(AssetSelector.available(BETA_DESKTOP_JVM, ReleaseChannel.BETA,
                UpdateKind.DESKTOP_JVM, WINDOWS))
                .extracting(AssetSelector.Available::version)
                .containsExactly(AhaVersion.parse("0.1.3.00242"), AhaVersion.parse("0.1.3.00241"));
    }
}
