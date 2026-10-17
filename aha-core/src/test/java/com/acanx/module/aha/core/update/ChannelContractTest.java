package com.acanx.module.aha.core.update;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通道契约：tag 与资产名的生成 / 解析（issue #51 §2.1）。
 *
 * <p>断言里的资产名<b>逐字抄自 2026-10-16 的发布页实查</b>（{@code gh api} 的资产列表），
 * 不是照文档猜的 —— 这份契约只要有一个人改错，三处实现就会漂移。</p>
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
    void buildsAssetNamesThatMatchTheLiveReleasePage() {
        // dev 线 JVM 包（V0.1.3.00242-aha-jvm 的 8 个资产）
        assertThat(ChannelContract.assetName(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                BUILD, "windows-amd64", 25)).isEqualTo("aha-desktop-0.1.3.00242-windows-amd64.zip");
        assertThat(ChannelContract.assetName(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                BUILD, "macos-aarch64", 27)).isEqualTo("aha-desktop-0.1.3.00242-macos-aarch64-jdk27.zip");
        assertThat(ChannelContract.assetName(ReleaseChannel.BETA, UpdateKind.CLI_JVM,
                BUILD, null, 25)).isEqualTo("aha-cli-0.1.3.00242.zip");
        assertThat(ChannelContract.assetName(ReleaseChannel.BETA, UpdateKind.CLI_JVM,
                BUILD, null, 27)).isEqualTo("aha-cli-0.1.3.00242-jdk27.zip");

        // dev 线原生包（V0.1.3.00242-aha-desktop-native）
        assertThat(ChannelContract.assetName(ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE,
                BUILD, "ubuntu-amd64", 25))
                .isEqualTo("AHA-Desktop-Native-0.1.3.00242-ubuntu-amd64-jdk25.zip");
        assertThat(ChannelContract.assetName(ReleaseChannel.BETA, UpdateKind.CLI_NATIVE,
                BUILD, "windows-amd64", 25))
                .isEqualTo("AHA-Cli-Native-0.1.3.00242-windows-amd64-jdk25.zip");

        // 正式发版（ReleaseProcess.md §3.1 / §3.2）
        assertThat(ChannelContract.assetName(ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM,
                BASELINE, "windows-amd64", 25)).isEqualTo("aha-desktop-0.1.3-windows-amd64.zip");
        assertThat(ChannelContract.assetName(ReleaseChannel.STABLE, UpdateKind.CLI_JVM,
                BASELINE, null, 25)).isEqualTo("aha-cli-0.1.3.zip");

        // 快照（BuildSpec.md §11.1：JVM 线只有 JDK 25，资产名里始终带 -jdk 段）
        assertThat(ChannelContract.assetName(ReleaseChannel.ALPHA, UpdateKind.DESKTOP_JVM,
                SNAPSHOT, "windows-amd64", 25))
                .isEqualTo("AHA-Desktop-JVM-0.1.3.00242.10161125-windows-amd64-jdk25.zip");
        assertThat(ChannelContract.assetName(ReleaseChannel.ALPHA, UpdateKind.DESKTOP_NATIVE,
                SNAPSHOT, "ubuntu-amd64", 25))
                .isEqualTo("AHA-Desktop-Native-0.1.3.00242.10161125-ubuntu-amd64-jdk25.zip");
    }

    @Test
    void buildsTagsForEverySupportedCombination() {
        assertThat(ChannelContract.tag(ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM, BASELINE))
                .isEqualTo("V0.1.3");
        assertThat(ChannelContract.tag(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM, BUILD))
                .isEqualTo("V0.1.3.00242-aha-jvm");
        assertThat(ChannelContract.tag(ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE, BUILD))
                .isEqualTo("V0.1.3.00242-aha-desktop-native");
        assertThat(ChannelContract.tag(ReleaseChannel.BETA, UpdateKind.CLI_NATIVE, BUILD))
                .isEqualTo("V0.1.3.00242-aha-cli-native");
        assertThat(ChannelContract.tag(ReleaseChannel.ALPHA, UpdateKind.DESKTOP_JVM, SNAPSHOT))
                .isEqualTo("V0.1.3.00242.10161125-AHA-Desktop-JVM-SNAPSHOT");
        assertThat(ChannelContract.tag(ReleaseChannel.ALPHA, UpdateKind.CLI_NATIVE, SNAPSHOT))
                .isEqualTo("V0.1.3.00242.10161125-AHA-Cli-Native-SNAPSHOT");
    }

    @Test
    void tagRoundTripsForEverySupportedCombination() {
        for (ReleaseChannel channel : ReleaseChannel.values()) {
            for (UpdateKind kind : UpdateKind.values()) {
                if (!ChannelContract.supports(channel, kind)) {
                    continue;
                }
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
    void versionOfTagRejectsOtherChannelsAndKinds() {
        // 通道对不上
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM,
                "V0.1.3.00242-aha-jvm")).isEmpty();
        // 形态对不上（同名 tag 的另一种目标词）
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE,
                "V0.1.3.00242-aha-jvm")).isEmpty();
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.BETA, UpdateKind.CLI_NATIVE,
                "V0.1.3.00242-aha-desktop-native")).isEmpty();
        // 版本段数与通道不符（快照 tag 里放的是 4 段版本）
        assertThat(ChannelContract.versionOfTag(ReleaseChannel.ALPHA, UpdateKind.DESKTOP_JVM,
                "V0.1.3.00242-AHA-Desktop-JVM-SNAPSHOT")).isEmpty();
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
                new Case(ReleaseChannel.STABLE, UpdateKind.CLI_JVM, BASELINE, null, 25),
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
    void reportArchiveIsNotAnUpgradeAsset() {
        // 原生线同族的构建报告包不得被当成升级资产（否则会拿报告包去替换程序）。
        assertThat(ChannelContract.parseAsset(ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE,
                "AHA-Desktop-Native-0.1.3.00242-ubuntu-amd64-jdk25-Report.zip")).isEmpty();
        // 前缀不属于本形态
        assertThat(ChannelContract.parseAsset(ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE,
                "aha-desktop-0.1.3.00242-windows-amd64.zip")).isEmpty();
        // 源码包 / 文档包
        assertThat(ChannelContract.parseAsset(ReleaseChannel.STABLE, UpdateKind.CLI_JVM,
                "aha-cli-0.1.3-sources.zip")).isEmpty();
    }

    @Test
    void stableHasNoNativePackagesYet() {
        assertThat(ChannelContract.supports(ReleaseChannel.STABLE, UpdateKind.DESKTOP_NATIVE))
                .isFalse();
        assertThat(ChannelContract.supports(ReleaseChannel.STABLE, UpdateKind.CLI_NATIVE))
                .isFalse();
        assertThat(ChannelContract.supports(ReleaseChannel.BETA, UpdateKind.DESKTOP_NATIVE))
                .isTrue();

        assertThatThrownBy(() -> ChannelContract.assetName(ReleaseChannel.STABLE,
                UpdateKind.DESKTOP_NATIVE, BASELINE, "windows-amd64", 25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目前没有");
        assertThatThrownBy(() -> ChannelContract.tag(ReleaseChannel.STABLE,
                UpdateKind.DESKTOP_NATIVE, BASELINE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目前没有");
        assertThat(ChannelContract.parseAsset(ReleaseChannel.STABLE, UpdateKind.CLI_NATIVE,
                "AHA-Cli-Native-0.1.3-ubuntu-amd64-jdk25.zip")).isEmpty();
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
    void rejectsPlatformMisuseAndTooOldJdk() {
        // CLI 的 JVM 包平台无关：给了平台段是调用方的错，不能默默忽略。
        assertThatThrownBy(() -> ChannelContract.assetName(ReleaseChannel.STABLE, UpdateKind.CLI_JVM,
                BASELINE, "windows-amd64", 25))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("没有平台段");
        // 桌面端包必须有平台段。
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

    @Test
    void defaultChannelStillCannotServeNativeShapes() {
        // 默认通道换了，**形态边界没换**：原生线还没发正式版 ⇒ 原生形态在默认通道用不了。
        // 所以这条不仅断言「不支持」，还断言提示**给出了可切换的通道** ——
        // 默认值落在 Stable 之后，原生装机的首屏就会碰到它（issue #252 的排期边界）
        assertThat(ChannelContract.supports(ReleaseChannel.DEFAULT, UpdateKind.CLI_JVM)).isTrue();
        assertThat(ChannelContract.supports(ReleaseChannel.DEFAULT, UpdateKind.DESKTOP_JVM)).isTrue();
        assertThat(ChannelContract.supports(ReleaseChannel.DEFAULT, UpdateKind.DESKTOP_NATIVE))
                .isFalse();
    }

    @Test
    void missingAssetMessageSaysWhichChannelToSwitchTo() {
        // 「不支持」必须落到「怎么办」：只说「没有这种包」等于把用户堵在墙前
        assertThat(AssetSelectorTest.unsupportedMessage(UpdateKind.DESKTOP_NATIVE))
                .contains("目前没有")
                .contains("开发 Beta")
                .contains("快照 Alpha")
                .doesNotContain("「稳定 Stable」");   // 句首那句是被描述的通道，不算备选
    }
}
