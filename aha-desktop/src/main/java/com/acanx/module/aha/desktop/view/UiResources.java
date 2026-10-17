package com.acanx.module.aha.desktop.view;

import java.util.List;

/**
 * 界面静态资源的**唯一路径来源**。
 *
 * <p>为什么要有这个类：在它出现之前，路径散在 7 个控制器（各自的 {@code FXML_VIEW} 常量）、
 * {@code ThemePaint}（两条 CSS）与 {@code LogoImage}（一张图片）里，共 10 处。
 * 调整一次目录就要动 10 个地方，而且**漏改不会有编译错误**——{@code Class.getResource}
 * 拿到的是字符串，找不到就返回 null，只有运行期才炸。集中到这里之后，目录再调整只改本文件。</p>
 *
 * <h2>两种形式，别混用</h2>
 * <ul>
 *   <li>本类的常量是**绝对 classpath 路径**（以 {@code /} 开头），可直接交给
 *       {@code Class#getResource} / {@code getResourceAsStream}；</li>
 *   <li>{@link #relative} 去掉前导 {@code /}——那是 **jar 内路径**形式，
 *       native-image 的 {@code -H:IncludeResources} 匹配的就是这一种，
 *       {@code FxmlViewChecks#assertResourceWhitelisted} 也用它。</li>
 * </ul>
 *
 * <h2>目录约定（2026-10-13 起）</h2>
 * <pre>
 * com/acanx/module/aha/desktop/view/
 * ├── fxml/   界面骨架
 * ├── css/    主题样式表
 * └── image/  图片
 * </pre>
 *
 * <p>注意：资源路径与 Java 包**不必对应**。把资源放进子目录不影响
 * {@code module-info} 的 {@code opens ... to javafx.fxml}——那条约束的是**控制器类所在的包**，
 * 与资源位置无关。</p>
 *
 * @since 0.1.3
 */
public final class UiResources {

    /** 资源根目录（绝对 classpath 路径，结尾不带分隔符）。 */
    public static final String BASE = "/com/acanx/module/aha/desktop/view";

    // ── FXML 骨架（fxml/）────────────────────────────────────────────────────

    /** 主窗口骨架。 */
    public static final String SHELL_FXML = BASE + "/fxml/desktop-shell.fxml";

    /** 供应商配置对话框。 */
    public static final String PROVIDER_DIALOG_FXML = BASE + "/fxml/provider-dialog.fxml";

    /** 日志面板。 */
    public static final String LOG_PANEL_FXML = BASE + "/fxml/log-panel.fxml";

    /** 设置面板。 */
    public static final String SETTINGS_DIALOG_FXML = BASE + "/fxml/settings-dialog.fxml";

    /** 授权弹窗。 */
    public static final String APPROVAL_DIALOG_FXML = BASE + "/fxml/approval-dialog.fxml";

    /** 「查看全部」输出对话框。 */
    public static final String OUTPUT_DIALOG_FXML = BASE + "/fxml/output-dialog.fxml";

    /** 工具列表对话框。 */
    public static final String TOOL_LIST_DIALOG_FXML = BASE + "/fxml/tool-list-dialog.fxml";

    // ── 样式表（css/）───────────────────────────────────────────────────────

    /** 暗色主题。 */
    public static final String THEME_DARK_CSS = BASE + "/css/aha-theme-dark.css";

    /** 亮色主题。 */
    public static final String THEME_LIGHT_CSS = BASE + "/css/aha-theme-light.css";

    // ── 图片（image/）───────────────────────────────────────────────────────

    /** 标志（`Logo.svg` 渲染出的位图版本，见 {@link LogoImage}）。 */
    public static final String LOGO_PNG = BASE + "/image/logo.png";

    private UiResources() {
    }

    /**
     * 去掉前导 {@code /}，转成 jar 内路径形式。
     *
     * @param absolutePath 绝对 classpath 路径（本类常量）
     * @return 形如 {@code com/acanx/module/aha/desktop/view/fxml/desktop-shell.fxml}
     */
    public static String relative(String absolutePath) {
        return absolutePath.startsWith("/") ? absolutePath.substring(1) : absolutePath;
    }

    /**
     * 全部受管资源（绝对形式）。
     *
     * <p>存在的意义是让「每一条路径都真的能加载到」可判定：{@code Class.getResource} 对写错的
     * 路径只会返回 {@code null}（不抛异常、编译也过），而资源白名单是**按扩展名**匹配的，
     * 静态对账同样拦不住写错目录——只有真去取一次才知道。见 {@code UiResourcesTest}。</p>
     *
     * @return 路径列表
     */
    public static List<String> all() {
        return List.of(SHELL_FXML, PROVIDER_DIALOG_FXML, LOG_PANEL_FXML, SETTINGS_DIALOG_FXML,
                APPROVAL_DIALOG_FXML, OUTPUT_DIALOG_FXML, TOOL_LIST_DIALOG_FXML,
                THEME_DARK_CSS, THEME_LIGHT_CSS, LOGO_PNG);
    }
}
