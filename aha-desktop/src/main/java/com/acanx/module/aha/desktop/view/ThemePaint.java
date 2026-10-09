package com.acanx.module.aha.desktop.view;

import java.net.URL;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
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
public final class ThemePaint {

    private static final Logger LOG = LoggerFactory.getLogger(ThemePaint.class);

    /**
     * 主题补丁样式表：**纯字面量**，用来盖掉 modena 里依赖 {@code derive()} /
     * {@code linear-gradient()} 以及自定义 color 查表的规则。
     *
     * <p><strong>为什么是「亮 / 暗两份」而不是「一份 + 变量」</strong>：早先试过一份表 + 在根节点
     * 内联样式里定义自定义 color，由表里的规则去查。而**原生镜像下这个查表不可靠**——
     * 声明里一旦查不到值，CSS 会**丢掉整条声明**，文字色于是回落到 modena 的默认黑：
     * 亮色下碰巧看不出来（亮色本就该是深色字），暗色下就成了一片黑字（issue #49 第四轮）。
     * 换成两份字面量、按主题整份装载，就不存在「查表」这一步（见两份 css 的头部说明）。</p>
     */
    private static final String DARK_SHEET =
            "/com/acanx/module/aha/desktop/view/aha-theme-dark.css";

    /** 亮色主题补丁样式表，与 {@link #DARK_SHEET} 一一对应。 */
    private static final String LIGHT_SHEET =
            "/com/acanx/module/aha/desktop/view/aha-theme-light.css";

    private ThemePaint() {
    }

    /**
     * 给对话框套主题（{@link Palette#dialogTheme()}）并装上补丁样式表。
     *
     * <p>{@code DialogPane} 的 {@code Scene} 要等窗口建好后才存在，所以这里挂一次监听补装。</p>
     *
     * @param pane 对话框面板
     */
    static void dialog(DialogPane pane) {
        String css = Palette.dialogTheme();
        pane.setStyle(css);
        apply(pane, css);
        install(pane.getScene());
        pane.sceneProperty().addListener((observable, oldScene, newScene) -> install(newScene));
    }

    /**
     * 把当前主题的补丁样式表挂到场景上（幂等，且会移除另一主题的那份）。
     *
     * <p><strong>切换主题时也要调用</strong>：补丁表是纯字面量，换主题就是整份替换，
     * 没有需要「重新绑定」的变量。调用点有两个——窗口建好时，以及 {@code applyTheme}。</p>
     *
     * @param scene 场景；{@code null} 时直接返回
     */
    public static void install(Scene scene) {
        if (scene == null) {
            return;
        }
        // Palette.current() 已经把 SYSTEM 解析成实际生效的那个
        boolean light = Palette.current() == Theme.LIGHT;
        String wanted = light ? LIGHT_SHEET : DARK_SHEET;
        String stale = light ? DARK_SHEET : LIGHT_SHEET;

        String external = external(wanted);
        if (external == null) {
            LOG.warn("主题补丁样式表缺失，控件颜色可能回落到 modena 默认值：{}", wanted);
            return;
        }
        String staleExternal = external(stale);
        if (staleExternal != null) {
            scene.getStylesheets().remove(staleExternal);
        }
        if (!scene.getStylesheets().contains(external)) {
            scene.getStylesheets().add(external);
        }
    }

    /**
     * 资源路径转 URL 字符串。
     *
     * @param path 资源路径
     * @return URL；资源不在 classpath 上时返回 {@code null}
     */
    private static String external(String path) {
        URL url = ThemePaint.class.getResource(path);
        return url == null ? null : url.toExternalForm();
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
