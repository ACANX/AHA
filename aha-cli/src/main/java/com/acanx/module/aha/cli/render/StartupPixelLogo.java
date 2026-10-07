package com.acanx.module.aha.cli.render;

import java.util.ArrayList;
import java.util.List;

/**
 * 启动横幅右侧的<b>像素风</b>标志：粗像素格、硬边界、平色，屏幕上是正方形。
 *
 * <p>为什么不采样 {@code Logo.svg}：终端里的像素艺术是<b>按格摆</b>出来的，不是缩采样出来的——
 * 采样会把 {@code AHA} 点阵糊掉。这里的光线走 Bresenham、灯泡用整数圆、字样与笑脸直接贴点阵，
 * 全部硬边界、无渐变。</p>
 *
 * <p>每个字符格承担上下两个像素（{@code \u2580} 前景=上、背景=下）。因终端字符格高宽比约 2:1，
 * 48x48 像素正好落成 48 列 x 24 行的正方形。透明像素走终端默认色（{@code 39} / {@code 49}），
 * 不画底卡、也不压暗终端背景。</p>
 *
 * <p>两档尺寸取自同一份网格：{@link #art()} 为 48x48，{@link #smallArt()} 由它按 2:1
 * <b>精确降采样</b>得到 24x24（不做二次描画，形状不会走样）。</p>
 *
 * <p>依赖 {@code \u2580} 与 256 色：控制台不支持时由调用方回退到纯 ASCII 线框标志
 * （{@link StartupLogo}）。</p>
 *
 * @since 0.1.0
 */
public final class StartupPixelLogo {

    /** 透明像素。 */
    private static final char TRANSPARENT = '.';

    /** ANSI：默认前景 / 默认背景（用于透明像素）。 */
    private static final String DEFAULT_FG = "39";
    private static final String DEFAULT_BG = "49";

    /** 重置。 */
    private static final String RESET = "\u001B[0m";

    /** 半块字符：前景画上半格、背景画下半格。 */
    private static final char HALF = '\u2580';

    // 调色板（取自 Logo.svg，去渐变、去中间过渡）：
    //   a = #B45309 -> 172（灯泡轮廓）
    //   b = #F59E0B -> 214（灯泡下半）
    //   c = #FDE68A -> 229（灯泡上半）
    //   e = #FBBF24 -> 221（光线）
    //   g = #7C2D12 -> 94（AHA 字样）
    //   f = #5B2107 -> 94（笑脸）
    //   l = #E5E7EB -> 195（灯座亮）
    //   n = #9CA3AF -> 247（灯座中）
    //   k = #4B5563 -> 66（灯座暗）

    /** 48x48 像素网格：每个字符是一个像素，'.' 为透明。 */
    private static final String[] PIXELS_48 = {
            "................................................",
            "................................................",
            "........................e.......................",
            "........................e.......................",
            ".............e..........e.........e.............",
            ".............e..........e.........e.............",
            "..............e.........e........e..............",
            "..............e.........e........e..............",
            "...............e........e.......e...............",
            "...............e................e...............",
            "................e......a.......e................",
            "..................aaaaaaaaaaa...................",
            "....ee..........aaaacccccccaaaa...........ee....",
            "......ee.......aaacccccccccccaaa........ee......",
            "........ee....aagggccgcccgccgggaa.....ee........",
            "..........e..aagcccgcgcccgcgcccgaa...e..........",
            "............aacgcccgcgcccgcgcccgcaa.............",
            "............aacgggggcgggggcgggggcaa.............",
            "...........aaccgcccgcgcccgcgcccgccaa............",
            "...........acccgcccgcgcccgcgcccgccca............",
            "..........aacccgcccgcgcccgcgcccgcccaa...........",
            "..........aabbbbbbbbbbbbbbbbbbbbbbbaa...........",
            "..........aabbbbbbbbbbbbbbbbbbbbbbbaa...........",
            "..........aabbbbbbbbbbbbbbbbbbbbbbbaa...........",
            ".eeeeeee..aabbbbfbbbbbbbbbbbbfbbbbbaa...eeeeeee.",
            "..........aabbbfbbbbbbbbbbbbbbfbbbbaa...........",
            "..........aabbbbbbbbbbbbbbbbbbbbbbbaa...........",
            "..........aabbbbbbbbbbbbbbbbbbbbbbbaa...........",
            "...........abbbbfffbbbbbbbfffbbbbbba............",
            "...........aabbbfffbbbbbbbfffbbbbbaa............",
            "............aabbfffbbbbbbbfffbbbbaa.............",
            "............aabbbbbbbbbbbbbbbbbbbaa.............",
            ".............aabbbbbbbbbbbbbbbbbaa..............",
            "..........e...aabbbbbbbbbbbbbbbaa....e..........",
            "........ee.....aaabbbbbbbbbbbaaa......ee........",
            "......ee........afaabbbbbbbaafa.........ee......",
            "....ee............faaaaaaaaaf.............ee....",
            "...................ff..a..ff....................",
            "..............aalllllllllllllllaa...............",
            "..............aalllllllllllllllaa...............",
            "...............annnnnnnnnnnnnnna................",
            "...............annnnnnnnnnnnnnna................",
            "...............aalllllllllllllaa................",
            "...............aalllllllllllllaa................",
            "................annnnnnnnnnnnna.................",
            "...............kkkkkkkkkkkkkkkkk................",
            "...............kkkkkkkkkkkkkkkkk................",
            "...............kkkkkkkkkkkkkkkkk................"
    };

    /** 24x24 像素网格：由 {@link #PIXELS_48} 按 2:1 降采样得到，供窄终端使用。 */
    private static final String[] PIXELS_24 = {
            "........................",
            "............e...........",
            "......e.....e....e......",
            ".......e....e...e.......",
            ".......e....e...e.......",
            "........eaaaaaae........",
            "..ee...aaaccccaa....ee..",
            "....eeaagggcgcgga.ee....",
            "......agggggggggaa......",
            ".....acgcggcggcgca......",
            ".....acbcbbcbbcbcaa.....",
            ".....abbbbbbbbbbbaa.....",
            "eeee.abbbbbbbbbbbaa.eeee",
            ".....abbbbbbbbbbbaa.....",
            ".....abbffbbbffbba......",
            "......abfbbbbfbbaa......",
            ".....eaabbbbbbbba.e.....",
            "...ee..aaabbbbaa...ee...",
            "..e......faaaff......e..",
            ".......alllllllaa.......",
            ".......annnnnnna........",
            ".......aalllllla........",
            ".......kknnnnnnk........",
            ".......kkkkkkkkk........"
    };

    private StartupPixelLogo() {
    }

    /**
     * 调色板字符对应的 256 色号。
     *
     * @param ch 调色板字符
     * @return 色号
     */
    private static int color(char ch) {
        return switch (ch) {
                case 'a' -> 172;
                case 'b' -> 214;
                case 'c' -> 229;
                case 'e' -> 221;
                case 'g' -> 94;
                case 'f' -> 94;
                case 'l' -> 195;
                case 'n' -> 247;
                case 'k' -> 66;
            default -> throw new IllegalArgumentException("未知调色板字符: " + ch);
        };
    }

    /**
     * 完整像素标志（48x48 像素 → 48 列 x 24 行）。
     *
     * @return 标志
     */
    public static LogoArt art() {
        return render(PIXELS_48);
    }

    /**
     * 降采样像素标志（24x24 像素 → 24 列 x 12 行）。
     *
     * @return 标志
     */
    public static LogoArt smallArt() {
        return render(PIXELS_24);
    }

    private static LogoArt render(String[] pixels) {
        int size = pixels.length;
        List<String> lines = new ArrayList<>(size / 2);
        for (int row = 0; row < size / 2; row++) {
            lines.add(renderLine(pixels, row));
        }
        return new LogoArt(size, List.copyOf(lines));
    }

    private static String renderLine(String[] pixels, int row) {
        String top = pixels[row * 2];
        String bottom = pixels[row * 2 + 1];
        int last = -1;
        for (int col = 0; col < top.length(); col++) {
            if (top.charAt(col) != TRANSPARENT || bottom.charAt(col) != TRANSPARENT) {
                last = col;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int col = 0; col <= last; col++) {
            char upper = top.charAt(col);
            char lower = bottom.charAt(col);
            if (upper == TRANSPARENT && lower == TRANSPARENT) {
                sb.append(' ');
                continue;
            }
            sb.append("\u001B[").append(upper == TRANSPARENT ? DEFAULT_FG : "38;5;" + color(upper))
                    .append(';').append(lower == TRANSPARENT ? DEFAULT_BG : "48;5;" + color(lower))
                    .append('m').append(HALF);
        }
        // 行尾不再补空白：右对齐由 StartupBanner 负责，尾部留白只会让重定向输出变脏
        return sb.append(RESET).toString();
    }
}
