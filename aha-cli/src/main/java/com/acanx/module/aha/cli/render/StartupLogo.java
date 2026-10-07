package com.acanx.module.aha.cli.render;

import java.util.List;

/**
 * 启动横幅右侧的 ASCII 标志（对应 {@code aha-core} 内的 {@code Logo.svg}）。
 *
 * <p>刻意只用 <b>纯 ASCII</b>（{@code 0x20}–{@code 0x7E}）：Unicode 方块字符
 * （{@code ▀▄█}）在非 UTF-8 代码页的控制台上会整片退化成 {@code ?}，而启动横幅正是
 * 用户最先看到的东西——退化成问号比没有标志更糟。副作用是每行宽度恒等于字符数，
 * 不必按显示宽度换算。</p>
 *
 * <p>图形与 {@code Logo.svg} 保持同一结构：灯泡四周 <b>9 条光线</b>（顶部 1 条 + 两侧各 4 条）、
 * 灯泡内上部是 {@code AHA} 字样、下部是笑脸、底部是螺纹灯座。标志分两档
 * （{@link Size#FULL} / {@link Size#COMPACT}），由 {@link StartupBanner} 按可用宽度选择；
 * 都放不下时整个标志不显示。</p>
 *
 * @since 0.1.0
 */
public final class StartupLogo {

    /** 标志档位：窄终端用 {@link #COMPACT}。 */
    public enum Size {
        /** 完整标志（23 列 × 16 行）。 */
        FULL,
        /** 紧凑标志（17 列 × 9 行）：省去笑脸，保留光线、灯泡、字样与灯座。 */
        COMPACT
    }

    /** 着色分区：光线 / 灯泡 / 灯座 / 字样。 */
    public enum Tone {
        /** 四周光线。 */
        RAY,
        /** 灯泡轮廓与笑脸。 */
        BULB,
        /** 灯座螺纹。 */
        BASE,
        /** {@code AHA} 字样。 */
        WORD
    }

    /**
     * 一段同色文本。
     *
     * @param text 原始文本（纯 ASCII，不含 SGR）
     * @param tone 着色分区
     */
    public record Segment(String text, Tone tone) {
    }

    /**
     * 标志的一行，由若干同色段落拼成。
     *
     * <p>之所以细到段落而不是整行：{@code AHA} 与灯泡轮廓落在同一行，整行同色会把字样
     * 和轮廓染成一个颜色，{@code Logo.svg} 里两者是分明的主次关系。</p>
     *
     * @param segments 段落
     */
    public record Row(List<Segment> segments) {

        /**
         * 拼接后的原始文本。
         *
         * @return 文本
         */
        public String text() {
            StringBuilder sb = new StringBuilder();
            segments.forEach(segment -> sb.append(segment.text()));
            return sb.toString();
        }
    }

    /** 完整标志：9 条光线 + 灯泡（上部 AHA、下部笑脸）+ 螺纹灯座。 */
    private static final List<Row> FULL = List.of(
            row(seg("       \\   |   /", Tone.RAY)),
            row(seg("        \\  |  /", Tone.RAY)),
            row(seg(" -       \\ | /       -", Tone.RAY)),
            row(seg("          \\|/", Tone.RAY)),
            row(seg("        .-\"\"\"\"\"-.", Tone.BULB)),
            row(seg("      .'         '.", Tone.BULB)),
            row(seg("     |   ", Tone.BULB), seg("A H A", Tone.WORD), seg("   |", Tone.BULB)),
            row(seg("-    ", Tone.RAY), seg("|           |", Tone.BULB), seg("    -", Tone.RAY)),
            row(seg("     |   o   o   |", Tone.BULB)),
            row(seg("     |     ^     |", Tone.BULB)),
            row(seg("     |   '---'   |", Tone.BULB)),
            row(seg("      '.       .'", Tone.BULB)),
            row(seg(" /      ", Tone.RAY), seg("'-...-'", Tone.BULB), seg("      \\", Tone.RAY)),
            row(seg("          |=====|", Tone.BASE)),
            row(seg("          |=====|", Tone.BASE)),
            row(seg("          '-----'", Tone.BASE)));

    /** 紧凑标志：同样 9 条光线，灯泡内保留字样、省去笑脸。 */
    private static final List<Row> COMPACT = List.of(
            row(seg("      \\ | /", Tone.RAY)),
            row(seg("   -   ", Tone.RAY), seg("\\|/", Tone.RAY), seg("   -", Tone.RAY)),
            row(seg("    .-\"   \"-.", Tone.BULB)),
            row(seg("   /  ", Tone.BULB), seg("A H A", Tone.WORD), seg("  \\", Tone.BULB)),
            row(seg("-  ", Tone.RAY), seg("|         |", Tone.BULB), seg("  -", Tone.RAY)),
            row(seg("    \\       /", Tone.BULB)),
            row(seg(" /   ", Tone.RAY), seg("'-...-'", Tone.BULB), seg("   \\", Tone.RAY)),
            row(seg("      |===", Tone.BASE)),
            row(seg("      '---", Tone.BASE)));

    private StartupLogo() {
    }

    private static Segment seg(String text, Tone tone) {
        return new Segment(text, tone);
    }

    private static Row row(Segment... segments) {
        return new Row(List.of(segments));
    }

    /**
     * 取指定档位的标志行。
     *
     * @param size 档位
     * @return 标志行（文本未着色）
     */
    public static List<Row> rows(Size size) {
        return size == Size.COMPACT ? COMPACT : FULL;
    }

    /**
     * 标志宽度（列）：纯 ASCII，故等于最长行的字符数。
     *
     * @param size 档位
     * @return 宽度
     */
    public static int width(Size size) {
        return rows(size).stream().mapToInt(r -> r.text().length()).max().orElse(0);
    }

    /**
     * 标志高度（行数）。
     *
     * @param size 档位
     * @return 高度
     */
    public static int height(Size size) {
        return rows(size).size();
    }

    /**
     * 渲染为已着色的行。
     *
     * <p>着色在这里一次完成，调用方拼接时不要再参与宽度计算——SGR 序列会被当成普通字符，
     * 按它数宽度必然错位（与 {@link ToolBlock} 同一条约束）。</p>
     *
     * @param size  档位
     * @param style 样式；{@code null} 或不着色时返回原文
     * @return 已着色的行
     */
    public static List<String> styled(Size size, Style style) {
        return rows(size).stream().map(row -> styled(row, style)).toList();
    }

    /**
     * 渲染为待并排的标志。
     *
     * @param size  档位
     * @param style 样式；{@code null} 或不着色时返回原文
     * @return 标志
     */
    public static LogoArt art(Size size, Style style) {
        return new LogoArt(width(size), styled(size, style));
    }

    private static String styled(Row row, Style style) {
        StringBuilder sb = new StringBuilder();
        row.segments().forEach(segment -> sb.append(paint(style, segment.tone(), segment.text())));
        return sb.toString();
    }

    private static String paint(Style style, Tone tone, String text) {
        if (style == null) {
            return text;
        }
        return switch (tone) {
            case RAY -> style.logoRay(text);
            case BULB -> style.logoBulb(text);
            case BASE -> style.logoBase(text);
            case WORD -> style.logoWord(text);
        };
    }
}
