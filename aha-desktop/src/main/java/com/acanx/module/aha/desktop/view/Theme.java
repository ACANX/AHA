package com.acanx.module.aha.desktop.view;

import java.util.Locale;

/**
 * 界面主题。
 *
 * <p>三个选项对应 {@code GUIDesign.md} 第 5.1 节「视图 → 暗色 / 亮色 / 跟随系统」。
 * 「跟随系统」的判定拆成 {@link #resolve(boolean)}（纯函数），系统偏好的**读取**留在
 * {@link SystemTheme}，于是「跟随系统到底解析成哪个主题」可以脱离图形环境测试。</p>
 *
 * @since 0.2.0
 */
public enum Theme {

    /** 暗色（设计稿的主色）。 */
    DARK("暗色"),

    /** 亮色。 */
    LIGHT("亮色"),

    /** 跟随系统。 */
    SYSTEM("跟随系统");

    private final String label;

    Theme(String label) {
        this.label = label;
    }

    /**
     * 中文名（菜单与设置面板显示）。
     *
     * @return 中文名
     */
    public String label() {
        return label;
    }

    /**
     * 下一个主题（{@code /theme} 与菜单项循环切换用）。
     *
     * @return 下一个主题
     */
    public Theme next() {
        Theme[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    /**
     * 解析为实际生效的主题。
     *
     * @param systemPrefersDark 系统是否偏好暗色
     * @return {@link #DARK} 或 {@link #LIGHT}；{@link #SYSTEM} 按系统偏好解析
     */
    public Theme resolve(boolean systemPrefersDark) {
        if (this != SYSTEM) {
            return this;
        }
        return systemPrefersDark ? DARK : LIGHT;
    }

    /**
     * 从文本解析（设置文件用）。
     *
     * @param text 文本（大小写不敏感）
     * @return 主题；无法识别时返回 {@link #SYSTEM}
     */
    public static Theme of(String text) {
        if (text == null || text.isBlank()) {
            return SYSTEM;
        }
        try {
            return valueOf(text.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SYSTEM;
        }
    }
}
