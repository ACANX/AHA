package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.common.tool.ToolKind;

/**
 * 语义配色（十六进制），支持暗色 / 亮色两套。
 *
 * <p>{@code GUIDesign.md} 第 3.1 节：CLI（256 色）与 GUI（十六进制）使用**同一套语义**，
 * 用户在两端看到同一种含义。</p>
 *
 * <p><strong>为什么是可变静态字段</strong>：样式是内联字符串，切换主题时必须把已经建好的节点
 * 重刷一遍。如果颜色是 {@code final}，就只能走「每个控件一个取色方法」的路子——
 * 那意味着几十处调用点各写一次，而且很容易漏掉一处，于是「文档说一套、界面做一套」。
 * 这里保留字段本身，只允许 {@link #setTheme(Theme)} 一处改动它们；切换后由
 * {@code DesktopShell} 重刷所有登记过的节点（见那里的 {@code themed}）。
 * 换句话说：**颜色的唯一来源仍是这张表**，只是它可以在主题之间切换。</p>
 *
 * <p>颜色值有测试钉住，改设计必须同时改测试。</p>
 *
 * @since 0.2.0
 */
public final class Palette {

    /** 读取：查看文件、列目录。 */
    public static String READ = "#87AFD7";

    /** 写入：修改 / 新建文件。 */
    public static String WRITE = "#D7AF5F";

    /** 执行：运行命令 / 脚本。 */
    public static String EXEC = "#FF875F";

    /** 网络：发起请求。 */
    public static String NETWORK = "#5FD7D7";

    /** 其它：未识别工具。 */
    public static String OTHER = "#BCBCBC";

    /** 成功：完成。 */
    public static String SUCCESS = "#7FD37F";

    /** 失败：错误、拒绝。 */
    public static String FAILURE = "#FF5F5F";

    /** 区块底：工具卡片底。 */
    public static String BLOCK_BACKGROUND = "#303030";

    /** 正文前景。 */
    public static String FOREGROUND = "#E4E4E4";

    /** 弱化的元信息色。 */
    public static String MUTED = "#9A9A9A";

    /** 输入框边框（与 CLI 的高亮紫一致）。 */
    public static String FOCUS_BORDER = "#AF87FF";

    /** 分隔线。 */
    public static String BORDER = "#3A3A3A";

    /** 主题底色（{@code -fx-base}）。 */
    public static String BASE = "#1E1E1E";

    /** 控件内部底色（{@code -fx-control-inner-background}）。 */
    public static String CONTROL_INNER = "#252526";

    /** 当前主题（{@link Theme#SYSTEM} 会被解析成实际生效的那一个）。 */
    private static Theme current = Theme.DARK;

    private Palette() {
    }

    /**
     * 当前生效的主题（已解析，不会是 {@link Theme#SYSTEM}）。
     *
     * <p>名字刻意不叫 {@code theme()}：那个名字留给**样式串**（已有几十处调用），
     * 两者重名会让「取主题」与「取样式」在各调用点读起来一样，迟早出错。</p>
     *
     * @return 主题
     */
    public static Theme current() {
        return current;
    }

    /**
     * 主题样式串。
     *
     * <p>必须应用到**每一个顶层容器**，不只是主窗口：{@code Dialog} / {@code Alert} 有自己的
     * 场景根，不会继承主窗口的样式。第一版只给主窗口套了主题，于是对话框是 JavaFX 默认白底，
     * 而列表文字用的是 {@link #FOREGROUND}（近白）——白底白字，看上去发灰、费眼。</p>
     *
     * <p><strong>刻意不缓存</strong>：早先把结果存在一个静态字段里，原生镜像下出现过
     * 「样式串仍是初始主题、而 {@link #FOREGROUND} 等字段已按新主题更新」的不同步——
     * 表现为主窗口底色已换、控件文字却是旧主题的值。改成每次按当前字段现算，
     * 代价只是拼几个字符串，换来的是「样式串与色表永远一致」（issue #49）。</p>
     *
     * @return 内联样式
     */
    public static String theme() {
        return buildThemeStyle();
    }

    /**
     * 切换主题。
     *
     * <p>{@link Theme#SYSTEM} 会按系统偏好解析成暗色或亮色（见 {@link SystemTheme}）。</p>
     *
     * @param theme 主题
     * @return 实际生效的主题
     */
    public static Theme setTheme(Theme theme) {
        Theme requested = theme == null ? Theme.DARK : theme;
        Theme resolved = requested == Theme.SYSTEM
                ? requested.resolve(SystemTheme.prefersDark())
                : requested.resolve(false);
        current = resolved;
        if (resolved == Theme.LIGHT) {
            applyLight();
        } else {
            applyDark();
        }
        return resolved;
    }

    /**
     * 按工具类别取语义色。
     *
     * <p>类别判定（哪个工具算读取 / 写入 / 执行 / 网络）来自 {@code aha-common} 的
     * {@link ToolKind}，CLI 与桌面端共用同一套；这里只做「类别 → 十六进制」的投影。</p>
     *
     * @param kind 工具类别
     * @return 十六进制颜色
     */
    public static String forToolKind(ToolKind kind) {
        return switch (kind) {
            case READ -> READ;
            case WRITE -> WRITE;
            case EXEC -> EXEC;
            case NETWORK -> NETWORK;
            default -> OTHER;
        };
    }

    private static void applyDark() {
        READ = "#87AFD7";
        WRITE = "#D7AF5F";
        EXEC = "#FF875F";
        NETWORK = "#5FD7D7";
        OTHER = "#BCBCBC";
        SUCCESS = "#7FD37F";
        FAILURE = "#FF5F5F";
        BLOCK_BACKGROUND = "#303030";
        FOREGROUND = "#E4E4E4";
        MUTED = "#9A9A9A";
        FOCUS_BORDER = "#AF87FF";
        BORDER = "#3A3A3A";
        BASE = "#1E1E1E";
        CONTROL_INNER = "#252526";
    }

    /**
     * 亮色主题。
     *
     * <p>语义色不是把暗色版「调亮」，而是重新挑：亮底上 #FF5F5F 这类高饱和色的对比度不足，
     * 因此失败 / 成功 / 强调色都取了更深的版本，保证在白底上仍可读。</p>
     */
    private static void applyLight() {
        READ = "#2A5D9F";
        WRITE = "#8A5A00";
        EXEC = "#B4441A";
        NETWORK = "#0F6E6E";
        OTHER = "#555555";
        SUCCESS = "#1E7A34";
        FAILURE = "#C0392B";
        BLOCK_BACKGROUND = "#F0F0F0";
        FOREGROUND = "#1F1F1F";
        MUTED = "#666666";
        FOCUS_BORDER = "#6A3FD0";
        BORDER = "#C8C8C8";
        BASE = "#F4F4F4";
        CONTROL_INNER = "#FFFFFF";
    }

    /**
     * 对话框（{@code Dialog} / {@code Alert}）场景根的样式。
     *
     * <p>比 {@link #theme()} 多一条**显式背景**：{@code DialogPane} 的底色在 modena 里来自规则
     * {@code .dialog-pane { -fx-background-color: -fx-background; }}——那是一次 looked-up color
     * 查表。原生镜像下出现过该查表未生效、对话框露出深色底的现象（issue #49），所以这里不再
     * 依赖规则，直接把底色写进内联样式。</p>
     *
     * @return 内联样式
     */
    public static String dialogTheme() {
        return theme() + "-fx-background-color: " + BASE + ";";
    }

    private static String buildThemeStyle() {
        return "-fx-base: " + BASE + ";"
                + "-fx-background: " + BASE + ";"
                + "-fx-control-inner-background: " + CONTROL_INNER + ";"
                + "-fx-control-inner-background-alt: " + CONTROL_INNER + ";"
                // 以下几条把文字色**显式钉死**，不再依赖 modena 的
                // ladder(-fx-base, 亮 45% / 暗 46%) 推导：原生镜像下出现过
                // 「底色已换、文字仍按旧底色推导」的混合状态（issue #49）。
                + "-fx-text-base-color: " + FOREGROUND + ";"
                + "-fx-text-background-color: " + FOREGROUND + ";"
                + "-fx-focused-text-base-color: " + FOREGROUND + ";"
                + "-fx-mark-color: " + FOREGROUND + ";"
                + "-fx-focused-mark-color: " + FOREGROUND + ";"
                + "-fx-selection-bar: " + FOCUS_BORDER + ";"
                + "-fx-selection-bar-non-focused: " + BORDER + ";"
                + "-fx-selection-bar-text: " + FOREGROUND + ";"
                + "-fx-accent: " + FOCUS_BORDER + ";";
    }
}
