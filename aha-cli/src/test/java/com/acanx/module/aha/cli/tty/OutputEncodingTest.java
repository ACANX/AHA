package com.acanx.module.aha.cli.tty;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link OutputEncoding} 测试：控制台编码探测与符号降级。
 *
 * @since 0.1.0
 */
class OutputEncodingTest {

    @Test
    void utf8CanEncodeEverything() {
        assertThat(OutputEncoding.canEncode(StandardCharsets.UTF_8, OutputEncoding.DECORATION)).isTrue();
        assertThat(OutputEncoding.canEncode(StandardCharsets.UTF_8, OutputEncoding.CJK_SAMPLE)).isTrue();

        OutputEncoding encoding = new OutputEncoding(StandardCharsets.UTF_8, true, true);
        assertThat(encoding.check()).isEqualTo("✓");
        assertThat(encoding.recommendUtf8()).isFalse();
        assertThat(encoding.charsetName()).isEqualTo("UTF-8");
    }

    @Test
    void gbkKeepsCjkButCannotEncodeDecoration() {
        // 中文 Windows 的典型情形：中文正常，但 ❯（U+276F）不在 GBK 内
        Charset gbk = Charset.forName("GBK");
        assertThat(OutputEncoding.canEncode(gbk, OutputEncoding.CJK_SAMPLE)).isTrue();
        assertThat(OutputEncoding.canEncode(gbk, OutputEncoding.DECORATION)).isFalse();

        OutputEncoding encoding = new OutputEncoding(gbk, true, false);
        // 符号降级为 ASCII，中文照常显示，因此无需提示用户切换编码
        assertThat(encoding.check()).isEqualTo("[ok]");
        assertThat(encoding.recommendUtf8()).isFalse();
    }

    @Test
    void asciiConsoleTriggersUtf8Recommendation() {
        // 英文 Windows（代码页 437/1252）连中文都表示不了，只能提示用户切换
        OutputEncoding encoding = new OutputEncoding(StandardCharsets.US_ASCII, false, false);
        assertThat(encoding.recommendUtf8()).isTrue();
    }

    @Test
    void gb18030CoversEverythingSoNoDegradationIsNeeded() {
        // GB18030 覆盖全部 Unicode，因此无需降级
        Charset gb18030 = Charset.forName("GB18030");
        assertThat(OutputEncoding.canEncode(gb18030, OutputEncoding.DECORATION)).isTrue();
        assertThat(OutputEncoding.canEncode(gb18030, OutputEncoding.CJK_SAMPLE)).isTrue();
    }

    @Test
    void unresolvableCharsetIsTreatedAsCapable() {
        // 无法判定时不降级、也不打扰用户
        assertThat(OutputEncoding.canEncode(null, OutputEncoding.DECORATION)).isTrue();
    }

    @Test
    void resolveHandlesUnknownAndBlankNames() {
        assertThat(OutputEncoding.resolve("UTF-8")).isEqualTo(StandardCharsets.UTF_8);
        assertThat(OutputEncoding.resolve("no-such-charset")).isNull();
        assertThat(OutputEncoding.resolve(null)).isNull();
        assertThat(OutputEncoding.resolve("  ")).isNull();
    }

    @Test
    void detectAlwaysReturnsUsableResult() {
        OutputEncoding encoding = OutputEncoding.detect();
        assertThat(encoding.charsetName()).isNotBlank();
    }
}
