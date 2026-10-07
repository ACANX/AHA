package com.acanx.module.aha.desktop.view;

/**
 * 语义配色（十六进制）。
 *
 * <p>{@code GUIDesign.md} 第 3.1 节：CLI（256 色）与 GUI（十六进制）使用**同一套语义**，
 * 用户在两端看到同一种含义。这里只放 GUI 侧的字面量；语义本身（哪个工具属于哪类）
 * 与 CLI 共用同一套判定，落在后续的工具卡片实现里。</p>
 *
 * <p>颜色值有测试钉住，改设计必须同时改测试——否则「文档说一套、界面做一套」会悄悄发生。</p>
 *
 * @since 0.2.0
 */
public final class Palette {

    /** 读取：查看文件、列目录。 */
    public static final String READ = "#87AFD7";

    /** 写入：修改 / 新建文件。 */
    public static final String WRITE = "#D7AF5F";

    /** 执行：运行命令 / 脚本。 */
    public static final String EXEC = "#FF875F";

    /** 网络：发起请求。 */
    public static final String NETWORK = "#5FD7D7";

    /** 其它：未识别工具。 */
    public static final String OTHER = "#BCBCBC";

    /** 成功：完成。 */
    public static final String SUCCESS = "#7FD37F";

    /** 失败：错误、拒绝。 */
    public static final String FAILURE = "#FF5F5F";

    /** 区块底：工具卡片底。 */
    public static final String BLOCK_BACKGROUND = "#303030";

    /** 正文前景（暗色优先主题）。 */
    public static final String FOREGROUND = "#E4E4E4";

    /** 弱化的元信息色。 */
    public static final String MUTED = "#9A9A9A";

    /** 输入框边框（与 CLI 的高亮紫一致）。 */
    public static final String FOCUS_BORDER = "#AF87FF";

    /**
     * 暗色主题（JavaFX 的「被查色」，子节点自动继承）。
     *
     * <p>必须应用到**每一个顶层容器**，不只是主窗口：{@code Dialog} / {@code Alert} 有自己的
     * 场景根，不会继承主窗口的样式。第一版只给主窗口套了主题，于是对话框是 JavaFX 默认白底，
     * 而列表文字用的是 {@link #FOREGROUND}（近白）——白底白字，看上去发灰、费眼。
     * 那个 bug 不是配色选择问题，是**主题没铺到对话框**。</p>
     *
     * @return 内联样式
     */
    public static String theme() {
        return "-fx-base: #1E1E1E;"
                + "-fx-background: #1E1E1E;"
                + "-fx-control-inner-background: #252526;"
                + "-fx-text-background-color: " + FOREGROUND + ";"
                + "-fx-accent: " + FOCUS_BORDER + ";";
    }

    /**
     * 按工具类别取语义色。
     *
     * <p>类别判定（哪个工具算读取 / 写入 / 执行 / 网络）来自 {@code aha-common} 的
     * {@code ToolKind}，CLI 与桌面端共用同一套；这里只做「类别 → 十六进制」的投影。</p>
     *
     * @param kind 工具类别
     * @return 十六进制颜色
     */
    public static String forToolKind(com.acanx.module.aha.common.tool.ToolKind kind) {
        return switch (kind) {
            case READ -> READ;
            case WRITE -> WRITE;
            case EXEC -> EXEC;
            case NETWORK -> NETWORK;
            default -> OTHER;
        };
    }

    private Palette() {
    }
}
