package com.acanx.module.aha.common.runtime;

import java.util.Map;

/**
 * 运行时描述符。
 *
 * <p>描述一个可执行 Agent 能力的环境实例；任务下发、事件回调与审计日志都以此作为归属标识。</p>
 *
 * @param id         实例标识，如 {@code local}、{@code wsl:Ubuntu}、{@code ssh:build-01}
 * @param kind       运行时类型
 * @param os         操作系统，如 {@code linux} / {@code windows} / {@code unknown}
 * @param arch       架构，如 {@code amd64} / {@code aarch64} / {@code unknown}
 * @param workingDir 工作目录，可为 {@code null}
 * @param remote     是否为远程/跨边界运行时
 * @param attributes 附加属性（如 WSL 发行版名、SSH 用户、容器 ID）
 * @since 0.1.0
 */
public record RuntimeDescriptor(
        String id,
        RuntimeKind kind,
        String os,
        String arch,
        String workingDir,
        boolean remote,
        Map<String, String> attributes
) {

    /** 本机运行时 ID。 */
    public static final String LOCAL_ID = "local";

    /**
     * 构造描述符（属性归一化为不可变 Map）。
     *
     * @param id         实例标识
     * @param kind       运行时类型
     * @param os         操作系统
     * @param arch       架构
     * @param workingDir 工作目录
     * @param remote     是否远程
     * @param attributes 附加属性
     */
    public RuntimeDescriptor {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    /**
     * 本机运行时描述符。
     *
     * @return 描述符
     */
    public static RuntimeDescriptor local() {
        String os = System.getProperty("os.name", "unknown").toLowerCase();
        String normalizedOs = os.contains("win") ? "windows" : os.contains("linux") ? "linux" : os;
        return new RuntimeDescriptor(
                LOCAL_ID,
                RuntimeKind.LOCAL,
                normalizedOs,
                System.getProperty("os.arch", "unknown"),
                System.getProperty("user.dir"),
                false,
                Map.of());
    }

    /**
     * 是否为本地运行时。
     *
     * @return 是否本地
     */
    public boolean isLocal() {
        return kind == RuntimeKind.LOCAL && !remote;
    }
}
