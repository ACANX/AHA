package com.acanx.module.aha.desktop.view;

import javafx.scene.text.Font;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * 界面字体回退：Linux 上缺中文字体时选中一个能显示汉字的族，并在完全缺失时给出可操作提示（issue #382）。
 *
 * <p>【为什么需要（2026-10-20 真机）】WSL2 Ubuntu 26.04 上 {@code fc-list :lang=zh} 为 0，
 * JavaFX 的默认族（{@code System} → fontconfig → DejaVu Sans）不含汉字字形，
 * 合成字体回退也借不到任何中文字体，于是界面上的汉字全是方块。
 * Windows / macOS 从不暴露这个问题，只是因为系统自带微软雅黑 / 苹方。</p>
 *
 * <p>【为什么只在 Linux 上替换全局字体】Windows / macOS 的默认 UI 字体本身能显示中文，
 * 替换成候选链里的雅黑 / 苹方会改变既有西文观感（违反「外观无回归」）。Linux 上则相反：
 * 默认族基本是 DejaVu Sans，必须显式选一个 CJK 族，中文才会用中文字体渲染。</p>
 *
 * <p>【判定口径】只看字体<strong>族名</strong>，不查字形：先按 {@link #CJK_CANDIDATES} 精确匹配
 * （大小写不敏感），全不命中时再用名字里的 CJK 特征做启发式兜底（如 {@code Noto Serif CJK}、
 * {@code Heiti SC}）。判定逻辑是纯函数 {@link #resolve(String, Collection)}，便于单测；
 * 真实环境只经由 {@link #current()} 取一次 {@link Font#getFamilies()}。</p>
 *
 * @since 0.1.5
 */
public final class FontFallback {

    /**
     * Linux 上安装中文字体的推荐命令（提示文案里直接给可复制的一行）。
     *
     * <p>{@code fonts-noto-cjk} 是 Debian / Ubuntu 的独立包，最小化安装与 WSL 镜像默认都不带。</p>
     */
    public static final String LINUX_INSTALL_COMMAND = "sudo apt install fonts-noto-cjk";

    /** 界面中文字体候选（顺序即优先级；先无衬线、再文泉驿 / Droid，最后才考虑 Windows / macOS 族）。 */
    public static final List<String> CJK_CANDIDATES = List.of(
            "Noto Sans CJK SC",
            "Source Han Sans SC",
            "Source Han Sans CN",
            "WenQuanYi Micro Hei",
            "WenQuanYi Zen Hei",
            "Droid Sans Fallback",
            "AR PL UMing CN",
            "Microsoft YaHei UI",
            "Microsoft YaHei",
            "PingFang SC",
            "Hiragino Sans GB");

    /**
     * 等宽代码区的字体链：先等宽族，再补 CJK 候选，最后才是通用族。
     *
     * <p>代码区（日志 / 工具输出 / 参数）不能换成比例字体，所以不能直接用全局 CJK 族；
     * 这里沿既有写法保留等宽族，只在 {@code monospace} <strong>之前</strong>插入 CJK 候选 ——
     * 通用族总是存在，放在它后面的候选永远不会被选中。</p>
     */
    public static final String MONOSPACE_CSS = "'Consolas', 'DejaVu Sans Mono', "
            + "'Noto Sans CJK SC', 'Source Han Sans SC', 'WenQuanYi Micro Hei', "
            + "'Droid Sans Fallback', 'Microsoft YaHei UI', 'PingFang SC', monospace";

    /** 族名里的 CJK 特征（精确候选全不命中时的启发式兜底）。 */
    private static final List<String> CJK_NAME_MARKERS = List.of(
            "cjk", "source han", "noto sans sc", "noto serif sc", "wenquanyi",
            "droid sans fallback", "heiti", "simhei", "yahei", "pingfang",
            "hiragino sans gb", "songti", "simsun", "kaiti");

    private static Resolution current;

    private FontFallback() {
    }

    /**
     * 中文字体探测结果。
     *
     * @param osName  操作系统名（{@code os.name} 的原文）
     * @param linux   是否 Linux
     * @param family  选中的 CJK 族；不需要替换或一个都没找到时为 {@code null}
     * @param matches 本机命中的 CJK 族（候选优先，其次启发式；按优先级）
     */
    public record Resolution(String osName, boolean linux, String family, List<String> matches) {

        /**
         * 是否该向用户提示「缺中文字体」。
         *
         * <p>只有 Linux 且一个 CJK 族都没找到才提示：Windows / macOS 系统自带中文字体，
         * 这里不替换字体也不打扰用户。</p>
         *
         * @return 需要提示返回 {@code true}
         */
        public boolean needsHint() {
            return linux && family == null;
        }

        /**
         * 一行诊断文案（写进启动日志）。
         *
         * @return 平台、是否替换字体、命中数量与族名
         */
        public String describe() {
            if (family != null) {
                return "平台=" + osName + "，界面字体=" + family
                        + "，本机命中 CJK 候选 " + matches.size() + " 个 " + matches;
            }
            String reason = linux
                    ? "本机未找到含汉字字形的字体（中文会显示为方块）"
                    : "非 Linux，沿用系统默认字体（系统自带中文字体）";
            return "平台=" + osName + "，不替换界面字体；" + reason
                    + "；本机命中 CJK 候选 " + matches.size() + " 个 " + matches;
        }
    }

    /**
     * 纯函数：按已安装字体族判断该选哪个 CJK 族。
     *
     * <p>把「取字体列表」与「判定」分开，单测就不需要真实字体环境：传入 Windows / Linux
     * 的各种族名组合即可覆盖候选顺序、缺失降级与非 Linux 分支。</p>
     *
     * @param osName            操作系统名（如 {@code Linux} / {@code Windows 11}）
     * @param installedFamilies 已安装字体族（{@link Font#getFamilies()}）
     * @return 探测结果
     */
    public static Resolution resolve(String osName, Collection<String> installedFamilies) {
        String os = osName == null ? "" : osName;
        boolean linux = os.toLowerCase(Locale.ROOT).contains("linux");
        List<String> installed = installedFamilies == null
                ? List.of() : installedFamilies.stream().filter(name -> name != null && !name.isBlank()).toList();

        List<String> matches = new ArrayList<>();
        for (String candidate : CJK_CANDIDATES) {
            for (String family : installed) {
                if (family.equalsIgnoreCase(candidate)) {
                    matches.add(family);
                    break;
                }
            }
        }
        if (matches.isEmpty()) {
            for (String family : installed) {
                if (looksLikeCjk(family)) {
                    matches.add(family);
                }
            }
        }
        String selected = linux && !matches.isEmpty() ? matches.get(0) : null;
        return new Resolution(os, linux, selected, List.copyOf(matches));
    }

    /**
     * 真实环境的探测结果（首次调用时读 {@link Font#getFamilies()}，之后复用）。
     *
     * <p>必须在 JavaFX 工具包初始化之后调用（{@code Application#start} 及之后的界面线程都满足）。</p>
     *
     * @return 探测结果
     */
    public static synchronized Resolution current() {
        if (current == null) {
            current = resolve(System.getProperty("os.name", ""), Font.getFamilies());
        }
        return current;
    }

    /**
     * 全局字体的 CSS 片段：需要替换时返回 {@code -fx-font-family: '…';}，否则返回空串。
     *
     * <p>拼进根节点内联样式即可让整棵树继承（{@code -fx-font-family} 是可继承属性）；
     * 不需要替换时返回空串，保证 Windows / macOS 的样式的字节内容与改动前完全一致。</p>
     *
     * @return CSS 片段
     */
    public static String globalFamilyCss() {
        String family = current().family();
        if (family == null || family.isBlank()) {
            return "";
        }
        // 族名来自系统字体表，理论上不含单引号；去掉它避免拼出非法 CSS
        return "-fx-font-family: '" + family.replace("'", "") + "';";
    }

    /**
     * 缺中文字体时给用户看的提示（多行，安装命令可直接复制）。
     *
     * @return 提示文案
     */
    public static String missingFontHint() {
        return "未检测到中文字体：本机没有含汉字字形的字体，界面中文会显示为方块。\n"
                + "Linux 上安装其中一种即可（装完重启 AHA 桌面端）：\n"
                + "  " + LINUX_INSTALL_COMMAND + "\n"
                + "  sudo apt install fonts-wqy-microhei";
    }

    private static boolean looksLikeCjk(String family) {
        String lower = family.toLowerCase(Locale.ROOT);
        for (String marker : CJK_NAME_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        return false;
    }
}
