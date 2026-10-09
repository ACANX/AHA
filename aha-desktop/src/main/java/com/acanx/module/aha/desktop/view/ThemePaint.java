package com.acanx.module.aha.desktop.view;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 直接用**属性 API**上色，绕开原生镜像下不可靠的 CSS 传导。
 *
 * <p><strong>为什么需要它</strong>：2026-10-09 对真机截图做像素测量，测到
 * 「左栏导航按钮是暗底近黑字（{@code #000000}）、输入框是纯白底」——而色表里
 * {@code FOREGROUND} 是 {@code #E4E4E4}、{@code CONTROL_INNER} 是 {@code #252526}。
 * 也就是说**色表值是对的，颜色却回落到 modena 默认值**。</p>
 *
 * <p>原因是 JavaFX 的 CSS 继承与 looked-up color 传导在原生镜像里不可靠：
 * {@code -fx-text-fill} 设在 {@code Button} 上，并没有落到它内部真正显示文字的节点；
 * {@code -fx-control-inner-background} 也没传到 {@code TextArea} 的 {@code .content}。
 * 而 {@link Labeled#setTextFill} / {@link Region#setBackground} 是属性 API，不经过 CSS 引擎，
 * 因此不受影响（issue #49）。</p>
 *
 * <p>两边都设，等于「CSS 生效时以 CSS 为准，CSS 失效时由 API 兜底」。</p>
 *
 * @since 0.2.0
 */
final class ThemePaint {

    private static final Logger LOG = LoggerFactory.getLogger(ThemePaint.class);

    private ThemePaint() {
    }

    /**
     * 设样式串，并把其中的文字色 / 底色用属性 API 再设一遍。
     *
     * @param node 节点
     * @param css  样式串
     */
    static void themed(Node node, String css) {
        node.setStyle(css);
        apply(node, css);
    }

    /**
     * 按样式串里的 {@code -fx-text-fill} / {@code -fx-background-color} 同步属性。
     *
     * @param node  节点
     * @param style 样式串
     */
    static void apply(Node node, String style) {
        if (node instanceof Labeled labeled) {
            String textFill = value(style, "-fx-text-fill");
            if (textFill != null) {
                color(textFill, c -> labeled.setTextFill(c), "文字色");
            }
        }
        if (node instanceof Region region) {
            String background = value(style, "-fx-background-color");
            if (background != null) {
                if (background.startsWith("transparent")) {
                    // 刻意透明也要落到属性上，否则会留着上一次设过的背景
                    region.setBackground(Background.EMPTY);
                } else {
                    color(background, c -> region.setBackground(
                            new Background(new BackgroundFill(c, CornerRadii.EMPTY, Insets.EMPTY))), "背景色");
                }
            }
        }
    }

    /**
     * 从样式串里取某个属性的值（取第一个匹配的声明）。
     *
     * @param style    样式串
     * @param property 属性名
     * @return 值；没有该属性时返回 {@code null}
     */
    static String value(String style, String property) {
        if (style == null) {
            return null;
        }
        for (String declaration : style.split(";")) {
            int colon = declaration.indexOf(':');
            if (colon > 0 && declaration.substring(0, colon).trim().equals(property)) {
                return declaration.substring(colon + 1).trim();
            }
        }
        return null;
    }

    private static void color(String hex, java.util.function.Consumer<Color> consumer, String what) {
        try {
            consumer.accept(Color.web(hex));
        } catch (IllegalArgumentException | NullPointerException e) {
            // 只可能是样式串写错；不该让一次上色失败影响整屏
            LOG.warn("无法解析{}（{}）：{}", what, hex, e.toString());
        }
    }
}
