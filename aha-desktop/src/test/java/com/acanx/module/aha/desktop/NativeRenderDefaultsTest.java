package com.acanx.module.aha.desktop;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link NativeRenderDefaults} 测试（issue #44 / issue #48）。
 *
 * <p>缓解措施的要点是「只在原生镜像内、只在用户没表态时、且总开关没关时才动手」，
 * 以及「任何一步失败都不阻断启动」。这里逐条钉住这几种情形——包括每个开关形态，
 * 避免将来把 {@code off} / {@code no} / {@code 0} 这类写法漏掉，
 * 让「回退不生效」变成只有真机才发现的坑。</p>
 *
 * <p>测试直接改系统属性，因此每个用例前后都要保存 / 还原 / 清理，
 * 不让状态漏给同 JVM 里的其它测试。</p>
 *
 * @since 0.2.0
 */
class NativeRenderDefaultsTest {

    /** 与实现约定一致的字面量：测试独立书写，防止实现改动被「复述式断言」掩盖。 */
    private static final String NATIVE_IMAGE_MARKER = "org.graalvm.nativeimage.imagecode";

    private static final String LCD_TEXT = "prism.lcdtext";

    private String savedMarker;
    private String savedLcd;
    private String savedSwitch;

    @BeforeEach
    void saveAndClear() {
        savedMarker = System.getProperty(NATIVE_IMAGE_MARKER);
        savedLcd = System.getProperty(LCD_TEXT);
        savedSwitch = System.getProperty(NativeRenderDefaults.GREYSCALE_DEFAULT_SWITCH);
        System.clearProperty(NATIVE_IMAGE_MARKER);
        System.clearProperty(LCD_TEXT);
        System.clearProperty(NativeRenderDefaults.GREYSCALE_DEFAULT_SWITCH);
    }

    @AfterEach
    void restore() {
        restoreProperty(NATIVE_IMAGE_MARKER, savedMarker);
        restoreProperty(LCD_TEXT, savedLcd);
        restoreProperty(NativeRenderDefaults.GREYSCALE_DEFAULT_SWITCH, savedSwitch);
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    @Test
    void jvmModeIsUntouched() {
        // 没有 GraalVM 标记属性 = JVM 模式：一个字都不改，prism.lcdtext 保持「没设」
        assertThat(NativeRenderDefaults.applyIfNativeImage()).isFalse();
        assertThat(System.getProperty(LCD_TEXT)).isNull();
        assertThat(NativeRenderDefaults.decision()).contains("非原生镜像");
    }

    @Test
    void nativeImageGetsGreyscaleDefault() {
        System.setProperty(NATIVE_IMAGE_MARKER, "runtime");

        assertThat(NativeRenderDefaults.applyIfNativeImage()).isTrue();
        assertThat(System.getProperty(LCD_TEXT)).isEqualTo("false");
        assertThat(NativeRenderDefaults.decision()).contains("prism.lcdtext=false");
    }

    @Test
    void explicitUserSettingWins() {
        System.setProperty(NATIVE_IMAGE_MARKER, "runtime");
        System.setProperty(LCD_TEXT, "true");

        // 用户显式指定了就一律不覆盖：这是最高优先级的回退手段
        assertThat(NativeRenderDefaults.applyIfNativeImage()).isFalse();
        assertThat(System.getProperty(LCD_TEXT)).isEqualTo("true");
        assertThat(NativeRenderDefaults.decision()).contains("用户已显式设置");
    }

    @Test
    void switchOffFormsAllSkip() {
        System.setProperty(NATIVE_IMAGE_MARKER, "runtime");

        for (String value : List.of("false", "FALSE", " off ", "no", "0")) {
            System.setProperty(NativeRenderDefaults.GREYSCALE_DEFAULT_SWITCH, value);
            System.clearProperty(LCD_TEXT);

            assertThat(NativeRenderDefaults.applyIfNativeImage())
                    .as("总开关=%s 时不应写入", value).isFalse();
            assertThat(System.getProperty(LCD_TEXT)).as("总开关=%s 时不应写入", value).isNull();
        }
    }

    @Test
    void switchOnFormsStillApply() {
        System.setProperty(NATIVE_IMAGE_MARKER, "runtime");

        // 除 false / off / no / 0 之外的取值（含空串、其它真值写法）都视为「没关」
        for (String value : List.of("true", "yes", "1", "")) {
            System.setProperty(NativeRenderDefaults.GREYSCALE_DEFAULT_SWITCH, value);
            System.clearProperty(LCD_TEXT);

            assertThat(NativeRenderDefaults.applyIfNativeImage())
                    .as("总开关=%s 时仍应写入", value).isTrue();
            assertThat(System.getProperty(LCD_TEXT)).as("总开关=%s 时仍应写入", value)
                    .isEqualTo("false");
        }
    }

    @Test
    void isIdempotent() {
        System.setProperty(NATIVE_IMAGE_MARKER, "runtime");

        assertThat(NativeRenderDefaults.applyIfNativeImage()).isTrue();
        // 第二次调用时属性已是 false，被当作「用户已显式设置」而跳过——结果不变
        assertThat(NativeRenderDefaults.applyIfNativeImage()).isFalse();
        assertThat(System.getProperty(LCD_TEXT)).isEqualTo("false");
    }

    /**
     * 守卫：本缓解措施的前提是 {@code com.sun.javafx.font} / {@code com.sun.prism} 为**运行期初始化**。
     *
     * <p>{@code prism.lcdtext} 只在 {@code PrismFontFactory} 静态初始化时读取一次。
     * 若这两包哪天被改成构建期初始化，属性就会在构建期被固化，我们在 {@code main} 里写的值
     * **不会再生效，而且不会报错** —— 这正是「静默失效」。真机观感出问题时，这一条能先把
     * 「是不是参数文件被改了」排除掉，不必重新疑到渲染本身。</p>
     */
    @Test
    void nativeImageArgsKeepPrismPackagesAtRuntimeInit() throws IOException {
        Path nativeDir = Path.of(System.getProperty("basedir", System.getProperty("user.dir")),
                "..", "aha-desktop-native", "src", "native");

        // 两份参数文件（JDK 25 基线 + JDK 27 实验腿）逐条同步，故都查
        for (String fileName : List.of("native-image-args.txt", "native-image-args-jdk27.txt")) {
            Path args = nativeDir.resolve(fileName);
            assertThat(args).as("%s 应存在", args).exists();

            String content = Files.readString(args, StandardCharsets.UTF_8);
            assertThat(content)
                    .as("%s 必须保持 com.sun.javafx.font 为运行期初始化，否则本缓解静默失效", fileName)
                    .contains("--initialize-at-run-time=com.sun.javafx.font")
                    .contains("--initialize-at-run-time=com.sun.prism");
        }
    }
}
