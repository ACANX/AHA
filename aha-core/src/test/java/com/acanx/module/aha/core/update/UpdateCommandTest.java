package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 无界面升级入口的参数解析（issue #51 §7 P2）。
 *
 * <p>这里只测「解析」：真跑要下载、要换装、要重新拉起，属 §11.3 由 ACANX 在 Windows 上验。
 * 但解析错了会让脚本静默做错事（比如把 {@code --yes} 吃掉、把目标版本读成通道名），所以它必须被测住。</p>
 *
 * @since 0.2.0
 */
class UpdateCommandTest {

    @Test
    void parsesTheMinimalForm() {
        UpdateCommand.Options options = UpdateCommand
                .parse(List.of("--update-to", "0.1.3.00243")).orElseThrow();

        assertThat(options.targetVersion()).isEqualTo(AhaVersion.parse("0.1.3.00243"));
        // 默认通道取 ReleaseChannel.DEFAULT（暂定 Beta：Stable 目前没有可下包）
        assertThat(options.channel()).isEqualTo(ReleaseChannel.DEFAULT);
        assertThat(options.confirmed()).isFalse();
        assertThat(options.checkOnly()).isFalse();
        assertThat(options.kind()).isNull();
    }

    @Test
    void parsesAllSwitchesInAnyOrder() {
        UpdateCommand.Options options = UpdateCommand.parse(List.of(
                "--yes", "--check", "--kind", "desktop-native",
                "--update-to", "0.1.3.00243", "--channel", "alpha")).orElseThrow();

        assertThat(options.confirmed()).isTrue();
        assertThat(options.checkOnly()).isTrue();
        assertThat(options.kind()).isEqualTo(UpdateKind.DESKTOP_NATIVE);
        assertThat(options.channel()).isEqualTo(ReleaseChannel.ALPHA);
        assertThat(options.targetVersion()).isEqualTo(AhaVersion.parse("0.1.3.00243"));
    }

    @Test
    void returnsEmptyWhenNotAnUpdateInvocation() {
        assertThat(UpdateCommand.parse(List.of("--some-other-flag"))).isEmpty();
        assertThat(UpdateCommand.isUpdateInvocation(List.of("--some-other-flag"))).isFalse();
        assertThat(UpdateCommand.isUpdateInvocation(List.of("--update-to", "0.1.3"))).isTrue();
    }

    @Test
    void refusesBadVersionsChannelsKindsAndMissingValues() {
        assertThatThrownBy(() -> UpdateCommand.parse(List.of("--update-to", "0.1")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("目标版本不合法");

        assertThatThrownBy(() -> UpdateCommand.parse(List.of("--update-to", "0.1.3", "--channel", "nightly")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("不认识的通道");

        assertThatThrownBy(() -> UpdateCommand.parse(List.of("--update-to", "0.1.3", "--kind", "web")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("不认识的形态");

        // 取值缺失（后面跟的是另一个开关，或不跟东西）都要报，而不是把开关当成取值
        assertThatThrownBy(() -> UpdateCommand.parse(List.of("--update-to", "--yes")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("缺少取值");
        assertThatThrownBy(() -> UpdateCommand.parse(List.of("--update-to", "0.1.3", "--channel")))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("缺少取值");
    }

    @Test
    void acceptsAllThreeVersionFormsOfTheThreeChannels() {
        assertThat(UpdateCommand.parse(List.of("--update-to", "0.1.3")).orElseThrow().targetVersion())
                .isEqualTo(AhaVersion.parse("0.1.3"));
        assertThat(UpdateCommand.parse(List.of("--update-to", "0.1.3.00243")).orElseThrow().targetVersion()
                .segmentCount()).isEqualTo(4);
        assertThat(UpdateCommand.parse(List.of("--update-to", "0.1.3.00243.10161125"))
                .orElseThrow().targetVersion().segmentCount()).isEqualTo(5);
    }
}
