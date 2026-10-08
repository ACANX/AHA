package com.acanx.module.aha.desktop.view;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LogoImage} 的资源测试（不需要图形环境）。
 *
 * <p>只验证 PNG 本身：存在、是真 PNG、是正方形、尺寸够用。刻意**不锁定精确像素**——
 * 物理像素数取决于生成时机器的缩放（{@code bin/GenLogoPng.py} 的输出 = 逻辑尺寸 x 系统缩放），
 * 锁死会让「换台机器重新生成」变成一次假失败。</p>
 *
 * @since 0.1.1
 */
class LogoImageTest {

    private static final String RESOURCE = "logo.png";

    private static final int MIN_SIZE = 128;

    @Test
    void logoResourceExistsAndIsPng() throws Exception {
        try (InputStream in = LogoImage.class.getResourceAsStream(RESOURCE)) {
            assertThat(in).as("资源 %s 应随模块打包", RESOURCE).isNotNull();
            byte[] head = in.readNBytes(24);
            assertThat(head).hasSizeGreaterThanOrEqualTo(24);
            // PNG 签名
            assertThat(new String(head, 0, 8, StandardCharsets.ISO_8859_1))
                    .isEqualTo("\u0089PNG\r\n\u001a\n");
        }
    }

    @Test
    void logoIsSquareAndBigEnoughForIcon() throws Exception {
        try (InputStream in = LogoImage.class.getResourceAsStream(RESOURCE)) {
            byte[] data = in.readAllBytes();
            // IHDR 紧跟签名与长度/类型：宽高在前 24 字节内
            int width = readInt(data, 16);
            int height = readInt(data, 20);
            assertThat(width).isEqualTo(height).isGreaterThanOrEqualTo(MIN_SIZE);
            // 占位图（纯色小块）通常几 KB 级；这里至少要像一张真标志
            assertThat(data.length).isGreaterThan(5 * 1024);
        }
    }

    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }
}
