package com.acanx.module.aha.core.update;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 把发布页的一堆 release 按「通道 + 形态」筛成候选版本列表（issue #51 §4.1）。
 *
 * <p>过滤与排序规则全部来自 {@link ChannelContract}（tag 形态 ⇄ 版本），本类不自己解析 tag ——
 * 「通道 ↔ tag」只允许有一处定义，否则三处实现（PowerShell / Python / Java）必然漂移。</p>
 *
 * <p>排序按<b>版本号</b>而不是发布时间：发布页的顺序是「最近发布在前」，而版本号才是
 * 「哪个更新」的判据（快照/预发行随时可能补发，发布顺序与版本大小并不总是一致）。</p>
 *
 * @since 0.2.0
 */
public final class ReleaseFilter {

    private ReleaseFilter() {
    }

    /**
     * 筛出该「通道 + 形态」的候选版本，按版本从新到旧。
     *
     * @param releases 发布页返回的全部 release
     * @param channel  通道
     * @param kind     形态
     * @return 候选列表（不可变，可能为空）
     */
    public static List<ChannelRelease> select(List<ReleaseInfo> releases, ReleaseChannel channel,
                                              UpdateKind kind) {
        return releases.stream()
                // 草稿对用户不可见：未认证请求本就不会返回草稿，但带上凭据时会出现，故显式排除。
                .filter(release -> !release.draft())
                // 正式通道只认正式发布：预发行（快照 / dev 线）不进 Stable。
                .filter(release -> channel != ReleaseChannel.STABLE || !release.prerelease())
                .flatMap(release -> ChannelContract
                        .versionOfTag(channel, kind, release.tag())
                        .map(version -> new ChannelRelease(release.tag(), version,
                                release.prerelease(), release.publishedAt(), release.assetList()))
                        .stream())
                .sorted(Comparator.comparing(ChannelRelease::version).reversed()
                        .thenComparing(ChannelRelease::tag))
                .toList();
    }

    /**
     * 候选版本：一条「属于本通道本形态」的 release。
     *
     * @param tag         tag（如 {@code V0.1.5.00346-aha-desktop-jvm}）
     * @param version     版本（已从 tag 解析）
     * @param prerelease  是否预发行
     * @param publishedAt 发布时间（可能为 {@code null}）
     * @param assets      该 release 的全部资产（挑本平台资产是下一步的事）
     */
    public record ChannelRelease(String tag, AhaVersion version, boolean prerelease,
                                 Instant publishedAt, List<AssetInfo> assets) {

        /**
         * 追加式构造：保证 {@code assets} 不可变且非空值。
         */
        public ChannelRelease {
            assets = assets == null ? List.of() : List.copyOf(assets);
        }
    }
}
