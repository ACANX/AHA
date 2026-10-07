package com.acanx.module.aha.cli;

import com.acanx.module.aha.cli.tty.OutputEncoding;
import com.acanx.module.aha.common.exception.Exceptions;
import com.acanx.module.aha.core.config.ModelConfig;
import com.acanx.module.aha.core.config.ModelConfigStore;
import com.acanx.module.aha.core.config.ProviderConfig;
import com.acanx.module.aha.core.config.ProviderPresets;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.Console;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 初始化命令：生成模型配置与目录结构。
 *
 * <p>首次使用 AHA 时执行一次，避免手工编写 {@code Model.yml}。</p>
 *
 * <p>交互模式下会列出内置供应商供选择，并可选择写入 API Key；
 * 非交互模式（{@code --no-input} 或无控制台）直接生成内置全量配置。</p>
 *
 * @since 0.1.0
 */
@Command(name = "init", description = "初始化配置（生成 Model.yml 与目录结构）", mixinStandardHelpOptions = true)
public final class InitCommand implements Callable<Integer> {

    private static final String API_KEY_PLACEHOLDER_PREFIX = "${AHA_API_KEY_";
    private static final String API_KEY_PLACEHOLDER_SUFFIX = "}";

    @Option(names = "--provider", description = "默认供应商 ID（内置键名，如 DeepSeek）")
    String provider;

    @Option(names = "--api-key", description = "API Key；不提供则写入环境变量占位")
    String apiKey;

    @Option(names = "--no-input", description = "非交互模式，直接生成内置全量配置")
    boolean noInput;

    @Option(names = "--force", description = "覆盖已存在的配置")
    boolean force;

    @Override
    public Integer call() {
        ModelConfigStore store = CliContext.modelStore();
        Path modelFile = store.path();
        Path home = modelFile.getParent();

        System.out.println("AHA 初始化");
        System.out.println();
        System.out.println("  配置目录  " + home);
        System.out.println("  模型配置  " + modelFile);
        System.out.println();

        if (store.exists() && !force) {
            System.out.println("配置已存在，无需初始化。");
            System.out.println("如需重建，请加 --force。");
            System.out.println();
            printNextSteps(store.load().defaultProvider());
            return 0;
        }

        Map<String, ProviderConfig> providers = new LinkedHashMap<>(ProviderPresets.builtin());
        ModelConfig builtin = ProviderPresets.builtinModel();
        String selected = provider;
        String key = apiKey;

        Console console = noInput ? null : System.console();
        if (console != null) {
            selected = promptProvider(console, providers, builtin.defaultProvider());
            key = promptApiKey(console, selected, providers.get(selected));
        }

        if (selected == null || selected.isBlank()) {
            selected = builtin.defaultProvider();
        }
        if (!providers.containsKey(selected)) {
            System.err.println("[error] 未知供应商: " + selected);
            System.err.println("可用供应商: " + String.join("、", providers.keySet()));
            return 1;
        }

        if (key != null && !key.isBlank()) {
            providers.put(selected, withApiKey(providers.get(selected), key));
        }

        try {
            createDirectories(home);
            store.save(new ModelConfig(selected, providers));
            restrictPermissions(modelFile);
        } catch (IOException e) {
            System.err.println("[error] 初始化失败: " + Exceptions.message(e));
            return 1;
        }

        System.out.println();
        // 控制台编码装不下 ✓ 时降级为 [ok]，避免显示成 '?'
        String mark = OutputEncoding.detect().check();
        System.out.println(mark + " 已生成 " + modelFile);
        System.out.println(mark + " 默认供应商: " + selected);
        if (key == null || key.isBlank()) {
            System.out.println("  提示：API Key 使用环境变量占位，请设置 "
                    + apiKeyPlaceholder(selected, providers.get(selected)) + " 后生效");
        }
        printNextSteps(selected);
        return 0;
    }

    /**
     * 列出内置供应商并让用户选择默认项。
     */
    private String promptProvider(Console console, Map<String, ProviderConfig> providers,
                                  String fallback) {
        System.out.println("可用供应商（内置预设）：");
        System.out.println();
        List<String> ids = new ArrayList<>(providers.keySet());
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            ProviderConfig p = providers.get(id);
            System.out.printf("  %d) %-18s %-22s %s%n",
                    i + 1, id, p.model() == null ? "-" : p.model(), apiKeyPlaceholder(id, p));
        }
        System.out.println();
        System.out.printf("请选择默认供应商 [1-%d]（直接回车使用 %s）: ", ids.size(), fallback);
        String input = readLine(console);
        if (input == null || input.isBlank()) {
            return fallback;
        }
        // 支持序号或直接输入键名
        try {
            int index = Integer.parseInt(input.trim());
            if (index >= 1 && index <= ids.size()) {
                return ids.get(index - 1);
            }
            System.out.println("序号超出范围，将使用 " + fallback);
            return fallback;
        } catch (NumberFormatException e) {
            if (providers.containsKey(input.trim())) {
                return input.trim();
            }
            System.out.println("未识别的输入，将使用 " + fallback);
            return fallback;
        }
    }

    /**
     * 询问是否写入 API Key；直接回车则保留环境变量占位。
     */
    private String promptApiKey(Console console, String providerId, ProviderConfig provider) {
        if (providerId == null || provider == null) {
            return null;
        }
        System.out.println();
        System.out.println("是否为 " + providerId + " 写入 API Key？");
        System.out.println("（直接回车则使用环境变量 "
                + apiKeyPlaceholder(providerId, provider) + "）");
        System.out.print("API Key: ");
        char[] input = console.readPassword();
        if (input == null || input.length == 0) {
            return null;
        }
        String value = new String(input).trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * 读取一行输入；无控制台时返回 {@code null}。
     */
    private String readLine(Console console) {
        return console.readLine();
    }

    /**
     * 创建默认目录结构。
     */
    private void createDirectories(Path home) throws IOException {
        if (home == null) {
            return;
        }
        Files.createDirectories(home);
        Files.createDirectories(home.resolve("Data"));
        Files.createDirectories(home.resolve("Key"));
        Files.createDirectories(home.resolve("Log"));
        Files.createDirectories(home.resolve("Extension"));
    }

    /**
     * 将密钥文件的权限收敛为 {@code rw-------}（POSIX 平台）。
     *
     * <p>Windows 不支持 POSIX 权限，静默跳过。</p>
     */
    private void restrictPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // 非 POSIX 平台，忽略
        }
    }

    private static ProviderConfig withApiKey(ProviderConfig provider, String key) {
        return new ProviderConfig(
                provider.adapter(),
                provider.baseUrl(),
                key,
                provider.model(),
                provider.timeoutSeconds(),
                provider.maxRetries(),
                provider.rateLimit(),
                provider.extra());
    }

    /**
     * 供应商对应的 API Key 环境变量占位符。
     *
     * <p>优先沿用供应商配置中<b>已有的</b>占位符：按 ID 推导环境变量名并不可靠。
     * 例如 {@code OpenAI} 的预设变量是 {@code AHA_API_KEY_OPENAI}，
     * 而推导会得到 {@code AHA_API_KEY_OPEN_AI}（驼峰切词把 {@code AI} 拆开）。
     * 仅当配置中无占位符（自定义供应商）时才按 ID 推导。</p>
     *
     * @param providerId 供应商 ID
     * @param provider   供应商配置，可为 {@code null}
     * @return 环境变量占位符
     */
    static String apiKeyPlaceholder(String providerId, ProviderConfig provider) {
        if (provider != null && provider.apiKey() != null
                && provider.apiKey().startsWith("${") && provider.apiKey().endsWith("}")) {
            return provider.apiKey();
        }
        return deriveApiKeyPlaceholder(providerId);
    }

    /**
     * 按供应商 ID 推导 API Key 环境变量占位符。
     *
     * @param providerId 供应商 ID
     * @return 推导出的占位符
     */
    static String deriveApiKeyPlaceholder(String providerId) {
        if (providerId == null) {
            return "";
        }
        String env = providerId.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toUpperCase(Locale.ROOT);
        return API_KEY_PLACEHOLDER_PREFIX + env + API_KEY_PLACEHOLDER_SUFFIX;
    }

    private void printNextSteps(String defaultProvider) {
        String providerCmd = "aha provider test " + defaultProvider;
        System.out.println();
        System.out.println("下一步：");
        System.out.printf("  %-34s %s%n", providerCmd, "# 查看配置与所需环境变量");
        System.out.printf("  %-34s %s%n", "aha provider list", "# 列出全部供应商");
        System.out.printf("  %-34s %s%n", "aha run \"你好\"", "# 单次推理");
        System.out.printf("  %-34s %s%n", "aha chat", "# 交互式对话");
    }
}
