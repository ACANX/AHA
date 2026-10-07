package com.acanx.module.aha.cli.tty;

import org.junit.jupiter.api.Test;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AboveStream} 测试。
 *
 * @since 0.1.0
 */
class AboveStreamTest {

    private final List<String> lines = new ArrayList<>();

    private AboveStream stream() {
        return new AboveStream(lines::add, StandardCharsets.UTF_8);
    }

    @Test
    void emitsOnNewlineOnly() {
        AboveStream stream = stream();

        // OutputStream.write(int) 收的是字节，不是 char
        byte[] bytes = "你好".getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            stream.write(b);
        }
        assertThat(lines).isEmpty();

        stream.write('\n');
        assertThat(lines).containsExactly("你好");
    }

    @Test
    void dropsCarriageReturn() {
        // 该模式不做原地重绘，\r 留着会污染输出
        AboveStream stream = stream();

        byte[] bytes = "abc\rdef\n".getBytes(StandardCharsets.UTF_8);
        stream.write(bytes, 0, bytes.length);

        assertThat(lines).containsExactly("abcdef");
    }

    @Test
    void handlesMultibyteCharactersSplitAcrossWrites() {
        byte[] bytes = "中文".getBytes(StandardCharsets.UTF_8);
        AboveStream stream = stream();

        // 逐字节写入：多字节字符被拆开也不能乱码
        for (byte b : bytes) {
            stream.write(b);
        }
        stream.write('\n');

        assertThat(lines).containsExactly("中文");
    }

    @Test
    void flushEmitsTrailingPartialLine() {
        AboveStream stream = stream();

        for (byte b : "尾".getBytes(StandardCharsets.UTF_8)) {
            stream.write(b);
        }
        stream.flush();

        assertThat(lines).containsExactly("尾");
        // flush 后缓冲已清空，重复 flush 不应重复输出
        stream.flush();
        assertThat(lines).containsExactly("尾");
    }

    @Test
    void printsThroughPrintStreamWithoutAutoFlush() {
        // 真实用法：PrintStream(autoFlush=false) 包住本流。
        // PrintStream 一旦开启 autoFlush，写 byte[] 就会触发 flush，
        // 半行会被当成整行推上去，折行随之失效——这里锁定这个约定。
        AboveStream stream = stream();
        PrintStream out = new PrintStream(stream, false, StandardCharsets.UTF_8);

        out.print("第一行");
        assertThat(lines).isEmpty();
        out.print("\n第二行");
        assertThat(lines).containsExactly("第一行");
        out.flush();
        assertThat(lines).containsExactly("第一行", "第二行");
    }
}
