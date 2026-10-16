package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * SHA-256 摘要与 {@code SHA256SUMS} 清单（issue #51 §4.3 第①层、§6）。
 *
 * <p>{@code SHA256SUMS} 是发布页上的一个资产，每行 {@code <sha256>  <文件名>}
 * （两空格，同 {@code sha256sum} 的文本模式；也接受 {@code *} 二进制模式前缀）。
 * 它给出的是<b>端到端</b>的完整性：下载是否被截断、传输是否被篡改。</p>
 *
 * <p><b>「没有校验和」与「校验和不符」是两件事</b>：前者说明该版本还没提供 {@code SHA256SUMS}
 * （现在就是这状态），界面要照实说「该版本未提供发布页校验和」并退到包内清单 + 落地前自证，
 * 不能假装验过（§4.3 的注）；后者是硬失败 —— <b>不落地</b>。</p>
 *
 * @since 0.2.0
 */
public final class Checksums {

    /** 摘要算法。 */
    public static final String ALGORITHM = "SHA-256";

    /** 十六进制摘要长度（32 字节）。 */
    private static final int HEX_LENGTH = 64;

    private Checksums() {
    }

    /**
     * 计算文件的 SHA-256（流式，不吃内存）。
     *
     * @param file 文件
     * @return 小写十六进制摘要
     * @throws UpdateException 读文件失败（{@link UpdateException#CODE_DOWNLOAD_FAILED}）
     */
    public static String sha256(Path file) {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[64 * 1024];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException e) {
            throw new UpdateException(UpdateException.CODE_DOWNLOAD_FAILED,
                    "读取文件失败：" + file + "（" + e.getMessage() + "）", e);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * 解析 {@code SHA256SUMS} 文本。
     *
     * <p>同名重复时保留<b>首次</b>出现的那一行：同名的两行是不该发生的，静默取最后一行会让
     * 「谁覆盖了谁」无从追查。</p>
     *
     * @param text 文本内容
     * @return 文件名 → 摘要（小写十六进制），保持文件里的顺序
     * @throws UpdateException 存在格式不合法的行（{@link UpdateException#CODE_INVALID_RESPONSE}）
     */
    public static Map<String, String> parse(String text) {
        Map<String, String> sums = new LinkedHashMap<>();
        int lineNo = 0;
        for (String raw : text.split("\n")) {
            lineNo++;
            String line = raw.replace("\r", "").trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separator = line.indexOf(' ');
            if (separator < 0) {
                throw malformed(lineNo, line);
            }
            String digest = line.substring(0, separator).toLowerCase(Locale.ROOT);
            // `sha256sum` 的二进制模式会写 ` *文件名`
            String name = line.substring(separator).trim();
            if (name.startsWith("*")) {
                name = name.substring(1);
            }
            if (digest.length() != HEX_LENGTH || !digest.chars().allMatch(Checksums::isHexDigit)
                    || name.isEmpty()) {
                throw malformed(lineNo, line);
            }
            sums.putIfAbsent(name, digest);
        }
        return Map.copyOf(sums);
    }

    /**
     * 在清单里找某个资产的摘要。
     *
     * @param sums      清单
     * @param assetName 资产名
     * @return 摘要；清单里没有这一项时为空（**不是**错，见类注释）
     */
    public static Optional<String> digestOf(Map<String, String> sums, String assetName) {
        return Optional.ofNullable(sums.get(assetName));
    }

    /**
     * 校验文件摘要，不符即抛。
     *
     * <p>调用方必须<b>先校验再改名</b>：本方法只报告，不负责删除；「不落地」是由「校验通过才把
     * {@code .part} 改名」这条流程保证的（见 {@link PackageDownloader}）。</p>
     *
     * @param file           文件（通常是 {@code .part}）
     * @param expectedDigest 期望摘要（小写十六进制）
     * @param assetName      资产名（用于错误信息）
     * @return 实际摘要
     * @throws UpdateException 摘要不符（{@link UpdateException#CODE_CHECKSUM_MISMATCH}）
     */
    public static String verify(Path file, String expectedDigest, String assetName) {
        String actual = sha256(file);
        if (!actual.equalsIgnoreCase(expectedDigest)) {
            throw new UpdateException(UpdateException.CODE_CHECKSUM_MISMATCH,
                    "校验和不符，已拒绝落地：" + assetName + "；期望 " + expectedDigest
                            + "，实际 " + actual + "（下载可能被截断或篡改，请重新下载）");
        }
        return actual;
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // 任何合规 JDK 都必然带 SHA-256；真缺了就是环境坏了，直接抛比兜底更诚实。
            throw new IllegalStateException(ALGORITHM + " 不可用", e);
        }
    }

    private static UpdateException malformed(int lineNo, String line) {
        return new UpdateException(UpdateException.CODE_INVALID_RESPONSE,
                "SHA256SUMS 第 " + lineNo + " 行格式不合法（应为「<sha256>  <文件名>」）：" + line);
    }

    private static boolean isHexDigit(int character) {
        return (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f');
    }
}
