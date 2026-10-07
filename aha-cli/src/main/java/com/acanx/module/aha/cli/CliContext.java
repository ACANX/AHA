package com.acanx.module.aha.cli;

import com.acanx.module.aha.common.tool.ToolApprover;
import com.acanx.module.aha.core.config.AhaConfig;
import com.acanx.module.aha.core.service.LocalAgentService;
import com.acanx.module.aha.core.config.ConfigLoader;
import com.acanx.module.aha.core.config.ModelConfigStore;
import com.acanx.module.aha.core.service.AgentService;
import com.acanx.module.aha.core.service.AgentServiceFactory;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI 共享上下文：懒加载配置与 Agent 服务。
 *
 * <p>配置分为两份：主配置（{@code Aha.yaml}）与模型配置（{@code Model.yml}）。</p>
 *
 * @since 0.1.0
 */
final class CliContext {

    /** 项目配置文件。 */
    static final String PROJECT_CONFIG = ConfigLoader.CONFIG_FILE_NAME;

    private static volatile AhaConfig config;
    private static volatile ModelConfigStore modelStore;
    private static volatile AgentService service;

    private CliContext() {
    }

    /**
     * 加载配置：优先项目 `./Aha.yaml`，否则使用内置默认；并合并 `Model.yml`。
     *
     * @return 配置
     */
    static synchronized AhaConfig config() {
        if (config == null) {
            Path path = Path.of(PROJECT_CONFIG);
            config = Files.exists(path) ? ConfigLoader.load(path) : ConfigLoader.loadDefault();
        }
        return config;
    }

    /**
     * 注入已加载的配置，避免重复读取与重复日志。
     *
     * <p>{@link AhaCli#main} 为配置日志系统已提前加载过一次，此处复用该结果。</p>
     *
     * @param loaded 已加载的配置
     */
    static synchronized void preload(AhaConfig loaded) {
        if (config == null) {
            config = loaded;
        }
    }

    /**
     * 模型供应商配置存取器（`Model.yml`）。
     *
     * @return 存取器
     */
    static synchronized ModelConfigStore modelStore() {
        if (modelStore == null) {
            modelStore = new ModelConfigStore(ConfigLoader.resolveModelPath(config()));
        }
        return modelStore;
    }

    /**
     * 获取（懒创建）Agent 服务。
     *
     * @return Agent 服务
     */
    static synchronized AgentService service() {
        if (service == null) {
            service = AgentServiceFactory.local(config());
        }
        return service;
    }

    /**
     * 获取（懒创建）Agent 服务，并注入工具授权裁决器。
     *
     * <p>服务已存在时仅更新裁决器，避免重复初始化存储与线程池。</p>
     *
     * @param approver 工具授权裁决器
     * @return Agent 服务
     */
    static synchronized AgentService service(ToolApprover approver) {
        if (service == null) {
            service = AgentServiceFactory.local(config(), approver);
        } else if (service instanceof LocalAgentService local) {
            local.setToolApprover(approver);
        }
        return service;
    }

    /**
     * 使缓存失效（模型配置变更后调用）。
     */
    static synchronized void invalidate() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        config = null;
        modelStore = null;
    }

    /**
     * 关闭资源。
     */
    static synchronized void shutdown() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
    }
}
