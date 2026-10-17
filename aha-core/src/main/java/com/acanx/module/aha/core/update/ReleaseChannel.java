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
 * <p><b>默认通道是 {@link #STABLE}</b>（2026-10-16 由临时值 {@link #BETA} 改回，
 * issue #252 / 待办 TD-00019）。此前暂定 Beta 的原因很具体：正式 release
 * {@code 0.1.0} / {@code V0.1.1} / {@code V0.1.2} 的资产数均为 0（2026-10-16 实查），
 * 默认取 Stable 会让首次打开的升级面板是个空列表、被理解成「坏了」。
 * 改回的机制前提已经就位：{@code Update.Channel} <b>缺键不写盘</b>
 * （只有用户显式选过才落盘），所以「从没改过该键」的安装会<b>跟着默认值走</b>，
 * 用户显式选过的不受影响。</p>
 *
 * <p><b>形态边界</b>：Stable 通道目前只有 JVM 形态的资产（原生线的正式发布排在 1.x），
 * 所以原生形态装机在默认通道列不出可升级的版本，界面会说明原因并指出可切换的通道
 * （见 {@link ChannelContract#supports}）。</p>
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
     * 默认通道：<b>稳定 Stable</b>（2026-10-16 由临时值 Beta 改回，issue #252 / TD-00019）。
     *
     * <p>改回前确认过「缺键不写盘」的机制真的在生效：从没选过通道的安装会跟着这个值走，
     * 显式选过的不受影响（见 {@code DesktopSettings} 的 {@code explicitKeys}）。</p>
     */
    public static final ReleaseChannel DEFAULT = STABLE;

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
