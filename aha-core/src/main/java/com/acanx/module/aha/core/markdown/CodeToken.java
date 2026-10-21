package com.acanx.module.aha.core.markdown;

/**
 * 代码高亮的一个记号（issue #420 / tracking #408 S2.2）。
 *
 * <p>只描述「类型 + 文本」，不带任何颜色：CLI 拿 ANSI 颜色，桌面端拿 JavaFX 样式，
 * 两边共用同一份词法结果，避免各写一套后高亮口径分叉。</p>
 *
 * @param kind 记号类型
 * @param text 原文片段（拼接后必须与输入行完全一致）
 * @since 0.2.0
 */
public record CodeToken(Kind kind, String text) {

    /**
     * 记号类型。
     */
    public enum Kind {
        /** 普通文本（空白、标点、非关键字标识符等）。 */
        PLAIN,
        /** 关键字。 */
        KEYWORD,
        /** 字符串字面量（含引号）。 */
        STRING,
        /** 数字字面量。 */
        NUMBER,
        /** 行注释（到行尾）。 */
        COMMENT
    }
}
