package com.acanx.module.aha.cli.render;

import java.util.List;

/**
 * 一块可以贴在启动横幅右侧的标志。
 *
 * <p>只描述「多宽、多少行、每行什么内容」，不关心它是怎么画出来的——纯 ASCII 线框图与
 * 像素图因此可以走同一条并排组装逻辑（{@link StartupBanner}）。行内容<b>必须已经着色</b>：
 * 组装时只做拼接，不再测量（SGR 序列会被当成普通字符，测出来的宽度是错的）。</p>
 *
 * @param width 显示宽度（列）
 * @param lines 行内容（已着色）
 * @since 0.1.0
 */
public record LogoArt(int width, List<String> lines) {

    /**
     * 行数。
     *
     * @return 行数
     */
    public int height() {
        return lines.size();
    }
}
