package com.acanx.module.aha.cli.tty;

import java.io.Console;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;

/**
 * 标准输出的字符编码探测与符号降级。
 *
 * <p>Windows 控制台按<b>代码页</b>解释字节：中文环境为 936（GBK），英文环境常为 437 / 1252。
 * JVM 写 stdout 所用的编码与之一致（{@code stdout.encoding}），因此中文在中文控制台上是正常的，
 * 但<b>代码页之外的字符会变成 {@code ?}</b>——AHA 的提示符 {@code ❯}（U+276F）不在 GBK 内，
 * 就是典型例子。英文 Windows 上则连中文都表示不了。</p>
 *
 * <p>本类探测两件事，都用 {@link Charset#newEncoder()}{@code .canEncode(...)} 实测而非猜测：</p>
 * <ul>
 *   <li>{@code symbols} —— 能否表示装饰符号，决定提示符是否降级为 ASCII</li>
 *   <li>{@code cjk} —— 能否表示 CJK，决定是否需要提示用户切换控制台编码</li>
 * </ul>
 *
 * <p>刻意<b>不</b>在启动脚本里自动 {@code chcp 65001}：PowerShell 5.1 会缓存
 * {@code [Console]::OutputEncoding}，子进程改代码页后它仍按旧编码解码，反而更乱。
 * 因此只探测、提示与降级，是否切换交给用户。</p>
 *
 * @param charset JVM 写 stdout 使用的字符集，无法确定时为 {@code null}
 * @param cjk     能否表示 CJK 字符
 * @param symbols 能否表示 AHA 的装饰符号
 * @since 0.1.0
 */
public record OutputEncoding(Charset charset, boolean cjk, boolean symbols) {

    /** AHA 使用的非 ASCII 装饰符号。 */
    public static final String DECORATION = "❯✓•─│├└";

    /** CJK 采样文本。 */
    public static final String CJK_SAMPLE = "中文字符";

    /** 依次尝试的系统属性：stdout 的编码优先，其次平台原生编码，最后兜底。 */
    private static final String[] ENCODING_KEYS = {"stdout.encoding", "native.encoding", "file.encoding"};

    /**
     * 探测标准输出编码。
     *
     * @return 探测结果
     */
    public static OutputEncoding detect() {
        String name = encodingName();
        Charset charset = resolve(name);
        return new OutputEncoding(charset,
                canEncode(charset, CJK_SAMPLE),
                canEncode(charset, DECORATION));
    }


    /**
     * 成功标记。
     *
     * @return 支持装饰符号时为 {@code "✓"}，否则退化为 {@code "[ok]"}
     */
    public String check() {
        return symbols ? "✓" : "[ok]";
    }

    /**
     * 列表符号。
     *
     * @return 支持装饰符号时为 {@code "•"}，否则保留 Markdown 原有的 {@code "-"}
     */
    public String bullet() {
        return symbols ? "•" : "-";
    }

    /**
     * 是否需要建议用户把控制台切换到 UTF-8。
     *
     * @return 无法表示 CJK 时为 {@code true}
     */
    public boolean recommendUtf8() {
        return !cjk;
    }

    /**
     * 字符集名，用于诊断输出。
     *
     * @return 字符集名
     */
    public String charsetName() {
        return charset == null ? "unknown" : charset.name();
    }

    /**
     * JVM 写 stdout 所用的编码名。
     *
     * <p>有控制台时以 {@link Console#charset()} 为准——它才是真正决定字节形态的依据。</p>
     *
     * @return 编码名；均不可得时返回 {@code null}
     */
    static String encodingName() {
        Console console = System.console();
        if (console != null) {
            try {
                Charset charset = console.charset();
                if (charset != null) {
                    return charset.name();
                }
            } catch (UnsupportedOperationException ignored) {
                // 无控制台时 JDK 会抛此异常，继续走系统属性
            }
        }
        for (String key : ENCODING_KEYS) {
            String value = System.getProperty(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    /**
     * 解析字符集。
     *
     * @param name 字符集名
     * @return 字符集；名称非法或不可用时返回 {@code null}
     */
    static Charset resolve(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Charset.forName(name);
        } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
            return null;
        }
    }

    /**
     * 实测字符集能否表示采样文本。
     *
     * <p>字符集不可得时返回 {@code true}：宁可少降级、不误降级，
     * 也不必因为无法判断而去打扰用户。</p>
     *
     * @param charset 字符集，可为 {@code null}
     * @param sample  采样文本
     * @return 能否表示
     */
    static boolean canEncode(Charset charset, String sample) {
        return charset == null || charset.newEncoder().canEncode(sample);
    }
}
