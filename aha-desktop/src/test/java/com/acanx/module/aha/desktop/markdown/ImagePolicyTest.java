package com.acanx.module.aha.desktop.markdown;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ImagePolicy} 测试（issue #423）：4 类地址输入的判定。
 *
 * @since 0.2.0
 */
class ImagePolicyTest {

    @Test
    void allowedSchemes() {
        assertThat(ImagePolicy.allowed("file:///tmp/a.png")).isTrue();
        assertThat(ImagePolicy.allowed("https://example.com/a.png")).isTrue();
        assertThat(ImagePolicy.allowed("HTTPS://example.com/a.png")).isTrue();
    }

    @Test
    void rejectedSchemes() {
        assertThat(ImagePolicy.allowed("http://example.com/a.png")).isFalse();
        assertThat(ImagePolicy.allowed("data:image/png;base64,AAAA")).isFalse();
        assertThat(ImagePolicy.allowed("a.png")).isFalse();
        assertThat(ImagePolicy.allowed("")).isFalse();
        assertThat(ImagePolicy.allowed(null)).isFalse();
    }

    @Test
    void rejectReasonNamesTheScheme() {
        assertThat(ImagePolicy.rejectReason("http://x/a.png")).contains("http:");
        assertThat(ImagePolicy.rejectReason("data:image/png;base64,AA")).contains("data:");
        assertThat(ImagePolicy.rejectReason("a.png")).contains("缺少协议");
        assertThat(ImagePolicy.rejectReason("")).contains("为空");
        assertThat(ImagePolicy.rejectReason("https://x/a.png")).isEmpty();
    }
}
