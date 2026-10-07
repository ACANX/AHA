package com.acanx.module.aha.cli.tty;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.function.Consumer;

/**
 * 行缓冲输出流：把字节流按行切分后交给回调。
 *
 * <p>服务于「流式期间保持输入可用」：渲染器照旧写 {@code PrintStream}，
 * 但每完成一行就经 {@code LineReader.printAbove} 打到输入行**上方**，
 * 输入行因此始终留在底部、始终可编辑。</p>
 *
 * <p>两条刻意的取舍：</p>
 *
 * <ul>
 *   <li><b>只按 {@code \n} 切分，丢弃 {@code \r}</b>。该模式下不做原地重绘
 *       （瞬时状态行会关闭），{@code \r} 留着只会污染输出。</li>
 *   <li><b>不整行不吐</b>。调用方的 {@code PrintStream} 必须关掉 autoFlush——
 *       {@code PrintStream} 在「写 byte[]」时也会 flush，若不关就会把半行当整行推上去，
 *       折行随之失效。收尾由调用方在回合结束时显式 {@code flush()}。</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class AboveStream extends OutputStream {

    private final Consumer<String> lines;

    private final Charset charset;

    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

    /**
     * 创建行缓冲流。
     *
     * @param lines   每完成一行的回调
     * @param charset 字节到字符串的编码（应与控制台一致）
     */
    public AboveStream(Consumer<String> lines, Charset charset) {
        this.lines = lines;
        this.charset = charset == null ? Charset.defaultCharset() : charset;
    }

    @Override
    public synchronized void write(int b) {
        if (b == '\n') {
            emit();
        } else if (b != '\r') {
            buffer.write(b);
        }
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
        for (int i = offset; i < offset + length; i++) {
            write(bytes[i]);
        }
    }

    /**
     * 把尚未成行的内容也推出去。
     *
     * <p>回合结束时调用：流式正文的最后一行往往没有换行符，
     * 不 flush 就会一直压在缓冲区里不显示。</p>
     */
    @Override
    public synchronized void flush() {
        if (buffer.size() > 0) {
            emit();
        }
    }

    /**
     * 推出一行并清空缓冲。
     */
    private void emit() {
        lines.accept(buffer.toString(charset));
        buffer.reset();
    }
}
