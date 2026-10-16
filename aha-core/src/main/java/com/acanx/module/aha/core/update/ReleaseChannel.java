package com.acanx.module.aha.core.update;

import java.util.Locale;

/**
 * 升级通道（issue #51 §2）：稳定 / 开发 / 快照。
 *
 * <p>三者对应三种版本形态，落到发布页上是三类载体（详见 {@link ChannelContract}）：</p>
 *
 * <ul>
 *   <li>{@link #STABLE} —— 正式 release（{@code V0.1.3}），长期保留；</li>
 *   <li>{@link #BETA} —— dev 线预发行（{@code V0.1.3.00233-aha-jvm}），合并到 {@code dev} 后自动出包；</li>
 *   <li>{@link #ALPHA} —— PR 快照预发行（{@code V0.1.3.00233.10161125-AHA-Desktop-JVM-SNAPSHOT}），
 *       同 PR 同目标保留最近 3 条、有效期 7 天。</li>
 * </ul>
 *
 * <p><b>默认通道是 {@link #BETA}（暂定）</b>：Stable 通道当前没有任何资产
 * （正式 release {@code V0.1.1} / {@code V0.1.2} 的资产数为 0，见 issue #51 §1.2 实查），
 * 因此先以 Beta 作为默认；<b>等首个带资产的正式发版落地后，再把默认值改回 Stable</b>
 * （issue #51 §10 D9，已登记待办 TD-00019）。</p>
 *
 * @since 0.2.0
 */
public enum ReleaseChannel {

    /** 稳定通道：正式发布，长期保留。 */
    STABLE("stable", "稳定 Stable", "正式发布（走完整发布流程），长期保留，最稳"),

    /** 开发通道：dev 分支最新合并产物，未做发布验收。 */
    BETA("beta", "开发 Beta", "以 dev 分支最新合并的代码构建的预发行包，未做发布验收"),

    /** 快照通道：某个未合并 PR 的试包，非交付物。 */
    ALPHA("alpha", "快照 Alpha", "某个未合并 PR 的试包，不是交付物；7 天后自动清理");

    /**
     * 默认通道：<b>暂定 Beta</b>（Stable 暂无可下包，见类注释与 issue #51 §10 D9）。
     */
    public static final ReleaseChannel DEFAULT = BETA;

    private final String key;

    private final String label;

    private final String description;

    ReleaseChannel(String key, String label, String description) {
        this.key = key;
        this.label = label;
        this.description = description;
    }

    /**
     * 配置里的取值（{@code desktop.properties} 的 {@code Update.Channel}）。
     *
     * @return 小写键名，如 {@code beta}
     */
    public String key() {
        return key;
    }

    /**
     * 界面上的中文标签。
     *
     * @return 标签，如 {@code 开发 Beta}
     */
    public String label() {
        return label;
    }

    /**
     * 界面上的通道说明（要让用户知道自己在吃什么包，尤其是 Beta / Alpha 是预发行）。
     *
     * @return 一句话说明
     */
    public String description() {
        return description;
    }

    /**
     * 容错解析配置值：大小写不敏感、允许前后空白；不认识的值<b>回落到默认通道</b>
     * （与 {@code Theme.of} 同一取舍：配置坏了不该让程序起不来）。
     *
     * @param text 配置值，可为 {@code null}
     * @return 通道，永不返回 {@code null}
     */
    public static ReleaseChannel of(String text) {
        if (text == null) {
            return DEFAULT;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        for (ReleaseChannel channel : values()) {
            if (channel.key.equals(normalized)) {
                return channel;
            }
        }
        return DEFAULT;
    }
}
