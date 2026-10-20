package com.acanx.module.aha.cli.tty;

import com.acanx.module.aha.common.runtime.Platform;
import org.jline.reader.LineReader;
import org.jline.reader.Widget;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.Locale;
import java.util.Map;

/**
 * Windows：把控制台输入切到 VT 模式，让括号粘贴标记透传给 JLine（issue #75）。
 *
 * <p><b>为什么需要这一步</b>：JLine 的 Windows 后端只打开输出侧的
 * {@code ENABLE_VIRTUAL_TERMINAL_PROCESSING}（0x0004），从未打开输入侧的
 * {@code ENABLE_VIRTUAL_TERMINAL_INPUT}（0x0200）。不开 0x0200 时，终端发出的
 * {@code ESC[200~ … ESC[201~}（括号粘贴标记）会被 conhost / ConPTY 的输入引擎直接消费掉，
 * 应用只拿到裸内容 + {@code \r}；JLine 的 {@code LineReaderImpl.beginPaste()} 因此永远等不到标记，
 * 多行粘贴只能逐行提交。</p>
 *
 * <p>开启 0x0200 后标记会原样透传，JLine 已有的括号粘贴解析即可把整块内容一次性写入缓冲区。
 * 实测（Windows Terminal，{@code ReadConsoleW}）：</p>
 *
 * <pre>
 * 不开 0x0200：line1&lt;CR&gt;line2&lt;CR&gt;line3
 * 开   0x0200：&lt;ESC&gt;[200~line1&lt;CR&gt;line2&lt;CR&gt;line3&lt;ESC&gt;[201~
 * </pre>
 *
 * <p><b>为什么每轮都要重设</b>：JLine 的 {@code AbstractWindowsTerminal.updateConsoleMode()}
 * 用固定组合重建输入模式（{@code ENABLE_WINDOW_INPUT} + 可选项），不含 0x0200；每次
 * {@code readLine} 进入 raw mode 都会把它清掉。因此绑定到 {@link LineReader#CALLBACK_INIT}
 * （在 {@code enterRawMode()} 之后调用），每轮补一次。</p>
 *
 * <p><b>回退</b>：{@code AHA_PASTE_VT=0}（或 {@code false} / {@code off} / {@code no}）关闭；
 * 非 Windows、拿不到控制台句柄、或调用失败时都静默返回 {@code false}，行为与改动前一致。</p>
 *
 * @since 0.2.0
 */
public final class VtInputMode {

    /** {@code ENABLE_VIRTUAL_TERMINAL_INPUT}：让控制台把 VT 序列（含括号粘贴标记）透传给应用。 */
    public static final int ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200;

    /** 关闭开关：{@code AHA_PASTE_VT=0}。 */
    public static final String ENV = "AHA_PASTE_VT";

    /** {@code STD_INPUT_HANDLE}。 */
    private static final int STD_INPUT_HANDLE = -10;

    private static volatile MethodHandle getStdHandle;
    private static volatile MethodHandle getConsoleMode;
    private static volatile MethodHandle setConsoleMode;

    /** 链接或调用失败后不再重试，避免每轮 readLine 都重复报错。 */
    private static volatile boolean unavailable;

    private VtInputMode() {
    }

    /**
     * 是否应按配置启用。
     *
     * @param windows 是否 Windows
     * @param env     环境变量
     * @return 是否启用
     */
    static boolean shouldEnable(boolean windows, Map<String, String> env) {
        if (!windows) {
            return false;
        }
        String value = env == null ? null : env.get(ENV);
        if (value == null || value.isBlank()) {
            return true;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return !("0".equals(normalized) || "false".equals(normalized)
                || "off".equals(normalized) || "no".equals(normalized));
    }

    /**
     * 把 {@code ENABLE_VIRTUAL_TERMINAL_INPUT} 补进输入模式。
     *
     * <p>幂等：已经带上该标志时直接返回 {@code true}，不重复写。</p>
     *
     * @return 当前是否处于 VT 输入模式
     */
    public static boolean ensure() {
        if (unavailable || !shouldEnable(Platform.isWindows(), System.getenv())) {
            return false;
        }
        init();
        if (unavailable || getStdHandle == null || getConsoleMode == null || setConsoleMode == null) {
            return false;
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment handle = (MemorySegment) getStdHandle.invoke(STD_INPUT_HANDLE);
            if (handle.equals(MemorySegment.NULL)) {
                return false;
            }
            MemorySegment modePtr = arena.allocate(ValueLayout.JAVA_INT);
            if (((Integer) getConsoleMode.invoke(handle, modePtr)) == 0) {
                return false;
            }
            int mode = modePtr.get(ValueLayout.JAVA_INT, 0);
            if ((mode & ENABLE_VIRTUAL_TERMINAL_INPUT) != 0) {
                return true;
            }
            // JLine 每轮 raw mode 都会重建 mode（不带 0x0200），所以这里只管补标志
            return ((Integer) setConsoleMode.invoke(handle, mode | ENABLE_VIRTUAL_TERMINAL_INPUT)) != 0;
        } catch (Throwable t) {
            if (t instanceof Error error) {
                throw error;
            }
            unavailable = true;
            return false;
        }
    }

    /**
     * 绑定到 {@link LineReader#CALLBACK_INIT}：每轮 {@code readLine} 在 raw mode 之后补一次 0x0200。
     *
     * @param reader JLine 行读取器
     */
    public static void bind(LineReader reader) {
        Widget previous = reader.getWidgets().get(LineReader.CALLBACK_INIT);
        reader.getWidgets().put(LineReader.CALLBACK_INIT, () -> {
            if (previous != null) {
                previous.apply();
            }
            ensure();
            return true;
        });
    }

    private static synchronized void init() {
        if (getStdHandle != null || unavailable) {
            return;
        }
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("kernel32", Arena.global());
            getStdHandle = linker.downcallHandle(
                    kernel32.find("GetStdHandle").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
            getConsoleMode = linker.downcallHandle(
                    kernel32.find("GetConsoleMode").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.ADDRESS));
            setConsoleMode = linker.downcallHandle(
                    kernel32.find("SetConsoleMode").orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_INT,
                            ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        } catch (Throwable t) {
            unavailable = true;
            if (t instanceof Error error) {
                throw error;
            }
        }
    }
}
