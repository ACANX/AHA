package com.acanx.module.aha.desktop.markdown;

/**
 * 长代码块的折叠状态（issue #422 / tracking #408 S2.4）。
 *
 * <p>纯状态机，不含 JavaFX：阈值边界（29 / 30 / 31 行）是最容易写错的地方，
 * 抽出来就能在无头环境用单测钉死。</p>
 *
 * <p>默认阈值 30 行与 CLI 工具输出的折叠口径一致：≤30 行不折叠（折叠反而多一次点击），
 * 超过才折叠并显示「已折叠 N 行」。</p>
 *
 * @since 0.2.0
 */
public final class FenceFold {

    /** 默认折叠阈值（行）。 */
    public static final int DEFAULT_THRESHOLD = 30;

    private final int totalLines;

    private final int threshold;

    private boolean expanded;

    /**
     * @param totalLines 代码总行数
     */
    public FenceFold(int totalLines) {
        this(totalLines, DEFAULT_THRESHOLD);
    }

    /**
     * @param totalLines 代码总行数
     * @param threshold  折叠阈值
     */
    public FenceFold(int totalLines, int threshold) {
        if (totalLines < 0) {
            throw new IllegalArgumentException("行数不能为负：" + totalLines);
        }
        if (threshold <= 0) {
            throw new IllegalArgumentException("阈值必须为正：" + threshold);
        }
        this.totalLines = totalLines;
        this.threshold = threshold;
        this.expanded = totalLines <= threshold;
    }

    /**
     * 是否需要折叠控件。
     *
     * @return 超过阈值返回 {@code true}
     */
    public boolean collapsible() {
        return totalLines > threshold;
    }

    /**
     * 当前是否展开。
     *
     * @return 展开返回 {@code true}
     */
    public boolean isExpanded() {
        return expanded;
    }

    /**
     * 当前应显示的末行（不含）。
     *
     * @return 可见行数
     */
    public int visibleLines() {
        return expanded ? totalLines : threshold;
    }

    /**
     * 被折叠的行数。
     *
     * @return 隐藏行数
     */
    public int hiddenLines() {
        return totalLines - visibleLines();
    }

    /**
     * 切换展开 / 收起（不可折叠时无副作用）。
     *
     * @return 切换后的展开状态
     */
    public boolean toggle() {
        if (collapsible()) {
            expanded = !expanded;
        }
        return expanded;
    }

    /**
     * 总行数。
     *
     * @return 行数
     */
    public int totalLines() {
        return totalLines;
    }
}
