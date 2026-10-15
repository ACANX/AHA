package com.acanx.module.aha.desktop;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import javafx.application.Application;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 单体 jar 启动入口的结构约束（issue #218）。
 *
 * <p>守的是一条「只在 {@code java -jar} 时才暴露」的硬约束：主类一旦变成
 * {@code Application} 的子类，JVM 的启动器就会报
 * 「JavaFX runtime components are missing」，而构建、单测与工作流的 Main-Class 检查
 * **都发现不了**（Main-Class 的名字没变）。这类回归只能在结构上守。</p>
 *
 * @since 0.2.0
 */
class AhaDesktopLauncherTest {

    /** 入口类必须是普通类，不能是 {@code Application} 的子类。 */
    @Test
    void 入口类不得继承Application() {
        assertThat(Application.class.isAssignableFrom(AhaDesktopLauncher.class)).isFalse();
    }

    /** 入口类必须提供标准的 {@code public static void main(String[])}。 */
    @Test
    void 入口类提供标准入口方法() throws Exception {
        Method main = AhaDesktopLauncher.class.getDeclaredMethod("main", String[].class);
        assertThat(Modifier.isPublic(main.getModifiers())).isTrue();
        assertThat(Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(main.getReturnType()).isEqualTo(void.class);
    }
}
