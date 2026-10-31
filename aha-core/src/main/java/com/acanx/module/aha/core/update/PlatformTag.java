package com.acanx.module.aha.core.update;

import java.util.Locale;
import java.util.Set;

/**
 * 平台标签：把本机的「操作系统 + 架构」归一化成发布页资产名里的那一段（issue #51 §2.1）。
 *
 * <p>归一化的目标值是发布页上的标签：{@code windows-amd64} / {@code ubuntu-amd64} /
 * {@code macos-aarch64}（架构词统一 {@code aarch64}，见 issue #287）。历史资产名里的
 * {@code macos-arm64} 仍被 {@link #matches(String)} 接受（读取兼容），但新产出一律 {@code aarch64}。</p>
 *
 * <p><b>注意这是「发布范围」而不是「应用内升级的实现范围」</b>——两者是不同的维度，别混：
 * Linux x86_64（{@code ubuntu-amd64}）在发布页上<b>有</b>包（本类判「有」），但本期的应用内升级
 * 只实现并只验证 Windows（issue #51 §3.4 / §10 D10）。界面上的置灰依据是后者，不是本类。</p>
 *
 * <p>Linux 一律映射到 {@code ubuntu-amd64}：包就是在 Ubuntu 上构建的（标签写 ubuntu）。
 * 这是**有意**的简化 —— 非 Ubuntu 发行版的 glibc 版本可能跑不起来，由用法文档提示，不在这里猜发行版。</p>
 *
 * <p>构造即归一化，所以不存在「未归一化」的实例；归一化是幂等的。
 * 本类无 IO，除 {@link #detect()} 读两个系统属性外不依赖环境，可在 CI 里完全离线验证。</p>
 *
 * @param os   归一化后的系统段（{@code windows} / {@code ubuntu} / {@code macos} 或原样小写词）
 * @param arch 归一化后的架构段（{@code amd64} / {@code aarch64} 或原样小写词）
 * @since 0.2.0
 */
public record PlatformTag(String os, String arch) {

    /** 系统段：Windows。 */
    public static final String WINDOWS = "windows";

    /** 系统段：Linux（统一按构建用的 Ubuntu 记）。 */
    public static final String UBUNTU = "ubuntu";

    /** 系统段：macOS。 */
    public static final String MACOS = "macos";

    /** 架构段：x86_64。 */
    public static final String AMD64 = "amd64";

    /** 架构段：ARM64（统一写 {@code aarch64}；历史 {@code arm64} 只在读取时被接受）。 */
    public static final String AARCH64 = "aarch64";

    /** 历史架构段写法（旧资产名 {@code macos-arm64}）：仅用于读取兼容。 */
    private static final String LEGACY_AARCH64 = "arm64";

    /** 发布页上实际出现过包的平台标签（桌面端 JVM / CLI / 原生线的并集）。 */
    private static final Set<String> PUBLISHED = Set.of(
            WINDOWS + "-" + AMD64,
            UBUNTU + "-" + AMD64,
            MACOS + "-" + AARCH64);

    /**
     * 构造即归一化（幂等）。
     *
     * @param os   操作系统名（如 {@code Windows 11} / {@code Linux} / {@code Mac OS X}）；空白即错
     * @param arch 架构名（如 {@code amd64} / {@code x86_64} / {@code aarch64}）；空白即错
     * @throws IllegalArgumentException 系统名或架构名为空
     */
    public PlatformTag {
        os = normalizeOs(os);
        arch = normalizeArch(arch);
    }

    /**
     * 按系统名与架构名构造（等价于直接 {@code new}，便于调用处表达意图）。
     *
     * @param osName 操作系统名
     * @param archName 架构名
     * @return 平台标签
     */
    public static PlatformTag of(String osName, String archName) {
        return new PlatformTag(osName, archName);
    }

    /**
     * 探测本机平台。
     *
     * <p>只读 {@code os.name} / {@code os.arch} 两个系统属性 —— 不跑外部命令、不查发行版文件。</p>
     *
     * @return 本机平台标签
     */
    public static PlatformTag detect() {
        return of(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    /**
     * 平台标签文本，即资产名里的那一段。
     *
     * @return 如 {@code windows-amd64}
     */
    public String value() {
        return os + "-" + arch;
    }

    /**
     * 判断给定的平台标签文本是否代表本平台。
     *
     * <p>除严格相等外，还接受<b>历史写法</b>：2026-10-17 之前 macOS 的架构段写作
     * {@code arm64}（如已发布的 {@code macos-arm64} 资产名）。新产出一律 {@code macos-aarch64}，
     * 但读取端必须能取到旧包，否则升级会「明明有包却说没有」（issue #287）。</p>
     *
     * @param tag 资产名里的平台段，可为 {@code null}
     * @return 是同一平台返回 {@code true}
     */
    public boolean matches(String tag) {
        if (tag == null) {
            return false;
        }
        return value().equals(tag)
                || (MACOS.equals(os) && AARCH64.equals(arch)
                        && (MACOS + "-" + LEGACY_AARCH64).equals(tag));
    }

    /**
     * 该平台在发布页上是否有包。
     *
     * <p><b>与形态无关</b>：这是「这台机器所属的平台标签有没有出现过包」的粗判，
     * 用于在联网之前给出可读的拒绝理由。具体某形态在某平台是否发版，由
     * {@link AssetSelector} 按真实资产判定（例如 macOS 桌面端原生包确实没有 —— 不带平台段的
     * CLI JVM 包则与平台无关，本方法判「无」也不影响它可用）。</p>
     *
     * @return 有包返回 {@code true}
     */
    public boolean hasPublishedPackages() {
        return PUBLISHED.contains(value());
    }

    /**
     * 该平台没有包的原因（给用户看的一句话）。有包时返回空串。
     *
     * <p>逐条写清「为什么没有」，而不是一句「不支持」—— 符合 `GUIDesign.md` 的
     * 「失败要能读懂：哪里错、怎么办」。</p>
     *
     * @return 原因；有包时为空串
     */
    public String unpublishedReason() {
        if (hasPublishedPackages()) {
            return "";
        }
        return switch (value()) {
            case WINDOWS + "-" + AARCH64 -> "Windows ARM64 暂无发布包：Maven Central 没有 win-aarch64 的"
                    + " OpenJFX 分类器（见 ReleaseProcess.md §3.2），暂时打不出桌面包";
            case MACOS + "-" + AMD64 -> "macOS Intel 暂无发布包：macOS 只发 Apple Silicon（macos-aarch64）";
            case UBUNTU + "-" + AARCH64 -> "Linux ARM64 暂无发布包：只发 Linux x86_64（ubuntu-amd64）";
            default -> "本平台（" + value() + "）不在发布范围内";
        };
    }

    /**
     * 文本形式即标签本身，便于直接拼进错误信息。
     *
     * @return {@link #value()}
     */
    @Override
    public String toString() {
        return value();
    }

    /** 系统名归一化：{@code Windows 11} → {@code windows}；{@code Mac OS X} → {@code macos}；{@code Linux} → {@code ubuntu}。 */
    private static String normalizeOs(String osName) {
        String token = token(osName);
        if (token.isEmpty()) {
            throw new IllegalArgumentException("操作系统名不能为空");
        }
        // 先判 macOS：`Darwin` 里含子串 "win"（dar-WIN），顺序反了会被判成 Windows（实测被单测抓到）。
        if (token.contains("mac") || token.contains("darwin") || token.contains("osx")) {
            return MACOS;
        }
        if (token.contains("win")) {
            return WINDOWS;
        }
        if (token.contains("linux")) {
            return UBUNTU;
        }
        return token;
    }

    /** 架构名归一化：{@code x86_64} / {@code x64} → {@code amd64}；{@code arm64} / {@code aarch64} → {@code aarch64}。 */
    private static String normalizeArch(String archName) {
        String token = token(archName);
        if (token.isEmpty()) {
            throw new IllegalArgumentException("架构名不能为空");
        }
        // 先判 ARM：aarch64 里也含 "64"，若先判 x86 分支的顺序反了就会误判。
        if (token.contains("aarch64") || token.contains("arm64")) {
            return AARCH64;
        }
        if (token.contains("amd64") || token.contains("x86-64") || token.contains("x64")
                || token.contains("x86")) {
            return AMD64;
        }
        return token;
    }

    /** 小写化 + 非字母数字压成连字符（{@code Mac OS X} → {@code mac-os-x}）。 */
    private static String token(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }
}
