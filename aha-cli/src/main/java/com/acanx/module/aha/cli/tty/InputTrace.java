package com.acanx.module.aha.cli.tty;

import com.acanx.module.aha.common.exception.Exceptions;
import com.acanx.module.aha.core.config.SystemPromptLoader;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 输入行为留痕（默认关闭）：把「终端到底送来了什么」记成可读的日志。
 *
 * <p><b>为什么需要它</b>：「多行粘贴被逐行提交」这件事在 conhost（传统 Windows 控制台）上
 * 无法在本机复现 —— 只有真机才知道终端实际送来了什么。有了这份日志就能定论：</p>
 *
 * <ul>
 *   <li>若一次 {@code readLine} 返回的内容里**带字面换行** ⇒ 终端发了括号粘贴标记，粘贴是整体的；</li>
 *   <li>若短时间内（毫秒级）连续返回**多条** Enter 结尾的行 ⇒ 终端把粘贴拆成了多次回车，
 *       这就是 conhost 的行为（人工打字不可能几毫秒一行）。</li>
 * </ul>
 *
 * <p>开启方式（任选其一，值为 {@code 1} / {@code true} 时写到 {@code AHA_HOME/input-trace.log}，
 * 否则当成文件路径）：</p>
 *
 * <pre>
 * AHA_INPUT_TRACE=1                      # AHA_HOME/input-trace.log
 * AHA_INPUT_TRACE=/tmp/aha-input.log     # 指定文件
 * -Daha.input.trace=/tmp/aha-input.log   # 系统属性同义
 * </pre>
 *
 * <p>日志只写「输入了什么」，不写模型请求与会话内容；未开启时所有方法都是空操作。
 * 写盘失败一律降级为「放弃留痕」，绝不因为日志问题影响交互。</p>
 *
 * @since 0.2.0
 */
public final class InputTrace implements AutoCloseable {

    /** 环境变量名。 */
    public static final String ENV = "AHA_INPUT_TRACE";

    /** 系统属性名（与 {@link #ENV} 同义，便于脚本与测试注入）。 */
    public static final String PROPERTY = "aha.input.trace";

    /** 单条记录最多写这么多字符，避免把日志写成几个 GB。 */
    private static final int MAX_CHARS = 2000;

    private static final DateTimeFormatter CLOCK =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    private final BufferedWriter writer;

    private final PrintStream errors;

    private long lastNanos;

    private InputTrace(BufferedWriter writer, PrintStream errors) {
        this.writer = writer;
        this.errors = errors;
    }

    /**
     * 按环境变量 / 系统属性决定是否留痕。
     *
     * @param errors 留痕自身出错时的汇报出口（通常是 {@code System.err}）
     * @return 留痕器；未开启时返回一个空操作实例（{@link #enabled()} 为 {@code false}）
     */
    public static InputTrace of(PrintStream errors) {
        String target = System.getProperty(PROPERTY);
        if (target == null || target.isBlank()) {
            target = System.getenv(ENV);
        }
        if (target == null || target.isBlank()) {
            return new InputTrace(null, errors);
        }
        Path path = resolvePath(target.trim());
        if (path == null) {
            errors.println("[warn] 无法确定输入留痕文件路径（AHA_HOME 与 user.home 都取不到）。");
            return new InputTrace(null, errors);
        }
        try {
            Path parent = path.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return new InputTrace(writer, errors);
        } catch (IOException | RuntimeException e) {
            errors.println("[warn] 输入留痕开启失败（" + Exceptions.message(e) + "），本次不记录。");
            return new InputTrace(null, errors);
        }
    }

    private static Path resolvePath(String target) {
        if ("1".equals(target) || "true".equalsIgnoreCase(target)) {
            Path home = SystemPromptLoader.resolveUserHome();
            return home == null ? null : home.resolve("input-trace.log");
        }
        return Path.of(target);
    }

    /**
     * 是否真的在留痕。
     *
     * @return 已开启返回 {@code true}
     */
    public boolean enabled() {
        return writer != null;
    }

    /**
     * 记一条启动信息（终端实现、能力探测结果）—— 排查时要先知道「用的是哪条 JLine 终端路径」。
     *
     * @param text 内容
     */
    public void note(String text) {
        write('|', text);
    }

    /**
     * 记一次输入：时间戳、与上一次输入的间隔、字符数、转义后的内容。
     *
     * @param line 一次 {@code readLine} 返回的文本（可为 {@code null}，表示 EOF）
     */
    public void input(String line) {
        write('>', line);
    }

    private void write(char kind, String text) {
        if (writer == null) {
            return;
        }
        long now = System.nanoTime();
        String delta = lastNanos == 0
                ? "-"
                : String.format(java.util.Locale.ROOT, "%.1fms", (now - lastNanos) / 1_000_000.0);
        lastNanos = now;
        String body = text == null ? "<null>" : escape(text);
        try {
            writer.write(kind + " " + CLOCK.format(Instant.now()) + " Δ" + delta
                    + " len=" + (text == null ? 0 : text.length()) + " " + body);
            writer.newLine();
            writer.flush();
        } catch (IOException e) {
            errors.println("[warn] 输入留痕写入失败：" + Exceptions.message(e));
        }
    }

    private static String escape(String text) {
        String clipped = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) + "…" : text;
        return clipped.replace("\\", "\\\\")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    @Override
    public void close() {
        if (writer == null) {
            return;
        }
        try {
            writer.close();
        } catch (IOException ignored) {
            // 关闭失败无所谓：留痕是排查辅助，不是功能
        }
    }
}
