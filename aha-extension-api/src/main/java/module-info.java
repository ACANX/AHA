/**
 * AHA Extension API 模块：扩展契约、事件总线、扩展点。
 *
 * <p>第三方扩展仅需依赖本模块与 {@code aha-common}。</p>
 *
 * @since 0.1.0
 */
module com.acanx.module.aha.extension.api {
    requires transitive com.acanx.module.aha.common;

    exports com.acanx.module.aha.extension.api;
    exports com.acanx.module.aha.extension.api.event;
    exports com.acanx.module.aha.extension.api.point;
}
