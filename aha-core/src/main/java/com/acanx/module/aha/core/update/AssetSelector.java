package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import com.acanx.module.aha.core.update.ReleaseFilter.ChannelRelease;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 挑资产：在**某一个版本自己的**资产列表里，按「形态 → 平台标签 → JDK 轴」找出那一份包
 * （issue #51 §2.1、§4.1）。
 *
 * <p><b>为什么必须「只在这一个版本里挑」</b>：这是 issue #210 的直接教训。若某版本缺本平台的腿
 * （构建没出、上传没完成、平台本就不发），退到更旧的版本去取包，界面上会表现为「升级成功、
 * 版本还是旧的」或「已是最新、其实不是」—— <b>静默且极难查</b>。因此本类的口径是：
 * <b>挑不到就响亮报错，并把这个版本实际有哪些资产列出来</b>，绝不换版本、绝不换平台。</p>
 *
 * <p><b>JDK 轴</b>：默认只取 JDK 25 变体（JVM 线的资产名不带 {@code -jdk} 后缀）；
 * {@code -jdk27} 是试验分支，<b>不默认</b>（§2.1）。若某平台只存在 {@code -jdk27} 变体，
 * 报错时会额外提示这一句，避免用户对着「有包却挑不到」的清单发愣。</p>
 *
 * <p>本类无 IO、无网络，输入是已解析好的 release 列表，可在 CI 里完全离线验证。</p>
 *
 * @since 0.2.0
 */
public final class AssetSelector {

    private AssetSelector() {
    }

    /**
     * 解析某个版本的资产，挑不到返回空（不抛）。
     *
     * <p>给<b>列表界面</b>用：逐版本标注「本平台可用 / 不可用」。<b>不要</b>用它来决定
     * 「有没有更新」—— 那必须用 {@link #select}，否则会重演 issue #210 的静默退版（见类注释）。</p>
     *
     * @param release  候选版本
     * @param channel  通道
     * @param kind     形态
     * @param platform 本机平台标签
     * @return 命中的资产；挑不到返回 {@link Optional#empty()}
     * @throws UpdateException 同一形态/平台匹配到多个资产（命名契约重复，属内容异常）
     */
    public static Optional<AssetInfo> resolve(ChannelRelease release, ReleaseChannel channel,
                                              UpdateKind kind, PlatformTag platform) {
        List<AssetInfo> matches = new ArrayList<>();
        for (AssetInfo asset : release.assets()) {
            if (matchesAsset(asset, release, channel, kind, platform)) {
                matches.add(asset);
            }
        }
        if (matches.size() > 1) {
            throw duplicateMatch(release, kind, platform, matches);
        }
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }

    /**
     * 解析某个版本的资产，挑不到就抛（检查流程<b>必须</b>用这个）。
     *
     * <p>错误码分三种，界面据此给不同的下一步：</p>
     * <ul>
     *   <li>{@link UpdateException#CODE_ASSET_MISSING} —— 该通道本就没有该形态 / 该版本缺本平台的腿
     *       （消息里附该版本实际资产清单，以及 jdk27 变体的提示）；</li>
     *   <li>{@link UpdateException#CODE_PLATFORM_UNPUBLISHED} —— 本平台不在发布范围内（如 Windows ARM64）；</li>
     *   <li>{@link UpdateException#CODE_INVALID_RESPONSE} —— 同一形态/平台匹配到多个资产。</li>
     * </ul>
     *
     * @param release  候选版本
     * @param channel  通道
     * @param kind     形态
     * @param platform 本机平台标签
     * @return 命中的资产（保证存在）
     * @throws UpdateException 挑不到或内容异常
     */
    public static AssetInfo select(ChannelRelease release, ReleaseChannel channel, UpdateKind kind,
                                   PlatformTag platform) {
        if (!ChannelContract.supports(channel, kind)) {
            throw new UpdateException(UpdateException.CODE_ASSET_MISSING,
                    channel.label() + " 通道目前没有 " + kind.targetWord() + " 的包"
                            + "（正式发版只出 CLI 包与桌面端 JVM 包，见 issue #51 §2.1）");
        }
        // 带平台段的形态先看「这个平台有没有出过包」；不带平台段的形态（CLI JVM）与平台无关，跳过此判。
        if (kind.hasPlatform() && !platform.hasPublishedPackages()) {
            throw new UpdateException(UpdateException.CODE_PLATFORM_UNPUBLISHED,
                    platform.unpublishedReason() + "；可手动到发布页取件");
        }
        return resolve(release, channel, kind, platform)
                .orElseThrow(() -> missing(release, channel, kind, platform));
    }

    /**
     * 批量挑资产：只保留**本平台本形态确实有包**的版本，版本从新到旧。
     *
     * <p>给列表界面用。调用方拿到的是「装了不亏」的版本集合；被跳过的版本说明它缺本平台的腿，
     * 而不是它不存在。判断「有没有更新」请用 {@link #select} 对<b>最新的候选版本</b>单独解析，
     * 不要用「本方法返回列表的首位」代替（那等于静默退版，见类注释）。</p>
     *
     * @param candidates 候选版本（{@link ReleaseFilter#select} 的输出，已按版本从新到旧）
     * @param channel    通道
     * @param kind       形态
     * @param platform   本机平台标签
     * @return 可用版本列表（不可变，顺序与入参一致）
     * @throws UpdateException 内容异常（同一形态/平台匹配到多个资产）
     */
    public static List<Available> available(List<ChannelRelease> candidates, ReleaseChannel channel,
                                            UpdateKind kind, PlatformTag platform) {
        List<Available> result = new ArrayList<>();
        for (ChannelRelease candidate : candidates) {
            resolve(candidate, channel, kind, platform)
                    .ifPresent(asset -> result.add(new Available(candidate, asset)));
        }
        return List.copyOf(result);
    }

    /**
     * 可用版本：一个「本平台本形态确实有包」的候选。
     *
     * @param release 候选版本
     * @param asset   该版本里命中的资产
     */
    public record Available(ChannelRelease release, AssetInfo asset) {

        /**
         * 版本号（便于排序与展示）。
         *
         * @return 版本
         */
        public AhaVersion version() {
            return release.version();
        }

        /**
         * tag（便于展示与留痕）。
         *
         * @return tag
         */
        public String tag() {
            return release.tag();
        }
    }

    /**
     * 单个资产是否命中「形态 → 平台标签 → JDK 轴」三条：
     *
     * <ol>
     *   <li>已上传完成（{@code state=uploaded}）；</li>
     *   <li>资产名能被 {@link ChannelContract} 解析成该「通道 + 形态」；</li>
     *   <li>资产名里的版本与所属 release 的 tag 版本<b>一致</b>（防「tag 与资产不同步」）；</li>
     *   <li>平台段与本机平台一致（不带平台段的形态要求资产也没有平台段）；</li>
     *   <li>JDK 轴是默认变体（jdk25）。</li>
     * </ol>
     */
    private static boolean matchesAsset(AssetInfo asset, ChannelRelease release, ReleaseChannel channel,
                                        UpdateKind kind, PlatformTag platform) {
        if (!asset.uploaded()) {
            return false;
        }
        Optional<ChannelContract.Asset> parsed = ChannelContract.parseAsset(channel, kind, asset.name());
        if (parsed.isEmpty()) {
            return false;
        }
        ChannelContract.Asset contractAsset = parsed.get();
        if (!contractAsset.version().equals(release.version())) {
            return false;
        }
        boolean platformOk = kind.hasPlatform()
                ? platform.value().equals(contractAsset.platformTag())
                : contractAsset.platformTag() == null;
        return platformOk && contractAsset.jdk() == ChannelContract.DEFAULT_JDK;
    }

    /** 挑不到资产时的报错：写清「谁缺什么」，并列出该版本实际有哪些资产。 */
    private static UpdateException missing(ChannelRelease release, ReleaseChannel channel,
                                           UpdateKind kind, PlatformTag platform) {
        return new UpdateException(UpdateException.CODE_ASSET_MISSING,
                "该版本没有本平台可用的包：" + missingWhat(kind, platform) + "；"
                        + "版本 " + release.tag() + " 实际有 " + release.assets().size() + " 个资产："
                        + describeAssets(release) + jdkVariantHint(release, channel, kind, platform)
                        + "。不会退回到更旧的版本，请改用其它通道，或等该平台的包构建完成");
    }

    /** 「缺什么」的措辞：带平台段的形态点名平台，不带平台段的形态只说形态。 */
    private static String missingWhat(UpdateKind kind, PlatformTag platform) {
        return kind.hasPlatform() ? platform.value() + " 平台 + " + kind.targetWord() + " 形态"
                : kind.targetWord() + " 形态";
    }

    /** 该版本资产清单（只列名字；最多 12 个，避免错误信息长到看不完）。 */
    private static String describeAssets(ChannelRelease release) {
        List<String> names = release.assets().stream().map(AssetInfo::name).toList();
        if (names.isEmpty()) {
            return "（该版本还没有任何资产，可能正在构建或上传中）";
        }
        int limit = 12;
        String joined = String.join("、", names.subList(0, Math.min(limit, names.size())));
        return names.size() > limit ? joined + " 等 " + names.size() + " 个" : joined;
    }

    /**
     * 「该平台其实有包，只是 jdk 变体不同」的提示。命中时返回一句提示，否则返回空串。
     *
     * <p>这不是理论情况：JVM 线的 {@code -jdk27} 变体确实存在（试验分支），若某平台只出了
     * {@code -jdk27} 而没出默认变体，用户会看到「资产列表里明明有本平台的包却挑不到」。</p>
     */
    private static String jdkVariantHint(ChannelRelease release, ReleaseChannel channel,
                                         UpdateKind kind, PlatformTag platform) {
        for (AssetInfo asset : release.assets()) {
            if (!asset.uploaded()) {
                continue;
            }
            Optional<ChannelContract.Asset> parsed =
                    ChannelContract.parseAsset(channel, kind, asset.name());
            if (parsed.isEmpty()) {
                continue;
            }
            ChannelContract.Asset contractAsset = parsed.get();
            boolean platformOk = kind.hasPlatform()
                    ? platform.value().equals(contractAsset.platformTag())
                    : contractAsset.platformTag() == null;
            if (platformOk && contractAsset.version().equals(release.version())
                    && contractAsset.jdk() != ChannelContract.DEFAULT_JDK) {
                return "；注：该平台只有 jdk" + contractAsset.jdk() + " 变体（试验分支），"
                        + "升级默认只取 jdk" + ChannelContract.DEFAULT_JDK + " 变体";
            }
        }
        return "";
    }

    /** 同一形态/平台匹配到多个资产：命名契约重复，属内容异常，必须响亮。 */
    private static UpdateException duplicateMatch(ChannelRelease release, UpdateKind kind,
                                                  PlatformTag platform, List<AssetInfo> matches) {
        return new UpdateException(UpdateException.CODE_INVALID_RESPONSE,
                "版本 " + release.tag() + " 有 " + matches.size() + " 个资产同时匹配 "
                        + missingWhat(kind, platform) + " 与 jdk" + ChannelContract.DEFAULT_JDK
                        + " 轴，命名契约重复：" + matches.stream().map(AssetInfo::name).toList());
    }
}
