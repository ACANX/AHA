package com.acanx.module.aha.cli.tty;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;

/**
 * Windows 控制台代码页协商：把当前控制台切成 UTF-8（代码页 65001）。
 *
 * <p>为什么需要它：Windows 控制台<b>按代码页解释字节</b>。英文环境的默认代码页常为
 * {@code 1252}，中文为 {@code 936}。AHA 的 {@code System.out} 是「字节流 + Charset」，
 * 代码页不是 UTF-8 时中文会在程序侧就被编码器替换成 {@code ?}；同时
 * {@link OutputEncoding} 探测到无法编码装饰符号，启动标志会从像素风退化为纯 ASCII。
 * 把控制台切成 UTF-8 后，两者一起恢复。</p>
 *
 * <p><b>安全边界（缺一不可）</b>：</p>
 * <ul>
 *   <li>只在本进程是 <b>Windows</b> 且 <b>标准输出真的连着控制台</b> 时才动；
 *       管道 / 重定向 / CI 一律不碰（{@link #ensureUtf8()} 的前置判断）。</li>
 *   <li>改动的是<b>整个控制台会话</b>的代码页，因此记录原值并在 JVM 退出时恢复
 *       （shutdown hook）。</li>
 *   <li>任何一步失败都静默返回 {@code false}，由调用方保持既有的探测 + 降级链。</li>
 * </ul>
 *
 * <p>实现走 FFM（{@code java.lang.foreign}）直接调 {@code kernel32}。CLI 以 fat jar / 原生镜像
 * 形式运行在 <b>无名模块</b>（classpath）上，启动脚本与原生参数文件都已带
 * {@code --enable-native-access=ALL-UNNAMED}，故无需额外的模块名参数。</p>
 *
 * @since 0.1.3
 */
public final class WindowsConsole {

    /** {@code STD_OUTPUT_HANDLE}（kernel32 约定值，{@code (DWORD)-11}）。 */
    private static final int STD_OUTPUT_HANDLE = -11;

    /** 控制台 UTF-8 代码页。 */
    public static final int CP_UTF8 = 65001;

    /** FFM 句柄的懒初始化状态（首次调用发生在运行期，不在构建期）。 */
    private static volatile boolean initDone;
    private static volatile boolean available;
    private static Arena arena;
    private static MethodHandle getStdHandle;
    private static MethodHandle getConsoleMode;
    private static MethodHandle getConsoleOutputCp;
    private static MethodHandle setConsoleOutputCp;
    private static MethodHandle setConsoleCp;

    /** 协商前的原代码页；{@code < 0} 表示本次未改过。 */
    private static volatile int originalCp = -1;
    private static volatile boolean restored;

    private WindowsConsole() {
    }

    /**
     * 是否运行在 Windows 上。
     *
     * @return 是 Windows 时为 {@code true}
     */
    public static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows");
    }

    /**
     * 把当前控制台协商成 UTF-8（代码页 65001）。
     *
     * <p>调用时机必须在<b>任何输出之前</b>；成功（含原代码页本就是 UTF-8）返回 {@code true}。
     * 非 Windows、无控制台（管道 / 重定向 / CI）或任何失败都返回 {@code false}。</p>
     *
     * @return 是否已确保控制台为 UTF-8
     */
    public static synchronized boolean ensureUtf8() {
        if (!isWindows() || System.console() == null) {
            return false;
        }
        if (!init() || !isConsole()) {
            return false;
        }
        try {
            int cp = (int) getConsoleOutputCp.invokeExact();
            if (cp == CP_UTF8) {
                return true;
            }
            if ((int) setConsoleOutputCp.invokeExact(CP_UTF8) == 0) {
                return false;
            }
            setConsoleCp.invokeExact(CP_UTF8);
            originalCp = cp;
            Runtime.getRuntime().addShutdownHook(
                    new Thread(WindowsConsole::restore, "aha-console-cp-restore"));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 当前控制台是否已经是 UTF-8。
     *
     * <p>供 {@link OutputEncoding#detect()} 在 {@code System.console().charset()} 可能缓存旧值时
     * 直接给出结论。</p>
     *
     * @return 是 UTF-8 控制台时 {@code true}
     */
    public static boolean isUtf8Console() {
        if (!isWindows() || System.console() == null) {
            return false;
        }
        if (!init() || !isConsole()) {
            return false;
        }
        try {
            return (int) getConsoleOutputCp.invokeExact() == CP_UTF8;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 退出时恢复原代码页（幂等）。 */
    private static synchronized void restore() {
        if (restored || originalCp < 0) {
            return;
        }
        restored = true;
        try {
            setConsoleOutputCp.invokeExact(originalCp);
            setConsoleCp.invokeExact(originalCp);
        } catch (Throwable ignored) {
            // 退出阶段恢复失败不再补救：代码页是会话级的，控制台关闭即失效
        }
    }

    /** {@code GetConsoleMode(STD_OUTPUT_HANDLE)} 成功即为「标准输出连着控制台」。 */
    private static boolean isConsole() {
        try {
            MemorySegment handle = (MemorySegment) getStdHandle.invokeExact(STD_OUTPUT_HANDLE);
            if (handle == null || MemorySegment.NULL.equals(handle)) {
                return false;
            }
            MemorySegment mode = arena.allocate(ValueLayout.JAVA_INT);
            return (int) getConsoleMode.invokeExact(handle, mode) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** 懒加载 kernel32 的符号；失败则永久不可用（不重试）。 */
    private static synchronized boolean init() {
        if (initDone) {
            return available;
        }
        initDone = true;
        try {
            arena = Arena.ofShared();
            SymbolLookup kernel = SymbolLookup.libraryLookup("kernel32", arena);
            Linker linker = Linker.nativeLinker();
            getStdHandle = linker.downcallHandle(kernel.find("GetStdHandle").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            getConsoleMode = linker.downcallHandle(kernel.find("GetConsoleMode").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            getConsoleOutputCp = linker.downcallHandle(kernel.find("GetConsoleOutputCP").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT));
            setConsoleOutputCp = linker.downcallHandle(kernel.find("SetConsoleOutputCP").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            setConsoleCp = linker.downcallHandle(kernel.find("SetConsoleCP").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
            available = true;
        } catch (Throwable t) {
            available = false;
        }
        return available;
    }
}
