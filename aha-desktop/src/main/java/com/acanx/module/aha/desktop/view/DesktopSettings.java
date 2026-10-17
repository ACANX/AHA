package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.core.config.SystemPromptLoader;
import com.acanx.module.aha.core.update.ReleaseChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;

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

    /** 升级通道键（{@code stable} / {@code beta} / {@code alpha}）。 */
    public static final String KEY_UPDATE_CHANNEL = "Update.Channel";

    /** 启动时是否检查更新（只检查，不自动下载）。 */
    public static final String KEY_UPDATE_CHECK_ON_STARTUP = "Update.CheckOnStartup";

    /** 是否已确认过「Alpha 是非交付物」。 */
    public static final String KEY_UPDATE_ALPHA_ACK = "Update.AlphaAcknowledged";

    /** 保留备份份数。 */
    public static final String KEY_UPDATE_BACKUP_KEEP = "Update.BackupKeep";

    /** 上次检查时间（**仅展示用，不参与决策**）。 */
    public static final String KEY_UPDATE_LAST_CHECK_AT = "Update.LastCheckAt";

    /** 默认升级通道：{@link ReleaseChannel#DEFAULT}（Stable；2026-10-16 由临时值 Beta 改回）。 */
    public static final ReleaseChannel DEFAULT_UPDATE_CHANNEL = ReleaseChannel.DEFAULT;

    /** 默认：启动时不检查更新（先按需）。 */
    public static final boolean DEFAULT_CHECK_ON_STARTUP = false;

    /** 默认保留备份份数。 */
    public static final int DEFAULT_BACKUP_KEEP = 2;

    /** 备份份数下限。 */
    public static final int MIN_BACKUP_KEEP = 0;

    /** 备份份数上限（一个包 25–55 MB，别让备份无限涨）。 */
    public static final int MAX_BACKUP_KEEP = 10;

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

    private ReleaseChannel updateChannel = DEFAULT_UPDATE_CHANNEL;

    private boolean checkOnStartup = DEFAULT_CHECK_ON_STARTUP;

    private boolean alphaAcknowledged;

    private int backupKeep = DEFAULT_BACKUP_KEEP;

    private String lastCheckAt;

    /**
     * 「用户显式设过」的键。
     *
     * <p>为什么需要它：升级通道的默认值会随版本调整（2026-10-16 就从临时值 Beta 改回了 Stable，
     * 见 issue #252 / TD-00019），「从没改过这个键」的安装应当跟着默认值切过去。
     * 若每次保存都把当前值写盘，默认值就被冻在磁盘上了，之后改代码默认值也没用。
     * 所以 {@link #save()} 只写用户显式设过的 {@code Update.*} 键 ——
     * 上面那次切换正是靠这条机制才惠及老安装的。</p>
     */
    private final Set<String> explicitKeys = new LinkedHashSet<>();

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
        settings.fontSize = clamp(fontSizeOf(properties.getProperty(KEY_FONT_SIZE)));
        if (properties.containsKey(KEY_UPDATE_CHANNEL)) {
            String raw = properties.getProperty(KEY_UPDATE_CHANNEL);
            ReleaseChannel channel = ReleaseChannel.of(raw);
            // `ReleaseChannel.of` 对不认识的值返回**默认通道**而不是 null，所以这里必须回头比一次 key：
            // 否则写坏的值会被当成「用户设过」，把默认值冻在磁盘上（将来改代码默认值就无效了）。
            if (raw != null && raw.strip().toLowerCase(java.util.Locale.ROOT).equals(channel.key())) {
                settings.updateChannel = channel;
                settings.explicitKeys.add(KEY_UPDATE_CHANNEL);
            } else {
                LOG.warn("设置里的升级通道不认识，按默认处理：{}", raw);
            }
        }
        // 与通道同一条口径：**只有值真的合法才算「用户设过」**。写坏的值若被当成显式设置，
        // 将来改默认值时这条安装就不会跟着走 —— 那正是「默认值改了也没生效」这类怪事。
        if (properties.containsKey(KEY_UPDATE_CHECK_ON_STARTUP)) {
            Boolean parsed = parseBoolean(properties.getProperty(KEY_UPDATE_CHECK_ON_STARTUP));
            if (parsed != null) {
                settings.checkOnStartup = parsed;
                settings.explicitKeys.add(KEY_UPDATE_CHECK_ON_STARTUP);
            }
        }
        if (properties.containsKey(KEY_UPDATE_ALPHA_ACK)) {
            Boolean parsed = parseBoolean(properties.getProperty(KEY_UPDATE_ALPHA_ACK));
            if (parsed != null) {
                settings.alphaAcknowledged = parsed;
                settings.explicitKeys.add(KEY_UPDATE_ALPHA_ACK);
            }
        }
        if (properties.containsKey(KEY_UPDATE_BACKUP_KEEP)) {
            Integer parsed = parse(properties.getProperty(KEY_UPDATE_BACKUP_KEEP));
            if (parsed != null) {
                settings.backupKeep = clampBackupKeep(parsed);
                settings.explicitKeys.add(KEY_UPDATE_BACKUP_KEEP);
            }
        }
        if (properties.containsKey(KEY_UPDATE_LAST_CHECK_AT)) {
            settings.lastCheckAt = properties.getProperty(KEY_UPDATE_LAST_CHECK_AT);
            settings.explicitKeys.add(KEY_UPDATE_LAST_CHECK_AT);
        }
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
        // 只写用户显式设过的升级相关键：见 explicitKeys 的说明（默认值改动要能惠及没设过的人）
        if (explicitKeys.contains(KEY_UPDATE_CHANNEL)) {
            properties.setProperty(KEY_UPDATE_CHANNEL, updateChannel.key());
        }
        if (explicitKeys.contains(KEY_UPDATE_CHECK_ON_STARTUP)) {
            properties.setProperty(KEY_UPDATE_CHECK_ON_STARTUP, String.valueOf(checkOnStartup));
        }
        if (explicitKeys.contains(KEY_UPDATE_ALPHA_ACK)) {
            properties.setProperty(KEY_UPDATE_ALPHA_ACK, String.valueOf(alphaAcknowledged));
        }
        if (explicitKeys.contains(KEY_UPDATE_BACKUP_KEEP)) {
            properties.setProperty(KEY_UPDATE_BACKUP_KEEP, String.valueOf(backupKeep));
        }
        if (explicitKeys.contains(KEY_UPDATE_LAST_CHECK_AT) && lastCheckAt != null) {
            properties.setProperty(KEY_UPDATE_LAST_CHECK_AT, lastCheckAt);
        }
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
     * 升级通道。
     *
     * @return 通道
     */
    public ReleaseChannel updateChannel() {
        return updateChannel;
    }

    /**
     * 设置升级通道（显式设过 ⇒ 之后会被写盘）。
     *
     * @param value 通道
     */
    public void updateChannel(ReleaseChannel value) {
        ReleaseChannel next = value == null ? DEFAULT_UPDATE_CHANNEL : value;
        if (next == updateChannel && !explicitKeys.contains(KEY_UPDATE_CHANNEL)) {
            // 设成「当前有效值」不算用户显式选择：值没变，没什么可记的。
            // 这条很要紧 —— 面板渲染初始选中、或用户点的就是默认通道时必须保持「没选过」，
            // 否则这个安装就被冻在当前默认值上，将来改默认值它不会再跟随（issue #252）。
            return;
        }
        this.updateChannel = next;
        explicitKeys.add(KEY_UPDATE_CHANNEL);
    }

    /**
     * 该安装是否显式设过升级通道（界面据此区分「默认」与「用户选的」）。
     *
     * @return 显式设过返回 {@code true}
     */
    public boolean updateChannelExplicit() {
        return explicitKeys.contains(KEY_UPDATE_CHANNEL);
    }

    /**
     * 启动时是否检查更新（只检查，不自动下载）。
     *
     * @return 默认 {@code false}
     */
    public boolean checkOnStartup() {
        return checkOnStartup;
    }

    /**
     * 设置「启动时检查更新」。
     *
     * @param value 取值
     */
    public void checkOnStartup(boolean value) {
        this.checkOnStartup = value;
        explicitKeys.add(KEY_UPDATE_CHECK_ON_STARTUP);
    }

    /**
     * 是否已确认过「Alpha 是非交付物」。
     *
     * @return 已确认返回 {@code true}
     */
    public boolean alphaAcknowledged() {
        return alphaAcknowledged;
    }

    /**
     * 记住「已确认 Alpha 是非交付物」。
     *
     * @param value 取值
     */
    public void alphaAcknowledged(boolean value) {
        this.alphaAcknowledged = value;
        explicitKeys.add(KEY_UPDATE_ALPHA_ACK);
    }

    /**
     * 备份保留份数。
     *
     * @return 份数
     */
    public int backupKeep() {
        return backupKeep;
    }

    /**
     * 设置备份保留份数（自动夹到 {@value #MIN_BACKUP_KEEP}~{@value #MAX_BACKUP_KEEP}）。
     *
     * @param value 份数
     */
    public void backupKeep(int value) {
        this.backupKeep = clampBackupKeep(value);
        explicitKeys.add(KEY_UPDATE_BACKUP_KEEP);
    }

    /**
     * 上次检查时间（ISO-8601；仅展示用）。
     *
     * @return 时间文本，可为 {@code null}
     */
    public String lastCheckAt() {
        return lastCheckAt;
    }

    /**
     * 记录上次检查时间。
     *
     * @param value ISO-8601 文本
     */
    public void lastCheckAt(String value) {
        this.lastCheckAt = value;
        explicitKeys.add(KEY_UPDATE_LAST_CHECK_AT);
    }

    /**
     * 备份份数夹取。
     *
     * @param value 份数
     * @return 夹取后的份数
     */
    public static int clampBackupKeep(int value) {
        return Math.max(MIN_BACKUP_KEEP, Math.min(MAX_BACKUP_KEEP, value));
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

    /** 解析整数；空或非法返回 {@code null}（调用方据此区分「没写」与「写坏了」）。 */
    private static Integer parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 解析布尔；空或非法返回 {@code null}（同上）。 */
    private static Boolean parseBoolean(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.strip().toLowerCase(java.util.Locale.ROOT);
        if ("true".equals(value)) {
            return Boolean.TRUE;
        }
        if ("false".equals(value)) {
            return Boolean.FALSE;
        }
        return null;
    }

    /** 字号：缺键或写坏都用默认（字号是界面偏好，不需要「是否显式设过」这层区分）。 */
    private static int fontSizeOf(String text) {
        Integer value = parse(text);
        return value == null ? DEFAULT_FONT_SIZE : value;
    }
}
