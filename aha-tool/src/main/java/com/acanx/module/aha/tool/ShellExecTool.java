package com.acanx.module.aha.tool;

import com.acanx.module.aha.common.model.ToolResult;
import com.acanx.module.aha.common.runtime.Platform;
import com.acanx.module.aha.common.tool.CancellationToken;
import com.acanx.module.aha.common.tool.JsonSchema;
import com.acanx.module.aha.common.tool.Tool;
import com.acanx.module.aha.common.tool.ToolPermission;
import com.acanx.module.aha.common.tool.ToolSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Shell 命令执行工具（必须显式确认）。
 *
 * <p>默认超时 30 秒：无限等待会让一次卡住的命令挂死整个对话循环。
 * 超时或取消时强制终止子进程。</p>
 *
 * @since 0.1.0
 */
public final class ShellExecTool implements Tool {

    private static final Logger LOG = LoggerFactory.getLogger(ShellExecTool.class);

    /**
     * 默认超时秒数。
     *
     * <p>与 {@code AhaDefault.yaml} 中 {@code Tools.Shell.TimeoutSeconds} 的默认值一致。
     * 该配置项目前尚未注入工具（{@code Tool} 接口无配置入口），
     * 待补配置注入后由配置驱动。</p>
     */
    public static final long DEFAULT_TIMEOUT_SECONDS = 30;

    /** 等待读取线程收尾的时间，避免进程已退出但输出未读完。 */
    private static final long READER_JOIN_MILLIS = 2000L;

    /** 实际超时秒数。 */
    private volatile long timeoutSeconds;

    /** 允许执行的命令；为空表示不限制。 */
    private volatile List<String> allowedCommands = List.of();

    /**
     * 白名单模式下禁止出现的 shell 元字符。
     *
     * <p>仅按首个子命令判定是不安全的：{@code echo hi; whoami} 的首 token 是允许的
     * {@code echo}，但 {@code whoami} 照样会被 sh 执行。含拼接、管道、重定向、
     * 命令替换或换行时直接拒绝。</p>
     */
    private static final java.util.regex.Pattern SHELL_METACHAR =
            java.util.regex.Pattern.compile("[;|&`$><\n\r]");

    /**
     * 构造工具，使用默认超时。
     */
    public ShellExecTool() {
        this(DEFAULT_TIMEOUT_SECONDS);
    }

    /**
     * 构造工具并指定超时秒数。
     *
     * @param timeoutSeconds 超时秒数，小于等于 0 时退回默认值
     */
    public ShellExecTool(long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds > 0 ? timeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
    }

    @Override
    public void configure(ToolSettings settings) {
        ToolSettings effective = settings == null ? ToolSettings.empty() : settings;
        this.timeoutSeconds = effective.integer("Shell.TimeoutSeconds", (int) DEFAULT_TIMEOUT_SECONDS);
        this.allowedCommands = effective.stringList("Shell.AllowedCommands");
    }

    /**
     * 返回当前允许的命令列表。
     *
     * @return 命令列表，空表示不限制
     */
    public List<String> allowedCommands() {
        return allowedCommands;
    }

    /**
     * 返回当前超时秒数。
     *
     * @return 超时秒数
     */
    public long timeoutSeconds() {
        return timeoutSeconds;
    }

    @Override
    public String name() {
        return "shell-exec";
    }

    @Override
    public String description() {
        return "执行 Shell 命令，需显式确认，默认 " + timeoutSeconds + " 秒超时";
    }

    @Override
    public JsonSchema parameters() {
        return JsonSchema.object(
                Map.of("command", JsonSchema.string("命令")),
                List.of("command"));
    }

    @Override
    public ToolResult execute(Map<String, Object> params, CancellationToken token) {
        Object command = params.get("command");
        if (command == null) {
            return ToolResult.failure("缺少参数: command");
        }
        String text = command.toString();
        if (!isAllowed(text)) {
            return ToolResult.failure("命令被 Tools.Shell.AllowedCommands 拒绝: " + firstToken(text)
                    + "；仅允许单一命令（不得含 ; | & ` $ > < 或换行）。已允许: " + allowedCommands);
        }
        // Windows 必须走临时批处理文件，不能直接 cmd.exe /c <原文>：
        //   · cmd /c 只执行命令行的**第一行**，多行命令的后续行被静默丢弃——
        //     不报错、退出码为 0，用户看到的是「命令成功但什么都没发生」
        //   · Java 的参数引用与 cmd 的解析规则不兼容，嵌套 cmd /c 时会多出引号
        // POSIX 侧 /bin/sh -c 本身就能处理多行，无需落盘
        Path script = null;
        List<String> argv;
        if (Platform.isWindows()) {
            try {
                script = writeScript(text);
            } catch (IOException e) {
                return ToolResult.failure("无法创建临时脚本: " + e.getMessage());
            }
            argv = List.of("cmd.exe", "/c", script.toString());
        } else {
            argv = List.of("/bin/sh", "-c", text);
        }

        Process process = null;
        try {
            process = new ProcessBuilder(argv).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            Process running = process;
            // 在独立线程读取输出：若在主线程先 waitFor 再读，
            // 输出量超过管道缓冲时子进程会阻塞在写而永不退出。
            Thread reader = Thread.ofVirtual().start(() -> {
                try (InputStream in = running.getInputStream()) {
                    output.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // 进程被终止时流会关闭，属预期
                }
            });

            boolean finished = waitWhileNotCancelled(process, token);
            if (!finished) {
                terminate(process, reader);
                return token.isCancelled()
                        ? ToolResult.failure("命令已取消: " + text)
                        : ToolResult.failure("命令超时（" + timeoutSeconds + " 秒）已终止: " + text);
            }
            reader.join(READER_JOIN_MILLIS);

            int exit = process.exitValue();
            String result = output.toString();
            if (exit == 0) {
                return ToolResult.success(result);
            }
            // 不再回显命令原文：展示层已用「<目录> <提示符> <命令全文>」单独一行给出，
            // 这里再贴一遍只是重复（早期命令不可见时才需要）
            return ToolResult.failure("退出码 " + exit + ": " + result);
        } catch (IOException e) {
            return ToolResult.failure("执行失败: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            terminate(process, null);
            return ToolResult.failure("执行被中断");
        } finally {
            deleteScript(script);
        }
    }

    /**
     * 把命令写入临时批处理文件。
     *
     * <p>两处细节不能省：</p>
     *
     * <ul>
     *   <li><b>行尾必须是 CRLF</b>。LF-only 的批处理在 {@code if (...)} 块等结构上解析不可靠
     *       （见 {@code Docs/DevSpec/DocumentationSpec.md} 的跨平台脚本规约）</li>
     *   <li><b>用平台原生编码写入</b>。cmd 按控制台代码页解析批处理文件，用 UTF-8 写会让
     *       其中的中文字节被误读</li>
     *   <li><b>首行 {@code @echo off}</b>。批处理默认回显每一行命令，会把
     *       {@code C:\...>echo first} 之类混进结果里（{@code cmd /c <命令>} 不会）</li>
     * </ul>
     *
     * @param command 命令原文
     * @return 脚本路径
     * @throws IOException 写入失败
     */
    private static Path writeScript(String command) throws IOException {
        Path file = Files.createTempFile("aha-shell-", ".cmd");
        // 关掉命令回显：批处理默认逐行回显，会把命令行本身混进结果
        String body = "@echo off\r\n"
                + command.replace("\r\n", "\n").replace("\n", "\r\n");
        Files.writeString(file, body, nativeCharset());
        file.toFile().deleteOnExit();
        return file;
    }

    /**
     * 平台原生字符集（Windows 上即控制台代码页）。
     *
     * @return 字符集
     */
    private static Charset nativeCharset() {
        String name = System.getProperty("native.encoding");
        if (name != null && !name.isBlank()) {
            try {
                return Charset.forName(name);
            } catch (RuntimeException e) {
                LOG.debug("未知的原生编码 {}，回退默认编码", name);
            }
        }
        return Charset.defaultCharset();
    }

    /**
     * 删除临时脚本（失败不影响本次执行结果）。
     *
     * @param script 脚本路径，可为 {@code null}
     */
    private static void deleteScript(Path script) {
        if (script == null) {
            return;
        }
        try {
            Files.deleteIfExists(script);
        } catch (IOException e) {
            LOG.debug("删除临时脚本失败: {}", e.getMessage());
        }
    }

    /**
     * 命令是否在允许列表中。
     *
     * <p>白名单模式仅接受「单一命令 + 无 shell 元字符 + 首个子命令在名单内」。</p>
     *
     * @param command 原始命令
     * @return 是否允许
     */
    private boolean isAllowed(String command) {
        List<String> allowed = this.allowedCommands;
        if (allowed.isEmpty()) {
            return true;
        }
        if (SHELL_METACHAR.matcher(command).find()) {
            return false;
        }
        return allowed.contains(firstToken(command));
    }

    /**
     * 取命令的首个子命令。
     *
     * @param command 原始命令
     * @return 首个子命令
     */
    private static String firstToken(String command) {
        String trimmed = command.trim();
        int end = 0;
        while (end < trimmed.length() && !Character.isWhitespace(trimmed.charAt(end))) {
            end++;
        }
        return end == 0 ? trimmed : trimmed.substring(0, end);
    }

    /**
     * 等待进程结束，同时轮询取消令牌。
     *
     * @param process 子进程
     * @param token   取消令牌
     * @return 进程是否在超时前正常结束
     * @throws InterruptedException 等待被中断
     */
    private boolean waitWhileNotCancelled(Process process, CancellationToken token)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            if (token.isCancelled()) {
                return false;
            }
            if (process.waitFor(200, TimeUnit.MILLISECONDS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 强制终止进程并等待读取线程收尾。
     *
     * @param process 子进程，可为 {@code null}
     * @param reader  读取线程，可为 {@code null}
     */
    private static void terminate(Process process, Thread reader) {
        if (process != null) {
            // 只 destroy 直接子进程会留下孤儿：`sh -c "sleep 120"` 被 SIGKILL 后
            // sleep 仍持有输出管道，读取线程无法收尾，调用方白等。
            // 必须先杀后代再杀自身。
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            try {
                process.waitFor(READER_JOIN_MILLIS, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (reader != null) {
            try {
                reader.join(READER_JOIN_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public ToolPermission requiredPermission() {
        return ToolPermission.EXECUTE;
    }
}
