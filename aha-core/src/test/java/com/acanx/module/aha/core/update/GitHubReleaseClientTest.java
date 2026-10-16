package com.acanx.module.aha.core.update;

import com.acanx.module.aha.common.exception.UpdateException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 发布页客户端的解析、翻页与错误分类（issue #51 §4.1）。
 *
 * <p>全部跑在本机 mock 服务器上，<b>不打真网络</b>（`TestingSpec.md` §5.1 的环境无关性要求）。
 * fixture 里 {@code GitHubReleasesBeta.json} 与 {@code GitHubReleasesFormal.json} 是
 * 2026-10-16 用 {@code gh api} 取的<b>真实响应投影</b>（只删掉与本功能无关的字段）。</p>
 *
 * @since 0.2.0
 */
class GitHubReleaseClientTest {

    private static final ObjectMapper JSON = JsonMapper.builder().build();

    private static final String BETA_FIXTURE = "/Fixtures/GitHubReleasesBeta.json";

    private MiniJsonServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MiniJsonServer();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private GitHubReleaseClient client(int perPage) {
        return new GitHubReleaseClient(server.base(), GitHubReleaseClient.DEFAULT_REPOSITORY, perPage,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), JSON);
    }

    private static String fixture(String resource) throws IOException {
        try (InputStream in = GitHubReleaseClientTest.class.getResourceAsStream(resource)) {
            assertThat(in).as("fixture 必须存在：%s", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parsesRealReleasePage() throws IOException {
        String page = fixture(BETA_FIXTURE);
        server.responder((path, out) -> respond(out, 200, page));

        List<ReleaseInfo> releases = client(100).listReleases();

        assertThat(releases).hasSize(6);
        ReleaseInfo first = releases.get(0);
        assertThat(first.tag()).isEqualTo("V0.1.3.00242-aha-jvm");
        assertThat(first.prerelease()).isTrue();
        assertThat(first.draft()).isFalse();
        assertThat(first.publishedAt()).isNotNull();
        assertThat(first.assetList()).hasSize(8);
        assertThat(first.assetList()).extracting(AssetInfo::name)
                .contains("aha-desktop-0.1.3.00242-windows-amd64.zip",
                        "aha-cli-0.1.3.00242-jdk27.zip");
        assertThat(first.assetList().get(0).uploaded()).isTrue();

        assertThat(server.callCount()).isEqualTo(1);
        assertThat(server.paths().get(0))
                .isEqualTo("/repos/ACANX/AHA/releases?per_page=100&page=1");
    }

    @Test
    void followsSecondPageUntilShortPage() throws IOException {
        String all = fixture(BETA_FIXTURE);
        server.responder((path, out) -> respond(out, 200, pageSlice(all, pageOf(path), 3)));

        List<ReleaseInfo> releases = client(3).listReleases();

        assertThat(releases).hasSize(6);
        assertThat(server.callCount()).isEqualTo(2);
        assertThat(server.paths()).containsExactly(
                "/repos/ACANX/AHA/releases?per_page=3&page=1",
                "/repos/ACANX/AHA/releases?per_page=3&page=2");
    }

    @Test
    void stopsAtMaxPages() throws IOException {
        // 每页都是满的 → 只能在 MAX_PAGES 处停下（否则会把发布页整页翻完）。
        String all = fixture(BETA_FIXTURE);
        server.responder((path, out) -> respond(out, 200, pageSlice(all, 1, 3)));

        List<ReleaseInfo> releases = client(3).listReleases();

        assertThat(releases).hasSize(2 * GitHubReleaseClient.MAX_PAGES);
        assertThat(server.callCount()).isEqualTo(GitHubReleaseClient.MAX_PAGES);
    }

    @Test
    void reportsRateLimitAsItsOwnReason() {
        server.responder((path, out) -> respond(out, 403,
                "{\"message\":\"API rate limit exceeded for 127.0.0.1\"}",
                "x-ratelimit-remaining: 0",
                "x-ratelimit-reset: 1780000000"));

        assertThatThrownBy(() -> client(100).listReleases())
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("限流")
                .hasMessageContaining("稍后再试")
                .hasMessageContaining("重置")
                .satisfies(error -> assertThat(((UpdateException) error).rateLimited()).isTrue()
                );
        // 限流不重试：重试只会放大限流。
        assertThat(server.callCount()).isEqualTo(1);
    }

    @Test
    void reportsHttpErrorWithoutRetry() {
        server.responder((path, out) -> respond(out, 500, "{\"message\":\"boom\"}"));

        assertThatThrownBy(() -> client(100).listReleases())
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("HTTP 500");
        assertThat(server.callCount()).isEqualTo(1);
    }

    @Test
    void reportsInvalidResponseOnBrokenJson() {
        server.responder((path, out) -> respond(out, 200, "这不是 JSON"));

        assertThatThrownBy(() -> client(100).listReleases())
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("无法解析");
    }

    @Test
    void reportsNetworkErrorWhenEndpointIsUnreachable() {
        GitHubReleaseClient unreachable = new GitHubReleaseClient(URI.create("http://127.0.0.1:1"),
                GitHubReleaseClient.DEFAULT_REPOSITORY, 100,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(), JSON);

        assertThatThrownBy(unreachable::listReleases)
                .isInstanceOf(UpdateException.class)
                .hasMessageContaining("访问发布页失败");
    }

    @Test
    void toleratesUnknownFields() {
        server.responder((path, out) -> respond(out, 200, """
                [
                  {
                    "tag_name": "V0.1.3",
                    "name": "AHA V0.1.3",
                    "prerelease": false,
                    "draft": false,
                    "published_at": "2026-10-20T00:00:00Z",
                    "author": {"login": "ACANX"},
                    "assets": [
                      {"name": "aha-cli-0.1.3.zip", "size": 1234,
                       "browser_download_url": "https://example.invalid/a.zip",
                       "state": "uploaded", "digest": "sha256:deadbeef"}
                    ]
                  }
                ]
                """));

        List<ReleaseInfo> releases = client(100).listReleases();

        assertThat(releases).hasSize(1);
        assertThat(releases.get(0).tag()).isEqualTo("V0.1.3");
        assertThat(releases.get(0).assetList()).hasSize(1);
        assertThat(releases.get(0).assetList().get(0).name()).isEqualTo("aha-cli-0.1.3.zip");
    }

    // ── 测试脚手架 ────────────────────────────────────────────────────────────

    private static int pageOf(String path) {
        // 不能用 indexOf("page=")：它会在 per_page= 里先命中（"per_page=" 含子串 "page="）。
        Matcher matcher = Pattern.compile("[?&]page=([0-9]+)").matcher(path);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 1;
    }

    /** 把 fixture 数组的第 page 页（每页 size 条）切出来，模拟分页响应。 */
    private static String pageSlice(String json, int page, int size) {
        List<Map<String, Object>> all = JSON.readValue(json,
                new TypeReference<List<Map<String, Object>>>() {
                });
        int from = Math.min((page - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        return JSON.writeValueAsString(all.subList(from, to));
    }

    private static void respond(OutputStream out, int status, String body, String... headers)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        StringBuilder head = new StringBuilder()
                .append("HTTP/1.1 ").append(status).append(" STATUS\r\n")
                .append("Content-Type: application/json; charset=utf-8\r\n")
                .append("Content-Length: ").append(bytes.length).append("\r\n");
        for (String header : headers) {
            head.append(header).append("\r\n");
        }
        head.append("Connection: close\r\n\r\n");
        out.write(head.toString().getBytes(StandardCharsets.ISO_8859_1));
        out.write(bytes);
    }

    /** 响应回调。 */
    @FunctionalInterface
    private interface Responder {
        void respond(String path, OutputStream out) throws IOException;
    }

    /**
     * 极简本机 HTTP 服务器：只处理 GET 的请求行与请求头，响应体由用例给。
     *
     * <p>与 {@code DefaultLlmClientTest} 里的同类脚手架分开写：重复率门禁只统计主源码
     * （{@code src/main/java}），测试脚手架重复不进门禁；而把两处合并需要动一个与本次改动
     * 无关的测试文件，得不偿失。</p>
     */
    private static final class MiniJsonServer implements AutoCloseable {

        private final ServerSocket serverSocket;

        private final Thread thread;

        private final AtomicInteger calls = new AtomicInteger();

        private final List<String> paths = new CopyOnWriteArrayList<>();

        private volatile Responder responder;

        MiniJsonServer() throws IOException {
            this.serverSocket = new ServerSocket(0, 16, InetAddress.getLoopbackAddress());
            this.thread = Thread.ofVirtual().name("mini-json").start(this::serve);
        }

        URI base() {
            return URI.create("http://127.0.0.1:" + serverSocket.getLocalPort());
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
}
