package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.core.update.ReleaseChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 桌面端设置的读写（含 issue #51 §2.4 的「缺键取默认、不主动写盘」）。
 *
 * <p>这条口径不是洁癖：升级通道的默认值是**暂定**的（Beta，§10 D9）。首个带资产的正式发版落地后
 * 要把代码默认值改回 Stable，那时「从没改过这个键」的安装应当跟着切过去 —— 若每次保存都把当前值
 * 写盘，默认值就被冻在磁盘上了，之后改代码也没用。所以这里专门测「没设过就不写」。</p>
 *
 * @since 0.2.0
 */
class DesktopSettingsTest {

    @TempDir
    Path tempDir;

    private Path file() {
        return tempDir.resolve(DesktopSettings.FILE_NAME);
    }

    private Properties read() throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(file(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        return properties;
    }

    @Test
    void defaultsMatchTheDecisions() {
        DesktopSettings settings = DesktopSettings.load(file());

        assertThat(settings.updateChannel()).isEqualTo(ReleaseChannel.DEFAULT);
        assertThat(settings.updateChannel()).isEqualTo(ReleaseChannel.STABLE);
        assertThat(settings.updateChannelExplicit()).isFalse();
        assertThat(settings.checkOnStartup()).isFalse();
        assertThat(settings.alphaAcknowledged()).isFalse();
        assertThat(settings.backupKeep()).isEqualTo(DesktopSettings.DEFAULT_BACKUP_KEEP);
        assertThat(settings.lastCheckAt()).isNull();
    }

    @Test
    void neverWritesTheChannelKeyUnlessTheUserSetIt() throws IOException {
        DesktopSettings settings = DesktopSettings.load(file());

        // 只改主题（界面上最常见的动作）也要保存，但**不该**顺手把通道默认值写进去
        settings.theme(Theme.DARK);
        settings.save();

        Properties written = read();
        assertThat(written.getProperty(DesktopSettings.KEY_THEME)).isEqualTo(Theme.DARK.name());
        assertThat(written).doesNotContainKey(DesktopSettings.KEY_UPDATE_CHANNEL);
        assertThat(written).doesNotContainKey(DesktopSettings.KEY_UPDATE_CHECK_ON_STARTUP);
    }

    @Test
    void installThatNeverPickedAChannelFollowsTheCodeDefault() throws IOException {
        // issue #252 验收：盘上没有 Update.Channel 的安装（老安装 / 从没选过）跟着代码默认值走。
        // 先保存一个**别的**设置，确认通道键仍不会被写盘 —— 这正是默认值能改回 Stable 的前提
        DesktopSettings settings = DesktopSettings.load(file());
        settings.theme(Theme.DARK);
        settings.save();
        assertThat(read()).doesNotContainKey(DesktopSettings.KEY_UPDATE_CHANNEL);

        DesktopSettings reloaded = DesktopSettings.load(file());

        assertThat(reloaded.updateChannel()).isEqualTo(ReleaseChannel.DEFAULT);
        assertThat(reloaded.updateChannel()).isEqualTo(ReleaseChannel.STABLE);
        assertThat(reloaded.updateChannelExplicit()).isFalse();
    }

    @Test
    void installThatPickedBetaExplicitlyKeepsIt() throws IOException {
        // issue #252 验收的另一半：显式选过 Beta 的安装**不受**默认值改动影响
        DesktopSettings settings = DesktopSettings.load(file());
        settings.updateChannel(ReleaseChannel.BETA);
        settings.save();

        DesktopSettings reloaded = DesktopSettings.load(file());

        assertThat(reloaded.updateChannel()).isEqualTo(ReleaseChannel.BETA);
        assertThat(reloaded.updateChannelExplicit()).isTrue();
    }

    @Test
    void persistsExplicitChoicesAndReloadsThem() throws IOException {
        DesktopSettings settings = DesktopSettings.load(file());
        settings.updateChannel(ReleaseChannel.ALPHA);
        settings.checkOnStartup(true);
        settings.alphaAcknowledged(true);
        settings.backupKeep(5);
        settings.lastCheckAt("2026-10-16T10:00:00Z");
        settings.save();

        DesktopSettings reloaded = DesktopSettings.load(file());

        assertThat(reloaded.updateChannel()).isEqualTo(ReleaseChannel.ALPHA);
        assertThat(reloaded.updateChannelExplicit()).isTrue();
        assertThat(reloaded.checkOnStartup()).isTrue();
        assertThat(reloaded.alphaAcknowledged()).isTrue();
        assertThat(reloaded.backupKeep()).isEqualTo(5);
        assertThat(reloaded.lastCheckAt()).isEqualTo("2026-10-16T10:00:00Z");
    }

    @Test
    void explicitChannelSurvivesLaterSavesOfOtherSettings() throws IOException {
        DesktopSettings settings = DesktopSettings.load(file());
        settings.updateChannel(ReleaseChannel.STABLE);
        settings.save();
        settings.theme(Theme.LIGHT);
        settings.save();

        assertThat(read().getProperty(DesktopSettings.KEY_UPDATE_CHANNEL))
                .isEqualTo(ReleaseChannel.STABLE.key());
    }

    @Test
    void invalidValuesFallBackToDefaultsInsteadOfBreakingTheUi() throws IOException {
        Files.writeString(file(), """
                Ui.Theme=NOT_A_THEME
                Ui.FontSize=abc
                %s=nightly
                %s=maybe
                %s=many
                """.formatted(DesktopSettings.KEY_UPDATE_CHANNEL,
                DesktopSettings.KEY_UPDATE_CHECK_ON_STARTUP,
                DesktopSettings.KEY_UPDATE_BACKUP_KEEP));

        DesktopSettings settings = DesktopSettings.load(file());

        assertThat(settings.theme()).isEqualTo(DesktopSettings.DEFAULT_THEME);
        assertThat(settings.fontSize()).isEqualTo(DesktopSettings.DEFAULT_FONT_SIZE);
        assertThat(settings.updateChannel()).isEqualTo(ReleaseChannel.DEFAULT);
        // 值不合法 ⇒ 不算「用户设过」：默认值将来改动时，这条安装应当跟着走
        assertThat(settings.updateChannelExplicit()).isFalse();
        assertThat(settings.checkOnStartup()).isFalse();
        assertThat(settings.backupKeep()).isEqualTo(DesktopSettings.DEFAULT_BACKUP_KEEP);

        // 写坏的值也不算「设过」：再保存一次不会把这几个键写死
        settings.theme(Theme.DARK);
        settings.save();
        Properties written = read();
        assertThat(written).doesNotContainKey(DesktopSettings.KEY_UPDATE_CHANNEL);
        assertThat(written).doesNotContainKey(DesktopSettings.KEY_UPDATE_CHECK_ON_STARTUP);
        assertThat(written).doesNotContainKey(DesktopSettings.KEY_UPDATE_BACKUP_KEEP);
    }

    @Test
    void backupKeepIsClampedOnBothEnds() {
        assertThat(DesktopSettings.clampBackupKeep(-3)).isEqualTo(DesktopSettings.MIN_BACKUP_KEEP);
        assertThat(DesktopSettings.clampBackupKeep(0)).isZero();
        assertThat(DesktopSettings.clampBackupKeep(99)).isEqualTo(DesktopSettings.MAX_BACKUP_KEEP);
    }

    @Test
    void aCorruptedFileJustMeansDefaults() throws IOException {
        Files.writeString(file(), "这不是 properties\n\u0000\u0001");

        DesktopSettings settings = DesktopSettings.load(file());

        assertThat(settings.theme()).isEqualTo(DesktopSettings.DEFAULT_THEME);
        assertThat(settings.updateChannel()).isEqualTo(ReleaseChannel.DEFAULT);
    }
}
