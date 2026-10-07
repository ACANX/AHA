package com.acanx.module.aha.cli.tty;

/**
 * 状态行载体：在终端底部保留一行并持续刷新。
 *
 * <p>与「往输出流里打一行」本质不同：这里的状态是<b>可原地重绘</b>的，因此能显示
 * 持续变化的内容（转圈、耗时、累计用量）。实现依赖终端滚动区域
 * （{@code change_scroll_region}）与光标定位，终端不支持时退化为 {@link #NONE}。</p>
 *
 * <p>调用方约定：{@link #hide()} 与 {@link #update(String)} 可交替调用，
 * 由实现负责让出/取回底部区域。</p>
 *
 * @since 0.1.0
 */
public interface StatusSurface {

    /** 空实现：非 TTY、终端不支持滚动区域、或渲染器无状态行时使用。 */
    StatusSurface NONE = new StatusSurface() {

        @Override
        public boolean available() {
            return false;
        }

        @Override
        public void update(java.util.List<String> lines) {
        }

        @Override
        public void hide() {
        }

        @Override
        public void close() {
        }

    };

    /**
     * 是否真的会显示。
     *
     * @return 终端支持时为 {@code true}
     */
    boolean available();

    /**
     * 更新底部保留区的内容。
     *
     * <p>第一行是输入框的下边框（由 {@code StatusLine} 生成，带颜色），其余是状态文本。
     * 行数即占用高度——所以实现不能自行增删行。</p>
     *
     * <p>文本可含 ANSI 颜色序列；宽度超出时由实现负责截断或换行。</p>
     *
     * @param lines 各行文本
     */
    void update(java.util.List<String> lines);

    /**
     * 隐藏状态行，把底部区域让回去。
     */
    void hide();

    /**
     * 关闭并恢复终端滚动区域。
     */
    void close();
}
