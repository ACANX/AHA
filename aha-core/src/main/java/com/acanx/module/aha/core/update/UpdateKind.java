package com.acanx.module.aha.core.update;

import java.util.Locale;

/**
 * 可升级的「形态」：桌面端 / CLI × JVM 便携包 / 原生单文件（issue #51 §2.1、§2.3）。
 *
 * <p>四个取值对应发布页上的四族资产，它们的命名规则不同（平台段有无、前缀大小写都不同），
 * 因此把「目标词」与「资产前缀」固化在这里，由 {@link ChannelContract} 统一生成与解析 ——
 * 命名规则只能有一处，否则必然漂移（历史上 JVM 线与原生线就各写过一套）。</p>
 *
 * @since 0.2.0
 */
public enum UpdateKind {

    /** 桌面端 JVM 便携包（{@code aha-desktop.jar} + 启动脚本）。 */
    DESKTOP_JVM("Desktop-JVM", "aha-desktop-", true, false),

    /** 桌面端原生单文件（native-image 产物）。 */
    DESKTOP_NATIVE("Desktop-Native", "AHA-Desktop-Native-", true, true),

    /** CLI JVM 包（平台无关，一份包通吃）。 */
    CLI_JVM("Cli-JVM", "aha-cli-", false, false),

    /** CLI 原生单文件。 */
    CLI_NATIVE("Cli-Native", "AHA-Cli-Native-", true, true);

    private final String targetWord;

    private final String assetPrefix;

    private final boolean hasPlatform;

    private final boolean nativeImage;

    UpdateKind(String targetWord, String assetPrefix, boolean hasPlatform, boolean nativeImage) {
        this.targetWord = targetWord;
        this.assetPrefix = assetPrefix;
        this.hasPlatform = hasPlatform;
        this.nativeImage = nativeImage;
    }

    /**
     * 快照 tag 里的目标词（大写驼峰带连字符），也是资产名里的目标段。
     *
     * @return 目标词，如 {@code Desktop-JVM}
     */
    public String targetWord() {
        return targetWord;
    }

    /**
     * 资产名前缀。
     *
     * @return 前缀，如 {@code aha-desktop-}（稳定 / dev 线的 JVM 包）或 {@code AHA-Desktop-Native-}
     */
    public String assetPrefix() {
        return assetPrefix;
    }

    /**
     * 资产名里是否带平台段（{@code windows-amd64} 之类）。
     *
     * <p>CLI 的 JVM 包<b>没有</b>平台维度：它是平台无关的一份包。给它传平台段是调用方的错，
     * 由 {@link ChannelContract#assetName} 直接拒绝。</p>
     *
     * @return 需要平台段返回 {@code true}
     */
    public boolean hasPlatform() {
        return hasPlatform;
    }

    /**
     * 是否原生镜像（单文件）形态。
     *
     * <p>形态决定升级时怎么替换：JVM 形态是「jar + 启动脚本」平铺替换，原生形态是「替换那个可执行文件」。
     * 两者<b>不可混升</b>（issue #51 §2.3 硬约束）。</p>
     *
     * @return 原生形态返回 {@code true}
     */
    public boolean nativeImage() {
        return nativeImage;
    }

    /**
     * tag 里 {@code -aha-} 之后的目标后缀（仅原生线的 dev tag 用得上）。
     *
     * @return 小写目标后缀，如 {@code desktop-native}
     */
    public String tagSuffix() {
        return targetWord.toLowerCase(Locale.ROOT);
    }

    /**
     * 容错解析：接受 {@code Desktop-JVM} / {@code desktop-jvm} / {@code desktop_jvm} 等写法。
     *
     * @param text 文本，可为 {@code null}
     * @return 形态；不认识时返回 {@code null}（由调用方决定报错方式）
     */
    public static UpdateKind of(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (UpdateKind kind : values()) {
            if (kind.targetWord.toLowerCase(Locale.ROOT).equals(normalized)) {
                return kind;
            }
        }
        return null;
    }
}
