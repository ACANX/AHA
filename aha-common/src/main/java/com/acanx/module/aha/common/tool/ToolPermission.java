package com.acanx.module.aha.common.tool;

/**
 * 工具权限级别。
 *
 * @since 0.1.0
 */
public enum ToolPermission {
    /** 读取文件、环境变量。 */
    READ,
    /** 写入文件、修改配置。 */
    WRITE,
    /** HTTP 请求。 */
    NETWORK,
    /** 执行外部命令。 */
    EXECUTE,
    /** 修改系统配置、安装工具。 */
    ADMIN
}
