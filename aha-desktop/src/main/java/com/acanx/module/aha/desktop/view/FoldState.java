package com.acanx.module.aha.desktop.view;

/**
 * 一栏的折叠状态。
 *
 * <p>刻意**不引用任何 JavaFX 类型**：折叠与展开是纯状态机，抽出来之后
 * 「折叠后能不能再展开」这种最该被钉住的行为，就能在**没有图形环境**的机器上
 * （CI、无显示的 WSL）用单元测试验证，而不是只能靠肉眼开窗看。</p>
 *
 * <p>三处入口共用同一个实例，因此不存在「菜单能展开、按钮不能」的分叉：
 * 菜单项、快捷键、栏边窄条按钮最终都调用 {@link #toggle()}。</p>
 *
 * @since 0.2.0
 */
public final class FoldState {

    /** 展开时显示的字符（表示「点它就把这一栏折起来」）。 */
    private static final String GLYPH_COLLAPSE = "‹";

    /** 折叠时显示的字符（表示「点它就把这一栏展开」）。 */
    private static final String GLYPH_EXPAND = "›";

    private final String name;

    private final double expandedWidth;

    private boolean expanded;

    /**
     * @param name           栏名（用于提示文案，如「左栏」）
     * @param expandedWidth  展开时的内容宽度（px）
     * @param expanded       初始是否展开
     */
    public FoldState(String name, double expandedWidth, boolean expanded) {
        this.name = name;
        this.expandedWidth = expandedWidth;
        this.expanded = expanded;
    }

    /**
     * 折叠 / 展开（左栏的菜单项、{@code Ctrl+B}、窄条按钮都走这里）。
     *
     * @return 切换后的展开状态
     */
    public boolean toggle() {
        expanded = !expanded;
        return expanded;
    }

    /**
     * 是否展开。
     *
     * @return 展开返回 {@code true}
     */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * 展开时的内容宽度。
     *
     * @return 宽度（px）
     */
    public double expandedWidth() {
        return expandedWidth;
    }

    /**
     * 内容区当前宽度：折叠时为 {@code 0}。
     *
     * @return 宽度（px）
     */
    public double contentWidth() {
        return expanded ? expandedWidth : 0;
    }

    /**
     * 内容区是否可见。
     *
     * @return 展开返回 {@code true}
     */
    public boolean isContentVisible() {
        return expanded;
    }

    /**
     * 内容区是否参与布局（折叠后不应继续占位）。
     *
     * @return 展开返回 {@code true}
     */
    public boolean isContentManaged() {
        return expanded;
    }

    /**
     * 窄条按钮的字符。
     *
     * @return 展开时 {@code ‹}（点了收起），折叠时 {@code ›}（点了展开）
     */
    public String glyph() {
        return expanded ? GLYPH_COLLAPSE : GLYPH_EXPAND;
    }

    /**
     * 窄条按钮的提示文案。
     *
     * <p>光有一个箭头字符含义不明，提示必须写明动作——折叠后尤其重要：
     * 用户要能一眼看出「这里可以把刚才收起来的栏拉回来」。</p>
     *
     * @return 如「折叠左栏」/「展开左栏」
     */
    public String tooltip() {
        return (expanded ? "折叠" : "展开") + name;
    }
}
