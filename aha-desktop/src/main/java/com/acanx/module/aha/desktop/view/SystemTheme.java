package com.acanx.module.aha.desktop.view;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 系统主题偏好。
 *
 * <p>JavaFX 22 起提供 {@code Platform.getPreferences().getColorScheme()}，这里包一层：
 * 取不到（旧版本、无头环境、非 UI 线程）一律按**暗色**处理——设计稿的主色是暗色，
 * 猜错的代价最小。</p>
 *
 * @since 0.2.0
 */
public final class SystemTheme {

    private static final Logger LOG = LoggerFactory.getLogger(SystemTheme.class);

    private SystemTheme() {
    }

    /**
     * 系统是否偏好暗色。
     *
     * <p>必须在 UI 线程调用（{@code Platform.getPreferences()} 的约束）。</p>
     *
     * @return 偏好暗色返回 {@code true}
     */
    public static boolean prefersDark() {
        try {
            Platform.Preferences preferences = Platform.getPreferences();
            if (preferences == null || preferences.getColorScheme() == null) {
                return true;
            }
            return preferences.getColorScheme() != ColorScheme.LIGHT;
        } catch (RuntimeException | Error e) {
            // 不是 UI 线程、或该平台不支持：按暗色处理，不抛给调用方
            LOG.debug("读取系统主题偏好失败，按暗色处理：{}", e.toString());
            return true;
        }
    }
}
