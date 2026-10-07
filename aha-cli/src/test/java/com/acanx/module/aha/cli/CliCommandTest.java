package com.acanx.module.aha.cli;

import com.acanx.module.aha.core.config.ModelConfig;
import com.acanx.module.aha.core.config.ProviderPresets;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CLI 命令测试（picocli 解析 + 执行 + 输出）。
 *
 * <p>仅覆盖不触发真实网络调用的命令；SQLite 数据落在临时
 * {@code user.home} 下，避免污染真实用户目录。</p>
 *
 * @since 0.1.0
 */
class CliCommandTest {

    @TempDir
    static Path home;

    private static String originalUserHome;

    private CommandLine cmd;
    private ByteArrayOutputStream stdout;
    private ByteArrayOutputStream stderr;
    private PrintStream originalOut;
    private PrintStream originalErr;

    @BeforeAll
    static void redirectHome() {
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", home.toString());
        // 模型配置与数据库路径均基于 AHA_HOME（见 AhaDefault.yaml）
        System.setProperty("AHA_HOME", home.toString());
    }

    @AfterAll
    static void restoreHome() {
        CliContext.shutdown();
        if (originalUserHome != null) {
            System.setProperty("user.home", originalUserHome);
            System.clearProperty("AHA_HOME");
        }
    }

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        originalErr = System.err;
        stdout = new ByteArrayOutputStream();
        stderr = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdout, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(stderr, true, StandardCharsets.UTF_8));
        cmd = new CommandLine(new AhaCli());
        cmd.setOut(new PrintWriter(new OutputStreamWriter(stdout, StandardCharsets.UTF_8), true));
        cmd.setErr(new PrintWriter(new OutputStreamWriter(stderr, StandardCharsets.UTF_8), true));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    private String out() {
        return stdout.toString(StandardCharsets.UTF_8);
    }

    /** 清空输出缓冲，供同一用例内多次执行命令时隔离断言。 */
    private void resetOutput() {
        stdout.reset();
        stderr.reset();
    }

    private String err() {
        return stderr.toString(StandardCharsets.UTF_8);
    }

    @Test
    void versionCommandPrintsVersion() {
        int code = cmd.execute("version");

        assertThat(code).isZero();
        // 不断言具体版本号：它由 Maven 注入，改 pom.xml 不该弄坏测试；
        // 只断言格式与「确实注入了」
        assertThat(out()).contains("AHA ").contains(AppVersion.DISPLAY);
    }

    @Test
    void versionOptionMatchesCommand() {
        int code = cmd.execute("--version");

        assertThat(code).isZero();
        // -V 与 version 子命令必须一致（此前两处各自硬编码，容易不同步）
        assertThat(out()).contains(AppVersion.DISPLAY);
    }

    @Test
    void helpListsAllCommands() {
        int code = cmd.execute("--help");

        assertThat(code).isZero();
        assertThat(out())
                .contains("init")
                .contains("chat")
                .contains("run")
                .contains("tool")
                .contains("provider")
                .contains("extension")
                .contains("config")
                .contains("version");
    }

    @Test
    void helpIsAvailableAtEveryCommandLevel() {
        // 任意层级的子命令都应支持 -h/--help 与 -V/--version。
        // 早期只在顶层 AhaCli 设置了 mixinStandardHelpOptions，
        // 导致 `aha chat --help` 报 “Unknown option: '--help'”。
        List<List<String>> cases = List.of(
                List.of("init"), List.of("chat"), List.of("run"), List.of("version"),
                List.of("tool", "list"), List.of("tool", "invoke"),
                List.of("provider", "list"), List.of("provider", "use"),
                List.of("provider", "add"), List.of("provider", "remove"),
                List.of("provider", "test"),
                List.of("extension", "list"), List.of("extension", "enable"),
                List.of("extension", "disable"), List.of("extension", "info"),
                List.of("config", "get"), List.of("config", "set"),
                List.of("config", "edit"));

        for (List<String> path : cases) {
            for (String flag : List.of("--help", "-h")) {
                List<String> args = new java.util.ArrayList<>(path);
                args.add(flag);
                String label = "aha " + String.join(" ", args);

                resetOutput();
                int code = cmd.execute(args.toArray(String[]::new));

                assertThat(code).as(label).isZero();
                assertThat(out()).as(label).contains("Usage: aha");
            }
        }
    }

    @Test
    void noArgsWithoutConsolePrintsUsage() {
        // 非交互环境（无控制台）下，无参数不应进入对话而挂起，而应打印帮助
        int code = cmd.execute();

        assertThat(code).isZero();
        assertThat(out()).contains("Usage: aha").contains("Commands:");
    }

    @Test
    void helpMentionsDefaultInteractiveBehavior() {
        cmd.execute("--help");

        assertThat(out()).contains("无参数时进入交互式对话");
    }

    @Test
    void providerListShowsBuiltinProviders() {
        int code = cmd.execute("provider", "list");

        assertThat(code).isZero();
        assertThat(out())
                .contains("DeepSeek")
                .contains("BigModelCN")
                .contains("Qwen")
                .contains("OpenAI")
                .contains("Anthropic")
                .contains("Gemini")
                .contains("当前默认");
    }

    @Test
    void providerTestPrintsConfiguration() {
        int code = cmd.execute("provider", "test", "OpenAI");

        assertThat(code).isZero();
        assertThat(out())
                .contains("OpenAI")
                .contains("BaseUrl")
                .contains("适配器");
    }

    @Test
    void providerTestUnknownFails() {
        int code = cmd.execute("provider", "test", "NoSuchProvider");

        assertThat(code).isEqualTo(1);
        assertThat(err()).contains("未找到供应商");
    }

    @Test
    void configGetResolvesKnownKeys() {
        assertThat(cmd.execute("config", "get", "Llm.DefaultProvider")).isZero();
        assertThat(out()).contains("OpenAI");

        stdout.reset();
        assertThat(cmd.execute("config", "get", "Memory.Storage")).isZero();
        assertThat(out()).contains("sqlite");

        stdout.reset();
        assertThat(cmd.execute("config", "get", "Extension.Enabled")).isZero();
        assertThat(out()).contains("true");
    }

    @Test
    void configGetUnknownKeyReportsUnknown() {
        int code = cmd.execute("config", "get", "Unknown.Key");

        assertThat(code).isZero();
        assertThat(out()).contains("未知配置键");
    }

    @Test
    void configEditPrintsProjectFile() {
        int code = cmd.execute("config", "edit");

        assertThat(code).isZero();
        assertThat(out()).contains(CliContext.PROJECT_CONFIG);
    }

    @Test
    void extensionCommandsReportNotAvailable() {
        int code = cmd.execute("extension", "list");

        assertThat(code).isZero();
        assertThat(out()).contains("0.3");
    }

    @Test
    void toolListShowsPermissions() {
        int code = cmd.execute("tool", "list");

        assertThat(code).isZero();
        assertThat(out())
                .contains("file-read").contains("READ").contains("yes")
                .contains("shell-exec").contains("EXECUTE").contains("no");
    }

    @Test
    void toolInvokeDeniesHighRiskWithoutConfirmation() {
        int code = cmd.execute("tool", "invoke", "shell-exec", "-p", "command=echo hi");

        assertThat(code).isEqualTo(1);
        assertThat(err()).contains("未获授权").contains("-y");
    }

    @Test
    void toolInvokeAllowsReadOnlyTool() throws Exception {
        Path file = home.resolve("cli-read.txt");
        java.nio.file.Files.writeString(file, "cli-content");

        int code = cmd.execute("tool", "invoke", "file-read", "-p", "path=" + file);

        assertThat(code).isZero();
        assertThat(out()).contains("cli-content");
    }

    @Test
    void configSetPrintsHint() {
        int code = cmd.execute("config", "set", "Llm.DefaultProvider", "DeepSeek");

        assertThat(code).isZero();
        assertThat(out()).contains("Llm.DefaultProvider").contains("DeepSeek");
    }

    @Test
    void extensionSubcommandsReportNotAvailable() {
        for (String sub : List.of("enable", "disable", "info")) {
            stdout.reset();

            assertThat(cmd.execute("extension", sub)).isZero();
            assertThat(out()).contains("0.3");
        }
    }

    @Test
    void groupCommandsPrintUsageWhenNoSubcommand() {
        for (String group : List.of("tool", "provider", "config", "extension")) {
            stdout.reset();

            assertThat(cmd.execute(group)).isZero();
            assertThat(out()).contains("用法");
        }
    }

    @Test
    void rootCommandPrintsUsage() {
        int code = cmd.execute();

        assertThat(code).isZero();
        assertThat(out()).contains("AHA");
    }

    @Test
    void providerUseSwitchesDefault() {
        int code = cmd.execute("provider", "use", "DeepSeek");

        assertThat(code).isZero();
        assertThat(out()).contains("已切换默认供应商: DeepSeek");
        assertThat(CliContext.modelStore().load().defaultProvider()).isEqualTo("DeepSeek");
        // 复原，避免影响其他用例
        cmd.execute("provider", "use", "OpenAI");
    }

    @Test
    void providerUseUnknownFails() {
        int code = cmd.execute("provider", "use", "NoSuchProvider");

        assertThat(code).isEqualTo(1);
        assertThat(err()).contains("未找到供应商");
    }

    @Test
    void providerAddSavesToModelFileAndCanBeDefault() {
        int code = cmd.execute("provider", "add", "MyProxy",
                "--adapter", "openai-compatible",
                "--base-url", "https://proxy.example/v1",
                "--model", "gpt-4o-mini",
                "--default");

        assertThat(code).isZero();
        assertThat(out()).contains("已保存供应商: MyProxy");

        ModelConfig model = CliContext.modelStore().load();
        assertThat(model.providersOrEmpty()).containsKey("MyProxy");
        assertThat(model.defaultProvider()).isEqualTo("MyProxy");
        assertThat(model.providersOrEmpty().get("MyProxy").apiKey())
                .isEqualTo("${AHA_API_KEY_MY_PROXY}");

        cmd.execute("provider", "remove", "MyProxy", "-y");
        cmd.execute("provider", "use", "OpenAI");
    }

    @Test
    void providerAddRejectsNonPascalCaseId() {
        int code = cmd.execute("provider", "add", "my_proxy",
                "--adapter", "openai-compatible",
                "--base-url", "https://x/v1",
                "--model", "m");

        assertThat(code).isEqualTo(1);
        assertThat(err()).contains("PascalCase");
    }

    @Test
    void providerRemoveRequiresConfirmation() {
        cmd.execute("provider", "add", "TempX",
                "--adapter", "openai-compatible",
                "--base-url", "https://x/v1",
                "--model", "m");
        stdout.reset();

        int code = cmd.execute("provider", "remove", "TempX");

        assertThat(code).isEqualTo(1);
        assertThat(err()).contains("-y");
        cmd.execute("provider", "remove", "TempX", "-y");
    }

    // ---------- init ----------

    @Test
    void initNoInputGeneratesModelFile() {
        int code = cmd.execute("init", "--no-input", "--force");

        assertThat(code).isZero();
        assertThat(home.resolve("Model.yml")).exists();
        assertThat(out()).contains("已生成").contains("默认供应商");
    }

    @Test
    void initCreatesDirectoryLayout() {
        cmd.execute("init", "--no-input", "--force");

        assertThat(home.resolve("Data")).isDirectory();
        // 目录名：单数 + 大驼峰（与 YAML 字段风格一致）
        assertThat(home.resolve("Key")).isDirectory();
        assertThat(home.resolve("Log")).isDirectory();
        assertThat(home.resolve("Extension")).isDirectory();
    }

    @Test
    void initWithProviderSetsDefault() {
        int code = cmd.execute("init", "--provider", "DeepSeek", "--force");

        assertThat(code).isZero();
        assertThat(out()).contains("默认供应商: DeepSeek");
        assertThat(out()).contains("AHA_API_KEY_DEEPSEEK");
    }

    @Test
    void initWithApiKeyWritesPlainTextKey() throws Exception {
        cmd.execute("init", "--provider", "DeepSeek", "--api-key", "sk-test", "--force");

        String yaml = Files.readString(home.resolve("Model.yml"));
        assertThat(yaml).contains("sk-test");
        assertThat(out()).doesNotContain("环境变量占位");
    }

    @Test
    void initIsIdempotentWithoutForce() {
        cmd.execute("init", "--no-input", "--force");
        stdout.reset();

        int code = cmd.execute("init", "--no-input");

        assertThat(code).isZero();
        assertThat(out()).contains("配置已存在").contains("--force");
    }

    @Test
    void initUnknownProviderFails() {
        int code = cmd.execute("init", "--provider", "NoSuchProvider", "--force");

        assertThat(code).isEqualTo(1);
        assertThat(err()).contains("未知供应商");
    }

    @Test
    void apiKeyPlaceholderReusesProviderPlaceholder() {
        // 回归：从显示名推导环境变量名不可靠，必须沿用配置中已有的占位符
        assertThat(InitCommand.apiKeyPlaceholder("OpenAI",
                ProviderPresets.builtin().get("OpenAI")))
                .isEqualTo("${AHA_API_KEY_OPENAI}");
        assertThat(InitCommand.apiKeyPlaceholder("DeepSeek",
                ProviderPresets.builtin().get("DeepSeek")))
                .isEqualTo("${AHA_API_KEY_DEEPSEEK}");
        // 无占位符时（自定义供应商）回退到推导
        assertThat(InitCommand.apiKeyPlaceholder("MyProxy", null))
                .isEqualTo("${AHA_API_KEY_MY_PROXY}");
    }

    @Test
    void deriveApiKeyPlaceholderHandlesCamelCase() {
        assertThat(InitCommand.deriveApiKeyPlaceholder("MyProxy"))
                .isEqualTo("${AHA_API_KEY_MY_PROXY}");
        assertThat(InitCommand.deriveApiKeyPlaceholder("BigModelCN"))
                .isEqualTo("${AHA_API_KEY_BIG_MODEL_CN}");
    }
}
