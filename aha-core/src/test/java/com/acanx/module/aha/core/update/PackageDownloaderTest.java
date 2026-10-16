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
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 下载器：{@code .part} 改名、缓存复用、重试边界、取消、白名单（issue #51 §4.2 / §4.3 / §4.7）。
 *
 * <p>全部跑在本机 mock 服务器上（不打真网络）。退避时长注入成 1ms，测试才不会真的等 500ms。</p>
 *
 * @since 0.2.0
 */
class PackageDownloaderTest {

    private static final byte[] PAYLOAD = "aha desktop package bytes\n".getBytes(StandardCharsets.UTF_8);

    /** 超过一个读缓冲区（64KB），用于「读了几段之后才取消」的用例。 */
    private static final byte[] BIG_PAYLOAD = "x".repeat(300 * 1024).getBytes(StandardCharsets.UTF_8);

    /** 外部（系统的 {@code sha256sum}）算出的 {@link #PAYLOAD} 的摘要。 */
    private static final String PAYLOAD_SHA256 =
            "da10a0bffaa63345e26c995c846a381e4ee2c57e7caf1263ea28f0cb2db34ebf";

    private MiniFileServer server;

    @TempDir
    Path staging;

    @BeforeEach
    void setUp() throws IOException {
        server = new MiniFileServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private PackageDownloader downloader() {
        return new PackageDownloader(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(),
                Set.of("127.0.0.1"), server.base().toString(), Duration.ofMillis(1));
    }

    private AssetInfo asset(String name, byte[] payload) {
        return new AssetInfo(name, payload.length, server.base() + "/assets/" + name, "uploaded");
    }

    @Test
    void downloadsThenVerifiesAndLeavesNoPartFile() throws IOException {
        String name = "aha-desktop-0.1.3.00242-windows-amd64.zip";
        server.responder((path, out) -> respond(out, 200, PAYLOAD));

        AtomicInteger lastDownloaded = new AtomicInteger();
        PackageDownloader.Downloaded downloaded = downloader().download(asset(name, PAYLOAD), staging,
                PAYLOAD_SHA256, (bytes, total) -> lastDownloaded.set((int) bytes),
                PackageDownloader.CancelSignal.NEVER);

        assertThat(downloaded.file()).isEqualTo(staging.resolve(name));
        assertThat(downloaded.checksumVerified()).isTrue();
        assertThat(downloaded.sha256()).isEqualTo(PAYLOAD_SHA256);
        assertThat(downloaded.size()).isEqualTo(PAYLOAD.length);
        assertThat(Files.readAllBytes(downloaded.file())).isEqualTo(PAYLOAD);
        assertThat(staging.resolve(name + ".part")).doesNotExist();
        assertThat(lastDownloaded.get()).isEqualTo(PAYLOAD.length);
        assertThat(server.callCount()).isEqualTo(1);
    }

    @Test
    void retriesOnServerErrorThenSucceeds() throws IOException {
        AtomicInteger attempts = new AtomicInteger();
        server.responder((path, out) -> {
            if (attempts.incrementAndGet() == 1) {
                respond(out, 500, "boom".getBytes(StandardCharsets.UTF_8));
            } else {
                respond(out, 200, PAYLOAD);
            }
        });

        PackageDownloader.Downloaded downloaded = downloader()
                .download(asset("a.zip", PAYLOAD), staging, null);

        assertThat(Files.readAllBytes(downloaded.file())).isEqualTo(PAYLOAD);
        assertThat(downloaded.checksumVerified()).isFalse();
        assertThat(server.callCount()).isEqualTo(2);
    }

    @Test
    void doesNotRetryOnClientError() {
        server.responder((path, out) -> respond(out, 404, "not found".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> downloader().download(asset("a.zip", PAYLOAD), staging, null))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("HTTP 404")
                .hasMessageContaining("重试没有意义")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_HTTP_ERROR));
        // 4xx 重试没有意义：只允许一次请求
        assertThat(server.callCount()).isEqualTo(1);
        assertThat(staging.resolve("a.zip")).doesNotExist();
        assertThat(staging.resolve("a.zip.part")).doesNotExist();
    }

    @Test
    void givesUpAfterMaxAttemptsWhenTheDownloadStaysIncomplete() {
        // 服务器每次只给 9 字节，而资产声明 9999 字节 ⇒ 每次都被判「下载不完整」
        server.responder((path, out) -> respond(out, 200, PAYLOAD));

        assertThatThrownBy(() -> downloader().download(
                new AssetInfo("a.zip", 9999, server.base() + "/assets/a.zip", "uploaded"), staging, null))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("下载不完整")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_DOWNLOAD_FAILED));
        assertThat(server.callCount()).isEqualTo(PackageDownloader.MAX_ATTEMPTS);
    }

    @Test
    void cancelKeepsThePartFileAndCreatesNoTarget() {
        AtomicInteger progressCalls = new AtomicInteger();
        server.responder((path, out) -> respond(out, 200, BIG_PAYLOAD));

        assertThatThrownBy(() -> downloader().download(asset("big.zip", BIG_PAYLOAD), staging, null,
                (bytes, total) -> progressCalls.incrementAndGet(),
                () -> progressCalls.get() > 0))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("已取消")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_CANCELLED));

        assertThat(staging.resolve("big.zip")).doesNotExist();
        assertThat(staging.resolve("big.zip.part")).exists();
        // 取消不重试
        assertThat(server.callCount()).isEqualTo(1);
    }

    @Test
    void rejectsUrlsOutsideTheAllowedPrefixWithoutAnyRequest() {
        AssetInfo outsider = new AssetInfo("a.zip", PAYLOAD.length,
                "https://example.invalid/assets/a.zip", "uploaded");

        assertThatThrownBy(() -> downloader().download(outsider, staging, null))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("不在允许的范围内")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_UNTRUSTED_SOURCE));
        assertThat(server.callCount()).isZero();
    }

    @Test
    void followsRedirectsInsideTheWhitelistOnly() throws IOException {
        server.responder((path, out) -> {
            if (path.equals("/assets/a.zip")) {
                redirect(out, "/assets/real.zip");
            } else {
                respond(out, 200, PAYLOAD);
            }
        });

        PackageDownloader.Downloaded downloaded = downloader()
                .download(asset("a.zip", PAYLOAD), staging, null);

        assertThat(Files.readAllBytes(downloaded.file())).isEqualTo(PAYLOAD);
        assertThat(server.paths()).containsExactly("/assets/a.zip", "/assets/real.zip");
    }

    @Test
    void allowsEveryHostGithubServesAssetsFrom() {
        // 2026-10-16 真机实测：GitHub 把发布资产换成 release-assets.githubusercontent.com，
        // 而白名单只有 objects.githubusercontent.com ⇒ 用户点升级直接失败在跳转白名单上。
        // mock 服务器给不出真实跳转，所以规则本身在这里单测（含「别把相似域名放进来」的反例）。
        Set<String> allowed = PackageDownloader.DEFAULT_ALLOWED_HOSTS;

        assertThat(PackageDownloader.isAllowedHost("github.com", allowed)).isTrue();
        assertThat(PackageDownloader.isAllowedHost("api.github.com", allowed)).isTrue();
        assertThat(PackageDownloader.isAllowedHost("objects.githubusercontent.com", allowed)).isTrue();
        assertThat(PackageDownloader.isAllowedHost("release-assets.githubusercontent.com", allowed))
                .as("GitHub 换过 CDN 主机名，这条规则就是为它加的").isTrue();
        assertThat(PackageDownloader.isAllowedHost("githubusercontent.com", allowed)).isTrue();

        assertThat(PackageDownloader.isAllowedHost("evil.example.com", allowed)).isFalse();
        assertThat(PackageDownloader.isAllowedHost("githubusercontent.com.evil.com", allowed))
                .as("后缀匹配必须贴在域名边界上").isFalse();
        assertThat(PackageDownloader.isAllowedHost("evilgithubusercontent.com", allowed)).isFalse();
    }

    @Test
    void refusesRedirectsLeavingTheWhitelist() {
        // 同端口、但主机名不同（localhost ≠ 127.0.0.1）⇒ 必须拒绝，且不再发第二次请求
        server.responder((path, out) -> redirect(out, "http://localhost:" + server.port() + "/evil.zip"));

        assertThatThrownBy(() -> downloader().download(asset("a.zip", PAYLOAD), staging, null))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("跳转目标的主机不在白名单")
                .hasMessageContaining("localhost")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_UNTRUSTED_SOURCE));
        assertThat(server.callCount()).isEqualTo(1);
    }

    @Test
    void reusesACachedFileWithTheSameSizeWithoutAnyRequest() throws IOException {
        String name = "aha-cli-0.1.3.00242.zip";
        Files.write(staging.resolve(name), PAYLOAD);
        server.responder((path, out) -> respond(out, 500, new byte[0]));

        PackageDownloader.Downloaded downloaded = downloader()
                .download(asset(name, PAYLOAD), staging, PAYLOAD_SHA256);

        assertThat(downloaded.checksumVerified()).isTrue();
        assertThat(downloaded.sha256()).isEqualTo(PAYLOAD_SHA256);
        assertThat(server.callCount()).isZero();
    }

    @Test
    void redownloadsWhenTheCachedFileFailsTheChecksum() throws IOException {
        String name = "aha-cli-0.1.3.00242.zip";
        // 同名同大小、但内容坏了：缓存不可信，应当丢掉重下，而不是直接失败
        Files.write(staging.resolve(name), "JAR-BYTES".getBytes(StandardCharsets.UTF_8));
        server.responder((path, out) -> respond(out, 200, PAYLOAD));

        PackageDownloader.Downloaded downloaded = downloader()
                .download(asset(name, PAYLOAD), staging, PAYLOAD_SHA256);

        assertThat(Files.readAllBytes(downloaded.file())).isEqualTo(PAYLOAD);
        assertThat(downloaded.checksumVerified()).isTrue();
        assertThat(server.callCount()).isEqualTo(1);
    }

    @Test
    void refusesToInstallWhenTheChecksumDoesNotMatch() {
        server.responder((path, out) -> respond(out, 200, PAYLOAD));

        assertThatThrownBy(() -> downloader().download(asset("a.zip", PAYLOAD), staging,
                "0".repeat(64)))
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("校验和不符")
                .hasMessageContaining("已拒绝落地")
                .satisfies(error -> assertThat(((UpdateException) error).code())
                        .isEqualTo(UpdateException.CODE_CHECKSUM_MISMATCH));

        // 正式文件绝不出现；未完成的 .part 保留（当排查现场用；重试是重新下载，不做 Range 续传）
        assertThat(staging.resolve("a.zip")).doesNotExist();
        assertThat(staging.resolve("a.zip.part")).exists();
    }

    // ── 测试脚手架 ────────────────────────────────────────────────────────────

    private static void respond(OutputStream out, int status, byte[] body, String... headers)
            throws IOException {
        StringBuilder head = new StringBuilder()
                .append("HTTP/1.1 ").append(status).append(" STATUS\r\n")
                .append("Content-Type: application/octet-stream\r\n")
                .append("Content-Length: ").append(body.length).append("\r\n");
        for (String header : headers) {
            head.append(header).append("\r\n");
        }
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
    }

    private static void redirect(OutputStream out, String location) throws IOException {
        out.write(("HTTP/1.1 302 Found\r\nLocation: " + location + "\r\nContent-Length: 0\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
    }

    /** 响应回调。 */
    @FunctionalInterface
    private interface Responder {
        void respond(String path, OutputStream out) throws IOException;
    }

    /** 极简本机 HTTP 服务器（只处理 GET 的请求行与请求头）。 */
    private static final class MiniFileServer implements AutoCloseable {

        private final ServerSocket serverSocket;

        private final Thread thread;

        private final AtomicInteger calls = new AtomicInteger();

        private final List<String> paths = new CopyOnWriteArrayList<>();

        private volatile Responder responder;

        MiniFileServer() throws IOException {
            this.serverSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
            this.thread = Thread.ofVirtual().name("mini-file").start(this::serve);
        }

        URI base() {
            return URI.create("http://127.0.0.1:" + port());
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        int callCount() {
            return calls.get();
        }

        List<String> paths() {
            return paths;
        }

        void responder(Responder responder) {
            this.responder = responder;
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
                // 请求头读干净即可：本用例只发 GET，没有请求体
            }
            String path = requestLine.split(" ")[1];
            paths.add(path);
            calls.incrementAndGet();
            Responder current = responder;
            if (current != null) {
                current.respond(path, out);
            }
            out.flush();
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
    @Test
    void refusesToDowngradeFromHttpsToPlainHttp() {
        // https → http 等于把包交给中间人；http → http（本机 mock 服务器）不算降级
        assertThat(PackageDownloader.downgradesScheme(
                URI.create("https://github.com/a.zip"), URI.create("http://github.com/a.zip")))
                .isTrue();
        assertThat(PackageDownloader.downgradesScheme(
                URI.create("https://github.com/a.zip"), URI.create("https://objects.github.com/a.zip")))
                .isFalse();
        assertThat(PackageDownloader.downgradesScheme(
                URI.create("http://127.0.0.1:1/a.zip"), URI.create("http://127.0.0.1:2/a.zip")))
                .isFalse();
    }

}
