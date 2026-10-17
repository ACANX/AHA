package com.acanx.module.aha.core.update;

import java.util.Locale;

/**
 * 可升级的「形态」：`<product>` × `<form>` 四组合（issue #51 §2.1、§2.3；命名文法见 issue #269）。
 *
 * <p>四个取值对应发布页上的四族资产。自 0.1.4 起它们共用**一套文法**：</p>
 *
 * <pre>{@code
 * aha-<product>-<form>-<version>-<os>-<arch>-<runtime>.zip
 * }</pre>
 *
 * <p>因此本枚举只保留「组合」这一层信息（product / form），前缀、tag 后缀、是否原生都由它派生 ——
 * 命名规则只允许有一处（{@link ChannelContract}），否则历史上的「JVM 线与原生线各写一套」必然重演。</p>
 *
 * <p><b>旧名兼容</b>：0.1.3 及更早的资产名是两套（{@code aha-desktop-…} / {@code AHA-Desktop-Native-…}），
 * 0.1.4 的一个版本周期内仍要能解析，故 {@link #legacyAssetPrefix()} 与
 * {@link #legacyTargetWord()} 保留旧写法，由 {@link ChannelContract#parseAsset} 走兼容分支。</p>
 *
 * @since 0.2.0
 */
public enum UpdateKind {

    /** 桌面端 JVM 便携包（{@code aha-desktop.jar} + 启动脚本）。 */
    DESKTOP_JVM("Desktop-JVM", "desktop", "jvm"),

    /** 桌面端原生单文件（native-image 产物）。 */
    DESKTOP_NATIVE("Desktop-Native", "desktop", "native"),

    /** CLI JVM 包（三个平台三个名字，内容暂相同，见 issue #269 §6 第 7 条）。 */
    CLI_JVM("Cli-JVM", "cli", "jvm"),

    /** CLI 原生单文件。 */
    CLI_NATIVE("Cli-Native", "cli", "native");

    private final String targetWord;

    private final String product;

    private final String form;

    UpdateKind(String targetWord, String product, String form) {
        this.targetWord = targetWord;
        this.product = product;
        this.form = form;
    }

    /**
     * 快照 tag 里的目标词（大写驼峰带连字符），也是**旧**资产名里的目标段。
     *
     * @return 目标词，如 {@code Desktop-JVM}
     */
    public String targetWord() {
        return targetWord;
    }

    /**
     * 产品段。
     *
     * @return {@code desktop} 或 {@code cli}
     */
    public String product() {
        return product;
    }

    /**
     * 形态段。
     *
     * @return {@code jvm} 或 {@code native}
     */
    public String form() {
        return form;
    }

    /**
     * 资产名前缀（新文法）。
     *
     * @return 前缀，如 {@code aha-desktop-jvm-}
     */
    public String assetPrefix() {
        return "aha-" + product + "-" + form + "-";
    }

    /**
     * 旧资产名前缀（0.1.3 及更早，兼容用）。
     *
     * <p>JVM 线是 {@code aha-desktop-} / {@code aha-cli-}；原生线是
     * {@code AHA-Desktop-Native-} / {@code AHA-Cli-Native-}。</p>
     *
     * @return 旧前缀
     */
    public String legacyAssetPrefix() {
        return nativeImage() ? "AHA-" + targetWord + "-" : "aha-" + product + "-";
    }

    /**
     * 旧 tag 的目标词（{@code Desktop-JVM} / {@code Desktop-Native} / …），供 Alpha 旧 tag 解析。
     *
     * @return 旧目标词
     */
    public String legacyTargetWord() {
        return targetWord;
    }

    /**
     * 资产名里是否带平台段。
     *
     * <p>自 issue #269 起**所有形态都带**平台段：CLI-JVM 原本是平台无关的一份包，
     * 现在先按平台拆成三个名字（内容暂相同），后续再真正按平台生成。</p>
     *
     * @return 恒为 {@code true}
     */
    public boolean hasPlatform() {
        return true;
    }

    /**
     * 是否原生镜像（单文件）形态。
     *
     * <p>形态决定升级时怎么替换：JVM 形态是「jar + 启动脚本」平铺替换，原生形态是「替换那个可执行文件」；
     * 同时决定 runtime 段是 {@code jdk<N>} 还是 {@code graalvm<N>}。两者<b>不可混升</b>（issue #51 §2.3）。</p>
     *
     * @return 原生形态返回 {@code true}
     */
    public boolean nativeImage() {
        return "native".equals(form);
    }

    /**
     * dev 线 tag 里 {@code -aha-} 之后的后缀（{@code desktop-jvm} / {@code desktop-native} / …）。
     *
     * @return 小写后缀
     */
    public String tagSuffix() {
        return product + "-" + form;
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
            if (kind.tagSuffix().equals(normalized)
                    || kind.targetWord.toLowerCase(Locale.ROOT).equals(normalized)) {
                return kind;
            }
        }
        return null;
    }
}
