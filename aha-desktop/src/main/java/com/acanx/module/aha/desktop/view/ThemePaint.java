package com.acanx.module.aha.desktop.view;

import com.acanx.module.aha.desktop.fx.FxDispatcher;
import com.acanx.module.aha.desktop.fx.PlatformFxDispatcher;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Labeled;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
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
    private static final String DARK_SHEET = UiResources.THEME_DARK_CSS;

    /** 亮色主题补丁样式表，与 {@link #DARK_SHEET} 一一对应。 */
    private static final String LIGHT_SHEET = UiResources.THEME_LIGHT_CSS;

    /**
     * 换了主题要重刷的组件（对话框内容、面板、弹层等自己登记的）。
     *
     * <p><strong>为什么需要它</strong>：颜色是内联在样式串里的，换色表不会自动影响已建节点。
     * 主窗口有 {@code DesktopShell.restylers} 管着，但对话框 / 面板 / 弹层不在那棵树上——
     * 早先把它们漏了，于是切主题后「输入框还是切之前的底色、对话框里的小标题还是旧颜色」
     * （2026-10-14 实测反馈）。凡是用一次性字符串上色的地方，都在这里登记重刷。</p>
     */
    private static final List<Runnable> THEME_LISTENERS = new CopyOnWriteArrayList<>();

    /**
     * **皮肤内部基面**：输入类控件的可见底色其实由这些节点绘制，而不是控件本体。
     *
     * <p>modena 的规则是 {@code .text-area > .content { -fx-background-color: -fx-control-inner-background; }}，
     * 也就是说「输入框的底色」是 {@code .content} 画的；{@code ScrollPane} 同理走 {@code .viewport} /
     * {@code .corner}。</p>
     *
     * <p><strong>为什么要在代码里再刷一遍</strong>：{@link #themed(Node, String)} 只作用于**控件本体**
     * （{@code setStyle} + 属性 API），内部基面此前只依赖补丁样式表重算。于是换主题时出现
     * 「本体已换色、内部基面仍是旧色」的混色 —— 现象是**日志区与对话输入框的底色切换前后不变，
     * 重启却正常**（issue #153 复现，2026-10-15）。重启之所以正常：整棵界面按新色表重建，
     * 本体与内部基面同时拿到新值。</p>
     *
     * <p>这份清单必须与两份补丁样式表里的对应选择器一致，由 {@code bin/CheckTheme.py} 断言。</p>
     */
    private static final List<String> SKIN_SURFACE_SELECTORS = List.of(".content", ".viewport", ".corner");

    /**
     * UI 线程投递器。
     *
     * <p>本仓库有线程契约：**只有 {@code PlatformFxDispatcher} 可以直接调用 JavaFX 线程 API**
     * （{@code FxThreadContractTest} 静态扫描兜底），其它位置一律经 {@link FxDispatcher} 投递。
     * 本类是静态工具、拿不到上层实例，故自持一个（{@code PlatformFxDispatcher} 是无状态的薄包装，
     * 多一个实例没有代价；它已在 UI 线程时直接执行，不会白绕队列）。</p>
     */
    private static final FxDispatcher DISPATCHER = new PlatformFxDispatcher();

    /** 已套过主题的对话框面板（弱引用：窗口关掉后自动消失，不会攒住旧场景）。 */
    private static final Set<DialogPane> DIALOG_PANES =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

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
        // 记下来：换主题时它可能还开着，得一并重刷（见 refreshAll）
        DIALOG_PANES.add(pane);
    }

    /**
     * 登记「换主题后怎么重刷」。
     *
     * <p>给那些**不在主窗口节点树里**的组件用（对话框内容、独立面板、弹层）：它们拿不到
     * {@code DesktopShell.themed} 的登记，不登记就会停在切之前的颜色上。</p>
     *
     * @param listener 重刷动作
     */
    public static void onThemeChange(Runnable listener) {
        THEME_LISTENERS.add(listener);
    }

    /**
     * 主题已切换：重刷还开着的对话框面板，再跑一遗登记过的重刷动作。
     *
     * @see #onThemeChange(Runnable)
     */
    public static void refreshAll() {
        List<DialogPane> panes;
        synchronized (DIALOG_PANES) {
            panes = List.copyOf(DIALOG_PANES);
        }
        for (DialogPane pane : panes) {
            // dialog() 会重算 dialogTheme()、重装场景样式表，完全幂等；个别窗口已释放时跳过
            try {
                dialog(pane);
            } catch (RuntimeException e) {
                LOG.warn("重刷对话框主题失败（已跳过该窗口）：{}", e.toString());
            }
        }
        for (Runnable listener : THEME_LISTENERS) {
            // 逐个隔离：一个组件重刷失败，不该让其余组件停在旧主题
            try {
                listener.run();
            } catch (RuntimeException e) {
                LOG.warn("重刷主题失败（已跳过该组件）：{}", e.toString());
            }
        }
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

        String external = external(wanted);
        if (external == null) {
            LOG.warn("主题补丁样式表缺失，控件颜色可能回落到 modena 默认值：{}", wanted);
            return;
        }
        // 按**文件名**清掉另一份（以及任何残留的同族表）：
        // 原先只按 `external(stale)` 的 URL 字符串移除 —— 一旦 URL 形式不一致（不同加载方式 /
        // 原生镜像），stale 表就删不掉；两份同时在场上时，切回旧主题获胜的会是**旧那份**
        // （顺序靠前，后加的反而被压住），症状同样是「某些区域停留在切换前的颜色」（issue #153）。
        scene.getStylesheets().removeIf(url -> isThemeSheet(url) && !url.endsWith(fileName(wanted)));
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
    /**
     * 是否本主题系统的补丁样式表（按文件名判断，与加载方式 / URL 形式无关）。
     *
     * @param url 场景样式表里的条目
     * @return 属于本主题系统返回 {@code true}
     */
    private static boolean isThemeSheet(String url) {
        return url != null && (url.endsWith(fileName(DARK_SHEET)) || url.endsWith(fileName(LIGHT_SHEET)));
    }

    /**
     * 取资源路径的文件名部分。
     *
     * @param path 资源路径
     * @return 文件名
     */
    private static String fileName(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

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
        // 本体之外还要覆盖「皮肤内部基面」：它才是输入类控件的可见底色（见 SKIN_SURFACE_SELECTORS）
        applySkinSurfaces(node, css);
    }

    /**
     * 设样式**并登记重刷**：与 {@code DesktopShell.themed} 同一语义，供对话框 / 面板使用。
     *
     * @param node  节点
     * @param style 样式提供者（每次重刷时重新求值，因此能拿到新主题的色值）
     */
    static void themedForever(Node node, Supplier<String> style) {
        onThemeChange(() -> themed(node, style.get()));
        themed(node, style.get());
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
            if (background == null) {
                // 输入类控件的底色走这个属性（modena 的 .text-input 用的也是它），
                // 而 setStyle 在原生镜像下未必传到 TextArea 的 .content（见类注释），
                // 所以这里也接受它，让 API 兜底真正盖住输入框。
                background = value(style, "-fx-control-inner-background");
            }
            if (background != null) {
                paint(region, background, "背景色");
            }
            paintBorder(region, style);
        }
    }

    /**
     * 按样式串里的 {@code -fx-border-*} 同步边框（issue #229）。
     *
     * <p>为什么要走属性 API：输入框那圈「轮廓线」在 JVM 下是 modena 的多层背景 / 边框规则画的，
     * 而那些规则里有 {@code derive()} / {@code linear-gradient()} —— 原生镜像下这些 CSS 函数
     * 会让**整条声明被丢弃**（见 {@code TS-2610-029}）。于是 exe 里输入框与页底同色
     * （{@code Palette.CONTROL_INNER}），却**一圈线都没有**：纯色界面下肉眼分不出输入框的范围
     * （2026-10-15 真机，issue #229）。补丁样式表里给的 {@code -fx-border-color} 虽是纯字面量，
     * 但对 {@code TextArea} 这类复合控件同样不可靠 —— 与底色同一个理由，落到属性 API 最稳。</p>
     *
     * <p>约定：样式串里**没写** {@code -fx-border-color} 就不动现有边框（不能把 CSS 给的边框抹掉）；
     * 写了 {@code transparent} / {@code none} 才清空。宽度 / 圆角缺省取 1 / 3 —— 与补丁样式表里
     * 输入类控件的取值一致，这样 JVM 下两种途径画出来的是同一个外观。</p>
     *
     * @param region 目标区域
     * @param style  与本体同一份样式串
     */
    private static void paintBorder(Region region, String style) {
        String colorText = value(style, "-fx-border-color");
        if (colorText == null) {
            return;
        }
        if (colorText.startsWith("transparent") || "none".equals(colorText.trim())) {
            region.setBorder(null);
            return;
        }
        double width = number(style, "-fx-border-width", 1);
        double radius = number(style, "-fx-border-radius", 3);
        color(colorText, c -> region.setBorder(new Border(new BorderStroke(
                c, BorderStrokeStyle.SOLID, new CornerRadii(radius), new BorderWidths(width)))), "边框色");
    }

    /**
     * 取样式串里的数值（带 {@code px} 单位也吃得下）。
     *
     * @param style    样式串
     * @param key      属性名
     * @param fallback 取不到 / 解析不出时的缺省值
     * @return 数值
     */
    private static double number(String style, String key, double fallback) {
        String raw = value(style, key);
        if (raw == null) {
            return fallback;
        }
        try {
            return Double.parseDouble(raw.trim().replace("px", ""));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /**
     * 把同一份底色/文字色写到**皮肤内部基面**上（{@link #SKIN_SURFACE_SELECTORS}）。
     *
     * <p>为什么要挑时机：皮肤是**首次 CSS 处理时**才建出来的，控件刚 {@code new} 出来时
     * {@code lookup(".content")} 什么都找不到；而 {@code lookup} 的结果也只在 CSS 处理之后有效
     * （JavaFX 的约定）。这里经 {@code FxDispatcher} 投递（已是 UI 线程时直接执行），
     * 查找前显式 {@code applyCss()}；皮肤尚未建立时 {@code lookup} 返回 {@code null}，
     * 直接跳过 —— 那个阶段由补丁样式表负责（也正是启动时看起来正常的原因）。</p>
     *
     * <p>只处理**基面**（背景），不动内部的文字节点：{@code TextArea} / {@code TextField} 的
     * {@code .text} 由皮肤把它绑定到控件的 {@code textFill} 属性上，再用属性 API 直写会打断那条绑定，
     * 反而让文字色从此不跟主题（{@link #apply(Node, String)} 设控件 {@code textFill} 已经是正路）。</p>
     *
     * @param node 控件
     * @param css  与本体同一份样式串
     */
    private static void applySkinSurfaces(Node node, String css) {
        String background = value(css, "-fx-background-color");
        if (background == null) {
            background = value(css, "-fx-control-inner-background");
        }
        if (background == null || node.getScene() == null) {
            // 没有底色可同步，或还没进场景（皮肤此时必然不存在）
            return;
        }
        String fill = background;
        DISPATCHER.dispatch(() -> {
            if (node.getScene() == null) {
                return;
            }
            try {
                // lookup 前必须让 CSS 跑过一遍，否则皮肤与内部节点可能尚未建立
                node.applyCss();
                for (String selector : SKIN_SURFACE_SELECTORS) {
                    if (node.lookup(selector) instanceof Region region) {
                        paint(region, fill, "皮肤内部基面 " + selector);
                    }
                }
            } catch (RuntimeException e) {
                // 上色失败不该影响整屏（与 refreshAll 的隔离策略一致）
                LOG.warn("同步皮肤内部基面失败（已跳过 {}）：{}", node.getClass().getSimpleName(), e.toString());
            }
        });
    }

    /**
     * 按样式串设底色（{@code transparent} 也要落到属性上，否则会留着上一次设过的背景）。
     *
     * @param region     目标区域
     * @param background 底色值
     * @param what       日志用的名称
     */
    private static void paint(Region region, String background, String what) {
        if (background.startsWith("transparent")) {
            region.setBackground(Background.EMPTY);
        } else {
            color(background, c -> region.setBackground(
                    new Background(new BackgroundFill(c, CornerRadii.EMPTY, Insets.EMPTY))), what);
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
