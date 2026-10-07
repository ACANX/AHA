/**
 * AHA CLI 模块：命令行入口（picocli + JLine）。
 */
module com.acanx.module.aha.cli {
    requires com.acanx.module.aha.core;
    requires com.acanx.module.aha.tool;
    requires info.picocli;
    requires org.jline;
    requires org.slf4j;

    // 控制台编码探测需要按名称解析旧代码页（GBK / Big5 等）。
    // 这些字符集由 jdk.charsets 提供，在命名模块下默认不会被解析。
    requires jdk.charsets;
    // JLine 的 SIGINT 处理靠 sun.misc.Signal（反射调用），它只由 jdk.unsupported 导出。
    // 在 module-path 下不声明就找不到：Ctrl+C 会按默认行为直接结束 JVM——
    // 会话被杀，而不是「打断当前生成」。独立探针跑在 classpath（无名模块）上正常，
    // 所以这个问题只在模块路径下暴露。
    requires jdk.unsupported;

    // 日志实现在应用层绑定：需要在运行时按 Aha.Logging 程序化配置 appender 与级别。
    // core 仅依赖 slf4j-api，不绑定具体实现。
    requires org.apache.logging.log4j.core;
    requires org.apache.logging.log4j;

    exports com.acanx.module.aha.cli;

    opens com.acanx.module.aha.cli to info.picocli;
}
