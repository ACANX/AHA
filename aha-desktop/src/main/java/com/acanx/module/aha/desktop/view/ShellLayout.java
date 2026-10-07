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
    public static final double LEFT_WIDTH = 220;

    /** 右栏宽度（px）：本轮上下文。 */
    public static final double RIGHT_WIDTH = 280;

    /** 底部状态栏高度（px）。 */
    public static final double STATUS_BAR_HEIGHT = 24;

    /** 间距基数（px）：所有间距取 8 的倍数。 */
    public static final double GAP = 8;

    /** 窗口初始宽度（px）。 */
    public static final double WINDOW_WIDTH = 1280;

    /** 窗口初始高度（px）。 */
    public static final double WINDOW_HEIGHT = 800;

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
