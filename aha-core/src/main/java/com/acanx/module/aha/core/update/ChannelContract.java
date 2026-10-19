package com.acanx.module.aha.core.update;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通道契约：把「通道 × 形态」唯一地映射到发布页的 <b>tag</b> 与 <b>资产名</b>
 * （issue #51 §2.1；文法自 issue #269 起统一）。
 *
 * <p>这是本仓库第三次实现「从发布页挑包」这套规则（前两次是 {@code Dist/UpdateJvm.ps1} 与
 * {@code Script/Python/NativeVersionUpdate.py}）。规则一旦漂移，表现是「某个通道/平台上永远取不到包」，
 * <b>静默且难查</b>，所以本类把命名规则收在一处、双向都给（生成 + 解析），并由
 * {@code bin/CheckScripts.py} 的 {@code check_release_channel_contract()} 断言三处实现逐字一致。</p>
 *
 * <p><b>资产名文法（唯一一处定义，issue #269 §1）</b>：</p>
 *
 * <pre>{@code
 * aha-<product>-<form>-<version>-<os>-<arch>-<runtime>.zip
 * }</pre>
 *
 * <ul>
 *   <li>{@code product}：{@code desktop} / {@code cli}；</li>
 *   <li>{@code form}：{@code jvm} / {@code native}；</li>
 *   <li>{@code os}-{@code arch}：{@code windows-amd64} / {@code ubuntu-amd64} / {@code macos-aarch64}；</li>
 *   <li>{@code runtime}：JVM 形态 {@code jdk25}（或 {@code jdk27}），原生形态 {@code graalvm25}。</li>
 * </ul>
 *
 * <p><b>tag 文法</b>：</p>
 *
 * <ul>
 *   <li>{@link ReleaseChannel#STABLE}：{@code V<版本>}（一个 release 装下全部资产）；</li>
 *   <li>{@link ReleaseChannel#BETA}：{@code V<版本>-aha-<product>-<form>}；</li>
 *   <li>{@link ReleaseChannel#ALPHA}：{@code V<版本>-AHA-<product>-<form>}
 *       （5 段版本已能区分，2026-10-17 起不再带 {@code -SNAPSHOT}）。</li>
 * </ul>
 *
 * <p><b>旧名兼容</b>：0.1.3 及更早的两套资产名（{@code aha-desktop-…} / {@code AHA-Desktop-Native-…}）
 * 与旧 tag（{@code -aha-jvm}、{@code -AHA-Desktop-Native-SNAPSHOT}）在一个版本周期内仍可解析，
 * 见 {@link #parseAsset} 与 {@link #versionOfTag} 的兼容分支。0.1.5 起旧名由
 * {@code check_release_matrix()} 的版本门槛判红。</p>
 *
 * <p>本类无 IO、无外部依赖，可在 CI 里完全离线验证。</p>
 *
 * @since 0.2.0
 */
public final class ChannelContract {

    /** 默认 JDK 段：JVM 线的默认变体。 */
    public static final int DEFAULT_JDK = 25;

    /** 旧 JVM 线 dev 预发行的 tag 后缀（0.1.5 之前的**合并**形态，仅解析兼容）。 */
    public static final String JVM_TAG_SUFFIX = "-aha-jvm";

    /** 原生线 dev 预发行的 tag 中缀（其后是 {@code desktop-native} / {@code cli-native}）。 */
    public static final String NATIVE_TAG_INFIX = "-aha-";

    /** 旧快照 tag 的目标词中缀（其后是 {@code Desktop-JVM} 等目标词）。 */
    public static final String SNAPSHOT_TARGET_INFIX = "-AHA-";

    /** 快照 tag 后缀。 */
    public static final String SNAPSHOT_TAG_SUFFIX = "-SNAPSHOT";

    /** 资产扩展名。 */
    public static final String ZIP_SUFFIX = ".zip";

    /** 旧 JDK 段前缀（{@code -jdk25} / {@code -jdk27}），兼容用。 */
    public static final String JDK_INFIX = "-jdk";

    /** 新文法的 runtime 段前缀：JVM 形态。 */
    public static final String RUNTIME_JDK = "jdk";

    /** 新文法的 runtime 段前缀：原生形态。 */
    public static final String RUNTIME_GRAALVM = "graalvm";

    /** 版本段的通用正则片段（3–5 段；具体段数由通道决定）。 */
    private static final String VERSION_GROUP = "(?<version>[0-9]+(?:\\.[0-9]+){2,4})";

    /** 平台标签片段（{@code windows-amd64} / {@code macos-aarch64}）。 */
    private static final String PLATFORM_GROUP = "(?<platform>[a-z0-9]+(?:-[a-z0-9]+)*)";

    /** 旧资产名末尾的 JDK 段（解析兼容分支时先剥掉）。 */
    private static final Pattern LEGACY_JDK_SUFFIX = Pattern.compile("-jdk([0-9]+)$");

    private ChannelContract() {
    }

    /**
     * 该通道是否有该形态的包。
     *
     * <p>自 0.1.4 起原生形态进 Stable（issue #269 §3），四组合在所有通道都发布；
     * 「本版本到底出了哪些」由 {@code .github/ReleaseMatrix.yml} 与本类的资产列表决定。</p>
     *
     * @param channel 通道
     * @param kind    形态
     * @return 恒为 {@code true}
     */
    public static boolean supports(ReleaseChannel channel, UpdateKind kind) {
        return true;
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
     * @return tag，如 {@code V0.1.4.00233-aha-desktop-jvm}
     * @throws IllegalArgumentException 版本段数与通道不符
     */
    public static String tag(ReleaseChannel channel, UpdateKind kind, AhaVersion version) {
        requireForm(channel, version);
        return switch (channel) {
            case STABLE -> "V" + version.value();
            case BETA -> "V" + version.value() + NATIVE_TAG_INFIX + kind.tagSuffix();
            // 2026-10-17 起快照 tag 不再带 `-SNAPSHOT`：5 段版本已能区分（issue #269）。
            case ALPHA -> "V" + version.value() + SNAPSHOT_TARGET_INFIX + kind.tagSuffix();
        };
    }

    /**
     * 从 tag 解析版本号；tag 不属于该「通道 + 形态」时返回空。
     *
     * <p>兼容 0.1.3 的旧写法：{@code -aha-jvm}（对应 {@code desktop-jvm} / {@code cli-jvm} 两者）
     * 与 {@code -AHA-<TargetWord>-SNAPSHOT}（大写驼峰目标词）。</p>
     *
     * @param channel 通道
     * @param kind    形态
     * @param tag     tag 文本，可为 {@code null}
     * @return 版本；不匹配返回 {@link Optional#empty()}
     */
    public static Optional<AhaVersion> versionOfTag(ReleaseChannel channel, UpdateKind kind, String tag) {
        if (tag == null) {
            return Optional.empty();
        }
        String text = tag.trim();
        for (String candidate : tagPatterns(channel, kind)) {
            Matcher matcher = Pattern.compile(candidate).matcher(text);
            if (matcher.matches()) {
                AhaVersion version = AhaVersion.parse(matcher.group("version"));
                if (matchesForm(channel, version)) {
                    return Optional.of(version);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * 生成资产名（新文法）。
     *
     * @param channel     通道
     * @param kind        形态
     * @param version     版本（段数必须与通道相符）
     * @param platformTag 平台标签（必填）
     * @param jdk         JDK 大版本（≥ {@link #DEFAULT_JDK}）
     * @return 资产名，如 {@code aha-desktop-jvm-0.1.4-windows-amd64-jdk25.zip}
     * @throws IllegalArgumentException 参数与文法不符
     */
    public static String assetName(ReleaseChannel channel, UpdateKind kind, AhaVersion version,
                                   String platformTag, int jdk) {
        requireForm(channel, version);
        if (jdk < DEFAULT_JDK) {
            throw new IllegalArgumentException(
                    "JDK 段必须 ≥ " + DEFAULT_JDK + "（实际 " + jdk + "）");
        }
        if (platformTag == null || platformTag.isBlank()) {
            throw new IllegalArgumentException(
                    kind.targetWord() + " 的资产名带平台段，必须传入平台标签");
        }
        String runtime = kind.nativeImage() ? RUNTIME_GRAALVM + jdk : RUNTIME_JDK + jdk;
        return "aha-" + kind.product() + "-" + kind.form() + "-" + version.value()
                + "-" + platformTag + "-" + runtime + ZIP_SUFFIX;
    }

    /**
     * 从资产名解析「版本 / 平台 / JDK / 运行时」；不是该形态的资产时返回空。
     *
     * @param channel   通道
     * @param kind      形态
     * @param assetName 资产名，可为 {@code null}
     * @return 解析结果；不匹配返回 {@link Optional#empty()}
     */
    public static Optional<Asset> parseAsset(ReleaseChannel channel, UpdateKind kind, String assetName) {
        if (assetName == null) {
            return Optional.empty();
        }
        String name = assetName.trim();
        if (!name.endsWith(ZIP_SUFFIX)) {
            return Optional.empty();
        }
        Optional<Asset> current = parseCurrent(kind, name);
        return current.isPresent() ? current : parseLegacy(channel, kind, name);
    }

    /**
     * 资产名的解析结果。
     *
     * @param version     版本
     * @param platformTag 平台标签；旧 CLI-JVM 包（无平台段）为 {@code null}
     * @param jdk         JDK 大版本
     * @param runtime     运行时（{@code jdk25} / {@code graalvm25}）
     */
    public record Asset(AhaVersion version, String platformTag, int jdk, String runtime) {
    }

    /** 新文法的匹配模式（product / form 由 kind 固定）。 */
    private static Pattern currentPattern(UpdateKind kind) {
        return Pattern.compile("^aha-" + Pattern.quote(kind.product()) + "-"
                + Pattern.quote(kind.form()) + "-" + VERSION_GROUP + "-" + PLATFORM_GROUP
                + "-(?<runtime>jdk|graalvm)(?<jdk>[0-9]+)" + Pattern.quote(ZIP_SUFFIX) + "$");
    }

    /** 新文法的解析：runtime 前缀必须与形态匹配。 */
    private static Optional<Asset> parseCurrent(UpdateKind kind, String name) {
        Matcher matcher = currentPattern(kind).matcher(name);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String runtimePrefix = matcher.group("runtime");
        String expectedPrefix = kind.nativeImage() ? RUNTIME_GRAALVM : RUNTIME_JDK;
        if (!expectedPrefix.equals(runtimePrefix)) {
            return Optional.empty();
        }
        AhaVersion version = AhaVersion.parse(matcher.group("version"));
        int jdk = Integer.parseInt(matcher.group("jdk"));
        return Optional.of(new Asset(version, matcher.group("platform"), jdk,
                runtimePrefix + jdk));
    }

    /**
     * 旧文法的解析（0.1.3 及更早）。
     *
     * <ul>
     *   <li>JVM 线：{@code aha-desktop-<版本>-<平台>[-jdk27].zip} / {@code aha-cli-<版本>[-jdk27].zip}；</li>
     *   <li>原生线：{@code AHA-<TargetWord>-<版本>-<平台>-jdk<JDK>.zip}。</li>
     * </ul>
     */
    private static Optional<Asset> parseLegacy(ReleaseChannel channel, UpdateKind kind, String name) {
        String core = name.substring(0, name.length() - ZIP_SUFFIX.length());
        int jdk = DEFAULT_JDK;
        boolean jdkSuffix = false;
        Matcher jdkMatcher = LEGACY_JDK_SUFFIX.matcher(core);
        if (jdkMatcher.find()) {
            jdk = Integer.parseInt(jdkMatcher.group(1));
            core = core.substring(0, jdkMatcher.start());
            jdkSuffix = true;
        }
        // 旧原生线（Beta / Alpha）的资产名始终带 -jdk<JDK>；JVM 线只有非默认变体才带。
        if (kind.nativeImage() && !jdkSuffix) {
            return Optional.empty();
        }
        for (String pattern : legacyPatterns(kind)) {
            Matcher matcher = Pattern.compile(pattern).matcher(core);
            if (!matcher.matches()) {
                continue;
            }
            AhaVersion version = AhaVersion.parse(matcher.group("version"));
            if (!matchesForm(channel, version)) {
                return Optional.empty();
            }
            String platform = matcher.group("platform");
            String runtime = kind.nativeImage() ? RUNTIME_GRAALVM + jdk : RUNTIME_JDK + jdk;
            return Optional.of(new Asset(version, platform, jdk, runtime));
        }
        return Optional.empty();
    }

    /**
     * 旧资产名的候选模式（{@code -jdk<JDK>} 已先被剥掉）。
     *
     * <ul>
     *   <li>JVM 线 Beta：{@code aha-desktop-<版本>-<平台>} / {@code aha-cli-<版本>}；</li>
     *   <li>Alpha：{@code AHA-<TargetWord>-<版本>-<平台>}（四组合共用）；</li>
     *   <li>原生线：{@code AHA-<TargetWord>-<版本>-<平台>}。</li>
     * </ul>
     */
    private static String[] legacyPatterns(UpdateKind kind) {
        String alpha = "^AHA-" + Pattern.quote(kind.legacyTargetWord()) + "-" + VERSION_GROUP
                + "-" + PLATFORM_GROUP + "$";
        if (kind.nativeImage()) {
            return new String[]{alpha};
        }
        String beta = "^aha-" + Pattern.quote(kind.product()) + "-" + VERSION_GROUP
                + "(?:-" + PLATFORM_GROUP + ")?$";
        return new String[]{beta, alpha};
    }

    /** tag 的候选模式：新文法在前，旧写法在后（只对历史版本有意义）。 */
    private static String[] tagPatterns(ReleaseChannel channel, UpdateKind kind) {
        String current = "^V" + VERSION_GROUP + Pattern.quote(NATIVE_TAG_INFIX + kind.tagSuffix()) + "$";
        String currentSnapshot = "^V" + VERSION_GROUP
                + Pattern.quote(SNAPSHOT_TARGET_INFIX + kind.tagSuffix()) + "$";
        // 兼容历史上带 `-SNAPSHOT` 的快照 tag（大写目标词与上一版的小写目标词两种写法）。
        String legacySnapshotUpper = "^V" + VERSION_GROUP
                + Pattern.quote(SNAPSHOT_TARGET_INFIX + kind.legacyTargetWord() + SNAPSHOT_TAG_SUFFIX)
                + "$";
        String legacySnapshotLower = "^V" + VERSION_GROUP
                + Pattern.quote(SNAPSHOT_TARGET_INFIX + kind.tagSuffix() + SNAPSHOT_TAG_SUFFIX) + "$";
        return switch (channel) {
            case STABLE -> new String[]{"^V" + VERSION_GROUP + "$"};
            // JVM 形态自 issue #347 起与原生线一样：每个形态一条 release（`-aha-desktop-jvm` /
            // `-aha-cli-jvm`）。旧合并 tag `-aha-jvm` 仍解析，否则 0.1.5 之前的 dev 包会查不到。
            case BETA -> kind.nativeImage()
                    ? new String[]{current}
                    : new String[]{current,
                            "^V" + VERSION_GROUP + Pattern.quote(JVM_TAG_SUFFIX) + "$"};
            case ALPHA -> new String[]{currentSnapshot, legacySnapshotUpper, legacySnapshotLower};
        };
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

    /** 版本形态的中文提示。 */
    private static String formHint(ReleaseChannel channel) {
        return switch (channel) {
            case STABLE -> "基线 x.y.z";
            case BETA -> "开发版 x.y.z.PPPPP";
            case ALPHA -> "快照 x.y.z.PPPPP.MMddHHmm";
        };
    }
}
