package com.acanx.module.aha.core.update;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通道契约：把「通道 × 形态」唯一地映射到发布页的 <b>tag</b> 与 <b>资产名</b>（issue #51 §2.1）。
 *
 * <p>这是本仓库第三次实现「从发布页挑包」这套规则（前两次是 {@code Dist/UpdateJvm.ps1} 与
 * {@code Script/Python/NativeVersionUpdate.py}）。规则一旦漂移，表现是「某个通道/平台上永远取不到包」，
 * <b>静默且难查</b>，所以本类把命名规则收在一处、双向都给（生成 + 解析），并由
 * {@code bin/CheckScripts.py} 的 {@code check_release_channel_contract()} 断言三处实现逐字一致。</p>
 *
 * <p><b>命名表（实现必须逐字按此）</b>：</p>
 *
 * <ul>
 *   <li>{@link ReleaseChannel#STABLE}（基线 {@code x.y.z}）：
 *     <ul>
 *       <li>tag：{@code V<版本>}（正式 release，{@code prerelease=false}）；</li>
 *       <li>资产：{@code aha-desktop-<版本>-<平台>.zip} / {@code aha-cli-<版本>.zip}
 *           —— 只有 JDK 25（无 {@code -jdk} 后缀）；</li>
 *       <li>原生形态<b>暂无</b>（正式发版目前只出 CLI 包与桌面端 JVM 包，见 issue #51 §2.1）。</li>
 *     </ul>
 *   </li>
 *   <li>{@link ReleaseChannel#BETA}（开发版 {@code x.y.z.PPPPP}）：
 *     <ul>
 *       <li>tag：JVM 线 {@code V<版本>-aha-jvm}；原生线 {@code V<版本>-aha-desktop-native}
 *           / {@code -aha-cli-native}；</li>
 *       <li>资产：JVM 线同上（JDK 25 无后缀，JDK 27 为 {@code -jdk27}）；
 *           原生线 {@code AHA-Desktop-Native-<版本>-<平台>-jdk<JDK>.zip}。</li>
 *     </ul>
 *   </li>
 *   <li>{@link ReleaseChannel#ALPHA}（快照 {@code x.y.z.PPPPP.MMddHHmm}）：
 *     <ul>
 *       <li>tag：{@code V<快照版本>-AHA-<目标词>-SNAPSHOT}；</li>
 *       <li>资产：{@code AHA-<目标词>-<快照版本>-<平台>-jdk<JDK>.zip}；
 *           同族的 {@code -Report.zip} 是构建报告包，<b>不是</b>升级资产（解析时会自然落空）。</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>本类无 IO、无外部依赖，可在 CI 里完全离线验证。</p>
 *
 * @since 0.2.0
 */
public final class ChannelContract {

    /** 默认 JDK 段：JVM 线的默认变体（资产名<b>不带</b> {@code -jdk} 后缀）。 */
    public static final int DEFAULT_JDK = 25;

    /** JVM 线 dev 预发行的 tag 后缀。 */
    public static final String JVM_TAG_SUFFIX = "-aha-jvm";

    /** 原生线 dev 预发行的 tag 中缀（其后是 {@code desktop-native} / {@code cli-native}）。 */
    public static final String NATIVE_TAG_INFIX = "-aha-";

    /** 快照 tag 的目标词中缀（其后是 {@code Desktop-JVM} 等目标词）。 */
    public static final String SNAPSHOT_TARGET_INFIX = "-AHA-";

    /** 快照 tag 后缀。 */
    public static final String SNAPSHOT_TAG_SUFFIX = "-SNAPSHOT";

    /** 资产扩展名。 */
    public static final String ZIP_SUFFIX = ".zip";

    /** 显式 JDK 段的前缀（{@code -jdk25} / {@code -jdk27}）。 */
    public static final String JDK_INFIX = "-jdk";

    /** 版本段的通用正则片段（3–5 段；具体段数由通道决定）。 */
    private static final String VERSION_GROUP = "(?<version>[0-9]+(?:\\.[0-9]+){2,4})";

    /** 平台标签片段（{@code windows-amd64} / {@code macos-aarch64}）。 */
    private static final String PLATFORM_GROUP = "(?<platform>[a-z0-9]+(?:-[a-z0-9]+)*)";

    /** 资产名末尾的 JDK 段（解析时先剥掉它，再匹配剩下的核心部分）。 */
    private static final Pattern JDK_SUFFIX = Pattern.compile("-jdk([0-9]+)$");

    private ChannelContract() {
    }

    /**
     * 该通道是否有该形态的包。
     *
     * <p>目前唯一的不支持组合是「稳定通道 + 原生形态」：正式发版只出 CLI 包与桌面端 JVM 包，
     * 原生单文件的正式发布排在 1.x（{@code Docs/DevSpec/ReleaseProcess.md} §3）。</p>
     *
     * @param channel 通道
     * @param kind    形态
     * @return 支持返回 {@code true}
     */
    public static boolean supports(ReleaseChannel channel, UpdateKind kind) {
        return channel != ReleaseChannel.STABLE || !kind.nativeImage();
    }

    /**
     * 该通道的版本号<b>应当</b>有几段（通道与版本形态是一一对应的，混用即错）。
     *
     * @param channel 通道
     * @return 3（稳定）/ 4（开发）/ 5（快照）
     */
    public static int expectedSegments(ReleaseChannel channel) {
        return switch (channel) {
            case STABLE -> AhaVersion.BASELINE_SEGMENTS;
            case BETA -> AhaVersion.BUILD_SEGMENTS;
            case ALPHA -> AhaVersion.SNAPSHOT_SEGMENTS;
        };
    }

    /**
     * 生成发布页 tag。
     *
     * @param channel 通道
     * @param kind    形态
     * @param version 版本（段数必须与通道相符）
     * @return tag，如 {@code V0.1.3.00233-aha-jvm}
     * @throws IllegalArgumentException 该通道没有该形态，或版本段数与通道不符
     */
    public static String tag(ReleaseChannel channel, UpdateKind kind, AhaVersion version) {
        requireSupported(channel, kind);
        requireForm(channel, version);
        return switch (channel) {
            case STABLE -> "V" + version.value();
            case BETA -> "V" + version.value() + (kind.nativeImage()
                    ? NATIVE_TAG_INFIX + kind.tagSuffix()
                    : JVM_TAG_SUFFIX);
            case ALPHA -> "V" + version.value() + SNAPSHOT_TARGET_INFIX + kind.targetWord()
                    + SNAPSHOT_TAG_SUFFIX;
        };
    }

    /**
     * 从 tag 解析版本号；tag 不属于该「通道 + 形态」时返回空。
     *
     * <p>「不属于」包含三种情况：tag 形态对不上、版本段数与通道不符、该组合本就不支持。
     * 调用方（发布页过滤）据此把 release 分派到对应通道，而不是靠一堆 {@code startsWith}。</p>
     *
     * @param channel 通道
     * @param kind    形态
     * @param tag     tag 文本，可为 {@code null}
     * @return 版本；不匹配返回 {@link Optional#empty()}
     */
    public static Optional<AhaVersion> versionOfTag(ReleaseChannel channel, UpdateKind kind, String tag) {
        if (tag == null || !supports(channel, kind)) {
            return Optional.empty();
        }
        Matcher matcher = tagPattern(channel, kind).matcher(tag.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        AhaVersion version = AhaVersion.parse(matcher.group("version"));
        return matchesForm(channel, version) ? Optional.of(version) : Optional.empty();
    }

    /**
     * 生成资产名。
     *
     * @param channel     通道
     * @param kind        形态
     * @param version     版本（段数必须与通道相符）
     * @param platformTag 平台标签；形态带平台段时必填，不带平台段时必须留空
     * @param jdk         JDK 大版本（≥ {@link #DEFAULT_JDK}）
     * @return 资产名，如 {@code aha-desktop-0.1.3.00233-windows-amd64-jdk27.zip}
     * @throws IllegalArgumentException 参数与命名表不符
     */
    public static String assetName(ReleaseChannel channel, UpdateKind kind, AhaVersion version,
                                   String platformTag, int jdk) {
        requireSupported(channel, kind);
        requireForm(channel, version);
        if (jdk < DEFAULT_JDK) {
            throw new IllegalArgumentException(
                    "JDK 段必须 ≥ " + DEFAULT_JDK + "（实际 " + jdk + "）");
        }
        boolean platformGiven = platformTag != null && !platformTag.isBlank();
        if (kind.hasPlatform() && !platformGiven) {
            throw new IllegalArgumentException(
                    kind.targetWord() + " 的资产名带平台段，必须传入平台标签");
        }
        if (!kind.hasPlatform() && platformGiven) {
            throw new IllegalArgumentException(
                    kind.targetWord() + " 的资产名没有平台段（平台无关），不应传入：" + platformTag);
        }
        String platform = kind.hasPlatform() ? "-" + platformTag : "";
        // JDK 段的两种口径：**原生线与快照线始终带** -jdk<JDK>；JVM 线只有非默认变体才带。
        String jdkSuffix = jdkRequired(channel, kind) ? JDK_INFIX + jdk : variantJdkSuffix(jdk);
        String prefix = channel == ReleaseChannel.ALPHA
                ? "AHA-" + kind.targetWord() + "-"
                : kind.assetPrefix();
        return prefix + version.value() + platform + jdkSuffix + ZIP_SUFFIX;
    }

    /**
     * 从资产名解析「版本 / 平台 / JDK」；不是该「通道 + 形态」的资产时返回空。
     *
     * @param channel   通道
     * @param kind      形态
     * @param assetName 资产名，可为 {@code null}
     * @return 解析结果；不匹配返回 {@link Optional#empty()}
     */
    public static Optional<Asset> parseAsset(ReleaseChannel channel, UpdateKind kind, String assetName) {
        if (assetName == null || !supports(channel, kind)) {
            return Optional.empty();
        }
        String name = assetName.trim();
        if (!name.endsWith(ZIP_SUFFIX)) {
            return Optional.empty();
        }
        // 先把末尾的 -jdk<JDK> 剥掉再匹配核心部分：JVM 线的 JDK 段是**可选**的，
        // 若把它交给主模式里的可选组，平台段会因为贪婪而把它吃掉（实测：
        // `…-ubuntu-amd64-jdk27.zip` 曾被解析成平台 `ubuntu-amd64-jdk27`）。
        String core = name.substring(0, name.length() - ZIP_SUFFIX.length());
        int jdk = DEFAULT_JDK;
        Matcher jdkMatcher = JDK_SUFFIX.matcher(core);
        if (jdkMatcher.find()) {
            jdk = Integer.parseInt(jdkMatcher.group(1));
            core = core.substring(0, jdkMatcher.start());
        } else if (jdkRequired(channel, kind)) {
            return Optional.empty();
        }
        Matcher matcher = assetCorePattern(channel, kind).matcher(core);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        AhaVersion version = AhaVersion.parse(matcher.group("version"));
        if (!matchesForm(channel, version)) {
            return Optional.empty();
        }
        String platform = kind.hasPlatform() ? matcher.group("platform") : null;
        return Optional.of(new Asset(version, platform, jdk));
    }

    /**
     * 资产名的解析结果。
     *
     * @param version     版本
     * @param platformTag 平台标签；形态不带平台段时为 {@code null}
     * @param jdk         JDK 大版本
     */
    public record Asset(AhaVersion version, String platformTag, int jdk) {
    }

    /** tag 的匹配模式（按通道与形态生成，带具名组）。 */
    private static Pattern tagPattern(ReleaseChannel channel, UpdateKind kind) {
        return switch (channel) {
            case STABLE -> Pattern.compile("^V" + VERSION_GROUP + "$");
            case BETA -> Pattern.compile("^V" + VERSION_GROUP + Pattern.quote(kind.nativeImage()
                    ? NATIVE_TAG_INFIX + kind.tagSuffix()
                    : JVM_TAG_SUFFIX) + "$");
            case ALPHA -> Pattern.compile("^V" + VERSION_GROUP
                    + Pattern.quote(SNAPSHOT_TARGET_INFIX + kind.targetWord() + SNAPSHOT_TAG_SUFFIX)
                    + "$");
        };
    }

    /** 资产名的**核心部分**模式（已去掉 {@code .zip} 与末尾的 {@code -jdk<JDK>}）。 */
    private static Pattern assetCorePattern(ReleaseChannel channel, UpdateKind kind) {
        String platform = kind.hasPlatform() ? "-" + PLATFORM_GROUP : "";
        return switch (channel) {
            case STABLE, BETA -> Pattern.compile("^" + Pattern.quote(kind.assetPrefix())
                    + VERSION_GROUP + platform + "$");
            case ALPHA -> Pattern.compile("^AHA-" + Pattern.quote(kind.targetWord()) + "-"
                    + VERSION_GROUP + platform + "$");
        };
    }

    /**
     * 该组合的资产名是否**必须**带 JDK 段。
     *
     * <p>实查（2026-10-16）：dev 线原生包的资产名是
     * {@code AHA-Desktop-Native-0.1.3.00242-ubuntu-amd64-jdk25.zip}（始终带），
     * 而 JVM 线的默认变体是 {@code aha-desktop-0.1.3.00242-windows-amd64.zip}（不带）。
     * 两者口径不同，不能共用一条规则。</p>
     */
    private static boolean jdkRequired(ReleaseChannel channel, UpdateKind kind) {
        return kind.nativeImage() || channel == ReleaseChannel.ALPHA;
    }

    /**
     * JVM 线（稳定 / dev）的 JDK 后缀：默认变体不带后缀，其它变体带。
     *
     * <p>实查（2026-10-16）：{@code V0.1.3.00242-aha-jvm} 的资产里既有
     * {@code aha-desktop-0.1.3.00242-windows-amd64.zip}（JDK 25），也有
     * {@code …-jdk27.zip}。</p>
     */
    private static String variantJdkSuffix(int jdk) {
        return jdk == DEFAULT_JDK ? "" : JDK_INFIX + jdk;
    }

    /** 版本段数是否与通道相符。 */
    private static boolean matchesForm(ReleaseChannel channel, AhaVersion version) {
        return version.segmentCount() == expectedSegments(channel);
    }

    /** 版本段数与通道不符时抛出，消息里写清「该通道要几段、给了几段」。 */
    private static void requireForm(ReleaseChannel channel, AhaVersion version) {
        if (!matchesForm(channel, version)) {
            throw new IllegalArgumentException(
                    channel.label() + " 通道的版本必须是 " + expectedSegments(channel)
                            + " 段（" + formHint(channel) + "），实际给了 " + version.segmentCount()
                            + " 段：" + version.value());
        }
    }

    /** 该通道支持的组合检查。 */
    private static void requireSupported(ReleaseChannel channel, UpdateKind kind) {
        if (!supports(channel, kind)) {
            throw new IllegalArgumentException(
                    channel.label() + " 通道目前没有 " + kind.targetWord() + " 的包"
                            + "（正式发版只出 CLI 包与桌面端 JVM 包，见 issue #51 §2.1）");
        }
    }

    /** 版本形态的中文提示。 */
    private static String formHint(ReleaseChannel channel) {
        return switch (channel) {
            case STABLE -> "基线 x.y.z";
            case BETA -> "开发版 x.y.z.PPPPP";
            case ALPHA -> "快照 x.y.z.PPPPP.MMddHHmm";
        };
    }
}
