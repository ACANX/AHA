package com.acanx.module.aha.tool;

import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.common.tool.ToolPermission;
import com.acanx.module.aha.common.tool.ToolSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 内置工具测试。
 *
 * @since 0.1.0
 */
class BuiltinToolsTest {

    @TempDir
    Path tempDir;

    @Test
    void readToolReturnsFileContent() throws Exception {
        Path file = tempDir.resolve("a.txt");
        Files.writeString(file, "hello");

        ToolResult result = new FileReadTool().execute(
                Map.of("path", file.toString()), new CancellationToken());

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("hello");
    }

    @Test
    void readToolFailsOnMissingFile() {
        ToolResult result = new FileReadTool().execute(
                Map.of("path", tempDir.resolve("nope.txt").toString()), new CancellationToken());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("读取失败");
    }

    @Test
    void readToolRequiresPath() {
        ToolResult result = new FileReadTool().execute(Map.of(), new CancellationToken());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("path");
    }

    @Test
    void writeToolWritesContent() throws Exception {
        Path file = tempDir.resolve("out.txt");

        ToolResult result = new FileWriteTool().execute(
                Map.of("path", file.toString(), "content", "written"), new CancellationToken());

        assertThat(result.success()).isTrue();
        assertThat(Files.readString(file)).isEqualTo("written");
    }

    @Test
    void writeToolRequiresBothParams() {
        assertThat(new FileWriteTool().execute(Map.of("path", "x"), new CancellationToken()).success())
                .isFalse();
        assertThat(new FileWriteTool().execute(Map.of("content", "x"), new CancellationToken()).success())
                .isFalse();
    }

    @Test
    void listToolListsDirectory() throws Exception {
        Files.writeString(tempDir.resolve("one.txt"), "1");
        Files.writeString(tempDir.resolve("two.txt"), "2");

        ToolResult result = new FileListTool().execute(
                Map.of("path", tempDir.toString()), new CancellationToken());

        assertThat(result.success()).isTrue();
        @SuppressWarnings("unchecked")
        List<String> entries = (List<String>) result.output();
        assertThat(entries).hasSize(2);
        assertThat(entries).anyMatch(p -> p.endsWith("one.txt"));
    }

    @Test
    void listToolFailsOnMissingDirectory() {
        ToolResult result = new FileListTool().execute(
                Map.of("path", tempDir.resolve("nope").toString()), new CancellationToken());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("列举失败");
    }

    @Test
    void httpGetToolValidatesUrl() {
        assertThat(new HttpGetTool().execute(Map.of(), new CancellationToken()).success()).isFalse();
        assertThat(new HttpGetTool().execute(
                Map.of("url", "http://127.0.0.1:1/unreachable"), new CancellationToken()).success())
                .isFalse();
    }

    @Test
    void shellExecRunsCommand() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String command = windows ? "echo hello" : "echo hello";

        ToolResult result = new ShellExecTool().execute(Map.of("command", command), new CancellationToken());

        assertThat(result.success()).isTrue();
        assertThat(String.valueOf(result.output()).trim()).isEqualTo("hello");
    }

    @Test
    void shellExecRequiresCommand() {
        assertThat(new ShellExecTool().execute(Map.of(), new CancellationToken()).success()).isFalse();
    }

    @Test
    void shellExecTerminatesOnTimeout() {
        // 回归：此前无超时，`process.waitFor()` 无限等待，
        // 一条卡住的命令会挂死整个对话循环
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String command = windows ? "ping -n 30 127.0.0.1" : "sleep 30";

        long start = System.nanoTime();
        ToolResult result = new ShellExecTool(1).execute(Map.of("command", command), new CancellationToken());
        long elapsedSeconds = java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("超时");
        // 1 秒超时 + 进程清理，给足余量但必须远小于命令自身时长
        assertThat(elapsedSeconds).isLessThan(10L);
    }

    @Test
    void shellExecTerminatesOnCancellation() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String command = windows ? "ping -n 30 127.0.0.1" : "sleep 30";
        CancellationToken token = new CancellationToken();
        Thread.ofVirtual().start(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            token.cancel();
        });

        long start = System.nanoTime();
        ToolResult result = new ShellExecTool(30).execute(Map.of("command", command), token);
        long elapsedSeconds = java.util.concurrent.TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("取消");
        // 取消应在数十毫秒内生效，不应等到 30 秒超时
        assertThat(elapsedSeconds).isLessThan(10L);
    }

    @Test
    void shellExecDescriptionMentionsTimeout() {
        assertThat(new ShellExecTool().description()).contains("超时");
    }

    @Test
    void shellExecRejectsCommandOutsideAllowList() {
        // 回归：Tools.Shell.AllowedCommands 此前从未生效
        ShellExecTool tool = new ShellExecTool();
        tool.configure(ToolSettings.of(Map.of("Shell.AllowedCommands", List.of("echo"))));

        ToolResult result = tool.execute(Map.of("command", "whoami"), new CancellationToken());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("拒绝");
        assertThat(tool.allowedCommands()).containsExactly("echo");
    }

    @Test
    void shellExecAcceptsCommandInAllowList() {
        ShellExecTool tool = new ShellExecTool();
        tool.configure(ToolSettings.of(Map.of("Shell.AllowedCommands", List.of("echo"))));

        ToolResult result = tool.execute(Map.of("command", "echo allowed"), new CancellationToken());

        assertThat(result.success()).isTrue();
        assertThat(String.valueOf(result.output()).trim()).isEqualTo("allowed");
    }

    @Test
    void shellExecAllowListRejectsShellMetacharacters() {
        // 仅按首 token 判定是不安全的：`echo hi; whoami` 的首 token 允许，
        // 但 whoami 照样会被 sh 执行
        ShellExecTool tool = new ShellExecTool();
        tool.configure(ToolSettings.of(Map.of("Shell.AllowedCommands", List.of("echo"))));

        for (String dangerous : List.of(
                "echo hi; whoami",
                "echo hi && whoami",
                "echo hi | sh",
                "echo $(whoami)",
                "echo `whoami`",
                "echo hi > /etc/passwd",
                "echo hi\nwhoami")) {
            ToolResult result = tool.execute(Map.of("command", dangerous), new CancellationToken());
            assertThat(result.success()).as(dangerous).isFalse();
            assertThat(result.error()).as(dangerous).contains("拒绝");
        }
    }

    @Test
    void shellExecAllowListAcceptsPlainCommand() {
        ShellExecTool tool = new ShellExecTool();
        tool.configure(ToolSettings.of(Map.of("Shell.AllowedCommands", List.of("echo"))));

        ToolResult result = tool.execute(Map.of("command", "echo hello world"), new CancellationToken());

        assertThat(result.success()).isTrue();
        assertThat(String.valueOf(result.output()).trim()).isEqualTo("hello world");
    }

    @Test
    void emptyAllowListMeansNoRestriction() {
        ShellExecTool tool = new ShellExecTool();
        tool.configure(ToolSettings.empty());

        assertThat(tool.allowedCommands()).isEmpty();
        assertThat(tool.execute(Map.of("command", "echo x"), new CancellationToken()).success()).isTrue();
    }

    @Test
    void shellExecTimeoutComesFromSettings() {
        ShellExecTool tool = new ShellExecTool();
        tool.configure(ToolSettings.of(Map.of("Shell.TimeoutSeconds", 7)));

        assertThat(tool.timeoutSeconds()).isEqualTo(7L);
        assertThat(tool.description()).contains("7 秒超时");
    }

    @Test
    void shellExecConfigureToleratesNull() {
        ShellExecTool tool = new ShellExecTool(5);
        tool.configure(null);

        // 退回默认而非置零
        assertThat(tool.timeoutSeconds()).isEqualTo(ShellExecTool.DEFAULT_TIMEOUT_SECONDS);
    }

    @Test
    void toolsDeclarePermissions() {
        assertThat(new FileReadTool().requiredPermission()).isEqualTo(ToolPermission.READ);
        assertThat(new FileListTool().requiredPermission()).isEqualTo(ToolPermission.READ);
        assertThat(new FileWriteTool().requiredPermission()).isEqualTo(ToolPermission.WRITE);
        assertThat(new HttpGetTool().requiredPermission()).isEqualTo(ToolPermission.NETWORK);
        assertThat(new ShellExecTool().requiredPermission()).isEqualTo(ToolPermission.EXECUTE);
    }

    @Test
    void providersAreDiscoverableAndExposeSchemas() {
        List<com.acanx.module.aha.common.tool.Tool> tools =
                StreamSupport.stream(ServiceLoader.load(com.acanx.module.aha.common.tool.ToolProvider.class)
                                .spliterator(), false)
                        .flatMap(provider -> provider.tools().stream())
                        .toList();

        assertThat(tools).hasSize(5);
        assertThat(tools).allSatisfy(tool -> {
            assertThat(tool.name()).isNotBlank();
            assertThat(tool.description()).isNotBlank();
            assertThat(tool.parameters()).isNotNull();
            assertThat(tool.requiredPermission()).isNotNull();
        });
    }
    @Test
    void shellExecRunsEveryLineOfAMultiLineCommand() {
        // 回归：Windows 上 cmd /c 只执行命令行第一行，后续行被静默丢弃——
        // 不报错、退出码为 0，表现为「命令成功但什么都没发生」
        String command = "echo first\necho second\necho third";

        ToolResult result = new ShellExecTool()
                .execute(Map.of("command", command), new CancellationToken());

        assertThat(result.success()).isTrue();
        String output = String.valueOf(result.output());
        assertThat(output).contains("first").contains("second").contains("third");
    }

    @Test
    void shellExecPropagatesExitCodeFromTheLastLine() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String command = windows ? "echo ok\r\nexit /b 3" : "echo ok\nexit 3";

        ToolResult result = new ShellExecTool()
                .execute(Map.of("command", command), new CancellationToken());

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("退出码 3");
    }

    @Test
    void shellExecFailureDoesNotEchoTheCommand() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String command = windows ? "cmd /c exit 1" : "exit 1";

        ToolResult result = new ShellExecTool()
                .execute(Map.of("command", command), new CancellationToken());

        assertThat(result.success()).isFalse();
        // 命令由展示层单独一行给出，这里再贴一遍只是重复
        assertThat(result.error()).doesNotContain("命令: " + command);
    }

}
