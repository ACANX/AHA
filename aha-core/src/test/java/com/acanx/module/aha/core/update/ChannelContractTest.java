package com.acanx.module.aha.core.update;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通道契约：tag 与资产名的生成 / 解析（issue #51 §2.1；文法统一见 issue #269）。
 *
 * <p>新文法（0.1.4 起）是<br>
 * {@code aha-<product>-<form>-<version>-<os>-<arch>-<runtime>.zip}；<br>
 * 旧名（0.1.3 及更早）在过渡期内仍要能解析，故本类同时钉住「新名生成」与「旧名兼容」两条线。</p>
 *
 * @since 0.2.0
 */
class ChannelContractTest {

    /** 各通道对应的版本形态（与 {@link ChannelContract#expectedSegments} 一致）。 */
    private static final AhaVersion BASELINE = AhaVersion.parse("0.1.3");

    private static final AhaVersion BUILD = AhaVersion.parse("0.1.3.00242");

    private static final AhaVersion SNAPSHOT = AhaVersion.parse("0.1.3.00242.10161125");

    private static AhaVersion versionOf(ReleaseChannel channel) {
        return switch (channel) {
            case STABLE -> BASELINE;
            case BETA -> BUILD;
            case ALPHA -> SNAPSHOT;
        };
    }

    @Test
    void buildsAssetNamesInTheUnifiedGrammar() {
        // dev 线（Beta）：四组合共用一套文法，平台段与 runtime 段都显式。
        assertThat(ChannelContract.assetName(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                BUILD, "windows-amd64", 25))
                .isEqualTo("aha-desktop-jvm-0.1.3.00242-windows-amd64-jdk25.zip");
        assertThat(ChannelContract.assetName(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                BUILD, "macos-aarch64", 27))
                .isEqualTo("aha-desktop-jvm-0.1.3.00242-macos-aarch64-jdk27.zip");
        assertThat(ChannelContract.assetName(ReleaseChannel.BETA, UpdateKind.CLI_JVM,
                BUILD, "ubuntu-amd64", 25))
                .isEqualTo("aha-cli-jvm-0.1.3.00242-ubuntu-amd64-jdk25.zip");
        assertThat(ChannelContract.assetName(ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE,
                BUILD, "ubuntu-amd64", 25))
                .isEqualTo("aha-desktop-native-0.1.3.00242-ubuntu-amd64-graalvm25.zip");
        assertThat(ChannelContract.assetName(ReleaseChannel.BETA, UpdateKind.CLI_NATIVE,
                BUILD, "windows-amd64", 25))
                .isEqualTo("aha-cli-native-0.1.3.00242-windows-amd64-graalvm25.zip");

        // 正式发版（Stable）：同一套文法，原生形态自 0.1.4 起也进 Stable（issue #269 §3）。
        assertThat(ChannelContract.assetName(ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM,
                BASELINE, "windows-amd64", 25))
                .isEqualTo("aha-desktop-jvm-0.1.3-windows-amd64-jdk25.zip");
        assertThat(ChannelContract.assetName(ReleaseChannel.STABLE, UpdateKind.CLI_JVM,
                BASELINE, "macos-aarch64", 25))
                .isEqualTo("aha-cli-jvm-0.1.3-macos-aarch64-jdk25.zip");
        assertThat(ChannelContract.assetName(ReleaseChannel.STABLE, UpdateKind.DESKTOP_NATIVE,
                BASELINE, "ubuntu-amd64", 25))
                .isEqualTo("aha-desktop-native-0.1.3-ubuntu-amd64-graalvm25.zip");

        // 快照（Alpha）：同样是新文法（tag 仍是 -AHA-…-SNAPSHOT）。
        assertThat(ChannelContract.assetName(ReleaseChannel.ALPHA, UpdateKind.DESKTOP_JVM,
                SNAPSHOT, "windows-amd64", 25))
                .isEqualTo("aha-desktop-jvm-0.1.3.00242.10161125-windows-amd64-jdk25.zip");
        assertThat(ChannelContract.assetName(ReleaseChannel.ALPHA, UpdateKind.CLI_NATIVE,
                SNAPSHOT, "ubuntu-amd64", 25))
                .isEqualTo("aha-cli-native-0.1.3.00242.10161125-ubuntu-amd64-graalvm25.zip");
    }

    @Test
    void buildsTagsForEverySupportedCombination() {
        assertThat(ChannelContract.tag(ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM, BASELINE))
                .isEqualTo("V0.1.3");
        assertThat(ChannelContract.tag(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM, BUILD))
                .isEqualTo("V0.1.3.00242-aha-desktop-jvm");
        assertThat(ChannelContract.tag(ReleaseChannel.BETA, UpdateKind.CLI_JVM, BUILD))
                .isEqualTo("V0.1.3.00242-aha-cli-jvm");
        assertThat(ChannelContract.tag(ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE, BUILD))
                .isEqualTo("V0.1.3.00242-aha-desktop-native");
        assertThat(ChannelContract.tag(ReleaseChannel.BETA, UpdateKind.CLI_NATIVE, BUILD))
                .isEqualTo("V0.1.3.00242-aha-cli-native");
        assertThat(ChannelContract.tag(ReleaseChannel.ALPHA, UpdateKind.DESKTOP_JVM, SNAPSHOT))
                .isEqualTo("V0.1.3.00242.10161125-AHA-desktop-jvm-SNAPSHOT");
        assertThat(ChannelContract.tag(ReleaseChannel.ALPHA, UpdateKind.CLI_NATIVE, SNAPSHOT))
                .isEqualTo("V0.1.3.00242.10161125-AHA-cli-native-SNAPSHOT");
    }

    @Test
    void tagRoundTripsForEverySupportedCombination() {
        for (ReleaseChannel channel : ReleaseChannel.values()) {
            for (UpdateKind kind : UpdateKind.values()) {
                AhaVersion version = versionOf(channel);
                String tag = ChannelContract.tag(channel, kind, version);
                Optional<AhaVersion> parsed = ChannelContract.versionOfTag(channel, kind, tag);
                assertThat(parsed)
                        .as("%s × %s 的 tag 往返（%s）", channel.key(), kind.targetWord(), tag)
                        .contains(version);
            }
        }
    }

    @Test
    void acceptsLegacyTagsFromBeforeTheRename() {
        // 0.1.3 的 dev JVM tag 是 -aha-jvm（两个 JVM 形态共用）。
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                "V0.1.3.00242-aha-jvm")).contains(BUILD);
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.BETA, UpdateKind.CLI_JVM,
                "V0.1.3.00242-aha-jvm")).contains(BUILD);
        // 快照旧 tag 用大写驼峰目标词。
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.ALPHA, UpdateKind.DESKTOP_NATIVE,
                "V0.1.3.00242.10161125-AHA-Desktop-Native-SNAPSHOT")).contains(SNAPSHOT);
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.ALPHA, UpdateKind.DESKTOP_JVM,
                "V0.1.3.00242.10161125-AHA-Desktop-JVM-SNAPSHOT")).contains(SNAPSHOT);
    }

    @Test
    void versionOfTagRejectsOtherChannelsAndKinds() {
        // 通道对不上
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM,
                "V0.1.3.00242-aha-desktop-jvm")).isEmpty();
        // 形态对不上（同名 tag 的另一种目标词）
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE,
                "V0.1.3.00242-aha-desktop-jvm")).isEmpty();
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.BETA, UpdateKind.CLI_NATIVE,
                "V0.1.3.00242-aha-desktop-native")).isEmpty();
        // 版本段数与通道不符
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.ALPHA, UpdateKind.DESKTOP_JVM,
                "V0.1.3.00242-AHA-desktop-jvm-SNAPSHOT")).isEmpty();
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                "V0.1.3")).isEmpty();
        // 空值安全
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM, null))
                .isEmpty();
    }

    @Test
    void legacyBareTagIsNotAChannelTag() {
        // 0.1.0 是历史上唯一一个裸 tag（无 V 前缀），已补别名 V0.1.0；
        // 通道只认带 V 的正式形态（ReleaseProcess.md §4.1）。
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM,
                "0.1.0")).isEmpty();
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM,
                "V0.1.0")).contains(AhaVersion.parse("0.1.0"));
    }

    @Test
    void assetRoundTripsThroughParse() {
        record Case(ReleaseChannel channel, UpdateKind kind, AhaVersion version, String platform,
                    int jdk) {
        }
        List<Case> cases = List.of(
                new Case(ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM, BASELINE, "windows-amd64", 25),
                new Case(ReleaseChannel.STABLE, UpdateKind.CLI_JVM, BASELINE, "ubuntu-amd64", 25),
                new Case(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM, BUILD, "ubuntu-amd64", 27),
                new Case(ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE, BUILD, "ubuntu-amd64", 25),
                new Case(ReleaseChannel.BETA, UpdateKind.CLI_NATIVE, BUILD, "macos-aarch64", 25),
                new Case(ReleaseChannel.ALPHA, UpdateKind.DESKTOP_JVM, SNAPSHOT, "windows-amd64", 25),
                new Case(ReleaseChannel.ALPHA, UpdateKind.CLI_NATIVE, SNAPSHOT, "ubuntu-amd64", 25));

        for (Case item : cases) {
            String asset = ChannelContract.assetName(item.channel(), item.kind(), item.version(),
                    item.platform(), item.jdk());
            Optional<ChannelContract.Asset> parsed =
                    ChannelContract.parseAsset(item.channel(), item.kind(), asset);
            assertThat(parsed).as("资产名往返：%s", asset).isPresent();
            assertThat(parsed.get().version()).isEqualTo(item.version());
            assertThat(parsed.get().platformTag()).isEqualTo(item.platform());
            assertThat(parsed.get().jdk()).isEqualTo(item.jdk());
        }
    }

    @Test
    void parsesLegacyAssetNamesFromBeforeTheRename() {
        // 0.1.3 的 JVM 桌面包：默认变体不带 -jdk 段。
        Optional<ChannelContract.Asset> desktop = ChannelContract.parseAsset(
                ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                "aha-desktop-jvm-0.1.3.00242-windows-amd64-jdk25.zip");
        assertThat(desktop).isPresent();
        assertThat(desktop.get().jdk()).isEqualTo(25);
        assertThat(desktop.get().platformTag()).isEqualTo("windows-amd64");

        // 0.1.3 的 CLI-JVM 是平台无关的一份包（解析结果 platform 为 null）。
        Optional<ChannelContract.Asset> cli = ChannelContract.parseAsset(
                ReleaseChannel.STABLE, UpdateKind.CLI_JVM, "aha-cli-jvm-0.1.3-windows-amd64-jdk25.zip");
        assertThat(cli).isPresent();
        assertThat(cli.get().platformTag()).isNull();

        // 0.1.3 的原生包（大写驼峰前缀 + -jdk<JDK>）。
        Optional<ChannelContract.Asset> nativeAsset = ChannelContract.parseAsset(
                ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE,
                "aha-desktop-native-0.1.3.00242-ubuntu-amd64-graalvm25.zip");
        assertThat(nativeAsset).isPresent();
        assertThat(nativeAsset.get().jdk()).isEqualTo(25);
    }

    @Test
    void reportAndDiagnosticsArchivesAreNotUpgradeAssets() {
        // 诊断包 / 旧报告包与镜像包同族，绝不能被当成升级资产。
        assertThat(ChannelContract.parseAsset(ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE,
                "aha-desktop-native-0.1.3.00242-ubuntu-amd64-graalvm25-diagnostics.zip")).isEmpty();
        assertThat(ChannelContract.parseAsset(ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE,
                "AHA-Desktop-Native-0.1.3.00242-ubuntu-amd64-jdk25-Report.zip")).isEmpty();
        // 前缀不属于本形态
        assertThat(ChannelContract.parseAsset(ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE,
                "aha-desktop-jvm-0.1.3.00242-windows-amd64-jdk25.zip")).isEmpty();
        // 源码包 / 文档包
        assertThat(ChannelContract.parseAsset(ReleaseChannel.STABLE, UpdateKind.CLI_JVM,
                "aha-cli-jvm-0.1.3-ubuntu-amd64-jdk25-sources.zip")).isEmpty();
    }

    @Test
    void everyChannelSupportsEveryKindSince014() {
        for (ReleaseChannel channel : ReleaseChannel.values()) {
            for (UpdateKind kind : UpdateKind.values()) {
                assertThat(ChannelContract.supports(channel, kind))
                        .as("%s × %s", channel.key(), kind.targetWord())
                        .isTrue();
            }
        }
    }

    @Test
    void rejectsVersionFormThatDoesNotMatchChannel() {
        assertThatThrownBy(() -> ChannelContract.tag(ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM,
                BUILD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是 3 段");
        assertThatThrownBy(() -> ChannelContract.tag(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是 4 段");
        assertThatThrownBy(() -> ChannelContract.tag(ReleaseChannel.ALPHA, UpdateKind.DESKTOP_JVM,
                BUILD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须是 5 段");
    }

    @Test
    void rejectsMissingPlatformAndTooOldJdk() {
        // 四种形态都带平台段：缺平台段是调用方的错，不能默默忽略。
        assertThatThrownBy(() -> ChannelContract.assetName(ReleaseChannel.STABLE, UpdateKind.CLI_JVM,
                BASELINE, null, 25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须传入平台标签");
        assertThatThrownBy(() -> ChannelContract.assetName(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                BUILD, "  ", 25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须传入平台标签");
        assertThatThrownBy(() -> ChannelContract.assetName(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                BUILD, "windows-amd64", 21))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("JDK 段必须");
    }

    @Test
    void parsesChannelAndKindKeysLeniently() {
        assertThat(ReleaseChannel.of("beta")).isEqualTo(ReleaseChannel.BETA);
        assertThat(ReleaseChannel.of("  ALPHA ")).isEqualTo(ReleaseChannel.ALPHA);
        assertThat(ReleaseChannel.of(null)).isEqualTo(ReleaseChannel.DEFAULT);
        assertThat(ReleaseChannel.of("nonsense")).isEqualTo(ReleaseChannel.DEFAULT);

        assertThat(UpdateKind.of("Desktop-JVM")).isEqualTo(UpdateKind.DESKTOP_JVM);
        assertThat(UpdateKind.of("desktop_jvm")).isEqualTo(UpdateKind.DESKTOP_JVM);
        assertThat(UpdateKind.of("desktop-jvm")).isEqualTo(UpdateKind.DESKTOP_JVM);
        assertThat(UpdateKind.of(" cli-native ")).isEqualTo(UpdateKind.CLI_NATIVE);
        assertThat(UpdateKind.of("nope")).isNull();
        assertThat(UpdateKind.of(null)).isNull();
    }

    @Test
    void defaultChannelIsStable() {
        // issue #252 / TD-00019：默认通道已由临时值 Beta 改回 STABLE（2026-10-16）。
        // 这条断言是**故意**钉住的：改默认值必须同时改设计文档、用户指南与 TD，
        // 否则测试会红 —— 免得「改默认」被人忘掉或被顺手改掉。
        assertThat(ReleaseChannel.DEFAULT).isEqualTo(ReleaseChannel.STABLE);
        assertThat(ReleaseChannel.DEFAULT.key()).isEqualTo("stable");
    }
}
