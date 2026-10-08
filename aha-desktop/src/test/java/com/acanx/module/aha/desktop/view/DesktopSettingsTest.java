package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DesktopSettings} 测试：默认值、往返保存与坏文件兜底。
 *
 * @since 0.2.0
 */
class DesktopSettingsTest {

    @Test
    void missingFileFallsBackToDefaults(@TempDir Path dir) {
        DesktopSettings settings = DesktopSettings.load(dir.resolve("不存在.properties"));

        assertThat(settings.theme()).isEqualTo(DesktopSettings.DEFAULT_THEME);
        assertThat(settings.fontSize()).isEqualTo(DesktopSettings.DEFAULT_FONT_SIZE);
        assertThat(settings.path()).isNotNull();
    }

    @Test
    void nullFileOnlyHasDefaults() {
        DesktopSettings settings = DesktopSettings.load(null);
        assertThat(settings.theme()).isEqualTo(DesktopSettings.DEFAULT_THEME);
        settings.save();
        assertThat(settings.path()).isNull();
    }

    @Test
    void saveThenLoadRoundTrips(@TempDir Path dir) {
        Path file = dir.resolve(DesktopSettings.FILE_NAME);
        DesktopSettings settings = DesktopSettings.load(file);
        settings.theme(Theme.LIGHT);
        settings.fontSize(16);

        settings.save();

        assertThat(Files.exists(file)).isTrue();
        DesktopSettings reloaded = DesktopSettings.load(file);
        assertThat(reloaded.theme()).isEqualTo(Theme.LIGHT);
        assertThat(reloaded.fontSize()).isEqualTo(16);
    }

    @Test
    void corruptFileFallsBackInsteadOfThrowing(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(DesktopSettings.FILE_NAME);
        Files.writeString(file, "不是合法的 properties 文件\u0000\u0007");

        DesktopSettings settings = DesktopSettings.load(file);

        assertThat(settings.theme()).isEqualTo(DesktopSettings.DEFAULT_THEME);
        assertThat(settings.fontSize()).isEqualTo(DesktopSettings.DEFAULT_FONT_SIZE);
    }

    @Test
    void unknownValuesFallBackToDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve(DesktopSettings.FILE_NAME);
        Files.writeString(file, DesktopSettings.KEY_THEME + "=眼花缭乱\n"
                + DesktopSettings.KEY_FONT_SIZE + "=很大\n");

        DesktopSettings settings = DesktopSettings.load(file);

        assertThat(settings.theme()).isEqualTo(Theme.SYSTEM);
        assertThat(settings.fontSize()).isEqualTo(DesktopSettings.DEFAULT_FONT_SIZE);
    }

    @Test
    void fontSizeIsClamped() {
        DesktopSettings settings = DesktopSettings.load(null);
        settings.fontSize(2);
        assertThat(settings.fontSize()).isEqualTo(DesktopSettings.MIN_FONT_SIZE);
        settings.fontSize(99);
        assertThat(settings.fontSize()).isEqualTo(DesktopSettings.MAX_FONT_SIZE);
        assertThat(DesktopSettings.clamp(13)).isEqualTo(13);
    }

    @Test
    void saveCreatesParentDirectories(@TempDir Path dir) {
        Path file = dir.resolve("nested/deeper/" + DesktopSettings.FILE_NAME);
        DesktopSettings settings = DesktopSettings.load(file);
        settings.theme(Theme.DARK);

        settings.save();

        assertThat(Files.isRegularFile(file)).isTrue();
        assertThat(DesktopSettings.load(file).theme()).isEqualTo(Theme.DARK);
    }
}
