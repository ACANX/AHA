package com.acanx.module.aha.cli.tty;

import com.acanx.module.aha.common.exception.AhaException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 剪贴板读取（不依赖终端能力）。
 *
 * <p>给「终端不支持括号粘贴」的场合一条替代入口（会话内命令 {@code /paste}）：
 * 用户先把内容复制到剪贴板，再由 AHA 自己读出来 —— 这条路径<b>不经过终端</b>，
 * 因此多行文本不会被逐行提交。</p>
 *
 * <p>刻意<b>不走 AWT</b>（{@code Toolkit.getSystemClipboard()}）：CLI 的模块图里没有
 * {@code java.desktop}，而且在无显示的 Linux 上会抛 {@code HeadlessException}。
 * 改为按平台调用系统命令，与 {@link ExternalEditor} 同一套路：列出可用手段、挨个试，
 * 都不行时给一条<b>能照做</b>的提示，而不是抛一个原始异常。</p>
 *
 * @since 0.1.0
 */
public final class Clipboard {

    /** 读不到剪贴板时的错误码。 */
    public static final String CODE_CLIPBOARD_UNAVAILABLE = "CLIPBOARD_UNAVAILABLE";

    /** 覆盖读取命令的系统属性（与 {@code aha.editor} 同一套路，便于测试与特殊环境）。 */
    public static final String CLIPBOARD_PROPERTY = "aha.clipboard";

    /** 单条命令的超时（秒）：读剪贴板是本地操作，卡住就说明它不能用了。 */
    private static final int TIMEOUT_SECONDS = 5;

    private Clipboard() {
    }

    /**
     * 剪贴板内容。
     *
     * @param text   文本内容（已去掉工具额外补的一个结尾换行）
     * @param source 实际生效的读取命令，用于告知用户「内容是从哪来的」
     * @since 0.1.0
     */
    public record Content(String text, String source) {
    }

    /**
     * 读取剪贴板文本。
     *
     * @return 文本内容与来源
     * @throws AhaException 所有手段都失败时（错误码 {@value #CODE_CLIPBOARD_UNAVAILABLE}，
     *                      消息里列出每种手段的失败原因与安装提示）
     */
    public static Content read() {
        String osName = System.getProperty("os.name", "");
        List<List<String>> candidates =
                candidates(osName, System.getenv(), System.getProperty(CLIPBOARD_PROPERTY));
        List<String> failures = new ArrayList<>();
        for (List<String> command : candidates) {
            try {
                return new Content(run(command), String.join(" ", command));
            } catch (IOException e) {
                // 命令不存在、退出码非 0、超时都走这里：换下一种手段
                failures.add(command.get(0) + "（" + e.getMessage() + "）");
            }
        }
        throw new AhaException(CODE_CLIPBOARD_UNAVAILABLE,
                "读不到剪贴板（已尝试 " + candidates.size() + " 种方式："
                        + String.join("、", failures) + "）。" + hint(osName));
    }

    /**
     * 候选读取命令（按平台与显示环境排序）。
     *
     * <p>包级可见以便测试：只做「选哪些命令」的判断，不真的执行。</p>
     *
     * @param osName   操作系统名（{@code os.name}）
     * @param env      环境变量
     * @param override {@code aha.clipboard} 的取值；非空时只用它
     * @return 候选命令（按尝试顺序）
     */
    static List<List<String>> candidates(String osName, Map<String, String> env, String override) {
        if (override != null && !override.isBlank()) {
            return List.of(List.of(override.strip().split("\\s+")));
        }
        String os = lower(osName);
        List<List<String>> candidates = new ArrayList<>();
        if (os.contains("win")) {
            // -Raw：保留原始换行（不加会把多行变成字符串数组/折行）；
            // 先设 UTF-8 输出编码：把 stdout 重定向到管道时，PowerShell 默认按控制台代码页
            // 编码（中文 Windows 上是 GBK），读回来就是乱码
            String getClipboard = "[Console]::OutputEncoding=[Text.Encoding]::UTF8; Get-Clipboard -Raw";
            candidates.add(List.of("powershell", "-NoProfile", "-NonInteractive", "-Command",
                    getClipboard));
            candidates.add(List.of("pwsh", "-NoProfile", "-NonInteractive", "-Command",
                    getClipboard));
            return List.copyOf(candidates);
        }
        if (os.contains("mac")) {
            candidates.add(List.of("pbpaste"));
            return List.copyOf(candidates);
        }
        // Linux / 其它 POSIX：Wayland 优先（有 WAYLAND_DISPLAY 时 X11 工具往往读不到内容），
        // 再退到 X11 的 xclip / xsel。--no-newline 是 wl-paste 的选项，用它免得
        // 每次粘贴都被塞一个额外的换行
        candidates.add(List.of("wl-paste", "--no-newline"));
        candidates.add(List.of("xclip", "-selection", "clipboard", "-o"));
        candidates.add(List.of("xsel", "--clipboard", "--output"));
        return List.copyOf(candidates);
    }

    /**
     * 去掉工具额外补的一个结尾换行。
     *
     * <p>{@code xclip} 这类工具会给输出补一个换行，而用户复制的文本本身通常没有；
     * 只去一个，避免动到用户内容里本来的空行。</p>
     *
     * @param text 原始输出
     * @return 去掉一个结尾换行后的文本
     */
    static String stripOneTrailingNewline(String text) {
        if (text.endsWith("\r\n")) {
            return text.substring(0, text.length() - 2);
        }
        if (text.endsWith("\n")) {
            return text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static String hint(String osName) {
        String os = lower(osName);
        if (os.contains("win")) {
            return "Windows 下需要 PowerShell（powershell 或 pwsh）在 PATH 上。";
        }
        if (os.contains("mac")) {
            return "macOS 下需要 pbpaste（系统自带）。";
        }
        return "Linux 下需要 wl-clipboard（Wayland，提供 wl-paste）或 xclip / xsel（X11）。";
    }

    /**
     * 执行一条候选命令并读取其标准输出。
     *
     * @param command 命令与参数
     * @return 标准输出文本
     * @throws IOException 启动失败、超时、被中断或退出码非 0
     */
    private static String run(List<String> command) throws IOException {
        Process process = new ProcessBuilder(command)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        // 读与等分开：在主线程里直接 readAllBytes 会被卡住的命令挂住，超时判据永远走不到
        byte[][] captured = new byte[1][];
        IOException[] readFailure = new IOException[1];
        Thread reader = Thread.ofVirtual().name("aha-clipboard-read").start(() -> {
            try {
                captured[0] = process.getInputStream().readAllBytes();
            } catch (IOException e) {
                readFailure[0] = e;
            }
        });
        try {
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("超时 " + TIMEOUT_SECONDS + " 秒未返回");
            }
            reader.join(Duration.ofSeconds(2));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IOException("被中断");
        }
        if (readFailure[0] != null) {
            throw readFailure[0];
        }
        if (process.exitValue() != 0) {
            throw new IOException("退出码 " + process.exitValue());
        }
        byte[] bytes = captured[0] == null ? new byte[0] : captured[0];
        return stripOneTrailingNewline(new String(bytes, StandardCharsets.UTF_8));
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
