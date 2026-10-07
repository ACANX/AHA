package com.acanx.module.aha.common.runtime;

/**
 * 运行时类型。
 *
 * <p>用于标识 Agent 能力实际执行所处的环境。0.1 仅使用 {@link #LOCAL}，
 * 其余取值自 1.0 起逐步落地（见 {@code Docs/Design/RemoteAndProtocolDesign.md}）。</p>
 *
 * @since 0.1.0
 */
public enum RuntimeKind {

    /** 本机进程内（0.1 唯一实现）。 */
    LOCAL,

    /** Windows 11 的 WSL 发行版内。 */
    WSL,

    /** 容器内（Docker / Podman / K8s Pod）。 */
    CONTAINER,

    /** 通过 SSH 连接的远程 Linux 主机。 */
    SSH,

    /** 云函数 / Serverless（由事件触发的一次性执行）。 */
    SERVERLESS,

    /** 常驻服务进程（守护进程 / 服务端模式）。 */
    DAEMON
}
