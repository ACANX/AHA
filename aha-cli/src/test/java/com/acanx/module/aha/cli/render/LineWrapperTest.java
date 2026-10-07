package com.acanx.module.aha.cli.render;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LineWrapper} 测试：按显示宽度折行。
 *
 * @since 0.1.0
 */
class LineWrapperTest {

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    private LineWrapper wrapper(int width) {
        return new LineWrapper(new PrintStream(buffer, true, StandardCharsets.UTF_8), width, "\n");
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }

    @Test
    void widthZeroDisablesWrapping() {
        LineWrapper wrapper = wrapper(0);
        wrapper.write("x".repeat(200));
        assertThat(output()).isEqualTo("x".repeat(200));
        assertThat(wrapper.column()).isEqualTo(200);
    }

    @Test
    void wrapsAsciiAtWidth() {
        LineWrapper wrapper = wrapper(5);
        wrapper.write("abcdefghij");
        assertThat(output()).isEqualTo("abcde\nfghij");
        assertThat(wrapper.column()).isEqualTo(5);
    }

    @Test
    void neverSplitsAWideCharacter() {
        // 宽度 5：两个汉字占 4 列，第三个汉字放不下，应整体换行而不是拆成半个字
        LineWrapper wrapper = wrapper(5);
        wrapper.write("你好世");
        assertThat(output()).isEqualTo("你好\n世");
        assertThat(wrapper.column()).isEqualTo(2);
    }

    @Test
    void wrappingIsIncrementalAcrossWrites() {
        // 流式输出按分片到达：折行必须跨分片记住列号
        LineWrapper wrapper = wrapper(4);
        wrapper.write("ab");
        wrapper.write("cd");
        wrapper.write("ef");
        assertThat(output()).isEqualTo("abcd\nef");
        assertThat(wrapper.column()).isEqualTo(2);
    }

    @Test
    void embeddedNewlineResetsColumn() {
        LineWrapper wrapper = wrapper(10);
        wrapper.write("hello\nworld");
        assertThat(output()).isEqualTo("hello\nworld");
        assertThat(wrapper.column()).isEqualTo(5);
    }

    @Test
    void escapeSequencesDoNotCountTowardWidth() {
        LineWrapper wrapper = wrapper(4);
        wrapper.write("\u001B[36mabcd\u001B[0m");
        // 转义序列不占列，因此不会在序列中间插入换行
        assertThat(output()).isEqualTo("\u001B[36mabcd\u001B[0m");
        assertThat(wrapper.column()).isEqualTo(4);
    }

    @Test
    void ensureLineStartOnlyBreaksWhenMidLine() {
        LineWrapper wrapper = wrapper(0);
        wrapper.ensureLineStart();
        assertThat(output()).isEmpty();

        wrapper.write("x");
        wrapper.ensureLineStart();
        wrapper.ensureLineStart();
        assertThat(output()).isEqualTo("x\n");
    }
}
