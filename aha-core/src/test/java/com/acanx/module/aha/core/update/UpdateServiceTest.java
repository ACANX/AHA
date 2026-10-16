package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 升级服务的发现 → 挑资产 → 下载 → 校验 → 解包 → 落暂存（issue #51 §4.1 ~ §4.3）。
 *
 * <p>这是 P1 与 P2 的接缝：前面每个类各有单测，这里验「按真实顺序串起来还成立」——
 * 而且是**离线**的（本机 mock 服务器 + 现造的包），符合 `TestingSpec.md` §5.1 的环境无关性要求。</p>
 *
 * @since 0.2.0
 */
class UpdateServiceTest {

    private static final AhaVersion TARGET = AhaVersion.parse("0.1.3.00243");

    private static final AhaVersion CURRENT = AhaVersion.parse("0.1.3.00242");

    private static final PlatformTag WINDOWS = PlatformTag.of("Windows 11", "amd64");

    private static final String ASSET_NAME = "aha-desktop-0.1.3.00243-windows-amd64.zip";

    @TempDir
    Path tempDir;

    private MiniServer server;

    private Map<String, byte[]> served;

    @BeforeEach
    void setUp() throws IOException {
        served = new LinkedHashMap<>();
        server = new MiniServer(served);
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    /** 造一个真包（含包内清单）并把它和发布页响应一起挂到 mock 服务器上。 */
    private void givenAPublishedPackage() throws IOException {
        byte[] jar = "jar-bytes".getBytes(StandardCharsets.UTF_8);
        Path packageZip = tempDir.resolve(ASSET_NAME);
        String manifest = """
                {"kind":"desktop-jvm","version":"0.1.3.00243","platform":"windows-amd64","jdk":25,
                 "files":[{"path":"aha-desktop.jar","sha256":"%s","size":%d}]}
                """.formatted(sha256(jar), jar.length);
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(packageZip))) {
            out.putNextEntry(new ZipEntry("aha-desktop.jar"));
            out.write(jar);
            out.closeEntry();
            out.putNextEntry(new ZipEntry("AHA-PACKAGE.json"));
            out.write(manifest.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        byte[] zipBytes = Files.readAllBytes(packageZip);
        String zipDigest = sha256(zipBytes);

        served.put("/assets/" + ASSET_NAME, zipBytes);
        byte[] sums = (zipDigest + "  " + ASSET_NAME + "\n").getBytes(StandardCharsets.UTF_8);
        served.put("/assets/SHA256SUMS", sums);
        served.put("/repos/ACANX/AHA/releases?per_page=100&page=1",
                releasesJson(zipBytes.length, sums.length));
    }

    private byte[] releasesJson(int zipSize, int sumsSize) {
        return """
                [
                  {"tag_name":"V0.1.3.00243-aha-jvm","prerelease":true,"draft":false,
                   "published_at":"2026-10-16T03:25:00Z","assets":[
                     {"name":"%s","size":%d,"browser_download_url":"%s/assets/%s","state":"uploaded"},
                     {"name":"SHA256SUMS","size":%d,"browser_download_url":"%s/assets/SHA256SUMS","state":"uploaded"}]},
                  {"tag_name":"V0.1.3.00242-aha-jvm","prerelease":true,"draft":false,
                   "published_at":"2026-10-16T02:00:00Z","assets":[
                     {"name":"aha-desktop-0.1.3.00242-windows-amd64.zip","size":1,
                      "browser_download_url":"%s/assets/old.zip","state":"uploaded"}]}
                ]
                """.formatted(ASSET_NAME, zipSize, server.base(), ASSET_NAME, sumsSize, server.base(),
                        server.base())
                .getBytes(StandardCharsets.UTF_8);
    }

    private UpdateService service() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return new UpdateService(UpdatePaths.at(tempDir.resolve("update")),
                new GitHubReleaseClient(server.base(), GitHubReleaseClient.DEFAULT_REPOSITORY, 100, http,
                        tools.jackson.databind.json.JsonMapper.builder().build()),
                new PackageDownloader(http, Set.of("127.0.0.1"), server.base().toString(),
                        Duration.ofMillis(1)));
    }

    @Test
    void checksFindsTheNewestVersionWithAnAssetForThisPlatform() throws IOException {
        givenAPublishedPackage();

        UpdateService.Check check = service().check(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                WINDOWS, CURRENT);

        assertThat(check.release().version()).isEqualTo(TARGET);
        assertThat(check.asset().name()).isEqualTo(ASSET_NAME);
        assertThat(check.newer()).isTrue();
        assertThat(check.platform()).isEqualTo(WINDOWS);
    }

    @Test
    void stageDownloadsVerifiesUnpacksAndChecksTheManifest() throws IOException {
        givenAPublishedPackage();
        UpdateService service = service();

        UpdateService.Check check = service.check(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                WINDOWS, CURRENT);
        UpdateService.Staged staged = service.stage(check, PackageDownloader.ProgressListener.NONE,
                PackageDownloader.CancelSignal.NEVER);

        assertThat(staged.version()).isEqualTo(TARGET);
        assertThat(staged.packageRoot().resolve("aha-desktop.jar")).exists();
        assertThat(staged.manifest().fileList()).hasSize(1);
        // 正式 zip 已落地、没有残留 .part
        assertThat(staged.packageZip()).exists();
        assertThat(staged.packageZip().resolveSibling("pkg.zip.part")).doesNotExist();
        // 日志留痕（§4.7）
        assertThat(service.log().lines()).anySatisfy(line -> assertThat(line).contains("[check]"))
                .anySatisfy(line -> assertThat(line).contains("[download]"))
                .anySatisfy(line -> assertThat(line).contains("[unpack]"));
    }

    @Test
    void stageRefusesAPackageWhoseChecksumDoesNotMatch() throws IOException {
        givenAPublishedPackage();
        // 把发布页上的校验和改掉（模拟「下载被截断或篡改」）
        served.put("/assets/SHA256SUMS",
                ("0".repeat(64) + "  " + ASSET_NAME + "\n").getBytes(StandardCharsets.UTF_8));
        UpdateService service = service();
        UpdateService.Check check = service.check(ReleaseChannel.BETA, UpdateKind.DESKTOP_JVM,
                WINDOWS, CURRENT);

        assertThatThrownBy(() -> service.stage(check, PackageDownloader.ProgressListener.NONE,
                PackageDownloader.CancelSignal.NEVER))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("校验和不符")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_CHECKSUM_MISMATCH));

        // 不落地：没有正式 zip，也没有解包目录
        assertThat(service.staging().packageZip(TARGET)).doesNotExist();
        assertThat(service.staging().newPackageDir(TARGET)).doesNotExist();
    }

    @Test
    void checkFailsLoudlyWhenNoVersionHasAnAssetForThisChannel() throws IOException {
        // 发布页上只有 dev 线的 -aha-jvm；Stable 通道（正式发版）此刻没有任何资产
        givenAPublishedPackage();

        assertThatThrownBy(() -> service().check(ReleaseChannel.STABLE, UpdateKind.DESKTOP_JVM,
                WINDOWS, CURRENT))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("暂无")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_ASSET_MISSING));
    }

    @Test
    void listReturnsCandidatesNewestFirst() throws IOException {
        givenAPublishedPackage();

        List<ReleaseFilter.ChannelRelease> candidates = service().list(ReleaseChannel.BETA,
                UpdateKind.DESKTOP_JVM);

        assertThat(candidates).extracting(release -> release.version().value())
                .containsExactly("0.1.3.00243", "0.1.3.00242");
    }

    /** 用 JDK 自己算摘要：不借 {@code Checksums}，否则等于「自己验自己」。 */
    private static String sha256(byte[] content) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(content));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    // ── 测试脚手架：按路径返回预置字节的极简服务器 ────────────────────────────

    private static final class MiniServer implements AutoCloseable {

        private final ServerSocket serverSocket;

        private final Thread thread;

        private final Map<String, byte[]> served;

        private final AtomicInteger calls = new AtomicInteger();

        MiniServer(Map<String, byte[]> served) throws IOException {
            this.served = served;
            this.serverSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
            this.thread = Thread.ofVirtual().name("mini-update").start(this::serve);
        }

        URI base() {
            return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort());
        }

        int callCount() {
            return calls.get();
        }

        @Override
        public void close() {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
                // 关闭失败可忽略
            }
            thread.interrupt();
        }

        private void serve() {
            while (!serverSocket.isClosed()) {
                try (Socket socket = serverSocket.accept()) {
                    handle(socket);
                } catch (IOException e) {
                    return;
                }
            }
        }

        private void handle(Socket socket) throws IOException {
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = new BufferedOutputStream(socket.getOutputStream());
            String requestLine = readLine(in);
            if (requestLine == null) {
                return;
            }
            String line;
            while ((line = readLine(in)) != null && !line.isEmpty()) {
                // 只发 GET，没有请求体
            }
            String path = requestLine.split(" ")[1];
            calls.incrementAndGet();
            byte[] body = served.get(path);
            if (body == null) {
                respond(out, 404, "not found".getBytes(StandardCharsets.UTF_8));
            } else {
                respond(out, 200, body);
            }
            out.flush();
        }

        private static void respond(OutputStream out, int status, byte[] body) throws IOException {
            String head = "HTTP/1.1 " + status + " STATUS\r\n"
                    + "Content-Type: application/octet-stream\r\n"
                    + "Content-Length: " + body.length + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(head.getBytes(StandardCharsets.ISO_8859_1));
            out.write(body);
        }

        private static String readLine(InputStream in) throws IOException {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') {
                    return buffer.toString(StandardCharsets.ISO_8859_1).trim();
                }
                buffer.write(b);
            }
            return buffer.size() == 0 ? null : buffer.toString(StandardCharsets.ISO_8859_1).trim();
        }
    }
}
