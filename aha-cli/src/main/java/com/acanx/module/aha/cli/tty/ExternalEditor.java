package com.acanx.module.aha.cli.tty;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * 外部编辑器探测与唤起。
 *
 * <p>用于「编辑 Agent 身份文件」这类需要用户手写文本的场景：AHA 不内置编辑器，
 * 只负责找到系统上可用的编辑器、把终端让出去、等它退出。</p>
 *
 * <p>优先级：{@code -Daha.editor} → {@code $VISUAL} → {@code $EDITOR} → 平台默认
 * （Windows {@code notepad}；macOS {@code open -W -t}；其余按 {@code nano} / {@code vim} / {@code vi} 顺序探测）。
 * 环境变量里的命令若不在 PATH 上则跳过而不是报错——用户设了但没装的情况很常见，
 * 直接失败会逼他去排查一个本可绕过的配置。</p>
 *
 * <p>选择在所有平台上都<b>阻塞</b>的命令：唤起后要等编辑器退出才能恢复终端，
 * 否则编辑器与 REPL 会同时读写终端。</p>
 *
 * @since 0.1.0
 */
public final class ExternalEditor {

    /** 编辑器环境变量（优先）。 */
    public static final String VISUAL = "VISUAL";

    /** 编辑器环境变量（次选）。 */
    public static final String EDITOR = "EDITOR";

    /** 用于覆盖编辑器的系统属性，优先于两个环境变量。 */
    public static final String EDITOR_PROPERTY = "aha.editor";

    private ExternalEditor() {
    }

    /**
     * 解析当前环境可用的编辑器命令。
     *
     * @return 命令与参数；无可用编辑器时为空
     */
    public static Optional<List<String>> command() {
        return command(System.getenv(), osName(), ExternalEditor::onPath,
                System.getProperty(EDITOR_PROPERTY));
    }

    /**
     * 按给定环境解析编辑器命令（便于测试）。
     *
     * @param env       环境变量
     * @param osName    操作系统名
     * @param available 判断命令是否可用的断言
     * @param explicit  显式指定的编辑器命令（对应 {@code -Daha.editor}）
     * @return 命令与参数；无可用编辑器时为空
     */
    static Optional<List<String>> command(Map<String, String> env, String osName,
                                          Predicate<String> available, String explicit) {
        if (explicit != null && !explicit.isBlank()) {
            List<String> parts = split(explicit, available);
            if (!parts.isEmpty() && available.test(parts.get(0))) {
                return Optional.of(parts);
            }
        }
        for (String key : List.of(VISUAL, EDITOR)) {
            String value = env == null ? null : env.get(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            List<String> parts = split(value, available);
            if (!parts.isEmpty() && available.test(parts.get(0))) {
                return Optional.of(parts);
            }
        }
        for (List<String> fallback : platformDefaults(osName)) {
            if (available.test(fallback.get(0))) {
                return Optional.of(fallback);
            }
        }
        return Optional.empty();
    }

    /**
     * 平台默认编辑器候选。
     *
     * @param osName 操作系统名
     * @return 候选命令
     */
    private static List<List<String>> platformDefaults(String osName) {
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return List.of(List.of("notepad"));
        }
        if (os.contains("mac") || os.contains("darwin")) {
            // -W 等编辑器退出，-t 用默认文本编辑器打开
            return List.of(List.of("open", "-W", "-t"));
        }
        return List.of(List.of("nano"), List.of("vim"), List.of("vi"));
    }

    /**
     * 用编辑器打开文件并等待退出。
     *
     * @param file     目标文件
     * @param handover 终端让位器
     * @return 编辑器退出码
     * @throws IOException 未找到编辑器或启动失败
     */
    public static int open(Path file, TerminalHandover handover) throws IOException {
        List<String> command = command()
                .orElseThrow(() -> new IOException("未找到可用的外部编辑器（可设置 VISUAL 或 EDITOR）"));
        List<String> full = new ArrayList<>(command);
        full.add(file.toString());
        TerminalHandover actual = handover == null ? TerminalHandover.DIRECT : handover;
        return actual.run(() -> launch(full));
    }

    /**
     * 启动编辑器进程并等待退出。
     *
     * @param command 完整命令
     * @return 退出码；被中断时返回 -1
     */
    private static int launch(List<String> command) {
        try {
            // inheritIO：把真实终端交给编辑器
            Process process = new ProcessBuilder(command).inheritIO().start();
            return process.waitFor();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return -1;
        }
    }

    /**
     * 命令名是否可在 PATH 上找到。
     *
     * @param name 命令名或路径
     * @return 可用时为 {@code true}
     */
    static boolean onPath(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (name.contains("/") || name.contains("\\")) {
            return Files.isExecutable(Path.of(name));
        }
        String path = System.getenv("PATH");
        if (path == null || path.isBlank()) {
            return false;
        }
        boolean windows = osName().contains("win");
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isBlank()) {
                continue;
            }
            Path base = Path.of(dir);
            if (Files.isExecutable(base.resolve(name))) {
                return true;
            }
            if (windows) {
                for (String suffix : List.of(".exe", ".cmd", ".bat")) {
                    if (Files.isExecutable(base.resolve(name + suffix))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 拆分编辑器命令（允许 {@code $EDITOR="code -w"} 这种带参数的写法）。
     *
     * <p>先看整串是不是一个可用命令：编辑器路径可能含空格（如 {@code C:\Program Files\...}），
     * 无条件按空白切会把路径切碎。</p>
     *
     * @param value     环境变量值
     * @param available 判断命令是否可用的断言
     * @return 命令与参数
     */
    private static List<String> split(String value, Predicate<String> available) {
        String trimmed = value.trim();
        if (available.test(trimmed)) {
            return List.of(trimmed);
        }
        List<String> parts = new ArrayList<>();
        for (String part : trimmed.split("\\s+")) {
            if (!part.isBlank()) {
                parts.add(part);
            }
        }
        return parts;
    }

    /**
     * 当前操作系统名。
     *
     * @return 操作系统名（小写）
     */
    private static String osName() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    }
}
