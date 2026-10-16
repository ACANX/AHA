package com.acanx.module.aha.cli.tty;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * 控制台编码协商的入口：在程序启动时把 stdout / stderr 统一到 UTF-8。
 *
 * <p>{@link WindowsConsole#ensureUtf8()} 改的是<b>控制台</b>的代码页；但
 * {@code System.out} / {@code System.err} 的字符集是在 JVM（或原生镜像）启动时就绑定好的，
 * 之后改代码页并不会自动更新它们。因此协商成功后必须<b>重建这两个流</b>，
 * 否则 <code>System.out.println("中文")</code> 仍会用旧编码写出 {@code ?}——
 * 启动横幅、{@code [warn]} 提示、picocli 的 {@code --help} 都走 {@code System.out}。</p>
 *
 * <p>本类只做编排，真正的判定与 Win32 调用在 {@link WindowsConsole}；两者在非 Windows 上
 * 都是 no-op，因此不影响 Linux / macOS 以及与平台无关的单元测试。</p>
 *
 * @since 0.1.3
 */
public final class ConsoleEncoding {

    private ConsoleEncoding() {
    }

    /**
     * 启动时调用一次，<b>必须在任何输出之前</b>（含日志装配与启动引导的警告）。
     *
     * <p>仅当 {@link WindowsConsole#ensureUtf8()} 成功（即当前是 Windows 控制台且已切到
     * UTF-8）时，才重建标准输出 / 错误流；其余情况保持原样。</p>
     */
    public static void prepare() {
        if (!WindowsConsole.ensureUtf8()) {
            return;
        }
        System.setOut(new PrintStream(new FileOutputStream(FileDescriptor.out), true,
                StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new FileOutputStream(FileDescriptor.err), true,
                StandardCharsets.UTF_8));
    }
}
