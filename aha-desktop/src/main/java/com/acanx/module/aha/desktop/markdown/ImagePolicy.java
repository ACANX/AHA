package com.acanx.module.aha.desktop.markdown;

import java.util.Locale;

/**
 * 图片地址白名单（issue #423 / tracking #408 S2.5）。
 *
 * <p>只允许 {@code file:} 与 {@code https:}：前者是本机文件，后者有传输加密。
 * {@code http:} 会让图片在明文信道里被替换，{@code data:} 可以把任意字节塞进「图片」，
 * 一律拒绝。没有协议的相对地址同样拒绝——它会被当成当前工作目录下的文件，
 * 属于「悄悄读本机文件」的那一类。</p>
 *
 * <p>纯逻辑，不引用 JavaFX：白名单判定值得单测，而渲染器只在用户点击后才调用它。</p>
 *
 * @since 0.2.0
 */
public final class ImagePolicy {

    private ImagePolicy() {
    }

    /**
     * 地址是否允许加载。
     *
     * @param url 图片地址
     * @return 允许返回 {@code true}
     */
    public static boolean allowed(String url) {
        String scheme = scheme(url);
        return "file".equals(scheme) || "https".equals(scheme);
    }

    /**
     * 拒绝原因（用于占位文案）。
     *
     * @param url 图片地址
     * @return 允许时返回空串
     */
    public static String rejectReason(String url) {
        if (url == null || url.isBlank()) {
            return "地址为空";
        }
        String scheme = scheme(url);
        if (scheme.isEmpty()) {
            return "缺少协议，只允许 file: 与 https:";
        }
        return "只允许 file: 与 https:（当前 " + scheme + ":）";
    }

    private static String scheme(String url) {
        if (url == null) {
            return "";
        }
        int colon = url.indexOf(':');
        if (colon <= 0) {
            return "";
        }
        return url.substring(0, colon).toLowerCase(Locale.ROOT);
    }
}
