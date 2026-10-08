package com.acanx.module.aha.desktop.view;

import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 系统主题偏好。
 *
 * <p>JavaFX 22 起提供 {@code Platform.getPreferences().getColorScheme()}，这里包一层。
 * 「跟随系统」的三种情形按可靠性从高到低逐级回退：</p>
 *
 * <ol>
 *   <li>颜色方案明确为 {@link ColorScheme#DARK} / {@link ColorScheme#LIGHT}——直接采用；</li>
 *   <li>颜色方案未定（返回 {@code null}）——退一步用系统**背景色**的亮度判断；
 *       这两者都来自同一份系统偏好数据，因此第二个判据能兜住第一个的空白；</li>
 *   <li>两者都取不到（无头环境、原生镜像里系统接口不可用、非界面线程）——按**亮色**处理。</li>
 * </ol>
 *
 * <p><strong>为什么第三步是亮色而不是过去的暗色</strong>：旧实现一律回退到暗色，
 * 于是在原生镜像里（系统偏好读取失败）桌面端永远以暗黑主题启动，与系统实际的亮色皮肤不一致
 * （issue #44）。亮色是 Windows 与主流桌面环境的出厂默认，猜错的代价更小；
 * 而「猜错」这件事本身由 {@link #onColorSchemeChanged(Runnable)} 在系统偏好变化时纠正。</p>
 *
 * @since 0.2.0
 */
public final class SystemTheme {

    private static final Logger LOG = LoggerFactory.getLogger(SystemTheme.class);

    /** 三级回退都失败时的默认值：亮色（系统出厂默认）。 */
    private static final boolean FALLBACK_DARK = false;

    private SystemTheme() {
    }

    /**
     * 系统是否偏好暗色。
     *
     * <p>必须在界面线程调用（{@code Platform.getPreferences()} 的约束）。</p>
     *
     * @return 偏好暗色返回 {@code true}
     */
    public static boolean prefersDark() {
        try {
            Platform.Preferences preferences = Platform.getPreferences();
            if (preferences == null) {
                return FALLBACK_DARK;
            }
            ColorScheme scheme = preferences.getColorScheme();
            if (scheme == ColorScheme.DARK) {
                return true;
            }
            if (scheme == ColorScheme.LIGHT) {
                return false;
            }
            // 颜色方案未定：用系统背景色的亮度兜底（深浅两套皮肤的底色差异极大，判据稳定）
            Color background = preferences.getBackgroundColor();
            if (background != null) {
                return background.getBrightness() < 0.5;
            }
        } catch (RuntimeException | Error e) {
            // 不是界面线程、或该平台不支持：按亮色处理，不抛给调用方
            LOG.debug("读取系统主题偏好失败，按亮色处理：{}", e.toString());
        }
        return FALLBACK_DARK;
    }

    /**
     * 订阅系统配色变化（用户在系统设置里切换深色 / 浅色皮肤时回调）。
     *
     * <p>回调在界面线程触发。取不到系统偏好（旧版本、无头环境、非界面线程）时静默跳过——
     * 订阅不上只是「不能实时跟随」，不该影响启动。</p>
     *
     * @param listener 变化回调
     */
    public static void onColorSchemeChanged(Runnable listener) {
        if (listener == null) {
            return;
        }
        try {
            Platform.Preferences preferences = Platform.getPreferences();
            if (preferences == null) {
                return;
            }
            preferences.colorSchemeProperty().addListener((observable, old, now) -> listener.run());
        } catch (RuntimeException | Error e) {
            LOG.debug("订阅系统主题变化失败：{}", e.toString());
        }
    }
}
