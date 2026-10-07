/**
 * AHA Common 模块：共享 DTO、Tool SPI、事件模型、异常层次。
 *
 * <p>零外部依赖，仅依赖 JDK。</p>
 */
module com.acanx.module.aha.common {
    exports com.acanx.module.aha.common.model;
    exports com.acanx.module.aha.common.tool;
    exports com.acanx.module.aha.common.event;
    exports com.acanx.module.aha.common.exception;
    exports com.acanx.module.aha.common.runtime;
}
