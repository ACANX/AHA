package com.acanx.module.aha.cli.tty;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link InputTrace} 测试：默认关闭、开了能读、换行被转义（一行一次输入）、
 * 写不进去时只降级提示而绝不抛异常。
 *
 * <p>为什么要留痕：conhost 上「多行粘贴被逐行提交」这件事本机复现不了，只有真机的日志能定论 ——
 * 所以这份日志本身必须是**一行一次输入 + 间隔**，才看得出「几毫秒内连来三行 Enter」。</p>
 *
 * @since 0.2.0
 */
class InputTraceTest {

    private final ByteArrayOutputStream errors = new ByteArrayOutputStream();

    private PrintStream err() {
        return new PrintStream(errors, true, StandardCharsets.UTF_8);
    }

    private String errorText() {
        return errors.toString(StandardCharsets.UTF_8);
    }

    private static void withProperty(String value, Runnable body) {
        String previous = System.getProperty(InputTrace.PROPERTY);
        if (value == null) {
            System.clearProperty(InputTrace.PROPERTY);
        } else {
            System.setProperty(InputTrace.PROPERTY, value);
        }
        try {
            body.run();
        } finally {
            if (previous == null) {
                System.clearProperty(InputTrace.PROPERTY);
            } else {
                System.setProperty(InputTrace.PROPERTY, previous);
            }
        }
    }

    @Test
    void disabledByDefault() {
        Assumptions.assumeTrue(System.getenv(InputTrace.ENV) == null,
                "本机设了 " + InputTrace.ENV + "，跳过默认关闭的判定");

        withProperty(null, () -> {
            try (InputTrace trace = InputTrace.of(err())) {
                trace.note("terminal=...");
                trace.input("hello");
                assertThat(trace.enabled()).isFalse();
                assertThat(errorText()).isEmpty();
            }
        });
    }

    @Test
    void writesOneLinePerInputAndEscapesNewlines(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("input-trace.log");

        withProperty(file.toString(), () -> {
            try (InputTrace trace = InputTrace.of(err())) {
                assertThat(trace.enabled()).isTrue();
                trace.note("terminal=org.jline.terminal.impl.DumbTerminal type=dumb");
                trace.input("第一行\n第二行");
                trace.input(null);
            }
        });

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).contains("| ").contains("terminal=org.jline");
        // 换行必须转义成 \n：否则「一次输入」会被写散成多行，看不出它是整体还是被拆过
        assertThat(lines.get(1)).contains("> ").contains("第一行\\n第二行").doesNotContain("\n");
        assertThat(lines.get(1)).contains("len=7");
        assertThat(lines.get(2)).contains("<null>");
        assertThat(errorText()).isEmpty();
    }

    @Test
    void keepsChineseInUtf8(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("trace.log");

        withProperty(file.toString(), () -> {
            try (InputTrace trace = InputTrace.of(err())) {
                trace.input("中文内容");
            }
        });

        assertThat(Files.readString(file, StandardCharsets.UTF_8)).contains("中文内容");
    }

    @Test
    void unwritableTargetOnlyDegrades(@TempDir Path dir) {
        // 指向一个目录：打开必然失败 —— 此时只提示一句，绝不让留痕拖垮交互
        withProperty(dir.toString(), () -> {
            try (InputTrace trace = InputTrace.of(err())) {
                trace.input("hello");
                assertThat(trace.enabled()).isFalse();
            }
        });

        assertThat(errorText()).contains("输入留痕开启失败");
    }
}
