package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.core.config.SystemPromptLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 桌面端设置（主题与字号）的持久化。
 *
 * <p>放在用户级目录（{@code AHA_HOME} → 默认为 {@code ~/.aha}）下，与 {@code Model.yml} 同级：
 * 换项目不该把界面偏好重置。文件名 {@code desktop.properties}——**刻意不是 YAML**：
 * 这是「程序自己写的偏好文件」，不该被当作可手写配置去参与 YAML 字段规范校验
 * （{@code ConfigLoader} 会按大驼峰校验 YAML 字段名，混在一起只会让用户困惑）。</p>
 *
 * <p>读取失败一律退回默认值：设置文件坏了不该让界面起不来。</p>
 *
 * @since 0.2.0
 */
public final class DesktopSettings {

    private static final Logger LOG = LoggerFactory.getLogger(DesktopSettings.class);

    /** 文件名。 */
    public static final String FILE_NAME = "desktop.properties";

    /** 主题键。 */
    public static final String KEY_THEME = "Ui.Theme";

    /** 字号键。 */
    public static final String KEY_FONT_SIZE = "Ui.FontSize";

    /** 默认主题：跟随系统。 */
    public static final Theme DEFAULT_THEME = Theme.SYSTEM;

    /** 默认字号（px）。 */
    public static final int DEFAULT_FONT_SIZE = 13;

    /** 字号下限（px）。 */
    public static final int MIN_FONT_SIZE = 12;

    /** 字号上限（px）。 */
    public static final int MAX_FONT_SIZE = 18;

    private final Path file;

    private Theme theme = DEFAULT_THEME;

    private int fontSize = DEFAULT_FONT_SIZE;

    private DesktopSettings(Path file) {
        this.file = file;
    }

    /**
     * 默认位置：{@code AHA_HOME/desktop.properties}。
     *
     * @return 路径；取不到用户目录时返回 {@code null}
     */
    public static Path defaultFile() {
        Path home = SystemPromptLoader.resolveUserHome();
        return home == null ? null : home.resolve(FILE_NAME);
    }

    /**
     * 读取设置。
     *
     * @param file 路径，可为 {@code null}（此时只有默认值）
     * @return 设置（永不抛异常）
     */
    public static DesktopSettings load(Path file) {
        DesktopSettings settings = new DesktopSettings(file);
        if (file == null || !Files.isRegularFile(file)) {
            return settings;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException | RuntimeException e) {
            LOG.warn("读取设置失败，使用默认值：{}（{}）", file, e.toString());
            return settings;
        }
        settings.theme = Theme.of(properties.getProperty(KEY_THEME));
        settings.fontSize = clamp(parse(properties.getProperty(KEY_FONT_SIZE)));
        return settings;
    }

    /**
     * 写入设置（失败只记日志：设置存不下来不该影响本次使用）。
     */
    public void save() {
        if (file == null) {
            return;
        }
        Properties properties = new Properties();
        properties.setProperty(KEY_THEME, theme.name());
        properties.setProperty(KEY_FONT_SIZE, String.valueOf(fontSize));
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(writer, "AHA 桌面端设置（由程序写入；删除本文件即恢复默认）");
            }
        } catch (IOException e) {
            LOG.warn("写入设置失败：{}（{}）", file, e.toString());
        }
    }

    /**
     * 设置文件路径。
     *
     * @return 路径，可为 {@code null}
     */
    public Path path() {
        return file;
    }

    /**
     * 主题。
     *
     * @return 主题
     */
    public Theme theme() {
        return theme;
    }

    /**
     * 设置主题。
     *
     * @param value 主题
     */
    public void theme(Theme value) {
        this.theme = value == null ? DEFAULT_THEME : value;
    }

    /**
     * 字号。
     *
     * @return 字号（px）
     */
    public int fontSize() {
        return fontSize;
    }

    /**
     * 设置字号（自动夹到 {@value #MIN_FONT_SIZE}~{@value #MAX_FONT_SIZE}）。
     *
     * @param value 字号（px）
     */
    public void fontSize(int value) {
        this.fontSize = clamp(value);
    }

    /**
     * 字号夹取。
     *
     * @param value 字号
     * @return 夹取后的字号
     */
    public static int clamp(int value) {
        return Math.max(MIN_FONT_SIZE, Math.min(MAX_FONT_SIZE, value));
    }

    private static int parse(String text) {
        if (text == null || text.isBlank()) {
            return DEFAULT_FONT_SIZE;
        }
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            return DEFAULT_FONT_SIZE;
        }
    }
}
