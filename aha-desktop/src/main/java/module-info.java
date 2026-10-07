/**
 * AHA Desktop 模块：桌面端（OpenJFX）。
 *
 * <p>0.1 为占位；0.2 起实装。当前已落地的是窗口骨架与线程桥接契约
 * （{@code com.acanx.module.aha.desktop.fx}），不是完整界面。</p>
 */
module com.acanx.module.aha.desktop {
    requires com.acanx.module.aha.core;
    // AppVersion：版本号唯一来源在 aha-common（CLI 与桌面端共用）
    requires com.acanx.module.aha.common;

    requires javafx.controls;
    requires javafx.graphics;

    requires org.slf4j;

    exports com.acanx.module.aha.desktop;
}
