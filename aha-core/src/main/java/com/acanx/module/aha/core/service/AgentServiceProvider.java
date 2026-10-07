package com.acanx.module.aha.core.service;

import com.acanx.module.aha.common.runtime.Endpoint;
import com.acanx.module.aha.core.config.AhaConfig;

/**
 * Agent 服务提供者 SPI：按端点 scheme 提供 Agent 服务实现。
 *
 * <p>通过 {@link java.util.ServiceLoader} 发现，使新增运行形态（远程 / WSL / 容器 /
 * 云函数）只需新增模块并声明 {@code provides}，无需修改 {@code aha-core}。</p>
 *
 * <p>实现约定：</p>
 * <ul>
 *   <li>{@code module-info.java} 声明 {@code provides ... AgentServiceProvider with ...}</li>
 *   <li>同时提供 {@code META-INF/services} 文件，兼容 classpath 运行</li>
 *   <li>返回的 {@code AgentService} 必须实现 {@link AgentService#runtime()}，
 *       以便上层获知实际执行环境</li>
 * </ul>
 *
 * @since 0.1.0
 */
public interface AgentServiceProvider {

    /**
     * 支持的端点 scheme（小写），如 {@code wsl}、{@code ssh}、{@code daemon}、{@code ws}。
     *
     * @return scheme
     */
    String scheme();

    /**
     * 创建服务实例。
     *
     * @param endpoint 端点
     * @param config   配置，可为 {@code null}
     * @return Agent 服务
     */
    AgentService create(Endpoint endpoint, AhaConfig config);

    /**
     * 是否支持该端点（默认按 scheme 匹配，具备多形态的实现可覆写）。
     *
     * @param endpoint 端点
     * @return 是否支持
     */
    default boolean supports(Endpoint endpoint) {
        return endpoint != null && scheme().equalsIgnoreCase(endpoint.scheme());
    }

    /**
     * 优先级（数值大者优先，用于同一 scheme 的多个实现）。
     *
     * @return 优先级
     */
    default int priority() {
        return 0;
    }
}
