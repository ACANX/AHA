package com.acanx.module.aha.cli;

import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.common.exception.Exceptions;
import com.acanx.module.aha.core.config.ModelConfig;
import com.acanx.module.aha.core.config.ModelConfigStore;
import com.acanx.module.aha.core.config.ModelTier;
import com.acanx.module.aha.core.config.ProviderConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * 模型档位与默认模型管理命令（{@code aha model}）。
 *
 * <p>{@code use <档位>} 写 {@code Providers.<Id>.DefaultTier} 并把 {@code Model}
 * 同步为该档模型；{@code use <模型名>} 仅接受五档内的模型名，反查所属档后同步。
 * 写盘后 {@code Model} 与 {@code DefaultTier} 保持一致。</p>
 *
 * @since 0.1.0
 */
@Command(
        name = "model",
        description = "模型档位与默认模型管理",
        subcommands = {
                ModelCommand.UseSub.class,
                ModelCommand.ShowSub.class},
        mixinStandardHelpOptions = true)
public final class ModelCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("用法: aha model [use|show]");
    }

    /**
     * 切换默认档位或默认模型。
     *
     * @since 0.1.0
     */
    @Command(name = "use", description = "设置默认档位或默认模型（写入 Model.yml）",
            mixinStandardHelpOptions = true)
    public static final class UseSub implements Callable<Integer> {

        @Parameters(index = "0",
                description = "档位（Ultra/Pro/Standard/Flash/Fallback，大小写敏感）或五档内模型名")
        String target;

        @Override
        public Integer call() {
            ModelConfigStore store = CliContext.modelStore();
            ModelConfig current = store.load();
            String providerId = current.defaultProvider();
            if (providerId == null || providerId.isBlank()) {
                System.err.println("[error] 未设置默认供应商，请先执行 aha provider use <Id>");
                return 1;
            }
            // 档位名精确匹配（大小写敏感）；其余一律当模型名（限五档内）
            Optional<ModelTier> tier = ModelTier.fromConfigName(target);
            try {
                ModelConfig next = tier.isPresent()
                        ? store.useTier(providerId, tier.get())
                        : store.useModelName(providerId, target);
                ProviderConfig provider = next.providersOrEmpty().get(providerId);
                ModelTier effective = provider.effectiveTier(next.defaultTier());
                System.out.printf("默认供应商: %s%n默认档位: %s%n默认模型: %s%n",
                        providerId, effective.configName(),
                        provider.effectiveModel(next.defaultTier()));
                return 0;
            } catch (AhaException e) {
                System.err.println("[error] " + Exceptions.message(e));
                System.err.println("提示：使用 aha model show 查看该供应商已配置的档位");
                return 1;
            }
        }
    }

    /**
     * 查看默认档位、默认模型与五档明细。
     *
     * @since 0.1.0
     */
    @Command(name = "show", description = "查看默认档位、默认模型与各档模型",
            mixinStandardHelpOptions = true)
    public static final class ShowSub implements Callable<Integer> {

        @Parameters(index = "0", arity = "0..1", description = "供应商 ID（缺省取当前默认供应商）")
        String id;

        @Override
        public Integer call() {
            ModelConfigStore store = CliContext.modelStore();
            ModelConfig model = store.load();
            String providerId = id == null || id.isBlank() ? model.defaultProvider() : id;
            if (providerId == null || providerId.isBlank()) {
                System.err.println("[error] 未指定供应商，且未设置默认供应商");
                return 1;
            }
            ProviderConfig provider = model.providersOrEmpty().get(providerId);
            if (provider == null) {
                System.err.println("[error] 未找到供应商: " + providerId);
                return 1;
            }
            System.out.printf("配置文件: %s%s%n", store.path(),
                    store.exists() ? "" : "（尚未创建，当前使用内置默认）");
            System.out.printf("供应商: %s%n", providerId);
            System.out.printf("适配器: %s%n", provider.adapter());
            boolean legacy = !provider.hasModels();
            System.out.printf("规则: %s%n", legacy ? "老规则（仅 Model，Model 生效）" : "新规则（Models 档位，DefaultTier 优先）");
            System.out.printf("默认档位: %s%n", provider.effectiveTier(model.defaultTier()).configName());
            System.out.printf("默认模型: %s%n", provider.effectiveModel(model.defaultTier()));
            if (legacy) {
                System.out.println("（老配置未配置档位表，Models 各档均按 Model 处理）");
                return 0;
            }
            System.out.println();
            System.out.printf("%-12s %s%n", "档位", "模型");
            for (ModelTier tier : ModelTier.strongestFirst()) {
                String configured = provider.configuredModel(tier);
                System.out.printf("%-12s %s%n", tier.configName(),
                        configured == null ? "（未配置）" : configured);
            }
            return 0;
        }
    }
}
