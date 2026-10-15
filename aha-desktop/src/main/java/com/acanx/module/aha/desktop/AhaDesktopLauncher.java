package com.acanx.module.aha.desktop;

/**
 * 桌面端**单体 jar（fat jar）**的启动入口。
 *
 * <p>为什么不把 {@link AhaDesktopApp} 直接写成 {@code Main-Class}：JDK 的启动器在
 * {@code java -jar} 模式下会检查主类——只要主类是 {@code javafx.application.Application}
 * 的子类，它就假定 JavaFX 由模块路径提供，直接报
 * 「Error: JavaFX runtime components are missing, and are required to run this application」。
 * 把 {@code main} 放进一个**不继承 Application** 的普通类再转发过去，同一个 jar 即可用
 * {@code java -jar aha-desktop.jar} 启动（issue #218）。</p>
 *
 * <p>转发而不是重写：{@link AhaDesktopApp#main(String[])} 里还有「启动横幅 + 渲染诊断开关 +
 * {@code launch(Class, args)}」这几步（版本行必须走 stdout，见 issue #26 / TS-2610-003），
 * 重写一份必然漂移。</p>
 *
 * <p>模块路径模式（{@code --module com.acanx.module.aha.desktop/…AhaDesktopApp}）不走本类，
 * 行为与改造前完全一致。</p>
 *
 * @since 0.2.0
 */
public final class AhaDesktopLauncher {

    private AhaDesktopLauncher() {
    }

    /**
     * 启动入口：转发给应用类。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        AhaDesktopApp.main(args);
    }
}
