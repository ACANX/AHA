package com.acanx.module.aha.core.update;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通道与形态的过滤、排序（issue #51 §4.1）。
 *
 * <p>fixture 来源：{@code GitHubReleasesBeta.json} / {@code GitHubReleasesFormal.json} 是
 * 2026-10-16 的真实发布页投影；{@code GitHubReleasesAlphaSynthesized.json} 是<b>合成</b>的 ——
 * 快照 release 有 7 天有效期、PR 合并后即清理，实查时（含翻到第 3 页）一条存活的都没有，
 * 所以它按 {@code BuildSpec.md} §11.1 的命名契约合成，**不能**当作「实查证据」。</p>
 *
 * @since 0.2.0
 */
class ReleaseFilterTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private static final List<ReleaseInfo> BETA = load("GitHubReleasesBeta.json");

    private static final List<ReleaseInfo> FORMAL = load("GitHubReleasesFormal.json");

    private static final List<ReleaseInfo> ALPHA = load("GitHubReleasesAlphaSynthesized.json");

    private static List<ReleaseInfo> load(String name) {
        try (InputStream in = ReleaseFilterTest.class.getResourceAsStream("/Fixtures/" + name)) {
            assertThat(in).as("fixture 必须存在：%s", name).isNotNull();
            return JSON.readValue(new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    new TypeReference<List<ReleaseInfo>>() {
                    });
        } catch (IOException e) {
            throw new IllegalStateException("读取 fixture 失败：" + name, e);
        }
    }

    @Test
    void selectsBetaJvmReleasesNewestFirst() {
        List<ReleaseFilter.ChannelRelease> jvm =
                ReleaseFilter.select(BETA, ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM);

        assertThat(jvm).extracting(ReleaseFilter.ChannelRelease::tag).containsExactly(
                "V0.1.3.00242-aha-jvm",
                "V0.1.3.00241-aha-jvm");
        assertThat(jvm).allSatisfy(release -> {
            assertThat(release.prerelease()).isTrue();
            assertThat(release.assets()).hasSize(8);
            assertThat(release.publishedAt()).isNotNull();
        });
        assertThat(jvm.get(0).version()).isEqualTo(AhaVersion.parse("0.1.3.00242"));
    }

    @Test
    void legacyCombinedTagStillMatchesBothKinds() {
        // 0.1.5 之前的历史形态：一条 `-aha-jvm` release 里装两个形态的资产，
        // 因此两个形态看到的是同一批 tag。issue #347 起新版本按形态拆成两条，
        // 新行为见 splitJvmReleasesAreSelectedPerKind。
        List<String> desktop = ReleaseFilter.select(BETA, ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM)
                .stream().map(ReleaseFilter.ChannelRelease::tag).toList();
        List<String> cli = ReleaseFilter.select(BETA, ReleaseChannel.BETA, UpdateKind.CLI_JVM)
                .stream().map(ReleaseFilter.ChannelRelease::tag).toList();

        assertThat(cli).isEqualTo(desktop);
    }

    @Test
    void splitJvmReleasesAreSelectedPerKind() {
        // issue #347：dev 线 JVM 与原生线一样按形态各发一条 release；
        // 旧合并 tag `-aha-jvm` 仍进两种形态的候选（历史 release 不掉线）。
        List<ReleaseInfo> split = JSON.readValue("""
                [
                  {"tag_name": "V0.1.5.00346-aha-desktop-jvm", "prerelease": true, "draft": false, "assets": []},
                  {"tag_name": "V0.1.5.00346-aha-cli-jvm", "prerelease": true, "draft": false, "assets": []},
                  {"tag_name": "V0.1.5.00345-aha-jvm", "prerelease": true, "draft": false, "assets": []}
                ]
                """, new TypeReference<List<ReleaseInfo>>() {
        });

        List<String> desktop = ReleaseFilter.select(split, ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM)
                .stream().map(ReleaseFilter.ChannelRelease::tag).toList();
        List<String> cli = ReleaseFilter.select(split, ReleaseChannel.BETA, UpdateKind.CLI_JVM)
                .stream().map(ReleaseFilter.ChannelRelease::tag).toList();

        assertThat(desktop).containsExactly(
                "V0.1.5.00346-aha-desktop-jvm",
                "V0.1.5.00345-aha-jvm");
        assertThat(cli).containsExactly(
                "V0.1.5.00346-aha-cli-jvm",
                "V0.1.5.00345-aha-jvm");
    }

    @Test
    void keepsNativeKindSeparateFromJvmKind() {
        List<String> nativeTags = ReleaseFilter
                .select(BETA, ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE)
                .stream().map(ReleaseFilter.ChannelRelease::tag).toList();

        assertThat(nativeTags).containsExactly(
                "V0.1.3.00242-aha-desktop-native",
                "V0.1.3.00241-aha-desktop-native");
        // 原生 tag 不得出现在 JVM 形态的候选里（反之亦然）：形态混升是硬约束禁止的。
        assertThat(ReleaseFilter.select(BETA, ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM))
                .extracting(ReleaseFilter.ChannelRelease::tag)
                .doesNotContainAnyElementsOf(nativeTags);
    }

    @Test
    void stableSeesOnlyFormalReleasesWhichCurrentlyHaveNoAssets() {
        List<ReleaseFilter.ChannelRelease> stable =
                ReleaseFilter.select(FORMAL, ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM);

        // 裸 tag `0.1.0` 不属于正式 tag 形态（正式形态是 `V<x.y.z>`），因此不进候选。
        assertThat(stable).extracting(ReleaseFilter.ChannelRelease::tag)
                .containsExactly("V0.1.2", "V0.1.1");
        // 这正是当前的真实状态：正式 release 的资产数为 0（issue #51 §1.2 实查），
        // 界面必须能说清「本通道暂无可用版本」而不是「已是最新」。
        assertThat(stable).allSatisfy(release -> assertThat(release.assets()).isEmpty());
    }

    @Test
    void stableNeverSeesPreReleases() {
        assertThat(ReleaseFilter.select(BETA, ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM))
                .isEmpty();
        assertThat(ReleaseFilter.select(ALPHA, ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM))
                .isEmpty();
    }

    @Test
    void alphaListsSnapshotsOnlyForTheirOwnTarget() {
        List<String> desktopJvm = ReleaseFilter
                .select(ALPHA, ReleaseChannel.ALPHA, UpdateKind.DESKTOP_JVM)
                .stream().map(ReleaseFilter.ChannelRelease::tag).toList();
        List<String> desktopNative = ReleaseFilter
                .select(ALPHA, ReleaseChannel.ALPHA, UpdateKind.DESKTOP_NATIVE)
                .stream().map(ReleaseFilter.ChannelRelease::tag).toList();
        List<String> cliJvm = ReleaseFilter
                .select(ALPHA, ReleaseChannel.ALPHA, UpdateKind.CLI_JVM)
                .stream().map(ReleaseFilter.ChannelRelease::tag).toList();

        assertThat(desktopJvm).containsExactly(
                "V0.1.3.00233.10161125-AHA-Desktop-JVM-SNAPSHOT",
                "V0.1.3.00228.10091200-AHA-Desktop-JVM-SNAPSHOT");
        assertThat(desktopNative).containsExactly(
                "V0.1.3.00233.10161125-AHA-Desktop-Native-SNAPSHOT");
        assertThat(cliJvm).containsExactly(
                "V0.1.3.00233.10161125-AHA-Cli-JVM-SNAPSHOT");
    }

    @Test
    void hidesDrafts() {
        // 未认证请求本就不会返回草稿，但带上凭据时会出现 —— 草稿对用户不可见，必须排掉。
        List<ReleaseInfo> withDraft = JSON.readValue("""
                [
                  {"tag_name": "V0.1.4", "prerelease": false, "draft": true, "assets": []},
                  {"tag_name": "V0.1.3", "prerelease": false, "draft": false, "assets": []}
                ]
                """, new TypeReference<List<ReleaseInfo>>() {
        });

        assertThat(ReleaseFilter.select(withDraft, ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM))
                .extracting(ReleaseFilter.ChannelRelease::tag)
                .containsExactly("V0.1.3");
    }
}
