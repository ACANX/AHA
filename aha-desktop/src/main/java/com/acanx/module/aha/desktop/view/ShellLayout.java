package com.acanx.module.aha.desktop.view;

/**
 * 三栏骨架的尺寸与可见性规则。
 *
 * <p>数值口径来自 {@code GUIDesign.md} 第 2 节的形态总览表——把它抽成常量而不是散落在
 * 各处的字面量，是为了「设计文档改了，代码有唯一一处要对」。</p>
 *
 * @since 0.2.0
 */
public final class ShellLayout {

    /** 左栏宽度（px）：导航与列表。 */
    public static final double LEFT_WIDTH = 372;

    /** 右栏宽度（px）：本轮上下文。 */
    public static final double RIGHT_WIDTH = 280;

    /** 左栏拖拽后的最小宽度（px）：再窄就放不下会话标题与按钮。 */
    public static final double LEFT_MIN_WIDTH = 160;

    /** 左栏拖拽后的最大宽度（px）：再宽会把中栏挤得只剩一条缝。 */
    public static final double LEFT_MAX_WIDTH = 480;

    /** 消息气泡的最大宽度（px）：过宽就不再像聊天窗口。 */
    public static final double BUBBLE_MAX_WIDTH = 720;

    /** 左栏与中栏之间拖拽条的宽度（px）。 */
    public static final double SPLITTER_WIDTH = 5;

    /**
     * 把左栏宽度收进允许范围。
     *
     * <p>抽成纯函数：拖拽边界（太窄 / 太宽）在没有图形环境的机器上也能单测。</p>
     *
     * @param width 目标宽度（px）
     * @return 收进 {@link #LEFT_MIN_WIDTH}–{@link #LEFT_MAX_WIDTH} 后的宽度
     */
    public static double clampLeftWidth(double width) {
        return Math.max(LEFT_MIN_WIDTH, Math.min(LEFT_MAX_WIDTH, width));
    }

    /** 底部状态栏高度（px）。 */
    public static final double STATUS_BAR_HEIGHT = 24;

    /** 间距基数（px）：所有间距取 8 的倍数。 */
    public static final double GAP = 8;

    /** 窗口初始宽度（px）。 */
    public static final double WINDOW_WIDTH = 1280;

    /** 窗口初始高度（px）。 */
    public static final double WINDOW_HEIGHT = 800;

    /** 窗口最小宽度（px）：再小就放不下三栏。 */
    public static final double MIN_WIDTH = 900;

    /** 窗口最小高度（px）：再小底栏与输入区会互相挤。 */
    public static final double MIN_HEIGHT = 560;

    private ShellLayout() {
    }

    /**
     * 折叠手柄（常驻窄条）的宽度（px）。
     *
     * <p>折叠后**不把整列藏没**：留这条窄条与其中的按钮，用户随时能用鼠标把栏展回来。
     * 只留快捷键是不够的——鼠标用户会直接卡死在这条规则上。</p>
     */
    public static final double TOGGLE_STRIP_WIDTH = 28;

    /**
     * 左栏内容区在给定折叠状态下的宽度。
     *
     * @param expanded 是否展开
     * @return 展开为 {@link #LEFT_WIDTH}，折叠为 {@code 0}
     */
    public static double leftContentWidth(boolean expanded) {
        return expanded ? LEFT_WIDTH : 0;
    }

    /**
     * 右栏内容区在给定折叠状态下的宽度。
     *
     * @param expanded 是否展开
     * @return 展开为 {@link #RIGHT_WIDTH}，折叠为 {@code 0}
     */
    public static double rightContentWidth(boolean expanded) {
        return expanded ? RIGHT_WIDTH : 0;
    }

    /**
     * 右栏是否可见。
     *
     * <p>设计规则：右栏是「当前这一轮发生了什么」的镜子，**空会话时隐藏**，
     * 中栏因此变宽。用户用 {@code Ctrl+J} 手动展开时不受此规则约束
     * （那是显式操作，见 {@code DesktopShell}）。</p>
     *
     * @param hasRound 当前会话是否已有本轮数据（用量、工具调用、记忆命中）
     * @return 有数据返回 {@code true}
     */
    public static boolean rightPaneVisibleByDefault(boolean hasRound) {
        return hasRound;
    }
}
