package com.acanx.module.aha.cli;

import com.acanx.module.aha.common.exception.AhaException;
import com.acanx.module.aha.common.exception.Exceptions;
import com.acanx.module.aha.core.config.ModelConfig;
import com.acanx.module.aha.core.config.ModelConfigStore;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.config.RateLimitConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * 供应商管理命令。
 *
 * <p>供应商明细存放于 {@code Model.yml}，与主配置 {@code Aha.yaml} 分离。
 * {@code use} 支持一键切换默认供应商。</p>
 *
 * @since 0.1.0
 */
@Command(
        name = "provider",
        description = "模型供应商管理",
        subcommands = {
                ProviderCommand.ListSub.class,
                ProviderCommand.TestSub.class,
                ProviderCommand.UseSub.class,
                ProviderCommand.AddSub.class,
                ProviderCommand.RemoveSub.class},
        mixinStandardHelpOptions = true)
public final class ProviderCommand implements Runnable {

    private static final Pattern PASCAL_CASE = Pattern.compile("^[A-Z][a-zA-Z0-9]*$");

    @Override
    public void run() {
        System.out.println("用法: aha provider [list|test|use|add|remove]");
    }

    private static ModelConfigStore store() {
        return CliContext.modelStore();
    }

    private static void printConfigFile(ModelConfigStore store) {
        System.out.printf("配置文件: %s%s%n", store.path(),
                store.exists() ? "" : "（尚未创建，当前使用内置默认）");
    }

    /**
     * 列出供应商。
     *
     * @since 0.1.0
     */
    @Command(name = "list", description = "列出供应商", mixinStandardHelpOptions = true)
    public static final class ListSub implements Callable<Integer> {
        @Override
        public Integer call() {
            ModelConfigStore store = store();
            ModelConfig model = store.load();
            String current = model.defaultProvider();
            printConfigFile(store);
            System.out.printf("%-16s %-20s %-24s %s%n", "NAME", "ADAPTER", "MODEL", "REMARK");
            model.providersOrEmpty().forEach((name, provider) -> System.out.printf(
                    "%-16s %-20s %-24s %s%n",
                    name, provider.adapter(), provider.model(),
                    name.equals(current) ? "<== 当前默认" : ""));
            if (!store.exists()) {
                System.out.println();
                System.out.println("提示：以上为内置默认。运行 aha init 生成配置文件，"
                        + "或设置 " + InitCommand.apiKeyPlaceholder(
                                current, model.providersOrEmpty().get(current))
                        + " 后直接使用。");
            }
            return 0;
        }
    }

    /**
     * 测试供应商配置。
     *
     * @since 0.1.0
     */
    @Command(name = "test", description = "查看供应商配置（不发起请求）", mixinStandardHelpOptions = true)
    public static final class TestSub implements Callable<Integer> {
        @Parameters(index = "0", description = "供应商 ID")
        String id;

        @Override
        public Integer call() {
            ProviderConfig provider = store().load().providersOrEmpty().get(id);
            if (provider == null) {
                System.err.println("[error] 未找到供应商: " + id);
                return 1;
            }
            boolean apiKeyConfigured = provider.apiKey() != null
                    && !provider.apiKey().isBlank()
                    && !com.acanx.module.aha.core.config.ConfigLoader
                            .isUnresolvedPlaceholder(provider.apiKey());
            System.out.printf("供应商: %s%n适配器: %s%nBaseUrl: %s%n模型: %s%nAPI Key: %s%n",
                    id, provider.adapter(), provider.baseUrl(), provider.model(),
                    apiKeyConfigured ? "已配置" : "未配置（请设置对应环境变量）");
            if (provider.apiKey() != null && provider.apiKey().startsWith("${")) {
                System.out.printf("所需环境变量: %s%n",
                        provider.apiKey().replace("${", "").replace("}", ""));
            }
            return 0;
        }
    }

    /**
     * 一键切换默认供应商。
     *
     * @since 0.1.0
     */
    @Command(name = "use", description = "切换默认供应商", mixinStandardHelpOptions = true)
    public static final class UseSub implements Callable<Integer> {
        @Parameters(index = "0", description = "供应商 ID")
        String id;

        @Override
        public Integer call() {
            ModelConfigStore store = store();
            try {
                ModelConfig next = store.setDefault(id);
                System.out.printf("已切换默认供应商: %s%n", next.defaultProvider());
                printConfigFile(store);
                CliContext.invalidate();
                return 0;
            } catch (AhaException e) {
                System.err.println("[error] " + Exceptions.message(e));
                System.err.println("提示：使用 aha provider list 查看可用供应商");
                return 1;
            }
        }
    }

    /**
     * 新增或覆盖供应商（保存到 Model.yml）。
     *
     * @since 0.1.0
     */
    @Command(name = "add", description = "新增/覆盖供应商（保存到 Model.yml）", mixinStandardHelpOptions = true)
    public static final class AddSub implements Callable<Integer> {

        @Parameters(index = "0", description = "供应商 ID（PascalCase，如 DeepSeek）")
        String id;

        @Option(names = "--adapter", required = true,
                description = "适配器: openai-compatible | anthropic | gemini")
        String adapter;

        @Option(names = "--base-url", required = true,
                description = "Base URL（如 https://api.deepseek.com/v1，无需包含 /chat/completions）")
        String baseUrl;

        @Option(names = "--model", required = true, description = "模型名")
        String model;

        @Option(names = "--api-key", description = "API Key，默认写入 ${AHA_API_KEY_<ID>} 占位")
        String apiKey;

        @Option(names = "--timeout", defaultValue = "120", description = "超时秒数")
        int timeoutSeconds;

        @Option(names = "--max-retries", defaultValue = "3", description = "最大重试次数")
        int maxRetries;

        @Option(names = "--rpm", defaultValue = "0", description = "每分钟请求上限，0 表示不限")
        int rpm;

        @Option(names = "--default", description = "同时设为默认供应商")
        boolean makeDefault;

        @Override
        public Integer call() {
            if (!PASCAL_CASE.matcher(id).matches()) {
                System.err.println("[error] 供应商 ID 必须为 PascalCase: " + id
                        + "（示例：DeepSeek、MyProxy）");
                return 1;
            }
            ModelConfigStore store = store();
            ProviderConfig provider = new ProviderConfig(
                    adapter,
                    baseUrl,
                    apiKey != null && !apiKey.isBlank() ? apiKey : existingOrDerivedPlaceholder(id),
                    model,
                    timeoutSeconds,
                    maxRetries,
                    new RateLimitConfig(rpm, 0),
                    Map.of());
            store.putProvider(id, provider);
            if (makeDefault) {
                store.setDefault(id);
            }
            System.out.printf("已保存供应商: %s%n", id);
            printConfigFile(store);
            System.out.printf("默认供应商: %s%n", store.load().defaultProvider());
            CliContext.invalidate();
            return 0;
        }

        /**
         * 未指定 {@code --api-key} 时的占位符。
         *
         * <p>覆盖内置供应商时沿用其原有占位符（如 {@code OpenAI} 的
         * {@code ${AHA_API_KEY_OPENAI}}）；从 ID 推导不可靠，仅在自定义供应商时使用。</p>
         */
        private static String existingOrDerivedPlaceholder(String id) {
            ProviderConfig builtin = com.acanx.module.aha.core.config.ProviderPresets
                    .builtin().get(id);
            if (builtin != null && builtin.apiKey() != null
                    && builtin.apiKey().startsWith("${") && builtin.apiKey().endsWith("}")) {
                return builtin.apiKey();
            }
            return InitCommand.deriveApiKeyPlaceholder(id);
        }
    }

    /**
     * 删除供应商。
     *
     * @since 0.1.0
     */
    @Command(name = "remove", description = "删除供应商", mixinStandardHelpOptions = true)
    public static final class RemoveSub implements Callable<Integer> {

        @Parameters(index = "0", description = "供应商 ID")
        String id;

        @Option(names = {"-y", "--yes"}, description = "确认删除")
        boolean confirmed;

        @Override
        public Integer call() {
            ModelConfigStore store = store();
            if (!store.load().providersOrEmpty().containsKey(id)) {
                System.err.println("[error] 未找到供应商: " + id);
                return 1;
            }
            if (!confirmed) {
                System.err.println("[error] 删除供应商 " + id + " 需显式确认");
                System.err.println("提示：使用 aha provider remove " + id + " -y 确认删除");
                return 1;
            }
            ModelConfig next = store.removeProvider(id);
            System.out.printf("已删除供应商: %s%n", id);
            printConfigFile(store);
            System.out.printf("默认供应商: %s%n",
                    next.defaultProvider() == null ? "（未设置）" : next.defaultProvider());
            CliContext.invalidate();
            return 0;
        }
    }
}
