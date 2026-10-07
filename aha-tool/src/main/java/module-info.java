/**
 * AHA Tool 模块：内置工具实现，通过 ServiceLoader 注册。
 */
module com.acanx.module.aha.tool {
    requires com.acanx.module.aha.common;
    requires com.acanx.module.aha.core;
    requires org.slf4j;
    requires java.net.http;

    // 必须声明 uses：本模块的测试会通过 ServiceLoader 发现自身的提供者，
    // 而 JPMS 要求**调用方模块**声明 uses，否则抛 ServiceConfigurationError。
    // 只写 provides 是不够的——“提供”与“消费”是两件事。
    uses com.acanx.module.aha.common.tool.ToolProvider;

    provides com.acanx.module.aha.common.tool.ToolProvider
            with com.acanx.module.aha.tool.FileToolProvider,
                 com.acanx.module.aha.tool.HttpToolProvider,
                 com.acanx.module.aha.tool.ShellToolProvider;
}
