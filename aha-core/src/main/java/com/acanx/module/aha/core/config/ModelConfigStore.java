package com.acanx.module.aha.core.config;

import com.acanx.module.aha.common.exception.ConfigException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 模型供应商配置存取器（{@code Model.yml}）。
 *
 * <p>负责读写用户级模型配置，并支持一键切换默认供应商。文件不存在时以
 * classpath 的 {@code ModelDefault.yml} 作为初始内容，首次写入即落盘。</p>
 *
 * @since 0.1.0
 */
public final class ModelConfigStore {

    private final Path path;

    /**
     * 构造存取器。
     *
     * @param path 模型配置文件路径
     */
    public ModelConfigStore(Path path) {
        if (path == null) {
            throw new ConfigException("MODEL_CONFIG_PATH_REQUIRED", "模型配置文件路径不能为空");
        }
        this.path = path;
    }

    /**
     * 配置路径。
     *
     * @return 路径
     */
    public Path path() {
        return path;
    }

    /**
     * 文件是否已存在。
     *
     * @return 是否存在
     */
    public boolean exists() {
        return Files.exists(path);
    }

    /**
     * 读取配置；文件不存在或为空时返回内置默认。
     *
     * @return 模型配置
     */
    public ModelConfig load() {
        ModelConfig config = ConfigLoader.loadModel(path);
        if (config == null || config.providers() == null || config.providers().isEmpty()) {
            ModelConfig builtin = ConfigLoader.loadModelDefault();
            if (config != null && config.defaultProvider() != null) {
                return builtin.withDefault(config.defaultProvider());
            }
            return builtin;
        }
        return config;
    }

    /**
     * 保存配置（自动创建父目录）。
     *
     * @param config 模型配置
     */
    public void save(ModelConfig config) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, ConfigLoader.toYaml(config));
        } catch (IOException e) {
            throw new ConfigException("MODEL_CONFIG_SAVE_FAILED",
                    "无法保存模型配置: " + path, e);
        }
    }

    /**
     * 一键切换默认（选中）供应商。
     *
     * @param providerId 供应商键名
     * @return 切换后的配置
     */
    public ModelConfig setDefault(String providerId) {
        ModelConfig current = load();
        if (!current.providersOrEmpty().containsKey(providerId)) {
            throw new ConfigException("PROVIDER_NOT_FOUND", "未找到供应商: " + providerId);
        }
        ModelConfig next = current.withDefault(providerId);
        save(next);
        return next;
    }

    /**
     * 设置全局默认档（写入 {@code Model.DefaultTier}）。
     *
     * @param tier 档位
     * @return 更新后的配置
     */
    public ModelConfig setGlobalDefaultTier(ModelTier tier) {
        ModelConfig next = load().withDefaultTier(tier);
        save(next);
        return next;
    }

    /**
     * 把供应商的默认档设为指定档位，并把 {@code Model} 同步为该档模型。
     *
     * <p>写盘约定：{@code Model} 与 {@code DefaultTier} 保持一致，这样旧客户端
     * 读 {@code Model} 也拿到同一个模型。</p>
     *
     * @param providerId 供应商键名
     * @param tier       目标档位
     * @return 更新后的配置
     */
    public ModelConfig useTier(String providerId, ModelTier tier) {
        ModelConfig current = load();
        ProviderConfig provider = current.providersOrEmpty().get(providerId);
        if (provider == null) {
            throw new ConfigException("PROVIDER_NOT_FOUND", "未找到供应商: " + providerId);
        }
        if (provider.modelFor(tier) == null) {
            throw new ConfigException("MODEL_TIER_NOT_CONFIGURED",
                    "供应商 " + providerId + " 未配置 " + tier.configName() + " 档");
        }
        ModelConfig next = current.withProvider(providerId, provider.withDefaultTier(tier));
        save(next);
        return next;
    }

    /**
     * 按模型名反查所属档位并设为默认（仅限五档内的模型名）。
     *
     * @param providerId 供应商键名
     * @param modelName  模型名
     * @return 更新后的配置
     */
    public ModelConfig useModelName(String providerId, String modelName) {
        ModelConfig current = load();
        ProviderConfig provider = current.providersOrEmpty().get(providerId);
        if (provider == null) {
            throw new ConfigException("PROVIDER_NOT_FOUND", "未找到供应商: " + providerId);
        }
        ModelTier tier = provider.tierOf(modelName)
                .orElseThrow(() -> new ConfigException("MODEL_NOT_IN_TIERS",
                        "模型 " + modelName + " 不在供应商 " + providerId + " 的五档内"));
        return useTier(providerId, tier);
    }

    /**
     * 新增或覆盖供应商。
     *
     * @param providerId 供应商键名
     * @param provider   供应商配置
     * @return 更新后的配置
     */
    public ModelConfig putProvider(String providerId, ProviderConfig provider) {
        ModelConfig next = load().withProvider(providerId, provider);
        save(next);
        return next;
    }

    /**
     * 删除供应商；若其为默认供应商，则默认值清空。
     *
     * @param providerId 供应商键名
     * @return 更新后的配置
     */
    public ModelConfig removeProvider(String providerId) {
        ModelConfig next = load().withoutProvider(providerId);
        save(next);
        return next;
    }
}
