package com.acanx.module.aha.core.service;

import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.common.runtime.Endpoint;
import com.acanx.module.aha.common.tool.ToolApprover;
import com.acanx.module.aha.core.config.AhaConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * Agent 服务工厂。
 *
 * <p>本地形态直接构造；非本地端点通过 {@link AgentServiceProvider} SPI 发现实现，
 * 因此 1.0 起新增远程 / WSL / 容器 / 云函数形态时无需修改本类。</p>
 *
 * @since 0.1.0
 */
public final class AgentServiceFactory {

    /** 本地端点的 scheme。 */
    public static final String SCHEME_LOCAL = "local";

    private AgentServiceFactory() {
    }

    /**
     * 创建本地 Agent 服务。
     *
     * @param config 配置
     * @return Agent 服务
     */
    public static AgentService local(AhaConfig config) {
        return new LocalAgentService(config);
    }

    /**
     * 创建本地 Agent 服务，并指定工具授权裁决器。
     *
     * <p>交互式 CLI 传入控制台确认实现；无人值守场景传入 {@code null} 或
     * {@link ToolApprover#denyAll()}。</p>
     *
     * @param config   配置，可为 {@code null}
     * @param approver 工具授权裁决器
     * @return Agent 服务
     */
    public static AgentService local(AhaConfig config, ToolApprover approver) {
        return new LocalAgentService(config, approver);
    }

    /**
     * 连接到指定端点（{@code local://} 等价于 {@link #local}）。
     *
     * @param endpoint 端点
     * @param config   配置，可为 {@code null}
     * @return Agent 服务
     * @throws AhaException 无匹配的 {@link AgentServiceProvider} 时抛出（错误码 {@code NO_PROVIDER_FOR_ENDPOINT}）
     */
    public static AgentService connect(Endpoint endpoint, AhaConfig config) {
        if (endpoint == null || endpoint.isLocal()) {
            return local(config);
        }
        return providers().stream()
                .filter(provider -> provider.supports(endpoint))
                .max(Comparator.comparingInt(AgentServiceProvider::priority))
                .orElseThrow(() -> new AhaException("NO_PROVIDER_FOR_ENDPOINT",
                        "尚无支持 " + endpoint.scheme() + ":// 的运行时实现"
                                + "（计划于 1.0 及后续版本提供）: " + endpoint.toUri()))
                .create(endpoint, config);
    }

    /**
     * 连接到指定端点 URI。
     *
     * @param uri    端点 URI，如 {@code ssh://user@host:22}、{@code wsl://Ubuntu}
     * @param config 配置，可为 {@code null}
     * @return Agent 服务
     */
    public static AgentService connect(String uri, AhaConfig config) {
        return connect(Endpoint.parse(uri), config);
    }

    /**
     * 当前可用的端点 scheme 集合（含内置 {@code local}）。
     *
     * @return scheme 集合
     */
    public static Set<String> supportedSchemes() {
        Set<String> schemes = new LinkedHashSet<>();
        schemes.add(SCHEME_LOCAL);
        providers().forEach(provider -> schemes.add(provider.scheme()));
        return schemes;
    }

    /**
     * 发现的 {@link AgentServiceProvider} 实现。
     *
     * @return 提供者列表
     */
    public static List<AgentServiceProvider> providers() {
        List<AgentServiceProvider> found = new ArrayList<>();
        ServiceLoader.load(AgentServiceProvider.class).forEach(found::add);
        return found;
    }
}
