package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SHA-256 摘要与 {@code SHA256SUMS} 解析（issue #51 §4.3 第①层）。
 *
 * <p>摘要值全部是**外部**算出来的（用系统的 {@code sha256sum} 算好写死在本文件里），
 * 不用本类的实现去验本类 —— 否则「算错了」也会自洽地通过测试。</p>
 *
 * @since 0.2.0
 */
class ChecksumsTest {

    /** 外部算出的 {@code "hello aha\n"} 的 SHA-256。 */
    private static final String HELLO_SHA256 =
            "b03dfa71b46c03142b3406dc866d4bc594b427dec3a02af3d19360afbdf0be1f";

    /** 外部算出的 {@code "aha\n"} 的 SHA-256。 */
    private static final String AHA_SHA256 =
            "3bd2dc359589f4780839b04aec1c3f547ab0dc0b1acd26511669727aaaafd20d";

    @TempDir
    Path tempDir;

    @Test
    void computesSha256ThatMatchesAnExternallyComputedValue() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello aha\n", StandardCharsets.UTF_8);

        assertThat(Checksums.sha256(file)).isEqualTo(HELLO_SHA256);
    }

    @Test
    void parsesSumsFileWithBothTextAndBinaryLinesAndComments() {
        String text = """
                # 这是注释
                %s  aha-cli-0.1.3.00242.zip

                %s *aha-desktop-0.1.3.00242-windows-amd64.zip\r
                """.formatted(HELLO_SHA256, AHA_SHA256);

        Map<String, String> sums = Checksums.parse(text);

        assertThat(sums).containsOnlyKeys("aha-cli-0.1.3.00242.zip",
                "aha-desktop-0.1.3.00242-windows-amd64.zip");
        assertThat(Checksums.digestOf(sums, "aha-cli-0.1.3.00242.zip")).contains(HELLO_SHA256);
        // `*` 二进制模式前缀要被剥掉，文件名里不含星号
        assertThat(Checksums.digestOf(sums, "aha-desktop-0.1.3.00242-windows-amd64.zip"))
                .contains(AHA_SHA256);
    }

    @Test
    void keepsTheFirstLineWhenTheSameNameAppearsTwice() {
        Map<String, String> sums = Checksums.parse(HELLO_SHA256 + "  a.zip\n" + AHA_SHA256 + "  a.zip\n");

        assertThat(Checksums.digestOf(sums, "a.zip")).contains(HELLO_SHA256);
    }

    @Test
    void rejectsMalformedLines() {
        assertThatThrownBy(() -> Checksums.parse("这不是一行校验和\n"))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("第 1 行")
                .hasMessageContaining("格式不合法")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_INVALID_RESPONSE));
        // 摘要长度不对
        assertThatThrownBy(() -> Checksums.parse("abc  a.zip\n"))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("第 1 行");
        // 有名字但没摘要
        assertThatThrownBy(() -> Checksums.parse("  a.zip\n"))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("第 1 行");
    }

    @Test
    void digestOfIsEmptyWhenTheAssetIsNotListed() {
        Map<String, String> sums = Checksums.parse(HELLO_SHA256 + "  a.zip\n");

        // 清单里没有这一项是「该版本未提供这个资产的校验和」，不是错误
        assertThat(Checksums.digestOf(sums, "b.zip")).isEmpty();
    }

    @Test
    void verifyPassesAndReturnsTheActualDigest() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello aha\n", StandardCharsets.UTF_8);

        assertThat(Checksums.verify(file, HELLO_SHA256.toUpperCase(), "hello.txt"))
                .isEqualTo(HELLO_SHA256);
    }

    @Test
    void verifyThrowsOnMismatchAndSaysItWontBeInstalled() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "hello aha\n", StandardCharsets.UTF_8);

        assertThatThrownBy(() -> Checksums.verify(file, AHA_SHA256, "hello.txt"))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("校验和不符")
                .hasMessageContaining("已拒绝落地")
                .hasMessageContaining(HELLO_SHA256)
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_CHECKSUM_MISMATCH));
    }

    @Test
    void readingAMissingFileIsReportedAsDownloadFailure() {
        assertThatThrownBy(() -> Checksums.sha256(tempDir.resolve("nope.txt")))
                .isInstanceOf(UpdateException.class)
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_DOWNLOAD_FAILED));
    }
}
