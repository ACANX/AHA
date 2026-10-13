package com.acanx.module.aha.desktop;

import java.util.Locale;

/**
 * 原生镜像下的文本渲染默认值 —— <b>临时缓解措施（workaround），不是最终方案</b>。
 *
 * <p><b>它在绕开什么</b>：GraalVM {@code native-image} 产物在 JavaFX 的 <b>LCD 次像素文本
 * 合成</b>路径上退化了——同一段文字、同一份字体，原生包渲染出来没有彩色边缘（次像素抗锯齿
 * 的必然产物），笔画边缘被灰度化后扩散，视觉上「更粗、更糊、发虚」；JVM 模式则保留彩色边缘、
 * 字形锐利。issue #48 的逐像素对照（两张 2560×1600 截图直接对齐）给出的数字：
 * 彩色边缘像素 JVM <b>165,783</b> / native <b>9,025</b>（18.4 倍），native 的暗像素反而多 67%。
 * 也就是说 <b>JVM = 次像素，native = 灰度</b> —— 不是字体族、不是字体实现、不是 Prism 管线、
 * 也不是 DPI 缩放（这几条都已在 #48 中逐条排除）。</p>
 *
 * <p>根本成因指向 DirectWrite 的渲染参数（{@code IDWriteRenderingParams}，ClearType 由系统 /
 * 显示器设置解析而来）在原生镜像里读不到：closed-world 编译下这条运行期路径缺失，DirectWrite
 * 退回默认参数（无 ClearType）。这与 issue #60（原生镜像读不到系统与驱动信息）很可能是同一类成因。</p>
 *
 * <p><b>重要：代码级根因尚未修好</b>。本类<b>没有把问题修好</b>，只是在原生镜像内改走灰度
 * 抗锯齿以绕开该退化路径——灰度路径在原生与 JVM 两个构建下表现一致（对照实验见
 * {@code Docs/Design/DesktopNativeDesign.md} §6.1 与 issue #48）。因此它<b>必须被视为临时措施</b>：
 * 一旦上游（GraalVM / OpenJFX）修复了该退化，或本项目补上了渲染参数的读取，
 * 就应当<b>删除本类与它的调用点</b>（{@link AhaDesktopApp#main}），回到 JavaFX 默认行为。</p>
 *
 * <h2>开关与优先级（命令行即可指定，不必改代码）</h2>
 *
 * <pre>
 * 1. -Dprism.lcdtext=true|false              用户直接决定，程序一律不覆盖（最高优先）
 * 2. -Daha.desktop.nativeTextGreyscale=false 关闭本缓解措施，回到 JavaFX 默认
 * 3. 未指定以上任一属性且在原生镜像内        程序写入 prism.lcdtext=false（灰度）← 本类默认行为
 * 4. JVM 版（Dist 里的 aha-desktop jar）     程序不做任何处理，保持 JavaFX 默认
 * </pre>
 *
 * <h2>回退操作（两种粒度）</h2>
 *
 * <pre>
 * 只这一次运行不缓解（推荐，不改代码、不改发布物）：
 *   aha-desktop-native.exe -Dprism.lcdtext=true                 直接恢复 JavaFX 默认（LCD 次像素）
 *   aha-desktop-native.exe -Daha.desktop.nativeTextGreyscale=false  关掉本默认值本身（等同未打本改动）
 *
 * 永久回退到改动前：
 *   ① 删掉 AhaDesktopApp.main 里的 NativeRenderDefaults.applyIfNativeImage(); 这一行；
 *   ② 或对合入本改动的提交执行 git revert（本类与调用点会一并撤销）。
 * </pre>
 *
 * <p><b>时机是硬约束</b>：{@code prism.lcdtext} 在 {@code PrismFontFactory} 的静态初始化里
 * 被读取 <b>一次</b>，晚于工具包启动再设置就没有效果。因此本方法必须最早执行 —— 当前在
 * {@link AhaDesktopApp#main} 的第一条语句处调用，早于 {@code Application.launch}。
 * 原生镜像构建里 {@code com.sun.javafx.font} 与 {@code com.sun.prism} 都是
 * {@code --initialize-at-run-time}（见 {@code aha-desktop-native/src/native/native-image-args.txt}），
 * 故运行期写入的值会被真正读取；若这两包日后改为构建期初始化，本缓解措施会<b>静默失效</b>
 * （这正是它属于临时措施的另一面，守卫见 {@code NativeRenderDefaultsTest}）。</p>
 *
 * @since 0.2.0
 */
public final class NativeRenderDefaults {

    /** GraalVM 在原生镜像内注入的标记属性；非 null 即表示当前运行于原生镜像。 */
    private static final String NATIVE_IMAGE_MARKER = "org.graalvm.nativeimage.imagecode";

    /** JavaFX 属性：是否启用 LCD 次像素文本合成。 */
    private static final String LCD_TEXT = "prism.lcdtext";

    /**
     * 本缓解措施的<b>总开关</b>：显式设为 false / off / no / 0 即关闭本默认值，
     * 回到 JavaFX 默认（Windows 上即 LCD 次像素合成）。未设置时视为开启。
     */
    public static final String GREYSCALE_DEFAULT_SWITCH = "aha.desktop.nativeTextGreyscale";

    /** 最近一次判定的结果，供渲染诊断如实呈现「这个值到底是谁设的」。 */
    private static volatile String decision = "未判定";

    private NativeRenderDefaults() {
    }

    /**
     * 原生镜像内、用户未显式指定 {@code prism.lcdtext}、且总开关未关闭时，关闭 LCD 次像素合成。
     *
     * <p>幂等：重复调用只在第一次生效。任何异常都不向外抛 —— 渲染默认值绝不能阻断启动。</p>
     *
     * @return 本次调用是否真的写入了该属性
     */
    public static boolean applyIfNativeImage() {
        try {
            if (System.getProperty(NATIVE_IMAGE_MARKER) == null) {
                decision = "跳过：非原生镜像（JVM 版不受影响）";
                return false;
            }
            String lcd = System.getProperty(LCD_TEXT);
            if (lcd != null) {
                decision = "跳过：用户已显式设置 prism.lcdtext=" + lcd;
                return false;
            }
            if (isSwitchOff()) {
                decision = "跳过：总开关已关闭（" + GREYSCALE_DEFAULT_SWITCH + "="
                        + System.getProperty(GREYSCALE_DEFAULT_SWITCH) + "）";
                return false;
            }
            System.setProperty(LCD_TEXT, "false");
            decision = "已按原生镜像默认写入 prism.lcdtext=false（灰度抗锯齿）";
            return true;
        } catch (RuntimeException | Error e) {
            decision = "不可用(" + e.getClass().getSimpleName() + ")";
            return false;
        }
    }

    /**
     * 最近一次判定结果，用于渲染诊断。
     *
     * <p>{@code AhaDesktopApp.logRenderingDiagnostics()} 会把它连同总开关的实际取值打进日志，
     * 于是「这一版的原生包到底是灰度还是次像素、是谁决定的」在真机日志里可以直接读出来，
     * 不必再靠观感猜。</p>
     *
     * @return 形如「已按原生镜像默认写入 prism.lcdtext=false（灰度抗锯齿）」或「跳过：…」的说明
     */
    public static String decision() {
        return decision;
    }

    /** @return 总开关是否被显式关闭（false / off / no / 0，忽略大小写与空白） */
    private static boolean isSwitchOff() {
        String value = System.getProperty(GREYSCALE_DEFAULT_SWITCH);
        if (value == null) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.equals("false") || normalized.equals("off")
                || normalized.equals("no") || normalized.equals("0");
    }
}
